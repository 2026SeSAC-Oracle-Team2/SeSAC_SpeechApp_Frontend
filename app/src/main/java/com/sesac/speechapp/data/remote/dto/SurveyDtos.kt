package com.sesac.speechapp.data.remote.dto

/**
 * GET /api/v1/users/me/tags 응답 (D-3 신설 — 05a v1.6 §2 실계약)
 * 태그 마스터 15종 {tagId, tag}. 백엔드 TagItem.tagId는 Long — 와이어는 숫자라 동일.
 */
data class TagDto(
    val tagId: Long,
    val tag: String,
)

data class TagsResponse(
    val tags: List<TagDto> = emptyList(),
)

/**
 * POST /api/v1/users/me/survey (D-3 신설 — 05a v1.6 §2 + 06 v1.7 §5.2)
 * 산출 주체 = 서버 — 클라는 answers 원문(문항 순서대로 1~5 정수 5개)만 전송.
 * 환산: 총점=Σ(answer×4) → 20~61→30 / 62~80→70 / 81~100→90.
 * 문항 텍스트는 앱 고정(strings_survey.xml) — 서버 저장 없음. SurveyResponse.userAq만 신뢰.
 */
data class SurveyRequest(
    val answers: List<Int>,
)

data class SurveyResponse(
    // 백엔드는 Int?(nullable)로 선언 — 성공 응답은 항상 non-null (환산 AQ)
    val userAq: Int? = null,
    val user: UserDto? = null,
)