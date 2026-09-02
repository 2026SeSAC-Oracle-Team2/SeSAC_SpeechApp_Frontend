package com.sesac.speechapp.data.remote.dto.session

/**
 * 세션 플로우 DTO — 백엔드 실계약 기준 (~/app dto/session/SessionFlowDtos.kt, 2026-09-03).
 * 05_API_Design.md §4 스펙과의 차이는 아래 주석 기록.
 *
 * 차이 기록 (스펙 대비 실구현):
 *  - 세션 생성: POST /api/v1/sessions/v2, userId = 쿼리파라미터 (스펙 §4.1과 경로 다름)
 *  - LISTEN selected: Int (order 1-based) — 스펙의 "<choice ref>" 문자열 아님
 *  - 음성 제출: multipart file + userId 쿼리파라미터 필수
 *  - choices[].mediaType: 스텁은 "text" 소문자 — 파싱 시 대소문자 무시 처리
 */

/** POST /api/v1/sessions/v2 응답 */
data class SessionCreateData(
    val sessionId: Long,
    val theme: String,
    val turns: List<TurnDto>,
)

data class TurnDto(
    val turnId: Long,
    val turnNumber: Int,
    val type: String,           // LISTEN | NAMING | SHADOWING | SELF_TALK
    val ttsUrl: String? = null, // /api/v1/voice/{voiceRecordId} (상대경로)
    val passage: String? = null,
    val choices: List<ChoiceDto>? = null, // LISTEN 전용
    val imageId: Long? = null,            // NAMING / SELF_TALK
    val imageUrl: String? = null,         // /api/v1/content/images/{id}/file
    val hintAvailable: Int? = null,       // NAMING: 2
)

data class ChoiceDto(
    val order: Int,
    val mediaType: String,  // "text" | "image" (스텁 소문자 — 클라는 대소문자 무시)
    val context: String,    // 텍스트 내용 또는 image_id
)

/** LISTEN 제출 — 실계약: selected는 Int (선택지 order, 1-based) */
data class ListenSubmitRequest(
    val selected: Int,
)

data class ListenSubmitData(
    val turnId: Long,
    val score: Int,     // 100 | 0
    val correct: Boolean,
)

/** NAMING/SHADOWING/SELF_TALK 제출 응답 */
data class VoiceSubmitData(
    val turnId: Long,
    val score: Double,
    val voiceRecordId: Long,
    val userVoiceEval: UserVoiceEvalDto,
)

data class UserVoiceEvalDto(
    val durationSecond: Int,
    val syllables: Int,
    val speakingTime: Double,
    val articulationTime: Double,
    val text: String,
)

/** NAMING 힌트 응답 — hintOrder 1=SEMANTIC(의미), 2=ARTICULATORY(조음) */
data class HintData(
    val hintOrder: Int,
    val cueType: String,    // SEMANTIC | ARTICULATORY
    val text: String,
)

/** 이야기 턴 응답 — 첫 호출 userText=null */
data class TalkData(
    val turnId: Long,
    val turnNumber: Int,
    val aiText: String,
    val userText: String? = null,
)

/** POST /sessions/{id}/finish 응답 */
data class FinishData(
    val sessionAQ: Int,
    val feedbacks: FeedbacksDto,
)

/** 피드백 6종 — 개별 nullable (Gson null 주의) */
data class FeedbacksDto(
    val listenFeedback: String? = null,
    val namingFeedback: String? = null,
    val shadowingFeedback: String? = null,
    val selfTalkFeedback: String? = null,
    val talkFeedback: String? = null,
    val totalFeedback: String? = null,
)