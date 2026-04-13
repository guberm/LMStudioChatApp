package com.guberdev.lmstudiochat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val gson = Gson()
    private var streamingJob: Job? = null

    private val _connections = MutableStateFlow<List<ConnectionProfile>>(emptyList())
    val connections: StateFlow<List<ConnectionProfile>> = _connections

    private val _activeConnection = MutableStateFlow<ConnectionProfile?>(null)
    val activeConnection: StateFlow<ConnectionProfile?> = _activeConnection

    private val _availableModels = MutableStateFlow<List<LmModel>>(emptyList())
    val availableModels: StateFlow<List<LmModel>> = _availableModels

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _currentSessionId = MutableStateFlow("")
    val currentSessionId: StateFlow<String> = _currentSessionId

    private val _pendingImages = MutableStateFlow<List<String>>(emptyList())
    val pendingImages: StateFlow<List<String>> = _pendingImages

    private var activeResponseBody: okhttp3.ResponseBody? = null

    init {
        _connections.value = prefs.getConnections()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Chat Responses", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications when AI response is ready" }
            val nm = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun saveConnection(profile: ConnectionProfile) {
        val current = _connections.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index != -1) current[index] = profile else current.add(profile)
        _connections.value = current
        prefs.saveConnections(current)
        if (_activeConnection.value == null) selectConnection(profile)
        else if (_activeConnection.value?.id == profile.id) _activeConnection.value = profile
    }

    fun deleteConnection(profileId: String) {
        val current = _connections.value.toMutableList()
        current.removeAll { it.id == profileId }
        _connections.value = current
        prefs.saveConnections(current)
        if (_activeConnection.value?.id == profileId) _activeConnection.value = null
    }

    fun selectConnection(profile: ConnectionProfile) {
        _activeConnection.value = profile
        LmStudioApiClient.updateBaseUrl(profile.baseUrl, profile.ngrokMode)
        val session = profile.activeSession
        _messages.clear()
        if (session != null) {
            _currentSessionId.value = session.id
            _messages.addAll(session.messages ?: emptyList())
        } else {
            startNewChat()
        }
        fetchModels()
    }

    fun setModelForActiveConnection(modelId: String) {
        val active = _activeConnection.value ?: return
        val updated = active.copy(selectedModel = modelId)
        _activeConnection.value = updated
        saveConnection(updated)
        val session = updated.activeSession
        if (session == null) startNewChat()
        else { _messages.clear(); _messages.addAll(session.messages ?: emptyList()) }
    }

    fun startNewChat() {
        val active = _activeConnection.value ?: return
        val sessionId = UUID.randomUUID().toString()
        _currentSessionId.value = sessionId
        _messages.clear()
        _messages.add(ChatMessage("system", "You are a helpful AI assistant connected via LM Studio."))
        _messages.add(ChatMessage("assistant", "Hello! Connected to **${active.name}** via `${active.selectedModel}`. How can I help?"))
        persistCurrentSession(sessionId, "New Chat")
    }

    private fun persistCurrentSession(sessionId: String = _currentSessionId.value, titleOverride: String = "") {
        val active = _activeConnection.value ?: return
        val sessionTitle = titleOverride.ifBlank {
            _messages.firstOrNull { it.role == "user" }?.content?.take(50) ?: "New Chat"
        }
        val session = ChatSession(id = sessionId, title = sessionTitle, messages = _messages.toList())
        val history = (active.chatHistory ?: emptyList()).toMutableList()
        val idx = history.indexOfFirst { it.id == sessionId }
        if (idx != -1) history[idx] = session else history.add(0, session)
        val updated = active.copy(chatHistory = history.take(50), activeSessionId = sessionId)
        _activeConnection.value = updated
        saveConnection(updated)
    }

    fun loadSession(session: ChatSession) {
        _currentSessionId.value = session.id
        _messages.clear()
        _messages.addAll(session.messages ?: emptyList())
        val active = _activeConnection.value ?: return
        val updated = active.copy(activeSessionId = session.id)
        _activeConnection.value = updated
        saveConnection(updated)
    }

    fun deleteSession(sessionId: String) {
        val active = _activeConnection.value ?: return
        val history = (active.chatHistory ?: emptyList()).filter { it.id != sessionId }
        val wasActive = active.activeSessionId == sessionId
        val newActiveId = if (wasActive) history.firstOrNull()?.id ?: "" else active.activeSessionId
        val updated = active.copy(chatHistory = history, activeSessionId = newActiveId)
        _activeConnection.value = updated
        saveConnection(updated)
        if (wasActive) {
            if (history.isNotEmpty()) loadSession(history.first()) else startNewChat()
        }
    }

    fun stopGeneration() {
        try { activeResponseBody?.close() } catch (_: Exception) {}
        activeResponseBody = null
        streamingJob?.cancel()
        streamingJob = null
        _isLoading.value = false
        stopForegroundService()
    }

    fun fetchModels() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                val response = LmStudioApiClient.apiService.getModels()
                _availableModels.value = response.data
                val currentModel = _activeConnection.value?.selectedModel
                if (currentModel.isNullOrEmpty() || response.data.none { it.id == currentModel }) {
                    response.data.firstOrNull()?.id?.let { setModelForActiveConnection(it) }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch models: ${e.localizedMessage}"
                _availableModels.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addPendingImage(base64: String) {
        _pendingImages.value = _pendingImages.value + base64
    }

    fun removePendingImage(index: Int) {
        if (index in _pendingImages.value.indices)
            _pendingImages.value = _pendingImages.value.toMutableList().also { it.removeAt(index) }
    }

    fun sendMessage(userText: String) {
        val images = _pendingImages.value.toList()
        if (userText.isBlank() && images.isEmpty()) return
        _pendingImages.value = emptyList()

        val activeProfile = _activeConnection.value ?: return
        if (activeProfile.selectedModel.isBlank()) { _errorMessage.value = "Please select a model first."; return }

        val userMessage = ChatMessage("user", userText.trim(), images.ifEmpty { null })
        _messages.add(userMessage)
        val assistantIndex = _messages.size
        _messages.add(ChatMessage("assistant", ""))
        _isLoading.value = true
        _errorMessage.value = null
        startForegroundService()

        streamingJob = viewModelScope.launch {
            try {
                // Strip images from all history except the current message to keep request size manageable
                val requestMessages = _messages.take(assistantIndex).mapIndexed { i, msg ->
                    if (i == assistantIndex - 1) msg else msg.copy(images = null)
                }
                val responseBody = LmStudioApiClient.apiService.createChatCompletionStream(
                    request = ChatRequest(
                        model = activeProfile.selectedModel,
                        messages = requestMessages,
                        stream = true
                    )
                )
                activeResponseBody = responseBody
                withContext(Dispatchers.IO) {
                    val reader = responseBody.byteStream().bufferedReader()
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            if (line.startsWith("data: ")) {
                                val data = line.removePrefix("data: ").trim()
                                if (data == "[DONE]") return@forEach
                                try {
                                    val streamResponse = gson.fromJson(data, StreamResponse::class.java)
                                    val content = streamResponse.choices?.firstOrNull()?.delta?.content
                                    if (content != null) {
                                        withContext(Dispatchers.Main) {
                                            val cur = _messages[assistantIndex]
                                            _messages[assistantIndex] = cur.copy(content = cur.content + content)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                withContext(Dispatchers.Main) { _errorMessage.value = "Error: ${e.localizedMessage}" }
            } finally {
                activeResponseBody = null
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    stopForegroundService()
                    persistCurrentSession()
                    showResponseNotification()
                }
            }
        }
    }

    fun renameCurrentSession(newTitle: String) {
        val active = _activeConnection.value ?: return
        val sessionId = _currentSessionId.value
        val history = (active.chatHistory ?: emptyList()).toMutableList()
        val idx = history.indexOfFirst { it.id == sessionId }
        if (idx != -1) {
            history[idx] = history[idx].copy(title = newTitle)
            val updated = active.copy(chatHistory = history)
            _activeConnection.value = updated
            saveConnection(updated)
        }
    }

    fun exportChatToMarkdown(): String {
        val active = _activeConnection.value
        return buildString {
            appendLine("# Chat Export")
            appendLine("**Connection:** ${active?.name ?: "Unknown"}")
            appendLine("**Model:** ${active?.selectedModel ?: "Unknown"}")
            appendLine()
            appendLine("---")
            appendLine()
            _messages.filter { it.role != "system" }.forEach { msg ->
                appendLine(if (msg.role == "user") "### You" else "### Assistant")
                appendLine()
                appendLine(msg.content)
                appendLine()
            }
        }
    }

    private fun startForegroundService() {
        try {
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, ChatForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        } catch (_: Exception) {}
    }

    private fun stopForegroundService() {
        try {
            getApplication<Application>().stopService(
                Intent(getApplication(), ChatForegroundService::class.java)
            )
        } catch (_: Exception) {}
    }

    private fun showResponseNotification() {
        try {
            val ctx = getApplication<Application>()
            val pi = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_IMMUTABLE
            )
            val sessionTitle = (_activeConnection.value?.chatHistory ?: emptyList())
                .firstOrNull { it.id == _currentSessionId.value }?.title ?: "Chat"
            val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle(sessionTitle)
                .setContentText("${_activeConnection.value?.selectedModel ?: "AI"} finished responding")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, notif)
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "chat_responses"
        const val NOTIF_ID = 1001
    }
}
