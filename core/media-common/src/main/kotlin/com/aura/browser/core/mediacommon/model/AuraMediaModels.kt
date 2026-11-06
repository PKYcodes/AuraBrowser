package com.aura.browser.core.mediacommon.model

data class LocalAudioTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String = "audio/mpeg",
    val sizeBytes: Long = 0L,
)

data class LocalVideoItem(
    val id: String,
    val uri: String,
    val title: String,
    val durationMs: Long,
    val width: Int = 1920,
    val height: Int = 1080,
    val mimeType: String = "video/mp4",
    val sizeBytes: Long = 0L,
    val lastPositionMs: Long = 0L,
    val subtitleUri: String? = null,
)

enum class RepeatModeOption(val label: String) {
    OFF("Repeat Off"),
    ONE("Repeat Track"),
    ALL("Repeat All"),
}

data class EqualizerState(
    val isSupported: Boolean = true,
    val enabled: Boolean = true,
    val presets: List<String> = listOf("Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock"),
    val selectedPresetIndex: Int = 0,
    val bassBoostStrength: Int = 250,
    val audioSessionId: Int = 0,
)
