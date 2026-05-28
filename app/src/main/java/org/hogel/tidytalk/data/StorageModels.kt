package org.hogel.tidytalk.data

import java.io.File

/** Total/free bytes of the primary shared storage volume, from StatFs. */
data class DeviceStorage(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = totalBytes - freeBytes
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

/** A top-level cleaning category backed by a single directory. */
data class StorageCategory(
    val label: String,
    val dir: File,
    val totalBytes: Long,
    val fileCount: Int,
)

/** One entry (file or directory) shown in the browser. */
data class StorageEntry(
    val file: File,
    val isDirectory: Boolean,
    /** Recursive size for directories, file length for files. */
    val totalBytes: Long,
    /** Recursive file count for directories, 0 for files. */
    val fileCount: Int,
    val lastModified: Long,
) {
    val name: String get() = file.name
}
