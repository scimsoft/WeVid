package com.scimsoft.wevid.data

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.io.File

/** Shared thumbnail + duration extraction for chat messages and feed posts. */
object VideoMetadata {
    fun extractThumbAndDuration(videoFile: File): Pair<ByteArray?, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(0)
            val bytes = frame?.let { bitmap ->
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    out.toByteArray()
                }
            }
            bytes to durationMs
        } catch (_: Exception) {
            null to 0L
        } finally {
            retriever.release()
        }
    }
}
