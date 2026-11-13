package com.aura.browser.core.network.download

import java.net.URLDecoder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ValidatedDownloadRequest(
    val sanitizedFileName: String,
    val mimeType: String,
    val requiresUserWarning: Boolean,
    val warningReason: String? = null,
)

/**
 * Validates and sanitizes download metadata coming from untrusted web content.
 */
@Singleton
class AuraDownloadValidator @Inject constructor() {

    private val highRiskExtensions = setOf(
        "apk", "xapk", "apks", "dex", "so", "sh", "bat", "cmd", "exe", "msi", "scr", "jar", "vbs", "ps1",
    )

    fun validateDownload(
        rawFileName: String?,
        sourceUrl: String,
        mimeType: String?,
    ): ValidatedDownloadRequest {
        val fallbackName = sourceUrl
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('/')
            .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

        val candidate = sanitizeFileName(rawFileName?.takeIf { it.isNotBlank() } ?: fallbackName)

        val extension = candidate.substringAfterLast('.', "").lowercase()
        val resolvedMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: guessMimeType(extension)

        val isHighRisk = extension in highRiskExtensions ||
            resolvedMime == "application/vnd.android.package-archive"

        return ValidatedDownloadRequest(
            sanitizedFileName = candidate,
            mimeType = resolvedMime,
            requiresUserWarning = isHighRisk,
            warningReason = if (isHighRisk) {
                "Executable or package file (.$extension) can harm your device. Verify the source before opening."
            } else null,
        )
    }

    /** Returns true if [fileName] would be flagged as potentially dangerous. */
    fun isHighRiskFileName(fileName: String, mimeType: String? = null): Boolean =
        validateDownload(fileName, "", mimeType).requiresUserWarning

    /**
     * Extracts the file name from a `Content-Disposition` header value.
     * Supports both `filename="a.pdf"` and RFC 5987 `filename*=UTF-8''a%20b.pdf`.
     */
    fun parseContentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null

        Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)
            ?.let { m ->
                val charset = m.groupValues[1].ifBlank { "UTF-8" }
                val value = m.groupValues[2].trim().trim('"')
                return runCatching { URLDecoder.decode(value.replace("+", "%2B"), charset) }.getOrNull()
            }

        return Regex("filename\\s*=\\s*(\"([^\"]*)\"|[^;]+)", RegexOption.IGNORE_CASE)
            .find(header)
            ?.let { m -> m.groupValues[2].ifEmpty { m.groupValues[1] }.trim().trim('"') }
            ?.takeIf { it.isNotBlank() }
    }

    fun computeSha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun sanitizeFileName(raw: String): String {
        var name = raw
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { !it.isISOControl() }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("..", "_")
            .trim()
            .trimStart('.')
            .trim()

        if (name.isBlank()) name = "aura_download_${System.currentTimeMillis()}.bin"

        if (name.length > MAX_FILE_NAME_LENGTH) {
            val ext = name.substringAfterLast('.', "").take(10)
            val stem = name.substringBeforeLast('.', name)
            name = if (ext.isNotEmpty() && ext != name) {
                stem.take(MAX_FILE_NAME_LENGTH - ext.length - 1) + "." + ext
            } else {
                name.take(MAX_FILE_NAME_LENGTH)
            }
        }
        return name
    }

    private fun guessMimeType(extension: String): String = when (extension) {
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus" -> "audio/*"
        "mp4", "mkv", "webm", "mov" -> "video/*"
        "jpg", "jpeg", "png", "gif", "webp", "avif" -> "image/*"
        "pdf" -> "application/pdf"
        "txt", "md", "csv" -> "text/plain"
        "zip", "xpi" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }

    companion object {
        private const val MAX_FILE_NAME_LENGTH = 120
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
