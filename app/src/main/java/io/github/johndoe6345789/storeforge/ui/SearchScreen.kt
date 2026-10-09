package io.github.johndoe6345789.storeforge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import io.github.johndoe6345789.storeforge.StoreState
import io.github.johndoe6345789.storeforge.catalog.CatalogApp

@Composable
fun SearchScreen(state: StoreState, actions: StoreActions, onOpenApp: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val results = remember(query, state.apps) { search(state.apps, query) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isNotBlank() && results.isEmpty()) item { EmptyState("No apps match \"$query\"") }
            items(results, key = { it.packageName }) { app ->
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

/** Name matches rank first, then developer/category/tags, then the description. */
fun search(apps: List<CatalogApp>, query: String): List<CatalogApp> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return apps.sortedBy { it.name.lowercase() }
    return apps.mapNotNull { app ->
        val name = app.name.lowercase()
        val meta = (listOf(app.developer, app.category, app.packageName) + app.tags).joinToString(" ").lowercase()
        val text = "${app.summary} ${app.description.orEmpty()}".lowercase()
        var score = 0
        for (term in terms) {
            score += when {
                name.startsWith(term) -> 100
                term in name -> 50
                term in meta -> 20
                term in text -> 5
                else -> return@mapNotNull null
            }
        }
        app to score
    }.sortedWith(compareByDescending<Pair<CatalogApp, Int>> { it.second }.thenBy { it.first.name.lowercase() })
        .map { it.first }
}
