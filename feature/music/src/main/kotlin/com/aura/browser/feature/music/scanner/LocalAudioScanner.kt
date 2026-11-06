package com.aura.browser.feature.music.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.aura.browser.core.mediacommon.model.LocalAudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans device local audio files via both Android MediaStore.Audio.Media and
 * user-selected Storage Access Framework (SAF) directory trees.
 */
@Singleton
class LocalAudioScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val supportedAudioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")

    suspend fun scanDeviceAudioLibrary(): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<LocalAudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    tracks.add(
                        LocalAudioTrack(
                            id = id.toString(),
                            uri = contentUri.toString(),
                            title = cursor.getString(titleCol) ?: "Unknown Track",
                            artist = cursor.getString(artistCol) ?: "Unknown Artist",
                            album = cursor.getString(albumCol) ?: "Local Album",
                            durationMs = cursor.getLong(durationCol).coerceAtLeast(0L),
                            mimeType = cursor.getString(mimeCol) ?: "audio/mpeg",
                            sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L),
                        )
                    )
                }
            }
        }
        tracks
    }

    suspend fun scanSafDirectoryTree(treeUri: Uri): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LocalAudioTrack>()
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        collectAudioFilesRecursive(rootDoc, results)
        results
    }

    private fun collectAudioFilesRecursive(
        dir: DocumentFile,
        out: MutableList<LocalAudioTrack>,
    ) {
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                collectAudioFilesRecursive(file, out)
            } else {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val mime = file.type ?: ""
                if (ext in supportedAudioExtensions || mime.startsWith("audio/")) {
                    out.add(
                        LocalAudioTrack(
                            id = file.uri.toString(),
                            uri = file.uri.toString(),
                            title = name.substringBeforeLast('.'),
                            artist = "SAF Folder Library",
                            album = dir.name ?: "Local Folder",
                            durationMs = 0L,
                            mimeType = mime.ifBlank { "audio/*" },
                            sizeBytes = file.length(),
                        )
                    )
                }
            }
        }
    }
}
