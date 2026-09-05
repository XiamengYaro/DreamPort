package cn.xmcraft.dreamport.server.verification;

import cn.xmcraft.dreamport.server.infra.I18nService;
import cn.xmcraft.dreamport.server.infra.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码（内存态，5 分钟有效，5 次失败作废——对齐旧版 VerifyCodeService）。
 */
@Service
public class VerifyCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerifyCodeService.class);
    private static final long TTL_MS = 5 * 60_000L;
    private static final int MAX_ATTEMPTS = 5;

    public record Entry(String code, long expiresAt, int attempts) {
    }

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final MailService mailService;
    private final I18nService i18n;

    public VerifyCodeService(MailService mailService, I18nService i18n) {
        this.mailService = mailService;
        this.i18n = i18n;
    }

    /** 生成并发送验证码 */
    public void send(String email, String lang) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(email.toLowerCase(), new Entry(code, System.currentTimeMillis() + TTL_MS, 0));
        mailService.sendVerifyCode(email, code, lang);
        log.info("验证码已生成: {}", email);
    }

    /** 校验并消费 */
    public boolean check(String email, String code) {
        String key = email == null ? "" : email.toLowerCase();
        Entry entry = codes.get(key);
        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() > entry.expiresAt()) {
            codes.remove(key);
            return false;
        }
        if (!entry.code().equals(code)) {
            int attempts = entry.attempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                codes.remove(key);
            } else {
                codes.put(key, new Entry(entry.code(), entry.expiresAt(), attempts));
            }
            return false;
        }
        codes.remove(key);
        return true;
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        codes.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }
}
