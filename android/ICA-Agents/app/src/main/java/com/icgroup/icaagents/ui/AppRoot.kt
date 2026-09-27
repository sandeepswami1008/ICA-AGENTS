package com.icgroup.icaagents.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.icgroup.icaagents.data.SessionStore
import com.icgroup.icaagents.ui.invoices.InvoicesScreen
import com.icgroup.icaagents.ui.invoices.InvoicesViewModel
import com.icgroup.icaagents.ui.leads.LeadsScreen
import com.icgroup.icaagents.ui.leads.LeadsViewModel
import com.icgroup.icaagents.ui.login.LoginScreen
import com.icgroup.icaagents.ui.login.LoginViewModel
import com.icgroup.icaagents.ui.payments.PaymentsScreen
import com.icgroup.icaagents.ui.payments.PaymentsViewModel
import com.icgroup.icaagents.ui.profile.ProfileScreen
import com.icgroup.icaagents.ui.profile.ProfileViewModel
import com.icgroup.icaagents.ui.register.RegisterScreen
import com.icgroup.icaagents.ui.register.RegisterViewModel

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
}

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val bottomTabs = listOf(
    BottomTab("leads", "Leads", Icons.Filled.List),
    BottomTab("invoices", "Invoices", Icons.Filled.Receipt),
    BottomTab("payments", "Payments", Icons.Filled.Payments),
    BottomTab("profile", "Profile", Icons.Filled.Person)
)

@Composable
fun AppRoot(factory: ViewModelFactory, session: SessionStore) {
    val rootNav = rememberNavController()
    val startDestination = if (session.isLoggedIn) Routes.HOME else Routes.LOGIN

    NavHost(navController = rootNav, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel(factory = factory)
            LoginScreen(
                vm = vm,
                onLoggedIn = { rootNav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onGoToRegister = { rootNav.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            val vm: RegisterViewModel = viewModel(factory = factory)
            RegisterScreen(vm = vm, onDone = { rootNav.popBackStack() })
        }
        composable(Routes.HOME) {
            HomeWithBottomNav(factory) {
                rootNav.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } }
            }
        }
    }
}

@Composable
private fun HomeWithBottomNav(factory: ViewModelFactory, onLoggedOut: () -> Unit) {
    val nav = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by nav.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "leads", modifier = androidx.compose.ui.Modifier.padding(padding)) {
            composable("leads") { LeadsScreen(viewModel(factory = factory) as LeadsViewModel) }
            composable("invoices") { InvoicesScreen(viewModel(factory = factory) as InvoicesViewModel) }
            composable("payments") { PaymentsScreen(viewModel(factory = factory) as PaymentsViewModel) }
            composable("profile") {
                ProfileScreen(viewModel(factory = factory) as ProfileViewModel, onLoggedOut = onLoggedOut)
            }
        }
    }
}
