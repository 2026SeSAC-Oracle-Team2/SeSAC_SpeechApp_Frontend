package com.sesac.speechapp.ui.learn

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sesac.speechapp.databinding.FragmentLearnBinding
import com.sesac.speechapp.ui.learning.LearningSessionLoadingActivity
import com.sesac.speechapp.ui.record.RecordingTestActivity

/**
 * P3-21: Learn 탭 — 오늘의 학습 카드 + 3버튼(디버깅용)
 * content_type 임시 매핑:
 *   - 따라말하기 → SHADOWING
 *   - 스스로말하기 → SELF_TALK
 *   - 이야기하기 → STORYTELLING
 * TODO: UI 확정 시 Card 디자인 교체
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

        // P3-26: "오늘의 학습" 카드 탭 → 세션 로딩 화면 (스텁 2~3초 대기)
        binding.cardHero.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }

        // 기존 시작하기 버튼도 동일 동작 (카드 자체가 탭 대상이므로 버튼은 그대로 둠)
        binding.btnStart.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }

        // 3버튼 디버깅 UI
        binding.btnShadowing.setOnClickListener {
            startRecordingTest("SHADOWING")
        }
        binding.btnSelfTalk.setOnClickListener {
            startRecordingTest("SELF_TALK")
        }
        binding.btnStorytelling.setOnClickListener {
            startRecordingTest("STORYTELLING")
        }
    }

    private fun startRecordingTest(contentType: String) {
        val intent = Intent(requireContext(), RecordingTestActivity::class.java)
        intent.putExtra(RecordingTestActivity.EXTRA_CONTENT_TYPE, contentType)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
