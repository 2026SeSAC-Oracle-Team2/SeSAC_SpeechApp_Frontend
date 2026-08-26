package com.sesac.speechapp.data.remote.api

import com.sesac.speechapp.data.remote.dto.ApiResponse
import com.sesac.speechapp.data.remote.dto.FirebaseAuthRequest
import com.sesac.speechapp.data.remote.dto.LoginResponse
import com.sesac.speechapp.data.remote.dto.TokenRefreshRequest
import com.sesac.speechapp.data.remote.dto.UserDto
import com.sesac.speechapp.data.remote.dto.VoiceUploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
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
     * POST /api/v1/voice/upload
     */
    @Multipart
    @POST("api/v1/voice/upload")
    suspend fun uploadVoice(
        @Part file: MultipartBody.Part,
        @Part("session_id") sessionId: RequestBody? = null
    ): Response<ApiResponse<VoiceUploadResponse>>
}
