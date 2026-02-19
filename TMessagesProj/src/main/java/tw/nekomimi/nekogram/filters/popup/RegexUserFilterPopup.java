package tw.nekomimi.nekogram.filters.popup;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class RegexUserFilterPopup {
    public static void show(BaseFragment fragment, View anchorView, float touchedX, float touchedY, Theme.ResourcesProvider resourcesProvider, Runnable onDelete) {
        if (fragment.getFragmentView() == null) {
            return;
        }

        var layout = RegexPopupUtils.createLayout(fragment);
        var popupWindow = RegexPopupUtils.createPopupWindow(layout);
        var windowLayout = createPopupLayout(layout, popupWindow, resourcesProvider, onDelete);
        RegexPopupUtils.showPopupAtTouch(fragment, anchorView, touchedX, touchedY, popupWindow, windowLayout);
    }

    private static ActionBarPopupWindow.ActionBarPopupWindowLayout createPopupLayout(ActionBarPopupWindow.ActionBarPopupWindowLayout layout, ActionBarPopupWindow popupWindow, Theme.ResourcesProvider resourcesProvider, Runnable onDelete) {
        layout.setFitItems(true);

        var deleteBtn = ActionBarMenuItem.addItem(layout, R.drawable.msg_delete, getString(R.string.Delete), false, resourcesProvider);
        deleteBtn.setOnClickListener(view -> {
            if (onDelete != null) {
                onDelete.run();
            }
            popupWindow.dismiss();
        });
        RegexPopupUtils.applyDeleteItemColor(deleteBtn);

        return layout;
    }
}
