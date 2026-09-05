package com.sesac.speechapp.ui.learning

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.R
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.ActivityLearningSessionLoadingBinding
import kotlinx.coroutines.launch

/**
 * P3-26 로딩 화면 — POST /api/v1/sessions/today(·theme) 수신까지 대기 + D-7 1.3 [시작] 게이트.
 *
 * - 마스코트 + "문제를 준비하고 있어요…" + 스피너
 * - 스텁 응답 2~3초 (자연 지연 — 로딩 UX 필수)
 * - 실패 시 재시도 버튼
 * - D-7 1.3: "로딩 완료!" 후 [시작] 버튼 → 마이크 권한 선제 확인(획득, 거절 시 폴백 안내)
 *   — 문제 생성 전 차단해 자원 낭비 방지. 기존 PermissionOnboarding과 연계 유지
 * - 성공 시 ProblemGuideActivity(턴1 가이드)로 sessionId 전달 (turns는 캐시 경유)
 */
class LearningSessionLoadingActivity : AppCompatActivity() {

    companion object {
        /** 로딩 성공 → ProblemGuideActivity 전달용 extras */
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_THEME = "theme"

        /** 진입 모드 — 미전달=오늘의 학습(today) / "CAFE"|"HOSPITAL"=테마별 학습(theme) */
        const val EXTRA_THEMA = "thema"
    }

    private lateinit var binding: ActivityLearningSessionLoadingBinding
    private lateinit var repository: SessionFlowRepository

    private var sessionId: Long = -1
    private var readyToStart = false

    /** 마이크 권한 런처 — onCreate 이전 등록 필수 (프로퍼티 등록 규약) */
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            goToGuide()
        } else {
            // 거절 시 폴백 안내 — 음성 문제 진입 차단 (자원 낭비 방지)
            binding.tvError.text = getString(R.string.mic_denied_fallback)
            binding.tvError.visibility = View.VISIBLE
            Toast.makeText(this, getString(R.string.mic_denied_fallback), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearningSessionLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SessionFlowRepository(this)

        binding.btnRetry.setOnClickListener { createSession() }
        binding.btnStart.setOnClickListener { onStartClicked() }
        binding.btnStart.visibility = View.GONE
        createSession()
    }

    private fun createSession() {
        binding.btnRetry.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tvMessage.text = getString(com.sesac.speechapp.R.string.loading_session)
        binding.spinner.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val thema = intent.getStringExtra(EXTRA_THEMA)
                val data = if (thema.isNullOrBlank()) {
                    repository.createSessionToday()
                } else {
                    repository.createSessionTheme(thema)
                }

                // ProblemGuideActivity로 전달 (turns는 크기 제한 임시 저장소 경유)
                SessionFlowCache.set(data)
                sessionId = data.sessionId

                // D-7 1.3: "로딩 완료!" + [시작] 버튼 (문제 화면 자동 진입 폐지)
                binding.spinner.visibility = View.GONE
                binding.tvMessage.text = getString(R.string.loading_done)
                binding.btnStart.visibility = View.VISIBLE
                readyToStart = true
            } catch (e: Exception) {
                binding.spinner.visibility = View.GONE
                binding.tvMessage.text = getString(com.sesac.speechapp.R.string.loading_session_fail)
                binding.tvError.text = e.message
                binding.tvError.visibility = View.VISIBLE
                binding.btnRetry.visibility = View.VISIBLE
            }
        }
    }

    /** [시작] — 마이크 권한 선제 확인 후 가이드 화면 진입 (06 §3, D-7 1.3) */
    private fun onStartClicked() {
        if (!readyToStart || sessionId <= 0) return
        val granted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            goToGuide()
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun goToGuide() {
        val intent = Intent(this, ProblemGuideActivity::class.java)
            .putExtra(ProblemGuideActivity.EXTRA_TURN_INDEX, 0)
        startActivity(intent)
        finish()
    }
}