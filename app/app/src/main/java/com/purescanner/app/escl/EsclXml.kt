package com.purescanner.app.escl

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** XML building and parsing for eSCL. Works on plain JVM (unit tests) and Android. */
internal object EsclXml {

    private const val NS_SCAN = "http://schemas.hp.com/imaging/escl/2011/05/03"
    private const val NS_PWG = "http://www.pwg.org/schemas/2010/12/sm"

    // ---------- Request ----------

    fun buildScanSettings(settings: ScanSettings): String {
        var width = settings.paperSize.widthUnits
        var height = settings.paperSize.heightUnits
        settings.maxWidth?.let { width = minOf(width, it) }
        settings.maxHeight?.let { height = minOf(height, it) }

        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<scan:ScanSettings xmlns:pwg="$NS_PWG" xmlns:scan="$NS_SCAN">""")
            append("<pwg:Version>2.0</pwg:Version>")
            append("<pwg:ScanRegions><pwg:ScanRegion>")
            append("<pwg:ContentRegionUnits>escl:ThreeHundredthsOfInches</pwg:ContentRegionUnits>")
            append("<pwg:XOffset>0</pwg:XOffset><pwg:YOffset>0</pwg:YOffset>")
            append("<pwg:Width>$width</pwg:Width><pwg:Height>$height</pwg:Height>")
            append("</pwg:ScanRegion></pwg:ScanRegions>")
            append("<pwg:InputSource>${settings.source.esclName}</pwg:InputSource>")
            append("<scan:ColorMode>${settings.colorMode.esclName}</scan:ColorMode>")
            append("<scan:XResolution>${settings.resolution}</scan:XResolution>")
            append("<scan:YResolution>${settings.resolution}</scan:YResolution>")
            append("<pwg:DocumentFormat>${settings.format.mime}</pwg:DocumentFormat>")
            append("</scan:ScanSettings>")
        }
    }

    // ---------- Responses ----------

    fun parseCapabilities(xml: String): ScannerCapabilities {
        val root = parse(xml)
        return ScannerCapabilities(
            makeAndModel = root.firstDescendant("MakeAndModel")?.text(),
            uuid = root.firstDescendant("UUID")?.text(),
            platen = root.firstDescendant("PlatenInputCaps")?.let(::parseSourceCaps),
            feeder = (root.firstDescendant("AdfSimplexInputCaps") ?: root.firstDescendant("AdfInputCaps"))
                ?.let(::parseSourceCaps),
        )
    }

    fun parseStatus(xml: String): ScannerStatus {
        val root = parse(xml)
        val state = when (root.firstDescendant("State")?.text()) {
            "Idle" -> ScannerState.Idle
            "Processing" -> ScannerState.Processing
            "Testing" -> ScannerState.Testing
            "Stopped" -> ScannerState.Stopped
            "Down" -> ScannerState.Down
            else -> ScannerState.Unknown
        }
        return ScannerStatus(state, root.firstDescendant("AdfState")?.text())
    }

    private fun parseSourceCaps(caps: Element): SourceCapabilities {
        val colorModes = caps.descendants("ColorMode").mapNotNull { node ->
            when (node.text()) {
                "RGB24" -> ColorMode.Color
                "Grayscale8" -> ColorMode.Gray
                else -> null
            }
        }.distinct()

        val formats = (caps.descendants("DocumentFormat") + caps.descendants("DocumentFormatExt"))
            .mapNotNull { node -> DocumentFormat.entries.firstOrNull { it.mime == node.text() } }
            .distinct()

        return SourceCapabilities(
            minWidth = caps.firstDescendant("MinWidth")?.text()?.toIntOrNull() ?: 0,
            maxWidth = caps.firstDescendant("MaxWidth")?.text()?.toIntOrNull() ?: 0,
            minHeight = caps.firstDescendant("MinHeight")?.text()?.toIntOrNull() ?: 0,
            maxHeight = caps.firstDescendant("MaxHeight")?.text()?.toIntOrNull() ?: 0,
            resolutions = parseResolutions(caps),
            colorModes = colorModes,
            formats = formats,
        )
    }

    private val standardResolutions = listOf(75, 100, 150, 200, 300, 600, 1200)

    private fun parseResolutions(caps: Element): List<Int> {
        val discrete = caps.descendants("DiscreteResolution").mapNotNull {
            it.firstDescendant("XResolution")?.text()?.toIntOrNull()
        }
        if (discrete.isNotEmpty()) return discrete.distinct().sorted()

        // Some scanners advertise a range instead of fixed values.
        val range = caps.firstDescendant("ResolutionRange")
        val xRange = range?.firstDescendant("XResolutionRange") ?: range
        val min = xRange?.firstDescendant("Min")?.text()?.toIntOrNull()
        val max = xRange?.firstDescendant("Max")?.text()?.toIntOrNull()
        if (min != null && max != null) return standardResolutions.filter { it in min..max }
        return emptyList()
    }

    // ---------- DOM helpers (namespace-agnostic: we match on local names) ----------

    private fun parse(xml: String): Element {
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Scanner replies are untrusted network input: refuse DTDs/entities where the parser allows it.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { isExpandEntityReferences = false }
            }
            return factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
                .documentElement
        } catch (e: Exception) {
            throw EsclException.Parse("Invalid eSCL XML", e)
        }
    }

    private fun Element.text(): String = textContent.trim()

    private fun Element.localNameOrName(): String = localName ?: nodeName.substringAfter(':')

    private fun Element.descendants(name: String): List<Element> {
        val result = mutableListOf<Element>()
        fun walk(node: Node) {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) {
                    if (child.localNameOrName() == name) result += child
                    walk(child)
                }
            }
        }
        walk(this)
        return result
    }

    private fun Element.firstDescendant(name: String): Element? = descendants(name).firstOrNull()
}
