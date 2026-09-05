package com.sesac.speechapp.ui.survey

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.MainActivity
import com.sesac.speechapp.R
import com.sesac.speechapp.data.repository.UserRepository
import com.sesac.speechapp.databinding.ActivitySurveyBinding
import kotlinx.coroutines.launch

/**
 * D-6 가입 설문조사 (06 v1.7 §5.2): 5문항 × 1~5 라디오 — 초기 AQ 확보.
 *
 * - 문항 텍스트는 앱 고정 (strings_survey.xml — 서버 저장 없음)
 * - 스킵 불가: 뒤로가기 시 경고 다이얼로그, 전 문항 응답 완료 시에만 [제출] 활성화
 * - 산출 주체 = 서버 — 클라는 answers 원문(문항 순서대로 5개)만 전송, 응답 환산 userAq만 신뢰
 * - 재응답 허용(백엔드 갱신) — 클라는 항상 제출
 */
class SurveyActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySurveyBinding
    private val userRepository by lazy { UserRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSubmitEnabled()
        observeQuestionGroups()

        binding.btnBack.setOnClickListener { confirmExit() }
        binding.btnSubmit.setOnClickListener { submitSurvey() }
    }

    /**
     * 전 문항(5개 라디오그룹) 응답 완료 시에만 [제출] 활성화 — 스킵 불가 규약.
     */
    private fun setupSubmitEnabled() {
        updateSubmitEnabled()
    }

    private fun allAnswered(): Boolean = listOf(
        binding.rgQuestion1, binding.rgQuestion2, binding.rgQuestion3,
        binding.rgQuestion4, binding.rgQuestion5
    ).all { it.checkedRadioButtonId != View.NO_ID }

    private fun updateSubmitEnabled() {
        binding.btnSubmit.isEnabled = allAnswered()
    }

    private fun observeQuestionGroups() {
        listOf(
            binding.rgQuestion1, binding.rgQuestion2, binding.rgQuestion3,
            binding.rgQuestion4, binding.rgQuestion5
        ).forEach { group ->
            group.setOnCheckedChangeListener { _, _ -> updateSubmitEnabled() }
        }
    }

    /**
     * answers 조립: 문항 순서대로 각 라디오그룹의 선택값(1~5) 5개.
     * 라디오 버튼 id는 레이아웃에서 rbQ{N}_S{1..5} 규약 — checkedRadioButtonId로 인덱스 역산.
     */
    private fun collectAnswers(): List<Int> {
        val groups = listOf(
            binding.rgQuestion1, binding.rgQuestion2, binding.rgQuestion3,
            binding.rgQuestion4, binding.rgQuestion5
        )
        return groups.map { group ->
            val checkedId = group.checkedRadioButtonId
            if (checkedId == View.NO_ID) return emptyList()
            val checked = group.findViewById<RadioButton>(checkedId)
            val tag = checked.tag as? String
            tag?.toIntOrNull() ?: 0
        }
    }

    private fun submitSurvey() {
        val answers = collectAnswers()
        // 클라 사전 검증: 5개 고정 + 각 1~5 (05a v1.6 §2 — 요청 계약)
        if (answers.size != SURVEY_QUESTION_COUNT || answers.any { it !in MIN_ANSWER..MAX_ANSWER }) {
            Toast.makeText(
                this,
                getString(R.string.survey_incomplete_error),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = getString(R.string.survey_submitting)
        lifecycleScope.launch {
            userRepository.submitSurvey(answers)
                .onSuccess { response ->
                    val userAq = response.userAq
                    if (userAq != null) {
                        // 응답 userAq 표시 안내 후 메인 이동 (06 §5.2 "학습 준비 완료")
                        showReadyDialog(userAq)
                    } else {
                        // 이론상 도달 불가(서버 산출 성공은 userAq 보증) — 방어선
                        Toast.makeText(
                            this@SurveyActivity,
                            getString(R.string.survey_result_missing),
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnSubmit.isEnabled = true
                        binding.btnSubmit.text = getString(R.string.survey_submit)
                    }
                }
                .onFailure { e ->
                    binding.btnSubmit.isEnabled = true
                    binding.btnSubmit.text = getString(R.string.survey_submit)
                    Toast.makeText(
                        this@SurveyActivity,
                        e.message ?: getString(R.string.survey_submit_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun showReadyDialog(userAq: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.survey_done_title))
            .setMessage(getString(R.string.survey_done_message, userAq))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.survey_done_confirm)) { _, _ ->
                navigateToMain()
            }
            .show()
    }

    /**
     * 스킵 불가 규약 — 뒤로가기 시 경고 다이얼로그.
     * (설문 완료 없이는 학습을 시작할 수 없다 — 메인 진입 불가. 취소 시 화면 유지)
     */
    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.survey_exit_title))
            .setMessage(getString(R.string.survey_exit_message))
            .setNegativeButton(getString(R.string.survey_exit_stay)) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(getString(R.string.survey_exit_quit)) { _, _ ->
                finishAffinity() // 가입 플로우 전체 종료 — 재로그인 시 재노출 판별로 재진입
            }
            .show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val SURVEY_QUESTION_COUNT = 5
        const val MIN_ANSWER = 1
        const val MAX_ANSWER = 5
    }
}