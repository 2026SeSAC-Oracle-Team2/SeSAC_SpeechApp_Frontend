package com.sesac.speechapp.ui.learning

import android.os.Bundle
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

/**
 * P3-26 이야기 턴 — 채팅 UI.
 *
 * - 첫 진입: /turns/talk (file=null) → AI 첫 대사 표시
 * - 유저: 녹음 FAB → 제출 → aiText(AI 말풍선) + userText(내 말풍선) 표시
 * - 데모 3턴 하드캡: 백엔드가 4번째 제출을 E0401 차단 → 종료 안내 표시
 * - 우측 상단 "대화 종료" 확인 다이얼로그 → finish → SessionReportActivity
 */
class StorytellingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        private const val DEMO_TALK_LIMIT = 3 // 백엔드 demo.talk-turn-limit과 동기
    }

    private lateinit var binding: ActivityStorytellingBinding
    private lateinit var repository: SessionFlowRepository
    private lateinit var recordingHelper: RecordingHelper
    private lateinit var chatAdapter: TalkChatAdapter

    private var sessionId: Long = -1
    private var userTalkCount = 0
    private var lastRecordedFile: java.io.File? = null
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorytellingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SessionFlowRepository(this)
        recordingHelper = RecordingHelper(this) { seconds ->
            binding.tvTimer.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
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

        binding.fabRecord.setOnClickListener { toggleRecording() }
        binding.btnExit.setOnClickListener { confirmExit() }

        startFirstTurn()
    }

    private fun startFirstTurn() {
        binding.waitingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val talk = repository.talk(sessionId, file = null)
                binding.waitingOverlay.visibility = View.GONE
                chatAdapter.add(TalkChatAdapter.Item(aiText = talk.aiText))
                scrollToBottom()
            } catch (e: Exception) {
                binding.waitingOverlay.visibility = View.GONE
                Toast.makeText(this@StorytellingActivity, e.message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun toggleRecording() {
        // 하드캡 도달 시 제출 금지 — 종료 안내만
        if (userTalkCount >= DEMO_TALK_LIMIT) {
            showCapReached()
            return
        }

        if (recordingHelper.recording) {
            lastRecordedFile = recordingHelper.stop()
            binding.tvRecordingHint.text = "탭하여 말하기"
            if (lastRecordedFile != null) submitTalk()
        } else {
            if (!recordingHelper.hasPermission()) {
                Toast.makeText(this, "마이크 권한이 필요해요", Toast.LENGTH_SHORT).show()
                return
            }
            if (recordingHelper.start()) {
                binding.tvRecordingHint.text = "녹음 중… 다시 탭하여 전송"
            }
        }
    }

    private fun submitTalk() {
        val file = lastRecordedFile ?: return
        binding.waitingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val talk = repository.talk(sessionId, file = file)
                binding.waitingOverlay.visibility = View.GONE
                userTalkCount++

                chatAdapter.add(TalkChatAdapter.Item(userText = talk.userText ?: "(인식된 말 없음)"))
                chatAdapter.add(TalkChatAdapter.Item(aiText = talk.aiText))
                scrollToBottom()

                // 데모 3턴 도달 → 종료 안내
                if (userTalkCount >= DEMO_TALK_LIMIT) {
                    showCapReached()
                }
            } catch (e: SessionFlowException) {
                binding.waitingOverlay.visibility = View.GONE
                // 백엔드 하드캡 차단 (E0401) → 종료 안내
                if (e.code == "E0401") showCapReached()
                else Toast.makeText(this@StorytellingActivity, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.waitingOverlay.visibility = View.GONE
                Toast.makeText(this@StorytellingActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCapReached() {
        if (finished) return
        finished = true
        AlertDialog.Builder(this)
            .setTitle("오늘의 대화 종료")
            .setMessage(getString(R.string.talk_cap_notice))
            .setPositiveButton(getString(R.string.btn_finish)) { _, _ -> goToReport() }
            .setNegativeButton("계속 대화") { d, _ -> d.dismiss() }
            .show()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.talk_exit))
            .setMessage(getString(R.string.talk_exit_confirm))
            .setPositiveButton(getString(R.string.btn_finish)) { _, _ -> goToReport() }
            .setNegativeButton("계속 대화", null)
            .show()
    }

    private fun goToReport() {
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
        recordingHelper.release()
    }
}

/** 채팅 어댑터 — AI/유저 말풍선 2-ViewHolder */
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