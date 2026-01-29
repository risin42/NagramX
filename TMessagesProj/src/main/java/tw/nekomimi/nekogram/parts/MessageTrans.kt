package tw.nekomimi.nekogram.parts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildVars
import org.telegram.messenger.ChatObject
import org.telegram.messenger.DialogObject
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.TranslateController
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ChatActivity
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.helpers.MessageHelper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.code2Locale
import tw.nekomimi.nekogram.translate.locale2code
import tw.nekomimi.nekogram.translate.source.LLMTranslator
import tw.nekomimi.nekogram.utils.AlertUtil
import tw.nekomimi.nekogram.utils.AppScope
import xyz.nextalone.nagram.NaConfig
import java.util.Locale

const val TRANSLATE_MODE_APPEND = 0
const val TRANSLATE_MODE_REPLACE = 1

const val TRANSLATION_SEPARATOR = "\n\n--------\n\n"

private val ChatActivity.translateController: TranslateController
    get() = messagesController.translateController


@JvmName("translateMessages")
fun ChatActivity.translateMessagesWithProvider(provider: Int) =
    translateMessages(provider = provider)

@JvmName("translateMessages")
fun ChatActivity.translateMessagesWithMessages(messages: List<MessageObject>) =
    translateMessages(messages = messages)

@JvmOverloads
fun ChatActivity.translateMessages(
    targetLocale: Locale = NekoConfig.translateToLang.String().code2Locale,
    provider: Int = 0,
    messages: List<MessageObject> = messageForTranslate?.let { listOf(it) }
        ?: selectedObjectGroup?.messages
        ?: emptyList()
) {
    if (messages.any { translateController.isTranslating(it) }) return

    val targetLanguage = targetLocale.toLanguageTag()
    val translatorMode = NaConfig.translatorMode.Int()
    val canReuseCache = provider == 0

    // Check if all messages are already translated, hide translation if so
    if (messages.all { it.isTranslated }) {
        messages.forEach { msg ->
            hideTranslation(msg, translatorMode)
        }
        return
    }

    // Try to use cached translation if available
    if (canReuseCache) {
        applyCachedTranslations(messages, targetLanguage, translatorMode)
    }

    // Filter messages that actually need translation
    val messagesToTranslate = messages.filter { msg ->
        msg.needsTranslation(canReuseCache, targetLanguage, translatorMode, translateController)
    }

    if (messagesToTranslate.isEmpty()) return

    // Mark messages as translating
    messagesToTranslate.forEach { msg ->
        messageHelper.detectLanguageNow(msg)
        translateController.addAsTranslatingItem(msg)
        translateController.addAsManualTranslate(msg)
        notificationCenter.postNotificationName(NotificationCenter.messageTranslating, msg)
    }

    // Perform translations concurrently
    val dispatcher = Dispatchers.IO.limitedParallelism(5)

    AppScope.io.launch {
        supervisorScope {
            messagesToTranslate.map { msg ->
                launch(dispatcher) {
                    if (!isActive) return@launch
                    translateSingleMessage(
                        msg,
                        provider,
                        targetLocale,
                        targetLanguage,
                        translatorMode,
                        canReuseCache
                    )
                }
            }.joinAll()
        }
    }
}

private suspend fun ChatActivity.translateSingleMessage(
    msg: MessageObject,
    provider: Int,
    targetLocale: Locale,
    targetLanguage: String,
    translatorMode: Int,
    canReuseCache: Boolean,
) {
    val needsSummary = msg.needsSummaryTranslation(canReuseCache, targetLanguage)
    val needsOriginal = msg.needsOriginalTranslation(canReuseCache, targetLanguage, translatorMode)

    val shouldUseContext = shouldUseLlmContext(provider)
    val llmContext = if (shouldUseContext) {
        buildLlmContext(this@translateSingleMessage, msg)
    } else null

    // Translate summary if needed
    if (needsSummary) {
        val success =
            translateSummary(msg, targetLocale, provider, translatorMode, llmContext)
        if (!success) return
    }

    // Translate original content if needed
    if (needsOriginal) {
        val success = if (msg.isPoll) {
            translatePoll(msg, targetLocale, provider)
        } else {
            translateMessageContent(
                msg,
                targetLocale,
                provider,
                translatorMode,
                llmContext,
            )
        }
        if (!success) return

        msg.messageOwner.translatedToLanguage =
            targetLocale.locale2code.lowercase(Locale.getDefault())
    }

    // Mark translation as complete
    finalizeTranslation(msg, translatorMode)
}

private suspend fun ChatActivity.translateSummary(
    msg: MessageObject,
    targetLocale: Locale,
    provider: Int,
    translatorMode: Int,
    llmContext: String?,
): Boolean {
    val summaryText = msg.messageOwner.summaryText ?: return false

    // Translate summary
    val translatedSummary = runCatching {
        translateText(targetLocale, summaryText.text, summaryText.entities, provider, llmContext)
    }.getOrElse { e ->
        handleTranslationError(parentActivity, e, msg, translateController) {
            translateMessages(targetLocale, provider, listOf(msg))
        }
        return false
    }

    // Store translated summary
    msg.messageOwner.translatedSummaryText = if (translatorMode == TRANSLATE_MODE_APPEND) {
        TLRPC.TL_textWithEntities().apply {
            text = summaryText.text + TRANSLATION_SEPARATOR + translatedSummary.text
            entities = MessageHelper.mergeAppendTranslatedEntities(
                summaryText.entities, translatedSummary, text
            )
        }
    } else {
        translatedSummary
    }
    msg.messageOwner.translatedSummaryLanguage = targetLocale.locale2code.lowercase(Locale.getDefault())

    return true
}

private suspend fun ChatActivity.translatePoll(
    msg: MessageObject,
    target: Locale,
    provider: Int
): Boolean {
    val poll = (msg.messageOwner.media as TLRPC.TL_messageMediaPoll).poll

    // Translate question
    val translatedQuestion = runCatching {
        Translator.translate(target, poll.question.text, provider)
    }.getOrElse { e ->
        handleTranslationError(parentActivity, e, msg, translateController) {
            translateMessages(target, provider, listOf(msg))
        }
        return false
    }
    poll.translatedQuestion = translatedQuestion

    // Translate answers
    for (answer in poll.answers) {
        val translatedAnswer = runCatching {
            Translator.translate(target, answer.text.text, provider)
        }.getOrElse { e ->
            handleTranslationError(parentActivity, e, msg, translateController) {
                translateMessages(target, provider, listOf(msg))
            }
            return false
        }
        answer.translatedText = translatedAnswer
    }

    return true
}

private suspend fun ChatActivity.translateMessageContent(
    msg: MessageObject,
    target: Locale,
    provider: Int,
    translatorMode: Int,
    llmContext: String?
): Boolean {
    val result = runCatching {
        translateText(
            target,
            msg.messageOwner.message,
            msg.messageOwner.entities,
            provider,
            llmContext
        )
    }.getOrElse { e ->
        handleTranslationError(parentActivity, e, msg, translateController) {
            translateMessages(target, provider, listOf(msg))
        }
        return false
    }

    val originalMessage = msg.messageOwner.message
    msg.messageOwner.translatedMessage = if (translatorMode == TRANSLATE_MODE_APPEND) {
        originalMessage + TRANSLATION_SEPARATOR + result.text
    } else {
        result.text
    }
    msg.messageOwner.translatedText = result

    return true
}

private suspend fun ChatActivity.finalizeTranslation(
    msg: MessageObject,
    translatorMode: Int
) {
    translateController.removeAsTranslatingItem(msg)
    msg.messageOwner.translated = true

    // Persist translation to storage
    if (!BuildVars.LOGS_ENABLED) {
        MessagesStorage.getInstance(currentAccount).updateMessageCustomParams(
            msg.dialogId, msg.messageOwner
        )
    }

    // Update UI
    if (msg.messageOwner.translatedText != null && translatorMode == TRANSLATE_MODE_REPLACE) {
        AndroidUtilities.runOnUIThread {
            postTranslatedNotification(msg)
            notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, 0)
        }
    } else {
        withContext(Dispatchers.Main) {
            clearTranslated(msg, currentAccount, translatorMode != TRANSLATE_MODE_APPEND)
            messageHelper.resetMessageContent(dialogId, msg)
        }
    }
}

private fun ChatActivity.postTranslatedNotification(msg: MessageObject) {
    if (msg.messageOwner.summarizedOpen) {
        notificationCenter.postNotificationName(
            NotificationCenter.messageTranslated, msg, true
        )
    } else {
        notificationCenter.postNotificationName(
            NotificationCenter.messageTranslated, msg
        )
    }
}

private fun ChatActivity.hideTranslation(
    msg: MessageObject,
    translatorMode: Int,
) {
    translateController.removeAsTranslatingItem(msg)
    translateController.removeAsManualTranslate(msg)
    msg.messageOwner.translated = false

    AndroidUtilities.runOnUIThread {
        if (translatorMode == TRANSLATE_MODE_APPEND || msg.isPoll) {
            messageHelper.resetMessageContent(dialogId, msg)
        } else {
            postTranslatedNotification(msg)
        }
    }
}

private fun ChatActivity.applyCachedTranslations(
    messages: List<MessageObject>,
    targetLanguage: String,
    translatorMode: Int,
) {
    val hasCachedReplace = translatorMode == TRANSLATE_MODE_REPLACE && messages.any { msg ->
        msg.messageOwner.translatedText?.text?.isNotEmpty() == true ||
                msg.messageOwner.translatedPoll?.question?.text?.isNotEmpty() == true ||
                (msg.messageOwner.summarizedOpen &&
                        msg.messageOwner.translatedSummaryText?.text?.isNotEmpty() == true)
    }

    val hasCachedAppend = translatorMode == TRANSLATE_MODE_APPEND && messages.any { msg ->
        msg.messageOwner.translatedMessage?.isNotEmpty() == true
    }

    val hasCachedPoll = messages.any { it.isTranslatedPoll() }

    val hasCachedSummary = messages.any { msg ->
        msg.messageOwner.summarizedOpen &&
                msg.messageOwner.translatedSummaryText?.text?.isNotEmpty() == true
    }

    if (!hasCachedReplace && !hasCachedAppend && !hasCachedPoll && !hasCachedSummary) return

    messages.forEach { msg ->
        if (!msg.matchesCachedLanguage(targetLanguage)) return@forEach

        translateController.removeAsTranslatingItem(msg)
        translateController.addAsManualTranslate(msg)
        msg.messageOwner.translated = true

        AndroidUtilities.runOnUIThread {
            when {
                translatorMode == TRANSLATE_MODE_REPLACE -> {
                    notificationCenter.postNotificationName(
                        NotificationCenter.messageTranslating, msg
                    )
                    postTranslatedNotification(msg)
                }

                msg.messageOwner.summarizedOpen -> {
                    postTranslatedNotification(msg)
                    notificationCenter.postNotificationName(
                        NotificationCenter.updateInterfaces, 0
                    )
                }

                else -> {
                    messageHelper.resetMessageContent(dialogId, msg)
                }
            }
        }
    }
}

private fun MessageObject.needsTranslation(
    canReuseCache: Boolean,
    targetLanguage: String,
    translatorMode: Int,
    controller: TranslateController
): Boolean {
    if (controller.isTranslating(this)) return false
    if (!(isPoll || messageOwner.message.isNotEmpty())) return false

    val needsSummary = needsSummaryTranslation(canReuseCache, targetLanguage)
    val needsOriginal = needsOriginalTranslation(canReuseCache, targetLanguage, translatorMode)

    return needsSummary || needsOriginal
}

private fun MessageObject.needsSummaryTranslation(
    canReuseCache: Boolean,
    targetLanguage: String
): Boolean {
    if (!messageOwner.summarizedOpen) return false

    return !canReuseCache ||
            messageOwner.translatedSummaryText?.text.isNullOrEmpty() ||
            !messageOwner.translatedSummaryLanguage.equals(targetLanguage, ignoreCase = true)
}

private fun MessageObject.needsOriginalTranslation(
    canReuseCache: Boolean,
    targetLanguage: String,
    translatorMode: Int
): Boolean {
    if (!canReuseCache) return true

    val languageMatches =
        messageOwner.translatedToLanguage.equals(targetLanguage, ignoreCase = true)

    return when {
        isPoll -> !isTranslatedPoll() || !languageMatches

        translatorMode == TRANSLATE_MODE_REPLACE ->
            messageOwner.translatedText?.text.isNullOrEmpty() || !languageMatches

        else ->
            messageOwner.translatedMessage.isNullOrEmpty() || !languageMatches
    }
}

private fun MessageObject.matchesCachedLanguage(targetLanguage: String): Boolean {
    return if (messageOwner.summarizedOpen) {
        messageOwner.translatedSummaryText?.text?.isNotEmpty() == true &&
                messageOwner.translatedSummaryLanguage.equals(targetLanguage, ignoreCase = true)
    } else {
        messageOwner.translatedToLanguage.equals(targetLanguage, ignoreCase = true)
    }
}

private fun MessageObject.isTranslatedPoll() =
    isPoll && (messageOwner.media as? TLRPC.TL_messageMediaPoll)?.poll?.let { poll ->
        poll.translatedQuestion?.isNotEmpty() == true && poll.answers.all { it.translatedText?.isNotEmpty() == true }
    } ?: false

private suspend fun translateText(
    target: Locale,
    text: String,
    entities: ArrayList<TLRPC.MessageEntity>?,
    provider: Int,
    llmContext: String?
): TLRPC.TL_textWithEntities {
    val safeEntities = entities ?: ArrayList()

    return if (llmContext != null) {
        LLMTranslator.withTranslationContext(llmContext) {
            Translator.translate(target, text, safeEntities, provider)
        }
    } else {
        Translator.translate(target, text, safeEntities, provider)
    }
}

private fun handleTranslationError(
    parentActivity: Context?,
    throwable: Throwable,
    messageObject: MessageObject,
    controller: TranslateController,
    retryAction: () -> Unit
) {
    controller.removeAsTranslatingItem(messageObject)
    controller.removeAsManualTranslate(messageObject)

    if (parentActivity != null) {
        AlertUtil.showTransFailedDialog(
            parentActivity,
            throwable is UnsupportedOperationException,
            throwable.message ?: throwable.javaClass.simpleName
        ) {
            retryAction()
        }
    }
}

private fun clearTranslated(
    messageObject: MessageObject,
    currentAccount: Int,
    clearTranslatedText: Boolean
) {
    if (clearTranslatedText) {
        messageObject.messageOwner.translatedText = null
    }
    messageObject.messageOwner.translatedPoll = null
    MessagesStorage.getInstance(currentAccount).updateMessageCustomParams(
        messageObject.dialogId, messageObject.messageOwner
    )
}

private fun shouldUseLlmContext(provider: Int): Boolean {
    val effectiveProvider = provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int()
    return effectiveProvider == Translator.providerLLMTranslator && NaConfig.llmUseContext.Bool()
}

private fun extractLlmContextText(message: MessageObject): String? {
    val text = MessageHelper.getMessagePlainText(message, null)?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (MessageHelper.shouldSkipTranslation(text)) return null
    return text
}

private fun buildLlmContext(chatActivity: ChatActivity, message: MessageObject): String? {
    val maxMessages = LLMTranslator.getContextMessageLimit()
    if (maxMessages <= 0) return null

    val seen = HashSet<String>(maxMessages * 2)

    fun extractWithDedup(candidate: MessageObject): String? {
        val key = "${candidate.dialogId}:${candidate.id}"
        if (!seen.add(key)) return null
        return extractLlmContextText(candidate)
    }

    // Reply chain
    val replyChainTexts = ArrayList<String>()
    var reply = message.replyMessageObject
    var isDirectReplyIncluded = false
    while (reply != null && replyChainTexts.size < maxMessages) {
        val text = extractWithDedup(reply)
        if (text != null) {
            replyChainTexts.add(text)
            if (reply === message.replyMessageObject) isDirectReplyIncluded = true
        }
        reply = reply.replyMessageObject
    }
    if (replyChainTexts.isNotEmpty()) {
        replyChainTexts.reverse() // oldest first
    }

    // Context messages
    val contextTexts = ArrayList<String>()
    val currentChat = chatActivity.currentChat
    if (currentChat == null || !ChatObject.isChannelAndNotMegaGroup(currentChat)) {
        val messages = chatActivity.chatAdapter?.messages
        if (messages != null) {
            val index = messages.indexOf(message).takeIf { it >= 0 }
                ?: messages.indexOfFirst { it.dialogId == message.dialogId && it.id == message.id }
                    .takeIf { it >= 0 }
            if (index != null) {
                val remaining = maxMessages - replyChainTexts.size
                for (i in (index + 1) until messages.size) {
                    if (contextTexts.size >= remaining) break
                    val msg = messages[i]
                    if (msg.isAyuDeleted) continue
                    extractWithDedup(msg)?.let { contextTexts.add(it) }
                }
                if (contextTexts.isNotEmpty()) {
                    contextTexts.reverse() // oldest first
                }
            }
        }
    }

    if (replyChainTexts.isEmpty() && contextTexts.isEmpty()) return null

    return buildString {
        val dialogTitle = DialogObject.getName(chatActivity.currentAccount, message.dialogId).trim()
        if (dialogTitle.isNotEmpty()) {
            append("Chat: ").append(dialogTitle).append("\n\n")
        }

        if (replyChainTexts.isNotEmpty()) {
            append("Reply chain (oldest → newest):\n")
            replyChainTexts.forEachIndexed { index, text ->
                append("R").append(index + 1).append(": ").append(text).append('\n')
            }
            if (isDirectReplyIncluded) {
                append("\nMessage to translate replies to: R").append(replyChainTexts.size).append('\n')
            } else if (message.replyMessageObject != null) {
                append("\nMessage to translate is a reply, but the replied message text is unavailable.\n")
            }
            append('\n')
        }

        if (contextTexts.isNotEmpty()) {
            append("Other context messages (chronological):\n")
            contextTexts.forEachIndexed { index, text ->
                append("C").append(index + 1).append(": ").append(text).append('\n')
            }
        }
    }.trim().takeIf { it.isNotEmpty() }
}
