package com.sesac.speechapp.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.load
import com.sesac.speechapp.BuildConfig
import com.sesac.speechapp.R
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.AuthImageLoader
import com.sesac.speechapp.data.repository.AuthRepository
import com.sesac.speechapp.databinding.FragmentProfileBinding
import com.sesac.speechapp.ui.login.LoginActivity
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 설정 버튼 → SettingActivity
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingActivity::class.java))
        }

        // P3-27: 닉네임/프로필사진 영역 탭 → ProfileEditActivity
        binding.frameProfileImage.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
        }
        binding.tvNickname.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
        }

        // 로그아웃
        binding.btnLogout.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                AuthRepository(requireContext()).logout()
                val loginActivity = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(loginActivity)
            }
        }

        // 프로필 로드
        viewModel.getMyProfile()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.profile.observe(viewLifecycleOwner) { user ->
            if (user == null) return@observe

            // 닉네임 (없으면 이메일 앞부분으로 대체)
            binding.tvNickname.text = user.nickname
                ?: TokenManager(requireContext()).getUserEmail()?.substringBefore("@")
                ?: "사용자"

            // 이메일
            binding.tvEmail.text = user.email?.ifEmpty {
                TokenManager(requireContext()).getUserEmail() ?: ""
            }

            // 레벨 뱃지 (null이면 숨김)
            val level = user.level
            if (level != null && level > 0) {
                binding.tvLevel.visibility = View.VISIBLE
                binding.tvLevel.text = "Lv. $level"
            } else {
                binding.tvLevel.visibility = View.GONE
            }

            loadProfileImage(user.profileImageUrl)
        }

        viewModel.error.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.consumeError()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading == true) View.VISIBLE else View.GONE
        }
    }

    /**
     * 프로필 사진 로드 — GET api/v1/users/me/profile-image (Authorization 필요)
     *
     * 서버는 스트리밍 전용 엔드포인트(/api/v1/users/me/profile-image)로 이미지를 내려준다.
     * 응답의 profileImageUrl은 Object Storage **키**({uuid}/profile.png)이지
     * HTTP URL이 아니다. → 항상 전용 엔드포인트를 호출하고, 키 존재 여부만 판단한다.
     */
    private fun loadProfileImage(profileImageUrl: String?) {
        if (profileImageUrl.isNullOrBlank()) {
            binding.ivProfile.setImageResource(R.drawable.ic_person_24)
            binding.spinnerProfile.visibility = View.GONE
            return
        }

        // cache buster: revision timestamp from SharedPreferences
        // (before edit: constant -> cache hit; after edit: changed -> fresh load)
        val cacheBuster = requireContext()
            .getSharedPreferences("speechapp_prefs", Context.MODE_PRIVATE)
            .getLong("profile_image_updated_at", 0L)
        val fullUrl = BuildConfig.SERVER_BASE_URL.trimEnd('/') +
            "/api/v1/users/me/profile-image?v=$cacheBuster"

        // 스피너 ON → 이미지 로드 성공/실패 시 OFF
        binding.spinnerProfile.visibility = View.VISIBLE

        binding.ivProfile.load(fullUrl, AuthImageLoader.get(requireContext())) {
            placeholder(R.drawable.ic_person_24)
            error(R.drawable.ic_person_24)
            crossfade(true)
            listener(
                onStart = { binding.spinnerProfile.visibility = View.VISIBLE },
                onSuccess = { _, _ -> binding.spinnerProfile.visibility = View.GONE },
                onError = { _, _ -> binding.spinnerProfile.visibility = View.GONE }
            )
        }
    }

    private fun resolveImageUrl(path: String): String {
        // 절대 URL이면 그대로 사용
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        // 상대경로 → BASE_URL 결합
        val base = BuildConfig.SERVER_BASE_URL.trimEnd('/')
        val relative = if (path.startsWith("/")) path else "/$path"
        return base + relative
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면(탈퇴/사진 변경)에서 돌아오면 갱신
        viewModel.getMyProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}