package dev.jdtech.jellyfin.cast.dlna

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AvTransportTest {
    private lateinit var server: HttpServer
    private lateinit var device: DlnaDevice
    private val requests = mutableListOf<Pair<String?, String>>()

    @Before
    fun setUp() {
        requests.clear()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/control") { exchange ->
            val action = exchange.requestHeaders.getFirst("SOAPACTION")
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            requests.add(action to body)

            val response =
                """
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
                    .toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        val port = server.address.port
        device =
            DlnaDevice(
                udn = "uuid:test",
                friendlyName = "Test TV",
                deviceType = "urn:schemas-upnp-org:device:MediaRenderer:1",
                location = "http://127.0.0.1:$port/description.xml",
                services =
                    listOf(
                        DlnaService(
                            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                            serviceId = "urn:upnp-org:serviceId:AVTransport",
                            controlUrl = "http://127.0.0.1:$port/control",
                        )
                    ),
            )
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `sends the didl metadata and reads the transport state`() = runBlocking {
        val transport = AvTransport(SoapClient())

        transport.setAvTransportUri(
            device,
            DidlMedia(
                id = "item-1",
                title = "Movie",
                url = "http://127.0.0.1/video.mp4?ApiKey=abc",
                mimeType = "video/mp4",
                duration = Duration.ofMinutes(90),
            ),
        )
        val info = transport.getTransportInfo(device)

        assertEquals(2, requests.size)
        assertTrue(requests[0].first!!.contains("SetAVTransportURI"))
        assertTrue(requests[0].second.contains("CurrentURIMetaData"))
        assertTrue(requests[0].second.contains("&lt;DIDL-Lite"))
        assertEquals(TransportState.PLAYING, info.state)
    }
}
