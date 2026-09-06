package com.sesac.speechapp.ui.learning

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.sesac.speechapp.BuildConfig
import com.sesac.speechapp.R
import com.sesac.speechapp.data.remote.AuthImageLoader
import com.sesac.speechapp.data.remote.dto.session.ChoiceDto
import com.sesac.speechapp.data.remote.dto.session.TurnDto
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.ActivityProblemBinding
import com.sesac.speechapp.ui.record.RecordingHelper
import kotlinx.coroutines.launch
import java.io.File

/**
 * P3-26 문제 풀이 화면 — 유형별 레이아웃 전환 + D-7 시간 통제 체계 (06 §3).
 *
 * D-8-C2 A-6 (사용자 확정 A안): 단일 Activity 유지 + 내부 상태 기반 뷰 전환.
 *  - GUIDE (가이드 컨테이너) → QUESTION (문항) → SUBMITTED (제출완료) → [다음으로] → GUIDE ...
 *  - ProblemGuideActivity는 앱 흐름에서 제외 (파일·매니페스트는 호환 유지)
 *  - 상단 헤더(X + 진행바 + n/total)는 화면 고정 — 턴이 바뀌어도 리셋 없음 (끊김 해소)
 *  - 단계 전환 시 200ms alpha fade (과한 애니 금지 — 시안에도 없음)
 *
 * D-8-C3 (시안 learn.$themeId 1:1 재작성 — R-0 근본원인 수정):
 *  R-0: A-6(1b2dec5)에서 showTurn의 유형별 render* 호출부가 소실되어 문항 UI 전체가
 *  미표시(데드코드) — enterQuestionPhase에 호출부 복원 + 시안 stage별 표시 시점 정리.
 *
 * - LISTEN: 대기 3초 → 재생 카드("질문을 들려드리고 있어요") → TTS 완료 → answer 단계
 *   (문구 전환 + [다시 듣기] + 선택지 + [제출] + 30초 pill).
 *   정오답 실시간 표시 없음 (사용자 확정 2026-09-06 — 제출 후 선택지 숨김)
 *   30초 도달: 선택 누름=최근 선택지 제출 / 미선택=오답 처리 제출
 * - NAMING: 이미지 + 대기 5초(사진 관찰) → 녹음 카드 표시 + 녹음 시작 → 30초.
 *   힌트 [힌트 보기 (n/2)] — 2회 소진 시 disabled 유지 (시안 NamingStep)
 * - SELF_TALK: 이미지 + 대기 5초 → 녹음 카드 + 녹음 시작 → 30초. [다시 듣기] 없음
 * - SHADOWING: 대기 3초 → 재생 카드("문장을 들려드리고 있어요" — 문장 텍스트 미표시) →
 *   재생 종료 후 3초 → 녹음 카드 + 녹음 시작 → 30초. [다시 듣기] 없음 (기획 06 v1.7 금지)
 * - 음성형: [녹음 완료](카드 내부 fabRecord 단일 버튼) or 30초 도달 → 녹음 컷 → multipart 강제 제출
 * - 제출 카운트다운: LISTEN=pill(tvSubmitCountdown) / 음성형=녹음 카드 상태 문구에 통합
 *   (시안 RecordPanel: "녹음 중이에요 · 남은 시간 N초")
 * - 제출 완료 흐름: 제출 중 → 완료 멘트(DuckSays, 음성형만) → SubmitResult → [다음으로]
 * - 힌트(NAMING): 30초 카운트다운 진행 중에도 요청 가능 (시간 정지 없음)
 * - 타이머: Handler postDelayed — 턴 이동/제출 시 cancel, onDestroy 해제 (누수 방지)
 */
class ProblemActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_TURN_INDEX = "turn_index"

        /** A-6 내부 단계 — GUIDE(가이드 표시) / QUESTION(문항) / SUBMITTED(제출완료) */
        private const val PHASE_GUIDE = 0
        private const val PHASE_QUESTION = 1
        private const val PHASE_SUBMITTED = 2

        /** 06 §3: 제출 제한 30초 통일 (모든 타입) */
        private const val SUBMIT_LIMIT_SECONDS = 30
        /** 대기 카운트다운: LISTEN·SHADOWING 3초 (TTS 전) / NAMING·SELF_TALK 5초 (사진 관찰) */
        private const val WAIT_LISTEN_SECONDS = 3
        private const val WAIT_RECORD_SECONDS = 5
        /** SHADOWING: TTS 재생 종료 후 3초 후 녹음 시작 */
        private const val SHADOWING_PRE_RECORD_SECONDS = 3
        /** D-8-C3: NAMING 힌트 최대 2회 (06 §3) */
        private const val HINT_MAX = 2

        private val TYPE_LABELS = mapOf(
            "LISTEN" to "알아듣기",
            "LISTEN_TEXT" to "알아듣기",
            "LISTEN_PICTURE" to "알아듣기",
            "NAMING" to "이름대기",
            "SHADOWING" to "따라말하기",
            "SELF_TALK" to "스스로말하기",
        )

        fun typeLabel(type: String): String = TYPE_LABELS[type] ?: type
    }

    private lateinit var binding: ActivityProblemBinding
    private lateinit var repository: SessionFlowRepository
    private lateinit var recordingHelper: RecordingHelper

    private var sessionId: Long = -1
    private var turns: List<TurnDto> = emptyList()
    private var currentIndex = 0

    private var mediaPlayer: MediaPlayer? = null
    private var recordedFile: File? = null

    /** D-8-C2 A-2: 마이크 pulse 애니메이터 — 녹음 중에만 활성 */
    private var micPulseAnimator: android.animation.ObjectAnimator? = null
    private var micPulseAnimatorY: android.animation.ObjectAnimator? = null
    private var micPulseAnimatorSet: android.animation.AnimatorSet? = null

    /** D-8-C2 A-6: 현재 내부 단계 — showTurn(GUIDE) → enterQuestionPhase(QUESTION) → showSubmittedState(SUBMITTED) */
    private var currentPhase = PHASE_GUIDE

    /** 현재 턴 제출 진행 상태 — 카운트다운/제출 흐름 제어 */
    private var submittedThisTurn = false
    /** LISTEN: 이번 턴에 유저가 선택한 최근 order (미선택=null) */
    private var selectedChoice: ChoiceDto? = null
    /** D-8-C2 A-5: 이번 제출이 30초 도달 강제 제출인지 — 제출 완료 문구 분기 플래그 */
    private var recordingWasForcedSubmit = false
    /** LISTEN 미선택 오답 제출용 — choices 최대 order + 1 (서버 정답 ref와 절대 일치하지 않는 값) */
    private var noChoiceSentinel = 0
    /** D-8-C3: NAMING 힌트 노출 수 (시안 [힌트 보기 (n/2)] 카운터) */
    private var hintShownCount = 0

    // ─── 타이머 (D-7 1.2 — Handler 기반, 턴 이동/종료 시 cancel) ───
    private val mainHandler = Handler(Looper.getMainLooper())
    private var waitRunnable: Runnable? = null
    private var submitCountdownRunnable: Runnable? = null
    private var shadowingPreRecordRunnable: Runnable? = null

    /** 결과 화면 전달용: 턴별 (type, score) 누적 — 제출 응답의 score 사용 */
    private val turnScores = mutableListOf<Int>()
    private val turnTypes = mutableListOf<String>()

    /**
     * 마이크 권한 런처 — onCreate 이전 등록 필수.
     * (기존: toggleRecording 안에서 registerForActivityResult 호출 → 생명주기 예외로 크래시)
     */
    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleRecording() // 권한 승인 → 녹음 재시작
        } else {
            Toast.makeText(this, getString(R.string.mic_denied_fallback), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SessionFlowRepository(this)
        recordingHelper = RecordingHelper(this) { seconds ->
            binding.tvRecordingTimer.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
        }

        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        val cacheData = SessionFlowCache.get()
        turns = cacheData?.turns ?: emptyList()

        if (sessionId <= 0 || cacheData == null) {
            // 비정상 진입(프로세스 재생성 등) — 홈으로 복귀
            Toast.makeText(this, "세션 정보를 찾을 수 없어요", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.fabRecord.setOnClickListener { toggleRecording() }
        binding.btnSubmitRecording.setOnClickListener { onRecordingSubmitClicked() }
        binding.btnTts.setOnClickListener { playTts() }
        binding.btnHint.setOnClickListener { requestHint() }
        binding.btnNext.setOnClickListener { onNextClicked() }
        // D-8-C2 A-6: 가이드 [준비됐어요!] → 문항 단계 전환 (같은 Activity)
        binding.btnReady.setOnClickListener { enterQuestionPhase() }

        // D-8-C2 A-1: SubmitCountdown 텍스트 중복 제거 — pill 배지 숫자만 갱신 (문항 단계 상시 표시 유지)
        binding.tvSubmitCountdown.visibility = View.GONE
        binding.containerSubmitted.visibility = View.GONE

        val startIndex = intent.getIntExtra(EXTRA_TURN_INDEX, 0)
            .coerceIn(0, (turns.size - 1).coerceAtLeast(0))
        showTurn(startIndex)
    }

    // ─── D-8-C2 A-6: 내부 단계 전환 (GUIDE → QUESTION → SUBMITTED) ───

    /** 단계 전환 + 200ms alpha fade (시안 수준의 절제된 애니 — 과한 애니 금지) */
    private fun setPhase(phase: Int) {
        val questionViews = if (phase == PHASE_GUIDE) View.GONE else View.VISIBLE
        binding.containerGuidePhase.visibility =
            if (phase == PHASE_GUIDE) View.VISIBLE else View.GONE
        binding.tvTypeBadge.visibility = questionViews
        binding.tvPassage.visibility = questionViews
        currentPhase = phase
        // fade: 루트 콘텐츠에 가벼운 alpha 트랜지션
        binding.root.animate().alpha(0.35f).setDuration(80).withEndAction {
            binding.root.animate().alpha(1f).setDuration(120).start()
        }.start()
    }

    /** GUIDE 단계 렌더 — ProblemGuideActivity 로직 이관 (06 §3 문구 그대로) */
    private fun renderGuidePhase(turn: TurnDto, index: Int) {
        binding.tvGuideTypeBadge.text = ProblemActivity.typeLabel(turn.type)

        val (body, extra) = when (turn.type) {
            "LISTEN", "LISTEN_TEXT" -> {
                val bodyText = getString(R.string.guide_listen_body) + "\n\n" +
                    getString(R.string.guide_listen_replay)
                bodyText to getString(R.string.guide_listen_time)
            }
            "LISTEN_PICTURE" -> {
                val bodyText = getString(R.string.guide_listen_body) + "\n\n" +
                    getString(R.string.guide_listen_replay)
                val extraText = getString(R.string.guide_listen_picture_extra) + "\n" +
                    getString(R.string.guide_listen_time)
                bodyText to extraText
            }
            "NAMING" -> getString(R.string.guide_naming_body) to
                getString(R.string.guide_naming_hint) + "\n" + getString(R.string.guide_naming_time)
            "SHADOWING" -> getString(R.string.guide_shadowing_body) to
                getString(R.string.guide_shadowing_replay) + "\n" +
                getString(R.string.guide_shadowing_time)
            "SELF_TALK" -> getString(R.string.guide_selftalk_body) to
                getString(R.string.guide_selftalk_time)
            else -> getString(R.string.guide_selftalk_body) to ""
        }
        binding.tvGuideBody.text = body
        if (extra.isBlank()) {
            binding.containerAudioCard.visibility = View.GONE
        } else {
            binding.tvGuideExtra.text = extra
            binding.containerAudioCard.visibility = View.VISIBLE
        }

        // 문제 이미지 (naming/selftalk만 — listen 제외)
        if (turn.type == "NAMING" || turn.type == "SELF_TALK") {
            val url = turn.imageUrl
            if (!url.isNullOrBlank()) {
                binding.cardGuideImage.visibility = View.VISIBLE
                val full = resolveUrl(url)
                binding.imgGuideProblem.load(full, AuthImageLoader.get(this)) {
                    crossfade(true)
                }
            } else {
                binding.cardGuideImage.visibility = View.GONE
            }
        } else {
            binding.cardGuideImage.visibility = View.GONE
        }
    }

    /** [준비됐어요!] 클릭 — GUIDE → QUESTION 전환 (문항 타이머는 여기서 시작) */
    private fun enterQuestionPhase() {
        if (currentPhase != PHASE_GUIDE) return
        val turn = turns.getOrNull(currentIndex) ?: return
        setPhase(PHASE_QUESTION)
        // D-8-C3 R-0: A-6에서 소실된 유형별 render* 호출부 복원 — 문항 UI 준비.
        // 기존 코드가 없어 문항 화면이 빈 상태였던 것이 사용자 피드백의 근본원인.
        when (turn.type) {
            "LISTEN", "LISTEN_TEXT", "LISTEN_PICTURE" -> renderListen(turn)
            "NAMING" -> renderNaming(turn)
            "SHADOWING" -> renderRecordingTurn(turn, showImage = false)
            "SELF_TALK" -> renderRecordingTurn(turn, showImage = true)
        }
        // 문항 단계 진입 시점에 대기 카운트다운 시작 (가이드 중에는 시간 흐르지 않음)
        when (turn.type) {
            "LISTEN", "LISTEN_TEXT", "LISTEN_PICTURE" -> {
                binding.tvWait.visibility = View.VISIBLE
                startWaitCountdown(WAIT_LISTEN_SECONDS, isListen = true)
            }
            "NAMING" -> {
                binding.tvWait.visibility = View.VISIBLE
                startWaitCountdown(WAIT_RECORD_SECONDS, isListen = false)
            }
            "SHADOWING" -> {
                binding.tvWait.visibility = View.VISIBLE
                startWaitCountdown(WAIT_LISTEN_SECONDS, isListen = true, isShadowing = true)
            }
            else -> {
                binding.tvWait.visibility = View.VISIBLE
                startWaitCountdown(WAIT_RECORD_SECONDS, isListen = false)
            }
        }
    }

    // ─── 턴 렌더 ────────────────────────────────────────────────

    private fun showTurn(index: Int) {
        if (index >= turns.size) {
            goToStorytelling()
            return
        }
        currentIndex = index
        val turn = turns[index]

        // 늦은 콜백 가드용: 턴 전환 시 진행 중인 녹음/미디어/타이머 정리 (지시문 4.5)
        cancelAllTimers()
        stopMicPulse()
        if (recordingHelper.recording) {
            recordingHelper.stop()
            recordedFile = null
        }
        stopTts()

        // 프로그레스 — A-6: 헤더는 화면 고정, 턴 전환에도 리셋 없이 값만 갱신
        binding.tvProgress.text = getString(R.string.progress_turn_fmt, index + 1, turns.size)
        binding.progressBar.progress = ((index + 1) * 100 / turns.size)
        // D-8-C3 S-0: 시안 배지 = "세션명 · 유형명" — 세션명은 캐시 theme 라벨 (계약 무변경)
        binding.tvTypeBadge.text = "${sessionLabel()} · ${typeLabel(turn.type)}"
        // D-8-C3 S-0: 문항 단계 제목 = 시안 유형별 고정 제목 (서버 passage 미표시)
        binding.tvPassage.text = when (turn.type) {
            "LISTEN", "LISTEN_TEXT", "LISTEN_PICTURE" -> getString(R.string.listen_question_title)
            "NAMING" -> getString(R.string.naming_question_title)
            "SHADOWING" -> getString(R.string.shadowing_question_title)
            "SELF_TALK" -> getString(R.string.selftalk_question_title)
            else -> turn.passage ?: ""
        }

        // 기본 상태 초기화 (D-7: 선택/제출 상태·카운트다운 포함)
        submittedThisTurn = false
        selectedChoice = null
        recordingWasForcedSubmit = false
        hintShownCount = 0
        noChoiceSentinel = (turn.choices.orEmpty().maxOfOrNull { it.order } ?: 0) + 1
        resetTurnViews()

        // A-6: 각 턴은 GUIDE 단계부터 시작 — 가이드 렌더 후 [준비됐어요!] 대기
        renderGuidePhase(turn, index)
        setPhase(PHASE_GUIDE)
    }

    private fun resetTurnViews() {
        binding.cardImage.visibility = View.GONE
        binding.tvChoicesTitle.visibility = View.GONE
        binding.containerChoices.visibility = View.GONE
        binding.containerChoices.removeAllViews()
        binding.cardRecord.visibility = View.GONE
        binding.containerRecord.visibility = View.GONE
        binding.containerRecordActions.visibility = View.GONE
        stopMicPulse()
        binding.containerHint.visibility = View.GONE
        binding.tvHint.visibility = View.GONE
        binding.btnHint.visibility = View.GONE
        binding.btnHint.isEnabled = true
        binding.tvHint.text = ""
        binding.tvSubmitCountdown.visibility = View.GONE
        binding.containerSubmitted.visibility = View.GONE
        binding.btnTts.visibility = View.GONE
        binding.containerAudioPlay.visibility = View.GONE
        binding.tvAudioPlayStatus.text = ""
        // D-8-C2 A-1: 재진입 시 INVISIBLE 잔존 상태 초기화 (GONE 복귀 — 다음 턴 대기 카드 정상 표시)
        binding.tvWait.visibility = View.GONE
        recordedFile = null
        stopTts()
    }

    // ─── D-8-C2 A-2: 마이크 pulse 애니메이션 (시안 animate-pulse 근사) ───

    /** 녹음 시작 시 pulse — 원형 프레임 scale 1.0↔1.08 왕복 (시니어 저강도) */
    private fun startMicPulse() {
        stopMicPulse()
        val pulse = android.animation.ObjectAnimator.ofFloat(
            binding.frameMicPulse, View.SCALE_X, 1f, 1.08f
        ).apply {
            duration = 700
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        val pulseY = android.animation.ObjectAnimator.ofFloat(
            binding.frameMicPulse, View.SCALE_Y, 1f, 1.08f
        ).apply {
            duration = 700
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        android.animation.AnimatorSet().apply {
            playTogether(pulse, pulseY)
            start()
        }.also { micPulseAnimatorSet = it }
        micPulseAnimator = pulse
        micPulseAnimatorY = pulseY
    }

    /** pulse 정지 + 스케일 원복 — 녹음 종료·제출 완료·턴 전환 */
    private fun stopMicPulse() {
        micPulseAnimatorSet?.cancel()
        micPulseAnimatorSet = null
        micPulseAnimator?.cancel()
        micPulseAnimator = null
        micPulseAnimatorY?.cancel()
        micPulseAnimatorY = null
        if (this::binding.isInitialized) {
            binding.frameMicPulse.scaleX = 1f
            binding.frameMicPulse.scaleY = 1f
        }
    }

    /**
     * LISTEN 문항 UI 준비 — D-8-C3 시안 ListenStep:
     * answer 단계(TTS 완료)까지 선택지·[제출]을 숨기고, 재생 카드와 문구만 준비.
     * 표시 시점: 선택지/pill/다시듣기 = startAnswerStage, 재생 카드 = startPlayStage.
     * 제출 후 정오답 표시 없음 (사용자 확정 — 정오답 색 drawable은 이번에 미사용).
     */
    private fun renderListen(turn: TurnDto) {
        // 문항 단계에서는 서버 passage(질문 텍스트)를 화면에 표시하지 않음 (시안 — 듣기 문제)
        binding.tvPassage.visibility = View.GONE
        // 재생 카드 (시안: 원형 64dp gradient + 스피커 + 상태 문구) — play 단계에서 표시
        binding.tvAudioPlayStatus.text = getString(R.string.listen_play_now)
        // 선택지 데이터는 answer 단계에서 채움 — 여기서는 컨테이너만 준비
        fillListenChoices(turn)
    }

    /** 선택지 행 생성 — answer 진입 시 표시 (시안: stage answer에서 리스트 렌더) */
    private fun fillListenChoices(turn: TurnDto) {
        val choices = turn.choices.orEmpty()
        // D-8-C1 시안: 이미지 모드 = 2열 그리드 / 텍스트 모드 = 세로 행
        val isImageMode = choices.isNotEmpty() && choices.all { it.mediaType.equals("image", ignoreCase = true) }
        binding.containerChoices.orientation =
            if (isImageMode) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL
        choices.forEach { choice ->
            val isImage = choice.mediaType.equals("image", ignoreCase = true)
            val item = layoutInflater.inflate(
                R.layout.item_listen_choice, binding.containerChoices, false
            )
            if (isImageMode) {
                val lp = item.layoutParams as? android.widget.LinearLayout.LayoutParams
                    ?: android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.width = 0
                lp.weight = 1f
                item.layoutParams = lp
            }
            val tvText = item.findViewById<TextView>(R.id.tvChoiceText)
            val ivImage = item.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.ivChoiceImage)

            if (isImage) {
                // 이미지형: context = image_id → 콘텐츠 프록시 URL
                tvText.visibility = View.GONE
                ivImage.visibility = View.VISIBLE
                val imgUrl = if (choice.context.startsWith("http")) choice.context
                else resolveUrl("/api/v1/content/images/${choice.context}/file")
                ivImage.load(imgUrl, AuthImageLoader.get(this)) {
                    crossfade(true)
                }
            } else {
                tvText.visibility = View.VISIBLE
                ivImage.visibility = View.GONE
                tvText.text = choice.context
            }
            item.setOnClickListener {
                // 시안 선택 상태: bg_choice_selected (primary 보더 + secondary 배경)
                for (i in 0 until binding.containerChoices.childCount) {
                    binding.containerChoices.getChildAt(i).setBackgroundResource(R.drawable.bg_question_card)
                }
                item.setBackgroundResource(R.drawable.bg_choice_selected)
                onChoiceSelected(choice)
            }
            binding.containerChoices.addView(item)
        }
    }

    /**
     * LISTEN play 단계 (시안 stage "play") — 대기 종료 직후: 재생 카드 + TTS 재생.
     * tvPassage는 문항 단계에서 미표시(듣기 문제) — setPhase가 VISIBLE로 전환한 것을 다시 숨김.
     */
    private fun startPlayStage() {
        binding.tvPassage.visibility = View.GONE
        binding.tvWait.visibility = View.INVISIBLE
        binding.containerAudioPlay.visibility = View.VISIBLE
        binding.tvAudioPlayStatus.text = getString(R.string.listen_play_now)
        binding.btnTts.visibility = View.GONE
    }

    /**
     * LISTEN answer 단계 (시안 stage "answer") — TTS 완료 직후:
     * 재생 카드 문구 전환 + [다시 듣기] + 선택지 + [제출] + 30초 pill.
     */
    private fun startAnswerStage() {
        binding.containerAudioPlay.visibility = View.VISIBLE
        binding.tvAudioPlayStatus.text = getString(R.string.listen_play_done)
        binding.btnTts.visibility = View.VISIBLE
        binding.tvChoicesTitle.visibility = View.VISIBLE
        binding.containerChoices.visibility = View.VISIBLE
        binding.containerRecordActions.visibility = View.VISIBLE
        binding.btnHint.visibility = View.GONE
        binding.btnSubmitRecording.text = getString(R.string.btn_listen_submit)
        binding.btnSubmitRecording.isEnabled = false
    }

    /** NAMING — 5초 사진 관찰 → 녹음 시작. 힌트는 카운트다운 중에도 가능.
     *  D-8-C3: 힌트 라벨을 시안식 카운터로 (hintShownCount는 requestHint에서 갱신) */
    private fun renderNaming(turn: TurnDto) {
        showImage(turn)
        updateHintButtonLabel()
        // 녹음 카드는 녹음 시작 시점(startRecordingAuto)에 표시 — 시안 stage wait에는
        // 이미지+카운트다운만 존재
    }

    private fun renderRecordingTurn(turn: TurnDto, showImage: Boolean) {
        if (showImage) showImage(turn)
        // D-8-C3: [다시 듣기](btnTts)는 LISTEN 전용 — 음성형 렌더에서 표시하지 않음
        // (시안 SpontaneousStep/NamingStep에 재생 버튼 없음. SHADOWING은 기획 06 v1.7 금지)
    }

    private fun showImage(turn: TurnDto) {
        val url = turn.imageUrl ?: return
        binding.cardImage.visibility = View.VISIBLE
        val fullUrl = resolveUrl(url)
        binding.ivProblem.load(fullUrl, AuthImageLoader.get(this)) {
            placeholder(R.drawable.ic_person_24)
            error(R.drawable.ic_person_24)
            crossfade(true)
        }
    }

    /** 녹음 카드 UI — 시안 RecordPanel. 녹음 시작 시점에만 호출 (D-8-C3 표시 시점 정리) */
    private fun showRecordingUI() {
        binding.cardRecord.visibility = View.VISIBLE
        binding.containerRecord.visibility = View.VISIBLE
        binding.containerRecordActions.visibility = View.GONE // 보조행 폐지 — [녹음 완료]는 카드 내부 fabRecord 단일 버튼
        binding.tvRecordingStatus.text = getString(R.string.recording_now)
        binding.tvRecordingTimer.text = getString(R.string.recording_timer_default)
    }

    /** NAMING 힌트 버튼 라벨 — 시안 [힌트 보기 (n/2)] 카운터 (2회 소진 시 disabled) */
    private fun updateHintButtonLabel() {
        binding.btnHint.text = getString(R.string.btn_hint_d8c, hintShownCount)
        binding.btnHint.isEnabled = hintShownCount < HINT_MAX
    }

    // ─── 대기 카운트다운 (3초/5초) ────────────────────────────────

    /**
     * 대기 카운트다운 — 종료 직후 TTS 재생(LISTEN·SHADOWING) 또는 녹음 시작(음성형).
     * D-8-C2 A-1: 카운트다운 숫자는 버블 원형(tvWaitNumber) 하나로 통일 —
     * 메시지(tvWaitMessage)는 strings_d8c wait_*_card_fmt (숫자 없는 고정 문구) 사용.
     * 종료 시 GONE 대신 INVISIBLE — 공간 유지로 텍스트·이미지 점프 방지 (사용자 확정).
     */
    private fun startWaitCountdown(seconds: Int, isListen: Boolean, isShadowing: Boolean = false) {
        var remaining = seconds
        // D-8-C2 A-1: 메시지에서 카운트다운 숫자 제거 — 버블 숫자만 갱신
        val fmt = when {
            isListen && isShadowing -> R.string.wait_shadow_card_fmt
            isListen -> R.string.wait_listen_card_fmt
            seconds >= WAIT_RECORD_SECONDS -> R.string.wait_record_card_fmt
            else -> R.string.wait_record2_card_fmt
        }
        binding.tvWaitNumber.text = remaining.toString()
        binding.tvWaitMessage.text = getString(fmt)

        waitRunnable = object : Runnable {
            override fun run() {
                remaining--
                if (remaining > 0) {
                    binding.tvWaitNumber.text = remaining.toString()
                    mainHandler.postDelayed(this, 1000)
                } else {
                    // A-1: INVISIBLE — 공간 유지 (GONE이면 아래 콘텐츠가 점프)
                    binding.tvWait.visibility = View.INVISIBLE
                    onWaitFinished()
                }
            }
        }
        mainHandler.postDelayed(waitRunnable!!, 1000)
    }

    private fun onWaitFinished() {
        val turn = turns.getOrNull(currentIndex) ?: return
        when (turn.type) {
            "LISTEN", "LISTEN_TEXT", "LISTEN_PICTURE" -> {
                // D-8-C3 시안 ListenStep: play 단계 — 재생 카드 표시 후 TTS, 완료 시 answer 단계
                startPlayStage()
                playTtsWithCompletion { startAnswerStage(); startSubmitCountdown() }
            }
            "SHADOWING" -> {
                // D-8-C3 시안 RepeatStep: play 단계 — 재생 카드("문장을 들려드리고 있어요")만,
                // 문장 텍스트 미표시. TTS 완료 → 3초 재대기 → 녹음 (기존 체계 유지)
                startPlayStage()
                playTtsWithCompletion { startShadowingPreRecord() }
            }
            "NAMING", "SELF_TALK" -> {
                // 시안 stage record 진입 — 녹음 카드 표시와 녹음 시작이 동일 시점
                showRecordingUI()
                startRecordingAuto()
            }
        }
    }

    /**
     * SHADOWING 전용: TTS 재생 종료 → 3초 후 녹음 시작 (06 §3).
     * D-8-C2 A-3: playTtsWithCompletion이 SHADOWING의 postDelay(3초)를 담당하므로
     * LISTEN도 이 함수를 재사용하되 콜백에서 즉시(추가 지연 없이) 카운트다운 시작.
     */
    private fun playTtsWithCompletion(onComplete: () -> Unit) {
        val turn = turns.getOrNull(currentIndex) ?: return
        val ttsUrl = turn.ttsUrl ?: run {
            onComplete()
            return
        }
        stopTts()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(resolveUrl(ttsUrl))
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    releasePlayer()
                    if (turns.getOrNull(currentIndex)?.type?.startsWith("LISTEN") == true) {
                        // D-8-C2 A-3: LISTEN — TTS 완료 즉시 카운트다운 (3초 재대기 없음)
                        onComplete()
                    } else {
                        // SHADOWING — 재생 종료 후 3초 재대기 후 녹음 (기존 구현 유지)
                        shadowingPreRecordRunnable = Runnable { onComplete() }.also {
                            mainHandler.postDelayed(it, SHADOWING_PRE_RECORD_SECONDS * 1000L)
                        }
                    }
                }
                prepareAsync()
            } catch (e: Exception) {
                Toast.makeText(this@ProblemActivity, "음성 재생 실패", Toast.LENGTH_SHORT).show()
                releasePlayer()
                onComplete()
            }
        }
    }

    private fun startShadowingPreRecord() {
        if (submittedThisTurn) return
        startRecordingAuto()
    }

    /** 녹음 자동 시작 (대기 카운트다운 종료 후) — 권한 있으면 바로 */
    private fun startRecordingAuto() {
        if (!recordingHelper.hasPermission()) {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (recordingHelper.start()) {
            // 시안 RecordPanel: 카드 내부 [녹음 완료] 단일 버튼 — text 전환으로 상태 표시
            binding.fabRecord.text = getString(R.string.btn_recording_stop)
            binding.fabRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.error)
            )
            binding.tvRecordingStatus.text = getString(R.string.recording_now)
            startMicPulse()
            startSubmitCountdown()
        } else {
            Toast.makeText(this, "녹음 시작 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── 제출 카운트다운 30초 (모든 타입 공통) ──────────────────────

    /**
     * 제출 카운트다운 30초 — D-8-C3: 시안에서 LISTEN=SubmitCountdown pill /
     * 음성형=RecordPanel 내부 상태 문구("녹음 중이에요 · 남은 시간 N초")로 분기 표시.
     * 도달 시: LISTEN=CASE 2/3 강제 제출 / 음성형=녹음 컷 → multipart 강제 제출.
     */
    private fun startSubmitCountdown() {
        if (submittedThisTurn) return
        var remaining = SUBMIT_LIMIT_SECONDS
        val turn = turns.getOrNull(currentIndex)
        val isListen = turn?.type?.startsWith("LISTEN") == true
        if (isListen) {
            binding.tvSubmitCountdown.visibility = View.VISIBLE
            binding.tvSubmitCountdown.text = getString(R.string.submit_countdown_fmt, remaining)
            applySubmitCountdownTone(remaining)
        } else {
            binding.tvSubmitCountdown.visibility = View.GONE
            binding.tvRecordingStatus.text = getString(R.string.record_panel_fmt, remaining)
        }

        submitCountdownRunnable = object : Runnable {
            override fun run() {
                if (submittedThisTurn) return
                remaining--
                if (remaining > 0) {
                    if (isListen) {
                        binding.tvSubmitCountdown.text = getString(R.string.submit_countdown_fmt, remaining)
                        applySubmitCountdownTone(remaining)
                    } else {
                        binding.tvRecordingStatus.text = getString(R.string.record_panel_fmt, remaining)
                    }
                    mainHandler.postDelayed(this, 1000)
                } else {
                    onSubmitTimeUp()
                }
            }
        }
        mainHandler.postDelayed(submitCountdownRunnable!!, 1000)
    }

    /**
     * D-8-C1 시안 SubmitCountdown: 남은 10초 이하 = destructive 보더+배경 10%+글자 (색 반전 강조).
     */
    private fun applySubmitCountdownTone(remaining: Int) {
        if (remaining <= 10) {
            binding.tvSubmitCountdown.setBackgroundResource(R.drawable.bg_grad_brand_r22_error)
            binding.tvSubmitCountdown.setTextColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.error)
            )
        } else {
            binding.tvSubmitCountdown.setBackgroundResource(R.drawable.bg_countdown_pill)
            binding.tvSubmitCountdown.setTextColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
            )
        }
    }

    /** 30초 도달 — 타입별 강제 제출 (06 §3). D-8-C2 A-5: 강제 제출은 타임오버 문구 표시 */
    private fun onSubmitTimeUp() {
        if (submittedThisTurn) return
        val turn = turns.getOrNull(currentIndex) ?: return
        when (turn.type) {
            "LISTEN", "LISTEN_TEXT", "LISTEN_PICTURE" -> {
                // CASE 2: 선택 누름=마지막 선택지로 제출 / CASE 3: 미선택=오답(0) 제출
                val choice = selectedChoice
                if (choice != null) {
                    submitListen(choice.order, byTimeout = true)
                } else {
                    // 미선택 자동 제출도 사용자 선택 없이 흘러간 것 — 강제 제출로 간주 (A-5)
                    submitListen(noChoiceSentinel, byTimeout = true)
                }
            }
            "NAMING", "SHADOWING", "SELF_TALK" -> forceSubmitRecording()
        }
    }

    /** 음성형 30초 도달 — 녹음 컷 → multipart 강제 제출 (RecordingHelper.stop+파일 확보) */
    private fun forceSubmitRecording() {
        if (recordingHelper.recording) {
            recordedFile = recordingHelper.stop()
            stopMicPulse()
        }
        val file = recordedFile
        if (file == null || !file.exists()) {
            // 녹음 파일 없음 — 오답 방어: 빈 파일 제출 불가, 다음 턴으로는 진행 불가(계약 유지)
            // 서버 계약상 multipart 필수 — 파일 없으면 제출 스킵 후 안내 (재시도 불가 케이스 — 보고서 기록)
            Toast.makeText(this, "녹음 파일이 준비되지 않았어요", Toast.LENGTH_SHORT).show()
            return
        }
        recordingWasForcedSubmit = true
        submitRecording()
    }

    // ─── 상호작용 ────────────────────────────────────────────────

    /** LISTEN 선택지 탭 — 선택만 하고 제출은 [제출] (06 §3 버튼 분리) */
    private fun onChoiceSelected(choice: ChoiceDto) {
        if (submittedThisTurn) return
        selectedChoice = choice
        // 선택 완료 → [제출] 활성 (answer 단계에서만 존재하는 버튼)
        binding.btnSubmitRecording.isEnabled = true
    }

    /** 음성형: 카드 내부 [녹음 완료](fabRecord) 토글 / LISTEN: [제출] */
    private fun onRecordingSubmitClicked() {
        val turn = turns.getOrNull(currentIndex) ?: return
        if (turn.type == "LISTEN" || turn.type == "LISTEN_TEXT" || turn.type == "LISTEN_PICTURE") {
            val choice = selectedChoice
            if (choice != null) submitListen(choice.order)
            return
        }
        toggleRecording()
    }

    private fun toggleRecording() {
        if (recordingHelper.recording) {
            recordedFile = recordingHelper.stop()
            stopMicPulse()
            // 녹음 종료 — 버튼 원복
            binding.fabRecord.text = getString(R.string.btn_recording_start)
            binding.fabRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.primary)
            )
            submitRecording()
        } else {
            if (!recordingHelper.hasPermission()) {
                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            if (recordingHelper.start()) {
                // 녹음 중 확실한 표시 — 버튼을 [녹음 완료]로 전환 (시니어 UI)
                binding.fabRecord.text = getString(R.string.btn_recording_stop)
                binding.fabRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.error)
                )
                binding.tvRecordingStatus.text = getString(R.string.recording_now)
                startMicPulse()
                startSubmitCountdown()
            } else {
                Toast.makeText(this, "녹음 시작 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * LISTEN 제출 — 제출 후 정오답 표시 없음 (사용자 확정): 선택지·pill 즉시 숨김,
     * SubmitResult(체크+답안이 제출되었어요+[다음으로])만 표시.
     */
    private fun submitListen(selected: Int, byTimeout: Boolean = false) {
        if (submittedThisTurn) return
        submittedThisTurn = true
        val turn = turns[currentIndex]
        cancelSubmitCountdown()
        hideListenAnswerUi()
        showSubmitProgress()

        lifecycleScope.launch {
            try {
                val data = repository.submitListen(sessionId, turn.turnId, selected)
                turnScores.add(data.score)
                turnTypes.add(turn.type)
                showSubmittedState(byTimeout)
            } catch (e: Exception) {
                submittedThisTurn = false
                hideSubmitProgress()
                Toast.makeText(this@ProblemActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 제출 시작 직후 LISTEN 문항 UI 정리 — 시안은 제출 후 선택지 블록이 사라짐 */
    private fun hideListenAnswerUi() {
        binding.containerChoices.visibility = View.GONE
        binding.tvChoicesTitle.visibility = View.GONE
        binding.containerRecordActions.visibility = View.GONE
        binding.btnTts.visibility = View.GONE
    }

    private fun submitRecording() {
        val file = recordedFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "녹음 파일이 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        val turn = turns[currentIndex]
        cancelSubmitCountdown()
        showSubmitProgress()
        // D-8-C2 A-5: 강제 제출(30초 도달) 경로로 진입했는지 플래그 — 제출 완료 문구 분기용
        val recordingWasForced = recordingWasForcedSubmit
        recordingWasForcedSubmit = false

        lifecycleScope.launch {
            try {
                val data = when (turn.type) {
                    "NAMING" -> repository.submitNaming(sessionId, turn.turnId, file)
                    "SHADOWING" -> repository.submitShadowing(sessionId, turn.turnId, file)
                    "SELF_TALK" -> repository.submitSelfTalk(sessionId, turn.turnId, file)
                    else -> throw IllegalStateException("녹음 제출이 없는 유형: ${turn.type}")
                }
                turnScores.add(data.score.toInt())
                turnTypes.add(turn.type)
                showSubmittedState(byTimeout = recordingWasForced)
            } catch (e: Exception) {
                hideSubmitProgress()
                Toast.makeText(this@ProblemActivity, e.message, Toast.LENGTH_SHORT).show()
                // 재제출 가능 상태 유지 (녹음 파일은 이미 확보됨 — 다시 [녹음 완료] 가능)
                submittedThisTurn = false
            }
        }
    }

    /** 제출 중 상태: "답안을 제출 중이에요" (06 §3 제출 완료 흐름 1단계) */
    private fun showSubmitProgress() {
        binding.scoringOverlay.visibility = View.VISIBLE
    }

    /**
     * 제출 완료 상태 — 시안 SubmitResult(체크 원형 + "답안이 제출되었어요" + [다음으로]).
     * D-8-C3 사용자 확정: 정오답 실시간 표시 없음 — 음성형은 완료 멘트(DuckSays)만,
     * 타임오버 강제 제출은 기존 "이런! 시간이 초과되었어요!" 문구 유지 (A-5).
     */
    private fun showSubmittedState(byTimeout: Boolean = false) {
        binding.scoringOverlay.visibility = View.GONE
        binding.tvSubmitCountdown.visibility = View.GONE
        // 음성형 완료 멘트 (시안 DuckSays — 유형별 문구) — LISTEN은 멘트 없음 (사용자 확정)
        val turn = turns.getOrNull(currentIndex)
        val doneMsg = when (turn?.type) {
            "NAMING" -> getString(R.string.duck_naming_done)
            "SHADOWING" -> getString(R.string.duck_repeat_done)
            "SELF_TALK" -> getString(R.string.duck_selftalk_done)
            else -> null
        }
        if (doneMsg != null && !byTimeout) {
            binding.tvDuckDoneMessage.text = doneMsg
            binding.containerDuckDone.visibility = View.VISIBLE
        } else {
            binding.containerDuckDone.visibility = View.GONE
        }
        binding.tvSubmittedStatus.text = getString(
            if (byTimeout) R.string.submit_timeout_msg else R.string.submitted_answer
        )
        currentPhase = PHASE_SUBMITTED
        binding.containerSubmitted.visibility = View.VISIBLE
        binding.cardRecord.visibility = View.GONE
        binding.containerRecord.visibility = View.GONE
        binding.containerRecordActions.visibility = View.GONE
        binding.containerChoices.visibility = View.GONE
        binding.tvChoicesTitle.visibility = View.GONE
        binding.containerHint.visibility = View.GONE
        binding.containerAudioPlay.visibility = View.GONE
        stopMicPulse()
    }

    private fun hideSubmitProgress() {
        binding.scoringOverlay.visibility = View.GONE
    }

    /**
     * [다음으로] — A-6: 같은 Activity 내에서 다음 턴 가이드 단계로 전환.
     * activity 이동(기존 ProblemGuideActivity startActivity+finish) 폐지 — 화면 고정 유지.
     */
    private fun onNextClicked() {
        goToNextTurn()
    }

    private fun goToNextTurn() {
        showTurn(currentIndex + 1) // A-6: 내부 전환 — 마지막 턴이면 showTurn이 goToStorytelling 처리
    }

    private fun requestHint() {
        val turnIndex = currentIndex
        val turn = turns[turnIndex]
        binding.btnHint.isEnabled = false
        lifecycleScope.launch {
            try {
                val hint = repository.requestHint(sessionId, turn.turnId)
                // 늦은 응답 가드: 턴이 이미 넘어갔으면 UI 갱신 무시 (새 턴의 버튼 상태 오염 방지)
                if (turnIndex != currentIndex) return@launch
                val label = if (hint.hintOrder == 1) getString(R.string.hint_semantic_label)
                else getString(R.string.hint_articulatory_label)
                val existing = binding.tvHint.text?.toString().orEmpty()
                binding.tvHint.text = if (existing.isBlank()) "$label: ${hint.text}"
                else "$existing\n$label: ${hint.text}"
                // D-8-C3 R-0 수정: tvHint만 VISIBLE한 버그 — 부모 containerHint를 함께 표시
                // (기존: containerHint GONE 방치 → 힌트 요청해도 화면에 안 보임)
                binding.containerHint.visibility = View.VISIBLE
                binding.tvHint.visibility = View.VISIBLE
                hintShownCount = hint.hintOrder.coerceAtLeast(hintShownCount)

                // 힌트 소진 (2개) → 시안대로 disabled 유지 (라벨 n/2 카운터 유지)
                updateHintButtonLabel()
            } catch (e: Exception) {
                if (turnIndex == currentIndex) {
                    Toast.makeText(this@ProblemActivity, e.message, Toast.LENGTH_SHORT).show()
                    updateHintButtonLabel()
                }
            }
        }
    }

    private fun playTts() {
        val turn = turns.getOrNull(currentIndex) ?: return
        val ttsUrl = turn.ttsUrl ?: return
        stopTts()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(resolveUrl(ttsUrl))
                setOnPreparedListener { it.start() }
                setOnCompletionListener { releasePlayer() }
                prepareAsync()
            } catch (e: Exception) {
                Toast.makeText(this@ProblemActivity, "음성 재생 실패", Toast.LENGTH_SHORT).show()
                releasePlayer()
            }
        }
    }

    private fun stopTts() {
        mediaPlayer?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        mediaPlayer = null
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun resolveUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = BuildConfig.SERVER_BASE_URL.trimEnd('/')
        val relative = if (path.startsWith("/")) path else "/$path"
        return base + relative
    }

    /**
     * D-8-C3 S-0: 세션명 라벨 — 시안 배지 "세션명 · 유형명"용.
     * 캐시의 theme 필드로 산출 (서버 DTO 무변경 — LearningSessionLoadingActivity와 동일 매핑).
     */
    private fun sessionLabel(): String = when (SessionFlowCache.get()?.theme?.uppercase()) {
        "CAFE" -> getString(R.string.theme_cafe_title)
        "HOSPITAL" -> getString(R.string.theme_hospital_title)
        else -> getString(R.string.session_type_today)
    }

    // ─── 타이머 해제 (지시문 4.5 — onDestroy + 턴 이동 시 cancel) ──────

    private fun cancelWaitTimer() {
        waitRunnable?.let { mainHandler.removeCallbacks(it) }
        waitRunnable = null
    }

    private fun cancelSubmitCountdown() {
        submitCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
        submitCountdownRunnable = null
    }

    private fun cancelShadowingPreRecord() {
        shadowingPreRecordRunnable?.let { mainHandler.removeCallbacks(it) }
        shadowingPreRecordRunnable = null
    }

    private fun cancelAllTimers() {
        cancelWaitTimer()
        cancelSubmitCountdown()
        cancelShadowingPreRecord()
    }

    // ─── 전환 ────────────────────────────────────────────────

    private fun goToStorytelling() {
        val intent = Intent(this, StorytellingActivity::class.java)
            .putExtra(StorytellingActivity.EXTRA_SESSION_ID, sessionId)
            .putIntegerArrayListExtra(SessionReportActivity.EXTRA_TURN_SCORES, ArrayList(turnScores))
            .putStringArrayListExtra(SessionReportActivity.EXTRA_TURN_TYPES, ArrayList(turnTypes))
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAllTimers()
        stopMicPulse()
        recordingHelper.release()
        releasePlayer()
    }
}