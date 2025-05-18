package tw.nekomimi.nekogram.transtale.source

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.transtale.HTMLKeeper
import tw.nekomimi.nekogram.transtale.Translator
import tw.nekomimi.nekogram.transtale.source.raw.DeepLTranslatorRaw
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object DeepLTranslator : Translator {

    private const val OFFICIAL_ENDPOINT = "https://api-free.deepl.com/v2/translate"
    private const val FALLBACK_ENDPOINT = "https://deeplx.gpu.nu/v2/translate"

    private val rawTranslator = DeepLTranslatorRaw()

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    override suspend fun doTranslate(
        from: String, to: String, query: String, entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {
        if (to.isEmpty()) {
            throw UnsupportedOperationException(getString(R.string.TranslateApiUnsupported) + " " + to)
        }

        try {
            val originalText = TLRPC.TL_textWithEntities().apply {
                this.text = query
                this.entities = entities
            }

            val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
                query,
                entities,
                false
            ) else query

            val translatedText = rawTranslator.translate(textToTranslate, from, to)

            val finalString = StringBuilder().append(translatedText)
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

    private suspend fun translateWithAPI(
        endpoint: String,
        targetLang: String,
        query: String,
        entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {
        val apiKey = getString(R.string.DEEPL_API_KEY)
        if (apiKey.isBlank()) error("Missing DeepL Translate Key")

        val originalText = TLRPC.TL_textWithEntities().apply {
            this.text = query
            this.entities = entities
        }

        val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
            query, entities, false
        ) else query

        val textArray = JSONArray().apply {
            put(textToTranslate)
        }

        val requestJsonPayload = JSONObject().apply {
            put("preserve_formatting", true)
            put("text", textArray)
            put("target_lang", targetLang.uppercase())
        }.toString()

        try {
            val responseBodyString = makeTranslateRequest(endpoint, apiKey, requestJsonPayload)
            val respObj = JSONObject(responseBodyString)
            val respArr = respObj.getJSONArray("translations")

            if (respArr.length() == 0) error("Empty translation result from DeepL API")

            val translatedText = respArr.getJSONObject(0).getString("text")
            val finalString = StringBuilder().append(translatedText)

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
        } catch (e: IOException) {
            error("DeepL API request failed due to network issue: ${e.message}")
        } catch (e: JSONException) {
            error("DeepL API response parsing failed: ${e.message}")
        } catch (e: Exception) {
            error("An unexpected error occurred during DeepL API translation: ${e.message}")
        }
    }

    private suspend fun makeTranslateRequest(
        endpoint: String, apiKey: String, requestBodyJson: String
    ): String {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = requestBodyJson.toRequestBody(mediaType)

        val request = Request.Builder().url(endpoint)
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .post(body).build()

        return httpClient.newCall(request).await().use { response ->
            val responseBodyString = response.body.string()
            if (!response.isSuccessful) {
                error("HTTP ${response.code} : $responseBodyString")
            }
            responseBodyString
        }
    }
}