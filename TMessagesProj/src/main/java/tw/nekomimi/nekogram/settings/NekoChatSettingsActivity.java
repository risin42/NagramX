package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;
import static tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity.PARTIAL;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCheckBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck2;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.helpers.TranscribeHelper;
import tw.nekomimi.nekogram.helpers.remote.EmojiHelper;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.EmojiSetCell;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.ui.cells.StickerSizePreviewMessagesCell;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.helper.DoubleTap;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class NekoChatSettingsActivity extends BaseNekoXSettingsActivity implements NotificationCenter.NotificationCenterDelegate, EmojiHelper.EmojiPacksLoadedListener {

    private final CellGroup cellGroup = new CellGroup(this);

    // Sticker Size
    private final AbstractConfigCell headerStickerSize = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.StickerSize)));
    private final AbstractConfigCell stickerSizeRow = cellGroup.appendCell(new ConfigCellCustom("StickerSize", ConfigCellCustom.CUSTOM_ITEM_StickerSize, false));
    private final AbstractConfigCell showTimeHintRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTimeHint()));
    private final AbstractConfigCell hideTimeForStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideTimeForSticker));
    private final AbstractConfigCell disableReplyBackgroundRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getMessageColoredBackground()));
    private final AbstractConfigCell dividerStickerSize = cellGroup.appendCell(new ConfigCellDivider());

    // Chats
    private final AbstractConfigCell headerChats = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Chat)));
    private final AbstractConfigCell emojiSetsRow = cellGroup.appendCell(new ConfigCellCustom("EmojiSets", ConfigCellCustom.CUSTOM_ITEM_EmojiSet, true));
    private final AbstractConfigCell premiumElementsToggleRow = cellGroup.appendCell(new ConfigCellTextCheck2("PremiumElements", getString(R.string.PremiumElements), new ArrayList<>() {{
        add(new ConfigCellCheckBox(NaConfig.INSTANCE.getPremiumItemEmojiStatus()));
        add(new ConfigCellCheckBox(NaConfig.INSTANCE.getPremiumItemEmojiInReplies()));
        add(new ConfigCellCheckBox(NaConfig.INSTANCE.getPremiumItemCustomColorInReplies()));
        add(new ConfigCellCheckBox(NaConfig.INSTANCE.getPremiumItemCustomWallpaper()));
        add(new ConfigCellCheckBox(NaConfig.INSTANCE.getPremiumItemVideoAvatar()));
        add(new ConfigCellCheckBox(NaConfig.INSTANCE.getPremiumItemStarInReactions()));
        add(new ConfigCellCheckBox(NaConfig.INSTANCE.getPremiumItemStickerEffects()));
        add(new ConfigCellCheckBox(NaConfig.INSTANCE.getPremiumItemBoosts()));
    }}, null));
    ArrayList<ConfigCellCheckBox> premiumElementsRows = ((ConfigCellTextCheck2) premiumElementsToggleRow).getCheckBox();
    private final AbstractConfigCell unreadBadgeOnBackButton = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.unreadBadgeOnBackButton));
    private final AbstractConfigCell sendCommentAfterForwardRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.sendCommentAfterForward));
    private final AbstractConfigCell useChatAttachMediaMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.useChatAttachMediaMenu, getString(R.string.UseChatAttachEnterMenuNotice)));
    private final AbstractConfigCell fixLinkPreviewRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getFixLinkPreview(), "x.com -> fixupx.com"));
    private final AbstractConfigCell disableLinkPreviewByDefaultRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableLinkPreviewByDefault));
    private final AbstractConfigCell deleteChatForBothSidesRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDeleteChatForBothSides()));
    private final AbstractConfigCell showMessageIDRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowMessageID()));
    private final AbstractConfigCell showSeconds = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showSeconds));
    private final AbstractConfigCell useEditedIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getUseEditedIcon()));
    private final AbstractConfigCell customEditedMessageRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomEditedMessage(), "", null));
    private final AbstractConfigCell dateOfForwardMsgRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDateOfForwardedMsg()));
    private final AbstractConfigCell showFullAboutRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowFullAbout()));
    private final AbstractConfigCell alwaysSaveChatOffsetRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getAlwaysSaveChatOffset()));
    private final AbstractConfigCell disableTrendingRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableTrending));
    private final AbstractConfigCell disableZalgoSymbolsRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getZalgoFilter(), getString(R.string.ZalgoFilterNotice)));
    private final AbstractConfigCell showOnlineStatusRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowOnlineStatus(), getString(R.string.ShowOnlineStatusNotice)));
    private final AbstractConfigCell coloredAdminTitleRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getColoredAdminTitle()));
    private final AbstractConfigCell leftButtonActionRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getLeftBottomButton(), new String[]{
            getString(R.string.NoQuoteForward),
            getString(R.string.Reply),
            getString(R.string.AddToSavedMessages),
            getString(R.string.DirectShare),
    }, null));
    private final AbstractConfigCell markdownParserRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getMarkdownParser(), new String[]{
            getString(R.string.Official),
            "Nekogram",
    }, null));
    private final AbstractConfigCell dividerChats = cellGroup.appendCell(new ConfigCellDivider());

    // Double Tap
    private final AbstractConfigCell headerDoubleTap = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.DoubleTapAction)));
    private final AbstractConfigCell doubleTapActionRow = cellGroup.appendCell(new ConfigCellCustom("DoubleTapIncoming", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell doubleTapActionOutRow = cellGroup.appendCell(new ConfigCellCustom("DoubleTapOutgoing", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerDoubleTap = cellGroup.appendCell(new ConfigCellDivider());

    // Camera
    private final AbstractConfigCell headerCamera = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.CameraSettings)));
    private final AbstractConfigCell disableInstantCameraRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableInstantCamera));
    private final AbstractConfigCell cameraInVideoMessages = cellGroup.appendCell(new ConfigCellSelectBox("CameraInVideoMessages", NaConfig.INSTANCE.getCameraInVideoMessages(), new String[]{
            getString(R.string.CameraInVideoMessagesFront),
            getString(R.string.CameraInVideoMessagesRear),
            getString(R.string.CameraInVideoMessagesAsk)
    }, null));
    private final AbstractConfigCell dividerCamera = cellGroup.appendCell(new ConfigCellDivider());

    // Media
    private final AbstractConfigCell headerMedia = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MediaSettings)));
    private final AbstractConfigCell showSmallGifRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowSmallGIF()));
    private final AbstractConfigCell takeGIFasVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.takeGIFasVideo));
    private final AbstractConfigCell autoInsertGIFCaptionRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getAutoInsertGIFCaption(), getString(R.string.AutoInsertGIFCaptionNotice)));
    private final AbstractConfigCell autoPauseVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.autoPauseVideo, getString(R.string.AutoPauseVideoAbout)));
    private final AbstractConfigCell disablePreviewVideoSoundShortcutRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisablePreviewVideoSoundShortcut(), getString(R.string.DisablePreviewVideoSoundShortcutNotice)));
    private final AbstractConfigCell dontAutoPlayNextVoiceRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDontAutoPlayNextVoice()));
    private final AbstractConfigCell showSpoilersDirectlyRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showSpoilersDirectly));
    private final AbstractConfigCell sendHighQualityPhotoRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSendHighQualityPhoto()));
    private final AbstractConfigCell dividerMedia = cellGroup.appendCell(new ConfigCellDivider());

    // Stickers
    private final AbstractConfigCell headerSticker = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.StickerSettings)));
    private final AbstractConfigCell dontSendGreetingStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.dontSendGreetingSticker));
    private final AbstractConfigCell hideGroupStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideGroupSticker));
    private final AbstractConfigCell maxRecentStickerCountRow = cellGroup.appendCell(new ConfigCellCustom("maxRecentStickerCount", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerSticker = cellGroup.appendCell(new ConfigCellDivider());

    // Transcribe
    private final AbstractConfigCell headerTranscribe = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.PremiumPreviewVoiceToText)));
    private final AbstractConfigCell transcribeProviderRow = cellGroup.appendCell(new ConfigCellSelectBox("TranscribeProviderShort", NaConfig.INSTANCE.getTranscribeProvider(), new String[]{
            getString(R.string.TranscribeProviderAuto),
            getString(R.string.TelegramPremium),
            getString(R.string.TranscribeProviderWorkersAI),
            getString(R.string.TranscribeProviderGemini),
            getString(R.string.TranscribeProviderOpenAI),
    }, null));
    private final AbstractConfigCell transcribeProviderCfCredentialsRow = cellGroup.appendCell(new ConfigCellCustom("CloudflareCredentials", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell transcribeProviderGeminiApiKeyRow = cellGroup.appendCell(new ConfigCellCustom("LlmProviderGeminiKey", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell transcribeProviderOpenAiRow = cellGroup.appendCell(new ConfigCellCustom("TranscribeProviderOpenAI", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell dividerTranscribe = cellGroup.appendCell(new ConfigCellDivider());

    // MenuAndButtons
    private final AbstractConfigCell headerMenuAndButtons = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MenuAndButtons)));
    private final AbstractConfigCell chatMenuRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "ChatMenu", null, R.drawable.menu_chats, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.ChatMenu, new ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShortcutsAdministrators(), getString(R.string.ChannelAdministrators), R.drawable.msg_admins));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShortcutsRecentActions(), getString(R.string.EventLog), R.drawable.msg_log));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShortcutsStatistics(), getString(R.string.Statistics), R.drawable.msg_stats));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShortcutsPermissions(), getString(R.string.ChannelPermissions), R.drawable.msg_permissions));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShortcutsMembers(), getString(R.string.GroupMembers), R.drawable.msg_groups, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getChatMenuItemBoostGroup(), getString(R.string.BoostingBoostGroupMenu), R.drawable.boost_channel_solar));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getChatMenuItemLinkedChat(), getString(R.string.LinkedGroupChat), R.drawable.msg_discussion));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getChatMenuItemToBeginning(), getString(R.string.ToTheBeginning), R.drawable.ic_upward));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getChatMenuItemGoToMessage(), getString(R.string.ToTheMessage), R.drawable.msg_go_up));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getChatMenuItemHideTitle(), getString(R.string.HideTitle), R.drawable.hide_title));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getChatMenuItemViewDeleted(), getString(R.string.ViewDeleted), R.drawable.msg_view_file));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getChatMenuItemClearDeleted(), getString(R.string.ClearDeleted), R.drawable.msg_clear));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getChatMenuItemDeleteOwnMessages(), getString(R.string.DeleteAllFromSelf), R.drawable.msg_delete));
            }}))
    ));
    private final AbstractConfigCell messageMenuRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "MessageMenu", null, R.drawable.msg_list, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.MessageMenu, new ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowReactions(), R.drawable.msg_reactions2));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowReplyInPrivate(), R.drawable.menu_reply));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowCopyLink(), R.drawable.msg_link));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowCopyFrame(), getString(R.string.CopyVideoFrame), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowCopyPhoto(), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowCopyAsSticker(), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowAddToStickers(), R.drawable.msg_sticker));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowAddToFavorites(), R.drawable.msg_fave));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowNoQuoteForward(), R.drawable.msg_forward_noquote));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowSetReminder(), R.drawable.msg_calendar2));
                add(new ConfigCellTextCheckIcon(NekoConfig.showAddToSavedMessages, getString(R.string.AddToSavedMessages), R.drawable.msg_saved));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowAddToBookmark(), getString(R.string.AddBookmark), R.drawable.msg_fave));
                add(new ConfigCellTextCheckIcon(NekoConfig.showRepeat, getString(R.string.Repeat), R.drawable.msg_repeat));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowRepeatAsCopy(), R.drawable.msg_repeat));
                add(new ConfigCellTextCheckIcon(NekoConfig.showDeleteDownloadedFile, getString(R.string.DeleteDownloadedFile), R.drawable.msg_clear));
                add(new ConfigCellTextCheckIcon(NekoConfig.showViewHistory, getString(R.string.ViewHistory), R.drawable.menu_recent));
                add(new ConfigCellTextCheckIcon(NekoConfig.showTranslate, getString(R.string.Translate), R.drawable.msg_translate));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getShowTranslateMessageLLM(), R.drawable.magic_stick_solar));
                add(new ConfigCellTextCheckIcon(NekoConfig.showShareMessages, getString(R.string.ShareMessages), R.drawable.msg_shareout));
                add(new ConfigCellTextCheckIcon(NekoConfig.showMessageHide, getString(R.string.Hide), R.drawable.msg_disable));
                add(new ConfigCellTextCheckIcon(NekoConfig.showReport, getString(R.string.ReportChat), R.drawable.msg_report));
                add(new ConfigCellTextCheckIcon(NekoConfig.showAdminActions, getString(R.string.EditAdminRights), R.drawable.profile_admin));
                add(new ConfigCellTextCheckIcon(NekoConfig.showChangePermissions, getString(R.string.ChangePermissions), R.drawable.msg_permissions));
                add(new ConfigCellTextCheckIcon(NekoConfig.showMessageDetails, getString(R.string.MessageDetails), R.drawable.msg_info));
            }}))
    ));
    private final AbstractConfigCell mediaViewerMenuRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "MediaViewerMenu", null, R.drawable.msg_photos, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.MediaViewerMenu, new ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getMediaViewerMenuItemForward(), getString(R.string.Forward), R.drawable.msg_forward));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getMediaViewerMenuItemNoQuoteForward(), getString(R.string.NoQuoteForward), R.drawable.msg_forward_noquote));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getMediaViewerMenuItemCopyFrame(), getString(R.string.CopyVideoFrame), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getMediaViewerMenuItemCopyPhoto(), getString(R.string.CopyPhoto), R.drawable.msg_copy_photo));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getMediaViewerMenuItemSetProfilePhoto(), getString(R.string.SetProfilePhoto), R.drawable.msg_openprofile));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getMediaViewerMenuItemScanQRCode(), getString(R.string.ScanQRCode), R.drawable.msg_qrcode));
            }}))
    ));
    private final AbstractConfigCell actionBarButtonRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "ActionBarButtons", null, R.drawable.msg_media, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.ActionBarButtons, new ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getActionBarButtonReply(), R.drawable.menu_reply));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getActionBarButtonEdit(), R.drawable.msg_edit));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getActionBarButtonSelectBetween(), R.drawable.ic_select_between));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getActionBarButtonCopy(), R.drawable.msg_copy));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getActionBarButtonForward(), R.drawable.msg_forward_noquote));
            }}))
    ));
    private final AbstractConfigCell defaultDeleteMenuRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "DefaultDeleteMenu", null, R.drawable.msg_admins, false, () -> {
        if (getParentActivity() == null) return;
        showDialog(showConfigMenuAlert(getParentActivity(), NaConfig.INSTANCE.getDefaultDeleteMenu().getKey(), new ArrayList<>() {{
            add(new ConfigCellTextCheck(NaConfig.INSTANCE.getDefaultDeleteMenuBanUsers()));
            add(new ConfigCellTextCheck(NaConfig.INSTANCE.getDefaultDeleteMenReportSpam()));
            add(new ConfigCellTextCheck(NaConfig.INSTANCE.getDefaultDeleteMenuDeleteAll()));
            add(new ConfigCellTextCheck(NaConfig.INSTANCE.getDefaultDeleteMenuDoActionsInCommonGroups()));
        }}));
    }));

    @SuppressLint("NotifyDataSetChanged")
    private final AbstractConfigCell textStyleRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "TextStyle", null, R.drawable.msg_photo_text_framed3, false, () -> {
        if (getParentActivity() == null) return;
        Context ctx = getParentActivity();
        record Item(String key, CharSequence title, ConfigCellTextCheck bind) {
        }
        ArrayList<Item> items = new ArrayList<>();
        SpannableStringBuilder sb;
        items.add(new Item("translate", getString(R.string.TranslateMessage), new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextTranslate(), null, getString(R.string.TranslateMessage))));
        sb = new SpannableStringBuilder(getString(R.string.Bold));
        sb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        items.add(new Item("bold", sb, new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextBold(), null, sb)));
        sb = new SpannableStringBuilder(getString(R.string.Italic));
        sb.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        items.add(new Item("italic", sb, new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextItalic(), null, sb)));
        sb = new SpannableStringBuilder(getString(R.string.Mono));
        sb.setSpan(new TypefaceSpan(Typeface.MONOSPACE), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        items.add(new Item("mono", sb, new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextMono(), null, sb)));
        sb = new SpannableStringBuilder(getString(R.string.MonoCode));
        sb.setSpan(new TypefaceSpan(Typeface.MONOSPACE), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        items.add(new Item("code", sb, new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextMonoCode(), null, sb)));
        sb = new SpannableStringBuilder(getString(R.string.Strike));
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
        sb.setSpan(new TextStyleSpan(run), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        items.add(new Item("strike", sb, new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextStrikethrough(), null, sb)));
        sb = new SpannableStringBuilder(getString(R.string.Underline));
        run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
        sb.setSpan(new TextStyleSpan(run), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        items.add(new Item("underline", sb, new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextUnderline(), null, sb)));
        items.add(new Item("quote", getString(R.string.Quote), new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextQuote(), null, getString(R.string.Quote))));
        items.add(new Item("spoiler", getString(R.string.Spoiler), new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextSpoiler(), null, getString(R.string.Spoiler))));
        items.add(new Item("link", getString(R.string.CreateLink), new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextCreateLink(), null, getString(R.string.CreateLink))));
        items.add(new Item("mention", getString(R.string.CreateMention), new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextCreateMention(), null, getString(R.string.CreateMention))));
        items.add(new Item("regular", getString(R.string.Regular), new ConfigCellTextCheck(NaConfig.INSTANCE.getShowTextRegular(), null, getString(R.string.Regular))));

        // recover saved order
        String orderStr = NaConfig.INSTANCE.getTextStyleOrder().String();
        ArrayList<Item> ordered = new ArrayList<>();
        if (!TextUtils.isEmpty(orderStr)) {
            String[] keys = orderStr.split(",");
            for (String k : keys) {
                for (Item it : items) {
                    if (it.key.equals(k) && !ordered.contains(it)) ordered.add(it);
                }
            }
            for (Item it : items) {
                if (!ordered.contains(it)) ordered.add(it);
            }
        } else {
            ordered.addAll(items);
        }

        RecyclerView rv = new RecyclerView(ctx);
        rv.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, false));
        class VH extends RecyclerView.ViewHolder {
            final TextCheckCell cell;

            VH(TextCheckCell c) {
                super(c);
                cell = c;
            }
        }

        class Adapter extends RecyclerView.Adapter<VH> {
            final ArrayList<Item> data;

            Adapter(ArrayList<Item> d) {
                data = d;
            }

            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextCheckCell c = new TextCheckCell(ctx);
                c.setBackground(Theme.getSelectorDrawable(false));
                return new VH(c);
            }

            @Override
            public void onBindViewHolder(VH holder, int position) {
                Item it = data.get(position);
                holder.cell.setTextAndCheck(it.title, it.bind.getBindConfig().Bool(), false);
                holder.cell.setOnClickListener(v -> {
                    int pos = holder.getAdapterPosition();
                    if (pos < 0 || pos >= data.size()) {
                        return;
                    }
                    Item ii = data.get(pos);
                    ii.bind.getBindConfig().toggleConfigBool();
                    holder.cell.setChecked(ii.bind.getBindConfig().Bool());
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                });
            }

            @Override
            public int getItemCount() {
                return data.size();
            }
        }
        Adapter adapter = new Adapter(ordered);
        rv.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                Item it = ordered.remove(from);
                ordered.add(to, it);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        helper.attachToRecyclerView(rv);

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(getString(R.string.TextStyle));
        builder.setView(rv);
        builder.setPositiveButton(getString(R.string.OK), (d, which) -> {
            // save order
            StringBuilder sbOrder = new StringBuilder();
            for (int i = 0; i < ordered.size(); i++) {
                if (i > 0) sbOrder.append(",");
                sbOrder.append(ordered.get(i).key);
            }
            NaConfig.INSTANCE.getTextStyleOrder().setConfigString(sbOrder.toString());
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setNeutralButton(getString(R.string.Reset), (d, which) -> {
            String def = "translate,bold,italic,mono,code,strike,underline,quote,spoiler,link,mention,regular";
            NaConfig.INSTANCE.getTextStyleOrder().setConfigString(def);
            ordered.clear();
            String[] keys = def.split(",");
            for (String k : keys) {
                for (Item it : items) {
                    if (it.key.equals(k) && !ordered.contains(it)) ordered.add(it);
                }
            }
            for (Item it : items) {
                if (!ordered.contains(it)) ordered.add(it);
            }
            adapter.notifyDataSetChanged();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        });
        builder.create().show();
    }));
    private final AbstractConfigCell dividerMenuAndButtons = cellGroup.appendCell(new ConfigCellDivider());

    // Interactions
    private final AbstractConfigCell headerInteractions = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.InteractionSettings)));
    private final AbstractConfigCell groupedMessageMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getGroupedMessageMenu(), getString(R.string.GroupedMessageMenuNotice)));
    private final AbstractConfigCell hideKeyboardOnChatScrollRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideKeyboardOnChatScroll));
    private final AbstractConfigCell disableVibrationRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableVibration));
    private final AbstractConfigCell disableMarkdownRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableMarkdown()));
    private final AbstractConfigCell disableProximityEventsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableProximityEvents));
    private final AbstractConfigCell disablePhotoSideActionRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disablePhotoSideAction));
    private final AbstractConfigCell disableClickProfileGalleryViewRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableAvatarTapToSwitch()));
    private final AbstractConfigCell rememberAllBackMessagesRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.rememberAllBackMessages));
    private final AbstractConfigCell typeMessageHintUseGroupNameRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getTypeMessageHintUseGroupName()));
    private final AbstractConfigCell showSendAsUnderMessageHintRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowSendAsUnderMessageHint()));
    private final AbstractConfigCell showQuickReplyInBotCommandsRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowQuickReplyInBotCommands()));
    private final AbstractConfigCell hideBotButtonInInputFieldRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideBotButtonInInputField()));
    private final AbstractConfigCell hideReactionsRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideReactions()));
    private final AbstractConfigCell dividerInteractions = cellGroup.appendCell(new ConfigCellDivider());

    // Channels
    private final AbstractConfigCell headerChannels = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.ChannelsTab)));
    private final AbstractConfigCell hideSendAsChannelRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.hideSendAsChannel));
    private final AbstractConfigCell hideShareButtonInChannelRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideShareButtonInChannel()));
    private final AbstractConfigCell disableChannelMuteButtonRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableChannelMuteButton()));
    private final AbstractConfigCell disableSwipeToNextRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableSwipeToNext));
    private final AbstractConfigCell labelChannelUserRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.labelChannelUser));
    private final AbstractConfigCell dividerChannels = cellGroup.appendCell(new ConfigCellDivider());

    // Confirmations
    private final AbstractConfigCell headerConfirmation = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.ConfirmSettings)));
    private final AbstractConfigCell skipOpenLinkConfirmRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.skipOpenLinkConfirm));
    private final AbstractConfigCell askBeforeCallRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.askBeforeCall));
    private final AbstractConfigCell repeatConfirmRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.repeatConfirm));
    private final AbstractConfigCell disableClickCommandToSendRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableClickCommandToSend()));
    private final AbstractConfigCell confirmAVRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.confirmAVMessage));
    private final AbstractConfigCell confirmAllLinksRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getConfirmAllLinks(), getString(R.string.ConfirmAllLinksDescription)));
    private final AbstractConfigCell dividerConfirmation = cellGroup.appendCell(new ConfigCellDivider());

    // Search tag
    private final AbstractConfigCell headerSearchTag = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.SavedTagSearchHint)));
    private final String[] searchPagesString = new String[]{
            getString(R.string.SearchThisChat),
            getString(R.string.SearchMyMessages),
            getString(R.string.SearchPublicPosts),
    };
    private final AbstractConfigCell searchHashtagDefaultPageChannelRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getSearchHashtagDefaultPageChannel(), searchPagesString, null));
    private final AbstractConfigCell searchHashtagDefaultPageChatRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getSearchHashtagDefaultPageChat(), searchPagesString, null));
    private final AbstractConfigCell dividerSearchTag  = cellGroup.appendCell(new ConfigCellDivider());

    private ListAdapter listAdapter;
    private ActionBarMenuItem menuItem;
    private StickerSizeCell stickerSizeCell;

    public NekoChatSettingsActivity() {
        if (NaConfig.INSTANCE.getUseEditedIcon().Bool()) {
            cellGroup.rows.remove(customEditedMessageRow);
        }
        if (NaConfig.INSTANCE.getTranscribeProvider().Int() != TranscribeHelper.TRANSCRIBE_OPENAI) {
            cellGroup.rows.remove(transcribeProviderOpenAiRow);
        }
        if (!BuildVars.LOGS_ENABLED) {
            cellGroup.rows.remove(markdownParserRow);
        }
        checkSkipOpenLinkConfirmRows();
        checkConfirmAVRows();
        addRowsToMap(cellGroup);
    }

    @Override
    public boolean onFragmentCreate() {
        EmojiHelper.getInstance().loadEmojisInfo(this);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        super.onFragmentCreate();

        updateRows();

        return true;
    }

    @SuppressLint("NewApi")
    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
        menuItem.addSubItem(1, R.drawable.msg_reset, getString(R.string.ResetStickerSize));
        menuItem.setVisibility(NekoConfig.stickerSize.Float() != 14.0f ? View.VISIBLE : View.GONE);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    NekoConfig.stickerSize.setConfigFloat(14.0f);
                    menuItem.setVisibility(View.GONE);
                    stickerSizeCell.invalidate();
                }
            }
        });

        listAdapter = new ListAdapter(context);

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
            } else if (a instanceof ConfigCellCustom) { // Custom onclick
                if (position == cellGroup.rows.indexOf(maxRecentStickerCountRow)) {
                    final int[] counts = {20, 30, 40, 50, 80, 100, 120, 150, 180, 200};
                    List<String> types = Arrays.stream(counts)
                            .filter(i -> i <= getMessagesController().maxRecentStickersCount)
                            .mapToObj(String::valueOf)
                            .collect(Collectors.toList());
                    PopupBuilder builder = new PopupBuilder(view);
                    builder.setItems(types, (i, str) -> {
                        NekoConfig.maxRecentStickerCount.setConfigInt(Integer.parseInt(str.toString()));
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                    builder.show();
                } else if (position == cellGroup.rows.indexOf(doubleTapActionRow) || position == cellGroup.rows.indexOf(doubleTapActionOutRow)) {
                    ArrayList<String> arrayList = new ArrayList<>();
                    ArrayList<Integer> types = new ArrayList<>();
                    arrayList.add(getString(R.string.Disable));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_NONE);
                    arrayList.add(getString(R.string.SendReactions));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_SEND_REACTIONS);
                    arrayList.add(getString(R.string.ShowReactions));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_SHOW_REACTIONS);
                    arrayList.add(getString(R.string.TranslateMessage));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE);
                    arrayList.add(getString(R.string.TranslateMessageLLM));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE_LLM);
                    arrayList.add(getString(R.string.Reply));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_REPLY);
                    arrayList.add(getString(R.string.AddToSavedMessages));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_SAVE);
                    arrayList.add(getString(R.string.Repeat));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT);
                    arrayList.add(getString(R.string.RepeatAsCopy));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_REPEAT_AS_COPY);
                    if (position == cellGroup.rows.indexOf(doubleTapActionOutRow)) {
                        arrayList.add(getString(R.string.Edit));
                        types.add(DoubleTap.DOUBLE_TAP_ACTION_EDIT);
                    }
                    arrayList.add(getString(R.string.Delete));
                    types.add(DoubleTap.DOUBLE_TAP_ACTION_DELETE);
                    PopupBuilder builder = new PopupBuilder(view);
                    builder.setItems(arrayList, (i, str) -> {
                        if (position == cellGroup.rows.indexOf(doubleTapActionRow)) {
                            NaConfig.INSTANCE.getDoubleTapAction().setConfigInt(types.get(i));
                        } else {
                            NaConfig.INSTANCE.getDoubleTapActionOut().setConfigInt(types.get(i));
                        }
                        listAdapter.notifyItemChanged(position);
                        return Unit.INSTANCE;
                    });
                    builder.show();
                } else if (position == cellGroup.rows.indexOf(emojiSetsRow)) {
                    presentFragment(new NekoEmojiSettingsActivity());
                } else if (position == cellGroup.rows.indexOf(transcribeProviderCfCredentialsRow)) {
                    TranscribeHelper.showCfCredentialsDialog(this);
                } else if (position == cellGroup.rows.indexOf(transcribeProviderGeminiApiKeyRow)) {
                    TranscribeHelper.showGeminiApiKeyDialog(this);
                } else if (position == cellGroup.rows.indexOf(transcribeProviderOpenAiRow)) {
                    TranscribeHelper.showOpenAiCredentialsDialog(this);
                }
            } else if (a instanceof ConfigCellTextCheckIcon) {
                ((ConfigCellTextCheckIcon) a).onClick();
            } else if (a instanceof ConfigCellTextCheck2) {
                ((ConfigCellTextCheck2) a).onClick();
            } else if (a instanceof ConfigCellCheckBox) {
                ((ConfigCellCheckBox) a).onClick((CheckBoxCell) view);
                int toggleRowIndex = cellGroup.rows.indexOf(premiumElementsToggleRow);
                if (position > toggleRowIndex && position <= toggleRowIndex + premiumElementsRows.size()) {
                    listAdapter.notifyItemRangeChanged(toggleRowIndex, premiumElementsRows.size());
                }
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (cellGroup.rows.get(position) instanceof ConfigCellCheckBox) {
                return true;
            }
            var holder = listView.findViewHolderForAdapterPosition(position);
            if (holder != null && listAdapter.isEnabled(holder)) {
                createLongClickDialog(context, NekoChatSettingsActivity.this, "chat", position);
                return true;
            }
            return false;
        });

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NekoConfig.disableProximityEvents.getKey())) {
                MediaController.getInstance().recreateProximityWakeLock();
            } else if (key.equals(NekoConfig.showSeconds.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getConfirmAllLinks().getKey())) {
                checkSkipOpenLinkConfirmRows();
            } else if (key.equals(NekoConfig.useChatAttachMediaMenu.getKey())) {
                checkConfirmAVRows();
            } else if (key.equals(NaConfig.INSTANCE.getUseEditedIcon().getKey())) {
                if ((boolean) newValue) {
                    if (cellGroup.rows.contains(customEditedMessageRow)) {
                        final int index = cellGroup.rows.indexOf(customEditedMessageRow);
                        cellGroup.rows.remove(customEditedMessageRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                } else {
                    if (!cellGroup.rows.contains(customEditedMessageRow)) {
                        final int index = cellGroup.rows.indexOf(useEditedIconRow) + 1;
                        cellGroup.rows.add(index, customEditedMessageRow);
                        listAdapter.notifyItemInserted(index);
                    }
                }
            } else if (key.equals(NaConfig.INSTANCE.getMessageColoredBackground().getKey())) {
                stickerSizeCell.invalidate();
            } else if (key.equals(NekoConfig.hideTimeForSticker.getKey())) {
                stickerSizeCell.invalidate();
            } else if (key.equals("PremiumElements" + "_check")) {
                stickerSizeCell.invalidate();
            } else if (key.equals(NaConfig.INSTANCE.getPremiumItemEmojiInReplies().getKey())) {
                stickerSizeCell.invalidate();
            } else if (key.equals(NaConfig.INSTANCE.getPremiumItemCustomColorInReplies().getKey())) {
                stickerSizeCell.invalidate();
            } else if (key.equals(NaConfig.INSTANCE.getTranscribeProvider().getKey())) {
                if ((int) newValue == TranscribeHelper.TRANSCRIBE_OPENAI) {
                    if (!cellGroup.rows.contains(transcribeProviderOpenAiRow)) {
                        final int index = cellGroup.rows.indexOf(transcribeProviderGeminiApiKeyRow) + 1;
                        cellGroup.rows.add(index, transcribeProviderOpenAiRow);
                        listAdapter.notifyItemInserted(index);
                    }
                } else {
                    if (cellGroup.rows.contains(transcribeProviderOpenAiRow)) {
                        final int index = cellGroup.rows.indexOf(transcribeProviderOpenAiRow);
                        cellGroup.rows.remove(transcribeProviderOpenAiRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                }
            } else if (key.equals("PremiumElements")) {
                addRowsToMap(cellGroup);
            }
        };

        //Cells: Set ListAdapter
        cellGroup.setListAdapter(listView, listAdapter);

        return superView;
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
    public int getDrawable() {
        return R.drawable.menu_chats;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Chat);
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

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2Track));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2TrackChecked));

        return themeDescriptions;
    }

    public static boolean[] getDeleteMenuChecks() {
        return new boolean[]{
                NaConfig.INSTANCE.getDefaultDeleteMenuBanUsers().Bool(),
                NaConfig.INSTANCE.getDefaultDeleteMenReportSpam().Bool(),
                NaConfig.INSTANCE.getDefaultDeleteMenuDeleteAll().Bool(),
                NaConfig.INSTANCE.getDefaultDeleteMenuDoActionsInCommonGroups().Bool(),
        };
    }

    @Override
    public void emojiPacksLoaded(String error) {
        if (listAdapter != null) {
            listAdapter.notifyItemChanged(cellGroup.rows.indexOf(emojiSetsRow), PARTIAL);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded && listAdapter != null) {
            listAdapter.notifyItemChanged(cellGroup.rows.indexOf(emojiSetsRow), PARTIAL);
        }
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        super.onFragmentDestroy();
    }

    private class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewMessagesCell messagesCell;
        private final SeekBarView sizeBar;
        private final int startStickerSize = 2;
        private final int endStickerSize = 20;

        private final TextPaint textPaint;

        public StickerSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(endStickerSize - startStickerSize + 1);
            sizeBar.setDelegate((stop, progress) -> {
                NekoConfig.stickerSize.setConfigFloat(startStickerSize + (endStickerSize - startStickerSize) * progress);
                StickerSizeCell.this.invalidate();
                menuItem.setVisibility(View.VISIBLE);
            });
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));

            messagesCell = new StickerSizePreviewMessagesCell(context, NekoChatSettingsActivity.this);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 53, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText("" + Math.round(NekoConfig.stickerSize.Float()), getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            sizeBar.setProgress((NekoConfig.stickerSize.Float() - startStickerSize) / (float) (endStickerSize - startStickerSize));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            messagesCell.invalidate();
            sizeBar.invalidate();
        }
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
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            View view = holder.itemView;
            AbstractConfigCell a = cellGroup.rows.get(position);
            if (a != null) {
                if (a instanceof ConfigCellCustom) {
                    // Custom binds
                    if (holder.itemView instanceof TextSettingsCell textCell) {
                        if (position == cellGroup.rows.indexOf(maxRecentStickerCountRow)) {
                            textCell.setTextAndValue(getString(R.string.maxRecentStickerCount), String.valueOf(NekoConfig.maxRecentStickerCount.Int()), true);
                        } else if (position == cellGroup.rows.indexOf(doubleTapActionRow)) {
                            textCell.setTextAndValue(getString(R.string.DoubleTapIncoming), DoubleTap.doubleTapActionMap.get(NaConfig.INSTANCE.getDoubleTapAction().Int()), true);
                        } else if (position == cellGroup.rows.indexOf(doubleTapActionOutRow)) {
                            textCell.setTextAndValue(getString(R.string.DoubleTapOutgoing), DoubleTap.doubleTapActionMap.get(NaConfig.INSTANCE.getDoubleTapActionOut().Int()), true);
                        } else if (position == cellGroup.rows.indexOf(transcribeProviderCfCredentialsRow)) {
                            textCell.setTextAndValue(getString(R.string.CloudflareCredentials), "", true);
                        } else if (position == cellGroup.rows.indexOf(transcribeProviderGeminiApiKeyRow)) {
                            textCell.setTextAndValue(getString(R.string.LlmProviderGeminiKey), "", true);
                        } else if (position == cellGroup.rows.indexOf(transcribeProviderOpenAiRow)) {
                            textCell.setTextAndValue(getString(R.string.TranscribeProviderOpenAI), "", true);
                        }
                    } else if (view instanceof EmojiSetCell v1) {
                        v1.setData(EmojiHelper.getInstance().getCurrentEmojiPackInfo(), false, true);
                    }
                } else {
                    // Default binds
                    a.onBindViewHolder(holder);
                }
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
                case ConfigCellCustom.CUSTOM_ITEM_StickerSize:
                    view = stickerSizeCell = new StickerSizeCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case ConfigCellCustom.CUSTOM_ITEM_EmojiSet:
                    view = new EmojiSetCell(mContext, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_TEXT_CHECK_ICON:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_CHECK2:
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case CellGroup.ITEM_TYPE_CHECK_BOX:
                    CheckBoxCell checkBoxCell = new CheckBoxCell(mContext, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, getResourceProvider());
                    checkBoxCell.getCheckBoxRound().setDrawBackgroundAsArc(14);
                    checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
                    checkBoxCell.setEnabled(true);
                    view = checkBoxCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            //noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }

    private void checkSkipOpenLinkConfirmRows() {
        boolean confirmAllLinks = NaConfig.INSTANCE.getConfirmAllLinks().Bool();
        if (listAdapter == null) {
            if (confirmAllLinks) {
                cellGroup.rows.remove(skipOpenLinkConfirmRow);
            }
            return;
        }
        if (!confirmAllLinks) {
            final int index = cellGroup.rows.indexOf(headerConfirmation);
            if (!cellGroup.rows.contains(skipOpenLinkConfirmRow)) {
                cellGroup.rows.add(index + 1, skipOpenLinkConfirmRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(skipOpenLinkConfirmRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(skipOpenLinkConfirmRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }

    private void checkConfirmAVRows() {
        boolean useChatAttachMediaMenu = NekoConfig.useChatAttachMediaMenu.Bool();
        if (listAdapter == null) {
            if (useChatAttachMediaMenu) {
                cellGroup.rows.remove(confirmAVRow);
            }
            return;
        }
        if (!useChatAttachMediaMenu) {
            final int index = cellGroup.rows.indexOf(disableClickCommandToSendRow);
            if (!cellGroup.rows.contains(confirmAVRow)) {
                cellGroup.rows.add(index + 1, confirmAVRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            int rowIndex = cellGroup.rows.indexOf(confirmAVRow);
            if (rowIndex != -1) {
                cellGroup.rows.remove(confirmAVRow);
                listAdapter.notifyItemRemoved(rowIndex);
            }
        }
        addRowsToMap(cellGroup);
    }
}
