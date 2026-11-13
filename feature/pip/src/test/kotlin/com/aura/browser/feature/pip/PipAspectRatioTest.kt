package com.aura.browser.feature.pip

import org.junit.Assert.assertEquals
import org.junit.Test

class PipAspectRatioTest {

    @Test
    fun `standard ratios are returned unchanged`() {
        assertEquals(16 to 9, PipAspectRatio.clamp(16, 9))
        assertEquals(1920 to 1080, PipAspectRatio.clamp(1920, 1080))
        assertEquals(9 to 16, PipAspectRatio.clamp(9, 16))
    }

    @Test
    fun `ultra wide video is clamped to 239 to 100`() {
        assertEquals(239 to 100, PipAspectRatio.clamp(3000, 1000))
    }

    @Test
    fun `ultra tall video is clamped to 100 to 239`() {
        assertEquals(100 to 239, PipAspectRatio.clamp(1000, 3000))
    }

    @Test
    fun `boundary ratios are accepted as is`() {
        assertEquals(239 to 100, PipAspectRatio.clamp(239, 100))
        assertEquals(100 to 239, PipAspectRatio.clamp(100, 239))
    }

    @Test
    fun `invalid dimensions fall back to 16 to 9`() {
        assertEquals(16 to 9, PipAspectRatio.clamp(0, 0))
        assertEquals(16 to 9, PipAspectRatio.clamp(-5, 10))
    }
}
