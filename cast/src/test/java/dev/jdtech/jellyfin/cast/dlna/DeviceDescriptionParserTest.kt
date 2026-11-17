package dev.jdtech.jellyfin.cast.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDescriptionParserTest {
    private val description =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>Living Room TV</friendlyName>
            <manufacturer>Samsung</manufacturer>
            <modelName>Q80</modelName>
            <modelNumber>2024</modelNumber>
            <UDN>uuid:renderer-1</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                <controlURL>/upnp/control/AVTransport1</controlURL>
                <eventSubURL>/upnp/event/AVTransport1</eventSubURL>
                <SCPDURL>/AVTransport.xml</SCPDURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <controlURL>http://192.168.1.10:9197/upnp/control/RenderingControl1</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
        """
            .trimIndent()

    @Test
    fun `parses a media renderer and resolves relative service urls`() {
        val devices = DeviceDescriptionParser.parse(description, "http://192.168.1.10:9197/dmr")

        assertEquals(1, devices.size)
        val device = devices.single()
        assertTrue(device.isMediaRenderer)
        assertEquals("Living Room TV", device.displayName)
        assertEquals("uuid:renderer-1", device.udn)
        assertNotNull(device.avTransport)
        assertEquals(
            "http://192.168.1.10:9197/upnp/control/AVTransport1",
            device.avTransport!!.controlUrl,
        )
        assertEquals(
            "http://192.168.1.10:9197/upnp/control/RenderingControl1",
            device.renderingControl!!.controlUrl,
        )
        assertNull(device.connectionManager)
    }

    @Test
    fun `skips devices without a udn`() {
        val xml =
            """
            <root><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType></device></root>
            """
                .trimIndent()
        val devices = DeviceDescriptionParser.parse(xml, "http://192.168.1.10/dmr")
        assertTrue(devices.isEmpty())
    }
}
