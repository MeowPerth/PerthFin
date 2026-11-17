package dev.jdtech.jellyfin.cast.dlna

import java.time.Duration
import java.util.Locale

/** Converts between UPnP `H:MM:SS[.mmm]` time strings and [Duration]. */
object DlnaTime {
    private val FRACTION_PATTERN = Regex("""^(\d+)\.(\d{1,3})$""")

    fun format(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatWithMillis(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val millis = duration.nano / 1_000_000
        return String.format(Locale.US, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    fun parse(value: String?): Duration? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.equals("NOT_IMPLEMENTED", ignoreCase = true)) return null

        val parts = text.split(':')
        if (parts.size !in 2..3) return null
        if (parts.any { it.isBlank() }) return null

        val secondsPart = parts.last()
        val seconds =
            when {
                secondsPart.contains('.') -> {
                    val match = FRACTION_PATTERN.matchEntire(secondsPart) ?: return null
                    match.groupValues[1].toLongOrNull() ?: return null
                }
                else -> secondsPart.toLongOrNull() ?: return null
            }
        val millis =
            when {
                secondsPart.contains('.') -> {
                    val match = FRACTION_PATTERN.matchEntire(secondsPart) ?: return null
                    match.groupValues[2].padEnd(3, '0').toLongOrNull() ?: 0
                }
                else -> 0L
            }
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        if (seconds < 0 || minutes < 0 || hours < 0) return null

        return Duration.ofSeconds(hours * 3600 + minutes * 60 + seconds, millis * 1_000_000)
    }
}
