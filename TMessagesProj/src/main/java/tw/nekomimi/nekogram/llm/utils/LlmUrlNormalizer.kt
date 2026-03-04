package tw.nekomimi.nekogram.llm.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object LlmUrlNormalizer {

    private const val HTTP_PREFIX = "http://"
    private const val HTTPS_PREFIX = "https://"
    private const val COMPLETIONS_SUFFIX = "/chat/completions"

    @JvmStatic
    fun normalizeBaseUrl(url: String?): String {
        if (url == null) {
            return ""
        }

        var normalized = url.trim()
        if (normalized.isEmpty()) {
            return ""
        }

        if (!hasSupportedScheme(normalized)) {
            normalized = HTTPS_PREFIX + normalized
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }

        if (normalized.endsWith(COMPLETIONS_SUFFIX, ignoreCase = true)) {
            normalized = normalized.dropLast(COMPLETIONS_SUFFIX.length)
            while (normalized.endsWith("/")) {
                normalized = normalized.dropLast(1)
            }
        }

        return normalized
    }

    @JvmStatic
    fun isValidBaseUrl(url: String?): Boolean {
        val normalized = normalizeBaseUrl(url)
        if (normalized.isEmpty()) {
            return true
        }
        return isValidNormalizedBaseUrl(normalized)
    }

    @JvmStatic
    fun isValidNormalizedBaseUrl(url: String): Boolean {
        val parsed = url.trim().toHttpUrlOrNull() ?: return false
        if (parsed.host.isBlank()) {
            return false
        }
        return parsed.scheme.equals("http", ignoreCase = true) ||
                parsed.scheme.equals("https", ignoreCase = true)
    }

    private fun hasSupportedScheme(url: String): Boolean {
        return url.startsWith(HTTP_PREFIX, ignoreCase = true) ||
                url.startsWith(HTTPS_PREFIX, ignoreCase = true)
    }
}
