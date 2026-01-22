package com.example.aqualevel_.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.aqualevel_.utils.Constants

class DatabaseHandler(val context: Context) : SQLiteOpenHelper(
    context, Constants.DATABASE_NAME, null,
    Constants.DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase?) {

    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int
    ) {
       
    }
}