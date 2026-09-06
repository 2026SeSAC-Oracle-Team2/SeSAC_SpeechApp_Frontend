package com.sesac.speechapp.ui.learn

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.R
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.FragmentLearnBinding
import com.sesac.speechapp.ui.detail.DateFormats
import com.sesac.speechapp.ui.learning.LearningSessionLoadingActivity
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 홈 탭 (구 Learn) — D-8-C1 시안 home.tsx 전면 재작성.
 * D-7 1.4: 오늘의 학습 → POST /sessions/today (EXTRA_THEMA 미전달 = today 분기)
 * D-8②b: 홈 통계 2카드(연속 학습/평균 점수) 실데이터 — GET /users/me/stats (05a §8.4).
 * D-8③: 최근 학습 결과 = GET /users/me/sessions/history 최근 3개 (터치 → 세부 보고서).
 * D-8-C1: 날짜 헤더(서버 날짜 API 부재 — 기기 로컬 표시, 시안 근사) + 인사 닉네임
 *         (GET /users/me — ProfileViewModel 공용) + FAB(시안 반영 예외 허용 — ChatActivity 연결).
 */
class LearnFragment : Fragment() {

    private var _binding: FragmentLearnBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: SessionFlowRepository

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
        repository = SessionFlowRepository(requireContext())

        // 오늘의 학습 카드/버튼 탭 → 세션 로딩 화면
        binding.cardHero.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }
        binding.btnStart.setOnClickListener {
            startActivity(Intent(requireContext(), LearningSessionLoadingActivity::class.java))
        }

        // 최근 학습 결과 3개 — onResume에서 조회
        binding.containerRecent.removeAllViews()

        bindDateHeader()
        bindFab()
        loadNickname()
    }

    /**
     * 시안 헤더 "9월 3일 목요일" — 서버 날짜 API 부재로 기기 로컬 표시 (시안 근사).
     * 기획 우선: 서버 날짜와 불일치 가능성은 보고서 리스크 표기.
     */
    private fun bindDateHeader() {
        val today = LocalDate.now()
        val monthDay = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA).format(today)
        val dayOfWeek = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREA)
        binding.tvHomeDate.text = "$monthDay $dayOfWeek"
    }

    /**
     * 시안 FAB(우하단): 말풍선 "덕분이와 함께 대화해요" + 오리 원형 버튼 → ChatActivity.
     * FAB는 시안 반영으로 예외 허용 (사용자 확정 "design 기준" — 지시문 §4).
     */
    private fun bindFab() {
        binding.fabChat.setOnClickListener {
            startActivity(Intent(requireContext(), com.sesac.speechapp.ui.chat.ChatActivity::class.java))
        }
        // 터치 피드백: active scale 0.96 (시안 Btn active:scale 근사)
        binding.fabChat.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }
            false
        }
    }

    /**
     * 인사 "덕분님, 오늘도 반가워요" — GET /users/me 닉네임 (기존 ProfileViewModel 재사용 대신
     * 가벼운 직접 호출 — Fragment별 뷰모델 의존 최소화).
     * 실패 시 기본 문구 유지 (닉네임 로딩 실패가 홈을 깨지 않게).
     */
    private fun loadNickname() {
        lifecycleScope.launch {
            try {
                val response = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.sesac.speechapp.data.remote.RetrofitClient.apiService.getMyProfile()
                }
                val nickname = if (response.isSuccessful) {
                    val body = response.body()
                    val inner = body?.data
                    // GET /users/me 응답: ApiResponse<UserDto> — inner가 UserDto 그 자체
                    if (body != null && body.success && inner != null) inner.nickname else null
                } else null
                binding.tvTitle.text = getString(
                    R.string.home_greeting_nickname_fmt,
                    nickname?.takeIf { it.isNotBlank() } ?: "덕분님"
                )
            } catch (_: Exception) {
                // 기본 문구 유지
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadStats()
        loadRecent()
    }

    /**
     * D-8③ 홈 최근 학습 결과 — history 상위 3개 카드 (시안 home.tsx).
     * D-8-C2 C-1 원인 확정: onViewCreated의 removeAllViews와 onResume loadRecent가
     * 경쟁하지는 않지만, API 실패 시 재시도 없이 empty로 남는 케이스가 있었다.
     * → 로그 추가 + 실패 시 1회 재시도 (네트워크 일시 실패 대비 — 지시문 C-1).
     */
    private fun loadRecent(retried: Boolean = false) {
        if (_binding == null) return  // D-8④ 사이클5: fragment 이탈 후 콜백 NPE 방지
        lifecycleScope.launch {
            try {
                val data = repository.getSessionHistory()
                val items = data.sessions.take(3)
                android.util.Log.d("LearnFragment", "loadRecent ok — ${data.sessions.size} sessions")
                binding.containerRecent.removeAllViews()
                binding.tvRecentEmpty.visibility =
                    if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.tvMoreHint.visibility =
                    if (data.sessions.size > 3) View.VISIBLE else View.GONE
                items.forEach { it_ ->
                    // D-8④ 사이클5: 루트가 MaterialCardView로 바뀜(D-8-C1) — 캐스트 제거(View 수신)
                    val row = layoutInflater.inflate(
                        R.layout.item_home_recent, binding.containerRecent, false
                    )
                    row.findViewById<TextView>(R.id.tvRecentTopic).text = it_.sessionName
                    row.findViewById<TextView>(R.id.tvRecentDate).text = DateFormats.toDashDate(it_.createdAt)
                    row.findViewById<TextView>(R.id.tvRecentAq).text = it_.aq.toString()
                    row.setOnClickListener {
                        startActivity(
                            Intent(requireContext(), com.sesac.speechapp.ui.detail.SessionDetailActivity::class.java)
                                .putExtra(com.sesac.speechapp.ui.detail.SessionDetailActivity.EXTRA_SESSION_ID, it_.sessionId)
                                .putExtra(com.sesac.speechapp.ui.detail.SessionDetailActivity.EXTRA_SESSION_NAME, it_.sessionName)
                                .putExtra(com.sesac.speechapp.ui.detail.SessionDetailActivity.EXTRA_CREATED_AT, it_.createdAt)
                        )
                    }
                    binding.containerRecent.addView(row)
                }
            } catch (e: Exception) {
                android.util.Log.w("LearnFragment", "loadRecent 실패 — ${e.message}")
                // D-8-C2 C-1: 실패 시 1회 재시도 (일시적 네트워크 실패 대비)
                if (!retried) {
                    kotlinx.coroutines.delay(1500)
                    if (_binding != null) loadRecent(retried = true)  // 탭 이탈 후 재시도 금지 — NPE 방지
                    return@launch
                }
                binding.containerRecent.removeAllViews()
                binding.tvRecentEmpty.visibility = View.VISIBLE
                binding.tvMoreHint.visibility = View.GONE
            }
        }
    }

    /**
     * D-8②b 홈 통계 2카드 — GET /users/me/stats.
     * 실패 시 카드는 유지하고 값 자리만 "-". deltaScore null이면 증감 TextView 숨김.
     */
    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val stats = repository.getMyStats()
                binding.containerStats.tvStreakValue.text =
                    getString(R.string.home_stat_streak_value).format(Locale.US, stats.streakDays)
                binding.containerStats.tvAvgValue.text = stats.avgScore?.let {
                    String.format(Locale.US, "%.1f", it)
                } ?: getString(R.string.home_stat_placeholder)
                if (stats.deltaScore != null) {
                    val delta = stats.deltaScore
                    val sign = if (delta >= 0) "+" else "\u2212"
                    binding.containerStats.tvDeltaValue.text = sign + String.format(Locale.US, "%.1f", kotlin.math.abs(delta))
                    binding.containerStats.tvDeltaValue.visibility = View.VISIBLE
                } else {
                    binding.containerStats.tvDeltaValue.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.containerStats.tvStreakValue.text = getString(R.string.home_stat_placeholder)
                binding.containerStats.tvAvgValue.text = getString(R.string.home_stat_placeholder)
                binding.containerStats.tvDeltaValue.visibility = View.GONE
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}