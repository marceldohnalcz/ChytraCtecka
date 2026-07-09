package io.github.marciano.smartreader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LibraryAdapter(
    private var items: List<SavedText>,
    private val selectedIds: MutableSet<String>,
    private val onItemClick: (SavedText) -> Unit,
    private val onDeleteClick: (SavedText) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.cbLibraryItem)
        val title: TextView = view.findViewById(R.id.tvLibraryItemTitle)
        val delete: ImageButton = view.findViewById(R.id.btnLibraryItemDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title

        // Zrušit starý listener před nastavením stavu, ať nespustí falešnou událost
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selectedIds.contains(item.id)
        holder.checkbox.setOnCheckedChangeListener { _, checked ->
            if (checked) selectedIds.add(item.id) else selectedIds.remove(item.id)
            onSelectionChanged()
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.delete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<SavedText>) {
        items = newItems
        notifyDataSetChanged()
    }
}
