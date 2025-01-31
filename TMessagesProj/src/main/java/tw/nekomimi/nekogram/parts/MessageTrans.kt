package tw.nekomimi.nekogram.parts

import kotlinx.coroutines.*
import org.telegram.messenger.MessageObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ChatActivity
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.transtale.TranslateDb
import tw.nekomimi.nekogram.transtale.Translator
import tw.nekomimi.nekogram.transtale.code2Locale
import tw.nekomimi.nekogram.utils.AlertUtil
import xyz.nextalone.nagram.NaConfig
import java.util.*

fun MessageObject.translateFinished(locale: Locale): Int {
    val db = TranslateDb.forLocale(locale)
    translating = false

    if (isPoll) {
        val pool = (messageOwner.media as TLRPC.TL_messageMediaPoll).poll
        val question = db.query(pool.question.text) ?: return 0

        pool.translatedQuestion =
            "${if (NaConfig.translatorMode.Int() == 0) pool.question.text + "\n\n--------\n\n" else ""}$question"

        pool.answers.forEach {
            val answer = db.query(it.text.text) ?: return 0
            it.translatedText = it.text.text + " | " + answer
        }
    } else {
        val text =
            db.query(messageOwner.message.takeIf { !it.isNullOrBlank() } ?: return 1) ?: return 0
        messageOwner.translatedMessage =
            "${if (NaConfig.translatorMode.Int() == 0) messageOwner.message + "\n\n--------\n\n" else ""}$text"
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

fun ChatActivity.translateMessages(
    target: Locale = NekoConfig.translateToLang.String().code2Locale,
    messages: List<MessageObject> = messageForTranslate?.let { listOf(it) }
        ?: selectedObjectGroup?.messages
        ?: emptyList()
) {
    if (messages.any { it.translating }) {
        return
    }

    val controller = MessagesController.getInstance(currentAccount).translateController

    if (messages.all { it.messageOwner.translated }) {
        messages.forEach { messageObject ->
            controller.removeAsTranslatingItem(messageObject)
            controller.removeAsManualTranslate(messageObject)
            messageObject.translating = false
            messageObject.messageOwner.translated = false
            messageHelper.resetMessageContent(dialogId, messageObject)
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
            val state = selectedObject.translateFinished(target)
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
                        }.onFailure {
                            val parentActivity = parentActivity
                            if (parentActivity != null) {
                                AlertUtil.showTransFailedDialog(
                                    parentActivity, it is UnsupportedOperationException, it.message
                                        ?: it.javaClass.simpleName
                                ) {
                                    translateMessages(target, messages)
                                }
                            }
                            return@trans
                        }
                    }
                    pool.translatedQuestion =
                        "${if (NaConfig.translatorMode.Int() == 0) pool.question.text + "\n\n--------\n\n" else ""}$question"

                    pool.answers.forEach {
                        var answer = db.query(it.text.text)
                        if (answer == null) {
                            runCatching {
                                answer = Translator.translate(target, it.text.text)
                            }.onFailure { e ->
                                val parentActivity = parentActivity
                                if (parentActivity != null) {
                                    AlertUtil.showTransFailedDialog(
                                        parentActivity,
                                        e is UnsupportedOperationException,
                                        e.message
                                            ?: e.javaClass.simpleName
                                    ) {
                                        translateMessages(target, messages)
                                    }
                                }
                                return@trans
                            }
                        }
                        it.translatedText = answer + " | " + it.text.text
                    }
                } else {
                    var text = db.query(selectedObject.messageOwner.message)
                    if (text == null) {
                        runCatching {
                            text = Translator.translate(target, selectedObject.messageOwner.message)
                        }.onFailure {
                            val parentActivity = parentActivity
                            if (parentActivity != null) {
                                AlertUtil.showTransFailedDialog(
                                    parentActivity, it is UnsupportedOperationException, it.message
                                        ?: it.javaClass.simpleName
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
                        "${if (NaConfig.translatorMode.Int() == 0) selectedObject.messageOwner.message + "\n\n--------\n\n" else ""}$text"
                }
                controller.removeAsTranslatingItem(selectedObject)
                controller.removeAsManualTranslate(selectedObject)
                selectedObject.messageOwner.translated = true
                withContext(Dispatchers.Main) {
                    messageHelper.resetMessageContent(dialogId, selectedObject)
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
