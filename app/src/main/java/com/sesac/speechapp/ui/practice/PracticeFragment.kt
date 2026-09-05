package com.sesac.speechapp.ui.practice

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sesac.speechapp.databinding.FragmentPracticeBinding
import com.sesac.speechapp.ui.learning.LearningSessionLoadingActivity

/**
 * 테마별 학습 탭 — D-7 1.4 실연동: POST /api/v1/sessions/theme?thema=CAFE|HOSPITAL.
 * 카드별 thema 코드 전달 → 같은 로딩 화면 경유 (05a v1.6 §3.1 — 대소문자 무관).
 * 시나리오 플로우 데이터는 컨텐츠 확정 전 — 현재 스텁은 today와 동일 무작위 출제 (05a §3.1).
 */
class PracticeFragment : Fragment() {

    private var _binding: com.sesac.speechapp.databinding.FragmentPracticeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = com.sesac.speechapp.databinding.FragmentPracticeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 테마 카드 → 로딩 화면에 thema 코드 전달 (theme 분기 — D-7 1.4)
        binding.cardThemeCafe.setOnClickListener {
            startActivity(
                Intent(requireContext(), LearningSessionLoadingActivity::class.java)
                    .putExtra(LearningSessionLoadingActivity.EXTRA_THEMA, "CAFE")
            )
        }
        binding.cardThemeHospital.setOnClickListener {
            startActivity(
                Intent(requireContext(), LearningSessionLoadingActivity::class.java)
                    .putExtra(LearningSessionLoadingActivity.EXTRA_THEMA, "HOSPITAL")
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}