package com.sesac.speechapp.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * JWT 토큰 관리 (SharedPreferences)
 * TODO: Phase 3에서 EncryptedSharedPreferences로 마이그레이션
 */
class TokenManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_UUID = "user_uuid"
        private const val KEY_USER_EMAIL = "user_email"
    }

    // ─── Access Token ─────────────────────────────────────────

    fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    // ─── Refresh Token ────────────────────────────────────────

    fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    // ─── User Info ────────────────────────────────────────────

    fun saveUserInfo(uuid: String, email: String) {
        prefs.edit()
            .putString(KEY_USER_UUID, uuid)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun getUserUuid(): String? = prefs.getString(KEY_USER_UUID, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    // ─── Clear ────────────────────────────────────────────────

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = getAccessToken() != null
}