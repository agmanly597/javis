package com.javis.assistant.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton event bus used to signal JAVIS activation from outside the app
 * (e.g. Quick Settings Tile, persistent notification button, or assistant intent).
 * ChatViewModel subscribes to this and starts voice listening when triggered.
 */
object JavisActivationBus {
    private val _activations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val activations: SharedFlow<Unit> = _activations.asSharedFlow()

    /** Fire an activation event (call from main thread safe) */
    fun emitActivation() {
        _activations.tryEmit(Unit)
    }
}
