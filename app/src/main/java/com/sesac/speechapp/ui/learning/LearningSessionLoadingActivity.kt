package com.sesac.speechapp.ui.learning

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.R
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.ActivityLearningSessionLoadingBinding
import kotlinx.coroutines.launch

/**
 * 세션 로딩 화면 — D-8-C1 시안 learn.$themeId intro 재작성.
 *
 * - Loading(오리 + 메시지) → API 응답 수신 후 즉시 "로딩 완료!" + 오리 + "{세션명} 준비가
 *   끝났어요." + [시작] (시안의 고정 1.2초 타이머 폐기 — API 응답 대기가 기획 우선 06 v1.7)
 * - D-7 1.3: [시작] → 마이크 권한 선제 확인 → ProblemGuideActivity
 * - 오리 크기: 로딩 96dp → 완료 120dp (코드 전환)
 * - 세션명: EXTRA_THEMA에서 라벨 산출 (today=오늘의 학습 / CAFE·HOSPITAL은 ProblemActivity
 *   TYPE_LABELS 톤 근사 — 서버 sessionName은 턴 응답에 없어 클라 라벨 사용, 보고서 표기)
 */
class LearningSessionLoadingActivity : AppCompatActivity() {

    companion object {
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
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            goToGuide()
        } else {
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
        // D-8-C2 C-6: 로딩 화면 X = 세션 이탈 (세션 시작 전 — 데이터 소실 없어 즉시 finish 허용.
        // 이탈 확인 팝업은 문항 단계(ProblemActivity)에서만)
        binding.btnSessionClose.setOnClickListener { finish() }
        // C-6: n/total — 총 항목 수는 세션 생성 후 확정이라 우선 0/{기획 기본 8} 형태 표시
        binding.tvLoadingProgress.text = getString(R.string.progress_turn_fmt, 0, 8)
        createSession()
    }

    private fun createSession() {
        binding.btnRetry.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tvMessage.visibility = View.VISIBLE
        binding.tvMessage.text = getString(R.string.loading_session)
        binding.spinner.visibility = View.VISIBLE
        binding.tvReadyTitle.visibility = View.GONE
        binding.tvReadySession.visibility = View.GONE
        binding.imgMascotDuck.layoutParams = binding.imgMascotDuck.layoutParams.apply {
            width = resources.getDimensionPixelSize(R.dimen.duck_loading)
            height = resources.getDimensionPixelSize(R.dimen.duck_loading)
        }

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

                // D-8-C1 시안 완료 상태: "로딩 완료!" + "{세션명} 준비가 끝났어요."
                binding.spinner.visibility = View.GONE
                binding.tvMessage.visibility = View.GONE
                binding.imgMascotDuck.layoutParams = binding.imgMascotDuck.layoutParams.apply {
                    width = resources.getDimensionPixelSize(R.dimen.duck_done)
                    height = resources.getDimensionPixelSize(R.dimen.duck_done)
                }
                binding.tvReadyTitle.visibility = View.VISIBLE
                binding.tvReadySession.text =
                    getString(R.string.loading_ready_fmt, sessionLabel(thema))
                binding.tvReadySession.visibility = View.VISIBLE
                binding.btnStart.visibility = View.VISIBLE
                readyToStart = true
            } catch (e: Exception) {
                binding.spinner.visibility = View.GONE
                binding.tvMessage.text = getString(R.string.loading_session_fail)
                binding.tvError.text = e.message
                binding.tvError.visibility = View.VISIBLE
                binding.btnRetry.visibility = View.VISIBLE
            }
        }
    }

    /** 세션명 라벨 — thema 코드 → 화면 표기 (서버 sessionName은 턴 데이터에 미포함) */
    private fun sessionLabel(thema: String?): String = when (thema?.uppercase()) {
        "CAFE" -> getString(R.string.theme_cafe_title)
        "HOSPITAL" -> getString(R.string.theme_hospital_title)
        else -> getString(R.string.session_type_today)
    }

    /** [시작] — 마이크 권한 선제 확인 후 가이드 화면 진입 (06 §3, D-7 1.3) */
    private fun onStartClicked() {
        if (!readyToStart || sessionId <= 0) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            goToGuide()
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * D-8-C2 A-6: ProblemActivity가 가이드 단계를 흡수 — 로딩 → ProblemActivity 직행.
     * (기존: 로딩 → ProblemGuideActivity → ProblemActivity 3단 activity 이동 = 끊김 원인)
     */
    private fun goToGuide() {
        val intent = Intent(this, ProblemActivity::class.java)
            .putExtra(ProblemActivity.EXTRA_SESSION_ID, sessionId)
            .putExtra(ProblemActivity.EXTRA_TURN_INDEX, 0)
        startActivity(intent)
        finish()
    }
}