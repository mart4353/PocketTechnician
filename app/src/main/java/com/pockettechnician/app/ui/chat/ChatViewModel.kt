package com.pockettechnician.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettechnician.app.PocketTechnicianApplication
import com.pockettechnician.app.hid.HidManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One entry rendered in the chat transcript. */
sealed interface ChatItem {
    val key: Long

    data class Said(override val key: Long, val fromUser: Boolean, val text: String) : ChatItem

    data class Proposal(
        override val key: Long,
        val label: String,
        val detail: String,
        val sensitive: Boolean,
        val status: ProposalStatus,
    ) : ChatItem
}

enum class ProposalStatus { Pending, Running, Done, Failed, Denied }

/** What an approved proposal actually does over HID. */
private sealed interface DemoAction {
    data class Type(val text: String) : DemoAction
    data class Move(val dx: Int, val dy: Int) : DemoAction
}

/** A scripted beat of the demo conversation. */
private sealed interface Beat {
    data class Say(val fromUser: Boolean, val text: String) : Beat
    data class Propose(
        val label: String,
        val detail: String,
        val sensitive: Boolean,
        val action: DemoAction,
    ) : Beat
}

/**
 * Drives the "Hello World! + move the mouse" demo: a scripted conversation
 * where each proposed HID action waits for the user's approval, then runs over
 * [HidManager]. Proposals only reveal the next beat once they complete, mirroring
 * the agent control loop (propose -> approve -> execute -> continue).
 */
class ChatViewModel(private val hidManager: HidManager) : ViewModel() {

    private val script: List<Beat> = listOf(
        Beat.Say(
            fromUser = true,
            text = "My PC is connected to this tablet over Bluetooth. Can you type a hello " +
                "message and move the mouse to check that keyboard and pointer control work?",
        ),
        Beat.Say(
            fromUser = false,
            text = "Yes. The tablet is registered as a Bluetooth keyboard and mouse for this " +
                "computer, so I can drive it directly. I'll do two actions — each waits for your approval.",
        ),
        Beat.Propose(
            label = "type_text",
            detail = "Type \"Hello World!\" into the focused window",
            sensitive = false,
            action = DemoAction.Type("Hello World!"),
        ),
        Beat.Say(
            fromUser = false,
            text = "Typed “Hello World!”. Now I'll reposition the pointer to (200, 120) to confirm mouse control.",
        ),
        Beat.Propose(
            label = "move_pointer",
            detail = "Move the pointer to (200, 120)",
            sensitive = false,
            action = DemoAction.Move(dx = 200, dy = 120),
        ),
        Beat.Say(
            fromUser = false,
            text = "Pointer moved. Keyboard and mouse output are both confirmed working over Bluetooth HID. ✅",
        ),
    )

    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private var scriptIndex = 0
    private var nextKey = 0L
    /** Action for the proposal currently awaiting approval, keyed by its item key. */
    private var pendingAction: Pair<Long, DemoAction>? = null

    init {
        advance()
    }

    /** Append scripted beats until the next proposal (which waits for approval) or the end. */
    private fun advance() {
        while (scriptIndex < script.size) {
            when (val beat = script[scriptIndex]) {
                is Beat.Say -> {
                    append(ChatItem.Said(nextKey++, beat.fromUser, beat.text))
                    scriptIndex++
                }
                is Beat.Propose -> {
                    val key = nextKey++
                    append(
                        ChatItem.Proposal(
                            key = key,
                            label = beat.label,
                            detail = beat.detail,
                            sensitive = beat.sensitive,
                            status = ProposalStatus.Pending,
                        ),
                    )
                    pendingAction = key to beat.action
                    return
                }
            }
        }
    }

    fun approve(key: Long) {
        val pending = pendingAction ?: return
        if (pending.first != key) return
        updateProposal(key) { it.copy(status = ProposalStatus.Running) }
        viewModelScope.launch {
            val ok = when (val action = pending.second) {
                is DemoAction.Type -> hidManager.typeText(action.text)
                is DemoAction.Move -> hidManager.movePointer(action.dx, action.dy)
            }
            updateProposal(key) {
                it.copy(status = if (ok) ProposalStatus.Done else ProposalStatus.Failed)
            }
            if (ok) {
                pendingAction = null
                scriptIndex++
                advance()
            }
        }
    }

    fun deny(key: Long) {
        if (pendingAction?.first != key) return
        updateProposal(key) { it.copy(status = ProposalStatus.Denied) }
    }

    /** Restart the demo from the top. */
    fun reset() {
        scriptIndex = 0
        pendingAction = null
        _items.value = emptyList()
        advance()
    }

    private fun append(item: ChatItem) {
        _items.value = _items.value + item
    }

    private fun updateProposal(key: Long, transform: (ChatItem.Proposal) -> ChatItem.Proposal) {
        _items.value = _items.value.map { item ->
            if (item is ChatItem.Proposal && item.key == key) transform(item) else item
        }
    }

    class Factory(
        private val application: PocketTechnicianApplication,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(application.hidManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
