package com.example.aqualevel

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var editUserName: EditText
    private lateinit var editFullDist: EditText
    private lateinit var editEmptyDist: EditText
    private lateinit var editVolume: EditText
    private lateinit var saveButton: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        editUserName = view.findViewById(R.id.editUserName)
        editFullDist = view.findViewById(R.id.editFullDist)
        editEmptyDist = view.findViewById(R.id.editEmptyDist)
        editVolume = view.findViewById(R.id.editVolume)
        saveButton = view.findViewById(R.id.saveButton)

        loadSettings()

        saveButton.setOnClickListener {
            performHapticFeedbackCommon(it)
            applyClickAnimation(it) {
                saveSettings()
            }
        }
    }

    private fun loadSettings() {
        val sharedPref = requireContext().getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
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
        sharedPref.apply()

        Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
    }

    private fun performHapticFeedbackCommon(view: View) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) vibrator.vibrate(50)
        }
    }

    private fun applyClickAnimation(view: View, onAnimationEnd: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction { onAnimationEnd() }.start()
        }.start()
    }
}
