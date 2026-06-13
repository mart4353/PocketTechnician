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

    /** Apply [transform] to one tool call inside one message, persisting the result. */
    suspend fun updateToolCall(
        conversationId: String,
        messageId: String,
        toolCallId: String,
        transform: (ToolCall) -> ToolCall,
    ) {
        update { list ->
            list.map { conversation ->
                if (conversation.id != conversationId) return@map conversation
                conversation.copy(
                    messages = conversation.messages.map { message ->
                        if (message.id != messageId) message
                        else message.copy(
                            toolCalls = message.toolCalls.map { call ->
                                if (call.id == toolCallId) transform(call) else call
                            },
                        )
                    },
                )
            }
        }
    }

    /**
     * Reset the HID tool-call test conversations to their pristine seed state on
     * every launch. These are throwaway fixtures for exercising the tool-call
     * approval flow, so any carried-on state — the call already accepted/run,
     * follow-up turns appended — is discarded and replaced by the original
     * "awaiting approval" bubbles. Fixed ids make the swap deterministic.
     */
    suspend fun resetTestConversations() {
        val seeds = testConversations()
        val seedIds = seeds.map { it.id }.toSet()
        update { list -> list.filterNot { it.id in seedIds } + seeds }
    }

    private fun testConversations(): List<Conversation> {
        fun convo(id: String, title: String, ask: String, reply: String, call: ToolCall) =
            Conversation(
                id = id,
                title = title,
                messages = listOf(
                    ChatMessage(ChatRole.USER, ask),
                    ChatMessage(ChatRole.ASSISTANT, reply, toolCalls = listOf(call)),
                ),
            )
        return listOf(
            convo(
                id = "test-type-text",
                title = "Test: type text",
                ask = "Type \"Hello world!\" on my computer.",
                reply = "Sure — I'll type that for you. Approve the action below.",
                call = ToolCall(
                    id = "seed-type-text",
                    name = "type_text",
                    arguments = JSONObject().put("text", "Hello world!").toString(),
                ),
            ),
            convo(
                id = "test-move-pointer-to",
                title = "Test: move pointer to",
                ask = "Move the mouse to the center of my 1920x1080 screen.",
                reply = "Will do — homing the pointer, then moving to the center (960, 540). Approve below.",
                call = ToolCall(
                    id = "seed-move-pointer-to",
                    name = "move_pointer_to",
                    arguments = JSONObject().put("x", 960).put("y", 540).toString(),
                ),
            ),
            convo(
                id = "test-right-click",
                title = "Test: right click",
                ask = "Right-click where the pointer is.",
                reply = "Okay — I'll right-click. Approve below.",
                call = ToolCall(
                    id = "seed-right-click",
                    name = "mouse_press",
                    arguments = JSONObject().put("button", "right").toString(),
                ),
            ),
            convo(
                id = "test-left-click",
                title = "Test: left click",
                ask = "Left-click where the pointer is.",
                reply = "Okay — I'll left-click. Approve below.",
                call = ToolCall(
                    id = "seed-left-click",
                    name = "mouse_press",
                    arguments = JSONObject().put("button", "left").toString(),
                ),
            ),
        )
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
                .put("id", message.id)
                .put("role", message.role.name)
                .put("text", message.text)
                .put("timestamp", message.timestampEpochMillis)
            if (message.imageBase64 != null) msgObj.put("imageBase64", message.imageBase64)
            if (message.toolCalls.isNotEmpty()) {
                val toolArray = JSONArray()
                for (call in message.toolCalls) {
                    toolArray.put(
                        JSONObject()
                            .put("id", call.id)
                            .put("name", call.name)
                            .put("arguments", call.arguments)
                            .put("status", call.status.name)
                            .put("resultText", call.resultText ?: JSONObject.NULL),
                    )
                }
                msgObj.put("toolCalls", toolArray)
            }
            messageArray.put(msgObj)
        }
        put("messages", messageArray)
    }

    private fun JSONArray?.toToolCalls(): List<ToolCall> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                add(
                    ToolCall(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        arguments = item.getString("arguments"),
                        status = runCatching { ToolCallStatus.valueOf(item.getString("status")) }
                            .getOrDefault(ToolCallStatus.PENDING),
                        resultText = if (item.isNull("resultText")) null else item.optString("resultText"),
                    ),
                )
            }
        }
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
                        id = if (item.has("id")) item.getString("id") else java.util.UUID.randomUUID().toString(),
                        toolCalls = item.optJSONArray("toolCalls").toToolCalls(),
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
