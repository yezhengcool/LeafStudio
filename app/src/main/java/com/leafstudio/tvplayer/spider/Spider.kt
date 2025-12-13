package com.leafstudio.tvplayer.spider

/**
 * 通用 Spider 接口
 * 支持 JS Spider 和 JAR Spider
 */
interface Spider {
    fun init(ext: String)
    fun home(filter: Boolean): String
    fun category(tid: String, pg: String, filter: Boolean, extend: String): String
    fun detail(ids: String): String
    fun play(flag: String, id: String, flags: List<String>): String
    fun search(wd: String, quick: Boolean): String
    fun action(action: String): String  // 用于特殊操作（如获取公告）
    fun destroy()
}
