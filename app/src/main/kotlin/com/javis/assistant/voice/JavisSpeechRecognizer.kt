package com.javis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

sealed class SpeechEvent {
    object Started : SpeechEvent()
    data class Partial(val text: String) : SpeechEvent()
    data class Result(val text: String) : SpeechEvent()
    data class Error(val code: Int, val message: String) : SpeechEvent()
    object Ended : SpeechEvent()
}

@Singleton
class JavisSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: SpeechRecognizer? = null

    private val _events = MutableStateFlow<SpeechEvent?>(null)
    val events: StateFlow<SpeechEvent?> = _events

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    fun startListening() {
        if (_isListening.value) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _events.value = SpeechEvent.Error(-1, "Speech recognition not available")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _events.value = SpeechEvent.Started
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                    _events.value = SpeechEvent.Ended
                }
                override fun onError(error: Int) {
                    _isListening.value = false
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No mic permission"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silence timeout"
                        else -> "Unknown error ($error)"
                    }
                    _events.value = SpeechEvent.Error(error, msg)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        _events.value = SpeechEvent.Result(text)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    if (partial.isNotBlank()) _events.value = SpeechEvent.Partial(partial)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Start listening failed: ${e.message}")
            _events.value = SpeechEvent.Error(-1, e.message ?: "Failed to start")
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        _isListening.value = false
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _isListening.value = false
    }

    companion object {
        private const val TAG = "JavisSpeechRecognizer"
    }
}
