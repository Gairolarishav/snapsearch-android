package com.snapsearch.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.IndexType
import io.objectbox.annotation.VectorDistanceType

/**
 * Core ObjectBox entity representing an indexed image.
 *
 * Each row stores:
 * - The image's content URI (unique lookup key — `id` is ObjectBox's required numeric primary key)
 * - OCR-extracted text
 * - Caption text (zero-shot tags in Tier 1, VLM sentence in Tier 2)
 * - CLIP image embedding, HNSW-indexed for fast approximate nearest-neighbor search
 * - Text embedding of (captionText + ocrText), HNSW-indexed the same way
 *
 * FloatArray properties are stored natively by ObjectBox — no manual byte conversion needed.
 */
@Entity
data class ImageEntity(
    @Id var id: Long = 0,
    @Index(type = IndexType.HASH) var uri: String = "",
    var indexedAtMillis: Long = 0,
    var ocrText: String = "",
    var captionSource: String = "",   // "clip_tags" | "vlm"
    var captionText: String = "",     // space-joined tags (Phase 2) or VLM sentence (Tier 2)
    @HnswIndex(dimensions = CLIP_EMBEDDING_DIMENSIONS, distanceType = VectorDistanceType.COSINE)
    var imageEmbedding: FloatArray? = null,
    @HnswIndex(dimensions = CLIP_EMBEDDING_DIMENSIONS, distanceType = VectorDistanceType.COSINE)
    var textEmbedding: FloatArray? = null
) {
    companion object {
        // Confirmed against the real MobileCLIP S0 (Xenova/mobileclip_s0) ONNX graph's
        // image_embeds/text_embeds output shape during Phase 1.3 — not assumed.
        // Long, not Int — @HnswIndex's `dimensions` parameter requires it.
        const val CLIP_EMBEDDING_DIMENSIONS: Long = 512L
    }
}
