package com.sesac.speechapp.data.remote.websocket

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket Manager — 뼈대 (P2-14, P3-10)
 *
 * TODO: 실제 연동 시:
 *  1. BASE_URL을 dev/prod 환경에 맞게 설정
 *  2. JWT 토큰을 Header에 추가 ("Authorization: Bearer ...")
 *  3. TURN_RESULT, TTS_READY 등 이벤트 수신 콜백 구현
 *  4. 자동 재연결 로직 + 백오프 구현 (리스크 대응)
 */
class WebSocketManager {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    // TODO: Base URL을 BuildConfig 또는 외부 설정으로 이동
    private val baseWsUrl = "wss://dev.api.speech-app.example.com/ws"

    fun connect(listener: WebSocketListener) {
        val request = Request.Builder()
            .url(baseWsUrl)
            .build()
        webSocket = client.newWebSocket(request, listener)
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    fun isConnected(): Boolean = webSocket != null
}
