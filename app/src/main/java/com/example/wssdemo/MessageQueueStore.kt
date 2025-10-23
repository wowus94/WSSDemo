package com.example.wssdemo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

object MessageQueueStore {
    private val Context.dataStore by preferencesDataStore(name = "message_queue")

    private val KEY_MESSAGES = stringSetPreferencesKey("queued_messages")

    suspend fun saveMessage(context: Context, message: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_MESSAGES] ?: emptySet()
            prefs[KEY_MESSAGES] = current + message
        }
    }

    suspend fun removeMessage(context: Context, message: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_MESSAGES] ?: emptySet()
            prefs[KEY_MESSAGES] = current - message
        }
    }

    suspend fun getAllMessages(context: Context): List<String> {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_MESSAGES]?.toList() ?: emptyList()
    }

    suspend fun clearAll(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MESSAGES] = emptySet()
        }
    }
}