package com.sesac.speech.data.remote.dto

/**
 * 서버 공통 응답 래퍼 (API SPEC v1.0)
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ApiError?,
    val timestamp: String?,
)

data class ApiError(
    val code: String,
    val message: String,
    val detail: String?,
)
