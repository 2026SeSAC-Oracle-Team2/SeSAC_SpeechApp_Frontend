package com.sesac.speech.data.remote.api

import com.sesac.speech.data.remote.dto.ApiResponse
import com.sesac.speech.data.remote.dto.LoginResponse
import com.sesac.speech.data.remote.dto.VoiceUploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {

    /**
     * POST /api/v1/auth/firebase
     * Firebase ID Token -> 서버 JWT
     */
    @POST("api/v1/auth/firebase")
    suspend fun firebaseAuth(
        @Part("id_token") idToken: RequestBody
    ): Response<ApiResponse<LoginResponse>>

    /**
     * GET /api/v1/users/me
     */
    @GET("api/v1/users/me")
    suspend fun getMyProfile(): Response<ApiResponse<com.sesac.speech.data.remote.dto.UserDto>>

    /**
     * POST /api/v1/voice/upload
     */
    @Multipart
    @POST("api/v1/voice/upload")
    suspend fun uploadVoice(
        @Part file: MultipartBody.Part,
        @Part("session_id") sessionId: RequestBody? = null
    ): Response<ApiResponse<VoiceUploadResponse>>

    // TODO: 추가 API (P2-05 Backend 완료 후 연동)
    //  - GET /api/v1/sessions
    //  - GET /api/v1/sessions/{id}
    //  - GET /api/v1/sessions/{id}/report
    //  - GET /api/v1/dashboard
}
