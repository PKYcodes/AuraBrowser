package com.aura.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aura.browser.core.database.entity.BookmarkEntity
import com.aura.browser.core.database.entity.DownloadEntity
import com.aura.browser.core.database.entity.DownloadStatus
import com.aura.browser.core.database.entity.HistoryEntity
import com.aura.browser.core.database.entity.PlaylistEntity
import com.aura.browser.core.database.entity.SitePermissionEntity
import com.aura.browser.core.database.entity.TabEntity
import com.aura.browser.core.database.entity.VideoPlaybackPositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM browser_tabs ORDER BY lastAccessedAt DESC")
    fun observeAllTabs(): Flow<List<TabEntity>>

    @Query("SELECT * FROM browser_tabs WHERE isActive = 1 LIMIT 1")
    fun observeActiveTab(): Flow<TabEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTab(tab: TabEntity)

    @Query("UPDATE browser_tabs SET isActive = CASE WHEN id = :tabId THEN 1 ELSE 0 END, lastAccessedAt = CASE WHEN id = :tabId THEN :timestamp ELSE lastAccessedAt END")
    suspend fun setActiveTab(tabId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM browser_tabs WHERE id = :tabId")
    suspend fun deleteTabById(tabId: String)

    @Query("DELETE FROM browser_tabs WHERE isPrivate = 1")
    suspend fun deletePrivateTabs()

    @Query("DELETE FROM browser_tabs")
    suspend fun deleteAllTabs()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchBookmarks(query: String): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM browsing_history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int = 500): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM browsing_history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM browsing_history WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntity): Long

    @Update
    suspend fun updateHistory(entry: HistoryEntity)

    @Transaction
    suspend fun recordVisit(title: String, url: String) {
        val existing = findByUrl(url)
        if (existing != null) {
            updateHistory(
                existing.copy(
                    title = title.ifBlank { existing.title },
                    visitCount = existing.visitCount + 1,
                    visitedAt = System.currentTimeMillis(),
                )
            )
        } else {
            insertHistory(
                HistoryEntity(
                    title = title.ifBlank { url },
                    url = url,
                    visitCount = 1,
                    visitedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    @Query("DELETE FROM browsing_history WHERE id = :id")
    suspend fun deleteHistoryItem(id: Long)

    @Query("DELETE FROM browsing_history")
    suspend fun clearAllHistory()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateDownloadProgress(
        id: Long,
        status: DownloadStatus,
        downloadedBytes: Long,
        totalBytes: Long,
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED' OR status = 'CANCELLED' OR status = 'FAILED'")
    suspend fun clearFinishedDownloads()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM music_playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM music_playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)
}

@Dao
interface VideoPlaybackPositionDao {
    @Query("SELECT * FROM video_playback_positions ORDER BY updatedAt DESC")
    fun observeAllPositions(): Flow<List<VideoPlaybackPositionEntity>>

    @Query("SELECT * FROM video_playback_positions WHERE mediaUri = :mediaUri LIMIT 1")
    suspend fun getPosition(mediaUri: String): VideoPlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(entity: VideoPlaybackPositionEntity)
}

@Dao
interface SitePermissionDao {
    @Query("SELECT * FROM site_permissions ORDER BY origin ASC")
    fun observeSitePermissions(): Flow<List<SitePermissionEntity>>

    @Query("SELECT * FROM site_permissions WHERE origin = :origin LIMIT 1")
    suspend fun getPermissionForOrigin(origin: String): SitePermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSitePermission(entity: SitePermissionEntity)

    @Query("DELETE FROM site_permissions WHERE origin = :origin")
    suspend fun deleteSitePermission(origin: String)

    @Query("DELETE FROM site_permissions")
    suspend fun clearAllSitePermissions()
}
