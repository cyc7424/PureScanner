package com.purescanner.app.escl

import okhttp3.HttpUrl

/**
 * Where a scanner's eSCL service lives, e.g. `http://192.168.1.102:80/eSCL`.
 * [baseUrl] never has a trailing slash in its path.
 */
data class EsclEndpoint(val baseUrl: HttpUrl) {
    val host: String get() = baseUrl.host

    fun resolve(vararg segments: String): HttpUrl =
        baseUrl.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()

    companion object {
        /** Build an endpoint from a manually typed IP/host. Port defaults to 80, root path to `eSCL`. */
        fun fromHost(host: String, port: Int = 80, rootPath: String = "eSCL", https: Boolean = false): EsclEndpoint {
            val url = HttpUrl.Builder()
                .scheme(if (https) "https" else "http")
                .host(host.trim())
                .port(port)
                .apply { rootPath.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) } }
                .build()
            return EsclEndpoint(url)
        }
    }
}

enum class InputSource(val esclName: String) {
    Platen("Platen"),
    Feeder("Feeder"),
}

enum class ColorMode(val esclName: String) {
    Color("RGB24"),
    Gray("Grayscale8"),
}

enum class DocumentFormat(val mime: String) {
    Jpeg("image/jpeg"),
    Pdf("application/pdf"),
}

/** Region sizes are in 1/300 inch (eSCL `ThreeHundredthsOfInches`). */
enum class PaperSize(val label: String, val widthUnits: Int, val heightUnits: Int) {
    A4("A4", 2480, 3508),
    A5("A5", 1748, 2480),
    Letter("Letter", 2550, 3300),
    Legal("Legal", 2550, 4200),
}

/** Capabilities for one input source (platen or ADF). Sizes in 1/300 inch. */
data class SourceCapabilities(
    val minWidth: Int,
    val maxWidth: Int,
    val minHeight: Int,
    val maxHeight: Int,
    val resolutions: List<Int>,
    val colorModes: List<ColorMode>,
    val formats: List<DocumentFormat>,
) {
    /**
     * Many scanners report A4 as 2550x3507 while our A4 is 2480x3508, so allow a few units of slack
     * (the request is clamped to the real maximum anyway, see ScanSettings.maxWidth/maxHeight).
     */
    fun supports(size: PaperSize): Boolean =
        size.widthUnits <= maxWidth + SIZE_TOLERANCE && size.heightUnits <= maxHeight + SIZE_TOLERANCE

    fun supportedPaperSizes(): List<PaperSize> = PaperSize.entries.filter { supports(it) }

    private companion object {
        const val SIZE_TOLERANCE = 8
    }
}

data class ScannerCapabilities(
    val makeAndModel: String?,
    val uuid: String?,
    val platen: SourceCapabilities?,
    val feeder: SourceCapabilities?,
) {
    fun source(source: InputSource): SourceCapabilities? = when (source) {
        InputSource.Platen -> platen
        InputSource.Feeder -> feeder
    }
}

data class ScanSettings(
    val source: InputSource = InputSource.Platen,
    val paperSize: PaperSize = PaperSize.A4,
    val resolution: Int = 300,
    val colorMode: ColorMode = ColorMode.Color,
    val format: DocumentFormat = DocumentFormat.Jpeg,
    /** If non-null, the requested region is clamped to the scanner's maximum area. */
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
)

enum class ScannerState { Idle, Processing, Testing, Stopped, Down, Unknown }

data class ScannerStatus(
    val state: ScannerState,
    /** Raw `AdfState` text if the scanner has a feeder, e.g. `ScannerAdfLoaded`. */
    val adfState: String?,
)

/** One scanned page as returned by the scanner. */
class ScannedPage(val bytes: ByteArray, val mime: String)

sealed class EsclException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Scanner is busy (HTTP 503) and stayed busy past our retry budget. */
    class Busy : EsclException("Scanner is busy")

    /** Unexpected HTTP status. */
    class Http(val code: Int, val body: String?) : EsclException("Unexpected HTTP $code")

    /** Could not reach the scanner. */
    class Network(cause: Throwable) : EsclException("Network error: ${cause.message}", cause)

    /** Response was not valid eSCL XML. */
    class Parse(message: String, cause: Throwable? = null) : EsclException(message, cause)

    /** The scanner does not support something we asked for. */
    class Unsupported(message: String) : EsclException(message)
}
