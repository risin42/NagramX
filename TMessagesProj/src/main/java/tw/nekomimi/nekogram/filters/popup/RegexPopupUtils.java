package tw.nekomimi.nekogram.filters.popup;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

final class RegexPopupUtils {

    private RegexPopupUtils() {
    }

    static ActionBarPopupWindow.ActionBarPopupWindowLayout createLayout(BaseFragment fragment) {
        return new ActionBarPopupWindow.ActionBarPopupWindowLayout(fragment.getContext());
    }

    static ActionBarPopupWindow createPopupWindow(ActionBarPopupWindow.ActionBarPopupWindowLayout layout) {
        ActionBarPopupWindow popupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        return popupWindow;
    }

    static void showPopupAtTouch(BaseFragment fragment, View anchorView, float touchedX, float touchedY,
                                 ActionBarPopupWindow popupWindow, View windowLayout) {
        windowLayout.measure(
            View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST)
        );

        float x = touchedX;
        float y = touchedY;
        View view = anchorView;
        while (view != fragment.getFragmentView()) {
            if (view.getParent() == null) {
                return;
            }
            x += view.getX();
            y += view.getY();
            view = (View) view.getParent();
        }
        popupWindow.showAtLocation(fragment.getFragmentView(), 0, (int) x, (int) y);
        popupWindow.dimBehind();
    }

    static void applyDeleteItemColor(ActionBarMenuSubItem item) {
        int deleteBtnColor = Theme.getColor(Theme.key_text_RedBold);
        item.setColors(deleteBtnColor, deleteBtnColor);
    }
}
