package dev.jdtech.jellyfin.player.local.cast

import dev.jdtech.jellyfin.cast.dlna.DlnaDevice
import java.util.UUID

enum class CastStatus {
    IDLE,
    CONNECTING,
    PLAYING,
    PAUSED,
    ERROR,
}

data class CastState(
    val status: CastStatus = CastStatus.IDLE,
    val device: DlnaDevice? = null,
    val itemId: UUID? = null,
    val title: String = "",
    val positionMs: Long = 0,
    /** Absolute position of the current TV stream's zero point (used after a seek re-push). */
    val basePositionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isTranscoded: Boolean = false,
    val errorMessage: String? = null,
) {
    val isActive: Boolean
        get() =
            status == CastStatus.CONNECTING ||
                status == CastStatus.PLAYING ||
                status == CastStatus.PAUSED
}

/**
 * Everything needed to (re)build a cast stream. The request is kept in memory so changing the audio
 * track, subtitle or quality can re-push the current position without asking the player screen for
 * its state again.
 */
data class CastRequest(
    val itemId: UUID,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val maxBitrate: Int?,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val container: String,
    val maxHeight: Int? = null,
)
