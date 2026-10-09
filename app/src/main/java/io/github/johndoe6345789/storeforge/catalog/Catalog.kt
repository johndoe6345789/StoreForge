package io.github.johndoe6345789.storeforge.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

const val CATALOG_SCHEMA_VERSION = 1

/** The `apps.json` file at the root of this repository. */
@Serializable
data class Catalog(
    val schemaVersion: Int,
    val name: String = "StoreForge",
    val featured: List<String> = emptyList(),
    val apps: List<CatalogApp>,
)

@Serializable
data class CatalogApp(
    /** Android application id; it is how installed apps are matched to catalog entries. */
    val packageName: String,
    val name: String,
    val summary: String,
    val developer: String = "Unknown developer",
    val description: String? = null,
    val category: String = "Other",
    val icon: String? = null,
    val screenshots: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val homepage: String? = null,
    val license: String? = null,
    /** Take the APK from the latest release of a GitHub repository. */
    val github: GitHubSource? = null,
    /** Or point at one fixed APK. */
    val apk: DirectApk? = null,
)

@Serializable
data class GitHubSource(
    /** "owner/repo" */
    val repo: String,
    /** Regex matched against release asset names; the first match is installed. */
    val asset: String = "\\.apk$",
    /** Also consider pre-releases when looking for the newest version. */
    val prereleases: Boolean = false,
)

@Serializable
data class DirectApk(
    val url: String,
    val versionName: String,
    val versionCode: Long? = null,
    val sha256: String? = null,
    val size: Long? = null,
    val releaseNotes: String? = null,
)

/** The concrete APK an app currently offers, from GitHub or a [DirectApk] entry. */
@Serializable
data class Release(
    val versionName: String,
    val apkUrl: String,
    val versionCode: Long? = null,
    val sha256: String? = null,
    val size: Long? = null,
    val releaseNotes: String? = null,
    val publishedAt: String? = null,
    val pageUrl: String? = null,
)

val CatalogJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private val PACKAGE_NAME = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
private val GITHUB_REPO = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
private val SHA256 = Regex("^[a-fA-F0-9]{64}$")

fun parseCatalog(text: String): Catalog = CatalogJson.decodeFromString(Catalog.serializer(), text)

/** Problems that make an entry unusable. An empty list means the catalog is valid. */
fun validateCatalog(catalog: Catalog): List<String> {
    val problems = mutableListOf<String>()
    if (catalog.schemaVersion != CATALOG_SCHEMA_VERSION) {
        problems += "schemaVersion: expected $CATALOG_SCHEMA_VERSION, got ${catalog.schemaVersion}"
    }
    val seen = mutableSetOf<String>()
    catalog.apps.forEachIndexed { index, app ->
        val where = "apps[$index] (${app.packageName})"
        validateApp(app).forEach { problems += "$where: $it" }
        if (!seen.add(app.packageName)) problems += "$where: duplicate packageName"
    }
    catalog.featured.filterNot { it in seen }.forEach { problems += "featured: unknown packageName \"$it\"" }
    return problems
}

fun validateApp(app: CatalogApp): List<String> {
    val problems = mutableListOf<String>()
    if (!PACKAGE_NAME.matches(app.packageName)) problems += "packageName: not a valid Android package name"
    if (app.name.isBlank()) problems += "name: required"
    if (app.summary.isBlank()) problems += "summary: required"
    when {
        app.github == null && app.apk == null -> problems += "needs a \"github\" or an \"apk\" source"
        app.github != null && app.apk != null -> problems += "use either \"github\" or \"apk\", not both"
    }
    app.github?.let { source ->
        if (!GITHUB_REPO.matches(source.repo)) problems += "github.repo: expected \"owner/repo\""
        if (runCatching { Regex(source.asset) }.isFailure) problems += "github.asset: invalid regex"
    }
    app.apk?.let { apk ->
        if (!apk.url.startsWith("https://") && !isRelative(apk.url)) problems += "apk.url: must be https"
        if (apk.versionName.isBlank()) problems += "apk.versionName: required"
        if (apk.sha256 != null && !SHA256.matches(apk.sha256)) problems += "apk.sha256: must be 64 hex characters"
    }
    return problems
}

private fun isRelative(url: String) = runCatching { !URI(url).isAbsolute }.getOrDefault(false)

/**
 * Drops invalid apps and makes relative links absolute against the catalog URL, so icons
 * and screenshots can be committed next to apps.json and referenced as "registry/icons/x.png".
 */
fun Catalog.normalized(baseUrl: String): Catalog {
    val base = URI(baseUrl)
    fun resolve(link: String) = runCatching { base.resolve(link).toString() }.getOrDefault(link)
    val valid = apps.filter { validateApp(it).isEmpty() }.distinctBy { it.packageName }.map { app ->
        app.copy(
            icon = app.icon?.let(::resolve),
            screenshots = app.screenshots.map(::resolve),
            apk = app.apk?.let { it.copy(url = resolve(it.url)) },
        )
    }
    val ids = valid.map { it.packageName }.toSet()
    return copy(apps = valid, featured = featured.filter { it in ids })
}

fun DirectApk.toRelease() = Release(
    versionName = versionName,
    apkUrl = url,
    versionCode = versionCode,
    sha256 = sha256,
    size = size,
    releaseNotes = releaseNotes,
)
