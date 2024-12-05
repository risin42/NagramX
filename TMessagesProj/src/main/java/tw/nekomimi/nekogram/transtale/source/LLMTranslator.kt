package tw.nekomimi.nekogram.transtale.source

import android.util.Log
import cn.hutool.core.util.StrUtil
import cn.hutool.http.HttpUtil
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.transtale.source.GoogleAppTranslator
import tw.nekomimi.nekogram.transtale.Translator
import xyz.nextalone.nagram.NaConfig

object LLMTranslator : Translator {

    private const val maxRetryCount = 3
    private const val baseWaitTimeMillis = 1000L

    override suspend fun doTranslate(from: String, to: String, query: String): String {
        var retryCount = 0
        while (retryCount < maxRetryCount) {
            try {
                return doLLMTranslate(from, to, query)
            } catch (e: RateLimitException) {
                retryCount++
                val waitTimeMillis = baseWaitTimeMillis * 2.0.pow(retryCount -1).toLong()
                val jitter = Random.nextLong(waitTimeMillis / 2)
                val actualWaitTimeMillis = waitTimeMillis + jitter

                if (actualWaitTimeMillis > 3000) {
                    break
                }

                Log.w("LLMTranslator", "Rate limited, retrying in ${actualWaitTimeMillis}ms, retry count: $retryCount")
                delay(actualWaitTimeMillis)
            }
        }
        Log.w("LLMTranslator", "Max retry count reached, fallback to GoogleAppTranslator")
        return GoogleAppTranslator.doTranslate(from, to, query)
    }

    private suspend fun doLLMTranslate(from: String, to: String, query: String): String {
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
            .timeout(20000)
            .body(
                JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                }.toString()
            )
            .execute()

        if (response.status == 429) {
            throw RateLimitException("LLM API rate limit exceeded")
        } else if (response.status != 200) {
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
            Text: $query
        """.trimIndent()
    }

    class RateLimitException(message: String) : Exception(message)
}