package com.vmesspro.android.domain.config

data class ImportPreview(
    val profiles: List<ProxyProfile>,
    val subscriptionUrls: List<String>,
    val duplicateCount: Int,
    val invalidCount: Int,
) {
    val detectedServerCount: Int get() = profiles.size + duplicateCount
    val validServerCount: Int get() = profiles.size
}

object BulkImportParser {
    fun parse(input: String): ImportPreview {
        val expanded = expandIfBase64Subscription(input)
        val tokens = expanded
            .lineSequence()
            .flatMap { line -> line.split(Regex("\\s+(?=(?:vmess|vless|trojan|https?)://)" )).asSequence() }
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()

        val profiles = mutableListOf<ProxyProfile>()
        val subscriptions = mutableListOf<String>()
        val seenIds = hashSetOf<String>()
        var duplicates = 0
        var invalid = 0

        for (token in tokens) {
            when {
                token.startsWith("http://", ignoreCase = true) || token.startsWith("https://", ignoreCase = true) -> {
                    if (runCatching { java.net.URI(token) }.getOrNull()?.host.isNullOrBlank()) invalid++ else subscriptions += token
                }
                else -> when (val result = ConfigParser.parse(token)) {
                    is ParseResult.Success -> {
                        if (seenIds.add(result.profile.stableId)) profiles += result.profile else duplicates++
                    }
                    is ParseResult.Failure -> invalid++
                }
            }
        }
        return ImportPreview(
            profiles = profiles,
            subscriptionUrls = subscriptions.distinct(),
            duplicateCount = duplicates,
            invalidCount = invalid,
        )
    }

    private fun expandIfBase64Subscription(input: String): String {
        val trimmed = input.trim()
        if (trimmed.contains("://") || trimmed.contains('\n')) return trimmed
        val decoded = ConfigParser.decodeBase64(trimmed) ?: return trimmed
        return if (decoded.contains("://")) decoded else trimmed
    }
}
