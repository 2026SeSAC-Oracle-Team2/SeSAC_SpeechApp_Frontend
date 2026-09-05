package com.sesac.speechapp.ui.history

/**
 * D-7 3.2 이력 카드 데이터 — 05a §8.2 실계약 기반 (sessionName / YYYY.mm.dd / AQ).
 * createdAt(ISO 원문)은 세부 보고서 화면 전달용으로 보관.
 */
data class SessionHistory(
    val id: Long,
    val date: String,
    val topic: String,
    val score: Int,
    val createdAt: String = "",
)