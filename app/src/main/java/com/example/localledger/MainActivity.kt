package com.example.localledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.navigation.AppNavHost
import com.example.localledger.ui.startup.DataLossNoticeScreen
import com.example.localledger.ui.startup.InitializationErrorScreen
import com.example.localledger.ui.startup.InitializationLoadingScreen
import com.example.localledger.ui.theme.LocalLedgerTheme
import com.example.localledger.core.storage.InitializationState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appSession: AppSession

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            val session = appSession.state.value
            val failed = session.initialization is InitializationState.Error || session.ledgerError != null
            !session.isReady && !failed
        }
        enableEdgeToEdge()
        setContent {
            LocalLedgerApp(appSession)
        }
    }
}

@Composable
fun LocalLedgerApp(appSession: AppSession) {
    val session by appSession.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LocalLedgerTheme(themeMode = session.themeMode) {
        when {
            !session.isReady -> {
                val init = session.initialization
                val ledgerError = session.ledgerError
                when {
                    init is InitializationState.Error -> InitializationErrorScreen(
                        message = init.error.message ?: "本地数据存储不可用",
                        onRetry = { scope.launch { appSession.retryInitialization() } },
                    )
                    ledgerError != null -> InitializationErrorScreen(
                        message = ledgerError.message ?: "本地数据存储不可用",
                        onRetry = { scope.launch { appSession.retryInitialization() } },
                    )
                    else -> InitializationLoadingScreen()
                }
            }
            session.showDataLossNotice -> DataLossNoticeScreen(
                onAcknowledge = { scope.launch { appSession.acknowledgeDataLoss() } },
            )
            else -> AppNavHost(appSession = appSession)
        }
    }
}
