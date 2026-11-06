package com.purescanner.app.save

import android.content.ContentResolver
import android.net.Uri
import com.purescanner.app.ScanPage
import java.io.IOException

/** Writes scanned pages to locations the user picked with the system file picker (no storage permission needed). */
object ScanSaver {

    fun writePdf(resolver: ContentResolver, uri: Uri, page: ScanPage) {
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Can't open the file for writing")
        stream.buffered().use { PdfWriter.write(listOf(PdfPage(page.bytes, page.dpi)), it) }
    }

    fun writeJpeg(resolver: ContentResolver, uri: Uri, page: ScanPage) {
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Can't open the file for writing")
        stream.use { it.write(page.bytes) }
    }
}
