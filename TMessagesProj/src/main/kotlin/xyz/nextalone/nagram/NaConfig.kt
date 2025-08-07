package xyz.nextalone.nagram

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.config.ConfigItem
import tw.nekomimi.nekogram.config.ConfigItemKeyLinked
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream


object NaConfig {
    val preferences: SharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences(
            "nkmrcfg",
            Context.MODE_PRIVATE
        )
    val sync =
        Any()
    private var configLoaded =
        false
    private val configs =
        ArrayList<ConfigItem>()

    // Configs
    val forceCopy =
        addConfig(
            "ForceCopy",
            ConfigItem.configTypeBool,
            true
        )
    val showTextBold =
        addConfig(
            "TextBold",
            ConfigItem.configTypeBool,
            true
        )
    val showTextItalic =
        addConfig(
            "TextItalic",
            ConfigItem.configTypeBool,
            true
        )
    val showTextMono =
        addConfig(
            "TextMonospace",
            ConfigItem.configTypeBool,
            true
        )
    val showTextStrikethrough =
        addConfig(
            "TextStrikethrough",
            ConfigItem.configTypeBool,
            true
        )
    val showTextUnderline =
        addConfig(
            "TextUnderline",
            ConfigItem.configTypeBool,
            true
        )
    val showTextQuote =
        addConfig(
            "TextQuote",
            ConfigItem.configTypeBool,
            true
        )
    val showTextSpoiler =
        addConfig(
            "TextSpoiler",
            ConfigItem.configTypeBool,
            true
        )
    val showTextCreateLink =
        addConfig(
            "TextLink",
            ConfigItem.configTypeBool,
            true
        )
    val showTextCreateMention =
        addConfig(
            "TextCreateMention",
            ConfigItem.configTypeBool,
            true
        )
    val showTextRegular =
        addConfig(
            "TextRegular",
            ConfigItem.configTypeBool,
            true
        )
    val combineMessage =
        addConfig(
            "CombineMessage",
            ConfigItem.configTypeInt,
            0
        )
    val noiseSuppressAndVoiceEnhance =
        addConfig(
            "NoiseSuppressAndVoiceEnhance",
            ConfigItem.configTypeBool,
            false
        )
    val showNoQuoteForward =
        addConfig(
            "NoQuoteForward",
            ConfigItem.configTypeBool,
            false
        )
    val showRepeatAsCopy =
        addConfig(
            "RepeatAsCopy",
            ConfigItem.configTypeBool,
            false
        )
    val doubleTapAction =
        addConfig(
            "DoubleTapAction",
            ConfigItem.configTypeInt,
            3
        )
    val doubleTapActionOut =
        addConfig(
            "DoubleTapActionOut",
            ConfigItem.configTypeInt,
            8
        )
    val showCopyPhoto =
        addConfig(
            "CopyPhoto",
            ConfigItem.configTypeBool,
            false
        )
    val showReactions =
        addConfig(
            "Reactions",
            ConfigItem.configTypeBool,
            true
        )
    val customTitle =
        addConfig(
            "CustomTitle",
            ConfigItem.configTypeString,
            getString(
                R.string.NagramX
            )
        )
    val dateOfForwardedMsg =
        addConfig(
            "DateOfForwardedMsg",
            ConfigItem.configTypeBool,
            false
        )
    val showMessageID =
        addConfig(
            "ShowMessageID",
            ConfigItem.configTypeBool,
            false
        )
    val showRPCError =
        addConfig(
            "ShowRPCError",
            ConfigItem.configTypeBool,
            false
        )
    val alwaysSaveChatOffset =
        addConfig(
            "AlwaysSaveChatOffset",
            ConfigItem.configTypeBool,
            false
        )
    val autoInsertGIFCaption =
        addConfig(
            "AutoInsertGIFCaption",
            ConfigItem.configTypeBool,
            true
        )
    val zalgoFilter =
        addConfig(
            "ZalgoFilter",
            ConfigItem.configTypeBool,
            false
        )
    val customChannelLabel =
        addConfig(
            "CustomChannelLabel",
            ConfigItem.configTypeString,
            ""
        )
    val alwaysShowDownloadIcon =
        addConfig(
            "AlwaysShowDownloadIcon",
            ConfigItem.configTypeBool,
            false
        )
    val customArtworkApi =
        addConfig(
            "CustomArtworkApi",
            ConfigItem.configTypeString,
            ""
        )
    val customEditedMessage =
        addConfig(
            "CustomEditedMessage",
            ConfigItem.configTypeString,
            ""
        )
    val disableProxyWhenVpnEnabled =
        addConfig(
            "DisableProxyWhenVpnEnabled",
            ConfigItem.configTypeBool,
            false
        )
    val disableEmojiDrawLimit =
        addConfig(
            "DisableEmojiDrawLimit",
            ConfigItem.configTypeBool,
            false
        )
    val iconDecoration =
        addConfig(
            "IconDecoration",
            ConfigItem.configTypeInt,
            0
        )
    val notificationIcon =
        addConfig(
            "NotificationIcon",
            ConfigItem.configTypeInt,
            1
        )
    val showSetReminder =
        addConfig(
            "SetReminder",
            ConfigItem.configTypeBool,
            false
        )
    val showOnlineStatus =
        addConfig(
            "ShowOnlineStatus",
            ConfigItem.configTypeBool,
            false
        )
    val showFullAbout =
        addConfig(
            "ShowFullAbout",
            ConfigItem.configTypeBool,
            true
        )
    val typeMessageHintUseGroupName =
        addConfig(
            "TypeMessageHintUseGroupName",
            ConfigItem.configTypeBool,
            false
        )
    val showSendAsUnderMessageHint =
        addConfig(
            "ShowSendAsUnderMessageHint",
            ConfigItem.configTypeBool,
            false
        )
    val hideBotButtonInInputField =
        addConfig(
            "HideBotButtonInInputField",
            ConfigItem.configTypeBool,
            false
        )
    val chatDecoration =
        addConfig(
            "ChatDecoration",
            ConfigItem.configTypeInt,
            0
        )
    val doNotUnarchiveBySwipe =
        addConfig(
            "DoNotUnarchiveBySwipe",
            ConfigItem.configTypeBool,
            false
        )
    val doNotShareMyPhoneNumber =
        addConfig(
            "DoNotShareMyPhoneNumber",
            ConfigItem.configTypeBool,
            false
        )
    val defaultDeleteMenu =
        addConfig(
            "DefaultDeleteMenu",
            ConfigItem.configTypeInt,
            0
        )
    val defaultDeleteMenuBanUsers =
        addConfig(
            "DeleteBanUsers",
            defaultDeleteMenu,
            3,
            false
        )
    val defaultDeleteMenReportSpam =
        addConfig(
            "DeleteReportSpam",
            defaultDeleteMenu,
            2,
            false
        )
    val defaultDeleteMenuDeleteAll =
        addConfig(
            "DeleteAll",
            defaultDeleteMenu,
            1,
            false
        )
    val defaultDeleteMenuDoActionsInCommonGroups =
        addConfig(
            "DoActionsInCommonGroups",
            defaultDeleteMenu,
            0,
            false
        )
    val disableSuggestionView =
        addConfig(
            "DisableSuggestionView",
            ConfigItem.configTypeBool,
            false
        )
    val disableStories =
        addConfig(
            "DisableStories",
            ConfigItem.configTypeBool,
            true
        )
    val useLocalQuoteColorData =
        addConfig(
            "useLocalQuoteColorData",
            ConfigItem.configTypeString,
            ""
        )
    val externalStickerCache =
        addConfig(
            "ExternalStickerCache",
            ConfigItem.configTypeString,
            ""
        )
    var externalStickerCacheUri: Uri?
        get() = externalStickerCache.String().let { return if (it.isBlank()) null else it.toUri() }
        set(value) = externalStickerCache.setConfigString(value.toString())
    val externalStickerCacheAutoRefresh =
        addConfig(
            "ExternalStickerCacheAutoRefresh",
            ConfigItem.configTypeBool,
            false
        )
    val externalStickerCacheDirNameType =
        addConfig(
            "ExternalStickerCacheDirNameType",
            ConfigItem.configTypeInt,
            0
        )
    val disableMarkdown =
        addConfig(
            "DisableMarkdown",
            ConfigItem.configTypeBool,
            false
        )
    val showSmallGIF =
        addConfig(
            "ShowSmallGIF",
            ConfigItem.configTypeBool,
            false
        )
    val disableClickCommandToSend =
        addConfig(
            "DisableClickCommandToSend",
            ConfigItem.configTypeBool,
            false
        )
    val disableDialogsFloatingButton =
        addConfig(
            "DisableDialogsFloatingButton",
            ConfigItem.configTypeBool,
            false
        )
    val disableFlagSecure =
        addConfig(
            "DisableFlagSecure",
            ConfigItem.configTypeBool,
            true
        )
    val centerActionBarTitle =
        addConfig(
            "CenterActionBarTitle",
            ConfigItem.configTypeBool,
            false
        )
    val showQuickReplyInBotCommands =
        addConfig(
            "ShowQuickReplyInBotCommands",
            ConfigItem.configTypeBool,
            false
        )
    val pushServiceType =
        addConfig(
            "PushServiceType",
            ConfigItem.configTypeInt,
            1
        )
    val pushServiceTypeInAppDialog =
        addConfig(
            "PushServiceTypeInAppDialog",
            ConfigItem.configTypeBool,
            false
        )
    val pushServiceTypeUnifiedGateway =
        addConfig(
            "PushServiceTypeUnifiedGateway",
            ConfigItem.configTypeString,
            ""
        )
    val sendMp4DocumentAsVideo =
        addConfig(
            "SendMp4DocumentAsVideo",
            ConfigItem.configTypeBool,
            true
        )
    val disableChannelMuteButton =
        addConfig(
            "DisableChannelMuteButton",
            ConfigItem.configTypeBool,
            false
        )
    val disablePreviewVideoSoundShortcut =
        addConfig(
            "DisablePreviewVideoSoundShortcut",
            ConfigItem.configTypeBool,
            true
        )
    val disableAutoWebLogin =
        addConfig(
            "DisableAutoWebLogin",
            ConfigItem.configTypeBool,
            false
        )
    val regexFiltersEnabled =
        addConfig(
            "RegexFilters",
            ConfigItem.configTypeBool,
            false
        )
    val regexFiltersData =
        addConfig(
            "RegexFiltersData",
            ConfigItem.configTypeString,
            "[]"
        )
    val regexFiltersEnableInChats =
        addConfig(
            "RegexFiltersEnableInChats",
            ConfigItem.configTypeBool,
            false
        )
    val showTimeHint =
        addConfig(
            "ShowTimeHint",
            ConfigItem.configTypeBool,
            false
        )
    val searchHashtagDefaultPageChannel =
        addConfig(
            "SearchHashtagDefaultPageChannel",
            ConfigItem.configTypeInt,
            0
        )
    val searchHashtagDefaultPageChat =
        addConfig(
            "SearchHashtagDefaultPageChat",
            ConfigItem.configTypeInt,
            0
        )
    val enablePanguOnSending =
        addConfig(
            "EnablePanguOnSending",
            ConfigItem.configTypeBool,
            false
        )
    val defaultHlsVideoQuality =
        addConfig(
            "DefaultHlsVideoQuality",
            ConfigItem.configTypeInt,
            1
        )
    val disableBotOpenButton =
        addConfig(
            "DisableBotOpenButton",
            ConfigItem.configTypeBool,
            false
    )
    val customTitleUserName =
        addConfig(
            "CustomTitleUserName",
            ConfigItem.configTypeBool,
            false
        )
    val enhancedVideoBitrate =
        addConfig(
            "EnhancedVideoBitrate",
            ConfigItem.configTypeBool,
            false
        )
    val ActionBarButtonReply =
        addConfig(
            "Reply",
            ConfigItem.configTypeBool,
            false
        )
    val ActionBarButtonEdit =
        addConfig(
            "Edit",
            ConfigItem.configTypeBool,
            true
        )
    val ActionBarButtonSelectBetween =
        addConfig(
            "SelectBetween",
            ConfigItem.configTypeBool,
            true
        )
    val ActionBarButtonCopy =
        addConfig(
            "Copy",
            ConfigItem.configTypeBool,
            true
        )
    val ActionBarButtonForward =
        addConfig(
            "Forward",
            ConfigItem.configTypeBool,
            true
        )
    val playerDecoder =
        addConfig(
            "VideoPlayerDecoder",
            ConfigItem.configTypeInt,
            1
        )

    // NagramX
    val enableSaveDeletedMessages =
        addConfig(
            "EnableSaveDeletedMessages",
            ConfigItem.configTypeBool,
            false
        )
    val enableSaveEditsHistory =
        addConfig(
            "EnableSaveEditsHistory",
            ConfigItem.configTypeBool,
            false
        )
    val messageSavingSaveMedia =
        addConfig(
            "MessageSavingSaveMedia",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPrivateChats =
        addConfig(
            "SaveMediaInPrivateChats",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPublicChannels =
        addConfig(
            "SaveMediaInPublicChannels",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPrivateChannels =
        addConfig(
            "SaveMediaInPrivateChannels",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPublicGroups =
        addConfig(
            "SaveMediaInPublicGroups",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPrivateGroups =
        addConfig(
            "SaveMediaInPrivateGroups",
            ConfigItem.configTypeBool,
            true
        )
    val saveDeletedMessageForBot =
        addConfig(
            "SaveDeletedMessageForBot", // save in bot chats
            ConfigItem.configTypeBool,
            false
        )
    val saveDeletedMessageForBotUser =
        addConfig(
            "SaveDeletedMessageForBotUser", // all messages from bot
            ConfigItem.configTypeBool,
            false
        )
    val customDeletedMark =
        addConfig(
            "CustomDeletedMark",
            ConfigItem.configTypeString,
            ""
        )
    val hidePremiumSection =
        addConfig(
            "HidePremiumSection",
            ConfigItem.configTypeBool,
            true
        )
    val hideHelpSection =
        addConfig(
            "HideHelpSection",
            ConfigItem.configTypeBool,
            true
        )
    val llmApiUrl =
        addConfig(
            "LlmApiUrl",
            ConfigItem.configTypeString,
            ""
        )
    val llmApiKey =
        addConfig(
            "LlmApiKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmModelName =
        addConfig(
            "LlmModelName",
            ConfigItem.configTypeString,
            ""
        )
    val llmSystemPrompt =
        addConfig(
            "LlmSystemPrompt",
            ConfigItem.configTypeString,
            ""
        )
    val llmUserPrompt =
        addConfig(
            "LlmUserPrompt",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderPreset =
        addConfig(
            "LlmProviderPreset",
            ConfigItem.configTypeInt,
            0
        )
    val llmProviderOpenAIKey =
        addConfig(
            "LlmProviderOpenAIKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderGeminiKey =
        addConfig(
            "LlmProviderGeminiKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderXAIKey =
        addConfig(
            "LlmProviderXAIKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderGroqKey =
        addConfig(
            "LlmProviderGroqKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderDeepSeekKey =
        addConfig(
            "LlmProviderDeepSeekKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmTemperature =
        addConfig(
            "LlmTemperature",
            ConfigItem.configTypeFloat,
            0.7f
        )

    val translucentDeletedMessages =
        addConfig(
            "TranslucentDeletedMessages",
            ConfigItem.configTypeBool,
            true
    )
    val enableSeparateArticleTranslator =
        addConfig(
            "EnableSeparateArticleTranslator",
            ConfigItem.configTypeBool,
            false
    )
    val articleTranslationProvider =
        addConfig(
            "ArticleTranslationProvider",
            ConfigItem.configTypeInt,
            1
    )
    val disableCrashlyticsCollection =
        addConfig(
            "DisableCrashlyticsCollection",
            ConfigItem.configTypeBool,
            false
        )
    val showStickersRowToplevel=
        addConfig(
            "ShowStickersRowToplevel",
            ConfigItem.configTypeBool,
            true
        )
    val hideShareButtonInChannel =
        addConfig(
            "HideShareButtonInChannel",
            ConfigItem.configTypeBool,
            false
        )
    val preferredTranslateTargetLang =
        addConfig(
            "PreferredTranslateTargetLang",
            ConfigItem.configTypeString,
            "ja, zh"
        )
    val disableScreenshotDetection =
        addConfig(
            "DisableScreenshotDetection",
            ConfigItem.configTypeBool,
            false
        )
    val telegramUIAutoTranslate =
        addConfig(
            "TelegramUIAutoTranslate",
            ConfigItem.configTypeBool,
            true
        )
    val translatorMode =
        addConfig(
            "TranslatorMode",
            ConfigItem.configTypeInt,
            1 // 0: append; 1: replace; 2: pop-up
        )
    val centerActionBarTitleType =
        addConfig(
            "CenterActionBarTitleType",
            ConfigItem.configTypeInt,
            1 // 0: off; 1: always on; 2: settings only; 3: chats only
        )
    val drawerItemMyProfile =
        addConfig(
            "DrawerItemMyProfile",
            ConfigItem.configTypeBool,
            true
        )
    val drawerItemSetEmojiStatus =
        addConfig(
            "DrawerItemSetEmojiStatus",
            ConfigItem.configTypeBool,
            true
        )
    val drawerItemNewGroup =
        addConfig(
            "DrawerItemNewGroup",
            ConfigItem.configTypeBool,
            true
        )
    val drawerItemNewChannel =
        addConfig(
            "DrawerItemNewChannel",
            ConfigItem.configTypeBool,
            false
        )
    val drawerItemContacts =
        addConfig(
            "DrawerItemContacts",
            ConfigItem.configTypeBool,
            true
        )
    val drawerItemCalls =
        addConfig(
            "DrawerItemCalls",
            ConfigItem.configTypeBool,
            true
        )
    val drawerItemSaved =
        addConfig(
            "DrawerItemSaved",
            ConfigItem.configTypeBool,
            true
        )
    val drawerItemSettings =
        addConfig(
            "DrawerItemSettings",
            ConfigItem.configTypeBool,
            true
        )
    val drawerItemNSettings =
        addConfig(
            "DrawerItemNSettings",
            ConfigItem.configTypeBool,
            true
        )
    val drawerItemQrLogin =
        addConfig(
            "DrawerItemQrLogin",
            ConfigItem.configTypeBool,
            false
        )
    val drawerItemArchivedChats =
        addConfig(
            "DrawerItemArchivedChats",
            ConfigItem.configTypeBool,
            false
        )
    val drawerItemRestartApp =
        addConfig(
            "DrawerItemRestartApp",
            ConfigItem.configTypeBool,
            false
        )
    val drawerItemBrowser =
        addConfig(
            "DrawerItemBrowser",
            ConfigItem.configTypeBool,
            false
        )
    val drawerItemSessions =
        addConfig(
            "DrawerItemSessions",
            ConfigItem.configTypeBool,
            false
        )
    val hideArchive =
        addConfig(
            "HideArchive",
            ConfigItem.configTypeBool,
            false
        )
    val hideChannelSilentBroadcast =
        addConfig(
            "HideChannelSilentBroadcast",
            ConfigItem.configTypeBool,
            false
        )
    val confirmAllLinks =
        addConfig(
            "ConfirmAllLinks",
            ConfigItem.configTypeBool,
            false
        )
    val useDeletedIcon =
        addConfig(
            "UseDeletedIcon",
            ConfigItem.configTypeBool,
            true
        )
    val useEditedIcon =
        addConfig(
            "UseEditedIcon",
            ConfigItem.configTypeBool,
            true
        )
    val saveToChatSubfolder =
        addConfig(
            "SaveToChatSubfolder",
            ConfigItem.configTypeBool,
            false
        )
    val silentMessageByDefault =
        addConfig(
            "SilentMessageByDefault",
            ConfigItem.configTypeBool,
            false
        )
    val folderNameAsTitle =
        addConfig(
            "FolderNameAsTitle",
            ConfigItem.configTypeBool,
            false
        )
    val translatorKeepMarkdown =
        addConfig(
            "TranslatorKeepMarkdown",
            ConfigItem.configTypeBool,
            false
        )
    val googleTranslateExp =
        addConfig(
            "GoogleTranslateExp",
            ConfigItem.configTypeBool,
            true
        )
    val springAnimation =
        addConfig(
            "SpringAnimation",
            ConfigItem.configTypeBool,
            true
        )
    val springAnimationCrossfade =
        addConfig(
            "SpringAnimationCrossfade",
            ConfigItem.configTypeBool,
            true
        )
    val dontAutoPlayNextVoice =
        addConfig(
            "DontAutoPlayNextVoice",
            ConfigItem.configTypeBool,
            false
        )
    val messageColoredBackground =
        addConfig(
            "MessageColoredBackground",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemBoostGroup =
        addConfig(
            "ChatMenuItemBoostGroup",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemLinkedChat =
        addConfig(
            "ChatMenuItemLinkedChat",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemToBeginning =
        addConfig(
            "ChatMenuItemToBeginning",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemGoToMessage =
        addConfig(
            "ChatMenuItemGoToMessage",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemHideTitle =
        addConfig(
            "ChatMenuItemHideTitle",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemClearDeleted =
        addConfig(
            "ChatMenuItemClearDeleted",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemDeleteOwnMessages =
        addConfig(
            "ChatMenuItemDeleteOwnMessages",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemForward =
        addConfig(
            "MediaViewerMenuItemForward",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemNoQuoteForward =
        addConfig(
            "MediaViewerMenuItemNoQuoteForward",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemCopyPhoto =
        addConfig(
            "MediaViewerMenuItemCopyPhoto",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemSetProfilePhoto =
        addConfig(
            "MediaViewerMenuItemSetProfilePhoto",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemScanQRCode =
        addConfig(
            "MediaViewerMenuItemScanQRCode",
            ConfigItem.configTypeBool,
            true
        )
    val coloredAdminTitle =
        addConfig(
            "ColoredAdminTitle",
            ConfigItem.configTypeBool,
            false
        )
    val hideReactions =
        addConfig(
            "HideReactions",
            ConfigItem.configTypeBool,
            false
        )
    val performanceClass =
        addConfig(
            "PerformanceClass",
            ConfigItem.configTypeInt,
            0
        )
    val transcribeProvider =
        addConfig(
            "TranscribeProvider",
            ConfigItem.configTypeInt,
            0
        )
    val transcribeProviderCfAccountID =
        addConfig(
            "TranscribeProviderCfAccountID",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderCfApiToken =
        addConfig(
            "TranscribeProviderCfApiToken",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderGeminiApiKey =
        addConfig(
            "TranscribeProviderGeminiApiKey",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderOpenAiApiBase =
        addConfig(
            "TranscribeProviderOpenAiApiBase",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderOpenAiModel =
        addConfig(
            "TranscribeProviderOpenAiModel",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderOpenAiApiKey =
        addConfig(
            "TranscribeProviderOpenAiApiKey",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderOpenAiPrompt =
        addConfig(
            "TranscribeProviderOpenAiPrompt",
            ConfigItem.configTypeString,
            ""
        )
    val showReplyInPrivate =
        addConfig(
            "ReplyInPrivate",
            ConfigItem.configTypeBool,
            false
        )
    val transcribeProviderGeminiPrompt =
        addConfig(
            "TranscribeProviderGeminiPrompt",
            ConfigItem.configTypeString,
            ""
        )
    val hideDividers =
        addConfig(
            "HideDividers",
            ConfigItem.configTypeBool,
            false
        )
    val iconReplacements =
        addConfig(
            "IconReplacements",
            ConfigItem.configTypeInt,
            0
        )
    val showCopyAsSticker =
        addConfig(
            "CopyPhotoAsSticker",
            ConfigItem.configTypeBool,
            false
        )
    val showAddToStickers =
        addConfig(
            "AddToStickers",
            ConfigItem.configTypeBool,
            false
        )
    val showAddToFavorites =
        addConfig(
            "AddToFavorites",
            ConfigItem.configTypeBool,
            true
        )
    val showTranslateMessageLLM =
        addConfig(
            "TranslateMessageLLM",
            ConfigItem.configTypeBool,
            false
        )
    val tabStyle =
        addConfig(
            "TabStyle",
            ConfigItem.configTypeInt,
            0
        )
    val shortcutsAdministrators =
        addConfig(
            "ChannelAdministrators",
            ConfigItem.configTypeBool,
            false
        )
    val shortcutsRecentActions =
        addConfig(
            "EventLog",
            ConfigItem.configTypeBool,
            false
        )
    val shortcutsStatistics =
        addConfig(
            "Statistics",
            ConfigItem.configTypeBool,
            false
        )
    val shortcutsPermissions =
        addConfig(
            "ChannelPermissions",
            ConfigItem.configTypeBool,
            false
        )
    val shortcutsMembers =
        addConfig(
            "GroupMembers",
            ConfigItem.configTypeBool,
            false
        )
    val leftBottomButton =
        addConfig(
            "LeftBottomButtonAction",
            ConfigItem.configTypeInt,
            0
        )
    val showTextMonoCode =
        addConfig(
            "TextMonoCode",
            ConfigItem.configTypeBool,
            true
        )
    val showCopyLink =
        addConfig(
            "CopyLink",
            ConfigItem.configTypeBool,
            true
        )
    val preferCommonGroupsTab =
        addConfig(
            "PreferCommonGroupsTab",
            ConfigItem.configTypeBool,
            true
        )
    val sendHighQualityPhoto =
        addConfig(
            "SendHighQualityPhoto",
            ConfigItem.configTypeBool,
            true
        )
    val groupedMessageMenu =
        addConfig(
            "GroupedMessageMenu",
            ConfigItem.configTypeBool,
            false
        )
    val autoUpdateChannel =
        addConfig(
            "AutoUpdateChannel",
            ConfigItem.configTypeInt,
            1 // 0: off; 1: release; 2: beta
        )
    val userAvatarsInMessagePreview =
        addConfig(
            "UserAvatarsInMessagePreview",
            ConfigItem.configTypeBool,
            false
        )
    val disableAvatarTapToSwitch =
        addConfig(
            "DisableAvatarTapToSwitch",
            ConfigItem.configTypeBool,
            false
        )
    val premiumItemEmojiStatus =
        addConfig(
            "PremiumItemEmojiStatus",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemEmojiInReplies =
        addConfig(
            "PremiumItemEmojiInReplies",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemCustomColorInReplies =
        addConfig(
            "PremiumItemCustomColorInReplies",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemCustomWallpaper =
        addConfig(
            "PremiumItemCustomWallpaper",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemVideoAvatar =
        addConfig(
            "PremiumItemVideoAvatar",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemStarInReactions =
        addConfig(
            "PremiumItemStarInReactions",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemStickerEffects =
        addConfig(
            "PremiumItemStickerEffects",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemBoosts =
        addConfig(
            "PremiumItemBoosts",
            ConfigItem.configTypeBool,
            true
        )
    val switchStyle =
        addConfig(
            "SwitchStyle",
            ConfigItem.configTypeInt,
            0 // 0: default; 1: Modern; 2: MD3
        )
    val sliderStyle =
        addConfig(
            "SliderStyle",
            ConfigItem.configTypeInt,
            0 // 0: default; 1: Modern; 2: MD3
        )
    val ignoreUnreadCount =
        addConfig(
            "IgnoreUnreadCount",
            ConfigItem.configTypeInt,
            getIgnoreMutedCountLegacy()
        )
    val markdownParser =
        addConfig(
            "MarkdownParser",
            ConfigItem.configTypeInt,
            NekoConfig.MARKDOWN_PARSER_NEKO
        )
    val keepTranslatorPreferences =
        addConfig(
            "KeepTranslatorPreferences",
            ConfigItem.configTypeBool,
            false
        )

    val preferredTranslateTargetLangList = ArrayList<String>()
    fun updatePreferredTranslateTargetLangList() {
        AndroidUtilities.runOnUIThread({
            preferredTranslateTargetLangList.clear()
            val str = preferredTranslateTargetLang.String().trim()

            if (str.isEmpty()) return@runOnUIThread

            val languages = str.replace('-', '_').split(",")
            if (languages.isEmpty() || languages[0].trim().isEmpty()) return@runOnUIThread

            languages.forEach { lang ->
                preferredTranslateTargetLangList.add(lang.trim().lowercase())
            }
        }, 1000)
    }

    fun isLLMTranslatorAvailable(): Boolean {
        val llmProvider = llmProviderPreset.Int()
        val keyConfig = when (llmProvider) {
            1 -> llmProviderOpenAIKey
            2 -> llmProviderGeminiKey
            3 -> llmProviderGroqKey
            4 -> llmProviderDeepSeekKey
            5 -> llmProviderXAIKey
            else -> llmApiKey
        }
        return keyConfig.String().isNotEmpty()
    }

    private fun getIgnoreMutedCountLegacy(): Int {
        return when {
            preferences.getBoolean("IgnoreFolderCount", false) -> NekoConfig.DIALOG_FILTER_EXCLUDE_ALL
            preferences.getBoolean("IgnoreMutedCount", true) -> NekoConfig.DIALOG_FILTER_EXCLUDE_MUTED
            else -> NekoConfig.DIALOG_FILTER_EXCLUDE_NONE
        }
    }

    private fun addConfig(
        k: String,
        t: Int,
        d: Any?
    ): ConfigItem {
        val a =
            ConfigItem(
                k,
                t,
                d
            )
        configs.add(
            a
        )
        return a
    }

    private fun addConfig(
        k: String,
        t: ConfigItem,
        d: Int,
        e: Any?
    ): ConfigItem {
        val a =
            ConfigItemKeyLinked(
                k,
                t,
                d,
                e,
            )
        configs.add(
            a
        )
        return a
    }

    fun loadConfig(
        force: Boolean
    ) {
        synchronized(
            sync
        ) {
            if (configLoaded && !force) {
                return
            }
            for (i in configs.indices) {
                val o =
                    configs[i]
                if (o.type == ConfigItem.configTypeBool) {
                    o.value =
                        preferences.getBoolean(
                            o.key,
                            o.defaultValue as Boolean
                        )
                }
                if (o.type == ConfigItem.configTypeInt) {
                    o.value =
                        preferences.getInt(
                            o.key,
                            o.defaultValue as Int
                        )
                }
                if (o.type == ConfigItem.configTypeLong) {
                    o.value =
                        preferences.getLong(
                            o.key,
                            (o.defaultValue as Long)
                        )
                }
                if (o.type == ConfigItem.configTypeFloat) {
                    o.value =
                        preferences.getFloat(
                            o.key,
                            (o.defaultValue as Float)
                        )
                }
                if (o.type == ConfigItem.configTypeString) {
                    o.value =
                        preferences.getString(
                            o.key,
                            o.defaultValue as String
                        )
                }
                if (o.type == ConfigItem.configTypeSetInt) {
                    val ss =
                        preferences.getStringSet(
                            o.key,
                            HashSet()
                        )
                    val si =
                        HashSet<Int>()
                    for (s in ss!!) {
                        si.add(
                            s.toInt()
                        )
                    }
                    o.value =
                        si
                }
                if (o.type == ConfigItem.configTypeMapIntInt) {
                    val cv =
                        preferences.getString(
                            o.key,
                            ""
                        )
                    // Log.e("NC", String.format("Getting pref %s val %s", o.key, cv));
                    if (cv!!.isEmpty()) {
                        o.value =
                            HashMap<Int, Int>()
                    } else {
                        try {
                            val data =
                                Base64.decode(
                                    cv,
                                    Base64.DEFAULT
                                )
                            val ois =
                                ObjectInputStream(
                                    ByteArrayInputStream(
                                        data
                                    )
                                )
                            o.value =
                                ois.readObject() as HashMap<*, *>
                            if (o.value == null) {
                                o.value =
                                    HashMap<Int, Int>()
                            }
                            ois.close()
                        } catch (e: Exception) {
                            o.value =
                                HashMap<Int, Int>()
                        }
                    }
                }
                if (o.type == ConfigItem.configTypeBoolLinkInt) {
                    o as ConfigItemKeyLinked
                    o.changedFromKeyLinked(preferences.getInt(o.keyLinked.key, 0))
                }
            }
            configLoaded =
                true
        }
    }

    init {
        loadConfig(
            false
        )
        updatePreferredTranslateTargetLangList()
        if (!BuildVars.LOGS_ENABLED) {
            showRPCError.setConfigBool(false)
        }
    }
}
