package com.leafstudio.tvplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.leafstudio.tvplayer.model.Channel
import com.leafstudio.tvplayer.network.PlaylistLoader

import kotlinx.coroutines.launch

/**
 * 主 Activity - 直接加载播放列表并播放第一个频道
 */
class MainActivity : FragmentActivity() {
    
    private val playlistLoader = PlaylistLoader()
    private val liveParser = com.leafstudio.tvplayer.parser.LiveParser()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 直接加载播放列表并播放第一个频道
        loadPlaylistAndPlay()
    }
    
    /**
     * 加载播放列表并自动播放第一个频道
     */
    private fun loadPlaylistAndPlay() {
        val playlistUrl = getString(R.string.default_playlist_url)
        
        lifecycleScope.launch {
            try {
                // 显示加载提示
                Toast.makeText(this@MainActivity, R.string.loading, Toast.LENGTH_SHORT).show()
                
                // 加载播放列表
                val content = playlistLoader.loadPlaylist(playlistUrl)
                
                // 解析频道
                val channels = liveParser.parse(content)
                
                if (channels.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.no_channels, Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }
                
                // 查找上次播放的频道索引
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val lastChannelId = prefs.getString("last_channel_id", null)
                
                val channelIndex = if (lastChannelId != null) {
                    // 查找上次播放的频道
                    channels.indexOfFirst { it.id == lastChannelId }.takeIf { it >= 0 } ?: 0
                } else {
                    0
                }
                
                // 播放找到的频道
                val intent = Intent(this@MainActivity, PlaybackActivity::class.java).apply {
                    putParcelableArrayListExtra(PlaybackActivity.EXTRA_ALL_CHANNELS, ArrayList(channels))
                    putExtra(PlaybackActivity.EXTRA_CURRENT_CHANNEL_INDEX, channelIndex)
                }
                startActivity(intent)
                finish()
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_loading_playlist) + ": ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
}
