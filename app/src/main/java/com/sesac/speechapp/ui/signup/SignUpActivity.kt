package com.sesac.speechapp.ui.signup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.sesac.speechapp.databinding.ActivitySignUpBinding

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private val viewModel: SignUpViewModel by viewModels()

    /** 갤러리에서 선택한 프로필 사진 (content:// Uri, 미선택 시 null) */
    private var selectedImageUri: Uri? = null

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivProfilePreview.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // 프로필 사진 선택 (카메라 오버레이 또는 원형 미리보기 탭)
        binding.cameraOverlay.setOnClickListener { launchImagePicker() }
        binding.ivProfilePreview.setOnClickListener { launchImagePicker() }

        // 닉네임 입력 변화 → 에러 해제 + 완료 버튼 활성화
        binding.etNickname.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                binding.tilNickname.error = null
                binding.tilNickname.isErrorEnabled = false
                binding.btnComplete.isEnabled = !s.isNullOrBlank()
            }
        })

        // 완료
        binding.btnComplete.setOnClickListener {
            val nickname = binding.etNickname.text?.toString().orEmpty()
            viewModel.complete(nickname, selectedImageUri)
        }

        observeState()
    }

    private fun launchImagePicker() {
        imagePicker.launch("image/*")
    }

    private fun observeState() {
        viewModel.signUpState.observe(this) { state ->
            when (state) {
                is SignUpState.Idle -> {
                    setUiEnabled(true)
                }
                is SignUpState.Loading -> {
                    setUiEnabled(false)
                    binding.btnComplete.text = "저장 중..."
                }
                is SignUpState.ValidationError -> {
                    setUiEnabled(true)
                    binding.btnComplete.text = "완료"
                    binding.tilNickname.error = state.message
                    binding.tilNickname.isErrorEnabled = true
                }
                is SignUpState.Error -> {
                    setUiEnabled(true)
                    binding.btnComplete.text = "완료"
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is SignUpState.PartialSuccess -> {
                    // 닉네임은 저장됨, 사진 업로드만 실패 → 안내 후 다음 단계 진행
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    navigateToProfileStep()
                }
                is SignUpState.Success -> navigateToProfileStep()
            }
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.btnComplete.isEnabled = enabled && !binding.etNickname.text.isNullOrBlank()
        binding.cameraOverlay.isEnabled = enabled
        binding.ivProfilePreview.isEnabled = enabled
    }

    /**
     * D-6 가입 다단계화: Step1(닉네임+사진) 완료 → Step2(성별·생년월일·취미·태그)로 이동.
     * 닉네임은 Step2 PATCH에서 전체 필드 일괄 전송에 사용 — extra로 전달.
     */
    private fun navigateToProfileStep() {
        val nickname = binding.etNickname.text?.toString()?.trim().orEmpty()
        val intent = Intent(this, SignUpProfileStepActivity::class.java).apply {
            putExtra(SignUpProfileStepActivity.EXTRA_NICKNAME, nickname)
        }
        startActivity(intent)
        finish()
    }
}