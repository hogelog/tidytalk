package org.hogel.tidytalk.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager

data class InstalledApp(
    val packageName: String,
    val label: String,
    /** APK + user data + cache. */
    val sizeBytes: Long,
    /** User data only (excludes APK and cache). */
    val dataBytes: Long,
    /** Cache only. */
    val cacheBytes: Long,
    /** Last foregrounded time per [UsageStatsManager]; `null` when no usage seen in the lookback window. */
    val lastUsedMillis: Long?,
)

object InstalledAppsScanner {
    /** Lookback window for "last used" queries. */
    private const val LAST_USED_LOOKBACK_DAYS = 365L

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
        val lastUsed = queryLastUsedMap(context)
        @Suppress("DEPRECATION")
        val all = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return all.asSequence()
            .filter { !it.isSystemApp() }
            .map { info ->
                val s = runCatching {
                    stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user)
                }.getOrNull()
                // StorageStats.dataBytes already includes cacheBytes, so the total is
                // appBytes + dataBytes and "user data" is dataBytes - cacheBytes.
                val cache = s?.cacheBytes ?: 0L
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    sizeBytes = if (s != null) s.appBytes + s.dataBytes else 0L,
                    dataBytes = (s?.dataBytes ?: 0L) - cache,
                    cacheBytes = cache,
                    lastUsedMillis = lastUsed[info.packageName],
                )
            }
            .sortedByDescending { it.sizeBytes }
            .toList()
    }

    /** Aggregates last-foregrounded time per package over the past [LAST_USED_LOOKBACK_DAYS]. */
    private fun queryLastUsedMap(context: Context): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - LAST_USED_LOOKBACK_DAYS * 24L * 60L * 60L * 1000L
        val stats = runCatching { usm.queryAndAggregateUsageStats(begin, end) }.getOrNull().orEmpty()
        return stats.mapNotNull { (pkg, s) ->
            val t = s.lastTimeUsed
            if (t > 0) pkg to t else null
        }.toMap()
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
}
