package org.hogel.tidytalk.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hogel.tidytalk.data.AnswerParseResult
import org.hogel.tidytalk.data.AppEntry
import org.hogel.tidytalk.data.AppScanner
import org.hogel.tidytalk.data.DEFAULT_AI_INSTRUCTION
import org.hogel.tidytalk.data.DEFAULT_APPS_AI_INSTRUCTION
import org.hogel.tidytalk.data.DeviceStorage
import org.hogel.tidytalk.data.PromptFileCount
import org.hogel.tidytalk.data.StorageCategory
import org.hogel.tidytalk.data.StorageEntry
import org.hogel.tidytalk.data.StorageScanner
import org.hogel.tidytalk.data.TidyTalkSettings
import org.hogel.tidytalk.data.buildAiPrompt
import org.hogel.tidytalk.data.buildAppsAiPrompt
import org.hogel.tidytalk.data.parseAnswerIds
import java.io.File

sealed interface Screen {
    data object Overview : Screen
    data class Browse(val dir: File) : Screen
    data class AiFlow(val dir: File) : Screen
    data object Apps : Screen
    data object AppsAi : Screen
}

class TidyTalkViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = TidyTalkSettings(application)

    private val backStack = mutableListOf<Screen>(Screen.Overview)
    var screen by mutableStateOf<Screen>(Screen.Overview)
        private set

    var device by mutableStateOf<DeviceStorage?>(null)
        private set
    var categories by mutableStateOf<List<StorageCategory>>(emptyList())
        private set
    var overviewLoading by mutableStateOf(false)
        private set

    var entries by mutableStateOf<List<StorageEntry>>(emptyList())
        private set
    var browseLoading by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Set<File>>(emptySet())
        private set

    var aiLoading by mutableStateOf(false)
        private set
    var aiPrompt by mutableStateOf("")
        private set
    var aiAnswer by mutableStateOf("")
        private set
    var aiInstruction by mutableStateOf(DEFAULT_AI_INSTRUCTION)
        private set

    /** Direct subdirectories of the AI flow's target dir; toggled to scope the prompt. */
    var aiSubdirs by mutableStateOf<List<File>>(emptyList())
        private set
    var aiSubdirEnabled by mutableStateOf<Set<File>>(emptySet())
        private set

    /** All files under the AI flow's target dir, cached so subdir toggles don't re-scan IO. */
    private var aiAllFiles: List<File> = emptyList()
    private var aiDir: File? = null

    /** Persisted top-N file count used when generating the AI prompt. */
    var aiPromptFileCount by mutableStateOf(PromptFileCount.DEFAULT)
        private set

    /** index+1 == ID printed in [aiPrompt]; used to resolve parsed IDs back to files. */
    private var aiSnapshot: List<File> = emptyList()

    /** Files the parsed answer mapped to (post-parse). `null` until [parseAnswer] runs successfully. */
    var aiMatched by mutableStateOf<List<File>?>(null)
        private set
    var aiInvalidIds by mutableStateOf<List<Int>>(emptyList())
        private set
    var aiNoIds by mutableStateOf(false)
        private set
    var aiSelected by mutableStateOf<Set<File>>(emptySet())
        private set

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var appsLoading by mutableStateOf(false)
        private set
    var appsUsagePermission by mutableStateOf(false)
        private set

    var appsAiLoading by mutableStateOf(false)
        private set
    var appsAiPrompt by mutableStateOf("")
        private set
    var appsAiAnswer by mutableStateOf("")
        private set
    var appsAiInstruction by mutableStateOf(DEFAULT_APPS_AI_INSTRUCTION)
        private set

    /** index+1 == ID in [appsAiPrompt]. */
    private var appsAiSnapshot: List<AppEntry> = emptyList()

    var appsAiMatched by mutableStateOf<List<AppEntry>?>(null)
        private set
    var appsAiInvalidIds by mutableStateOf<List<Int>>(emptyList())
        private set
    var appsAiNoIds by mutableStateOf(false)
        private set
    var appsAiSelected by mutableStateOf<Set<String>>(emptySet())
        private set

    private var loadJob: Job? = null
    private var started = false

    init {
        viewModelScope.launch {
            // Snapshot once; chip taps update both state and DataStore so we don't need to observe.
            aiPromptFileCount = settings.promptFileCount.first()
        }
    }

    /** Called once after the storage permission is granted. */
    fun start() {
        if (started) return
        started = true
        loadOverview()
    }

    fun updateAiPromptFileCount(value: Int) {
        if (value == aiPromptFileCount) return
        aiPromptFileCount = value
        viewModelScope.launch { settings.setPromptFileCount(value) }
        when (screen) {
            is Screen.AiFlow -> {
                rebuildAiPrompt()
                invalidateAiParse()
            }
            Screen.AppsAi -> {
                rebuildAppsAiPrompt()
                invalidateAppsAiParse()
            }
            else -> Unit
        }
    }

    fun openDir(dir: File) {
        backStack.add(Screen.Browse(dir))
        screen = backStack.last()
        loadBrowse(dir)
    }

    fun openAiFlow(dir: File) {
        backStack.add(Screen.AiFlow(dir))
        screen = backStack.last()
        loadAiFlow(dir)
    }

    fun openApps() {
        backStack.add(Screen.Apps)
        screen = backStack.last()
        loadApps()
    }

    fun openAppsAi() {
        backStack.add(Screen.AppsAi)
        screen = backStack.last()
        loadAppsAi()
    }

    /** Pops one level. Returns false when already at the root (let the system handle back). */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        screen = backStack.last()
        reloadCurrent()
        return true
    }

    fun refresh() = reloadCurrent()

    /**
     * Re-check the usage-access permission after returning from the system settings screen.
     * When it flips on while the Apps screen is visible, reload so sizes appear.
     */
    fun refreshUsagePermission() {
        val granted = AppScanner.hasUsageStatsPermission(getApplication())
        val gainedAccess = granted && !appsUsagePermission
        appsUsagePermission = granted
        if (gainedAccess && screen == Screen.Apps) {
            loadApps()
        }
    }

    private fun reloadCurrent() {
        when (val s = screen) {
            Screen.Overview -> loadOverview()
            is Screen.Browse -> loadBrowse(s.dir)
            is Screen.AiFlow -> loadAiFlow(s.dir)
            Screen.Apps -> loadApps()
            Screen.AppsAi -> loadAppsAi()
        }
    }

    private fun loadOverview() {
        loadJob?.cancel()
        overviewLoading = true
        device = StorageScanner.deviceStorage()
        loadJob = viewModelScope.launch {
            val dirs = withContext(Dispatchers.IO) { StorageScanner.categoryDirs() }
            categories = withContext(Dispatchers.IO) {
                dirs.map { (label, dir) -> async { StorageScanner.scanCategory(label, dir) } }.awaitAll()
            }
            overviewLoading = false
        }
    }

    private fun loadBrowse(dir: File) {
        loadJob?.cancel()
        browseLoading = true
        entries = emptyList()
        selected = emptySet()
        loadJob = viewModelScope.launch {
            entries = withContext(Dispatchers.IO) { StorageScanner.listEntries(dir) }
            browseLoading = false
        }
    }

    private fun loadAiFlow(dir: File) {
        loadJob?.cancel()
        aiLoading = true
        aiDir = dir
        aiInstruction = DEFAULT_AI_INSTRUCTION
        aiPrompt = ""
        aiAnswer = ""
        aiMatched = null
        aiInvalidIds = emptyList()
        aiNoIds = false
        aiSelected = emptySet()
        aiSnapshot = emptyList()
        aiAllFiles = emptyList()
        aiSubdirs = emptyList()
        aiSubdirEnabled = emptySet()
        loadJob = viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { StorageScanner.allFiles(dir) }
            val subs = withContext(Dispatchers.IO) { StorageScanner.subdirs(dir) }
            aiAllFiles = all
            aiSubdirs = subs
            aiSubdirEnabled = subs.toSet()
            rebuildAiPrompt()
            aiLoading = false
        }
    }

    /** Recomputes [aiSnapshot] and [aiPrompt] from the current dir, files, subdir filter, and instruction. */
    private fun rebuildAiPrompt() {
        val dir = aiDir ?: return
        val rootPath = dir.absolutePath
        val enabledPaths = aiSubdirEnabled.map { "${it.absolutePath}/" }
        val filtered = aiAllFiles.filter { file ->
            val parentPath = file.parentFile?.absolutePath ?: return@filter false
            // Files directly under the target dir are always included; deeper files require
            // their top-level subdir to be enabled.
            if (parentPath == rootPath) return@filter true
            enabledPaths.any { file.absolutePath.startsWith(it) }
        }
        aiSnapshot = filtered.sortedByDescending { it.length() }.take(aiPromptFileCount)
        aiPrompt = buildAiPrompt(aiInstruction, dir, aiSnapshot)
    }

    fun updateAiInstruction(text: String) {
        aiInstruction = text
        val dir = aiDir ?: return
        aiPrompt = buildAiPrompt(text, dir, aiSnapshot)
    }

    fun toggleAiSubdir(sub: File) {
        aiSubdirEnabled = if (sub in aiSubdirEnabled) aiSubdirEnabled - sub else aiSubdirEnabled + sub
        rebuildAiPrompt()
        // Snapshot's ID mapping changed; drop any prior parse so the next "解析" rebinds.
        invalidateAiParse()
    }

    fun updateAiAnswer(text: String) {
        aiAnswer = text
        invalidateAiParse()
    }

    private fun invalidateAiParse() {
        if (aiMatched != null || aiNoIds) {
            aiMatched = null
            aiInvalidIds = emptyList()
            aiNoIds = false
            aiSelected = emptySet()
        }
    }

    fun parseAnswer() {
        when (val result = parseAnswerIds(aiAnswer, aiSnapshot.size)) {
            AnswerParseResult.NoIds -> {
                aiNoIds = true
                aiMatched = null
                aiInvalidIds = emptyList()
                aiSelected = emptySet()
            }

            is AnswerParseResult.Ok -> {
                val files = result.validIds.map { aiSnapshot[it - 1] }
                aiMatched = files
                aiInvalidIds = result.invalidIds
                aiNoIds = false
                aiSelected = files.toSet()
            }
        }
    }

    fun toggleAiSelect(file: File) {
        aiSelected = if (file in aiSelected) aiSelected - file else aiSelected + file
    }

    val aiSelectedBytes: Long
        get() = (aiMatched ?: emptyList()).filter { it in aiSelected }.sumOf { it.length() }

    fun deleteAiSelected() {
        val targets = aiSelected.toList()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { targets.forEach { it.deleteRecursively() } }
            // Pop the AI flow; the underlying browser reloads via reloadCurrent().
            back()
        }
    }

    fun toggleSelect(file: File) {
        selected = if (file in selected) selected - file else selected + file
    }

    fun clearSelection() {
        selected = emptySet()
    }

    val selectedBytes: Long
        get() = entries.filter { it.file in selected }.sumOf { it.totalBytes }

    fun deleteSelected() {
        val targets = selected.toList()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { targets.forEach { it.deleteRecursively() } }
            selected = emptySet()
            reloadCurrent()
        }
    }

    private fun loadApps() {
        loadJob?.cancel()
        appsLoading = true
        appsUsagePermission = AppScanner.hasUsageStatsPermission(getApplication())
        loadJob = viewModelScope.launch {
            apps = withContext(Dispatchers.IO) { AppScanner.listUserApps(getApplication()) }
            appsLoading = false
        }
    }

    private fun loadAppsAi() {
        loadJob?.cancel()
        appsAiLoading = true
        appsAiInstruction = DEFAULT_APPS_AI_INSTRUCTION
        appsAiPrompt = ""
        appsAiAnswer = ""
        appsAiMatched = null
        appsAiInvalidIds = emptyList()
        appsAiNoIds = false
        appsAiSelected = emptySet()
        appsAiSnapshot = emptyList()
        loadJob = viewModelScope.launch {
            // Apps state may already be populated from the Apps screen; refresh in case.
            if (apps.isEmpty()) {
                apps = withContext(Dispatchers.IO) { AppScanner.listUserApps(getApplication()) }
            }
            rebuildAppsAiPrompt()
            appsAiLoading = false
        }
    }

    private fun rebuildAppsAiPrompt() {
        appsAiSnapshot = apps.take(aiPromptFileCount)
        appsAiPrompt = buildAppsAiPrompt(appsAiInstruction, appsAiSnapshot)
    }

    fun updateAppsAiInstruction(text: String) {
        appsAiInstruction = text
        appsAiPrompt = buildAppsAiPrompt(text, appsAiSnapshot)
    }

    fun updateAppsAiAnswer(text: String) {
        appsAiAnswer = text
        invalidateAppsAiParse()
    }

    private fun invalidateAppsAiParse() {
        if (appsAiMatched != null || appsAiNoIds) {
            appsAiMatched = null
            appsAiInvalidIds = emptyList()
            appsAiNoIds = false
            appsAiSelected = emptySet()
        }
    }

    fun parseAppsAnswer() {
        when (val result = parseAnswerIds(appsAiAnswer, appsAiSnapshot.size)) {
            AnswerParseResult.NoIds -> {
                appsAiNoIds = true
                appsAiMatched = null
                appsAiInvalidIds = emptyList()
                appsAiSelected = emptySet()
            }

            is AnswerParseResult.Ok -> {
                val matched = result.validIds.map { appsAiSnapshot[it - 1] }
                appsAiMatched = matched
                appsAiInvalidIds = result.invalidIds
                appsAiNoIds = false
                appsAiSelected = matched.map { it.packageName }.toSet()
            }
        }
    }

    fun toggleAppsAiSelect(pkg: String) {
        appsAiSelected = if (pkg in appsAiSelected) appsAiSelected - pkg else appsAiSelected + pkg
    }
}
