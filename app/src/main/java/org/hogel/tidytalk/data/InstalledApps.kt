package org.hogel.tidytalk.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager

data class InstalledApp(
    val packageName: String,
    val label: String,
    val sizeBytes: Long,
)

object InstalledAppsScanner {
    /** Whether the user has granted "Usage access" so [loadInstalledApps] can compute sizes. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** User-installed apps (no system apps), sorted by total size desc. */
    fun loadInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val user = Process.myUserHandle()
        @Suppress("DEPRECATION")
        val all = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return all.asSequence()
            .filter { !it.isSystemApp() }
            .map { info ->
                val bytes = runCatching {
                    val s = stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user)
                    s.appBytes + s.dataBytes + s.cacheBytes
                }.getOrDefault(0L)
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    sizeBytes = bytes,
                )
            }
            .sortedByDescending { it.sizeBytes }
            .toList()
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
}
