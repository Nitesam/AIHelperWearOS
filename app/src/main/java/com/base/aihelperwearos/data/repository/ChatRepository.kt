@file:Suppress("OPT_IN_USAGE")

package com.base.aihelperwearos.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_data")

@Serializable
data class ChatSession(
    val id: Long,
    val modelId: String,
    val title: String,
    val timestamp: Long,
    val isAnalysisMode: Boolean = false
)

@Serializable
data class ChatMessage(
    val id: Long,
    val sessionId: Long,
    val role: String,
    val content: String,
    val timestamp: Long,
    val audioPath: String? = null
)

@Serializable
data class ChatData(
    val sessions: List<ChatSession> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val nextSessionId: Long = 1,
    val nextMessageId: Long = 1
)

class ChatRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val CHAT_DATA_KEY = stringPreferencesKey("chat_data")
    }

    private suspend fun getChatData(): ChatData {
        android.util.Log.d("ChatRepository", "getChatData - START")
        return try {
            val data = context.dataStore.data.map { preferences ->
                val jsonString = preferences[CHAT_DATA_KEY]
                android.util.Log.d("ChatRepository", "getChatData - jsonString: ${jsonString?.take(100)}")

                if (jsonString == null) {
                    android.util.Log.d("ChatRepository", "getChatData - No data, returning empty ChatData")
                    ChatData()
                } else {
                    try {
                        val decoded = json.decodeFromString<ChatData>(jsonString)
                        android.util.Log.d("ChatRepository", "getChatData - Decoded data: sessions=${decoded.sessions.size}")
                        decoded
                    } catch (e: Exception) {
                        android.util.Log.e("ChatRepository", "getChatData - Decode error", e)
                        ChatData()
                    }
                }
            }.firstOrNull() ?: ChatData()

            android.util.Log.d("ChatRepository", "getChatData - SUCCESS - sessions: ${data.sessions.size}")
            data
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "getChatData - ERROR", e)
            ChatData()
        }
    }

    private suspend fun saveChatData(data: ChatData) {
        android.util.Log.d("ChatRepository", "saveChatData - START")
        try {
            context.dataStore.edit { preferences ->
                val jsonString = json.encodeToString(data)
                preferences[CHAT_DATA_KEY] = jsonString
                android.util.Log.d("ChatRepository", "saveChatData - Saved ${jsonString.length} chars")
            }
            android.util.Log.d("ChatRepository", "saveChatData - SUCCESS")
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "saveChatData - ERROR", e)
            throw e
        }
    }

    fun getAllSessions(): Flow<List<ChatSession>> {
        return context.dataStore.data.map { preferences ->
            val jsonString = preferences[CHAT_DATA_KEY] ?: return@map emptyList()
            try {
                val data = json.decodeFromString<ChatData>(jsonString)
                data.sessions.sortedByDescending { it.timestamp }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return context.dataStore.data.map { preferences ->
            val jsonString = preferences[CHAT_DATA_KEY] ?: return@map emptyList()
            try {
                val data = json.decodeFromString<ChatData>(jsonString)
                data.messages
                    .filter { it.sessionId == sessionId }
                    .sortedBy { it.timestamp }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun createSession(modelId: String, title: String, isAnalysisMode: Boolean): Long {
        android.util.Log.d("ChatRepository", "createSession - START - modelId: $modelId, title: $title, isAnalysisMode: $isAnalysisMode")

        try {
            val currentData = getChatData()
            android.util.Log.d("ChatRepository", "Current data loaded - sessions: ${currentData.sessions.size}, nextId: ${currentData.nextSessionId}")

            val newSession = ChatSession(
                id = currentData.nextSessionId,
                modelId = modelId,
                title = title,
                timestamp = System.currentTimeMillis(),
                isAnalysisMode = isAnalysisMode
            )

            android.util.Log.d("ChatRepository", "New session created: $newSession")

            val updatedData = currentData.copy(
                sessions = currentData.sessions + newSession,
                nextSessionId = currentData.nextSessionId + 1
            )

            android.util.Log.d("ChatRepository", "Saving updated data...")
            saveChatData(updatedData)

            android.util.Log.d("ChatRepository", "Session saved successfully - ID: ${newSession.id}")
            return newSession.id
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "ERROR in createSession: ${e.message}", e)
            throw e
        }
    }

    suspend fun getSession(sessionId: Long): ChatSession? {
        val data = getChatData()
        return data.sessions.find { it.id == sessionId }
    }

    suspend fun addMessage(sessionId: Long, role: String, content: String, audioPath: String? = null) {
        val currentData = getChatData()
        val newMessage = ChatMessage(
            id = currentData.nextMessageId,
            sessionId = sessionId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            audioPath = audioPath
        )

        val updatedData = currentData.copy(
            messages = currentData.messages + newMessage,
            nextMessageId = currentData.nextMessageId + 1
        )

        saveChatData(updatedData)
    }

    suspend fun updateSessionTitle(sessionId: Long, newTitle: String) {
        val currentData = getChatData()
        val updatedSessions = currentData.sessions.map { session ->
            if (session.id == sessionId) {
                session.copy(title = newTitle)
            } else {
                session
            }
        }

        val updatedData = currentData.copy(sessions = updatedSessions)
        saveChatData(updatedData)
    }

    suspend fun deleteSession(sessionId: Long) {
        val currentData = getChatData()
        val updatedData = currentData.copy(
            sessions = currentData.sessions.filter { it.id != sessionId },
            messages = currentData.messages.filter { it.sessionId != sessionId }
        )
        saveChatData(updatedData)
    }
}

