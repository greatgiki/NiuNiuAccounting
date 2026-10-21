package com.example.localledger

import android.app.Application
import com.example.localledger.core.storage.BackendInitializer
import com.example.localledger.security.AppLockController
import com.example.localledger.ui.common.CategoryIcons
import com.example.localledger.ui.editor.EditorCatalog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LocalLedgerApplication : Application() {
    @Inject lateinit var backendInitializer: BackendInitializer
    @Inject lateinit var editorCatalog: EditorCatalog
    @Inject lateinit var appLockController: AppLockController

    override fun onCreate() {
        super.onCreate()
        appLockController.start()
        backendInitializer.start()
        editorCatalog.warm()
        CategoryIcons.prewarm()
    }
}
