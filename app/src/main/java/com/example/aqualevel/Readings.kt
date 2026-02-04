package com.example.aqualevel

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "Readings")
data class Readings(
    val timestamp: Int,
    val level:Int
)
{@PrimaryKey(autoGenerate = true)
    val id=0
}