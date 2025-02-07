package tw.nekomimi.nekogram.transtale.source

import android.util.Log
import cn.hutool.core.util.StrUtil
import cn.hutool.http.HttpUtil
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import tw.nekomimi.nekogram.transtale.Translator
import xyz.nextalone.nagram.NaConfig
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.random.Random

object LLMTranslator : Translator {

    private const val maxRetryCount = 3
    private const val baseWaitTimeMillis = 1000L

    private val providerUrls = mapOf(
        1 to "https://api.openai.com/v1",
        2 to "https://generativelanguage.googleapis.com/v1beta/openai",
        3 to "https://api.groq.com/openai/v1",
        4 to "https://api.deepseek.com/v1",
        5 to "https://api.x.ai/v1",
    )

    private val providerModels = mapOf(
        1 to "gpt-4o-mini",
        2 to "gemini-2.0-flash-lite-preview",
        3 to "llama-3.3-70b-versatile",
        4 to "deepseek-chat",
        5 to "grok-2",
    )

    private var apiKeys: List<String> = emptyList()
    private val apiKeyIndex = AtomicInteger(0)
    private var currentProvider = -1

    private fun updateApiKeys() {
        val llmProvider = NaConfig.llmProviderPreset.Int()
        if (currentProvider == llmProvider && apiKeys.isNotEmpty()) {
            return
        }

        val keyConfig = when (llmProvider) {
            1 -> NaConfig.llmProviderOpenAIKey
            2 -> NaConfig.llmProviderGeminiKey
            3 -> NaConfig.llmProviderGroqKey
            4 -> NaConfig.llmProviderDeepSeekKey
            5 -> NaConfig.llmProviderXAIKey
            else -> NaConfig.llmApiKey
        }
        val key = keyConfig.String()

        apiKeys = if (StrUtil.isNotBlank(key)) {
            key.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        currentProvider = llmProvider
        apiKeyIndex.set(0)
    }

    private fun getNextApiKey(): String? {
        updateApiKeys()
        if (apiKeys.isEmpty()) {
            return null
        }

        val index = apiKeyIndex.getAndIncrement() % apiKeys.size
        if (apiKeyIndex.get() >= apiKeys.size * 2) {
            apiKeyIndex.set(index + 1)
        }
        return apiKeys[index]
    }

    override suspend fun doTranslate(from: String, to: String, query: String): String {
        var retryCount = 0
        while (retryCount < maxRetryCount) {
            try {
                return doLLMTranslate(from, to, query)
            } catch (e: RateLimitException) {
                retryCount++
                val waitTimeMillis = baseWaitTimeMillis * 2.0.pow(retryCount - 1).toLong()
                val jitter = Random.nextLong(waitTimeMillis / 2)
                val actualWaitTimeMillis = waitTimeMillis + jitter

                if (actualWaitTimeMillis > 3000) {
                    break
                }

                if (BuildVars.LOGS_ENABLED) Log.d("LLMTranslator", "Rate limited, retrying in ${actualWaitTimeMillis}ms, retry count: $retryCount")
                delay(actualWaitTimeMillis)
            }
        }
        if (BuildVars.LOGS_ENABLED) Log.d("LLMTranslator", "Max retry count reached, falling back to GoogleAppTranslator")
        return GoogleAppTranslator.doTranslate(from, to, query)
    }

    private suspend fun doLLMTranslate(from: String, to: String, query: String): String {
        val apiKey = getNextApiKey()
        if (apiKey == null) {
            throw IllegalStateException("Missing LLM API Key")
        }
        val apiKeyForLog = apiKey.takeLast(3).padStart(apiKey.length, '*')
        if (BuildVars.LOGS_ENABLED) Log.d("LLMTranslator", "createPost: Bearer $apiKeyForLog")

        val llmProviderPreset = NaConfig.llmProviderPreset.Int()
        val apiUrl = providerUrls.getOrDefault(
            llmProviderPreset,
            NaConfig.llmApiUrl.String().ifEmpty { getString(R.string.LlmApiUrlDefault) })
            .removeSuffix("/").removeSuffix("/chat/completions")
        val model = providerModels.getOrDefault(
            llmProviderPreset,
            NaConfig.llmModelName.String().ifEmpty { getString(R.string.LlmModelNameDefault) })

        val sysPrompt = NaConfig.llmSystemPrompt.String().orEmpty()
        val userCustomPrompt = NaConfig.llmUserPrompt.String().orEmpty()
        val userPrompt = if (userCustomPrompt.isNotEmpty()) {
            userCustomPrompt.replace("@text", query).replace("@toLang", to)
        } else {
            generatePrompt(query, to)
        }

        val messages = JSONArray().apply {
            if (sysPrompt.isNotEmpty()) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", sysPrompt)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }
        if (BuildVars.LOGS_ENABLED) Log.d("LLMTranslator", "Requesting LLM API with model: $model, messages: $messages")

        val response = HttpUtil.createPost("$apiUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json; charset=UTF-8")
            .timeout(20000)
            .body(JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", NaConfig.llmTemperature.Float())
            }.toString())
            .execute()
        if (BuildVars.LOGS_ENABLED) Log.d("LLMTranslator", "LLM API response: ${response.status} : ${response.body()}")

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

    private fun generatePrompt(query: String, to: String): String {
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