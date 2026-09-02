package com.sesac.speechapp.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.File

/**
 * P3-26: 녹음 로직 재사용 추출 (RecordingTestActivity에서).
 *
 * - MediaRecorder AAC/m4a (백엔드 규약: audio/mp4)
 * - API 31+ 조건부 생성자 유지 (603f4ee 회귀 금지)
 * - 권한 체크/타이머/파일 정리 포함 — Activity는 콜백만 구현
 *
 * 사용법:
 *   val helper = RecordingHelper(context, onTick = { sec -> ... })
 *   helper.checkPermissionOrStart(onReady)  // 권한 런처는 Activity 쪽에서
 */
class RecordingHelper(
    private val context: Context,
    private val onTick: ((seconds: Int) -> Unit)? = null
) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var secondsElapsed = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    val recording: Boolean get() = isRecording
    val elapsedSeconds: Int get() = secondsElapsed
    val file: File? get() = outputFile?.takeIf { it.exists() }

    /** 권한 상태 확인 */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** 녹음 시작 (권한 있어야 호출됨) */
    fun start(): Boolean {
        if (isRecording) return false
        outputFile = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile!!.absolutePath)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                release()
                mediaRecorder = null
                return false
            }
        }
        isRecording = true
        secondsElapsed = 0
        onTick?.invoke(0)
        startTimer()
        return true
    }

    /** 녹음 중지 — 파일 반환 (실패/빈파일 null) */
    fun stop(): File? {
        mediaRecorder?.apply {
            try { stop() } catch (_: Exception) { /* 빈 녹음 */ }
            release()
        }
        mediaRecorder = null
        isRecording = false
        stopTimer()
        return outputFile?.takeIf { it.exists() && it.length() > 0 }
    }

    /** Activity onDestroy에서 호출 */
    fun release() {
        if (isRecording) stop()
        stopTimer()
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    secondsElapsed++
                    onTick?.invoke(secondsElapsed)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }
}