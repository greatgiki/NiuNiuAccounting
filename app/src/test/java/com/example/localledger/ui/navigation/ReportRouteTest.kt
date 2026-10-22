package com.example.localledger.ui.navigation

import android.content.Context
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReportRouteTest {
    @Test
    fun weekAndMonthRoutesKeepKindAndAnchor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = NavController(context)
        controller.navigatorProvider.addNavigator(ComposeNavigator())
        controller.graph = controller.createGraph(startDestination = Routes.MAIN) {
            composable(Routes.MAIN) {}
            composable(
                route = Routes.REPORT,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("anchor") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {}
        }

        controller.navigate(Routes.weekReport(LocalDate.of(2026, 7, 20)))
        assertEquals(Routes.REPORT, controller.currentDestination?.route)
        assertEquals("week", controller.currentBackStackEntry?.arguments?.getString("kind"))
        assertEquals("2026-07-20", controller.currentBackStackEntry?.arguments?.getString("anchor"))

        controller.navigate(Routes.monthReport(LocalDate.of(2026, 7, 1)))
        assertEquals("month", controller.currentBackStackEntry?.arguments?.getString("kind"))
        assertEquals("2026-07-01", controller.currentBackStackEntry?.arguments?.getString("anchor"))
    }
}
