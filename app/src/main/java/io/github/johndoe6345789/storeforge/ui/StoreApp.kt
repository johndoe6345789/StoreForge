package io.github.johndoe6345789.storeforge.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.johndoe6345789.storeforge.StoreViewModel
import io.github.johndoe6345789.storeforge.UiEvent

/** Everything a screen can ask the store to do. */
class StoreActions(
    val refresh: () -> Unit,
    val install: (String) -> Unit,
    val uninstall: (String) -> Unit,
    val cancel: (String) -> Unit,
    val open: (String) -> Unit,
    val updateAll: () -> Unit,
)

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Apps", Icons.Filled.Storefront),
    Search("search", "Search", Icons.Filled.Search),
    Library("library", "My apps", Icons.Filled.Apps),
}

@Composable
fun StoreApp(viewModel: StoreViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var permissionIntent by remember { mutableStateOf<Intent?>(null) }
    var canInstallApps by remember { mutableStateOf(viewModel.canInstallApps()) }

    val actions = remember(viewModel) {
        StoreActions(
            refresh = { viewModel.refresh(force = true) },
            install = viewModel::install,
            uninstall = viewModel::uninstall,
            cancel = viewModel::cancel,
            open = viewModel::open,
            updateAll = viewModel::updateAll,
        )
    }

    // Apps can be installed or removed outside the store, and the install permission can
    // change in Settings, so re-check whenever the user comes back.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshInstalled()
        canInstallApps = viewModel.canInstallApps()
        onPauseOrDispose {}
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbar.showSnackbar(event.text)
                is UiEvent.Launch -> try {
                    context.startActivity(event.intent)
                } catch (_: ActivityNotFoundException) {
                    snackbar.showSnackbar("Couldn't open the app")
                }
                is UiEvent.NeedInstallPermission -> permissionIntent = event.settings
            }
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = Tab.entries.any { it.route == currentRoute }
    val openApp: (String) -> Unit = { navController.navigate("app/$it") }

    Scaffold(
        // Screens draw their own top app bars, which handle the status bar themselves.
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val updates = state.updates.size
                                if (tab == Tab.Library && updates > 0) {
                                    BadgedBox(badge = { Badge { Text("$updates") } }) { Icon(tab.icon, null) }
                                } else {
                                    Icon(tab.icon, null)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Tab.Home.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Home.route) {
                HomeScreen(state, actions, onOpenApp = openApp, onOpenSettings = { navController.navigate("settings") })
            }
            composable(Tab.Search.route) { SearchScreen(state, actions, onOpenApp = openApp) }
            composable(Tab.Library.route) { LibraryScreen(state, actions, onOpenApp = openApp) }
            composable("app/{packageName}", arguments = listOf(navArgument("packageName") { type = NavType.StringType })) { entry ->
                DetailScreen(
                    state = state,
                    packageName = entry.arguments?.getString("packageName").orEmpty(),
                    actions = actions,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings") {
                SettingsScreen(
                    catalogUrl = viewModel.catalogUrl,
                    canInstallApps = canInstallApps,
                    onSaveCatalogUrl = viewModel::setCatalogUrl,
                    onOpenInstallPermission = { context.startActivity(viewModel.installPermissionSettings()) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    permissionIntent?.let { intent ->
        AlertDialog(
            onDismissRequest = { permissionIntent = null },
            title = { Text("Allow StoreForge to install apps") },
            text = {
                Text("Android only lets app stores you trust install apps. Turn on \"Allow from this source\" for StoreForge, then come back and tap Install again.")
            },
            confirmButton = {
                TextButton(onClick = {
                    permissionIntent = null
                    context.startActivity(intent)
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { permissionIntent = null }) { Text("Not now") } },
        )
    }
}
