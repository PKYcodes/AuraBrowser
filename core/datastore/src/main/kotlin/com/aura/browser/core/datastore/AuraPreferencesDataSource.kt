package com.aura.browser.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aura.browser.core.common.model.CookiePolicyMode
import com.aura.browser.core.common.model.SearchEngine
import com.aura.browser.core.common.model.TrackingProtectionPreset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.auraDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aura_browser_preferences"
)

data class AuraUserSettings(
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val homePageUrl: String = "https://duckduckgo.com",
    val httpsOnlyMode: Boolean = true,
    val trackingProtectionPreset: TrackingProtectionPreset = TrackingProtectionPreset.STRICT,
    val cookiePolicyMode: CookiePolicyMode = CookiePolicyMode.BLOCK_CROSS_SITE,
    val blockTrackersCount: Int = 0,
    val httpsUpgradesCount: Int = 0,
    val desktopSiteDefault: Boolean = false,
    val clearSessionOnExit: Boolean = false,
    val autoEnterPipOnBackground: Boolean = true,
    val youtubeBackgroundPlayEnabled: Boolean = true,
    val youtubeHideShortsShelf: Boolean = false,
    val youtubeTheaterModeDefault: Boolean = true,
    val equalizerPresetIndex: Int = 0,
    val bassBoostStrength: Int = 250,
    val safMusicTreeUri: String? = null,
    val safDownloadsTreeUri: String? = null,
)

@Singleton
class AuraPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val HOME_PAGE_URL = stringPreferencesKey("home_page_url")
        val HTTPS_ONLY_MODE = booleanPreferencesKey("https_only_mode")
        val TRACKING_PROTECTION = stringPreferencesKey("tracking_protection_preset")
        val COOKIE_POLICY = stringPreferencesKey("cookie_policy_mode")
        val BLOCKED_TRACKERS_COUNT = intPreferencesKey("blocked_trackers_count")
        val HTTPS_UPGRADES_COUNT = intPreferencesKey("https_upgrades_count")
        val DESKTOP_SITE_DEFAULT = booleanPreferencesKey("desktop_site_default")
        val CLEAR_SESSION_ON_EXIT = booleanPreferencesKey("clear_session_on_exit")
        val AUTO_ENTER_PIP = booleanPreferencesKey("auto_enter_pip")
        val YT_BACKGROUND_PLAY = booleanPreferencesKey("yt_background_play")
        val YT_HIDE_SHORTS = booleanPreferencesKey("yt_hide_shorts")
        val YT_THEATER_DEFAULT = booleanPreferencesKey("yt_theater_default")
        val EQ_PRESET_INDEX = intPreferencesKey("eq_preset_index")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val SAF_MUSIC_TREE_URI = stringPreferencesKey("saf_music_tree_uri")
        val SAF_DOWNLOADS_TREE_URI = stringPreferencesKey("saf_downloads_tree_uri")
    }

    val userSettings: Flow<AuraUserSettings> = dataStore.data.map { prefs ->
        val engine = prefs[Keys.SEARCH_ENGINE]
            ?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() }
            ?: SearchEngine.DUCKDUCKGO
        val tracking = prefs[Keys.TRACKING_PROTECTION]
            ?.let { runCatching { TrackingProtectionPreset.valueOf(it) }.getOrNull() }
            ?: TrackingProtectionPreset.STRICT
        val cookies = prefs[Keys.COOKIE_POLICY]
            ?.let { runCatching { CookiePolicyMode.valueOf(it) }.getOrNull() }
            ?: CookiePolicyMode.BLOCK_CROSS_SITE

        AuraUserSettings(
            searchEngine = engine,
            homePageUrl = prefs[Keys.HOME_PAGE_URL] ?: engine.homeUrl,
            httpsOnlyMode = prefs[Keys.HTTPS_ONLY_MODE] ?: true,
            trackingProtectionPreset = tracking,
            cookiePolicyMode = cookies,
            blockTrackersCount = prefs[Keys.BLOCKED_TRACKERS_COUNT] ?: 0,
            httpsUpgradesCount = prefs[Keys.HTTPS_UPGRADES_COUNT] ?: 0,
            desktopSiteDefault = prefs[Keys.DESKTOP_SITE_DEFAULT] ?: false,
            clearSessionOnExit = prefs[Keys.CLEAR_SESSION_ON_EXIT] ?: false,
            autoEnterPipOnBackground = prefs[Keys.AUTO_ENTER_PIP] ?: true,
            youtubeBackgroundPlayEnabled = prefs[Keys.YT_BACKGROUND_PLAY] ?: true,
            youtubeHideShortsShelf = prefs[Keys.YT_HIDE_SHORTS] ?: false,
            youtubeTheaterModeDefault = prefs[Keys.YT_THEATER_DEFAULT] ?: true,
            equalizerPresetIndex = prefs[Keys.EQ_PRESET_INDEX] ?: 0,
            bassBoostStrength = prefs[Keys.BASS_BOOST_STRENGTH] ?: 250,
            safMusicTreeUri = prefs[Keys.SAF_MUSIC_TREE_URI],
            safDownloadsTreeUri = prefs[Keys.SAF_DOWNLOADS_TREE_URI],
        )
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        dataStore.edit {
            it[Keys.SEARCH_ENGINE] = engine.name
            it[Keys.HOME_PAGE_URL] = engine.homeUrl
        }
    }

    suspend fun setHomePageUrl(url: String) {
        dataStore.edit { it[Keys.HOME_PAGE_URL] = url }
    }

    suspend fun setHttpsOnlyMode(enabled: Boolean) {
        dataStore.edit { it[Keys.HTTPS_ONLY_MODE] = enabled }
    }

    suspend fun setTrackingProtectionPreset(preset: TrackingProtectionPreset) {
        dataStore.edit { it[Keys.TRACKING_PROTECTION] = preset.name }
    }

    suspend fun setCookiePolicyMode(mode: CookiePolicyMode) {
        dataStore.edit { it[Keys.COOKIE_POLICY] = mode.name }
    }

    suspend fun incrementBlockedTrackers(delta: Int = 1) {
        dataStore.edit {
            val current = it[Keys.BLOCKED_TRACKERS_COUNT] ?: 0
            it[Keys.BLOCKED_TRACKERS_COUNT] = current + delta
        }
    }

    suspend fun incrementHttpsUpgrades() {
        dataStore.edit {
            val current = it[Keys.HTTPS_UPGRADES_COUNT] ?: 0
            it[Keys.HTTPS_UPGRADES_COUNT] = current + 1
        }
    }

    suspend fun setDesktopSiteDefault(enabled: Boolean) {
        dataStore.edit { it[Keys.DESKTOP_SITE_DEFAULT] = enabled }
    }

    suspend fun setClearSessionOnExit(enabled: Boolean) {
        dataStore.edit { it[Keys.CLEAR_SESSION_ON_EXIT] = enabled }
    }

    suspend fun setAutoEnterPip(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_ENTER_PIP] = enabled }
    }

    suspend fun setYoutubeBackgroundPlay(enabled: Boolean) {
        dataStore.edit { it[Keys.YT_BACKGROUND_PLAY] = enabled }
    }

    suspend fun setYoutubeHideShorts(enabled: Boolean) {
        dataStore.edit { it[Keys.YT_HIDE_SHORTS] = enabled }
    }

    suspend fun setYoutubeTheaterDefault(enabled: Boolean) {
        dataStore.edit { it[Keys.YT_THEATER_DEFAULT] = enabled }
    }

    suspend fun setEqualizerConfig(presetIndex: Int, bassStrength: Int) {
        dataStore.edit {
            it[Keys.EQ_PRESET_INDEX] = presetIndex
            it[Keys.BASS_BOOST_STRENGTH] = bassStrength.coerceIn(0, 1000)
        }
    }

    suspend fun setSafMusicTreeUri(uriString: String?) {
        dataStore.edit {
            if (uriString == null) it.remove(Keys.SAF_MUSIC_TREE_URI)
            else it[Keys.SAF_MUSIC_TREE_URI] = uriString
        }
    }

    suspend fun setSafDownloadsTreeUri(uriString: String?) {
        dataStore.edit {
            if (uriString == null) it.remove(Keys.SAF_DOWNLOADS_TREE_URI)
            else it[Keys.SAF_DOWNLOADS_TREE_URI] = uriString
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.auraDataStore
}
