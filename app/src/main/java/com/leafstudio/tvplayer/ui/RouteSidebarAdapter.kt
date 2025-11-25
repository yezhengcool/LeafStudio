package com.leafstudio.tvplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.leafstudio.tvplayer.R

/**
 * 线路侧边栏适配器
 */
class RouteSidebarAdapter(
    private val routes: List<String>,
    private var currentRouteIndex: Int = 0,
    private val onRouteClick: (Int) -> Unit
) : RecyclerView.Adapter<RouteSidebarAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_sidebar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(routes[position], position == currentRouteIndex) {
            onRouteClick(position)
        }
    }

    override fun getItemCount() = routes.size

    /**
     * 更新当前线路
     */
    fun updateCurrentRoute(index: Int) {
        val oldIndex = currentRouteIndex
        currentRouteIndex = index
        notifyItemChanged(oldIndex)
        notifyItemChanged(index)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val routeName: TextView = view.findViewById(R.id.route_name)

        fun bind(name: String, isCurrent: Boolean, onClick: () -> Unit) {
            routeName.text = name
            
            // 高亮当前线路
            if (isCurrent) {
                itemView.setBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))
            } else {
                itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            
            itemView.setOnClickListener { onClick() }
        }
    }
}
