package ir.dinal.storehub

import android.app.Application
import ir.dinal.storehub.worker.WorkerScheduler

class StoreHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkerScheduler.scheduleAll(this)
    }
}
