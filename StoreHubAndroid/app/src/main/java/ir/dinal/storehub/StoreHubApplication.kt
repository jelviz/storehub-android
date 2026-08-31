package ir.dinal.storehub

import android.app.Application
import ir.dinal.storehub.publishing.ProductImageProcessor
import ir.dinal.storehub.worker.WorkerScheduler

class StoreHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkerScheduler.scheduleAll(this)
        // Start the optional background-removal model download before the user opens Smart Product.
        ProductImageProcessor.prefetchSubjectSegmentation(this)
    }
}
