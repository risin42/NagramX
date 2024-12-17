package tw.nekomimi.nekogram.transtale.source

import cn.hutool.http.HttpUtil
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.transtale.Translator

object DeepLTranslator : Translator {

    private const val OFFICIAL_ENDPOINT = "https://api-free.deepl.com/v2/translate"
    private const val FALLBACK_ENDPOINT = "https://deeplx.gpu.nu/v2/translate"

    override suspend fun doTranslate(from: String, to: String, query: String): String {

        if (to.isEmpty()) {
            throw UnsupportedOperationException(LocaleController.getString(R.string.TranslateApiUnsupported))
        }

        val apiKey = LocaleController.getString(R.string.DEEPL_API_KEY)
        if (apiKey.isBlank()) error("Missing DeepL Translate Key")
 
        val textArray = JSONArray().apply {
            put(query)
        }

        val requestBody = JSONObject().apply {
            put("preserve_formatting", true)
            put("text", textArray)
            put("target_lang", to.uppercase())
        }.toString()

        val response = try {
            makeTranslateRequest(OFFICIAL_ENDPOINT, apiKey, requestBody)
        } catch (e: Exception) {
            makeTranslateRequest(FALLBACK_ENDPOINT, apiKey, requestBody)
        }

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
            if (endpoint == OFFICIAL_ENDPOINT) {
                throw Exception("Official DeepL API failed")
            }
            error("HTTP ${response.status} : ${response.body()}")
        }

        return response
    }
}