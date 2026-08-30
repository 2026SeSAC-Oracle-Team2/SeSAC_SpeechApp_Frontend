package com.sesac.speechapp.data.repository

import android.content.Context
import android.net.Uri
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.RetrofitClient
import com.sesac.speechapp.data.remote.dto.UpdateProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 사용자 프로필 API 담당 Repository
 */
class UserRepository(private val context: Context) {

    private val apiService = RetrofitClient.apiService

    /**
     * 닉네임 수정 (PATCH /api/v1/users/me)
     */
    suspend fun updateNickname(nickname: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.updateProfile(UpdateProfileRequest(nickname))

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("닉네임 저장 실패 (${response.code()})"))
            }

            val body = response.body()
                ?: return@withContext Result.failure(Exception("닉네임 저장 실패: 응답 본문 없음"))

            if (!body.success) {
                return@withContext Result.failure(
                    Exception(body.error?.message ?: "닉네임 저장 실패")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 프로필 사진 업로드 (multipart POST /api/v1/users/me/profile-image)
     *
     * content:// Uri는 파일 경로가 아니므로 bytes로 읽어 multipart part를 만든다.
     */
    suspend fun uploadProfileImage(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("이미지 파일을 읽을 수 없습니다"))

            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }

            val part = MultipartBody.Part.createFormData(
                "file",
                "profile.$extension",
                bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )

            val response = apiService.uploadProfileImage(part)

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("프로필 사진 업로드 실패 (${response.code()})"))
            }

            val body = response.body()
                ?: return@withContext Result.failure(Exception("프로필 사진 업로드 실패: 응답 본문 없음"))

            if (!body.success) {
                return@withContext Result.failure(
                    Exception(body.error?.message ?: "프로필 사진 업로드 실패")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}