package org.hogel.tidytalk.data

import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Reads sizes and listings off the shared external storage. Every method here
 * touches the filesystem and must run off the main thread; callers dispatch to
 * [kotlinx.coroutines.Dispatchers.IO].
 */
object StorageScanner {

    private val root: File get() = Environment.getExternalStorageDirectory()

    fun deviceStorage(): DeviceStorage {
        val stat = StatFs(root.path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return DeviceStorage(totalBytes = total, freeBytes = free)
    }

    fun rootDir(): File = root

    /** Standard public directories that exist, in display order. */
    fun categoryDirs(): List<Pair<String, File>> {
        val candidates = listOf(
            "ダウンロード" to Environment.DIRECTORY_DOWNLOADS,
            "カメラ" to Environment.DIRECTORY_DCIM,
            "画像" to Environment.DIRECTORY_PICTURES,
            "動画" to Environment.DIRECTORY_MOVIES,
            "音楽" to Environment.DIRECTORY_MUSIC,
            "ドキュメント" to Environment.DIRECTORY_DOCUMENTS,
        )
        return candidates.mapNotNull { (label, type) ->
            val dir = Environment.getExternalStoragePublicDirectory(type)
            if (dir.isDirectory) label to dir else null
        }
    }

    fun scanCategory(label: String, dir: File): StorageCategory {
        val (bytes, count) = sizeAndCount(dir)
        return StorageCategory(label = label, dir = dir, totalBytes = bytes, fileCount = count)
    }

    /** Direct children of [dir] as entries, largest first. */
    fun listEntries(dir: File): List<StorageEntry> {
        val children = dir.listFiles() ?: return emptyList()
        return children.map { child ->
            if (child.isDirectory) {
                val (bytes, count) = sizeAndCount(child)
                StorageEntry(child, true, bytes, count, child.lastModified())
            } else {
                StorageEntry(child, false, child.length(), 0, child.lastModified())
            }
        }.sortedByDescending { it.totalBytes }
    }

    /** Recursive total size and file count under [dir]. */
    private fun sizeAndCount(dir: File): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                bytes += f.length()
                count++
            }
        }
        return bytes to count
    }
}
