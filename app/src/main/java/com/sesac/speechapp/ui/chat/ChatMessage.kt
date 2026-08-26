package com.sesac.speechapp.ui.chat

/**
 * 채팅 메시지 sealed class — AI / USER / TURN_RESULT 세 가지 타입
 */
sealed class ChatMessage {
    abstract val id: Long
    abstract val timestamp: String

    data class AiMessage(
        override val id: Long,
        override val timestamp: String,
        val content: String,
        val avatarUrl: String? = null,
    ) : ChatMessage()

    data class UserMessage(
        override val id: Long,
        override val timestamp: String,
        val content: String,
    ) : ChatMessage()

    data class TurnResult(
        override val id: Long,
        override val timestamp: String,
        val overallScore: Int,
        val feedbackText: String,
    ) : ChatMessage()
}
