package io.github.johndoe6345789.storeforge.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Outcome of an install or uninstall handed to [PackageInstaller]. */
sealed interface PackageResult {
    data object Success : PackageResult
    data class Failure(val status: Int, val message: String) : PackageResult
}

/**
 * Receives [PackageInstaller] status callbacks. When Android needs the user to confirm,
 * it opens the system dialog; final results complete the request that is waiting on them.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getIntExtra(EXTRA_REQUEST_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            val shown = confirm != null && runCatching {
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { Log.w(TAG, "Could not show the confirmation dialog", it) }.isSuccess
            if (!shown) complete(requestId, PackageResult.Failure(status, "Could not show Android's confirmation dialog"))
            return
        }

        val result = if (status == PackageInstaller.STATUS_SUCCESS) {
            PackageResult.Success
        } else {
            PackageResult.Failure(status, describeFailure(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
        }
        complete(requestId, result)
    }

    companion object {
        private const val TAG = "StoreForge"
        const val ACTION = "io.github.johndoe6345789.storeforge.PACKAGE_RESULT"
        const val EXTRA_REQUEST_ID = "request_id"

        private val nextId = AtomicInteger(1)
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<PackageResult>>()

        /** Registers a request; its result arrives through the returned deferred. */
        fun newRequest(): Pair<Int, CompletableDeferred<PackageResult>> {
            val id = nextId.getAndIncrement()
            val deferred = CompletableDeferred<PackageResult>()
            pending[id] = deferred
            return id to deferred
        }

        fun forget(id: Int) {
            pending.remove(id)
        }

        private fun complete(id: Int, result: PackageResult) {
            pending.remove(id)?.complete(result)
        }

        fun describeFailure(status: Int, detail: String?): String = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Cancelled"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Blocked by the device"
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "Conflicts with the installed version (it may be signed with a different key; uninstall it first)"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Not compatible with this device"
            PackageInstaller.STATUS_FAILURE_INVALID -> "The APK is invalid"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage"
            else -> detail ?: "Failed (status $status)"
        }
    }
}
