package com.sesac.speechapp.data.remote.dto

import com.google.gson.annotations.SerializedName

data class VoiceUploadResponse(
    @SerializedName("voice_record_id") val voiceRecordId: Long,
    @SerializedName("storage_url") val storageUrl: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long,
)
