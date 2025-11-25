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
        .proxy(java.net.Proxy.NO_PROXY)
        .build()
    
    /**
     * 从 URL 加载播放列表内容
     * @param url 播放列表 URL
     * @return 播放列表内容字符串
     * @throws IOException 网络错误
     */
    suspend fun loadPlaylist(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("加载播放列表失败: ${response.code}")
            }
            
            response.body?.string() ?: throw IOException("播放列表内容为空")
        }
    }
}
