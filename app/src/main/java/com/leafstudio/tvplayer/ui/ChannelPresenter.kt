package com.leafstudio.tvplayer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.leafstudio.tvplayer.R
import com.leafstudio.tvplayer.model.Channel

/**
 * 频道卡片 Presenter
 * 用于在 Leanback UI 中展示频道项
 */
class ChannelPresenter : Presenter() {
    
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val cardView = viewHolder.view
        
        val titleView = cardView.findViewById<TextView>(R.id.channel_title)
        val groupView = cardView.findViewById<TextView>(R.id.channel_group)
        
        titleView.text = channel.name
        groupView.text = channel.group ?: "未分类"
        
        // 设置卡片尺寸
        cardView.layoutParams = ViewGroup.LayoutParams(300, 150)
    }
    
    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // 清理资源
    }
}
