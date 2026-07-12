package com.aqualevel.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqualevel.app.WaveView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Fragment for managing user profile, sensor calibration, and data import/export.
 */
class SettingsFragment : Fragment() {

    private lateinit var editUserName: com.google.android.material.textfield.TextInputEditText
    private lateinit var editFullDist: com.google.android.material.textfield.TextInputEditText
    private lateinit var editEmptyDist: com.google.android.material.textfield.TextInputEditText
    private lateinit var editVolume: com.google.android.material.textfield.TextInputEditText
    private lateinit var switchVibration: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var switchNotifications: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var switchGyroWater: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var switchDisableAnimation: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var switchPerformanceMode: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var radioGroupUnits: android.widget.RadioGroup
    private lateinit var seekbarThreshold: android.widget.SeekBar
    private lateinit var textThresholdValue: android.widget.TextView
    private lateinit var switchWidgetPercentage: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var switchWidgetVolume: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var switchWidgetTimestamp: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var radioGroupWidgetTheme: android.widget.RadioGroup
    private lateinit var saveButton: Button
    private lateinit var exportDataButton: Button 
    private lateinit var importDataButton: Button
    private lateinit var scanDeviceButton: Button
    private lateinit var openWebDashboardButton: Button

    private lateinit var readingsRepository: ReadingsRepository

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    exportDataToUri(uri)
                }
            }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    importDataFromUri(uri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val readingsDao = ReadingsDatabase.getInstance(requireContext()).getReadingsDao()
        readingsRepository = ReadingsRepository(readingsDao)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        editUserName = view.findViewById(R.id.editUserName)
        editFullDist = view.findViewById(R.id.editFullDist)
        editEmptyDist = view.findViewById(R.id.editEmptyDist)
        editVolume = view.findViewById(R.id.editVolume)
        switchVibration = view.findViewById(R.id.switchVibration)
        switchNotifications = view.findViewById(R.id.switchNotifications)
        switchGyroWater = view.findViewById(R.id.switchGyroWater)
        switchDisableAnimation = view.findViewById(R.id.switchDisableAnimation)
        switchPerformanceMode = view.findViewById(R.id.switchPerformanceMode)
        
        switchWidgetPercentage = view.findViewById(R.id.switchWidgetPercentage)
        switchWidgetVolume = view.findViewById(R.id.switchWidgetVolume)
        switchWidgetTimestamp = view.findViewById(R.id.switchWidgetTimestamp)
        
        radioGroupWidgetTheme = view.findViewById(R.id.radioGroupWidgetTheme)
        
        radioGroupUnits = view.findViewById(R.id.radioGroupUnits)
        seekbarThreshold = view.findViewById(R.id.seekbarThreshold)
        textThresholdValue = view.findViewById(R.id.textThresholdValue)
        saveButton      = view.findViewById(R.id.saveButton)
        exportDataButton = view.findViewById(R.id.exportDataButton) 
        importDataButton = view.findViewById(R.id.importDataButton)
        scanDeviceButton = view.findViewById(R.id.scanDeviceButton)
        openWebDashboardButton = view.findViewById(R.id.openWebDashboardButton)
        
        val profileWaveView = view.findViewById<WaveView>(R.id.profileWaveView)
        view.postDelayed({
            profileWaveView.setWaterLevel(50) 
        }, 500)

        seekbarThreshold.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                textThresholdValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        loadSettings()

        saveButton.setOnClickListener {
            performHapticFeedbackCommon(it)
            applyClickAnimation(it) { saveSettings() }
        }

        scanDeviceButton.setOnClickListener {
            performHapticFeedbackCommon(it)
            applyClickAnimation(it) {
                val intent = Intent(requireContext(), PairDeviceActivity::class.java)
                    .putExtra(PairDeviceActivity.EXTRA_FROM_SETTINGS, true)
                startActivity(intent)
            }
        }

        openWebDashboardButton.setOnClickListener {
            performHapticFeedbackCommon(it)
            applyClickAnimation(it) {
                val url = "https://aleemkanyu.github.io/AquaLevel/"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
        }

        exportDataButton.setOnClickListener { 
            performHapticFeedbackCommon(it)
            applyClickAnimation(it) {
                exportData()
            }
        }

        importDataButton.setOnClickListener { 
            performHapticFeedbackCommon(it)
            applyClickAnimation(it) {
                importData()
            }
        }
    }

    private fun loadSettings() {
        val sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        val userName = sharedPref.getString("user_name", "")
        val fullDist = sharedPref.getFloat("full_distance", 20.0f)
        val emptyDist = sharedPref.getFloat("empty_distance", 130.0f)
        val volume = sharedPref.getInt("tank_volume", 2000)
        val vibrationEnabled = sharedPref.getBoolean("vibration_enabled", true)
        val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
        val unit = sharedPref.getString("volume_unit", "L")
        val threshold = sharedPref.getInt("alert_threshold", 30)

        editUserName.setText(userName)
        editFullDist.setText(fullDist.toString())
        editEmptyDist.setText(emptyDist.toString())
        editVolume.setText(volume.toString())
        switchVibration.isChecked = vibrationEnabled
        switchNotifications.isChecked = notificationsEnabled
        val gyroWaterEnabled = sharedPref.getBoolean("gyro_water_enabled", false)
        switchGyroWater.isChecked = gyroWaterEnabled
        
        switchDisableAnimation.isChecked = sharedPref.getBoolean("disable_animation", false)
        switchPerformanceMode.isChecked = sharedPref.getBoolean("performance_mode", false)

        switchWidgetPercentage.isChecked = sharedPref.getBoolean("widget_show_percentage", true)
        switchWidgetVolume.isChecked = sharedPref.getBoolean("widget_show_volume", true)
        switchWidgetTimestamp.isChecked = sharedPref.getBoolean("widget_show_timestamp", true)
        
        val widgetTheme = sharedPref.getString("widget_theme", "dark")
        if (widgetTheme == "light") {
            view?.findViewById<android.widget.RadioButton>(R.id.radioWidgetLight)?.isChecked = true
        } else {
            view?.findViewById<android.widget.RadioButton>(R.id.radioWidgetDark)?.isChecked = true
        }

        if (unit == "gal") {
            view?.findViewById<android.widget.RadioButton>(R.id.radioGallons)?.isChecked = true
        } else {
            view?.findViewById<android.widget.RadioButton>(R.id.radioLiters)?.isChecked = true
        }
        seekbarThreshold.progress = threshold
        textThresholdValue.text = "$threshold%"
    }

    private fun saveSettings() {
        val sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE).edit()

        val userName = editUserName.text.toString()
        val fullDist = editFullDist.text.toString().toFloatOrNull()
        val emptyDist = editEmptyDist.text.toString().toFloatOrNull()
        val volume = editVolume.text.toString().toIntOrNull()
        
        if (userName.isBlank()) {
            Toast.makeText(requireContext(), "User name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (fullDist == null || emptyDist == null || volume == null) {
            Toast.makeText(requireContext(), "Please enter valid numbers for calibration", Toast.LENGTH_SHORT).show()
            return
        }

        sharedPref.putString("user_name", userName)
        sharedPref.putFloat("full_distance", fullDist)
        sharedPref.putFloat("empty_distance", emptyDist)
        sharedPref.putInt("tank_volume", volume)
        sharedPref.putBoolean("vibration_enabled", switchVibration.isChecked)
        sharedPref.putBoolean("notifications_enabled", switchNotifications.isChecked)
        sharedPref.putBoolean("gyro_water_enabled", switchGyroWater.isChecked)
        sharedPref.putBoolean("disable_animation", switchDisableAnimation.isChecked)
        sharedPref.putBoolean("performance_mode", switchPerformanceMode.isChecked)
        
        sharedPref.putBoolean("widget_show_percentage", switchWidgetPercentage.isChecked)
        sharedPref.putBoolean("widget_show_volume", switchWidgetVolume.isChecked)
        sharedPref.putBoolean("widget_show_timestamp", switchWidgetTimestamp.isChecked)
        
        val widgetTheme = if (radioGroupWidgetTheme.checkedRadioButtonId == R.id.radioWidgetLight) "light" else "dark"
        sharedPref.putString("widget_theme", widgetTheme)
        
        val unit = if (radioGroupUnits.checkedRadioButtonId == R.id.radioGallons) "gal" else "L"
        sharedPref.putString("volume_unit", unit)
        sharedPref.putInt("alert_threshold", seekbarThreshold.progress)
        sharedPref.apply()

        Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
        
        val intent = android.content.Intent(requireContext(), WaterLevelWidget::class.java)
        intent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = android.appwidget.AppWidgetManager.getInstance(requireContext())
            .getAppWidgetIds(android.content.ComponentName(requireContext(), WaterLevelWidget::class.java))
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        requireContext().sendBroadcast(intent)
    }

    private fun exportData() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "aqualevel_readings_$timeStamp.csv"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        createDocumentLauncher.launch(intent)
    }

    /**
     * Exports all readings from the database to a CSV file.
     * Uses a synchronous fetch to ensure all data is captured reliably.
     */
    private suspend fun exportDataToUri(uri: Uri) {
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "Exporting data...", Toast.LENGTH_SHORT).show()
        }
        try {
            // Using synchronous fetch for reliable background thread access
            val allReadings = readingsRepository.getAllReadingsSync()
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write("id,level,timestamp\n") 
                    allReadings.forEach { reading ->
                        writer.write("${reading.id},${reading.level},${reading.timestamp}\n")
                    }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Data exported successfully!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error exporting data: ${e.message}", Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
    }

    private fun importData() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            val mimeTypes = arrayOf(
                "text/csv",
                "text/comma-separated-values",
                "application/csv",
                "text/plain"
            )
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        openDocumentLauncher.launch(intent)
    }

    /**
     * Imports readings from a CSV file into the database.
     * Uses bulk insertion and ignores original IDs to prevent primary key conflicts, 
     * ensuring all historical data is merged correctly.
     */
    private suspend fun importDataFromUri(uri: Uri) {
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "Importing data...", Toast.LENGTH_SHORT).show()
        }
        try {
            val importedReadings = mutableListOf<Readings>()
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(inputStream.reader()).use { reader ->
                    var line = reader.readLine() // Read header
                    if (line == null || !line.contains("level") || !line.contains("timestamp")) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Invalid CSV format. Header 'level,timestamp' expected.", Toast.LENGTH_LONG).show()
                        }
                        return@use
                    }

                    while (reader.readLine().also { line = it } != null) {
                        val parts = line?.split(",")
                        if (parts != null && parts.size >= 3) {
                            try {
                                val level = parts[1].toDouble()
                                val timestamp = parts[2].toLong()
                                // Create new Readings object; Room will auto-generate new unique IDs.
                                // This is safer for merging data from different devices.
                                importedReadings.add(Readings(level = level, timestamp = timestamp))
                            } catch (e: Exception) {
                                Log.e("SettingsFragment", "Skipping malformed row: $line", e)
                            }
                        }
                    }
                }
            }
            
            if (importedReadings.isNotEmpty()) {
                // Perform a single bulk insertion for maximum efficiency and UI consistency
                readingsRepository.insertAll(importedReadings)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Successfully imported ${importedReadings.size} readings!", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "No valid data found in CSV.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Error importing data: ${e.message}", Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
    }

    private fun performHapticFeedbackCommon(view: View) {
        val sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
        if (!sharedPref.getBoolean("vibration_enabled", true)) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun applyClickAnimation(view: View, onAnimationEnd: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction { onAnimationEnd() }.start()
        }.start()
    }
}
