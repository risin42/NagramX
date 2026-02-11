package tw.nekomimi.nekogram.llm

import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.config.ConfigItem
import tw.nekomimi.nekogram.llm.preset.LlmPresetRegistry
import tw.nekomimi.nekogram.llm.utils.LlmUrlNormalizer
import tw.nekomimi.nekogram.translate.Translator
import xyz.nextalone.nagram.NaConfig

object LlmConfig {

    @JvmStatic
    fun getDefaultModelName(preset: Int): String {
        return getString(LlmPresetRegistry.getDefaultModelResId(preset))
    }

    @JvmStatic
    fun getSavedModelName(preset: Int): String {
        val value = when (preset) {
            LlmPresetRegistry.OPENAI -> NaConfig.llmProviderOpenAIModel.String()
            LlmPresetRegistry.GEMINI -> NaConfig.llmProviderGeminiModel.String()
            LlmPresetRegistry.GROQ -> NaConfig.llmProviderGroqModel.String()
            LlmPresetRegistry.DEEPSEEK -> NaConfig.llmProviderDeepSeekModel.String()
            LlmPresetRegistry.XAI -> NaConfig.llmProviderXAIModel.String()
            LlmPresetRegistry.CEREBRAS -> NaConfig.llmProviderCerebrasModel.String()
            LlmPresetRegistry.OLLAMA_CLOUD -> NaConfig.llmProviderOllamaCloudModel.String()
            LlmPresetRegistry.OPENROUTER -> NaConfig.llmProviderOpenRouterModel.String()
            LlmPresetRegistry.VERCEL_AI_GATEWAY -> NaConfig.llmProviderVercelAIGatewayModel.String()
            else -> NaConfig.llmModelName.String()
        }
        return value?.trim() ?: ""
    }

    @JvmStatic
    fun setSavedModelName(preset: Int, model: String?) {
        val value = model?.trim() ?: ""
        when (preset) {
            LlmPresetRegistry.OPENAI -> NaConfig.llmProviderOpenAIModel.setConfigString(value)
            LlmPresetRegistry.GEMINI -> NaConfig.llmProviderGeminiModel.setConfigString(value)
            LlmPresetRegistry.GROQ -> NaConfig.llmProviderGroqModel.setConfigString(value)
            LlmPresetRegistry.DEEPSEEK -> NaConfig.llmProviderDeepSeekModel.setConfigString(value)
            LlmPresetRegistry.XAI -> NaConfig.llmProviderXAIModel.setConfigString(value)
            LlmPresetRegistry.CEREBRAS -> NaConfig.llmProviderCerebrasModel.setConfigString(value)
            LlmPresetRegistry.OLLAMA_CLOUD -> NaConfig.llmProviderOllamaCloudModel.setConfigString(value)
            LlmPresetRegistry.OPENROUTER -> NaConfig.llmProviderOpenRouterModel.setConfigString(value)
            LlmPresetRegistry.VERCEL_AI_GATEWAY -> NaConfig.llmProviderVercelAIGatewayModel.setConfigString(value)
            else -> NaConfig.llmModelName.setConfigString(value)
        }
    }

    @JvmStatic
    fun getEffectiveModelName(preset: Int): String {
        val saved = getSavedModelName(preset)
        return saved.ifBlank {
            getDefaultModelName(preset)
        }
    }

    @JvmStatic
    fun getEffectiveBaseUrl(preset: Int): String {
        return if (preset == LlmPresetRegistry.CUSTOM) {
            val userUrl = NaConfig.llmApiUrl.String().trim()
            userUrl.ifEmpty {
                getString(R.string.LlmApiUrlDefault)
            }
        } else {
            LlmPresetRegistry.getPresetBaseUrl(preset).orEmpty()
        }
    }

    @JvmStatic
    fun setSavedCustomBaseUrl(baseUrl: String?) {
        val value = LlmUrlNormalizer.normalizeBaseUrl(baseUrl)
        NaConfig.llmApiUrl.setConfigString(value)
    }

    @JvmStatic
    fun getApiKeyConfigItem(preset: Int): ConfigItem {
        return when (preset) {
            LlmPresetRegistry.OPENAI -> NaConfig.llmProviderOpenAIKey
            LlmPresetRegistry.GEMINI -> NaConfig.llmProviderGeminiKey
            LlmPresetRegistry.GROQ -> NaConfig.llmProviderGroqKey
            LlmPresetRegistry.DEEPSEEK -> NaConfig.llmProviderDeepSeekKey
            LlmPresetRegistry.XAI -> NaConfig.llmProviderXAIKey
            LlmPresetRegistry.CEREBRAS -> NaConfig.llmProviderCerebrasKey
            LlmPresetRegistry.OLLAMA_CLOUD -> NaConfig.llmProviderOllamaCloudKey
            LlmPresetRegistry.OPENROUTER -> NaConfig.llmProviderOpenRouterKey
            LlmPresetRegistry.VERCEL_AI_GATEWAY -> NaConfig.llmProviderVercelAIGatewayKey
            else -> NaConfig.llmApiKey
        }
    }

    @JvmStatic
    fun getFirstApiKey(preset: Int): String? {
        val raw = getApiKeyConfigItem(preset).String()?.trim()
        if (raw.isNullOrBlank()) {
            return null
        }
        return raw.split(",")
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    @JvmStatic
    fun isLLMTranslatorAvailable(): Boolean {
        val llmProvider = NaConfig.llmProviderPreset.Int()
        val keyConfig = when (llmProvider) {
            LlmPresetRegistry.OPENAI -> NaConfig.llmProviderOpenAIKey
            LlmPresetRegistry.GEMINI -> NaConfig.llmProviderGeminiKey
            LlmPresetRegistry.GROQ -> NaConfig.llmProviderGroqKey
            LlmPresetRegistry.DEEPSEEK -> NaConfig.llmProviderDeepSeekKey
            LlmPresetRegistry.XAI -> NaConfig.llmProviderXAIKey
            LlmPresetRegistry.CEREBRAS -> NaConfig.llmProviderCerebrasKey
            LlmPresetRegistry.OLLAMA_CLOUD -> NaConfig.llmProviderOllamaCloudKey
            LlmPresetRegistry.OPENROUTER -> NaConfig.llmProviderOpenRouterKey
            LlmPresetRegistry.VERCEL_AI_GATEWAY -> NaConfig.llmProviderVercelAIGatewayKey
            else -> NaConfig.llmApiKey
        }
        return keyConfig.String().isNotEmpty()
    }

    @JvmStatic
    fun llmIsDefaultProvider(): Boolean {
        return NekoConfig.translationProvider.Int() == Translator.providerLLMTranslator
    }
}
