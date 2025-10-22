package com.example.wssdemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val webSocketManager = WebSocketManager()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Запускаем наблюдение ДО подключения
        observeMessages()
        observeConnection()
        observeErrors()

        // Затем подключаемся
        connectToWebSocket()
    }

    private fun connectToWebSocket() {
        _isLoading.value = true
        _errorMessage.value = null
        webSocketManager.connect("wss://websocket-echo.com/")
    }

    private fun observeMessages() {
        viewModelScope.launch {
            webSocketManager.messages.collect { message ->
                _messages.value = _messages.value + message
                _isLoading.value = false
            }
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            webSocketManager.connectionState.collect { connected ->
                _isConnected.value = connected
                _isLoading.value = false
            }
        }
    }

    private fun observeErrors() {
        viewModelScope.launch {
            webSocketManager.messages.collect { message ->
                if (message.text.contains("❌")) {
                    _errorMessage.value = message.text
                }
            }
        }
    }

    fun sendMessage(message: String) {
        if (message.isNotBlank() && _isConnected.value) {
            webSocketManager.sendMessage(message)
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun reconnect() {
        webSocketManager.disconnect()
        _isLoading.value = true
        _errorMessage.value = null
        connectToWebSocket()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        webSocketManager.disconnect()
        super.onCleared()
    }
}