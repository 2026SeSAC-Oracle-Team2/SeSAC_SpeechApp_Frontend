package com.sesac.speechapp.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.sesac.speechapp.databinding.FragmentDashboardBinding
import com.sesac.speechapp.ui.history.SessionHistory
import com.sesac.speechapp.ui.history.SessionHistoryAdapter
import com.sesac.speechapp.ui.learning.RadarChartView

/**
 * 대시보드 탭 — 지표별 방사형 그래프(stub) + 이전 학습 기록 리스트.
 * 이력 탭 폐지에 따라 기존 이력 리스트를 하단으로 이동 (사용자 확정).
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val historyAdapter = SessionHistoryAdapter()

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

        // 점수 추이 → 지표별 방사형 (stub — userAQ 산정식 API 연동은 P4)
        binding.radarDashboard.setData(
            listOf(
                RadarChartView.AxisData("알아듣기", 68f),
                RadarChartView.AxisData("이름대기", 55f),
                RadarChartView.AxisData("따라말하기", 77f),
                RadarChartView.AxisData("스스로말하기", 62f),
            )
        )

        // 이력 리스트 (stub — GET /sessions 목록 연동은 P4-10)
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
        loadStubHistory()
    }

    private fun loadStubHistory() {
        val stubList = listOf(
            SessionHistory(1, "2026.08.20", "주말에 뭐 했어요?", 72, true, 8),
            SessionHistory(2, "2026.08.19", "좋아하는 음식", 68, true, 6),
            SessionHistory(3, "2026.08.18", "취미 소개", 81, true, 10),
            SessionHistory(4, "2026.08.17", "일상 대화", 0, false, 3),
            SessionHistory(5, "2026.08.15", "날씨 얘기", 75, true, 7),
        )
        historyAdapter.submitList(stubList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}