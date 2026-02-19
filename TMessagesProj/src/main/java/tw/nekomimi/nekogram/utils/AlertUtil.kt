package tw.nekomimi.nekogram.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.ui.BottomBuilder
import tw.nekomimi.nekogram.ui.PopupBuilder
import java.util.concurrent.atomic.AtomicReference

object AlertUtil {

    @JvmStatic
    fun copyAndAlert(text: String, fragment: BaseFragment? = null) {
        if (AndroidUtilities.addToClipboard(text)) {
            BulletinFactory.of(fragment).createCopyBulletin(getString(R.string.TextCopied)).show()
        }
    }

    @JvmStatic
    fun copyLinkAndAlert(text: String, fragment: BaseFragment? = null) {
        AndroidUtilities.addToClipboard(text)
        BulletinFactory.of(fragment).createCopyBulletin(getString(R.string.LinkCopied)).show()
    }

    @JvmStatic
    fun call(number: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_DIAL, ("tel:+$number").toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ApplicationLoader.applicationContext.startActivity(intent)
        }.onFailure {
            showToast(it)
        }
    }

    @JvmStatic
    fun showToast(e: Throwable) = showToast(e.message ?: e.javaClass.simpleName)

    @JvmStatic
    fun showToast(e: TLRPC.TL_error?) {
        if (e == null) return
        showToast("${e.code}: ${e.text}")
    }

    @JvmStatic
    fun showToast(text: String) = AndroidUtilities.runOnUIThread {
        Toast.makeText(
            ApplicationLoader.applicationContext,
            text.takeIf { it.isNotBlank() }
                ?: "喵 !",
            Toast.LENGTH_LONG
        ).show()
    }

    @JvmStatic
    fun showSimpleAlert(ctx: Context?, error: Throwable) {
        showSimpleAlert(ctx, null, error.message ?: error.javaClass.simpleName)
    }

    @JvmStatic
    @JvmOverloads
    fun showSimpleAlert(ctx: Context?, text: String, listener: ((AlertDialog.Builder) -> Unit)? = null) {
        showSimpleAlert(ctx, null, text, listener)
    }

    @JvmStatic
    @JvmOverloads
    fun showSimpleAlert(ctx: Context?, title: String?, text: String, listener: ((AlertDialog.Builder) -> Unit)? = null) = AndroidUtilities.runOnUIThread(Runnable {
        if (ctx == null) return@Runnable

        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(title ?: getString(R.string.NagramX))
        builder.setMessage(text)

        builder.setPositiveButton(getString(R.string.OK)) { _, _ ->
            builder.dismissRunnable?.run()
            listener?.invoke(builder)
        }
        builder.show()
    })

    @JvmStatic
    fun showCopyAlert(ctx: Context, text: String) = AndroidUtilities.runOnUIThread {
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(getString(R.string.Translate))
        builder.setMessage(text)

        builder.setNegativeButton(getString(R.string.Copy)) { _, _ ->
            AndroidUtilities.addToClipboard(text)
            if (AndroidUtilities.shouldShowClipboardToast()) {
                showToast(getString(R.string.TextCopied))
            }
            builder.dismissRunnable.run()
        }
        builder.setPositiveButton(getString(R.string.OK)) { _, _ ->
            builder.dismissRunnable.run()
        }
        builder.show()

    }

    @JvmOverloads
    @JvmStatic
    fun showProgress(ctx: Context, text: String = getString(R.string.Loading)): AlertDialog {
        return AlertDialog.Builder(ctx, AlertDialog.ALERT_TYPE_MESSAGE).apply {
            setMessage(text)
        }.create()
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirm(ctx: Context, title: String, text: String? = null, icon: Int, button: String, red: Boolean, listener: Runnable) = AndroidUtilities.runOnUIThread {
        val builder = BottomBuilder(ctx)

        if (text != null) {
            builder.addTitle(title, text)
        } else {
            builder.addTitle(title)
        }

        builder.addItem(button, icon, red) {
            listener.run()
        }
        builder.addCancelItem()
        builder.show()

    }

    @JvmStatic
    fun showTransFailedDialog(ctx: Context, noRetry: Boolean, message: String, retryRunnable: Runnable) = AndroidUtilities.runOnUIThread {
        ctx.setTheme(R.style.Theme_TMessages)
        val reference = AtomicReference<AlertDialog>()

        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(getString(R.string.TranslateFailed))
        builder.setMessage(message)

        builder.setNeutralButton(getString(R.string.ChangeTranslateProvider)) { _, _ ->
            val view = reference.get().getButton(AlertDialog.BUTTON_NEUTRAL)
            val popup = PopupBuilder(view, true)
            val providers = listOf(
                ProviderInfo(Translator.providerGoogle, R.string.ProviderGoogleTranslate),
                ProviderInfo(Translator.providerYandex, R.string.ProviderYandexTranslate), 
                ProviderInfo(Translator.providerLingo, R.string.ProviderLingocloud),
                ProviderInfo(Translator.providerMicrosoft, R.string.ProviderMicrosoftTranslator),
                ProviderInfo(Translator.providerRealMicrosoft, R.string.ProviderRealMicrosoftTranslator),
                ProviderInfo(Translator.providerDeepL, R.string.ProviderDeepLTranslate),
                ProviderInfo(Translator.providerTelegram, R.string.ProviderTelegramAPI),
                ProviderInfo(Translator.providerTranSmart, R.string.ProviderTranSmartTranslate),
                ProviderInfo(Translator.providerLLMTranslator, R.string.ProviderLLMTranslator)
            )
            val itemNames = providers.map { getString(it.nameResId) }
            popup.setItems(itemNames.toTypedArray()) { index, _ ->
                reference.get().dismiss()
                NekoConfig.translationProvider.setConfigInt(providers[index].providerConstant)
                retryRunnable.run()
            }
            popup.show()
        }

        if (noRetry) {
            builder.setPositiveButton(getString(R.string.Cancel)) { _, _ ->
                reference.get().dismiss()
            }
        } else {
            builder.setPositiveButton(getString(R.string.Retry)) { _, _ ->
                reference.get().dismiss()
                retryRunnable.run()
            }
            builder.setNegativeButton(getString(R.string.Cancel)) { _, _ ->
                reference.get().dismiss()
            }
        }

        reference.set(builder.create().apply {
            setDismissDialogByButtons(false)
            show()
        })
    }

    private data class ProviderInfo(
        val providerConstant: Int,
        val nameResId: Int
    )
}
