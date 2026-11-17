package dev.jdtech.jellyfin.cast.dlna

import java.time.Duration

data class SsdpResponse(
    val location: String,
    val usn: String? = null,
    val searchTarget: String? = null,
    val server: String? = null,
    val maxAge: Duration? = null,
) {
    /** Stable key for de-duplicating multiple responses from the same device. */
    val key: String
        get() = usn?.substringBefore("::")?.takeIf { it.isNotBlank() } ?: location
}

/**
 * Parser and builder for SSDP messages. Kept free of Android and socket code so it can be unit
 * tested.
 */
object SsdpMessages {
    const val MULTICAST_HOST = "239.255.255.250"
    const val MULTICAST_PORT = 1900
    const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

    fun search(target: String = MEDIA_RENDERER, mxSeconds: Int = 2): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: $MULTICAST_HOST:$MULTICAST_PORT\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: ${mxSeconds.coerceIn(1, 5)}\r\n")
        append("ST: $target\r\n")
        append("USER-AGENT: Android/PerthFin UPnP/1.1 DLNADOC/1.50\r\n")
        append("\r\n")
    }

    fun parseResponse(raw: String): SsdpResponse? {
        val lines =
            raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return null
        if (!lines.first().startsWith("HTTP/", ignoreCase = true)) return null

        val headers = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) headers[name] = value
        }

        val location =
            headers["location"]?.takeIf { it.startsWith("http", ignoreCase = true) } ?: return null

        return SsdpResponse(
            location = location,
            usn = headers["usn"],
            searchTarget = headers["st"] ?: headers["nt"],
            server = headers["server"],
            maxAge = headers["cache-control"]?.let { parseMaxAge(it) },
        )
    }

    private fun parseMaxAge(cacheControl: String): Duration? {
        val value =
            cacheControl
                .split(',')
                .map { it.trim() }
                .firstOrNull { it.startsWith("max-age", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim() ?: return null
        return value.toLongOrNull()?.takeIf { it > 0 }?.let(Duration::ofSeconds)
    }
}
