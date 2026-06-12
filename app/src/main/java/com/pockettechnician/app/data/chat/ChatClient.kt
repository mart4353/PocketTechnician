package com.pockettechnician.app.data.chat

import com.pockettechnician.app.data.ai.AiProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a conversation to the selected provider and returns the assistant
 * reply. Both APIs are stateless, so the full message history is sent on
 * every call.
 */
class ChatClient {
    fun complete(
        provider: AiProvider,
        modelId: String,
        apiKey: String,
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
    ): String {
        require(messages.isNotEmpty()) { "Cannot send an empty conversation" }
        val request = when (provider) {
            AiProvider.ANTHROPIC -> anthropicRequest(modelId, messages, systemPrompt, maxTokens)
            AiProvider.OPENAI, AiProvider.GROK -> openAiStyleRequest(modelId, messages, systemPrompt)
        }
        val connection = openConnection(provider, apiKey)
        try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (responseCode !in 200..299) {
                val message = parseErrorMessage(body) ?: "HTTP $responseCode"
                throw ChatException(provider, message)
            }
            return when (provider) {
                AiProvider.ANTHROPIC -> parseAnthropicReply(body)
                AiProvider.OPENAI, AiProvider.GROK -> parseOpenAiStyleReply(body)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(provider: AiProvider, apiKey: String): HttpURLConnection {
        val url = URL(provider.chatEndpoint())
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("Content-Type", "application/json")
            when (provider) {
                AiProvider.ANTHROPIC -> {
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                }
                AiProvider.OPENAI, AiProvider.GROK -> {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }
        }
    }

    private fun readBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    private fun anthropicRequest(
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        maxTokens: Int,
    ): JSONObject = JSONObject().apply {
        put("model", modelId)
        put("max_tokens", maxTokens)
        if (!systemPrompt.isNullOrBlank()) put("system", systemPrompt)
        put("messages", messagesJson(messages))
    }

    // No token cap: OpenAI and Grok disagree on max_tokens vs
    // max_completion_tokens, so the API default is used for both.
    private fun openAiStyleRequest(
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
    ): JSONObject = JSONObject().apply {
        put("model", modelId)
        val all = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            all.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        val history = messagesJson(messages)
        for (index in 0 until history.length()) all.put(history.get(index))
        put("messages", all)
    }

    private fun messagesJson(messages: List<ChatMessage>): JSONArray {
        val array = JSONArray()
        for (message in messages) {
            array.put(
                JSONObject()
                    .put("role", if (message.role == ChatRole.USER) "user" else "assistant")
                    .put("content", message.text),
            )
        }
        return array
    }

    private fun parseAnthropicReply(body: String): String {
        val json = JSONObject(body)
        val stopReason = json.optString("stop_reason")
        if (stopReason == "refusal") {
            throw ChatException(AiProvider.ANTHROPIC, "The model declined to answer this request")
        }
        val content = json.getJSONArray("content")
        val text = buildString {
            for (index in 0 until content.length()) {
                val block = content.getJSONObject(index)
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
        if (text.isBlank()) {
            throw ChatException(AiProvider.ANTHROPIC, "Empty reply (stop_reason: $stopReason)")
        }
        return text
    }

    private fun parseOpenAiStyleReply(body: String): String {
        val choices = JSONObject(body).getJSONArray("choices")
        if (choices.length() == 0) throw ChatException(AiProvider.OPENAI, "Empty reply")
        return choices.getJSONObject(0).getJSONObject("message").optString("content")
    }

    private fun parseErrorMessage(body: String): String? = runCatching {
        val json = JSONObject(body)
        json.optJSONObject("error")?.optString("message")
            ?: json.optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun AiProvider.chatEndpoint(): String = when (this) {
        AiProvider.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
        AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
        AiProvider.GROK -> "https://api.x.ai/v1/chat/completions"
    }

    companion object {
        private const val DEFAULT_MAX_TOKENS = 4096

        // Reasoning models can take a while before the first byte arrives.
        private const val READ_TIMEOUT_MILLIS = 180_000
    }
}

class ChatException(
    val provider: AiProvider,
    override val message: String,
) : Exception(message)
