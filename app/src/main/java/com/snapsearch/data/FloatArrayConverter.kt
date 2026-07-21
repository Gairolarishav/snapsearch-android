package com.snapsearch.data

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Room TypeConverter for FloatArray <-> ByteArray.
 *
 * Stores floats in little-endian byte order (matches ONNX Runtime's
 * native output on ARM64, avoiding unnecessary byte swaps).
 */
class FloatArrayConverter {

    @TypeConverter
    fun fromFloatArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }
}
