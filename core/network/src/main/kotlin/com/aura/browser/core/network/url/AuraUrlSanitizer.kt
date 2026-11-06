package com.aura.browser.core.network.url

import com.aura.browser.core.common.model.SearchEngine
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ResolvedNavigationTarget {
    data class ValidUrl(val url: String, val wasUpgradedToHttps: Boolean) : ResolvedNavigationTarget
    data class SearchQuery(val searchUrl: String, val rawQuery: String) : ResolvedNavigationTarget
    data class BlockedUnsafeScheme(val rawInput: String, val reason: String) : ResolvedNavigationTarget
}

/**
 * Security-hardened URL sanitizer and HTTPS-first resolver.
 * Blocks dangerous schemes (javascript:, data:text/html, vbscript:, file:// traversal)
 * and upgrades cleartext HTTP navigation to HTTPS when HTTPS-Only mode is active.
 */
@Singleton
class AuraUrlSanitizer @Inject constructor() {

    private val blockedSchemes = setOf("javascript", "vbscript", "data", "jar", "intent")
    private val allowedInternalPages = setOf(
        "about:blank",
        "about:buildconfig",
        "about:config",
        "aura://home",
        "aura://offline",
    )

    private val domainRegex = Regex(
        pattern = "^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d{1,5})?(/.*)?$"
    )

    fun resolveInput(
        rawInput: String,
        searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
        httpsOnlyMode: Boolean = true,
    ): ResolvedNavigationTarget {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ResolvedNavigationTarget.ValidUrl(searchEngine.homeUrl, false)
        }

        if (allowedInternalPages.contains(trimmed.lowercase())) {
            return ResolvedNavigationTarget.ValidUrl(trimmed.lowercase(), false)
        }

        val schemeCandidate = trimmed.substringBefore(":", missingDelimiterValue = "").lowercase()
        if (schemeCandidate in blockedSchemes) {
            return ResolvedNavigationTarget.BlockedUnsafeScheme(
                rawInput = trimmed,
                reason = "Blocked potentially unsafe URI scheme '$schemeCandidate:' for security.",
            )
        }

        // If input contains whitespace and does not start with http/https, treat as search query
        if (trimmed.contains(" ") && !trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            return buildSearchTarget(trimmed, searchEngine)
        }

        if (trimmed.startsWith("https://", ignoreCase = true)) {
            return ResolvedNavigationTarget.ValidUrl(normalizeTrackingParams(trimmed), false)
        }

        if (trimmed.startsWith("http://", ignoreCase = true)) {
            val host = runCatching { URI(trimmed).host ?: "" }.getOrDefault("")
            val isLocalhost = host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.")
            return if (httpsOnlyMode && !isLocalhost) {
                val upgraded = "https://" + trimmed.removePrefix("http://").removePrefix("HTTP://")
                ResolvedNavigationTarget.ValidUrl(normalizeTrackingParams(upgraded), true)
            } else {
                ResolvedNavigationTarget.ValidUrl(normalizeTrackingParams(trimmed), false)
            }
        }

        if (domainRegex.matches(trimmed)) {
            val prefix = if (httpsOnlyMode) "https://" else "http://"
            return ResolvedNavigationTarget.ValidUrl(normalizeTrackingParams("$prefix$trimmed"), httpsOnlyMode)
        }

        return buildSearchTarget(trimmed, searchEngine)
    }

    /**
     * Strips known cross-site tracking query parameters (utm_*, fbclid, gclid, mc_cid).
     */
    fun normalizeTrackingParams(url: String): String {
        return runCatching {
            val uri = URI(url)
            val rawQuery = uri.rawQuery ?: return url
            val trackingPrefixes = listOf("utm_", "fbclid", "gclid", "mc_cid", "msclkid", "igshid")
            val filteredParams = rawQuery
                .split("&")
                .filter { param ->
                    val key = param.substringBefore("=").lowercase()
                    trackingPrefixes.none { prefix -> key == prefix || key.startsWith(prefix) }
                }
            val rebuiltQuery = filteredParams.joinToString("&").ifEmpty { null }
            URI(
                uri.scheme,
                uri.rawAuthority,
                uri.rawPath,
                rebuiltQuery,
                uri.rawFragment,
            ).toASCIIString()
        }.getOrDefault(url)
    }

    fun extractOrigin(url: String): String {
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme ?: return url
            val host = uri.host ?: return url
            val portPart = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            "$scheme://$host$portPart"
        }.getOrDefault(url)
    }

    private fun buildSearchTarget(
        query: String,
        searchEngine: SearchEngine,
    ): ResolvedNavigationTarget.SearchQuery {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val searchUrl = String.format(searchEngine.queryTemplate, encoded)
        return ResolvedNavigationTarget.SearchQuery(searchUrl = searchUrl, rawQuery = query)
    }
}
