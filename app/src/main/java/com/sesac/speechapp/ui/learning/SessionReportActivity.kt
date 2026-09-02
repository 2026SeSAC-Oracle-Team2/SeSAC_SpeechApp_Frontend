package com.sesac.speechapp.ui.learning

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.R
import com.sesac.speechapp.data.remote.dto.session.FinishData
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.ActivitySessionReportBinding
import kotlinx.coroutines.launch

/**
 * P3-26 결과 화면 — POST /finish (스텁 2~3초 동기 대기) 후 표시.
 *
 * - AQ 큰 숫자 + 방사형 그래프 4축 + 피드백 6종 카드
 * - 방사형 데이터: 세션 턴(type별 score 평균) — 제출 응답에서 집계한
 *   ProblemActivity 채점 결과를 SessionFlowCache.turnScores로 전달받음.
 *   (백엔드에 세션 상세 GET API가 없어 클라 누적 방식 — 보고서에 기록)
 * - "완료" → 홈 복귀 (MainActivity로 CLEAR_TOP)
 */
class SessionReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"

        /** ProblemActivity가 제출 시 누적한 턴별 점수 (type별 평균용) */
        const val EXTRA_TURN_SCORES = "turn_scores" // ArrayList<Int> — score*100 아님, 그대로 정수/100
        const val EXTRA_TURN_TYPES = "turn_types"   // ArrayList<String>
    }

    private lateinit var binding: ActivitySessionReportBinding
    private lateinit var repository: SessionFlowRepository

    private var sessionId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SessionFlowRepository(this)
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)

        binding.btnDone.setOnClickListener { goHome() }
        binding.btnRetry.setOnClickListener { fetchReport() }

        fetchReport()
    }

    private fun fetchReport() {
        if (sessionId <= 0) {
            showError("세션 정보가 없어요")
            return
        }
        binding.containerLoading.visibility = View.VISIBLE
        binding.containerResult.visibility = View.GONE
        binding.btnDone.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val data = repository.finishSession(sessionId)
                render(data)
            } catch (e: Exception) {
                showError(e.message ?: "리포트 생성 실패")
            }
        }
    }

    private fun render(data: FinishData) {
        binding.containerLoading.visibility = View.GONE
        binding.containerResult.visibility = View.VISIBLE
        binding.btnDone.visibility = View.VISIBLE

        binding.tvAQ.text = data.sessionAQ.toString()

        // 방사형 그래프: 턴별 type+score 집계 (Intent로 전달받은 것)
        val scores = intent.getIntegerArrayListExtra(EXTRA_TURN_SCORES).orEmpty()
        val types = intent.getStringArrayListExtra(EXTRA_TURN_TYPES).orEmpty()
        binding.radarChart.setData(aggregateRadar(types, scores))

        // 피드백 6종 — 개별 nullable (null이면 카드 숨김)
        val f = data.feedbacks
        bindFeedback(binding.tvFeedbackListen, "알아듣기", f.listenFeedback)
        bindFeedback(binding.tvFeedbackNaming, "이름대기", f.namingFeedback)
        bindFeedback(binding.tvFeedbackShadowing, "따라말하기", f.shadowingFeedback)
        bindFeedback(binding.tvFeedbackSelfTalk, "스스로말하기", f.selfTalkFeedback)
        bindFeedback(binding.tvFeedbackTalk, "이야기하기", f.talkFeedback)
        bindFeedback(binding.tvFeedbackTotal, "종합", f.totalFeedback)
    }

    /** type별 score 평균 → 4축 (지시문 §3-4: LISTEN/NAMING/SHADOWING/SELF_TALK) */
    private fun aggregateRadar(types: List<String>, scores: List<Int>): List<RadarChartView.AxisData> {
        val buckets = mutableMapOf<String, MutableList<Int>>()
        types.zip(scores).forEach { (type, score) ->
            buckets.getOrPut(type) { mutableListOf() }.add(score)
        }
        fun avg(type: String): Float =
            buckets[type]?.average()?.toFloat() ?: 0f

        return listOf(
            RadarChartView.AxisData("알아듣기", avg("LISTEN")),
            RadarChartView.AxisData("이름대기", avg("NAMING")),
            RadarChartView.AxisData("따라말하기", avg("SHADOWING")),
            RadarChartView.AxisData("스스로말하기", avg("SELF_TALK")),
        )
    }

    private fun bindFeedback(view: View, label: String, text: String?) {
        if (text.isNullOrBlank()) {
            view.visibility = View.GONE
            return
        }
        (view as android.widget.TextView).text = "[$label]\n$text"
        view.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        binding.containerLoading.visibility = View.GONE
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun goHomeInternal() {
        // 플로우 정리 후 홈 복귀 (BottomNav 복원)
        SessionFlowCache.clear()
        val intent = Intent(this, com.sesac.speechapp.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun goHome() = goHomeInternal()
}