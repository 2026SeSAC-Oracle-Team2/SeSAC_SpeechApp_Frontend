package com.sesac.speechapp.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 서버 응답의 JSON 필드명은 Jackson 기본(camelCase)로 내려온다.
 * - 실측 (2026-08-30): {"accessToken":"...","refreshToken":"...","isNewUser":true, ...}
 * - 과거 snake_case 규약(access_token) 문서와 달라서 @SerializedName 제거 (양쪽 불일치 시 기본값 ""로
 *   채워져 토큰이 사라지고 Authorization 헤더 누락 → 전 API 403 되는 문제의 근원)
 */
data class LoginResponse(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresIn: Int = 0,
    val user: UserDto? = null,
    val isNewUser: Boolean = false,
)

/**
 * D-6 확장 (05a v1.6 §2 UserDto — 백엔드 D-3 AuthDto.kt UserDto 대응):
 * 하위호환 — 기존 필드 유지 + nullable 5필드 추가만 (제거 없음, 기존 클라 파싱 무영향).
 * - userAq: 대표 AQ — null = 설문 미응답 (가입 설문 재노출 판별 기준, 06 v1.7 §5.2)
 */
data class UserDto(
    val id: Long = -1,
    val uuid: String = "",
    val email: String? = null,
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val level: Int? = null,
    val hobbies: String? = null,
    val sex: String? = null,
    // ISO yyyy-MM-dd 문자열 (서버 UserProfile.birthDate LocalDate toString)
    val birthDate: String? = null,
    // 선택 태그 쉼표 문자열 (03a §1.1 형식 "등산, 골프")
    val tags: String? = null,
    val userAq: Int? = null,
)