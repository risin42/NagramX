package tw.nekomimi.nekogram.transtale

import android.view.View
import cn.hutool.core.util.ArrayUtil
import cn.hutool.core.util.StrUtil
import cn.hutool.http.HttpRequest
import java.util.*
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.cc.CCConverter
import tw.nekomimi.nekogram.cc.CCTarget
import tw.nekomimi.nekogram.transtale.source.*
import tw.nekomimi.nekogram.ui.PopupBuilder
import tw.nekomimi.nekogram.utils.UIUtil
import tw.nekomimi.nekogram.utils.receive
import tw.nekomimi.nekogram.utils.receiveLazy
import xyz.nextalone.nagram.NaConfig

fun <T : HttpRequest> T.applyProxy(): T {
    return this
}

val String.code2Locale: Locale by
        receiveLazy<String, Locale> {
            var ret: Locale
            if (this.isBlank()) {
                ret = LocaleController.getInstance().currentLocale
            } else {
                val args = replace('-', '_').split('_')

                if (args.size == 1) {
                    ret = Locale(args[0])
                } else {
                    ret = Locale(args[0], args[1])
                }
            }
            ret
        }

val Locale.locale2code by
        receiveLazy<Locale, String> {
            if (StrUtil.isBlank(country)) {
                language
            } else {
                "$language-$country"
            }
        }

val LocaleController.LocaleInfo.locale by
        receiveLazy<LocaleController.LocaleInfo, Locale> { pluralLangCode.code2Locale }

val Locale.transDb by
        receive<Locale, TranslateDb> {
            TranslateDb.repo[this] ?: TranslateDb(locale2code).also { TranslateDb.repo[this] = it }
        }

val String.transDbByCode by receive<String, TranslateDb> { code2Locale.transDb }

interface Translator {

    suspend fun doTranslate(from: String, to: String, query: String): String

    companion object {

        const val providerGoogle = 1
        const val providerGoogleCN = 2
        const val providerYandex = 3
        const val providerLingo = 4
        const val providerMicrosoft = 5
        const val providerRealMicrosoft = 6
        const val providerDeepL = 7
        const val providerTelegram = 8
        const val providerTranSmart = 9
        const val providerLLMTranslator = 10

        @Throws(Exception::class)
        suspend fun translate(query: String) =
                translate(
                        NekoConfig.translateToLang.String()?.code2Locale
                                ?: LocaleController.getInstance().currentLocale,
                        query
                )

        @Throws(Exception::class)
        suspend fun translateArticle(query: String) =
                translateArticle(
                        NekoConfig.translateToLang.String()?.code2Locale
                                ?: LocaleController.getInstance().currentLocale,
                        query
                )

        @Throws(Exception::class)
        suspend fun translate(to: Locale, query: String): String {
            return translateBase(to, query, NekoConfig.translationProvider.Int())
        }

        @Throws(Exception::class)
        suspend fun translateArticle(to: Locale, query: String): String {
            val provider =
                    if (NaConfig.enableSeparateArticleTranslator.Bool()) {
                        NaConfig.articleTranslationProvider.Int()
                    } else {
                        NekoConfig.translationProvider.Int()
                    }
            return translateBase(to, query, provider)
        }

        @Throws(Exception::class)
        private suspend fun translateBase(to: Locale, query: String, provider: Int): String {
            var language = to.language
            var country = to.country

            if (language == "in") language = "id"
            if (country.lowercase() == "duang") country = "CN"

            when (provider) {
                providerDeepL -> language = language.uppercase()
                providerMicrosoft, providerRealMicrosoft, providerGoogle ->
                        if (language == "zh") {
                            val countryUpperCase = country.uppercase()
                            if (countryUpperCase == "CN" || countryUpperCase == "DUANG") {
                                language = if (provider == providerMicrosoft || provider == providerRealMicrosoft) "zh-Hans" else "zh-CN"
                            } else if (countryUpperCase == "TW" || countryUpperCase == "HK") {
                                language = if (provider == providerMicrosoft || provider == providerRealMicrosoft) "zh-HanT" else "zh-TW"
                            }
                        }
                providerTelegram ->
                        language = TelegramAPITranslator.convertLanguageCode(language, country)
            }
            val translator =
                    when (provider) {
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

            val result =
                    translator.doTranslate("auto", language, query).also {
                        to.transDb.save(query, it)
                    }

            if (language == "zh") {
                val countryUpperCase = country.uppercase()
                if (countryUpperCase == "CN") {
                    return CCConverter.get(CCTarget.SP).convert(result)
                } else if (countryUpperCase == "TW") {
                    return CCConverter.get(CCTarget.TT).convert(result)
                }
            }

            return result
        }

        val availableLocaleList: Array<Locale> = Locale.getAvailableLocales().also {
            Arrays.sort(it, Comparator.comparing(Locale::toString))
        }

        @JvmStatic
        @JvmOverloads
        fun showTargetLangSelect(
                anchor: View,
                input: Boolean = false,
                full: Boolean = false,
                callback: (Locale) -> Unit
        ) {
            val builder = PopupBuilder(anchor)

            // Get built-in language list
            var locales: MutableList<Locale> = if (full) {
                availableLocaleList
                    .filter { it.variant.isBlank() }
                    .toMutableList()
            } else {
                LocaleController.getInstance()
                    .languages
                    .map { it.pluralLangCode }
                    .toSet()
                    .filter { !it.lowercase().contains("duang") }
                    .map { it.code2Locale }
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
            val preferredLocales = NaConfig.preferredTranslateTargetLangList
                    .mapNotNull { lang ->
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
                localeNames[i] =
                        if (i == 0) {
                            LocaleController.getString(R.string.Default) +
                                    " ( " +
                                    locales[i].getDisplayName(currLocale) +
                                    " )"
                        } else if (i <= preferredLocales.size) {
                            "⭐ " + locales[i].getDisplayName(currLocale)
                        } else {
                            locales[i].getDisplayName(currLocale)
                        }
            }

            if (!full) {
                localeNames[localeNames.size - 1] = LocaleController.getString(R.string.More)
            }

            builder.setItems(localeNames.filterIsInstance<CharSequence>().toTypedArray()) {
                    index: Int,
                    _ ->
                if (index == locales.size) {
                    showTargetLangSelect(anchor, input, true, callback)
                } else {
                    callback(locales[index])
                }
            }

            builder.show()
        }

        @JvmStatic
        @JvmOverloads
        fun showCCTargetSelect(anchor: View, input: Boolean = true, callback: (String) -> Unit) {

            val builder = PopupBuilder(anchor)

            builder.setItems(
                    arrayOf(
                            if (!input) LocaleController.getString(R.string.CCNo) else null,
                            LocaleController.getString(R.string.CCSC),
                            LocaleController.getString(R.string.CCSP),
                            LocaleController.getString(R.string.CCTC),
                            LocaleController.getString(R.string.CCHK),
                            LocaleController.getString(R.string.CCTT),
                            LocaleController.getString(R.string.CCJP)
                    )
            ) { index: Int, _ ->
                callback(
                        when (index) {
                            1 -> CCTarget.SC.name
                            2 -> CCTarget.SP.name
                            3 -> CCTarget.TC.name
                            4 -> CCTarget.HK.name
                            5 -> CCTarget.TT.name
                            6 -> CCTarget.JP.name
                            else -> ""
                        }
                )
            }

            builder.show()
        }

        @JvmStatic
        @JvmOverloads
        fun translate(
                to: Locale =
                        NekoConfig.translateToLang.String()?.code2Locale
                                ?: LocaleController.getInstance().currentLocale,
                query: String,
                translateCallBack: TranslateCallBack
        ) {

            UIUtil.runOnIoDispatcher {
                runCatching {
                    val result = translate(to, query)

                    UIUtil.runOnUIThread(Runnable { translateCallBack.onSuccess(result) })
                }
                        .onFailure {
                            UIUtil.runOnUIThread(Runnable {
                            translateCallBack.onFailed(
                                    it is UnsupportedOperationException,
                                    it.message ?: it.javaClass.simpleName
                            )})
                        }
            }
        }

        interface TranslateCallBack {

            fun onSuccess(translation: String)
            fun onFailed(unsupported: Boolean, message: String)
        }
    }
}
