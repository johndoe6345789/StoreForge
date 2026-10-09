package io.github.johndoe6345789.storeforge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.johndoe6345789.storeforge.AppStatus
import io.github.johndoe6345789.storeforge.StoreState

/** "Manage apps": updates waiting to be installed, and everything installed from the store. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(state: StoreState, actions: StoreActions, onOpenApp: (String) -> Unit) {
    val updates = state.updates
    val busy = state.apps.filter { it.packageName in state.tasks && it.packageName !in state.installed }
    val upToDate = state.installedApps.filter { state.statusOf(it) !is AppStatus.UpdateAvailable }.sortedBy { it.name.lowercase() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("My apps") })
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (updates.isEmpty() && upToDate.isEmpty() && busy.isEmpty()) {
                    item { EmptyState("Apps you install from StoreForge show up here") }
                }

                if (updates.isNotEmpty()) {
                    item {
                        SectionHeader("Updates available (${updates.size})") {
                            FilledTonalButton(onClick = actions.updateAll) { Text("Update all") }
                        }
                    }
                    items(updates, key = { "update-" + it.packageName }) { app ->
                        AppListItem(app, state.statusOf(app), { onOpenApp(app.packageName) }, { actions.install(app.packageName) }, { actions.open(app.packageName) })
                    }
                }

                if (busy.isNotEmpty()) {
                    item { SectionHeader("Installing") }
                    items(busy, key = { "busy-" + it.packageName }) { app ->
                        AppListItem(app, state.statusOf(app), { onOpenApp(app.packageName) }, { actions.install(app.packageName) }, { actions.open(app.packageName) })
                    }
                }

                if (upToDate.isNotEmpty()) {
                    item { SectionHeader(if (updates.isEmpty()) "Installed • all up to date" else "Installed") }
                    items(upToDate, key = { "installed-" + it.packageName }) { app ->
                        AppListItem(app, state.statusOf(app), { onOpenApp(app.packageName) }, { actions.install(app.packageName) }, { actions.open(app.packageName) })
                    }
                }
            }
        }
    }
}
