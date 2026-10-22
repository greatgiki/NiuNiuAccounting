package com.example.localledger.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.example.localledger.data.local.database.DatabaseProvider
import com.example.localledger.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes Glance updates after transaction writes, coalesced so a bill import
 * does not rebuild the widget once per row. No periodic polling.
 */
@Singleton
class LedgerWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: DatabaseProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            trigger.debounce(300).collect { push() }
        }
        scope.launch {
            try {
                val database = databaseProvider.get()
                database.invalidationTracker.createFlow("transactions", emitInitialState = true).collect {
                    request()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "小组件监听流水失败", error)
            }
        }
    }

    fun request() {
        trigger.tryEmit(Unit)
    }

    private suspend fun push() {
        try {
            val manager = GlanceAppWidgetManager(context)
            if (manager.getGlanceIds(LedgerSummaryWidget::class.java).isEmpty()) return
            LedgerSummaryWidget().updateAll(context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "刷新小组件失败", error)
        }
    }

    private companion object {
        const val TAG = "NiuNiuWidget"
    }
}
