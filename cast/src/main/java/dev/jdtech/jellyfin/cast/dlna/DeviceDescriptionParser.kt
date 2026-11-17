package dev.jdtech.jellyfin.cast.dlna

import java.io.StringReader
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Parses an UPnP device description document.
 *
 * The parser is deliberately namespace agnostic: UPnP descriptions are almost always in the
 * `urn:schemas-upnp-org:device-1-0` namespace, but some televisions emit a default namespace
 * without prefixes or use mixed prefixes.
 */
object DeviceDescriptionParser {

    fun parse(xml: String, location: String): List<DlnaDevice> {
        val document =
            newDocumentBuilderFactory().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val deviceElements = document.getElementsByTagName("device")

        val devices = mutableListOf<DlnaDevice>()
        for (index in 0 until deviceElements.length) {
            val element = deviceElements.item(index) as? Element ?: continue
            val udn = element.childText("UDN") ?: continue
            if (udn.isBlank()) continue

            val services =
                element
                    .child("serviceList")
                    ?.childElements("service")
                    ?.mapNotNull { serviceElement ->
                        val serviceType = serviceElement.childText("serviceType").orEmpty()
                        val controlUrl = serviceElement.childText("controlURL").orEmpty()
                        if (serviceType.isBlank() || controlUrl.isBlank()) return@mapNotNull null
                        DlnaService(
                            serviceType = serviceType,
                            serviceId = serviceElement.childText("serviceId"),
                            controlUrl = resolve(location, controlUrl),
                            eventSubUrl =
                                serviceElement
                                    .childText("eventSubURL")
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { resolve(location, it) },
                            scpdUrl =
                                serviceElement
                                    .childText("SCPDURL")
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { resolve(location, it) },
                        )
                    }
                    .orEmpty()

            devices.add(
                DlnaDevice(
                    udn = udn,
                    friendlyName = element.childText("friendlyName").orEmpty(),
                    deviceType = element.childText("deviceType").orEmpty(),
                    location = location,
                    manufacturer = element.childText("manufacturer"),
                    modelName = element.childText("modelName"),
                    modelNumber = element.childText("modelNumber"),
                    services = services,
                )
            )
        }

        return devices
    }

    private fun newDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { isXIncludeAware = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
        }

    private fun Element.child(tagName: String): Element? {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.tagName.equals(tagName, ignoreCase = true)) return child
        }
        return null
    }

    private fun Element.childElements(tagName: String): List<Element> {
        val children = childNodes
        val result = mutableListOf<Element>()
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.tagName.equals(tagName, ignoreCase = true)) result.add(child)
        }
        return result
    }

    private fun Element.childText(tagName: String): String? =
        child(tagName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun resolve(base: String, reference: String): String {
        val trimmed = reference.trim()
        if (
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        return runCatching { URI(base).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }
}
