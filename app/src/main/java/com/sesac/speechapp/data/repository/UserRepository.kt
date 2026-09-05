package com.sesac.speechapp.data.repository

import android.content.Context
import android.net.Uri
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.RetrofitClient
import com.sesac.speechapp.data.remote.dto.SurveyRequest
import com.sesac.speechapp.data.remote.dto.SurveyResponse
import com.sesac.speechapp.data.remote.dto.TagsResponse
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
     * 프로필 일괄 저장 (PATCH /api/v1/users/me — D-6 가입 Step2)
     *
     * 가입 다단계 플로우에서 Step1(닉네임·사진) 완료 후 성별·생년월일·취미·태그를
     * 전체 필드 일괄로 전송한다 (05a v1.6 §2: 부분 업데이트 — null은 기존값 유지).
     * tagIds는 전량 교체 계약 — 항상 현재 선택 전체를 전송.
     */
    suspend fun updateProfileFull(request: UpdateProfileRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateProfile(request)

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("프로필 저장 실패 (${response.code()})")
                    )
                }

                val body = response.body()
                    ?: return@withContext Result.failure(
                        Exception("프로필 저장 실패: 응답 본문 없음")
                    )

                if (!body.success) {
                    return@withContext Result.failure(
                        Exception(body.error?.message ?: "프로필 저장 실패")
                    )
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 태그 마스터 15종 조회 (GET /api/v1/users/me/tags — D-6 가입 Step2 버블용)
     */
    suspend fun getTags(): Result<TagsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTags()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("태그 조회 실패 (${response.code()})"))
            }

            val body = response.body()
                ?: return@withContext Result.failure(Exception("태그 조회 실패: 응답 본문 없음"))

            if (!body.success) {
                return@withContext Result.failure(
                    Exception(body.error?.message ?: "태그 조회 실패")
                )
            }

            val data = body.data
                ?: return@withContext Result.failure(Exception("태그 조회 실패: 데이터 없음"))

            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 가입 설문 접수 (POST /api/v1/users/me/survey — D-6 설문 화면)
     *
     * 산출 주체 = 서버 — 클라는 answers 원문(문항 순서대로 1~5 정수 5개)만 전송하고
     * 응답의 환산 userAq만 신뢰한다 (06 v1.7 §5.2). 재응답 허용 — 클라는 항상 제출.
     */
    suspend fun submitSurvey(answers: List<Int>): Result<SurveyResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.submitSurvey(SurveyRequest(answers))

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("설문 제출 실패 (${response.code()})")
                    )
                }

                val body = response.body()
                    ?: return@withContext Result.failure(
                        Exception("설문 제출 실패: 응답 본문 없음")
                    )

                if (!body.success) {
                    return@withContext Result.failure(
                        Exception(body.error?.message ?: "설문 제출 실패")
                    )
                }

                val data = body.data
                    ?: return@withContext Result.failure(
                        Exception("설문 제출 실패: 데이터 없음")
                    )

                Result.success(data)
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