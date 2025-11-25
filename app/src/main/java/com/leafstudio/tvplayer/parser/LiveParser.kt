package com.leafstudio.tvplayer.parser

import com.leafstudio.tvplayer.model.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern
import java.util.regex.Matcher

class LiveParser {

    companion object {
        private val M3U = Pattern.compile("^(?!.*#genre#).*#EXT(?:M3U|INF).*", Pattern.MULTILINE)
        private val HTTP_USER_AGENT = Pattern.compile(".*http-user-agent=\"(.?|.+?)\".*")
        private val CATCHUP_REPLACE = Pattern.compile(".*catchup-replace=\"(.?|.+?)\".*")
        private val CATCHUP_SOURCE = Pattern.compile(".*catchup-source=\"(.?|.+?)\".*")
        private val CATCHUP = Pattern.compile(".*catchup=\"(.?|.+?)\".*")
        private val TVG_CHNO = Pattern.compile(".*tvg-chno=\"(.?|.+?)\".*")
        private val TVG_LOGO = Pattern.compile(".*tvg-logo=\"(.?|.+?)\".*")
        private val TVG_NAME = Pattern.compile(".*tvg-name=\"(.?|.+?)\".*")
        private val TVG_URL = Pattern.compile(".*tvg-url=\"(.?|.+?)\".*")
        private val TVG_ID = Pattern.compile(".*tvg-id=\"(.?|.+?)\".*")
        private val URL_TVG = Pattern.compile(".*url-tvg=\"(.?|.+?)\".*")
        private val GROUP = Pattern.compile(".*group-title=\"(.?|.+?)\".*")
        private val NAME = Pattern.compile(".*,(.+?)$")

        private fun extract(line: String, pattern: Pattern): String {
            val matcher = pattern.matcher(line.trim())
            if (matcher.matches()) return matcher.group(1).trim()
            return ""
        }

        private fun extract(line: String, vararg keywords: String): String {
            val splits = line.split(" ")
            for (split in splits) {
                for (keyword in keywords) {
                    if (split.contains(keyword)) {
                        return split.split("=")[1].replace("\"", "")
                    }
                }
            }
            return ""
        }
    }

    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        if (text.trim().startsWith("[") && text.trim().endsWith("]")) {
             json(text, channels)
        } else {
             text(text, channels)
        }
        
        // 为所有频道分配连续的编号（跨地区统一编号）
        channels.forEachIndexed { index, channel ->
            // 频道编号从1开始
            channel.number = index + 1
        }
        
        return channels
    }

    private fun text(text: String, channels: MutableList<Channel>) {
        if (M3U.matcher(text).find()) m3u(text, channels)
        else txt(text, channels)
    }

    private fun json(text: String, channels: MutableList<Channel>) {
        try {
            val jsonArray = JSONArray(text)
            for (i in 0 until jsonArray.length()) {
                val groupObj = jsonArray.getJSONObject(i)
                val groupName = groupObj.optString("name")
                val channelArray = groupObj.optJSONArray("urls")
                if (channelArray != null) {
                    for (j in 0 until channelArray.length()) {
                        val channelObj = channelArray.getJSONObject(j)
                        val name = channelObj.optString("name")
                        val url = channelObj.optString("url")
                        val logo = channelObj.optString("icon")
                        
                        // Find or create channel
                        var channel = channels.find { it.name == name && it.group == groupName }
                        if (channel == null) {
                            channel = Channel(
                                id = name.hashCode().toString(),
                                name = name,
                                urls = mutableListOf(),
                                logo = logo,
                                group = groupName
                            )
                            channels.add(channel)
                        }
                        (channel.urls as MutableList).add(url)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun m3u(text: String, channels: MutableList<Channel>) {
        val setting = Setting()
        var currentChannel: Channel? = null
        val lines = text.replace("\r\n", "\n").replace("\r", "").split("\n")
        
        for (line in lines) {
            if (setting.find(line)) {
                setting.check(line)
            } else if (line.startsWith("#EXTM3U")) {
                // Global settings, maybe extract EPG here if needed
            } else if (line.startsWith("#EXTINF:")) {
                val groupName = extract(line, GROUP)
                val name = extract(line, NAME)
                val logo = extract(line, TVG_LOGO)
                
                // Find existing channel or create new one
                // Note: Original logic merges by name in same group
                var channel = channels.find { it.name == name && it.group == groupName }
                if (channel == null) {
                    channel = Channel(
                        id = name.hashCode().toString(),
                        name = name,
                        urls = mutableListOf(), // Mutable list for adding URLs
                        logo = logo.ifEmpty { null },
                        group = groupName.ifEmpty { null }
                    )
                    channels.add(channel)
                }
                
                channel.ua = extract(line, HTTP_USER_AGENT).ifEmpty { null }
                // Other fields like tvg-id, tvg-name can be added if Channel supports them
                
                currentChannel = channel
            } else if (!line.startsWith("#") && line.contains("://")) {
                val parts = line.split("|", limit = 2)
                if (parts.size > 1) setting.headers(parts[1])
                
                currentChannel?.let { ch ->
                    (ch.urls as MutableList).add(parts[0])
                    setting.copy(ch).clear()
                }
            }
        }
    }

    private fun txt(text: String, channels: MutableList<Channel>) {
        val setting = Setting()
        val lines = text.replace("\r\n", "\n").replace("\r", "").split("\n")
        
        for (line in lines) {
            val split = line.split(",", limit = 2)
            if (setting.find(line)) setting.check(line)
            if (line.contains("#genre#")) setting.clear()
            
            if (line.contains("#genre#")) {
                // Genre line, usually defines a group start in some formats, 
                // but in this logic it seems to just clear settings.
                // The original code: live.getGroups().add(Group.create(split[0], live.isPass()));
                // It seems it creates a new group.
                // But for TXT format: "GroupName,#genre#"
            }
            
            // Logic for TXT format: "Name,URL" or "GroupName,#genre#"
            // The original code seems to handle "GroupName,#genre#" by adding a group.
            // And then subsequent lines "Name,URL" are added to the last group.
            
            if (split.size > 1 && split[1].contains("://")) {
                // It's a channel line: "Name,URL"
                // We need to know the current group.
                // In the original code, it gets the last group: live.getGroups().get(live.getGroups().size() - 1)
                
                // We need to track current group name
                var groupName: String? = null
                // Find the last group name from the channels list or track it separately?
                // Better track it separately.
                // But wait, the loop iterates lines.
            }
        }
        
        // Re-implementing TXT logic properly
        var currentGroupName: String? = null
        
        for (line in lines) {
            val split = line.split(",", limit = 2)
            if (setting.find(line)) setting.check(line)
            if (line.contains("#genre#")) {
                setting.clear()
                currentGroupName = split[0]
                continue
            }
            
            if (split.size > 1 && split[1].contains("://")) {
                val name = split[0]
                val urlPart = split[1]
                
                var channel = channels.find { it.name == name && it.group == currentGroupName }
                if (channel == null) {
                    channel = Channel(
                        id = name.hashCode().toString(),
                        name = name,
                        urls = mutableListOf(),
                        logo = null,
                        group = currentGroupName
                    )
                    channels.add(channel)
                }
                
                for (url in urlPart.split("#")) {
                    val parts = url.split("|", limit = 2)
                    if (parts.size > 1) setting.headers(parts[1])
                    (channel.urls as MutableList).add(parts[0])
                    setting.copy(channel)
                }
            }
        }
    }

    private class Setting {
        var ua: String? = null
        var key: String? = null
        var type: String? = null
        var click: String? = null
        var format: String? = null
        var origin: String? = null
        var referer: String? = null
        var parse: Int? = null
        var header: MutableMap<String, String>? = null

        fun find(line: String): Boolean {
            return line.startsWith("ua") || line.startsWith("parse") || line.startsWith("click") || 
                   line.startsWith("header") || line.startsWith("format") || line.startsWith("origin") || 
                   line.startsWith("referer") || line.startsWith("#EXTHTTP:") || line.startsWith("#EXTVLCOPT:") || 
                   line.startsWith("#KODIPROP:")
        }

        fun check(line: String) {
            when {
                line.startsWith("ua") -> ua(line)
                line.startsWith("parse") -> parse(line)
                line.startsWith("click") -> click(line)
                line.startsWith("header") -> header(line)
                line.startsWith("format") -> format(line)
                line.startsWith("origin") -> origin(line)
                line.startsWith("referer") -> referer(line)
                line.startsWith("#EXTHTTP:") -> header(line)
                line.startsWith("#EXTVLCOPT:http-origin") -> origin(line)
                line.startsWith("#EXTVLCOPT:http-user-agent") -> ua(line)
                line.startsWith("#EXTVLCOPT:http-referrer") -> referrer(line)
                line.startsWith("#KODIPROP:inputstream.adaptive.license_key") -> key(line)
                line.startsWith("#KODIPROP:inputstream.adaptive.license_type") -> type(line)
                line.startsWith("#KODIPROP:inputstream.adaptive.drm_legacy") -> drmLegacy(line)
                line.startsWith("#KODIPROP:inputstream.adaptive.manifest_type") -> format(line)
                line.startsWith("#KODIPROP:inputstream.adaptive.stream_headers") -> headers(line)
                line.startsWith("#KODIPROP:inputstream.adaptive.common_headers") -> headers(line)
            }
        }

        fun copy(channel: Channel): Setting {
            if (ua != null) channel.ua = ua
            // if (parse != null) channel.setParse(parse) // Channel doesn't have parse
            // if (click != null) channel.setClick(click) // Channel doesn't have click
            // if (format != null) channel.setFormat(format) // Channel doesn't have format
            // if (origin != null) channel.setOrigin(origin) // Channel doesn't have origin
            // if (referer != null) channel.setReferer(referer) // Channel doesn't have referer
            
            if (header != null) {
                channel.headers = HashMap(header)
            }
            
            if (key != null && type != null) {
                channel.drmKey = key
                channel.drmType = type
            }
            return this
        }
        
        fun clear() {
            ua = null
            key = null
            type = null
            parse = null
            click = null
            header = null
            format = null
            origin = null
            referer = null
        }

        private fun ua(line: String) {
            try {
                if (line.contains("user-agent=")) ua = line.split("(?i)user-agent=".toRegex())[1].trim().replace("\"", "")
                if (line.contains("ua=")) ua = line.split("ua=")[1].trim().replace("\"", "")
            } catch (e: Exception) {
                ua = null
            }
        }

        private fun referer(line: String) {
            try {
                referer = line.split("(?i)referer=".toRegex())[1].trim().replace("\"", "")
            } catch (e: Exception) {
                referer = null
            }
        }

        private fun referrer(line: String) {
            try {
                referer = line.split("(?i)referrer=".toRegex())[1].trim().replace("\"", "")
            } catch (e: Exception) {
                referer = null
            }
        }

        private fun parse(line: String) {
            try {
                parse = line.split("parse=")[1].trim().toInt()
            } catch (e: Exception) {
                parse = null
            }
        }

        private fun click(line: String) {
            try {
                click = line.split("click=")[1].trim()
            } catch (e: Exception) {
                click = null
            }
        }

        private fun format(line: String) {
            try {
                if (line.startsWith("format=")) format = line.split("format=")[1].trim()
                if (line.contains("manifest_type=")) format = line.split("manifest_type=")[1].trim()
            } catch (e: Exception) {
                format = null
            }
        }

        private fun origin(line: String) {
            try {
                origin = line.split("(?i)origin=".toRegex())[1].trim()
            } catch (e: Exception) {
                origin = null
            }
        }

        private fun key(line: String) {
            try {
                key = if (line.contains("license_key=")) line.split("license_key=")[1].trim() else line
                // if (!key.startsWith("http")) convert(); // Skip convert for now as we don't have ClearKey
            } catch (e: Exception) {
                key = null
            }
        }

        private fun type(line: String) {
            try {
                type = if (line.contains("license_type=")) line.split("license_type=")[1].trim() else line
            } catch (e: Exception) {
                type = null
            }
        }

        private fun drmLegacy(line: String) {
            try {
                val parts = line.split("drm_legacy=")[1].trim().split("|")
                type(parts[0].trim())
                key(parts[1].trim())
            } catch (e: Exception) {
                type = null
                key = null
            }
        }

        private fun header(line: String) {
            try {
                if (line.contains("#EXTHTTP:")) {
                    val jsonStr = line.split("#EXTHTTP:")[1].trim()
                    val jsonObj = JSONObject(jsonStr)
                    val map = mutableMapOf<String, String>()
                    jsonObj.keys().forEach { key -> map[key] = jsonObj.getString(key) }
                    header = map
                }
                if (line.contains("header=")) {
                    val jsonStr = line.split("header=")[1].trim()
                    val jsonObj = JSONObject(jsonStr)
                    val map = mutableMapOf<String, String>()
                    jsonObj.keys().forEach { key -> map[key] = jsonObj.getString(key) }
                    header = map
                }
            } catch (e: Exception) {
                header = null
            }
        }

        fun headers(line: String) {
            try {
                if (line.contains("headers=")) headers(line.split("headers=")[1].trim().split("&").toTypedArray())
                else if (line.contains("|")) {
                    for (text in line.split("|")) headers(text)
                } else headers(line.trim().split("&").toTypedArray())
            } catch (ignored: Exception) {
            }
        }

        private fun headers(params: Array<String>) {
            if (header == null) header = HashMap()
            for (param in params) {
                if (!param.contains("=")) continue
                val a = param.split("=", limit = 2)
                val k = a[0].trim().replace("\"", "")
                val v = a[1].trim().replace("\"", "")
                if ("drmScheme" == k) type(v)
                else if ("drmLicense" == k) key(v)
                else header!![k] = v
            }
        }
    }
}
