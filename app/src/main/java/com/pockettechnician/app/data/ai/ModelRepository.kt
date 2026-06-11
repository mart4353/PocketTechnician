package com.pockettechnician.app.data.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ModelRepository(
    private val apiKeyStore: ApiKeyStore,
    private val preferencesStore: AiPreferencesStore,
    private val modelFetcher: ModelFetcher = ModelFetcher(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val cacheState = MutableStateFlow<Map<AiProvider, ProviderModelCache>>(emptyMap())
    private val refreshState = MutableStateFlow(false)
    private val refreshMutex = Mutex()

    val isRefreshing: Flow<Boolean> = refreshState

    val availableModels: Flow<List<RemoteModel>> = combine(
        cacheState,
        apiKeyStore.configuredProviders,
    ) { caches, configured ->
        configured.flatMap { provider ->
            caches[provider]?.models.orEmpty()
        }.filter { it.supportsVision && it.supportsStructuredOutput }
            .sortedWith(compareBy({ it.provider.ordinal }, { it.displayName.lowercase() }))
    }

    val providerErrors: Flow<Map<AiProvider, String>> = preferencesStore.providerErrors()

    suspend fun loadCachedModels() {
        val loaded = AiProvider.entries.mapNotNull { provider ->
            preferencesStore.readModelCache(provider)?.let { provider to it }
        }.toMap()
        cacheState.value = loaded
    }

    suspend fun refreshAllConfigured() {
        apiKeyStore.configuredProviders.value.forEach { provider ->
            refresh(provider)
        }
    }

    suspend fun refresh(provider: AiProvider): Result<List<RemoteModel>> {
        val apiKey = apiKeyStore.get(provider)
            ?: return Result.failure(IllegalStateException("No API key for ${provider.displayName}"))

        return refreshMutex.withLock {
            refreshState.value = true
            try {
                withContext(ioDispatcher) {
                    runCatching {
                        modelFetcher.fetch(provider, apiKey)
                    }
                }.fold(
                    onSuccess = { models ->
                        val usable = models.filter { it.supportsVision && it.supportsStructuredOutput }
                        val cache = ProviderModelCache(
                            provider = provider,
                            models = usable,
                            fetchedAtEpochMillis = System.currentTimeMillis(),
                        )
                        preferencesStore.writeModelCache(cache)
                        cacheState.update { it + (provider to cache) }
                        Result.success(usable)
                    },
                    onFailure = { error ->
                        val message = (error as? ModelFetchException)?.message
                            ?: error.message
                            ?: "Failed to fetch models"
                        val previous = cacheState.value[provider]
                            ?: preferencesStore.readModelCache(provider)
                        val cache = ProviderModelCache(
                            provider = provider,
                            models = previous?.models.orEmpty(),
                            fetchedAtEpochMillis = previous?.fetchedAtEpochMillis ?: 0L,
                            errorMessage = message,
                        )
                        preferencesStore.writeModelCache(cache)
                        cacheState.update { it + (provider to cache) }
                        Result.failure(error)
                    },
                )
            } finally {
                refreshState.value = false
            }
        }
    }

    suspend fun reconcileSelectedModel(
        current: AiModelSelection?,
        available: List<RemoteModel>,
    ): AiModelSelection? {
        val resolved = when {
            current != null && available.any { it.provider == current.provider && it.id == current.modelId } -> current
            else -> available.firstOrNull()?.let { AiModelSelection(it.provider, it.id) }
        }
        if (resolved != current) {
            preferencesStore.setSelectedModel(resolved)
        }
        return resolved
    }
}