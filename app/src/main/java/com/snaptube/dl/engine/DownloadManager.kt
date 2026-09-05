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

    private fun getDownloadDir(context: Context): File {
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
                addOption("--socket-timeout", "15")
            }
            val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
            val title = info.title ?: "Downloaded Media"
            val uploader = info.uploader ?: "Web Video"
            val durationSec = info.duration
            val durationFormatted = formatDuration(durationSec)
            val thumbnail = info.thumbnail ?: ""

            // Build Snaptube-style curated format options
            val audioOptions = listOf(
                FormatOption(
                    formatId = "bestaudio/mp3-320",
                    label = "MP3 High Quality (320k)",
                    ext = "mp3",
                    fileSizeEstimate = "~8 - 12 MB",
                    isAudioOnly = true
                ),
                FormatOption(
                    formatId = "bestaudio/mp3-128",
                    label = "MP3 Standard (128k)",
                    ext = "mp3",
                    fileSizeEstimate = "~3 - 5 MB",
                    isAudioOnly = true
                ),
                FormatOption(
                    formatId = "bestaudio/m4a",
                    label = "M4A Audio",
                    ext = "m4a",
                    fileSizeEstimate = "~4 - 6 MB",
                    isAudioOnly = true
                )
            )

            val videoOptions = listOf(
                FormatOption(
                    formatId = "1080",
                    label = "1080p FHD",
                    ext = "mp4",
                    fileSizeEstimate = "~45 - 90 MB",
                    isAudioOnly = false,
                    height = 1080
                ),
                FormatOption(
                    formatId = "720",
                    label = "720p HD",
                    ext = "mp4",
                    fileSizeEstimate = "~25 - 45 MB",
                    isAudioOnly = false,
                    height = 720
                ),
                FormatOption(
                    formatId = "480",
                    label = "480p SD",
                    ext = "mp4",
                    fileSizeEstimate = "~15 - 25 MB",
                    isAudioOnly = false,
                    height = 480
                ),
                FormatOption(
                    formatId = "360",
                    label = "360p Low",
                    ext = "mp4",
                    fileSizeEstimate = "~8 - 15 MB",
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
            Result.failure(e)
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
            localFilePath = targetFile.absolutePath
        )

        updateDownloadItem(item)

        val job = scope.launch {
            try {
                val request = YoutubeDLRequest(metadata.webpageUrl).apply {
                    if (format.isAudioOnly) {
                        addOption("-x")
                        addOption("--audio-format", format.ext)
                        addOption("--audio-quality", "0")
                    } else {
                        addOption("-f", "bestvideo[height<=${format.height}]+bestaudio/best[height<=${format.height}]/best")
                        addOption("--merge-output-format", "mp4")
                    }
                    addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")
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
                    notifyUpdated()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                val failedItem = findItem(taskId)
                if (failedItem != null) {
                    failedItem.status = DownloadStatus.FAILED
                    failedItem.errorMessage = e.localizedMessage
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
            Log.w(TAG, "Could not kill process: ${e.message}")
        }
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)

        val item = findItem(taskId)
        if (item != null) {
            item.status = DownloadStatus.CANCELLED
            notifyUpdated()
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

    private fun formatDuration(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return "00:00"
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val h = seconds / 3600
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("[\\\\/*?:\"<>|]".toRegex(), "").trim().take(80)
    }

    private fun parseSpeed(line: String?): String {
        if (line == null) return ""
        val speedRegex = Regex("""(\d+\.?\d*\s*[KMG]i?B/s)""")
        val match = speedRegex.find(line)
        return match?.value ?: ""
    }
}
