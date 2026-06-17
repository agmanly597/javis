package com.javis.assistant.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ElevenLabsTts @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var onDoneCallback: (() -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    var apiKey: String = ""
    var voiceId: String = DEFAULT_VOICE_ID

    suspend fun speak(text: String, onDone: (() -> Unit)? = null) = withContext(Dispatchers.IO) {
        onDoneCallback = onDone
        stopSpeaking()

        if (apiKey.isBlank()) {
            onDone?.invoke()
            return@withContext
        }

        try {
            val body = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_turbo_v2")
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                    put("style", 0.3)
                    put("use_speaker_boost", true)
                })
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
                .header("xi-api-key", apiKey)
                .header("Accept", "audio/mpeg")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val audioBytes = response.body?.bytes()
                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    val tempFile = File(context.cacheDir, "javis_tts_${System.currentTimeMillis()}.mp3")
                    tempFile.writeBytes(audioBytes)
                    withContext(Dispatchers.Main) {
                        playAudioFile(tempFile, onDone)
                    }
                } else {
                    onDone?.invoke()
                }
            } else {
                Log.e(TAG, "ElevenLabs error: ${response.code}")
                onDone?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs TTS failed: ${e.message}")
            onDone?.invoke()
        }
    }

    private fun playAudioFile(file: File, onDone: (() -> Unit)?) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    file.delete()
                    onDone?.invoke()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    file.delete()
                    onDone?.invoke()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer error: ${e.message}")
            file.delete()
            onDone?.invoke()
        }
    }

    fun stopSpeaking() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}")
        }
    }

    fun isSpeaking(): Boolean = mediaPlayer?.isPlaying == true

    companion object {
        private const val TAG = "ElevenLabsTts"
        // "Adam" voice — clear, confident, slightly British-ish
        const val DEFAULT_VOICE_ID = "pNInz6obpgDQGcFmaJgB"
        // Popular free alternatives:
        // "Antoni"  → ErXwobaYiN019PkySvjV
        // "Josh"    → TxGEqnHWrfWFTfGW9XjX
        // "Arnold"  → VR6AewLTigWG4xSOukaG
        const val VOICE_ADAM = "pNInz6obpgDQGcFmaJgB"
        const val VOICE_JOSH = "TxGEqnHWrfWFTfGW9XjX"
        const val VOICE_ARNOLD = "VR6AewLTigWG4xSOukaG"
    }
}
