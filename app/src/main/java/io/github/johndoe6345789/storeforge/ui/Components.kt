package io.github.johndoe6345789.storeforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.github.johndoe6345789.storeforge.AppStatus
import io.github.johndoe6345789.storeforge.Task
import io.github.johndoe6345789.storeforge.catalog.CatalogApp
import java.util.Locale

private val AvatarColors = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935), Color(0xFF8E24AA),
    Color(0xFFFB8C00), Color(0xFF00897B), Color(0xFF3949AB), Color(0xFFD81B60),
)

/** The app's icon, or a coloured initial while it loads or when there is none. */
@Composable
fun AppIcon(app: CatalogApp, size: Dp, modifier: Modifier = Modifier) {
    var loaded by remember(app.icon) { mutableStateOf(false) }
    val shape = RoundedCornerShape(size * 0.22f)
    Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (!loaded) {
            Box(
                Modifier.matchParentSize().background(AvatarColors[Math.floorMod(app.packageName.hashCode(), AvatarColors.size)]),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.45f).sp,
                )
            }
        }
        if (app.icon != null) {
            AsyncImage(
                model = app.icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                onState = { loaded = it is AsyncImagePainter.State.Success },
            )
        }
    }
}

/** One row in an app list, Play Store style: icon, name, a line of detail, and a quick action. */
@Composable
fun AppListItem(
    app: CatalogApp,
    status: AppStatus,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, 56.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                statusLine(app, status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        QuickAction(status, onInstall, onOpen)
    }
}

@Composable
private fun QuickAction(status: AppStatus, onInstall: () -> Unit, onOpen: () -> Unit) {
    when (status) {
        is AppStatus.NotInstalled -> FilledTonalButton(onClick = onInstall) { Text("Install") }
        is AppStatus.UpdateAvailable -> FilledTonalButton(onClick = onInstall) { Text("Update") }
        is AppStatus.Installed -> TextButton(onClick = onOpen) { Text("Open") }
        is AppStatus.Busy -> {
            val fraction = (status.task as? Task.Downloading)?.fraction
            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                if (fraction != null) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        }
        AppStatus.Checking, is AppStatus.Unavailable -> Unit
    }
}

fun statusLine(app: CatalogApp, status: AppStatus): String = when (status) {
    is AppStatus.Busy -> taskLabel(status.task)
    is AppStatus.UpdateAvailable -> "Update to ${status.release.versionName}" +
        (status.release.size?.let { " • ${formatBytes(it)}" } ?: "")
    is AppStatus.Installed -> "Installed • ${status.installed.versionName ?: ""}".trimEnd(' ', '•')
    is AppStatus.Unavailable -> status.reason
    else -> listOf(app.developer, app.category).joinToString(" • ")
}

fun taskLabel(task: Task): String = when (task) {
    is Task.Downloading -> if (task.total > 0) {
        "Downloading ${formatBytes(task.received)} of ${formatBytes(task.total)}"
    } else {
        "Downloading ${formatBytes(task.received)}"
    }
    Task.Installing -> "Installing…"
    Task.Uninstalling -> "Uninstalling…"
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), if (value < 10) "%.1f %s" else "%.0f %s", value, units[unit])
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        action()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
