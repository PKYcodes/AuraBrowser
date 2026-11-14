package com.aura.browser.feature.browser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import kotlin.math.abs
import kotlin.math.roundToInt

enum class GestureKind { BRIGHTNESS, VOLUME, ZOOM }

/**
 * Hosts a [GeckoView] and, while [gesturesEnabled] (fullscreen video), adds the gestures a native
 * video app has:
 * - vertical swipe on the left half: brightness
 * - vertical swipe on the right half: volume
 * - pinch: zoom the picture in/out (1x to 3x)
 *
 * Gestures are only taken over once they clearly start (slop passed / second finger down), so taps on the
 * page's own player controls still reach the web content.
 */
class VideoGestureLayout(context: Context) : FrameLayout(context) {

    fun interface Listener {
        fun onFeedback(kind: GestureKind, percent: Int)
    }

    private enum class Mode { NONE, BRIGHTNESS, VOLUME, ZOOM }

    val geckoView = GeckoView(context)
    var listener: Listener? = null

    var gesturesEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) resetEffects()
        }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var level = 0f
    private var zoom = 1f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                geckoView.pivotX = detector.focusX
                geckoView.pivotY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, MAX_ZOOM)
                geckoView.scaleX = zoom
                geckoView.scaleY = zoom
                listener?.onFeedback(GestureKind.ZOOM, (zoom * 100).roundToInt())
                return true
            }
        },
    )

    init {
        addView(geckoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bindSession(session: GeckoSession) {
        if (geckoView.session !== session) {
            geckoView.releaseSession()
            geckoView.setSession(session)
        }
    }

    fun release() {
        resetEffects()
        geckoView.releaseSession()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!gesturesEnabled) return false
        if (mode == Mode.NONE) scaleDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastY = ev.y
                mode = Mode.NONE
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount == 2) {
                    mode = Mode.ZOOM
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.NONE && ev.pointerCount == 1) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    val inSafeBand = downY > height * 0.12f && downY < height * 0.80f
                    if (inSafeBand && abs(dy) > touchSlop * 2 && abs(dy) > abs(dx) * 1.5f) {
                        beginAdjust(left = downX < width / 2f)
                        lastY = ev.y
                        return true
                    }
                }
            }
        }
        return mode != Mode.NONE
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!gesturesEnabled || mode == Mode.NONE) return super.onTouchEvent(ev)

        when (mode) {
            Mode.ZOOM -> scaleDetector.onTouchEvent(ev)
            Mode.BRIGHTNESS, Mode.VOLUME -> if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
                val delta = (lastY - ev.y) / height * SWIPE_GAIN
                lastY = ev.y
                level = (level + delta).coerceIn(0f, 1f)
                if (mode == Mode.BRIGHTNESS) applyBrightness(level) else applyVolume(level)
            }
            Mode.NONE -> Unit
        }

        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            mode = Mode.NONE
        }
        return true
    }

    override fun onDetachedFromWindow() {
        resetEffects()
        super.onDetachedFromWindow()
    }

    private fun beginAdjust(left: Boolean) {
        if (left) {
            mode = Mode.BRIGHTNESS
            level = currentBrightness()
        } else {
            mode = Mode.VOLUME
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        }
    }

    private fun currentBrightness(): Float {
        val window = hostActivity()?.window ?: return 0.5f
        val explicit = window.attributes.screenBrightness
        if (explicit >= 0f) return explicit
        val system = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        return (system / 255f).coerceIn(0f, 1f)
    }

    private fun applyBrightness(value: Float) {
        val window = hostActivity()?.window ?: return
        val attributes = window.attributes
        attributes.screenBrightness = value.coerceAtLeast(MIN_BRIGHTNESS)
        window.attributes = attributes
        listener?.onFeedback(GestureKind.BRIGHTNESS, (value * 100).roundToInt())
    }

    private fun applyVolume(value: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt().coerceIn(0, max), 0)
        listener?.onFeedback(GestureKind.VOLUME, (value * 100).roundToInt())
    }

    private fun resetEffects() {
        mode = Mode.NONE
        zoom = 1f
        geckoView.scaleX = 1f
        geckoView.scaleY = 1f
        hostActivity()?.window?.let { window ->
            val attributes = window.attributes
            if (attributes.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = attributes
            }
        }
    }

    private fun hostActivity(): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private companion object {
        const val MAX_ZOOM = 3f
        const val MIN_BRIGHTNESS = 0.02f
        const val SWIPE_GAIN = 1.4f
    }
}
