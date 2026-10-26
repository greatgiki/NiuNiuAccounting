package com.example.localledger

import android.app.Application
import com.example.localledger.capture.CaptureCoordinator
import com.example.localledger.core.storage.BackendInitializer
import com.example.localledger.security.AppLockController
import com.example.localledger.ui.common.CategoryIcons
import com.example.localledger.ui.editor.EditorCatalog
import com.example.localledger.widget.LedgerWidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LocalLedgerApplication : Application() {
    @Inject lateinit var backendInitializer: BackendInitializer
    @Inject lateinit var editorCatalog: EditorCatalog
    @Inject lateinit var appLockController: AppLockController
    @Inject lateinit var widgetRefresher: LedgerWidgetRefresher
    @Inject lateinit var captureCoordinator: CaptureCoordinator

    override fun onCreate() {
        super.onCreate()
        appLockController.start()
        backendInitializer.start()
        editorCatalog.warm()
        CategoryIcons.prewarm()
        widgetRefresher.start()
        captureCoordinator.warm()
    }
}
