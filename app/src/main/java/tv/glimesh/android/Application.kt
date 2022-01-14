package tv.glimesh.android

import tv.glimesh.android.notification.LiveWorker

class Application : android.app.Application() {
    override fun onCreate() {
        super.onCreate()

        LiveWorker.start(applicationContext)
    }
}