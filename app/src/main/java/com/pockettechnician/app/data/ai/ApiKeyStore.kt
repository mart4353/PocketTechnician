package com.pockettechnician.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ApiKeyStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)
    private val _configuredProviders = MutableStateFlow(readConfiguredProviders())
    val configuredProviders: StateFlow<Set<AiProvider>> = _configuredProviders.asStateFlow()

    fun isConfigured(provider: AiProvider): Boolean =
        !get(provider).isNullOrBlank()

    fun get(provider: AiProvider): String? =
        prefs.getString(provider.preferenceKey, null)?.takeIf { it.isNotBlank() }

    fun set(provider: AiProvider, apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "API key cannot be empty" }
        prefs.edit().putString(provider.preferenceKey, trimmed).apply()
        refreshConfiguredProviders()
    }

    fun clear(provider: AiProvider) {
        prefs.edit().remove(provider.preferenceKey).apply()
        refreshConfiguredProviders()
    }

    fun mask(provider: AiProvider): String? {
        val key = get(provider) ?: return null
        return if (key.length <= 4) "••••" else "••••${key.takeLast(4)}"
    }

    private fun refreshConfiguredProviders() {
        _configuredProviders.value = readConfiguredProviders()
    }

    private fun readConfiguredProviders(): Set<AiProvider> =
        AiProvider.entries.filterTo(mutableSetOf()) { isConfigured(it) }

    companion object {
        private const val PREFS_NAME = "api_keys"

        private fun createPrefs(context: Context): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}