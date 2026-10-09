package io.github.johndoe6345789.storeforge

import io.github.johndoe6345789.storeforge.catalog.Catalog
import io.github.johndoe6345789.storeforge.catalog.CatalogApp
import io.github.johndoe6345789.storeforge.catalog.GitHubSource
import io.github.johndoe6345789.storeforge.catalog.Release
import io.github.johndoe6345789.storeforge.install.InstalledApp
import io.github.johndoe6345789.storeforge.ui.search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreStateTest {
    private val notes = CatalogApp("com.example.notes", "Notes", "Write things down", category = "Productivity", github = GitHubSource("e/notes"))
    private val chess = CatalogApp("com.example.chess", "Chess", "Play chess", category = "Games", tags = listOf("board"), github = GitHubSource("e/chess"))
    private val catalog = Catalog(schemaVersion = 1, apps = listOf(notes, chess))
    private val release = Release(versionName = "1.2.0", apkUrl = "https://example.com/notes.apk")

    @Test
    fun statusFollowsReleaseInstallAndTaskState() {
        var state = StoreState(catalog = catalog, releases = mapOf(notes.packageName to ReleaseState.Loading))
        assertEquals(AppStatus.Checking, state.statusOf(notes))

        state = state.copy(releases = mapOf(notes.packageName to ReleaseState.Ready(release)))
        assertTrue(state.statusOf(notes) is AppStatus.NotInstalled)

        state = state.copy(installed = mapOf(notes.packageName to InstalledApp(notes.packageName, "1.1.0", 5, 0)))
        assertTrue(state.statusOf(notes) is AppStatus.UpdateAvailable)
        assertEquals(listOf(notes), state.updates)

        state = state.copy(installed = mapOf(notes.packageName to InstalledApp(notes.packageName, "1.2.0", 6, 0)))
        assertTrue(state.statusOf(notes) is AppStatus.Installed)

        state = state.copy(tasks = mapOf(notes.packageName to Task.Installing))
        assertTrue(state.statusOf(notes) is AppStatus.Busy)

        state = StoreState(catalog = catalog, releases = mapOf(chess.packageName to ReleaseState.Failed("no release")))
        assertEquals(AppStatus.Unavailable("no release"), state.statusOf(chess))
    }

    @Test
    fun versionCodeWinsOverVersionName() {
        val installed = InstalledApp("com.example.notes", "9.9.9", 10, 0)
        assertTrue(isUpdate(release.copy(versionCode = 11), installed))
        assertTrue(!isUpdate(release.copy(versionCode = 10), installed))
        assertTrue(!isUpdate(release, installed))
    }

    @Test
    fun searchRanksNameMatchesFirst() {
        assertEquals(listOf(chess), search(catalog.apps, "board"))
        assertEquals(listOf(notes), search(catalog.apps, "note"))
        assertEquals(emptyList<CatalogApp>(), search(catalog.apps, "chess notes"))
        assertEquals(listOf(chess, notes), search(catalog.apps, ""))
    }
}
