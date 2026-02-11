package tw.nekomimi.nekogram.llm.utils

object LlmUrlNormalizer {

    @JvmStatic
    fun normalizeBaseUrl(url: String?): String {
        if (url == null) {
            return ""
        }

        var normalized = url.trim()
        if (normalized.isEmpty()) {
            return ""
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }

        val completionsSuffix = "/chat/completions"
        if (normalized.endsWith(completionsSuffix, ignoreCase = true)) {
            normalized = normalized.dropLast(completionsSuffix.length)
            while (normalized.endsWith("/")) {
                normalized = normalized.dropLast(1)
            }
        }

        return normalized
    }
}

