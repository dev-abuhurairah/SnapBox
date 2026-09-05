package com.snaptube.dl.engine

import android.content.Context
import android.os.Environment
import android.util.Log
import com.snaptube.dl.data.DownloadItem
import com.snaptube.dl.data.DownloadStatus
import com.snaptube.dl.data.FormatOption
import com.snaptube.dl.data.VideoMetadata
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DownloadManager {

    private const val TAG = "DownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    fun getDownloadDir(context: Context): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SnapDownloader"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun extractMetadata(url: String): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--no-check-certificates")
                addOption("--ignore-errors")
                addOption("--socket-timeout", "15")
                addOption("--user-agent", "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                addOption("--add-header", "Accept-Language:en-US,en;q=0.9")
            }
            val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
            val title = (info.title ?: "Social Media Video").trim()
            val uploader = (info.uploader ?: "Creator").trim()
            val durationSec = info.duration
            val durationFormatted = formatDuration(durationSec)
            val thumbnail = info.thumbnail ?: ""

            val audioOptions = listOf(
                FormatOption(
                    formatId = "bestaudio/mp3-320",
                    label = "MP3 High Quality (320k)",
                    ext = "mp3",
                    fileSizeEstimate = "~5 - 10 MB",
                    isAudioOnly = true
                ),
                FormatOption(
                    formatId = "bestaudio/mp3-128",
                    label = "MP3 Standard (128k)",
                    ext = "mp3",
                    fileSizeEstimate = "~3 - 6 MB",
                    isAudioOnly = true
                ),
                FormatOption(
                    formatId = "bestaudio/m4a",
                    label = "M4A Audio",
                    ext = "m4a",
                    fileSizeEstimate = "~3 - 5 MB",
                    isAudioOnly = true
                )
            )

            val videoOptions = listOf(
                FormatOption(
                    formatId = "1080",
                    label = "1080p FHD",
                    ext = "mp4",
                    fileSizeEstimate = "~30 - 80 MB",
                    isAudioOnly = false,
                    height = 1080
                ),
                FormatOption(
                    formatId = "720",
                    label = "720p HD",
                    ext = "mp4",
                    fileSizeEstimate = "~15 - 40 MB",
                    isAudioOnly = false,
                    height = 720
                ),
                FormatOption(
                    formatId = "480",
                    label = "480p SD",
                    ext = "mp4",
                    fileSizeEstimate = "~8 - 20 MB",
                    isAudioOnly = false,
                    height = 480
                ),
                FormatOption(
                    formatId = "360",
                    label = "360p Low",
                    ext = "mp4",
                    fileSizeEstimate = "~5 - 12 MB",
                    isAudioOnly = false,
                    height = 360
                )
            )

            Result.success(
                VideoMetadata(
                    id = info.id ?: UUID.randomUUID().toString(),
                    title = title,
                    uploader = uploader,
                    duration = durationFormatted,
                    thumbnailUrl = thumbnail,
                    webpageUrl = url,
                    audioFormats = audioOptions,
                    videoFormats = videoOptions
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            val cleanMsg = cleanErrorMessage(e.localizedMessage)
            Result.failure(Exception(cleanMsg, e))
        }
    }

    fun startDownload(context: Context, metadata: VideoMetadata, format: FormatOption) {
        val taskId = UUID.randomUUID().toString()
        val downloadDir = getDownloadDir(context)
        val cleanTitle = sanitizeFilename(metadata.title)
        val fileName = "$cleanTitle.${format.ext}"
        val targetFile = File(downloadDir, fileName)

        val item = DownloadItem(
            id = taskId,
            url = metadata.webpageUrl,
            title = metadata.title,
            formatLabel = format.label,
            ext = format.ext,
            thumbnailUrl = metadata.thumbnailUrl,
            status = DownloadStatus.DOWNLOADING,
            progress = 0,
            duration = metadata.duration,
            fileSizeString = "",
            localFilePath = targetFile.absolutePath
        )

        updateDownloadItem(item)

        val job = scope.launch {
            try {
                val request = YoutubeDLRequest(metadata.webpageUrl).apply {
                    addOption("--no-warnings")
                    addOption("--no-check-certificates")
                    addOption("--prefer-free-formats")
                    addOption("--socket-timeout", "30")
                    addOption("--user-agent", "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    addOption("--add-header", "Accept-Language:en-US,en;q=0.9")

                    // High speed multi-connection aria2c accelerator
                    try {
                        addOption("--downloader", "libaria2c.so")
                        addOption("--downloader-args", "aria2c:-c -j 8 -s 8 -x 8 -k 1M")
                    } catch (e: Exception) {
                        Log.w(TAG, "Aria2c option error: ${e.message}")
                    }

                    if (format.isAudioOnly) {
                        addOption("-x")
                        addOption("--audio-format", format.ext)
                        addOption("--audio-quality", "0")
                        addOption("-f", "bestaudio/best")
                    } else {
                        // Universal format fallback: Works for Instagram Reels, TikTok, and YouTube
                        addOption("-f", "bestvideo[height<=${format.height}]+bestaudio/best[height<=${format.height}]/bestvideo+bestaudio/best")
                        addOption("--merge-output-format", "mp4")
                    }

                    addOption("-o", "${downloadDir.absolutePath}/%(title).60s.%(ext)s")
                    addOption("--no-mtime")
                }

                YoutubeDL.getInstance().execute(request, taskId) { progress, etaInSeconds, line ->
                    val currentItem = findItem(taskId) ?: return@execute
                    currentItem.progress = progress.toInt().coerceIn(0, 100)
                    currentItem.etaSeconds = etaInSeconds
                    currentItem.speedString = parseSpeed(line)
                    currentItem.status = DownloadStatus.DOWNLOADING
                    notifyUpdated()
                }

                val completedItem = findItem(taskId)
                if (completedItem != null) {
                    completedItem.status = DownloadStatus.COMPLETED
                    completedItem.progress = 100

                    val foundFile = findDownloadedFile(downloadDir, cleanTitle, format.ext)
                    if (foundFile != null && foundFile.exists()) {
                        completedItem.localFilePath = foundFile.absolutePath
                        completedItem.fileSizeString = formatBytes(foundFile.length())
                    } else if (targetFile.exists()) {
                        completedItem.fileSizeString = formatBytes(targetFile.length())
                    }
                    notifyUpdated()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                val failedItem = findItem(taskId)
                if (failedItem != null) {
                    failedItem.status = DownloadStatus.FAILED
                    failedItem.errorMessage = cleanErrorMessage(e.localizedMessage)
                    notifyUpdated()
                }
            } finally {
                activeJobs.remove(taskId)
            }
        }

        activeJobs[taskId] = job
    }

    fun cancelDownload(taskId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (e: Exception) {
            Log.w(TAG, "Could not destroy process: ${e.message}")
        }
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)

        val item = findItem(taskId)
        if (item != null) {
            item.status = DownloadStatus.CANCELLED
            notifyUpdated()
        }
    }

    fun deleteItem(item: DownloadItem, deleteFileFromDisk: Boolean = true) {
        cancelDownload(item.id)
        if (deleteFileFromDisk && item.localFilePath.isNotEmpty()) {
            try {
                val f = File(item.localFilePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete file from disk: ${e.message}")
            }
        }
        val current = _downloads.value.toMutableList()
        current.removeAll { it.id == item.id }
        _downloads.value = current
    }

    fun clearAllCompleted() {
        val current = _downloads.value.toMutableList()
        current.removeAll { it.status == DownloadStatus.COMPLETED }
        _downloads.value = current
    }

    suspend fun updateEngine(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
            Result.success("yt-dlp updated: ${status?.name ?: "Up to date"}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findItem(id: String): DownloadItem? {
        return _downloads.value.find { it.id == id }
    }

    private fun updateDownloadItem(item: DownloadItem) {
        val current = _downloads.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        _downloads.value = current
    }

    private fun notifyUpdated() {
        _downloads.value = _downloads.value.toList()
    }

    private fun findDownloadedFile(dir: File, prefix: String, ext: String): File? {
        val files = dir.listFiles() ?: return null
        return files.firstOrNull { it.name.startsWith(prefix) || it.extension.equals(ext, ignoreCase = true) }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format("%.1f %s", formatted, units[digitGroups.coerceIn(0, units.size - 1)])
    }

    private fun formatDuration(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return "00:00"
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val h = seconds / 3600
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("[\\\\/*?:\"<>|#]".toRegex(), "").trim().take(50)
    }

    private fun parseSpeed(line: String?): String {
        if (line == null) return ""
        val speedRegex = Regex("""(\d+\.?\d*\s*[KMG]i?B/s)""")
        val match = speedRegex.find(line)
        return match?.value ?: ""
    }

    private fun cleanErrorMessage(rawMsg: String?): String {
        if (rawMsg == null) return "Unable to extract video. Please check URL or network."
        // Remove yt-dlp version warnings from message
        val lines = rawMsg.split("\n")
        val filtered = lines.filterNot { it.contains("WARNING:", ignoreCase = true) }
        val result = filtered.joinToString(" ").trim()
        return if (result.isNotEmpty()) result else "Video cannot be reached. It may be private or restricted."
    }
}