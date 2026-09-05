package com.sesac.speechapp.ui.learning

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.R
import com.sesac.speechapp.data.repository.SessionFlowException
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.ActivityStorytellingBinding
import com.sesac.speechapp.ui.record.RecordingHelper
import kotlinx.coroutines.launch
import java.io.File

/**
 * P3-26 이야기 턴 — 채팅 UI + D-7 AI 대화 UX (06 §3 STORYTELLING 규약).
 *
 * - 첫 진입: /turns/talk (file=null) → AI 첫 대사 표시
 * - [학습 중단하기] 버튼(우상단): 1~3턴 동안 표시 — 우는 덕분이 팝업 → [네, 나갈게요]=finish 호출(중단 판정)
 *   / [아니요, 계속할게요]=계속
 * - 유저 4턴째 답변 제출 완료 시 버튼 [학습 마치기] 전환 — 팝업 → 학습 완료 판정 (total 호출)
 * - 8턴 하드캡: talk-turn-limit=8 — 9번째 제출 E0401 수신 시 종료 안내
 *   + 8턴째 답변 후 AI 마무리 응답 표시 → "이번 학습 수고하셨어요!" [학습 결과 보기]
 * - LLM 로딩: "덕분이가 답변을 생각중이에요" 버블 표시
 * - 녹음 UX: [음성으로 답변하기] 직접 클릭 시작 + 30초 제한 표시 + [녹음 완료]/30초 도달 → 자동 제출
 */
class StorytellingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"

        /** 백엔드 talk-turn-limit=8 (application.yml — D-5 상향, 05a v1.6 §3.5) */
        private const val TALK_HARD_CAP = 8
        /** 학습 완료 판정: 유저 4턴째 답변 (06 v1.7 §3 — 최소 완료 판정) */
        private const val MIN_COMPLETE_TURNS = 4
        /** 녹음 30초 제한 (06 §3 — AI 대화 답변도 30초 통일) */
        private const val RECORD_LIMIT_SECONDS = 30
    }

    private lateinit var binding: ActivityStorytellingBinding
    private lateinit var repository: SessionFlowRepository
    private lateinit var recordingHelper: RecordingHelper
    private lateinit var chatAdapter: TalkChatAdapter

    private var sessionId: Long = -1
    private var userTalkCount = 0
    private var lastRecordedFile: File? = null
    private var finished = false

    /** 녹음 30초 제한 타이머 — 도달 시 자동 제출 (06 §3) */
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recordLimitRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorytellingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SessionFlowRepository(this)
        recordingHelper = RecordingHelper(this) { seconds ->
            binding.tvTimer.text = getString(R.string.talk_record_limit_fmt, seconds)
        }

        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        if (sessionId <= 0) {
            Toast.makeText(this, "세션 정보가 없어요", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatAdapter = TalkChatAdapter()
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = chatAdapter

        binding.btnRecord.setOnClickListener { toggleRecording() }
        binding.btnStopOrFinish.setOnClickListener { onStopOrFinishClicked() }

        startFirstTurn()
    }

    private fun startFirstTurn() {
        showThinking()
        lifecycleScope.launch {
            try {
                val talk = repository.talk(sessionId, file = null)
                hideThinking()
                chatAdapter.add(TalkChatAdapter.Item(aiText = talk.aiText))
                scrollToBottom()
            } catch (e: Exception) {
                hideThinking()
                Toast.makeText(this@StorytellingActivity, e.message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // ─── 중단/마치기 버튼 (06 §3 — 4턴 전환) ─────────────────────

    /**
     * 우상단 버튼 — 1~3턴 동안 [학습 중단하기], 4턴째 답변 완료 후 [학습 마치기].
     * 클릭 시 팝업: 중단=우는 덕분이 팝업 / 마치기=완료 확인 팝업.
     */
    private fun onStopOrFinishClicked() {
        if (finished) return
        if (userTalkCount >= MIN_COMPLETE_TURNS) {
            confirmFinish()
        } else {
            confirmStop()
        }
    }

    /** [학습 중단하기] 팝업 — 1~3턴: 우는 덕분이 + 경고 (06 §3 기획 문구) */
    private fun confirmStop() {
        val remaining = TALK_HARD_CAP - userTalkCount
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.talk_stop_btn))
            .setMessage(getString(R.string.talk_stop_confirm_fmt, remaining))
            .setPositiveButton(getString(R.string.talk_stop_yes)) { _, _ ->
                // [네, 나갈게요]=finish 호출(중단 판정) → 간이 보고서 표시
                goToReport()
            }
            .setNegativeButton(getString(R.string.talk_stop_no), null)
            .show()
    }

    /** [학습 마치기] 팝업 — 4턴째 답변 후: 학습 완료 판정 (06 §3) */
    private fun confirmFinish() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.talk_finish_btn))
            .setMessage(getString(R.string.talk_finish_confirm))
            .setPositiveButton(getString(R.string.talk_finish_yes)) { _, _ ->
                goToReport()
            }
            .setNegativeButton(getString(R.string.talk_stop_no), null)
            .show()
    }

    // ─── 녹음 UX (06 §3 — 직접 클릭 시작, 30초 제한, 자동 제출) ─────

    private fun toggleRecording() {
        if (finished) return

        if (recordingHelper.recording) {
            cancelRecordLimit()
            lastRecordedFile = recordingHelper.stop()
            resetRecordUi()
            if (lastRecordedFile != null) submitTalk()
            else Toast.makeText(this, "녹음 파일이 없어요", Toast.LENGTH_SHORT).show()
        } else {
            if (!recordingHelper.hasPermission()) {
                Toast.makeText(this, "마이크 권한이 필요해요", Toast.LENGTH_SHORT).show()
                return
            }
            if (recordingHelper.start()) {
                binding.btnRecord.text = getString(R.string.btn_recording_stop)
                binding.tvRecordingHint.text = getString(R.string.recording_now)
                startRecordLimit()
            }
        }
    }

    /** 녹음 30초 제한 표시 + 도달 시 녹음 컷 → 자동 제출 (06 §3) */
    private fun startRecordLimit() {
        recordLimitRunnable = Runnable {
            if (recordingHelper.recording) {
                lastRecordedFile = recordingHelper.stop()
                resetRecordUi()
                if (lastRecordedFile != null) submitTalk()
            }
        }
        mainHandler.postDelayed(recordLimitRunnable!!, RECORD_LIMIT_SECONDS * 1000L)
    }

    private fun cancelRecordLimit() {
        recordLimitRunnable?.let { mainHandler.removeCallbacks(it) }
        recordLimitRunnable = null
    }

    private fun resetRecordUi() {
        binding.btnRecord.text = getString(R.string.btn_voice_answer)
        binding.tvRecordingHint.text = getString(R.string.btn_voice_answer)
        binding.tvTimer.text = getString(R.string.recording_timer_default)
    }

    private fun submitTalk() {
        val file = lastRecordedFile ?: return
        showThinking()

        lifecycleScope.launch {
            try {
                val talk = repository.talk(sessionId, file = file)
                hideThinking()
                userTalkCount++

                chatAdapter.add(TalkChatAdapter.Item(userText = talk.userText ?: "(인식된 말 없음)"))
                chatAdapter.add(TalkChatAdapter.Item(aiText = talk.aiText))
                scrollToBottom()

                onUserTalkCompleted()
            } catch (e: SessionFlowException) {
                hideThinking()
                // 백엔드 하드캡 차단 (E0401) → 종료 안내 (8턴 상향 후에도 동일 코드 대응)
                if (e.code == "E0401") showCapReached()
                else Toast.makeText(this@StorytellingActivity, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                hideThinking()
                Toast.makeText(this@StorytellingActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 유저 답변 완료 후 처리 (06 §3):
     * - 4턴째 답변 제출 완료 시점부터 버튼 [학습 마치기] 전환 (1~3턴 = [학습 중단하기])
     * - 8턴째 답변 후 AI 마무리 응답(=이번 aiText) 수신 → "이번 학습 수고하셨어요!" [학습 결과 보기]
     */
    private fun onUserTalkCompleted() {
        if (userTalkCount >= TALK_HARD_CAP) {
            // 8턴 하드캡 도달 — AI 마무리 응답까지 표시됨 → 종료 안내
            showCapReached()
            return
        }
        // 4턴 경계 전환 — 버튼 라벨 갱신 (중단하기 → 마치기)
        refreshExitButton()
    }

    /** 버튼 라벨 갱신 — 4턴 전 [학습 중단하기] / 4턴째 답변 후 [학습 마치기] */
    private fun refreshExitButton() {
        binding.btnStopOrFinish.text =
            if (userTalkCount >= MIN_COMPLETE_TURNS) getString(R.string.talk_finish_btn)
            else getString(R.string.talk_stop_btn)
    }

    /** 8턴 하드캡 — "이번 학습 수고하셨어요!" [학습 결과 보기] (06 §3) */
    private fun showCapReached() {
        if (finished) return
        finished = true
        cancelRecordLimit()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.talk_cap_done))
            .setMessage(getString(R.string.talk_cap_notice))
            .setPositiveButton(getString(R.string.btn_view_result)) { _, _ -> goToReport() }
            .setCancelable(false)
            .show()
    }

    // ─── LLM 로딩 표시 (06 §3 — "덕분이가 답변을 생각중이에요") ──────

    private fun showThinking() {
        binding.thinkingBubble.visibility = View.VISIBLE
        binding.btnRecord.isEnabled = false
    }

    private fun hideThinking() {
        binding.thinkingBubble.visibility = View.GONE
        binding.btnRecord.isEnabled = true
        resetRecordUi()
    }

    private fun goToReport() {
        finished = true
        cancelRecordLimit()
        recordingHelper.release()
        // ProblemActivity가 누적한 턴별 점수를 결과 화면으로 전달
        val scores = intent.getIntegerArrayListExtra(SessionReportActivity.EXTRA_TURN_SCORES) ?: ArrayList()
        val types = intent.getStringArrayListExtra(SessionReportActivity.EXTRA_TURN_TYPES) ?: ArrayList()
        val intent = android.content.Intent(this, SessionReportActivity::class.java)
            .putExtra(SessionReportActivity.EXTRA_SESSION_ID, sessionId)
            .putIntegerArrayListExtra(SessionReportActivity.EXTRA_TURN_SCORES, scores)
            .putStringArrayListExtra(SessionReportActivity.EXTRA_TURN_TYPES, types)
        startActivity(intent)
        finish()
    }

    private fun scrollToBottom() {
        binding.rvChat.post {
            binding.rvChat.scrollToPosition((chatAdapter.itemCount - 1).coerceAtLeast(0))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelRecordLimit()
        recordingHelper.release()
    }
}

/** 채팅 어댑터 — AI/유저 말풍선 2-ViewHolder + D-7 생각중 버블(ThinkingBubble 별도 뷰) */
class TalkChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    data class Item(val aiText: String? = null, val userText: String? = null)

    private val items = mutableListOf<Item>()

    fun add(item: Item) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].aiText != null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            AiVH(inflater.inflate(R.layout.item_talk_ai, parent, false))
        } else {
            UserVH(inflater.inflate(R.layout.item_talk_user, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is AiVH -> holder.tv.text = item.aiText
            is UserVH -> holder.tv.text = item.userText
        }
    }

    class AiVH(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(R.id.tvAiMessage)
    }

    class UserVH(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(R.id.tvUserMessage)
    }
}