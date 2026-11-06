package com.aura.browser.core.mediacommon.equalizer

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.aura.browser.core.mediacommon.model.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware AudioFX Equalizer & BassBoost controller bound to ExoPlayer's audioSessionId.
 * Gracefully degrades when device OEM firmware omits hardware equalizer effects.
 */
@Singleton
class AuraAudioEffectsController @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        release()
        runCatching {
            val eq = Equalizer(0, audioSessionId).apply { enabled = true }
            val presetCount = eq.numberOfPresets.toInt()
            val presetNames = (0 until presetCount).map { idx ->
                eq.getPresetName(idx.toShort()).toString()
            }.ifEmpty { _state.value.presets }

            val bass = BassBoost(0, audioSessionId).apply {
                enabled = true
                if (strengthSupported) {
                    setStrength(_state.value.bassBoostStrength.toShort())
                }
            }

            equalizer = eq
            bassBoost = bass
            _state.value = _state.value.copy(
                isSupported = true,
                enabled = true,
                presets = presetNames,
                audioSessionId = audioSessionId,
            )
        }.onFailure {
            _state.value = _state.value.copy(
                isSupported = false,
                enabled = false,
                audioSessionId = audioSessionId,
            )
        }
    }

    fun selectPreset(presetIndex: Int) {
        val current = _state.value
        val coerced = presetIndex.coerceIn(0, (current.presets.size - 1).coerceAtLeast(0))
        runCatching {
            equalizer?.usePreset(coerced.toShort())
        }
        _state.value = current.copy(selectedPresetIndex = coerced)
    }

    fun setBassBoostStrength(strength: Int) {
        val coerced = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.takeIf { it.strengthSupported }?.setStrength(coerced.toShort())
        }
        _state.value = _state.value.copy(bassBoostStrength = coerced)
    }

    fun setEnabled(enabled: Boolean) {
        runCatching {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        }
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }
}
