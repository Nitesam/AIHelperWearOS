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

    /**
     * Loads persisted chat data from DataStore or returns an empty default.
     *
     * @return `ChatData` with sessions/messages from storage or defaults.
     */
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

    /**
     * Persists chat data to DataStore.
     *
     * @param data chat payload to serialize and store.
     * @return `Unit` after data is saved.
     */
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

    /**
     * Streams all stored chat sessions ordered by most recent.
     *
     * @return `Flow<List<ChatSession>>` of sessions sorted by timestamp.
     */
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

    /**
     * Streams messages for a specific session in chronological order.
     *
     * @param sessionId session identifier to filter messages.
     * @return `Flow<List<ChatMessage>>` of session messages.
     */
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

    /**
     * Creates a new chat session and persists it.
     *
     * @param modelId model identifier used for the session.
     * @param title display title for the session.
     * @param isAnalysisMode whether the session runs in analysis mode.
     * @return new session id as a `Long`.
     */
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

    /**
     * Retrieves a session by id.
     *
     * @param sessionId session identifier to find.
     * @return matching `ChatSession?`, or `null` when missing.
     */
    suspend fun getSession(sessionId: Long): ChatSession? {
        val data = getChatData()
        return data.sessions.find { it.id == sessionId }
    }

    /**
     * Appends a new message to an existing session.
     *
     * @param sessionId session identifier to attach the message.
     * @param role message role (user/assistant/system).
     * @param content message text content.
     * @param audioPath optional local audio path for the message.
     * @return `Unit` after the message is stored.
     */
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

    /**
     * Updates a session title and persists the change.
     *
     * @param sessionId session identifier to update.
     * @param newTitle new title to set.
     * @return `Unit` after the update is saved.
     */
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

    /**
     * Deletes a session and its related messages.
     *
     * @param sessionId session identifier to remove.
     * @return `Unit` after data is updated.
     */
    suspend fun deleteSession(sessionId: Long) {
        val currentData = getChatData()
        val updatedData = currentData.copy(
            sessions = currentData.sessions.filter { it.id != sessionId },
            messages = currentData.messages.filter { it.sessionId != sessionId }
        )
        saveChatData(updatedData)
    }

    /**
     * Exports all chat data to a JSON file in Downloads folder.
     *
     * @return `Result` with the file path on success or an error.
     */
    suspend fun exportToFile(): Result<String> {
        return try {
            val chatData = getChatData()
            val jsonString = json.encodeToString(chatData)
            
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val exportFile = java.io.File(downloadsDir, "aihelper_chat_export.json")
            
            exportFile.writeText(jsonString)
            
            Result.success(exportFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
