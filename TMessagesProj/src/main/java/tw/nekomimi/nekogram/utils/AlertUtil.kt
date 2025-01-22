package tw.nekomimi.nekogram.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.NumberPicker;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.transtale.Translator;
import tw.nekomimi.nekogram.ui.BottomBuilder;
import tw.nekomimi.nekogram.ui.PopupBuilder;


object AlertUtil {

    @JvmStatic
    fun copyAndAlert(text: String) {
        AndroidUtilities.addToClipboard(text)
        AlertUtil.showToast(LocaleController.getString(R.string.TextCopied))
    }

    @JvmStatic
    fun copyLinkAndAlert(text: String) {
        AndroidUtilities.addToClipboard(text)
        AlertUtil.showToast(LocaleController.getString(R.string.LinkCopied))
    }

    @JvmStatic
    fun call(number: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + number))
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
    fun showToast(text: String) = UIUtil.runOnUIThread(Runnable {
        Toast.makeText(
                ApplicationLoader.applicationContext,
                text.takeIf { it.isNotBlank() }
                        ?: "喵 !",
                Toast.LENGTH_LONG
        ).show()
    })

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
    fun showSimpleAlert(ctx: Context?, title: String?, text: String, listener: ((AlertDialog.Builder) -> Unit)? = null) = UIUtil.runOnUIThread(Runnable {
        if (ctx == null) return@Runnable

        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(title ?: LocaleController.getString(R.string.NekoX))
        builder.setMessage(text)

        builder.setPositiveButton(LocaleController.getString(R.string.OK)) { _, _ ->
            builder.dismissRunnable?.run()
            listener?.invoke(builder)
        }
        builder.show()
    })

    @JvmStatic
    fun showCopyAlert(ctx: Context, text: String) = UIUtil.runOnUIThread(Runnable {
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(LocaleController.getString(R.string.Translate))
        builder.setMessage(text)

        builder.setNegativeButton(LocaleController.getString(R.string.Copy)) { _, _ ->
            AndroidUtilities.addToClipboard(text)
            AlertUtil.showToast(LocaleController.getString(R.string.TextCopied))
            builder.dismissRunnable.run()
        }
        builder.setPositiveButton(LocaleController.getString(R.string.OK)) { _, _ ->
            builder.dismissRunnable.run()
        }
        builder.show()

    })

    @JvmOverloads
    @JvmStatic
    fun showProgress(ctx: Context, text: String = LocaleController.getString(R.string.Loading)): AlertDialog {
        return AlertDialog.Builder(ctx, AlertDialog.ALERT_TYPE_MESSAGE).apply {
            setMessage(text)
        }.create()
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirm(ctx: Context, title: String, text: String? = null, icon: Int, button: String, red: Boolean, listener: Runnable) = UIUtil.runOnUIThread(Runnable {
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

    })

    @JvmStatic
    @JvmOverloads
    fun showTransFailedDialog(ctx: Context, noRetry: Boolean, message: String, retryRunnable: Runnable) = UIUtil.runOnUIThread(Runnable {
        ctx.setTheme(R.style.Theme_TMessages)
        val reference = AtomicReference<AlertDialog>()

        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(LocaleController.getString(R.string.TranslateFailed))
        builder.setMessage(message)

        builder.setNeutralButton(LocaleController.getString(R.string.ChangeTranslateProvider)) { _, _ ->
            val view = reference.get().getButton(AlertDialog.BUTTON_NEUTRAL)
            val popup = PopupBuilder(view, true)
            val providers = listOf(
                ProviderInfo(Translator.providerGoogle, R.string.ProviderGoogleTranslate),
                ProviderInfo(Translator.providerYandex, R.string.ProviderYandexTranslate), 
                ProviderInfo(Translator.providerLingo, R.string.ProviderLingocloud),
                ProviderInfo(Translator.providerMicrosoft, R.string.ProviderMicrosoftTranslator),
                ProviderInfo(Translator.providerYouDao, R.string.ProviderYouDao),
                ProviderInfo(Translator.providerDeepL, R.string.ProviderDeepLTranslate),
                ProviderInfo(Translator.providerTelegram, R.string.ProviderTelegramAPI),
                ProviderInfo(Translator.providerTranSmart, R.string.ProviderTranSmartTranslate),
                ProviderInfo(Translator.providerLLMTranslator, R.string.ProviderLLMTranslator)
            )
            val itemNames = providers.map { LocaleController.getString(it.nameResId) }
            popup.setItems(itemNames.toTypedArray()) { index, _ ->
                reference.get().dismiss()
                NekoConfig.translationProvider.setConfigInt(providers[index].providerConstant)
                retryRunnable.run()
            }
            popup.show()
        }

        if (noRetry) {
            builder.setPositiveButton(LocaleController.getString(R.string.Cancel)) { _, _ ->
                reference.get().dismiss()
            }
        } else {
            builder.setPositiveButton(LocaleController.getString(R.string.Retry)) { _, _ ->
                reference.get().dismiss()
                retryRunnable.run()
            }
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel)) { _, _ ->
                reference.get().dismiss()
            }
        }

        reference.set(builder.create().apply {
            setDismissDialogByButtons(false)
            show()
        })
    })

    private data class ProviderInfo(
        val providerConstant: Int,
        val nameResId: Int
    )
}