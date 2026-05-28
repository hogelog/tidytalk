package org.hogel.tidytalk.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.hogel.tidytalk.data.StorageScanner

@Composable
fun TidyTalkApp(vm: TidyTalkViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(Environment.isExternalStorageManager()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasPermission = Environment.isExternalStorageManager()
    }

    if (!hasPermission) {
        PermissionGate(onGrant = {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null),
            )
            launcher.launch(intent)
        })
        return
    }

    LaunchedEffect(Unit) { vm.start() }

    when (val screen = vm.screen) {
        Screen.Overview -> OverviewScreen(
            device = vm.device,
            categories = vm.categories,
            loading = vm.overviewLoading,
            onOpenRoot = { vm.openDir(StorageScanner.rootDir()) },
            onOpenCategory = { vm.openDir(it.dir) },
            onOpenCategoryAi = { vm.openAiFlow(it.dir) },
            onRefresh = vm::refresh,
        )

        is Screen.Browse -> {
            BackHandler { vm.back() }
            BrowseScreen(
                dir = screen.dir,
                entries = vm.entries,
                selected = vm.selected,
                loading = vm.browseLoading,
                selectedBytes = vm.selectedBytes,
                onBack = { vm.back() },
                onOpenDir = vm::openDir,
                onToggleSelect = vm::toggleSelect,
                onRefresh = vm::refresh,
                onDelete = vm::deleteSelected,
                onOpenAi = { vm.openAiFlow(screen.dir) },
            )
        }

        is Screen.AiFlow -> {
            BackHandler { vm.back() }
            AiFlowScreen(
                dir = screen.dir,
                loading = vm.aiLoading,
                prompt = vm.aiPrompt,
                answer = vm.aiAnswer,
                instruction = vm.aiInstruction,
                subdirs = vm.aiSubdirs,
                subdirEnabled = vm.aiSubdirEnabled,
                matched = vm.aiMatched,
                invalidIds = vm.aiInvalidIds,
                noIds = vm.aiNoIds,
                selected = vm.aiSelected,
                selectedBytes = vm.aiSelectedBytes,
                promptFileCount = vm.aiPromptFileCount,
                onPromptFileCountChange = vm::updateAiPromptFileCount,
                onBack = { vm.back() },
                onRefresh = vm::refresh,
                onInstructionChange = vm::updateAiInstruction,
                onToggleSubdir = vm::toggleAiSubdir,
                onAnswerChange = vm::updateAiAnswer,
                onParse = vm::parseAnswer,
                onToggleSelect = vm::toggleAiSelect,
                onDelete = vm::deleteAiSelected,
            )
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("TidyTalk", style = MaterialTheme.typography.headlineMedium)
        Text(
            "端末内のファイルの容量を確認して整理するために、全ファイルへのアクセス許可が必要です。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        Button(onClick = onGrant) { Text("全ファイルアクセスを許可") }
    }
}
