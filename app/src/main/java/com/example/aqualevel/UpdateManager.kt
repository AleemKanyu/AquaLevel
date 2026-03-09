package com.example.aqualevel

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    private companion object {
        const val GITHUB_API_URL = "https://api.github.com/repos/AleemKanyu/AquaLevel/releases/latest"
    }

    private var downloadId: Long = -1L

    fun checkForUpdates(onUpdateAvailable: (String, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == 200) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(content)
                    
                    // The tag from GitHub, e.g. "v1.8.0"
                    val latestTagName = json.getString("tag_name")
                    // Strip "v" if present so it match our versionName e.g. "1.8.0"
                    val cleanLatestTag = latestTagName.removePrefix("v").removePrefix("V")
                    
                    val currentTag = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                    
                    Log.d("UpdateManager", "Current version: $currentTag, Latest version: $cleanLatestTag")

                    if (isNewerVersion(currentTag, cleanLatestTag)) {
                        // Check if the user already dismissed this specific version update
                        val sharedPref = context.getSharedPreferences("AquaLevelPrefs", Context.MODE_PRIVATE)
                        val skippedVersion = sharedPref.getString("skipped_update_version", null)
                        
                        if (cleanLatestTag != skippedVersion) {
                            val assets = json.getJSONArray("assets")
                            if (assets.length() > 0) {
                                val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                                
                                withContext(Dispatchers.Main) {
                                    onUpdateAvailable(cleanLatestTag, downloadUrl)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Error checking for updates", e)
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currentParts = current.split(".").map { it.toInt() }
            val latestParts = latest.split(".").map { it.toInt() }
            
            for (i in 0 until minOf(currentParts.size, latestParts.size)) {
                if (latestParts[i] > currentParts[i]) return true
                if (latestParts[i] < currentParts[i]) return false
            }
            latestParts.size > currentParts.size
        } catch (e: Exception) {
            latest != current
        }
    }

    fun downloadAndInstall(downloadUrl: String) {
        val request = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("AquaLevel Update")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "AquaLevel_Update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk()
                    context?.unregisterReceiver(this)
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        
        Toast.makeText(context, "Update download started...", Toast.LENGTH_SHORT).show()
    }

    private fun installApk() {
        val file = java.io.File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "AquaLevel_Update.apk"
        )
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
