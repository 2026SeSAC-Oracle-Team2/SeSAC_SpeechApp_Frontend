package com.sesac.speech.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ChatViewModel : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages

    // 하드코딩된 스텁 데이터 (API 연동 전)
    init {
        loadStubMessages()
    }

    private fun loadStubMessages() {
        val stubList = listOf(
            ChatMessage.AiMessage(
                id = 1,
                timestamp = "오전 10:01",
                content = "안녕하세요! 오늘은 '주말에 뭐 했어요?'라는 주제로 대화해볼까요?"
            ),
            ChatMessage.UserMessage(
                id = 2,
                timestamp = "오전 10:02",
                content = "주말에 친구랑 공원에 갔어요"
            ),
            ChatMessage.TurnResult(
                id = 3,
                timestamp = "오전 10:02",
                overallScore = 72,
                feedbackText = "문장 구성은 좋았어요! 다양한 접속사를 사용하면 더 풍부한 표현이 될 거예요."
            ),
            ChatMessage.AiMessage(
                id = 4,
                timestamp = "오전 10:03",
                content = "공원에서 무엇을 했나요?"
            ),
        )
        _messages.value = stubList
    }

    // 녹음 완료 후 호출 (P3-10 연동 시)
    fun addUserMessage(content: String) {
        val current = _messages.value?.toMutableList() ?: mutableListOf()
        current.add(
            ChatMessage.UserMessage(
                id = System.currentTimeMillis(),
                timestamp = "방금",
                content = content
            )
        )
        _messages.value = current
    }

    // API 응답 시 호출 (P3-10 연동 시)
    fun addAiResponse(content: String) {
        val current = _messages.value?.toMutableList() ?: mutableListOf()
        current.add(
            ChatMessage.AiMessage(
                id = System.currentTimeMillis(),
                timestamp = "방금",
                content = content
            )
        )
        _messages.value = current
    }

    // 채점 결과 수신 시 호출 (P3-10 연동 시)
    fun addTurnResult(score: Int, feedback: String) {
        val current = _messages.value?.toMutableList() ?: mutableListOf()
        current.add(
            ChatMessage.TurnResult(
                id = System.currentTimeMillis(),
                timestamp = "방금",
                overallScore = score,
                feedbackText = feedback
            )
        )
        _messages.value = current
    }
}
