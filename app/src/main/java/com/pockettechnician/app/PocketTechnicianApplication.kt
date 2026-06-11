package com.pockettechnician.app

import android.app.Application
import com.pockettechnician.app.data.ai.AiPreferencesStore
import com.pockettechnician.app.data.ai.ApiKeyStore
import com.pockettechnician.app.data.ai.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PocketTechnicianApplication : Application() {
    lateinit var apiKeyStore: ApiKeyStore
        private set
    lateinit var aiPreferencesStore: AiPreferencesStore
        private set
    lateinit var modelRepository: ModelRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        apiKeyStore = ApiKeyStore(this)
        aiPreferencesStore = AiPreferencesStore(this)
        modelRepository = ModelRepository(apiKeyStore, aiPreferencesStore)

        applicationScope.launch {
            modelRepository.loadCachedModels()
            modelRepository.refreshAllConfigured()
        }
    }
}