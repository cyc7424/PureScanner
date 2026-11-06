package com.purescanner.app.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class PdfWriterTest {

    /** Smallest byte sequence our parser needs: SOI, a SOF0 header, EOI. Not decodable, but fine for structure tests. */
    private fun fakeJpeg(width: Int, height: Int, components: Int): ByteArray {
        val sof = byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(),
            0x00, (8 + 3 * components).toByte(),
            8,
            (height shr 8).toByte(), height.toByte(),
            (width shr 8).toByte(), width.toByte(),
            components.toByte(),
        ) + ByteArray(3 * components)
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + sof + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    private fun pdfText(pages: List<PdfPage>): String {
        val out = ByteArrayOutputStream()
        PdfWriter.write(pages, out)
        return out.toByteArray().toString(Charsets.ISO_8859_1)
    }

    @Test
    fun `reads jpeg header`() {
        val info = PdfWriter.readJpegInfo(fakeJpeg(2480, 3508, 3))
        assertEquals(2480, info.width)
        assertEquals(3508, info.height)
        assertEquals(3, info.components)
    }

    @Test
    fun `skips other segments before the frame header`() {
        // APP0 segment (length 4 => 2 payload bytes) in front of the SOF.
        val app0 = byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x11, 0x22)
        val plain = fakeJpeg(100, 200, 1)
        val jpeg = plain.copyOfRange(0, 2) + app0 + plain.copyOfRange(2, plain.size)
        val info = PdfWriter.readJpegInfo(jpeg)
        assertEquals(100, info.width)
        assertEquals(200, info.height)
        assertEquals(1, info.components)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non jpeg`() {
        PdfWriter.readJpegInfo(byteArrayOf(1, 2, 3, 4, 5, 6))
    }

    @Test
    fun `writes a one page pdf sized from dpi`() {
        // 2480x3508 px at 300 dpi = A4 = 595.20 x 841.92 pt
        val text = pdfText(listOf(PdfPage(fakeJpeg(2480, 3508, 3), 300)))

        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("/Count 1"))
        assertTrue(text.contains("/MediaBox [0 0 595.20 841.92]"))
        assertTrue(text.contains("/Filter /DCTDecode"))
        assertTrue(text.contains("/ColorSpace /DeviceRGB"))
    }

    @Test
    fun `grayscale and multiple pages`() {
        val text = pdfText(listOf(PdfPage(fakeJpeg(10, 10, 1), 300), PdfPage(fakeJpeg(10, 10, 3), 150)))
        assertTrue(text.contains("/Count 2"))
        assertTrue(text.contains("/DeviceGray"))
        assertTrue(text.contains("/MediaBox [0 0 2.40 2.40]")) // 10 px at 300 dpi
        assertTrue(text.contains("/MediaBox [0 0 4.80 4.80]")) // 10 px at 150 dpi
    }

    @Test
    fun `xref offsets point at their objects`() {
        val text = pdfText(listOf(PdfPage(fakeJpeg(10, 10, 3), 300), PdfPage(fakeJpeg(20, 20, 3), 300)))

        val startxref = text.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertTrue(text.substring(startxref).startsWith("xref"))

        val entries = text.substring(startxref).lines().filter { it.endsWith(" 00000 n ") }
        assertEquals(8, entries.size) // catalog + pages + 2 * (page, content, image)
        entries.forEachIndexed { index, line ->
            val offset = line.substringBefore(' ').toInt()
            assertTrue("object ${index + 1} at $offset", text.substring(offset).startsWith("${index + 1} 0 obj"))
        }
    }
}
