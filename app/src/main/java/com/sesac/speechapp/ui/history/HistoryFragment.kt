package com.sesac.speechapp.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.sesac.speechapp.databinding.FragmentHistoryBinding

/**
 * 구 이력 탭 — D-7 이력 카드가 대시보드로 이동하며 폐기 예정인 화면.
 * 내비게이션 그래프에서 참조가 남아 있어 컴파일 유지 — stub 데이터는 새 계약
 * (SessionHistory: id/date/topic/score/createdAt — 05a §8.2)으로 갱신.
 * 실데이터 로직은 DashboardFragment(D-7 3.2)가 담당 — 이 화면은 더미만 유지.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val adapter = SessionHistoryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistoryFragment.adapter
        }

        loadStubData()
    }

    private fun loadStubData() {
        val stubList = listOf(
            SessionHistory(
                id = 1,
                date = "2026.08.20",
                topic = "주말에 뭐 했어요?",
                score = 72,
                createdAt = "2026-08-20T10:00:00"
            ),
            SessionHistory(
                id = 2,
                date = "2026.08.19",
                topic = "좋아하는 음식",
                score = 68,
                createdAt = "2026-08-19T10:00:00"
            ),
            SessionHistory(
                id = 3,
                date = "2026.08.18",
                topic = "취미 소개",
                score = 81,
                createdAt = "2026-08-18T10:00:00"
            ),
            SessionHistory(
                id = 4,
                date = "2026.08.17",
                topic = "일상 대화",
                score = 0,
                createdAt = "2026-08-17T10:00:00"
            ),
            SessionHistory(
                id = 5,
                date = "2026.08.15",
                topic = "날씨 얘기",
                score = 75,
                createdAt = "2026-08-15T10:00:00"
            ),
        )
        adapter.submitList(stubList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}