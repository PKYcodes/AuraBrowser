package com.aura.browser.feature.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aura.browser.core.designsystem.theme.AuraColors
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoSession

/** Short-lived on-screen indicator for brightness / volume / zoom gestures. */
data class GestureFeedback(val kind: GestureKind, val percent: Int, val stamp: Long = System.nanoTime())

/**
 * Shows [session] and, while [gesturesEnabled], supports pinch-zoom plus brightness (left swipe) and
 * volume (right swipe) gestures. A small HUD reports the new level.
 */
@Composable
fun GeckoSessionView(
    session: GeckoSession?,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }

    LaunchedEffect(feedback?.stamp) {
        if (feedback != null) {
            delay(900)
            feedback = null
        }
    }

    Box(modifier = modifier) {
        if (session != null && session.isOpen) {
            AndroidView(
                factory = { context ->
                    VideoGestureLayout(context).also { layout ->
                        layout.listener = VideoGestureLayout.Listener { kind, percent ->
                            feedback = GestureFeedback(kind, percent)
                        }
                    }
                },
                update = { layout ->
                    layout.gesturesEnabled = gesturesEnabled
                    layout.bindSession(session)
                },
                onRelease = { layout -> layout.release() },
                modifier = Modifier.matchParentSize(),
            )
        }

        feedback?.let { current ->
            val label = when (current.kind) {
                GestureKind.BRIGHTNESS -> "Brightness"
                GestureKind.VOLUME -> "Volume"
                GestureKind.ZOOM -> "Zoom"
            }
            val value = if (current.kind == GestureKind.ZOOM) "${current.percent}%" else "${current.percent}%"
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "$label  $value",
                    style = MaterialTheme.typography.titleMedium.copy(color = AuraColors.TextPrimary),
                )
            }
        }
    }
}
