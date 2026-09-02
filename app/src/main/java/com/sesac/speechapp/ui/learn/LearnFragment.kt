package com.sesac.speechapp.ui.learn

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sesac.speechapp.databinding.FragmentLearnBinding
import com.sesac.speechapp.ui.learning.LearningSessionLoadingActivity
import com.sesac.speechapp.ui.learning.RadarChartView

/**
 * 홈 탭 (구 Learn) — 오늘의 학습 카드 + 실력 지표 방사형 그래프(stub).
 * 디버깅용 3버튼(P3-21)은 제거됨 — 실제 세션 플로우(P3-26)로 대체.
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

        // 오늘의 학습 카드/버튼 탭 → 세션 로딩 화면 (스텁 2~3초 대기)
        binding.cardHero.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }
        binding.btnStart.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }

        // 실력 지표 stub — userAQ 최근20세션 상위10 평균 산정식은 P4에서 API 연동
        binding.radarHome.setData(
            listOf(
                com.sesac.speechapp.ui.learning.RadarChartView.AxisData("알아듣기", 72f),
                com.sesac.speechapp.ui.learning.RadarChartView.AxisData("이름대기", 58f),
                com.sesac.speechapp.ui.learning.RadarChartView.AxisData("따라말하기", 81f),
                com.sesac.speechapp.ui.learning.RadarChartView.AxisData("스스로말하기", 66f),
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}