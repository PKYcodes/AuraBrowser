package com.aura.browser

import android.app.Application
import android.content.ComponentCallbacks2
import com.aura.browser.core.network.download.AuraDownloadManager
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AuraBrowserApplication : Application() {

    @Inject
    lateinit var downloadManager: AuraDownloadManager

    @Inject
    lateinit var engineManager: AuraGeckoEngineManager

    override fun onCreate() {
        super.onCreate()
        downloadManager.recoverInterruptedDownloads()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Free background tabs when the system is short on memory; they restore lazily when selected.
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            engineManager.releaseBackgroundSessions()
        }
    }
}
