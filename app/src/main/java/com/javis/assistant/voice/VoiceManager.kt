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

    // Default JAVIS voice profile — deep, authoritative, British-ish
    private var currentPitch = JAVIS_PITCH
    private var currentRate = JAVIS_RATE

    companion object {
        const val JAVIS_PITCH = 0.82f    // Slightly lower than default — commanding
        const val JAVIS_RATE = 0.96f     // Slightly slower — deliberate
        val PREFERRED_LOCALES = listOf(
            Locale.UK,          // British English — closest to Jarvis
            Locale.US,
            Locale.ENGLISH
        )
        // Voice name fragments to prefer (Google TTS UK voices)
        val PREFERRED_VOICE_FRAGMENTS = listOf(
            "en-gb", "en_gb", "en-GB", "en_GB",
            "en-us-x-sfg",  // Google male voices
            "en-us-x-iom",
            "en-us-x-iob",
            "male"
        )
    }

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
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
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _voiceState.value = VoiceState.Idle
                    }
                })
            }
        }
    }

    private fun applyJavisVoice() {
        val tts = textToSpeech ?: return
        tts.setPitch(currentPitch)
        tts.setSpeechRate(currentRate)

        // Attempt to find the best Jarvis-like voice
        val voices = tts.voices ?: return
        val best = findBestJavisVoice(voices)
        if (best != null) {
            tts.voice = best
        } else {
            // Fallback: use UK locale
            val ukResult = tts.setLanguage(Locale.UK)
            if (ukResult == TextToSpeech.LANG_NOT_SUPPORTED || ukResult == TextToSpeech.LANG_MISSING_DATA) {
                tts.setLanguage(Locale.US)
            }
        }
    }

    private fun findBestJavisVoice(voices: Set<Voice>): Voice? {
        // Priority 1: UK English male, not requiring network
        val ukOfflineMale = voices.filter { v ->
            !v.isNetworkConnectionRequired &&
            v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true &&
            (v.name.lowercase().contains("en-gb") || v.name.lowercase().contains("en_gb")) &&
            v.name.lowercase().let { n -> n.contains("male") || !n.contains("female") }
        }.minByOrNull { it.latency }

        if (ukOfflineMale != null) return ukOfflineMale

        // Priority 2: Any UK English voice
        val ukAny = voices.filter { v ->
            v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true &&
            (v.name.lowercase().contains("en-gb") || v.name.lowercase().contains("en_gb") ||
             v.locale == Locale.UK)
        }.minByOrNull { it.latency }

        if (ukAny != null) return ukAny

        // Priority 3: Any preferred fragment match
        PREFERRED_VOICE_FRAGMENTS.forEach { fragment ->
            val match = voices.firstOrNull { v ->
                v.name.lowercase().contains(fragment.lowercase()) &&
                v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            if (match != null) return match
        }

        // Priority 4: Any US English male voice
        return voices.filter { v ->
            v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true &&
            (v.locale == Locale.US || v.name.lowercase().contains("en-us")) &&
            v.name.lowercase().let { n -> !n.contains("female") && !n.contains("f-") }
        }.minByOrNull { it.latency }
    }

    fun startListening(onResult: (String) -> Unit, onEnd: (() -> Unit)? = null) {
        onSpeechResult = onResult
        onSpeechEnd = onEnd
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = VoiceState.Error("Speech recognition not available")
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
                _voiceState.value = VoiceState.Error(getSpeechErrorMessage(error))
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    onSpeechResult?.invoke(text)
                }
                _voiceState.value = VoiceState.Idle
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _voiceState.value = VoiceState.Idle
    }

    fun speak(text: String, speechRate: Float = currentRate, onDone: (() -> Unit)? = null) {
        if (!isTtsReady) return
        onSpeechEnd = onDone
        // Strip JSON action blocks before speaking
        val cleanText = text.replace(Regex("""\{[^{}]*"action"\s*:\s*"LAUNCH_APP"[^{}]*\}"""), "").trim()
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }
        textToSpeech?.setSpeechRate(speechRate)
        textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "JAVIS_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _voiceState.value = VoiceState.Idle
    }

    fun setVoice(voiceName: String) {
        if (voiceName.isBlank()) {
            applyJavisVoice() // re-apply Javis defaults
            return
        }
        textToSpeech?.voices?.find { it.name == voiceName }?.let {
            textToSpeech?.voice = it
        }
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch
        textToSpeech?.setPitch(pitch)
    }

    fun setSpeechRate(rate: Float) {
        currentRate = rate
        textToSpeech?.setSpeechRate(rate)
    }

    fun getAvailableVoices(): List<String> {
        return textToSpeech?.voices
            ?.filter { v ->
                v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true &&
                (v.locale.language == "en")
            }
            ?.sortedWith(compareByDescending { v ->
                when {
                    v.name.lowercase().contains("en-gb") -> 3
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

    private fun getSpeechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK -> "Network unavailable"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
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
