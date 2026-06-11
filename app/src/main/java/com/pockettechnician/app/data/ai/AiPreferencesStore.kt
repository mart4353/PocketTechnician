package com.pockettechnician.app.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.aiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_preferences",
)

class AiPreferencesStore(private val context: Context) {
    private val dataStore = context.applicationContext.aiPreferencesDataStore

    val selectedModel: Flow<AiModelSelection?> = dataStore.data.map { prefs ->
        val providerName = prefs[SELECTED_PROVIDER] ?: return@map null
        val modelId = prefs[SELECTED_MODEL_ID] ?: return@map null
        val provider = runCatching { AiProvider.valueOf(providerName) }.getOrNull() ?: return@map null
        AiModelSelection(provider, modelId)
    }

    suspend fun setSelectedModel(selection: AiModelSelection?) {
        dataStore.edit { prefs ->
            if (selection == null) {
                prefs.remove(SELECTED_PROVIDER)
                prefs.remove(SELECTED_MODEL_ID)
            } else {
                prefs[SELECTED_PROVIDER] = selection.provider.name
                prefs[SELECTED_MODEL_ID] = selection.modelId
            }
        }
    }

    suspend fun readModelCache(provider: AiProvider): ProviderModelCache? {
        val prefs = dataStore.data.first()
        val modelsJson = prefs[modelsCacheKey(provider)] ?: return null
        val fetchedAt = prefs[fetchedAtKey(provider)] ?: return null
        return parseModelCache(provider, modelsJson, fetchedAt)
    }

    suspend fun writeModelCache(cache: ProviderModelCache) {
        dataStore.edit { prefs ->
            prefs[modelsCacheKey(cache.provider)] = encodeModels(cache.models)
            prefs[fetchedAtKey(cache.provider)] = cache.fetchedAtEpochMillis
            if (cache.errorMessage == null) {
                prefs.remove(errorKey(cache.provider))
            } else {
                prefs[errorKey(cache.provider)] = cache.errorMessage
            }
        }
    }

    fun providerErrors(): Flow<Map<AiProvider, String>> = dataStore.data.map { prefs ->
        AiProvider.entries.mapNotNull { provider ->
            prefs[errorKey(provider)]?.let { provider to it }
        }.toMap()
    }

    companion object {
        private val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        private val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")

        private fun modelsCacheKey(provider: AiProvider) =
            stringPreferencesKey("models_cache_${provider.name.lowercase()}")

        private fun fetchedAtKey(provider: AiProvider) =
            longPreferencesKey("models_fetched_at_${provider.name.lowercase()}")

        private fun errorKey(provider: AiProvider) =
            stringPreferencesKey("models_error_${provider.name.lowercase()}")

        private fun encodeModels(models: List<RemoteModel>): String {
            val array = JSONArray()
            models.forEach { model ->
                array.put(
                    JSONObject()
                        .put("id", model.id)
                        .put("displayName", model.displayName)
                        .put("supportsVision", model.supportsVision)
                        .put("supportsStructuredOutput", model.supportsStructuredOutput)
                        .put("supportsEffort", model.supportsEffort),
                )
            }
            return array.toString()
        }

        private fun parseModelCache(
            provider: AiProvider,
            modelsJson: String,
            fetchedAt: Long,
        ): ProviderModelCache {
            val models = JSONArray(modelsJson).let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            RemoteModel(
                                provider = provider,
                                id = item.getString("id"),
                                displayName = item.getString("displayName"),
                                supportsVision = item.optBoolean("supportsVision", true),
                                supportsStructuredOutput = item.optBoolean("supportsStructuredOutput", true),
                                supportsEffort = item.optBoolean("supportsEffort", false),
                            ),
                        )
                    }
                }
            }
            return ProviderModelCache(provider, models, fetchedAt)
        }
    }
}