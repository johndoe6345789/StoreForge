package io.github.johndoe6345789.storeforge

import io.github.johndoe6345789.storeforge.install.ApkDownloader
import io.github.johndoe6345789.storeforge.install.ChecksumMismatchException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ApkDownloaderTest {
    @get:Rule val temp = TemporaryFolder()

    private val server = MockWebServer()
    private val downloader = ApkDownloader(OkHttpClient())
    private val payload = ByteArray(300_000) { (it % 251).toByte() }
    private val sha256 = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun enqueuePayload() = server.enqueue(MockResponse.Builder().body(okio.Buffer().write(payload)).build())

    @Test
    fun downloadsAndVerifies() = runBlocking {
        enqueuePayload()
        val target = File(temp.root, "apks/app.apk")
        var lastProgress = 0L to 0L
        downloader.download(server.url("/app.apk").toString(), target, sha256.uppercase()) { r, t -> lastProgress = r to t }

        assertArrayEquals(payload, target.readBytes())
        assertEquals(payload.size.toLong() to payload.size.toLong(), lastProgress)
    }

    @Test
    fun rejectsChecksumMismatch() = runBlocking {
        enqueuePayload()
        val target = File(temp.root, "app.apk")
        try {
            downloader.download(server.url("/app.apk").toString(), target, "0".repeat(64)) { _, _ -> }
            fail("expected a checksum error")
        } catch (_: ChecksumMismatchException) {
        }
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }
}
