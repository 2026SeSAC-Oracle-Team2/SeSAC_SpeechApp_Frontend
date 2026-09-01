package com.sesac.speechapp.data.remote.api

import com.sesac.speechapp.data.remote.dto.ApiResponse
import com.sesac.speechapp.data.remote.dto.FirebaseAuthRequest
import com.sesac.speechapp.data.remote.dto.LoginResponse
import com.sesac.speechapp.data.remote.dto.TokenRefreshRequest
import com.sesac.speechapp.data.remote.dto.UpdateProfileRequest
import com.sesac.speechapp.data.remote.dto.UserDto
import com.sesac.speechapp.data.remote.dto.VoiceUploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    /**
     * POST /api/v1/auth/firebase
     * Firebase ID Token → 서버 JWT
     */
    @POST("api/v1/auth/firebase")
    suspend fun firebaseAuth(
        @Body request: FirebaseAuthRequest
    ): Response<ApiResponse<LoginResponse>>

    /**
     * POST /api/v1/auth/refresh
     */
    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<ApiResponse<Map<String, Any>>>

    /**
     * POST /api/v1/auth/logout
     */
    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<ApiResponse<Void?>>

    /**
     * GET /api/v1/users/me
     */
    @GET("api/v1/users/me")
    suspend fun getMyProfile(): Response<ApiResponse<UserDto>>

    /**
     * PATCH /api/v1/users/me — 닉네임 등 프로필 수정
     */
    @PATCH("api/v1/users/me")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse<UserDto>>

    /**
     * POST /api/v1/users/me/profile-image — 프로필 사진 업로드 (multipart)
     */
    @Multipart
    @POST("api/v1/users/me/profile-image")
    suspend fun uploadProfileImage(
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<UserDto>>

    /**
     * DELETE /api/v1/users/me — 회원탈퇴
     */
    @DELETE("api/v1/users/me")
    suspend fun withdraw(): Response<ApiResponse<Void?>>

    /**
     * POST /api/v1/voice/upload
     */
    @Multipart
    @POST("api/v1/voice/upload")
    suspend fun uploadVoice(
        @Part file: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("contentType") contentType: RequestBody,
        @Part("sessionId") sessionId: RequestBody? = null
    ): Response<ApiResponse<VoiceUploadResponse>>
}
