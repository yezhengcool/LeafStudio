package com.leafstudio.tvplayer.utils

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.leafstudio.tvplayer.R

fun ImageView.loadUrl(url: String?) {
    if (url.isNullOrEmpty()) {
        this.setImageDrawable(null)
        return
    }

    // Handle headers format: url@Key=Value@Key=Value
    // Example: http://example.com/img.jpg@Referer=http://exp.com@User-Agent=Mozilla
    if (url.contains("@")) {
        val parts = url.split("@")
        val realUrl = parts[0]
        val headersBuilder = LazyHeaders.Builder()
        
        for (i in 1 until parts.size) {
            val headerPart = parts[i]
            val split = headerPart.split("=", limit = 2)
            if (split.size == 2) {
                headersBuilder.addHeader(split[0], split[1])
            }
        }
        
        val glideUrl = GlideUrl(realUrl, headersBuilder.build())
        Glide.with(context)
            .load(glideUrl)
            // .placeholder(android.R.drawable.ic_menu_gallery) // 移除占位图
            // .error(android.R.drawable.stat_notify_error) // 移除错误图
            .into(this)
    } else {
        Glide.with(context)
            .load(url)
            // .placeholder(android.R.drawable.ic_menu_gallery)
            // .error(android.R.drawable.stat_notify_error)
            .into(this)
    }
}
