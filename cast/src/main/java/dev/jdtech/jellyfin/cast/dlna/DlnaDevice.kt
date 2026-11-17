package dev.jdtech.jellyfin.cast.dlna

/**
 * A UPnP device found on the local network that can render media.
 *
 * Only devices that expose the AVTransport service are useful for casting, but the discovery layer
 * intentionally keeps every MediaRenderer so the UI can show devices that are missing a service
 * instead of hiding them silently.
 */
data class DlnaDevice(
    val udn: String,
    val friendlyName: String,
    val deviceType: String,
    val location: String,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val modelNumber: String? = null,
    val services: List<DlnaService> = emptyList(),
) {
    val isMediaRenderer: Boolean
        get() = deviceType.contains("MediaRenderer", ignoreCase = true)

    val displayName: String
        get() = friendlyName.ifBlank { modelName?.takeIf { it.isNotBlank() } ?: udn }

    val avTransport: DlnaService?
        get() = service("AVTransport")

    val renderingControl: DlnaService?
        get() = service("RenderingControl")

    val connectionManager: DlnaService?
        get() = service("ConnectionManager")

    fun service(name: String): DlnaService? = services.firstOrNull {
        it.serviceType.contains(":service:$name:", ignoreCase = true)
    }

    fun canPlay(): Boolean = avTransport != null
}

data class DlnaService(
    val serviceType: String,
    val serviceId: String?,
    val controlUrl: String,
    val eventSubUrl: String? = null,
    val scpdUrl: String? = null,
) {
    /** Service type without the version suffix, e.g. `AVTransport`. */
    val name: String
        get() =
            serviceType.substringAfter(":service:", serviceType).substringBefore(':').ifBlank {
                serviceType
            }

    val version: Int
        get() = serviceType.substringAfterLast(':').toIntOrNull() ?: 1
}
