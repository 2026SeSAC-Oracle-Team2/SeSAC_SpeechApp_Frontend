package com.sesac.speechapp.data.remote.dto

/**
 * PATCH /api/v1/users/me 요청 바디
 *
 * D-6 확장 (05a v1.6 §2 실계약 — 백엔드 D-3 AuthDto.kt UpdateProfileRequest 대응):
 * - 부분 업데이트 규약: null 필드는 기존값 유지 (기존 nickname 단독 호출부 영향 없음)
 * - tagIds: 전량 교체 — 클라는 항상 현재 선택 전체를 전송.
 *   null = 태그 변경 없음 / 명시적 [] = 전체 삭제 / >5개 = E0400 / 없는 tag_id = E0404
 * - birthDate: ISO yyyy-MM-dd 고정 (파싱 실패 = E0400)
 */
data class UpdateProfileRequest(
    val nickname: String,
    val hobbies: String? = null,
    val sex: String? = null,
    val birthDate: String? = null,
    val tagIds: List<Long>? = null,
)