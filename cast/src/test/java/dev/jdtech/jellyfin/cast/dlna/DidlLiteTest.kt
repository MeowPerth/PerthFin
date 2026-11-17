package dev.jdtech.jellyfin.cast.dlna

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DidlLiteTest {
    @Test
    fun `builds metadata with escaped title and url`() {
        val xml =
            DidlLite.build(
                DidlMedia(
                    id = "abc-123",
                    title = "Tom & Jerry <4K>",
                    url = "http://192.168.1.2:8096/Videos/1/stream?static=true&ApiKey=token",
                    mimeType = "video/mp4",
                    sizeBytes = 1234L,
                    duration = Duration.ofSeconds(3672),
                    isTranscoded = true,
                )
            )

        assertTrue(xml.contains("<dc:title>Tom &amp; Jerry &lt;4K&gt;</dc:title>"))
        assertTrue(xml.contains("Tom &amp; Jerry"))
        assertTrue(xml.contains("static=true&amp;ApiKey=token"))
        assertTrue(xml.contains("size=\"1234\""))
        assertTrue(xml.contains("duration=\"1:01:12.000\""))
        assertTrue(xml.contains("DLNA.ORG_CI=1"))
        assertTrue(
            xml.contains("protocolInfo=\"http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=1;")
        )
    }

    @Test
    fun `omits optional attributes when unknown`() {
        val xml =
            DidlLite.build(
                DidlMedia(
                    id = "1",
                    title = "Movie",
                    url = "http://host/video.mp4",
                    mimeType = "video/mp4",
                    isTranscoded = false,
                )
            )
        assertTrue(!xml.contains("size="))
        assertTrue(!xml.contains("duration="))
        assertTrue(xml.contains("DLNA.ORG_CI=0"))
    }

    @Test
    fun `parses and formats upnp times`() {
        assertEquals(Duration.ofSeconds(62, 500_000_000), DlnaTime.parse("00:01:02.500"))
        assertEquals(Duration.ofSeconds(3723), DlnaTime.parse("1:02:03"))
        assertEquals("1:02:03", DlnaTime.format(Duration.ofSeconds(3723)))
        assertEquals(
            "1:02:03.500",
            DlnaTime.formatWithMillis(Duration.ofSeconds(3723, 500_000_000)),
        )
        assertNull(DlnaTime.parse("NOT_IMPLEMENTED"))
        assertNull(DlnaTime.parse(""))
    }
}
