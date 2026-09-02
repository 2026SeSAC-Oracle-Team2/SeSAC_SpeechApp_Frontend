package com.sesac.speechapp.data.remote.dto

/**
 * POST /api/v1/auth/refresh 응답 data (백엔드 TokenRefreshResponse camelCase 실측)
 */
data class TokenRefreshResponse(
    val accessToken: String = "",
    val expiresIn: Long = 0,
)