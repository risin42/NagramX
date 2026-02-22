package tw.nekomimi.nekogram.menu.forum;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import tw.nekomimi.nekogram.DialogConfig;

public class CustomForumTabsPopupWrapper {

    private final long dialogId;
    private final ActionBarMenuSubItem defaultItem;
    private final ActionBarMenuSubItem enableItem;
    private final ActionBarMenuSubItem disableItem;
    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;

    public CustomForumTabsPopupWrapper(BaseFragment fragment, PopupSwipeBackLayout swipeBackLayout, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        Context context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider);
        windowLayout.setFitItems(true);
        this.dialogId = dialogId;

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, getString(R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
            ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);
        }

        defaultItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(R.string.Default), true, resourcesProvider);

        defaultItem.setOnClickListener(view -> {
            DialogConfig.removeCustomForumTabsConfig(dialogId);
            updateItems();
        });
        defaultItem.setAlpha(1.0f);

        enableItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(R.string.TopicsLayoutTabs), true, resourcesProvider);
        enableItem.setChecked(DialogConfig.hasCustomForumTabsConfig(dialogId) && DialogConfig.isCustomForumTabsEnable(dialogId));
        enableItem.setOnClickListener(view -> {
            DialogConfig.setCustomForumTabsEnable(dialogId, true);
            updateItems();
        });
        enableItem.setAlpha(1.0f);

        disableItem = ActionBarMenuItem.addItem(windowLayout, 0, getString(R.string.TopicsLayoutList), true, resourcesProvider);
        disableItem.setChecked(DialogConfig.hasCustomForumTabsConfig(dialogId) && !DialogConfig.isCustomForumTabsEnable(dialogId));
        disableItem.setOnClickListener(view -> {
            DialogConfig.setCustomForumTabsEnable(dialogId, false);
            updateItems();
        });
        disableItem.setAlpha(1.0f);
        updateItems();
    }

    public void updateItems() {
        defaultItem.setChecked(!DialogConfig.hasCustomForumTabsConfig(dialogId));
        enableItem.setChecked(DialogConfig.hasCustomForumTabsConfig(dialogId) && DialogConfig.isCustomForumTabsEnable(dialogId));
        disableItem.setChecked(DialogConfig.hasCustomForumTabsConfig(dialogId) && !DialogConfig.isCustomForumTabsEnable(dialogId));
    }
}
