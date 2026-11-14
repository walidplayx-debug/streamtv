package com.streamtv.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.streamtv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private data class DownloadedUpdate(
    val file: File,
    val versionName: String,
    val versionCode: Long
)

@Composable
fun AppUpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloaded by remember { mutableStateOf<DownloadedUpdate?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1C1C1E))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("streamtv", color = Color.White, fontSize = 23.sp)
        Text(
            "Version ${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
            color = Color(0xFF99999E),
            fontSize = 16.sp
        )
        if (status.isNotBlank()) {
            Text(status, color = Color(0xFFD5DADF), fontSize = 16.sp)
        }
        Spacer(Modifier.height(4.dp))
        val buttonLabel = when {
            busy -> "Checking for updates…"
            downloaded != null -> "Install update"
            else -> "Check for updates"
        }
        androidx.compose.foundation.layout.Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (focused) Color.White else Color.White.copy(alpha = .88f))
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = !busy) {
                    val ready = downloaded
                    if (ready != null) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()
                            ) {
                                status = context.getStringFromAppText(
                                    "Allow STV to install updates in the next screen, then return and choose Install update."
                                )
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } else {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                                    ready.file
                                )
                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(installIntent)
                            }
                        } catch (_: Exception) {
                            status = context.getStringFromAppText(
                                "Could not check for updates. Check your internet connection and try again."
                            )
                        }
                    } else {
                        busy = true
                        status = context.getStringFromAppText("Checking for updates…")
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    checkAndDownloadUpdate(context)
                                }
                                downloaded = result
                                status = context.getStringFromAppText(
                                    if (result == null) "You're up to date"
                                    else "Update downloaded. Choose Install update to continue."
                                )
                            } catch (error: IncompatibleUpdateException) {
                                status = context.getStringFromAppText("This update is not compatible with this app.")
                            } catch (_: Exception) {
                                status = context.getStringFromAppText(
                                    "Could not check for updates. Check your internet connection and try again."
                                )
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
                .padding(horizontal = 25.dp, vertical = 14.dp)
        ) {
            Text(
                context.getStringFromAppText(buttonLabel),
                color = Color(0xFF101014),
                fontSize = 17.sp
            )
        }
    }
}

private fun android.content.Context.getStringFromAppText(key: String): String =
    translateForCurrentLanguage(this, key)

private fun translateForCurrentLanguage(context: android.content.Context, key: String): String {
    val preferences = context.getSharedPreferences("stv_preferences", android.content.Context.MODE_PRIVATE)
    val language = preferences.getString("language", "en") ?: "en"
    return translate(language, key)
}

private class IncompatibleUpdateException : IOException()

private fun checkAndDownloadUpdate(context: android.content.Context): DownloadedUpdate? {
    val channel = BuildConfig.UPDATE_CHANNEL
    val assetName = if (channel == "latest") "stv.apk" else "stv-preview.apk"
    val apiUrl = "https://api.github.com/repos/walidplayx-debug/streamtv/releases/tags/$channel"

    val releaseConnection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "STV-Android-App")
    }
    val assetUrl = try {
        if (releaseConnection.responseCode !in 200..299) {
            throw IOException("Release lookup failed")
        }
        val release = JSONObject(releaseConnection.inputStream.bufferedReader().use { it.readText() })
        val assets = release.getJSONArray("assets")
        (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == assetName }
            ?.optString("browser_download_url")
            ?.takeIf { it.startsWith("https://") }
            ?: throw IOException("Update asset not found")
    } finally {
        releaseConnection.disconnect()
    }

    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    val apk = File(directory, "stv-update.apk")
    apk.delete()
    val downloadConnection = (URL(assetUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty("User-Agent", "STV-Android-App")
    }
    try {
        if (downloadConnection.responseCode !in 200..299) {
            throw IOException("APK download failed")
        }
        downloadConnection.inputStream.use { input ->
            apk.outputStream().use { output -> input.copyTo(output) }
        }
    } finally {
        downloadConnection.disconnect()
    }

    if (!apk.isFile || apk.length() < 1_000_000L) {
        apk.delete()
        throw IOException("Downloaded APK is incomplete")
    }
    val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        ?: run {
            apk.delete()
            throw IncompatibleUpdateException()
        }
    if (archive.packageName != context.packageName) {
        apk.delete()
        throw IncompatibleUpdateException()
    }
    val remoteVersionCode = if (Build.VERSION.SDK_INT >= 28) {
        archive.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        archive.versionCode.toLong()
    }
    if (remoteVersionCode <= BuildConfig.VERSION_CODE.toLong()) {
        apk.delete()
        return null
    }
    return DownloadedUpdate(
        file = apk,
        versionName = archive.versionName ?: "—",
        versionCode = remoteVersionCode
    )
}
