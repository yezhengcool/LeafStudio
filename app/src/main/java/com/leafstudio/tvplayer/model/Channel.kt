package com.leafstudio.tvplayer.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 频道数据模型 - 支持多线路
 * @property id 频道唯一标识
 * @property name 频道名称
 * @property urls 流媒体 URL 列表（多线路）
 * @property logo 频道 logo URL (可选)
 * @property group 频道分组 (可选)
 * @property currentRouteIndex 当前选择的线路索引
 */
@Parcelize
data class Channel(
    val id: String,
    val name: String,
    val urls: List<String>,
    val logo: String? = null,
    val group: String? = null,
    var currentRouteIndex: Int = 0,
    var ua: String? = null,
    var headers: Map<String, String>? = null,
    var drmKey: String? = null,
    var drmType: String? = null,
    var number: Int = 0
) : Parcelable {
    /**
     * 获取当前选择的线路 URL
     */
    fun getCurrentUrl(): String {
        val rawUrl = if (urls.isNotEmpty() && currentRouteIndex in urls.indices) {
            urls[currentRouteIndex]
        } else {
            urls.firstOrNull() ?: ""
        }
        // 移除可能的中文标点或空格等非 URL 字符
        val cleaned = rawUrl.trim().replace(Regex("[，、;；]"), "")
        // 如果包含 $，只返回前面的 URL 部分
        return if (cleaned.contains('$')) {
            cleaned.substringBefore('$').trim()
        } else {
            cleaned
        }
    }
    
    /**
     * 获取线路数量
     */
    fun getRouteCount(): Int = urls.size
    
    /**
     * 获取线路名称
     * @param index 线路索引
     * @return 线路名称，优先显示 $ 后的名称，否则显示 线路 N
     */
    fun getRouteName(index: Int): String {
        if (index in urls.indices) {
            val rawUrl = urls[index]
            if (rawUrl.contains('$')) {
                val name = rawUrl.substringAfter('$').trim()
                if (name.isNotEmpty()) {
                    return name
                }
            }
        }
        return "线路 ${index + 1}"
    }
    
    /**
     * 切换到下一个线路
     * @return 是否成功切换（如果已经是最后一个线路则返回 false）
     */
    fun switchToNextRoute(): Boolean {
        return if (currentRouteIndex < urls.size - 1) {
            currentRouteIndex++
            true
        } else {
            false
        }
    }
    
    /**
     * 切换到指定线路
     * @param index 线路索引
     * @return 是否成功切换
     */
    fun switchToRoute(index: Int): Boolean {
        return if (index in urls.indices) {
            currentRouteIndex = index
            true
        } else {
            false
        }
    }
    
    companion object {
        /**
         * 从 M3U EXTINF 行创建频道对象
         * 格式: #EXTINF:-1 tvg-logo="logo_url" group-title="group",Channel Name
         * URL 格式: 实际URL$线路名称
         */
        fun fromExtInf(extInfLine: String, urlLine: String): Channel? {
            try {
                // 提取频道名称 (逗号后的部分)
                val nameStartIndex = extInfLine.lastIndexOf(',')
                if (nameStartIndex == -1) return null
                val name = extInfLine.substring(nameStartIndex + 1).trim()
                
                // 提取 logo
                val logoRegex = """tvg-logo="([^"]+)"""".toRegex()
                val logo = logoRegex.find(extInfLine)?.groupValues?.get(1)
                
                // 提取分组
                val groupRegex = """group-title="([^"]+)"""".toRegex()
                val group = groupRegex.find(extInfLine)?.groupValues?.get(1)
                
                // 处理 URL：保留原始 URL（包含 $线路名），以便后续提取
                val actualUrl = urlLine.trim()
                
                // 如果 URL 为空，返回 null
                if (actualUrl.isEmpty()) return null
                
                // 生成 ID (使用频道名称的 hash)
                val id = name.hashCode().toString()
                
                return Channel(id, name, listOf(actualUrl), logo, group)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }
}
