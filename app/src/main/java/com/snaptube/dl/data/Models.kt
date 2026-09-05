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
    val isAudioOnly: Boolean,
    val height: Int = 0
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
    val url: String,
    val title: String,
    val formatLabel: String,
    val ext: String,
    val thumbnailUrl: String,
    var status: DownloadStatus = DownloadStatus.QUEUED,
    var progress: Int = 0,
    var etaSeconds: Long = 0,
    var speedString: String = "",
    var localFilePath: String = "",
    var errorMessage: String? = null
)

data class PlatformItem(
    val name: String,
    val url: String,
    val colorHex: String
)
