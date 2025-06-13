package tw.nekomimi.nekogram.parts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.code2Locale
import tw.nekomimi.nekogram.utils.AlertUtil
import tw.nekomimi.nekogram.utils.UIUtil
import tw.nekomimi.nekogram.utils.uDismiss
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

    dialog.setOnCancelListener {
        canceled.set(true)
    }

    dialog.show()

    fun update(message: String) {
        UIUtil.runOnUIThread(Runnable { dialog.setMessage(message) })
    }

    GlobalScope.launch(Dispatchers.IO) {
        runCatching {
            val result = Translator.translate(finalToLang, text, finalProvider)
            if (!canceled.get()) {
                dialog.uDismiss()
                AlertUtil.showCopyAlert(ctx, result)
            }
        }.onFailure { e ->
            dialog.uDismiss()
            if (!canceled.get()) {
                AlertUtil.showTransFailedDialog(
                    ctx,
                    e is UnsupportedOperationException,
                    e.message ?: e.javaClass.simpleName
                ) {
                    startTrans(ctx, text, toLang, finalProvider)
                }
            }
        }
    }
}