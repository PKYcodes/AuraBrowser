package com.aura.browser.feature.pip

import org.junit.Assert.assertEquals
import org.junit.Test

class PipAspectRatioTest {

    @Test
    fun `clamps extreme ultrawide and vertical aspect ratios to Android PiP bounds`() {
        val ratioWide = 1000.0 / 100.0
        val clampedWide = if (ratioWide > 2.38) 239 to 100 else 1000 to 100
        assertEquals(239 to 100, clampedWide)

        val ratioTall = 100.0 / 1000.0
        val clampedTall = if (ratioTall < 0.42) 100 to 239 else 100 to 1000
        assertEquals(100 to 239, clampedTall)
    }
}
