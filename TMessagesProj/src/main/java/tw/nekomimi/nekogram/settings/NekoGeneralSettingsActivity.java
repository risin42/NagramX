package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Parcelable;
import android.text.TextPaint;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
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
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCheckBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.ui.cells.DrawerProfilePreviewCell;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings("unused")
public class NekoGeneralSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;
    private ValueAnimator statusBarColorAnimator;
    private DrawerProfilePreviewCell profilePreviewCell;
    private ChatBlurAlphaSeekBar chatBlurAlphaSeekbar;
    private UndoView restartTooltip;
    private Parcelable recyclerViewState = null;

    private final CellGroup cellGroup = new CellGroup(this);

    // Profile
    private final AbstractConfigCell profilePreviewRow = cellGroup.appendCell(new ConfigCellDrawerProfilePreview());
    private final AbstractConfigCell largeAvatarInDrawerRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.largeAvatarInDrawer, getString(R.string.valuesLargeAvatarInDrawer), null));
    private final AbstractConfigCell avatarBackgroundBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.avatarBackgroundBlur));
    private final AbstractConfigCell avatarBackgroundDarkenRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.avatarBackgroundDarken));
    private final AbstractConfigCell hidePhoneRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hidePhone));
    private final AbstractConfigCell dividerProfile = cellGroup.appendCell(new ConfigCellDivider());

    // Map
    private final AbstractConfigCell headerMap = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Map)));
    private final AbstractConfigCell useOSMDroidMapRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useOSMDroidMap));
    private final AbstractConfigCell mapDriftingFixForGoogleMapsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.mapDriftingFixForGoogleMaps));
    private final AbstractConfigCell mapPreviewRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.mapPreviewProvider, new String[]{
            getString(R.string.MapPreviewProviderTelegram),
            getString(R.string.MapPreviewProviderYandexNax),
            getString(R.string.MapPreviewProviderNobody)
    }, null));
    private final AbstractConfigCell dividerMap = cellGroup.appendCell(new ConfigCellDivider());

    // Connections
    private final AbstractConfigCell headerConnection = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Connection)));
    private final AbstractConfigCell useIPv6Row = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useIPv6));
    private final AbstractConfigCell useProxyItemRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useProxyItem));
    private final AbstractConfigCell hideProxyByDefaultRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideProxyByDefault));
    private final AbstractConfigCell disableProxyWhenVpnEnabledRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableProxyWhenVpnEnabled()));
    private final AbstractConfigCell defaultHlsVideoQualityRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getDefaultHlsVideoQuality(), new String[]{
            getString(R.string.QualityAuto),
            getString(R.string.QualityOriginal),
            getString(R.string.Quality1440),
            getString(R.string.Quality1080),
            getString(R.string.Quality720),
            getString(R.string.Quality144),
    }, null));
    private final AbstractConfigCell dnsTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.dnsType, new String[]{
            getString(R.string.MapPreviewProviderTelegram),
            getString(R.string.NagramX),
            getString(R.string.DnsTypeSystem),
            getString(R.string.CustomDoH),
    }, null));
    private final AbstractConfigCell customDoHRow = cellGroup.appendCell(new ConfigCellTextInput(null, NekoConfig.customDoH, "https://1.0.0.1/dns-query", null));
    private final AbstractConfigCell dividerConnection = cellGroup.appendCell(new ConfigCellDivider());

    // Folder
    private final AbstractConfigCell headerFolder = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Folder)));
    private final AbstractConfigCell hideAllTabRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideAllTab, getString(R.string.HideAllTabAbout)));
    private final AbstractConfigCell doNotUnarchiveBySwipeRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDoNotUnarchiveBySwipe()));
    private final AbstractConfigCell openArchiveOnPullRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.openArchiveOnPull));
    private final AbstractConfigCell hideArchiveRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideArchive()));
    private final AbstractConfigCell ignoreUnreadCountRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getIgnoreUnreadCount(), new String[]{
            getString(R.string.Disable),
            getString(R.string.FilterMuted),
            getString(R.string.FilterAllChatsShort)
    }, null));
    private final AbstractConfigCell tabsTitleTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.tabsTitleType, new String[]{
            getString(R.string.TabTitleTypeText),
            getString(R.string.TabTitleTypeIcon),
            getString(R.string.TabTitleTypeMix)
    }, null));
    private final AbstractConfigCell tabStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("TabStyle", NaConfig.INSTANCE.getTabStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.TabStylePure),
            getString(R.string.TabStylePills)
    }, null));
    private final AbstractConfigCell dividerFolder = cellGroup.appendCell(new ConfigCellDivider());

    // Dialogs
    private final AbstractConfigCell headerDialogs = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.DialogsSettings)));
    private final AbstractConfigCell sortMenuRow = cellGroup.appendCell(new ConfigCellSelectBox("SortMenu", null, null, () -> {
        if (getParentActivity() == null) return;
        showDialog(showConfigMenuAlert(getParentActivity(), "SortMenu", new ArrayList<>() {{
            add(new ConfigCellTextCheck(NekoConfig.sortByUnread, null, getString(R.string.SortByUnread)));
            add(new ConfigCellTextCheck(NekoConfig.sortByUnmuted, null, getString(R.string.SortByUnmuted)));
            add(new ConfigCellTextCheck(NekoConfig.sortByUser, null, getString(R.string.SortByUser)));
            add(new ConfigCellTextCheck(NekoConfig.sortByContacts, null, getString(R.string.SortByContacts)));
        }}));
    }));
    private final AbstractConfigCell mediaPreviewRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.mediaPreview));
    private final AbstractConfigCell userAvatarsInMessagePreviewRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getUserAvatarsInMessagePreview()));
    private final AbstractConfigCell disableDialogsFloatingButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableDialogsFloatingButton()));
    private final AbstractConfigCell disableBotOpenButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableBotOpenButton()));
    private final AbstractConfigCell dividerDialogs = cellGroup.appendCell(new ConfigCellDivider());

    // Appearance
    private final AbstractConfigCell headerAppearance = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Appearance)));
    private final AbstractConfigCell typefaceRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.typeface));
    private final AbstractConfigCell transparentStatusBarRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.transparentStatusBar));
    private final AbstractConfigCell appBarShadowRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableAppBarShadow));
    private final AbstractConfigCell hideDividers = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideDividers()));
    private final AbstractConfigCell newYearRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.newYear));
    private final AbstractConfigCell alwaysShowDownloadIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getAlwaysShowDownloadIcon()));
    private final AbstractConfigCell hidePremiumSectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHidePremiumSection()));
    private final AbstractConfigCell hideHelpSectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideHelpSection()));
    private final AbstractConfigCell showStickersInTopLevelRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowStickersRowToplevel()));
    private final AbstractConfigCell forceBlurInChatRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.forceBlurInChat));
    private final AbstractConfigCell headerChatBlur = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.ChatBlurAlphaValue)));
    private final AbstractConfigCell chatBlurAlphaValueRow = cellGroup.appendCell(new ConfigCellCustom("ChatBlurAlphaValue", ConfigCellCustom.CUSTOM_ITEM_CharBlurAlpha, NekoConfig.forceBlurInChat.Bool()));
    private final AbstractConfigCell iconReplacements = cellGroup.appendCell(new ConfigCellSelectBox("IconReplacements", NaConfig.INSTANCE.getIconReplacements(), new String[]{
            getString(R.string.Default),
            getString(R.string.IconReplacementSolar),
    }, null));
    private final AbstractConfigCell switchStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("SwitchStyle", NaConfig.INSTANCE.getSwitchStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleModern),
            getString(R.string.StyleMaterialDesign3)
    }, null));
    private final AbstractConfigCell sliderStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("SliderStyle", NaConfig.INSTANCE.getSliderStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleModern),
            getString(R.string.StyleMaterialDesign3)
    }, null));
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
            getString(R.string.MapPreviewProviderTelegram),
            getString(R.string.NagramX),
            getString(R.string.Nagram),
            getString(R.string.NekoX)
    }, null));
    private final AbstractConfigCell tabletModeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.tabletMode, new String[]{
            getString(R.string.TabletModeDefault),
            getString(R.string.Enable),
            getString(R.string.Disable)
    }, null));
    private final AbstractConfigCell centerActionBarTitleRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getCenterActionBarTitleType(), new String[]{
            getString(R.string.Disable),
            getString(R.string.Enable),
            getString(R.string.SettingsOnly),
            getString(R.string.ChatsOnly)
    }, null));
    private final AbstractConfigCell drawerElementsRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "DrawerElements", null, R.drawable.menu_newfilter, false, () ->
        showDialog(showConfigMenuWithIconAlert(this, R.string.DrawerElements, new ArrayList<>() {{
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemMyProfile(), getString(R.string.MyProfile), R.drawable.left_status_profile));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSetEmojiStatus(), getString(R.string.SetEmojiStatus), R.drawable.msg_status_set, true));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemArchivedChats(), getString(R.string.ArchivedChats), R.drawable.msg_archive, true));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNewGroup(), getString(R.string.NewGroup), R.drawable.msg_groups));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNewChannel(), getString(R.string.NewChannel), R.drawable.msg_channel));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemContacts(), getString(R.string.Contacts), R.drawable.msg_contacts));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemCalls(), getString(R.string.Calls), R.drawable.msg_calls));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSaved(), getString(R.string.SavedMessages), R.drawable.msg_saved));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSettings(), getString(R.string.Settings), R.drawable.msg_settings_old, true));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNSettings(), getString(R.string.NekoSettings), R.drawable.nagramx_outline));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemBrowser(), getString(R.string.InappBrowser), R.drawable.web_browser));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemQrLogin(), getString(R.string.ImportLogin), R.drawable.msg_qrcode, true));
            add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemRestartApp(), getString(R.string.RestartApp), R.drawable.msg_retry));
        }}))
    ));
    private final AbstractConfigCell dividerAppearance = cellGroup.appendCell(new ConfigCellDivider());

    // Privacy
    private final AbstractConfigCell headerPrivacy = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.PrivacyTitle)));
    private final AbstractConfigCell disableSystemAccountRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableSystemAccount));
    private final AbstractConfigCell doNotShareMyPhoneNumberRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDoNotShareMyPhoneNumber()));
    private final AbstractConfigCell disableSuggestionViewRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableSuggestionView()));
    private final AbstractConfigCell disableAutoWebLoginRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableAutoWebLogin()));
    private final AbstractConfigCell disableCrashlyticsCollectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableCrashlyticsCollection()));
    private final AbstractConfigCell dividerPrivacy = cellGroup.appendCell(new ConfigCellDivider());

    // General
    private final AbstractConfigCell headerGeneral = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.General)));
    private final AbstractConfigCell customTitleRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomTitle(),
            getString(R.string.CustomTitleHint), null,
            (input) -> input.isEmpty() ? (String) NaConfig.INSTANCE.getCustomTitle().defaultValue : input));
    private final AbstractConfigCell customSavePathRow = cellGroup.appendCell(new ConfigCellTextInput(null, NekoConfig.customSavePath,
            getString(R.string.customSavePathHint), null,
            (input) -> input.matches("^[A-za-z0-9.]{1,255}$") || input.isEmpty() ? input : (String) NekoConfig.customSavePath.defaultValue));
    private final AbstractConfigCell customTitleUserNameRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getCustomTitleUserName()));
    private final AbstractConfigCell folderNameAsTitleRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getFolderNameAsTitle()));
    private final AbstractConfigCell showIdAndDcRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showIdAndDc));
    private final AbstractConfigCell preferCommonGroupsTabRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getPreferCommonGroupsTab(), getString(R.string.PreferCommonGroupsTabNotice)));
    private final AbstractConfigCell autoPauseVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.autoPauseVideo, getString(R.string.AutoPauseVideoAbout)));
    private final AbstractConfigCell disableNumberRoundingRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableNumberRounding, "4.8K -> 4777"));
    private final AbstractConfigCell usePersianCalendarRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.usePersianCalendar, getString(R.string.UsePersianCalendarInfo)));
    private final AbstractConfigCell displayPersianCalendarByLatinRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.displayPersianCalendarByLatin));
    private final AbstractConfigCell nameOrderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.nameOrder, new String[]{
            getString(R.string.LastFirst),
            getString(R.string.FirstLast)
    }, null));
    private final AbstractConfigCell dividerGeneral = cellGroup.appendCell(new ConfigCellDivider());

    // Notifications
    private final AbstractConfigCell headerNotifications = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Notifications)));
    private final AbstractConfigCell pushServiceTypeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPushServiceType(), new String[]{
            getString(R.string.PushServiceTypeInApp),
            getString(R.string.PushServiceTypeFCM),
            getString(R.string.PushServiceTypeUnified),
            getString(R.string.PushServiceTypeMicroG),
    }, null));
    private final AbstractConfigCell pushServiceTypeInAppDialogRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getPushServiceTypeInAppDialog()));
    private final AbstractConfigCell disableNotificationBubblesRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableNotificationBubbles));
    private final AbstractConfigCell pushServiceTypeUnifiedGatewayRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway(), null, null, (input) -> input.isEmpty() ? (String) NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway().defaultValue : input));
    private final AbstractConfigCell dividerNotifications = cellGroup.appendCell(new ConfigCellDivider());

    // AutoDownload
    private final AbstractConfigCell headerAutoDownload = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.AutoDownload)));
    private final AbstractConfigCell win32Row = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableAutoDownloadingWin32Executable));
    private final AbstractConfigCell archiveRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableAutoDownloadingArchive));
    private final AbstractConfigCell dividerAutoDownload = cellGroup.appendCell(new ConfigCellDivider());

    public NekoGeneralSettingsActivity() {
        if (!NaConfig.INSTANCE.getCenterActionBarTitle().Bool()) {
            NaConfig.INSTANCE.getCenterActionBarTitleType().setConfigInt(0);
        }
        if (!shouldShowPersian()) {
            cellGroup.rows.remove(usePersianCalendarRow);
            cellGroup.rows.remove(displayPersianCalendarByLatinRow);
        }

        checkProfileConfigCellRows();
        checkCustomDoHCellRows();
        addRowsToMap(cellGroup);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        updateRows();

        return true;
    }

    @SuppressLint({"NewApi", "NotifyDataSetChanged", "UseCompatLoadingForDrawables"})
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

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setChangeDuration(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);

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
                    } catch (Exception ignored) {}
                }
            } else if (a instanceof ConfigCellCustom) { // Custom OnClick
                if (position == cellGroup.rows.indexOf(nameOrderRow)) {
                    LocaleController.getInstance().recreateFormatters();
                }
            } else if (a instanceof ConfigCellTextCheckIcon) {
                ((ConfigCellTextCheckIcon) a).onClick();
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (cellGroup.rows.get(position) instanceof ConfigCellCheckBox) {
                return true;
            }
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                createLongClickDialog(context, NekoGeneralSettingsActivity.this, "general", position);
                return true;
            }
            return false;
        });

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NekoConfig.hidePhone.getKey())) {
                parentLayout.rebuildFragments(0);
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(profilePreviewRow));
            } else if (key.equals(NekoConfig.transparentStatusBar.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
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
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        ContactsController.getInstance(a).checkAppAccount();
                    }
                }
            } else if (key.equals(NekoConfig.largeAvatarInDrawer.getKey())) {
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                TransitionManager.beginDelayedTransition(profilePreviewCell);
                checkProfileConfigCellRows();
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
                    enabled = preferences.getBoolean("pushService", true);
                } else {
                    enabled = MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("keepAliveService", false);
                }
                if ((int) newValue == 0) {
                    NaConfig.INSTANCE.getPushServiceTypeInAppDialog().setConfigBool(true);
                    ((ConfigCellTextCheck) pushServiceTypeInAppDialogRow).setEnabledAndUpdateState(true);
                    if (!enabled) {
                        editor.putBoolean("pushService", true);
                        editor.putBoolean("pushConnection", true);
                        editor.apply();
                    }
                    ApplicationLoader.startPushService();
                } else {
                    NaConfig.INSTANCE.getPushServiceTypeInAppDialog().setConfigBool(false);
                    ((ConfigCellTextCheck) pushServiceTypeInAppDialogRow).setEnabledAndUpdateState(false);
                    AndroidUtilities.runOnUIThread(() -> context.stopService(new Intent(context, NotificationsService.class)));
                }
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(pushServiceTypeInAppDialogRow));
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPushServiceTypeInAppDialog().getKey())) {
                ApplicationLoader.applicationContext.stopService(new Intent(ApplicationLoader.applicationContext, NotificationsService.class));
                ApplicationLoader.startPushService();
            } else if (key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedGateway().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getDisableCrashlyticsCollection().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getCustomTitleUserName().getKey())) {
                boolean enabled = (Boolean) newValue;
                ((ConfigCellTextInput) customTitleRow).setEnabled(!enabled);
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(customTitleRow));
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getIgnoreUnreadCount().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.useProxyItem.getKey())) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NekoConfig.hideProxyByDefault.getKey())) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NekoConfig.hideAllTab.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getCenterActionBarTitleType().getKey())) {
                int value = (int) newValue;
                NaConfig.INSTANCE.getCenterActionBarTitle().setConfigBool(value != 0);
            } else if (key.equals(NaConfig.INSTANCE.getHideArchive().getKey())) {
                setCanNotChange();
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(openArchiveOnPullRow));
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getDisableBotOpenButton().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHideDividers().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getIconReplacements().getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSwitchStyle().getKey()) || key.equals(NaConfig.INSTANCE.getSliderStyle().getKey())) {
                if (listView.getLayoutManager() != null) {
                    recyclerViewState = listView.getLayoutManager().onSaveInstanceState();
                    parentLayout.rebuildFragments(INavigationLayout.REBUILD_FLAG_REBUILD_LAST);
                    listView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
                }
            } else if (key.equals(NekoConfig.usePersianCalendar.getKey())) {
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.dnsType.getKey())) {
                checkCustomDoHCellRows();
                restartTooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
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

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
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

        private final Context mContext;

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
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                a.onBindViewHolder(holder);
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
                case CellGroup.ITEM_TYPE_TEXT_CHECK_ICON:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
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

        enabled = NaConfig.INSTANCE.getPushServiceType().Int() == 0;
        ((ConfigCellTextCheck) pushServiceTypeInAppDialogRow).setEnabled(enabled);

        enabled = NaConfig.INSTANCE.getHideArchive().Bool();
        ((ConfigCellTextCheck) openArchiveOnPullRow).setEnabled(!enabled);
    }

    private static class ChatBlurAlphaSeekBar extends FrameLayout {

        private final SeekBarView sizeBar;
        private final TextPaint textPaint;
        private boolean enabled = true;

        @SuppressLint("ClickableViewAccessibility")
        public ChatBlurAlphaSeekBar(Context context) {
            super(context);

            setWillNotDraw(false);
            setClickable(true);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(256);
            sizeBar.setDelegate((stop, progress) -> {
                NekoConfig.chatBlueAlphaValue.setConfigInt(Math.min(255, (int) (255 * progress)));
                invalidate();
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

    private void checkProfileConfigCellRows() {
        int backgroundType = NekoConfig.largeAvatarInDrawer.Int();
        boolean useAvatar = backgroundType == NekoConfig.DRAWER_BACKGROUND_AVATAR || backgroundType == NekoConfig.DRAWER_BACKGROUND_BIG_AVATAR;
        if (listAdapter == null) {
            if (!useAvatar) {
                cellGroup.rows.remove(avatarBackgroundBlurRow);
                cellGroup.rows.remove(avatarBackgroundDarkenRow);
            }
            return;
        }
        if (useAvatar) {
            final int index = cellGroup.rows.indexOf(largeAvatarInDrawerRow);
            if (!cellGroup.rows.contains(avatarBackgroundBlurRow)) {
                cellGroup.rows.add(index + 1, avatarBackgroundBlurRow);
                listAdapter.notifyItemInserted(index + 1);
            }
            if (!cellGroup.rows.contains(avatarBackgroundDarkenRow)) {
                cellGroup.rows.add(index + 2, avatarBackgroundDarkenRow);
                listAdapter.notifyItemInserted(index + 2);
            }
        } else {
            int blurRowIndex = cellGroup.rows.indexOf(avatarBackgroundBlurRow);
            if (blurRowIndex != -1) {
                cellGroup.rows.remove(avatarBackgroundBlurRow);
                listAdapter.notifyItemRemoved(blurRowIndex);
            }
            int darkenRowIndex = cellGroup.rows.indexOf(avatarBackgroundDarkenRow);
            if (darkenRowIndex != -1) {
                cellGroup.rows.remove(avatarBackgroundDarkenRow);
                listAdapter.notifyItemRemoved(darkenRowIndex);
            }
        }
        listAdapter.notifyItemChanged(cellGroup.rows.indexOf(profilePreviewRow));
    }

    private void checkCustomDoHCellRows() {
        boolean useDoH = NekoConfig.dnsType.Int() == NekoConfig.DNS_TYPE_CUSTOM_DOH;
        if (listAdapter == null) {
            if (!useDoH) {
                cellGroup.rows.remove(customDoHRow);
            }
            return;
        }
        if (useDoH) {
            final int index = cellGroup.rows.indexOf(dnsTypeRow);
            if (!cellGroup.rows.contains(customDoHRow)) {
                cellGroup.rows.add(index + 1, customDoHRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int customDoHRowIndex = cellGroup.rows.indexOf(customDoHRow);
            if (customDoHRowIndex != -1) {
                cellGroup.rows.remove(customDoHRow);
                listAdapter.notifyItemRemoved(customDoHRowIndex);
            }
        }
    }

    private boolean shouldShowPersian() {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        return locale != null && locale.getLanguage().equals("fa");
    }
}