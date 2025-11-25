package com.leafstudio.tvplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.leafstudio.tvplayer.model.Channel
import com.leafstudio.tvplayer.network.PlaylistLoader
import com.leafstudio.tvplayer.parser.LiveParser
import kotlinx.coroutines.launch

/**
 * 手机版主 Activity - 使用 RecyclerView
 */
class PhoneMainActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private val playlistLoader = PlaylistLoader()
    private val liveParser = LiveParser()
    private var allChannels: List<Channel> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_main)
        
        // 初始化 RecyclerView
        recyclerView = findViewById(R.id.channel_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        supportActionBar?.title = getString(R.string.browse_title)
        
        // 加载数据
        loadPlaylist()
    }
    
    /**
     * 加载 M3U 播放列表
     */
    private fun loadPlaylist() {
        val playlistUrl = getString(R.string.default_playlist_url)
        
        lifecycleScope.launch {
            try {
                Toast.makeText(this@PhoneMainActivity, R.string.loading, Toast.LENGTH_SHORT).show()
                
                // 异步加载和解析
                val content = playlistLoader.loadPlaylist(playlistUrl)
                val channels = liveParser.parse(content)
                allChannels = channels
                
                if (channels.isEmpty()) {
                    Toast.makeText(this@PhoneMainActivity, R.string.no_channels, Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // 设置适配器
                adapter = ChannelAdapter(channels) { channel ->
                    playChannel(channel)
                }
                recyclerView.adapter = adapter
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@PhoneMainActivity,
                    getString(R.string.error_loading_playlist) + ": ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * 播放选中的频道
     * 如果频道有多个线路，显示选择对话框
     */
    private fun playChannel(channel: Channel) {
        if (channel.getRouteCount() > 1) {
            // 多线路，显示选择对话框
            showRouteSelectionDialog(channel)
        } else {
            // 单线路，直接播放
            startPlayback(channel, 0)
        }
    }
    
    /**
     * 显示线路选择对话框
     */
    private fun showRouteSelectionDialog(channel: Channel) {
        val routes = Array(channel.getRouteCount()) { index ->
            channel.getRouteName(index)
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.route_selection_title)
            .setItems(routes) { dialog, which ->
                startPlayback(channel, which)
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * 开始播放指定线路
     */
    private fun startPlayback(channel: Channel, routeIndex: Int) {
        channel.switchToRoute(routeIndex)
        
        // 找到频道在列表中的索引
        val channelIndex = allChannels.indexOf(channel)
        
        val intent = Intent(this, PlaybackActivity::class.java).apply {
            putParcelableArrayListExtra(PlaybackActivity.EXTRA_ALL_CHANNELS, ArrayList(allChannels))
            putExtra(PlaybackActivity.EXTRA_CURRENT_CHANNEL_INDEX, channelIndex)
        }
        startActivity(intent)
    }
    
    // RecyclerView Adapter
    /**
     * 频道列表适配器
     */
    private class ChannelAdapter(
        private val channels: List<Channel>,
        private val onChannelClick: (Channel) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.channel_name)
            val groupText: TextView = view.findViewById(R.id.channel_group_text)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel_phone, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val channel = channels[position]
            holder.nameText.text = channel.name
            holder.groupText.text = channel.group ?: "未分类"
            holder.itemView.setOnClickListener {
                onChannelClick(channel)
            }
        }
        
        override fun getItemCount() = channels.size
    }
}
