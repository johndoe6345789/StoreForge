package io.github.johndoe6345789.storeforge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.johndoe6345789.storeforge.StoreState
import io.github.johndoe6345789.storeforge.catalog.CatalogApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: StoreState,
    actions: StoreActions,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = state.apps.map { it.category }.distinct().sorted()
    val featured = state.catalog?.featured.orEmpty().mapNotNull { state.app(it) }
    val listed = state.apps.filter { category == null || it.category == category }.sortedBy { it.name.lowercase() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.catalog?.name ?: "StoreForge") },
            actions = {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            },
        )
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                state.error?.let { error ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = actions.refresh, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
                        }
                    }
                }
                if (state.catalog == null && state.error == null) {
                    item { EmptyState("Loading apps…") }
                }

                if (featured.isNotEmpty() && category == null) {
                    item { SectionHeader("Featured") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(featured, key = { it.packageName }) { app ->
                                FeaturedCard(app, onClick = { onOpenApp(app.packageName) })
                            }
                        }
                    }
                }

                if (categories.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                            }
                            items(categories) { name ->
                                FilterChip(
                                    selected = category == name,
                                    onClick = { category = if (category == name) null else name },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }
                }

                if (state.catalog != null) {
                    item { SectionHeader(category ?: "All apps") }
                    if (listed.isEmpty()) item { EmptyState("No apps here yet") }
                }
                items(listed, key = { it.packageName }) { app ->
                    AppListItem(
                        app = app,
                        status = state.statusOf(app),
                        onClick = { onOpenApp(app.packageName) },
                        onInstall = { actions.install(app.packageName) },
                        onOpen = { actions.open(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedCard(app: CatalogApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(280.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            AppIcon(app, 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
