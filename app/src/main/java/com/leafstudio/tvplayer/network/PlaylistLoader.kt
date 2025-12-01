package com.leafstudio.tvplayer.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 播放列表加载器
 */
class PlaylistLoader {
    
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        // 移除代理限制，使用系统默认
        // .proxy(java.net.Proxy.NO_PROXY)
        .build()
    
    /**
     * 从 URL 加载播放列表内容
     * @param url 播放列表 URL
     * @return 播放列表内容字符串
     * @throws IOException 网络错误
     */
    suspend fun loadPlaylist(url: String): String = withContext(Dispatchers.IO) {
        android.util.Log.d("PlaylistLoader", "开始加载播放列表: $url")
        
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "LeafStudio TV Player/2.1.7")
                .build()
            
            android.util.Log.d("PlaylistLoader", "发送网络请求...")
            
            client.newCall(request).execute().use { response ->
                android.util.Log.d("PlaylistLoader", "响应状态码: ${response.code}")
                
                if (!response.isSuccessful) {
                    val errorMsg = "加载播放列表失败: HTTP ${response.code}"
                    android.util.Log.e("PlaylistLoader", errorMsg)
                    throw IOException(errorMsg)
                }
                
                val content = response.body?.string()
                if (content.isNullOrEmpty()) {
                    val errorMsg = "播放列表内容为空"
                    android.util.Log.e("PlaylistLoader", errorMsg)
                    throw IOException(errorMsg)
                }
                
                android.util.Log.d("PlaylistLoader", "播放列表加载成功，长度: ${content.length} 字节")
                android.util.Log.d("PlaylistLoader", "内容前100字符: ${content.take(100)}")
                
                content
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistLoader", "加载播放列表异常", e)
            throw e
        }
    }
}
