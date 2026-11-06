package com.aura.browser.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "browser_tabs",
    indices = [Index(value = ["isPrivate"]), Index(value = ["lastAccessedAt"])],
)
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val isPrivate: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isActive: Boolean = false,
    val sessionStateJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true), Index(value = ["folder"])],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folder: String = "Bookmarks",
    val faviconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "browsing_history",
    indices = [Index(value = ["url"]), Index(value = ["visitedAt"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitCount: Int = 1,
    val visitedAt: Long = System.currentTimeMillis(),
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@Entity(
    tableName = "downloads",
    indices = [Index(value = ["status"]), Index(value = ["createdAt"])],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemDownloadId: Long? = null,
    val fileName: String,
    val sourceUrl: String,
    val mimeType: String,
    val destinationUri: String,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val sha256Checksum: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "music_playlists",
    indices = [Index(value = ["name"], unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val trackUrisCsv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "video_playback_positions",
    indices = [Index(value = ["mediaUri"], unique = true)],
)
data class VideoPlaybackPositionEntity(
    @PrimaryKey val mediaUri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float = 1.0f,
    val subtitleUri: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "site_permissions",
    indices = [Index(value = ["origin"], unique = true)],
)
data class SitePermissionEntity(
    @PrimaryKey val origin: String,
    val cameraAllowed: Boolean = false,
    val microphoneAllowed: Boolean = false,
    val locationAllowed: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val autoplayAllowed: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)
