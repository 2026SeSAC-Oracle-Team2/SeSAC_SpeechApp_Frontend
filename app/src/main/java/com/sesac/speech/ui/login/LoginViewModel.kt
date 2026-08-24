package com.sesac.speech.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoginViewModel : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loginError = MutableLiveData<String?>(null)
    val loginError: LiveData<String?> = _loginError

    // TODO: Firebase Auth 연동 (P2-09)
    //  signInWithGoogle(idToken: String) -> POST /api/v1/auth/firebase

    fun onGoogleSignInClick() {
        _isLoading.value = true
        // Stub
    }
}
