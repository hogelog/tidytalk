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
import org.hogel.tidytalk.data.DeviceStorage
import org.hogel.tidytalk.data.StorageCategory
import org.hogel.tidytalk.data.StorageEntry
import org.hogel.tidytalk.data.StorageScanner
import java.io.File

sealed interface Screen {
    data object Overview : Screen
    data class Browse(val dir: File) : Screen
}

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
