package com.javis.assistant.ai

import com.javis.assistant.data.remote.api.GroqApiService
import com.javis.assistant.data.remote.models.ApiMessage
import com.javis.assistant.data.remote.models.ChatCompletionRequest
import com.javis.assistant.domain.model.ChatMessage
import com.javis.assistant.domain.model.MessageRole

class GroqAiProvider(
    private val apiService: GroqApiService,
    private val apiKey: String
) : AiProvider {

    override val name = "Groq"

    override suspend fun generateResponse(
        messages: List<ChatMessage>,
        systemPrompt: String
    ): Result<String> = runCatching {
        val apiMessages = mutableListOf(
            ApiMessage(role = "system", content = systemPrompt)
        )
        apiMessages.addAll(
            messages.takeLast(20).map { msg ->
                ApiMessage(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    },
                    content = msg.content
                )
            }
        )
        val request = ChatCompletionRequest(
            model = "llama-3.3-70b-versatile",
            messages = apiMessages,
            maxTokens = 512
        )
        val response = apiService.chatCompletion(
            authorization = "Bearer $apiKey",
            request = request
        )
        response.choices.firstOrNull()?.message?.content
            ?: error("Empty response from Groq")
    }
}
