package com.sesac.speechapp.data.remote

import com.sesac.speechapp.BuildConfig
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.api.ApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private val BASE_URL = BuildConfig.SERVER_BASE_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /**
     * 저장된 access token을 Authorization 헤더로 첨부하는 인터셉터.
     * 토큰은 매 요청 시점에 TokenManager에서 다시 읽는다 (갱신 대응).
     *
     * TokenManager는 SpeechApplication.onCreate에서 initTokenManager로 주입한다.
     */
    @Volatile
    private var tokenProvider: (() -> String?)? = null

    private var tokenManagerRef: TokenManager? = null

    fun initTokenManager(tokenManager: TokenManager) {
        tokenManagerRef = tokenManager
        tokenProvider = { tokenManager.getAccessToken() }
    }

    /** 하위호환: 기존 provider 주입 방식 */
    fun initTokenProvider(provider: () -> String?) {
        tokenProvider = provider
    }

    private fun currentToken(): String? = tokenProvider?.invoke()

    /**
     * P3-28: refresh API 직접 호출 — TokenAuthenticator에서 401/403 시 사용.
     * Retrofit 순환의존을 피하기 위해 순수 OkHttp Request로 호출한다.
     * 응답: {"success":true,"data":{"accessToken":"...","expiresIn":900}}
     */
    private suspend fun refreshAccessToken(refreshToken: String): String? {
        val request = okhttp3.Request.Builder()
            .url(BASE_URL.trimEnd('/') + "/api/v1/auth/refresh")
            .post(
                """{"refreshToken":"$refreshToken"}"""
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            )
            .build()
        return try {
            val response = OkHttpClient.Builder().build().newCall(request).execute()
            val body = response.body?.string()
            response.close()
            if (body.isNullOrEmpty()) return null
            // 경량 파싱 (org.json — 안드로이드 내장)
            val json = org.json.JSONObject(body)
            if (!json.optBoolean("success", false)) return null
            json.optJSONObject("data")?.optString("accessToken")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private val client: OkHttpClient by lazy {
        val tm = tokenManagerRef
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                currentToken()?.let { token ->
                    if (token.isNotBlank()) {
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                }
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(loggingInterceptor)

        // TokenManager 주입 전 lazy 초기화 방지 — SpeechApplication.onCreate에서 주입 후 첫 호출
        if (tm != null) {
            builder.authenticator(TokenAuthenticator(tm, ::refreshAccessToken))
        }

        builder.build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}