package org.hogel.tidytalk.ui

import android.content.Context
import android.text.format.Formatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun formatSize(context: Context, bytes: Long): String =
    Formatter.formatFileSize(context, bytes)

/** UI rendering for [org.hogel.tidytalk.data.InstalledApp.lastUsedMillis]; "未起動" when null. */
fun formatLastUsedLabel(millis: Long?): String =
    if (millis != null && millis > 0) {
        LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).toString()
    } else {
        "未起動"
    }
