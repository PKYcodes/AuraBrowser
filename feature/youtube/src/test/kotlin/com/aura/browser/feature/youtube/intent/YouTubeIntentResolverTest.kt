package com.aura.browser.feature.youtube.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeIntentResolverTest {

    private val resolver = YouTubeIntentResolver()

    @Test
    fun `resolves shared youtu_be short link inside text body`() {
        val sharedText = "Watch this talk: https://youtu.be/dQw4w9WgXcQ?si=abc123"
        val result = resolver.extractAndNormalize(sharedText)
        assertNotNull(result)
        assertTrue(result!!.isYouTubeUrl)
        assertEquals("dQw4w9WgXcQ", result.videoId)
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", result.canonicalHttpsUrl)
    }

    @Test
    fun `resolves youtube shorts url correctly`() {
        val result = resolver.extractAndNormalize("https://www.youtube.com/shorts/abc987xyz")
        assertNotNull(result)
        assertTrue(result!!.isYouTubeUrl)
        assertTrue(result.isShorts)
        assertEquals("https://m.youtube.com/shorts/abc987xyz", result.canonicalHttpsUrl)
    }
}
