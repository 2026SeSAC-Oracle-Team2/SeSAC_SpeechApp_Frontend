package com.sesac.speechapp.ui.practice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.sesac.speechapp.databinding.FragmentPracticeBinding

/**
 * 학습 탭 — 테마별 학습 (카페/병원).
 * 데모 범위: 화면 틀만 — 테마 선택 세션은 백엔드 테마 선택 API 확장 후 연동.
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

        val comingSoon = View.OnClickListener {
            android.widget.Toast.makeText(
                requireContext(), "테마 학습은 곧 열릴 예정이에요", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        binding.cardThemeCafe.setOnClickListener(comingSoon)
        binding.cardThemeHospital.setOnClickListener(comingSoon)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}