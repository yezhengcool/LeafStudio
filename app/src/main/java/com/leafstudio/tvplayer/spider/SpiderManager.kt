package com.leafstudio.tvplayer.spider

import android.content.Context
import dalvik.system.DexClassLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.nio.ByteBuffer
import java.util.zip.ZipFile
import com.leafstudio.tvplayer.spider.Spider as LocalSpider // 别名以区分

/**
 * Spider 管理器
 * 负责管理 ClassLoader 和 Spider 实例的缓存
 */
object SpiderManager {
    var currentSpider: LocalSpider? = null
    private val executor = Executors.newSingleThreadExecutor()
    
    // ClassLoader 缓存 - key 是 JAR URL 的 MD5
    private val loaders = ConcurrentHashMap<String, ClassLoader>()
    
    // Spider 实例缓存 - key 是 jarKey + siteKey
    private val spiders = ConcurrentHashMap<String, LocalSpider>()
    
    private val client = OkHttpClient.Builder().build()
    
    fun <T> submit(task: () -> T) = executor.submit(task)
    
    fun execute(task: () -> Unit) = executor.execute(task)
    
    fun clear() {
        spiders.values.forEach { it.destroy() }
        loaders.clear()
        spiders.clear()
    }
    
    fun getJarSpider(context: Context, siteKey: String, className: String, ext: String, jarUrl: String): LocalSpider {
        val jarKey = jarUrl.hashCode().toString()
        val spiderKey = jarKey + siteKey
        
        try {
            // 1. 尝试使用现有缓存
            return getSpiderInternal(context, jarKey, spiderKey, className, ext, jarUrl)
        } catch (e: Throwable) {
            // 2. 如果失败且缓存存在，清除缓存重试
            if (loaders.containsKey(jarKey)) {
                android.util.Log.w("SpiderManager", "首次加载失败，清除缓存并重试: $className")
                loaders.remove(jarKey)
                spiders.remove(spiderKey)
                
                try {
                    return getSpiderInternal(context, jarKey, spiderKey, className, ext, jarUrl)
                } catch (retryE: Throwable) {
                    android.util.Log.e("SpiderManager", "重试依然失败: $className", retryE)
                    throw retryE
                }
            }
            throw e
        }
    }

    private fun getSpiderInternal(context: Context, jarKey: String, spiderKey: String, className: String, ext: String, jarUrl: String): LocalSpider {
        // 检查实例缓存
        spiders[spiderKey]?.let { 
            android.util.Log.d("SpiderManager", "使用缓存的 Spider 实例: $className")
            return it 
        }
        
        android.util.Log.d("SpiderManager", "开始创建新的 Spider 实例: $className")
        android.util.Log.d("SpiderManager", "ext 参数: $ext")
        
        // 获取 Loader (可能会触发新的加载)
        val loader = getClassLoader(context, jarKey, jarUrl)
        
        // 加载类
        val spiderClass = loadSpiderClass(loader, className)
        android.util.Log.d("SpiderManager", "成功加载类: ${spiderClass.name}")
        
        // 实例化
        val instance = spiderClass.newInstance()
        android.util.Log.d("SpiderManager", "成功实例化 Spider")
        
        // 适配: 将 com.github.catvod.crawler.Spider 转换为 com.leafstudio.tvplayer.spider.Spider
        if (instance is com.github.catvod.crawler.Spider) {
            val spider = JarSpiderAdapter(context, instance)
            android.util.Log.d("SpiderManager", "开始初始化 Spider，ext: $ext")
            spider.init(ext)
            android.util.Log.d("SpiderManager", "Spider 初始化完成")
            
            // 缓存
            spiders[spiderKey] = spider
            android.util.Log.d("SpiderManager", "✅ Spider 创建并适配成功: ${spiderClass.name}")
            return spider
        } else {
            throw ClassCastException("加载的类不是 com.github.catvod.crawler.Spider 的子类: ${instance.javaClass.name}")
        }
    }
    
    private fun getClassLoader(context: Context, jarKey: String, jarUrl: String): ClassLoader {
        if (loaders.containsKey(jarKey)) return loaders[jarKey]!!
        
        synchronized(loaders) {
            if (loaders.containsKey(jarKey)) return loaders[jarKey]!!
            
            android.util.Log.d("SpiderManager", "开始加载 JAR: $jarUrl")
            val jarFile = downloadJar(context, jarUrl)
            
            var loader: ClassLoader? = null
            
            // 1. 优先尝试内存加载 (Android 8.0+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                loader = loadDexInMemory(context, jarFile)
            }
            
            // 2. 如果内存加载不可用失败，回退到标准 DexClassLoader
            if (loader == null) {
                android.util.Log.d("SpiderManager", "内存加载不可用或失败，使用标准 DexClassLoader")
                val optimizedDir = context.getDir("dex_opt", Context.MODE_PRIVATE)
                val jarDir = jarFile.parentFile ?: context.cacheDir
                
                loader = DexClassLoader(
                    jarFile.absolutePath,
                    optimizedDir.absolutePath,
                    jarDir.absolutePath,
                    context.classLoader
                )
            }
            
            // 3. 尝试初始化 Init
            try {
                val initClass = loader!!.loadClass("com.github.catvod.spider.Init")
                val initMethod = initClass.getMethod("init", Context::class.java)
                initMethod.invoke(null, context)
            } catch (e: Exception) {
                // Init 类可能不存在，非致命错误
            }

            loaders[jarKey] = loader!!
            return loader!!
        }
    }
    
    private fun loadDexInMemory(context: Context, jarFile: File): ClassLoader? {
        try {
            val dexBuffers = mutableListOf<ByteBuffer>()
            ZipFile(jarFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".dex")) {
                        android.util.Log.d("SpiderManager", "读取内存 DEX: ${entry.name}")
                        zip.getInputStream(entry).use { input ->
                            val bytes = input.readBytes()
                            dexBuffers.add(ByteBuffer.wrap(bytes))
                        }
                    }
                }
            }
            
            if (dexBuffers.isNotEmpty()) {
                android.util.Log.d("SpiderManager", "✅ InMemoryDexClassLoader 初始化成功，共 ${dexBuffers.size} 个 DEX")
                return dalvik.system.InMemoryDexClassLoader(
                    dexBuffers.toTypedArray(),
                    context.classLoader
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SpiderManager", "InMemoryDexClassLoader 失败", e)
        }
        return null
    }

    private fun loadSpiderClass(loader: ClassLoader, className: String): Class<*> {
        val candidates = mutableListOf<String>()
        if (className.startsWith("csp_")) {
            val name = className.substring(4)
            candidates.add("com.github.catvod.spider.$name")
            candidates.add("com.github.tvbox.osc.spider.$name")
        }
        candidates.add("com.github.catvod.spider.$className")
        candidates.add(className)
        
        for (candidate in candidates) {
            try {
                return loader.loadClass(candidate)
            } catch (e: ClassNotFoundException) { }
        }
        
        throw ClassNotFoundException("无法在 JAR 中找到类: $className (已尝试: $candidates)")
    }
    
    private fun downloadJar(context: Context, url: String): File {
        val jarDir = File(context.cacheDir, "jars")
        if (!jarDir.exists()) jarDir.mkdirs()
        
        // 使用原始 URL 生成文件名，这样可以区分不同 MD5 的版本
        val fileName = url.hashCode().toString() + ".jar"
        val jarFile = File(jarDir, fileName)
        
        if (jarFile.exists() && jarFile.length() > 0) {
            android.util.Log.d("SpiderManager", "使用缓存 JAR: ${jarFile.absolutePath} (${jarFile.length()} bytes)")
            return jarFile
        }
        
        // 处理 URL: 去除 ;md5 等后缀
        val realUrl = if (url.contains(";")) url.split(";")[0] else url
        
        android.util.Log.d("SpiderManager", "开始下载 JAR: $realUrl")
        val request = Request.Builder()
            .url(realUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            android.util.Log.e("SpiderManager", "下载失败: HTTP ${response.code}")
            throw Exception("下载失败: ${response.code}")
        }
        
        val bytes = response.body?.bytes() ?: throw Exception("JAR 内容为空")
        android.util.Log.d("SpiderManager", "下载完成: ${bytes.size} bytes")
        
        // 宽松验证：只检查大小，不检查 ZIP header（因为有些 JAR 伪装成图片）
        if (bytes.size < 100) {
            android.util.Log.e("SpiderManager", "文件太小，可能不是有效的 JAR: ${bytes.size} bytes")
            throw Exception("下载的文件太小，可能无效")
        }
        
        jarFile.writeBytes(bytes)
        
        try {
            jarFile.setWritable(true, true)
            jarFile.setReadable(true, false)
        } catch (e: Exception) { 
            android.util.Log.w("SpiderManager", "设置文件权限失败", e)
        }
        
        android.util.Log.d("SpiderManager", "JAR 保存成功: ${jarFile.absolutePath}")
        return jarFile
    }

    /**
     * 适配器类：将 JAR Spider (Abstract Class) 适配为 Local Spider (Interface)
     */
    class JarSpiderAdapter(private val context: Context, private val jarSpider: com.github.catvod.crawler.Spider) : LocalSpider {
        
        override fun init(ext: String) {
            try {
                android.util.Log.d("SpiderManager", "JarSpiderAdapter.init() 被调用，ext: $ext")
                jarSpider.init(context.applicationContext, ext)
                android.util.Log.d("SpiderManager", "JarSpider.init() 执行成功")
            } catch (e: Throwable) {
                android.util.Log.e("SpiderManager", "JarSpider init 失败", e)
                throw e
            }
        }

        override fun home(filter: Boolean): String {
            return try { 
                android.util.Log.d("SpiderManager", "调用 JarSpider.homeContent(filter=$filter)")
                val res = jarSpider.homeContent(filter)
                android.util.Log.d("SpiderManager", "home result length: ${res.length}, first 200 chars: ${res.take(200)}")
                res
            } catch (e: NullPointerException) {
                android.util.Log.e("SpiderManager", "JarSpider home NPE - 可能是公告类 Spider 需要特殊处理", e)
                ""
            } catch (e: Throwable) { 
                android.util.Log.e("SpiderManager", "JarSpider home 失败", e)
                "" 
            }
        }

        override fun category(tid: String, pg: String, filter: Boolean, extend: String): String {
            return try { 
                val map = java.util.HashMap<String, String>()
                val res = jarSpider.categoryContent(tid, pg, filter, map) 
                android.util.Log.d("SpiderManager", "category result (first 200 chars): ${res.take(200)}")
                res
            } catch (e: NullPointerException) {
                android.util.Log.e("SpiderManager", "JarSpider category NPE - 可能是公告类 Spider 需要特殊处理", e)
                ""
            } catch (e: Throwable) { 
                android.util.Log.e("SpiderManager", "JarSpider category failed", e)
                "" 
            }
        }

        override fun detail(ids: String): String {
            return try { 
                val idList = ids.split(",").filter { it.isNotEmpty() }
                val res = jarSpider.detailContent(idList) 
                android.util.Log.d("SpiderManager", "detail result: $res")
                res
            } catch (e: Throwable) { 
                android.util.Log.e("SpiderManager", "JarSpider detail failed", e)
                "" 
            }
        }

        override fun play(flag: String, id: String, flags: List<String>): String {
            return try { jarSpider.playerContent(flag, id, flags) } catch (e: Throwable) { 
                android.util.Log.e("SpiderManager", "JarSpider play failed", e)
                "" 
            }
        }

        override fun search(wd: String, quick: Boolean): String {
            return try { 
                val res = jarSpider.searchContent(wd, quick)
                android.util.Log.d("SpiderManager", "search result (first 200 chars): ${res.take(200)}")
                res
            } catch (e: Throwable) { 
                android.util.Log.e("SpiderManager", "JarSpider search failed", e)
                "" 
            }
        }

        override fun action(action: String): String {
            return try {
                jarSpider.action(action) ?: ""
            } catch (e: Throwable) {
                android.util.Log.e("SpiderManager", "JarSpider action failed", e)
                ""
            }
        }

        override fun destroy() {
            try { jarSpider.destroy() } catch (e: Exception) { }
        }
    }
}
