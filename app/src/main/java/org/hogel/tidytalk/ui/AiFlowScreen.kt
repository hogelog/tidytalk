package org.hogel.tidytalk.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.ClipData
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
import org.hogel.tidytalk.data.PromptFileCount
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiFlowScreen(
    dir: File,
    loading: Boolean,
    prompt: String,
    answer: String,
    instruction: String,
    subdirs: List<File>,
    subdirEnabled: Set<File>,
    matched: List<File>?,
    invalidIds: List<Int>,
    noIds: Boolean,
    selected: Set<File>,
    selectedBytes: Long,
    promptFileCount: Int,
    onPromptFileCountChange: (Int) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInstructionChange: (String) -> Unit,
    onToggleSubdir: (File) -> Unit,
    onAnswerChange: (String) -> Unit,
    onParse: () -> Unit,
    onToggleSelect: (File) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 掃除: ${dir.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        Button(onClick = { confirmDelete = true }) { Text("削除") }
                    }
                }
            }
        },
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
                        InstructionSection(instruction, onInstructionChange)
                    }
                    if (subdirs.isNotEmpty()) {
                        item {
                            SubdirsSection(subdirs, subdirEnabled, onToggleSubdir, dir.absolutePath)
                        }
                    }
                    item {
                        PromptSection(
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
                        AnswerSection(
                            answer = answer,
                            onAnswerChange = onAnswerChange,
                            noIds = noIds,
                            onParse = onParse,
                        )
                    }
                    if (matched != null) {
                        item { ResultsHeader(matched.size, invalidIds) }
                        items(matched, key = { it.path }) { file ->
                            MatchedFileRow(
                                file = file,
                                checked = file in selected,
                                onToggle = { onToggleSelect(file) },
                                rootPath = dir.absolutePath,
                            )
                            HorizontalDivider()
                        }
                        item { Spacer(Modifier.height(80.dp)) }  // breathing room above bottomBar
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("削除の確認") },
            text = {
                Text("選択した ${selected.size} 件（${formatSize(context, selectedBytes)}）を完全に削除します。元に戻せません。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun InstructionSection(instruction: String, onChange: (String) -> Unit) {
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
private fun SubdirsSection(
    subdirs: List<File>,
    enabled: Set<File>,
    onToggle: (File) -> Unit,
    rootPath: String,
) {
    Column {
        Text("対象サブディレクトリ", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "オフにしたサブディレクトリ配下のファイルはプロンプトに含めません。直下のファイルは常に対象です。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Card {
            Column(modifier = Modifier.fillMaxWidth()) {
                subdirs.forEachIndexed { i, sub ->
                    val rel = sub.absolutePath.removePrefix("$rootPath/").ifEmpty { sub.name }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = sub in enabled, onCheckedChange = { onToggle(sub) })
                        Text(
                            rel,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (i < subdirs.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptSection(
    prompt: String,
    fileCount: Int,
    onFileCountChange: (Int) -> Unit,
    onCopy: () -> Unit,
) {
    Column {
        Text("1. 生成プロンプト", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "下のテキストを ChatGPT や Claude などに貼り付けて、削除推奨の ID を返してもらってください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ファイル数", style = MaterialTheme.typography.bodyMedium)
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
                    text = prompt.ifEmpty { "対象ファイルがありません" },
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
private fun AnswerSection(
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
private fun ResultsHeader(matchedCount: Int, invalidIds: List<Int>) {
    Column {
        Text("3. 削除候補（人が最終判断）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "$matchedCount 件にマッチしました。チェックを外したものは削除されません。",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchedFileRow(
    file: File,
    checked: Boolean,
    onToggle: () -> Unit,
    rootPath: String,
) {
    val context = LocalContext.current
    val rel = file.absolutePath.removePrefix("$rootPath/").ifEmpty { file.name }
    val previewKind = previewKindFor(file)
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { base ->
            if (previewKind != PreviewKind.None) {
                base.combinedClickable(
                    onClick = onToggle,
                    onLongClick = { openFileExternally(context, file) },
                )
            } else {
                base
            }
        }
        .padding(end = 8.dp)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        if (previewKind != PreviewKind.None) {
            PreviewThumbnail(
                file = file,
                kind = previewKind,
                onOpen = { openFileExternally(context, file) },
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(
                rel,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatSize(context, file.length()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
