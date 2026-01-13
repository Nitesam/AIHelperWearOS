package com.base.aihelperwearos.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import com.base.aihelperwearos.utils.getCurrentLanguageCode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("language")
    }

    /**
     * Reads the current system language code from the device configuration.
     *
     * @return system language code as a `String`.
     */
    private fun getSystemLanguage(): String {
        return context.getCurrentLanguageCode()
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val savedLanguage = preferences[LANGUAGE_KEY]
        if (savedLanguage.isNullOrEmpty()) {
            val systemLang = getSystemLanguage()
            android.util.Log.d("UserPreferences", "No saved language, using system: $systemLang")
            systemLang
        } else {
            android.util.Log.d("UserPreferences", "Using saved language: $savedLanguage")
            savedLanguage
        }
    }

    /**
     * Persists the selected language code in DataStore.
     *
     * @param language language code to save.
     * @return `Unit` after the preference is stored.
     */
    suspend fun setLanguage(language: String) {
        android.util.Log.d("UserPreferences", "Saving language: $language")
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    /**
     * Retrieves the stored language code or falls back to the system language.
     *
     * @return selected language code as a `String`.
     */
    fun getLanguage(): String {
        return runBlocking {
            context.dataStore.data.map { preferences ->
                preferences[LANGUAGE_KEY] ?: getSystemLanguage()
            }.first()
        }
    }
}
