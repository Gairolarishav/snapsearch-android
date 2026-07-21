package com.snapsearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.snapsearch.data.ImageEntity
import com.snapsearch.data.SnapSearchDatabase
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
                    DebugRoomTest(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Throwaway debug UI for Phase 1.1 — Room schema verification.
 * Inserts a dummy row, reads it back, and shows pass/fail.
 */
@Composable
fun DebugRoomTest(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Tap the button to test Room DB") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = status)

        Button(
            onClick = {
                status = "Testing..."
                scope.launch {
                    status = testRoomInsertAndRead(context)
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Test Room Insert + Read")
        }
    }
}

private suspend fun testRoomInsertAndRead(context: android.content.Context): String {
    return withContext(Dispatchers.IO) {
        try {
            val db = SnapSearchDatabase.get(context)
            val dao = db.imageDao()

            // Create a dummy entity with fake embeddings
            val dummyEmbedding = FloatArray(4) { it.toFloat() } // [0.0, 1.0, 2.0, 3.0]
            val dummyBytes = com.snapsearch.data.FloatArrayConverter().fromFloatArray(dummyEmbedding)

            val entity = ImageEntity(
                uri = "content://test/dummy_image_001",
                indexedAtMillis = System.currentTimeMillis(),
                ocrText = "Hello Room test",
                captionSource = "clip_tags",
                captionText = "test debug",
                imageEmbedding = dummyBytes,
                textEmbedding = dummyBytes
            )

            // Insert
            dao.upsert(entity)

            // Read back
            val count = dao.count()
            val all = dao.getAll()
            val readBack = all.find { it.uri == "content://test/dummy_image_001" }

            if (readBack == null) {
                return@withContext "❌ FAIL: inserted but could not read back"
            }

            // Verify embedding round-trip
            val converter = com.snapsearch.data.FloatArrayConverter()
            val recoveredEmbedding = converter.toFloatArray(readBack.imageEmbedding)
            val embeddingMatch = recoveredEmbedding.contentEquals(dummyEmbedding)

            buildString {
                appendLine("✅ PASS — Room works!")
                appendLine("Count: $count")
                appendLine("URI: ${readBack.uri}")
                appendLine("OCR text: ${readBack.ocrText}")
                appendLine("Caption: ${readBack.captionText}")
                appendLine("Embedding round-trip: ${if (embeddingMatch) "✅ match" else "❌ mismatch"}")
                appendLine("Embedding values: ${recoveredEmbedding.toList()}")
            }
        } catch (e: Exception) {
            "❌ FAIL: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
