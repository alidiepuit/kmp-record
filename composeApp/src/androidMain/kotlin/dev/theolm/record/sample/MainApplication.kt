package dev.theolm.record.sample

import android.app.Application
import di.appModule
import org.koin.core.context.startKoin

class MainApplication: Application() {
    companion object {
        private const val TAG = "MainApplication"
    }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(appModule())
        }
    }
}