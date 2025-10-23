package com.example.wssdemo

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(application) as T
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WebSocketDemoTheme {
                WebSocketDemoScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSocketDemoScreen(
    viewModel: MainViewModel
) {
    val messages by viewModel.messages.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isReconnect by viewModel.isReconnect.collectAsState()

    var textFieldValue by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size) {
        // Прокручиваем к самому низу, когда список обновляется
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Заголовок и статус
        Text(
            text = "WebSocket Демо",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Статус подключения
        ConnectionStatus(
            isConnected = isConnected,
            isLoading = isLoading,
            isReconnect = isReconnect
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Поле ввода и кнопка отправки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                placeholder = { Text("Введите сообщение...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Button(
                onClick = {
                    viewModel.sendMessage(textFieldValue)
                    textFieldValue = ""
                },
                enabled = textFieldValue.isNotBlank()
            ) {
                Text("Отправить")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Кнопки управления
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.reconnect() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Переподключиться")
            }

            Button(
                onClick = { viewModel.clearMessages() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Очистить")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Лог сообщений
        Text(
            text = "Лог сообщений:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        MessagesLog(
            messages = messages,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            scrollState = scrollState
        )
    }
}

@Composable
fun ConnectionStatus(
    isConnected: Boolean,
    isLoading: Boolean,
    isReconnect: Long?
) {
    val (text, color) = when {
        isLoading -> "Подключение..." to MaterialTheme.colorScheme.secondary
        isReconnect != null -> "Переподключение через $isReconnect сек" to MaterialTheme.colorScheme.secondary
        isConnected -> "Подключено к WebSocket" to MaterialTheme.colorScheme.primary
        else -> "Отключено" to MaterialTheme.colorScheme.error
    }

    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun MessagesLog(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    scrollState: ScrollState
) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Сообщений пока нет",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                messages.forEach { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (message.queued) {
                                Icon(
                                    imageVector = Icons.Filled.Send,
                                    contentDescription = "Очередь сообщений",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 4.dp)
                                )
                            }
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = message.timestamp.format(formatter),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WebSocketDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewWebSocketDemo() {
    WebSocketDemoTheme {
        // Для превью создаем mock ViewModel
        WebSocketDemoScreen(viewModel = MainViewModel(Application()))
    }
}