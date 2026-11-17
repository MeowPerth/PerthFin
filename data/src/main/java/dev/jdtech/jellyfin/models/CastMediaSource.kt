package dev.jdtech.jellyfin.models

/**
 * A playback URL prepared for a DLNA renderer.
 *
 * [isTranscoded] is true when Jellyfin has to produce the stream (subtitle burn-in, codec mismatch
 * or a requested bitrate cap). Plain direct play keeps the original file and therefore needs no
 * server CPU.
 */
data class CastMediaSource(
    val url: String,
    val mimeType: String,
    val isTranscoded: Boolean,
    val sizeBytes: Long? = null,
)
