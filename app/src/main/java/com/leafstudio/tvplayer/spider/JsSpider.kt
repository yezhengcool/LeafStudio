package com.leafstudio.tvplayer.spider

import android.content.Context
import android.content.SharedPreferences
import app.cash.quickjs.QuickJs
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class JsSpider(private val context: Context, private val jsContent: String) : Spider {

    private var quickJs: QuickJs? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val local = LocalStorage(context)
    private val req = SpiderReq(client)

    override fun init(ext: String) {
        quickJs = QuickJs.create()
        try {
            // 注入基础对象
            quickJs?.set("local", ILocal::class.java, local)
            // 注入 req 对象 (有些爬虫使用 req，有些可能使用其他方式，这里提供一个基础的 req)
            // 注意：很多 TVBox JS 爬虫期望 req 是一个函数，而不是对象的方法。
            // QuickJS 注入 Java 对象时，在 JS 中是对象。
            // 如果 JS 中调用 req(url, opt)，我们需要注入一个名为 req 的函数。
            // 但 QuickJs 只能 set 对象。
            // 通常做法是注入一个对象，然后在 JS 中包装一下，或者 JS 本身就期望它是对象。
            // 这里我们注入 "spiderReq" 对象，并在 JS 头部注入一段 shim 代码。
            
            quickJs?.set("spiderReq", IReq::class.java, req)
            
            // 注入 shim 代码，将 req 映射到 spiderReq.request
            // 同时注入 console.log
            val shim = """
                var local = local || {};
                var req = function(url, opt) {
                    return spiderReq.request(url, JSON.stringify(opt || {}));
                };
                var console = {
                    log: function(msg) {
                        spiderReq.log(msg + "");
                    }
                };
            """.trimIndent()
            
            quickJs?.evaluate(shim)
            
            // 执行脚本
            quickJs?.evaluate(jsContent)
            
            // 调用 init
            val initScript = "init('$ext')"
            quickJs?.evaluate(initScript)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun home(filter: Boolean): String {
        return try {
            quickJs?.evaluate("home($filter)") as String
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun homeVod(): String {
        return try {
            quickJs?.evaluate("homeVod()") as String
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override fun category(tid: String, pg: String, filter: Boolean, extend: String): String {
        return try {
            // extend 需要转义或者处理 JSON
            val extendJson = if (extend.isEmpty()) "{}" else extend
            // 注意：如果 extend 是 JSON 字符串，直接传；如果是对象，需要 stringify。
            // 这里假设 extend 是 JSON 字符串。
            quickJs?.evaluate("category('$tid', '$pg', $filter, $extendJson)") as String
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override fun detail(ids: String): String {
        return try {
            quickJs?.evaluate("detail('$ids')") as String
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override fun play(flag: String, id: String, flags: List<String>): String {
        return try {
            // 将 flags List 转为 JS 数组字符串
            val flagsJson = flags.joinToString(prefix = "[", postfix = "]", separator = ",") { "'$it'" }
            quickJs?.evaluate("play('$flag', '$id', $flagsJson)") as String
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override fun search(wd: String, quick: Boolean): String {
        return try {
            quickJs?.evaluate("search('$wd', $quick)") as String
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override fun action(action: String): String {
        return try {
            quickJs?.evaluate("action('$action')") as String
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override fun destroy() {
        try {
            quickJs?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        quickJs = null
    }

    // Interfaces
    interface ILocal {
        fun get(key: String): String
        fun set(key: String, value: String)
        fun delete(key: String)
    }

    interface IReq {
        fun request(url: String, options: String): String
        fun log(msg: String)
    }

    // Implementations
    class LocalStorage(context: Context) : ILocal {
        private val prefs: SharedPreferences = context.getSharedPreferences("spider_local", Context.MODE_PRIVATE)

        override fun get(key: String): String {
            return prefs.getString(key, "") ?: ""
        }

        override fun set(key: String, value: String) {
            prefs.edit().putString(key, value).apply()
        }

        override fun delete(key: String) {
            prefs.edit().remove(key).apply()
        }
    }

    class SpiderReq(private val client: OkHttpClient) : IReq {
        override fun request(url: String, options: String): String {
            try {
                val opt = JSONObject(options)
                val method = opt.optString("method", "GET")
                val headers = opt.optJSONObject("headers")
                val body = opt.optString("body")

                val builder = Request.Builder().url(url)
                
                if (headers != null) {
                    val keys = headers.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        builder.addHeader(key, headers.optString(key))
                    }
                }

                if (method.equals("POST", ignoreCase = true)) {
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    builder.post(body.toRequestBody(mediaType))
                } else {
                    builder.get()
                }

                val response = client.newCall(builder.build()).execute()
                val content = response.body?.string() ?: ""
                
                // 返回结构通常包含 content, headers, status 等
                // 这里简单返回 content，视具体 JS 爬虫需求而定
                // 很多 TVBox 爬虫直接返回 content 字符串，或者返回包含 content 的对象
                // 为了兼容性，我们尝试返回 content 字符串。
                // 如果需要更复杂对象，需要调整。
                return content
            } catch (e: Exception) {
                e.printStackTrace()
                return ""
            }
        }

        override fun log(msg: String) {
            android.util.Log.d("JsSpider", msg)
        }
    }
}
