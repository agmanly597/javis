package com.javis.assistant.ai

import com.javis.assistant.data.remote.api.DeepSeekApiService
import com.javis.assistant.data.remote.api.GroqApiService
import com.javis.assistant.domain.model.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderFactory @Inject constructor(
    private val groqApiService: GroqApiService,
    private val deepSeekApiService: DeepSeekApiService
) {
    fun create(provider: AiProvider, apiKey: String): com.javis.assistant.ai.AiProvider {
        return when (provider) {
            AiProvider.GROQ -> GroqAiProvider(groqApiService, apiKey)
            AiProvider.DEEPSEEK -> DeepSeekAiProvider(deepSeekApiService, apiKey)
        }
    }
}
