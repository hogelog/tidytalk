package org.hogel.tidytalk.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Preset values for the AI prompt file count chip selector. First entry is the default. */
object PromptFileCount {
    val PRESETS = listOf(50, 100, 200, 500)
    const val DEFAULT = 200
}

private val Context.tidyTalkDataStore: DataStore<Preferences> by preferencesDataStore(name = "tidytalk")

class TidyTalkSettings(context: Context) {
    private val dataStore = context.applicationContext.tidyTalkDataStore

    val promptFileCount: Flow<Int> =
        dataStore.data.map { it[KEY_PROMPT_FILE_COUNT] ?: PromptFileCount.DEFAULT }

    suspend fun setPromptFileCount(value: Int) {
        dataStore.edit { it[KEY_PROMPT_FILE_COUNT] = value }
    }

    companion object {
        private val KEY_PROMPT_FILE_COUNT = intPreferencesKey("prompt_file_count")
    }
}
