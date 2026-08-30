package com.sesac.speechapp.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sesac.speechapp.data.remote.RetrofitClient
import com.sesac.speechapp.data.remote.dto.UserDto
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = RetrofitClient.apiService

    private val _profile = MutableLiveData<UserDto?>()
    val profile: LiveData<UserDto?> = _profile

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * GET /api/v1/users/me — 프로필 조회
     *
     * 참고: 백엔드 UserService 버그로 서버가 일시적으로 실패할 수 있으나,
     * 프론트는 정상 응답을 가정하고 작성한다. 실패 시 에러 상태만 전달.
     */
    fun getMyProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getMyProfile()

                if (!response.isSuccessful) {
                    _error.value = "프로필 조회 실패 (${response.code()})"
                    return@launch
                }

                val body = response.body()
                if (body == null || !body.success) {
                    _error.value = body?.error?.message ?: "프로필 조회 실패"
                    return@launch
                }

                _profile.value = body.data
            } catch (e: Exception) {
                _error.value = e.message ?: "프로필 조회 중 오류가 발생했습니다"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 에러 메시지 소비 (중복 표시 방지) */
    fun consumeError() {
        _error.value = null
    }
}