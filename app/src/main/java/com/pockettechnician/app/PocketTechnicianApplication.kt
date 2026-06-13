package com.pockettechnician.app

import android.app.Application
import com.pockettechnician.app.data.ai.AiPreferencesStore
import com.pockettechnician.app.data.ai.ApiKeyStore
import com.pockettechnician.app.data.ai.ModelRepository
import com.pockettechnician.app.data.chat.ChatClient
import com.pockettechnician.app.data.chat.ConversationRepository
import com.pockettechnician.app.hid.HidManager
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
    lateinit var hidManager: HidManager
        private set
    lateinit var conversationRepository: ConversationRepository
        private set
    val chatClient = ChatClient()

    /** System prompt text, loaded from assets/system_prompt.txt at startup. */
    lateinit var systemPrompt: String
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        systemPrompt = loadSystemPrompt()
        apiKeyStore = ApiKeyStore(this)
        aiPreferencesStore = AiPreferencesStore(this)
        modelRepository = ModelRepository(apiKeyStore, aiPreferencesStore)
        hidManager = HidManager(this)
        conversationRepository = ConversationRepository(this)

        applicationScope.launch {
            modelRepository.loadCachedModels()
            modelRepository.refreshAllConfigured()
        }
        applicationScope.launch {
            conversationRepository.load()
            conversationRepository.resetTestConversations()
        }
    }

    private fun loadSystemPrompt(): String =
        runCatching {
            assets.open(SYSTEM_PROMPT_ASSET).bufferedReader().use { it.readText() }.trim()
        }.getOrDefault(FALLBACK_SYSTEM_PROMPT)

    private companion object {
        const val SYSTEM_PROMPT_ASSET = "system_prompt.md"
        const val FALLBACK_SYSTEM_PROMPT =
            "You are Pocket Technician, a friendly tech-support assistant. " +
                "Help diagnose and fix computer problems step by step."
    }
}