package com.pockettechnician.app.data.ai

enum class AiProvider(val displayName: String, val preferenceKey: String) {
    ANTHROPIC("Claude", "key_anthropic"),
    OPENAI("OpenAI", "key_openai"),
    GROK("Grok", "key_grok"),
    ;

    companion object {
        fun fromPreferenceKey(key: String): AiProvider? =
            entries.firstOrNull { it.preferenceKey == key }
    }
}

data class AiModelSelection(
    val provider: AiProvider,
    val modelId: String,
)