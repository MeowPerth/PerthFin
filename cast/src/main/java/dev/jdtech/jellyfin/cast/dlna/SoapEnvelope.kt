package dev.jdtech.jellyfin.cast.dlna

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** Builds and parses the SOAP envelopes used by UPnP control points. */
object SoapEnvelope {
    fun build(serviceType: String, action: String, arguments: Map<String, String>): String =
        buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """)
            append("""s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""")
            append("<s:Body>")
            append("<u:").append(action).append(" xmlns:u=\"").append(DidlLite.escape(serviceType))
            append("\">")
            arguments.forEach { (name, value) ->
                append("<").append(name).append(">")
                append(DidlLite.escape(value))
                append("</").append(name).append(">")
            }
            append("</u:").append(action).append(">")
            append("</s:Body></s:Envelope>")
        }

    /**
     * Flattens the response body into a map of element name to text. UPnP actions return a flat
     * argument list, so this is enough for AVTransport/RenderingControl.
     */
    fun parseResponse(xml: String): Map<String, String> {
        val document =
            newDocumentBuilderFactory().newDocumentBuilder().parse(InputSource(StringReader(xml)))

        val fault = document.getElementsByTagNameNS("*", "Fault")
        if (fault.length > 0) {
            val detail = document.getElementsByTagNameNS("*", "detail")
            val errorCode =
                if (detail.length > 0) {
                    (detail.item(0) as? Element)
                        ?.getElementsByTagNameNS("*", "errorCode")
                        ?.item(0)
                        ?.textContent
                        ?.trim()
                        ?.toIntOrNull()
                } else {
                    null
                }
            val description =
                listOf("errorDescription", "faultstring").firstNotNullOfOrNull { tag ->
                    document.getElementsByTagNameNS("*", tag).item(0)?.textContent?.trim()?.takeIf {
                        it.isNotEmpty()
                    }
                } ?: "UPnP SOAP fault"
            throw DlnaException(description, errorCode)
        }

        val result = mutableMapOf<String, String>()
        val elements = document.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as? Element ?: continue
            val children = element.childNodes
            var text: String? = null
            var onlyText = true
            for (childIndex in 0 until children.length) {
                val child = children.item(childIndex)
                when (child.nodeType) {
                    Node.TEXT_NODE,
                    Node.CDATA_SECTION_NODE -> text = (text.orEmpty() + child.nodeValue).trim()
                    Node.ELEMENT_NODE -> onlyText = false
                }
            }
            if (!onlyText) continue
            val name = element.localName ?: element.nodeName.substringAfter(':')
            if (name.isNotBlank() && text != null) result[name] = text
        }
        return result
    }

    private fun newDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
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
}
