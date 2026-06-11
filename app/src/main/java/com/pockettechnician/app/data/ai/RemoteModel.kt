package com.pockettechnician.app.data.ai

data class RemoteModel(
    val provider: AiProvider,
    val id: String,
    val displayName: String,
    val supportsVision: Boolean = true,
    val supportsStructuredOutput: Boolean = true,
    val supportsEffort: Boolean = false,
)

data class ProviderModelCache(
    val provider: AiProvider,
    val models: List<RemoteModel>,
    val fetchedAtEpochMillis: Long,
    val errorMessage: String? = null,
)