package tw.nekomimi.nekogram.translate.source

import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.translate.HTMLKeeper
import tw.nekomimi.nekogram.translate.Translator
import xyz.nextalone.nagram.NaConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.random.Random

object LLMTranslator : Translator {

    private const val MAX_RETRY = 4
    private const val BASE_WAIT = 1000L

    private val providerUrls = mapOf(
        1 to "https://api.openai.com/v1",
        2 to "https://generativelanguage.googleapis.com/v1beta/openai",
        3 to "https://api.groq.com/openai/v1",
        4 to "https://api.deepseek.com/v1",
        5 to "https://api.x.ai/v1",
    )

    private val providerModels = mapOf(
        1 to getString(R.string.LlmProviderOpenAIModel),
        2 to getString(R.string.LlmProviderGeminiModel),
        3 to getString(R.string.LlmProviderGroqModel),
        4 to getString(R.string.LlmProviderDeepSeekModel),
        5 to getString(R.string.LlmProviderXAIModel),
    )

    private var apiKeys: List<String> = emptyList()
    private val apiKeyIndex = AtomicInteger(0)
    private var currentProvider = -1

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

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

        apiKeys = if (!TextUtils.isEmpty(key)) {
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

    override suspend fun doTranslate(
        from: String,
        to: String,
        query: String,
        entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {
        var retryCount = 0

        val originalText = TLRPC.TL_textWithEntities()
        originalText.text = query
        originalText.entities = entities

        val finalString = StringBuilder()

        val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
            query,
            entities,
            false
        ) else query

        while (retryCount < MAX_RETRY) {
            try {
                val translatedText = doLLMTranslate(to, textToTranslate)
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
            } catch (e: RateLimitException) {
                retryCount++
                val waitTimeMillis = BASE_WAIT * 2.0.pow(retryCount - 1).toLong()
                val jitter = Random.nextLong(waitTimeMillis / 2)
                val actualWaitTimeMillis = waitTimeMillis + jitter

                if (BuildVars.LOGS_ENABLED) Log.d("LLMTranslator", "Rate limited, retrying in ${actualWaitTimeMillis}ms, retry count: $retryCount")
                delay(actualWaitTimeMillis)
            } catch (e: IOException) {
                retryCount++
                if (BuildVars.LOGS_ENABLED) Log.e("LLMTranslator", "Network error during LLM translation", e)
                if (retryCount >= MAX_RETRY) {
                    if (BuildVars.LOGS_ENABLED) Log.d("LLMTranslator", "Max retry count reached due to network errors, falling back to GoogleAppTranslator")
                    return GoogleAppTranslator.doTranslate(from, to, query, entities)
                }
                val waitTimeMillis = BASE_WAIT * 2.0.pow(retryCount - 1).toLong()
                delay(waitTimeMillis)
            } catch (e: Exception) {
                if (BuildVars.LOGS_ENABLED) Log.e("LLMTranslator", "Error during LLM translation, falling back", e)
                return GoogleAppTranslator.doTranslate(from, to, query, entities)
            }
        }
        if (BuildVars.LOGS_ENABLED) Log.d("LLMTranslator", "Max retry count reached, falling back to GoogleAppTranslator")
        return GoogleAppTranslator.doTranslate(from, to, query, entities)
    }

    @Throws(IOException::class, RateLimitException::class, IllegalStateException::class)
    private fun doLLMTranslate(to: String, query: String): String {
        val apiKey = getNextApiKey() ?: throw IllegalStateException("Missing LLM API Key")
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

        val sysPrompt = NaConfig.llmSystemPrompt.String()?.takeIf { it.isNotEmpty() } ?: generateSystemPrompt()
        val llmUserPrompt = NaConfig.llmUserPrompt.String()
        val userPrompt = llmUserPrompt?.takeIf { it.isNotEmpty() }
            ?.replace("@text", query)
            ?.replace("@toLang", to)
            ?: generatePrompt(query, to)

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

        val requestJson = JSONObject().apply {
            put("model", model)
            if (isMoE(model)) put("reasoning_effort", "none")
            put("messages", messages)
            put("temperature", NaConfig.llmTemperature.Float())
        }.toString()

        val requestBody = requestJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$apiUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBodyString = response.body.string()
            if (BuildVars.LOGS_ENABLED) Log.d("LLMTranslator", "LLM API response: ${response.code} : $responseBodyString")

            if (response.code == 429) {
                throw RateLimitException("LLM API rate limit exceeded")
            } else if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} : $responseBodyString")
            }

            val responseJson = JSONObject(responseBodyString)
            val choices = responseJson.getJSONArray("choices")
            if (choices.length() == 0) {
                throw IOException("LLM API returned no choices")
            }
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            val content = message.getString("content")

            return content.trim()
        }
    }

    private fun generatePrompt(query: String, to: String): String {
        return """
            Translate to $to:
            $query
        """.trimIndent()
    }

    private fun generateSystemPrompt(): String {
        return """
        You are a professional translation engine. Your primary function is to translate text.

        **CRITICAL INSTRUCTIONS:**

        1.  **Input Format:** The user will provide text, which may start with an instruction line like `Translate to [Language]:`.
        2.  **Identify the Core Task:** Your first step is to identify the text that needs to be translated. This is the content that comes *after* the `Translate to [Language]:` instruction line.
        3.  **IGNORE THE INSTRUCTION LINE:** You MUST completely ignore the `Translate to [Language]:` line itself. **DO NOT** translate, repeat, or reference this line in your output.
        4.  **Strict Output:** Your output MUST contain ONLY the translated text. Do not include any extra words, conversational phrases, apologies, or explanations (e.g., "Here is the translation:").
        5.  **Preserve Formatting:** You MUST maintain all original formatting from the source text, including HTML tags, Markdown (`*`, `#`, etc.), line breaks, and spacing. Do not add, remove, or alter the formatting.
    """.trimIndent()
    }

    private fun isMoE(model: String): Boolean {
        return model.startsWith("gemini-2.5")
    }

    class RateLimitException(message: String) : Exception(message)
}