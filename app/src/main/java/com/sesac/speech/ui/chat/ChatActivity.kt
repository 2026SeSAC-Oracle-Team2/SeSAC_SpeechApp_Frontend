package com.sesac.speech.ui.chat

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sesac.speech.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val chatAdapter = ChatAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeMessages()

        // TODO: Mic FAB 클릭 -> 녹음 오버레이 표시 (Phase 2)
        //  binding.micFab.setOnClickListener { ... }
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
            // 새 메시지 추가 시 맨 아래로 스크롤
            binding.rvChat.post {
                binding.rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }
}
