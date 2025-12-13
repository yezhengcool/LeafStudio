package com.leafstudio.tvplayer.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TvBoxConfig(
    val spider: String? = null,
    val wallpaper: String? = null,
    val sites: List<Site> = emptyList(),
    val lives: List<Live> = emptyList()
) : Parcelable

@Parcelize
data class Site(
    val key: String,
    val name: String,
    val type: Int, // 0: xml/json, 1: json, 3: spider, 4: spider_py
    val api: String,
    val searchable: Int = 0,
    val quickSearch: Int = 0,
    val filterable: Int = 0,
    val ext: String? = null,
    val jar: String? = null,
    val playUrl: String? = null
) : Parcelable

@Parcelize
data class Live(
    val name: String,
    val type: Int,
    val url: String,
    val playerType: Int = 1,
    val ua: String? = null
) : Parcelable

@Parcelize
data class Vod(
    val vod_id: String,
    val vod_name: String,
    val vod_pic: String,
    val vod_remarks: String,
    val vod_year: String? = null,
    val vod_area: String? = null,
    val vod_director: String? = null,
    val vod_actor: String? = null,
    val vod_content: String? = null,
    val vod_play_from: String? = null,
    val vod_play_url: String? = null,
    val vod_tag: String? = null
) : Parcelable

@Parcelize
data class Category(
    val type_id: String,
    val type_name: String
) : Parcelable
