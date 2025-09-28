package tw.nekomimi.nekogram.parts

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ArticleViewer
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.utils.AlertUtil
import tw.nekomimi.nekogram.utils.uUpdate
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private fun extractTextPlainLeaves(
    richText: TLRPC.RichText,
    leaves: MutableList<TLRPC.TL_textPlain>
) {
    when (richText) {
        is TLRPC.TL_textPlain -> {
            if (richText.text.isNotBlank()) {
                leaves.add(richText)
            }
        }

        is TLRPC.TL_textConcat -> richText.texts.forEach { extractTextPlainLeaves(it, leaves) }
        is TLRPC.TL_textBold -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textItalic -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textUnderline -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textStrike -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textFixed -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textUrl -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textEmail -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textPhone -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textMarked -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textSubscript -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textSuperscript -> extractTextPlainLeaves(richText.text, leaves)
        is TLRPC.TL_textAnchor -> extractTextPlainLeaves(richText.text, leaves)
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun ArticleViewer.doTransLATE() {
    val status = AlertUtil.showProgress(parentActivity)
    status.show()

    val transPool = newFixedThreadPoolContext(5, "Article Trans Pool")
    val cancel = AtomicBoolean(false)
    val adapter = pages[0].adapter
    val translatedTextCache = adapter.translatedTextCache

    status.setOnCancelListener {
        updateTranslateButton(false)
        cancel.set(true)
        transPool.close()
        translatedTextCache.clear()
    }

    GlobalScope.launch(Dispatchers.IO) {
        val textsToTranslate = HashSet<String>()
        pages[0].adapter.textBlocks.forEach { item ->
            when (item) {
                is TLRPC.RichText -> {
                    val leaves = mutableListOf<TLRPC.TL_textPlain>()
                    extractTextPlainLeaves(item, leaves)
                    leaves.forEach { plainText ->
                        textsToTranslate.add(plainText.text)
                    }
                }

                is String -> {
                    if (item.isNotBlank()) {
                        textsToTranslate.add(item)
                    }
                }
            }
        }

        val errorCount = AtomicInteger()
        val deferreds = LinkedList<Deferred<Unit>>()
        val all = textsToTranslate.size
        val taskCount = AtomicInteger(all)

        status.uUpdate("0 / $all")

        textsToTranslate.forEach { str ->
            deferreds.add(async(transPool) {
                if (cancel.get()) return@async

                if (translatedTextCache.containsKey(str)) {
                    taskCount.decrementAndGet()
                    status.uUpdate("${all - taskCount.get()} / $all")
                    return@async
                }

                runCatching {
                    val translatedResult = Translator.translateArticle(str)
                    translatedTextCache[str] = translatedResult

                    status.uUpdate("${all - taskCount.get()} / $all")

                    if (taskCount.decrementAndGet() % 10 == 0) {
                        AndroidUtilities.runOnUIThread { updatePaintSize() }
                    }
                }.onFailure {
                    if (cancel.get()) return@async

                    if (errorCount.incrementAndGet() > 3) {
                        cancel.set(true)
                        AndroidUtilities.runOnUIThread {
                            status.dismiss()
                            updatePaintSize()
                            updateTranslateButton(false)
                            translatedTextCache.clear()
                            AlertUtil.showTransFailedDialog(
                                parentActivity,
                                it is UnsupportedOperationException,
                                it.message ?: it.javaClass.simpleName
                            ) {
                                doTransLATE()
                            }
                        }
                    }
                }
            })
        }

        deferreds.awaitAll()
        transPool.cancel()
        transPool.close()

        if (!cancel.get()) {
            AndroidUtilities.runOnUIThread {
                updatePaintSize()
                status.dismiss()
            }
        }
    }
}