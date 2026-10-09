package io.github.johndoe6345789.storeforge.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpException(val code: Int, message: String) : IOException(message)

/**
 * GETs [url] as text through the client's HTTP cache.
 *
 * With [refresh] the cached copy is revalidated (an ETag round trip; GitHub does not count
 * 304 answers against its rate limit). If the network is unreachable the last cached copy
 * is served, so the store keeps working offline.
 */
suspend fun OkHttpClient.getText(
    url: String,
    refresh: Boolean,
    configure: Request.Builder.() -> Unit = {},
): String = withContext(Dispatchers.IO) {
    fun request(cacheControl: CacheControl?) = Request.Builder().url(url).apply(configure).apply {
        if (cacheControl != null) cacheControl(cacheControl)
    }.build()

    try {
        newCall(request(if (refresh) CacheControl.Builder().noCache().build() else null))
            .await()
            .use { it.textOrThrow() }
    } catch (network: IOException) {
        // A missing resource is a real answer; anything else (offline, rate limited,
        // server trouble) falls back to the last copy we saw.
        if (network is HttpException && network.code == 404) throw network
        val cached = runCatching {
            newCall(request(CacheControl.FORCE_CACHE)).await().use { it.textOrThrow() }
        }
        cached.getOrElse { throw network }
    }
}

private fun Response.textOrThrow(): String {
    if (code == 504 && cacheResponse == null && networkResponse == null) throw IOException("Not cached")
    if (!isSuccessful) throw HttpException(code, "HTTP $code for ${request.url}")
    return body.string()
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)
        override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
    })
}
