package com.snaptube.dl

import android.app.Application

class SnapApp : Application() {

    companion object {
        lateinit var instance: SnapApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}