package com.sesac.speechapp.data.remote.dto

/**
 * POST /api/v1/auth/refresh 요청
 */
data class TokenRefreshRequest(
    val refresh_token: String,
)
