package com.example.aqualevel

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class AquaLevelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
