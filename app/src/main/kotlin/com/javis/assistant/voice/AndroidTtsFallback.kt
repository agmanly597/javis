package com.javis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidTtsFallback @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    var speechRate: Float = 1.0f
    var pitch: Float = 0.9f

    init { initTts() }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(pitch)
                isReady = true
            }
        }
    }

    suspend fun speak(text: String): Unit = suspendCancellableCoroutine { cont ->
        if (!isReady || tts == null) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)

        val utteranceId = "javis_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onDone(u: String?) { if (u == utteranceId) cont.resume(Unit) }
            override fun onError(u: String?) { if (u == utteranceId) cont.resume(Unit) }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        cont.invokeOnCancellation { tts?.stop() }
    }

    fun stop() { tts?.stop() }
    fun isSpeaking() = tts?.isSpeaking == true

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
