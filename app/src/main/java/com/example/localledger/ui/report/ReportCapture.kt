package com.example.localledger.ui.report

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/**
 * Records the report card once, on the share or save click.
 * The layer is not captured on ordinary frames, so scrolling stays cheap.
 */
class ReportCaptureController {
    var armed by mutableStateOf(false)
        private set

    private var pending: CompletableDeferred<Unit>? = null

    fun onRecorded() {
        val waiter = pending ?: return
        pending = null
        waiter.complete(Unit)
    }

    suspend fun capture(layer: GraphicsLayer): Bitmap {
        val waiter = CompletableDeferred<Unit>()
        pending = waiter
        armed = true
        try {
            withTimeout(2_000) { waiter.await() }
            val image = layer.toImageBitmap()
            return image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            armed = false
            pending = null
        }
    }
}

@Composable
internal fun rememberReportLayer(): GraphicsLayer = rememberGraphicsLayer()

@Composable
internal fun ReportCaptureHost(
    controller: ReportCaptureController,
    layer: GraphicsLayer,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val armed = controller.armed
    Box(
        modifier.drawWithContent {
            if (armed && size.width >= 1f && size.height >= 1f) {
                // One drawContent call, into the layer. A second call in the same pass is dropped.
                layer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(layer)
                // Resume after this draw. Completing inline would read the layer mid-pass.
                Handler(Looper.getMainLooper()).post { controller.onRecorded() }
            } else {
                drawContent()
            }
        },
    ) {
        content()
    }
}
