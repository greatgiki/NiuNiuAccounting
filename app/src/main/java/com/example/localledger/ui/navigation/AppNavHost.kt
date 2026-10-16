package com.example.localledger.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.account.AccountManageScreen
import com.example.localledger.ui.budget.BudgetManageScreen
import com.example.localledger.ui.category.CategoryManageScreen
import com.example.localledger.ui.recurring.RecurringRuleScreen
import com.example.localledger.ui.detail.TransactionListScreen
import com.example.localledger.ui.editor.TransactionEditorScreen
import com.example.localledger.ui.home.HomeScreen
import com.example.localledger.ui.ledger.LedgerManageScreen
import com.example.localledger.ui.settings.SettingsScreen
import com.example.localledger.ui.statistics.StatisticsScreen
import java.time.LocalDate

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val STATISTICS = "statistics"
    const val SETTINGS = "settings"
    const val LEDGERS = "ledgers"
    const val ACCOUNTS = "accounts"
    const val BUDGETS = "budgets"
    const val RECURRING = "recurring"
    const val TRANSACTIONS_PATTERN =
        "transactions?type={type}&categoryId={categoryId}&accountId={accountId}" +
            "&startDate={startDate}&endDate={endDate}"
    const val TRANSACTION_NEW = "transaction/new?type={type}"
    const val TRANSACTION_EDIT = "transaction/edit/{transactionId}"
    const val CATEGORIES = "categories/{categoryType}"

    fun transactionNew(type: TransactionType = TransactionType.EXPENSE) =
        "transaction/new?type=${type.name}"

    fun transactionEdit(transactionId: String) = "transaction/edit/$transactionId"

    fun categories(type: CategoryType) = "categories/${type.name}"

    fun transactions(
        type: TransactionType? = null,
        categoryId: String? = null,
        accountId: String? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): String {
        val params = buildList {
            if (type != null) add("type=${type.name}")
            if (categoryId != null) add("categoryId=$categoryId")
            if (accountId != null) add("accountId=$accountId")
            if (startDate != null) add("startDate=$startDate")
            if (endDate != null) add("endDate=$endDate")
        }
        return if (params.isEmpty()) TRANSACTIONS else "transactions?${params.joinToString("&")}"
    }
}

internal data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val matchRoute: String = route,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, "首页", Icons.Default.Home),
    TopLevelDestination(
        Routes.TRANSACTIONS,
        "明细",
        Icons.AutoMirrored.Filled.List,
        matchRoute = Routes.TRANSACTIONS_PATTERN,
    ),
    TopLevelDestination(Routes.STATISTICS, "统计", Icons.Default.Assessment),
    TopLevelDestination(Routes.SETTINGS, "设置", Icons.Default.Settings),
)

private val topLevelRoutes = topLevelDestinations.map { it.matchRoute }.toSet()

private fun isTopLevelRoute(route: String?) = route in topLevelRoutes

@Composable
private fun RowScope.TopLevelNavItem(
    destination: TopLevelDestination,
    currentRoute: String?,
    navController: NavHostController,
) {
    NavigationBarItem(
        selected = currentRoute == destination.matchRoute,
        onClick = {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = {
            Icon(destination.icon, contentDescription = destination.label)
        },
        label = { Text(destination.label) },
    )
}

@Composable
fun AppNavHost(appSession: AppSession) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = topLevelDestinations.any { it.matchRoute == currentRoute }

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                LedgerBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCreate = { navController.navigate(Routes.transactionNew()) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = {
                if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                    fadeIn(tween(220))
                } else {
                    slideInHorizontally(tween(280)) { it } + fadeIn(tween(180))
                }
            },
            exitTransition = {
                if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                    fadeOut(tween(180))
                } else {
                    slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(180))
                }
            },
            popEnterTransition = {
                if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                    fadeIn(tween(220))
                } else {
                    slideInHorizontally(tween(280)) { -it / 4 } + fadeIn(tween(180))
                }
            },
            popExitTransition = {
                if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                    fadeOut(tween(180))
                } else {
                    slideOutHorizontally(tween(280)) { it } + fadeOut(tween(180))
                }
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(appSession = appSession, navController = navController)
            }
            composable(
                route = Routes.TRANSACTIONS_PATTERN,
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("categoryId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("accountId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("startDate") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("endDate") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                TransactionListScreen(appSession = appSession, navController = navController)
            }
            composable(Routes.STATISTICS) {
                StatisticsScreen(appSession = appSession, navController = navController)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(appSession = appSession, navController = navController)
            }
            composable(Routes.LEDGERS) {
                LedgerManageScreen(appSession = appSession, navController = navController)
            }
            composable(Routes.ACCOUNTS) {
                AccountManageScreen(appSession = appSession, navController = navController)
            }
            composable(Routes.BUDGETS) {
                BudgetManageScreen(navController = navController)
            }
            composable(Routes.RECURRING) {
                RecurringRuleScreen(navController = navController)
            }
            composable(
                route = Routes.CATEGORIES,
                arguments = listOf(navArgument("categoryType") { type = NavType.StringType }),
            ) {
                CategoryManageScreen(appSession = appSession, navController = navController)
            }
            composable(
                route = Routes.TRANSACTION_NEW,
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = TransactionType.EXPENSE.name
                    },
                ),
            ) {
                TransactionEditorScreen(appSession = appSession, navController = navController)
            }
            composable(
                route = Routes.TRANSACTION_EDIT,
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
            ) {
                TransactionEditorScreen(appSession = appSession, navController = navController)
            }
        }
    }
}

@Composable
internal fun LedgerBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
    onCreate: () -> Unit,
) {
    NavigationBar {
        topLevelDestinations.take(2).forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.matchRoute,
                onClick = { onNavigate(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
        NavigationBarItem(
            selected = false,
            onClick = onCreate,
            icon = {
                FilledIconButton(
                    onClick = onCreate,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            },
            label = { Text("记一笔") },
        )
        topLevelDestinations.drop(2).forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.matchRoute,
                onClick = { onNavigate(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

fun NavHostController.navigateToTransactions(
    type: TransactionType? = null,
    categoryId: String? = null,
    accountId: String? = null,
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
) {
    navigate(Routes.transactions(type, categoryId, accountId, startDate, endDate)) {
        launchSingleTop = true
    }
}
