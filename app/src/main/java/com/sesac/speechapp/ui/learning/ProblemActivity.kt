package com.sesac.speechapp.ui.learning

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
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
import com.sesac.speechapp.data.remote.dto.session.TurnDto
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.ActivityProblemBinding
import com.sesac.speechapp.ui.record.RecordingHelper
import kotlinx.coroutines.launch
import java.io.File

/**
 * P3-26 문제 풀이 화면 — 유형별 레이아웃 전환.
 *
 * - LISTEN: TTS + 선택지 카드(텍스트/이미지) 탭 → 즉시 제출 (응답 스코어 무시, 다음 턴)
 * - NAMING: 이미지 + TTS + 녹음 제출 + 힌트 버튼(의미→조음, 최대 2개, 하단 텍스트)
 * - SHADOWING: TTS 문장 재생 + 녹음 제출
 * - SELF_TALK: 상황 이미지 + 녹음 제출
 * - 공통: 채점 대기 오버레이(스텁 0.8~1.5초) → 다음 턴 (점수 미표시 — 결과에서 일괄)
 * - 상단 "n / 8" 프로그레스
 */
class ProblemActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_THEME = "theme"
        private val TYPE_LABELS = mapOf(
            "LISTEN" to "알아듣기",
            "NAMING" to "이름대기",
            "SHADOWING" to "따라말하기",
            "SELF_TALK" to "스스로말하기",
        )
    }

    private lateinit var binding: ActivityProblemBinding
    private lateinit var repository: SessionFlowRepository
    private lateinit var recordingHelper: RecordingHelper

    private var sessionId: Long = -1
    private var turns: List<TurnDto> = emptyList()
    private var currentIndex = 0

    private var mediaPlayer: MediaPlayer? = null
    private var recordedFile: File? = null

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
            Toast.makeText(this, "마이크 권한이 필요해요", Toast.LENGTH_SHORT).show()
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
        binding.btnSubmitRecording.setOnClickListener { submitRecording() }
        binding.btnTts.setOnClickListener { playTts() }
        binding.btnHint.setOnClickListener { requestHint() }

        showTurn(0)
    }

    // ─── 턴 렌더 ────────────────────────────────────────────────

    private fun showTurn(index: Int) {
        if (index >= turns.size) {
            goToStorytelling()
            return
        }
        currentIndex = index
        val turn = turns[index]

        // 늦은 콜백 가드용: 턴 전환 시 진행 중인 녹음/미디어 정리
        if (recordingHelper.recording) {
            recordingHelper.stop()
            recordedFile = null
            binding.fabRecord.setColorFilter(ContextCompat.getColor(this, R.color.surface))
        }
        stopTts()

        // 프로그레스
        binding.tvProgress.text = getString(R.string.progress_turn_fmt, index + 1, turns.size)
        binding.progressBar.progress = ((index + 1) * 100 / turns.size)
        binding.tvTypeBadge.text = TYPE_LABELS[turn.type] ?: turn.type
        // SELF_TALK: 지문 고정 문구 (사용자 확정) — 스텁 상황 설명은 이미지가 담당
        binding.tvPassage.text = if (turn.type == "SELF_TALK") "다음 상황을 보고 묘사해보세요"
        else turn.passage ?: ""

        // 기본 상태 초기화
        resetTurnViews()

        when (turn.type) {
            "LISTEN" -> renderListen(turn)
            "NAMING" -> renderNaming(turn)
            "SHADOWING" -> renderRecordingTurn(turn, showImage = false)
            "SELF_TALK" -> renderRecordingTurn(turn, showImage = true)
            else -> renderRecordingTurn(turn, showImage = false)
        }

        // TTS 버튼: ttsUrl 있을 때만
        binding.btnTts.visibility = if (turn.ttsUrl != null) View.VISIBLE else View.GONE

        // 자동재생: 턴 진입 시 TTS 바로 재생 (사용자 피드백 — 재생버튼 안 눌러도 나와야 함)
        if (turn.ttsUrl != null) playTts()
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
        recordedFile = null
        stopTts()
    }

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
            item.setOnClickListener { submitListen(choice.order) }
            binding.containerChoices.addView(item)
        }
    }

    private fun renderNaming(turn: TurnDto) {
        showImage(turn)
        showRecordingUI(showHintButton = true)
    }

    private fun renderRecordingTurn(turn: TurnDto, showImage: Boolean) {
        if (showImage) showImage(turn)
        showRecordingUI(showHintButton = false)
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
        binding.tvRecordingHint.text = "버튼을 눌러 녹음을 시작하세요"
        binding.tvTimer.text = getString(R.string.recording_timer_default)
    }

    // ─── 상호작용 ────────────────────────────────────────────────

    private fun toggleRecording() {
        if (recordingHelper.recording) {
            recordedFile = recordingHelper.stop()
            binding.fabRecord.setColorFilter(ContextCompat.getColor(this, R.color.surface))
            binding.tvRecordingHint.text = "녹음 완료 — 제출하세요"
            binding.btnSubmitRecording.isEnabled = recordedFile != null
        } else {
            if (!recordingHelper.hasPermission()) {
                // 권한 요청 (런처는 프로퍼티로 등록됨 — 크래시 없음)
                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            if (recordingHelper.start()) {
                binding.fabRecord.setColorFilter(ContextCompat.getColor(this, R.color.error))
                binding.tvRecordingHint.text = "녹음 중… 다시 눌러 중지"
                binding.btnSubmitRecording.isEnabled = false
            } else {
                Toast.makeText(this, "녹음 시작 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitListen(selected: Int) {
        val turn = turns[currentIndex]
        lifecycleScope.launch {
            try {
                val data = repository.submitListen(sessionId, turn.turnId, selected)
                // 결과 화면 집계용 누적
                turnScores.add(data.score)
                turnTypes.add(turn.type)
                // 스펙 확정: LISTEN 즉시 다음 턴 (스코어 미표시)
                nextTurn()
            } catch (e: Exception) {
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
        binding.scoringOverlay.visibility = View.VISIBLE // 채점 대기 (스텁 0.8~1.5초)

        lifecycleScope.launch {
            try {
                val data = when (turn.type) {
                    "NAMING" -> repository.submitNaming(sessionId, turn.turnId, file)
                    "SHADOWING" -> repository.submitShadowing(sessionId, turn.turnId, file)
                    "SELF_TALK" -> repository.submitSelfTalk(sessionId, turn.turnId, file)
                    else -> throw IllegalStateException("녹음 제출이 없는 유형: ${turn.type}")
                }
                // 결과 화면 집계용 누적 (score는 0~100 Double — 반올림)
                turnScores.add(data.score.toInt())
                turnTypes.add(turn.type)
                // 스펙 확정: 응답 수신하면 다음 턴 (점수는 결과 화면에서 일괄)
                binding.scoringOverlay.visibility = View.GONE
                nextTurn()
            } catch (e: Exception) {
                binding.scoringOverlay.visibility = View.GONE
                Toast.makeText(this@ProblemActivity, e.message, Toast.LENGTH_SHORT).show()
                // 재제출 가능 상태 유지
                binding.btnSubmitRecording.isEnabled = true
            }
        }
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

    // ─── 전환 ────────────────────────────────────────────────

    private fun nextTurn() {
        showTurn(currentIndex + 1)
    }

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
        recordingHelper.release()
        releasePlayer()
    }
}