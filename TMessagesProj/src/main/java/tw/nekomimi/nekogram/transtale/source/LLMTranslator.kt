package tw.nekomimi.nekogram.transtale.source

import cn.hutool.core.util.StrUtil
import cn.hutool.http.HttpUtil
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.transtale.Translator
import xyz.nextalone.nagram.NaConfig

object LLMTranslator : Translator {

    override suspend fun doTranslate(from: String, to: String, query: String): String {

        val apiKey = NaConfig.llmApiKey.String()
        if (StrUtil.isBlank(apiKey)) error("Missing LLM API Key")

        var apiUrl = NaConfig.llmApiUrl.String().takeIf { it.isNotEmpty() } ?: LocaleController.getString(R.string.LlmApiUrlDefault)
        apiUrl = apiUrl.removeSuffix("/")
        apiUrl = apiUrl.removeSuffix("/chat/completions")

        val model = NaConfig.llmModelName.String().takeIf { it.isNotEmpty() } ?: LocaleController.getString(R.string.LlmModelNameDefault)

        val generatedPrompt = generatePrompt(query, to)
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", generatedPrompt)
            })
        }

        val response = HttpUtil.createPost("$apiUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json; charset=UTF-8")
            .body(JSONObject().apply {
                put("model", model)
                put("messages", messages)
            }.toString())
            .execute()

        if (response.status != 200) {
            error("HTTP ${response.status} : ${response.body()}")
        }

        val responseBody = JSONObject(response.body())
        val choices = responseBody.getJSONArray("choices")
        val firstChoice = choices.getJSONObject(0)
        val message = firstChoice.getJSONObject("message")
        val content = message.getString("content")

        return content
    }

    fun generatePrompt(query: String, to: String): String {
        return """
            Translate the following text from its original language to $to.
            If the text contains code snippets, provide the translated text and briefly explain the function or purpose of the code in $to.
            Otherwise, only return the translated text without any explanation.
            Use plain text only.
            Text: `$query`
        """.trimIndent()
    }
}