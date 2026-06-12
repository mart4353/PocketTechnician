package com.pockettechnician.app.data.chat

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local conversation storage backed by a JSON file in app-private storage.
 * Also owns the app-wide "active conversation" selection so the Chat and
 * Conversations tabs stay in sync.
 */
class ConversationRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val mutex = Mutex()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    suspend fun load() = mutex.withLock {
        val loaded = withContext(Dispatchers.IO) { readFromDisk() }
        _conversations.value = loaded.sortedByDescending { it.updatedAtEpochMillis }
    }

    fun select(id: String?) {
        _activeConversationId.value = id
    }

    fun get(id: String): Conversation? =
        _conversations.value.firstOrNull { it.id == id }

    suspend fun create(): Conversation {
        val conversation = Conversation()
        update { it + conversation }
        _activeConversationId.value = conversation.id
        return conversation
    }

    suspend fun appendMessage(id: String, message: ChatMessage) {
        update { list ->
            list.map { conversation ->
                if (conversation.id != id) conversation
                else conversation.copy(
                    messages = conversation.messages + message,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun setTitle(id: String, title: String) {
        update { list ->
            list.map { if (it.id == id) it.copy(title = title) else it }
        }
    }

    suspend fun delete(id: String) {
        update { list -> list.filterNot { it.id == id } }
        if (_activeConversationId.value == id) _activeConversationId.value = null
    }

    private suspend fun update(transform: (List<Conversation>) -> List<Conversation>) {
        mutex.withLock {
            val next = transform(_conversations.value).sortedByDescending { it.updatedAtEpochMillis }
            _conversations.value = next
            withContext(Dispatchers.IO) { writeToDisk(next) }
        }
    }

    private fun readFromDisk(): List<Conversation> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toConversation())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeToDisk(conversations: List<Conversation>) {
        val array = JSONArray()
        for (conversation in conversations) array.put(conversation.toJson())
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(array.toString())
        temp.renameTo(file)
    }

    private fun Conversation.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title ?: JSONObject.NULL)
        put("createdAt", createdAtEpochMillis)
        put("updatedAt", updatedAtEpochMillis)
        val messageArray = JSONArray()
        for (message in messages) {
            val msgObj = JSONObject()
                .put("role", message.role.name)
                .put("text", message.text)
                .put("timestamp", message.timestampEpochMillis)
            if (message.imageBase64 != null) msgObj.put("imageBase64", message.imageBase64)
            messageArray.put(msgObj)
        }
        put("messages", messageArray)
    }

    private fun JSONObject.toConversation(): Conversation {
        val messageArray = optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (index in 0 until messageArray.length()) {
                val item = messageArray.getJSONObject(index)
                add(
                    ChatMessage(
                        role = ChatRole.valueOf(item.getString("role")),
                        text = item.getString("text"),
                        imageBase64 = if (item.isNull("imageBase64")) null else item.optString("imageBase64"),
                        timestampEpochMillis = item.optLong("timestamp"),
                    ),
                )
            }
        }
        return Conversation(
            id = getString("id"),
            title = if (isNull("title")) null else optString("title"),
            createdAtEpochMillis = optLong("createdAt"),
            updatedAtEpochMillis = optLong("updatedAt"),
            messages = messages,
        )
    }

    companion object {
        private const val FILE_NAME = "conversations.json"
    }
}
