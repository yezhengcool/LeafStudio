package com.leafstudio.tvplayer.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.leafstudio.tvplayer.R
import com.leafstudio.tvplayer.model.Channel

/**
 * 频道侧边栏适配器 - 显示单个地区的频道列表
 */
class ChannelSidebarAdapter(
    private var channels: List<Channel>,
    private var currentChannel: Channel? = null,
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelSidebarAdapter.ChannelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel_sidebar, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        // 使用对象比较来确定是否选中
        val isSelected = currentChannel != null && channel == currentChannel
        holder.bind(channel, position, isSelected) {
            onChannelClick(channel)
        }
    }

    override fun getItemCount() = channels.size

    /**
     * 更新频道列表
     */
    fun updateChannels(newChannels: List<Channel>, current: Channel?) {
        channels = newChannels
        currentChannel = current
        notifyDataSetChanged()
    }

    /**
     * 更新当前播放频道
     */
    fun updateCurrentChannel(channel: Channel?) {
        currentChannel = channel
        notifyDataSetChanged()
    }

    /**
     * 频道项 ViewHolder
     */
    class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val channelNumber: TextView = view.findViewById(R.id.channel_number)
        private val channelLogo: ImageView = view.findViewById(R.id.channel_logo)
        private val channelName: TextView = view.findViewById(R.id.channel_name)

        fun bind(channel: Channel, channelIndex: Int, isCurrent: Boolean, onClick: () -> Unit) {
            // 显示全局台号
            channelNumber.text = if (channel.number > 0) {
                channel.number.toString()
            } else {
                "${channelIndex + 1}"
            }
            
            channelName.text = channel.name
            
            // 设置选中背景色为半透明白色蒙版
            if (isCurrent) {
                itemView.setBackgroundColor(Color.parseColor("#33FFFFFF"))
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT)
            }
            
            // 加载台标
            if (!channel.logo.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(channel.logo)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(channelLogo)
            } else {
                channelLogo.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            
            itemView.setOnClickListener { onClick() }
        }
    }
}
