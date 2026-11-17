package dev.jdtech.jellyfin.cast.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpMessagesTest {
    @Test
    fun `builds a valid media renderer search`() {
        val message = SsdpMessages.search()
        assertTrue(message.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(message.contains("HOST: 239.255.255.250:1900\r\n"))
        assertTrue(message.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(message.contains("ST: ${SsdpMessages.MEDIA_RENDERER}\r\n"))
        assertTrue(message.endsWith("\r\n\r\n"))
    }

    @Test
    fun `parses a response with mixed case headers`() {
        val raw =
            """
            HTTP/1.1 200 OK
            CACHE-CONTROL: max-age=1800
            Location: http://192.168.1.10:9197/dmr
            Server: Linux/3.0 UPnP/1.0 Samsung/1.0
            ST: urn:schemas-upnp-org:device:MediaRenderer:1
            USN: uuid:abc::urn:schemas-upnp-org:device:MediaRenderer:1

            """
                .trimIndent()
                .replace("\n", "\r\n")

        val response = SsdpMessages.parseResponse(raw)
        assertNotNull(response)
        assertEquals("http://192.168.1.10:9197/dmr", response!!.location)
        assertEquals("uuid:abc", response.key)
        assertEquals(1800L, response.maxAge?.seconds)
    }

    @Test
    fun `ignores non 200 and responses without location`() {
        assertNull(SsdpMessages.parseResponse("HTTP/1.1 404 Not Found\r\n\r\n"))
        assertNull(SsdpMessages.parseResponse("HTTP/1.1 200 OK\r\nST: ssdp:all\r\n\r\n"))
    }
}
