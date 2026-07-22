package io.github.marciano.smartreader

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TrackedProfilesAdapter(
    private var items: List<TrackedProfile>,
    private val onOpenClick: (TrackedProfile) -> Unit,
    private val onMenuClick: (TrackedProfile, View) -> Unit
) : RecyclerView.Adapter<TrackedProfilesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvProfileName)
        val lastChecked: TextView = view.findViewById(R.id.tvProfileLastChecked)
        val open: ImageButton = view.findViewById(R.id.btnProfileOpen)
        val menu: ImageButton = view.findViewById(R.id.btnProfileMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tracked_profile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.name.text = item.name
        holder.lastChecked.text = if (item.lastCheckedTimestamp != null) {
            val relative = DateUtils.getRelativeTimeSpanString(
                item.lastCheckedTimestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            context.getString(R.string.label_last_checked, relative)
        } else {
            context.getString(R.string.label_never_checked)
        }
        holder.open.setOnClickListener { onOpenClick(item) }
        holder.menu.setOnClickListener { onMenuClick(item, holder.menu) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<TrackedProfile>) {
        items = newItems
        notifyDataSetChanged()
    }
}
