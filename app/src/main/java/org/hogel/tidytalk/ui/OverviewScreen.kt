package org.hogel.tidytalk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hogel.tidytalk.BuildConfig
import org.hogel.tidytalk.data.DeviceStorage
import org.hogel.tidytalk.data.StorageCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    device: DeviceStorage?,
    categories: List<StorageCategory>,
    loading: Boolean,
    appsCount: Int,
    appsTotalBytes: Long,
    onOpenRoot: () -> Unit,
    onOpenCategory: (StorageCategory) -> Unit,
    onOpenCategoryAi: (StorageCategory) -> Unit,
    onOpenApps: () -> Unit,
    onOpenAppsAi: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TidyTalk") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "再読み込み")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                DeviceCard(device, onClick = onOpenRoot)
                Spacer(Modifier.height(8.dp))
                Text("カテゴリ", style = MaterialTheme.typography.titleMedium)
            }
            item {
                AppsRow(
                    count = appsCount,
                    totalBytes = appsTotalBytes,
                    onClick = onOpenApps,
                    onAiClick = onOpenAppsAi,
                )
            }
            items(categories, key = { it.dir.path }) { category ->
                CategoryRow(
                    category = category,
                    onClick = { onOpenCategory(category) },
                    onAiClick = { onOpenCategoryAi(category) },
                )
            }
            if (loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "${BuildConfig.VERSION_NAME}-${BuildConfig.GIT_SHORT_REV}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceStorage?, onClick: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("スマホ全体", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (device == null) {
                CircularProgressIndicator()
            } else {
                LinearProgressIndicator(
                    progress = { device.usedFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "使用 ${formatSize(context, device.usedBytes)} / 全体 ${formatSize(context, device.totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "空き ${formatSize(context, device.freeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppsRow(
    count: Int,
    totalBytes: Long,
    onClick: () -> Unit,
    onAiClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    "アプリ",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = if (count == 0) "—" else "${formatSize(context, totalBytes)} ・ $count 件"
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAiClick) { Text("AI 掃除") }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun CategoryRow(
    category: StorageCategory,
    onClick: () -> Unit,
    onAiClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatSize(context, category.totalBytes)} ・ ${category.fileCount} 件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAiClick) { Text("AI 掃除") }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
