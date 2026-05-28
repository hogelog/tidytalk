package org.hogel.tidytalk.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.util.Log

/**
 * Lists installed apps and queries their storage usage via [StorageStatsManager].
 * Sizes require the special PACKAGE_USAGE_STATS permission; without it sizes are 0.
 */
object AppScanner {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** User-installed apps (and updated system apps), sorted by total size descending. */
    fun listUserApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val user = Process.myUserHandle()
        val hasStats = hasUsageStatsPermission(context)
        return pm.getInstalledApplications(0)
            .filter { !it.isSystemApp() || it.isUpdatedSystemApp() }
            .map { info ->
                val label = info.loadLabel(pm).toString()
                val (app, data, cache) = if (hasStats) {
                    queryStats(ssm, info.packageName, user)
                } else {
                    Triple(0L, 0L, 0L)
                }
                AppEntry(info.packageName, label, app, data, cache)
            }
            .sortedByDescending { it.totalBytes }
    }

    private fun queryStats(
        ssm: StorageStatsManager,
        pkg: String,
        user: UserHandle,
    ): Triple<Long, Long, Long> = try {
        val stats = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, user)
        Triple(stats.appBytes, stats.dataBytes, stats.cacheBytes)
    } catch (e: Exception) {
        Log.w("AppScanner", "Failed to query stats for $pkg", e)
        Triple(0L, 0L, 0L)
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        flags and ApplicationInfo.FLAG_SYSTEM != 0

    private fun ApplicationInfo.isUpdatedSystemApp(): Boolean =
        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
}
