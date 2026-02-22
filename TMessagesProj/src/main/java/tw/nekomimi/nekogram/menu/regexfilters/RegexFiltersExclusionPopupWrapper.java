package tw.nekomimi.nekogram.menu.regexfilters;

import static org.telegram.messenger.LocaleController.getString;

import android.util.TypedValue;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import tw.nekomimi.nekogram.filters.AyuFilter;

public class RegexFiltersExclusionPopupWrapper {

    private final long chatId;
    private final ActionBarMenuSubItem defaultItem;
    private final ActionBarMenuSubItem exclusionItem;
    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;

    public RegexFiltersExclusionPopupWrapper(BaseFragment fragment, PopupSwipeBackLayout swipeBackLayout, long chatId, Theme.ResourcesProvider resourcesProvider) {
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);
        this.chatId = chatId;

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, getString(R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
            ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);
        }

        defaultItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(R.string.Default), true, resourcesProvider);
        defaultItem.setChecked(!AyuFilter.isDialogExcluded(chatId));
        defaultItem.setOnClickListener(view -> {
            AyuFilter.setDialogExcluded(chatId, false);
            updateItems();
        });

        exclusionItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(R.string.SaveDeletedExcluded), true, resourcesProvider);
        exclusionItem.setChecked(AyuFilter.isDialogExcluded(chatId));
        exclusionItem.setOnClickListener(view -> {
            AyuFilter.setDialogExcluded(chatId, true);
            updateItems();
        });

        ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);
        TextView textView = new TextView(context);
        textView.setTag(R.id.fit_width_tag, 1);
        textView.setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(8), AndroidUtilities.dp(13), AndroidUtilities.dp(8));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        textView.setText(getString(R.string.RegexFiltersSubMenuDescription));
        windowLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public void updateItems() {
        defaultItem.setChecked(!AyuFilter.isDialogExcluded(chatId));
        exclusionItem.setChecked(AyuFilter.isDialogExcluded(chatId));
    }
}
