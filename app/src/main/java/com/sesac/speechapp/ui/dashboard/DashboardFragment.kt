package com.sesac.speechapp.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
 * 기록 탭 — D-7 3.2 실데이터화 + D-8-C1 시안 records.tsx 재작성.
 *
 * - (2) AI 대화 요약 배너: 최근 세션의 talk 피드백 — API에 배너 전용 필드가 없어
 *   기존 기획 문구(strings_d8c dash_ai_banner_d8c) 유지 (기획 우선 적용).
 * - (3) AQ 카드: 대표점수 4축 방사형 + AQ 큰 숫자(40sp) + 4지표 2열 칩
 *   (지표명 muted + "점수 / 만점" bold — RadarChartView 데이터에서 병행 표기)
 * - (4) 지난 보고서: GET /users/me/sessions/history → RecyclerView
 *   행 = [테마 제목 / 날짜 · AQ n | chevron] → 세부 보고서
 * - 탭 재진입 시마다 재조회 (D-7 3.5)
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
        loadDashboard()
    }

    private fun loadDashboard() {
        // 방사형 + AQ 큰 숫자 + 4지표 칩 (null 폴백 — §8.1)
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
                val allNull = scores.listen == null && scores.naming == null &&
                    scores.shadowing == null && scores.selfTalk == null
                binding.tvAqFallback.visibility = if (allNull) View.VISIBLE else View.GONE

                // 4지표 2열 칩: 지표명 + 점수/만점 (§8.1 대표점수 100 환산 아님 — 원 점수 표기)
                bindChip(binding.chipListen, getString(R.string.metric_listen), scores.listen)
                bindChip(binding.chipNaming, getString(R.string.metric_naming), scores.naming)
                bindChip(binding.chipShadowing, getString(R.string.metric_shadowing), scores.shadowing)
                bindChip(binding.chipSelfTalk, getString(R.string.metric_selftalk), scores.selfTalk)
            } catch (e: Exception) {
                binding.tvAqFallback.visibility = View.VISIBLE
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }

        // 이력 카드 (§8.2 실데이터)
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
                binding.tvHistoryEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

                // AQ 기준일 = 최근 세션 날짜 (시안 "9월 2일 기준" — 이력 첫 행 날짜 근사)
                val latest = data.sessions.firstOrNull()
                if (latest != null) {
                    val dashDate = DateFormats.toDashDate(latest.createdAt)
                    binding.tvDashDate.text = getString(R.string.dash_aq_basis_fmt, dashDate)
                    binding.tvDashDate.visibility = View.VISIBLE
                } else {
                    binding.tvDashDate.visibility = View.GONE
                }
            } catch (e: Exception) {
                historyAdapter.submitList(emptyList())
                binding.tvHistoryEmpty.visibility = View.VISIBLE
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 지표 칩: "알아듣기  8" 형태 — null이면 "-" (점수 전부 null 폴백과 동일) */
    private fun bindChip(view: TextView, label: String, score: Double?) {
        val value = score?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else "%.1f".format(it) } ?: "-"
        view.text = "$label  $value"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}