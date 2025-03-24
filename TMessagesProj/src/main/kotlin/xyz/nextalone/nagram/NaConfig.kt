package xyz.nextalone.nagram

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import tw.nekomimi.nekogram.config.ConfigItem
import tw.nekomimi.nekogram.config.ConfigItemKeyLinked
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import androidx.core.net.toUri


object NaConfig {
    const val TAG =
        "NextAlone"
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
            false
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
            0
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
    val codeSyntaxHighlight =
        addConfig(
            "CodeSyntaxHighlight",
            ConfigItem.configTypeBool,
            true
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
    val showPremiumStarInChat =
        addConfig(
            "ShowPremiumStarInChat",
            ConfigItem.configTypeBool,
            true
        )
    val showPremiumAvatarAnimation =
        addConfig(
            "ShowPremiumAvatarAnimation",
            ConfigItem.configTypeBool,
            true
        )
    val alwaysSaveChatOffset =
        addConfig(
            "AlwaysSaveChatOffset",
            ConfigItem.configTypeBool,
            true
        )
    val autoInsertGIFCaption =
        addConfig(
            "AutoInsertGIFCaption",
            ConfigItem.configTypeBool,
            true
        )
    val defaultMonoLanguage =
        addConfig(
            "DefaultMonoLanguage",
            ConfigItem.configTypeString,
            ""
        )
    val disableGlobalSearch =
        addConfig(
            "DisableGlobalSearch",
            ConfigItem.configTypeBool,
            false
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
    val quickToggleAnonymous =
        addConfig(
            "QuickToggleAnonymous",
            ConfigItem.configTypeBool,
            false
        )
    val realHideTimeForSticker =
        addConfig(
            "RealHideTimeForSticker",
            ConfigItem.configTypeBool,
            false
        )
    val ignoreFolderCount =
        addConfig(
            "IgnoreFolderCount",
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
    val fakeHighPerformanceDevice =
        addConfig(
            "FakeHighPerformanceDevice",
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
            false
        )
    val hideMessageSeenTooltip =
        addConfig(
            "HideMessageSeenTooltip",
            ConfigItem.configTypeBool,
            false
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
    val disableSendReadStories =
        addConfig(
            "DisableSendReadStories",
            ConfigItem.configTypeBool,
            true
        )
    val hideFilterMuteAll =
        addConfig(
            "HideFilterMuteAll",
            ConfigItem.configTypeBool,
            false
        )
    val useLocalQuoteColor =
        addConfig(
            "UseLocalQuoteColor",
            ConfigItem.configTypeBool,
            false
        )
    val useLocalQuoteColorData =
        addConfig(
            "useLocalQuoteColorData",
            ConfigItem.configTypeString,
            ""
        )
    val showRecentOnlineStatus =
        addConfig(
            "ShowRecentOnlineStatus",
            ConfigItem.configTypeBool,
            false
        )
    val showSquareAvatar =
        addConfig(
            "ShowSquareAvatar",
            ConfigItem.configTypeBool,
            false
        )
    val disableCustomWallpaperUser =
        addConfig(
            "DisableCustomWallpaperUser",
            ConfigItem.configTypeBool,
            false
        )
    val disableCustomWallpaperChannel =
        addConfig(
            "DisableCustomWallpaperChannel",
            ConfigItem.configTypeBool,
            false
        )
    val externalStickerCache =
        addConfig(
            "ExternalStickerCache",
            ConfigItem.configTypeString,
            ""
        )
    var externalStickerCacheUri: Uri?
        get() = externalStickerCache.String().let { if (it.isBlank()) return null else return it.toUri() }
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
    val disableClickProfileGalleryView =
        addConfig(
            "DisableClickProfileGalleryView",
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
            true
        )
    val showTimeHint =
        addConfig(
            "ShowTimeHint",
            ConfigItem.configTypeBool,
            true
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
    val openUrlOutBotWebViewRegex =
        addConfig(
            "OpenUrlOutBotWebViewRegex",
            ConfigItem.configTypeString,
            ""
        )
    val enablePanguOnSending =
        addConfig(
            "EnablePanguOnSending",
            ConfigItem.configTypeBool,
            false
        )
    val enablePanguOnReceiving =
        addConfig(
            "EnablePanguOnReceiving",
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
            true
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
            true
        )
    val saveDeletedMessageForBotUser =
        addConfig(
            "SaveDeletedMessageForBotUser", // all messages from bot
            ConfigItem.configTypeBool,
            true
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
            0 // 0: append; 1: replace; 2: pop-up
        )
    val centerActionBarTitleType =
        addConfig(
            "CenterActionBarTitleType",
            ConfigItem.configTypeInt,
            1 // 0: off; 1: always on; 2: settings only; 3: chats only
        )
    var drawerItemMyProfile =
        addConfig(
            "DrawerItemMyProfile",
            ConfigItem.configTypeBool,
            true
        )
    var drawerItemSetEmojiStatus =
        addConfig(
            "DrawerItemSetEmojiStatus",
            ConfigItem.configTypeBool,
            true
        )
    var drawerItemNewGroup =
        addConfig(
            "DrawerItemNewGroup",
            ConfigItem.configTypeBool,
            true
        )
    var drawerItemNewChannel =
        addConfig(
            "DrawerItemNewChannel",
            ConfigItem.configTypeBool,
            false
        )
    var drawerItemContacts =
        addConfig(
            "DrawerItemContacts",
            ConfigItem.configTypeBool,
            true
        )
    var drawerItemCalls =
        addConfig(
            "DrawerItemCalls",
            ConfigItem.configTypeBool,
            true
        )
    var drawerItemSaved =
        addConfig(
            "DrawerItemSaved",
            ConfigItem.configTypeBool,
            true
        )
    var drawerItemSettings =
        addConfig(
            "DrawerItemSettings",
            ConfigItem.configTypeBool,
            true
        )
    var drawerItemNSettings =
        addConfig(
            "DrawerItemNSettings",
            ConfigItem.configTypeBool,
            true
        )
    var drawerItemQrLogin =
        addConfig(
            "DrawerItemQrLogin",
            ConfigItem.configTypeBool,
            false
        )
    var drawerItemArchivedChats =
        addConfig(
            "DrawerItemArchivedChats",
            ConfigItem.configTypeBool,
            false
        )
    var drawerItemRestartApp =
        addConfig(
            "DrawerItemRestartApp",
            ConfigItem.configTypeBool,
            false
        )
    var hideArchive =
        addConfig(
            "HideArchive",
            ConfigItem.configTypeBool,
            false
        )
    var hideChannelSilentBroadcast =
        addConfig(
            "HideChannelSilentBroadcast",
            ConfigItem.configTypeBool,
            false
        )
    var confirmAllLinks =
        addConfig(
            "ConfirmAllLinks",
            ConfigItem.configTypeBool,
            false
        )
    var useDeletedIcon =
        addConfig(
            "UseDeletedIcon",
            ConfigItem.configTypeBool,
            true
        )
    var useEditedIcon =
        addConfig(
            "UseEditedIcon",
            ConfigItem.configTypeBool,
            true
        )
    var saveToChatSubfolder =
        addConfig(
            "SaveToChatSubfolder",
            ConfigItem.configTypeBool,
            false
        )
    var silentMessageByDefault =
        addConfig(
            "SilentMessageByDefault",
            ConfigItem.configTypeBool,
            false
        )
    var folderNameAsTitle =
        addConfig(
            "FolderNameAsTitle",
            ConfigItem.configTypeBool,
            false
        )
    var translatorKeepMarkdown =
        addConfig(
            "TranslatorKeepMarkdown",
            ConfigItem.configTypeBool,
            false
        )
    var googleTranslateExp =
        addConfig(
            "GoogleTranslateExp",
            ConfigItem.configTypeBool,
            true
        )
    var springAnimation =
        addConfig(
            "SpringAnimation",
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
            NaConfig.showRPCError.setConfigBool(false);
        }
    }
}
