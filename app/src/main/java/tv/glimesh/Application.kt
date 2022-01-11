package tv.glimesh

import tv.glimesh.notification.LiveWorker

class Application : android.app.Application() {
    override fun onCreate() {
        super.onCreate()

        LiveWorker.start(applicationContext)
    }
}