package dev.jdtech.jellyfin.cast.dlna

import java.time.Duration

/** State reported by `AVTransport.GetTransportInfo`. */
enum class TransportState {
    PLAYING,
    PAUSED_PLAYBACK,
    PAUSED_RECORDING,
    RECORDING,
    STOPPED,
    NO_MEDIA_PRESENT,
    TRANSITIONING,
    UNKNOWN;

    val isPlaying: Boolean
        get() = this == PLAYING || this == RECORDING

    val isPaused: Boolean
        get() = this == PAUSED_PLAYBACK || this == PAUSED_RECORDING

    companion object {
        fun fromUpnp(value: String?): TransportState =
            when (value?.trim()?.uppercase()) {
                "PLAYING" -> PLAYING
                "PAUSED_PLAYBACK" -> PAUSED_PLAYBACK
                "PAUSED_RECORDING" -> PAUSED_RECORDING
                "RECORDING" -> RECORDING
                "STOPPED" -> STOPPED
                "NO_MEDIA_PRESENT" -> NO_MEDIA_PRESENT
                "TRANSITIONING" -> TRANSITIONING
                else -> UNKNOWN
            }
    }
}

data class TransportInfo(
    val state: TransportState,
    val status: String? = null,
    val speed: String? = null,
)

data class PositionInfo(
    val track: Int? = null,
    val trackDuration: Duration? = null,
    val trackUri: String? = null,
    val relativeTime: Duration? = null,
    val absoluteTime: Duration? = null,
)

data class MediaInfo(
    val duration: Duration? = null,
    val currentUri: String? = null,
    val numberOfTracks: Int? = null,
)

data class TransportActions(
    val play: Boolean = true,
    val pause: Boolean = true,
    val stop: Boolean = true,
    val seek: Boolean = true,
    val next: Boolean = false,
)
