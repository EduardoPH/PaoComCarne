package com.example.data.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SupabaseRealtimeClient(
    private val context: Context,
    private val onDataTableChanged: (table: String, eventType: String) -> Unit,
    private val onReleaseChanged: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val refCounter = AtomicInteger(1)
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var debounceJob: Job? = null

    @Volatile
    private var shouldRun = false

    fun start() {
        if (shouldRun) return
        shouldRun = true
        connect()
    }

    fun stop() {
        shouldRun = false
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        debounceJob?.cancel()
        webSocket?.close(1000, "Stopped")
        webSocket = null
    }

    private fun connect() {
        if (!shouldRun) return
        val baseUrl = SupabaseConfig.getUrl(context)
        val anonKey = SupabaseConfig.getKey(context)
        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()
        webSocket?.cancel()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i("SupabaseRealtime", "Conectado ao Realtime.")
                joinPublicChannel(ws, anonKey)
                startHeartbeat(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w("SupabaseRealtime", "Falha Realtime: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (shouldRun) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun joinPublicChannel(ws: WebSocket, anonKey: String) {
        val tables = listOf("attendances", "shifts", "queue_events", "system_logs", "app_releases")
        val pgChanges = JSONArray()
        for (table in tables) {
            pgChanges.put(
                JSONObject().apply {
                    put("event", "*")
                    put("schema", "public")
                    put("table", table)
                }
            )
        }
        val payload = JSONObject().apply {
            put("config", JSONObject().apply {
                put("broadcast", JSONObject().put("self", false))
                put("presence", JSONObject().put("key", ""))
                put("postgres_changes", pgChanges)
            })
            put("access_token", anonKey)
        }
        val joinMsg = JSONObject().apply {
            put("topic", "realtime:public")
            put("event", "phx_join")
            put("payload", payload)
            put("ref", refCounter.getAndIncrement().toString())
        }
        ws.send(joinMsg.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && shouldRun) {
                delay(25_000L)
                val hb = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                ws.send(hb.toString())
            }
        }
    }

    private fun scheduleReconnect() {
        heartbeatJob?.cancel()
        if (!shouldRun) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5_000L)
            if (shouldRun) connect()
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            val event = json.optString("event")
            if (event == "postgres_changes") {
                val payload = json.optJSONObject("payload") ?: return
                val data = payload.optJSONObject("data") ?: payload
                val table = data.optString("table", "")
                val changeType = data.optString("type", "UPDATE")
                if (table == "app_releases") {
                    onReleaseChanged()
                } else if (table.isNotBlank()) {
                    debounceJob?.cancel()
                    debounceJob = scope.launch {
                        delay(450L)
                        onDataTableChanged(table, changeType)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SupabaseRealtime", "Erro ao processar evento Realtime: ${e.message}")
        }
    }
}
