package com.leafstudio.tvplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 媒体配置适配器
 */
class MediaAdapter(
    private val items: List<MediaConfigItem>
) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.item_title)
        val contentText: TextView = view.findViewById(R.id.item_content)
        val typeText: TextView = view.findViewById(R.id.item_type)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_config, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleText.text = item.title
        holder.contentText.text = item.content
        holder.typeText.text = when (item.type) {
            MediaConfigType.SPIDER -> "爬虫"
            MediaConfigType.PROXY -> "代理"
            MediaConfigType.HOSTS -> "主机"
            MediaConfigType.HEADERS -> "请求头"
        }
        
        // 设置类型标签的背景色
        val typeColor = when (item.type) {
            MediaConfigType.SPIDER -> 0x66FF6B6B.toInt()
            MediaConfigType.PROXY -> 0x664ECDC4.toInt()
            MediaConfigType.HOSTS -> 0x66FFA502.toInt()
            MediaConfigType.HEADERS -> 0x665F27CD.toInt()
        }
        holder.typeText.setBackgroundColor(typeColor)
    }
    
    override fun getItemCount() = items.size
}
