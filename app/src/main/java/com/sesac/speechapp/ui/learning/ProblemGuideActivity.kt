package com.sesac.speechapp.ui.learning

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.sesac.speechapp.R
import com.sesac.speechapp.data.remote.dto.session.SessionCreateData
import com.sesac.speechapp.data.remote.dto.session.TurnDto
import com.sesac.speechapp.databinding.ActivityProblemGuideBinding

/**
 * D-7 1.1 문제 가이드 화면 — 턴마다 표시되는 안내 화면 (06 §3).
 *
 * - 안내 텍스트만, 안내 TTS 없음 (기획 확정)
 * - 타입별 안내 문구: strings_d7.xml guide_* (06 §3 기획 확정 문구 그대로)
 * - [준비됐어요!] 클릭 → 문제 화면(ProblemActivity, 해당 턴)
 *
 * 진입: LearningSessionLoadingActivity(최초) / ProblemActivity [다음으로](턴 이동)
 */
class ProblemGuideActivity : AppCompatActivity() {

    companion object {
        /** 문제 화면 → 다음 턴 가이드 복귀용 */
        const val EXTRA_TURN_INDEX = "turn_index"
    }

    private lateinit var binding: ActivityProblemGuideBinding

    private var turnIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cacheData = SessionFlowCache.get()
        if (cacheData == null) {
            // 비정상 진입(프로세스 재생성 등) — 플로우 복귀 불가
            finish()
            return
        }
        val turns = cacheData.turns
        turnIndex = intent.getIntExtra(EXTRA_TURN_INDEX, 0)
            .coerceIn(0, (turns.size - 1).coerceAtLeast(0))

        render(turns[turnIndex], turnIndex, turns.size)

        binding.btnReady.setOnClickListener {
            val intent = Intent(this, ProblemActivity::class.java)
                .putExtra(ProblemActivity.EXTRA_SESSION_ID, cacheData.sessionId)
                .putExtra(ProblemActivity.EXTRA_TURN_INDEX, turnIndex)
            startActivity(intent)
            finish()
        }
    }

    /** 턴 타입별 안내 문구 조립 — 06 §3 기획 확정 문구 (strings_d7.xml) */
    private fun render(turn: TurnDto, index: Int, total: Int) {
        binding.tvProgress.text = getString(R.string.progress_turn_fmt, index + 1, total)
        binding.progressBar.progress = ((index + 1) * 100 / total)

        binding.tvTypeBadge.text = ProblemActivity.typeLabel(turn.type)

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
            binding.tvGuideExtra.visibility = View.GONE
        } else {
            binding.tvGuideExtra.text = extra
            binding.tvGuideExtra.visibility = View.VISIBLE
        }
    }
}