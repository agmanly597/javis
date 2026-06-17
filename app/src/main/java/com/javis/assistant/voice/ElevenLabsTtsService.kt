package com.javis.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ElevenLabs TTS — high quality AI voice that sounds like ChatGPT's voice.
 * Free tier: 10,000 characters/month at elevenlabs.io
 * Sign up free → Profile → API Key → paste into JAVIS Settings
 */
@Singleton
class ElevenLabsTtsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
        // Callum — deep British male voice, very Jarvis-like
        const val VOICE_CALLUM = "N2lVS1w4EtoT3dr4eOWO"
        // Adam — authoritative American male
        const val VOICE_ADAM = "pNInz6obpgDQGcFmaJgB"
        // Antoni — well-rounded male
        const val VOICE_ANTONI = "ErXwobaYiN019PkySvjV"
        // Daniel — deep British
        const val VOICE_DANIEL = "onwK4e9ZLuTAKqWW03F9"

        val DEFAULT_VOICE = VOICE_DANIEL
    }

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Synthesize [text] using ElevenLabs and play it immediately.
     * Returns true if successful, false if API key is missing or call failed.
     */
    suspend fun speakWithElevenLabs(
        text: String,
        apiKey: String,
        voiceId: String = DEFAULT_VOICE,
        onDone: () -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext false

        val clean = text.trim().take(2500) // safety cap per request
        if (clean.isBlank()) { onDone(); return@withContext true }

        return@withContext try {
            val audioBytes = synthesize(clean, apiKey, voiceId)
            if (audioBytes != null) {
                withContext(Dispatchers.Main) {
                    playAudio(audioBytes, onDone)
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun synthesize(text: String, apiKey: String, voiceId: String): ByteArray? {
        val url = URL("$BASE_URL/$voiceId?optimize_streaming_latency=3")
        val body = """{"text":"${text.replace("\"","\\\"").replace("\n"," ")}","model_id":"eleven_multilingual_v2","voice_settings":{"stability":0.45,"similarity_boost":0.80,"style":0.15,"use_speaker_boost":true}}"""

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("xi-api-key", apiKey)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "audio/mpeg")
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        conn.doOutput = true
        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

        return if (conn.responseCode == 200) conn.inputStream.readBytes() else null
    }

    private fun playAudio(audioBytes: ByteArray, onDone: () -> Unit) {
        try {
            stopCurrent()
            val file = File(context.cacheDir, "javis_voice.mp3")
            file.writeBytes(audioBytes)

            val player = MediaPlayer()
            mediaPlayer = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                it.release()
                if (mediaPlayer == it) mediaPlayer = null
                onDone()
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (mediaPlayer == mp) mediaPlayer = null
                onDone()
                true
            }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            onDone()
        }
    }

    fun stopCurrent() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun isPlaying() = mediaPlayer?.isPlaying == true
}
