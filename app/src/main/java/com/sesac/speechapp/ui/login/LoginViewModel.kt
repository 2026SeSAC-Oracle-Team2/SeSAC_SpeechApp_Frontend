package com.sesac.speechapp.ui.login

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sesac.speechapp.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application.applicationContext)

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    val googleSignInIntent: Intent
        get() = authRepository.getSignInIntent()

    init {
        _loginState.value = LoginState.Idle
    }

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = authRepository.handleSignInResult(data)
            val loginResult = result.getOrNull()
            _loginState.value = when {
                loginResult != null -> LoginState.Success(
                    isNewUser = loginResult.isNewUser,
                    nickname = loginResult.nickname
                )
                else -> LoginState.Error(result.exceptionOrNull()?.message ?: "로그인 실패")
            }
        }
    }

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(
        val isNewUser: Boolean = false,
        val nickname: String? = null,
    ) : LoginState()
    data class Error(val message: String) : LoginState()
}