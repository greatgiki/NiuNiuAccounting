package com.example.localledger.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.localledger.ui.component.LedgerHairline
import com.example.localledger.ui.component.rememberLedgerHaptic
import com.example.localledger.ui.theme.LedgerMotion
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.localledger.domain.model.CategoryType
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.notification.PendingNavigation
import com.example.localledger.ui.AppSession
import com.example.localledger.ui.account.AccountManageScreen
import com.example.localledger.ui.budget.BudgetManageScreen
import com.example.localledger.ui.category.CategoryManageScreen
import com.example.localledger.ui.detail.TransactionListScreen
import com.example.localledger.ui.editor.TransactionEditorScreen
import com.example.localledger.ui.home.HomeScreen
import com.example.localledger.ui.importing.AutoRecordScreen
import com.example.localledger.ui.importing.BillImportScreen
import com.example.localledger.ui.importing.DraftInboxScreen
import com.example.localledger.ui.ledger.LedgerManageScreen
import com.example.localledger.ui.recurring.RecurringRuleScreen
import com.example.localledger.ui.settings.SettingsScreen
import com.example.localledger.ui.statistics.StatisticsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

object Routes {
    const val MAIN = "main"
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val STATISTICS = "statistics"
    const val SETTINGS = "settings"
    const val LEDGERS = "ledgers"
    const val ACCOUNTS = "accounts"
    const val BUDGETS = "budgets"
    const val RECURRING = "recurring"
    const val IMPORT = "import"
    const val AUTO_RECORD = "auto-record"
    const val DRAFTS = "drafts"
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

enum class TopLevelPage(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home(Routes.HOME, "首页", Icons.Outlined.Home),
    Transactions(Routes.TRANSACTIONS, "明细", Icons.AutoMirrored.Outlined.ReceiptLong),
    Statistics(Routes.STATISTICS, "统计", Icons.Outlined.PieChart),
    Settings(Routes.SETTINGS, "设置", Icons.Outlined.Settings),
    ;

    companion object {
        fun fromIndex(index: Int): TopLevelPage = entries[index.coerceIn(0, entries.lastIndex)]
    }
}

internal const val TAB_SWITCH_MILLIS = LedgerMotion.Medium

internal fun <T> tabSwitchTween() = LedgerMotion.medium<T>()

internal fun pageFadeAlpha(currentPage: Int, currentPageOffsetFraction: Float, page: Int): Float {
    val distance = abs((currentPage - page) + currentPageOffsetFraction).coerceIn(0f, 1f)
    return 1f - 0.28f * distance
}

internal fun isFilteredTransactionRoute(route: String?): Boolean {
    if (route == null) return false
    return route == Routes.TRANSACTIONS_PATTERN || route.startsWith("transactions?")
}

val LocalSelectTopLevelPage = staticCompositionLocalOf<(TopLevelPage) -> Unit> { {} }

@Composable
fun AppNavHost(appSession: AppSession, pendingNavigation: PendingNavigation) {
    val navController = rememberNavController()
    val pagerState = rememberPagerState(pageCount = { TopLevelPage.entries.size })
    val scope = rememberCoroutineScope()
    val gate = remember { PagerGestureGate() }
    val pendingPage = remember { mutableStateOf<Int?>(null) }
    val selectPage: (TopLevelPage) -> Unit = remember(navController, pagerState, scope, pendingPage) {
        { page ->
            val onMain = navController.currentDestination?.route == Routes.MAIN
            if (!onMain) {
                pendingPage.value = page.ordinal
                navController.popBackStack(Routes.MAIN, inclusive = false)
            } else {
                scope.launch {
                    pagerState.animateScrollToPage(page.ordinal, animationSpec = tabSwitchTween())
                }
            }
        }
    }
    LaunchedEffect(pendingNavigation) {
        pendingNavigation.routes.collect { route ->
            if (!route.isNullOrBlank()) {
                navController.navigate(route) { launchSingleTop = true }
                pendingNavigation.consume()
            }
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.MAIN || isFilteredTransactionRoute(currentRoute)

    CompositionLocalProvider(
        LocalPagerGestureGate provides gate,
        LocalSelectTopLevelPage provides selectPage,
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    PagerNavigationBar(
                        pagerState = pagerState,
                        currentRoute = currentRoute,
                        onSelect = selectPage,
                        onCreate = { navController.navigate(Routes.transactionNew()) },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.MAIN,
                modifier = Modifier
                    .padding(padding)
                    // Scaffold already applied these insets. Consuming them keeps
                    // nested bars from adding a second status-bar gap.
                    .consumeWindowInsets(padding)
                    .background(MaterialTheme.colorScheme.background),
                enterTransition = {
                    slideInHorizontally(tabSwitchTween()) { it } + fadeIn(tabSwitchTween())
                },
                exitTransition = {
                    slideOutHorizontally(tabSwitchTween()) { -it / 4 } + fadeOut(tabSwitchTween())
                },
                popEnterTransition = {
                    slideInHorizontally(tabSwitchTween()) { -it / 4 } + fadeIn(tabSwitchTween())
                },
                popExitTransition = {
                    slideOutHorizontally(tabSwitchTween()) { it } + fadeOut(tabSwitchTween())
                },
            ) {
                composable(Routes.MAIN) {
                    TopLevelPager(
                        appSession = appSession,
                        navController = navController,
                        pagerState = pagerState,
                        gestureGate = gate,
                        pendingPage = pendingPage.value,
                        onPendingPageConsumed = { pendingPage.value = null },
                    )
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
                composable(Routes.IMPORT) {
                    BillImportScreen(navController = navController)
                }
                composable(Routes.AUTO_RECORD) {
                    AutoRecordScreen(appSession = appSession, navController = navController)
                }
                composable(Routes.DRAFTS) {
                    DraftInboxScreen(navController = navController)
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
}

@Composable
private fun PagerNavigationBar(
    pagerState: PagerState,
    currentRoute: String?,
    onSelect: (TopLevelPage) -> Unit,
    onCreate: () -> Unit,
) {
    val selectedPage = if (isFilteredTransactionRoute(currentRoute)) {
        TopLevelPage.Transactions
    } else {
        TopLevelPage.fromIndex(pagerState.targetPage)
    }
    LedgerBottomBar(
        selectedPage = selectedPage,
        onSelect = onSelect,
        onCreate = onCreate,
    )
}

@Composable
private fun TopLevelPager(
    appSession: AppSession,
    navController: NavHostController,
    pagerState: PagerState,
    gestureGate: PagerGestureGate,
    pendingPage: Int?,
    onPendingPageConsumed: () -> Unit,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(pendingPage) {
        val target = pendingPage ?: return@LaunchedEffect
        pagerState.animateScrollToPage(target, animationSpec = tabSwitchTween())
        onPendingPageConsumed()
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        beyondViewportPageCount = 1,
        userScrollEnabled = gestureGate.userScrollEnabled,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = tabSwitchTween(),
        ),
        key = { TopLevelPage.entries[it].route },
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            saveableStateHolder.SaveableStateProvider(TopLevelPage.entries[page].route) {
                when (TopLevelPage.entries[page]) {
                    TopLevelPage.Home -> HomeScreen(appSession = appSession, navController = navController)
                    TopLevelPage.Transactions ->
                        TransactionListScreen(appSession = appSession, navController = navController)
                    TopLevelPage.Statistics ->
                        StatisticsScreen(appSession = appSession, navController = navController)
                    TopLevelPage.Settings ->
                        SettingsScreen(appSession = appSession, navController = navController)
                }
            }
        }
    }
}

@Composable
internal fun LedgerBottomBar(
    selectedPage: TopLevelPage,
    onSelect: (TopLevelPage) -> Unit,
    onCreate: () -> Unit,
) {
    val haptic = rememberLedgerHaptic()
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column {
        LedgerHairline()
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            TopLevelPage.entries.take(2).forEach { page ->
                NavigationBarItem(
                    selected = selectedPage == page,
                    onClick = { onSelect(page) },
                    icon = { Icon(page.icon, contentDescription = page.label) },
                    label = { Text(page.label) },
                    colors = itemColors,
                )
            }
            NavigationBarItem(
                selected = false,
                onClick = {
                    haptic()
                    onCreate()
                },
                colors = itemColors,
                icon = {
                    Surface(
                        onClick = {
                            haptic()
                            onCreate()
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Add, contentDescription = "记一笔")
                        }
                    }
                },
                label = { Text("记一笔") },
            )
            TopLevelPage.entries.drop(2).forEach { page ->
                NavigationBarItem(
                    selected = selectedPage == page,
                    onClick = { onSelect(page) },
                    icon = { Icon(page.icon, contentDescription = page.label) },
                    label = { Text(page.label) },
                    colors = itemColors,
                )
            }
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
