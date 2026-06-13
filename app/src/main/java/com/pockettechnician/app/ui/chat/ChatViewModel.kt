package com.pockettechnician.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettechnician.app.PocketTechnicianApplication
import com.pockettechnician.app.data.ai.AiModelSelection
import com.pockettechnician.app.data.ai.AiPreferencesStore
import com.pockettechnician.app.data.ai.ApiKeyStore
import com.pockettechnician.app.data.chat.ChatClient
import com.pockettechnician.app.data.chat.ChatMessage
import com.pockettechnician.app.data.chat.ChatRole
import com.pockettechnician.app.data.chat.ConversationRepository
import com.pockettechnician.app.data.chat.HidTools
import com.pockettechnician.app.data.chat.ToolCall
import com.pockettechnician.app.data.chat.ToolCallStatus
import com.pockettechnician.app.data.chat.isResolved
import com.pockettechnician.app.hid.HidDescriptors
import com.pockettechnician.app.hid.HidManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val conversationTitle: String? = null,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val modelLabel: String? = null,
    val modelConfigured: Boolean = false,
    val pendingImageBase64: String? = null,
    val hidConnected: Boolean = false,
)

/**
 * Drives the Chat tab: sends the active conversation to the selected model and
 * appends the reply. When the model requests HID tool calls they are appended
 * as pending and surfaced for the user to approve; on approval the call runs on
 * [HidManager] and the result is fed back to the model. The active conversation
 * lives in [ConversationRepository] so the Conversations tab sees the same state.
 */
class ChatViewModel(
    private val repository: ConversationRepository,
    private val chatClient: ChatClient,
    private val apiKeyStore: ApiKeyStore,
    private val aiPreferencesStore: AiPreferencesStore,
    private val hidManager: HidManager,
    private val systemPrompt: String,
) : ViewModel() {

    private val isSending = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val _pendingImageBase64 = MutableStateFlow<String?>(null)

    private val modelState = combine(
        aiPreferencesStore.selectedModel,
        apiKeyStore.configuredProviders,
    ) { selection, configured ->
        ModelState(selection, selection != null && selection.provider in configured)
    }

    private val coreUiState = combine(
        repository.conversations,
        repository.activeConversationId,
        modelState,
        isSending,
        errorMessage,
    ) { conversations, activeId, model, sending, error ->
        val active = conversations.firstOrNull { it.id == activeId }
        ChatUiState(
            messages = active?.messages.orEmpty(),
            conversationTitle = active?.title,
            isSending = sending,
            errorMessage = error,
            modelLabel = model.selection?.modelId,
            modelConfigured = model.configured,
        )
    }

    val uiState: StateFlow<ChatUiState> = combine(
        coreUiState,
        _pendingImageBase64,
        hidManager.state,
    ) { state, pending, hid ->
        state.copy(pendingImageBase64 = pending, hidConnected = hid.connected)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    fun attachImage(base64: String) {
        _pendingImageBase64.value = base64
    }

    fun clearPendingImage() {
        _pendingImageBase64.value = null
    }

    fun send(text: String) {
        val trimmed = text.trim()
        val imageBase64 = _pendingImageBase64.value
        if (trimmed.isEmpty() && imageBase64 == null) return
        if (isSending.value) return
        viewModelScope.launch {
            val credentials = resolveCredentials() ?: return@launch
            isSending.value = true
            errorMessage.value = null
            _pendingImageBase64.value = null
            val conversationId = repository.activeConversationId.value
                ?: repository.create().id
            repository.appendMessage(conversationId, ChatMessage(ChatRole.USER, trimmed, imageBase64))
            runAssistantTurn(conversationId, credentials)
            isSending.value = false
        }
    }

    /**
     * Accept or reject a pending tool call. On accept the call runs on the HID
     * link; either way the outcome is recorded as the tool result. Once every
     * tool call on the message is resolved, the model is asked to continue.
     */
    fun resolveToolCall(messageId: String, toolCallId: String, accepted: Boolean) {
        if (isSending.value) return
        val conversationId = repository.activeConversationId.value ?: return
        viewModelScope.launch {
            repository.updateToolCall(conversationId, messageId, toolCallId) {
                it.copy(status = ToolCallStatus.RUNNING)
            }
            val call = repository.get(conversationId)?.messages
                ?.firstOrNull { it.id == messageId }
                ?.toolCalls?.firstOrNull { it.id == toolCallId } ?: return@launch

            val (status, result) = if (!accepted) {
                ToolCallStatus.REJECTED to "User declined to run this action."
            } else {
                executeToolCall(call)
            }
            repository.updateToolCall(conversationId, messageId, toolCallId) {
                it.copy(status = status, resultText = result)
            }

            val message = repository.get(conversationId)?.messages?.firstOrNull { it.id == messageId }
            if (message != null && message.toolCalls.all { it.isResolved }) {
                val credentials = resolveCredentials() ?: return@launch
                isSending.value = true
                runAssistantTurn(conversationId, credentials)
                isSending.value = false
            }
        }
    }

    /** Run one accepted tool call on the HID link, returning (status, result text). */
    private suspend fun executeToolCall(call: ToolCall): Pair<ToolCallStatus, String> {
        if (!hidManager.state.value.connected) {
            return ToolCallStatus.FAILED to "Not connected to a computer over Bluetooth HID."
        }
        val args = runCatching { JSONObject(call.arguments) }.getOrNull()
            ?: return ToolCallStatus.FAILED to "Invalid tool arguments."
        return when (call.name) {
            HidTools.TYPE_TEXT -> {
                val text = args.optString("text")
                if (hidManager.typeText(text)) ToolCallStatus.EXECUTED to "Typed \"$text\"."
                else ToolCallStatus.FAILED to "Failed to send keystrokes."
            }
            HidTools.MOVE_POINTER -> {
                val dx = args.optInt("dx")
                val dy = args.optInt("dy")
                if (hidManager.movePointer(dx, dy)) ToolCallStatus.EXECUTED to "Moved pointer by ($dx, $dy)."
                else ToolCallStatus.FAILED to "Failed to move pointer."
            }
            HidTools.MOUSE_PRESS -> {
                val button = args.optString("button", "left")
                val buttons = if (button == "right") HidDescriptors.MOUSE_BUTTON_RIGHT
                else HidDescriptors.MOUSE_BUTTON_LEFT
                if (hidManager.click(buttons)) ToolCallStatus.EXECUTED to "Clicked the $button mouse button."
                else ToolCallStatus.FAILED to "Failed to click."
            }
            else -> ToolCallStatus.FAILED to "Unknown tool: ${call.name}"
        }
    }

    /** Send the current history to the model and append the resulting turn. */
    private suspend fun runAssistantTurn(conversationId: String, credentials: Credentials) {
        val history = repository.get(conversationId)?.messages.orEmpty()
        if (history.isEmpty()) return
        runCatching {
            withContext(Dispatchers.IO) {
                chatClient.complete(
                    provider = credentials.selection.provider,
                    modelId = credentials.selection.modelId,
                    apiKey = credentials.apiKey,
                    messages = history,
                    systemPrompt = systemPrompt,
                )
            }
        }.onSuccess { turn ->
            val toolCalls = turn.toolCalls.map { request ->
                ToolCall(
                    id = request.id.ifBlank { UUID.randomUUID().toString() },
                    name = request.name,
                    arguments = request.argumentsJson,
                )
            }
            repository.appendMessage(
                conversationId,
                ChatMessage(ChatRole.ASSISTANT, turn.text, toolCalls = toolCalls),
            )
            if (toolCalls.isEmpty()) maybeAutoName(conversationId, credentials)
        }.onFailure { error ->
            errorMessage.value = error.message ?: "Request failed"
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            repository.create()
            errorMessage.value = null
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    private suspend fun resolveCredentials(): Credentials? {
        val selection = aiPreferencesStore.selectedModel.first()
        val apiKey = selection?.let { apiKeyStore.get(it.provider) }
        if (selection == null || apiKey == null) {
            errorMessage.value = "Select a model and add its API key on the Dashboard tab."
            return null
        }
        return Credentials(selection, apiKey)
    }

    /** Have the model name the conversation in the background after the first exchange. */
    private fun maybeAutoName(conversationId: String, credentials: Credentials) {
        val conversation = repository.get(conversationId) ?: return
        if (conversation.title != null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val prompt = buildString {
                    appendLine(
                        "Write a short title (at most 6 words) for the tech-support " +
                            "conversation below. Reply with the title only, no quotes.",
                    )
                    conversation.messages.take(4).forEach { message ->
                        val imageSuffix = if (message.imageBase64 != null) " [image]" else ""
                        appendLine("${message.role.name.lowercase()}: ${message.text.take(500)}$imageSuffix")
                    }
                }
                chatClient.complete(
                    provider = credentials.selection.provider,
                    modelId = credentials.selection.modelId,
                    apiKey = credentials.apiKey,
                    messages = listOf(ChatMessage(ChatRole.USER, prompt)),
                    maxTokens = 1024,
                    enableTools = false,
                ).text
            }.onSuccess { title ->
                val cleaned = title.trim().trim('"').take(60)
                if (cleaned.isNotBlank()) repository.setTitle(conversationId, cleaned)
            }
        }
    }

    private data class ModelState(
        val selection: AiModelSelection?,
        val configured: Boolean,
    )

    private data class Credentials(
        val selection: AiModelSelection,
        val apiKey: String,
    )

    class Factory(
        private val application: PocketTechnicianApplication,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(
                    application.conversationRepository,
                    application.chatClient,
                    application.apiKeyStore,
                    application.aiPreferencesStore,
                    application.hidManager,
                    application.systemPrompt,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
