package com.javis.assistant.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val elevenLabsTtsService: ElevenLabsTtsService
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    var elevenLabsApiKey: String = ""
    var elevenLabsVoiceId: String = ElevenLabsTtsService.DEFAULT_VOICE
    private var currentSpeechRate = 0.93f
    private var currentPitch = 0.75f

    companion object {
        const val GOOGLE_TTS_ENGINE = "com.google.android.tts"

        private val UK_VOICE_FRAGMENTS = listOf(
            "en-gb-x-gbb", "en-gb-x-gbg", "en-gb-x-gbd",
            "en_gb", "en-gb", "en-GB"
        )
    }

    init {
        initTts()
        routeAudioToSpeaker()
    }

    private fun routeAudioToSpeaker() {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.mode = AudioManager.MODE_NORMAL
            audio.isSpeakerphoneOn = true
        } catch (_: Exception) {}
    }

    private fun initTts() {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                applyJavisVoice()
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { _voiceState.value = VoiceState.Speaking }
                    override fun onDone(id: String?) {
                        _voiceState.value = VoiceState.Idle
                        pendingOnDone?.invoke()
                        pendingOnDone = null
                    }
                    @Deprecated("Deprecated")
                    override fun onError(id: String?) {
                        _voiceState.value = VoiceState.Idle
                        pendingOnDone?.invoke()
                        pendingOnDone = null
                    }
                })
            }
        }
        textToSpeech = TextToSpeech(context, listener, GOOGLE_TTS_ENGINE)
    }

    private fun applyJavisVoice() {
        val tts = textToSpeech ?: return
        tts.setPitch(currentPitch)
        tts.setSpeechRate(currentSpeechRate)

        val voices = tts.voices
        if (!voices.isNullOrEmpty()) {
            val best = findBestVoice(voices)
            if (best != null) { tts.voice = best; return }
        }
        val ukResult = tts.setLanguage(Locale.UK)
        if (ukResult < TextToSpeech.LANG_AVAILABLE) tts.setLanguage(Locale.US)
    }

    private fun findBestVoice(voices: Set<Voice>): Voice? {
        val installed = voices.filter { v ->
            v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        // UK English male first
        UK_VOICE_FRAGMENTS.forEach { frag ->
            installed.firstOrNull { v ->
                v.name.lowercase().contains(frag.lowercase()) && !v.isNetworkConnectionRequired
            }?.let { return it }
        }
        installed.firstOrNull { v -> v.locale.language == "en" && v.locale.country == "GB" }?.let { return it }
        installed.firstOrNull { v -> v.locale.language == "en" }?.let { return it }
        return null
    }

    private var pendingOnDone: (() -> Unit)? = null

    fun startListening(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = VoiceState.Error("Speech recognition unavailable")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { _voiceState.value = VoiceState.Listening }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { _voiceState.value = VoiceState.Processing }
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No internet"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission needed"
                    else -> "Speech error"
                }
                _voiceState.value = VoiceState.Error(msg)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                _voiceState.value = VoiceState.Idle
                if (text.isNotBlank()) onResult(text)
            }
            override fun onPartialResults(partial: Bundle?) {}
            override fun onEvent(type: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _voiceState.value = VoiceState.Idle
    }

    /**
     * Speak text — tries ElevenLabs AI voice first, falls back to Android TTS.
     * Routes audio to speaker so JAVIS is always heard loudly.
     */
    fun speak(text: String, speechRate: Float = currentSpeechRate, onDone: (() -> Unit)? = null) {
        val clean = cleanForSpeech(text)
        if (clean.isBlank()) { onDone?.invoke(); return }

        pendingOnDone = onDone
        _voiceState.value = VoiceState.Speaking
        routeAudioToSpeaker()

        if (elevenLabsApiKey.isNotBlank()) {
            scope.launch {
                val success = elevenLabsTtsService.speakWithElevenLabs(
                    clean, elevenLabsApiKey, elevenLabsVoiceId
                ) {
                    _voiceState.value = VoiceState.Idle
                    pendingOnDone?.invoke()
                    pendingOnDone = null
                }
                if (!success) {
                    // Fallback to Android TTS
                    speakWithAndroidTts(clean, speechRate)
                }
            }
        } else {
            speakWithAndroidTts(clean, speechRate)
        }
    }

    private fun speakWithAndroidTts(text: String, rate: Float) {
        if (!isTtsReady) { _voiceState.value = VoiceState.Idle; pendingOnDone?.invoke(); pendingOnDone = null; return }
        textToSpeech?.setSpeechRate(rate)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "J_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        elevenLabsTtsService.stopCurrent()
        textToSpeech?.stop()
        _voiceState.value = VoiceState.Idle
        pendingOnDone = null
    }

    fun setPitch(pitch: Float) { currentPitch = pitch; textToSpeech?.setPitch(pitch) }
    fun setSpeechRate(rate: Float) { currentSpeechRate = rate; textToSpeech?.setSpeechRate(rate) }
    fun setVoice(voiceName: String) {
        if (voiceName.isBlank()) { applyJavisVoice(); return }
        textToSpeech?.voices?.find { it.name == voiceName }?.let { textToSpeech?.voice = it }
    }

    private fun cleanForSpeech(text: String): String {
        return text
            .replace(Regex("""\{[^{}]*"action"\s*:[^{}]*\}"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    fun release() {
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        elevenLabsTtsService.stopCurrent()
    }
}

sealed class VoiceState {
    object Idle : VoiceState()
    object Listening : VoiceState()
    object Processing : VoiceState()
    object Speaking : VoiceState()
    data class Error(val message: String) : VoiceState()
}
