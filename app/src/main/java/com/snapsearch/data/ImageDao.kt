package com.snapsearch.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ImageDao {
    @Query("SELECT * FROM images")
    suspend fun getAll(): List<ImageEntity>

    @Upsert
    suspend fun upsert(entity: ImageEntity)

    @Query("SELECT COUNT(*) FROM images")
    suspend fun count(): Int
}
