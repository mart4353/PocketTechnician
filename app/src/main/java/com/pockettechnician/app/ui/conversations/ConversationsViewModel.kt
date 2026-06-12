package com.pockettechnician.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettechnician.app.PocketTechnicianApplication
import com.pockettechnician.app.data.chat.ConversationRepository
import com.pockettechnician.app.data.chat.ConversationSummary
import com.pockettechnician.app.data.chat.toSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationsViewModel(
    private val repository: ConversationRepository,
) : ViewModel() {

    val conversations: StateFlow<List<ConversationSummary>> = repository.conversations
        .map { list -> list.map { it.toSummary() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val activeConversationId: StateFlow<String?> = repository.activeConversationId

    /** Make [id] the active conversation, then run [onOpened] (e.g. switch to the Chat tab). */
    fun open(id: String, onOpened: () -> Unit) {
        repository.select(id)
        onOpened()
    }

    fun createNew(onCreated: () -> Unit) {
        viewModelScope.launch {
            repository.create()
            onCreated()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    class Factory(
        private val application: PocketTechnicianApplication,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ConversationsViewModel::class.java)) {
                return ConversationsViewModel(application.conversationRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
