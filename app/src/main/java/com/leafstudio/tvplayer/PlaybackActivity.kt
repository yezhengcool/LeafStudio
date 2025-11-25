package com.leafstudio.tvplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
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

// 新增标记，用于 RTMP 自动切换防止递归
private var hasAutoSwitchedForRtmp = false

/**
 * 播放 Activity - 支持侧边栏频道列表、线路选择和数字键换台
 */
class PlaybackActivity : FragmentActivity() {
    
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
            showDecoderDialog()
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
            Toast.makeText(this, "多媒体功能开发中...", Toast.LENGTH_SHORT).show()
            resetAutoHideTimer()
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
        
        // 保存当前频道ID
        val currentChannel = getCurrentChannel()
        if (currentChannel != null) {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit().putString("last_channel_id", currentChannel.id).apply()
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
            
            // 创建 ExoPlayer 实例，支持所有主流协议
            // ExoPlayer 自动检测并支持: HLS, DASH, SmoothStreaming, RTSP, RTMP, HTTP Progressive
            player = ExoPlayer.Builder(this)
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
    private fun initializeIJKPlayer(currentUrl: String, useHardwareDecoder: Boolean) {
        try {
            // 检查 IJKPlayer 是否可用
            val ijkClass = Class.forName("tv.danmaku.ijk.media.player.IjkMediaPlayer")

            // 使用反射加载库
            try {
                val loadLibrariesMethod = ijkClass.getMethod("loadLibrariesOnce", Class.forName("tv.danmaku.ijk.media.player.IjkLibLoader"))
                loadLibrariesMethod.invoke(null, null)
            } catch (e: Exception) {
                // 库可能已经加载,忽略错误
                e.printStackTrace()
            }

            try {
                val profileBeginMethod = ijkClass.getMethod("native_profileBegin", String::class.java)
                profileBeginMethod.invoke(null, "libijkplayer.so")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 创建实例
            val player = ijkClass.newInstance()
            ijkMediaPlayer = player

            // 使用反射设置选项
            val setOptionMethod = ijkClass.getMethod("setOption", Int::class.javaPrimitiveType, String::class.java, Long::class.javaPrimitiveType)
            val OPT_CATEGORY_PLAYER = ijkClass.getField("OPT_CATEGORY_PLAYER").getInt(null)
            val OPT_CATEGORY_FORMAT = ijkClass.getField("OPT_CATEGORY_FORMAT").getInt(null)
            val OPT_CATEGORY_CODEC = ijkClass.getField("OPT_CATEGORY_CODEC").getInt(null)

            // 硬解/软解设置
            if (useHardwareDecoder) {
                setOptionMethod.invoke(player, OPT_CATEGORY_PLAYER, "mediacodec", 1L)
                setOptionMethod.invoke(player, OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
                setOptionMethod.invoke(player, OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
            } else {
                setOptionMethod.invoke(player, OPT_CATEGORY_PLAYER, "mediacodec", 0L)
            }

            // 基本播放设置
            setOptionMethod.invoke(player, OPT_CATEGORY_PLAYER, "opensles", 0L)
            setOptionMethod.invoke(player, OPT_CATEGORY_PLAYER, "framedrop", 1L)
            setOptionMethod.invoke(player, OPT_CATEGORY_PLAYER, "start-on-prepared", 1L)

            // 网络和格式设置（含 RTMP 增强）
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "timeout", 10000000L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "reconnect", 1L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "dns_cache_clear", 1L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "rtmp_live", 1L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "rtmp_buffer", 1024L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "rtmp_timeout", 5000000L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "rtmp_pageurl", 1L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "rtsp_sdp_url", 1L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "rtmp_swfurl", 1L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "analyzeduration", 2000000L)
            setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "probesize", 409600L)

            // 缓冲设置
            setOptionMethod.invoke(player, OPT_CATEGORY_PLAYER, "infbuf", 1L)
            setOptionMethod.invoke(player, OPT_CATEGORY_PLAYER, "packet-buffering", 1L)

            // 编解码设置
            setOptionMethod.invoke(player, OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)

            // 设置 UA 和 Headers
            val currentChannel = getCurrentChannel()
            if (currentChannel != null) {
                if (!currentChannel.ua.isNullOrEmpty()) {
                    setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "user_agent", currentChannel.ua)
                }
                
                if (!currentChannel.headers.isNullOrEmpty()) {
                    val sb = StringBuilder()
                    currentChannel.headers!!.forEach { (k, v) ->
                        sb.append(k).append(": ").append(v).append("\r\n")
                    }
                    setOptionMethod.invoke(player, OPT_CATEGORY_FORMAT, "headers", sb.toString())
                }
            }

            // 设置数据源并准备播放
            try {
                val setDataSourceMethod = ijkClass.getMethod("setDataSource", String::class.java)
                setDataSourceMethod.invoke(player, currentUrl)
                val prepareAsyncMethod = ijkClass.getMethod("prepareAsync")
                prepareAsyncMethod.invoke(player)
            } catch (e: Exception) {
                e.printStackTrace()
                // IJK 播放失败，回退到 ExoPlayer
                Toast.makeText(this, "IJKPlayer 播放失败，切换到 ExoPlayer", Toast.LENGTH_LONG).show()
                currentDecoderType = 0
                getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("decoder", 0).apply()
                initializeExoPlayer(currentUrl)
                return
            }

            // 清理 ExoPlayer 的绑定
            binding.playerView.player = null

            // 创建 SurfaceView 并加入到 playerView
            val surfaceView = android.view.SurfaceView(this)
            binding.playerView.removeAllViews()
            binding.playerView.addView(surfaceView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
            
            val setDisplayMethod = ijkClass.getMethod("setDisplay", android.view.SurfaceHolder::class.java)
            
            surfaceView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    try {
                        setDisplayMethod.invoke(player, holder)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                    try {
                        setDisplayMethod.invoke(player, holder)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                    try {
                        setDisplayMethod.invoke(player, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
            
            // 添加 OnPreparedListener 以隐藏背景图
            try {
                val listenerClass = Class.forName("tv.danmaku.ijk.media.player.IMediaPlayer\$OnPreparedListener")
                val listener = java.lang.reflect.Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { _, method, _ ->
                    if (method.name == "onPrepared") {
                        runOnUiThread {
                            binding.ivLoadingBackground.visibility = View.GONE
                        }
                    }
                    null
                }
                val setOnPreparedListenerMethod = ijkClass.getMethod("setOnPreparedListener", listenerClass)
                setOnPreparedListenerMethod.invoke(player, listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 准备并播放
            val prepareAsyncMethod = ijkClass.getMethod("prepareAsync")
            prepareAsyncMethod.invoke(player)
            
            // 显示当前线路信息
            if (currentChannel != null && currentChannel.getRouteCount() > 1) {
                showCurrentRouteInfo()
            }
            
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            Toast.makeText(this, "IJKPlayer 库未安装\n请下载 IJKPlayer AAR 文件并放入 app/libs/ 目录\n已自动切换到 ExoPlayer", Toast.LENGTH_LONG).show()
            currentDecoderType = 0
            getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("decoder", 0).apply()
            initializeExoPlayer(currentUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "IJKPlayer 初始化失败: ${e.message}\n已自动切换到 ExoPlayer", Toast.LENGTH_LONG).show()
            currentDecoderType = 0
            getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("decoder", 0).apply()
            initializeExoPlayer(currentUrl)
        }
    }

    
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
                // 已尝试所有线路
                hasTriedAllRoutes = true
                Toast.makeText(
                    this,
                    getString(R.string.all_routes_failed),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        } else {
            // 只有一个线路或已尝试所有线路
            Toast.makeText(
                this,
                getString(R.string.error_playing_video) + ": ${error.message}",
                Toast.LENGTH_LONG
            ).show()
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
        
        // 释放 IJKPlayer (使用反射)
        ijkMediaPlayer?.let { ijk ->
            try {
                val ijkClass = ijk.javaClass
                val stopMethod = ijkClass.getMethod("stop")
                stopMethod.invoke(ijk)
                val releaseMethod = ijkClass.getMethod("release")
                releaseMethod.invoke(ijk)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        ijkMediaPlayer = null
        
        // 清理 playerView,确保切换解码器时视图正确重置
        try {
            // 移除 ExoPlayer 的绑定
            binding.playerView.player = null
            
            // 只在之前使用 IJKPlayer 时才移除子视图
            // ExoPlayer 使用 PlayerView 的内部视图,不应该被移除
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 显示解码器选择对话框
     */
    private fun showDecoderDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_decoder_select, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val radioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.decoder_radio_group)
        
        // 读取保存的解码器选择
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedDecoder = prefs.getInt("decoder", 0)
        currentDecoderType = savedDecoder
        
        when (savedDecoder) {
            0 -> radioGroup.check(R.id.radio_exoplayer)
            1 -> radioGroup.check(R.id.radio_ijk_hw)
            2 -> radioGroup.check(R.id.radio_ijk_sw)
        }
        
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val decoderType = when (checkedId) {
                R.id.radio_exoplayer -> 0
                R.id.radio_ijk_hw -> 1
                R.id.radio_ijk_sw -> 2
                else -> 0
            }
            
            // 保存选择
            prefs.edit().putInt("decoder", decoderType).apply()
            currentDecoderType = decoderType
            
            // 提示用户
            val decoderName = when (decoderType) {
                0 -> "ExoPlayer"
                1 -> "IJKPlayer 硬解"
                2 -> "IJKPlayer 软解"
                else -> "ExoPlayer"
            }
            Toast.makeText(this, "已切换到$decoderName", Toast.LENGTH_SHORT).show()
            
            dialog.dismiss()
            
            // 重新初始化播放器应用新设置
            releasePlayer()
            initializePlayer()
        }
        
        dialog.setOnDismissListener {
            showBottomControls()
        }
        
        hideBottomControls()
        dialog.show()
    }
    
    /**
     * 显示菜单对话框
     */
    private fun showMenuDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_menu, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 软件更新按钮
        dialogView.findViewById<android.widget.Button>(R.id.btn_check_update).setOnClickListener {
            Toast.makeText(this, "检查更新功能待实现\n需要配置更新服务器地址", Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }
        
        // 激活按钮
        dialogView.findViewById<android.widget.Button>(R.id.btn_activation).setOnClickListener {
            Toast.makeText(this, "激活功能待实现\n需要配置激活验证逻辑", Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }

        // 设置城市按钮
        dialogView.findViewById<android.widget.Button>(R.id.btn_set_city).setOnClickListener {
            dialog.setOnDismissListener(null)
            dialog.dismiss()
            showCitySettingDialog()
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
}
