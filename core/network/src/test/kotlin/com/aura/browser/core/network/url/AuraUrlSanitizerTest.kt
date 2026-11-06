package com.aura.browser.core.network.url

import com.aura.browser.core.common.model.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraUrlSanitizerTest {

    private val sanitizer = AuraUrlSanitizer()

    @Test
    fun `blocks unsafe javascript scheme`() {
        val result = sanitizer.resolveInput("javascript:alert(document.cookie)")
        assertTrue(result is ResolvedNavigationTarget.BlockedUnsafeScheme)
    }

    @Test
    fun `upgrades cleartext http domain to https when httpsOnlyMode enabled`() {
        val result = sanitizer.resolveInput(
            rawInput = "http://mozilla.org/en-US/",
            searchEngine = SearchEngine.DUCKDUCKGO,
            httpsOnlyMode = true,
        )
        assertTrue(result is ResolvedNavigationTarget.ValidUrl)
        val valid = result as ResolvedNavigationTarget.ValidUrl
        assertEquals("https://mozilla.org/en-US/", valid.url)
        assertTrue(valid.wasUpgradedToHttps)
    }

    @Test
    fun `strips utm and fbclid tracking query parameters`() {
        val cleaned = sanitizer.normalizeTrackingParams(
            "https://example.com/article?id=42&utm_source=newsletter&fbclid=IwAR123"
        )
        assertEquals("https://example.com/article?id=42", cleaned)
    }

    @Test
    fun `converts natural language query to DuckDuckGo search URL`() {
        val result = sanitizer.resolveInput(
            rawInput = "kotlin geckoview android tutorial",
            searchEngine = SearchEngine.DUCKDUCKGO,
            httpsOnlyMode = true,
        )
        assertTrue(result is ResolvedNavigationTarget.SearchQuery)
        val search = result as ResolvedNavigationTarget.SearchQuery
        assertEquals("https://duckduckgo.com/?q=kotlin+geckoview+android+tutorial", search.searchUrl)
    }
}
