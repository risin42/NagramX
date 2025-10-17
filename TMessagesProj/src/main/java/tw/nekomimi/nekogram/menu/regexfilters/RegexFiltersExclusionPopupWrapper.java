package tw.nekomimi.nekogram.menu.regexfilters;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import tw.nekomimi.nekogram.helpers.AyuFilter;

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
    }

    public void updateItems() {
        defaultItem.setChecked(!AyuFilter.isDialogExcluded(chatId));
        exclusionItem.setChecked(AyuFilter.isDialogExcluded(chatId));
    }
}
