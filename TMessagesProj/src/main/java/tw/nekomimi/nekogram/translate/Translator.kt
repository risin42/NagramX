package tw.nekomimi.nekogram.translate

import android.text.TextUtils
import android.view.View
import androidx.core.content.edit
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.messenger.TranslateController
import org.telegram.tgnet.TLRPC
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.translate.source.*
import tw.nekomimi.nekogram.ui.PopupBuilder
import tw.nekomimi.nekogram.utils.AppScope
import tw.nekomimi.nekogram.utils.receiveLazy
import xyz.nextalone.nagram.NaConfig
import java.io.IOException
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val String.code2Locale: Locale by receiveLazy<String, Locale> {
    val ret: Locale = if (this.isBlank()) {
        LocaleController.getInstance().currentLocale
    } else {
        val args = replace('-', '_').split('_')

        if (args.size == 1) {
            Locale(args[0])
        } else {
            Locale(args[0], args[1])
        }
    }
    ret
}

val Locale.locale2code by receiveLazy<Locale, String> {
    if (TextUtils.isEmpty(country)) {
        language
    } else {
        "$language-$country"
    }
}

val LocaleController.LocaleInfo.locale by receiveLazy<LocaleController.LocaleInfo, Locale> { pluralLangCode.code2Locale }

@Suppress("ConstPropertyName")
interface Translator {

    suspend fun doTranslate(
        from: String, to: String, query: String, entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities

    companion object {

        const val providerGoogle = 1
        const val providerGoogleCN = 2 // deprecated
        const val providerYandex = 3
        const val providerLingo = 4
        const val providerMicrosoft = 5
        const val providerRealMicrosoft = 6
        const val providerDeepL = 7
        const val providerTelegram = 8
        const val providerTranSmart = 9
        const val providerLLMTranslator = 10

        @JvmStatic
        fun getInputTranslateLangForChat(chatId: Long): String {
            val key = "translateInputLang_$chatId"
            return NekoConfig.getPreferences().getString(key, null)
                ?: NekoConfig.translateInputLang.String()
        }

        @JvmStatic
        fun setInputTranslateLangForChat(chatId: Long, langCode: String) {
            val key = "translateInputLang_$chatId"
            NekoConfig.getPreferences().edit {
                putString(key, langCode)
            }
        }

        @JvmStatic
        fun getInputTranslateLangLocaleForChat(chatId: Long): Locale {
            return getInputTranslateLangForChat(chatId).code2Locale
        }

        @Throws(Exception::class)
        suspend fun translate(to: Locale, query: String, provider: Int = 0): String {
            val result: TLRPC.TL_textWithEntities = translateBase(to, query, ArrayList(), provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int())
            return result.text.toString()
        }

        @Throws(Exception::class)
        suspend fun translate(to: Locale, query: String, entities: ArrayList<TLRPC.MessageEntity>, provider: Int = 0): TLRPC.TL_textWithEntities {
            val result: TLRPC.TL_textWithEntities = translateBase(to, query, entities, provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int())
            return result
        }

        @JvmStatic
        @JvmOverloads
        fun translate(
            to: Locale = NekoConfig.translateToLang.String()?.code2Locale
                ?: LocaleController.getInstance().currentLocale,
            query: String,
            provider: Int = 0,
            translateCallBack: TranslateCallBack
        ) {

            AppScope.io.launch {
                runCatching {
                    val result: String = translate(to, query, provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int())

                    AndroidUtilities.runOnUIThread {
                        translateCallBack.onSuccess(result)
                    }
                }.onFailure {
                    AndroidUtilities.runOnUIThread {
                        translateCallBack.onFailed(
                            it is UnsupportedOperationException,
                            it.message ?: it.javaClass.simpleName
                        )
                    }
                }
            }
        }

        @JvmStatic
        @JvmOverloads
        fun translate(
            to: Locale = NekoConfig.translateToLang.String()?.code2Locale
                ?: LocaleController.getInstance().currentLocale,
            query: String,
            entities: ArrayList<TLRPC.MessageEntity>,
            translateCallBack: TranslateCallBack2
        ) {

            AppScope.io.launch {
                runCatching {
                    val result = translateBase(
                        to, query, entities, NekoConfig.translationProvider.Int()
                    )

                    AndroidUtilities.runOnUIThread { translateCallBack.onSuccess(result) }
                }.onFailure {
                    AndroidUtilities.runOnUIThread {
                        translateCallBack.onFailed(
                            it is UnsupportedOperationException,
                            it.message ?: it.javaClass.simpleName
                        )
                    }
                }
            }
        }

        @JvmStatic
        fun translateWithContext(
            to: Locale,
            query: String,
            entities: ArrayList<TLRPC.MessageEntity>,
            context: String?,
            translateCallBack: TranslateCallBack2
        ) {
            translateWithContext(to, query, entities, context, 0, translateCallBack)
        }

        @JvmStatic
        fun translateWithContext(
            to: Locale,
            query: String,
            entities: ArrayList<TLRPC.MessageEntity>,
            context: String?,
            provider: Int,
            translateCallBack: TranslateCallBack2
        ) {
            AppScope.io.launch {
                runCatching {
                    val effectiveProvider = provider.takeIf { it != 0 } ?: NekoConfig.translationProvider.Int()
                    val result = LLMTranslator.withTranslationContext(context) {
                        translateBase(to, query, entities, effectiveProvider)
                    }
                    AndroidUtilities.runOnUIThread { translateCallBack.onSuccess(result) }
                }.onFailure {
                    AndroidUtilities.runOnUIThread {
                        translateCallBack.onFailed(
                            it is UnsupportedOperationException,
                            it.message ?: it.javaClass.simpleName
                        )
                    }
                }
            }
        }

        @JvmStatic
        fun translatePoll(
            to: Locale = NekoConfig.translateToLang.String()?.code2Locale
                ?: LocaleController.getInstance().currentLocale,
            query: TranslateController.PollText,
            translateCallBack: TranslateCallBack3
        ) {

            AppScope.io.launch {
                runCatching {
                    var translatedPoll = TranslateController.PollText()
                    if (query.question != null) {
                        translatedPoll.question = translateBase(
                            to, query.question.text, ArrayList(), NekoConfig.translationProvider.Int()
                        )
                    }
                    for (answer in query.answers) {
                        var translatedAnswer = TLRPC.TL_pollAnswer()
                        translatedAnswer.text = translateBase(
                            to, answer.text.text, ArrayList(), NekoConfig.translationProvider.Int()
                        )
                        translatedAnswer.option = answer.option
                        translatedPoll.answers.add(translatedAnswer)
                    }
                    if (query.solution != null) {
                        translatedPoll.solution = translateBase(
                            to, query.solution.text, ArrayList(), NekoConfig.translationProvider.Int()
                        )
                    }

                    AndroidUtilities.runOnUIThread { translateCallBack.onSuccess(translatedPoll) }
                }.onFailure {
                    AndroidUtilities.runOnUIThread {
                        translateCallBack.onFailed(
                            it is UnsupportedOperationException,
                            it.message ?: it.javaClass.simpleName
                        )
                    }
                }
            }
        }
 
        @Throws(Exception::class)
        suspend fun translateArticle(query: String) = translateArticle(
            NekoConfig.translateToLang.String()?.code2Locale
                ?: LocaleController.getInstance().currentLocale, query
        )

        @Throws(Exception::class)
        suspend fun translateArticle(to: Locale, query: String): String {
            val provider = if (NaConfig.enableSeparateArticleTranslator.Bool()) {
                NaConfig.articleTranslationProvider.Int()
            } else {
                NekoConfig.translationProvider.Int()
            }
            val result: TLRPC.TL_textWithEntities = translateBase(to, query, ArrayList(), provider)
            return result.text.toString()
        }

        @Throws(Exception::class)
        private suspend fun translateBase(
            to: Locale, query: String, entities: ArrayList<TLRPC.MessageEntity>, provider: Int
        ): TLRPC.TL_textWithEntities {
            var language = to.language
            var country = to.country

            if (language == "in") language = "id"
            if (country.lowercase() == "duang") country = "CN"

            when (provider) {
                providerDeepL -> language = language.uppercase()
                providerMicrosoft, providerRealMicrosoft, providerGoogle -> if (language == "zh") {
                    val countryUpperCase = country.uppercase()
                    if (countryUpperCase == "CN" || countryUpperCase == "DUANG") {
                        language =
                            if (provider == providerMicrosoft || provider == providerRealMicrosoft) "zh-Hans" else "zh-CN"
                    } else if (countryUpperCase == "TW" || countryUpperCase == "HK") {
                        language =
                            if (provider == providerMicrosoft || provider == providerRealMicrosoft) "zh-HanT" else "zh-TW"
                    }
                }

                providerTelegram -> language =
                    TelegramAPITranslator.convertLanguageCode(language, country)
            }
            val translator = when (provider) {
                providerGoogle -> GoogleAppTranslator
                providerYandex -> YandexTranslator
                providerLingo -> LingoTranslator
                providerMicrosoft -> MicrosoftTranslator
                providerRealMicrosoft -> RealMicrosoftTranslator
                providerDeepL -> DeepLTranslator
                providerTelegram -> TelegramAPITranslator
                providerTranSmart -> TranSmartTranslator
                providerLLMTranslator -> LLMTranslator
                else -> throw IllegalArgumentException()
            }

            return translator.doTranslate("auto", language, query, entities)
        }

        private val availableLocaleList: Array<Locale> = Locale.getAvailableLocales().also {
            Arrays.sort(it, Comparator.comparing(Locale::toString))
        }

        data class ProviderInfo(val providerConstant: Int, val nameResId: Int) {
            companion object {
                val PROVIDERS = arrayOf(
                    ProviderInfo(providerGoogle, R.string.ProviderGoogleTranslate),
                    ProviderInfo(providerYandex, R.string.ProviderYandexTranslate),
                    ProviderInfo(providerLingo, R.string.ProviderLingocloud),
                    ProviderInfo(providerMicrosoft, R.string.ProviderMicrosoftTranslator),
                    ProviderInfo(providerRealMicrosoft, R.string.ProviderRealMicrosoftTranslator),
                    ProviderInfo(providerDeepL, R.string.ProviderDeepLTranslate),
                    ProviderInfo(providerTelegram, R.string.ProviderTelegramAPI),
                    ProviderInfo(providerTranSmart, R.string.ProviderTranSmartTranslate),
                    ProviderInfo(providerLLMTranslator, R.string.ProviderLLMTranslator),
                )
            }
        }

        @JvmStatic
        fun showProviderSelect(anchor: View, callback: (Int) -> Unit) {
            val builder = PopupBuilder(anchor)
            val itemNames = ProviderInfo.PROVIDERS.map { getString(it.nameResId) }.toTypedArray()
            builder.setItems(itemNames.map { it as CharSequence }.toTypedArray()) { index, _ ->
                callback(ProviderInfo.PROVIDERS[index].providerConstant)
            }
            builder.show()
        }

        @JvmStatic
        @JvmOverloads
        fun showTargetLangSelect(
            anchor: View, input: Boolean = false, full: Boolean = false, callback: (Locale) -> Unit
        ) {
            val builder = PopupBuilder(anchor)

            // Get built-in language list
            val locales: MutableList<Locale> = if (full) {
                availableLocaleList.filter { it.variant.isBlank() }.toMutableList()
            } else {
                LocaleController.getInstance().languages.asSequence().map { it.pluralLangCode }.toSet()
                    .filter { !it.lowercase().contains("duang") }.map { it.code2Locale }
                    .toMutableList()
            }

            val firstLocale = if (!input) {
                LocaleController.getInstance().currentLocale
            } else {
                Locale.ENGLISH
            }

            locales.remove(firstLocale)
            locales.add(0, firstLocale)

            // Get preferred languages and insert after first position
            val preferredLocales = NaConfig.preferredTranslateTargetLangList.mapNotNull { lang ->
                try {
                    lang.code2Locale
                } catch (e: Exception) {
                    null
                }
            }

            if (preferredLocales.isNotEmpty()) {
                // Remove existing preferred languages to avoid duplicates
                locales.removeAll(preferredLocales.toSet())
                // Add preferred languages starting from position 1
                locales.addAll(1, preferredLocales)
            }

            val currLocale = LocaleController.getInstance().currentLocale
            val localeNames = arrayOfNulls<String>(if (full) locales.size else locales.size + 1)

            for (i in locales.indices) {
                localeNames[i] = if (i == 0) {
                    getString(R.string.Default) + " ( " + locales[i].getDisplayName(currLocale) + " )"
                } else if (i <= preferredLocales.size) {
                    "⭐ " + locales[i].getDisplayName(currLocale)
                } else {
                    locales[i].getDisplayName(currLocale)
                }
            }

            if (!full) {
                localeNames[localeNames.size - 1] = getString(R.string.More)
            }

            builder.setItems(
                localeNames.filterIsInstance<CharSequence>().toTypedArray()
            ) { index: Int, _ ->
                if (index == locales.size) {
                    showTargetLangSelect(anchor, input, true, callback)
                } else {
                    callback(locales[index])
                }
            }

            builder.show()
        }

        interface TranslateCallBack {
            fun onSuccess(translation: String)
            fun onFailed(unsupported: Boolean, message: String)
        }

        interface TranslateCallBack2 {
            fun onSuccess(finalText: TLRPC.TL_textWithEntities)
            fun onFailed(unsupported: Boolean, message: String)
        }

        interface TranslateCallBack3 {
            fun onSuccess(pollText: TranslateController.PollText)
            fun onFailed(unsupported: Boolean, message: String)
        }
    }

    suspend fun Call.await(): Response {
        return suspendCancellableCoroutine { continuation ->
            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            })

            continuation.invokeOnCancellation {
                try {
                    cancel()
                } catch (_: Throwable) {
                    // ignore cancel exception
                }
            }
        }
    }
}
