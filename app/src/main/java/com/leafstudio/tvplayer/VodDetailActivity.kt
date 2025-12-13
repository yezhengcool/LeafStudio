package com.leafstudio.tvplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.leafstudio.tvplayer.model.Vod
import com.leafstudio.tvplayer.ui.Episode
import com.leafstudio.tvplayer.ui.EpisodeAdapter
import com.leafstudio.tvplayer.ui.SourceAdapter
import com.leafstudio.tvplayer.utils.loadUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class VodDetailActivity : AppCompatActivity() {

    private lateinit var episodeAdapter: EpisodeAdapter
    private lateinit var sourceAdapter: SourceAdapter
    
    private var allEpisodesMap = mutableMapOf<String, List<Episode>>()
    private var currentSourceIndex = 0
    private var sourceNames = mutableListOf<String>()
    private var currentEpisodes = listOf<Episode>()
    private var isReverseOrder = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vod_detail)

        // 设置全屏
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        val vod = intent.getParcelableExtra<Vod>("vod")
        
        // Load wallpaper
        val wallpaperUrl = MediaActivity.wallpaperUrl
        if (!wallpaperUrl.isNullOrEmpty()) {
             val ivWallpaper = findViewById<ImageView>(R.id.iv_wallpaper)
             
             // 添加随机参数
             val delimiter = if (wallpaperUrl.contains("?")) "&" else "?"
             val finalUrl = "$wallpaperUrl${delimiter}t=${System.currentTimeMillis()}"
             
             ivWallpaper.loadUrl(finalUrl)
        }
        
        if (vod == null) {
            finish()
            return
        }

        setupViews(vod)
    }

    private fun setupViews(vod: Vod) {
        // 绑定视图
        val ivPoster = findViewById<ImageView>(R.id.iv_poster)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        val tvInfo1 = findViewById<TextView>(R.id.tv_info_1)
        val tvActors = findViewById<TextView>(R.id.tv_actors)
        val tvDirector = findViewById<TextView>(R.id.tv_director)
        val tvContent = findViewById<TextView>(R.id.tv_content)
        val rvEpisodes = findViewById<RecyclerView>(R.id.rv_episodes)
        val rvSources = findViewById<RecyclerView>(R.id.rv_sources)
        
        val btnFullscreen = findViewById<Button>(R.id.btn_fullscreen)
        val btnQuickSearch = findViewById<Button>(R.id.btn_quick_search)
        val btnReverse = findViewById<Button>(R.id.btn_reverse)
        val btnFavorite = findViewById<Button>(R.id.btn_favorite)

        // 设置数据
        tvTitle.text = vod.vod_name
        
        // 如果没有播放地址且有 Spider，尝试获取详情
        if (vod.vod_play_url.isNullOrEmpty() && com.leafstudio.tvplayer.spider.SpiderManager.currentSpider != null) {
            loadDetailFromSpider(vod)
        } else {
            updateUiWithVod(vod)
        }
        
        // 按钮事件
        btnFullscreen.setOnClickListener {
            if (currentEpisodes.isNotEmpty()) {
                playEpisode(currentEpisodes[0], vod.vod_name)
            } else {
                Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnQuickSearch.setOnClickListener {
            val intent = Intent(this, MediaActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra("search_keyword", vod.vod_name)
            startActivity(intent)
            finish()
        }
        
        btnReverse.setOnClickListener {
            isReverseOrder = !isReverseOrder
            updateEpisodeList(rvEpisodes)
            Toast.makeText(this, if (isReverseOrder) "已倒序" else "已正序", Toast.LENGTH_SHORT).show()
        }
        
        btnFavorite.setOnClickListener {
            Toast.makeText(this, "已加入收藏", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadDetailFromSpider(vod: Vod) {
        com.leafstudio.tvplayer.spider.SpiderManager.execute {
            try {
                val json = com.leafstudio.tvplayer.spider.SpiderManager.currentSpider!!.detail(vod.vod_id)
                
                if (json.isNullOrEmpty()) {
                    runOnUiThread {
                        if (vod.vod_play_url.isNullOrEmpty()) {
                            Toast.makeText(this, "正在全网搜索...", Toast.LENGTH_SHORT).show()
                            findViewById<Button>(R.id.btn_quick_search).performClick()
                        } else {
                            updateUiWithVod(vod)
                        }
                    }
                    return@execute
                }

                val obj = JSONObject(json)
                val list = obj.optJSONArray("list")
                if (list != null && list.length() > 0) {
                    val item = list.getJSONObject(0)
                    val fullVod = Vod(
                        vod_id = item.optString("vod_id"),
                        vod_name = item.optString("vod_name"),
                        vod_pic = item.optString("vod_pic"),
                        vod_remarks = item.optString("vod_remarks"),
                        vod_play_url = item.optString("vod_play_url"),
                        vod_play_from = item.optString("vod_play_from"),
                        vod_year = item.optString("vod_year"),
                        vod_area = item.optString("vod_area"),
                        vod_actor = item.optString("vod_actor"),
                        vod_director = item.optString("vod_director"),
                        vod_content = item.optString("vod_content").ifEmpty { item.optString("des") }
                    )
                    runOnUiThread {
                        updateUiWithVod(fullVod)
                    }
                } else {
                    runOnUiThread {
                        if (vod.vod_play_url.isNullOrEmpty()) {
                            Toast.makeText(this, "正在全网搜索...", Toast.LENGTH_SHORT).show()
                            findViewById<Button>(R.id.btn_quick_search).performClick()
                        } else {
                            updateUiWithVod(vod)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "获取详情失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    updateUiWithVod(vod)
                }
            }
        }
    }

    private fun updateUiWithVod(vod: Vod) {
        val ivPoster = findViewById<ImageView>(R.id.iv_poster)
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        val tvInfo1 = findViewById<TextView>(R.id.tv_info_1)
        val tvActors = findViewById<TextView>(R.id.tv_actors)
        val tvDirector = findViewById<TextView>(R.id.tv_director)
        val tvContent = findViewById<TextView>(R.id.tv_content)
        val rvEpisodes = findViewById<RecyclerView>(R.id.rv_episodes)
        val rvSources = findViewById<RecyclerView>(R.id.rv_sources)
        
        val btnFullscreen = findViewById<Button>(R.id.btn_fullscreen)
        
        tvTitle.text = vod.vod_name
        
        // 拼接信息
        val infoBuilder = StringBuilder()
        infoBuilder.append("来源: ${vod.vod_play_from ?: "默认"}  ")
        if (!vod.vod_year.isNullOrEmpty()) infoBuilder.append("年份: ${vod.vod_year}  ")
        if (!vod.vod_area.isNullOrEmpty()) infoBuilder.append("地区: ${vod.vod_area}  ")
        if (!vod.vod_remarks.isNullOrEmpty()) infoBuilder.append("备注: ${vod.vod_remarks}")
        tvInfo1.text = infoBuilder.toString()
        
        tvActors.text = "演员: ${vod.vod_actor ?: "未知"}"
        tvDirector.text = "导演: ${vod.vod_director ?: "未知"}"
        tvContent.text = android.text.Html.fromHtml("内容简介: ${vod.vod_content ?: "暂无简介"}")

        ivPoster.loadUrl(vod.vod_pic)

        // 解析播放源和集数
        sourceNames.clear()
        allEpisodesMap.clear()
        currentSourceIndex = 0
        parseSourcesAndEpisodes(vod.vod_play_from, vod.vod_play_url)
        
        // 设置源列表
        rvSources.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        sourceAdapter = SourceAdapter(sourceNames) { index ->
            currentSourceIndex = index
            updateEpisodeList(rvEpisodes)
        }
        rvSources.adapter = sourceAdapter
        
        // 设置集数列表
        val flexLayoutManager = com.google.android.flexbox.FlexboxLayoutManager(this).apply {
            flexDirection = com.google.android.flexbox.FlexDirection.ROW
            flexWrap = com.google.android.flexbox.FlexWrap.WRAP
            justifyContent = com.google.android.flexbox.JustifyContent.FLEX_START
            alignItems = com.google.android.flexbox.AlignItems.FLEX_START
        }
        rvEpisodes.layoutManager = flexLayoutManager
        episodeAdapter = EpisodeAdapter(emptyList()) { episode ->
            playEpisode(episode, vod.vod_name)
        }
        rvEpisodes.adapter = episodeAdapter
        
        // 初始显示第一个源的集数
        if (sourceNames.isNotEmpty()) {
            updateEpisodeList(rvEpisodes)
        }
    }
    
    private fun parseSourcesAndEpisodes(playFrom: String?, playUrl: String?) {
        if (playUrl.isNullOrEmpty()) return
        
        val urlParts = playUrl.split("$$$")
        val fromParts = playFrom?.split("$$$") ?: emptyList()
        
        for (i in urlParts.indices) {
            val sourceName = if (i < fromParts.size) fromParts[i] else "源${i + 1}"
            sourceNames.add(sourceName)
            
            val episodes = mutableListOf<Episode>()
            val items = urlParts[i].split("#")
            for (item in items) {
                val parts = item.split("$")
                if (parts.size >= 2) {
                    episodes.add(Episode(parts[0], parts[1]))
                } else if (item.isNotEmpty()) {
                    episodes.add(Episode("正片", parts[0]))
                }
            }
            allEpisodesMap[sourceName] = episodes
        }
    }
    
    private fun updateEpisodeList(recyclerView: RecyclerView) {
        if (sourceNames.isEmpty()) return
        val sourceName = sourceNames[currentSourceIndex]
        var episodes = allEpisodesMap[sourceName] ?: emptyList()
        
        if (isReverseOrder) {
            episodes = episodes.reversed()
        }
        
        currentEpisodes = episodes
        episodeAdapter = EpisodeAdapter(episodes) { episode ->
            playEpisode(episode, findViewById<TextView>(R.id.tv_title).text.toString())
        }
        recyclerView.adapter = episodeAdapter
    }
    
    private fun playEpisode(episode: Episode, title: String) {
         val vod = intent.getParcelableExtra<Vod>("vod")
         
         // extract playParser from vod_tag (format: "SiteName@@@ParserUrl" or just "ParserUrl")
         // 安全处理 split，防止越界
         val rawTag = vod?.vod_tag ?: ""
         val playParser = if (rawTag.contains("@@@")) {
              val parts = rawTag.split("@@@")
              if (parts.size > 1) parts[1] else ""
         } else {
              rawTag
         }
 
        // 场景1: 存在 JSON 解析配置
        if (!playParser.isNullOrEmpty() && playParser.startsWith("json:")) {
             Toast.makeText(this, "正在解析播放地址...", Toast.LENGTH_SHORT).show()
             val parseUrl = playParser.substring(5) // remove "json:"
             
             com.leafstudio.tvplayer.spider.SpiderManager.execute {
                 var parseSuccess = false
                 try {
                     val targetUrl = parseUrl + episode.url
                     android.util.Log.d("VodDetailActivity", "Parsing Target URL: $targetUrl")
                     
                     val client = com.leafstudio.tvplayer.utils.OkHttpUtil.getUnsafeOkHttpClient()
                     val request = Request.Builder()
                         .url(targetUrl)
                         .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                         .build()
                         
                     val response = client.newCall(request).execute()
                     val jsonStr = response.body?.string()
                     
                     if (jsonStr != null && !jsonStr.trim().startsWith("<")) {
                         val json = JSONObject(jsonStr)
                         val realUrl = json.optString("url")
                         if (realUrl.isNotEmpty()) {
                             parseSuccess = true
                             runOnUiThread {
                                 startPlayer(realUrl, title, episode.name)
                             }
                         }
                     } else if (jsonStr != null && jsonStr.trim().startsWith("<")) {
                         android.util.Log.w("VodDetailActivity", "CMS Parse returned HTML: $jsonStr")
                     }
                 } catch (e: Exception) {
                     android.util.Log.e("VodDetailActivity", "JSON Parse failed", e)
                 }
                 
                 // 如果 JSON 解析失败，降级处理 (Spider -> Direct)
                 if (!parseSuccess) {
                     android.util.Log.w("VodDetailActivity", "JSON Parse failed, falling back...")
                     fallbackToSpiderOrDirect(episode, title)
                 }
             }
        } 
        // 场景2: 无 JSON 配置，直接进入降级处理 (Spider -> Direct)
        else {
            fallbackToSpiderOrDirect(episode, title)
        }
    }

    // 统一的降级播放逻辑：尝试 Spider -> 失败则直接播放
    private fun fallbackToSpiderOrDirect(episode: Episode, title: String) {
        if (com.leafstudio.tvplayer.spider.SpiderManager.currentSpider != null) {
            runOnUiThread { Toast.makeText(this, "尝试 Spider 解析...", Toast.LENGTH_SHORT).show() }
            com.leafstudio.tvplayer.spider.SpiderManager.execute {
                try {
                    val flag = if (currentSourceIndex < sourceNames.size) sourceNames[currentSourceIndex] else ""
                    val playJson = com.leafstudio.tvplayer.spider.SpiderManager.currentSpider!!.play(flag, episode.url, emptyList())
                    
                    var spiderSuccess = false
                    if (!playJson.isNullOrEmpty() && !playJson.trim().startsWith("<")) {
                        val playObj = JSONObject(playJson)
                        val realUrl = playObj.optString("url")
                         if (realUrl.isNotEmpty()) {
                             spiderSuccess = true
                             runOnUiThread {
                                 startPlayer(realUrl, title, episode.name)
                             }
                         }
                    }
                    
                    if (!spiderSuccess) {
                        android.util.Log.w("VodDetailActivity", "Spider play returned invalid data, using original URL.")
                        runOnUiThread {
                            // Spider 也失败了，最后尝试直接播放原始 URL
                             startPlayer(episode.url, title, episode.name)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VodDetailActivity", "Spider play failed", e)
                    runOnUiThread {
                         // Spider 报错，最后尝试直接播放原始 URL
                         startPlayer(episode.url, title, episode.name)
                    }
                }
            }
        } else {
            // 无 Spider，直接播放
            startPlayer(episode.url, title, episode.name)
        }
    }

    private fun startPlayer(url: String, title: String, episodeName: String) {
        val intent = Intent(this, VodPlayerActivity::class.java)
        intent.putExtra("url", url)
        intent.putExtra("name", "$title - $episodeName")
        startActivity(intent)
    }
}

