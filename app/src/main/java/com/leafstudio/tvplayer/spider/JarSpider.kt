package com.leafstudio.tvplayer.spider

import android.content.Context

/**
 * JAR Spider 包装器
 * 实际的 Spider 实例由 SpiderManager 管理
 * 这个类只是一个轻量级的代理
 */
class JarSpider(
    private val context: Context,
    private val jarUrl: String,
    private val className: String,
    private val siteKey: String
) : Spider {
    
    // 实际的 Spider 实例（延迟初始化）
    private var actualSpider: Spider? = null
    
    override fun init(ext: String) {
        try {
            android.util.Log.d("JarSpider", "初始化 JAR Spider: $className from $jarUrl")
            
            // 从 SpiderManager 获取 Spider 实例（会自动缓存）
            actualSpider = SpiderManager.getJarSpider(context, siteKey, className, ext, jarUrl)
            
            android.util.Log.d("JarSpider", "✅ JAR Spider 初始化成功")
        } catch (e: Exception) {
            android.util.Log.e("JarSpider", "JAR Spider 初始化失败", e)
            throw Exception("JAR Spider 初始化失败: ${e.message}")
        }
    }
    
    override fun home(filter: Boolean): String {
        return try {
            actualSpider?.home(filter) ?: ""
        } catch (e: Exception) {
            android.util.Log.e("JarSpider", "home() 失败", e)
            ""
        }
    }
    
    override fun category(tid: String, pg: String, filter: Boolean, extend: String): String {
        return try {
            actualSpider?.category(tid, pg, filter, extend) ?: ""
        } catch (e: Exception) {
            android.util.Log.e("JarSpider", "category() 失败", e)
            ""
        }
    }
    
    override fun detail(ids: String): String {
        return try {
            actualSpider?.detail(ids) ?: ""
        } catch (e: Exception) {
            android.util.Log.e("JarSpider", "detail() 失败", e)
            ""
        }
    }
    
    override fun play(flag: String, id: String, flags: List<String>): String {
        return try {
            actualSpider?.play(flag, id, flags) ?: ""
        } catch (e: Exception) {
            android.util.Log.e("JarSpider", "play() 失败", e)
            ""
        }
    }
    
    override fun search(wd: String, quick: Boolean): String {
        return try {
            actualSpider?.search(wd, quick) ?: ""
        } catch (e: Exception) {
            android.util.Log.e("JarSpider", "search() 失败", e)
            ""
        }
    }
    
    override fun action(action: String): String {
        return try {
            actualSpider?.action(action) ?: ""
        } catch (e: Exception) {
            android.util.Log.e("JarSpider", "action() 失败", e)
            ""
        }
    }
    
    override fun destroy() {
        // Spider 实例由 SpiderManager 管理，这里不需要做什么
        actualSpider = null
    }
}
