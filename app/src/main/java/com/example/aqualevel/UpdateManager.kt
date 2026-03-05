package com.example.aqualevel

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    private val GITHUB_API_URL = "https://api.github.com/repos/AleemKanyu/AquaLevel/releases/latest"
    private var downloadId: Long = -1L

    fun checkForUpdates(currentVersion: Int, onUpdateAvailable: (String, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == 200) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(content)
                    val latestTagName = json.getString("tag_name")
                    // Assuming tag is like "v1.2" and we check against versionCode or similar
                    // For simplicity, let's assume we use a version string comparison or explicit property
                    
                    // In a real app, you'd parse version from tag or a metadata file
                    // Here we'll just check if tag is different for demonstration
                    val currentTag = "v${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"
                    
                    if (latestTagName != currentTag) {
                        val assets = json.getJSONArray("assets")
                        if (assets.length() > 0) {
                            val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                            val releaseBody = json.optString("body", "No changelog provided.")
                            
                            withContext(Dispatchers.Main) {
                                onUpdateAvailable(latestTagName, downloadUrl)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Error checking for updates", e)
            }
        }
    }

    fun downloadAndInstall(downloadUrl: String) {
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("AquaLevel Update")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "AquaLevel_Update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk()
                    context?.unregisterReceiver(this)
                }
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        
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
