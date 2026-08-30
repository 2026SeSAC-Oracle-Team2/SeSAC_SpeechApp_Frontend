package com.sesac.speechapp.data.remote.dto

/**
 * PATCH /api/v1/users/me 요청 바디
 */
data class UpdateProfileRequest(
    val nickname: String,
)