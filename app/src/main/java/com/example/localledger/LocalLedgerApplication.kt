package com.example.localledger

import android.app.Application
import com.example.localledger.core.storage.BackendInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LocalLedgerApplication : Application() {
    @Inject lateinit var backendInitializer: BackendInitializer

    override fun onCreate() {
        super.onCreate()
        backendInitializer.start()
    }
}
