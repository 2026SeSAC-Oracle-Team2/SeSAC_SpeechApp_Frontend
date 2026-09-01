package com.sesac.speechapp.ui.record

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.remote.api.ApiService
import com.sesac.speechapp.databinding.ActivityRecordingTestBinding
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * P3-21: 디버깅용 녹음 테스트 Activity
 * - MediaRecorder AAC/m4a
 * - 녹음 시작/중지 + 시간 카운트
 * - 업로드 테스트 → POST /api/v1/voice/upload
 * - 응답 raw 표시
 * TODO: UI 확정. Trust Blue 토큰 준수하되 최소한으로.
 */
class RecordingTestActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONTENT_TYPE = "content_type"
    }

    private lateinit var binding: ActivityRecordingTestBinding
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var secondsElapsed = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private lateinit var tokenManager: TokenManager
    private lateinit var apiService: ApiService

    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else showToast("RECORD_AUDIO 권한 필요")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)
        apiService = com.sesac.speechapp.data.remote.RetrofitClient.apiService

        val contentType = intent.getStringExtra(EXTRA_CONTENT_TYPE) ?: "SHADOWING"
        binding.tvContentType.text = "유형: $contentType"

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else checkPermissionAndRecord()
        }

        binding.btnUpload.setOnClickListener {
            uploadRecording(contentType)
        }

        binding.btnUpload.isEnabled = false
    }

    private fun checkPermissionAndRecord() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED -> startRecording()
            else -> recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        outputFile = File(cacheDir, "test_recording_${System.currentTimeMillis()}.m4a")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        }
        isRecording = true
        secondsElapsed = 0
        binding.btnRecord.text = "중지"
        binding.tvTimer.text = "00:00"
        startTimer()
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        mediaRecorder = null
        isRecording = false
        stopTimer()
        binding.btnRecord.text = "녹음 시작"
        binding.btnUpload.isEnabled = true
        showToast("녹음 완료 (${secondsElapsed}초)")
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    secondsElapsed++
                    val min = secondsElapsed / 60
                    val sec = secondsElapsed % 60
                    binding.tvTimer.text = String.format("%02d:%02d", min, sec)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun uploadRecording(contentType: String) {
        val file = outputFile ?: return showToast("녹음 파일 없음")
        if (!file.exists()) return showToast("파일 없음")

        binding.tvResult.text = "업로드 중..."

        val filePart = MultipartBody.Part.createFormData(
            "file", file.name,
            file.asRequestBody("audio/mp4".toMediaTypeOrNull())
        )
        val userIdPart = "1".toRequestBody("text/plain".toMediaTypeOrNull())
        val contentTypePart = contentType.toRequestBody("text/plain".toMediaTypeOrNull())

        lifecycleScope.launch {
            try {
                val response = apiService.uploadVoice(
                    file = filePart,
                    userId = userIdPart,
                    contentType = contentTypePart
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    binding.tvResult.text = body?.data?.let {
                        "voiceRecordId=${it.voiceRecordId}\nturnId=${it.turnId}\nsessionId=${it.sessionId}\nfilePath=${it.filePath}"
                    } ?: "응답 없음"
                } else {
                    binding.tvResult.text = "업로드 실패: ${response.code()}"
                }
            } catch (e: Exception) {
                binding.tvResult.text = "예외: ${e.message}"
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) stopRecording()
    }
}
