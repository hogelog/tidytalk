package org.hogel.tidytalk.data

data class AppEntry(
    val packageName: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}
