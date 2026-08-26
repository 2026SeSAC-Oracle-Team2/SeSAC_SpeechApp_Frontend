package com.sesac.speechapp.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.sesac.speechapp.databinding.FragmentHistoryBinding

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
                isCompleted = true,
                turnCount = 8
            ),
            SessionHistory(
                id = 2,
                date = "2026.08.19",
                topic = "좋아하는 음식",
                score = 68,
                isCompleted = true,
                turnCount = 6
            ),
            SessionHistory(
                id = 3,
                date = "2026.08.18",
                topic = "취미 소개",
                score = 81,
                isCompleted = true,
                turnCount = 10
            ),
            SessionHistory(
                id = 4,
                date = "2026.08.17",
                topic = "일상 대화",
                score = 0,
                isCompleted = false,
                turnCount = 3
            ),
            SessionHistory(
                id = 5,
                date = "2026.08.15",
                topic = "날씨 얘기",
                score = 75,
                isCompleted = true,
                turnCount = 7
            ),
        )
        adapter.submitList(stubList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
