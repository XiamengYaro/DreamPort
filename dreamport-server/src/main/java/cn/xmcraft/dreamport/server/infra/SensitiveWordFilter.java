package cn.xmcraft.dreamport.server.infra;

import cn.xmcraft.dreamport.server.settings.SettingService;
import org.springframework.stereotype.Service;

/**
 * 敏感词过滤(dp_setting sensitive.words 逗号分隔,命中替换等长 *** )。
 * 从 ChatService 抽出共用:聊天/论坛发帖/回帖/反馈等用户生成内容统一走这里。
 */
@Service
public class SensitiveWordFilter {

    public static final String KEY_SENSITIVE_WORDS = "sensitive.words";

    private final SettingService settingService;

    public SensitiveWordFilter(SettingService settingService) {
        this.settingService = settingService;
    }

    public String filter(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String words = settingService.getRaw(KEY_SENSITIVE_WORDS);
        if (words == null || words.isBlank()) {
            return text;
        }
        for (String w : words.split("[,，]")) {
            String word = w.trim();
            if (word.length() >= 2 && text.contains(word)) {
                text = text.replace(word, "*".repeat(word.length()));
            }
        }
        return text;
    }

    /** 是否命中敏感词(替换前的原文判定,供"先发后审时命中即转待审"之类场景) */
    public boolean hits(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String words = settingService.getRaw(KEY_SENSITIVE_WORDS);
        if (words == null || words.isBlank()) {
            return false;
        }
        for (String w : words.split("[,，]")) {
            String word = w.trim();
            if (word.length() >= 2 && text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
