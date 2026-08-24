package com.sesac.speechapp.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sesac.speechapp.R
import com.sesac.speechapp.databinding.ItemSessionHistoryBinding

class SessionHistoryAdapter : RecyclerView.Adapter<SessionHistoryAdapter.ViewHolder>() {

    private val items = mutableListOf<SessionHistory>()

    fun submitList(newList: List<SessionHistory>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemSessionHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SessionHistory) {
            binding.tvDate.text = item.date
            binding.tvTopic.text = item.topic
            binding.tvScore.text = "${item.score}점"
            binding.tvTurnCount.text = "${item.turnCount}턴"

            val statusText = if (item.isCompleted) "완료" else "진행중"
            val statusColor = if (item.isCompleted) {
                R.color.success
            } else {
                R.color.warning
            }
            binding.tvStatus.text = statusText
            binding.tvStatus.setTextColor(binding.root.context.getColor(statusColor))
        }
    }
}
