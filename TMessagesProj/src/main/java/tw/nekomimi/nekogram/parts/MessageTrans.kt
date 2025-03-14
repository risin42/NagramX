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
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ChatActivity
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.transtale.TranslateDb
import tw.nekomimi.nekogram.transtale.Translator
import tw.nekomimi.nekogram.transtale.code2Locale
import tw.nekomimi.nekogram.utils.AlertUtil
import xyz.nextalone.nagram.NaConfig
import java.util.LinkedList
import java.util.Locale

const val TRANSLATE_MODE_APPEND = 0
const val TRANSLATE_MODE_REPLACE = 1
const val TRANSLATE_MODE_POPUP = 2

/**
 * Checks if translations are already available in the local database for this MessageObject.
 *
 * @param locale The target locale for translation.
 * @return 0 if translations are not found in the database and translation is needed,
 *         1 if the message has no translatable text (e.g., empty message),
 *         2 if translations are successfully loaded from the database.
 */
fun MessageObject.translateFinished(locale: Locale, translatorMode: Int): Int {
    val db = TranslateDb.forLocale(locale)
//    translating = false

    if (isPoll) {
        val pool = (messageOwner.media as TLRPC.TL_messageMediaPoll).poll
        val question = db.query(pool.question.text) ?: return 0

        pool.translatedQuestion =
            "${if (translatorMode == TRANSLATE_MODE_APPEND) pool.question.text + "\n\n--------\n\n" else ""}$question"

        pool.answers.forEach {
            val answer = db.query(it.text.text) ?: return 0
            it.translatedText = it.text.text + " | " + answer
        }
    } else {
        val text =
            db.query(messageOwner.message.takeIf { !it.isNullOrBlank() } ?: return 1) ?: return 0
        messageOwner.translatedMessage =
            "${if (translatorMode == TRANSLATE_MODE_APPEND) messageOwner.message + "\n\n--------\n\n" else ""}$text"
    }
    return 2
}

@JvmName("translateMessages")
fun ChatActivity.translateMessages1() = translateMessages()

@JvmName("translateMessages")
fun ChatActivity.translateMessages2(target: Locale) = translateMessages(target)

@JvmName("translateMessages")
fun ChatActivity.translateMessages3(messages: List<MessageObject>) =
    translateMessages(messages = messages)

@OptIn(DelicateCoroutinesApi::class)
fun ChatActivity.translateMessages(
    target: Locale = NekoConfig.translateToLang.String().code2Locale,
    messages: List<MessageObject> = messageForTranslate?.let { listOf(it) }
        ?: selectedObjectGroup?.messages ?: emptyList()
) {
    if (messages.any { it.translating }) {
        return
    }

    val controller = MessagesController.getInstance(currentAccount).translateController
    val translatorMode = NaConfig.translatorMode.Int()

    if (messages.all { it.messageOwner.translated }) {
        messages.forEach { messageObject ->
            controller.removeAsTranslatingItem(messageObject)
            controller.removeAsManualTranslate(messageObject)
            messageObject.translating = false
            messageObject.messageOwner.translated = false
            if (translatorMode != TRANSLATE_MODE_REPLACE) {
                messageHelper.resetMessageContent(dialogId, messageObject)
            } else {
                AndroidUtilities.runOnUIThread {
                    NotificationCenter.getInstance(currentAccount)
                        .postNotificationName(NotificationCenter.messageTranslated, messageObject)
                }
            }
        }
        return
    } else {
        messages.forEach { messageObject ->
            controller.addAsTranslatingItem(messageObject)
            controller.addAsManualTranslate(messageObject)
            messageObject.translating = true
            NotificationCenter.getInstance(currentAccount)
                .postNotificationName(NotificationCenter.messageTranslating, messageObject)
        }
    }

    val deferred = LinkedList<Deferred<Unit>>()
    val transPool = newFixedThreadPoolContext(5, "Message Trans Pool")

    GlobalScope.launch(Dispatchers.IO) {
        messages.forEachIndexed { _, selectedObject ->
            val state = selectedObject.translateFinished(target, translatorMode)
            if (state == 1) {
                return@forEachIndexed
            } else if (state == 2) {
                withContext(Dispatchers.Main) {
                    controller.removeAsTranslatingItem(selectedObject)
                    controller.removeAsManualTranslate(selectedObject)
                    selectedObject.messageOwner.translated = true
                    messageHelper.resetMessageContent(dialogId, selectedObject)
                }
                return@forEachIndexed
            }

            deferred.add(async(transPool) trans@{
                val db = TranslateDb.forLocale(target)
                if (selectedObject.isPoll) {
                    val pool = (selectedObject.messageOwner.media as TLRPC.TL_messageMediaPoll).poll
                    var question = db.query(pool.question.text)
                    if (question == null) {
                        runCatching {
                            question = Translator.translate(target, pool.question.text)
                        }.onFailure { e ->
                            handleTranslationError(parentActivity, e) {
                                translateMessages(target, messages)
                            }
                            return@trans
                        }
                    }
                    pool.translatedQuestion =
                        "${if (translatorMode == TRANSLATE_MODE_APPEND) pool.question.text + "\n\n--------\n\n" else ""}$question"

                    pool.answers.forEach {
                        var answer = db.query(it.text.text)
                        if (answer == null) {
                            runCatching {
                                answer = Translator.translate(target, it.text.text)
                            }.onFailure { e ->
                                handleTranslationError(parentActivity, e) {
                                    translateMessages(target, messages)
                                }
                                return@trans
                            }
                        }
                        it.translatedText = answer + " | " + it.text.text
                    }
                } else {
                    val text = db.query(selectedObject.messageOwner.message)
                    var result: TLRPC.TL_textWithEntities? = null
                    if (text == null) {
                        runCatching {
                            result = Translator.translate(
                                target,
                                selectedObject.messageOwner.message,
                                selectedObject.messageOwner.entities
                            )
                        }.onFailure {
                            val parentActivity = parentActivity
                            if (parentActivity != null) {
                                AlertUtil.showTransFailedDialog(
                                    parentActivity,
                                    it is UnsupportedOperationException,
                                    it.message ?: it.javaClass.simpleName
                                ) {
                                    translateMessages(target, messages)
                                }
                            }
                            controller.removeAsTranslatingItem(selectedObject)
                            controller.removeAsManualTranslate(selectedObject)
                            return@trans
                        }
                    }
                    selectedObject.messageOwner.translatedMessage =
                        "${if (translatorMode == TRANSLATE_MODE_APPEND) selectedObject.messageOwner.message + "\n\n--------\n\n" else ""}${result?.text ?: text}"

                    if (result != null && translatorMode == TRANSLATE_MODE_REPLACE) {
                        selectedObject.messageOwner.translatedText = result
                    }
                }
                controller.removeAsTranslatingItem(selectedObject)
                selectedObject.messageOwner.translated = true
                selectedObject.messageOwner.translatedToLanguage = target.language

                if (translatorMode == TRANSLATE_MODE_REPLACE) {
                    MessagesStorage.getInstance(currentAccount).updateMessageCustomParams(
                        selectedObject.dialogId,
                        selectedObject.messageOwner
                    )
                    AndroidUtilities.runOnUIThread {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(
                            NotificationCenter.messageTranslated, selectedObject)
                        NotificationCenter.getInstance(currentAccount).postNotificationName(
                            NotificationCenter.updateInterfaces, 0)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (translatorMode != TRANSLATE_MODE_REPLACE) {
                        messageHelper.resetMessageContent(dialogId, selectedObject)
                    }
                }
            })
        }
        deferred.awaitAll()
        transPool.cancel()
    }
    messages.forEach { messageObject ->
        messageObject.translating = false
    }
}

private fun handleTranslationError(
    parentActivity: Context?, throwable: Throwable, retryAction: () -> Unit
) {
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