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
 * Security-hardened URL sanitizer and HTTPS-first resolver for **user-entered** input
 * (omnibox, shared text, bookmarks, history).
 *
 * - Blocks script, data, local-file and engine-internal schemes.
 * - Upgrades cleartext HTTP to HTTPS when HTTPS-Only mode is active (except for local/private hosts).
 * - Strips well-known tracking query parameters **without re-encoding** the rest of the URL.
 *
 * In-page link navigation is policed separately by the engine's `NavigationDelegate`.
 */
@Singleton
class AuraUrlSanitizer @Inject constructor() {

    private val blockedSchemes = setOf(
        "javascript", "vbscript", "data", "jar", "intent",
        "file", "content", "filesystem", "blob", "resource", "chrome",
    )

    private val allowedInternalPages = setOf(
        "about:blank",
        "about:buildconfig",
        "about:config",
        "aura://home",
        "aura://offline",
    )

    private val domainRegex = Regex("^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d{1,5})?([/?#].*)?$")
    private val ipv4Regex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?([/?#].*)?$")
    private val localhostRegex = Regex("^localhost(:\\d{1,5})?([/?#].*)?$", RegexOption.IGNORE_CASE)

    private val trackingExactKeys = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "mc_cid", "mc_eid",
        "msclkid", "igshid", "yclid", "twclid", "_hsenc", "_hsmi",
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

        val hasWebScheme = trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)

        // Free text containing whitespace is always a search query.
        if (!hasWebScheme && trimmed.any { it.isWhitespace() }) {
            return buildSearchTarget(trimmed, searchEngine)
        }

        if (trimmed.startsWith("https://", ignoreCase = true)) {
            return ResolvedNavigationTarget.ValidUrl(normalizeTrackingParams(trimmed), false)
        }

        if (trimmed.startsWith("http://", ignoreCase = true)) {
            val withoutScheme = trimmed.substring("http://".length)
            val local = isLocalOrPrivateHost(extractHost(trimmed))
            return if (httpsOnlyMode && !local) {
                ResolvedNavigationTarget.ValidUrl(normalizeTrackingParams("https://$withoutScheme"), true)
            } else {
                ResolvedNavigationTarget.ValidUrl(normalizeTrackingParams("http://$withoutScheme"), false)
            }
        }

        // Bare host forms: localhost[:port], IPv4[:port], example.com[/path]
        if (localhostRegex.matches(trimmed) || ipv4Regex.matches(trimmed)) {
            return ResolvedNavigationTarget.ValidUrl("http://$trimmed", false)
        }

        if (domainRegex.matches(trimmed)) {
            val local = isLocalOrPrivateHost(extractHost("http://$trimmed"))
            val useHttps = httpsOnlyMode && !local
            val prefix = if (useHttps) "https://" else "http://"
            return ResolvedNavigationTarget.ValidUrl(normalizeTrackingParams("$prefix$trimmed"), useHttps)
        }

        return buildSearchTarget(trimmed, searchEngine)
    }

    /**
     * Strips known cross-site tracking query parameters (utm_*, fbclid, gclid, mc_cid, ...).
     *
     * Implemented with plain string operations on purpose: rebuilding the URL through the
     * multi-argument `java.net.URI` constructors would re-quote every `%` and corrupt
     * already percent-encoded paths and query values.
     */
    fun normalizeTrackingParams(url: String): String {
        val fragmentIndex = url.indexOf('#')
        val beforeFragment = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
        val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""

        val queryIndex = beforeFragment.indexOf('?')
        if (queryIndex < 0) return url

        val base = beforeFragment.substring(0, queryIndex)
        val kept = beforeFragment.substring(queryIndex + 1)
            .split('&')
            .filter { it.isNotEmpty() && !isTrackingKey(it.substringBefore('=')) }

        return if (kept.isEmpty()) base + fragment else base + "?" + kept.joinToString("&") + fragment
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

    /** True for loopback, RFC1918 private ranges, link-local, `*.local` and similar LAN-only hosts. */
    fun isLocalOrPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h.isEmpty()) return false
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".lan")) return true
        if (h == "::1") return true
        val parts = h.split('.')
        if (parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { n -> n in 0..255 } == true }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 10 ||
                a == 127 ||
                (a == 192 && b == 168) ||
                (a == 172 && b in 16..31) ||
                (a == 169 && b == 254)
        }
        return false
    }

    private fun isTrackingKey(rawKey: String): Boolean {
        val key = rawKey.lowercase()
        return key.startsWith("utm_") || key in trackingExactKeys
    }

    /** Extracts the host of an absolute `scheme://authority/...` URL without using java.net.URI. */
    private fun extractHost(url: String): String {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = url)
        val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
        return if (authority.startsWith("[")) {
            authority.substringBefore(']').trimStart('[')
        } else {
            authority.substringBefore(':')
        }
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
