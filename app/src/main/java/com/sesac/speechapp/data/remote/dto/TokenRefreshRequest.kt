package com.sesac.speechapp.data.remote.dto

/**
 * POST /api/v1/auth/refresh 요청
 *
 * ⚠️ 백엔드 실계약 (2026-09-03 실측, AuthDto.kt): refreshToken (camelCase).
 * 과거 snake_case(refresh_token)는 서버가 파싱하지 못해 500을 반환했다.
 */
data class TokenRefreshRequest(
    val refreshToken: String,
)