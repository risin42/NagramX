package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsService;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.List;

import cn.hutool.core.util.StrUtil;
import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.NekoXConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.transtale.Translator;
import tw.nekomimi.nekogram.transtale.TranslatorKt;
import tw.nekomimi.nekogram.ui.BottomBuilder;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
public class NekoGeneralSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;
    private ValueAnimator statusBarColorAnimator;
    private DrawerProfilePreviewCell profilePreviewCell;
    private ChatBlurAlphaSeekBar chatBlurAlphaSeekbar;
    private UndoView restartTooltip;

    private final CellGroup cellGroup = new CellGroup(this);

    private final AbstractConfigCell profilePreviewRow = cellGroup.appendCell(new ConfigCellDrawerProfilePreview());
    private final AbstractConfigCell largeAvatarInDrawerRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.largeAvatarInDrawer, getString("valuesLargeAvatarInDrawer"), null));
    private final AbstractConfigCell avatarBackgroundBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.avatarBackgroundBlur));
    private final AbstractConfigCell avatarBackgroundDarkenRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.avatarBackgroundDarken));
    private final AbstractConfigCell showSquareAvatarRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowSquareAvatar()));
    private final AbstractConfigCell hidePhoneRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hidePhone));
    private final AbstractConfigCell divider0 = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerTranslation = cellGroup.appendCell(new ConfigCellHeader(getString("Translate")));
    private final AbstractConfigCell translationProviderRow = cellGroup.appendCell(new ConfigCellCustom(NekoConfig.translationProvider.getKey(), CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell translatorModeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getTranslatorMode(),
        new String[]{
                getString(R.string.TranslatorModeAppend),
                getString(R.string.TranslatorModeInline),
                getString(R.string.TranslatorModePopup),
        }, null));
    private final AbstractConfigCell useTelegramUIAutoTranslateRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getTelegramUIAutoTranslate()));
    private final AbstractConfigCell translateToLangRow = cellGroup.appendCell(new ConfigCellCustom("TranslateToLang", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell translateInputToLangRow = cellGroup.appendCell(new ConfigCellCustom("TranslateInputToLang", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell preferredTranslateTargetLangRow = cellGroup.appendCell(
        new ConfigCellTextInput(getString(R.string.PreferredTranslateTargetLangName),
            NaConfig.INSTANCE.getPreferredTranslateTargetLang(),
            getString(R.string.PreferredTranslateTargetLangExample),
            null,
            (value) -> {
                NaConfig.INSTANCE.getPreferredTranslateTargetLang().setConfigString(value);
                NaConfig.INSTANCE.updatePreferredTranslateTargetLangList();
                return value;
            }
        )
    );
    private final AbstractConfigCell googleCloudTranslateKeyRow = cellGroup.appendCell(new ConfigCellTextDetail(NekoConfig.googleCloudTranslateKey, (view, position) -> {
        customDialog_BottomInputString(position, NekoConfig.googleCloudTranslateKey, getString("GoogleCloudTransKeyNotice"), "Key");
    }, getString(R.string.None)));
    // AI Translator
    private final AbstractConfigCell headerAITranslatorSettings = cellGroup.appendCell(new ConfigCellHeader(getString("AITranslatorSettings")));
    private final AbstractConfigCell llmApiKeyRow = cellGroup.appendCell(new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmApiKey(), (view, position) -> {
        customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmApiKey(), getString(R.string.LlmApiKeyNotice), "Key");
    }, getString(R.string.None)));
    private final AbstractConfigCell llmApiUrlRow = cellGroup.appendCell(new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmApiUrl(), (view, position) -> {
        customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmApiUrl(), getString(R.string.LlmApiUrlNotice), "e.g. https://api.openai.com/v1");
    }, getString(R.string.LlmApiUrlDefault)));
    private final AbstractConfigCell llmModelNameRow = cellGroup.appendCell(new ConfigCellTextDetail(NaConfig.INSTANCE.getLlmModelName(), (view, position) -> {
        customDialog_BottomInputString(position, NaConfig.INSTANCE.getLlmModelName(), getString(R.string.LlmModelNameNotice), "e.g. gpt-4o-mini");
    }, getString(R.string.LlmModelNameDefault)));
    private final AbstractConfigCell enableSeparateArticleTranslatorRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableSeparateArticleTranslator()));
    private final AbstractConfigCell articletranslationProviderRow = cellGroup.appendCell(new ConfigCellCustom("ArticleTranslationProvider", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerTranslation = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerMap = cellGroup.appendCell(new ConfigCellHeader("Map"));
    private final AbstractConfigCell useOSMDroidMapRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useOSMDroidMap));
    private final AbstractConfigCell mapDriftingFixForGoogleMapsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.mapDriftingFixForGoogleMaps));
    private final AbstractConfigCell mapPreviewRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.mapPreviewProvider,
            new String[]{
                    getString(R.string.MapPreviewProviderTelegram),
                    getString(R.string.MapPreviewProviderYandex),
                    getString(R.string.MapPreviewProviderNobody)
            }, null));
    private final AbstractConfigCell dividerMap = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerConnection = cellGroup.appendCell(new ConfigCellHeader(getString("Connection")));
    private final AbstractConfigCell useIPv6Row = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useIPv6));
    private final AbstractConfigCell useProxyItemRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useProxyItem));
    private final AbstractConfigCell hideProxyByDefaultRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideProxyByDefault));
    private final AbstractConfigCell useSystemDNSRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useSystemDNS));
    private final AbstractConfigCell disableProxyWhenVpnEnabledRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableProxyWhenVpnEnabled()));
    private final AbstractConfigCell customDoHRow = cellGroup.appendCell(new ConfigCellTextInput(null, NekoConfig.customDoH, "https://1.0.0.1/dns-query", null));
    private final AbstractConfigCell defaultHlsVideoQualityRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getDefaultHlsVideoQuality(),
        new String[]{
                getString(R.string.QualityAuto),
                getString(R.string.QualityOriginal),
                getString(R.string.Quality1440),
                getString(R.string.Quality1080),
                getString(R.string.Quality720),
                getString(R.string.Quality144),
        }, null));
    private final AbstractConfigCell dividerConnection = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerFolder = cellGroup.appendCell(new ConfigCellHeader(getString("Folder")));
    private final AbstractConfigCell hideAllTabRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideAllTab, getString("HideAllTabAbout")));
    private final AbstractConfigCell openArchiveOnPullRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.openArchiveOnPull));
    private final AbstractConfigCell ignoreMutedCountRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.ignoreMutedCount));
    private final AbstractConfigCell ignoreFolderCountRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getIgnoreFolderCount()));
    private final AbstractConfigCell tabsTitleTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.tabsTitleType,
            new String[]{
                    getString(R.string.TabTitleTypeText),
                    getString(R.string.TabTitleTypeIcon),
                    getString(R.string.TabTitleTypeMix)
            }, null));
    private final AbstractConfigCell hideFilterMuteAllRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideFilterMuteAll()));
    private final AbstractConfigCell dividerFolder = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell header_notification = cellGroup.appendCell(new ConfigCellHeader(getString("NekoGeneralNotification")));
    private final AbstractConfigCell disableNotificationBubblesRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableNotificationBubbles));
    private final AbstractConfigCell divider_notification = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell header4 = cellGroup.appendCell(new ConfigCellHeader(getString("DialogsSettings")));
    private final AbstractConfigCell sortMenuRow = cellGroup.appendCell(new ConfigCellSelectBox("SortMenu", null, null, () -> {
        if (getParentActivity() == null) return;
        showDialog(NekoChatSettingsActivity.showConfigMenuAlert(getParentActivity(), "SortMenu", new ArrayList<>() {{
            add(new ConfigCellTextCheck(NekoConfig.sortByUnread, null, getString(R.string.SortByUnread)));
            add(new ConfigCellTextCheck(NekoConfig.sortByUnmuted, null, getString(R.string.SortByUnmuted)));
            add(new ConfigCellTextCheck(NekoConfig.sortByUser, null, getString(R.string.SortByUser)));
            add(new ConfigCellTextCheck(NekoConfig.sortByContacts, null, getString(R.string.SortByContacts)));
        }}));
    }));
    private final AbstractConfigCell divider4 = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell header5 = cellGroup.appendCell(new ConfigCellHeader(getString("Appearance")));
    private final AbstractConfigCell typefaceRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.typeface));
    private final AbstractConfigCell transparentStatusBarRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.transparentStatusBar));
    private final AbstractConfigCell appBarShadowRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableAppBarShadow));
    private final AbstractConfigCell newYearRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.newYear));
    private final AbstractConfigCell alwaysShowDownloadIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getAlwaysShowDownloadIcon()));
    private final AbstractConfigCell actionBarDecorationRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.actionBarDecoration, new String[]{
            getString(R.string.DependsOnDate),
            getString(R.string.Snowflakes),
            getString(R.string.Fireworks),
            getString(R.string.DecorationNone),
    }, null));
    private final AbstractConfigCell iconDecorationRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getIconDecoration(), new String[]{
            getString(R.string.DependsOnDate),
            getString(R.string.Christmas),
            getString(R.string.Valentine),
            getString(R.string.HalloWeen),
            getString(R.string.DecorationNone),
    }, null));
    private final AbstractConfigCell chatDecorationRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getChatDecoration(), new String[]{
            getString(R.string.DependsOnDate),
            getString(R.string.Snowflakes),
            getString(R.string.DecorationNone),
    }, null));
    private final AbstractConfigCell notificationIconRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getNotificationIcon(), new String[]{
            getString(R.string.Official),
            getString(R.string.NagramX),
            getString(R.string.Nagram),
            getString(R.string.NekoX)
    }, null));
    private final AbstractConfigCell tabletModeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.tabletMode, new String[]{
            getString(R.string.TabletModeDefault),
            getString(R.string.Enable),
            getString(R.string.Disable)
    }, null));
    private final AbstractConfigCell forceBlurInChatRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.forceBlurInChat));
    private final AbstractConfigCell header_chatblur = cellGroup.appendCell(new ConfigCellHeader(getString("ChatBlurAlphaValue")));
    private final AbstractConfigCell chatBlurAlphaValueRow = cellGroup.appendCell(new ConfigCellCustom("ChatBlurAlphaValue", ConfigCellCustom.CUSTOM_ITEM_CharBlurAlpha, NekoConfig.forceBlurInChat.Bool()));
    private final AbstractConfigCell disableDialogsFloatingButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableDialogsFloatingButton()));
    private final AbstractConfigCell centerActionBarTitleRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getCenterActionBarTitle()));
    private final AbstractConfigCell hidePremiumSectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHidePremiumSection()));
    private final AbstractConfigCell hideHelpSectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideHelpSection()));
    private final AbstractConfigCell showStickersRowToplevelRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowStickersRowToplevel()));
    private final AbstractConfigCell divider5 = cellGroup.appendCell(new ConfigCellDivider());

    // Privacy
    private final AbstractConfigCell header6 = cellGroup.appendCell(new ConfigCellHeader(getString("PrivacyTitle")));
    private final AbstractConfigCell disableSystemAccountRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableSystemAccount));
    private final AbstractConfigCell doNotShareMyPhoneNumberRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDoNotShareMyPhoneNumber()));
    private final AbstractConfigCell disableSuggestionViewRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableSuggestionView()));
    private final AbstractConfigCell disableAutoWebLoginRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableAutoWebLogin()));
    private final AbstractConfigCell disableCrashlyticsCollectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableCrashlyticsCollection()));
    private final AbstractConfigCell divider6 = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell header7 = cellGroup.appendCell(new ConfigCellHeader(getString("General")));
    private final AbstractConfigCell customTitleRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomTitle(),
            getString(R.string.CustomTitleHint), null,
            (input) -> input.isEmpty() ? (String) NaConfig.INSTANCE.getCustomTitle().defaultValue : input));
    private final AbstractConfigCell customSavePathRow = cellGroup.appendCell(new ConfigCellTextInput(null, NekoConfig.customSavePath,
            getString(R.string.customSavePathHint), null,
            (input) -> input.matches("^[A-za-z0-9.]{1,255}$") || input.isEmpty() ? input : (String) NekoConfig.customSavePath.defaultValue));
    private final AbstractConfigCell customTitleUserNameRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getCustomTitleUserName()));
    private final AbstractConfigCell useSystemUnlockRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getUseSystemUnlock()));
    private final AbstractConfigCell disableUndoRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableUndo));
    private final AbstractConfigCell showIdAndDcRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showIdAndDc));
    private final AbstractConfigCell autoPauseVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.autoPauseVideo, getString("AutoPauseVideoAbout")));
    private final AbstractConfigCell disableNumberRoundingRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableNumberRounding, "4.8K -> 4777"));
    private final AbstractConfigCell nameOrderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.nameOrder, new String[]{
            getString(R.string.LastFirst),
            getString(R.string.FirstLast)
    }, null));
    private final AbstractConfigCell divider7 = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerPushService = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Notifications)));
    private final AbstractConfigCell pushServiceTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPushServiceType(), new String[]{
            getString(R.string.PushServiceTypeInApp),
            getString(R.string.PushServiceTypeFCM),
            getString(R.string.PushServiceTypeUnified),
            getString(R.string.PushServiceTypeMicroG),
    }, null));
    private final AbstractConfigCell pushServiceTypeInAppDialogRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getPushServiceTypeInAppDialog()));
    private final AbstractConfigCell pushServiceTypeUnifiedGatewayRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway(), null, null, (input) -> input.isEmpty() ? (String) NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway().defaultValue : input));
    private final AbstractConfigCell divider8 = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerAutoDownload = cellGroup.appendCell(new ConfigCellHeader(getString("AutoDownload")));
    private final AbstractConfigCell win32Row = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableAutoDownloadingWin32Executable));
    private final AbstractConfigCell archiveRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableAutoDownloadingArchive));
    private final AbstractConfigCell dividerAutoDownload = cellGroup.appendCell(new ConfigCellDivider());

    public NekoGeneralSettingsActivity() {
        if (!NaConfig.INSTANCE.getEnableSeparateArticleTranslator().Bool()) {
            cellGroup.rows.remove(articletranslationProviderRow);
        }

        addRowsToMap(cellGroup);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        updateRows();

        return true;
    }

    @SuppressLint("NewApi")
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getTitle());

        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        // Before listAdapter
        setCanNotChange();

        listView = new BlurredRecyclerView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        if (listView.getItemAnimator() != null) {
            ((DefaultItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);

        // Fragment: Set OnClick Callbacks
        listView.setOnItemClickListener((view, position, x, y) -> {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a instanceof ConfigCellTextCheck) {
                ((ConfigCellTextCheck) a).onClick((TextCheckCell) view);
            } else if (a instanceof ConfigCellSelectBox) {
                ((ConfigCellSelectBox) a).onClick(view);
            } else if (a instanceof ConfigCellTextInput) {
                ((ConfigCellTextInput) a).onClick();
            } else if (a instanceof ConfigCellTextDetail) {
                RecyclerListView.OnItemClickListener o = ((ConfigCellTextDetail) a).onItemClickListener;
                if (o != null) {
                    try {
                        o.onItemClick(view, position);
                    } catch (Exception e) {
                    }
                }
            } else if (a instanceof ConfigCellCustom) { // Custom OnClick
                if (position == cellGroup.rows.indexOf(translationProviderRow)) {
                    showProviderSelectionPopup(view, NekoConfig.translationProvider, () -> {
                        if (NekoConfig.translationProvider.Int() == Translator.providerTelegram) {
                            boolean isAutoTranslateEnabled = NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool();
                            boolean isRealPremium = UserConfig.getInstance(currentAccount).isRealPremium();
                            if (isAutoTranslateEnabled && !isRealPremium) {
                                NaConfig.INSTANCE.getTelegramUIAutoTranslate().setConfigBool(false);
                                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(useTelegramUIAutoTranslateRow));
                                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.TelegramUIAutoTranslateTips)).show();
                            }
                        }
                        listAdapter.notifyItemChanged(position);
                    });
                } else if (position == cellGroup.rows.indexOf(articletranslationProviderRow)) {
                    showProviderSelectionPopup(view, NaConfig.INSTANCE.getArticleTranslationProvider(), () -> {
                        listAdapter.notifyItemChanged(position);
                    });
                } else if (position == cellGroup.rows.indexOf(translateToLangRow) || position == cellGroup.rows.indexOf(translateInputToLangRow)) {
                    Translator.showTargetLangSelect(view, position == cellGroup.rows.indexOf(translateInputToLangRow), (locale) -> {
                        if (position == cellGroup.rows.indexOf(translateToLangRow)) {
                            NekoConfig.translateToLang.setConfigString(TranslatorKt.getLocale2code(locale));
                        } else {
                            NekoConfig.translateInputLang.setConfigString(TranslatorKt.getLocale2code(locale));
                        }
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                } else if (position == cellGroup.rows.indexOf(nameOrderRow)) {
                    LocaleController.getInstance().recreateFormatters();
                }
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                createLongClickDialog(context, NekoGeneralSettingsActivity.this, "general", position);
                return true;
            }
            return false;
        });

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NekoConfig.useIPv6.getKey())) {
                for (int a : SharedConfig.activeAccounts) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        ConnectionsManager.native_setIpStrategy(a, ConnectionsManager.getIpStrategy());
                    }
                }
            } else if (key.equals(NekoConfig.hidePhone.getKey())) {
                parentLayout.rebuildAllFragmentViews(false, false);
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(profilePreviewRow));
            } else if (key.equals(NekoConfig.transparentStatusBar.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.hideProxySponsorChannel.getKey())) {
                for (int a : SharedConfig.activeAccounts) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        MessagesController.getInstance(a).checkPromoInfo(true);
                    }
                }
            } else if (key.equals(NekoConfig.actionBarDecoration.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getNotificationIcon().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.tabletMode.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.newYear.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.disableSystemAccount.getKey())) {
                if ((boolean) newValue) {
                    getContactsController().deleteUnknownAppAccounts();
                } else {
                    for (int a : SharedConfig.activeAccounts) {
                        ContactsController.getInstance(a).checkAppAccount();
                    }
                }
            } else if (key.equals(NekoConfig.largeAvatarInDrawer.getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                TransitionManager.beginDelayedTransition(profilePreviewCell);
                setCanNotChange();
                listAdapter.notifyDataSetChanged();
            } else if (key.equals(NekoConfig.avatarBackgroundBlur.getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(profilePreviewRow));
            } else if (key.equals(NekoConfig.avatarBackgroundDarken.getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(profilePreviewRow));
            } else if (key.equals(NekoConfig.disableAppBarShadow.getKey())) {
                ActionBarLayout.headerShadowDrawable = (boolean) newValue ? null : parentLayout.getParentActivity().getResources().getDrawable(R.drawable.header_shadow).mutate();
                parentLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST | INavigationLayout.REBUILD_FLAG_REBUILD_ONLY_LAST);
            } else if (key.equals(NekoConfig.forceBlurInChat.getKey())) {
                boolean enabled = (Boolean) newValue;
                if (chatBlurAlphaSeekbar != null)
                    chatBlurAlphaSeekbar.setEnabled(enabled);
                ((ConfigCellCustom) chatBlurAlphaValueRow).enabled = enabled;
            } else if (key.equals(NekoConfig.useOSMDroidMap.getKey())) {
                boolean enabled = (Boolean) newValue;
                ((ConfigCellTextCheck) mapDriftingFixForGoogleMapsRow).setEnabled(!enabled);
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(mapDriftingFixForGoogleMapsRow));
            } else if (key.equals(NaConfig.INSTANCE.getPushServiceType().getKey())) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
                SharedPreferences.Editor editor = preferences.edit();
                boolean enabled;
                if (preferences.contains("pushService")) {
                    enabled = preferences.getBoolean("pushService", false);
                } else {
                    enabled = MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("keepAliveService", false);
                }
                if ((int) newValue == 0) {
                    NaConfig.INSTANCE.getPushServiceTypeInAppDialog().setConfigBool(true);
                    ((ConfigCellTextCheck) pushServiceTypeInAppDialogRow).setEnabledAndUpdateState(true);
                    if (!enabled) {
                        editor.putBoolean("pushService", !enabled);
                        editor.putBoolean("pushConnection", !enabled);
                        editor.apply();
                    }
                } else {
                    NaConfig.INSTANCE.getPushServiceTypeInAppDialog().setConfigBool(false);
                    ((ConfigCellTextCheck) pushServiceTypeInAppDialogRow).setEnabledAndUpdateState(false);
                    if (enabled) {
                        editor.putBoolean("pushService", !enabled);
                        editor.putBoolean("pushConnection", !enabled);
                        editor.apply();
                    }
                }
                ApplicationLoader.startPushService();
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(pushServiceTypeInAppDialogRow));
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPushServiceTypeInAppDialog().getKey())) {
                ApplicationLoader.applicationContext.stopService(new Intent(ApplicationLoader.applicationContext, NotificationsService.class));
                ApplicationLoader.startPushService();
            } else if (key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getEnableSeparateArticleTranslator().getKey())) {
                if ((boolean) newValue) {
                    if (!cellGroup.rows.contains(articletranslationProviderRow)) {
                        final int index = cellGroup.rows.indexOf(enableSeparateArticleTranslatorRow) + 1;
                        cellGroup.rows.add(index, articletranslationProviderRow);
                        listAdapter.notifyItemInserted(index);
                    }
                } else {
                    if (cellGroup.rows.contains(articletranslationProviderRow)) {
                        final int index = cellGroup.rows.indexOf(articletranslationProviderRow);
                        cellGroup.rows.remove(articletranslationProviderRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                }
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(enableSeparateArticleTranslatorRow));
            } else if (key.equals(NaConfig.INSTANCE.getDisableCrashlyticsCollection().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getCustomTitleUserName().getKey())) {
                boolean enabled = (Boolean) newValue;
                ((ConfigCellTextInput) customTitleRow).setEnabled(!enabled);
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(customTitleRow));
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPreferredTranslateTargetLang().getKey())) {
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(translateToLangRow));
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(translateInputToLangRow));
            } else if (key.equals(NaConfig.INSTANCE.getIgnoreFolderCount().getKey())) {
                setCanNotChange();
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(ignoreMutedCountRow));
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.ignoreMutedCount.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.hideProxyByDefault.getKey())) {
                setCanNotChange();
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(useProxyItemRow));
            } else if (key.equals(NekoConfig.hideAllTab.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getTelegramUIAutoTranslate().getKey())) {
                boolean enabled = (Boolean) newValue;
                if (enabled && NekoConfig.translationProvider.Int() == Translator.providerTelegram) {
                    boolean isAutoTranslateEnabled = NaConfig.INSTANCE.getTelegramUIAutoTranslate().Bool();
                    boolean isRealPremium = UserConfig.getInstance(currentAccount).isRealPremium();
                    if (isAutoTranslateEnabled && !isRealPremium) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.TelegramUIAutoTranslateTips)).show();
                    }
                }
            }
        };

        //Cells: Set ListAdapter
        cellGroup.setListAdapter(listView, listAdapter);

        restartTooltip = new UndoView(context);
        frameLayout.addView(restartTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        return fragmentView;
    }

    private class ConfigCellDrawerProfilePreview extends AbstractConfigCell {
        public int getType() {
            return ConfigCellCustom.CUSTOM_ITEM_ProfilePreview;
        }

        public boolean isEnabled() {
            return false;
        }

        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            DrawerProfilePreviewCell cell = (DrawerProfilePreviewCell) holder.itemView;
            cell.setUser(getUserConfig().getCurrentUser(), false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void updateRows() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public int getBaseGuid() {
        return 12000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_theme;
    }

    @Override
    public String getTitle() {
        return getString(R.string.General);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyCell.class, TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_avatar_actionBarIconBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        return themeDescriptions;
    }

    //impl ListAdapter
    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return cellGroup.rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                return a.isEnabled();
            }
            return true;
        }

        @Override
        public int getItemViewType(int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                return a.getType();
            }
            return CellGroup.ITEM_TYPE_TEXT_DETAIL;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                if (a instanceof ConfigCellCustom) {
                    // Custom binds
                    if (holder.itemView instanceof TextSettingsCell) {
                        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                        if (position == cellGroup.rows.indexOf(translationProviderRow)) {
                            textCell.setTextAndValue(getString(R.string.TranslationProvider), getProviderName(NekoConfig.translationProvider.Int()), true);
                        } else if (position == cellGroup.rows.indexOf(translateToLangRow)) {
                            textCell.setTextAndValue(getString(R.string.TransToLang), NekoXConfig.formatLang(NekoConfig.translateToLang.String()), true);
                        } else if (position == cellGroup.rows.indexOf(translateInputToLangRow)) {
                            textCell.setTextAndValue(getString(R.string.TransInputToLang), NekoXConfig.formatLang(NekoConfig.translateInputLang.String()), true);
                        } else if (position == cellGroup.rows.indexOf(articletranslationProviderRow)) {
                            textCell.setTextAndValue(getString(R.string.ArticleTranslationProvider), getProviderName(NaConfig.INSTANCE.getArticleTranslationProvider().Int()), true);
                        }
                    }
                } else {
                    // Default binds
                    a.onBindViewHolder(holder);
                }
                // Other things
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case CellGroup.ITEM_TYPE_DIVIDER:
                    view = new ShadowSectionCell(mContext);
                    break;
                case CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT_DETAIL:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case ConfigCellCustom.CUSTOM_ITEM_ProfilePreview:
                    view = profilePreviewCell = new DrawerProfilePreviewCell(mContext);
                    view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case ConfigCellCustom.CUSTOM_ITEM_CharBlurAlpha:
                    view = chatBlurAlphaSeekbar = new ChatBlurAlphaSeekBar(mContext);
                    chatBlurAlphaSeekbar.setEnabled(NekoConfig.forceBlurInChat.Bool());
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            //noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }

    private void setCanNotChange() {
        if (NekoConfig.useOSMDroidMap.Bool())
            ((ConfigCellTextCheck) mapDriftingFixForGoogleMapsRow).setEnabled(false);

        if (NaConfig.INSTANCE.getCustomTitleUserName().Bool())
            ((ConfigCellTextInput) customTitleRow).setEnabled(false);

        boolean enabled;

        enabled = NekoConfig.largeAvatarInDrawer.Int() > 0;
        ((ConfigCellTextCheck) avatarBackgroundBlurRow).setEnabled(enabled);
        ((ConfigCellTextCheck) avatarBackgroundDarkenRow).setEnabled(enabled);

        enabled = NaConfig.INSTANCE.getPushServiceType().Int() == 0;
        ((ConfigCellTextCheck) pushServiceTypeInAppDialogRow).setEnabled(enabled);

        enabled = NaConfig.INSTANCE.getIgnoreFolderCount().Bool();
        ((ConfigCellTextCheck) ignoreMutedCountRow).setEnabled(!enabled);

        enabled = NekoConfig.hideProxyByDefault.Bool();
        ((ConfigCellTextCheck) useProxyItemRow).setEnabled(!enabled);
    }

    //Custom dialogs
    private void customDialog_BottomInputString(int position, ConfigItem bind, String subtitle, String hint) {
        BottomBuilder builder = new BottomBuilder(getParentActivity());

        builder.addTitle(
                getString(bind.getKey()),
                subtitle
        );

        EditText keyField = builder.addEditText(hint);

        if (StrUtil.isNotBlank(bind.String())) {
            keyField.setText(bind.String());
        }

        builder.addCancelButton();

        builder.addOkButton((it) -> {

            String key = keyField.getText().toString();

            if (StrUtil.isBlank(key)) key = null;

            bind.setConfigString(key);

            listAdapter.notifyItemChanged(position);

            return Unit.INSTANCE;

        });

        builder.show();

        keyField.requestFocus();
        AndroidUtilities.showKeyboard(keyField);
    }

    private void showProviderSelectionPopup(View view, ConfigItem configItem, Runnable onSelected) {
        PopupBuilder builder = new PopupBuilder(view);

        List<ProviderInfo> filteredProviders = new ArrayList<>();
        for (ProviderInfo provider : ProviderInfo.PROVIDERS) {
            if (configItem == NaConfig.INSTANCE.getArticleTranslationProvider() && provider.providerConstant == Translator.providerLLMTranslator) {
                continue; // Exclude AI Translator for article translation provider
            }
            filteredProviders.add(provider);
        }

        String[] itemNames = new String[filteredProviders.size()];
        for (int i = 0; i < filteredProviders.size(); i++) {
            itemNames[i] = getString(filteredProviders.get(i).nameResId);
        }

        builder.setItems(itemNames, (i, __) -> {
            configItem.setConfigInt(filteredProviders.get(i).providerConstant);
            onSelected.run();
            return Unit.INSTANCE;
        });
        builder.show();
    }

    private String getProviderName(int providerConstant) {
        for (ProviderInfo info : ProviderInfo.PROVIDERS) {
            if (info.providerConstant == providerConstant) {
                return getString(info.nameResId);
            }
        }
        return "Unknown";
    }

    private class ChatBlurAlphaSeekBar extends FrameLayout {

        private final SeekBarView sizeBar;
        private final TextPaint textPaint;
        private boolean enabled = true;

        public ChatBlurAlphaSeekBar(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    NekoConfig.chatBlueAlphaValue.setConfigInt(Math.min(255, (int) (255 * progress)));
                    invalidate();
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {

                }
            });
            sizeBar.setOnTouchListener((v, event) -> !enabled);
            sizeBar.setProgress(NekoConfig.chatBlueAlphaValue.Int());
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText(String.valueOf(NekoConfig.chatBlueAlphaValue.Int()), getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            sizeBar.setProgress((NekoConfig.chatBlueAlphaValue.Int() / 255.0f));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            sizeBar.invalidate();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            this.enabled = enabled;
            sizeBar.setAlpha(enabled ? 1.0f : 0.5f);
            textPaint.setAlpha((int) ((enabled ? 1.0f : 0.3f) * 255));
            this.invalidate();
        }
    }

    private static class ProviderInfo {
        public final int providerConstant;
        public final int nameResId;

        public ProviderInfo(int providerConstant, int nameResId) {
            this.providerConstant = providerConstant;
            this.nameResId = nameResId;
        }

        public static final ProviderInfo[] PROVIDERS = {
                new ProviderInfo(Translator.providerGoogle, R.string.ProviderGoogleTranslate),
                new ProviderInfo(Translator.providerYandex, R.string.ProviderYandexTranslate),
                new ProviderInfo(Translator.providerLingo, R.string.ProviderLingocloud),
                new ProviderInfo(Translator.providerMicrosoft, R.string.ProviderMicrosoftTranslator),
                new ProviderInfo(Translator.providerRealMicrosoft, R.string.ProviderRealMicrosoftTranslator),
                new ProviderInfo(Translator.providerDeepL, R.string.ProviderDeepLTranslate),
                new ProviderInfo(Translator.providerTelegram, R.string.ProviderTelegramAPI),
                new ProviderInfo(Translator.providerTranSmart, R.string.ProviderTranSmartTranslate),
                new ProviderInfo(Translator.providerLLMTranslator, R.string.ProviderLLMTranslator),
        };
    }
}