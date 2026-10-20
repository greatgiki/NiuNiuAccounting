package com.example.localledger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.localledger.data.local.database.DatabaseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.localledger.benchmark.BenchmarkFlags
import com.example.localledger.benchmark.BenchmarkSeed
import com.example.localledger.notification.PendingNavigation
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.navigation.AppNavHost
import com.example.localledger.ui.navigation.Routes
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
    @Inject lateinit var pendingNavigation: PendingNavigation
    @Inject lateinit var databaseProvider: DatabaseProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (com.example.localledger.BuildConfig.SEED_DEMO) {
            val count = intent?.getIntExtra(BenchmarkSeed.EXTRA_SEED_COUNT, 0) ?: 0
            if (count > 0) {
                BenchmarkFlags.seedCount = count
                BenchmarkFlags.expandedTransactionList = true
                lifecycleScope.launch {
                    appSession.state.first { it.isReady }
                    BenchmarkSeed.seedIfRequested(databaseProvider)
                }
            }
        }
        splashScreen.setKeepOnScreenCondition {
            val session = appSession.state.value
            val failed = session.initialization is InitializationState.Error || session.ledgerError != null
            !session.isReady && !failed
        }
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            LocalLedgerApp(appSession, pendingNavigation)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DRAFTS, false) == true) {
            pendingNavigation.request(Routes.DRAFTS)
        }
    }

    companion object {
        const val EXTRA_OPEN_DRAFTS = "open_drafts"
    }
}

@Composable
fun LocalLedgerApp(appSession: AppSession, pendingNavigation: PendingNavigation) {
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
            else -> AppNavHost(appSession = appSession, pendingNavigation = pendingNavigation)
        }
    }
}
