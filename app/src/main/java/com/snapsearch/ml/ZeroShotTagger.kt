package com.snapsearch.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Phase 2 zero-shot tagging: classifies an already-computed image embedding against
 * [TagVocabulary]'s curated labels using MobileCLIP's own text tower — no separate model,
 * see SnapSearch_Implementation_Plan.md §2 ("one embedding model does three jobs").
 *
 * Vocabulary prompts are embedded once (lazily, on first use) and cached in memory for
 * the process lifetime, so per-image tagging after that first call is just a handful of
 * dot products against an already-computed image vector — "near-free" relative to the
 * OCR + CLIP image-embed cost that actually dominates indexing time (Implementation
 * Plan §6), not a second per-label model inference.
 *
 * Tag selection is rank-based (top-K by score), not a fixed cosine-similarity cutoff:
 * Phase 1.5's cross-modal sanity check found MobileCLIP S0 int8 produces compressed,
 * uncalibrated similarity scores (0.03-0.09 range) rather than a clean absolute threshold.
 */
object ZeroShotTagger {

    const val TOP_K = 5

    private val mutex = Mutex()

    @Volatile
    private var cachedEmbeddings: List<Pair<String, FloatArray>>? = null

    /** Full ranked (tag, score) list against every vocabulary label, highest similarity first. */
    suspend fun classifyScored(context: Context, imageEmbedding: FloatArray): List<Pair<String, Float>> {
        val vocabulary = vocabularyEmbeddings(context)
        return withContext(Dispatchers.Default) {
            vocabulary
                .map { (tag, promptEmbedding) -> tag to dotProduct(imageEmbedding, promptEmbedding) }
                .sortedByDescending { it.second }
        }
    }

    /** The top [TOP_K] tags for an image embedding, ready to join into `captionText`. */
    suspend fun classify(context: Context, imageEmbedding: FloatArray): List<String> =
        classifyScored(context, imageEmbedding).take(TOP_K).map { it.first }

    fun vocabularySize(): Int = TagVocabulary.LABELS.size

    private suspend fun vocabularyEmbeddings(context: Context): List<Pair<String, FloatArray>> {
        cachedEmbeddings?.let { return it }
        return mutex.withLock {
            cachedEmbeddings?.let { return@withLock it }
            val embedded = TagVocabulary.LABELS.map { label ->
                label.tag to ClipEngine.embedText(context, label.prompt)
            }
            cachedEmbeddings = embedded
            embedded
        }
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}
