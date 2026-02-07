package com.example.aqualevel

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(entities = [Readings::class], version = 1, exportSchema = false)
abstract class ReadingsDatabase : RoomDatabase() {
    abstract fun getReadingsDao(): ReadingsDao

    companion object {
        @Volatile private var INSTANCE: ReadingsDatabase? = null

        fun getInstance(context: Context): ReadingsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReadingsDatabase::class.java,
                    "readings_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
