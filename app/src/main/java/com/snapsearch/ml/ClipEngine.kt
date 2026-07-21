package com.snapsearch.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * MobileCLIP S0 (int8, ONNX) image + text towers — asset-bundled model, source:
 * Xenova/mobileclip_s0 on Hugging Face (ONNX port of Apple's MobileCLIP-S0,
 * see assets/mobileclip/LICENSE.txt). Text tower tokenization is handled by
 * [ClipTokenizer] (CLIP byte-level BPE, ported from the same HF repo's
 * tokenizer.json).
 *
 * Preprocessing (resize shortest edge to 256, center crop 256x256, scale to
 * [0,1] with no mean/std normalization) and the 512-dim output size were
 * confirmed directly from the model's own preprocessor_config.json and ONNX
 * graph, not assumed — the vision model feeds pixel_values straight into a
 * quantize node with no normalization op ahead of it, and the projection
 * head has no L2-normalize op after it, so normalization is our job here.
 * The text model takes a single `input_ids` int64 input (no attention_mask)
 * and likewise has no in-graph L2-normalize step, confirmed from its ONNX graph.
 */
object ClipEngine {

    private const val VISION_MODEL_ASSET = "mobileclip/vision_model_int8.onnx"
    private const val TEXT_MODEL_ASSET = "mobileclip/text_model_int8.onnx"
    private const val IMAGE_SIZE = 256

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    @Volatile
    private var loadedVisionSession: OrtSession? = null

    @Volatile
    private var loadedTextSession: OrtSession? = null

    private fun loadVisionSession(context: Context): OrtSession {
        loadedVisionSession?.let { return it }
        synchronized(this) {
            loadedVisionSession?.let { return it }
            val modelBytes = context.assets.open(VISION_MODEL_ASSET).use { it.readBytes() }
            val options = OrtSession.SessionOptions()
            options.addXnnpack(emptyMap())
            val session = env.createSession(modelBytes, options)
            loadedVisionSession = session
            return session
        }
    }

    private fun loadTextSession(context: Context): OrtSession {
        loadedTextSession?.let { return it }
        synchronized(this) {
            loadedTextSession?.let { return it }
            val modelBytes = context.assets.open(TEXT_MODEL_ASSET).use { it.readBytes() }
            val options = OrtSession.SessionOptions()
            options.addXnnpack(emptyMap())
            val session = env.createSession(modelBytes, options)
            loadedTextSession = session
            return session
        }
    }

    /**
     * Embed one image into MobileCLIP's 512-dim joint embedding space.
     * Returns an L2-normalized FloatArray, ready to store/compare directly.
     */
    suspend fun embedImage(context: Context, uri: Uri): FloatArray = withContext(Dispatchers.Default) {
        val bitmap = loadOrientedBitmap(context, uri)
        val cropped = resizeAndCenterCrop(bitmap, IMAGE_SIZE)
        val input = bitmapToChwFloatArray(cropped)

        val session = loadVisionSession(context)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())).use { tensor ->
            session.run(mapOf("pixel_values" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = (result[0].value as Array<FloatArray>)[0]
                l2Normalize(output)
            }
        }
    }

    /**
     * Embed one text string into MobileCLIP's 512-dim joint embedding space —
     * used for search queries, OCR text, and zero-shot tag labels alike.
     * Returns an L2-normalized FloatArray, ready to store/compare directly.
     */
    suspend fun embedText(context: Context, text: String): FloatArray = withContext(Dispatchers.Default) {
        val inputIds = ClipTokenizer.tokenize(context, text)
        val session = loadTextSession(context)
        OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, inputIds.size.toLong())).use { tensor ->
            session.run(mapOf("input_ids" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = (result[0].value as Array<FloatArray>)[0]
                l2Normalize(output)
            }
        }
    }

    private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalArgumentException("Could not decode image: $uri")

        val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        if (rotationDegrees == 0f) return original
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    }

    /** Resize shortest edge to [targetSize], then center-crop to targetSize x targetSize. */
    private fun resizeAndCenterCrop(bitmap: Bitmap, targetSize: Int): Bitmap {
        val scale = targetSize.toFloat() / minOf(bitmap.width, bitmap.height)
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(targetSize)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(targetSize)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val xOffset = (scaledWidth - targetSize) / 2
        val yOffset = (scaledHeight - targetSize) / 2
        return Bitmap.createBitmap(scaled, xOffset, yOffset, targetSize, targetSize)
    }

    /** RGB, CHW layout, scaled to [0,1] — matches preprocessor_config.json (do_normalize=false). */
    private fun bitmapToChwFloatArray(bitmap: Bitmap): FloatArray {
        val size = IMAGE_SIZE
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        val channelSize = size * size
        val chw = FloatArray(3 * channelSize)
        for (i in pixels.indices) {
            val p = pixels[i]
            chw[i] = ((p shr 16) and 0xFF) / 255f
            chw[channelSize + i] = ((p shr 8) and 0xFF) / 255f
            chw[2 * channelSize + i] = (p and 0xFF) / 255f
        }
        return chw
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) sumSquares += v.toDouble() * v.toDouble()
        val norm = sqrt(sumSquares).toFloat()
        if (norm == 0f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }
}
