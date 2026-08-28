package com.sesac.speechapp.data.remote

import com.sesac.speechapp.BuildConfig
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.api.ApiService
import okhttp3.OkHttpClient
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
     * TokenManager는 SpeechApplication.onCreate에서 initTokenProvider로 주입한다.
     */
    @Volatile
    private var tokenProvider: (() -> String?)? = null

    fun initTokenProvider(provider: () -> String?) {
        tokenProvider = provider
    }

    private fun currentToken(): String? = tokenProvider?.invoke()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
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
            .build()
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