package com.pockettechnician.app.data.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ModelFetcher {
    fun fetch(provider: AiProvider, apiKey: String): List<RemoteModel> {
        val connection = openConnection(provider, apiKey)
        try {
            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (responseCode !in 200..299) {
                val message = parseErrorMessage(body) ?: "HTTP $responseCode"
                throw ModelFetchException(provider, message)
            }
            return parseModels(provider, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(provider: AiProvider, apiKey: String): HttpURLConnection {
        val url = URL(provider.modelsEndpoint())
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
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
        return connection
    }

    private fun readBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    private fun parseModels(provider: AiProvider, body: String): List<RemoteModel> {
        val data = JSONObject(body).getJSONArray("data")
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.getJSONObject(index)
                val id = item.getString("id")
                if (!ModelCapabilityFilter.isUsable(provider, id)) continue
                add(
                    when (provider) {
                        AiProvider.ANTHROPIC -> parseAnthropicModel(item)
                        AiProvider.OPENAI, AiProvider.GROK -> parseOpenAiStyleModel(provider, item)
                    },
                )
            }
        }.sortedBy { it.displayName.lowercase() }
    }

    private fun parseAnthropicModel(item: JSONObject): RemoteModel {
        val capabilities = item.optJSONObject("capabilities")
        val supportsVision = capabilities?.optJSONObject("image_input")
            ?.optBoolean("supported", false) == true
        val supportsStructuredOutput = capabilities?.optJSONObject("structured_outputs")
            ?.optBoolean("supported", false) == true
        val supportsEffort = capabilities?.optJSONObject("effort")
            ?.optBoolean("supported", false) == true
        return RemoteModel(
            provider = AiProvider.ANTHROPIC,
            id = item.getString("id"),
            displayName = item.optString("display_name", item.getString("id")),
            supportsVision = supportsVision,
            supportsStructuredOutput = supportsStructuredOutput,
            supportsEffort = supportsEffort,
        )
    }

    private fun parseOpenAiStyleModel(provider: AiProvider, item: JSONObject): RemoteModel =
        RemoteModel(
            provider = provider,
            id = item.getString("id"),
            displayName = item.optString("id"),
            supportsVision = true,
            supportsStructuredOutput = true,
            supportsEffort = false,
        )

    private fun parseErrorMessage(body: String): String? = runCatching {
        val json = JSONObject(body)
        json.optJSONObject("error")?.optString("message")
            ?: json.optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun AiProvider.modelsEndpoint(): String = when (this) {
        AiProvider.ANTHROPIC -> "https://api.anthropic.com/v1/models?limit=1000"
        AiProvider.OPENAI -> "https://api.openai.com/v1/models"
        AiProvider.GROK -> "https://api.x.ai/v1/models"
    }
}

class ModelFetchException(
    val provider: AiProvider,
    override val message: String,
) : Exception(message)