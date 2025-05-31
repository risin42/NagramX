package tw.nekomimi.nekogram.menu.copy;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import java.util.Arrays;

public class CopyPopupWrapper {

    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;

    public CopyPopupWrapper(ChatActivity fragment, MessageObject selectedObject, int fromOption, boolean isPrivate, PopupSwipeBackLayout swipeBackLayout, ActionBarMenuItem.ActionBarMenuItemDelegate delegate, Theme.ResourcesProvider resourcesProvider) {
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, getString(R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());

            ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);
        }

        var isPhoto = selectedObject.isPhoto();

        Arrays.stream(CopyItem.ITEM_IDS).forEach(id -> {
            if (fromOption == id) {
                return;
            }
            if (!isPhoto && (id == CopyItem.ID_COPY_PHOTO || id == CopyItem.ID_COPY_PHOTO_AS_STICKER)) {
                return;
            }
            if (isPrivate && id == CopyItem.ID_COPY_LINK) {
                return;
            }
            if (!isPrivate && id == CopyItem.ID_COPY_IN_PM) {
                return;
            }
            var item = ActionBarMenuItem.addItem(false, false, windowLayout, id == CopyItem.ID_COPY_LINK || id == CopyItem.ID_COPY_IN_PM ? R.drawable.msg_link : R.drawable.msg_copy, CopyItem.ITEM_TITLES.get(id), false, resourcesProvider);
            item.setOnClickListener(view -> delegate.onItemClick(id));
        });
    }
}
