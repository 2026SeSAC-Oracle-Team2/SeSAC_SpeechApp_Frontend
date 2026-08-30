package com.sesac.speechapp.ui.signup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sesac.speechapp.data.repository.UserRepository
import kotlinx.coroutines.launch

class SignUpViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository(application.applicationContext)

    private val _signUpState = MutableLiveData<SignUpState>(SignUpState.Idle)
    val signUpState: LiveData<SignUpState> = _signUpState

    /**
     * 닉네임 검증 → 저장 → (선택) 프로필 사진 업로드
     *
     * - 닉네임 필수, 공백만 입력 불가, 최대 20자
     * - 사진 업로드 실패는 닉네임 저장 성공 시 전체 실패로 보지 않는다 (PartialSuccess)
     */
    fun complete(nickname: String, imageUri: Uri?) {
        val trimmed = nickname.trim()

        // 필수 + 공백만 입력 불가 (trim 후 빈 문자열이면 공백만 입력된 것)
        if (trimmed.isEmpty()) {
            _signUpState.value = SignUpState.ValidationError("닉네임을 입력하세요")
            return
        }
        if (trimmed.length > MAX_NICKNAME_LENGTH) {
            _signUpState.value = SignUpState.ValidationError("닉네임은 최대 ${MAX_NICKNAME_LENGTH}자까지 가능합니다")
            return
        }

        viewModelScope.launch {
            _signUpState.value = SignUpState.Loading

            // 1) 닉네임 저장
            val nicknameResult = userRepository.updateNickname(trimmed)
            if (nicknameResult.isFailure) {
                _signUpState.value = SignUpState.Error(
                    nicknameResult.exceptionOrNull()?.message ?: "회원가입 실패"
                )
                return@launch
            }

            // 2) 프로필 사진 업로드 (선택) — 실패해도 닉네임 저장 성공이면 진행
            if (imageUri != null) {
                val imageResult = userRepository.uploadProfileImage(imageUri)
                if (imageResult.isFailure) {
                    _signUpState.value = SignUpState.PartialSuccess(
                        "프로필 사진 업로드에 실패했어요. 나중에 설정에서 다시 시도할 수 있어요."
                    )
                    return@launch
                }
            }

            _signUpState.value = SignUpState.Success
        }
    }

    companion object {
        const val MAX_NICKNAME_LENGTH = 20
    }
}

sealed class SignUpState {
    object Idle : SignUpState()
    object Loading : SignUpState()
    data class ValidationError(val message: String) : SignUpState()
    data class Error(val message: String) : SignUpState()
    data class PartialSuccess(val message: String) : SignUpState()
    object Success : SignUpState()
}