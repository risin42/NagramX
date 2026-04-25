package tw.nekomimi.nekogram.helpers;

import android.text.TextUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;

public class LocaleHelper {

    public static String getSuggestedBetaLanguageCode(String systemLang) {
        if (systemLang != null && systemLang.toLowerCase().startsWith("ja")) {
            return "ja_beta";
        }
        return null;
    }

    public static LocaleController.LocaleInfo localeInfoFromLanguage(TLRPC.TL_langPackLanguage language) {
        if (language == null || TextUtils.isEmpty(language.lang_code)) {
            return null;
        }
        language.lang_code = language.lang_code.replace('-', '_').toLowerCase();
        language.plural_code = language.plural_code.replace('-', '_').toLowerCase();
        if (language.base_lang_code != null) {
            language.base_lang_code = language.base_lang_code.replace('-', '_').toLowerCase();
        }
        LocaleController.LocaleInfo info = new LocaleController.LocaleInfo();
        info.name = language.native_name;
        info.nameEnglish = language.name;
        info.shortName = language.lang_code;
        info.baseLangCode = language.base_lang_code;
        info.pluralLangCode = language.plural_code;
        info.isRtl = language.rtl;
        info.pathToFile = language.official ? "remote" : "unofficial";
        return info;
    }
}
