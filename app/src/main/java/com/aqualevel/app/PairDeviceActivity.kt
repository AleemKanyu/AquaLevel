package com.aqualevel.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider

class PairDeviceActivity : ComponentActivity() {

    private val fromSettings get() = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)

    private lateinit var pairDeviceViewModel: PairDeviceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        pairDeviceViewModel = ViewModelProvider(this)[PairDeviceViewModel::class.java]
        handleDeepLink(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    PairDeviceScreen(
                        onPairSuccess = { navigateOnSuccess() },
                        viewModel     = pairDeviceViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "aqualevel" && uri.host == "pair") {
            val deviceId = uri.getQueryParameter("id")
            if (!deviceId.isNullOrBlank()) {
                pairDeviceViewModel.preloadDeviceId(deviceId.uppercase())
            }
        }
    }

    private fun navigateOnSuccess() {
        if (fromSettings) {
            // Came from Settings — just go back, MainActivity is already running
            finish()
        } else {
            // First launch — clear stack and start fresh at MainActivity
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }

    companion object {
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }
}
