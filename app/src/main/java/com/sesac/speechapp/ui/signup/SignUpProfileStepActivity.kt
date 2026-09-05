package com.sesac.speechapp.ui.signup

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.sesac.speechapp.R
import com.sesac.speechapp.data.remote.dto.TagDto
import com.sesac.speechapp.data.remote.dto.UpdateProfileRequest
import com.sesac.speechapp.data.repository.UserRepository
import com.sesac.speechapp.databinding.ActivitySignUpProfileStepBinding
import com.sesac.speechapp.ui.survey.SurveyActivity
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * D-6 가입 다단계 플로우 Step2 (06 v1.7 §5.1): 성별·생년월일·취미·관심사 태그(최대 5개).
 *
 * - Step1(SignUpActivity) 완료 후 진입 — 닉네임은 extra로 전달받아 PATCH 전체 필드 일괄 전송에 사용
 * - 태그: GET /me/tags로 15종 수신 → Chip 버블, 6개째 선택 시 해제 + 안내 (최대 5개)
 * - 생년월일: DatePickerDialog (maxDate=현재 — 미래 생일 방지), yyyy-MM-dd 전송
 * - [다음] → PATCH /me(전체 필드 일괄) → 성공 시 SurveyActivity
 */
class SignUpProfileStepActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpProfileStepBinding
    private val userRepository by lazy { UserRepository(this) }

    private var selectedSex: String? = null
    private var selectedBirthDate: LocalDate? = null

    /** 동적으로 생성한 칩 id → 서버 tagId 매핑 */
    private val tagIdMap = mutableMapOf<Int, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpProfileStepBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupSexToggle()
        setupBirthDatePicker()
        loadTags()
        observeTagSelection()

        binding.btnNext.setOnClickListener { submitProfile() }
    }

    private fun setupSexToggle() {
        binding.chipSexM.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedSex = SEX_MALE
                if (binding.chipSexF.isChecked) binding.chipSexF.isChecked = false
            } else if (selectedSex == SEX_MALE) {
                selectedSex = null
            }
        }
        binding.chipSexF.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedSex = SEX_FEMALE
                if (binding.chipSexM.isChecked) binding.chipSexM.isChecked = false
            } else if (selectedSex == SEX_FEMALE) {
                selectedSex = null
            }
        }
    }

    private fun setupBirthDatePicker() {
        binding.etBirthDate.isFocusable = false
        binding.etBirthDate.isClickable = true
        binding.etBirthDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val initial = selectedBirthDate ?: LocalDate.now().minusYears(INITIAL_AGE_YEARS)
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = LocalDate.of(year, month + 1, day)
                if (picked.isAfter(LocalDate.now())) {
                    Toast.makeText(
                        this,
                        getString(R.string.signup_profile_birth_future_error),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@DatePickerDialog
                }
                selectedBirthDate = picked
                binding.etBirthDate.setText(picked.toString()) // yyyy-MM-dd
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        )
        dialog.datePicker.maxDate = System.currentTimeMillis() // 미래 생일 방지
        dialog.show()
    }

    private fun loadTags() {
        lifecycleScope.launch {
            userRepository.getTags()
                .onSuccess { tagsResponse ->
                    populateTagChips(tagsResponse.tags)
                }
                .onFailure { e ->
                    Toast.makeText(
                        this@SignUpProfileStepActivity,
                        e.message ?: getString(R.string.signup_profile_tags_load_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun populateTagChips(tags: List<TagDto>) {
        binding.chipGroupTags.removeAllViews()
        tagIdMap.clear()
        tags.forEach { tag ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = tag.tag
                isCheckable = true
                isClickable = true
            }
            tagIdMap[chip.id] = tag.tagId
            binding.chipGroupTags.addView(chip)
        }
    }

    /**
     * 태그 최대 5개 제한 (06 v1.7 §5.1) — 6개째 선택 시 즉시 해제 + 안내.
     * 칩 자체 disable 방식보다 UX 나음 (지시문 함정 목록 권장 방식).
     */
    private fun observeTagSelection() {
        binding.chipGroupTags.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.size > MAX_TAG_COUNT) {
                val lastChecked = checkedIds.lastOrNull()
                if (lastChecked != null) {
                    group.findViewById<Chip>(lastChecked).isChecked = false
                }
                Toast.makeText(
                    this,
                    getString(R.string.signup_profile_tag_limit_error, MAX_TAG_COUNT),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun currentSelectedTagIds(): List<Long> =
        binding.chipGroupTags.checkedChipIds.mapNotNull { viewId -> tagIdMap[viewId] }

    private fun submitProfile() {
        // 필수 검증: 성별·생년월일 (취미·태그는 선택 — 06 §5.1에 필수 표기 없음)
        if (selectedSex == null) {
            Toast.makeText(
                this,
                getString(R.string.signup_profile_sex_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (selectedBirthDate == null) {
            Toast.makeText(
                this,
                getString(R.string.signup_profile_birth_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Step1에서 전달받은 닉네임 — PATCH 전체 필드 일괄 전송 (05a v1.6 §2)
        val nickname = intent.getStringExtra(EXTRA_NICKNAME).orEmpty().trim()
        val request = UpdateProfileRequest(
            nickname = nickname,
            sex = selectedSex,
            birthDate = selectedBirthDate?.toString(), // yyyy-MM-dd
            hobbies = binding.etHobby.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            tagIds = currentSelectedTagIds() // 전량 교체 계약 — 항상 현재 선택 전체
        )
        sendProfile(request)
    }

    private fun sendProfile(request: UpdateProfileRequest) {
        binding.btnNext.isEnabled = false
        binding.btnNext.text = getString(R.string.signup_profile_saving)
        lifecycleScope.launch {
            userRepository.updateProfileFull(request)
                .onSuccess { navigateToSurvey() }
                .onFailure { e ->
                    binding.btnNext.isEnabled = true
                    binding.btnNext.text = getString(R.string.signup_profile_next)
                    Toast.makeText(
                        this@SignUpProfileStepActivity,
                        e.message ?: getString(R.string.signup_profile_save_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun navigateToSurvey() {
        startActivity(Intent(this, SurveyActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_NICKNAME = "extra_nickname"
        private const val SEX_MALE = "M"
        private const val SEX_FEMALE = "F"
        private const val MAX_TAG_COUNT = 5

        /** DatePicker 초기 표시 연도 — 시니어 타겟 기본값 (현재-70년) */
        private const val INITIAL_AGE_YEARS = 70L
    }
}