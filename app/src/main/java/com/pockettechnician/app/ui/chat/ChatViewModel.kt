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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val conversationTitle: String? = null,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val modelLabel: String? = null,
    val modelConfigured: Boolean = false,
    val pendingImageBase64: String? = null,
)

/**
 * Drives the Chat tab: sends the active conversation to the selected model
 * and appends the reply. The active conversation lives in
 * [ConversationRepository] so the Conversations tab sees the same state.
 */
class ChatViewModel(
    private val repository: ConversationRepository,
    private val chatClient: ChatClient,
    private val apiKeyStore: ApiKeyStore,
    private val aiPreferencesStore: AiPreferencesStore,
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

    val uiState: StateFlow<ChatUiState> = combine(coreUiState, _pendingImageBase64) { state, pending ->
        state.copy(pendingImageBase64 = pending)
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
            val selection = aiPreferencesStore.selectedModel.first()
            val apiKey = selection?.let { apiKeyStore.get(it.provider) }
            if (selection == null || apiKey == null) {
                errorMessage.value = "Select a model and add its API key on the Dashboard tab."
                return@launch
            }
            isSending.value = true
            errorMessage.value = null
            _pendingImageBase64.value = null
            val conversationId = repository.activeConversationId.value
                ?: repository.create().id
            repository.appendMessage(conversationId, ChatMessage(ChatRole.USER, trimmed, imageBase64))
            val history = repository.get(conversationId)?.messages.orEmpty()
            runCatching {
                withContext(Dispatchers.IO) {
                    chatClient.complete(
                        provider = selection.provider,
                        modelId = selection.modelId,
                        apiKey = apiKey,
                        messages = history,
                        systemPrompt = SYSTEM_PROMPT,
                    )
                }
            }.onSuccess { reply ->
                repository.appendMessage(conversationId, ChatMessage(ChatRole.ASSISTANT, reply))
                maybeAutoName(conversationId, selection, apiKey)
            }.onFailure { error ->
                errorMessage.value = error.message ?: "Request failed"
            }
            isSending.value = false
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

    /** Have the model name the conversation in the background after the first exchange. */
    private fun maybeAutoName(
        conversationId: String,
        selection: AiModelSelection,
        apiKey: String,
    ) {
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
                    provider = selection.provider,
                    modelId = selection.modelId,
                    apiKey = apiKey,
                    messages = listOf(ChatMessage(ChatRole.USER, prompt)),
                    maxTokens = 1024,
                )
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
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are Pocket Technician, a friendly tech-support assistant running on the " +
                "user's phone. Help diagnose and fix computer problems step by step. " +
                "Keep replies short and practical, and ask for a photo of the screen " +
                "when you need to see the computer's state."
    }
}
