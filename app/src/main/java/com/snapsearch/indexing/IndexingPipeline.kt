package com.snapsearch.indexing

import android.content.Context
import android.net.Uri
import com.snapsearch.data.ImageEntity
import com.snapsearch.data.ImageEntity_
import com.snapsearch.data.ObjectBoxStore
import com.snapsearch.ml.ClipEngine
import com.snapsearch.ml.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-image indexing steps 2-6 of the data pipeline (SnapSearch_Implementation_Plan.md §3):
 * OCR -> embed image -> embed text -> upsert into ObjectBox. Zero-shot tagging (step 4,
 * Phase 2) doesn't exist yet, so the text embedding is OCR text alone for now — once tagging
 * lands, its output just gets concatenated into the same `embedText` call here, no schema
 * or fusion change needed.
 *
 * One call = one image. A `WorkManager` `CoroutineWorker` driving this over a whole gallery
 * is Phase 3 — for 1.6 callers (the debug UI) just loop over a small picked set directly.
 */
object IndexingPipeline {

    suspend fun indexImage(context: Context, uri: Uri): ImageEntity {
        val ocrText = OcrEngine.extractText(context, uri)
        val imageEmbedding = ClipEngine.embedImage(context, uri)
        val textEmbedding = ClipEngine.embedText(context, ocrText)

        val uriString = uri.toString()
        return withContext(Dispatchers.IO) {
            val box = ObjectBoxStore.imageBox(context)
            val existing = box.query(ImageEntity_.uri.equal(uriString)).build().findFirst()

            val entity = (existing ?: ImageEntity()).copy(
                uri = uriString,
                indexedAtMillis = System.currentTimeMillis(),
                ocrText = ocrText,
                captionSource = "clip_tags",
                imageEmbedding = imageEmbedding,
                textEmbedding = textEmbedding
            )
            box.put(entity)
            entity
        }
    }
}
