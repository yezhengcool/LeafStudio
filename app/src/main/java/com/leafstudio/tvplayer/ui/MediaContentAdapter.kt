package com.leafstudio.tvplayer.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.leafstudio.tvplayer.R
import com.leafstudio.tvplayer.utils.loadUrl
import com.leafstudio.tvplayer.model.Vod

class MediaContentAdapter(
    private var items: List<Vod>,
    private val onContentClick: (Vod) -> Unit
) : RecyclerView.Adapter<MediaContentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val posterImage: ImageView = view.findViewById(R.id.iv_poster)
        val titleText: TextView = view.findViewById(R.id.tv_title)
        val remarksText: TextView = view.findViewById(R.id.tv_remarks)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_content, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleText.text = item.vod_name
        holder.remarksText.text = item.vod_remarks
        
        holder.posterImage.loadUrl(item.vod_pic)
            
        // 解析 vod_tag 获取源名称 (格式: "SiteName@@@PlayUrl")
        val tag = item.vod_tag
        if (!tag.isNullOrEmpty() && tag.contains("@@@")) {
            val siteName = tag.split("@@@")[0]
            val tvSite = holder.itemView.findViewById<TextView>(R.id.tv_site)
            if (tvSite != null) {
                tvSite.text = siteName
                tvSite.visibility = View.VISIBLE
            }
        } else {
             // 如果不是搜索结果或没有源信息，隐藏标签
             val tvSite = holder.itemView.findViewById<TextView>(R.id.tv_site)
             tvSite?.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onContentClick(item)
        }
    }

    override fun getItemCount() = items.size
    
    fun updateContent(newItems: List<Vod>) {
        items = newItems
        notifyDataSetChanged()
    }
}
