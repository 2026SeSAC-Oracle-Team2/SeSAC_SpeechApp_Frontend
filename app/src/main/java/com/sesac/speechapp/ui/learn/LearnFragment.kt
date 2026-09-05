package com.sesac.speechapp.ui.learn

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.R
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.FragmentLearnBinding
import com.sesac.speechapp.ui.detail.DateFormats
import com.sesac.speechapp.ui.learning.LearningSessionLoadingActivity
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 홈 탭 (구 Learn) — 오늘의 학습 카드 + 홈 통계 2카드 + 실력 지표 방사형 그래프.
 * D-7 1.4: 오늘의 학습 → POST /sessions/today (EXTRA_THEMA 미전달 = today 분기)
 * D-8②b: 홈 통계 2카드(연속 학습/평균 점수) 실데이터 — GET /users/me/stats (05a §8.4).
 *         조회 실패 시 카드 유지 + 값 자리 "-" (로딩 실패가 화면을 깨지 않게).
 *         onResume 재조회 — 학습 완료 후 복귀 시 즉시 반영.
 * D-8③: 실력 지표 레이더는 홈에서 제거 (사용자 확정 — 최근 학습 결과 리스트로 대체).
 *         최근 학습 결과 = GET /users/me/sessions/history 최근 3개 (터치 → 세부 보고서).
 */
class LearnFragment : Fragment() {

    private var _binding: FragmentLearnBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: SessionFlowRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = SessionFlowRepository(requireContext())

        // 오늘의 학습 카드/버튼 탭 → 세션 로딩 화면 (스텁 2~3초 대기, D-7 1.4: /today 분기)
        binding.cardHero.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }
        binding.btnStart.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }

        // 최근 학습 결과 3개 — onResume에서 조회 (탭 재진입 반영)
        binding.containerRecent.removeAllViews()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
        loadRecent()
    }

    /**
     * D-8③ 홈 최근 학습 결과 — history 상위 3개 카드 (시안 home.tsx).
     * 터치 → 세부 보고서. 실패/빈 목록 시 빈 상태 문구 (카드 유지 — ②b 폴백 방침 동일).
     */
    private fun loadRecent() {
        lifecycleScope.launch {
            try {
                val data = repository.getSessionHistory()
                val items = data.sessions.take(3)
                binding.containerRecent.removeAllViews()
                binding.tvRecentEmpty.visibility =
                    if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.tvMoreHint.visibility =
                    if (data.sessions.size > 3) View.VISIBLE else View.GONE
                items.forEach { it_ ->
                    val row = layoutInflater.inflate(
                        R.layout.item_home_recent, binding.containerRecent, false
                    ) as LinearLayout
                    row.findViewById<TextView>(R.id.tvRecentTopic).text = it_.sessionName
                    row.findViewById<TextView>(R.id.tvRecentDate).text = DateFormats.toDashDate(it_.createdAt)
                    row.findViewById<TextView>(R.id.tvRecentAq).text = it_.aq.toString()
                    row.setOnClickListener {
                        startActivity(
                            Intent(requireContext(), com.sesac.speechapp.ui.detail.SessionDetailActivity::class.java)
                                .putExtra(com.sesac.speechapp.ui.detail.SessionDetailActivity.EXTRA_SESSION_ID, it_.sessionId)
                                .putExtra(com.sesac.speechapp.ui.detail.SessionDetailActivity.EXTRA_SESSION_NAME, it_.sessionName)
                                .putExtra(com.sesac.speechapp.ui.detail.SessionDetailActivity.EXTRA_CREATED_AT, it_.createdAt)
                        )
                    }
                    binding.containerRecent.addView(row)
                }
            } catch (e: Exception) {
                binding.containerRecent.removeAllViews()
                binding.tvRecentEmpty.visibility = View.VISIBLE
                binding.tvMoreHint.visibility = View.GONE
            }
        }
    }

    /**
     * D-8②b 홈 통계 2카드 — GET /users/me/stats.
     * 실패 시 카드는 유지하고 값 자리만 "-" (null 케이스와 동일 폴백).
     * deltaScore null이면 증감 TextView 숨김. 부호 포맷(+3.4/−2.1)은 클라 담당.
     */
    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val stats = repository.getMyStats()
                binding.containerStats.tvStreakValue.text =
                    getString(R.string.home_stat_streak_value).format(Locale.US, stats.streakDays)
                binding.containerStats.tvAvgValue.text = stats.avgScore?.let {
                    String.format(Locale.US, "%.1f", it)
                } ?: getString(R.string.home_stat_placeholder)
                if (stats.deltaScore != null) {
                    val delta = stats.deltaScore
                    val sign = if (delta >= 0) "+" else "\u2212" // −(U+2212) 음수 기호
                    binding.containerStats.tvDeltaValue.text = sign + String.format(Locale.US, "%.1f", kotlin.math.abs(delta))
                    binding.containerStats.tvDeltaValue.visibility = View.VISIBLE
                } else {
                    binding.containerStats.tvDeltaValue.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.containerStats.tvStreakValue.text = getString(R.string.home_stat_placeholder)
                binding.containerStats.tvAvgValue.text = getString(R.string.home_stat_placeholder)
                binding.containerStats.tvDeltaValue.visibility = View.GONE
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}