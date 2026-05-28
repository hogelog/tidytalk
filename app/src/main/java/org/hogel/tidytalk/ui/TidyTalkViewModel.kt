package org.hogel.tidytalk.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hogel.tidytalk.data.AnswerParseResult
import org.hogel.tidytalk.data.DeviceStorage
import org.hogel.tidytalk.data.StorageCategory
import org.hogel.tidytalk.data.StorageEntry
import org.hogel.tidytalk.data.StorageScanner
import org.hogel.tidytalk.data.buildAiPrompt
import org.hogel.tidytalk.data.parseAnswerIds
import java.io.File

sealed interface Screen {
    data object Overview : Screen
    data class Browse(val dir: File) : Screen
    data class AiFlow(val dir: File) : Screen
}

/** Maximum files listed in an AI cleaning prompt (top-N by size, recursive). */
private const val AI_PROMPT_FILE_LIMIT = 200

class TidyTalkViewModel : ViewModel() {

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

    private var loadJob: Job? = null
    private var started = false

    /** Called once after the storage permission is granted. */
    fun start() {
        if (started) return
        started = true
        loadOverview()
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

    /** Pops one level. Returns false when already at the root (let the system handle back). */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        screen = backStack.last()
        reloadCurrent()
        return true
    }

    fun refresh() = reloadCurrent()

    private fun reloadCurrent() {
        when (val s = screen) {
            Screen.Overview -> loadOverview()
            is Screen.Browse -> loadBrowse(s.dir)
            is Screen.AiFlow -> loadAiFlow(s.dir)
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
        aiPrompt = ""
        aiAnswer = ""
        aiMatched = null
        aiInvalidIds = emptyList()
        aiNoIds = false
        aiSelected = emptySet()
        aiSnapshot = emptyList()
        loadJob = viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                StorageScanner.topFilesByBytes(dir, AI_PROMPT_FILE_LIMIT)
            }
            aiSnapshot = snapshot
            aiPrompt = buildAiPrompt(dir, snapshot)
            aiLoading = false
        }
    }

    fun updateAiAnswer(text: String) {
        aiAnswer = text
        // Editing the answer invalidates any previous parse.
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
}
