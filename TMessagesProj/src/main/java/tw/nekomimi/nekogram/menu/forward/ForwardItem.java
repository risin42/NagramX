package tw.nekomimi.nekogram.menu.forward;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;

import java.util.Collection;
import java.util.HashMap;

public class ForwardItem {
    public static final int ID_FORWARD_NOQUOTE = 2011;
    public static final int ID_FORWARD_NOCAPTION = 2035;

    static final int[] ITEM_IDS = new int[]{
            ID_FORWARD_NOQUOTE,
            ID_FORWARD_NOCAPTION
    };

    static final HashMap<Integer, String> ITEM_TITLES = new HashMap<>() {{
        put(ID_FORWARD_NOQUOTE, getString(R.string.NoQuoteForward));
        put(ID_FORWARD_NOCAPTION, getString(R.string.NoCaptionForward));
    }};

    public static boolean hasCaption(Collection<MessageObject> messages) {
        return messages.stream().anyMatch(messageObject -> !TextUtils.isEmpty(messageObject.caption));
    }

    public static boolean hasCaption(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        if (!TextUtils.isEmpty(selectedObject.caption)) {
            return true;
        } else if (selectedObjectGroup != null) {
            return selectedObjectGroup.messages.stream().anyMatch(messageObject -> !TextUtils.isEmpty(messageObject.caption));
        } else {
            return false;
        }
    }
}
