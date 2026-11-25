package com.osaf.jarvis.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class PreparedAttachment(
    val displayName: String,
    val isImage: Boolean,
    val imageBase64Jpeg: String? = null,
    val documentExcerpt: String? = null
)

object AttachmentHelper {

    suspend fun prepareImageFromUri(context: Context, uri: Uri): PreparedAttachment? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val name = resolveDisplayName(context, uri) ?: "Image"

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                var sampleSize = 1
                while (maxDim / sampleSize > 1024) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                    ?: return@withContext null

                val scaled = scaleDownIfNeeded(bmp, 1024)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()

                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                PreparedAttachment(
                    displayName = name,
                    isImage = true,
                    imageBase64Jpeg = b64
                )
            } catch (_: Exception) {
                null
            }
        }

    suspend fun prepareDocumentFromUri(context: Context, uri: Uri): PreparedAttachment? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val name = resolveDisplayName(context, uri) ?: "Document"
                val mime = resolver.getType(uri).orEmpty().lowercase()

                if (mime.contains("pdf") || name.lowercase().endsWith(".pdf")) {
                    // Extract printable ASCII/UTF-8 text strings safely from PDF stream when uncompressed, bounded
                    val rawBytes = resolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(128 * 1024)
                        val read = input.read(buffer)
                        if (read > 0) buffer.copyOf(read) else ByteArray(0)
                    } ?: ByteArray(0)

                    val extracted = extractReadablePdfStrings(rawBytes)
                    val note = if (extracted.length >= 40) {
                        extracted.take(12_000)
                    } else {
                        "PDF file '$name' attached (${rawBytes.size / 1024} KB read). If the PDF uses compressed/scanned streams, tell the user honestly if full OCR text isn't readable locally."
                    }
                    return@withContext PreparedAttachment(
                        displayName = name,
                        isImage = false,
                        documentExcerpt = note
                    )
                }

                val text = resolver.openInputStream(uri)?.use { input ->
                    val reader = input.bufferedReader(Charsets.UTF_8)
                    val sb = StringBuilder()
                    val chars = CharArray(4096)
                    var total = 0
                    while (total < 14_000) {
                        val n = reader.read(chars)
                        if (n <= 0) break
                        sb.append(chars, 0, n)
                        total += n
                    }
                    sb.toString()
                }.orEmpty().trim()

                if (text.isEmpty()) return@withContext null
                PreparedAttachment(
                    displayName = name,
                    isImage = false,
                    documentExcerpt = text.take(14_000)
                )
            } catch (_: Exception) {
                null
            }
        }

    private fun extractReadablePdfStrings(bytes: ByteArray): String {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val matches = Regex("""\(([^()\\]{3,160})\)""").findAll(raw)
        val joined = matches.map { it.groupValues[1].trim() }
            .filter { s -> s.count { it.isLetterOrDigit() || it == ' ' } >= (s.length * 0.6) }
            .take(120)
            .joinToString(" ")
        return joined.trim()
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxEdge && h <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }
}
