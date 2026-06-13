package com.pockettechnician.app.data.chat

import java.util.UUID

enum class ChatRole { USER, ASSISTANT }

/** Lifecycle of an HID tool call the model wants to run on the target computer. */
enum class ToolCallStatus { PENDING, RUNNING, EXECUTED, FAILED, REJECTED }

/**
 * A single tool invocation requested by the model. [arguments] is the raw JSON
 * object string of the tool input. [id] is the provider's tool-use id (or a
 * generated one for seeded calls) and is echoed back as the tool result id.
 * Carried on an ASSISTANT [ChatMessage] and gated behind user approval before
 * it runs.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val status: ToolCallStatus = ToolCallStatus.PENDING,
    val resultText: String? = null,
)

/** True once the user has accepted or rejected the call, so it no longer needs a decision. */
val ToolCall.isResolved: Boolean
    get() = status == ToolCallStatus.EXECUTED ||
        status == ToolCallStatus.FAILED ||
        status == ToolCallStatus.REJECTED

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val imageBase64: String? = null,
    val timestampEpochMillis: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
    val toolCalls: List<ToolCall> = emptyList(),
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
)

data class ConversationSummary(
    val id: String,
    val title: String?,
    val updatedAtEpochMillis: Long,
    val messageCount: Int,
)

fun Conversation.toSummary() = ConversationSummary(
    id = id,
    title = title,
    updatedAtEpochMillis = updatedAtEpochMillis,
    messageCount = messages.size,
)

/** Display title with a fallback for conversations that haven't been auto-named yet. */
val ConversationSummary.displayTitle: String
    get() = title?.takeIf { it.isNotBlank() } ?: "New conversation"
