package com.leafstudio.tvplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.leafstudio.tvplayer.databinding.ActivityPlaybackBinding
import com.leafstudio.tvplayer.model.Channel
import com.leafstudio.tvplayer.ui.ChannelSidebarAdapter
import com.leafstudio.tvplayer.ui.RouteSidebarAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.leafstudio.tvplayer.utils.UpdateManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// 新增标记，用于 RTMP 自动切换防止递归
private var hasAutoSwitchedForRtmp = false

/**
 * 播放 Activity - 支持侧边栏频道列表、线路选择和数字键换台
 */
class PlaybackActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPlaybackBinding
    private var player: ExoPlayer? = null
    
    // 频道数据
    private var allChannels: ArrayList<Channel>? = null
    private var currentChannelIndex: Int = 0
    private var currentRouteIndex: Int = 0
    private var hasTriedAllRoutes: Boolean = false
    
    // 侧边栏
    private var channelSidebarAdapter: ChannelSidebarAdapter? = null
    private var routeSidebarAdapter: RouteSidebarAdapter? = null
    private var isChannelSidebarVisible = false
    private var isRouteSidebarVisible = false
    
    // 画面比例模式：0=适应, 1=固定宽, 2=固定高, 3=填充, 4=裁剪
    // 画面比例模式：0=全屏,1=剪裁,2=16:9,3=原始,4=4:3,5=填充
    private var aspectRatioMode = 0  // 默认全屏
    
    // 数字键换台
    private val digitBuffer = StringBuilder()
    private val digitHandler = Handler(Looper.getMainLooper())
    private val digitRunnable = Runnable { processDigitInput() }
    
    // 自动隐藏控制栏
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideBottomControls() }
    private val AUTO_HIDE_DELAY = 5000L // 5秒后自动隐藏
    
    // 时间更新
    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            timeHandler.postDelayed(this, 1000) // 每秒更新
        }
    }
    
    // 分辨率显示
    private val resolutionHandler = Handler(Looper.getMainLooper())
    private val resolutionRunnable = Runnable { binding.tvResolution.visibility = View.GONE }
    
    // 台号显示
    private val channelNumberHandler = Handler(Looper.getMainLooper())
    private val channelNumberRunnable = Runnable { binding.tvChannelNumber.visibility = View.GONE }
    
    // IJKPlayer support (using Any to avoid compilation errors when library is not present)
    private var ijkMediaPlayer: Any? = null
    private var currentDecoderType = 0 // 0=ExoPlayer, 1=IJK硬解, 2=IJK软解
    
    companion object {
        const val EXTRA_ALL_CHANNELS = "all_channels"
        const val EXTRA_CURRENT_CHANNEL_INDEX = "current_channel_index"
        
        // 兼容旧版本
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_CHANNEL_URL = "channel_url"
        const val EXTRA_CHANNEL_URLS = "channel_urls"
        const val EXTRA_CURRENT_ROUTE_INDEX = "current_route_index"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 检查激活状态 - 必须通过API，以便数据同步到管理后台
        lifecycleScope.launch {
            val info = com.leafstudio.tvplayer.utils.ActivationManager.checkActivationStatus(this@PlaybackActivity)
            
            // 如果是网络错误，提示用户并退出
            if (info.message.contains("网络错误") || info.message.contains("API错误")) {
                android.util.Log.e("PlaybackActivity", "无法连接到激活服务器: ${info.message}")
                
                // 显示友好的错误提示
                runOnUiThread {
                    android.app.AlertDialog.Builder(this@PlaybackActivity)
                        .setTitle("无法连接服务器")
                        .setMessage("激活服务暂时无法访问，请稍后再试或联系管理员。\n\n错误信息: ${info.message}")
                        .setPositiveButton("退出") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }
                return@launch
            }
            
            if (!info.isValid) {
                // 已过期，强制显示激活对话框
                showActivationDialog(true, info.message)
                return@launch // 不继续初始化
            }
            
            // 已激活或试用期内（API已自动注册设备到数据库），正常初始化
            initializeApp()
        }
    }
    
    /**
     * 初始化应用（在激活检查通过后调用）
     */
    private fun initializeApp() {
        
        // 获取频道数据
        loadChannelData()
        
        // 初始化侧边栏
        setupSidebars()
        
        // 设置底部按钮点击监听
        setupBottomButtons()
        
        // 初始化播放器
        initializePlayer()
        
        // 初始显示底部按钮，并启动自动隐藏计时器
        showBottomControls()
        
        // 初始化画面比例按钮文字
        binding.btnAspectRatio.text = "全屏"
        
        // 加载加载背景图
        loadLoadingBackground()
        
        // 加载跑马灯提示
        loadMarqueeText()
        
        // 检查更新
        UpdateManager.checkUpdate(this, false)
        
        // 设置全屏模式
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }
    
    /**
     * 加载频道数据
     */
    private fun loadChannelData() {
        allChannels = intent.getParcelableArrayListExtra(EXTRA_ALL_CHANNELS)
        currentChannelIndex = intent.getIntExtra(EXTRA_CURRENT_CHANNEL_INDEX, 0)
        
        // 兼容旧版本
        if (allChannels == null) {
            val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
            val channelUrls = intent.getStringArrayListExtra(EXTRA_CHANNEL_URLS)
            currentRouteIndex = intent.getIntExtra(EXTRA_CURRENT_ROUTE_INDEX, 0)
            
            if (channelUrls != null && channelName != null) {
                val channel = Channel(
                    id = "0",
                    name = channelName,
                    urls = channelUrls,
                    currentRouteIndex = currentRouteIndex
                )
                allChannels = arrayListOf(channel)
                currentChannelIndex = 0
            }
        }
        
        if (allChannels.isNullOrEmpty()) {
            Toast.makeText(this, R.string.error_playing_video, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 如果是从主界面启动（没有指定频道），尝试加载上次播放的频道
        if (currentChannelIndex == 0 && !intent.hasExtra(EXTRA_CURRENT_CHANNEL_INDEX)) {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val lastChannelId = prefs.getString("last_channel_id", "")
            if (!lastChannelId.isNullOrEmpty()) {
                val index = allChannels?.indexOfFirst { it.id == lastChannelId } ?: -1
                if (index >= 0) {
                    currentChannelIndex = index
                    // 恢复上次选择的线路
                    val lastRouteIndex = prefs.getInt("last_route_${lastChannelId}", 0)
                    allChannels?.get(index)?.currentRouteIndex = lastRouteIndex
                }
            }
        }
    }
    
    /**
     * 设置侧边栏
     */
    private fun setupSidebars() {
        val channels = allChannels ?: return
        
        // 按地区分组
        val groupedChannels = channels.groupBy { it.group ?: "未分类" }
        val groups = groupedChannels.keys.toList()
        
        // 左侧：地区列表
        val groupRecyclerView = findViewById<RecyclerView>(R.id.group_recyclerview)
        groupRecyclerView.layoutManager = LinearLayoutManager(this)
        val groupListAdapter = com.leafstudio.tvplayer.ui.GroupListAdapter(groups, 0) { groupIndex ->
            // 点击地区，更新右侧频道列表
            val selectedGroup = groups[groupIndex]
            val channelsInGroup = groupedChannels[selectedGroup] ?: emptyList()
            
            // 更新右侧频道列表
            channelSidebarAdapter?.updateChannels(channelsInGroup, getCurrentChannel())
        }
        groupRecyclerView.adapter = groupListAdapter
        
        // 右侧：频道列表（默认显示第一个地区的频道）
        val channelRecyclerView = findViewById<RecyclerView>(R.id.channel_recyclerview)
        channelRecyclerView.layoutManager = LinearLayoutManager(this)
        
        val firstGroup = groups.firstOrNull() ?: "未分类"
        val firstGroupChannels = groupedChannels[firstGroup] ?: emptyList()
        
        channelSidebarAdapter = ChannelSidebarAdapter(firstGroupChannels, getCurrentChannel()) { channel ->
            // 通过频道对象查找全局索引
            val globalIndex = channels.indexOf(channel)
            if (globalIndex >= 0) {
                switchToChannel(globalIndex)
                hideChannelSidebar()
            }
        }
        channelRecyclerView.adapter = channelSidebarAdapter
        
        // 线路选择侧边栏
        updateRouteSidebar()
        
        // 点击侧边栏背景关闭
        binding.sidebarChannelRoot.root.setOnClickListener {
            hideChannelSidebar()
        }
        binding.sidebarRouteRoot.root.setOnClickListener {
            hideRouteSidebar()
        }
    }
    
    /**
     * 更新线路侧边栏
     */
    private fun updateRouteSidebar() {
        val currentChannel = getCurrentChannel() ?: return
        val routes = currentChannel.urls.mapIndexed { index, _ ->
            currentChannel.getRouteName(index)
        }
        
        val routeRecyclerView = findViewById<RecyclerView>(R.id.route_recyclerview)
        routeRecyclerView.layoutManager = LinearLayoutManager(this)
        routeSidebarAdapter = RouteSidebarAdapter(routes, currentChannel.currentRouteIndex) { index ->
            switchToRoute(index)
            hideRouteSidebar()
        }
        routeRecyclerView.adapter = routeSidebarAdapter
    }
    
    /**
     * 设置底部按钮点击监听
     */
    private fun setupBottomButtons() {
        // 频道按钮
        binding.btnChannelList.setOnClickListener {
            if (isChannelSidebarVisible) {
                hideChannelSidebar()
            } else {
                showChannelSidebar()
                resetAutoHideTimer()
            }
        }
        
        // 线路按钮
        binding.btnRouteList.setOnClickListener {
            if (isRouteSidebarVisible) {
                hideRouteSidebar()
            } else {
                showRouteSidebar()
                resetAutoHideTimer()
            }
        }
        
        // 解码按钮
    binding.btnDecoder.setOnClickListener {
        toggleDecoder()
        resetAutoHideTimer()
    }
    
        // 菜单按钮
        binding.btnMenu.setOnClickListener {
            showMenuDialog()
            resetAutoHideTimer()
        }
        
        // 设置点击事件以显示/隐藏控制栏
        binding.playerView.setOnClickListener {
            try {
                toggleBottomControls()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 画面比例按钮
        binding.btnAspectRatio.setOnClickListener {
            toggleAspectRatio()
            resetAutoHideTimer()
        }
        
        // 多媒体按钮
        binding.btnMedia.setOnClickListener {
            val intent = Intent(this, MediaActivity::class.java)
            startActivity(intent)
            resetAutoHideTimer()
        }
    }

/**
 * 切换解码器
 * 顺序: 系统解码 -> IJK软解 -> IJK硬解 -> 系统解码
 */
private fun toggleDecoder() {
    // 0=ExoPlayer(系统), 1=IJK硬解, 2=IJK软解
    // 切换顺序: 0(系统) -> 2(软解) -> 1(硬解) -> 0(系统)
    var nextDecoder = when (currentDecoderType) {
        0 -> 2 // 系统 -> 软解
        2 -> 1 // 软解 -> 硬解
        1 -> 0 // 硬解 -> 系统
        else -> 0
    }

    // 增强的IJKPlayer可用性检查
    if (nextDecoder != 0) {
        if (!isIJKPlayerAvailable()) {
            Toast.makeText(this, "IJKPlayer库不可用，继续使用系统解码器", Toast.LENGTH_LONG).show()
            currentDecoderType = 0
            // 保存设置并直接返回（不需要重新初始化，因为已经是系统解码）
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putInt("decoder", currentDecoderType)
                .apply()
            return
        } else {
            // IJKPlayer可用，但也要提醒用户可能存在兼容性
            android.util.Log.i("PlaybackActivity", "IJKPlayer库检查通过，尝试切换到${if (nextDecoder == 1) "硬解" else "软解"}")
        }
    }

    currentDecoderType = nextDecoder

    // 保存设置
    getSharedPreferences("settings", MODE_PRIVATE).edit()
        .putInt("decoder", currentDecoderType)
        .apply()

    // 提示用户当前切换
    val decoderName = when (currentDecoderType) {
        0 -> "系统解码(ExoPlayer)"
        1 -> "IJK硬解"
        2 -> "IJK软解"
        else -> "系统解码"
    }

    if (currentDecoderType == 0) {
        Toast.makeText(this, "已切换为: $decoderName", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(this, "正在切换为: $decoderName...", Toast.LENGTH_SHORT).show()
    }

    // 重新初始化
    releasePlayer()
    initializePlayer()
}

/**
 * 初始化 IJKPlayer
 */
/**
 * 检查IJKPlayer是否可用
 * 增强版本：不仅检查库加载，还检查解码兼容性
 */
private fun isIJKPlayerAvailable(): Boolean {
    return try {
        // 1. 尝试加载IJKPlayer库
        tv.danmaku.ijk.media.player.IjkMediaPlayer.loadLibrariesOnce(null)
        tv.danmaku.ijk.media.player.IjkMediaPlayer.native_profileBegin("libijkplayer.so")

        // 2. 创建测试实例并配置选项
        val testPlayer = tv.danmaku.ijk.media.player.IjkMediaPlayer()

        // 3. 设置兼容性选项
        testPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", "0")
        testPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", "1")
        testPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", "1")

        // 4. 检查关键解码器支持
        testPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "nobuffer")
        testPlayer.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", "48")

        // 5. 释放测试实例
        testPlayer.release()

        android.util.Log.i("PlaybackActivity", "IJKPlayer库检查通过，支持主流解码格式")
        true
    } catch (e: UnsatisfiedLinkError) {
        android.util.Log.e("PlaybackActivity", "IJKPlayer本地库缺失: ${e.message}")
        false
    } catch (e: ClassNotFoundException) {
        android.util.Log.e("PlaybackActivity", "IJKPlayer类未找到: ${e.message}")
        false
    } catch (e: NoClassDefFoundError) {
        android.util.Log.e("PlaybackActivity", "IJKPlayer类定义错误: ${e.message}")
        false
    } catch (e: Throwable) {
        android.util.Log.e("PlaybackActivity", "IJKPlayer不可用: ${e.message}")
        false
    }
}

private fun initializeIJKPlayer(currentUrl: String, useHardwareDecoder: Boolean) {
    // 预检查IJKPlayer是否可用
    if (!isIJKPlayerAvailable()) {
        runOnUiThread {
            Toast.makeText(this, "IJKPlayer库不可用，自动切换到系统解码器", Toast.LENGTH_LONG).show()
            currentDecoderType = 0
            initializeExoPlayer(currentUrl)
        }
        return
    }

    try {

        // 2. 创建实例
        val player = tv.danmaku.ijk.media.player.IjkMediaPlayer()
        ijkMediaPlayer = player

        // 3. 设置参数 (精简版，提高稳定性)
        // 通用设置
        player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L) // 关闭 OpenSL ES，使用 AudioTrack
        player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", tv.danmaku.ijk.media.player.IjkMediaPlayer.SDL_FCC_RV32.toLong()) // 使用 RV32 格式
        player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L) // 允许丢帧
        player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L) // 准备好自动播放
        
        // 重连设置
        player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L)
        player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L)
        
        // 缓冲设置 (减少缓冲，提高起播速度)
        player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "nobuffer")
        player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0L)

        // 硬解/软解设置
        if (useHardwareDecoder) {
            player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
            player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
            player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
        } else {
            player.setOption(tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0L)
        }

        // 4. 设置数据源
        player.dataSource = currentUrl
        
        // 5. 设置显示 Surface (动态创建，避免 ExoPlayer 冲突)
        binding.playerView.removeAllViews()
        val surfaceView = android.view.SurfaceView(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        params.gravity = android.view.Gravity.CENTER
        surfaceView.layoutParams = params
        binding.playerView.addView(surfaceView)
        
        surfaceView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                player.setDisplay(holder)
            }
            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                // Surface 销毁时，必须解绑，否则可能导致 Native Crash
                if (ijkMediaPlayer != null) {
                    player.setDisplay(null)
                }
            }
        })
        
        // 6. 设置监听器
        player.setOnPreparedListener {
            it.start()
            hasTriedAllRoutes = false
            binding.ivLoadingBackground.visibility = View.GONE
        }
        
        player.setOnErrorListener { _, what, extra ->
            // 发生错误时，记录详细错误信息并尝试切换回系统解码
            val errorMessage = when (what) {
                tv.danmaku.ijk.media.player.IjkMediaPlayer.MEDIA_ERROR_UNKNOWN -> "未知错误"
                tv.danmaku.ijk.media.player.IjkMediaPlayer.MEDIA_ERROR_SERVER_DIED -> "服务器断开连接"
                tv.danmaku.ijk.media.player.IjkMediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> "不支持渐进式播放"
                tv.danmaku.ijk.media.player.IjkMediaPlayer.MEDIA_ERROR_IO -> "网络I/O错误"
                tv.danmaku.ijk.media.player.IjkMediaPlayer.MEDIA_ERROR_MALFORMED -> "媒体格式错误"
                tv.danmaku.ijk.media.player.IjkMediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "不支持的媒体格式"
                tv.danmaku.ijk.media.player.IjkMediaPlayer.MEDIA_ERROR_TIMED_OUT -> "播放超时"
                else -> "播放错误($what, $extra)"
            }

            android.util.Log.e("PlaybackActivity", "IJKPlayer错误: $errorMessage")

            runOnUiThread {
                Toast.makeText(this, "IJKPlayer出错($errorMessage)，切换回系统解码器", Toast.LENGTH_LONG).show()
                currentDecoderType = 0
                // 保存解码器设置，避免再次尝试IJKPlayer
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putInt("decoder", 0)
                    .apply()
                initializePlayer()
            }
            true
        }
        
        player.setOnCompletionListener {
            finish()
        }
        
        // 7. 异步准备
        player.prepareAsync()
        
    } catch (e: Throwable) {
        e.printStackTrace()
        // 捕获所有异常（包括 LinkError），降级到 ExoPlayer
        runOnUiThread {
            Toast.makeText(this, "IJKPlayer 启动失败，已切换回系统解码", Toast.LENGTH_LONG).show()
            currentDecoderType = 0
            getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("decoder", 0).apply()
            initializeExoPlayer(currentUrl)
        }
    }
}
    
    /**
     * 显示底部按钮并重置计时器
     */
    private fun showBottomControls() {
        binding.bottomControlsContainer.visibility = View.VISIBLE
        binding.topStatusContainer.visibility = View.VISIBLE
        updateTime() // 显示时立即更新时间
        timeHandler.removeCallbacks(timeRunnable)
        timeHandler.post(timeRunnable) // 开始时间更新
        resetAutoHideTimer()
    }
    
    /**
     * 隐藏底部按钮
     */
    private fun hideBottomControls() {
        // 如果侧边栏显示中，不隐藏按钮
        if (!isChannelSidebarVisible && !isRouteSidebarVisible) {
            binding.bottomControlsContainer.visibility = View.GONE
            // 顶部状态栏同步隐藏
            binding.topStatusContainer.visibility = View.GONE
            timeHandler.removeCallbacks(timeRunnable) // 停止时间更新
        }
    }
    
    /**
     * 更新顶部时间显示
     */
    /**
     * 更新顶部时间显示
     */
    private fun updateTime() {
        try {
            val now = java.util.Date()
            val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault())
            val timeStr = sdf.format(now)
            
            // 获取农历
            val lunarStr = com.leafstudio.tvplayer.utils.LunarCalendar.getLunarDate(now)
            // 获取节气
            val solarTerm = com.leafstudio.tvplayer.utils.LunarCalendar.getSolarTerm(now)
            
            val fullStr = if (solarTerm.isNotEmpty()) {
                "$timeStr $lunarStr $solarTerm"
            } else {
                "$timeStr $lunarStr"
            }
            
            binding.tvCurrentTime.text = fullStr
        } catch (e: Exception) {
            e.printStackTrace()
            // 出错时至少显示系统时间
            try {
                val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault())
                binding.tvCurrentTime.text = sdf.format(java.util.Date())
            } catch (e2: Exception) {
                binding.tvCurrentTime.text = "时间获取失败"
            }
        }
    }
    
    /**
     * 更新天气信息
     */
    private fun updateWeather() {
        // 1. 优先检查用户是否设置了自定义城市
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val customCity = prefs.getString("custom_city", "")
        
        if (!customCity.isNullOrEmpty()) {
            // 使用用户设置的城市
            fetchWeatherByCity(customCity)
            return
        }
        
        // 2. 检查位置权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 如果没有权限，直接使用IP定位
                updateWeatherByIP()
                return
            }
        }
        
        // 3. 尝试使用GPS获取位置
        Thread {
            try {
                val locationManager = getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                var cityName: String? = null
                
                // 尝试获取最后已知位置
                try {
                    val location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    
                    if (location != null) {
                        // 使用 Geocoder 反向地理编码
                        val geocoder = android.location.Geocoder(this, java.util.Locale.CHINESE)
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        
                        if (addresses != null && addresses.isNotEmpty()) {
                            val address = addresses[0]
                            // 优先使用 locality（城市），如果没有则使用 subAdminArea（区县）
                            cityName = address.locality ?: address.subAdminArea ?: address.adminArea
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // 如果GPS获取成功，使用城市名查询天气
                if (!cityName.isNullOrEmpty()) {
                    fetchWeatherByCity(cityName)
                } else {
                    // GPS失败，使用IP定位
                    updateWeatherByIP()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateWeatherByIP()
            }
        }.start()
    }
    
    /**
     * 根据城市名获取天气
     */
    private fun fetchWeatherByCity(city: String) {
        Thread {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val url = "https://wttr.in/$city?format=%l+%C+%t&lang=zh"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val weatherText = response.body?.string()?.trim()
                    if (!weatherText.isNullOrEmpty() && !weatherText.contains("not found", true)) {
                        runOnUiThread {
                            binding.tvWeather.text = weatherText.replace("+", " ")
                        }
                        return@Thread // Return from the thread, not the function
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // 如果失败，回退到IP定位
            updateWeatherByIP()
        }.start()
    }
    
    /**
     * 使用IP定位获取天气
     */
    private fun updateWeatherByIP() {
        Thread {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS) // 缩短超时时间
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    
                // 1. 尝试直接使用 wttr.in 获取位置和天气
                var url = "https://wttr.in/?format=%l+%C+%t&lang=zh"
                var request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .build()
                
                var weatherText: String? = null
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        weatherText = response.body?.string()?.trim()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 检查是否包含中文，如果不包含中文（且不是"未获取"），则尝试强制获取中文位置
                val hasChinese = weatherText?.matches(Regex(".*[\\u4e00-\\u9fa5].*")) == true

                // 2. 如果失败，或者显示的是英文地名（如 Huamu），尝试使用 PCOnline 获取中文地名
                if (weatherText.isNullOrEmpty() || weatherText.contains("not found", true) || weatherText.contains("Unknown", true) || !hasChinese) {
                     try {
                        // PCOnline 接口，返回 GBK 编码的 JSON
                        val pconlineUrl = "http://whois.pconline.com.cn/ipJson.jsp?json=true"
                        val pcRequest = okhttp3.Request.Builder().url(pconlineUrl).build()
                        val pcResponse = client.newCall(pcRequest).execute()
                        
                        if (pcResponse.isSuccessful) {
                            val bytes = pcResponse.body?.bytes()
                            if (bytes != null) {
                                // PCOnline 返回的是 GBK 编码
                                val jsonStr = String(bytes, java.nio.charset.Charset.forName("GBK"))
                                val json = org.json.JSONObject(jsonStr)
                                var city = json.optString("city")
                                val addr = json.optString("addr")
                                
                                // 如果 city 为空，尝试从 addr 解析
                                if (city.isEmpty() && addr.isNotEmpty()) {
                                    city = addr.split(" ").firstOrNull() ?: ""
                                }
                                
                                if (city.isNotEmpty()) {
                                    // 3. 使用获取到的中文城市名再次查询天气
                                    val cityUrl = "https://wttr.in/$city?format=%l+%C+%t&lang=zh"
                                    val cityRequest = request.newBuilder().url(cityUrl).build()
                                    val cityResponse = client.newCall(cityRequest).execute()
                                    
                                    if (cityResponse.isSuccessful) {
                                        val text = cityResponse.body?.string()?.trim()
                                        if (!text.isNullOrEmpty() && !text.contains("not found", true)) {
                                            weatherText = text
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // PCOnline 失败，尝试 ip-api
                        try {
                            val ipApiUrl = "http://ip-api.com/json/?lang=zh-CN"
                            val ipApiRequest = okhttp3.Request.Builder().url(ipApiUrl).build()
                            val ipApiResponse = client.newCall(ipApiRequest).execute()
                            if (ipApiResponse.isSuccessful) {
                                val jsonStr = ipApiResponse.body?.string()
                                if (!jsonStr.isNullOrEmpty()) {
                                    val json = org.json.JSONObject(jsonStr)
                                    val city = json.optString("city")
                                    if (city.isNotEmpty()) {
                                        val cityUrl = "https://wttr.in/$city?format=%l+%C+%t&lang=zh"
                                        val cityRequest = request.newBuilder().url(cityUrl).build()
                                        val cityResponse = client.newCall(cityRequest).execute()
                                        if (cityResponse.isSuccessful) {
                                            val text = cityResponse.body?.string()?.trim()
                                            if (!text.isNullOrEmpty()) weatherText = text
                                        }
                                    }
                                }
                            }
                        } catch (e2: Exception) {
                            e2.printStackTrace()
                        }
                    }
                }
                
                // 4. 如果仍然失败，最后尝试只获取天气（不带位置）
                if (weatherText.isNullOrEmpty() || weatherText.contains("not found", true)) {
                    try {
                        val fallbackUrl = "https://wttr.in/?format=%C+%t&lang=zh"
                        val fallbackRequest = request.newBuilder().url(fallbackUrl).build()
                        val response = client.newCall(fallbackRequest).execute()
                        if (response.isSuccessful) {
                             val text = response.body?.string()?.trim()
                             if (!text.isNullOrEmpty()) {
                                 weatherText = text
                             }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                val finalWeatherText = weatherText ?: "天气获取失败"
                runOnUiThread {
                    try {
                        binding.tvWeather.text = finalWeatherText.replace("+", " ")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    binding.tvWeather.text = "天气获取失败"
                }
            }
        }.start()
    }
    
    /**
     * 切换底部按钮显示状态
     */
    private fun toggleBottomControls() {
        if (binding.bottomControlsContainer.visibility == View.VISIBLE) {
            hideBottomControls()
        } else {
            showBottomControls()
        }
    }
    
    /**
     * 显示分辨率
     */
    private fun showResolution(text: String) {
        binding.tvResolution.text = text
        binding.tvResolution.visibility = View.VISIBLE
        resolutionHandler.removeCallbacks(resolutionRunnable)
        resolutionHandler.postDelayed(resolutionRunnable, 3000L)
    }
    
    /**
     * 显示台号
     */
    private fun showChannelNumber(number: Int) {
        binding.tvChannelNumber.text = number.toString()
        binding.tvChannelNumber.visibility = View.VISIBLE
        channelNumberHandler.removeCallbacks(channelNumberRunnable)
        channelNumberHandler.postDelayed(channelNumberRunnable, 2000L)
    }
    
    /**
     * 重置自动隐藏计时器
     */
    private fun resetAutoHideTimer() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY)
    }
    
    
    // 移除 dispatchTouchEvent，避免每次触摸都强制显示控制栏
    // override fun dispatchTouchEvent(ev: MotionEvent?): Boolean { ... }
    
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        // 用户按键时显示按钮
        if (event?.action == KeyEvent.ACTION_DOWN) {
            showBottomControls()
        }
        return super.dispatchKeyEvent(event)
    }
    
    /**
     * 按键监听
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                // 菜单键：显示/隐藏频道列表
                if (isChannelSidebarVisible) {
                    hideChannelSidebar()
                } else {
                    showChannelSidebar()
                }
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                // 返回键：显示/隐藏线路选择
                if (isRouteSidebarVisible) {
                    hideRouteSidebar()
                    true
                } else if (isChannelSidebarVisible) {
                    hideChannelSidebar()
                    true
                } else {
                    // 如果有多个线路，显示线路选择
                    val currentChannel = getCurrentChannel()
                    if (currentChannel != null && currentChannel.getRouteCount() > 1) {
                        showRouteSidebar()
                        true
                    } else {
                        super.onKeyDown(keyCode, event)
                    }
                }
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                // 数字键：换台
                val digit = keyCode - KeyEvent.KEYCODE_0
                handleDigitInput(digit)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    
    /**
     * 处理数字键输入
     */
    private fun handleDigitInput(digit: Int) {
        digitBuffer.append(digit)
        
        // 移除之前的定时器
        digitHandler.removeCallbacks(digitRunnable)
        
        // 显示当前输入
        Toast.makeText(this, getString(R.string.channel_number_hint, digitBuffer.toString().toInt()), Toast.LENGTH_SHORT).show()
        
        // 1秒后处理输入
        digitHandler.postDelayed(digitRunnable, 1000)
    }
    
    /**
     * 处理数字输入
     */
    private fun processDigitInput() {
        if (digitBuffer.isEmpty()) return
        
        val channelNumber = digitBuffer.toString().toIntOrNull() ?: return
        digitBuffer.clear()
        
        // 频道号从1开始，索引从0开始
        val targetIndex = channelNumber - 1
        
        if (targetIndex in allChannels?.indices ?: IntRange.EMPTY) {
            Toast.makeText(this, getString(R.string.switching_channel, channelNumber), Toast.LENGTH_SHORT).show()
            switchToChannel(targetIndex)
        } else {
            Toast.makeText(this, "频道号无效", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示频道列表侧边栏
     */
    private fun showChannelSidebar() {
        binding.sidebarChannelRoot.root.visibility = View.VISIBLE
        isChannelSidebarVisible = true
        // 侧边栏显示时隐藏底部按钮和顶部状态栏
        binding.bottomControlsContainer.visibility = View.GONE
        binding.topStatusContainer.visibility = View.GONE
        autoHideHandler.removeCallbacks(autoHideRunnable)
        hideRouteSidebar()
    }
    
    /**
     * 隐藏频道列表侧边栏
     */
    private fun hideChannelSidebar() {
        binding.sidebarChannelRoot.root.visibility = View.GONE
        isChannelSidebarVisible = false
        // 只有当另一个侧边栏也不显示时，才恢复显示按钮
        if (!isRouteSidebarVisible) {
            showBottomControls()
        }
    }
    
    /**
     * 显示线路选择侧边栏
     */
    private fun showRouteSidebar() {
        updateRouteSidebar()
        binding.sidebarRouteRoot.root.visibility = View.VISIBLE
        isRouteSidebarVisible = true
        // 侧边栏显示时隐藏底部按钮和顶部状态栏
        binding.bottomControlsContainer.visibility = View.GONE
        binding.topStatusContainer.visibility = View.GONE
        autoHideHandler.removeCallbacks(autoHideRunnable)
        hideChannelSidebar()
    }
    
    /**
     * 隐藏线路选择侧边栏
     */
    private fun hideRouteSidebar() {
        binding.sidebarRouteRoot.root.visibility = View.GONE
        isRouteSidebarVisible = false
        // 只有当另一个侧边栏也不显示时，才恢复显示按钮
        if (!isChannelSidebarVisible) {
            showBottomControls()
        }
    }
    
    /**
     * 切换到指定频道
     */
    private fun switchToChannel(index: Int) {
        if (index !in allChannels?.indices ?: IntRange.EMPTY) return
        
        currentChannelIndex = index
        hasTriedAllRoutes = false
        
        // 保存当前频道ID和线路索引
        val currentChannel = getCurrentChannel()
        if (currentChannel != null) {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit()
                .putString("last_channel_id", currentChannel.id)
                .putInt("last_route_${currentChannel.id}", currentChannel.currentRouteIndex)
                .apply()
        }
        
        // 更新适配器
        channelSidebarAdapter?.updateCurrentChannel(getCurrentChannel())
        
        // 显示大大的黄色台号
        if (currentChannel != null && currentChannel.number > 0) {
            showChannelNumber(currentChannel.number)
        }
        
        // 显示加载背景
        loadLoadingBackground()
        
        // 重新初始化播放器
        releasePlayer()
        initializePlayer()
    }
    
    /**
     * 切换到指定线路
     */
    private fun switchToRoute(routeIndex: Int) {
        val currentChannel = getCurrentChannel() ?: return
        
        if (currentChannel.switchToRoute(routeIndex)) {
            hasTriedAllRoutes = false
            
            // 保存线路选择
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit()
                .putInt("last_route_${currentChannel.id}", routeIndex)
                .apply()
            
            // 更新适配器
            routeSidebarAdapter?.updateCurrentRoute(routeIndex)
            
            // 显示加载背景
            loadLoadingBackground()
            
            // 重新初始化播放器
            releasePlayer()
            initializePlayer()
        }
    }
    
    /**
     * 获取当前频道
     */
    private fun getCurrentChannel(): Channel? {
        return allChannels?.getOrNull(currentChannelIndex)
    }
    
    /**
     * 初始化 ExoPlayer
     */
    private fun initializePlayer() {
        try {
            val currentChannel = getCurrentChannel() ?: run {
                Toast.makeText(this, "无法获取频道信息", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // 直接使用 Channel 的 getCurrentUrl（已在 Channel 中处理 $ 与非法字符）
            val currentUrl = currentChannel.getCurrentUrl()

            if (currentUrl.isEmpty()) {
                Toast.makeText(this, R.string.error_playing_video, Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // 读取解码器设置
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            currentDecoderType = prefs.getInt("decoder", 0)

            // 根据解码器类型初始化播放器（保持原有选择逻辑）
            when (currentDecoderType) {
                0 -> initializeExoPlayer(currentUrl)
                1, 2 -> initializeIJKPlayer(currentUrl, currentDecoderType == 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "播放器初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    /**
     * 验证 URL 是否有效
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            // 基本的 URL 格式检查
            url.isNotEmpty() && (
                url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("rtmp://", ignoreCase = true) ||
                url.startsWith("rtmps://", ignoreCase = true) ||
                url.startsWith("rtsp://", ignoreCase = true)
            )
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 初始化 ExoPlayer
     */
    private fun initializeExoPlayer(currentUrl: String) {
        try {
            val currentChannel = getCurrentChannel()
            val userAgent = currentChannel?.ua ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            
            // 配置 HttpDataSource 工厂
            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)
            
            // 设置自定义请求头
            // 设置自定义请求头
            currentChannel?.headers?.forEach { (key, value) ->
                httpDataSourceFactory.setDefaultRequestProperties(mapOf(key to value))
            }
            
            // 创建数据源工厂,添加错误处理防止 RTMP 闪退
            val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
            
            val dataSourceFactory = try {
                // 尝试创建 RTMP 数据源工厂
                val rtmpDataSourceFactory = androidx.media3.datasource.rtmp.RtmpDataSource.Factory()
                
                // 创建支持多协议的数据源工厂
                androidx.media3.datasource.DataSource.Factory {
                    if (currentUrl.startsWith("rtmp://", ignoreCase = true) || 
                        currentUrl.startsWith("rtmps://", ignoreCase = true)) {
                        try {
                            rtmpDataSourceFactory.createDataSource()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            defaultDataSourceFactory.createDataSource()
                        }
                    } else {
                        defaultDataSourceFactory.createDataSource()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果 RTMP 支持不可用,回退到默认数据源
                defaultDataSourceFactory
            }
            
            // 配置 LoadControl 以优化缓冲
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000,  // minBufferMs - 最小缓冲
                    50000,  // maxBufferMs - 最大缓冲
                    2500,   // bufferForPlaybackMs - 开始播放所需缓冲
                    5000    // bufferForPlaybackAfterRebufferMs - 重新缓冲后播放所需缓冲
                )
                .build()

            // 配置音频轨道选择器 - 解决组播源有图没声音的问题
            val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this)

            // 配置音频参数以确保组播源音频兼容性（使用1.2.1兼容API）
            val parametersBuilder = trackSelector.parameters.buildUpon()
                .setPreferredAudioLanguage("zh") // 优先中文音频
                .setMaxAudioChannelCount(8) // 支持多声道音频（AC3、DTS等）
                .setPreferredAudioMimeType("audio/mp4a-latm") // 优先选择AAC音频格式

            trackSelector.setParameters(parametersBuilder)

            // 创建 ExoPlayer 实例，支持所有主流协议
            // ExoPlayer 自动检测并支持: HLS, DASH, SmoothStreaming, RTSP, RTMP, HTTP Progressive
            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector) // 添加音频轨道选择器
                .setMediaSourceFactory(
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                )
                .setLoadControl(loadControl)
                .build().also { exoPlayer ->
                
                // 只清理 IJKPlayer 添加的 SurfaceView(如果有的话)
                // 不要清理 PlayerView 的内部视图
                if (ijkMediaPlayer != null) {
                    // 从 IJKPlayer 切换过来,需要清理 SurfaceView
                    binding.playerView.removeAllViews()
                }
                
                // 绑定 ExoPlayer
                binding.playerView.player = exoPlayer
                
                // 设置视频缩放模式
                val resizeMode = when (aspectRatioMode) {
                    0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL // 全屏
                    1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM // 剪裁
                    2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT // 16:9
                    3 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH // 原始
                    4 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT // 4:3
                    5 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL // 填充
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                binding.playerView.resizeMode = resizeMode
                
                // 设置媒体项
                try {
                    val mediaItemBuilder = MediaItem.Builder().setUri(currentUrl)
                    
                    // 针对 PHP/ASP/JSP 等动态脚本链接，通常是 HLS 直播流，强制指定为 M3U8
                    // 解决如 1905.php 等链接无法识别的问题
                    if (currentUrl.contains(".php", true) || 
                        currentUrl.contains(".asp", true) || 
                        currentUrl.contains(".jsp", true)) {
                        mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                    }
                    
                    exoPlayer.setMediaItem(mediaItemBuilder.build())
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "无效的视频 URL: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                
                // 添加播放器监听器
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        handlePlaybackError(error)
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                // 播放器准备就绪，重置重试标志
                                hasTriedAllRoutes = false
                                // 隐藏加载背景
                                binding.ivLoadingBackground.visibility = View.GONE
                            }
                            Player.STATE_ENDED -> {
                                // 播放结束
                                finish()
                            }
                        }
                    }
                    
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        super.onVideoSizeChanged(videoSize)
                        val resolution = "${videoSize.width}x${videoSize.height}"
                        binding.tvVideoResolution.text = resolution
                    }
                })
            }
                
            // 准备播放器并自动播放
            player?.prepare()
            player?.playWhenReady = true
            

            
            // 显示当前线路信息
            if (currentChannel != null && currentChannel.getRouteCount() > 1) {
                showCurrentRouteInfo()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "播放器初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    /**
     * 初始化 IJKPlayer
     */


    
    /**
     * 显示当前线路信息
     */
    private fun showCurrentRouteInfo() {
        val currentChannel = getCurrentChannel() ?: return
        val routeName = currentChannel.getRouteName(currentChannel.currentRouteIndex)
        val message = getString(R.string.current_route, routeName)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 处理播放错误
     */
    private fun handlePlaybackError(error: PlaybackException) {
        val currentChannel = getCurrentChannel() ?: return
        
        // 如果有多个线路且还没尝试完所有线路
        if (currentChannel.getRouteCount() > 1 && !hasTriedAllRoutes) {
            if (currentChannel.switchToNextRoute()) {
                // 尝试下一个线路
                Toast.makeText(
                    this,
                    getString(R.string.auto_switching_route, currentChannel.currentRouteIndex + 1),
                    Toast.LENGTH_SHORT
                ).show()
                
                releasePlayer()
                initializePlayer()
            } else {
                // 已尝试所有线路，切换到下一个频道
                hasTriedAllRoutes = true
                switchToNextChannel()
            }
        } else {
            // 只有一个线路或已尝试所有线路，切换到下一个频道
            switchToNextChannel()
        }
    }

    /**
     * 切换到下一个频道
     */
    private fun switchToNextChannel() {
        val totalChannels = allChannels?.size ?: 0
        if (totalChannels > 0) {
            val nextIndex = (currentChannelIndex + 1) % totalChannels
            Toast.makeText(this, "当前频道播放失败，自动切换下一台", Toast.LENGTH_SHORT).show()
            switchToChannel(nextIndex)
        } else {
            Toast.makeText(this, "无可用频道", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    
    override fun onPause() {
        super.onPause()
        // 暂停播放，但不释放播放器
        player?.pause()
    }
    
    override fun onResume() {
        super.onResume()
        // 恢复播放
        player?.play()
        // 更新天气信息
        updateWeather()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        digitHandler.removeCallbacks(digitRunnable)
        channelNumberHandler.removeCallbacks(channelNumberRunnable)
        resolutionHandler.removeCallbacks(resolutionRunnable)
        autoHideHandler.removeCallbacks(autoHideRunnable)
        timeHandler.removeCallbacks(timeRunnable)
    }
    
    /**
     * 释放播放器资源
     */
    private fun releasePlayer() {
        // 释放 ExoPlayer
        player?.let { exoPlayer ->
            exoPlayer.stop()
            exoPlayer.release()
        }
        player = null
        
        // 释放 IJKPlayer
        if (ijkMediaPlayer is tv.danmaku.ijk.media.player.IMediaPlayer) {
            try {
                val ijk = ijkMediaPlayer as tv.danmaku.ijk.media.player.IMediaPlayer
                ijk.stop()
                ijk.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        ijkMediaPlayer = null
        
        // 清理 playerView
        try {
            binding.playerView.player = null
            binding.playerView.removeAllViews()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 显示解码器选择对话框
     */

    
    /**
     * 显示菜单对话框
     */
    private fun showMenuDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_menu, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 获取当前版本
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "未知"
        }
        
        // 检查更新并显示版本信息
        val updateIndicator = dialogView.findViewById<View>(R.id.update_indicator)
        val versionInfo = dialogView.findViewById<TextView>(R.id.tv_version_info)
        
        // 后台检查更新
        Thread {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url("https://yezheng.dpdns.org/tv/update/version.json")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (jsonStr != null) {
                        val json = org.json.JSONObject(jsonStr)
                        val latestVersion = json.optString("versionName", "")
                        val latestVersionCode = json.optInt("versionCode", 0)
                        val currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode
                        
                        runOnUiThread {
                            if (latestVersionCode > currentVersionCode) {
                                // 有新版本，显示小红点和版本信息
                                updateIndicator.visibility = View.VISIBLE
                                versionInfo.visibility = View.VISIBLE
                                versionInfo.text = "$currentVersion → $latestVersion"
                                versionInfo.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                            } else {
                                // 无更新，只显示当前版本
                                versionInfo.visibility = View.VISIBLE
                                versionInfo.text = "v$currentVersion"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    versionInfo.visibility = View.VISIBLE
                    versionInfo.text = "v$currentVersion"
                }
            }
        }.start()
        
        // 更新登录状态显示
        val loginStatus = dialogView.findViewById<TextView>(R.id.tv_login_status)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val username = prefs.getString("username", "")
        
        if (isLoggedIn && !username.isNullOrEmpty()) {
            loginStatus.text = username
            loginStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            loginStatus.text = "未登录"
            loginStatus.setTextColor(android.graphics.Color.parseColor("#999999"))
        }
        
        // 更新激活状态显示
        val activationStatus = dialogView.findViewById<TextView>(R.id.tv_activation_status)
        lifecycleScope.launch {
            try {
                val info = com.leafstudio.tvplayer.utils.ActivationManager.checkActivationStatus(this@PlaybackActivity)
                runOnUiThread {
                    if (info.isValid) {
                        // 已激活 - 绿色
                        val remainingDays = (info.remainingSeconds / 86400).toInt()
                        activationStatus.text = if (remainingDays > 0) {
                            "已激活 (剩余${remainingDays}天)"
                        } else {
                            val remainingHours = (info.remainingSeconds / 3600).toInt()
                            "已激活 (剩余${remainingHours}小时)"
                        }
                        activationStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    } else {
                        // 未激活 - 红色
                        activationStatus.text = "未激活"
                        activationStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackActivity", "查询激活状态失败", e)
                runOnUiThread {
                    activationStatus.text = "查询失败"
                    activationStatus.setTextColor(android.graphics.Color.parseColor("#999999"))
                }
            }
        }
        
        // 用户登录按钮
        dialogView.findViewById<View>(R.id.btn_user_login).setOnClickListener {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            if (isLoggedIn) {
                // 已登录，显示用户信息和退出选项
                showUserInfoDialog()
            } else {
                // 未登录，显示登录对话框
                showLoginDialog()
            }
        }
        
        // 软件更新按钮（现在是 RelativeLayout）
        dialogView.findViewById<View>(R.id.btn_check_update).setOnClickListener {
            UpdateManager.checkUpdate(this, true)
            dialog.dismiss()
        }
        
        // 激活按钮
        dialogView.findViewById<View>(R.id.btn_activation).setOnClickListener {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            showActivationDialog(false)
        }

        // 设置城市按钮
        dialogView.findViewById<android.widget.Button>(R.id.btn_set_city).setOnClickListener {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            showCitySettingDialog()
        }

        // 清空所有设置按钮
        dialogView.findViewById<android.widget.Button>(R.id.btn_clear_settings).setOnClickListener {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            showClearSettingsDialog()
        }

        // 关于按钮
        dialogView.findViewById<android.widget.Button>(R.id.btn_about).setOnClickListener {
            // 移除监听器以避免闪烁，或者在 showAboutDialog 中再次隐藏
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            showAboutDialog()
        }
        
        dialog.setOnDismissListener {
            showBottomControls()
        }
        
        hideBottomControls()
        dialog.show()
    }

    /**
     * 显示城市设置对话框
     */
    private fun showCitySettingDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val currentCity = prefs.getString("custom_city", "") ?: ""
        
        val input = android.widget.EditText(this)
        input.setText(currentCity)
        input.hint = "请输入城市名称（如：上海）"
        input.setTextColor(android.graphics.Color.WHITE)
        input.setHintTextColor(android.graphics.Color.GRAY)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("设置城市")
            .setMessage("设置后将使用指定城市的天气信息")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val city = input.text.toString().trim()
                if (city.isNotEmpty()) {
                    prefs.edit().putString("custom_city", city).apply()
                    Toast.makeText(this, "已设置城市为：$city", Toast.LENGTH_SHORT).show()
                    // 立即更新天气
                    updateWeather()
                } else {
                    prefs.edit().remove("custom_city").apply()
                    Toast.makeText(this, "已清除自定义城市，将使用自动定位", Toast.LENGTH_SHORT).show()
                    updateWeather()
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        dialog.setOnDismissListener {
            showBottomControls()
        }
        
        hideBottomControls()
        dialog.show()
    }

    /**
     * 显示关于对话框
     */
    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 设置版本号
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            dialogView.findViewById<TextView>(R.id.tv_app_version).text = "Version $version"
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 关闭按钮
        dialogView.findViewById<android.widget.Button>(R.id.btn_close_about).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.setOnDismissListener {
            showBottomControls()
        }
        
        hideBottomControls()
        dialog.show()
    }
    
    /**
     * 显示清空设置确认对话框
     */
    private fun showClearSettingsDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("清空所有设置")
            .setMessage("确定要清空所有设置吗？\n\n这将清除：\n• 上次播放的频道\n• 所有频道的线路选择\n• 解码器设置\n• 自定义城市设置\n\n此操作无法撤销！")
            .setPositiveButton("确定清空") { _, _ ->
                clearAllSettings()
            }
            .setNegativeButton("取消", null)
            .create()
        
        dialog.setOnDismissListener {
            showBottomControls()
        }
        
        hideBottomControls()
        dialog.show()
    }
    
    /**
     * 清空所有设置
     */
    private fun clearAllSettings() {
        try {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            Toast.makeText(this, "所有设置已清空\n将使用默认设置", Toast.LENGTH_LONG).show()
            
            // 可选：重启应用以应用默认设置
            // 或者提示用户重启应用
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "清空设置失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    
    /**
 * 切换画面比例
 */
private fun toggleAspectRatio() {
    // 循环六种模式
    aspectRatioMode = (aspectRatioMode + 1) % 6

    // 根据模式选择对应的 resizeMode
    val resizeMode = when (aspectRatioMode) {
        0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL // 全屏
        1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM // 剪裁
        2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT // 16:9（适应）
        3 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH // 原始（固定宽）
        4 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT // 4:3（固定高）
        5 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL // 填充（同全屏）
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    binding.playerView.resizeMode = resizeMode

    // 按顺序映射文字标签
    val modeName = when (aspectRatioMode) {
        0 -> "全屏"
        1 -> "剪裁"
        2 -> "16:9"
        3 -> "原始"
        4 -> "4:3"
        5 -> "填充"
        else -> "全屏"
    }

    // 更新按钮文字显示当前模式
    binding.btnAspectRatio.text = modeName

    // 隐藏底部控制栏
    hideBottomControls()
}

    /**
     * 加载加载背景图
     */
    private fun loadLoadingBackground() {
        try {
            binding.ivLoadingBackground.visibility = View.VISIBLE
            Glide.with(this)
                .load("http://api.btstu.cn/sjbz/")
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(binding.ivLoadingBackground)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 加载跑马灯提示
     */
    private fun loadMarqueeText() {
        Thread {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url("https://yezheng.dpdns.org/tv/hi.txt")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val text = response.body?.string()?.trim()
                    if (!text.isNullOrEmpty()) {
                        runOnUiThread {
                            binding.tvMarquee.text = text
                            binding.tvMarquee.isSelected = true // 启动跑马灯
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
    
    /**
     * 显示登录对话框
     */
    private fun showLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_login, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val qrcodeImageView = dialogView.findViewById<android.widget.ImageView>(R.id.iv_qrcode)
        val loadingProgress = dialogView.findViewById<android.widget.ProgressBar>(R.id.pb_loading)
        val hintTextView = dialogView.findViewById<TextView>(R.id.tv_login_hint)
        val refreshButton = dialogView.findViewById<android.widget.Button>(R.id.btn_refresh_qrcode)
        val closeButton = dialogView.findViewById<android.widget.Button>(R.id.btn_close_login)
        
        var loginTicket = ""
        var checkLoginHandler: Handler? = null
        var checkLoginRunnable: Runnable? = null
        
        // 生成二维码
        fun generateQRCode() {
            loadingProgress.visibility = View.VISIBLE
            qrcodeImageView.visibility = View.GONE
            refreshButton.visibility = View.GONE
            hintTextView.text = "正在生成二维码..."
            
            Thread {
                try {
                    // 生成唯一的登录票据
                    loginTicket = "ticket_${System.currentTimeMillis()}_${(1000..9999).random()}"
                    
                    // 生成二维码内容 - 这里可以是你的微信登录链接
                    // 实际使用时,这应该是一个微信登录的URL
                    val qrcodeContent = "https://yezheng.dpdns.org/tv/login?ticket=$loginTicket"
                    
                    // 生成二维码图片
                    val qrcodeBitmap = generateQRCodeBitmap(qrcodeContent, 500, 500)
                    
                    runOnUiThread {
                        loadingProgress.visibility = View.GONE
                        qrcodeImageView.visibility = View.VISIBLE
                        qrcodeImageView.setImageBitmap(qrcodeBitmap)
                        hintTextView.text = "请使用微信扫描二维码登录"
                        
                        // 开始轮询检查登录状态
                        startCheckLoginStatus(loginTicket, dialog)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        loadingProgress.visibility = View.GONE
                        refreshButton.visibility = View.VISIBLE
                        hintTextView.text = "二维码生成失败: ${e.message}"
                        Toast.makeText(this@PlaybackActivity, "二维码生成失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
        
        // 刷新二维码
        refreshButton.setOnClickListener {
            generateQRCode()
        }
        
        // 关闭按钮
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.setOnDismissListener {
            checkLoginHandler?.removeCallbacks(checkLoginRunnable!!)
            showBottomControls()
        }
        
        hideBottomControls()
        dialog.show()
        
        // 初始生成二维码
        generateQRCode()
    }
    
    /**
     * 生成二维码图片
     */
    private fun generateQRCodeBitmap(content: String, width: Int, height: Int): android.graphics.Bitmap {
        val hints = hashMapOf<com.google.zxing.EncodeHintType, Any>()
        hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[com.google.zxing.EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H
        hints[com.google.zxing.EncodeHintType.MARGIN] = 1
        
        val bitMatrix = com.google.zxing.MultiFormatWriter().encode(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            width,
            height,
            hints
        )
        
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitMatrix.get(x, y)) {
                    pixels[y * width + x] = android.graphics.Color.BLACK
                } else {
                    pixels[y * width + x] = android.graphics.Color.WHITE
                }
            }
        }
        
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    /**
     * 开始检查登录状态
     */
    private fun startCheckLoginStatus(ticket: String, dialog: androidx.appcompat.app.AlertDialog) {
        val handler = Handler(Looper.getMainLooper())
        var checkCount = 0
        
        val runnable = object : Runnable {
            override fun run() {
                checkCount++
                
                // 演示模式: 10秒后自动登录成功 (5次检查 * 2秒 = 10秒)
                if (checkCount >= 5) {
                    runOnUiThread {
                        // 模拟登录成功
                        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                        val username = "演示用户"
                        val userId = "demo_${System.currentTimeMillis()}"
                        
                        prefs.edit().apply {
                            putBoolean("is_logged_in", true)
                            putString("username", username)
                            putString("user_id", userId)
                            putString("token", ticket)
                            apply()
                        }
                        
                        Toast.makeText(this@PlaybackActivity, "登录成功！欢迎 $username", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    return
                }
                
                // 实际使用时的API检查逻辑(当前注释掉,使用演示模式)
                /*
                Thread {
                    try {
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        
                        val request = okhttp3.Request.Builder()
                            .url("https://yezheng.dpdns.org/tv/api/login/check?ticket=$ticket")
                            .build()
                        
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val jsonStr = response.body?.string()
                            if (jsonStr != null) {
                                val json = org.json.JSONObject(jsonStr)
                                val status = json.optString("status", "")
                                
                                when (status) {
                                    "success" -> {
                                        val username = json.optString("username", "")
                                        val userId = json.optString("user_id", "")
                                        val token = json.optString("token", "")
                                        
                                        runOnUiThread {
                                            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                                            prefs.edit().apply {
                                                putBoolean("is_logged_in", true)
                                                putString("username", username)
                                                putString("user_id", userId)
                                                putString("token", token)
                                                apply()
                                            }
                                            
                                            Toast.makeText(this@PlaybackActivity, "登录成功！欢迎 $username", Toast.LENGTH_LONG).show()
                                            dialog.dismiss()
                                        }
                                        return@Thread
                                    }
                                    "waiting" -> {
                                        handler.postDelayed(this, 2000)
                                    }
                                    "expired" -> {
                                        runOnUiThread {
                                            Toast.makeText(this@PlaybackActivity, "二维码已过期，请刷新", Toast.LENGTH_SHORT).show()
                                            dialog.findViewById<android.widget.Button>(R.id.btn_refresh_qrcode)?.visibility = View.VISIBLE
                                            dialog.findViewById<TextView>(R.id.tv_login_hint)?.text = "二维码已过期，请点击刷新"
                                        }
                                        return@Thread
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
                */
                
                // 继续检查
                handler.postDelayed(this, 2000)
            }
        }
        
        handler.postDelayed(runnable, 2000)
    }
    
    /**
     * 显示用户信息对话框
     */
    private fun showUserInfoDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val userId = prefs.getString("user_id", "") ?: ""
        
        val message = "用户名: $username\nID: $userId"
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("用户信息")
            .setMessage(message)
            .setPositiveButton("退出登录") { _, _ ->
                // 清除登录信息
                prefs.edit().apply {
                    putBoolean("is_logged_in", false)
                    remove("username")
                    remove("user_id")
                    remove("token")
                    apply()
                }
                Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .create()
        
        dialog.setOnDismissListener {
            showBottomControls()
        }
        
        hideBottomControls()
        dialog.show()
    }
    
    /**
     * 显示激活对话框
     * @param forceActivation 是否强制激活(未激活时不可关闭)
     */
    /**
     * 刷新激活界面 UI
     */
    private fun refreshActivationUI(
        dialog: androidx.appcompat.app.AlertDialog,
        dialogView: View,
        forceActivation: Boolean,
        handler: Handler,
        updateRunnableContainer: Array<Runnable?>
    ) {
        val btnRefresh = dialogView.findViewById<android.widget.Button>(R.id.btn_refresh_status)
        val layoutStatus = dialogView.findViewById<View>(R.id.layout_activation_status)
        val layoutNotActivated = dialogView.findViewById<View>(R.id.layout_not_activated)
        val tvExpiry = dialogView.findViewById<TextView>(R.id.tv_expiry_date)
        val tvRemaining = dialogView.findViewById<TextView>(R.id.tv_remaining_time)
        val tvTrial = dialogView.findViewById<TextView>(R.id.tv_trial_info)
        
        btnRefresh.isEnabled = false
        btnRefresh.text = "刷新中..."
        
        lifecycleScope.launch {
            val ctx = dialogView.context
            val info = com.leafstudio.tvplayer.utils.ActivationManager.checkActivationStatus(ctx)
            
            updateRunnableContainer[0]?.let { handler.removeCallbacks(it) }
            
            if (info.isValid) {
                layoutStatus.visibility = View.VISIBLE
                layoutNotActivated.visibility = View.GONE

                tvExpiry.text = "过期时间: ${info.expiryTime}"

                val timerRunnable = object : Runnable {
                    override fun run() {
                        val currentRemainingSeconds = info.remainingSeconds - (System.currentTimeMillis() - System.currentTimeMillis()) / 1000
                        if (currentRemainingSeconds > 0) {
                            val days = currentRemainingSeconds / 86400
                            val hours = (currentRemainingSeconds % 86400) / 3600
                            val minutes = (currentRemainingSeconds % 3600) / 60
                            val seconds = currentRemainingSeconds % 60
                            tvRemaining.text = String.format("剩余: %d天 %02d:%02d:%02d", days, hours, minutes, seconds)
                            handler.postDelayed(this, 1000)
                        } else {
                            tvRemaining.text = "已过期"
                            tvRemaining.setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
                        }
                    }
                }
                updateRunnableContainer[0] = timerRunnable
                handler.post(timerRunnable)
                
                if (forceActivation) {
                    Toast.makeText(ctx, "激活成功！", Toast.LENGTH_LONG).show()
                    handler.postDelayed({
                        dialog.dismiss()
                        (ctx as? android.app.Activity)?.recreate()
                    }, 1500)
                }
            } else {
                layoutStatus.visibility = View.GONE
                layoutNotActivated.visibility = View.VISIBLE
                tvTrial.text = info.message
            }
            
            btnRefresh.isEnabled = true
            btnRefresh.text = "刷新激活状态"
        }
    }

    /**
     * 显示激活对话框
     * @param forceActivation 是否强制激活(未激活时不可关闭)
     */
    private fun showActivationDialog(forceActivation: Boolean, message: String? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_activation, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(!forceActivation)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val machineCodeTextView = dialogView.findViewById<TextView>(R.id.tv_machine_code)
        val copyButton = dialogView.findViewById<android.widget.Button>(R.id.btn_copy_machine_code)
        val cancelButton = dialogView.findViewById<android.widget.Button>(R.id.btn_cancel_activation)
        val refreshButton = dialogView.findViewById<android.widget.Button>(R.id.btn_refresh_status)

        // 获取并显示机器码
        val machineCode = com.leafstudio.tvplayer.utils.ActivationManager.getMachineCode(this)
        machineCodeTextView.text = machineCode
        
        val handler = Handler(Looper.getMainLooper())
        val updateRunnableContainer = arrayOf<Runnable?>(null)
        
        // 初始刷新
        refreshActivationUI(dialog, dialogView, forceActivation, handler, updateRunnableContainer)
        
        // 自动刷新
        val autoRefreshRunnable = object : Runnable {
            override fun run() {
                if (dialog.isShowing) {
                    refreshActivationUI(dialog, dialogView, forceActivation, handler, updateRunnableContainer)
                    handler.postDelayed(this, 10000)
                }
            }
        }
        handler.postDelayed(autoRefreshRunnable, 10000)
        
        // 绑定事件
        copyButton.setOnClickListener {
            val clipboard = dialog.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("机器码", machineCode)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(dialog.context, "机器码已复制", Toast.LENGTH_SHORT).show()
        }
        
        refreshButton.setOnClickListener {
            refreshActivationUI(dialog, dialogView, forceActivation, handler, updateRunnableContainer)
        }
        
        if (forceActivation) {
            cancelButton.visibility = View.GONE
        } else {
            cancelButton.setOnClickListener {
                dialog.dismiss()
            }
        }
        
        dialog.setOnDismissListener {
            updateRunnableContainer[0]?.let { handler.removeCallbacks(it) }
            handler.removeCallbacks(autoRefreshRunnable)
            if (!forceActivation) {
                showBottomControls()
            }
        }
        
        if (!forceActivation) {
            hideBottomControls()
        }
        
        dialog.show()
        
        if (forceActivation) {
            dialog.setOnKeyListener { _: android.content.DialogInterface, keyCode: Int, _: android.view.KeyEvent ->
                keyCode == android.view.KeyEvent.KEYCODE_BACK
            }
        }
    }
}
