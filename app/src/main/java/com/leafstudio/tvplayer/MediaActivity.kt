package com.leafstudio.tvplayer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.leafstudio.tvplayer.databinding.ActivityMediaBinding
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 多媒体 Activity - 加载和显示 TVBox 配置
 */
class MediaActivity : FragmentActivity() {
    
    private lateinit var binding: ActivityMediaBinding
    private var mediaAdapter: MediaAdapter? = null
    private val configUrl = "https://yezheng.dpdns.org/tv/tvbox_2026.json"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置全屏
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        
        // 初始化 RecyclerView
        setupRecyclerView()
        
        // 加载配置
        loadConfig()
    }
    
    private fun setupRecyclerView() {
        binding.mediaRecyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun loadConfig() {
        binding.progressBar.visibility = View.VISIBLE
        
        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url(configUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string()
                    if (!jsonStr.isNullOrEmpty()) {
                        val jsonObject = JSONObject(jsonStr)
                        parseConfig(jsonObject)
                    } else {
                        showError("配置内容为空")
                    }
                } else {
                    showError("加载失败: ${response.code}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("加载失败: ${e.message}")
            } finally {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }.start()
    }
    
    private fun parseConfig(jsonObject: JSONObject) {
        try {
            val items = mutableListOf<MediaConfigItem>()
            
            // 解析 spider
            val spider = jsonObject.optString("spider", "")
            if (spider.isNotEmpty()) {
                items.add(MediaConfigItem("爬虫配置", spider, MediaConfigType.SPIDER))
            }
            
            // 解析 proxy
            val proxyArray = jsonObject.optJSONArray("proxy")
            if (proxyArray != null) {
                for (i in 0 until proxyArray.length()) {
                    val proxyObj = proxyArray.getJSONObject(i)
                    val name = proxyObj.optString("name", "代理 ${i + 1}")
                    val hosts = proxyObj.optJSONArray("hosts")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                    val urls = proxyObj.optJSONArray("urls")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                    
                    items.add(MediaConfigItem(
                        name,
                        "主机: ${hosts.joinToString(", ")}\n代理: ${urls.joinToString(", ")}",
                        MediaConfigType.PROXY
                    ))
                }
            }
            
            // 解析 hosts
            val hostsArray = jsonObject.optJSONArray("hosts")
            if (hostsArray != null) {
                for (i in 0 until hostsArray.length()) {
                    val hostMapping = hostsArray.getString(i)
                    items.add(MediaConfigItem("主机映射 ${i + 1}", hostMapping, MediaConfigType.HOSTS))
                }
            }
            
            // 解析 headers
            val headersArray = jsonObject.optJSONArray("headers")
            if (headersArray != null) {
                for (i in 0 until headersArray.length()) {
                    val headerObj = headersArray.getJSONObject(i)
                    val host = headerObj.optString("host", "")
                    val header = headerObj.optJSONObject("header")
                    val headerStr = header?.let { obj ->
                        obj.keys().asSequence().map { key ->
                            "$key: ${obj.getString(key)}"
                        }.joinToString("\n")
                    } ?: ""
                    
                    items.add(MediaConfigItem(
                        "请求头: $host",
                        headerStr,
                        MediaConfigType.HEADERS
                    ))
                }
            }
            
            runOnUiThread {
                if (items.isEmpty()) {
                    showError("配置为空")
                } else {
                    mediaAdapter = MediaAdapter(items)
                    binding.mediaRecyclerView.adapter = mediaAdapter
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showError("解析失败: ${e.message}")
        }
    }
    
    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            binding.errorText.text = message
            binding.errorText.visibility = View.VISIBLE
        }
    }
}

/**
 * 媒体配置项数据类
 */
data class MediaConfigItem(
    val title: String,
    val content: String,
    val type: MediaConfigType
)

/**
 * 配置类型枚举
 */
enum class MediaConfigType {
    SPIDER,
    PROXY,
    HOSTS,
    HEADERS
}
