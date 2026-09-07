package cn.xmcraft.dreamport.server.bootstrap;

import cn.xmcraft.dreamport.server.questionnaire.QuestionnaireService;
import cn.xmcraft.dreamport.server.settings.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认示例内容种子：全新部署时让门户/问卷立刻可用。
 * - 门户（夏日小镇XMCraft：轮播/特性/时间线/团队/社交）
 * - 背景与公告、验证页配置
 * - 默认问卷题库（dp_question 为空时导入）
 * 仅在对应键/表为空时执行（不覆盖管理员已配置的内容）。
 */
@Component
public class DefaultContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultContentSeeder.class);

    private final SettingService settingService;
    private final QuestionnaireService questionnaireService;

    public DefaultContentSeeder(SettingService settingService, QuestionnaireService questionnaireService) {
        this.settingService = settingService;
        this.questionnaireService = questionnaireService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedPortal();
            seedBackground();
            seedVerifyPage();
            seedQuestionnaire();
        } catch (Exception e) {
            log.error("[种子] 默认内容生成失败: {}", e.getMessage(), e);
        }
    }

    private void seedPortal() {
        if (!settingService.getMap(SettingService.KEY_PORTAL).isEmpty()) {
            return;
        }
        Map<String, Object> portal = new LinkedHashMap<>();
        portal.put("server_name", "夏日小镇XMCraft");
        portal.put("brand_short", "XMCraft");
        portal.put("brand_tagline", "玩家账户系统");
        portal.put("favicon", "");
        portal.put("accent", "#f97316");
        portal.put("subtitle", "Minecraft Java/基岩双版生存服务器");
        portal.put("description", "一个有趣、友好的 Minecraft 生存服务器，起于 2020 年的夏日小镇，欢迎每一位玩家加入！");
        portal.put("version", "1.20.4");
        portal.put("server_ip", "mc.xmcraft.cn");
        portal.put("server_port", 25565);
        portal.put("map_url", "");
        portal.put("logo", "/Logo111.png");       // 原项目 Logo
        portal.put("icp", "");
        portal.put("social", Map.of("wiki", "", "qq_group", ""));
        portal.put("carousel", List.of(
                Map.of("image", "/bg.png", "title", "欢迎来到夏日小镇XMCraft", "subtitle", "开始你的冒险之旅"),
                Map.of("image", "/bg.webp", "title", "共建温馨小镇", "subtitle", "与伙伴们一起探索与建造"),
                Map.of("image", "/bg.jpg", "title", "公平公正的游戏环境", "subtitle", "纯净生存 · 无付费特权")));
        portal.put("features", List.of(
                Map.of("icon", "home", "title", "温馨社区", "description", "友好的玩家氛围，管理团队在线响应"),
                Map.of("icon", "bolt", "title", "红石与生存", "description", "支持红石机器与生电玩法，公共机器共享"),
                Map.of("icon", "shield", "title", "纯净公平", "description", "无付费特权，反作弊保障，领地保护")));
        portal.put("team", List.of(
                Map.of("name", "Xia_Meng_", "role", "服主"),
                Map.of("name", "管理员招募中", "role", "管理员")));
        portal.put("timeline", List.of(
                Map.of("date", "2020-07-15", "title", "服务器创立", "description", "夏日小镇正式开服"),
                Map.of("date", "2021-07-15", "title", "一周年庆典", "description", "社区成员突破百人"),
                Map.of("date", "2026-09-06", "title", "梦港焕新", "description", "全新门户与玩家管理系统上线")));
        settingService.set(SettingService.KEY_PORTAL, portal);
        log.info("[种子] 已生成默认门户内容（夏日小镇XMCraft）");
    }

    private void seedBackground() {
        if (settingService.getMap(SettingService.KEY_BACKGROUND).isEmpty()) {
            Map<String, Object> bg = new LinkedHashMap<>();
            bg.put("image", "/bg.webp");
            bg.put("opacity", 0.55);
            bg.put("blur", 20);
            settingService.set(SettingService.KEY_BACKGROUND, bg);
        }
        if (settingService.get(SettingService.KEY_ANNOUNCEMENT, String.class) == null) {
            settingService.set(SettingService.KEY_ANNOUNCEMENT, "欢迎加入夏日小镇XMCraft！遇到问题请及时联系管理团队。");
        }
    }

    private void seedVerifyPage() {
        if (!settingService.getMap("verify.config").isEmpty()) {
            return;
        }
        Map<String, Object> verify = new LinkedHashMap<>();
        verify.put("title", "ID 验证");
        verify.put("subtitle", "验证你的 Minecraft 账户");
        verify.put("java_server_address", "mc.xmcraft.cn");
        verify.put("java_server_port", 25565);
        verify.put("bedrock_server_address", "mc.xmcraft.cn");
        verify.put("bedrock_server_port", 19132);
        verify.put("java_version", "1.20.4");
        verify.put("instructions", "使用已绑定的游戏 ID 在 3 分钟内进服一次，然后返回本页点击验证。");
        settingService.set("verify.config", verify);
    }

    private void seedQuestionnaire() {
        try (var in = getClass().getResourceAsStream("/questionnaire-default.yml")) {
            if (in == null || !questionnaireService.questions(
                    questionnaireService.activeQuestionnaire().id()).isEmpty()) {
                return;
            }
            int count = questionnaireService.importYaml(new String(in.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
            log.info("[种子] 已导入默认问卷题库 {} 题", count);
        } catch (Exception e) {
            log.warn("[种子] 默认问卷导入失败: {}", e.getMessage());
        }
    }
}
