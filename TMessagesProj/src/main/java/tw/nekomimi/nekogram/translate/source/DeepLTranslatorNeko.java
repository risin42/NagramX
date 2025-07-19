package tw.nekomimi.nekogram.translate.source;

import app.nekogram.translator.DeepLTranslator;

public class DeepLTranslatorNeko {

    public static String translate(String text, String from, String to) throws Exception {
        var translator = DeepLTranslator.getInstance();

        var result = translator.translate(text, from, to);
        return result.translation;
    }
}
