package tw.nekomimi.nekogram.transtale.source

import cn.hutool.http.HttpUtil
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.transtale.HTMLKeeper
import tw.nekomimi.nekogram.transtale.Translator
import tw.nekomimi.nekogram.transtale.source.raw.DeepLTranslatorRaw
import kotlin.random.Random

object DeepLTranslator : Translator {

    private const val OFFICIAL_ENDPOINT = "https://api-free.deepl.com/v2/translate"
    private const val FALLBACK_ENDPOINT = "https://deeplx.gpu.nu/v2/translate"

    private val rawTranslator = DeepLTranslatorRaw()

    override suspend fun doTranslate(
        from: String,
        to: String,
        query: String,
        entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {
        if (to.isEmpty()) {
            throw UnsupportedOperationException(getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        try {
            val originalText = TLRPC.TL_textWithEntities()
            originalText.text = query
            originalText.entities = entities

            val finalString = StringBuilder()

            val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
                query,
                entities,
                false
            ) else query

            val translatedText = rawTranslator.translate(textToTranslate, from, to)
            finalString.append(translatedText)

            var finalText = TLRPC.TL_textWithEntities()
            if (entities.isNotEmpty()) {
                val resultPair = HTMLKeeper.htmlToEntities(finalString.toString(), entities, false)
                finalText.text = resultPair.first
                finalText.entities = resultPair.second
                finalText = TranslateAlert2.preprocess(originalText, finalText)
            } else {
                finalText.text = finalString.toString()
            }

            return finalText
        } catch (e: Exception) {
            val endpoint = if (Random.nextBoolean()) OFFICIAL_ENDPOINT else FALLBACK_ENDPOINT
            return translateWithAPI(endpoint, to, query, entities)
        }
    }

    private fun translateWithAPI(
        endpoint: String,
        targetLang: String,
        query: String,
        entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {
        val apiKey = getString(R.string.DEEPL_API_KEY)
        if (apiKey.isBlank()) error("Missing DeepL Translate Key")

        val originalText = TLRPC.TL_textWithEntities()
        originalText.text = query
        originalText.entities = entities

        val finalString = StringBuilder()

        val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
            query,
            entities,
            false
        ) else query

        val textArray = JSONArray().apply {
            put(textToTranslate)
        }

        val requestBody = JSONObject().apply {
            put("preserve_formatting", true)
            put("text", textArray)
            put("target_lang", targetLang.uppercase())
        }.toString()

        val response = makeTranslateRequest(endpoint, apiKey, requestBody)
        val respObj = JSONObject(response.body())
        val respArr = respObj.getJSONArray("translations")

        if (respArr.length() == 0) error("Empty translation result")

        val translatedText = respArr.getJSONObject(0).getString("text")
        finalString.append(translatedText)

        var finalText = TLRPC.TL_textWithEntities()
        if (entities.isNotEmpty()) {
            val resultPair = HTMLKeeper.htmlToEntities(finalString.toString(), entities, false)
            finalText.text = resultPair.first
            finalText.entities = resultPair.second
            finalText = TranslateAlert2.preprocess(originalText, finalText)
        } else {
            finalText.text = finalString.toString()
        }

        return finalText
    }

    private fun makeTranslateRequest(endpoint: String, apiKey: String, requestBody: String): cn.hutool.http.HttpResponse {
        val response = HttpUtil.createPost(endpoint)
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .header("Content-Type", "application/json")
            .body(requestBody)
            .execute()

        if (response.status != 200) {
            error("HTTP ${response.status} : ${response.body()}")
        }

        return response
    }
}