package com.sesac.speechapp.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sesac.speechapp.databinding.ItemChatAiMessageBinding
import com.sesac.speechapp.databinding.ItemChatTurnResultBinding
import com.sesac.speechapp.databinding.ItemChatUserMessageBinding

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    companion object {
        const val VIEW_TYPE_AI = 1
        const val VIEW_TYPE_USER = 2
        const val VIEW_TYPE_TURN_RESULT = 3
    }

    fun submitList(newList: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newList)
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return when (messages[position]) {
            is ChatMessage.AiMessage -> VIEW_TYPE_AI
            is ChatMessage.UserMessage -> VIEW_TYPE_USER
            is ChatMessage.TurnResult -> VIEW_TYPE_TURN_RESULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_AI -> {
                val binding = ItemChatAiMessageBinding.inflate(inflater, parent, false)
                AiMessageViewHolder(binding)
            }
            VIEW_TYPE_USER -> {
                val binding = ItemChatUserMessageBinding.inflate(inflater, parent, false)
                UserMessageViewHolder(binding)
            }
            VIEW_TYPE_TURN_RESULT -> {
                val binding = ItemChatTurnResultBinding.inflate(inflater, parent, false)
                TurnResultViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AiMessageViewHolder -> holder.bind(messages[position] as ChatMessage.AiMessage)
            is UserMessageViewHolder -> holder.bind(messages[position] as ChatMessage.UserMessage)
            is TurnResultViewHolder -> holder.bind(messages[position] as ChatMessage.TurnResult)
        }
    }

    override fun getItemCount(): Int = messages.size

    // ─── ViewHolders ────────────────────────────────────────

    inner class AiMessageViewHolder(
        private val binding: ItemChatAiMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessage.AiMessage) {
            binding.tvContent.text = item.content
            binding.tvTimestamp.text = item.timestamp
        }
    }

    inner class UserMessageViewHolder(
        private val binding: ItemChatUserMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessage.UserMessage) {
            binding.tvContent.text = item.content
            binding.tvTimestamp.text = item.timestamp
        }
    }

    inner class TurnResultViewHolder(
        private val binding: ItemChatTurnResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessage.TurnResult) {
            binding.tvScore.text = item.overallScore.toString()
            binding.tvFeedback.text = item.feedbackText
            binding.tvTimestamp.text = item.timestamp
        }
    }
}
