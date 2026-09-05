package com.sesac.speechapp.ui.detail

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.sesac.speechapp.BuildConfig
import com.sesac.speechapp.R
import com.sesac.speechapp.data.remote.AuthImageLoader
import com.sesac.speechapp.data.remote.dto.session.MetricCardDto
import com.sesac.speechapp.data.remote.dto.session.MetricTurnDto
import com.sesac.speechapp.data.remote.dto.session.SessionReportData
import com.sesac.speechapp.data.remote.dto.session.TalkHistoryItem
import com.sesac.speechapp.data.repository.SessionFlowException
import com.sesac.speechapp.data.repository.SessionFlowRepository
import com.sesac.speechapp.databinding.ActivitySessionDetailBinding
import com.sesac.speechapp.ui.learning.ProblemActivity
import com.sesac.speechapp.ui.learning.RadarChartView
import kotlinx.coroutines.launch

/**
 * D-7 3.3 세부 보고서 화면 — GET /api/v1/sessions/{id}/report?userId= (05a v1.6 §8.3).
 *
 * - 종합 피드백(totalFeedback) + AQ 지수 + radar(세션 TURN 집계 — 응답값 그대로)
 * - 지표별 카드 4종: 점수·피드백 — 클릭 확장 시 turns(문제 안내/TTS/내 답변)
 *   LISTEN_TEXT=answer.value 선택지 텍스트 / LISTEN_PICTURE=image 로딩 / 음성형=voiceUrl 재생. LISTEN은 correct 표시
 * - AI 대화 피드백(talkFeedback) + 대화 내역(talkHistory — AI text + 내 답변 다시 듣기)
 * - 상단: 세션명·날짜 (이력 카드에서 전달 — sessionId만으로도 조회 가능하나 표시용 수신)
 * - REPORT_VIEWED_AT은 서버가 기록 — 클라 콜백 불필요
 * - 생성 지연(스텁 total 10초): 즉시 조회 실패 시 "준비 중" 안내 후 재시도 버튼
 */
class SessionDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SESSION_NAME = "session_name"
        const val EXTRA_CREATED_AT = "created_at" // ISO — 표시 포맷은 클라 책임
    }

    private lateinit var binding: ActivitySessionDetailBinding
    private lateinit var repository: SessionFlowRepository

    private var sessionId: Long = -1
    private var sessionName: String = ""
    private var createdAt: String = ""

    private lateinit var metricAdapter: MetricCardAdapter
    private lateinit var talkAdapter: TalkHistoryAdapter

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SessionFlowRepository(this)
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: ""
        createdAt = intent.getStringExtra(EXTRA_CREATED_AT) ?: ""

        if (sessionId <= 0) {
            Toast.makeText(this, "세션 정보가 없어요", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvSessionName.text = sessionName
        binding.tvSessionDate.text = DateFormats.toDashDate(createdAt)

        metricAdapter = MetricCardAdapter(this)
        binding.rvMetricCards.layoutManager = LinearLayoutManager(this)
        binding.rvMetricCards.adapter = metricAdapter

        talkAdapter = TalkHistoryAdapter(this)
        binding.rvTalkHistory.layoutManager = LinearLayoutManager(this)
        binding.rvTalkHistory.adapter = talkAdapter

        binding.btnDone.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { fetchReport() }

        fetchReport()
    }

    private fun fetchReport() {
        binding.containerLoading.visibility = View.VISIBLE
        binding.containerResult.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val data = repository.getSessionReport(sessionId)
                render(data)
            } catch (e: SessionFlowException) {
                // 중단 세션(E0404) vs 생성 지연(스텁 total 10초 — 동일 E0404로 수신 가능)
                showError(getString(R.string.detail_preparing))
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.detail_loading_fail))
            }
        }
    }

    private fun render(data: SessionReportData) {
        binding.containerLoading.visibility = View.GONE
        binding.containerResult.visibility = View.VISIBLE

        binding.tvAQ.text = data.aq.toString()

        // 방사형: 세션 TURN 집계 (§8.3 radar 응답값 그대로 — null이면 0 표시)
        val r = data.radar
        binding.radarDetail.setData(
            listOf(
                RadarChartView.AxisData(getString(R.string.metric_listen), r?.listen?.toFloat() ?: 0f),
                RadarChartView.AxisData(getString(R.string.metric_naming), r?.naming?.toFloat() ?: 0f),
                RadarChartView.AxisData(getString(R.string.metric_shadowing), r?.shadowing?.toFloat() ?: 0f),
                RadarChartView.AxisData(getString(R.string.metric_selftalk), r?.selfTalk?.toFloat() ?: 0f),
            )
        )

        // 종합 피드백
        if (data.totalFeedback.isNullOrBlank()) {
            binding.tvTotalFeedback.visibility = View.GONE
        } else {
            binding.tvTotalFeedback.text = data.totalFeedback
            binding.tvTotalFeedback.visibility = View.VISIBLE
        }

        // 지표별 카드
        metricAdapter.submitList(data.metricCards)

        // AI 대화 피드백
        if (data.talkFeedback.isNullOrBlank()) {
            binding.tvTalkFeedback.visibility = View.GONE
        } else {
            binding.tvTalkFeedback.text = data.talkFeedback
            binding.tvTalkFeedback.visibility = View.VISIBLE
        }

        // 대화 내역
        talkAdapter.submitList(data.talkHistory)
    }

    private fun showError(message: String) {
        binding.containerLoading.visibility = View.GONE
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.VISIBLE
    }

    // ─── 음성 재생 공통 (ttsUrl / voiceUrl) ──────────────────────

    fun playVoice(url: String) {
        stopVoice()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(resolveUrl(url))
                setOnPreparedListener { it.start() }
                setOnCompletionListener { releaseVoice() }
                prepareAsync()
            } catch (e: Exception) {
                Toast.makeText(this@SessionDetailActivity, "음성 재생 실패", Toast.LENGTH_SHORT).show()
                releaseVoice()
            }
        }
    }

    fun stopVoice() {
        mediaPlayer?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        mediaPlayer = null
    }

    private fun releaseVoice() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /** 어댑터(inner class)에서 사용 — 같은 파일 내 클래스라 private 불가 (컴파일 계약) */
    fun resolveUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = BuildConfig.SERVER_BASE_URL.trimEnd('/')
        val relative = if (path.startsWith("/")) path else "/$path"
        return base + relative
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVoice()
    }
}

/** ISO createdAt → YYYY.mm.dd (06 §7.1 — 표현은 클라 포맷 책임) */
object DateFormats {
    /** OffsetDateTime 실패 시 LocalDateTime 폴백 — 오프셋 여유 있게 파서 설계 (지시문 §6) */
    fun toDashDate(iso: String): String {
        val date = try {
            java.time.OffsetDateTime.parse(iso).toLocalDate()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(iso).toLocalDate()
            } catch (_: Exception) {
                // 타임스탬프 부분만 잘라 폴백 (마이크로초/오프셋 혼재 방어)
                return iso.take(10).replace('-', '.')
            }
        }
        return date.toString().replace('-', '.')
    }
}

/**
 * 지표 카드 어댑터 — 점수·피드백 표시, 클릭 확장 시 turns(문제 기록) 토글.
 * type(LISTEN|NAMING|SHADOWING|SELF_TALK) → 05a §8.3 계약 그대로 렌더.
 */
class MetricCardAdapter(private val activity: SessionDetailActivity) :
    RecyclerView.Adapter<MetricCardAdapter.VH>() {

    private val items = mutableListOf<MetricCardDto>()
    private val expanded = mutableSetOf<Int>()

    fun submitList(list: List<MetricCardDto>) {
        items.clear()
        items.addAll(list)
        expanded.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = com.sesac.speechapp.databinding.ItemMetricCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position)
    }

    inner class VH(private val binding: com.sesac.speechapp.databinding.ItemMetricCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: MetricCardDto, position: Int) {
            binding.tvMetricType.text = ProblemActivity.typeLabel(card.type)
            binding.tvMetricScore.text = card.score?.let { "${it}점" } ?: "-"
            binding.tvMetricFeedback.text = card.feedback ?: ""

            // 클릭 확장 토글 — turns(문제 기록) 표시
            val isOpen = expanded.contains(position)
            binding.containerTurns.visibility = if (isOpen) View.VISIBLE else View.GONE
            binding.root.setOnClickListener {
                if (expanded.contains(position)) expanded.remove(position) else expanded.add(position)
                notifyItemChanged(position)
            }

            if (!isOpen) return
            binding.containerTurns.removeAllViews()
            card.turns.forEach { turn ->
                val row = activity.layoutInflater.inflate(
                    R.layout.item_metric_turn, binding.containerTurns, false
                )
                bindTurnRow(row, card, turn)
                binding.containerTurns.addView(row)
            }
        }

        /** 문제 기록 행 — promptText / TTS 재생 / 내 답변(계약별) / LISTEN correct 표시 */
        private fun bindTurnRow(row: View, card: MetricCardDto, turn: MetricTurnDto) {
            val tvPrompt = row.findViewById<TextView>(R.id.tvTurnPrompt)
            val btnTts = row.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTurnTts)
            val tvAnswer = row.findViewById<TextView>(R.id.tvTurnAnswer)
            val ivImage = row.findViewById<android.widget.ImageView>(R.id.ivTurnAnswerImage)
            val btnVoice = row.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTurnVoice)
            val tvImageNote = row.findViewById<TextView>(R.id.tvTurnImage)

            tvPrompt.text = turn.promptText ?: "${card.type} #${turn.turnNumber}"

            // AI TTS 재생 (ttsUrl 있을 때만)
            if (turn.ttsUrl != null) {
                btnTts.visibility = View.VISIBLE
                btnTts.setOnClickListener { activity.playVoice(turn.ttsUrl) }
            } else {
                btnTts.visibility = View.GONE
            }

            if (turn.imageUrl != null) {
                // NAMING/SELF_TALK 문제 이미지
                ivImage.visibility = View.VISIBLE
                ivImage.load(activity.resolveUrl(turn.imageUrl), AuthImageLoader.get(activity)) {
                    crossfade(true)
                }
            }

            val answer = turn.answer
            if (answer == null) {
                tvAnswer.text = "-"
                return
            }
            val isListen = card.type == "LISTEN"
            when (answer.mediaType.lowercase()) {
                "text" -> {
                    tvAnswer.text = answer.value ?: "-"
                    // LISTEN은 correct 표시 (§8.3 계약)
                    if (isListen && answer.correct != null) {
                        tvAnswer.text = "${answer.value} (${if (answer.correct) "정답" else "오답"})"
                    }
                    ivImage.visibility = View.GONE
                    btnVoice.visibility = View.GONE
                }
                "image" -> {
                    // LISTEN_PICTURE: value = image_id 문자열 → 콘텐츠 프록시 이미지 로딩
                    tvAnswer.text = "(그림 선택지)"
                    ivImage.visibility = View.VISIBLE
                    val imgId = answer.value ?: ""
                    if (imgId.isNotBlank()) {
                        ivImage.load(
                            activity.resolveUrl("/api/v1/content/images/$imgId/file"),
                            AuthImageLoader.get(activity)
                        ) { crossfade(true) }
                    }
                    if (isListen && answer.correct != null) {
                        tvAnswer.text = "(그림 선택지 — ${if (answer.correct) "정답" else "오답"})"
                    }
                    btnVoice.visibility = View.GONE
                }
                "voice" -> {
                    // 음성형: STT 텍스트 + 유저 음성 voiceUrl 재생
                    tvAnswer.text = answer.value ?: "-"
                    if (answer.voiceUrl != null) {
                        btnVoice.visibility = View.VISIBLE
                        btnVoice.setOnClickListener { activity.playVoice(answer.voiceUrl) }
                    } else {
                        btnVoice.visibility = View.GONE
                    }
                }
                else -> tvAnswer.text = answer.value ?: "-"
            }
            // tvImageNote는 미사용 (이미지는 ivImage에 로드) — GONE 유지
            tvImageNote.visibility = View.GONE
        }
    }
}

/**
 * 대화 내역 어댑터 — AI text(±ttsUrl) / USER text(±voiceUrl 다시 듣기).
 * §8.3 talkHistory 계약 그대로.
 */
class TalkHistoryAdapter(private val activity: SessionDetailActivity) :
    RecyclerView.Adapter<TalkHistoryAdapter.VH>() {

    private val items = mutableListOf<TalkHistoryItem>()

    fun submitList(list: List<TalkHistoryItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = com.sesac.speechapp.databinding.ItemTalkHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: com.sesac.speechapp.databinding.ItemTalkHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TalkHistoryItem) {
            val isAi = item.speaker.equals("AI", ignoreCase = true)
            binding.tvAiText.visibility = if (isAi) View.VISIBLE else View.GONE
            binding.containerUser.visibility = if (isAi) View.GONE else View.VISIBLE

            if (isAi) {
                binding.tvAiText.text = item.text
                // AI 발화 TTS도 다시 듣기 가능하면 재생 버튼 제공 — 레이아웃 단순화 위해 텍스트 탭 재생
                if (item.ttsUrl != null) {
                    binding.tvAiText.setOnClickListener { activity.playVoice(item.ttsUrl) }
                } else {
                    binding.tvAiText.setOnClickListener(null)
                }
            } else {
                binding.tvUserText.text = item.text
                if (item.voiceUrl != null) {
                    binding.btnUserVoice.visibility = View.VISIBLE
                    binding.btnUserVoice.setOnClickListener { activity.playVoice(item.voiceUrl) }
                } else {
                    binding.btnUserVoice.visibility = View.GONE
                }
            }
        }
    }
}