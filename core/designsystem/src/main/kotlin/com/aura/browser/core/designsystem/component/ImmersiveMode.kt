package com.aura.browser.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared flag that lets any screen request an immersive (system bars + app chrome hidden) layout,
 * e.g. fullscreen web video or the fullscreen local video player. The host activity reads [enabled].
 */
@Stable
class ImmersiveModeState {
    var enabled: Boolean by mutableStateOf(false)

    /** True while the activity is shown in a Picture-in-Picture window; screens hide their chrome. */
    var inPip: Boolean by mutableStateOf(false)
}

val LocalImmersiveMode = compositionLocalOf { ImmersiveModeState() }

/** Requests immersive mode while [active] is true and the caller stays in composition. */
@Composable
fun ImmersiveModeEffect(state: ImmersiveModeState = LocalImmersiveMode.current, active: Boolean) {
    DisposableEffect(state, active) {
        state.enabled = active
        onDispose { state.enabled = false }
    }
}
