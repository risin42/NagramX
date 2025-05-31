package tw.nekomimi.nekogram.menu.copy;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;

import java.util.HashMap;

public class CopyItem {
    public static final int ID_COPY_LINK = 22;
    public static final int ID_COPY_IN_PM = 2025;
    public static final int ID_COPY_PHOTO = 150;
    public static final int ID_COPY_PHOTO_AS_STICKER = 151;

    static final int[] ITEM_IDS = new int[]{
            ID_COPY_LINK,
            ID_COPY_IN_PM,
            ID_COPY_PHOTO,
            ID_COPY_PHOTO_AS_STICKER
    };

    static final HashMap<Integer, String> ITEM_TITLES = new HashMap<>() {{
        put(ID_COPY_LINK, getString(R.string.CopyLink));
        put(ID_COPY_IN_PM, getString(R.string.CopyLink));
        put(ID_COPY_PHOTO, getString(R.string.CopyPhoto));
        put(ID_COPY_PHOTO_AS_STICKER, getString(R.string.CopyPhotoAsSticker));
    }};
}
