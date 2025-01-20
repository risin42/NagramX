package tw.nekomimi.nekogram.transtale.source

import cn.hutool.http.HttpUtil
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.transtale.Translator
import tw.nekomimi.nekogram.transtale.source.raw.DeepLTranslatorRaw
import kotlin.random.Random

object DeepLTranslator : Translator {

    private const val OFFICIAL_ENDPOINT = "https://api-free.deepl.com/v2/translate"
    private const val FALLBACK_ENDPOINT = "https://deeplx.gpu.nu/v2/translate"

    private val rawTranslator = DeepLTranslatorRaw()

    override suspend fun doTranslate(from: String, to: String, query: String): String {
        if (to.isEmpty()) {
            throw UnsupportedOperationException(LocaleController.getString(R.string.TranslateApiUnsupported))
        }

        try {
            return rawTranslator.translate(query, from, to)
        } catch (e: Exception) {
            val endpoint = if (Random.nextBoolean()) OFFICIAL_ENDPOINT else FALLBACK_ENDPOINT
            return translateWithAPI(endpoint, to, query)
        }
    }

    private fun translateWithAPI(endpoint: String, targetLang: String, query: String): String {
        val apiKey = LocaleController.getString(R.string.DEEPL_API_KEY)
        if (apiKey.isBlank()) error("Missing DeepL Translate Key")

        val textArray = JSONArray().apply {
            put(query)
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

        return respArr.getJSONObject(0).getString("text")
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