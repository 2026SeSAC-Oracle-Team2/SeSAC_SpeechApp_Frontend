package com.sesac.speechapp.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * P3-19 v1.2 음성 업로드 응답
 * TODO: 서버 응답 스키마 확정 시 필드 재조정
 */
data class VoiceUploadResponse(
    @SerializedName("voiceRecordId") val voiceRecordId: Long,
    @SerializedName("turnId") val turnId: Long,
    @SerializedName("sessionId") val sessionId: Long,
    @SerializedName("filePath") val filePath: String,
)
