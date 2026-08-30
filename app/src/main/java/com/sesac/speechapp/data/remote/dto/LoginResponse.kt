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

data class UserDto(
    val uuid: String = "",
    val email: String = "",
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val level: Int? = null,
)
