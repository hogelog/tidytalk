package org.hogel.tidytalk.data

data class AppEntry(
    val packageName: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    /** Epoch millis of last foreground use, or null when no usage data is available. */
    val lastUsedMillis: Long?,
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}
