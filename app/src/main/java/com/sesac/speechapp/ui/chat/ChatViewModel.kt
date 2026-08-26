package com.sesac.speechapp.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isUploading = MutableLiveData(false)
    val isUploading: LiveData<Boolean> = _isUploading

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

    /**
     * 녹음 중단 후 스텁 플로우
     * 실제 구현 시: upload -> WebSocket -> AI 응답 -> 채점 결과
     */
    fun onRecordingStopped() {
        val current = _messages.value?.toMutableList() ?: mutableListOf()

        // 1. 사용자 녹음 메시지 추가 (STT 텍스트 스텁)
        current.add(
            ChatMessage.UserMessage(
                id = System.currentTimeMillis(),
                timestamp = "방금",
                content = "음... 공원에서 산책하고 커피 마셨어요"
            )
        )
        _messages.value = current.toList()

        // 2. 약간의 딜레이 후 AI 응답 + 턴 결과 추가 (업로드/처리 시뮬레이션)
        viewModelScope.launch {
            delay(1500)

            val updated = _messages.value?.toMutableList() ?: mutableListOf()

            // AI 응답
            updated.add(
                ChatMessage.AiMessage(
                    id = System.currentTimeMillis(),
                    timestamp = "방금",
                    content = "산책하면서 어떤 대화를 나누셨나요?"
                )
            )

            // 턴 결과 (랜덤 스텁 점수)
            updated.add(
                ChatMessage.TurnResult(
                    id = System.currentTimeMillis(),
                    timestamp = "방금",
                    overallScore = (65..85).random(),
                    feedbackText = "구체적인 상황 설명이 좋았어요! 다음에는 감정 표현도 함께 말해보세요."
                )
            )

            _messages.value = updated.toList()
        }
    }

    // API 연동 시 사용할 메서드들
    fun addUserMessage(content: String) {
        val current = _messages.value?.toMutableList() ?: mutableListOf()
        current.add(
            ChatMessage.UserMessage(
                id = System.currentTimeMillis(),
                timestamp = "방금",
                content = content
            )
        )
        _messages.value = current.toList()
    }

    fun addAiResponse(content: String) {
        val current = _messages.value?.toMutableList() ?: mutableListOf()
        current.add(
            ChatMessage.AiMessage(
                id = System.currentTimeMillis(),
                timestamp = "방금",
                content = content
            )
        )
        _messages.value = current.toList()
    }

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
        _messages.value = current.toList()
    }
}
