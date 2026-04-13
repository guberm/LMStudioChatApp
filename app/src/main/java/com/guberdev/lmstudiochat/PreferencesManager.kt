package com.guberdev.lmstudiochat

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class ConnectionProfile(
    val id: String,
    val name: String,
    val ip: String,
    val port: String,
    val selectedModel: String = "",
    val ngrokMode: Boolean = false,
    val savedChat: List<ChatMessage>? = emptyList(),
    val chatHistory: List<ChatSession> = emptyList(),
    val activeSessionId: String = ""
) {
    val baseUrl: String get() = "http://$ip:$port/v1/"
    val safeChat: List<ChatMessage> get() = savedChat ?: emptyList()
    val activeSession: ChatSession? get() = chatHistory?.find { it.id == activeSessionId }
}

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("lmstudio_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getConnections(): List<ConnectionProfile> {
        val json = prefs.getString("connections", "[]")
        val type = object : TypeToken<List<ConnectionProfile>>() {}.type
        val raw: List<ConnectionProfile> = gson.fromJson(json, type) ?: emptyList()
        return raw.map { p ->
            p.copy(
                chatHistory = (p.chatHistory ?: emptyList()).map { s ->
                    s.copy(messages = (s.messages ?: emptyList()).map { msg ->
                        msg.copy(images = msg.images ?: emptyList())
                    })
                },
                activeSessionId = p.activeSessionId ?: ""
            )
        }
    }

    fun saveConnections(connections: List<ConnectionProfile>) {
        prefs.edit().putString("connections", gson.toJson(connections)).apply()
    }
}
