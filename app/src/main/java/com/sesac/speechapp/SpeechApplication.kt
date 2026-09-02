package com.sesac.speechapp

import android.app.Application
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.RetrofitClient

/**
 * Application 클래스: RetrofitClient에 TokenManager 주입
 * (인터셉터 토큰 provider + P3-28 TokenAuthenticator 무음 재인증)
 */
class SpeechApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val tokenManager = TokenManager(this)
        RetrofitClient.initTokenManager(tokenManager)
    }
}