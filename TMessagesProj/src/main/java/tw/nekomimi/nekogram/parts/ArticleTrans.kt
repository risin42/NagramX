package tw.nekomimi.nekogram.parts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.telegram.messenger.AndroidUtilities
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ArticleViewer
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.utils.AlertUtil
import tw.nekomimi.nekogram.utils.AppScope
import tw.nekomimi.nekogram.utils.uUpdate
import java.util.concurrent.atomic.AtomicInteger

private fun extractTextPlainLeaves(
    richText: TLRPC.RichText, leaves: MutableList<TLRPC.TL_textPlain>
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

fun ArticleViewer.doTransLATE() {
    val status = AlertUtil.showProgress(parentActivity)
    status.show()

    val adapter = pages[0].adapter
    val translatedTextCache = adapter.translatedTextCache
    val job: Job = AppScope.io.launch {
        val dispatcher = Dispatchers.IO.limitedParallelism(5)
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
        val all = textsToTranslate.size
        val taskCount = AtomicInteger(all)

        status.uUpdate("0 / $all")

        supervisorScope {
            val jobs = textsToTranslate.map { str ->
                launch(dispatcher) {
                    if (!isActive) return@launch

                    if (translatedTextCache.containsKey(str)) {
                        taskCount.decrementAndGet()
                        status.uUpdate("${all - taskCount.get()} / $all")
                        return@launch
                    }

                    runCatching {
                        val translatedResult = Translator.translateArticle(str)
                        translatedTextCache[str] = translatedResult

                        status.uUpdate("${all - taskCount.get()} / $all")

                        if (taskCount.decrementAndGet() % 10 == 0) {
                            AndroidUtilities.runOnUIThread { updatePaintSize() }
                        }
                    }.onFailure {
                        if (!isActive) return@launch

                        if (errorCount.incrementAndGet() > 3) {
                            this@supervisorScope.cancel()
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
                }
            }

            jobs.joinAll()
        }

        if (isActive) {
            AndroidUtilities.runOnUIThread {
                updatePaintSize()
                status.dismiss()
            }
        }
    }

    status.setOnCancelListener {
        updateTranslateButton(false)
        job.cancel()
        translatedTextCache.clear()
        AndroidUtilities.runOnUIThread { updatePaintSize() }
    }
}
