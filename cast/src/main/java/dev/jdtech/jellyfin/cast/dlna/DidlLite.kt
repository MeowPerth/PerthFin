package dev.jdtech.jellyfin.cast.dlna

import java.time.Duration

/** Everything needed to describe a video to a DLNA renderer. */
data class DidlMedia(
    val id: String,
    val title: String,
    val url: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
    val duration: Duration? = null,
    val isTranscoded: Boolean = false,
    val dlnaProfileName: String? = null,
)

object DidlLite {
    private const val DLNA_FLAGS = "01700000000000000000000000000000"

    fun build(media: DidlMedia): String = buildString {
        append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """)
        append("""xmlns:dc="http://purl.org/dc/elements/1.1/" """)
        append("""xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""")
        append("<item id=\"").append(escape(media.id)).append("\" parentID=\"0\" restricted=\"1\">")
        append("<dc:title>").append(escape(media.title)).append("</dc:title>")
        append("<upnp:class>object.item.videoItem</upnp:class>")
        append("<res")
        append(" protocolInfo=\"").append(escape(protocolInfo(media))).append("\"")
        if (media.sizeBytes != null && media.sizeBytes > 0) {
            append(" size=\"").append(media.sizeBytes).append("\"")
        }
        media.duration?.let {
            append(" duration=\"").append(DlnaTime.formatWithMillis(it)).append("\"")
        }
        append(">").append(escape(media.url)).append("</res>")
        append("</item></DIDL-Lite>")
    }

    fun protocolInfo(media: DidlMedia): String = buildString {
        append("http-get:*:")
        append(media.mimeType)
        append(':')
        media.dlnaProfileName
            ?.takeIf { it.isNotBlank() }
            ?.let { append("DLNA.ORG_PN=").append(it).append(';') }
        append("DLNA.ORG_OP=01")
        append(";DLNA.ORG_CI=").append(if (media.isTranscoded) 1 else 0)
        append(";DLNA.ORG_FLAGS=").append(DLNA_FLAGS)
    }

    fun escape(value: String): String =
        buildString(value.length + 16) {
            value.forEach { character ->
                when (character) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(character)
                }
            }
        }
}
