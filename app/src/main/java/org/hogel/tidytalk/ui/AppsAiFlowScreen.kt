package org.hogel.tidytalk.ui

import android.content.ClipData
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.hogel.tidytalk.data.AppEntry
import org.hogel.tidytalk.data.PromptFileCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsAiFlowScreen(
    loading: Boolean,
    prompt: String,
    answer: String,
    instruction: String,
    matched: List<AppEntry>?,
    invalidIds: List<Int>,
    noIds: Boolean,
    selected: Set<String>,
    promptFileCount: Int,
    onPromptFileCountChange: (Int) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInstructionChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onParse: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onOpenAppDetails: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        AppsInstructionSection(instruction, onInstructionChange)
                    }
                    item {
                        AppsPromptSection(
                            prompt = prompt,
                            fileCount = promptFileCount,
                            onFileCountChange = onPromptFileCountChange,
                            onCopy = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("TidyTalk", prompt)))
                                    snackbarHost.showSnackbar("プロンプトをコピーしました")
                                }
                            },
                        )
                    }
                    item {
                        AppsAnswerSection(
                            answer = answer,
                            onAnswerChange = onAnswerChange,
                            noIds = noIds,
                            onParse = onParse,
                        )
                    }
                    if (matched != null) {
                        item { AppsResultsHeader(matched.size, invalidIds) }
                        items(matched, key = { it.packageName }) { app ->
                            MatchedAppRow(
                                app = app,
                                checked = app.packageName in selected,
                                onToggle = { onToggleSelect(app.packageName) },
                                onOpenDetails = { onOpenAppDetails(app.packageName) },
                            )
                            HorizontalDivider()
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsInstructionSection(instruction: String, onChange: (String) -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsPromptSection(
    prompt: String,
    fileCount: Int,
    onFileCountChange: (Int) -> Unit,
    onCopy: () -> Unit,
) {
    Column {
        Text("1. 生成プロンプト", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "下のテキストを ChatGPT や Claude などに貼り付けて、アンインストール推奨の ID を返してもらってください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("アプリ数", style = MaterialTheme.typography.bodyMedium)
            PromptFileCount.PRESETS.forEach { n ->
                FilterChip(
                    selected = n == fileCount,
                    onClick = { onFileCountChange(n) },
                    label = { Text("$n") },
                )
            }
        }
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
private fun AppsAnswerSection(
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
private fun AppsResultsHeader(matchedCount: Int, invalidIds: List<Int>) {
    Column {
        Text("3. アンインストール候補", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "$matchedCount 件にマッチしました。行をタップで OS のアプリ情報画面に飛んで個別にアンインストールしてください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (invalidIds.isNotEmpty()) {
            Text(
                "範囲外の ID は無視しました: ${invalidIds.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MatchedAppRow(
    app: AppEntry,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val context = LocalContext.current
    val icon by rememberAppIcon(context, app.packageName)
    val labelStyle = if (checked) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
    val labelColor = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Box(modifier = Modifier.size(48.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 4.dp)) {
            Text(
                app.label,
                style = labelStyle,
                color = labelColor,
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
        }
        Text(
            if (app.totalBytes > 0) formatSize(context, app.totalBytes) else "-",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        OutlinedButton(onClick = onOpenDetails) { Text("開く") }
    }
}
