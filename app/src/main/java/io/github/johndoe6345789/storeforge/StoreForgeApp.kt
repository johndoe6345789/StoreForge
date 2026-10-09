package io.github.johndoe6345789.storeforge

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.johndoe6345789.storeforge.catalog.CatalogRepository
import io.github.johndoe6345789.storeforge.install.ApkDownloader
import io.github.johndoe6345789.storeforge.install.AppInstaller
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class StoreForgeApp : Application(), SingletonImageLoader.Factory {

    /** Shared client; its disk cache keeps the catalog and release info available offline. */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val catalogRepository by lazy { CatalogRepository(httpClient) }

    // APKs bypass the HTTP cache: they are large and only needed once.
    val downloader by lazy { ApkDownloader(httpClient.newBuilder().cache(null).build()) }

    val installer by lazy { AppInstaller(this) }

    val settings by lazy { StoreSettings(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient })) }
            .build()
}

class StoreSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Where apps.json is read from. Defaults to this repository's copy on GitHub. */
    var catalogUrl: String
        get() = prefs.getString(KEY_CATALOG_URL, null) ?: BuildConfig.CATALOG_URL
        set(value) {
            val trimmed = value.trim()
            prefs.edit {
                if (trimmed.isEmpty() || trimmed == BuildConfig.CATALOG_URL) remove(KEY_CATALOG_URL)
                else putString(KEY_CATALOG_URL, trimmed)
            }
        }

    private companion object {
        const val KEY_CATALOG_URL = "catalog_url"
    }
}
