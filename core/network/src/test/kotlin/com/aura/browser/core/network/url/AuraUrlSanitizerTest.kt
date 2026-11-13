package com.aura.browser.core.network.url

import com.aura.browser.core.common.model.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraUrlSanitizerTest {

    private val sanitizer = AuraUrlSanitizer()

    private fun valid(input: String, httpsOnly: Boolean = true) =
        sanitizer.resolveInput(input, SearchEngine.DUCKDUCKGO, httpsOnly) as ResolvedNavigationTarget.ValidUrl

    @Test
    fun `blocks unsafe schemes`() {
        listOf(
            "javascript:alert(document.cookie)",
            "VBScript:msgbox(1)",
            "data:text/html,<script>1</script>",
            "file:///data/data/com.aura.browser/databases",
            "content://com.android.contacts/contacts",
            "resource://android/assets/x",
        ).forEach { input ->
            assertTrue(input, sanitizer.resolveInput(input) is ResolvedNavigationTarget.BlockedUnsafeScheme)
        }
    }

    @Test
    fun `upgrades cleartext http domain to https when httpsOnlyMode enabled`() {
        val valid = valid("http://mozilla.org/en-US/")
        assertEquals("https://mozilla.org/en-US/", valid.url)
        assertTrue(valid.wasUpgradedToHttps)
    }

    @Test
    fun `upgrade handles uppercase scheme`() {
        assertEquals("https://example.com/a", valid("HTTP://example.com/a").url)
    }

    @Test
    fun `does not upgrade local and private hosts`() {
        listOf(
            "http://localhost:3000/app",
            "http://127.0.0.1:8080",
            "http://192.168.1.10/admin",
            "http://10.0.0.5:9000",
            "http://172.20.1.1",
            "http://printer.local/",
        ).forEach { input ->
            val v = valid(input)
            assertTrue(input, v.url.startsWith("http://"))
            assertFalse(input, v.wasUpgradedToHttps)
        }
    }

    @Test
    fun `bare localhost and ip addresses are navigated not searched`() {
        assertEquals("http://localhost:3000", valid("localhost:3000").url)
        assertEquals("http://192.168.0.1", valid("192.168.0.1").url)
        assertEquals("http://10.0.0.1:8080/status", valid("10.0.0.1:8080/status").url)
    }

    @Test
    fun `bare domain gets https prefix`() {
        val v = valid("example.com/path?x=1")
        assertEquals("https://example.com/path?x=1", v.url)
        assertTrue(v.wasUpgradedToHttps)
    }

    @Test
    fun `domain with query but no path is recognised`() {
        assertEquals("https://example.com?q=1", valid("example.com?q=1").url)
    }

    @Test
    fun `strips utm and fbclid tracking query parameters`() {
        val cleaned = sanitizer.normalizeTrackingParams(
            "https://example.com/article?id=42&utm_source=newsletter&fbclid=IwAR123"
        )
        assertEquals("https://example.com/article?id=42", cleaned)
    }

    @Test
    fun `removes query entirely when only tracking params exist and keeps fragment`() {
        assertEquals(
            "https://example.com/a#section",
            sanitizer.normalizeTrackingParams("https://example.com/a?utm_source=x&gclid=1#section"),
        )
    }

    @Test
    fun `never double encodes percent escapes`() {
        val url = "https://example.com/caf%C3%A9?q=hello%20world&redirect=https%3A%2F%2Fa.b%2F&utm_medium=x"
        assertEquals(
            "https://example.com/caf%C3%A9?q=hello%20world&redirect=https%3A%2F%2Fa.b%2F",
            sanitizer.normalizeTrackingParams(url),
        )
        // And through the full resolver:
        assertEquals(
            "https://example.com/caf%C3%A9?q=hello%20world",
            valid("https://example.com/caf%C3%A9?q=hello%20world").url,
        )
    }

    @Test
    fun `converts natural language query to DuckDuckGo search URL`() {
        val result = sanitizer.resolveInput(
            rawInput = "kotlin geckoview android tutorial",
            searchEngine = SearchEngine.DUCKDUCKGO,
            httpsOnlyMode = true,
        )
        assertTrue(result is ResolvedNavigationTarget.SearchQuery)
        assertEquals(
            "https://duckduckgo.com/?q=kotlin+geckoview+android+tutorial",
            (result as ResolvedNavigationTarget.SearchQuery).searchUrl,
        )
    }

    @Test
    fun `private host classification`() {
        assertTrue(sanitizer.isLocalOrPrivateHost("172.31.255.1"))
        assertFalse(sanitizer.isLocalOrPrivateHost("172.32.0.1"))
        assertFalse(sanitizer.isLocalOrPrivateHost("example.com"))
        assertTrue(sanitizer.isLocalOrPrivateHost("[::1]"))
    }
}
