package com.leafstudio.tvplayer.spider

import com.leafstudio.tvplayer.model.Category
import com.leafstudio.tvplayer.model.Vod

interface SiteClient {
    fun getCategories(callback: (List<Category>) -> Unit, error: (String) -> Unit)
    fun getCategoryContent(category: Category, page: Int, callback: (List<Vod>) -> Unit, error: (String) -> Unit)
    fun getDetail(vodId: String, callback: (Vod) -> Unit, error: (String) -> Unit)
    fun getPlayUrl(flag: String, id: String, callback: (String) -> Unit, error: (String) -> Unit)
    fun search(keyword: String, callback: (List<Vod>) -> Unit, error: (String) -> Unit)
}
