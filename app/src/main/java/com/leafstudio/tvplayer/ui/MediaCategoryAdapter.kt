package com.leafstudio.tvplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.leafstudio.tvplayer.R
import com.leafstudio.tvplayer.model.Category

class MediaCategoryAdapter(
    private var categories: List<Category>,
    private var onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<MediaCategoryAdapter.ViewHolder>() {

    private var selectedPosition = 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tv_category_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.nameText.text = category.type_name
        
        holder.itemView.isSelected = position == selectedPosition
        
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            onCategoryClick(category)
        }
    }

    override fun getItemCount() = categories.size
    
    fun updateCategories(newCategories: List<Category>) {
        categories = newCategories
        selectedPosition = 0
        notifyDataSetChanged()
    }
    
    fun setOnCategoryClickListener(listener: (Category) -> Unit) {
        this.onCategoryClick = listener
    }
    
    fun getSelectedCategory(): Category? {
        return if (categories.isNotEmpty() && selectedPosition in categories.indices) {
            categories[selectedPosition]
        } else {
            null
        }
    }
}
