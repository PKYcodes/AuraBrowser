package com.aura.browser.core.network.download

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ValidatedDownloadRequest(
    val sanitizedFileName: String,
    val mimeType: String,
    val requiresUserWarning: Boolean,
    val warningReason: String? = null,
)

@Singleton
class AuraDownloadValidator @Inject constructor() {

    private val highRiskExtensions = setOf("apk", "xapk", "dex", "so", "sh", "bat", "exe", "jar")

    fun validateDownload(
        rawFileName: String?,
        sourceUrl: String,
        mimeType: String?,
    ): ValidatedDownloadRequest {
        val fallbackName = sourceUrl
            .substringAfterLast('/')
            .substringBefore('?')
            .ifBlank { "aura_download_${System.currentTimeMillis()}.bin" }

        val candidate = (rawFileName ?: fallbackName)
            .replace(Regex("[\\\\/:*?"<>|]"), "_")
            .replace("..", "_")
            .trim()
            .ifBlank { "aura_download.bin" }

        val extension = candidate.substringAfterLast('.', "").lowercase()
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: guessMimeType(extension)
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

    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun guessMimeType(extension: String): String = when (extension) {
        "mp3", "m4a", "aac", "flac", "wav", "ogg" -> "audio/*"
        "mp4", "mkv", "webm", "mov" -> "video/*"
        "pdf" -> "application/pdf"
        "zip", "xpi" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }
}
