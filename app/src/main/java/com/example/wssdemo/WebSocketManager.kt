package com.example.wssdemo

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketManager {
    private var webSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Используем MutableSharedFlow с replay = 1 для буферизации
    private val _messages = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 50
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _connectionState = MutableSharedFlow<Boolean>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val connectionState: SharedFlow<Boolean> = _connectionState.asSharedFlow()

    fun connect(url: String) {
        try {
            Log.d("WebSocket", "Попытка подключения к: $url")

            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("WebSocket", "✅ WebSocket подключен успешно")
                    // Используем tryEmit - он не блокируется
                    _connectionState.tryEmit(true)
                    _messages.tryEmit("✅ Подключено к WebSocket серверу")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d("WebSocket", "📨 Получено сообщение: $text")
                    _messages.tryEmit("📨 Сервер: $text")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("WebSocket", "❌ Ошибка WebSocket: ${t.message}")
                    _messages.tryEmit("❌ Ошибка подключения: ${t.message}")
                    _connectionState.tryEmit(false)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WebSocket", "🔒 Соединение закрывается: $reason")
                    _messages.tryEmit("🔒 Соединение закрывается...")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WebSocket", "🔴 Соединение закрыто: $reason")
                    _connectionState.tryEmit(false)
                    _messages.tryEmit("🔴 Соединение закрыто")
                }
            })
        } catch (e: Exception) {
            Log.e("WebSocket", "❌ Ошибка при создании WebSocket: ${e.message}")
            _messages.tryEmit("❌ Критическая ошибка: ${e.message}")
            _connectionState.tryEmit(false)
        }
    }

    fun sendMessage(message: String) {
        try {
            webSocket?.send(message)
            _messages.tryEmit("📤 Вы: $message")
        } catch (e: Exception) {
            Log.e("WebSocket", "❌ Ошибка отправки сообщения: ${e.message}")
            _messages.tryEmit("❌ Ошибка отправки: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Завершение работы")
            webSocket = null
        } catch (e: Exception) {
            Log.e("WebSocket", "❌ Ошибка при отключении: ${e.message}")
        }
    }
}