package com.javis.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javis.assistant.data.model.Memory
import com.javis.assistant.memory.MemoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryManager: MemoryManager
) : ViewModel() {

    val memories: StateFlow<List<Memory>> = memoryManager.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteMemory(memory: Memory) {
        viewModelScope.launch {
            memoryManager.forget(memory.key)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            memoryManager.clearAll()
        }
    }
}
