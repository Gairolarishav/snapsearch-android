package com.snapsearch.search

import android.content.Context
import com.snapsearch.data.ImageEntity
import com.snapsearch.data.ImageEntity_
import com.snapsearch.data.ObjectBoxStore
import com.snapsearch.ml.ClipEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One ranked search result: a stored image plus its similarity on each signal it was
 * actually a candidate for. `imageSimilarity`/`textSimilarity` are null when the row
 * didn't appear in that signal's top-K ANN candidate list at all — see [ScoreFusion].
 */
data class SearchResult(
    val entity: ImageEntity,
    val fusedScore: Float,
    val imageSimilarity: Float?,
    val textSimilarity: Float?
)

/**
 * Fuses ObjectBox's two independent HNSW `nearestNeighbors()` candidate lists (image-embedding
 * index, text-embedding index) into one ranked list — SnapSearch_Implementation_Plan.md §1.
 * There is no full-table scan: each side only ever returns its own top-K, and fusion takes the
 * *union* of both lists, so a candidate present in only one list contributes just that side's
 * score (weighted), not a penalized/zeroed-out combined score.
 *
 * ObjectBox reports HNSW query scores as COSINE *distance* (0 = identical, larger = more
 * different — confirmed during Phase 1.1's self-match verification, top score 0.0). Converted
 * to similarity (1 - distance) here so a higher `fusedScore` always means a better match.
 */
object ScoreFusion {

    suspend fun search(
        context: Context,
        query: String,
        candidatesPerSignal: Int = 50,
        imageWeight: Float = 0.5f,
        textWeight: Float = 0.5f
    ): List<SearchResult> {
        val queryEmbedding = ClipEngine.embedText(context, query)

        val (imageCandidates, textCandidates) = withContext(Dispatchers.IO) {
            val box = ObjectBoxStore.imageBox(context)
            val images = box.query(ImageEntity_.imageEmbedding.nearestNeighbors(queryEmbedding, candidatesPerSignal))
                .build().findWithScores()
            val texts = box.query(ImageEntity_.textEmbedding.nearestNeighbors(queryEmbedding, candidatesPerSignal))
                .build().findWithScores()
            images to texts
        }

        val accumulators = LinkedHashMap<Long, ResultAccumulator>()
        imageCandidates.forEach { withScore ->
            val entity = withScore.get()
            accumulators[entity.id] = ResultAccumulator(entity, imageSimilarity = 1f - withScore.score.toFloat())
        }
        textCandidates.forEach { withScore ->
            val entity = withScore.get()
            val similarity = 1f - withScore.score.toFloat()
            val existing = accumulators[entity.id]
            accumulators[entity.id] = existing?.copy(textSimilarity = similarity)
                ?: ResultAccumulator(entity, textSimilarity = similarity)
        }

        return accumulators.values.map { acc ->
            val fused = (acc.imageSimilarity ?: 0f) * imageWeight + (acc.textSimilarity ?: 0f) * textWeight
            SearchResult(acc.entity, fused, acc.imageSimilarity, acc.textSimilarity)
        }.sortedByDescending { it.fusedScore }
    }

    private data class ResultAccumulator(
        val entity: ImageEntity,
        val imageSimilarity: Float? = null,
        val textSimilarity: Float? = null
    )
}
