package tw.nekomimi.nekogram.parts

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ArticleViewer
import tw.nekomimi.nekogram.transtale.TranslateDb
import tw.nekomimi.nekogram.transtale.Translator
import tw.nekomimi.nekogram.utils.AlertUtil
import tw.nekomimi.nekogram.utils.UIUtil
import tw.nekomimi.nekogram.utils.uUpdate
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

fun HashSet<Any>.filterBaseTexts(): HashSet<Any> {

    var hasNext: Boolean

    do {

        hasNext = false

        HashSet(this).forEach { item ->

            when (item) {

                is TLRPC.TL_textConcat -> {

                    remove(item)
                    addAll(item.texts)

                    hasNext = true

                }

            }

        }

    } while (hasNext)

    return this

}

fun ArticleViewer.doTransLATE() {

    val status = AlertUtil.showProgress(parentActivity)

    status.show()

    val transPool = newFixedThreadPoolContext(5, "Article Trans Pool")

    val cancel = AtomicBoolean(false)

    status.setOnCancelListener {

        updateTranslateButton(false)
        cancel.set(true)
        transPool.close()

    }

    GlobalScope.launch(Dispatchers.IO) {

        val copy = HashMap(pages[0].adapter.textToBlocks)
        val array = HashSet(pages[0].adapter.textBlocks).filterBaseTexts()

        val errorCount = AtomicInteger()

        val deferreds = LinkedList<Deferred<Unit>>()

        val all = array.size
        val taskCount = AtomicInteger(array.size)

        status.uUpdate("0 / $all")

        array.forEach { item ->

            when (item) {

                is TLRPC.RichText -> getText(
                    pages[0].adapter,
                    null,
                    item,
                    item,
                    copy[item] ?: copy[item.parentRichText],
                    1000,
                    true
                ).takeIf { it.isNotBlank() }?.toString()

                is String -> item
                else -> null

            }?.also { str ->

                deferreds.add(async(transPool) {

                    if (TranslateDb.currentTarget().contains(str)) {

                        status.uUpdate("${all - taskCount.get()} / $all")

                        if (taskCount.decrementAndGet() % 10 == 0) UIUtil.runOnUIThread(Runnable {

                            updatePaintSize()

                        })

                        return@async

                    }

                    runCatching {

                        if (cancel.get()) return@async

                        Translator.translateArticle(str)

                        status.uUpdate((all - taskCount.get()).toString() + " / " + all)

                        if (taskCount.decrementAndGet() % 10 == 0) UIUtil.runOnUIThread(Runnable {

                            updatePaintSize()

                        })

                    }.onFailure {

                        if (cancel.get()) return@async

                        if (errorCount.incrementAndGet() > 3) {

                            cancel.set(true)

                            UIUtil.runOnUIThread(Runnable {

                                cancel.set(true)
                                status.dismiss()
                                updatePaintSize()
                                updateTranslateButton(false)
                                AlertUtil.showTransFailedDialog(
                                    parentActivity,
                                    it is UnsupportedOperationException,
                                    it.message ?: it.javaClass.simpleName
                                ) {
                                    doTransLATE()
                                }

                            })

                        }

                    }

                })

            }.also {

                if (it == null) taskCount.decrementAndGet()

            }

        }

        deferreds.awaitAll()
        transPool.cancel()

        if (!cancel.get()) UIUtil.runOnUIThread {

            updatePaintSize()
            status.dismiss()

        }

    }


}