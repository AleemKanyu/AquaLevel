package com.example.aqualevel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    private lateinit var editUserName: EditText
    private lateinit var editFullDist: EditText
    private lateinit var editEmptyDist: EditText
    private lateinit var editVolume: EditText
    private lateinit var saveButton: Button
    private lateinit var backButton: ImageView
    
    // Navbar components
    private lateinit var homeButton: FrameLayout
    private lateinit var analyticsButton: FrameLayout
    private lateinit var settingsButton: FrameLayout
    private lateinit var buttonCheck: ImageView

    private lateinit var themeToggle: FrameLayout
    private lateinit var themeIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize Settings Views
        editUserName = findViewById(R.id.editUserName)
        editFullDist = findViewById(R.id.editFullDist)
        editEmptyDist = findViewById(R.id.editEmptyDist)
        editVolume = findViewById(R.id.editVolume)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)

        // Initialize Navbar Views
        homeButton = findViewById(R.id.homeButton)
        analyticsButton = findViewById(R.id.analyticsButton)
        settingsButton = findViewById(R.id.settingsButton)
        buttonCheck = findViewById(R.id.buttonCheck)

        // Theme Toggle Setup
        themeToggle = findViewById(R.id.themeToggle)
        themeIcon = findViewById(R.id.themeIcon)
        updateThemeIcon()

        themeToggle.setOnClickListener {
            val isDarkNow = sharedPref.getBoolean("is_dark_mode", false)
            val newMode = !isDarkNow
            sharedPref.edit().putBoolean("is_dark_mode", newMode).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        loadSettings()

        saveButton.setOnClickListener {
            saveSettings()
        }

        backButton.setOnClickListener {
            finish()
        }

        // Navbar Click Listeners
        homeButton.setOnClickListener {
            applyClickAnimation(it) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
        }

        analyticsButton.setOnClickListener {
            applyClickAnimation(it) {
                startActivity(Intent(this, Analytics::class.java))
                finish()
            }
        }

        settingsButton.setOnClickListener {
            applyClickAnimation(it) {
                Toast.makeText(this, "Already in Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateThemeIcon() {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("is_dark_mode", false)
        themeIcon.setImageResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_moon)
    }

    private fun applyClickAnimation(view: View, onAnimationEnd: () -> Unit) {
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction { onAnimationEnd() }
                    .start()
            }
            .start()
    }

    private fun loadSettings() {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val userName = sharedPref.getString("user_name", "")
        val fullDist = sharedPref.getFloat("full_distance", 20.0f)
        val emptyDist = sharedPref.getFloat("empty_distance", 130.0f)
        val volume = sharedPref.getInt("tank_volume", 2000)

        editUserName.setText(userName)
        editFullDist.setText(fullDist.toString())
        editEmptyDist.setText(emptyDist.toString())
        editVolume.setText(volume.toString())
    }

    private fun saveSettings() {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE).edit()

        val userName = editUserName.text.toString()
        val fullDist = editFullDist.text.toString().toFloatOrNull()
        val emptyDist = editEmptyDist.text.toString().toFloatOrNull()
        val volume = editVolume.text.toString().toIntOrNull()

        if (userName.isBlank()) {
            Toast.makeText(this, "User name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (fullDist == null || emptyDist == null || volume == null) {
            Toast.makeText(this, "Please enter valid numbers for calibration", Toast.LENGTH_SHORT).show()
            return
        }

        sharedPref.putString("user_name", userName)
        sharedPref.putFloat("full_distance", fullDist)
        sharedPref.putFloat("empty_distance", emptyDist)
        sharedPref.putInt("tank_volume", volume)

        sharedPref.apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
