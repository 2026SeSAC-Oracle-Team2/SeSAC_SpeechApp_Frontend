package com.sesac.speechapp.ui.profile

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.chip.Chip
import com.sesac.speechapp.BuildConfig
import com.sesac.speechapp.R
import com.sesac.speechapp.data.remote.AuthImageLoader
import com.sesac.speechapp.data.remote.RetrofitClient
import com.sesac.speechapp.data.remote.dto.TagDto
import com.sesac.speechapp.data.remote.dto.UpdateProfileRequest
import com.sesac.speechapp.data.repository.UserRepository
import com.sesac.speechapp.databinding.ActivityProfileEditBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.view.View

/**
 * 프로필 수정 (ProfileFragment 탭 진입) — D-7b 확장 (06 v1.7 §5.1 기획 근거).
 *
 * - 프로필 사진: 탭 → 갤러리 선택 → multipart POST /api/v1/users/me/profile-image
 * - 닉네임: PATCH /api/v1/users/me — 유효성 2~20자 + 공백만 금지
 * - D-7b 확장: 성별(Chip 토글 — 선택 안 함 허용)·생년월일(DatePicker maxDate=현재)·
 *   취미(EditText)·태그(GET /me/tags 15종 — 최대 5개, 전량교체 PATCH)
 * - [저장] = PATCH 전체 일괄 (부분 업데이트 — 변경 없는 필드는 null 전송으로 기존값 유지.
 *   tagIds는 선택 변경 있을 때만 전송 — 전량교체 계약 05a v1.6 §2)
 * - 태그 역매핑: UserDto.tags 쉼표 문자열("건강관리, 등산") → trim → 이름 매칭 → tagId
 *   (태그명 유니크라 안전 — 03a §1.1. 미매칭 시 해당 태그 스킵+로그)
 * - 저장 성공 → finish (ProfileFragment는 onResume에서 갱신)
 */
class ProfileEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding
    private lateinit var userRepository: UserRepository

    private var selectedImageUri: Uri? = null
    private var currentNickname: String? = null

    // D-7b: 원본값 캐시 — 변경분만 PATCH (부분 업데이트 규약)
    private var currentSex: String? = null
    private var currentBirthDate: String? = null
    private var currentHobbies: String? = null
    private var currentTags: List<Long> = emptyList()
    private var sexChanged = false
    private var birthChanged = false
    private var hobbiesChanged = false
    private var tagsChanged = false

    /** 동적으로 생성한 칩 id → 서버 tagId 매핑 (D-6 패턴 재사용) */
    private val tagIdMap = mutableMapOf<Int, Long>()

    // 갤러리 선택
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivProfile.load(uri) { crossfade(true) }
        }
    }

    // 권한 요청 (READ_MEDIA_IMAGES 33+, READ_EXTERNAL_STORAGE 32-)
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) galleryLauncher.launch("image/*")
        else toast("갤러리 권한이 필요해요")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)
        loadCurrentProfile()

        binding.frameProfileImage.setOnClickListener { pickImage() }
        binding.cameraBadge.setOnClickListener { pickImage() }

        setupSexChips()
        setupBirthDatePicker()
        setupTagLimitObserver()

        binding.btnSave.setOnClickListener { save() }
    }

    private fun loadCurrentProfile() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMyProfile()
                val user = response.body()?.takeIf { it.success }?.data
                    ?: return@launch
                currentNickname = user.nickname
                binding.etNickname.setText(user.nickname ?: "")

                // D-7b: 기존값 캐시 + 화면 초기화
                currentSex = user.sex
                currentBirthDate = user.birthDate
                currentHobbies = user.hobbies
                binding.etHobby.setText(user.hobbies ?: "")
                user.birthDate?.let { binding.etBirthDate.setText(it) }

                // 성별 칩 초기 상태 (체크 리스너 발동 방지 — 캐시 세팅 후 반영)
                when (user.sex) {
                    "M" -> binding.chipSexM.isChecked = true
                    "F" -> binding.chipSexF.isChecked = true
                }

                // 태그: GET /me/tags 15종 로드 + 현재 선택 역매핑
                loadTags(user.tags)

                // 기존 사진 표시 (키가 있을 때만 전용 엔드포인트)
                val key = user.profileImageUrl
                if (!key.isNullOrBlank()) {
                    val url = BuildConfig.SERVER_BASE_URL.trimEnd('/') + "/api/v1/users/me/profile-image?v=" + System.currentTimeMillis()
                    binding.ivProfile.load(url, AuthImageLoader.get(this@ProfileEditActivity)) {
                        placeholder(R.drawable.ic_person_24)
                        error(R.drawable.ic_person_24)
                        crossfade(true)
                    }
                } else {
                    binding.ivProfile.setImageResource(R.drawable.ic_person_24)
                }
            } catch (_: Exception) {
                // 조회 실패해도 편집은 진행 가능
            }
        }
    }

    // ============================================================
    // D-7b: 성별 — Chip 토글(M/F 상호배타), 선택 안 함 허용(null 유지)
    // ============================================================
    private fun setupSexChips() {
        binding.chipSexM.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.chipSexF.isChecked = false
                currentSex = "M"; sexChanged = true
            } else if (!binding.chipSexF.isChecked) {
                currentSex = null; sexChanged = true  // 둘 다 해제 = 선택 안 함(null)
            }
        }
        binding.chipSexF.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.chipSexM.isChecked = false
                currentSex = "F"; sexChanged = true
            } else if (!binding.chipSexM.isChecked) {
                currentSex = null; sexChanged = true
            }
        }
    }

    // ============================================================
    // D-7b: 생년월일 — 읽기 표시(yyyy-MM-dd) + 탭 → DatePicker (maxDate=현재)
    // ============================================================
    private fun setupBirthDatePicker() {
        binding.etBirthDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        // 초기 표시: 기존값 있으면 그 날짜, 없으면 현재-70년 (시니어 타겟 — D-6 선례)
        val initial = currentBirthDate?.let { d ->
            runCatching { LocalDate.parse(d) }.getOrNull()
        } ?: LocalDate.now().minusYears(70)
        val dialog = DatePickerDialog(
            this,
            { _, y, m, day ->
                val picked = LocalDate.of(y, m + 1, day)
                val iso = picked.format(DateTimeFormatter.ISO_LOCAL_DATE)
                binding.etBirthDate.setText(iso)
                if (iso != currentBirthDate) {
                    currentBirthDate = iso
                    birthChanged = true
                }
            },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        )
        dialog.datePicker.maxDate = System.currentTimeMillis() // 미래 생일 방지
        dialog.show()
    }

    // ============================================================
    // D-7b: 태그 — 15종 버블 + 최대 5개 + 역매핑
    // ============================================================
    private fun loadTags(existingTags: String?) {
        lifecycleScope.launch {
            userRepository.getTags()
                .onSuccess { tagsResponse ->
                    populateTagChips(tagsResponse.tags, existingTags)
                }
                .onFailure {
                    Toast.makeText(
                        this@ProfileEditActivity,
                        getString(R.string.profile_edit_tags_load_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun populateTagChips(tags: List<TagDto>, existingTags: String?) {
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
        restoreTagSelection(existingTags, tags)
    }

    /**
     * 현재 선택 상태 초기 로드 — UserDto.tags("건강관리, 등산") 쉼표 분할 → trim →
     * 이름 매칭 → tagId 역매핑 (태그명 유니크 — 03a §1.1).
     * 미매칭(서버 태그명 변경 등) 시 해당 태그 스킵 + 로그 (지시문 2.4 방어).
     */
    private fun restoreTagSelection(existingTags: String?, tags: List<TagDto>) {
        if (existingTags.isNullOrBlank()) return
        val names = existingTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val nameToId = tags.associate { it.tag to it.tagId }
        val restored = mutableListOf<Long>()
        names.forEach { name ->
            val id = nameToId[name]
            if (id != null) {
                restored.add(id)
                tagIdMap.entries.firstOrNull { it.value == id }?.let { entry ->
                    binding.chipGroupTags.findViewById<Chip>(entry.key)?.isChecked = true
                }
            } else {
                android.util.Log.w("ProfileEdit", "태그 역매핑 실패 — 스킵: $name")
            }
        }
        currentTags = restored
    }

    /** 태그 최대 5개 제한 (06 v1.7 §5.1) — 6개째 선택 시 즉시 해제 + 안내 (D-6 로직 재사용) */
    private fun setupTagLimitObserver() {
        binding.chipGroupTags.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.size > MAX_TAG_COUNT) {
                val lastChecked = checkedIds.lastOrNull()
                if (lastChecked != null) {
                    group.findViewById<Chip>(lastChecked).isChecked = false
                }
                Toast.makeText(
                    this,
                    getString(R.string.profile_edit_tag_limit_error, MAX_TAG_COUNT),
                    Toast.LENGTH_SHORT
                ).show()
            }
            // 선택 변화 감지 — 현재 선택 tagId 집합과 다르면 변경 플래그
            val nowIds = checkedIds.mapNotNull { viewId -> tagIdMap[viewId] }.sorted()
            if (nowIds != currentTags.sorted()) tagsChanged = true
        }
    }

    private fun currentSelectedTagIds(): List<Long> =
        binding.chipGroupTags.checkedChipIds.mapNotNull { viewId -> tagIdMap[viewId] }

    private fun pickImage() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            galleryLauncher.launch("image/*")
        } else {
            mediaPermissionLauncher.launch(permission)
        }
    }

    // ============================================================
    // [저장] — PATCH 전체 일괄 (부분 업데이트: 변경 없는 필드는 null 전송)
    // ============================================================
    private fun save() {
        val nickname = binding.etNickname.text?.toString()?.trim() ?: ""

        // 유효성: 2~20자, 공백만 금지
        if (nickname.length !in 2..20) {
            binding.tilNickname.error = getString(R.string.profile_nickname_error)
            return
        }

        val imageChanged = selectedImageUri != null
        val nicknameChanged = nickname != currentNickname
        val hobbies = binding.etHobby.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        hobbiesChanged = hobbies != currentHobbies

        if (!imageChanged && !nicknameChanged && !sexChanged && !birthChanged && !hobbiesChanged && !tagsChanged) {
            finish() // 변경 없음
            return
        }

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            var ok = true
            // 닉네임·D-7b 필드 — PATCH 부분 업데이트 (변경 없는 필드는 null → 기존값 유지)
            if (nicknameChanged || sexChanged || birthChanged || hobbiesChanged || tagsChanged) {
                userRepository.updateProfileFull(
                    UpdateProfileRequest(
                        nickname = nickname,           // non-null 필수 — 기존값 전송
                        sex = if (sexChanged) currentSex else null,
                        birthDate = if (birthChanged) currentBirthDate else null,
                        hobbies = if (hobbiesChanged) hobbies else null,
                        tagIds = if (tagsChanged) currentSelectedTagIds() else null  // 전량교체
                    )
                ).onFailure { ok = false; toast(it.message ?: getString(R.string.profile_edit_save_error)) }
            }
            if (ok && imageChanged) {
                userRepository.uploadProfileImage(selectedImageUri!!)
                    .onFailure { ok = false; toast(it.message ?: "사진 업로드 실패") }
            }

            binding.progressBar.visibility = android.view.View.GONE
            binding.btnSave.isEnabled = true

            if (ok) {
                // ProfileFragment 캐시버스터용 — 수정 시점 기록
                getSharedPreferences("speechapp_prefs", MODE_PRIVATE)
                    .edit().putLong("profile_image_updated_at", System.currentTimeMillis()).apply()
                toast(getString(R.string.profile_edit_saved))
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val MAX_TAG_COUNT = 5
    }
}
