package com.danielstiner.glimdroid

import com.danielstiner.glimdroid.notification.LiveWorker

@Suppress("unused")
class Application : android.app.Application() {
    override fun onCreate() {
        super.onCreate()

        LiveWorker.start(applicationContext)
    }
}
