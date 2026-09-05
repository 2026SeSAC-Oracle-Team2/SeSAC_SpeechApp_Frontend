package com.sesac.speechapp.ui.learn

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sesac.speechapp.R
import com.sesac.speechapp.databinding.FragmentLearnBinding
import com.sesac.speechapp.ui.learning.LearningSessionLoadingActivity
import com.sesac.speechapp.ui.learning.RadarChartView

/**
 * 홈 탭 (구 Learn) — 오늘의 학습 카드 + 실력 지표 방사형 그래프.
 * D-7 1.4: 오늘의 학습 → POST /sessions/today (EXTRA_THEMA 미전달 = today 분기)
 * 실력 지표 stub — 대시보드 D-7 3.2에서 실데이터화 (홈 카드는 D-7 범위 아님 — stub 유지)
 */
class LearnFragment : Fragment() {

    private var _binding: FragmentLearnBinding? = null
    private val binding get() = _binding!!

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

        // 오늘의 학습 카드/버튼 탭 → 세션 로딩 화면 (스텁 2~3초 대기, D-7 1.4: /today 분기)
        binding.cardHero.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }
        binding.btnStart.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }

        // 실력 지표 stub — 대시보드 탭이 D-7 3.2에서 실데이터화 (홈은 P4 이월)
        binding.radarHome.setData(
            listOf(
                RadarChartView.AxisData(getString(R.string.metric_listen), 0f),
                RadarChartView.AxisData(getString(R.string.metric_naming), 0f),
                RadarChartView.AxisData(getString(R.string.metric_shadowing), 0f),
                RadarChartView.AxisData(getString(R.string.metric_selftalk), 0f),
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}