package com.snapsearch.ml

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Wraps ML Kit Text Recognition v2 (bundled Latin model).
 *
 * The recognizer is created once and reused — ML Kit handles its own
 * lifecycle and caching internally.
 */
object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Extract all text from the image at the given content URI.
     * Returns the raw concatenated text (empty string if no text detected).
     */
    suspend fun extractText(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        return result.text
    }
}
