package com.sesac.speechapp.data.remote

import android.content.Context
import coil.ImageLoader
import okhttp3.OkHttpClient

/**
 * 인증이 필요한 이미지(프로필 사진 등)를 로드하기 위한 Coil ImageLoader.
 *
 * RetrofitClient와 동일한 방식으로 매 요청 시점에 TokenManager에서 access token을
 * 읽어 Authorization 헤더를 첨부한다.
 *
 * 사용법:
 *   imageView.load(url, imageLoader = AuthImageLoader.get(context))
 */
object AuthImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    private fun create(context: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = TokenManager(context).getAccessToken()
                val request = chain.request().newBuilder().apply {
                    if (!token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }.build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .build()
    }
}