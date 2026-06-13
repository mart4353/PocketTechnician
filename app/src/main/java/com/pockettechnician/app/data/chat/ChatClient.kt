package com.pockettechnician.app.data.chat

import com.pockettechnician.app.data.ai.AiProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** A tool the model asked to run, as returned in an assistant turn. */
data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * One assistant response: free-text plus any tool calls the model requested.
 * When [toolCalls] is non-empty the model is waiting on tool results before it
 * can continue.
 */
data class AssistantTurn(
    val text: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
)

/**
 * Sends a conversation to the selected provider and returns the assistant
 * turn. Both APIs are stateless, so the full message history — including prior
 * tool calls and their results — is sent on every call.
 */
class ChatClient {
    fun complete(
        provider: AiProvider,
        modelId: String,
        apiKey: String,
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        enableTools: Boolean = true,
    ): AssistantTurn {
        require(messages.isNotEmpty()) { "Cannot send an empty conversation" }
        val request = when (provider) {
            AiProvider.ANTHROPIC -> anthropicRequest(modelId, messages, systemPrompt, maxTokens, enableTools)
            AiProvider.OPENAI, AiProvider.GROK -> openAiStyleRequest(modelId, messages, systemPrompt, enableTools)
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
        enableTools: Boolean,
    ): JSONObject = JSONObject().apply {
        put("model", modelId)
        put("max_tokens", maxTokens)
        if (!systemPrompt.isNullOrBlank()) put("system", systemPrompt)
        if (enableTools) put("tools", HidTools.anthropicTools())
        put("messages", anthropicMessages(messages))
    }

    // No token cap: OpenAI and Grok disagree on max_tokens vs
    // max_completion_tokens, so the API default is used for both.
    private fun openAiStyleRequest(
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        enableTools: Boolean,
    ): JSONObject = JSONObject().apply {
        put("model", modelId)
        if (enableTools) put("tools", HidTools.openAiTools())
        val all = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            all.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        val history = openAiMessages(messages)
        for (index in 0 until history.length()) all.put(history.get(index))
        put("messages", all)
    }

    // ----- Anthropic message serialization -----

    private fun anthropicMessages(messages: List<ChatMessage>): JSONArray {
        val array = JSONArray()
        for (message in messages) {
            when {
                message.role == ChatRole.ASSISTANT && message.toolCalls.isNotEmpty() -> {
                    val content = JSONArray()
                    if (message.text.isNotBlank()) {
                        content.put(JSONObject().put("type", "text").put("text", message.text))
                    }
                    for (call in message.toolCalls) {
                        content.put(
                            JSONObject()
                                .put("type", "tool_use")
                                .put("id", call.id)
                                .put("name", call.name)
                                .put("input", runCatching { JSONObject(call.arguments) }.getOrElse { JSONObject() }),
                        )
                    }
                    array.put(JSONObject().put("role", "assistant").put("content", content))

                    // The model requires a tool_result for every tool_use before it
                    // can continue; only emit them once the user has resolved them all.
                    if (message.toolCalls.all { it.isResolved }) {
                        val results = JSONArray()
                        for (call in message.toolCalls) {
                            results.put(
                                JSONObject()
                                    .put("type", "tool_result")
                                    .put("tool_use_id", call.id)
                                    .put("content", call.resultText ?: "")
                                    .put("is_error", call.status == ToolCallStatus.FAILED),
                            )
                        }
                        array.put(JSONObject().put("role", "user").put("content", results))
                    }
                }
                message.imageBase64 != null && message.role == ChatRole.USER -> {
                    val content = buildImageContentArray(message.imageBase64, message.text, AiProvider.ANTHROPIC)
                    array.put(JSONObject().put("role", "user").put("content", content))
                }
                else -> {
                    val role = if (message.role == ChatRole.USER) "user" else "assistant"
                    array.put(JSONObject().put("role", role).put("content", message.text))
                }
            }
        }
        return array
    }

    // ----- OpenAI / Grok message serialization -----

    private fun openAiMessages(messages: List<ChatMessage>): JSONArray {
        val array = JSONArray()
        for (message in messages) {
            when {
                message.role == ChatRole.ASSISTANT && message.toolCalls.isNotEmpty() -> {
                    val toolCalls = JSONArray()
                    for (call in message.toolCalls) {
                        toolCalls.put(
                            JSONObject()
                                .put("id", call.id)
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject().put("name", call.name).put("arguments", call.arguments),
                                ),
                        )
                    }
                    val assistant = JSONObject().put("role", "assistant").put("tool_calls", toolCalls)
                    if (message.text.isNotBlank()) assistant.put("content", message.text)
                    array.put(assistant)

                    if (message.toolCalls.all { it.isResolved }) {
                        for (call in message.toolCalls) {
                            array.put(
                                JSONObject()
                                    .put("role", "tool")
                                    .put("tool_call_id", call.id)
                                    .put("content", call.resultText ?: ""),
                            )
                        }
                    }
                }
                message.imageBase64 != null && message.role == ChatRole.USER -> {
                    val content = buildImageContentArray(message.imageBase64, message.text, AiProvider.OPENAI)
                    array.put(JSONObject().put("role", "user").put("content", content))
                }
                else -> {
                    val role = if (message.role == ChatRole.USER) "user" else "assistant"
                    array.put(JSONObject().put("role", role).put("content", message.text))
                }
            }
        }
        return array
    }

    private fun buildImageContentArray(
        imageBase64: String,
        text: String,
        provider: AiProvider,
    ): JSONArray = JSONArray().apply {
        when (provider) {
            AiProvider.ANTHROPIC -> {
                put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", imageBase64),
                        ),
                )
                put(JSONObject().put("type", "text").put("text", text))
            }
            AiProvider.OPENAI, AiProvider.GROK -> {
                put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", "data:image/jpeg;base64,$imageBase64"),
                        ),
                )
                put(JSONObject().put("type", "text").put("text", text))
            }
        }
    }

    private fun parseAnthropicReply(body: String): AssistantTurn {
        val json = JSONObject(body)
        val stopReason = json.optString("stop_reason")
        if (stopReason == "refusal") {
            throw ChatException(AiProvider.ANTHROPIC, "The model declined to answer this request")
        }
        val content = json.getJSONArray("content")
        val toolCalls = mutableListOf<ToolCallRequest>()
        val text = buildString {
            for (index in 0 until content.length()) {
                val block = content.getJSONObject(index)
                when (block.optString("type")) {
                    "text" -> append(block.optString("text"))
                    "tool_use" -> toolCalls.add(
                        ToolCallRequest(
                            id = block.optString("id"),
                            name = block.optString("name"),
                            argumentsJson = block.optJSONObject("input")?.toString() ?: "{}",
                        ),
                    )
                }
            }
        }
        if (text.isBlank() && toolCalls.isEmpty()) {
            throw ChatException(AiProvider.ANTHROPIC, "Empty reply (stop_reason: $stopReason)")
        }
        return AssistantTurn(text.trim(), toolCalls)
    }

    private fun parseOpenAiStyleReply(body: String): AssistantTurn {
        val choices = JSONObject(body).getJSONArray("choices")
        if (choices.length() == 0) throw ChatException(AiProvider.OPENAI, "Empty reply")
        val message = choices.getJSONObject(0).getJSONObject("message")
        val text = message.optString("content")
        val toolCalls = mutableListOf<ToolCallRequest>()
        message.optJSONArray("tool_calls")?.let { calls ->
            for (index in 0 until calls.length()) {
                val call = calls.getJSONObject(index)
                val function = call.optJSONObject("function") ?: continue
                toolCalls.add(
                    ToolCallRequest(
                        id = call.optString("id"),
                        name = function.optString("name"),
                        argumentsJson = function.optString("arguments", "{}"),
                    ),
                )
            }
        }
        return AssistantTurn(text, toolCalls)
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
