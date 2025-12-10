package com.leafstudio.tvplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.leafstudio.tvplayer.model.Category
import com.leafstudio.tvplayer.model.Live
import com.leafstudio.tvplayer.model.Site
import com.leafstudio.tvplayer.model.TvBoxConfig
import com.leafstudio.tvplayer.model.Vod
import com.leafstudio.tvplayer.ui.MediaCategoryAdapter
import com.leafstudio.tvplayer.ui.MediaContentAdapter
import com.leafstudio.tvplayer.ui.SourceAdapter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 多媒体 Activity - 仿 TVBox 界面
 */
class MediaActivity : FragmentActivity() {
    
    private lateinit var sourceAdapter: SourceAdapter
    private lateinit var categoryAdapter: MediaCategoryAdapter
    private lateinit var contentAdapter: MediaContentAdapter
    
    private var config: TvBoxConfig? = null
    private var currentSite: Site? = null
    private var currentCategory: Category? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
        
    private val configUrl = "https://yezheng.dpdns.org/tv/tvbox_2026.json"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media)
        
        // 设置全屏
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        
        setupViews()
        loadConfig()
    }
    
    override fun onResume() {
        super.onResume()
        // 发送广播暂停 PlaybackActivity 的播放器
        val intent = Intent("com.leafstudio.tvplayer.PAUSE_PLAYBACK")
        sendBroadcast(intent)
    }
    
    override fun onPause() {
        super.onPause()
        // 如果用户返回到 PlaybackActivity，发送广播恢复播放
        if (isFinishing) {
            val intent = Intent("com.leafstudio.tvplayer.RESUME_PLAYBACK")
            sendBroadcast(intent)
        }
    }
    
    private fun setupViews() {
        // 顶部时间
        val timeText = findViewById<TextView>(R.id.tv_time)
        timeText.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        
        // 源切换按钮
        findViewById<View>(R.id.tv_current_source).setOnClickListener {
            showSourceDialog()
        }
        
        // 搜索按钮
        findViewById<View>(R.id.btn_search).setOnClickListener {
            showSearchDialog()
        }
        
        // 历史按钮
        findViewById<View>(R.id.btn_history).setOnClickListener {
            Toast.makeText(this, "历史功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        // 直播按钮
        findViewById<View>(R.id.btn_live).setOnClickListener {
            Toast.makeText(this, "直播功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        // 推送按钮
        findViewById<View>(R.id.btn_push).setOnClickListener {
            Toast.makeText(this, "推送功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        // 收藏按钮
        findViewById<View>(R.id.btn_favorites).setOnClickListener {
            Toast.makeText(this, "收藏功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        // 设置按钮
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            Toast.makeText(this, "设置功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        // 分类列表
        val rvCategories = findViewById<RecyclerView>(R.id.rv_categories)
        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        categoryAdapter = MediaCategoryAdapter(emptyList()) { category ->
            currentCategory = category
            loadContent(category)
        }
        rvCategories.adapter = categoryAdapter
        
        // 内容列表
        val rvContent = findViewById<RecyclerView>(R.id.rv_content)
        rvContent.layoutManager = GridLayoutManager(this, 5) // 5列布局
        contentAdapter = MediaContentAdapter(emptyList()) { vod ->
            val intent = Intent(this, VodDetailActivity::class.java)
            intent.putExtra("vod", vod)
            startActivity(intent)
        }
        rvContent.adapter = contentAdapter
        
        // 源适配器初始化
        sourceAdapter = SourceAdapter(emptyList()) { index ->
            val sites = config?.sites
            if (sites != null && index >= 0 && index < sites.size) {
                onSiteSelected(sites[index])
            }
        }
        
        // 尝试恢复上次选择的源
        val lastSiteKey = getLastSelectedSite()
        if (lastSiteKey != null) {
            val lastSite = config?.sites?.find { it.key == lastSiteKey }
            if (lastSite != null) {
                // Wait for layout/data (?) or just select it
                // We are in setupViews, config might be null yet. 
                // Wait, setupViews is called BEFORE loadConfig. So config IS null here.
                // We should do restoration in loadConfig, not here.
            }
        }
    }
    
    private fun loadConfig() {
        showLoading(true)
        
        Thread {
            try {
                val request = Request.Builder()
                    .url(configUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                val response = client.newCall(request).execute()
                val jsonStr = response.body?.string()
                
                if (jsonStr != null) {
                    val jsonObject = JSONObject(jsonStr)
                    val sites = parseSites(jsonObject)
                    val lives = parseLives(jsonObject)
                    val spider = resolveUrl(jsonObject.optString("spider"))
                    
                    config = TvBoxConfig(spider, sites, lives)
                    
                    runOnUiThread {
                        showLoading(false)
                        sourceAdapter.updateSources(sites.map { it.name })
                        
                        // 尝试恢复上次选择的源
                        val lastSiteKey = getLastSelectedSite()
                        val lastSite = if (lastSiteKey != null) {
                            sites.find { it.key == lastSiteKey }
                        } else null
                        
                        if (lastSite != null) {
                            android.util.Log.d("MediaActivity", "恢复上次选择的源: ${lastSite.name}")
                            onSiteSelected(lastSite)
                        } else {
                            // 如果没有上次选择的源，选择第一个非公告源
                            val defaultSite = sites.firstOrNull { 
                                !it.api.contains("Notice", ignoreCase = true) && !it.name.contains("公告", ignoreCase = true)
                            }
                            if (defaultSite != null) {
                                onSiteSelected(defaultSite)
                            } else {
                                showError("没有可用的源")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    showLoading(false)
                    showError("配置加载失败: ${e.message}")
                }
            }
        }.start()
    }
    
    private fun parseSites(jsonObject: JSONObject): List<Site> {
        val sites = mutableListOf<Site>()
        val jsonArray = jsonObject.optJSONArray("sites") ?: return sites
        
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val rawApi = item.optString("api")
            val type = item.optInt("type")
            
            // 对于 Type 3 的 JAR Spider（类名格式），不进行 URL 解析
            val isJarSpider = type == 3 && !rawApi.contains("http") && !rawApi.contains("/") && !rawApi.endsWith(".js")
            val resolvedApi = if (isJarSpider) rawApi else resolveUrl(rawApi)
            
            val rawPlayUrl = item.optString("playurl")
            val playUrl = if (rawPlayUrl.startsWith("json:")) rawPlayUrl else resolveUrl(rawPlayUrl)
            
            sites.add(Site(
                key = item.optString("key"),
                name = item.optString("name"),
                type = type,
                api = resolvedApi,
                searchable = item.optInt("searchable"),
                quickSearch = item.optInt("quickSearch"),
                filterable = item.optInt("filterable"),
                ext = resolveUrl(item.optString("ext")),
                jar = resolveUrl(item.optString("jar")),
                playUrl = playUrl
            ))
        }
        
        return sites
    }
    
    private fun parseLives(jsonObject: JSONObject): List<Live> {
        return emptyList()
    }
    
    private fun resolveUrl(url: String): String {
        if (url.isBlank() || url.startsWith("http") || url.startsWith("json:")) return url
        
        val baseUrl = configUrl.substringBeforeLast("/")
        return when {
            url.startsWith("./") -> "$baseUrl/${url.substring(2)}"
            url.startsWith("/") -> {
                val host = configUrl.substringBefore("/", "").let {
                    if (it.contains("://")) configUrl.substringBefore("://") + "://" + configUrl.substringAfter("://").substringBefore("/")
                    else configUrl.substringBefore("/")
                }
                "$host$url"
            }
            else -> "$baseUrl/$url"
        }
    }
    
    private fun onSiteSelected(site: Site) {
        currentSite = site
        findViewById<TextView>(R.id.tv_current_source).text = "当前源: ${site.name}"
        
        // 保存当前选择的源
        saveLastSelectedSite(site.key)
        
        // 特殊处理：公告源
        if (site.api.contains("Notice", ignoreCase = true) || site.name.contains("公告", ignoreCase = true)) {
            android.util.Log.d("MediaActivity", "检测到通知类 Spider: ${site.name}")
            categoryAdapter.updateCategories(emptyList())
            contentAdapter.updateContent(emptyList())
            showLoading(true)
            
            com.leafstudio.tvplayer.spider.SpiderManager.execute {
                try {
                    android.util.Log.d("MediaActivity", "开始获取公告内容，ext: ${site.ext}")
                    
                    var noticeContent = ""
                    
                    // 方法1: 直接从 ext URL 获取
                    if (!site.ext.isNullOrEmpty() && (site.ext.startsWith("http://") || site.ext.startsWith("https://"))) {
                        try {
                            android.util.Log.d("MediaActivity", "尝试从 ext URL 获取公告: ${site.ext}")
                            val request = Request.Builder()
                                .url(site.ext)
                                .header("User-Agent", "Mozilla/5.0")
                                .build()
                            val response = client.newCall(request).execute()
                            val content = response.body?.string() ?: ""
                            android.util.Log.d("MediaActivity", "ext URL 返回: ${content.take(200)}")
                            
                            if (content.isNotEmpty()) {
                                noticeContent = parseNoticeFromJson(content)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("MediaActivity", "从 ext URL 获取失败", e)
                        }
                    }
                    
                    // 方法2: 尝试通过 Spider action
                    if (noticeContent.isEmpty()) {
                        val jarUrl = if (!site.jar.isNullOrEmpty()) site.jar else config?.spider ?: ""
                        if (jarUrl.isNotEmpty()) {
                            try {
                                val spider = com.leafstudio.tvplayer.spider.JarSpider(this, jarUrl, site.api, site.key)
                                spider.init(site.ext ?: "")
                                val actionResult = spider.action("notice")
                                if (actionResult.isNotEmpty()) {
                                   noticeContent = parseNoticeFromJson(actionResult)
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("MediaActivity", "通过 Spider action 获取失败", e)
                            }
                        }
                    }
                    
                    android.util.Log.d("MediaActivity", "最终公告内容: $noticeContent")
                    
                    runOnUiThread {
                        showLoading(false)
                        if (noticeContent.isNotEmpty()) {
                            showNoticeMarquee(noticeContent)
                        } else {
                            showError("此源为公告源，但未获取到公告内容")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MediaActivity", "加载公告失败", e)
                    runOnUiThread {
                        showLoading(false)
                        showError("加载失败: ${e.message}")
                    }
                }
            }
            return
        }
        
        // 重置 UI
        hideNoticeMarquee()
        categoryAdapter.updateCategories(emptyList())
        contentAdapter.updateContent(emptyList())
        showLoading(true)
        
        com.leafstudio.tvplayer.spider.SpiderManager.execute {
            // 在同一线程销毁旧的 Spider，避免线程安全问题
            com.leafstudio.tvplayer.spider.SpiderManager.currentSpider?.destroy()
            com.leafstudio.tvplayer.spider.SpiderManager.currentSpider = null
            
            try {
                if (site.type == 3) {
                    // Type 3: 区分 JS Spider 和 JAR Spider
                    if (site.api.contains("http") || site.api.contains("/") || site.api.endsWith(".js")) {
                        // JS Spider: api 是 URL 或文件路径
                        loadJsSpider(site)
                    } else {
                        // JAR Spider: api 是类名（如 csp_Douban）
                        loadJarSpider(site)
                    }
                } else if (site.type == 0 || site.type == 1) {
                    // Type 0/1: CMS
                    loadCmsCategories(site)
                } else {
                    runOnUiThread {
                        showError("不支持的源类型: ${site.type}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    showError("加载失败: ${e.message}")
                }
            }
        }
    }
    
    private fun loadJsSpider(site: Site) {
        try {
            val spider = com.leafstudio.tvplayer.spider.JsSpider(this, site.api)
            try {
                spider.init(site.ext ?: "")
            } catch (e: Exception) {
                // JS init might fail if extended not supported, ignore
            }
            com.leafstudio.tvplayer.spider.SpiderManager.currentSpider = spider
            
            val homeContent = spider.home(true)
            android.util.Log.d("MediaActivity", "JS Spider home: $homeContent")
            
            if (homeContent.isNotEmpty()) {
                val jsonObject = JSONObject(homeContent)
                val classes = parseCategories(jsonObject)
                
                runOnUiThread {
                    showLoading(false)
                    categoryAdapter.updateCategories(classes)
                    if (classes.isNotEmpty()) {
                        loadContent(classes[0])
                    }
                }
            } else {
                runOnUiThread {
                    showLoading(false)
                    showError("获取首页失败")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                showLoading(false)
                showError("JS加载失败: ${e.message}")
            }
        }
    }
    
    private fun loadJarSpider(site: Site) {
        try {
            val jarUrl = if (!site.jar.isNullOrEmpty()) site.jar else config?.spider ?: ""
            if (jarUrl.isEmpty()) throw Exception("未配置 JAR URL")
            
            val spider = com.leafstudio.tvplayer.spider.JarSpider(this, jarUrl, site.api, site.key)
            spider.init(site.ext ?: "")
            com.leafstudio.tvplayer.spider.SpiderManager.currentSpider = spider
            
            val homeContent = spider.home(true)
            android.util.Log.d("MediaActivity", "JAR Spider home: $homeContent")
             
            if (homeContent.isNotEmpty()) {
                val jsonObject = JSONObject(homeContent)
                val classes = parseCategories(jsonObject)
                
                runOnUiThread {
                    showLoading(false)
                    categoryAdapter.updateCategories(classes)
                    if (classes.isNotEmpty()) {
                        loadContent(classes[0])
                    }
                }
            } else {
                 runOnUiThread {
                    showLoading(false)
                    showError("获取首页失败")
                }
            }
        } catch (e: Exception) {
             e.printStackTrace()
             runOnUiThread {
                showLoading(false)
                showError("JAR加载失败: ${e.message}")
            }
        }
    }
    
    // 之前缺失的 parseCategories 方法
    private fun parseCategories(jsonObject: JSONObject): List<Category> {
         val classArray = jsonObject.optJSONArray("class")
         val categories = mutableListOf<Category>()
         if (classArray != null) {
             for (i in 0 until classArray.length()) {
                 val item = classArray.getJSONObject(i)
                 categories.add(Category(
                     type_id = item.optString("type_id"),
                     type_name = item.optString("type_name")
                 ))
             }
         }
         return categories
    }

    private fun saveLastSelectedSite(siteKey: String) {
        val prefs = getSharedPreferences("media_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_selected_site", siteKey).apply()
    }
    
    private fun getLastSelectedSite(): String? {
        val prefs = getSharedPreferences("media_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_selected_site", null)
    }
    
    private fun showNoticeMarquee(content: String) {
        val marqueeView = findViewById<TextView>(R.id.tv_notice_marquee)
        if (marqueeView != null) {
            marqueeView.text = content
                            marqueeView.visibility = View.VISIBLE
            marqueeView.isSelected = true 
        }
    }
    
    private fun hideNoticeMarquee() {
        val marqueeView = findViewById<TextView>(R.id.tv_notice_marquee)
        if (marqueeView != null) {
            marqueeView.visibility = View.GONE
            marqueeView.text = ""
        }
    }
    
    private fun parseNoticeFromJson(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            
            // 尝试多种可能的字段名
            val possibleFields = listOf("msg", "message", "notice", "content", "text", "data")
            
            for (field in possibleFields) {
                val value = json.optString(field)
                if (value.isNotEmpty()) {
                    return value
                }
            }
            
            // 如果有 list 数组，尝试从第一个元素获取
            val list = json.optJSONArray("list")
            if (list != null && list.length() > 0) {
                val first = list.getJSONObject(0)
                for (field in possibleFields) {
                    val value = first.optString(field)
                    if (value.isNotEmpty()) {
                        return value
                    }
                }
                
                // 尝试 vod_name 或 vod_content
                val vodName = first.optString("vod_name")
                val vodContent = first.optString("vod_content")
                if (vodName.isNotEmpty()) return vodName
                if (vodContent.isNotEmpty()) return vodContent
            }
            
            ""
        } catch (e: Exception) {
            // 如果不是 JSON，直接返回原始文本（如果看起来不像 HTML/JSON）
            if (jsonStr.startsWith("{") || jsonStr.startsWith("[")) "" else jsonStr
        }
    }
    
    private fun loadCmsCategories(site: Site) {
        try {
            var apiUrl = site.api.replace("/at/xml/", "/at/json/", ignoreCase = true)
            
            val url = if (apiUrl.contains("?")) "${apiUrl}&ac=list" else "${apiUrl}?ac=list"
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val jsonStr = response.body?.string()
            
            if (jsonStr != null) {
                val jsonObject = JSONObject(jsonStr)
                val classArray = jsonObject.optJSONArray("class")
                
                val categories = mutableListOf<Category>()
                if (classArray != null) {
                    for (i in 0 until classArray.length()) {
                        val item = classArray.getJSONObject(i)
                        categories.add(Category(
                            type_id = item.optString("type_id"),
                            type_name = item.optString("type_name")
                        ))
                    }
                }
                
                runOnUiThread {
                    showLoading(false)
                    categoryAdapter.updateCategories(categories)
                    
                    if (categories.isNotEmpty()) {
                        loadContent(categories[0])
                    }
                }
            } else {
                runOnUiThread {
                    showLoading(false)
                    showError("获取分类失败：返回数据为空")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                showLoading(false)
                showError("加载分类失败: ${e.message}")
            }
        }
    }
    
    private fun loadContent(category: Category) {
        currentCategory = category
        showLoading(true)
        contentAdapter.updateContent(emptyList())
        
        val site = currentSite ?: return
        
        com.leafstudio.tvplayer.spider.SpiderManager.execute {
            try {
                val list = mutableListOf<Vod>()
                
                if (site.type == 3) {
                    // Spider 源逻辑
                    val spider = com.leafstudio.tvplayer.spider.SpiderManager.currentSpider
                    if (spider != null) {
                        // 假设第一页，无筛选
                        val result = spider.category(category.type_id, "1", false, "{}")
                        if (result.isNotEmpty()) {
                            val jsonObject = JSONObject(result)
                            val listArray = jsonObject.optJSONArray("list")
                            if (listArray != null) {
                                for (i in 0 until listArray.length()) {
                                    val item = listArray.getJSONObject(i)
                                    list.add(Vod(
                                        vod_id = item.optString("vod_id"),
                                        vod_name = item.optString("vod_name"),
                                        vod_pic = item.optString("vod_pic"),
                                        vod_remarks = item.optString("vod_remarks"),
                                        vod_play_url = item.optString("vod_play_url"),
                                        vod_year = item.optString("vod_year"),
                                        vod_area = item.optString("vod_area"),
                                        vod_director = item.optString("vod_director"),
                                        vod_actor = item.optString("vod_actor"),
                                        vod_content = item.optString("vod_content").ifEmpty { item.optString("des") },
                                        vod_tag = "${site.name}@@@${site.playUrl ?: ""}" // 传递 playUrl 用于播放解析
                                    ))
                                }
                            }
                        }
                    }
                } else {
                    // CMS 源逻辑 (Type 0/1)
                    var apiUrl = site.api.replace("/at/xml/", "/at/json/", ignoreCase = true)
                    
                    val url = if (apiUrl.contains("?")) {
                        "${apiUrl}&ac=detail&t=${category.type_id}"
                    } else {
                        "${apiUrl}?ac=detail&t=${category.type_id}"
                    }
                    
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val jsonStr = response.body?.string()
                    
                    if (jsonStr != null) {
                        val jsonObject = JSONObject(jsonStr)
                        val listArray = jsonObject.optJSONArray("list")
                        
                        if (listArray != null) {
                            for (i in 0 until listArray.length()) {
                                val item = listArray.getJSONObject(i)
                                list.add(Vod(
                                    vod_id = item.optString("vod_id"),
                                    vod_name = item.optString("vod_name"),
                                    vod_pic = item.optString("vod_pic"),
                                    vod_remarks = item.optString("vod_remarks"),
                                    vod_play_url = item.optString("vod_play_url"),
                                    vod_year = item.optString("vod_year"),
                                    vod_area = item.optString("vod_area"),
                                    vod_director = item.optString("vod_director"),
                                    vod_actor = item.optString("vod_actor"),
                                    vod_content = item.optString("vod_content").ifEmpty { item.optString("des") },
                                    vod_tag = "${site.name}@@@${site.playUrl ?: ""}"
                                ))
                            }
                        }
                    }
                }

                runOnUiThread {
                    showLoading(false)
                    if (list.isEmpty()) {
                        // showError("未获取到内容") // 可选：不显示错误，或者显示“暂无内容”
                        Toast.makeText(this@MediaActivity, "暂无内容", Toast.LENGTH_SHORT).show()
                    } else {
                        contentAdapter.updateContent(list)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    showLoading(false)
                    showError("加载内容失败: ${e.message}")
                }
            }
        }
    }
    
    private fun showSourceDialog() {
        val sites = config?.sites ?: return
        if (sites.isEmpty()) {
            Toast.makeText(this, "没有可用的源", Toast.LENGTH_SHORT).show()
            return
        }
        
        val siteNames = sites.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择视频源")
            .setItems(siteNames) { _, which ->
                onSiteSelected(sites[which])
            }
            .show()
    }
    
    private fun showSearchDialog() {
        val editText = EditText(this)
        editText.hint = "输入搜索关键词"
        
        AlertDialog.Builder(this)
            .setTitle("搜索")
            .setView(editText)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = editText.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    performSearch(keyword)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun performSearch(keyword: String) {
        val sites = config?.sites?.filter { it.searchable == 1 && (it.type == 0 || it.type == 1) } ?: return
        
        if (sites.isEmpty()) {
            showError("没有可搜索的源")
            return
        }
        
        // 清除 Spider，防止干扰 CMS 搜索结果的播放逻辑
        com.leafstudio.tvplayer.spider.SpiderManager.currentSpider = null

        // Update title
        findViewById<TextView>(R.id.tv_current_source).text = "搜索: $keyword"
        
        // Clear categories
        categoryAdapter.updateCategories(emptyList())
        
        showLoading(true)
        
        Thread {
            val allResults = mutableListOf<Vod>()
            
            for (site in sites) {
                try {
                    // CMS API: ?ac=detail&wd=KEYWORD
                    var apiUrl = site.api.replace("/at/xml/", "/at/json/", ignoreCase = true)
                    val url = if (apiUrl.contains("?")) "${apiUrl}&ac=detail&wd=$keyword" else "${apiUrl}?ac=detail&wd=$keyword"
                    
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val jsonStr = response.body?.string()
                    
                    if (jsonStr != null) {
                        val jsonObject = JSONObject(jsonStr)
                        val listArray = jsonObject.optJSONArray("list")
                        
                        if (listArray != null) {
                            for (i in 0 until listArray.length()) {
                                val item = listArray.getJSONObject(i)
                                allResults.add(Vod(
                                    vod_id = item.optString("vod_id"),
                                    vod_name = item.optString("vod_name"),
                                    vod_pic = item.optString("vod_pic"),
                                    vod_remarks = item.optString("vod_remarks"),
                                    vod_play_url = item.optString("vod_play_url"),
                                    vod_year = item.optString("vod_year"),
                                    vod_area = item.optString("vod_area"),
                                    vod_director = item.optString("vod_director"),
                                    vod_actor = item.optString("vod_actor"),
                                    vod_content = item.optString("vod_content").ifEmpty { item.optString("des") },
                                    vod_tag = "${site.name}@@@${site.playUrl ?: ""}"
                                ))
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Continue to next source
                }
            }
            
            runOnUiThread {
                showLoading(false)
                if (allResults.isEmpty()) {
                    showError("未找到相关内容")
                } else {
                    contentAdapter.updateContent(allResults)
                    Toast.makeText(this, "找到 ${allResults.size} 个结果", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun showLoading(show: Boolean) {
        findViewById<View>(R.id.progress_bar).visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun showError(message: String) {
        findViewById<View>(R.id.progress_bar).visibility = View.GONE
        findViewById<TextView>(R.id.error_text).apply {
            text = message
            visibility = View.VISIBLE
        }
    }
}
