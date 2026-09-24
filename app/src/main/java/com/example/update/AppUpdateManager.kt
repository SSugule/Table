package com.example.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Downloading(val progress: Float, val version: String) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File, val version: String) : UpdateStatus()
    data class UpToDate(val version: String) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "duty_scheduler_update_prefs"
    private const val KEY_GITHUB_REPO = "github_repo"

    // Репозиторий по умолчанию
    const val DEFAULT_REPO = "super-souls2018/monarchy-schedule"

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getGitHubRepo(context: Context): String {
        return getPrefs(context).getString(KEY_GITHUB_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
    }

    fun setGitHubRepo(context: Context, repo: String) {
        getPrefs(context).edit().putString(KEY_GITHUB_REPO, repo.trim()).apply()
    }

    /**
     * Проверяет наличие обновления и СРАЗУ же загружает и запускает установку
     * БЕЗ дополнительных подтверждений от пользователя.
     */
    suspend fun checkForUpdateAndInstall(context: Context, silent: Boolean = true) {
        val repo = getGitHubRepo(context)
        _status.value = UpdateStatus.Checking

        withContext(Dispatchers.IO) {
            try {
                val latestRelease = fetchLatestRelease(repo)
                if (latestRelease == null) {
                    _status.value = if (silent) UpdateStatus.Idle else UpdateStatus.UpToDate(BuildConfig.VERSION_NAME)
                    return@withContext
                }

                val currentVersion = BuildConfig.VERSION_NAME
                val isNewer = isVersionNewer(latestRelease.versionName, currentVersion)

                Log.d(TAG, "Current: $currentVersion, Latest: ${latestRelease.versionName}, isNewer: $isNewer")

                if (isNewer) {
                    // НАЙДЕНА НОВАЯ ВЕРСИЯ: Сразу загружаем APK файл в фоне без ожидания подтверждения
                    downloadAndTriggerInstall(context, latestRelease)
                } else {
                    _status.value = UpdateStatus.UpToDate(currentVersion)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                _status.value = UpdateStatus.Error(e.message ?: "Ошибка проверки обновления")
            }
        }
    }

    /**
     * Запрос информации о последнем релизе через GitHub REST API
     */
    private fun fetchLatestRelease(repo: String): ReleaseInfo? {
        val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "MonarchyDutyScheduler-Android")
        conn.connectTimeout = 10000
        conn.readTimeout = 15000

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            Log.d(TAG, "No releases found on repo $repo")
            return null
        }
        if (responseCode != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "GitHub API returned code: $responseCode")
            return null
        }

        val jsonString = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(jsonString)

        val tagName = root.optString("tag_name", "")
        val versionName = tagName.removePrefix("v")
        val body = root.optString("body", "")

        val assets = root.optJSONArray("assets") ?: return null
        var apkDownloadUrl: String? = null

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkDownloadUrl = asset.optString("browser_download_url")
                break
            }
        }

        if (apkDownloadUrl.isNullOrBlank()) {
            Log.w(TAG, "Release $tagName found, but no .apk asset found")
            return null
        }

        return ReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            downloadUrl = apkDownloadUrl,
            releaseNotes = body
        )
    }

    /**
     * Скачивание файла APK и мгновенный запуск установки
     */
    private suspend fun downloadAndTriggerInstall(context: Context, release: ReleaseInfo) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "DutySchedule_${release.versionName}.apk")

        if (apkFile.exists()) {
            apkFile.delete()
        }

        _status.value = UpdateStatus.Downloading(0f, release.versionName)

        val url = URL(release.downloadUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.connect()

        val totalLength = conn.contentLength
        var downloadedBytes = 0L

        conn.inputStream.use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalLength > 0) {
                        val progress = downloadedBytes.toFloat() / totalLength.toFloat()
                        _status.value = UpdateStatus.Downloading(progress, release.versionName)
                    }
                }
            }
        }

        _status.value = UpdateStatus.ReadyToInstall(apkFile, release.versionName)

        // Запуск установки APK на устройстве
        withContext(Dispatchers.Main) {
            launchInstallApk(context, apkFile)
        }
    }

    /**
     * Запуск встроенного системного установщика пакетов
     */
    fun launchInstallApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
            return
        }

        try {
            // Проверка разрешения на установку из неизвестных источников (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(manageIntent)
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            _status.value = UpdateStatus.Error("Не удалось запустить установку: ${e.message}")
        }
    }

    /**
     * Строгое сравнение версий по правилам Блока 5: X.Y.Z[suffix]
     * Возвращает true, если newVersion строго новее, чем currentVersion.
     */
    fun isVersionNewer(newVersion: String, currentVersion: String): Boolean {
        val cleanNew = newVersion.removePrefix("v").trim()
        val cleanCur = currentVersion.removePrefix("v").trim()

        if (cleanNew == cleanCur) return false

        val regex = Regex("""^(\d+)\.(\d+)\.(\d+)([a-z])?$""")
        val matchNew = regex.find(cleanNew)
        val matchCur = regex.find(cleanCur)

        if (matchNew != null && matchCur != null) {
            val (nx, ny, nz) = matchNew.destructured.let { Triple(it.component1().toInt(), it.component2().toInt(), it.component3().toInt()) }
            val (cx, cy, cz) = matchCur.destructured.let { Triple(it.component1().toInt(), it.component2().toInt(), it.component3().toInt()) }

            if (nx != cx) return nx > cx
            if (ny != cy) return ny > cy
            if (nz != cz) return nz > cz

            val nSuffix = matchNew.groups[4]?.value ?: ""
            val cSuffix = matchCur.groups[4]?.value ?: ""

            return nSuffix > cSuffix
        }

        // Общий fallback для сравнения версий через точки
        val newParts = cleanNew.split(".").mapNotNull { it.toIntOrNull() }
        val curParts = cleanCur.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(newParts.size, curParts.size)
        for (i in 0 until maxLen) {
            val n = newParts.getOrElse(i) { 0 }
            val c = curParts.getOrElse(i) { 0 }
            if (n != c) return n > c
        }

        return cleanNew > cleanCur
    }
}
