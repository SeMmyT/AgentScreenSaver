package com.claudescreensaver.data.network

import com.claudescreensaver.data.models.AgentStatus
import com.launchdarkly.eventsource.ConnectStrategy
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import com.launchdarkly.eventsource.background.BackgroundEventHandler
import com.launchdarkly.eventsource.background.BackgroundEventSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class SseClient {

    private val _status = MutableStateFlow(AgentStatus.DISCONNECTED)
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, AgentStatus>>(emptyMap())
    val sessions: StateFlow<Map<String, AgentStatus>> = _sessions.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var eventSource: BackgroundEventSource? = null
    private var baseUrl: String = "" // e.g. "http://host:4001"

    fun connect(url: String) {
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING

        // Derive base URL from SSE URL (e.g. "http://host:4001/events" -> "http://host:4001")
        baseUrl = url.removeSuffix("/events").removeSuffix("/")

        try {
            connectInternal(url)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun connectInternal(url: String) {
        val handler = object : BackgroundEventHandler {
            override fun onOpen() {
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(event: String, messageEvent: MessageEvent) {
                try {
                    when (event) {
                        "snapshot" -> {
                            // Full sessions map from bridge
                            val obj = JSONObject(messageEvent.data)
                            val map = mutableMapOf<String, AgentStatus>()
                            for (key in obj.keys()) {
                                val sessionJson = obj.getJSONObject(key).toString()
                                map[key] = AgentStatus.fromJson(sessionJson)
                            }
                            _sessions.value = map
                            // Set single status to the "hottest" session
                            _status.value = pickHottestSession(map)
                        }
                        "update" -> {
                            val agentStatus = AgentStatus.fromJson(messageEvent.data)
                            _status.value = agentStatus
                            // Update sessions map
                            val updated = _sessions.value.toMutableMap()
                            updated[agentStatus.sessionId] = agentStatus
                            _sessions.value = updated
                        }
                        else -> {
                            // Fallback: try parsing as single status
                            val agentStatus = AgentStatus.fromJson(messageEvent.data)
                            _status.value = agentStatus
                        }
                    }
                } catch (e: Exception) {
                    // Ignore malformed messages
                }
            }

            override fun onError(t: Throwable) {
                _connectionState.value = ConnectionState.ERROR
            }

            override fun onClosed() {
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onComment(comment: String) {}
        }

        val connectStrategy = ConnectStrategy.http(URI.create(url))

        eventSource = BackgroundEventSource.Builder(
            handler,
            EventSource.Builder(connectStrategy)
                .retryDelay(2, TimeUnit.SECONDS)
        ).build()
        eventSource?.start()
    }

    fun sendInput(sessionId: String, text: String) {
        if (baseUrl.isBlank()) return
        thread {
            try {
                val url = URL("$baseUrl/session/$sessionId/input")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write("""{"text":"${text.replace("\"", "\\\"")}"}""")
                }
                conn.responseCode // trigger the request
                conn.disconnect()
            } catch (_: Exception) {
                // Best-effort
            }
        }
    }

    fun fetchSkinList(callback: (List<SkinListItem>) -> Unit) {
        if (baseUrl.isBlank()) return
        thread {
            try {
                val url = URL("$baseUrl/skins")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = JSONArray(body)
                val items = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    SkinListItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        author = obj.optString("author", "unknown"),
                        isPremium = obj.optBoolean("is_premium", false),
                    )
                }
                callback(items)
            } catch (_: Exception) {
                callback(emptyList())
            }
        }
    }

    fun fetchSkinJson(skinId: String, callback: (String?) -> Unit) {
        if (baseUrl.isBlank()) return
        thread {
            try {
                val url = URL("$baseUrl/skins/$skinId")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                callback(body)
            } catch (_: Exception) {
                callback(null)
            }
        }
    }

    fun uploadSkin(skinJson: String, callback: (Boolean) -> Unit) {
        if (baseUrl.isBlank()) return
        thread {
            try {
                val url = URL("$baseUrl/skins")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                OutputStreamWriter(conn.outputStream).use { it.write(skinJson) }
                callback(conn.responseCode == 201)
                conn.disconnect()
            } catch (_: Exception) {
                callback(false)
            }
        }
    }

    fun disconnect() {
        eventSource?.close()
        eventSource = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _status.value = AgentStatus.DISCONNECTED
        _sessions.value = emptyMap()
    }

    private fun pickHottestSession(sessions: Map<String, AgentStatus>): AgentStatus {
        if (sessions.isEmpty()) return AgentStatus.DISCONNECTED
        val priority = listOf("awaiting_input", "error", "tool_call", "thinking", "idle", "complete")
        return sessions.values.sortedBy { priority.indexOf(it.state.value).let { i -> if (i < 0) 99 else i } }.first()
    }
}

data class SkinListItem(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val isPremium: Boolean,
)
