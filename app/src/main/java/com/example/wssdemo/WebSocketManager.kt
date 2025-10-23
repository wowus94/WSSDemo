package com.example.wssdemo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class WebSocketManager {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private val messageQueue = LinkedList<String>()

    private lateinit var appContext: Context
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Используем MutableSharedFlow с replay = 1 для буферизации
    private val _messages = MutableSharedFlow<ChatMessage>(
        replay = 1,
        extraBufferCapacity = 50
    )
    val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableSharedFlow<Boolean>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val connectionState: SharedFlow<Boolean> = _connectionState.asSharedFlow()

    private var url: String = ""

    private val _isReconnect = MutableStateFlow<Long?>(value = null)
    val isReconnect: StateFlow<Long?> = _isReconnect.asStateFlow()


    fun connect(context: Context,url: String) {
        this.url = url
        this.appContext = context.applicationContext
        try {
            val request = Request.Builder().url(url).build()
            webSocket = client.newWebSocket(request, createListener())
        } catch (e: Exception) {
            _messages.tryEmit(ChatMessage("❌ Критическая ошибка: ${e.message}", isFromServer = true))
            _connectionState.tryEmit(false)
            scheduleReconnect()
        }
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts = 0
            _connectionState.tryEmit(true)
            _messages.tryEmit(ChatMessage("✅ Подключено к WebSocket серверу", isFromServer = true))
            startPing()
            flushMessageQueue()
            scope.launch {
                val queued = MessageQueueStore.getAllMessages(appContext)
                queued.forEach {
                    webSocket?.send(it)
                    _messages.tryEmit(ChatMessage("📤 (Из очереди) Вы: $it", isFromServer = true))
                    MessageQueueStore.removeMessage(appContext, it)
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "ping") {
                println("✅ Сервер ответил на ping")
            } else {
                _messages.tryEmit(ChatMessage("📨 Сервер: $text", isFromServer = true))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _messages.tryEmit(ChatMessage("❌ Ошибка подключения: ${t.message}", isFromServer = true))
            _connectionState.tryEmit(false)
            scheduleReconnect()
            stopPing()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            _messages.tryEmit(ChatMessage("🔒 Соединение закрывается: $reason", isFromServer = true))
            stopPing()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.tryEmit(false)
            _messages.tryEmit(ChatMessage("🔴 Соединение закрыто: $reason", isFromServer = true))
            scheduleReconnect()
            stopPing()
        }
    }

    fun sendMessage(context: Context, message: String) {
        if (_connectionState.replayCache.firstOrNull() == true) {
            try {
                webSocket?.send(message)
                _messages.tryEmit(ChatMessage("📤 Вы: $message", isFromServer = true))
            } catch (e: Exception) {
                scope.launch { MessageQueueStore.saveMessage(context, message) }
                _messages.tryEmit(ChatMessage("⏳ В очереди: $message", isFromServer = true, queued = true))
            }
        } else {
            scope.launch { MessageQueueStore.saveMessage(context, message) }
            _messages.tryEmit(ChatMessage("⏳ В очереди: $message", isFromServer = true, queued = true))
        }
    }

    private fun startPing(intervalMillis: Long = 30_000) {
        if (pingJob?.isActive == true) return

        pingJob = scope.launch {
            while (isActive) {
                delay(intervalMillis)
                try {
                    if (_connectionState.tryEmit(true)) {
                        webSocket?.send("ping")
                        println("📡 Ping отправлен")
                    }
                } catch (e: Exception) {
                    println("⚠️ Ошибка при отправке ping: ${e.message}")
                }
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Завершение работы")
            webSocket = null
        } catch (e: Exception) {
            Log.e("WebSocket", "❌ Ошибка при отключении: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            var remaining = calculateReconnectDelay() / 1000
            _isReconnect.value = remaining

            while (remaining > 0) {
                delay(1000)
                remaining--
                _isReconnect.value = remaining
            }

            _isReconnect.value = null
            reconnectAttempts++
            connect(appContext, url)
        }
    }

    private fun calculateReconnectDelay(): Long {
        val delay = 1000L * 2.0.pow(reconnectAttempts.toDouble())
        return minOf(delay.toLong(), 30_000L)
    }

    private fun flushMessageQueue() {
        while (messageQueue.isNotEmpty()) {
            val msg = messageQueue.poll()
            webSocket?.send(msg)
            _messages.tryEmit(ChatMessage("📤 (Очередь) Вы: $msg", isFromServer = true))
        }
    }
}