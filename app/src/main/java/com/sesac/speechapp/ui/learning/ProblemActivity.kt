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
 * - LISTEN: 대기 카운트다운 3초 → TTS → 선택지 탭 → [제출] (30초 제한)
 *   30초 도달: 선택 누름=최근 선택지 제출 / 미선택=오답 처리 제출
 * - NAMING/SELF_TALK: 대기 카운트다운 5초(사진 관찰) → 녹음 시작 → 30초
 * - SHADOWING: 대기 3초 → TTS → 재생 종료 후 3초 → 녹음 시작 → 30초. [다시 듣기] 없음
 * - 음성형: [녹음 완료] or 30초 도달 → 녹음 컷 → multipart 강제 제출
 * - 제출 완료 흐름: 제출 중 → 제출 완료 → [다음으로] (제출/이동 버튼 분리)
 * - 힌트(NAMING): 30초 카운트다운 진행 중에도 요청 가능 (시간 정지 없음)
 * - 타이머: Handler postDelayed — 턴 이동/제출 시 cancel, onDestroy 해제 (누수 방지)
 */
class ProblemActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_TURN_INDEX = "turn_index"

        /** 06 §3: 제출 제한 30초 통일 (모든 타입) */
        private const val SUBMIT_LIMIT_SECONDS = 30
        /** 대기 카운트다운: LISTEN·SHADOWING 3초 (TTS 전) / NAMING·SELF_TALK 5초 (사진 관찰) */
        private const val WAIT_LISTEN_SECONDS = 3
        private const val WAIT_RECORD_SECONDS = 5
        /** SHADOWING: TTS 재생 종료 후 3초 후 녹음 시작 */
        private const val SHADOWING_PRE_RECORD_SECONDS = 3

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

    /** 현재 턴 제출 진행 상태 — 카운트다운/제출 흐름 제어 */
    private var submittedThisTurn = false
    /** LISTEN: 이번 턴에 유저가 선택한 최근 order (미선택=null) */
    private var selectedChoice: ChoiceDto? = null
    /** LISTEN 미선택 오답 제출용 — choices 최대 order + 1 (서버 정답 ref와 절대 일치하지 않는 값) */
    private var noChoiceSentinel = 0

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
            binding.tvTimer.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
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

        // D-7 1.2: 제출 카운트다운 표시 + 제출 완료 상태 영역
        binding.tvSubmitCountdown.visibility = View.GONE
        binding.containerSubmitted.visibility = View.GONE

        val startIndex = intent.getIntExtra(EXTRA_TURN_INDEX, 0)
            .coerceIn(0, (turns.size - 1).coerceAtLeast(0))
        showTurn(startIndex)
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
        if (recordingHelper.recording) {
            recordingHelper.stop()
            recordedFile = null
        }
        stopTts()

        // 프로그레스
        binding.tvProgress.text = getString(R.string.progress_turn_fmt, index + 1, turns.size)
        binding.progressBar.progress = ((index + 1) * 100 / turns.size)
        binding.tvTypeBadge.text = typeLabel(turn.type)
        // SELF_TALK: 지문 고정 문구 (사용자 확정) — 스텁 상황 설명은 이미지가 담당
        binding.tvPassage.text = if (turn.type == "SELF_TALK") "다음 상황을 보고 묘사해보세요"
        else turn.passage ?: ""

        // 기본 상태 초기화 (D-7: 선택/제출 상태·카운트다운 포함)
        submittedThisTurn = false
        selectedChoice = null
        noChoiceSentinel = (turn.choices.orEmpty().maxOfOrNull { it.order } ?: 0) + 1
        resetTurnViews()

        when (turn.type) {
            "LISTEN", "LISTEN_TEXT", "LISTEN_PICTURE" -> renderListen(turn)
            "NAMING" -> renderNaming(turn)
            "SHADOWING" -> renderRecordingTurn(turn, showImage = false)
            "SELF_TALK" -> renderRecordingTurn(turn, showImage = true)
            else -> renderRecordingTurn(turn, showImage = false)
        }
    }

    private fun resetTurnViews() {
        binding.cardImage.visibility = View.GONE
        binding.tvChoicesTitle.visibility = View.GONE
        binding.containerChoices.visibility = View.GONE
        binding.containerChoices.removeAllViews()
        binding.containerRecord.visibility = View.GONE
        binding.tvHint.visibility = View.GONE
        binding.btnHint.visibility = View.GONE
        binding.tvHint.text = ""
        binding.tvSubmitCountdown.visibility = View.GONE
        binding.containerSubmitted.visibility = View.GONE
        binding.btnTts.visibility = View.GONE
        binding.tvWait.visibility = View.GONE
        recordedFile = null
        stopTts()
    }

    /** LISTEN — 대기 카운트다운 3초 후 TTS, 선택지 탭 → 제출 상태 진입 */
    private fun renderListen(turn: TurnDto) {
        binding.tvChoicesTitle.visibility = View.VISIBLE
        binding.containerChoices.visibility = View.VISIBLE

        val choices = turn.choices.orEmpty()
        choices.forEach { choice ->
            val isImage = choice.mediaType.equals("image", ignoreCase = true)
            val item = layoutInflater.inflate(
                R.layout.item_listen_choice, binding.containerChoices, false
            )
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
                // D-8③ 시안 선택 상태: secondary 배경 강조 (선택지 카드들 원복 후 적용)
                for (i in 0 until binding.containerChoices.childCount) {
                    binding.containerChoices.getChildAt(i).setBackgroundColor(
                        androidx.core.content.ContextCompat.getColor(this@ProblemActivity, android.R.color.transparent)
                    )
                }
                item.setBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(this@ProblemActivity, R.color.brand_secondary)
                )
                onChoiceSelected(choice)
            }
            binding.containerChoices.addView(item)
        }

        // D-7 1.2: 대기 카운트다운 3초 → 종료 직후 TTS 재생
        binding.tvWait.visibility = View.VISIBLE
        startWaitCountdown(WAIT_LISTEN_SECONDS, isListen = true)
    }

    /** NAMING — 5초 사진 관찰 → 녹음 시작. 힌트는 카운트다운 중에도 가능 */
    private fun renderNaming(turn: TurnDto) {
        showImage(turn)
        showRecordingUI(showHintButton = true)
        binding.tvWait.visibility = View.VISIBLE
        startWaitCountdown(WAIT_RECORD_SECONDS, isListen = false)
    }

    private fun renderRecordingTurn(turn: TurnDto, showImage: Boolean) {
        if (showImage) showImage(turn)
        showRecordingUI(showHintButton = false)
        if (turn.type == "SHADOWING") {
            // SHADOWING: 3초 → TTS 재생 → 재생 종료 후 3초 → 녹음 시작. [다시 듣기] 없음 (마이크 오염 방지)
            binding.btnTts.visibility = View.GONE
            binding.tvWait.visibility = View.VISIBLE
            startWaitCountdown(WAIT_LISTEN_SECONDS, isListen = true)
        } else {
            // SELF_TALK: 5초 사진 관찰 → 녹음 시작
            binding.tvWait.visibility = View.VISIBLE
            startWaitCountdown(WAIT_RECORD_SECONDS, isListen = false)
            if (turn.ttsUrl != null) binding.btnTts.visibility = View.VISIBLE
        }
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

    private fun showRecordingUI(showHintButton: Boolean) {
        binding.containerRecord.visibility = View.VISIBLE
        binding.btnHint.visibility = if (showHintButton) View.VISIBLE else View.GONE
        binding.btnSubmitRecording.isEnabled = false
        binding.btnSubmitRecording.text = getString(R.string.btn_recording_start)
        binding.tvRecordingHint.text = getString(R.string.recording_now)
        binding.tvTimer.text = getString(R.string.recording_timer_default)
    }

    // ─── 대기 카운트다운 (3초/5초) ────────────────────────────────

    /**
     * 대기 카운트다운 — 종료 직후 TTS 재생(LISTEN·SHADOWING) 또는 녹음 시작(음성형).
     * 시니어 UI: 큰 숫자 카운트 + 안내 문구 (06 §3).
     */
    private fun startWaitCountdown(seconds: Int, isListen: Boolean) {
        var remaining = seconds
        val fmt = if (isListen) R.string.wait_listen_fmt else R.string.wait_record_fmt
        binding.tvWait.text = getString(fmt, remaining)

        waitRunnable = object : Runnable {
            override fun run() {
                remaining--
                if (remaining > 0) {
                    binding.tvWait.text = getString(fmt, remaining)
                    mainHandler.postDelayed(this, 1000)
                } else {
                    binding.tvWait.visibility = View.GONE
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
                playTts()
                startSubmitCountdown()
            }
            "SHADOWING" -> {
                playTtsWithCompletion { startShadowingPreRecord() }
            }
            "NAMING", "SELF_TALK" -> {
                startRecordingAuto()
            }
        }
    }

    /** SHADOWING 전용: TTS 재생 종료 → 3초 후 녹음 시작 (06 §3) */
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
                    shadowingPreRecordRunnable = Runnable { onComplete() }.also {
                        mainHandler.postDelayed(it, SHADOWING_PRE_RECORD_SECONDS * 1000L)
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
            // 녹음 중 확실한 표시 — 버튼을 [녹음 완료]로 전환 (시니어 UI: 색상 대신 텍스트)
            binding.fabRecord.text = getString(R.string.btn_recording_stop)
            binding.fabRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.error)
            )
            binding.btnSubmitRecording.text = getString(R.string.btn_recording_stop)
            binding.btnSubmitRecording.isEnabled = true
            binding.tvRecordingHint.text = getString(R.string.recording_now)
            startSubmitCountdown()
        } else {
            Toast.makeText(this, "녹음 시작 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── 제출 카운트다운 30초 (모든 타입 공통) ──────────────────────

    /**
     * 제출 카운트다운 30초 — 시각적 표시.
     * 도달 시: LISTEN=CASE 2/3 강제 제출 / 음성형=녹음 컷 → multipart 강제 제출.
     */
    private fun startSubmitCountdown() {
        if (submittedThisTurn) return
        binding.tvSubmitCountdown.visibility = View.VISIBLE
        var remaining = SUBMIT_LIMIT_SECONDS
        binding.tvSubmitCountdown.text = getString(R.string.submit_countdown_fmt, remaining)

        submitCountdownRunnable = object : Runnable {
            override fun run() {
                if (submittedThisTurn) return
                remaining--
                if (remaining > 0) {
                    binding.tvSubmitCountdown.text = getString(R.string.submit_countdown_fmt, remaining)
                    mainHandler.postDelayed(this, 1000)
                } else {
                    onSubmitTimeUp()
                }
            }
        }
        mainHandler.postDelayed(submitCountdownRunnable!!, 1000)
    }

    /** 30초 도달 — 타입별 강제 제출 (06 §3) */
    private fun onSubmitTimeUp() {
        if (submittedThisTurn) return
        val turn = turns.getOrNull(currentIndex) ?: return
        when (turn.type) {
            "LISTEN", "LISTEN_TEXT", "LISTEN_PICTURE" -> {
                // CASE 2: 선택 누름=마지막 선택지로 제출 / CASE 3: 미선택=오답(0) 제출
                val choice = selectedChoice
                if (choice != null) {
                    submitListen(choice.order)
                } else {
                    submitListen(noChoiceSentinel) // 오답 처리 — 서버가 0점 자체 채점
                }
            }
            "NAMING", "SHADOWING", "SELF_TALK" -> forceSubmitRecording()
        }
    }

    /** 음성형 30초 도달 — 녹음 컷 → multipart 강제 제출 (RecordingHelper.stop+파일 확보) */
    private fun forceSubmitRecording() {
        if (recordingHelper.recording) {
            recordedFile = recordingHelper.stop()
        }
        val file = recordedFile
        if (file == null || !file.exists()) {
            // 녹음 파일 없음 — 오답 방어: 빈 파일 제출 불가, 다음 턴으로는 진행 불가(계약 유지)
            // 서버 계약상 multipart 필수 — 파일 없으면 제출 스킵 후 안내 (재시도 불가 케이스 — 보고서 기록)
            Toast.makeText(this, "녹음 파일이 준비되지 않았어요", Toast.LENGTH_SHORT).show()
            return
        }
        submitRecording()
    }

    // ─── 상호작용 ────────────────────────────────────────────────

    /** LISTEN 선택지 탭 — 선택만 하고 제출은 [제출] (06 §3 버튼 분리) */
    private fun onChoiceSelected(choice: ChoiceDto) {
        if (submittedThisTurn) return
        selectedChoice = choice
        // 선택 완료 → [제출] 버튼 (06 §3: 제출(제한)과 다음으로(자유) 분리)
        binding.btnSubmitRecording.text = getString(R.string.btn_listen_submit)
        binding.btnSubmitRecording.isEnabled = true
        binding.btnSubmitRecording.visibility = View.VISIBLE
    }

    /** 음성형: [녹음 시작]→[녹음 완료] 토글 / LISTEN: [제출] */
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
            // 녹음 종료 — 버튼 원복
            binding.fabRecord.text = getString(R.string.btn_recording_start)
            binding.fabRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.primary)
            )
            binding.btnSubmitRecording.text = getString(R.string.btn_recording_stop)
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
                binding.btnSubmitRecording.text = getString(R.string.btn_recording_stop)
                binding.btnSubmitRecording.isEnabled = true
                binding.tvRecordingHint.text = getString(R.string.recording_now)
                startSubmitCountdown()
            } else {
                Toast.makeText(this, "녹음 시작 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitListen(selected: Int) {
        if (submittedThisTurn) return
        submittedThisTurn = true
        val turn = turns[currentIndex]
        cancelSubmitCountdown()
        showSubmitProgress()

        lifecycleScope.launch {
            try {
                val data = repository.submitListen(sessionId, turn.turnId, selected)
                turnScores.add(data.score)
                turnTypes.add(turn.type)
                showSubmittedState()
            } catch (e: Exception) {
                submittedThisTurn = false
                hideSubmitProgress()
                Toast.makeText(this@ProblemActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
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
                showSubmittedState()
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

    /** 제출 완료 상태: "답안이 제출되었어요" + [다음으로] 등장 (2단계 — 버튼 분리) */
    private fun showSubmittedState() {
        binding.scoringOverlay.visibility = View.GONE
        binding.tvSubmitCountdown.visibility = View.GONE
        binding.containerSubmitted.visibility = View.VISIBLE
        binding.containerRecord.visibility = View.GONE
        binding.containerChoices.visibility = View.GONE
    }

    private fun hideSubmitProgress() {
        binding.scoringOverlay.visibility = View.GONE
    }

    /** [다음으로] — 이동 자유 (제한 없음) → 다음 턴 문제 가이드 화면 */
    private fun onNextClicked() {
        goToNextTurn()
    }

    private fun goToNextTurn() {
        val intent = Intent(this, ProblemGuideActivity::class.java)
            .putExtra(ProblemGuideActivity.EXTRA_TURN_INDEX, currentIndex + 1)
        startActivity(intent)
        finish()
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
                binding.tvHint.visibility = View.VISIBLE

                // 힌트 소진 (2개) → 버튼 숨김 (다음 턴에서 showRecordingUI가 상태 리셋)
                if (hint.hintOrder >= 2) {
                    binding.btnHint.visibility = View.GONE
                } else {
                    binding.btnHint.isEnabled = true
                }
            } catch (e: Exception) {
                if (turnIndex == currentIndex) {
                    Toast.makeText(this@ProblemActivity, e.message, Toast.LENGTH_SHORT).show()
                    binding.btnHint.isEnabled = true
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
        recordingHelper.release()
        releasePlayer()
    }
}