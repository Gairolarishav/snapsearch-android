package com.snapsearch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Core Room entity representing an indexed image.
 *
 * Each row stores:
 * - The image's content URI (used as the primary key — unique per image)
 * - OCR-extracted text
 * - Caption text (zero-shot tags in Tier 1, VLM sentence in Tier 2)
 * - CLIP image embedding (FloatArray stored as ByteArray)
 * - Text embedding of (captionText + ocrText)
 */
@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val uri: String,
    val indexedAtMillis: Long,
    val ocrText: String,
    val captionSource: String,   // "clip_tags" | "vlm"
    val captionText: String,     // space-joined tags (Phase 2) or VLM sentence (Tier 2)
    val imageEmbedding: ByteArray,  // FloatArray -> ByteArray via FloatArrayConverter
    val textEmbedding: ByteArray    // CLIP-text-tower vector
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageEntity) return false
        return uri == other.uri
    }

    override fun hashCode(): Int = uri.hashCode()
}
