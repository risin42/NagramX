package tw.nekomimi.nekogram.llm.preset

import org.telegram.messenger.R

object LlmPresetRegistry {

    const val CUSTOM = 0
    const val OPENAI = 1
    const val GEMINI = 2
    const val GROQ = 3
    const val DEEPSEEK = 4
    const val XAI = 5
    const val CEREBRAS = 6
    const val OLLAMA_CLOUD = 7
    const val OPENROUTER = 8
    const val VERCEL_AI_GATEWAY = 9

    private val presetBaseUrls = mapOf(
        OPENAI to "https://api.openai.com/v1",
        GEMINI to "https://generativelanguage.googleapis.com/v1beta/openai",
        GROQ to "https://api.groq.com/openai/v1",
        DEEPSEEK to "https://api.deepseek.com/v1",
        XAI to "https://api.x.ai/v1",
        CEREBRAS to "https://api.cerebras.ai/v1",
        OLLAMA_CLOUD to "https://ollama.com/v1",
        OPENROUTER to "https://openrouter.ai/api/v1",
        VERCEL_AI_GATEWAY to "https://ai-gateway.vercel.sh/v1",
    )

    private val defaultModelResIds = mapOf(
        CUSTOM to R.string.LlmModelNameDefault,
        OPENAI to R.string.LlmProviderOpenAIModel,
        GEMINI to R.string.LlmProviderGeminiModel,
        GROQ to R.string.LlmProviderGroqModel,
        DEEPSEEK to R.string.LlmProviderDeepSeekModel,
        XAI to R.string.LlmProviderXAIModel,
        CEREBRAS to R.string.LlmProviderCerebrasModel,
        OLLAMA_CLOUD to R.string.LlmProviderOllamaCloudModel,
        OPENROUTER to R.string.LlmProviderOpenRouterModel,
        VERCEL_AI_GATEWAY to R.string.LlmProviderVercelAIGatewayModel,
    )

    @JvmStatic
    fun getPresetBaseUrl(preset: Int): String? {
        return presetBaseUrls[preset]
    }

    @JvmStatic
    fun getDefaultModelResId(preset: Int): Int {
        return defaultModelResIds[preset] ?: R.string.LlmModelNameDefault
    }
}
