package com.sesac.speechapp.ui.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sesac.speechapp.databinding.ActivityChatBinding
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()

    private var recordingTimerHandler: Handler? = null
    private var recordingRunnable: Runnable? = null
    private var elapsedSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeMessages()
        setupMicButton()
        setupStopButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = chatAdapter
        }
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages)
            binding.rvChat.post {
                if (chatAdapter.itemCount > 0) {
                    binding.rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        }
    }

    private fun setupMicButton() {
        binding.micFab.setOnClickListener {
            showRecordingOverlay()
            startRecordingTimer()
        }
    }

    private fun setupStopButton() {
        binding.btnStop.setOnClickListener {
            stopRecordingTimer()
            hideRecordingOverlay()
            viewModel.onRecordingStopped()
        }
    }

    // ─── Recording Overlay ─────────────────────────────────────

    private fun showRecordingOverlay() {
        binding.recordingOverlay.visibility = View.VISIBLE
        binding.micFab.visibility = View.GONE
        elapsedSeconds = 0
        updateTimerUI()
    }

    private fun hideRecordingOverlay() {
        binding.recordingOverlay.visibility = View.GONE
        binding.micFab.visibility = View.VISIBLE
    }

    private fun startRecordingTimer() {
        recordingTimerHandler = Handler(Looper.getMainLooper())
        recordingRunnable = object : Runnable {
            override fun run() {
                elapsedSeconds++
                updateTimerUI()
                if (elapsedSeconds < 30) {
                    recordingTimerHandler?.postDelayed(this, 1000)
                } else {
                    stopRecordingTimer()
                    hideRecordingOverlay()
                    viewModel.onRecordingStopped()
                }
            }
        }
        recordingTimerHandler?.postDelayed(recordingRunnable!!, 1000)
    }

    private fun stopRecordingTimer() {
        recordingRunnable?.let { recordingTimerHandler?.removeCallbacks(it) }
        recordingTimerHandler = null
        recordingRunnable = null
    }

    private fun updateTimerUI() {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        binding.tvTimer.text =
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        binding.progressTimer.progress = elapsedSeconds
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecordingTimer()
    }
}
