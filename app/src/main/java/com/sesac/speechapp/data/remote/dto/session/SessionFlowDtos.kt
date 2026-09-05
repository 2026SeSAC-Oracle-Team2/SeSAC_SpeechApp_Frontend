package com.sesac.speechapp.data.remote.dto.session

/**
 * 세션 플로우 DTO — 백엔드 실계약 기준 (~/app dto/session/SessionFlowDtos.kt, 2026-09-03).
 * 05_API_Design.md §4 스펙과의 차이는 아래 주석 기록.
 *
 * 차이 기록 (스펙 대비 실구현):
 *  - 세션 생성: POST /api/v1/sessions/today·/theme (D-7 1.4 — /v2는 하위호환 유지), userId = 쿼리파라미터
 *  - LISTEN selected: Int (order 1-based) — 스펙의 "<choice ref>" 문자열 아님
 *  - 음성 제출: multipart file + userId 쿼리파라미터 필수
 *  - choices[].mediaType: 스텁은 "text" 소문자 — 파싱 시 대소문자 무시 처리
 */

/** POST /api/v1/sessions/today·/theme 응답 (D-5: type 필드 — today | theme) */
data class SessionCreateData(
    val sessionId: Long,
    val theme: String,
    val type: String? = null,   // D-5 신설: today | theme — 요청 엔드포인트에 따른 세션 종류
    val turns: List<TurnDto>,
)

data class TurnDto(
    val turnId: Long,
    val turnNumber: Int,
    val type: String,           // LISTEN_TEXT | LISTEN_PICTURE | NAMING | SHADOWING | SELF_TALK
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

/** POST /sessions/{id}/finish 응답 — 간이 보고서 (v1.6 2단계: talk/total 항상 null) */
data class FinishData(
    val sessionAQ: Int,
    val feedbacks: FeedbacksDto,
)

/** 피드백 6종 — 개별 nullable (Gson null 주의). talk/total은 세부 보고서(§8.3)에서 수령 */
data class FeedbacksDto(
    val listenFeedback: String? = null,
    val namingFeedback: String? = null,
    val shadowingFeedback: String? = null,
    val selfTalkFeedback: String? = null,
    val talkFeedback: String? = null,
    val totalFeedback: String? = null,
)

// ═══════════════════════════════════════════════════════════════
// D-7 3.1 대시보드 / 세부 보고서 DTO — 05a v1.6 §8.1~8.3 JSON 그대로
// ═══════════════════════════════════════════════════════════════

/**
 * GET /api/v1/users/me/scores 응답 (§8.1 — 대표점수)
 * null = 캐시 미산출 — 클라 폴백: 방사형 0 표시 + "학습을 시작해보세요" 안내문
 */
data class SessionScoresData(
    val userAq: Int? = null,
    val listen: Double? = null,
    val naming: Double? = null,
    val shadowing: Double? = null,
    val selfTalk: Double? = null,
)

/**
 * GET /api/v1/users/me/sessions/history 응답 (§8.2 — 지난 학습 카드)
 * 조회 조건(서버): STATUS != COMPLETED_NO_TALK AND AQ IS NOT NULL
 * createdAt은 ISO 타임스탬프 문자열 — 표현(YYYY.mm.dd)은 클라 포맷 책임 (§8.2 규약)
 */
data class SessionHistoryData(
    val sessions: List<SessionHistoryItem> = emptyList(),
)

data class SessionHistoryItem(
    val sessionId: Long,
    val sessionName: String,
    val createdAt: String,  // ISO — 예: "2026-09-06T01:50:09.527617"
    val aq: Int,
)

/**
 * GET /api/v1/sessions/{id}/report 응답 (§8.3 — 세부 보고서)
 * radar = 해당 세션 TURN.score 집계 (대표점수 아님 — §8.1과 출처 구분)
 * answer 계약: LISTEN_TEXT={mediaType:"text", value:선택지 텍스트, correct} /
 *   LISTEN_PICTURE={mediaType:"image", value:image_id 문자열, correct} /
 *   음성형={mediaType:"voice", value:STT, voiceUrl}
 * 응답 수신 시 서버가 REPORT_VIEWED_AT 기록 — 클라 콜백 불필요
 */
data class SessionReportData(
    val sessionId: Long,
    val aq: Int,
    val totalFeedback: String? = null,
    val radar: SessionRadarData? = null,
    val metricCards: List<MetricCardDto> = emptyList(),
    val talkFeedback: String? = null,
    val talkHistory: List<TalkHistoryItem> = emptyList(),
    val reportViewedAt: String? = null,
)

data class SessionRadarData(
    val listen: Double? = null,
    val naming: Double? = null,
    val shadowing: Double? = null,
    val selfTalk: Double? = null,
)

/** 지표별 카드 — 클릭 확장 시 turns 배열 (문제 기록) */
data class MetricCardDto(
    val type: String,       // LISTEN | NAMING | SHADOWING | SELF_TALK
    val score: Double? = null,
    val feedback: String? = null,
    val turns: List<MetricTurnDto> = emptyList(),
)

data class MetricTurnDto(
    val turnId: Long,
    val turnNumber: Int,
    val promptText: String? = null,
    val ttsUrl: String? = null,
    val imageUrl: String? = null,
    val answer: AnswerDto? = null,
)

/**
 * 내 답변 (§8.3 answer 계약 v1.6 확정):
 * LISTEN_TEXT={mediaType:"text", value:"선택지 텍스트", correct} /
 * LISTEN_PICTURE={mediaType:"image", value:"image_id 문자열", correct} /
 * 음성형={mediaType:"voice", value:"STT", voiceUrl}
 */
data class AnswerDto(
    val mediaType: String,  // "text" | "image" | "voice"
    val value: String? = null,
    val correct: Boolean? = null,   // LISTEN만 포함
    val voiceUrl: String? = null,   // 음성형만
)

/** 대화 내역 — AI text + 내 답변 다시 듣기 (voiceUrl) */
data class TalkHistoryItem(
    val speaker: String,    // AI | USER
    val text: String,
    val ttsUrl: String? = null,     // AI 발화 (VOICE_RECORD AI행 있으면)
    val voiceUrl: String? = null,   // 유저 답변 다시 듣기
)