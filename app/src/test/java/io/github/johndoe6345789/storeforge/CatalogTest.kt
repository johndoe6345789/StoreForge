package io.github.johndoe6345789.storeforge

import io.github.johndoe6345789.storeforge.catalog.CatalogApp
import io.github.johndoe6345789.storeforge.catalog.DirectApk
import io.github.johndoe6345789.storeforge.catalog.GitHubSource
import io.github.johndoe6345789.storeforge.catalog.normalized
import io.github.johndoe6345789.storeforge.catalog.parseCatalog
import io.github.johndoe6345789.storeforge.catalog.validateApp
import io.github.johndoe6345789.storeforge.catalog.validateCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogTest {

    /** Guards the apps.json at the root of the repository: CI fails if it is broken. */
    @Test
    fun repositoryCatalogIsValid() {
        val path = System.getProperty("storeforge.catalog") ?: error("storeforge.catalog not set")
        val catalog = parseCatalog(File(path).readText())
        assertEquals(emptyList<String>(), validateCatalog(catalog))
        assertTrue("catalog lists no apps", catalog.apps.isNotEmpty())
    }

    @Test
    fun parsesMinimalApp() {
        val catalog = parseCatalog(
            """
            {"schemaVersion": 1, "apps": [
              {"packageName": "com.example.app", "name": "Example", "summary": "Does things",
               "github": {"repo": "example/app"}, "someFutureField": true}
            ]}
            """,
        )
        val app = catalog.apps.single()
        assertEquals("Other", app.category)
        assertEquals("\\.apk$", app.github?.asset)
        assertEquals(emptyList<String>(), validateCatalog(catalog))
    }

    @Test
    fun reportsBrokenEntries() {
        val app = CatalogApp(packageName = "notapackage", name = "", summary = "x")
        val problems = validateApp(app)
        assertTrue(problems.any { it.startsWith("packageName") })
        assertTrue(problems.any { it.startsWith("name") })
        assertTrue(problems.any { "source" in it })

        val both = app.copy(
            packageName = "com.example.a",
            name = "A",
            github = GitHubSource("a/b"),
            apk = DirectApk(url = "https://example.com/a.apk", versionName = "1.0", sha256 = "nothex"),
        )
        val bothProblems = validateApp(both)
        assertTrue(bothProblems.any { "not both" in it })
        assertTrue(bothProblems.any { it.startsWith("apk.sha256") })
    }

    @Test
    fun duplicatesAndUnknownFeaturedAreReported() {
        val catalog = parseCatalog(
            """
            {"schemaVersion": 1, "featured": ["com.example.missing"], "apps": [
              {"packageName": "com.example.a", "name": "A", "summary": "s", "github": {"repo": "x/a"}},
              {"packageName": "com.example.a", "name": "A2", "summary": "s", "github": {"repo": "x/a"}}
            ]}
            """,
        )
        val problems = validateCatalog(catalog)
        assertTrue(problems.any { "duplicate" in it })
        assertTrue(problems.any { it.startsWith("featured") })
    }

    @Test
    fun normalizeResolvesRelativeLinksAndDropsInvalidApps() {
        val catalog = parseCatalog(
            """
            {"schemaVersion": 1, "featured": ["com.example.a", "bad"], "apps": [
              {"packageName": "com.example.a", "name": "A", "summary": "s", "icon": "registry/icons/a.png",
               "screenshots": ["registry/shots/1.png", "https://cdn.example.com/2.png"],
               "apk": {"url": "builds/a.apk", "versionName": "1.0"}},
              {"packageName": "bad", "name": "Bad", "summary": "s", "github": {"repo": "x/y"}}
            ]}
            """,
        ).normalized("https://raw.githubusercontent.com/me/store/main/apps.json")

        val app = catalog.apps.single()
        assertEquals("https://raw.githubusercontent.com/me/store/main/registry/icons/a.png", app.icon)
        assertEquals(
            listOf("https://raw.githubusercontent.com/me/store/main/registry/shots/1.png", "https://cdn.example.com/2.png"),
            app.screenshots,
        )
        assertEquals("https://raw.githubusercontent.com/me/store/main/builds/a.apk", app.apk?.url)
        assertEquals(listOf("com.example.a"), catalog.featured)
    }
}
