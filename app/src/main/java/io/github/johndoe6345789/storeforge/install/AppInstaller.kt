package io.github.johndoe6345789.storeforge.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class InstalledApp(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val lastUpdateTime: Long,
)

/** Installs, updates and uninstalls APKs through Android's [PackageInstaller]. */
class AppInstaller(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val packageInstaller: PackageInstaller = packageManager.packageInstaller

    fun installedApp(packageName: String): InstalledApp? = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        InstalledApp(packageName, info.versionName, PackageInfoCompat.getLongVersionCode(info), info.lastUpdateTime)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Android requires the user to allow each app store to install apps ("unknown sources"). */
    fun canInstallApps(): Boolean = packageManager.canRequestPackageInstalls()

    fun installPermissionSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    fun launchIntent(packageName: String): Intent? = packageManager.getLaunchIntentForPackage(packageName)

    /** Reads the downloaded APK and makes sure it is the app the catalog promised. */
    fun inspectApk(apk: File, expectedPackage: String): PackageInfo {
        val info = packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: throw IOException("The downloaded file is not a valid APK")
        if (info.packageName != expectedPackage) {
            throw IOException("The APK contains ${info.packageName}, expected $expectedPackage")
        }
        return info
    }

    /** Installs or updates [apk]. Suspends until Android reports the outcome. */
    suspend fun install(apk: File, packageName: String): PackageResult {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                // Updates to apps this store installed can then go through without a prompt.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
            }
        }
        val sessionId = withContext(Dispatchers.IO) {
            val id = packageInstaller.createSession(params)
            try {
                packageInstaller.openSession(id).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("base.apk", 0, apk.length()).use { output ->
                            input.copyTo(output, 64 * 1024)
                            session.fsync(output)
                        }
                    }
                }
            } catch (e: Exception) {
                packageInstaller.abandonSession(id)
                throw e
            }
            id
        }

        val (requestId, result) = InstallResultReceiver.newRequest()
        try {
            packageInstaller.openSession(sessionId).use { it.commit(statusReceiver(requestId).intentSender) }
            return result.await()
        } catch (e: Throwable) {
            // Cancelled while waiting (e.g. the user backed out of the dialog and tapped Cancel).
            withContext(NonCancellable) { runCatching { packageInstaller.abandonSession(sessionId) } }
            throw e
        } finally {
            InstallResultReceiver.forget(requestId)
        }
    }

    /** Asks Android to remove [packageName]; the user confirms in a system dialog. */
    suspend fun uninstall(packageName: String): PackageResult {
        val (requestId, result) = InstallResultReceiver.newRequest()
        try {
            packageInstaller.uninstall(packageName, statusReceiver(requestId).intentSender)
            return result.await()
        } finally {
            InstallResultReceiver.forget(requestId)
        }
    }

    private fun statusReceiver(requestId: Int): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java)
            .setAction(InstallResultReceiver.ACTION)
            .putExtra(InstallResultReceiver.EXTRA_REQUEST_ID, requestId)
        // Mutable: PackageInstaller fills in the status extras.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, requestId, intent, flags)
    }
}
