package com.aura.browser.core.network.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.aura.browser.core.common.dispatchers.ApplicationScope
import com.aura.browser.core.common.dispatchers.AuraDispatcher
import com.aura.browser.core.common.dispatchers.Dispatcher
import com.aura.browser.core.database.dao.DownloadDao
import com.aura.browser.core.database.entity.DownloadEntity
import com.aura.browser.core.database.entity.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadRequest(
    val sourceUrl: String,
    val fileNameHint: String? = null,
    val contentDisposition: String? = null,
    val mimeType: String? = null,
    val contentLength: Long = -1L,
)

sealed interface DownloadEvent {
    data class Started(val fileName: String) : DownloadEvent
    data class Completed(val downloadId: Long, val fileName: String) : DownloadEvent
    data class Failed(val fileName: String, val reason: String) : DownloadEvent
}

/**
 * Streams downloads to public storage (MediaStore.Downloads on Android 10+, app-scoped storage
 * before that), tracks progress in Room and computes a SHA-256 checksum while writing.
 *
 * Pause/resume is intentionally not offered: a response body stream coming from the web engine
 * cannot be resumed with a range request.
 */
@Singleton
class AuraDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val validator: AuraDownloadValidator,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(AuraDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val jobs = ConcurrentHashMap<Long, Job>()
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    /** Call once at process start: rows left in a transient state by a process death can never finish. */
    fun recoverInterruptedDownloads() {
        scope.launch(ioDispatcher) { downloadDao.markInterruptedDownloadsFailed() }
    }

    /**
     * Persists [stream] as a download. The stream is consumed and closed on a background dispatcher,
     * so this can be called straight from an engine callback.
     */
    fun enqueue(request: DownloadRequest, stream: InputStream) {
        scope.launch(ioDispatcher) {
            runDownload(request, stream)
        }
    }

    /** Fetches a public HTTPS resource (e.g. a context-menu image) and saves it as a download. */
    fun enqueueFromUrl(url: String) {
        scope.launch(ioDispatcher) {
            if (!url.startsWith("https://", ignoreCase = true)) {
                _events.tryEmit(DownloadEvent.Failed(url.substringAfterLast('/'), "Only HTTPS downloads are supported here."))
                return@launch
            }
            val connection = runCatching {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AuraBrowser")
                }
            }.getOrNull()
            if (connection == null) {
                _events.tryEmit(DownloadEvent.Failed(url.substringAfterLast('/'), "Invalid URL."))
                return@launch
            }
            try {
                if (connection.responseCode !in 200..299) {
                    _events.tryEmit(DownloadEvent.Failed(url.substringAfterLast('/'), "Server returned ${connection.responseCode}."))
                    return@launch
                }
                runDownload(
                    DownloadRequest(
                        sourceUrl = url,
                        contentDisposition = connection.getHeaderField("Content-Disposition"),
                        mimeType = connection.contentType,
                        contentLength = connection.contentLengthLong,
                    ),
                    connection.inputStream,
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _events.tryEmit(DownloadEvent.Failed(url.substringAfterLast('/'), t.message ?: "Network error."))
            } finally {
                connection.disconnect()
            }
        }
    }

    fun cancel(downloadId: Long) {
        jobs[downloadId]?.cancel()
    }

    /** Removes the record and, when present, the downloaded file. */
    suspend fun delete(download: DownloadEntity) = withContext(ioDispatcher) {
        cancel(download.id)
        deleteBackingFile(download.destinationUri)
        downloadDao.deleteDownload(download.id)
    }

    fun buildViewIntent(download: DownloadEntity): Intent? {
        if (download.status != DownloadStatus.COMPLETED || download.destinationUri.isBlank()) return null
        val stored = Uri.parse(download.destinationUri)
        val shareable = if (stored.scheme == "file") {
            val path = stored.path ?: return null
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            }.getOrNull() ?: return null
        } else {
            stored
        }
        val type = context.contentResolver.getType(shareable)
            ?.takeIf { it.isNotBlank() }
            ?: download.mimeType.takeUnless { it.endsWith("/*") }
            ?: "*/*"
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(shareable, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ---------------------------------------------------------------------------------------------

    private suspend fun runDownload(request: DownloadRequest, stream: InputStream) {
        val fileNameHint = request.fileNameHint
            ?: validator.parseContentDispositionFileName(request.contentDisposition)
        val validated = validator.validateDownload(fileNameHint, request.sourceUrl, request.mimeType)

        val sink = runCatching { openSink(validated.sanitizedFileName, validated.mimeType) }.getOrNull()
        if (sink == null) {
            runCatching { stream.close() }
            _events.tryEmit(DownloadEvent.Failed(validated.sanitizedFileName, "Could not create the destination file."))
            return
        }

        val id = downloadDao.insertDownload(
            DownloadEntity(
                fileName = sink.displayName,
                sourceUrl = request.sourceUrl,
                mimeType = validated.mimeType,
                destinationUri = sink.destinationUri,
                totalBytes = request.contentLength,
                status = DownloadStatus.DOWNLOADING,
            )
        )
        currentCoroutineContext()[Job]?.let { jobs[id] = it }
        _events.tryEmit(DownloadEvent.Started(sink.displayName))

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var downloaded = 0L
            var lastReport = 0L

            stream.use { input ->
                sink.output.use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            downloadDao.updateDownloadProgress(id, DownloadStatus.DOWNLOADING, downloaded, request.contentLength)
                        }
                    }
                    output.flush()
                }
            }

            sink.finish()
            downloadDao.getDownload(id)?.let { current ->
                downloadDao.updateDownload(
                    current.copy(
                        status = DownloadStatus.COMPLETED,
                        downloadedBytes = downloaded,
                        totalBytes = downloaded,
                        sha256Checksum = digest.digest().toHex(),
                    )
                )
            }
            _events.tryEmit(DownloadEvent.Completed(id, sink.displayName))
        } catch (t: Throwable) {
            withContext(NonCancellable) {
                sink.abort()
                val cancelled = t is CancellationException
                downloadDao.getDownload(id)?.let { current ->
                    downloadDao.updateDownload(
                        current.copy(status = if (cancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED)
                    )
                }
                if (!cancelled) {
                    _events.tryEmit(DownloadEvent.Failed(sink.displayName, t.message ?: "Download failed."))
                }
            }
            if (t is CancellationException) throw t
        } finally {
            jobs.remove(id)
        }
    }

    private class Sink(
        val displayName: String,
        val destinationUri: String,
        val output: OutputStream,
        val finish: () -> Unit,
        val abort: () -> Unit,
    )

    private fun openSink(fileName: String, mimeType: String): Sink {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType.takeUnless { it.endsWith("/*") } ?: "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Aura")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            val finalName = runCatching {
                resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: fileName
            val output = resolver.openOutputStream(uri) ?: error("Cannot open output stream")
            Sink(
                displayName = finalName,
                destinationUri = uri.toString(),
                output = output,
                finish = {
                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                },
                abort = { runCatching { resolver.delete(uri, null, null) } },
            )
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            dir.mkdirs()
            val target = uniqueFile(dir, fileName)
            Sink(
                displayName = target.name,
                destinationUri = Uri.fromFile(target).toString(),
                output = FileOutputStream(target),
                finish = {},
                abort = { runCatching { target.delete() } },
            )
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($index)$ext")
            index++
        }
        return candidate
    }

    private fun deleteBackingFile(destinationUri: String) {
        if (destinationUri.isBlank()) return
        val uri = Uri.parse(destinationUri)
        runCatching {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 350L
    }
}
