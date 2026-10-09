package io.github.johndoe6345789.storeforge.install

import io.github.johndoe6345789.storeforge.catalog.HttpException
import io.github.johndoe6345789.storeforge.catalog.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ChecksumMismatchException : IOException("The download is corrupt or was tampered with (checksum mismatch)")

/** Streams an APK to disk, reporting progress and checking its SHA-256 when one is known. */
class ApkDownloader(private val client: OkHttpClient) {

    suspend fun download(
        url: String,
        destination: File,
        expectedSha256: String?,
        onProgress: (received: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.path + ".part")
        val response = client.newCall(Request.Builder().url(url).build()).await()
        response.use {
            if (!response.isSuccessful) throw HttpException(response.code, "Download failed (HTTP ${response.code})")
            val body = response.body
            val total = body.contentLength().coerceAtLeast(0)
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastReport = 0L
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 100) {
                            lastReport = now
                            onProgress(received, total)
                        }
                    }
                }
            }
            onProgress(received, total.takeIf { it > 0 } ?: received)

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256 != null && !expectedSha256.equals(actual, ignoreCase = true)) {
                partial.delete()
                throw ChecksumMismatchException()
            }
        }
        if (!partial.renameTo(destination)) throw IOException("Could not save the download")
        destination
    }
}
