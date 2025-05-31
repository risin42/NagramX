package tw.nekomimi.nekogram.menu.translate;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import java.util.Arrays;

import kotlin.Unit;
import tw.nekomimi.nekogram.parts.MessageTransKt;
import tw.nekomimi.nekogram.translate.Translator;

public class TranslatePopupWrapper {

    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;

    public TranslatePopupWrapper(ChatActivity fragment, PopupSwipeBackLayout swipeBackLayout, ActionBarMenuItem.ActionBarMenuItemDelegate delegate, Theme.ResourcesProvider resourcesProvider) {
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, getString(R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());

            ActionBarMenuItem.addColoredGap(windowLayout, resourcesProvider);
        }

        Arrays.stream(TranslateItem.ITEM_IDS).forEach(id -> {
            var item = ActionBarMenuItem.addItem(false, false, windowLayout, R.drawable.magic_stick_solar, TranslateItem.ITEM_TITLES.get(id), false, resourcesProvider);
            item.setOnClickListener(view -> delegate.onItemClick(id));
            item.setOnLongClickListener(view -> {
                Translator.showTargetLangSelect(view, (locale) -> {
                    if (fragment.scrimPopupWindow != null) {
                        fragment.scrimPopupWindow.dismiss();
                        fragment.scrimPopupWindow = null;
                        fragment.scrimPopupWindowItems = null;
                    }
                    MessageTransKt.translateMessages(fragment, locale, Translator.providerLLMTranslator);
                    return Unit.INSTANCE;
                });
                return true;
            });
        });
    }
}
