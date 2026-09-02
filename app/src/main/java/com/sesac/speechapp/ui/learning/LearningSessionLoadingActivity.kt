package com.sesac.speechapp.ui.learning

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.ActivityLearningSessionLoadingBinding
import kotlinx.coroutines.launch

/**
 * P3-26 로딩 화면 — POST /api/v1/sessions/v2 수신까지 대기.
 *
 * - 마스코트 + "문제를 준비하고 있어요…" + 스피너
 * - 스텁 응답 2~3초 (자연 지연 — 로딩 UX 필수)
 * - 실패 시 재시도 버튼
 * - 성공 시 ProblemActivity로 sessionId+turns 전달
 */
class LearningSessionLoadingActivity : AppCompatActivity() {

    companion object {
        /** 로딩 성공 → ProblemActivity 전달용 extras */
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_THEME = "theme"
    }

    private lateinit var binding: ActivityLearningSessionLoadingBinding
    private lateinit var repository: SessionFlowRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearningSessionLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SessionFlowRepository(this)

        binding.btnRetry.setOnClickListener { createSession() }
        createSession()
    }

    private fun createSession() {
        binding.btnRetry.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tvMessage.text = getString(com.sesac.speechapp.R.string.loading_session)
        binding.spinner.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val data = repository.createSession()

                // ProblemActivity로 전달 (turns는 크기 제한 임시 저장소 경유)
                SessionFlowCache.set(data)
                val intent = Intent(this@LearningSessionLoadingActivity, ProblemActivity::class.java).apply {
                    putExtra(EXTRA_SESSION_ID, data.sessionId)
                    putExtra(EXTRA_THEME, data.theme)
                    // ProblemActivity는 세션 전체 턴을 캐시에서 읽음 (Bundle 직렬화 회피)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                binding.spinner.visibility = View.GONE
                binding.tvMessage.text = getString(com.sesac.speechapp.R.string.loading_session_fail)
                binding.tvError.text = e.message
                binding.tvError.visibility = View.VISIBLE
                binding.btnRetry.visibility = View.VISIBLE
            }
        }
    }
}