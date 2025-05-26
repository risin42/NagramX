package tw.nekomimi.nekogram.parts

import android.content.Context
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.TranslateController
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ChatActivity
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.code2Locale
import tw.nekomimi.nekogram.translate.locale2code
import tw.nekomimi.nekogram.utils.AlertUtil
import xyz.nextalone.nagram.NaConfig
import java.util.LinkedList
import java.util.Locale

const val TRANSLATE_MODE_APPEND = 0
const val TRANSLATE_MODE_REPLACE = 1

@JvmName("translateMessages")
fun ChatActivity.translateMessages1() = translateMessages()

@JvmName("translateMessages")
fun ChatActivity.translateMessages2(target: Locale) = translateMessages(target)

@JvmName("translateMessages")
fun ChatActivity.translateMessages3(messages: List<MessageObject>) = translateMessages(messages = messages)

@JvmName("translateMessages")
fun ChatActivity.translateMessages4(provider: Int) = translateMessages(provider = provider)

@OptIn(DelicateCoroutinesApi::class)
fun ChatActivity.translateMessages(
    target: Locale = NekoConfig.translateToLang.String().code2Locale,
    messages: List<MessageObject> = messageForTranslate?.let { listOf(it) } ?: selectedObjectGroup?.messages ?: emptyList(),
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
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageTranslated, messageObject)
                }
            }
        }
        return
    // translation for this message is already available in that case
    } else if (
        translatorMode == TRANSLATE_MODE_REPLACE && provider == 0 &&
            (messages.any { it.messageOwner.translatedText?.text?.isNotEmpty() == true } || messages.any { it.messageOwner.translatedPoll?.question?.text?.isNotEmpty() == true })
    ) {
        messages.forEach { messageObject ->
            if (messageObject.messageOwner.translatedToLanguage.equals(target.toLanguageTag(), ignoreCase = true)) {
                controller.removeAsTranslatingItem(messageObject)
                controller.addAsManualTranslate(messageObject)
                messageObject.translating = false
                messageObject.messageOwner.translated = true
                AndroidUtilities.runOnUIThread {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageTranslating, messageObject)
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageTranslated, messageObject)
                }
            }
        }
    } else if (
        (translatorMode == TRANSLATE_MODE_APPEND && provider == 0 &&
                messages.any { it.messageOwner.translatedMessage?.isNotEmpty() == true }
        ) || messages.any { isTranslatedPoll(it) }
    ) {
        messages.forEach { messageObject ->
            if (messageObject.messageOwner.translatedToLanguage.equals(target.toLanguageTag(), ignoreCase = true)) {
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

    val messagesToTranslate = messages.filter { !it.translating && !it.messageOwner.translated }
    if (messagesToTranslate.isEmpty()) {
        return
    }

    messagesToTranslate.forEach { messageObject ->
        controller.addAsTranslatingItem(messageObject)
        controller.addAsManualTranslate(messageObject)
        messageObject.translating = true
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageTranslating, messageObject)
    }

    val deferred = LinkedList<Deferred<Unit>>()
    val transPool = newFixedThreadPoolContext(5, "Message Trans Pool")

    GlobalScope.launch(Dispatchers.IO) {
        messagesToTranslate.forEachIndexed { _, selectedObject ->
            deferred.add(async(transPool) trans@{
                if (selectedObject.isPoll) {
                    val pool = (selectedObject.messageOwner.media as TLRPC.TL_messageMediaPoll).poll
                    var translatedQuestion: String? = null
                    runCatching {
                        translatedQuestion = Translator.translate(target, pool.question.text, provider)
                    }.onFailure { e ->
                        handleTranslationError(parentActivity, e, controller, selectedObject) {
                            translateMessages(target, messagesToTranslate)
                        }
                        return@trans
                    }
                    pool.translatedQuestion = translatedQuestion

                    pool.answers.forEach {
                        var translatedAnswer: String? = null
                        runCatching {
                            translatedAnswer = Translator.translate(target, it.text.text, provider)
                        }.onFailure { e ->
                            handleTranslationError(parentActivity, e, controller, selectedObject) {
                                translateMessages(target, messagesToTranslate)
                            }
                            return@trans
                        }
                        it.translatedText = translatedAnswer
                    }
                } else {
                    var result: TLRPC.TL_textWithEntities? = null
                    runCatching {
                        result = Translator.translate(
                            target,
                            selectedObject.messageOwner.message,
                            selectedObject.messageOwner.entities,
                            provider
                        )
                    }.onFailure { e ->
                        handleTranslationError(parentActivity, e, controller, selectedObject) {
                            translateMessages(target, messagesToTranslate)
                        }
                        return@trans
                    }
                    if (result != null) {
                        selectedObject.messageOwner.translatedMessage = selectedObject.messageOwner.message + "\n\n--------\n\n" + result?.text
                        selectedObject.messageOwner.translatedText = result
                    }
                }
                controller.removeAsTranslatingItem(selectedObject)
                selectedObject.messageOwner.translated = true
                selectedObject.messageOwner.translatedToLanguage = target.locale2code.lowercase(Locale.getDefault())

                MessagesStorage.getInstance(currentAccount).updateMessageCustomParams(
                    selectedObject.dialogId,
                    selectedObject.messageOwner
                )

                if (selectedObject.messageOwner.translatedText != null) {
                    AndroidUtilities.runOnUIThread {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageTranslated, selectedObject)
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        messageHelper.resetMessageContent(dialogId, selectedObject)
                    }
                }
            })
        }
        deferred.awaitAll()
        transPool.cancel()
        transPool.close()
    }
    messagesToTranslate.forEach { messageObject ->
        messageObject.translating = false
    }
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

private fun isTranslatedPoll(messageObject: MessageObject) = messageObject.isPoll &&
    (messageObject.messageOwner.media as? TLRPC.TL_messageMediaPoll)?.poll?.let { poll ->
        poll.translatedQuestion?.isNotEmpty() == true &&
        poll.answers.all { it.translatedText?.isNotEmpty() == true }
    } ?: false
