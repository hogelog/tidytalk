package org.hogel.tidytalk.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hogel.tidytalk.data.AppEntry
import org.hogel.tidytalk.data.lastUsedSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    loading: Boolean,
    usagePermission: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAi: () -> Unit,
    onGrantUsage: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("アプリ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenAi, enabled = apps.isNotEmpty()) { Text("AI 掃除") }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "再読み込み")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading && apps.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!usagePermission) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            UsagePermissionCard(onGrant = onGrantUsage)
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                    items(apps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            onOpenSettings = { openAppDetails(context, app.packageName) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun UsagePermissionCard(onGrant: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("data/cache を含めた正確なサイズを出すには", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "今は APK 本体サイズのみで並べています。「使用状況へのアクセス」を許可すると data/cache も含めた正確なサイズになります。許可しなくてもこの画面と AI 掃除は使えます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onGrant) { Text("使用状況アクセスを許可") }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val icon by rememberAppIcon(context, app.packageName)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 4.dp)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                appSizeSummary(context, app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Info, contentDescription = "OS のアプリ情報を開く")
        }
    }
}

private fun appSizeSummary(context: Context, app: AppEntry): String {
    val parts = mutableListOf<String>()
    if (app.totalBytes == 0L) {
        parts += "サイズ不明"
    } else {
        val total = formatSize(context, app.totalBytes)
        val cache = if (app.cacheBytes > 0) " (キャッシュ ${formatSize(context, app.cacheBytes)})" else ""
        parts += "合計 $total$cache"
    }
    if (app.lastUsedMillis != null) {
        parts += "最終使用 " + lastUsedSummary(app.lastUsedMillis, System.currentTimeMillis())
    }
    return parts.joinToString(" ・ ")
}

@Composable
fun rememberAppIcon(context: Context, packageName: String) =
    produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toImageBitmap()
            }.getOrNull()
        }
    }

private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap.asImageBitmap()
    val width = intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}

fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(intent) }
}
