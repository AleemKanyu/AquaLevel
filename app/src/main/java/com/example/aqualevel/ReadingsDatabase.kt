package com.example.aqualevel

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(entities=arrayOf(Readings::class),version=1,exportSchema=false)
abstract class ReadingsDatabase: RoomDatabase(){
    abstract  fun getReadingsDao(): ReadingsDao

    companion object{
        private var INSTANCE: ReadingsDatabase?=null

        fun getInstance(context: Context): ReadingsDatabase{
            //if the instance is null then return it
            //if its not null then create the database
        return INSTANCE?:synchronized(this){
            val instance = Room.databaseBuilder(context.applicationContext,
                ReadingsDatabase::class.java,
                "Readings"
            ).build()
            INSTANCE=instance
            instance
        }
        }
    }
}