package tw.nekomimi.nekogram.filters.popup;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;

import tw.nekomimi.nekogram.filters.AyuFilter;
import tw.nekomimi.nekogram.filters.RegexChatFiltersListActivity;
import tw.nekomimi.nekogram.filters.RegexFilterEditActivity;

public class RegexChatFilterPopup {
    public static void show(RegexChatFiltersListActivity fragment, View anchorView, float touchedX, float touchedY, long dialogId, int filterIdx) {
        if (fragment.getFragmentView() == null) return;

        var layout = RegexPopupUtils.createLayout(fragment);
        var popupWindow = RegexPopupUtils.createPopupWindow(layout);
        var windowLayout = createPopupLayout(layout, popupWindow, fragment, dialogId, filterIdx);
        RegexPopupUtils.showPopupAtTouch(fragment, anchorView, touchedX, touchedY, popupWindow, windowLayout);
    }

    private static ActionBarPopupWindow.ActionBarPopupWindowLayout createPopupLayout(ActionBarPopupWindow.ActionBarPopupWindowLayout layout, ActionBarPopupWindow popupWindow, RegexChatFiltersListActivity fragment, long dialogId, int filterIdx) {
        layout.setFitItems(true);

        var editBtn = ActionBarMenuItem.addItem(layout, R.drawable.msg_edit, getString(R.string.Edit), false, fragment.getResourceProvider());
        editBtn.setOnClickListener(view -> {
            fragment.presentFragment(new RegexFilterEditActivity(dialogId, filterIdx));
            popupWindow.dismiss();
        });

        var deleteBtn = ActionBarMenuItem.addItem(layout, R.drawable.msg_delete, getString(R.string.Delete), false, fragment.getResourceProvider());
        deleteBtn.setOnClickListener(view -> {
            AyuFilter.removeChatFilter(dialogId, filterIdx);
            fragment.onResume();
            popupWindow.dismiss();
        });
        RegexPopupUtils.applyDeleteItemColor(deleteBtn);

        return layout;
    }
}
