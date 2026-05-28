package org.hogel.tidytalk.ui

import android.content.ClipData
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import org.hogel.tidytalk.data.InstalledApp
import org.hogel.tidytalk.data.InstalledAppsScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsAiFlowScreen(
    loading: Boolean,
    instruction: String,
    prompt: String,
    answer: String,
    matched: List<InstalledApp>?,
    invalidIds: List<String>,
    noIds: Boolean,
    selected: Set<String>,
    selectedBytes: Long,
    currentUninstall: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInstructionChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onParse: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onRequestUninstall: () -> Unit,
    onUninstallFinished: () -> Unit,
) {
    val context = LocalContext.current
    var hasUsage by remember { mutableStateOf(InstalledAppsScanner.hasUsageAccess(context)) }
    val clipboard = LocalClipboard.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmUninstall by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 掃除: アプリ", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "プロンプト再生成")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) { Snackbar(it) } },
        bottomBar = {
            if (matched != null && selected.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${selected.size} 件 ・ ${formatSize(context, selectedBytes)}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { confirmUninstall = true }) { Text("アンインストール") }
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasUsage) {
                UsageGate(onGrant = {
                    usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                })
            } else if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        InstructionField(instruction, onInstructionChange)
                    }
                    item {
                        PromptSection(
                            prompt = prompt,
                            onCopy = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("TidyTalk", prompt)))
                                    snackbarHost.showSnackbar("プロンプトをコピーしました")
                                }
                            },
                        )
                    }
                    item {
                        AnswerField(
                            answer = answer,
                            onAnswerChange = onAnswerChange,
                            noIds = noIds,
                            onParse = onParse,
                        )
                    }
                    if (matched != null) {
                        item { ResultsHeader(matched.size, invalidIds) }
                        items(matched, key = { it.packageName }) { app ->
                            MatchedAppRow(
                                app = app,
                                checked = app.packageName in selected,
                                onToggle = { onToggleSelect(app.packageName) },
                            )
                            HorizontalDivider()
                        }
                        item { Spacer(Modifier.height(80.dp)) }
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
                    "選択した ${selected.size} 件（${formatSize(context, selectedBytes)}）を順番にアンインストールします。" +
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
private fun InstructionField(instruction: String, onChange: (String) -> Unit) {
    Column {
        Text("前文（AI への指示）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "プロンプト先頭に付ける文章。AI に伝えたい観点があれば書き換えてください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = instruction,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
    }
}

@Composable
private fun PromptSection(prompt: String, onCopy: () -> Unit) {
    Column {
        Text("1. 生成プロンプト", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "下のテキストを ChatGPT や Claude などに貼り付けて、アンインストール推奨の ID を返してもらってください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Card {
            SelectionContainer {
                Text(
                    text = prompt.ifEmpty { "対象アプリがありません" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCopy, enabled = prompt.isNotEmpty()) { Text("プロンプトをコピー") }
    }
}

@Composable
private fun AnswerField(
    answer: String,
    onAnswerChange: (String) -> Unit,
    noIds: Boolean,
    onParse: () -> Unit,
) {
    Column {
        Text("2. AI の回答を貼り付け", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "AI が返したコードブロック付きの回答をそのまま貼り付けて「解析」を押してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            placeholder = { Text("AI の回答をここに貼り付け") },
        )
        if (noIds) {
            Text(
                "ID が見つかりませんでした。AI が返した ID リスト（コードブロックの中身でも可）をそのまま貼り付けてください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onParse, enabled = answer.isNotBlank()) { Text("解析") }
    }
}

@Composable
private fun ResultsHeader(matchedCount: Int, invalidIds: List<String>) {
    Column {
        Text("3. アンインストール候補（人が最終判断）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "$matchedCount 件にマッチしました。チェックを外したものはアンインストールされません。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (invalidIds.isNotEmpty()) {
            Text(
                "未知の ID: ${invalidIds.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MatchedAppRow(
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
private fun UsageGate(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "アプリごとの容量を取得するには、「使用履歴へのアクセス」権限を許可してください。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onGrant) { Text("使用履歴へのアクセスを許可") }
    }
}
