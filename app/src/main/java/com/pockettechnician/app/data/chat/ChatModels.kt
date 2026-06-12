package com.pockettechnician.app.data.chat

import java.util.UUID

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val timestampEpochMillis: Long = System.currentTimeMillis(),
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
