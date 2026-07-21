package com.snapsearch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ImageEntity::class], version = 1, exportSchema = false)
@TypeConverters(FloatArrayConverter::class)
abstract class SnapSearchDatabase : RoomDatabase() {

    abstract fun imageDao(): ImageDao

    companion object {
        @Volatile
        private var INSTANCE: SnapSearchDatabase? = null

        fun get(context: Context): SnapSearchDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SnapSearchDatabase::class.java,
                    "snapsearch.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
