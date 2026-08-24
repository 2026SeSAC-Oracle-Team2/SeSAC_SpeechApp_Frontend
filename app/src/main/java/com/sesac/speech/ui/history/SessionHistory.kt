package com.sesac.speech.ui.history

data class SessionHistory(
    val id: Long,
    val date: String,
    val topic: String,
    val score: Int,
    val isCompleted: Boolean,
    val turnCount: Int,
)
