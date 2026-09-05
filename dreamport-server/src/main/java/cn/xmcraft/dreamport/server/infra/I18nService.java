package cn.xmcraft.dreamport.server.infra;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 服务端 i18n（i18n/messages_{zh,en}.properties，键与旧版一致，Rules.md §8）。
 * lang 取值 zh/en，默认 zh。
 */
@Service
public class I18nService {

    private final MessageSource messageSource;

    public I18nService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String msg(String key, String lang, Object... args) {
        Locale locale = "en".equalsIgnoreCase(lang) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
        try {
            return messageSource.getMessage(key, args, key, locale);
        } catch (Exception e) {
            return key;
        }
    }

    public String msg(String key) {
        return msg(key, LocaleContextHolder.getLocale().getLanguage());
    }
}
