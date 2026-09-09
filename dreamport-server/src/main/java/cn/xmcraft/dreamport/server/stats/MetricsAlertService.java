package cn.xmcraft.dreamport.server.stats;

import cn.xmcraft.dreamport.server.infra.MailService;
import cn.xmcraft.dreamport.server.notification.NotificationRecord;
import cn.xmcraft.dreamport.server.notification.NotificationRepository;
import cn.xmcraft.dreamport.server.settings.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TPS 低阈值告警：某服近 1 分钟 TPS 连续 3 次心跳低于阈值 → 管理员铃铛 + 通知邮箱。
 * 默认阈值 15.0,1 小时冷却（每服独立）,可在 dp_setting metrics.config 关闭/调整。
 */
@Service
public class MetricsAlertService {

    private static final Logger log = LoggerFactory.getLogger(MetricsAlertService.class);
    private static final int TRIGGER_STREAK = 3;
    private static final long COOLDOWN_MS = 60L * 60_000;

    private final ServerStatsService statsService;
    private final SettingService settingService;
    private final NotificationRepository notificationRepository;
    private final MailService mailService;

    /** serverId → 连续低 TPS 计数 */
    private final Map<String, Integer> lowStreak = new ConcurrentHashMap<>();
    /** serverId → 上次告警时间（冷却） */
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();

    public MetricsAlertService(ServerStatsService statsService, SettingService settingService,
                               NotificationRepository notificationRepository, MailService mailService) {
        this.statsService = statsService;
        this.settingService = settingService;
        this.notificationRepository = notificationRepository;
        this.mailService = mailService;
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 120_000)
    public void check() {
        double threshold = threshold();
        if (threshold <= 0) {
            return;
        }
        for (var hb : statsService.heartbeats().values()) {
            Double tps = hb.tps1m();
            String serverId = hb.serverId();
            if (tps == null) {
                lowStreak.remove(serverId);
                continue;
            }
            if (tps < threshold) {
                int streak = lowStreak.merge(serverId, 1, Integer::sum);
                if (streak >= TRIGGER_STREAK) {
                    lowStreak.put(serverId, 0);
                    long now = System.currentTimeMillis();
                    Long prev = lastAlert.get(serverId);
                    if (prev == null || now - prev > COOLDOWN_MS) {
                        lastAlert.put(serverId, now);
                        alert(serverId, tps, threshold);
                    }
                }
            } else {
                lowStreak.remove(serverId);
            }
        }
    }

    private double threshold() {
        try {
            Map<String, Object> cfg = settingService.getMap(SettingService.KEY_METRICS_CONFIG);
            Object enabled = cfg.get("tpsAlertEnabled");
            if (enabled != null && !Boolean.parseBoolean(String.valueOf(enabled))) {
                return 0;
            }
            Object t = cfg.get("tpsThreshold");
            return t == null ? 15.0 : Double.parseDouble(String.valueOf(t));
        } catch (Exception e) {
            return 15.0;
        }
    }

    private void alert(String serverId, double tps, double threshold) {
        log.warn("[告警] {} TPS 持续偏低: {}（阈值 {}）", serverId, tps, threshold);
        String text = "服务器「" + serverId + "」TPS 持续偏低（" + String.format("%.1f", tps)
                + ",阈值 " + threshold + "）,请检查服务器负载";
        try {
            var admins = settingService.get(SettingService.KEY_ADMINS, java.util.List.class);
            if (admins != null) {
                for (Object a : admins) {
                    notificationRepository.save(new NotificationRecord(
                            null, String.valueOf(a), "server_tps_low", "TPS 低阈值告警", text, null, null, null));
                }
            }
            String notifyEmail = settingService.get(SettingService.KEY_ADMIN_NOTIFY_EMAIL, String.class);
            if (notifyEmail != null && !notifyEmail.isBlank()) {
                mailService.sendAdminNotification(text, notifyEmail);
            }
        } catch (Exception e) {
            log.warn("TPS 告警发送失败: {}", e.getMessage());
        }
    }
}
