package io.github.johndoe6345789.storeforge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.johndoe6345789.storeforge.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    catalogUrl: String,
    canInstallApps: Boolean,
    onSaveCatalogUrl: (String) -> Unit,
    onOpenInstallPermission: () -> Unit,
    onBack: () -> Unit,
) {
    var url by rememberSaveable(catalogUrl) { mutableStateOf(catalogUrl) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Catalog", style = MaterialTheme.typography.titleMedium)
            Text(
                "The apps.json file StoreForge lists apps from.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Catalog URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { url = BuildConfig.CATALOG_URL; onSaveCatalogUrl(BuildConfig.CATALOG_URL) }) {
                    Text("Reset")
                }
                Button(onClick = { onSaveCatalogUrl(url) }, enabled = url.startsWith("https://") && url != catalogUrl) {
                    Text("Save")
                }
            }

            Text("Installing apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                if (canInstallApps) "StoreForge is allowed to install apps." else "Android needs your permission before StoreForge can install apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenInstallPermission) { Text(if (canInstallApps) "Manage permission" else "Allow installing apps") }

            Text(
                "StoreForge ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
