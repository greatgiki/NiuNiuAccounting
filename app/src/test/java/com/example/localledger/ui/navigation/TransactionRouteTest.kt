package com.example.localledger.ui.navigation

import android.content.Intent
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import com.example.localledger.MainActivity
import com.example.localledger.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TransactionRouteTest {
    @Test
    fun widgetAddExtraOpensManualEntry() {
        val intent = Intent().putExtra(MainActivity.EXTRA_OPEN_NEW_TRANSACTION, true)
        val route = MainActivity.routeFor(intent)
        assertEquals(Routes.transactionNew(), route)
        assertEquals("transaction/new?type=EXPENSE", route)
        assertFalse(route!!.contains("voice"))
    }

    @Test
    fun voiceQueryStaysOptionalSoTheWidgetUriStillMatches() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val controller = NavController(context)
        controller.navigatorProvider.addNavigator(ComposeNavigator())
        controller.graph = controller.createGraph(startDestination = Routes.MAIN) {
            composable(Routes.MAIN) {}
            composable(
                route = Routes.TRANSACTION_NEW,
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = TransactionType.EXPENSE.name
                    },
                    navArgument("voice") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) {}
        }
        controller.navigate(Routes.transactionNew())
        assertEquals(Routes.TRANSACTION_NEW, controller.currentDestination?.route)
        assertEquals(false, controller.currentBackStackEntry?.arguments?.getBoolean("voice"))

        controller.navigate(Routes.transactionNew(voice = true))
        assertEquals(Routes.TRANSACTION_NEW, controller.currentDestination?.route)
        assertEquals(true, controller.currentBackStackEntry?.arguments?.getBoolean("voice"))
    }
}
