package app.grapheneos.pdfviewer.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pdf_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class PdfPreferencesRepository (private val context: Context) {

    private object PreferencesKeys {
        val LAST_OPENED_URI = stringPreferencesKey("last_opened_uri")
        val LAST_OPENED_PAGE = intPreferencesKey("last_opened_page")
        val FILE_PAGE_POSITIONS = stringPreferencesKey("file_page_positions")
    }

    companion object {
        private const val MAX_FILE_HISTORY = 50
    }

    data class PdfState(
        val lastOpenedUri: String? = null,
        val lastOpenedPage: Int = 1,
        val filePagePositions: Map<String, Int> = emptyMap()
    )

    val pdfStateFlow: Flow<PdfState> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            PdfState(
                lastOpenedUri = preferences[PreferencesKeys.LAST_OPENED_URI],
                lastOpenedPage = preferences[PreferencesKeys.LAST_OPENED_PAGE] ?: 1,
                filePagePositions = parseFilePagePositions(
                    preferences[PreferencesKeys.FILE_PAGE_POSITIONS]
                )
            )
        }

    suspend fun saveLastOpened(uri: String?, page: Int) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[PreferencesKeys.LAST_OPENED_URI] = uri
            } else {
                preferences.remove(PreferencesKeys.LAST_OPENED_URI)
            }
            preferences[PreferencesKeys.LAST_OPENED_PAGE] = page
        }
    }

    suspend fun clearLastOpened() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.LAST_OPENED_URI)
            preferences.remove(PreferencesKeys.LAST_OPENED_PAGE)
        }
    }

    suspend fun updatePagePosition(fileHash: String, page: Int) {
        context.dataStore.edit { preferences ->
            val currentMap = parseFilePagePositions(
                preferences[PreferencesKeys.FILE_PAGE_POSITIONS]
            ).toMutableMap()

            currentMap[fileHash] = page

            if (currentMap.size > MAX_FILE_HISTORY) {
                val toRemove = currentMap.keys.take(currentMap.size - MAX_FILE_HISTORY)
                toRemove.forEach { currentMap.remove(it) }
            }

            preferences[PreferencesKeys.FILE_PAGE_POSITIONS] = serializeFilePagePositions(currentMap)
        }
    }

    suspend fun getPageForFile(fileHash: String): Int? {
        return pdfStateFlow.first().filePagePositions[fileHash]
    }

    private fun parseFilePagePositions(json: String?): Map<String, Int> {
        if (json.isNullOrEmpty()) return emptyMap()
        val jsonObject = JSONObject(json)
        return buildMap {
            jsonObject.keys().forEach { key ->
                put(key, jsonObject.getInt(key))
            }
        }
    }

    private fun serializeFilePagePositions(map: Map<String, Int>): String {
        return JSONObject(map).toString()
    }
}