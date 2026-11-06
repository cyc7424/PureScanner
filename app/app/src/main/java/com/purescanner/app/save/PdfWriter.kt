package com.purescanner.app.save

import java.io.OutputStream
import java.util.Locale

class PdfPage(val jpeg: ByteArray, val dpi: Int)

/**
 * Minimal PDF writer: one JPEG per page, embedded as-is (DCTDecode), so there is no re-encoding, no quality
 * loss and no need to decode huge bitmaps into memory. Page size follows the scan resolution (pixels / dpi).
 */
object PdfWriter {

    class JpegInfo(val width: Int, val height: Int, val components: Int)

    /** Reads size and channel count from the JPEG's start-of-frame header. */
    fun readJpegInfo(bytes: ByteArray): JpegInfo {
        require(bytes.size > 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) { "Not a JPEG" }
        fun u8(i: Int) = bytes[i].toInt() and 0xFF
        fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)

        var i = 2
        while (i + 3 < bytes.size) {
            if (u8(i) != 0xFF) {
                i++
                continue
            }
            val marker = u8(i + 1)
            when {
                marker == 0xFF -> i++ // fill byte
                marker == 0x01 || marker in 0xD0..0xD8 -> i += 2 // markers without a length
                marker == 0xD9 -> break
                else -> {
                    val isStartOfFrame = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
                    if (isStartOfFrame) {
                        require(i + 9 < bytes.size) { "Truncated JPEG header" }
                        return JpegInfo(width = u16(i + 7), height = u16(i + 5), components = u8(i + 9))
                    }
                    i += 2 + u16(i + 2)
                }
            }
        }
        throw IllegalArgumentException("No JPEG frame header found")
    }

    fun write(pages: List<PdfPage>, output: OutputStream) {
        require(pages.isNotEmpty()) { "A PDF needs at least one page" }
        val out = Counter(output)
        val offsets = LongArray(2 + 3 * pages.size)

        fun beginObject(number: Int) {
            offsets[number - 1] = out.count
            out.ascii("$number 0 obj\n")
        }

        fun endObject() = out.ascii("endobj\n")

        out.ascii("%PDF-1.4\n")
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        beginObject(1)
        out.ascii("<< /Type /Catalog /Pages 2 0 R >>\n")
        endObject()

        beginObject(2)
        val kids = pages.indices.joinToString(" ") { "${3 + 3 * it} 0 R" }
        out.ascii("<< /Type /Pages /Count ${pages.size} /Kids [$kids] >>\n")
        endObject()

        pages.forEachIndexed { index, page ->
            val info = readJpegInfo(page.jpeg)
            val colorSpace = when (info.components) {
                1 -> "/DeviceGray"
                3 -> "/DeviceRGB"
                else -> throw IllegalArgumentException("Unsupported JPEG with ${info.components} channels")
            }
            val dpi = if (page.dpi > 0) page.dpi else 300
            val widthPt = points(info.width, dpi)
            val heightPt = points(info.height, dpi)
            val pageObj = 3 + 3 * index
            val contentObj = pageObj + 1
            val imageObj = pageObj + 2

            beginObject(pageObj)
            out.ascii(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $widthPt $heightPt] " +
                    "/Resources << /XObject << /Im0 $imageObj 0 R >> >> /Contents $contentObj 0 R >>\n",
            )
            endObject()

            val content = "q $widthPt 0 0 $heightPt 0 0 cm /Im0 Do Q\n"
            beginObject(contentObj)
            out.ascii("<< /Length ${content.length} >>\nstream\n$content" + "endstream\n")
            endObject()

            beginObject(imageObj)
            out.ascii(
                "<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} " +
                    "/ColorSpace $colorSpace /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.jpeg.size} >>\nstream\n",
            )
            out.write(page.jpeg)
            out.ascii("\nendstream\n")
            endObject()
        }

        val xrefOffset = out.count
        out.ascii("xref\n0 ${offsets.size + 1}\n")
        out.ascii("0000000000 65535 f \n")
        offsets.forEach { out.ascii(String.format(Locale.US, "%010d 00000 n \n", it)) }
        out.ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        out.flush()
    }

    private fun points(pixels: Int, dpi: Int): String = String.format(Locale.US, "%.2f", pixels * 72.0 / dpi)

    /** Counts bytes written so xref offsets are exact. */
    private class Counter(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            target.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            count += len
        }

        override fun flush() = target.flush()

        fun ascii(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))
    }
}
