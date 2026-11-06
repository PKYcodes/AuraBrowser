package com.aura.browser.core.network.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraDownloadValidatorTest {

    private val validator = AuraDownloadValidator()

    @Test
    fun `flags apk download as high risk requiring user warning`() {
        val res = validator.validateDownload(
            rawFileName = "../evil_update.apk",
            sourceUrl = "https://example.com/evil_update.apk",
            mimeType = "application/vnd.android.package-archive",
        )
        assertTrue(res.requiresUserWarning)
        assertFalse(res.sanitizedFileName.contains(".."))
    }

    @Test
    fun `marks standard pdf download as safe`() {
        val res = validator.validateDownload(
            rawFileName = "geckoview-guide.pdf",
            sourceUrl = "https://mozilla.org/geckoview-guide.pdf",
            mimeType = "application/pdf",
        )
        assertFalse(res.requiresUserWarning)
        assertEquals("geckoview-guide.pdf", res.sanitizedFileName)
    }
}
