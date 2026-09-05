package com.sesac.speechapp.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sesac.speechapp.R
import com.sesac.speechapp.databinding.ItemSessionHistoryBinding

/**
 * D-7 3.2 이력 카드 어댑터 — 실데이터 계약 (05a §8.2):
 * 세션명 sessionName / 날짜 YYYY.mm.dd (클라 포맷 완료값 수신) / "AQ nn점".
 * 카드 터치 → 세부 보고서 (onItemClick 콜백 — DashboardFragment에서 sessionId 전달).
 */
class SessionHistoryAdapter : RecyclerView.Adapter<SessionHistoryAdapter.ViewHolder>() {

    var onItemClick: ((SessionHistory) -> Unit)? = null

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
            binding.tvScore.text = item.score.toString()
            // 05a §8.2: aq는 "AQ nn점"으로 클라 포맷
            binding.tvStatus.text = binding.root.context.getString(R.string.dash_aq_fmt, item.score)
            binding.tvStatus.setTextColor(binding.root.context.getColor(R.color.success))
            binding.tvTurnCount.visibility = View.GONE

            binding.root.setOnClickListener { onItemClick?.invoke(item) }
        }
    }
}