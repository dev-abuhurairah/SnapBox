package com.snaptube.dl.data

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class FormatOption(
    val formatId: String,
    val label: String,
    val ext: String,
    val fileSizeEstimate: String,
    val downloadUrl: String,
    val isAudioOnly: Boolean,
    val height: Int? = null
)

data class VideoMetadata(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: String,
    val thumbnailUrl: String,
    val webpageUrl: String,
    val audioFormats: List<FormatOption>,
    val videoFormats: List<FormatOption>
)

data class DownloadItem(
    val id: String,
    var downloadManagerId: Long = -1L,
    val url: String,
    val downloadUrl: String,
    val title: String,
    val formatLabel: String,
    val ext: String,
    val thumbnailUrl: String,
    var status: DownloadStatus = DownloadStatus.QUEUED,
    var progress: Int = 0,
    var speedString: String = "",
    var etaSeconds: Long? = null,
    var duration: String = "00:00",
    var fileSizeString: String = "",
    var localFilePath: String = "",
    var errorMessage: String? = null
)

data class PlatformItem(
    val name: String,
    val url: String,
    val colorHex: String
)