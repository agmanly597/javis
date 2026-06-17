package com.javis.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javis.assistant.data.model.AiProvider
import com.javis.assistant.domain.JavisRepository
import com.javis.assistant.memory.MemoryManager
import com.javis.assistant.storage.JavisPreferences
import com.javis.assistant.voice.ElevenLabsTts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val groqApiKey: String = "",
    val elevenLabsApiKey: String = "",
    val deepSeekApiKey: String = "",
    val elevenLabsVoiceId: String = "",
    val aiProvider: AiProvider = AiProvider.GROQ,
    val speechRate: Float = 1.0f,
    val ttsPitch: Float = 0.9f,
    val useElevenLabs: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val continuousMode: Boolean = false,
    val savedMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: JavisPreferences,
    private val memoryManager: MemoryManager,
    private val repository: JavisRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.groqApiKey,
        prefs.elevenLabsApiKey,
        prefs.deepSeekApiKey,
        prefs.elevenLabsVoiceId,
        prefs.aiProvider,
        prefs.speechRate,
        prefs.ttsPitch,
        prefs.useElevenLabs,
        prefs.notificationsEnabled,
        prefs.continuousMode
    ) { vals ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            groqApiKey = vals[0] as String,
            elevenLabsApiKey = vals[1] as String,
            deepSeekApiKey = vals[2] as String,
            elevenLabsVoiceId = vals[3] as String,
            aiProvider = vals[4] as AiProvider,
            speechRate = vals[5] as Float,
            ttsPitch = vals[6] as Float,
            useElevenLabs = vals[7] as Boolean,
            notificationsEnabled = vals[8] as Boolean,
            continuousMode = vals[9] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private val _savedMessage = MutableStateFlow<String?>(null)
    val savedMessage: StateFlow<String?> = _savedMessage

    fun save(state: SettingsUiState) {
        viewModelScope.launch {
            prefs.setGroqApiKey(state.groqApiKey)
            prefs.setElevenLabsApiKey(state.elevenLabsApiKey)
            prefs.setDeepSeekApiKey(state.deepSeekApiKey)
            prefs.setElevenLabsVoiceId(state.elevenLabsVoiceId.ifBlank { ElevenLabsTts.DEFAULT_VOICE_ID })
            prefs.setAiProvider(state.aiProvider)
            prefs.setSpeechRate(state.speechRate)
            prefs.setTtsPitch(state.ttsPitch)
            prefs.setUseElevenLabs(state.useElevenLabs)
            prefs.setNotificationsEnabled(state.notificationsEnabled)
            prefs.setContinuousMode(state.continuousMode)
            _savedMessage.value = "Settings saved"
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            memoryManager.clearAll()
            _savedMessage.value = "Memory cleared"
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _savedMessage.value = "Conversation history cleared"
        }
    }

    fun dismissSaved() { _savedMessage.value = null }
}

// Workaround: combine supports up to 5 parameters natively;
// we use the vararg version that returns Array<Any>
@Suppress("UNCHECKED_CAST")
private fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> combine(
    f1: Flow<T1>, f2: Flow<T2>, f3: Flow<T3>, f4: Flow<T4>, f5: Flow<T5>,
    f6: Flow<T6>, f7: Flow<T7>, f8: Flow<T8>, f9: Flow<T9>, f10: Flow<T10>,
    transform: suspend (Array<Any?>) -> R
): Flow<R> = combine(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10) { v1, v2, v3, v4, v5, v6, v7, v8, v9, v10 ->
    transform(arrayOf(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10))
}

private fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> combine(
    f1: Flow<T1>, f2: Flow<T2>, f3: Flow<T3>, f4: Flow<T4>, f5: Flow<T5>,
    f6: Flow<T6>, f7: Flow<T7>, f8: Flow<T8>, f9: Flow<T9>, f10: Flow<T10>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    kotlinx.coroutines.flow.combine(f1, f2, f3, f4, f5) { a, b, c, d, e -> arrayOf(a, b, c, d, e) },
    kotlinx.coroutines.flow.combine(f6, f7, f8, f9, f10) { a, b, c, d, e -> arrayOf(a, b, c, d, e) }
) { arr1, arr2 ->
    transform(
        arr1[0] as T1, arr1[1] as T2, arr1[2] as T3, arr1[3] as T4, arr1[4] as T5,
        arr2[0] as T6, arr2[1] as T7, arr2[2] as T8, arr2[3] as T9, arr2[4] as T10
    )
}
