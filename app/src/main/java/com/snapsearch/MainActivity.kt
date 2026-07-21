package com.snapsearch

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapsearch.data.ImageEntity
import com.snapsearch.data.ImageEntity_
import com.snapsearch.data.ObjectBoxStore
import com.snapsearch.ml.ClipEngine
import com.snapsearch.ml.OcrEngine
import com.snapsearch.ui.theme.SnapSearchTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapSearchTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DebugScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Throwaway debug UI for Phase 1.1 + 1.2 verification.
 * Tests ObjectBox insert/read/nearest-neighbor search and OCR on a user-selected image.
 */
@Composable
fun DebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var objectBoxStatus by remember { mutableStateOf("Tap to test ObjectBox") }
    var ocrStatus by remember { mutableStateOf("Pick an image to test OCR") }
    var clipStatus by remember { mutableStateOf("Pick an image to test ClipEngine") }
    var clipTextStatus by remember { mutableStateOf("Tap to test ClipEngine text tower") }
    var crossModalStatus by remember { mutableStateOf("Pick a real photo to test cross-modal search") }
    var customCandidate by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            ocrStatus = "Running OCR on: ${uri.lastPathSegment}..."
            scope.launch {
                ocrStatus = runOcr(context, uri)
            }
        }
    }

    val clipImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            clipStatus = "Embedding: ${uri.lastPathSegment}..."
            scope.launch {
                clipStatus = runClipImageEmbed(context, uri)
            }
        }
    }

    val crossModalImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            crossModalStatus = "Embedding image + candidate strings..."
            scope.launch {
                crossModalStatus = runCrossModalSanityCheck(context, uri, customCandidate)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- ObjectBox test section ---
        Text("Phase 1.1 — ObjectBox", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(objectBoxStatus, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            objectBoxStatus = "Testing..."
            scope.launch { objectBoxStatus = testObjectBoxInsertAndSearch(context) }
        }) {
            Text("Test ObjectBox Insert + Nearest-Neighbor Search")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- OCR test section ---
        Text("Phase 1.2 — OCR (ML Kit)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(ocrStatus, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            imagePickerLauncher.launch("image/*")
        }) {
            Text("Pick Image for OCR")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- ClipEngine image tower test section ---
        Text("Phase 1.3 — ClipEngine (image tower)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(clipStatus, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            clipImagePickerLauncher.launch("image/*")
        }) {
            Text("Pick Image to Embed")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- ClipEngine text tower test section ---
        Text("Phase 1.4 — ClipEngine (text tower)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(clipTextStatus, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            clipTextStatus = "Embedding: \"$CLIP_TEXT_DEBUG_STRING\"..."
            scope.launch { clipTextStatus = runClipTextEmbed(context) }
        }) {
            Text("Embed Hardcoded Test String")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- Cross-modal sanity check section ---
        Text("Phase 1.5 — Cross-modal sanity check", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedTextField(
            value = customCandidate,
            onValueChange = { customCandidate = it },
            label = { Text("What is the photo actually of? (optional, adds a matching candidate)") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(crossModalStatus, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            crossModalImagePickerLauncher.launch("image/*")
        }) {
            Text("Pick Real Photo to Rank Against Candidates")
        }
    }
}

// ---- ObjectBox test (Phase 1.1, migrated from Room) ----

private suspend fun testObjectBoxInsertAndSearch(context: android.content.Context): String {
    return withContext(Dispatchers.IO) {
        try {
            val box = ObjectBoxStore.imageBox(context)
            val testUri = "content://test/dummy_image_001"

            // Distinct, non-trivial dummy vector so a nearest-neighbor match is meaningful,
            // not just "the only row in the table".
            val dummyEmbedding = FloatArray(ImageEntity.CLIP_EMBEDDING_DIMENSIONS.toInt()) { i -> i.toFloat() }

            val entity = ImageEntity(
                uri = testUri,
                indexedAtMillis = System.currentTimeMillis(),
                ocrText = "Hello ObjectBox test",
                captionSource = "clip_tags",
                captionText = "test debug",
                imageEmbedding = dummyEmbedding,
                textEmbedding = dummyEmbedding
            )
            box.put(entity)

            val count = box.count()
            val readBack = box.query(ImageEntity_.uri.equal(testUri)).build().findFirst()
                ?: return@withContext "❌ FAIL: inserted but could not read back by uri"

            val embeddingMatch = readBack.imageEmbedding?.contentEquals(dummyEmbedding) == true

            val nnQuery = box.query(ImageEntity_.imageEmbedding.nearestNeighbors(dummyEmbedding, 5)).build()
            val nnResults = nnQuery.findWithScores()
            val topMatchIsSelf = nnResults.firstOrNull()?.get()?.uri == testUri

            buildString {
                appendLine("✅ PASS — ObjectBox works!")
                appendLine("Count: $count")
                appendLine("URI: ${readBack.uri}")
                appendLine("Embedding round-trip: ${if (embeddingMatch) "✅" else "❌"}")
                appendLine("Nearest-neighbor self-match: ${if (topMatchIsSelf) "✅" else "❌"} (top score: ${nnResults.firstOrNull()?.score})")
            }
        } catch (e: Exception) {
            "❌ FAIL: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}

// ---- OCR test (Phase 1.2) ----

private suspend fun runOcr(context: android.content.Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        try {
            val startMs = System.currentTimeMillis()
            val text = OcrEngine.extractText(context, uri)
            val elapsed = System.currentTimeMillis() - startMs

            if (text.isBlank()) {
                "⚠️ No text detected (${elapsed}ms)\nURI: $uri"
            } else {
                buildString {
                    appendLine("✅ OCR completed in ${elapsed}ms")
                    appendLine("Characters: ${text.length}")
                    appendLine("--- Extracted text ---")
                    appendLine(text)
                }
            }
        } catch (e: Exception) {
            "❌ OCR FAIL: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}

// ---- ClipEngine image tower test (Phase 1.3) ----

private suspend fun runClipImageEmbed(context: android.content.Context, uri: Uri): String {
    return withContext(Dispatchers.Default) {
        try {
            val startMs = System.currentTimeMillis()
            val embedding = ClipEngine.embedImage(context, uri)
            val elapsed = System.currentTimeMillis() - startMs

            val hasNaN = embedding.any { it.isNaN() || it.isInfinite() }
            val norm = kotlin.math.sqrt(embedding.sumOf { (it.toDouble() * it.toDouble()) })

            buildString {
                appendLine(if (!hasNaN) "✅ Embedded in ${elapsed}ms" else "❌ FAIL: NaN/Inf in output")
                appendLine("Dimensions: ${embedding.size}")
                appendLine("L2 norm: $norm (expect ~1.0)")
                appendLine("First 5 values: ${embedding.take(5)}")
            }
        } catch (e: Exception) {
            "❌ ClipEngine FAIL: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}

// ---- ClipEngine text tower test (Phase 1.4) ----

private const val CLIP_TEXT_DEBUG_STRING = "a photo of a cat"

private suspend fun runClipTextEmbed(context: android.content.Context): String {
    return withContext(Dispatchers.Default) {
        try {
            val startMs = System.currentTimeMillis()
            val embedding = ClipEngine.embedText(context, CLIP_TEXT_DEBUG_STRING)
            val elapsed = System.currentTimeMillis() - startMs

            val hasNaN = embedding.any { it.isNaN() || it.isInfinite() }
            val norm = kotlin.math.sqrt(embedding.sumOf { (it.toDouble() * it.toDouble()) })

            buildString {
                appendLine(if (!hasNaN) "✅ Embedded \"$CLIP_TEXT_DEBUG_STRING\" in ${elapsed}ms" else "❌ FAIL: NaN/Inf in output")
                appendLine("Dimensions: ${embedding.size}")
                appendLine("L2 norm: $norm (expect ~1.0)")
                appendLine("First 5 values: ${embedding.take(5)}")
            }
        } catch (e: Exception) {
            "❌ ClipEngine text tower FAIL: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}

// ---- Cross-modal sanity check (Phase 1.5) ----

// Deliberately broad, mutually exclusive categories so a real photo should score clearly
// higher against its actual category than against the others — this is the real
// correctness gate for the shared image/text embedding space, not just "code runs".
// A tester-supplied custom candidate (the photo's real content) is folded in at run time,
// since a fixed generic label set can't cover every real photo in someone's library.
private val CROSS_MODAL_CANDIDATE_LABELS = listOf(
    "a handwritten note on paper",
    "a screenshot of a phone app",
    "a photo of a person",
    "a photo of nature or outdoors"
)

private suspend fun runCrossModalSanityCheck(context: android.content.Context, uri: Uri, customCandidate: String): String {
    return withContext(Dispatchers.Default) {
        try {
            val startMs = System.currentTimeMillis()
            val imageEmbedding = ClipEngine.embedImage(context, uri)
            val candidates = if (customCandidate.isBlank()) {
                CROSS_MODAL_CANDIDATE_LABELS
            } else {
                listOf(customCandidate.trim()) + CROSS_MODAL_CANDIDATE_LABELS
            }
            val scored = candidates.map { label ->
                val textEmbedding = ClipEngine.embedText(context, label)
                val similarity = dotProduct(imageEmbedding, textEmbedding)
                label to similarity
            }.sortedByDescending { it.second }
            val elapsed = System.currentTimeMillis() - startMs

            val topScore = scored.first().second
            val runnerUpScore = scored.getOrNull(1)?.second ?: Float.NEGATIVE_INFINITY
            val margin = topScore - runnerUpScore

            buildString {
                appendLine("Embedded image + ${candidates.size} candidates in ${elapsed}ms")
                appendLine("Top match: \"${scored.first().first}\" (margin over runner-up: ${"%.3f".format(margin)})")
                appendLine("--- Ranked cosine similarity ---")
                scored.forEach { (label, score) ->
                    appendLine("${"%.4f".format(score)}  \"$label\"")
                }
                appendLine()
                appendLine("Judge by eye: does the top match fit the picked photo?")
            }
        } catch (e: Exception) {
            "❌ Cross-modal check FAIL: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}

/** Dot product of two already-L2-normalized vectors = cosine similarity. */
private fun dotProduct(a: FloatArray, b: FloatArray): Float {
    var sum = 0f
    for (i in a.indices) sum += a[i] * b[i]
    return sum
}
