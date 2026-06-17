package com.javis.assistant.ai

import com.google.gson.annotations.SerializedName
import com.javis.assistant.data.model.AiMessage
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class DsRequest(
    val model: String,
    val messages: List<DsMessage>,
    val temperature: Float,
    @SerializedName("max_tokens") val maxTokens: Int
)

data class DsMessage(val role: String, val content: String)
data class DsResponse(val choices: List<DsChoice>)
data class DsChoice(val message: DsMessage)

interface DeepSeekApi {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: DsRequest
    ): DsResponse
}

class DeepSeekProvider @Inject constructor() : AiProvider {

    override val name = "DeepSeek"

    private val api: DeepSeekApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeepSeekApi::class.java)
    }

    override suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        return try {
            val allMessages = mutableListOf(DsMessage("system", systemPrompt)) +
                    messages.map { DsMessage(it.role, it.content) }

            val response = api.chat(
                auth = "Bearer $apiKey",
                request = DsRequest(
                    model = "deepseek-chat",
                    messages = allMessages,
                    temperature = temperature,
                    maxTokens = maxTokens
                )
            )
            val content = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("Empty response"))
            Result.success(content.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        var apiKey: String = ""
    }
}
