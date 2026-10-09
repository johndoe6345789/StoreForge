package io.github.johndoe6345789.storeforge.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.io.IOException

/** Finds the APK an app's GitHub repository currently publishes on its releases page. */
class GitHubReleases(
    private val client: OkHttpClient,
    private val apiBase: String = "https://api.github.com",
) {

    suspend fun latest(source: GitHubSource, refresh: Boolean): Release {
        val release = try {
            if (source.prereleases) {
                val text = client.getText("$apiBase/repos/${source.repo}/releases?per_page=10", refresh, ::githubHeaders)
                CatalogJson.decodeFromString(ListSerializer(GhRelease.serializer()), text).firstOrNull { !it.draft }
            } else {
                val text = client.getText("$apiBase/repos/${source.repo}/releases/latest", refresh, ::githubHeaders)
                CatalogJson.decodeFromString(GhRelease.serializer(), text)
            }
        } catch (e: HttpException) {
            throw when (e.code) {
                404 -> IOException("${source.repo} has no published release yet")
                403, 429 -> IOException("GitHub rate limit reached, try again in a while")
                else -> e
            }
        } ?: throw IOException("${source.repo} has no published release yet")

        return release.toRelease(source)
    }

    private fun githubHeaders(builder: okhttp3.Request.Builder) {
        builder.header("Accept", "application/vnd.github+json")
        builder.header("X-GitHub-Api-Version", "2022-11-28")
    }
}

fun GhRelease.toRelease(source: GitHubSource): Release {
    val pattern = Regex(source.asset, RegexOption.IGNORE_CASE)
    val asset = assets.firstOrNull { pattern.containsMatchIn(it.name) }
        ?: throw IOException("Release $tagName of ${source.repo} has no file matching \"${source.asset}\"")
    return Release(
        versionName = tagName.replace(Regex("^[vV](?=\\d)"), ""),
        apkUrl = asset.downloadUrl,
        sha256 = asset.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
        size = asset.size,
        releaseNotes = body?.takeIf { it.isNotBlank() },
        publishedAt = publishedAt,
        pageUrl = htmlUrl,
    )
}

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long? = null,
    /** "sha256:<hex>", published by GitHub for release assets. */
    val digest: String? = null,
)
