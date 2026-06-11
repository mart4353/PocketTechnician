package com.pockettechnician.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pockettechnician.app.PocketTechnicianApplication
import com.pockettechnician.app.data.ai.AiModelSelection
import com.pockettechnician.app.data.ai.AiPreferencesStore
import com.pockettechnician.app.data.ai.AiProvider
import com.pockettechnician.app.data.ai.ApiKeyStore
import com.pockettechnician.app.data.ai.ModelRepository
import com.pockettechnician.app.data.ai.RemoteModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val configuredProviders: Set<AiProvider> = emptySet(),
    val providerKeyMasks: Map<AiProvider, String> = emptyMap(),
    val providerErrors: Map<AiProvider, String> = emptyMap(),
    val availableModels: List<RemoteModel> = emptyList(),
    val selectedModel: AiModelSelection? = null,
    val isRefreshingModels: Boolean = false,
    val showApiKeySheet: Boolean = false,
    val sheetProvider: AiProvider? = null,
    val statusMessage: String? = null,
)

class DashboardViewModel(
    private val apiKeyStore: ApiKeyStore,
    private val aiPreferencesStore: AiPreferencesStore,
    private val modelRepository: ModelRepository,
) : ViewModel() {
    private val showApiKeySheet = MutableStateFlow(false)
    private val sheetProvider = MutableStateFlow<AiProvider?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            apiKeyStore.configuredProviders,
            modelRepository.availableModels,
            modelRepository.isRefreshing,
            modelRepository.providerErrors,
            aiPreferencesStore.selectedModel,
        ) { configured, models, refreshing, errors, selected ->
            CoreState(configured, models, refreshing, errors, selected)
        },
        combine(showApiKeySheet, sheetProvider, statusMessage) { sheetVisible, focusedProvider, message ->
            SheetState(sheetVisible, focusedProvider, message)
        },
    ) { core, sheet ->
        DashboardUiState(
            configuredProviders = core.configured,
            providerKeyMasks = core.configured.associateWith { apiKeyStore.mask(it) ?: "Set" },
            providerErrors = core.errors,
            availableModels = core.models,
            selectedModel = core.selected,
            isRefreshingModels = core.refreshing,
            showApiKeySheet = sheet.sheetVisible,
            sheetProvider = sheet.focusedProvider,
            statusMessage = sheet.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    init {
        viewModelScope.launch {
            modelRepository.loadCachedModels()
            reconcileSelection()
        }
        viewModelScope.launch {
            modelRepository.availableModels.collect {
                reconcileSelection(it)
            }
        }
    }

    fun openApiKeySheet(provider: AiProvider? = null) {
        sheetProvider.value = provider
        showApiKeySheet.value = true
        statusMessage.value = null
    }

    fun dismissApiKeySheet() {
        showApiKeySheet.value = false
        sheetProvider.value = null
    }

    fun saveApiKey(provider: AiProvider, apiKey: String) {
        viewModelScope.launch {
            runCatching {
                apiKeyStore.set(provider, apiKey)
            }.onSuccess {
                statusMessage.value = "${provider.displayName} API key saved"
                modelRepository.refresh(provider)
                dismissApiKeySheet()
            }.onFailure { error ->
                statusMessage.value = error.message ?: "Failed to save API key"
            }
        }
    }

    fun clearApiKey(provider: AiProvider) {
        viewModelScope.launch {
            apiKeyStore.clear(provider)
            statusMessage.value = "${provider.displayName} API key removed"
            modelRepository.refreshAllConfigured()
            reconcileSelection()
        }
    }

    fun selectModel(model: RemoteModel) {
        viewModelScope.launch {
            aiPreferencesStore.setSelectedModel(AiModelSelection(model.provider, model.id))
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            modelRepository.refreshAllConfigured()
        }
    }

    private suspend fun reconcileSelection(available: List<RemoteModel>? = null) {
        val models = available ?: modelRepository.availableModels.first()
        val current = aiPreferencesStore.selectedModel.first()
        modelRepository.reconcileSelectedModel(current, models)
    }

    private data class CoreState(
        val configured: Set<AiProvider>,
        val models: List<RemoteModel>,
        val refreshing: Boolean,
        val errors: Map<AiProvider, String>,
        val selected: AiModelSelection?,
    )

    private data class SheetState(
        val sheetVisible: Boolean,
        val focusedProvider: AiProvider?,
        val message: String?,
    )

    class Factory(
        private val application: PocketTechnicianApplication,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(
                    application.apiKeyStore,
                    application.aiPreferencesStore,
                    application.modelRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}