package com.snaptube.dl

import android.app.Application
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
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

        applicationScope.launch {
            try {
                // Initialize embedded Python + yt-dlp + FFmpeg + Aria2c engines
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                Aria2c.getInstance().init(applicationContext)
                Log.i(TAG, "YoutubeDL, FFmpeg, and Aria2c initialized successfully.")

                // Background check for latest yt-dlp core to keep extractors up to date
                try {
                    val status = YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel._STABLE)
                    Log.i(TAG, "yt-dlp auto-update check: $status")
                } catch (updateErr: Exception) {
                    Log.w(TAG, "yt-dlp update check: ${updateErr.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize engines: ${e.message}", e)
            }
        }
    }
}