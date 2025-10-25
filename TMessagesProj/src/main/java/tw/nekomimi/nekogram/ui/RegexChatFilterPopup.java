package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.helpers.AyuFilter;

public class RegexChatFilterPopup {
    public static void show(RegexChatFiltersListActivity fragment, View anchorView, float touchedX, float touchedY, long dialogId, int filterIdx) {
        if (fragment.getFragmentView() == null) return;

        var layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(fragment.getContext());
        var popupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        var windowLayout = createPopupLayout(layout, popupWindow, fragment, dialogId, filterIdx);

        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        windowLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.getContentView().setFocusableInTouchMode(true);

        float x = touchedX, y = touchedY;
        View view = anchorView;
        while (view != fragment.getFragmentView()) {
            if (view.getParent() == null) return;
            x += view.getX();
            y += view.getY();
            view = (View) view.getParent();
        }
        popupWindow.showAtLocation(fragment.getFragmentView(), 0, (int) x, (int) y);
        popupWindow.dimBehind();
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
        int deleteBtnColor = Theme.getColor(Theme.key_text_RedBold);
        deleteBtn.setColors(deleteBtnColor, deleteBtnColor);

        return layout;
    }
}
