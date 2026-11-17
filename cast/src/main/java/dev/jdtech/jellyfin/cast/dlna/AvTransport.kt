package dev.jdtech.jellyfin.cast.dlna

import java.time.Duration
import timber.log.Timber

/** Typed wrapper around the UPnP AVTransport service. */
class AvTransport(private val soapClient: SoapClient = SoapClient()) {

    suspend fun setAvTransportUri(
        device: DlnaDevice,
        media: DidlMedia,
        instanceId: Int = DEFAULT_INSTANCE_ID,
    ) {
        val metadata = DidlLite.build(media)
        call(
            device,
            "SetAVTransportURI",
            mapOf(
                "InstanceID" to instanceId.toString(),
                "CurrentURI" to media.url,
                "CurrentURIMetaData" to metadata,
            ),
        )
    }

    suspend fun setNextAvTransportUri(
        device: DlnaDevice,
        media: DidlMedia,
        instanceId: Int = DEFAULT_INSTANCE_ID,
    ) {
        call(
            device,
            "SetNextAVTransportURI",
            mapOf(
                "InstanceID" to instanceId.toString(),
                "NextURI" to media.url,
                "NextURIMetaData" to DidlLite.build(media),
            ),
        )
    }

    suspend fun play(device: DlnaDevice, instanceId: Int = DEFAULT_INSTANCE_ID) {
        call(
            device,
            "Play",
            mapOf("InstanceID" to instanceId.toString(), "Speed" to "1"),
        )
    }

    suspend fun pause(device: DlnaDevice, instanceId: Int = DEFAULT_INSTANCE_ID) {
        call(device, "Pause", mapOf("InstanceID" to instanceId.toString()))
    }

    suspend fun stop(device: DlnaDevice, instanceId: Int = DEFAULT_INSTANCE_ID) {
        call(device, "Stop", mapOf("InstanceID" to instanceId.toString()))
    }

    suspend fun seek(
        device: DlnaDevice,
        position: Duration,
        instanceId: Int = DEFAULT_INSTANCE_ID,
    ) {
        call(
            device,
            "Seek",
            mapOf(
                "InstanceID" to instanceId.toString(),
                "Unit" to "REL_TIME",
                "Target" to DlnaTime.format(position),
            ),
        )
    }

    suspend fun getTransportInfo(
        device: DlnaDevice,
        instanceId: Int = DEFAULT_INSTANCE_ID,
    ): TransportInfo {
        val result = call(device, "GetTransportInfo", mapOf("InstanceID" to instanceId.toString()))
        return TransportInfo(
            state = TransportState.fromUpnp(result["CurrentTransportState"]),
            status = result["CurrentTransportStatus"],
            speed = result["CurrentSpeed"],
        )
    }

    suspend fun getPositionInfo(
        device: DlnaDevice,
        instanceId: Int = DEFAULT_INSTANCE_ID,
    ): PositionInfo {
        val result = call(device, "GetPositionInfo", mapOf("InstanceID" to instanceId.toString()))
        return PositionInfo(
            track = result["Track"]?.toIntOrNull(),
            trackDuration = DlnaTime.parse(result["TrackDuration"]),
            trackUri = result["TrackURI"]?.takeIf { it.isNotBlank() },
            relativeTime = DlnaTime.parse(result["RelTime"]),
            absoluteTime = DlnaTime.parse(result["AbsTime"]),
        )
    }

    suspend fun getMediaInfo(
        device: DlnaDevice,
        instanceId: Int = DEFAULT_INSTANCE_ID,
    ): MediaInfo {
        val result = call(device, "GetMediaInfo", mapOf("InstanceID" to instanceId.toString()))
        return MediaInfo(
            duration = DlnaTime.parse(result["MediaDuration"]),
            currentUri = result["CurrentURI"]?.takeIf { it.isNotBlank() },
            numberOfTracks = result["NrTracks"]?.toIntOrNull(),
        )
    }

    private suspend fun call(
        device: DlnaDevice,
        action: String,
        arguments: Map<String, String>,
    ): Map<String, String> {
        val service =
            device.avTransport ?: throw DlnaException("Renderer does not support AVTransport")
        Timber.d("AVTransport $action on ${device.displayName}")
        return soapClient.call(service, action, arguments)
    }

    private companion object {
        const val DEFAULT_INSTANCE_ID = 0
    }
}
