package com.aura.browser.core.network.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `double extension is judged by the last extension`() {
        assertTrue(validator.validateDownload("invoice.pdf.apk", "https://x.y/z", null).requiresUserWarning)
    }

    @Test
    fun `file name fallback ignores query string and decodes url escapes`() {
        val res = validator.validateDownload(null, "https://cdn.example.com/files/My%20Report.pdf?token=abc#frag", null)
        assertEquals("My Report.pdf", res.sanitizedFileName)
    }

    @Test
    fun `strips path traversal hidden prefix and caps length`() {
        val res = validator.validateDownload("..\\..\\.hidden/" + "a".repeat(300) + ".txt", "https://x.y/", null)
        assertFalse(res.sanitizedFileName.startsWith("."))
        assertTrue(res.sanitizedFileName.length <= 120)
        assertTrue(res.sanitizedFileName.endsWith(".txt"))
    }

    @Test
    fun `mime parameters are removed`() {
        assertEquals("text/plain", validator.validateDownload("a.txt", "https://x.y/", "text/plain; charset=utf-8").mimeType)
    }

    @Test
    fun `parses content disposition variants`() {
        assertEquals("report.pdf", validator.parseContentDispositionFileName("attachment; filename=\"report.pdf\""))
        assertEquals("report.pdf", validator.parseContentDispositionFileName("attachment; filename=report.pdf; size=3"))
        assertEquals(
            "na\u00efve caf\u00e9.txt",
            validator.parseContentDispositionFileName("attachment; filename*=UTF-8''na%C3%AFve%20caf%C3%A9.txt"),
        )
        assertNull(validator.parseContentDispositionFileName("inline"))
        assertNull(validator.parseContentDispositionFileName(null))
    }

    @Test
    fun `sha256 of empty input matches known digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            validator.computeSha256Hex(ByteArray(0)),
        )
    }
}
