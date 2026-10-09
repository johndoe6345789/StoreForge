package io.github.johndoe6345789.storeforge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.johndoe6345789.storeforge.AppStatus
import io.github.johndoe6345789.storeforge.ReleaseState
import io.github.johndoe6345789.storeforge.StoreState
import io.github.johndoe6345789.storeforge.Task
import io.github.johndoe6345789.storeforge.catalog.CatalogApp
import io.github.johndoe6345789.storeforge.catalog.Release
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(state: StoreState, packageName: String, actions: StoreActions, onBack: () -> Unit) {
    val app = state.app(packageName)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        if (app == null) {
            EmptyState(if (state.catalog == null) "Loading…" else "This app is no longer in the catalog")
            return@Column
        }
        val status = state.statusOf(app)
        val release = (state.releases[app.packageName] as? ReleaseState.Ready)?.release

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Header(app)
            Stats(app, release)
            ActionArea(app, status, actions)

            if (app.screenshots.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(app.screenshots) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Screenshot",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(320.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }

            SectionHeader("About this app")
            Text(
                app.description ?: app.summary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            release?.releaseNotes?.let { notes ->
                SectionHeader("What's new")
                Text(
                    "Version ${release.versionName}" + (formatDate(release.publishedAt)?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(notes.trim(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }

            SectionHeader("App info")
            AppInfo(app, status, release)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(app: CatalogApp) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, 80.dp)
        Column(Modifier.padding(start = 20.dp)) {
            Text(app.name, style = MaterialTheme.typography.headlineSmall)
            Text(app.developer, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(app.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stats(app: CatalogApp, release: Release?) {
    val stats = listOfNotNull(
        release?.let { it.versionName to "Version" },
        release?.size?.let { formatBytes(it) to "Size" },
        app.category to "Category",
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        stats.forEach { (value, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(104.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, textAlign = TextAlign.Center)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActionArea(app: CatalogApp, status: AppStatus, actions: StoreActions) {
    var confirmUninstall by remember { mutableStateOf(false) }
    val pkg = app.packageName
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        when (status) {
            AppStatus.Checking -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Checking the latest release…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            is AppStatus.Unavailable -> {
                Text(status.reason, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = actions.refresh, modifier = Modifier.padding(top = 8.dp)) { Text("Try again") }
            }
            is AppStatus.NotInstalled -> Button(onClick = { actions.install(pkg) }, modifier = Modifier.fillMaxWidth()) {
                Text("Install")
            }
            is AppStatus.Installed -> TwoButtons(
                secondary = "Uninstall" to { confirmUninstall = true },
                primary = "Open" to { actions.open(pkg) },
            )
            is AppStatus.UpdateAvailable -> TwoButtons(
                secondary = "Uninstall" to { confirmUninstall = true },
                primary = "Update" to { actions.install(pkg) },
            )
            is AppStatus.Busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val fraction = (status.task as? Task.Downloading)?.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(taskLabel(status.task), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                IconButton(onClick = { actions.cancel(pkg) }) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("Uninstall ${app.name}?") },
            text = { Text("The app and its data will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { confirmUninstall = false; actions.uninstall(pkg) }) { Text("Uninstall") }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TwoButtons(secondary: Pair<String, () -> Unit>, primary: Pair<String, () -> Unit>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = secondary.second, modifier = Modifier.weight(1f)) { Text(secondary.first) }
        Button(onClick = primary.second, modifier = Modifier.weight(1f)) { Text(primary.first) }
    }
}

@Composable
private fun AppInfo(app: CatalogApp, status: AppStatus, release: Release?) {
    val uriHandler = LocalUriHandler.current
    val installed = when (status) {
        is AppStatus.Installed -> status.installed
        is AppStatus.UpdateAvailable -> status.installed
        is AppStatus.Busy -> status.installed
        else -> null
    }
    val rows = listOfNotNull(
        "Package" to app.packageName,
        installed?.let { "Installed version" to (it.versionName ?: it.versionCode.toString()) },
        release?.let { "Latest version" to it.versionName },
        formatDate(release?.publishedAt)?.let { "Released" to it },
        app.license?.let { "License" to it },
        app.github?.let { "Source" to "github.com/${it.repo}" },
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
            }
            HorizontalDivider()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            // Only web links: these come from the catalog and are opened with ACTION_VIEW.
            release?.pageUrl?.takeIf(::isWebLink)?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url) }) { Text("Release page") }
            }
            app.homepage?.takeIf(::isWebLink)?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url) }) { Text("Website") }
            }
        }
    }
}

private fun isWebLink(url: String) = url.startsWith("https://") || url.startsWith("http://")

private fun formatDate(iso: String?): String? = iso?.let {
    runCatching { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date.from(Instant.parse(it))) }.getOrNull()
}
