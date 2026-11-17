package dev.jdtech.jellyfin.cast.dlna

import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * High level DLNA control point: discovers renderers, downloads their descriptions and forwards
 * AVTransport commands.
 */
class DlnaClient(
    private val discovery: SsdpDiscovery = SsdpDiscovery(),
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val avTransport: AvTransport = AvTransport(SoapClient(httpClient)),
) {

    fun discoverDevices(timeout: Duration = Duration.ofSeconds(4)): Flow<DlnaDevice> = flow {
        val seen = mutableSetOf<String>()
        discovery.discover(timeout).collect { response ->
            val device =
                try {
                    loadDevice(response.location)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load device description at ${response.location}")
                    null
                } ?: return@collect

            if (!device.isMediaRenderer) return@collect
            if (!seen.add(device.udn)) return@collect
            emit(device)
        }
    }
        .flowOn(Dispatchers.IO)

    suspend fun loadDevice(location: String): DlnaDevice? =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(location)
                    .header("User-Agent", "PerthFin/1.0 UPnP/1.1 DLNADOC/1.50")
                    .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for $location")
                }
                val body =
                    response.body?.string()?.takeIf { it.isNotBlank() }
                        ?: throw IOException("Empty device description at $location")
                DeviceDescriptionParser.parse(body, location).firstOrNull { it.isMediaRenderer }
            }
        }

    suspend fun play(device: DlnaDevice, media: DidlMedia) {
        avTransport.setAvTransportUri(device, media)
        avTransport.play(device)
    }

    suspend fun setUri(device: DlnaDevice, media: DidlMedia) {
        avTransport.setAvTransportUri(device, media)
    }

    suspend fun pause(device: DlnaDevice) = avTransport.pause(device)

    suspend fun resume(device: DlnaDevice) = avTransport.play(device)

    suspend fun stop(device: DlnaDevice) = avTransport.stop(device)

    suspend fun seek(device: DlnaDevice, position: Duration) = avTransport.seek(device, position)

    suspend fun transportInfo(device: DlnaDevice): TransportInfo =
        avTransport.getTransportInfo(device)

    suspend fun positionInfo(device: DlnaDevice): PositionInfo = avTransport.getPositionInfo(device)

    suspend fun mediaInfo(device: DlnaDevice): MediaInfo = avTransport.getMediaInfo(device)

    companion object {
        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
    }
}
