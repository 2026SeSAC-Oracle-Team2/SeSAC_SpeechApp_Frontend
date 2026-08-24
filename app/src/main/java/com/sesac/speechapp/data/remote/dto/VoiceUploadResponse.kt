package com.sesac.speechapp.data.remote.dto

import com.google.gson.annotations.SerializedName

data class VoiceUploadResponse(
    @SerializedName("voice_record_id") val voiceRecordId: Long,
    @SerializedName("storage_url") val storageUrl: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long,
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val timestamp: String? = null,
)

data class ApiError(
    val code: String,
    val message: String,
    val detail: String? = null,
    val timestamp: String? = null,
)
