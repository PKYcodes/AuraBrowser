package com.aura.browser.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aura.browser.core.database.dao.BookmarkDao
import com.aura.browser.core.database.dao.DownloadDao
import com.aura.browser.core.database.dao.HistoryDao
import com.aura.browser.core.database.dao.PlaylistDao
import com.aura.browser.core.database.dao.SitePermissionDao
import com.aura.browser.core.database.dao.TabDao
import com.aura.browser.core.database.dao.VideoPlaybackPositionDao
import com.aura.browser.core.database.entity.BookmarkEntity
import com.aura.browser.core.database.entity.DownloadConverters
import com.aura.browser.core.database.entity.DownloadEntity
import com.aura.browser.core.database.entity.HistoryEntity
import com.aura.browser.core.database.entity.PlaylistEntity
import com.aura.browser.core.database.entity.SitePermissionEntity
import com.aura.browser.core.database.entity.TabEntity
import com.aura.browser.core.database.entity.VideoPlaybackPositionEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        TabEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        PlaylistEntity::class,
        VideoPlaybackPositionEntity::class,
        SitePermissionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(DownloadConverters::class)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun videoPlaybackPositionDao(): VideoPlaybackPositionDao
    abstract fun sitePermissionDao(): SitePermissionDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAuraDatabase(@ApplicationContext context: Context): AuraDatabase =
        Room.databaseBuilder(
            context,
            AuraDatabase::class.java,
            "aura_browser.db",
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideTabDao(db: AuraDatabase): TabDao = db.tabDao()

    @Provides
    fun provideBookmarkDao(db: AuraDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideHistoryDao(db: AuraDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideDownloadDao(db: AuraDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun providePlaylistDao(db: AuraDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideVideoPlaybackPositionDao(db: AuraDatabase): VideoPlaybackPositionDao =
        db.videoPlaybackPositionDao()

    @Provides
    fun provideSitePermissionDao(db: AuraDatabase): SitePermissionDao =
        db.sitePermissionDao()
}
