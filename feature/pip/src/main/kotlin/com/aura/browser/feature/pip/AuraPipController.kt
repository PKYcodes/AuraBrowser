package com.aura.browser.feature.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.core.content.ContextCompat
import com.aura.browser.core.mediacommon.controller.AuraMediaControllerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Android Picture-in-Picture (PiP) controller supporting API 26+ through API 35.
 * Implements:
 * - Aspect ratio clamping within Android's valid [0.418410, 2.390000] bounds
 * - Custom RemoteAction controls (Previous, Play/Pause, Next)
 * - Smooth transition sourceRectHint and Android 12+ (API 31) setAutoEnterEnabled
 * - BroadcastReceiver for PiP window media action intents
 */
@Singleton
class AuraPipController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mediaControllerManager: AuraMediaControllerManager,
) {
    companion object {
        const val ACTION_PIP_MEDIA_CONTROL = "com.aura.browser.action.PIP_MEDIA_CONTROL"
        const val EXTRA_CONTROL_TYPE = "extra_control_type"
        const val CONTROL_PREVIOUS = 1
        const val CONTROL_PLAY_PAUSE = 2
        const val CONTROL_NEXT = 3
    }

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private var sourceViewBounds: Rect? = null
    private var receiverRegistered = false

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PIP_MEDIA_CONTROL) return
            when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                CONTROL_PREVIOUS -> mediaControllerManager.skipPrevious()
                CONTROL_PLAY_PAUSE -> mediaControllerManager.togglePlayPause()
                CONTROL_NEXT -> mediaControllerManager.skipNext()
            }
        }
    }

    fun isPipSupported(activity: Activity): Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun registerActionReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_PIP_MEDIA_CONTROL)
        ContextCompat.registerReceiver(
            appContext,
            pipActionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    fun unregisterActionReceiver() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(pipActionReceiver) }
        receiverRegistered = false
    }

    fun updateVideoViewBounds(bounds: Rect) {
        sourceViewBounds = bounds
    }

    /**
     * Clamps aspect ratio numerator/denominator to Android's required range [100:239, 239:100].
     */
    fun clampAspectRatio(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 16 to 9
        val ratio = width.toDouble() / height.toDouble()
        return when {
            ratio < 0.42 -> 100 to 239
            ratio > 2.38 -> 239 to 100
            else -> width to height
        }
    }

    fun buildPipParams(
        width: Int = 16,
        height: Int = 9,
        isPlaying: Boolean = true,
        autoEnterEnabled: Boolean = true,
    ): PictureInPictureParams {
        val (clampedW, clampedH) = clampAspectRatio(width, height)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(clampedW, clampedH))
            .setActions(buildRemoteActions(isPlaying))

        sourceViewBounds?.let { builder.setSourceRectHint(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled && isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    fun enterPipNow(
        activity: Activity,
        width: Int = 16,
        height: Int = 9,
        isPlaying: Boolean = true,
    ): Boolean {
        if (!isPipSupported(activity)) return false
        registerActionReceiver()
        val params = buildPipParams(width = width, height = height, isPlaying = isPlaying)
        return runCatching {
            activity.enterPictureInPictureMode(params)
        }.getOrDefault(false)
    }

    fun onPictureInPictureModeChanged(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    private fun buildRemoteActions(isPlaying: Boolean): List<RemoteAction> {
        val prevAction = createRemoteAction(
            requestCode = CONTROL_PREVIOUS,
            controlType = CONTROL_PREVIOUS,
            iconRes = android.R.drawable.ic_media_previous,
            title = "Previous",
        )
        val playPauseAction = createRemoteAction(
            requestCode = CONTROL_PLAY_PAUSE,
            controlType = CONTROL_PLAY_PAUSE,
            iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            title = if (isPlaying) "Pause" else "Play",
        )
        val nextAction = createRemoteAction(
            requestCode = CONTROL_NEXT,
            controlType = CONTROL_NEXT,
            iconRes = android.R.drawable.ic_media_next,
            title = "Next",
        )
        return listOf(prevAction, playPauseAction, nextAction)
    }

    private fun createRemoteAction(
        requestCode: Int,
        controlType: Int,
        iconRes: Int,
        title: String,
    ): RemoteAction {
        val intent = Intent(ACTION_PIP_MEDIA_CONTROL).apply {
            setPackage(appContext.packageName)
            putExtra(EXTRA_CONTROL_TYPE, controlType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(appContext, iconRes)
        return RemoteAction(icon, title, title, pendingIntent)
    }
}
