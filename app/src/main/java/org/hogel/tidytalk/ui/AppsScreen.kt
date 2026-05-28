package org.hogel.tidytalk.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.hogel.tidytalk.data.InstalledApp
import org.hogel.tidytalk.data.InstalledAppsScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    apps: List<InstalledApp>,
    selected: Set<String>,
    selectedBytes: Long,
    loading: Boolean,
    currentUninstall: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onRequestUninstall: () -> Unit,
    onUninstallFinished: () -> Unit,
) {
    val context = LocalContext.current
    var hasUsage by remember { mutableStateOf(InstalledAppsScanner.hasUsageAccess(context)) }

    val usageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasUsage = InstalledAppsScanner.hasUsageAccess(context)
        if (hasUsage) onRefresh()
    }
    val uninstallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onUninstallFinished()
    }
    LaunchedEffect(currentUninstall) {
        val pkg = currentUninstall ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", pkg, null)
        }
        uninstallLauncher.launch(intent)
    }

    var confirmUninstall by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("インストール済みアプリ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (hasUsage && selected.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(
                                "${selected.size} 件 ・ ${formatSize(context, selectedBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = { confirmUninstall = true }) { Text("アンインストール") }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasUsage) {
                UsageAccessGate(onGrant = {
                    usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                })
            } else if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(apps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in selected,
                            onToggle = { onToggleSelect(app.packageName) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("アンインストールの確認") },
            text = {
                Text(
                    "選択した ${selected.size} 件を順番にアンインストールします。" +
                        "アプリごとにシステムの確認ダイアログが表示されます。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    onRequestUninstall()
                }) { Text("開始") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) { Text("キャンセル") }
            },
        )
    }

    BackHandler(onBack = onBack)
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val icon = remember(app.packageName) {
        runCatching { pm.getApplicationIcon(app.packageName).toBitmap(width = 96, height = 96) }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatSize(context, app.sizeBytes)} ・ ${app.packageName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UsageAccessGate(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "アプリごとの容量を表示するには、" +
                "「使用履歴へのアクセス」権限を許可してください。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onGrant) { Text("使用履歴へのアクセスを許可") }
    }
}
