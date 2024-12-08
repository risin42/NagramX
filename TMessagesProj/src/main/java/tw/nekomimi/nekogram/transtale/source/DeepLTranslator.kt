package tw.nekomimi.nekogram.transtale.source

import cn.hutool.http.HttpUtil
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.transtale.Translator

object DeepLTranslator : Translator {

    private const val API_ENDPOINT = "https://api-free.deepl.com/v2/translate"

    override suspend fun doTranslate(from: String, to: String, query: String): String {

        if (to.isEmpty()) {
            throw UnsupportedOperationException(LocaleController.getString(R.string.TranslateApiUnsupported))
        }

        val apiKey = BuildConfig.DEEPL_API_KEY
        if (apiKey.isBlank()) error("Missing DeepL Translate Key")
 
        val textArray = JSONArray().apply {
            put(query)
        }

        val requestBody = JSONObject().apply {
            put("text", textArray)
            put("target_lang", to.uppercase())
        }.toString()

        val response = HttpUtil.createPost(API_ENDPOINT)
                .header("Authorization", "DeepL-Auth-Key $apiKey")
                .header("Content-Type", "application/json")
                .body(requestBody)
                .execute()

        if (response.status != 200) {
            error("HTTP ${response.status} : ${response.body()}")
        }

        val respObj = JSONObject(response.body())
        val respArr = respObj.getJSONArray("translations")

        if (respArr.length() == 0) error("Empty translation result")

        return respArr.getJSONObject(0).getString("text")
    }
}