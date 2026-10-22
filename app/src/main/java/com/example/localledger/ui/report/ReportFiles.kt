package com.example.localledger.ui.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.localledger.domain.report.ReportKind
import java.io.File
import java.time.LocalDate

internal object ReportFiles {
    fun suggestedName(kind: ReportKind, start: LocalDate): String {
        val label = if (kind == ReportKind.WEEK) "周报" else "月报"
        return "牛牛记账-$label-$start.png"
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "分享报表"))
    }

    fun sharePng(context: Context, bitmap: Bitmap, text: String) {
        val uri = writeCachePng(context, bitmap)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "report", uri)
        }
        context.startActivity(
            Intent.createChooser(send, "分享报表").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    fun writePng(context: Context, uri: Uri, bitmap: Bitmap) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "png" }
        } ?: error("output")
    }

    private fun writeCachePng(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "report-share").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - DAY_MILLIS
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
        val file = File(dir, "report-${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "png" }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
}
