package tw.nekomimi.nekogram.transtale

import android.view.View
import cn.hutool.core.util.StrUtil
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.cc.CCConverter
import tw.nekomimi.nekogram.cc.CCTarget
import tw.nekomimi.nekogram.transtale.source.*
import tw.nekomimi.nekogram.ui.PopupBuilder
import tw.nekomimi.nekogram.utils.UIUtil
import tw.nekomimi.nekogram.utils.receive
import tw.nekomimi.nekogram.utils.receiveLazy
import xyz.nextalone.nagram.NaConfig
import java.util.*

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
    if (StrUtil.isBlank(country)) {
        language
    } else {
        "$language-$country"
    }
}

val LocaleController.LocaleInfo.locale by receiveLazy<LocaleController.LocaleInfo, Locale> { pluralLangCode.code2Locale }

val Locale.transDb by receive<Locale, TranslateDb> {
    TranslateDb.repo[this] ?: TranslateDb(locale2code).also { TranslateDb.repo[this] = it }
}

val String.transDbByCode by receive<String, TranslateDb> { code2Locale.transDb }

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

        @Throws(Exception::class)
        suspend fun translate(to: Locale, query: String): String {
            val result: TLRPC.TL_textWithEntities = translateBase(to, query, ArrayList(), NekoConfig.translationProvider.Int())
            return result.text.toString()
        }

        @Throws(Exception::class)
        suspend fun translate(to: Locale, query: String, entities: ArrayList<TLRPC.MessageEntity>): TLRPC.TL_textWithEntities {
            val result: TLRPC.TL_textWithEntities = translateBase(to, query, entities, NekoConfig.translationProvider.Int())
            return result
        }

        @JvmStatic
        @JvmOverloads
        fun translate(
            to: Locale = NekoConfig.translateToLang.String()?.code2Locale
                ?: LocaleController.getInstance().currentLocale,
            query: String,
            translateCallBack: TranslateCallBack
        ) {

            UIUtil.runOnIoDispatcher {
                runCatching {
                    val result: String = translate(to, query)

                    UIUtil.runOnUIThread(Runnable {
                        translateCallBack.onSuccess(result)
                    })
                }.onFailure {
                    UIUtil.runOnUIThread(Runnable {
                        translateCallBack.onFailed(
                            it is UnsupportedOperationException,
                            it.message ?: it.javaClass.simpleName
                        )
                    })
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

            UIUtil.runOnIoDispatcher {
                runCatching {
                    val result = translateBase(
                        to, query, entities, NekoConfig.translationProvider.Int()
                    )

                    UIUtil.runOnUIThread(Runnable { translateCallBack.onSuccess(result) })
                }.onFailure {
                    UIUtil.runOnUIThread(Runnable {
                        translateCallBack.onFailed(
                            it is UnsupportedOperationException,
                            it.message ?: it.javaClass.simpleName
                        )
                    })
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

            val result = translator.doTranslate("auto", language, query, entities)
            val textToSave = result.text?.toString() ?: ""

            to.transDb.save(query, textToSave)

            if (language == "zh") {
                val countryUpperCase = country.uppercase()
                if (countryUpperCase == "CN") {
                    val convertedText = CCConverter.get(CCTarget.SP).convert(result.text)
                    result.text = convertedText
                    return result
                } else if (countryUpperCase == "TW") {
                    val convertedText = CCConverter.get(CCTarget.TT).convert(result.text)
                    result.text = convertedText
                    return result
                }
            }

            return result
        }

        private val availableLocaleList: Array<Locale> = Locale.getAvailableLocales().also {
            Arrays.sort(it, Comparator.comparing(Locale::toString))
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

        @JvmStatic
        @JvmOverloads
        fun showCCTargetSelect(anchor: View, input: Boolean = true, callback: (String) -> Unit) {

            val builder = PopupBuilder(anchor)

            builder.setItems(
                arrayOf(
                    if (!input) getString(R.string.CCNo) else null,
                    getString(R.string.CCSC),
                    getString(R.string.CCSP),
                    getString(R.string.CCTC),
                    getString(R.string.CCHK),
                    getString(R.string.CCTT),
                    getString(R.string.CCJP)
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

        interface TranslateCallBack {
            fun onSuccess(translation: String)
            fun onFailed(unsupported: Boolean, message: String)
        }

        interface TranslateCallBack2 {
            fun onSuccess(finalText: TLRPC.TL_textWithEntities)
            fun onFailed(unsupported: Boolean, message: String)
        }
    }
}
