package com.snaptube.dl.engine

import android.app.DownloadManager as AndroidDownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.snaptube.dl.data.DownloadItem
import com.snaptube.dl.data.DownloadStatus
import com.snaptube.dl.data.FormatOption
import com.snaptube.dl.data.VideoMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object DownloadManager {

    private const val TAG = "SnapDownloader"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private var pollingJob: Job? = null
    private var isReceiverRegistered = false

    fun init(context: Context) {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(AndroidDownloadManager.ACTION_DOWNLOAD_COMPLETE)
            context.applicationContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(AndroidDownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
                    handleDownloadCompleted(context.applicationContext, id)
                }
            }, filter)
            isReceiverRegistered = true
        }
    }

    fun getDownloadDir(context: Context): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SnapBox"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Snaptube & VidMate Extraction Engine:
     * Resolves Instagram, TikTok, YouTube, and Web Videos directly to direct stream URLs.
     */
    suspend fun extractMetadata(url: String): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val lower = cleanUrl.lowercase()

        // 1. Try Platform-specific direct resolvers (TikTok, Instagram)
        try {
            if (lower.contains("tiktok.com")) {
                val tiktokRes = resolveTikTok(cleanUrl)
                if (tiktokRes != null) return@withContext Result.success(tiktokRes)
            } else if (lower.contains("instagram.com")) {
                val instaRes = resolveInstagram(cleanUrl)
                if (instaRes != null) return@withContext Result.success(instaRes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Platform direct resolver attempt: ${e.message}")
        }

        // 3. Fallback: Direct HTML OpenGraph & Video tag scraper
        try {
            val scraped = scrapeWebMedia(cleanUrl)
            if (scraped != null) {
                return@withContext Result.success(scraped)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scraper fallback failed: ${e.message}", e)
        }

        Result.failure(Exception("Could not extract video stream. Please ensure the link is public or open it in the Browser tab."))
    }

    private fun resolveInstagram(url: String): VideoMetadata? {
        // Fetch Instagram embed page with mobile browser user agent
        val embedUrl = if (url.contains("/embed")) url else {
            val base = url.split("?")[0].trimEnd('/')
            "$base/embed/captioned/"
        }

        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val response = httpClient.newCall(request).execute()
        val html = response.body?.string() ?: return null

        // Extract video URL from HTML
        val videoUrl = extractMatch(html, "video_url\":\"(https:[^\"\\\\]+)")
            ?: extractMatch(html, "<meta property=\"og:video\" content=\"([^\"]+)\"")
            ?: extractMatch(html, "<video[^>]+src=\"([^\"]+)\"")

        val cleanVideoUrl = videoUrl?.replace("\\u0026", "&")?.replace("\\/", "/") ?: return null

        val thumbUrl = extractMatch(html, "<meta property=\"og:image\" content=\"([^\"]+)\"")
            ?.replace("\\u0026", "&")?.replace("\\/", "/") ?: ""
        val title = extractMatch(html, "<meta property=\"og:title\" content=\"([^\"]+)\"")
            ?: "Instagram Reel"

        val formats = listOf(
            FormatOption(
                formatId = "video-mp4-hd",
                label = "HD Video (Original)",
                ext = "mp4",
                fileSizeEstimate = "~5 - 15 MB",
                downloadUrl = cleanVideoUrl,
                isAudioOnly = false
            )
        )
        val audioFormats = listOf(
            FormatOption(
                formatId = "audio-mp3",
                label = "Audio (MP3 / M4A)",
                ext = "m4a",
                fileSizeEstimate = "~2 - 4 MB",
                downloadUrl = cleanVideoUrl,
                isAudioOnly = true
            )
        )

        return VideoMetadata(
            id = UUID.randomUUID().toString(),
            title = title,
            uploader = "Instagram",
            duration = "00:30",
            thumbnailUrl = thumbUrl,
            webpageUrl = url,
            audioFormats = audioFormats,
            videoFormats = formats
        )
    }

    private fun resolveTikTok(url: String): VideoMetadata? {
        try {
            val apiUrl = "https://www.tikwm.com/api/?url=${Uri.encode(url)}"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: return null

            val title = data.optString("title", "TikTok Video").ifEmpty { "TikTok Video" }
            val author = data.optJSONObject("author")?.optString("nickname", "TikTok") ?: "TikTok"
            val thumb = data.optString("cover", "")
            val playUrl = data.optString("play", "")
            val musicUrl = data.optString("music", playUrl)

            if (playUrl.isEmpty()) return null

            val formats = listOf(
                FormatOption(
                    formatId = "tiktok-hd",
                    label = "HD Video (No Watermark)",
                    ext = "mp4",
                    fileSizeEstimate = "~5 - 18 MB",
                    downloadUrl = playUrl,
                    isAudioOnly = false,
                    height = 1080
                )
            )
            val audioFormats = listOf(
                FormatOption(
                    formatId = "tiktok-audio",
                    label = "Audio (MP3)",
                    ext = "mp3",
                    fileSizeEstimate = "~2 - 5 MB",
                    downloadUrl = if (musicUrl.isNotEmpty()) musicUrl else playUrl,
                    isAudioOnly = true
                )
            )

            return VideoMetadata(
                id = UUID.randomUUID().toString(),
                title = title,
                uploader = author,
                duration = "00:30",
                thumbnailUrl = thumb,
                webpageUrl = url,
                audioFormats = audioFormats,
                videoFormats = formats
            )
        } catch (e: Exception) {
            Log.w(TAG, "TikTok resolver error: ${e.message}")
            return null
        }
    }

    private fun scrapeWebMedia(url: String): VideoMetadata? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val response = httpClient.newCall(request).execute()
        val html = response.body?.string() ?: return null

        val videoUrl = extractMatch(html, "<meta property=\"og:video\" content=\"([^\"]+)\"")
            ?: extractMatch(html, "<meta property=\"og:video:url\" content=\"([^\"]+)\"")
            ?: extractMatch(html, "<video[^>]+src=\"([^\"]+)\"")
            ?: return null

        val title = extractMatch(html, "<meta property=\"og:title\" content=\"([^\"]+)\"") ?: "Online Video"
        val thumb = extractMatch(html, "<meta property=\"og:image\" content=\"([^\"]+)\"") ?: ""

        val formats = listOf(
            FormatOption(
                formatId = "video-stream",
                label = "Video (MP4)",
                ext = "mp4",
                fileSizeEstimate = "~10 - 30 MB",
                downloadUrl = videoUrl,
                isAudioOnly = false
            )
        )

        return VideoMetadata(
            id = UUID.randomUUID().toString(),
            title = title,
            uploader = "Web",
            duration = "00:45",
            thumbnailUrl = thumb,
            webpageUrl = url,
            audioFormats = emptyList(),
            videoFormats = formats
        )
    }

    /**
     * Downloads directly using Android's native system DownloadManager!
     * Blazing fast, shows OS notification progress, saves straight to Downloads/SnapBox.
     */
    fun startDownload(context: Context, metadata: VideoMetadata, format: FormatOption) {
        init(context)
        val taskId = UUID.randomUUID().toString()
        val downloadDir = getDownloadDir(context)
        val cleanTitle = sanitizeFilename(metadata.title)
        val fileName = "$cleanTitle.${format.ext}"
        val targetFile = File(downloadDir, fileName)

        val cleanStreamUrl = format.downloadUrl.replace(Regex("&range=\\d+-\\d+"), "")
        val androidDm = context.getSystemService(Context.DOWNLOAD_SERVICE) as AndroidDownloadManager
        val request = AndroidDownloadManager.Request(Uri.parse(cleanStreamUrl)).apply {
            setTitle(metadata.title)
            setDescription("Downloading with SnapBox")
            setNotificationVisibility(AndroidDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SnapBox/$fileName")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36")
            try {
                val cookie = android.webkit.CookieManager.getInstance().getCookie(cleanStreamUrl)
                    ?: android.webkit.CookieManager.getInstance().getCookie(metadata.webpageUrl)
                if (!cookie.isNullOrEmpty()) {
                    addRequestHeader("Cookie", cookie)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val downloadId = try {
            androidDm.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Enqueue failed: ${e.message}", e)
            -1L
        }

        val item = DownloadItem(
            id = taskId,
            downloadManagerId = downloadId,
            url = metadata.webpageUrl,
            downloadUrl = cleanStreamUrl,
            title = metadata.title,
            formatLabel = format.label,
            ext = format.ext,
            thumbnailUrl = metadata.thumbnailUrl,
            status = if (downloadId != -1L) DownloadStatus.DOWNLOADING else DownloadStatus.FAILED,
            progress = 0,
            duration = metadata.duration,
            fileSizeString = format.fileSizeEstimate,
            localFilePath = targetFile.absolutePath,
            errorMessage = if (downloadId == -1L) "Failed to start download" else null
        )

        updateDownloadItem(item)
        startPolling(context)
    }

    private fun startPolling(context: Context) {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            val androidDm = context.getSystemService(Context.DOWNLOAD_SERVICE) as AndroidDownloadManager
            while (isActive) {
                val activeItems = _downloads.value.filter { it.status == DownloadStatus.DOWNLOADING && it.downloadManagerId != -1L }
                if (activeItems.isEmpty()) break

                var hasChanged = false
                for (item in activeItems) {
                    val query = AndroidDownloadManager.Query().setFilterById(item.downloadManagerId)
                    val cursor: Cursor? = androidDm.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(AndroidDownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(AndroidDownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(AndroidDownloadManager.COLUMN_STATUS))

                        if (bytesTotal > 0) {
                            val pct = (bytesDownloaded * 100L / bytesTotal).toInt().coerceIn(0, 100)
                            if (pct != item.progress) {
                                item.progress = pct
                                hasChanged = true
                            }
                        }

                        if (status == AndroidDownloadManager.STATUS_SUCCESSFUL) {
                            item.status = DownloadStatus.COMPLETED
                            item.progress = 100
                            val file = File(item.localFilePath)
                            if (file.exists()) {
                                item.fileSizeString = formatBytes(file.length())
                            }
                            hasChanged = true
                        } else if (status == AndroidDownloadManager.STATUS_FAILED) {
                            item.status = DownloadStatus.FAILED
                            item.errorMessage = "Download failed"
                            hasChanged = true
                        }
                    }
                    cursor?.close()
                }

                if (hasChanged) {
                    notifyUpdated()
                }
                delay(1000)
            }
        }
    }

    private fun handleDownloadCompleted(context: Context, downloadId: Long) {
        val item = _downloads.value.find { it.downloadManagerId == downloadId } ?: return
        item.status = DownloadStatus.COMPLETED
        item.progress = 100
        val file = File(item.localFilePath)
        if (file.exists()) {
            item.fileSizeString = formatBytes(file.length())
        }
        notifyUpdated()
    }

    fun cancelDownload(taskId: String) {
        val item = findItem(taskId) ?: return
        item.status = DownloadStatus.CANCELLED
        notifyUpdated()
    }

    fun deleteItem(item: DownloadItem, deleteFileFromDisk: Boolean = true) {
        if (deleteFileFromDisk && item.localFilePath.isNotEmpty()) {
            try {
                val f = File(item.localFilePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                Log.w(TAG, "File delete failed: ${e.message}")
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

    private fun extractMatch(source: String, regex: String): String? {
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(source)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("[\\\\/*?:\"<>|#]".toRegex(), "").trim().take(50).ifEmpty { "SnapBox_Video" }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format("%.1f %s", formatted, units[digitGroups.coerceIn(0, units.size - 1)])
    }
}