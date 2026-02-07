package com.example.aqualevel

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var editUserName: EditText
    private lateinit var editFullDist: EditText
    private lateinit var editEmptyDist: EditText
    private lateinit var editVolume: EditText
    private lateinit var saveButton: Button
    private lateinit var backButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        editUserName = findViewById(R.id.editUserName)
        editFullDist = findViewById(R.id.editFullDist)
        editEmptyDist = findViewById(R.id.editEmptyDist)
        editVolume = findViewById(R.id.editVolume)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)

        loadSettings()

        saveButton.setOnClickListener {
            saveSettings()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {
        val sharedPref = getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val userName = sharedPref.getString("user_name", "")
        val fullDist = sharedPref.getFloat("full_distance", 20.0f)
        val emptyDist = sharedPref.getFloat("empty_distance", 130.0f)
        val volume = sharedPref.getInt("tank_volume", 1000)

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
