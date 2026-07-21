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
import com.snapsearch.data.FloatArrayConverter
import com.snapsearch.data.ImageEntity
import com.snapsearch.data.SnapSearchDatabase
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
 * Tests Room insert/read and OCR on a user-selected image.
 */
@Composable
fun DebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var roomStatus by remember { mutableStateOf("Tap to test Room DB") }
    var ocrStatus by remember { mutableStateOf("Pick an image to test OCR") }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Room test section ---
        Text("Phase 1.1 — Room", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(roomStatus, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            roomStatus = "Testing..."
            scope.launch { roomStatus = testRoomInsertAndRead(context) }
        }) {
            Text("Test Room Insert + Read")
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
    }
}

// ---- Room test (from Phase 1.1) ----

private suspend fun testRoomInsertAndRead(context: android.content.Context): String {
    return withContext(Dispatchers.IO) {
        try {
            val db = SnapSearchDatabase.get(context)
            val dao = db.imageDao()

            val dummyEmbedding = FloatArray(4) { it.toFloat() }
            val dummyBytes = FloatArrayConverter().fromFloatArray(dummyEmbedding)

            val entity = ImageEntity(
                uri = "content://test/dummy_image_001",
                indexedAtMillis = System.currentTimeMillis(),
                ocrText = "Hello Room test",
                captionSource = "clip_tags",
                captionText = "test debug",
                imageEmbedding = dummyBytes,
                textEmbedding = dummyBytes
            )

            dao.upsert(entity)
            val count = dao.count()
            val readBack = dao.getAll().find { it.uri == "content://test/dummy_image_001" }

            if (readBack == null) return@withContext "❌ FAIL: inserted but could not read back"

            val recoveredEmbedding = FloatArrayConverter().toFloatArray(readBack.imageEmbedding)
            val embeddingMatch = recoveredEmbedding.contentEquals(dummyEmbedding)

            buildString {
                appendLine("✅ PASS — Room works!")
                appendLine("Count: $count")
                appendLine("URI: ${readBack.uri}")
                appendLine("Embedding round-trip: ${if (embeddingMatch) "✅" else "❌"}")
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
