package com.javis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private var onSpeechResult: ((String) -> Unit)? = null
    private var onSpeechEnd: (() -> Unit)? = null

    private var currentPitch = JAVIS_PITCH
    private var currentRate = JAVIS_RATE

    companion object {
        // Deep, authoritative Jarvis voice
        const val JAVIS_PITCH = 0.75f   // Noticeably lower than default 1.0
        const val JAVIS_RATE = 0.93f    // Slightly slower — deliberate and measured

        // Google TTS engine package — better voices than Pico TTS
        const val GOOGLE_TTS_ENGINE = "com.google.android.tts"

        val PREFERRED_VOICE_FRAGMENTS = listOf(
            "en-gb-x-gbb",   // Google UK English Male
            "en-gb-x-gbg",
            "en-gb",
            "en_gb",
            "en-us-x-sfg",   // Google US English Male
            "en-us-x-iom",
            "en-us-x-iob",
            "en-us-x-d0m",
        )
    }

    init {
        initTts()
    }

    private fun initTts() {
        // Try Google TTS first (better voice quality), fall back to default
        val onInit = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                applyJavisVoice()
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _voiceState.value = VoiceState.Speaking
                    }
                    override fun onDone(utteranceId: String?) {
                        _voiceState.value = VoiceState.Idle
                        onSpeechEnd?.invoke()
                        onSpeechEnd = null
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _voiceState.value = VoiceState.Idle
                        onSpeechEnd?.invoke()
                        onSpeechEnd = null
                    }
                })
            }
        }

        // Try Google TTS engine first
        textToSpeech = TextToSpeech(context, onInit, GOOGLE_TTS_ENGINE)
    }

    private fun applyJavisVoice() {
        val tts = textToSpeech ?: return
        tts.setPitch(currentPitch)
        tts.setSpeechRate(currentRate)

        val voices = tts.voices
        if (!voices.isNullOrEmpty()) {
            val best = findBestJavisVoice(voices)
            if (best != null) {
                tts.voice = best
                return
            }
        }

        // Fallback: set UK locale for British accent
        val ukResult = tts.setLanguage(Locale.UK)
        if (ukResult == TextToSpeech.LANG_NOT_SUPPORTED || ukResult == TextToSpeech.LANG_MISSING_DATA) {
            tts.setLanguage(Locale.US)
        }
    }

    private fun findBestJavisVoice(voices: Set<Voice>): Voice? {
        val available = voices.filter { v ->
            v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }

        // Priority 1: UK English male voice, not requiring network
        PREFERRED_VOICE_FRAGMENTS.forEach { fragment ->
            val match = available.firstOrNull { v ->
                v.name.lowercase().contains(fragment.lowercase()) &&
                !v.isNetworkConnectionRequired
            }
            if (match != null) return match
        }

        // Priority 2: Any UK English voice
        available.firstOrNull { v ->
            v.locale.language == "en" && v.locale.country == "GB"
        }?.let { return it }

        // Priority 3: Any English male voice
        available.firstOrNull { v ->
            v.locale.language == "en" &&
            v.name.lowercase().let { n -> !n.contains("female") && !n.contains("f-") }
        }?.let { return it }

        // Priority 4: Any available English voice
        return available.firstOrNull { it.locale.language == "en" }
    }

    fun startListening(onResult: (String) -> Unit, onEnd: (() -> Unit)? = null) {
        onSpeechResult = onResult
        onSpeechEnd = onEnd

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = VoiceState.Error("Speech recognition unavailable")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _voiceState.value = VoiceState.Listening
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _voiceState.value = VoiceState.Processing
            }
            override fun onError(error: Int) {
                val msg = getSpeechError(error)
                _voiceState.value = VoiceState.Error(msg)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                if (text.isNotBlank()) onSpeechResult?.invoke(text)
                _voiceState.value = VoiceState.Idle
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _voiceState.value = VoiceState.Idle
    }

    /**
     * Speak text aloud with JAVIS voice.
     * Strips JSON action blocks before speaking.
     * Calls onDone when finished.
     */
    fun speak(text: String, speechRate: Float = currentRate, onDone: (() -> Unit)? = null) {
        if (!isTtsReady) {
            onDone?.invoke()
            return
        }

        // Strip JSON action blocks
        val clean = text
            .replace(Regex("""\{[^{}]*"action"\s*:\s*"[^"]*"[^{}]*\}"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        if (clean.isBlank()) {
            onDone?.invoke()
            return
        }

        onSpeechEnd = onDone
        textToSpeech?.setSpeechRate(speechRate)
        textToSpeech?.speak(
            clean,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "JAVIS_${System.currentTimeMillis()}"
        )
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _voiceState.value = VoiceState.Idle
        onSpeechEnd = null
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch
        textToSpeech?.setPitch(pitch)
    }

    fun setSpeechRate(rate: Float) {
        currentRate = rate
        textToSpeech?.setSpeechRate(rate)
    }

    fun setVoice(voiceName: String) {
        if (voiceName.isBlank()) {
            applyJavisVoice()
            return
        }
        textToSpeech?.voices?.find { it.name == voiceName }?.let {
            textToSpeech?.voice = it
        }
    }

    fun getAvailableVoices(): List<String> {
        return textToSpeech?.voices
            ?.filter { v ->
                v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true &&
                v.locale.language == "en"
            }
            ?.sortedWith(compareByDescending { v ->
                when {
                    v.name.lowercase().contains("en-gb") -> 4
                    v.name.lowercase().contains("google") -> 3
                    v.name.lowercase().contains("male") -> 2
                    else -> 1
                }
            })
            ?.map { it.name }
            ?: emptyList()
    }

    fun release() {
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }

    private fun getSpeechError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission needed"
        SpeechRecognizer.ERROR_NETWORK -> "No internet"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "Couldn't hear you"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Unknown error"
    }
}

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Processing : VoiceState()
    object Speaking : VoiceState()
    data class Error(val message: String) : VoiceState()
}
