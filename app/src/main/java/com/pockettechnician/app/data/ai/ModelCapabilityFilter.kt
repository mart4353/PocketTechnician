package com.pockettechnician.app.data.ai

object ModelCapabilityFilter {
    private val excludedFragments = listOf(
        "embed",
        "tts",
        "whisper",
        "dall-e",
        "moderation",
        "realtime",
        "transcribe",
        "audio-preview",
        "sora",
    )

    fun isUsable(provider: AiProvider, modelId: String): Boolean {
        val normalized = modelId.lowercase()
        if (excludedFragments.any { normalized.contains(it) }) return false
        return when (provider) {
            AiProvider.ANTHROPIC -> normalized.startsWith("claude")
            AiProvider.OPENAI -> {
                normalized.startsWith("gpt-") ||
                    normalized.startsWith("o1") ||
                    normalized.startsWith("o3") ||
                    normalized.startsWith("o4") ||
                    normalized.startsWith("chatgpt")
            }
            AiProvider.GROK -> normalized.startsWith("grok")
        }
    }
}