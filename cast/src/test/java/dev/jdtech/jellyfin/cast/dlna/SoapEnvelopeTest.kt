package dev.jdtech.jellyfin.cast.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SoapEnvelopeTest {
    @Test
    fun `builds a set av transport uri envelope`() {
        val xml =
            SoapEnvelope.build(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "SetAVTransportURI",
                mapOf(
                    "InstanceID" to "0",
                    "CurrentURI" to "http://host/video.mp4?a=1&b=2",
                    "CurrentURIMetaData" to "<DIDL-Lite><item id=\"1\"/></DIDL-Lite>",
                ),
            )

        assertTrue(xml.startsWith("<?xml"))
        assertTrue(xml.contains("<u:SetAVTransportURI"))
        assertTrue(xml.contains("xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\""))
        assertTrue(xml.contains("http://host/video.mp4?a=1&amp;b=2"))
        assertTrue(xml.contains("&lt;DIDL-Lite&gt;"))
    }

    @Test
    fun `parses transport info response`() {
        val xml =
            """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <CurrentTransportState>PLAYING</CurrentTransportState>
                  <CurrentTransportStatus>OK</CurrentTransportStatus>
                  <CurrentSpeed>1</CurrentSpeed>
                </u:GetTransportInfoResponse>
              </s:Body>
            </s:Envelope>
            """
                .trimIndent()

        val result = SoapEnvelope.parseResponse(xml)
        assertEquals("PLAYING", result["CurrentTransportState"])
        assertEquals("OK", result["CurrentTransportStatus"])
    }

    @Test
    fun `throws for a upnp fault`() {
        val xml =
            """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <s:Fault>
                  <faultcode>s:Client</faultcode>
                  <faultstring>UPnPError</faultstring>
                  <detail>
                    <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                      <errorCode>714</errorCode>
                      <errorDescription>Illegal MIME-type</errorDescription>
                    </UPnPError>
                  </detail>
                </s:Fault>
              </s:Body>
            </s:Envelope>
            """
                .trimIndent()

        val exception = assertThrows(DlnaException::class.java) { SoapEnvelope.parseResponse(xml) }
        assertEquals(714, exception.errorCode)
        assertTrue(exception.message!!.contains("Illegal MIME-type"))
    }
}
