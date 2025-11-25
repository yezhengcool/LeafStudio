package com.leafstudio.tvplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.leafstudio.tvplayer.R

/**
 * 地区列表适配器
 */
class GroupListAdapter(
    private val groups: List<String>,
    private var selectedGroupIndex: Int = 0,
    private val onGroupClick: (Int) -> Unit
) : RecyclerView.Adapter<GroupListAdapter.GroupViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position], position == selectedGroupIndex) {
            val oldIndex = selectedGroupIndex
            selectedGroupIndex = position
            notifyItemChanged(oldIndex)
            notifyItemChanged(position)
            onGroupClick(position)
        }
    }

    override fun getItemCount() = groups.size

    /**
     * 更新选中的地区
     */
    fun updateSelectedGroup(index: Int) {
        val oldIndex = selectedGroupIndex
        selectedGroupIndex = index
        notifyItemChanged(oldIndex)
        notifyItemChanged(index)
    }

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val groupName: TextView = view.findViewById(R.id.group_name)

        fun bind(group: String, isSelected: Boolean, onClick: () -> Unit) {
            groupName.text = group
            
            // 设置选中背景色为半透明白色蒙版
            if (isSelected) {
                itemView.setBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))
            } else {
                itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            
            itemView.setOnClickListener { onClick() }
        }
    }
}
