package com.javis.assistant.ai

import com.google.gson.annotations.SerializedName
import com.javis.assistant.data.model.AiMessage
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Float,
    @SerializedName("max_tokens") val maxTokens: Int,
    val stream: Boolean = false
)

data class GroqMessage(val role: String, val content: String)

data class GroqResponse(
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqMessage,
    @SerializedName("finish_reason") val finishReason: String?
)

interface GroqApi {
    @POST("openai/v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: GroqRequest
    ): GroqResponse
}

class GroqProvider @Inject constructor() : AiProvider {

    override val name = "Groq"

    private val api: GroqApi by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApi::class.java)
    }

    override suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        return try {
            val allMessages = mutableListOf(
                GroqMessage("system", systemPrompt)
            ) + messages.map { GroqMessage(it.role, it.content) }

            val response = api.chat(
                auth = "Bearer $apiKey",
                request = GroqRequest(
                    model = MODEL,
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
        const val MODEL = "llama-3.3-70b-versatile"
        var apiKey: String = ""
    }
}
