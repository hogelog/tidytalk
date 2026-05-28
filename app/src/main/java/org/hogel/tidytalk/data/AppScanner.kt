package org.hogel.tidytalk.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import android.util.Log
import java.io.File

/**
 * Lists installed apps and reports their on-disk size.
 *
 * Full stats (app + data + cache) require the special PACKAGE_USAGE_STATS permission.
 * Without it we fall back to APK/split-APK file sizes read from
 * [ApplicationInfo.publicSourceDir] / [ApplicationInfo.splitPublicSourceDirs] — that's
 * always readable and good enough for "what's eating space" sorting; data/cache are
 * left at 0 in that mode so the UI can surface the limitation.
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
                    queryStats(ssm, info.packageName, user) ?: Triple(apkSize(info), 0L, 0L)
                } else {
                    Triple(apkSize(info), 0L, 0L)
                }
                AppEntry(info.packageName, label, app, data, cache)
            }
            .sortedByDescending { it.totalBytes }
    }

    private fun queryStats(
        ssm: StorageStatsManager,
        pkg: String,
        user: UserHandle,
    ): Triple<Long, Long, Long>? = try {
        val stats = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, user)
        Triple(stats.appBytes, stats.dataBytes, stats.cacheBytes)
    } catch (e: Exception) {
        Log.w("AppScanner", "Failed to query stats for $pkg", e)
        null
    }

    /** Sum of the base APK and any split APKs on disk. Readable without special permissions. */
    private fun apkSize(info: ApplicationInfo): Long {
        val paths = buildList {
            info.publicSourceDir?.let { add(it) }
            info.splitPublicSourceDirs?.forEach { add(it) }
        }
        return paths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        flags and ApplicationInfo.FLAG_SYSTEM != 0

    private fun ApplicationInfo.isUpdatedSystemApp(): Boolean =
        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
}
