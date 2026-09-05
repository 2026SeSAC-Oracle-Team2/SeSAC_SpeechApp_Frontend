package com.sesac.speechapp.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sesac.speechapp.R
import com.sesac.speechapp.databinding.FragmentDashboardBinding
import com.sesac.speechapp.ui.detail.SessionDetailActivity
import com.sesac.speechapp.ui.detail.DateFormats
import com.sesac.speechapp.ui.history.SessionHistoryAdapter
import com.sesac.speechapp.ui.history.SessionHistory
import com.sesac.speechapp.ui.learning.RadarChartView
import com.sesac.speechapp.data.repository.SessionFlowRepository
import kotlinx.coroutines.launch

/**
 * 대시보드 탭 — D-7 3.2 실데이터화 (stub 제거).
 *
 * - 방사형: GET /users/me/scores → 대표점수 4축 (null이면 0 표시 + 폴백 문구) — AQ는 큰 숫자 표시
 * - 이력 카드: GET /users/me/sessions/history → RecyclerView (sessionName / YYYY.mm.dd / AQ nn점)
 *   빈 목록 시 "아직 학습 기록이 없어요" 빈 상태
 * - 카드 터치 → 세부 보고서 화면(3.3)으로 sessionId 전달
 * - 탭 재진입 시마다 재조회 (학습 완료 → [홈으로] → 대시보드 반영 — D-7 3.5)
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val historyAdapter = SessionHistoryAdapter()
    private lateinit var repository: SessionFlowRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = SessionFlowRepository(requireContext())

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
        historyAdapter.onItemClick = { item ->
            val intent = Intent(requireContext(), SessionDetailActivity::class.java)
                .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, item.id)
                .putExtra(SessionDetailActivity.EXTRA_SESSION_NAME, item.topic)
                .putExtra(SessionDetailActivity.EXTRA_CREATED_AT, item.createdAt)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // D-7 3.5: 탭 재진입 시 재조회 — 새 세션이 카드에 뜨는 흐름 유지
        loadDashboard()
    }

    private fun loadDashboard() {
        // 방사형: 대표점수 (null 폴백 — §8.1)
        lifecycleScope.launch {
            try {
                val scores = repository.getMyScores()
                binding.radarDashboard.setData(
                    listOf(
                        RadarChartView.AxisData(getString(R.string.metric_listen), scores.listen?.toFloat() ?: 0f),
                        RadarChartView.AxisData(getString(R.string.metric_naming), scores.naming?.toFloat() ?: 0f),
                        RadarChartView.AxisData(getString(R.string.metric_shadowing), scores.shadowing?.toFloat() ?: 0f),
                        RadarChartView.AxisData(getString(R.string.metric_selftalk), scores.selfTalk?.toFloat() ?: 0f),
                    )
                )
                binding.tvAqBig.text = scores.userAq?.toString() ?: "0"
                // 전부 null → "학습을 시작해보세요" 폴백 문구
                val allNull = scores.listen == null && scores.naming == null &&
                    scores.shadowing == null && scores.selfTalk == null
                binding.tvAqFallback.visibility = if (allNull) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                binding.tvAqFallback.visibility = View.VISIBLE
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }

        // 이력 카드 (§8.2 — stubList 제거, 실데이터)
        lifecycleScope.launch {
            try {
                val data = repository.getSessionHistory()
                val items = data.sessions.map {
                    SessionHistory(
                        id = it.sessionId,
                        date = DateFormats.toDashDate(it.createdAt),
                        topic = it.sessionName,
                        score = it.aq,
                        createdAt = it.createdAt,
                    )
                }
                historyAdapter.submitList(items)
                // 빈 목록 → 빈 상태 문구
                binding.tvHistoryEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                historyAdapter.submitList(emptyList())
                binding.tvHistoryEmpty.visibility = View.VISIBLE
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}