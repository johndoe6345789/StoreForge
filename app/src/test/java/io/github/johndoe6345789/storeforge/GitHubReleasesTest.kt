package io.github.johndoe6345789.storeforge

import io.github.johndoe6345789.storeforge.catalog.GitHubReleases
import io.github.johndoe6345789.storeforge.catalog.GitHubSource
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class GitHubReleasesTest {
    @get:Rule val temp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var releases: GitHubReleases

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().cache(Cache(temp.newFolder("http"), 1_000_000)).build()
        releases = GitHubReleases(client, apiBase = server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200, etag: String? = null) = MockResponse.Builder()
        .code(code)
        .body(body)
        .addHeader("Content-Type", "application/json")
        .apply { if (etag != null) addHeader("ETag", etag).addHeader("Cache-Control", "public, max-age=60, s-maxage=60") }
        .build()

    private val latest = """
        {"tag_name": "v1.4.0", "body": "Bug fixes", "html_url": "https://github.com/o/r/releases/tag/v1.4.0",
         "published_at": "2026-09-01T10:00:00Z",
         "assets": [
           {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt", "size": 10},
           {"name": "app-release.apk", "browser_download_url": "https://example.com/app.apk", "size": 4096,
            "digest": "sha256:${"ab".repeat(32)}"}
         ]}
    """

    @Test
    fun picksTheMatchingApkFromTheLatestRelease() = runBlocking {
        server.enqueue(json(latest))
        val release = releases.latest(GitHubSource("o/r"), refresh = false)

        assertEquals("/repos/o/r/releases/latest", server.takeRequest().target)
        assertEquals("1.4.0", release.versionName)
        assertEquals("https://example.com/app.apk", release.apkUrl)
        assertEquals("ab".repeat(32), release.sha256)
        assertEquals(4096L, release.size)
        assertEquals("Bug fixes", release.releaseNotes)
        assertNull(release.versionCode)
    }

    @Test
    fun prereleasesUseTheReleaseListAndSkipDrafts() = runBlocking {
        server.enqueue(
            json(
                """[
                  {"tag_name": "v2.0.0-draft", "draft": true, "assets": []},
                  {"tag_name": "v2.0.0-beta.1", "prerelease": true,
                   "assets": [{"name": "app.apk", "browser_download_url": "https://example.com/beta.apk"}]}
                ]""",
            ),
        )
        val release = releases.latest(GitHubSource("o/r", prereleases = true), refresh = false)
        assertTrue(server.takeRequest().target.startsWith("/repos/o/r/releases?"))
        assertEquals("2.0.0-beta.1", release.versionName)
    }

    @Test
    fun missingReleaseHasAReadableError() = runBlocking {
        server.enqueue(json("""{"message": "Not Found"}""", code = 404))
        try {
            releases.latest(GitHubSource("o/r"), refresh = false)
            fail("expected an error")
        } catch (e: IOException) {
            assertEquals("o/r has no published release yet", e.message)
        }
    }

    @Test
    fun noMatchingAssetIsAnError() = runBlocking {
        server.enqueue(json(latest))
        try {
            releases.latest(GitHubSource("o/r", asset = "\\.aab$"), refresh = false)
            fail("expected an error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("no file matching"))
        }
    }

    @Test
    fun rateLimitFallsBackToTheCachedRelease() = runBlocking {
        server.enqueue(json(latest, etag = "\"v1\""))
        releases.latest(GitHubSource("o/r"), refresh = false)

        server.enqueue(json("""{"message": "API rate limit exceeded"}""", code = 403))
        val release = releases.latest(GitHubSource("o/r"), refresh = true)
        assertEquals("1.4.0", release.versionName)
    }
}
