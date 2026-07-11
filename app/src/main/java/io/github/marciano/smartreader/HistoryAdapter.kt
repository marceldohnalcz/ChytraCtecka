package io.github.marciano.smartreader

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private var items: List<HistoryEntry>,
    private val onItemClick: (HistoryEntry) -> Unit,
    private val onDeleteClick: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvHistoryItemTitle)
        val delete: ImageButton = view.findViewById(R.id.btnHistoryItemDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        if (item.played) {
            val text = "▶ ${item.preview}"
            val spannable = SpannableString(text)
            val greenColor = ContextCompat.getColor(holder.itemView.context, R.color.brand_play)
            spannable.setSpan(ForegroundColorSpan(greenColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            holder.title.text = spannable
        } else {
            holder.title.text = item.preview
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.delete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<HistoryEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}
