package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ReminderApplication
import kotlinx.coroutines.launch
import com.example.ui.dashboard.FinancialDashboardScreen
import com.example.ui.home.HomeScreen
import com.example.ui.ledger.AddVoucherScreen
import com.example.ui.ledger.LedgerDetailScreen
import com.example.ui.ledger.LedgerScreen
import com.example.ui.more.MoreScreen
import com.example.ui.parties.AddPartyScreen
import com.example.ui.parties.PartiesScreen
import com.example.ui.reminders.AddReminderScreen
import com.example.ui.reminders.RemindersScreen
import com.example.ui.security.AppLockScreen
import com.example.ui.splash.SplashScreen

import com.example.ui.components.StartupPermissionDialog

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as ReminderApplication).container
    val securityManager = appContainer.securityManager
    val isLocked by securityManager.isLocked.collectAsStateWithLifecycle()

    var showSplashScreen by rememberSaveable { mutableStateOf(true) }

    if (showSplashScreen) {
        SplashScreen(
            onTimeout = {
                showSplashScreen = false
            }
        )
    } else if (securityManager.isAppLockEnabled() && isLocked) {
        AppLockScreen(
            securityManager = securityManager,
            onUnlocked = {
                securityManager.unlockSession()
            }
        )
    } else {
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val coroutineScope = rememberCoroutineScope()

        val items = listOf(
            Screen.Home,
            Screen.FinancialDashboard,
            Screen.Reminders,
            Screen.Ledger,
            Screen.Parties,
            Screen.Settings
        )

        val onOpenDrawer = { coroutineScope.launch { drawerState.open() } }

        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Navigation",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationDrawerItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) { 
                    HomeScreen(
                        onOpenDrawer = { onOpenDrawer() },
                        onNavigateToReminders = { filter ->
                            navController.navigate("reminders?filter=$filter")
                        },
                        onNavigateToParties = {
                            navController.navigate(Screen.Parties.route)
                        },
                        onNavigateToFinancialDashboard = {
                            navController.navigate(Screen.FinancialDashboard.route)
                        }
                    ) 
                }
                composable(Screen.FinancialDashboard.route) {
                    FinancialDashboardScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenDrawer = { onOpenDrawer() },
                        onNavigateToParties = { navController.navigate(Screen.Parties.route) },
                        onNavigateToLedger = { navController.navigate(Screen.Ledger.route) }
                    )
                }
                composable(
                    route = "reminders?filter={filter}",
                    arguments = listOf(navArgument("filter") { 
                        type = NavType.StringType 
                        defaultValue = "All"
                    })
                ) { backStackEntry ->
                    val filter = backStackEntry.arguments?.getString("filter") ?: "All"
                    RemindersScreen(
                        initialFilter = filter,
                        onAddReminderClick = { navController.navigate("add_reminder") },
                        onEditReminderClick = { reminderId -> navController.navigate("edit_reminder/$reminderId") },
                        onOpenDrawer = { onOpenDrawer() }
                    ) 
                }
                composable(Screen.Reminders.route) { 
                    RemindersScreen(
                        initialFilter = "All",
                        onAddReminderClick = { navController.navigate("add_reminder") },
                        onEditReminderClick = { reminderId -> navController.navigate("edit_reminder/$reminderId") },
                        onOpenDrawer = { onOpenDrawer() }
                    ) 
                }
                composable(Screen.Ledger.route) { 
                    LedgerScreen(
                        onAddVoucherClick = { navController.navigate("add_voucher") },
                        onPartyClick = { partyId -> navController.navigate("ledger_detail/$partyId") },
                        onOpenDrawer = { onOpenDrawer() }
                    ) 
                }
                composable(Screen.Parties.route) { 
                    PartiesScreen(
                        onAddPartyClick = { navController.navigate("add_party") },
                        onEditPartyClick = { partyId -> navController.navigate("edit_party/$partyId") },
                        onPartyClick = { partyId -> navController.navigate("ledger_detail/$partyId") },
                        onOpenDrawer = { onOpenDrawer() }
                    ) 
                }
                composable(Screen.Settings.route) { MoreScreen(onOpenDrawer = { onOpenDrawer() }) }
                composable("more") { MoreScreen(onOpenDrawer = { onOpenDrawer() }) }
                composable("add_party") { AddPartyScreen(partyId = 0L, onBackClick = { navController.popBackStack() }) }
            composable(
                route = "edit_party/{partyId}",
                arguments = listOf(navArgument("partyId") { type = NavType.LongType })
            ) { backStackEntry ->
                val partyId = backStackEntry.arguments?.getLong("partyId") ?: 0L
                AddPartyScreen(
                    partyId = partyId,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("add_voucher") {
                AddVoucherScreen(
                    onBackClick = { navController.popBackStack() },
                    onNavigateToAddParty = { navController.navigate("add_party") }
                )
            }
            composable(
                route = "ledger_detail/{partyId}",
                arguments = listOf(navArgument("partyId") { type = NavType.LongType })
            ) { backStackEntry ->
                val partyId = backStackEntry.arguments?.getLong("partyId") ?: 0L
                LedgerDetailScreen(
                    partyId = partyId,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("add_reminder") { 
                AddReminderScreen(
                    reminderId = 0L,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToAddParty = { navController.navigate("add_party") }
                ) 
            }
            composable(
                route = "edit_reminder/{reminderId}",
                arguments = listOf(navArgument("reminderId") { type = NavType.LongType })
            ) { backStackEntry ->
                val reminderId = backStackEntry.arguments?.getLong("reminderId") ?: 0L
                AddReminderScreen(
                    reminderId = reminderId,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToAddParty = { navController.navigate("add_party") }
                )
            }
        }
    }
}
            StartupPermissionDialog(isReady = true)
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object FinancialDashboard : Screen("financial_dashboard", "Financial Health", Icons.Filled.TrendingUp)
    object Reminders : Screen("reminders", "Reminders", Icons.Filled.Notifications)
    object Ledger : Screen("ledger", "Ledger", Icons.Filled.List)
    object Parties : Screen("parties", "Parties & Accounts", Icons.Filled.Person)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object More : Screen("settings", "Settings", Icons.Filled.Settings)
}
