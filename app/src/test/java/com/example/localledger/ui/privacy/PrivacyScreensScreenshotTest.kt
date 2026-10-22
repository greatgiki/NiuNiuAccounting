package com.example.localledger.ui.privacy

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.localledger.data.backup.BackupSummary
import com.example.localledger.ui.backup.BackupRestoreContent
import com.example.localledger.ui.backup.BackupUiState
import com.example.localledger.ui.backup.RestoreConfirmDialog
import com.example.localledger.ui.exporting.ExportContent
import com.example.localledger.ui.exporting.ExportUiState
import com.example.localledger.ui.exporting.LedgerOption
import com.example.localledger.ui.lock.AppLockSettingsContent
import com.example.localledger.ui.lock.LockScreen
import com.example.localledger.ui.theme.LocalLedgerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h2400dp-xxhdpi")
class PrivacyScreensScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renderBackupExportLockAndForgotPin() {
        var screen by mutableStateOf("backup")
        compose.setContent {
            LocalLedgerTheme {
                when (screen) {
                    "backup" -> Box(Modifier.testTag("shot").width(360.dp).background(MaterialTheme.colorScheme.background)) {
                        BackupRestoreContent(
                            state = BackupUiState(autoEnabled = true, folderLabel = "文档/牛牛备份", keepCount = 5),
                            onExport = {},
                            onRestore = {},
                            onToggleAuto = {},
                            onPickFolder = {},
                            onKeepCount = {},
                        )
                    }
                    "export" -> Box(Modifier.testTag("shot").width(360.dp).background(MaterialTheme.colorScheme.background)) {
                        ExportContent(
                            state = ExportUiState(
                                ledgers = listOf(LedgerOption("daily", "日常账本")),
                                ledgerId = "daily",
                                start = LocalDate.of(2026, 10, 1),
                                end = LocalDate.of(2026, 10, 21),
                            ),
                            onLedger = {},
                            onStart = {},
                            onEnd = {},
                            onCsv = {},
                            onXlsx = {},
                        )
                    }
                    "lock-settings" -> Box(Modifier.testTag("shot").width(360.dp).background(MaterialTheme.colorScheme.background)) {
                        AppLockSettingsContent(
                            hasPin = true,
                            biometricEnabled = true,
                            biometricAvailable = true,
                            timeoutMinutes = 1,
                            message = null,
                            onCreate = {},
                            onChange = {},
                            onDisable = {},
                            onBiometric = {},
                            onTimeout = {},
                        )
                    }
                    "confirm" -> Box(Modifier.fillMaxSize().testTag("shot")) {
                        RestoreConfirmDialog(
                            summary = BackupSummary(5, 1_761_000_000_000L, 1, 4, 12),
                            busy = false,
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                    else -> Box(
                        Modifier.testTag("shot").width(360.dp).height(760.dp).background(MaterialTheme.colorScheme.background),
                    ) {
                        LockScreen(
                            biometricAvailable = true,
                            error = null,
                            busy = false,
                            onUnlock = {},
                            onBiometric = {},
                            initialPin = "12",
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("导出备份").assertExists()
        capture("backup_restore")
        screen = "export"
        compose.onNodeWithText("导出 Excel").assertExists()
        capture("export_transactions")
        screen = "lock-settings"
        compose.onNodeWithText("修改密码").assertExists()
        capture("app_lock_settings")
        screen = "lock"
        compose.onNodeWithText("已锁定").assertExists()
        capture("lock_screen")
        compose.onNodeWithText("忘记密码").performClick()
        compose.onNodeWithText("无法找回密码").assertExists()
        capture("forgot_pin")
        screen = "confirm"
        compose.onNodeWithText("覆盖并恢复").assertExists()
        capture("restore_confirm")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithTag("shot").fetchSemanticsNode()
        val rootView = (node.root as ViewRootForTest).view
        val bitmap = Bitmap.createBitmap(rootView.width.coerceAtLeast(1), rootView.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        rootView.draw(Canvas(bitmap))
        val dirs = listOf(File("build/screenshots"), File("/opt/cursor/artifacts/screenshots"))
        dirs.forEach { dir ->
            dir.mkdirs()
            File(dir, "$name.png").outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
        bitmap.recycle()
    }
}
