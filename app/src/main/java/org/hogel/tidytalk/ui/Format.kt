package org.hogel.tidytalk.ui

import android.content.Context
import android.text.format.Formatter

fun formatSize(context: Context, bytes: Long): String =
    Formatter.formatFileSize(context, bytes)
