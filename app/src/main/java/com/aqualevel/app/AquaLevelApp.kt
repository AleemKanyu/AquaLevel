package com.aqualevel.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class AquaLevelApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyBDy5mkeoGeUlCK5dyx3g1cKGhYb9qTh5Q")
                .setApplicationId("1:1058099888216:android:98471527e6b3304a05ca2d")
                .setProjectId("aqualevel-383e2")
                .setGcmSenderId("1058099888216")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
        
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
