package com.aura.browser.feature.youtube.intent

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeDeepLinkResult(
    val isYouTubeUrl: Boolean,
    val canonicalHttpsUrl: String,
    val videoId: String? = null,
    val playlistId: String? = null,
    val isShorts: Boolean = false,
)

@Singleton
class YouTubeIntentResolver @Inject constructor() {

    private val youtubeHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com",
    )

    private val urlExtractorRegex = Regex("(https?://[^\\s\"<>]+)")

    fun extractAndNormalize(rawSharedTextOrUrl: String?): YouTubeDeepLinkResult? {
        if (rawSharedTextOrUrl.isNullOrBlank()) return null
        val candidateUrl = urlExtractorRegex.find(rawSharedTextOrUrl)?.value ?: rawSharedTextOrUrl.trim()

        return runCatching {
            val normalizedInput = if (candidateUrl.startsWith("vnd.youtube:", ignoreCase = true)) {
                val idPart = candidateUrl.removePrefix("vnd.youtube:").removePrefix("//").substringBefore("?")
                "https://www.youtube.com/watch?v=$idPart"
            } else {
                candidateUrl
            }

            val uri = URI(normalizedInput)
            val host = (uri.host ?: "").lowercase()
            if (host !in youtubeHosts) {
                return YouTubeDeepLinkResult(
                    isYouTubeUrl = false,
                    canonicalHttpsUrl = normalizedInput,
                )
            }

            val path = uri.path ?: ""
            val queryParams = parseQueryMap(uri.rawQuery)

            val videoId = when {
                host == "youtu.be" -> path.trim('/').substringBefore('/')
                path.startsWith("/shorts/") -> path.removePrefix("/shorts/").substringBefore('/')
                path.startsWith("/embed/") -> path.removePrefix("/embed/").substringBefore('/')
                else -> queryParams["v"]
            }?.takeIf { it.isNotBlank() }

            val playlistId = queryParams["list"]?.takeIf { it.isNotBlank() }
            val isShorts = path.startsWith("/shorts/")

            val canonicalUrl = when {
                videoId != null && playlistId != null ->
                    "https://m.youtube.com/watch?v=$videoId&list=$playlistId"
                videoId != null && isShorts ->
                    "https://m.youtube.com/shorts/$videoId"
                videoId != null ->
                    "https://m.youtube.com/watch?v=$videoId"
                playlistId != null ->
                    "https://m.youtube.com/playlist?list=$playlistId"
                host == "music.youtube.com" ->
                    "https://music.youtube.com${path}${uri.rawQuery?.let { "?$it" } ?: ""}"
                else ->
                    "https://m.youtube.com${path.ifEmpty { "/" }}${uri.rawQuery?.let { "?$it" } ?: ""}"
            }

            YouTubeDeepLinkResult(
                isYouTubeUrl = true,
                canonicalHttpsUrl = canonicalUrl,
                videoId = videoId,
                playlistId = playlistId,
                isShorts = isShorts,
            )
        }.getOrNull()
    }

    private fun parseQueryMap(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val key = pair.substringBefore("=", "")
            val value = pair.substringAfter("=", "")
            if (key.isNotBlank()) key to value else null
        }.toMap()
    }
}
