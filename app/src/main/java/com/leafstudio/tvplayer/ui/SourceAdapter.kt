package com.leafstudio.tvplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.leafstudio.tvplayer.R

class SourceAdapter(
    private var sources: List<String>,
    private val onSourceClick: (Int) -> Unit
) : RecyclerView.Adapter<SourceAdapter.ViewHolder>() {

    private var selectedPosition = 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tv_episode_name) // Reuse episode item layout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false) // Reuse item_episode
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.nameText.text = sources[position]
        holder.itemView.isSelected = position == selectedPosition
        
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            onSourceClick(selectedPosition)
        }
    }

    override fun getItemCount() = sources.size
    
    fun updateSources(newSources: List<String>) {
        this.sources = newSources
        this.selectedPosition = 0
        notifyDataSetChanged()
    }
}
