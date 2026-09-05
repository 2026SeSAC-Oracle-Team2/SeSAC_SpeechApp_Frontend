package com.sesac.speechapp.ui.practice

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import coil.load
import com.sesac.speechapp.BuildConfig
import com.sesac.speechapp.R
import com.sesac.speechapp.data.remote.AuthImageLoader
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
        loadThemeImages()

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

    override fun onResume() {
        super.onResume()
        loadThemeImages()
    }

    /**
     * D-8③ 테마 카드 대표 이미지 — DB OCI 콘텐츠 이미지 (시안 더미 아님).
     * 문제 세부와 동일 프록시 경로(/api/v1/content/images/{id}/file) — 표시만, 로직 무관.
     * 이미지 ID는 서버 운영 데이터 의존 — 실패 시 카드만 표시(깨지지 않게).
     */
    private fun loadThemeImages() {
        val base = BuildConfig.SERVER_BASE_URL.trimEnd('/')
        val loader = AuthImageLoader.get(requireContext())
        val cafeId = ThemeImagePrefs.cafeImageId(requireContext())
        val hospitalId = ThemeImagePrefs.hospitalImageId(requireContext())
        if (cafeId != null) {
            binding.ivThemeCafe.load(base + "/api/v1/content/images/" + cafeId + "/file", loader) {
                crossfade(true)
            }
        }
        if (hospitalId != null) {
            binding.ivThemeHospital.load(base + "/api/v1/content/images/" + hospitalId + "/file", loader) {
                crossfade(true)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * D-8③ 테마 대표 이미지 ID 저장소 — 서버에 테마 이미지 엔드포인트가 없어
 * 클라 설정값으로 관리 (BuildConfig 아님 — SharedPreferences). 미설정 시 카드만 표시.
 * 기본값: 운영 DB에 등록된 카페/병원 대표 이미지 ID (서버 데이터에 맞춰 수정 가능).
 */
object ThemeImagePrefs {
    private const val PREFS = "theme_image_prefs"
    // D-8④ 기본값: 운영 DB 각 테마 최초 등록 이미지 (LIVE 실측 2026-09-06 — CAFE 68~139, HOSPITAL 110~149)
    // 기획자 소스 확정 시 prefs 덮어쓰기로 대체 — 코드 수정 불필요
    private const val DEFAULT_CAFE_IMAGE_ID = 68L
    private const val DEFAULT_HOSPITAL_IMAGE_ID = 110L

    fun cafeImageId(ctx: android.content.Context): Long? =
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getLong("cafe_image_id", DEFAULT_CAFE_IMAGE_ID).takeIf { it > 0 }

    fun hospitalImageId(ctx: android.content.Context): Long? =
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getLong("hospital_image_id", DEFAULT_HOSPITAL_IMAGE_ID).takeIf { it > 0 }
}