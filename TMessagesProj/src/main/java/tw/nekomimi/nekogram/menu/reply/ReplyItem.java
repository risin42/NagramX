package tw.nekomimi.nekogram.menu.reply;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;

import java.util.HashMap;

public class ReplyItem {
    public static final int ID_REPLY_PRIVATE = 2033;

    static final int[] ITEM_IDS = new int[]{
            ID_REPLY_PRIVATE,
    };

    static final HashMap<Integer, String> ITEM_TITLES = new HashMap<>() {{
        put(ID_REPLY_PRIVATE, getString(R.string.ReplyInPrivate));
    }};
}
