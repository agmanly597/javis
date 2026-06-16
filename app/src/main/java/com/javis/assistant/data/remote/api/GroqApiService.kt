package com.javis.assistant.data.remote.api

import com.javis.assistant.data.remote.models.ChatCompletionRequest
import com.javis.assistant.data.remote.models.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GroqApiService {
    @POST("openai/v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}
