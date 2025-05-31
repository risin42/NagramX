package tw.nekomimi.nekogram.menu.translate;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;

import java.util.HashMap;

public class TranslateItem {
    public static final int ID_TRANSLATE_LLM = 2034;

    static final int[] ITEM_IDS = new int[]{ID_TRANSLATE_LLM,};

    static final HashMap<Integer, String> ITEM_TITLES = new HashMap<>() {{
        put(ID_TRANSLATE_LLM, getString(R.string.TranslateMessageLLM));
    }};
}
