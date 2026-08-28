package com.sesac.speechapp

import android.app.Application
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.RetrofitClient

/**
 * Application 클래스: RetrofitClient에 토큰 provider 주입
 */
class SpeechApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val tokenManager = TokenManager(this)
        RetrofitClient.initTokenProvider { tokenManager.getAccessToken() }
    }
}