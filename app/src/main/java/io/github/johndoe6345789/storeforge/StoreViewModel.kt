package io.github.johndoe6345789.storeforge

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.johndoe6345789.storeforge.catalog.Catalog
import io.github.johndoe6345789.storeforge.catalog.CatalogApp
import io.github.johndoe6345789.storeforge.catalog.Release
import io.github.johndoe6345789.storeforge.catalog.isNewerVersion
import io.github.johndoe6345789.storeforge.install.InstalledApp
import io.github.johndoe6345789.storeforge.install.PackageResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** What the store knows about one app's latest release. */
sealed interface ReleaseState {
    data object Loading : ReleaseState
    data class Ready(val release: Release) : ReleaseState
    data class Failed(val message: String) : ReleaseState
}

/** A download/install/uninstall in progress. */
sealed interface Task {
    data class Downloading(val received: Long, val total: Long) : Task {
        val fraction: Float? get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else null
    }
    data object Installing : Task
    data object Uninstalling : Task
}

/** The state that decides which buttons an app shows. */
sealed interface AppStatus {
    data object Checking : AppStatus
    data class Unavailable(val reason: String) : AppStatus
    data class NotInstalled(val release: Release) : AppStatus
    data class Installed(val installed: InstalledApp, val release: Release?) : AppStatus
    data class UpdateAvailable(val installed: InstalledApp, val release: Release) : AppStatus
    data class Busy(val task: Task, val installed: InstalledApp?) : AppStatus
}

data class StoreState(
    val catalog: Catalog? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val releases: Map<String, ReleaseState> = emptyMap(),
    val installed: Map<String, InstalledApp> = emptyMap(),
    val tasks: Map<String, Task> = emptyMap(),
) {
    val apps: List<CatalogApp> get() = catalog?.apps.orEmpty()

    fun app(packageName: String): CatalogApp? = apps.firstOrNull { it.packageName == packageName }

    fun statusOf(app: CatalogApp): AppStatus {
        val installed = installed[app.packageName]
        tasks[app.packageName]?.let { return AppStatus.Busy(it, installed) }
        val release = releases[app.packageName]
        if (installed != null) {
            val ready = (release as? ReleaseState.Ready)?.release
            return if (ready != null && isUpdate(ready, installed)) {
                AppStatus.UpdateAvailable(installed, ready)
            } else {
                AppStatus.Installed(installed, ready)
            }
        }
        return when (release) {
            is ReleaseState.Ready -> AppStatus.NotInstalled(release.release)
            is ReleaseState.Failed -> AppStatus.Unavailable(release.message)
            ReleaseState.Loading, null -> AppStatus.Checking
        }
    }

    val updates: List<CatalogApp> get() = apps.filter { statusOf(it) is AppStatus.UpdateAvailable }

    val installedApps: List<CatalogApp> get() = apps.filter { it.packageName in installed }
}

fun isUpdate(release: Release, installed: InstalledApp): Boolean {
    release.versionCode?.let { return it > installed.versionCode }
    return isNewerVersion(release.versionName, installed.versionName ?: return false)
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Launch(val intent: Intent) : UiEvent
    /** The user must allow StoreForge to install apps before anything can be installed. */
    data class NeedInstallPermission(val settings: Intent) : UiEvent
}

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StoreForgeApp
    private val repository = app.catalogRepository
    private val installer = app.installer
    private val downloader = app.downloader

    private val _state = MutableStateFlow(StoreState())
    val state: StateFlow<StoreState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val jobs = mutableMapOf<String, Job>()
    private var refreshJob: Job? = null

    val catalogUrl: String get() = app.settings.catalogUrl

    fun canInstallApps(): Boolean = installer.canInstallApps()

    fun installPermissionSettings(): Intent = installer.installPermissionSettings()

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            try {
                val catalog = repository.loadCatalog(catalogUrl, refresh = force)
                _state.update { current ->
                    current.copy(
                        catalog = catalog,
                        releases = catalog.apps.associate { a ->
                            a.packageName to (current.releases[a.packageName] ?: ReleaseState.Loading)
                        },
                    )
                }
                refreshInstalled()
                val resolved = repository.resolveReleases(catalog.apps, refresh = force)
                _state.update { current ->
                    current.copy(releases = resolved.mapValues { (_, result) ->
                        result.fold(
                            onSuccess = { ReleaseState.Ready(it) },
                            onFailure = { ReleaseState.Failed(it.message ?: "Unavailable") },
                        )
                    })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn't load the catalog: ${e.message}") }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /** Re-reads which apps are installed; called on resume since apps can be removed outside the store. */
    fun refreshInstalled() {
        val apps = _state.value.apps
        val installed = apps.mapNotNull { installer.installedApp(it.packageName) }.associateBy { it.packageName }
        _state.update { it.copy(installed = installed) }
    }

    fun setCatalogUrl(url: String) {
        app.settings.catalogUrl = url
        refreshJob?.cancel()
        _state.update { StoreState(installed = it.installed, tasks = it.tasks) }
        refresh(force = true)
    }

    fun install(packageName: String) {
        val current = _state.value
        val catalogApp = current.app(packageName) ?: return
        val release = (current.releases[packageName] as? ReleaseState.Ready)?.release ?: return
        if (jobs[packageName]?.isActive == true) return
        if (!installer.canInstallApps()) {
            _events.trySend(UiEvent.NeedInstallPermission(installer.installPermissionSettings()))
            return
        }

        jobs[packageName] = viewModelScope.launch {
            val apk = File(app.cacheDir, "apks/$packageName.apk")
            try {
                setTask(packageName, Task.Downloading(0, release.size ?: 0))
                downloader.download(release.apkUrl, apk, release.sha256) { received, total ->
                    setTask(packageName, Task.Downloading(received, total))
                }
                setTask(packageName, Task.Installing)
                val info = installer.inspectApk(apk, packageName)
                val installed = installer.installedApp(packageName)
                if (installed != null && PackageInfoCompat.getLongVersionCode(info) < installed.versionCode) {
                    throw IllegalStateException("The release is older than the installed version")
                }
                when (val result = installer.install(apk, packageName)) {
                    PackageResult.Success -> {
                        val verb = if (installed != null) "updated" else "installed"
                        _events.trySend(UiEvent.Message("${catalogApp.name} $verb"))
                    }
                    is PackageResult.Failure -> _events.trySend(UiEvent.Message("${catalogApp.name}: ${result.message}"))
                }
            } catch (e: CancellationException) {
                _events.trySend(UiEvent.Message("${catalogApp.name}: cancelled"))
                throw e
            } catch (e: Exception) {
                _events.trySend(UiEvent.Message("${catalogApp.name}: ${e.message ?: "install failed"}"))
            } finally {
                apk.delete()
                setTask(packageName, null)
                jobs.remove(packageName)
                refreshInstalled()
            }
        }
    }

    fun updateAll() {
        _state.value.updates.forEach { install(it.packageName) }
    }

    fun cancel(packageName: String) {
        jobs[packageName]?.cancel()
    }

    fun uninstall(packageName: String) {
        val catalogApp = _state.value.app(packageName) ?: return
        if (jobs[packageName]?.isActive == true) return
        jobs[packageName] = viewModelScope.launch {
            try {
                setTask(packageName, Task.Uninstalling)
                when (val result = installer.uninstall(packageName)) {
                    PackageResult.Success -> _events.trySend(UiEvent.Message("${catalogApp.name} uninstalled"))
                    is PackageResult.Failure -> _events.trySend(UiEvent.Message("${catalogApp.name}: ${result.message}"))
                }
            } finally {
                setTask(packageName, null)
                jobs.remove(packageName)
                refreshInstalled()
            }
        }
    }

    fun open(packageName: String) {
        val intent = installer.launchIntent(packageName)
        _events.trySend(
            if (intent != null) UiEvent.Launch(intent) else UiEvent.Message("This app has nothing to open"),
        )
    }

    private fun setTask(packageName: String, task: Task?) {
        _state.update {
            it.copy(tasks = if (task == null) it.tasks - packageName else it.tasks + (packageName to task))
        }
    }
}
