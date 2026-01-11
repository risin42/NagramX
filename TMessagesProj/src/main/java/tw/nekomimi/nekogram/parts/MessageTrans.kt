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
import org.telegram.messenger.MessagesController
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

@JvmName("translateMessages")
fun ChatActivity.translateMessages1() = translateMessages()

@JvmName("translateMessages")
fun ChatActivity.translateMessages2(target: Locale, provider: Int = 0) =
    translateMessages(target, provider = provider)

@JvmName("translateMessages")
fun ChatActivity.translateMessages3(messages: List<MessageObject>) =
    translateMessages(messages = messages)

@JvmName("translateMessages")
fun ChatActivity.translateMessages4(provider: Int) = translateMessages(provider = provider)

fun ChatActivity.translateMessages(
    target: Locale = NekoConfig.translateToLang.String().code2Locale,
    messages: List<MessageObject> = messageForTranslate?.let { listOf(it) }
        ?: selectedObjectGroup?.messages ?: emptyList(),
    provider: Int = 0
) {
    if (messages.any { it.translating }) {
        return
    }

    val controller = MessagesController.getInstance(currentAccount).translateController
    val translatorMode = NaConfig.translatorMode.Int()

    // hide translation
    if (messages.all { it.messageOwner.translated || it.translated }) {
        messages.forEach { messageObject ->
            controller.removeAsTranslatingItem(messageObject)
            controller.removeAsManualTranslate(messageObject)
            messageObject.translating = false
            messageObject.messageOwner.translated = false
            if (translatorMode == TRANSLATE_MODE_APPEND || messageObject.isPoll) {
                AndroidUtilities.runOnUIThread {
                    messageHelper.resetMessageContent(dialogId, messageObject)
                }
            } else {
                AndroidUtilities.runOnUIThread {
                    notificationCenter.postNotificationName(NotificationCenter.messageTranslated, messageObject)
                }
            }
        }
        return
        // translation for this message is already available in that case
    } else if (translatorMode == TRANSLATE_MODE_REPLACE && provider == 0 && (messages.any { it.messageOwner.translatedText?.text?.isNotEmpty() == true } || messages.any { it.messageOwner.translatedPoll?.question?.text?.isNotEmpty() == true })) {
        messages.forEach { messageObject ->
            if (messageObject.messageOwner.translatedToLanguage.equals(
                    target.toLanguageTag(), ignoreCase = true
                )
            ) {
                controller.removeAsTranslatingItem(messageObject)
                controller.addAsManualTranslate(messageObject)
                messageObject.translating = false
                messageObject.messageOwner.translated = true
                AndroidUtilities.runOnUIThread {
                    notificationCenter.postNotificationName(NotificationCenter.messageTranslating, messageObject)
                    notificationCenter.postNotificationName(NotificationCenter.messageTranslated, messageObject)
                }
            }
        }
    } else if ((translatorMode == TRANSLATE_MODE_APPEND && provider == 0 && messages.any { it.messageOwner.translatedMessage?.isNotEmpty() == true }) || messages.any {
            isTranslatedPoll(
                it
            )
        }) {
        messages.forEach { messageObject ->
            if (messageObject.messageOwner.translatedToLanguage.equals(
                    target.toLanguageTag(), ignoreCase = true
                )
            ) {
                controller.removeAsTranslatingItem(messageObject)
                controller.addAsManualTranslate(messageObject)
                messageObject.translating = false
                messageObject.messageOwner.translated = true
                AndroidUtilities.runOnUIThread {
                    messageHelper.resetMessageContent(dialogId, messageObject)
                }
            }
        }
    }

    val messagesToTranslate =
        messages.filter { !it.translating && !it.messageOwner.translated && (it.isPoll || it.messageOwner.message.isNotEmpty()) }
    if (messagesToTranslate.isEmpty()) {
        return
    }

    messagesToTranslate.forEach { messageObject ->
        MessageHelper.getInstance(currentAccount).detectLanguageNow(messageObject)
        controller.addAsTranslatingItem(messageObject)
        controller.addAsManualTranslate(messageObject)
        messageObject.translating = true
        notificationCenter.postNotificationName(NotificationCenter.messageTranslating, messageObject)
    }

    val dispatcher = Dispatchers.IO.limitedParallelism(5)

    AppScope.io.launch {
        supervisorScope {
            val jobs = messagesToTranslate.map { selectedObject ->
                launch(dispatcher) {
                    if (!isActive) return@launch
                    if (selectedObject.isPoll) {
                        val poll = (selectedObject.messageOwner.media as TLRPC.TL_messageMediaPoll).poll

                        var translatedQuestion: String? = null
                        runCatching {
                            translatedQuestion = Translator.translate(target, poll.question.text, provider)
                        }.onFailure { e ->
                            handleTranslationError(parentActivity, e, controller, selectedObject) {
                                translateMessages(target, listOf(selectedObject), provider)
                            }
                            return@launch
                        }
                        poll.translatedQuestion = translatedQuestion

                        poll.answers.forEach {
                            var translatedAnswer: String? = null
                            runCatching {
                                translatedAnswer = Translator.translate(target, it.text.text, provider)
                            }.onFailure { e ->
                                handleTranslationError(
                                    parentActivity, e, controller, selectedObject
                                ) {
                                    translateMessages(target, listOf(selectedObject), provider)
                                }
                                return@launch
                            }
                            it.translatedText = translatedAnswer
                        }
                    } else {
                        var result: TLRPC.TL_textWithEntities? = null

                        runCatching {
                            val shouldUseContext = shouldUseLlmContext(provider)
                            val context = if (shouldUseContext) buildLlmContext(this@translateMessages, selectedObject) else null
                            result = if (shouldUseContext) {
                                LLMTranslator.withTranslationContext(context) {
                                    Translator.translate(
                                        target,
                                        selectedObject.messageOwner.message,
                                        selectedObject.messageOwner.entities,
                                        provider
                                    )
                                }
                            } else {
                                Translator.translate(
                                    target,
                                    selectedObject.messageOwner.message,
                                    selectedObject.messageOwner.entities,
                                    provider
                                )
                            }
                        }.onFailure { e ->
                            handleTranslationError(parentActivity, e, controller, selectedObject) {
                                translateMessages(target, listOf(selectedObject), provider)
                            }
                            return@launch
                        }

                        selectedObject.messageOwner.translatedMessage =
                            "${if (translatorMode == TRANSLATE_MODE_APPEND) selectedObject.messageOwner.message + TRANSLATION_SEPARATOR else ""}${result?.text}"

                        if (result != null) {
                            selectedObject.messageOwner.translatedText = result
                        }
                    }
                    controller.removeAsTranslatingItem(selectedObject)
                    selectedObject.translating = false
                    selectedObject.messageOwner.translated = true
                    selectedObject.messageOwner.translatedToLanguage = target.locale2code.lowercase(Locale.getDefault())

                    if (!BuildVars.LOGS_ENABLED) {
                        MessagesStorage.getInstance(currentAccount).updateMessageCustomParams(
                            selectedObject.dialogId, selectedObject.messageOwner
                        )
                    }

                    if (selectedObject.messageOwner.translatedText != null && translatorMode == TRANSLATE_MODE_REPLACE) {
                        AndroidUtilities.runOnUIThread {
                            notificationCenter.postNotificationName(NotificationCenter.messageTranslated, selectedObject)
                            notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, 0)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            clearTranslated(selectedObject, currentAccount, translatorMode != TRANSLATE_MODE_APPEND)
                            messageHelper.resetMessageContent(dialogId, selectedObject)
                        }
                    }
                }
            }
            jobs.joinAll()
        }
    }
}

private fun shouldUseLlmContext(provider: Int): Boolean {
    val effectiveProvider = provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int()
    return effectiveProvider == Translator.providerLLMTranslator && NaConfig.llmUseContext.Bool()
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
                ?: messages.indexOfFirst { it.dialogId == message.dialogId && it.id == message.id }.takeIf { it >= 0 }
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

private fun extractLlmContextText(message: MessageObject): String? {
    val text = MessageHelper.getMessagePlainText(message, null)?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (MessageHelper.shouldSkipTranslation(text)) return null
    return text
}

private fun handleTranslationError(
    parentActivity: Context?,
    throwable: Throwable,
    controller: TranslateController,
    messageObject: MessageObject,
    retryAction: () -> Unit
) {
    controller.removeAsTranslatingItem(messageObject)
    controller.removeAsManualTranslate(messageObject)
    messageObject.translating = false

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

private fun isTranslatedPoll(messageObject: MessageObject) =
    messageObject.isPoll && (messageObject.messageOwner.media as? TLRPC.TL_messageMediaPoll)?.poll?.let { poll ->
        poll.translatedQuestion?.isNotEmpty() == true && poll.answers.all { it.translatedText?.isNotEmpty() == true }
    } ?: false

private fun clearTranslated(messageObject: MessageObject, currentAccount: Int, clearTranslatedText: Boolean) {
    if (clearTranslatedText) {
        messageObject.messageOwner.translatedText = null
    }
    messageObject.messageOwner.translatedPoll = null
    MessagesStorage.getInstance(currentAccount).updateMessageCustomParams(
        messageObject.dialogId, messageObject.messageOwner
    )
}