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

/**
 * 프로필 탭 — D-8-C1 시안 profile.tsx 재작성 (기존 기능 흐름 유지).
 *
 * - 프로필 카드: 원형 사진 64dp + 카메라 배지(터치=기존 사진 변경 흐름 ProfileEditActivity)
 *   + 닉네임(터치=인라인 편집 → 기존 프로필 수정 화면 호출로 통일 — 지시문 §4-10) + Google 연결 문구
 * - 설정 섹션: 토글 2개 + 알림 시간 행 (시안 반영 — 동작은 기존 prefs 로직 없음 → 표시만,
 *   SettingActivity로 위임. 토글 리스너는 미연결 — 보고서 보류 항목)
 * - 계정 카드: 로그아웃(기존 로직) / 회원탈퇴(SettingActivity 경유 — 기존 로직 유지)
 * - tvLevel/btnSettings/progressBar: 시안에 없는 요소 — GONE 유지(바인딩 보존)
 */
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

        // 시안: 사진 카메라 배지 터치 = 기존 사진 변경 흐름 (ProfileEditActivity)
        binding.frameProfileImage.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
        }
        binding.ivCameraBadge.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
        }

        // 시안: 닉네임 터치 = 인라인 편집 → 기존 프로필 수정 화면 호출로 통일
        binding.tvNickname.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
        }
        binding.btnEditNickname.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
        }

        // 설정 진입(기존 기능 유지 — 시안에 버튼 없어 GONE, SettingActivity는 계정 카드에서 접근)
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingActivity::class.java))
        }

        // 시안 계정 카드: 로그아웃 행 (기존 로직 그대로)
        binding.btnLogout.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                AuthRepository(requireContext()).logout()
                val loginActivity = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(loginActivity)
            }
        }

        // 시안 계정 카드: 회원탈퇴 행 → SettingActivity 경유 (기존 withdraw 로직 유지)
        binding.btnWithdrawProfile.setOnClickListener {
            startActivity(Intent(requireContext(), SettingActivity::class.java))
        }

        // 알림 시간 행 → SettingActivity (기존 TimePicker 로직 위치 유지)
        binding.tvNotiTime.setOnClickListener {
            startActivity(Intent(requireContext(), SettingActivity::class.java))
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

            // 이메일 (시안: "Google 계정으로 연결됨" — 이메일 표시는 기존 기능 유지로 병기)
            binding.tvEmail.text = user.email?.ifEmpty {
                TokenManager(requireContext()).getUserEmail() ?: ""
            } ?: getString(R.string.profile_google_linked)

            // 레벨 뱃지 (null이면 숨김 — 시안에 없어 항상 GONE 방향)
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
        val cacheBuster = requireContext()
            .getSharedPreferences("speechapp_prefs", Context.MODE_PRIVATE)
            .getLong("profile_image_updated_at", 0L)
        val fullUrl = BuildConfig.SERVER_BASE_URL.trimEnd('/') +
            "/api/v1/users/me/profile-image?v=$cacheBuster"

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