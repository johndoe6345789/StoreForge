package io.github.johndoe6345789.storeforge.catalog

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient

/** Fetches apps.json and works out which release each listed app currently offers. */
class CatalogRepository(
    private val client: OkHttpClient,
    private val releases: GitHubReleases = GitHubReleases(client),
) {

    suspend fun loadCatalog(url: String, refresh: Boolean): Catalog =
        parseCatalog(client.getText(url, refresh)).normalized(url)

    /** Resolves every app's release in parallel; a failure only affects that one app. */
    suspend fun resolveReleases(apps: List<CatalogApp>, refresh: Boolean): Map<String, Result<Release>> =
        coroutineScope {
            val limit = Semaphore(4)
            apps.map { app ->
                async { app.packageName to limit.withPermit { runCatching { resolveRelease(app, refresh) } } }
            }.awaitAll().toMap()
        }

    suspend fun resolveRelease(app: CatalogApp, refresh: Boolean): Release {
        app.apk?.let { return it.toRelease() }
        val source = requireNotNull(app.github) { "${app.packageName} has no source" }
        return releases.latest(source, refresh)
    }
}
