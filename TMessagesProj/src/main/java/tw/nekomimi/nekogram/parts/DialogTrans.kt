package tw.nekomimi.nekogram.parts

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.code2Locale
import tw.nekomimi.nekogram.utils.AlertUtil
import tw.nekomimi.nekogram.utils.uDismiss
import xyz.nextalone.nagram.NaConfig
import java.util.concurrent.atomic.AtomicBoolean

@JvmOverloads
fun startTrans(
    ctx: Context,
    text: String,
    toLang: String = NekoConfig.translateToLang.String(),
    provider: Int = 0
) {

    val dialog = AlertUtil.showProgress(ctx)
    val canceled = AtomicBoolean(false)
    val finalToLang = toLang.code2Locale
    val finalProvider = provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int()
    val appendOriginal = NaConfig.translatorMode.Int() == TRANSLATE_MODE_APPEND
    val job = Job()

    dialog.show()
    dialog.setOnCancelListener {
        canceled.set(true)
        job.cancel()
    }

    val scope = CoroutineScope(Dispatchers.IO + job)
    scope.launch {
        runCatching {
            val result = Translator.translate(finalToLang, text, finalProvider)
            if (!canceled.get()) {
                withContext(Dispatchers.Main) {
                    dialog.uDismiss()
                    val finalText = if (appendOriginal) {
                        "$text$TRANSLATION_SEPARATOR$result"
                    } else {
                        result
                    }
                    AlertUtil.showCopyAlert(ctx, finalText)
                }
            }
        }.onFailure { e ->
            scope.cancel()
            withContext(Dispatchers.Main) {
                dialog.uDismiss()
                if (!canceled.get()) {
                    AlertUtil.showTransFailedDialog(
                        ctx, e is UnsupportedOperationException, e.message ?: e.javaClass.simpleName
                    ) {
                        startTrans(ctx, text, toLang, finalProvider)
                    }
                }
            }
        }
    }
}
