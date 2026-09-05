package com.snaptube.dl

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SnapApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "SnapApp"
        lateinit var instance: SnapApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize embedded Python + yt-dlp + FFmpeg engines
        applicationScope.launch {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                Log.i(TAG, "YoutubeDL and FFmpeg initialized successfully.")
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "Failed to initialize YoutubeDL / FFmpeg: ${e.message}", e)
            }
        }
    }
}
