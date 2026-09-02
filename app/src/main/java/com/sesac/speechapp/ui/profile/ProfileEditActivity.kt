package com.sesac.speechapp.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.sesac.speechapp.BuildConfig
import com.sesac.speechapp.R
import com.sesac.speechapp.data.remote.AuthImageLoader
import com.sesac.speechapp.data.remote.RetrofitClient
import com.sesac.speechapp.data.repository.UserRepository
import com.sesac.speechapp.databinding.ActivityProfileEditBinding
import kotlinx.coroutines.launch

/**
 * P3-27 프로필 수정 (ProfileFragment 탭 진입).
 *
 * - 프로필 사진: 탭 → 갤러리 선택 → multipart POST /api/v1/users/me/profile-image
 * - 닉네임: PATCH /api/v1/users/me — 유효성 2~20자 + 공백만 금지
 * - 저장 성공 → finish (ProfileFragment는 onResume에서 갱신)
 */
class ProfileEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding
    private lateinit var userRepository: UserRepository

    private var selectedImageUri: Uri? = null
    private var currentNickname: String? = null

    // 갤러리 선택
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivProfile.load(uri) { crossfade(true) }
        }
    }

    // 권한 요청 (READ_MEDIA_IMAGES 33+, READ_EXTERNAL_STORAGE 32-)
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) galleryLauncher.launch("image/*")
        else toast("갤러리 권한이 필요해요")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)
        loadCurrentProfile()

        binding.frameProfileImage.setOnClickListener { pickImage() }
        binding.cameraBadge.setOnClickListener { pickImage() }

        binding.btnSave.setOnClickListener { save() }
    }

    private fun loadCurrentProfile() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMyProfile()
                val user = response.body()?.takeIf { it.success }?.data
                    ?: return@launch
                currentNickname = user.nickname
                binding.etNickname.setText(user.nickname ?: "")

                // 기존 사진 표시 (키가 있을 때만 전용 엔드포인트)
                val key = user.profileImageUrl
                if (!key.isNullOrBlank()) {
                    val url = BuildConfig.SERVER_BASE_URL.trimEnd('/') + "/api/v1/users/me/profile-image"
                    binding.ivProfile.load(url, AuthImageLoader.get(this@ProfileEditActivity)) {
                        placeholder(R.drawable.ic_person_24)
                        error(R.drawable.ic_person_24)
                        crossfade(true)
                    }
                } else {
                    binding.ivProfile.setImageResource(R.drawable.ic_person_24)
                }
            } catch (_: Exception) {
                // 조회 실패해도 편집은 진행 가능
            }
        }
    }

    private fun pickImage() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            galleryLauncher.launch("image/*")
        } else {
            mediaPermissionLauncher.launch(permission)
        }
    }

    private fun save() {
        val nickname = binding.etNickname.text?.toString()?.trim() ?: ""

        // 유효성: 2~20자, 공백만 금지
        if (nickname.length !in 2..20) {
            binding.tilNickname.error = getString(R.string.profile_nickname_error)
            return
        }

        val imageChanged = selectedImageUri != null
        val nicknameChanged = nickname != currentNickname
        if (!imageChanged && !nicknameChanged) {
            finish() // 변경 없음
            return
        }

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            var ok = true
            if (nicknameChanged) {
                userRepository.updateNickname(nickname)
                    .onFailure { ok = false; toast(it.message ?: "닉네임 저장 실패") }
            }
            if (ok && imageChanged) {
                userRepository.uploadProfileImage(selectedImageUri!!)
                    .onFailure { ok = false; toast(it.message ?: "사진 업로드 실패") }
            }

            binding.progressBar.visibility = android.view.View.GONE
            binding.btnSave.isEnabled = true

            if (ok) {
                toast(getString(R.string.profile_saved))
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}