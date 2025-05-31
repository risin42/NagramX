package tw.nekomimi.nekogram.menu.reply;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import java.util.Arrays;

public class ReplyPopupWrapper {

    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;

    public ReplyPopupWrapper(ChatActivity fragment, PopupSwipeBackLayout swipeBackLayout, ActionBarMenuItem.ActionBarMenuItemDelegate delegate, Theme.ResourcesProvider resourcesProvider) {
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, getString(R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());

            ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);
        }

        Arrays.stream(ReplyItem.ITEM_IDS).forEach(id -> {
            var item = ActionBarMenuItem.addItem(false, false, windowLayout, R.drawable.menu_reply, ReplyItem.ITEM_TITLES.get(id), false, resourcesProvider);
            item.setOnClickListener(view -> delegate.onItemClick(id));
        });
    }
}
