package com.leafstudio.tvplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import com.leafstudio.tvplayer.model.Channel
import com.leafstudio.tvplayer.network.PlaylistLoader
import com.leafstudio.tvplayer.parser.LiveParser
import com.leafstudio.tvplayer.ui.ChannelPresenter
import kotlinx.coroutines.launch

/**
 * TV 主 Activity - 使用 Leanback UI
 */
class TvMainActivity : FragmentActivity() {
    
    private lateinit var browseSupportFragment: BrowseSupportFragment
    private val playlistLoader = PlaylistLoader()
    private val liveParser = LiveParser()
    private var allChannels: List<Channel> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化 UI 和数据加载
        setupBrowseFragment()
        loadPlaylist()
    }
    
    /**
     * 初始化 Leanback BrowseFragment
     * 配置标题、头部状态和点击监听器
     */
    private fun setupBrowseFragment() {
        browseSupportFragment = supportFragmentManager
            .findFragmentById(R.id.main_browse_fragment) as BrowseSupportFragment
        
        browseSupportFragment.apply {
            title = getString(R.string.browse_title)
            headersState = BrowseSupportFragment.HEADERS_ENABLED
            isHeadersTransitionOnBackEnabled = true
            
            // 设置点击监听
            onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
                if (item is Channel) {
                    playChannel(item)
                }
            }
        }
    }
    
    /**
     * 加载 M3U 播放列表
     * 使用协程在后台加载和解析数据
     */
    private fun loadPlaylist() {
        val playlistUrl = getString(R.string.default_playlist_url)
        
        lifecycleScope.launch {
            try {
                // 显示加载提示
                Toast.makeText(this@TvMainActivity, R.string.loading, Toast.LENGTH_SHORT).show()
                
                // 加载播放列表
                val content = playlistLoader.loadPlaylist(playlistUrl)
                
                // 解析频道
                val channels = liveParser.parse(content)
                allChannels = channels
                
                if (channels.isEmpty()) {
                    Toast.makeText(this@TvMainActivity, R.string.no_channels, Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // 按分组组织频道
                val groupedChannels = channels.groupBy { it.group ?: "未分类" }
                
                // 创建行适配器
                val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
                
                groupedChannels.forEach { (groupName, channelList) ->
                    val listRowAdapter = ArrayObjectAdapter(ChannelPresenter())
                    channelList.forEach { channel ->
                        listRowAdapter.add(channel)
                    }
                    
                    val header = HeaderItem(groupName)
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                }
                
                browseSupportFragment.adapter = rowsAdapter
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@TvMainActivity,
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
        
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(R.string.route_selection_title)
        builder.setItems(routes) { dialog, which ->
            startPlayback(channel, which)
            dialog.dismiss()
        }
        builder.show()
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
}
