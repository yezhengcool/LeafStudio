package com.leafstudio.tvplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.net.ConnectivityManager
import android.net.Network
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.leafstudio.tvplayer.model.Channel
import com.leafstudio.tvplayer.network.PlaylistLoader

import kotlinx.coroutines.launch

/**
 * 主 Activity - 显示加载界面并加载播放列表
 */
class MainActivity : FragmentActivity() {
    
    private val playlistLoader = PlaylistLoader()
    private val liveParser = com.leafstudio.tvplayer.parser.LiveParser()
    
    private lateinit var backgroundImage: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var btnRetry: Button
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkLost = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化视图
        backgroundImage = findViewById(R.id.iv_splash_background)
        progressBar = findViewById(R.id.progress_loading)
        loadingText = findViewById(R.id.tv_loading_text)
        btnRetry = findViewById(R.id.btn_retry)
        
        btnRetry.setOnClickListener {
            retryLoading()
        }
        
        // 注册网络监听
        registerNetworkCallback()
        
        // 设置全屏模式
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        
        // 加载背景图片
        loadBackgroundImage()
        
        // 加载播放列表并播放第一个频道
        loadPlaylistAndPlay()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
    }
    
    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread {
                        if (btnRetry.visibility == View.VISIBLE) {
                            retryLoading()
                        }
                    }
                }
            }
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun retryLoading() {
        btnRetry.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        loadingText.text = "正在重试..."
        loadPlaylistAndPlay()
    }
    
    /**
     * 加载背景图片
     */
    private fun loadBackgroundImage() {
        try {
            // 使用用户指定的背景图接口
            Glide.with(this)
                .load("https://api.btstu.cn/sjbz/?lx=meizi")
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(backgroundImage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 加载播放列表并自动播放第一个频道
     */
    private fun loadPlaylistAndPlay() {
        val playlistUrl = getString(R.string.default_playlist_url)
        
        lifecycleScope.launch {
            try {
                // 更新加载文字
                loadingText.text = "正在加载播放列表..."
                
                // 加载播放列表
                val content = playlistLoader.loadPlaylist(playlistUrl)
                
                // 更新加载文字
                loadingText.text = "正在解析频道..."
                
                // 解析频道
                val channels = liveParser.parse(content)
                
                if (channels.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.no_channels, Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }
                
                // 更新加载文字
                loadingText.text = "准备播放..."
                
                // 查找上次播放的频道索引
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val lastChannelId = prefs.getString("last_channel_id", null)
                
                val channelIndex = if (lastChannelId != null) {
                    // 查找上次播放的频道
                    val index = channels.indexOfFirst { it.id == lastChannelId }.takeIf { it >= 0 } ?: 0
                    // 恢复上次选择的线路
                    if (index >= 0 && index < channels.size) {
                        val lastRouteIndex = prefs.getInt("last_route_${lastChannelId}", 0)
                        channels[index].currentRouteIndex = lastRouteIndex
                    }
                    index
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
                loadingText.text = "加载失败，请检查网络"
                progressBar.visibility = View.GONE
                btnRetry.visibility = View.VISIBLE
                
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_loading_playlist) + ": ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                // 不要 finish()，允许用户重试
            }
        }
    }
}
