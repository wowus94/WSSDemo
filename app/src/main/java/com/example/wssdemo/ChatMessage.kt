package com.example.wssdemo

import java.time.LocalDateTime

data class ChatMessage(
    val text: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isFromServer: Boolean
)