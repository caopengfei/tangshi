package com.rakor.tangshi.data.repo

import android.content.Context
import android.util.Log
import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.model.SearchIndex
import kotlinx.serialization.json.Json

class PoemRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val poems: List<Poem> by lazy {
        context.assets.open("content/poems.json").bufferedReader().use { r ->
            json.decodeFromString<List<Poem>>(r.readText())
        }.sortedBy { it.order }
    }
    /**
     * 搜索拼音索引；缺文件/坏文件/空表降级为双空表并留 logcat 痕迹
     * （诗句键运行时可算，搜索不崩；catch Exception 不吞 Error——OOM 等仍大声失败）。
     * 空表守卫：生成器保证双表恒非空，解码成功却为空 = schema 漂移，同样要留痕。
     */
    private val searchIndex: SearchIndex by lazy {
        try {
            val idx = context.assets.open("content/search_index.json").bufferedReader().use { r ->
                json.decodeFromString<SearchIndex>(r.readText())
            }
            require(idx.poets.isNotEmpty() && idx.titles.isNotEmpty()) {
                "search_index.json 解析为空（schema 漂移？）"
            }
            idx
        } catch (e: Exception) {
            Log.w("PoemRepository", "search_index.json 加载失败，降级空表", e)
            SearchIndex()
        }
    }
    fun loadSearchIndex(): SearchIndex = searchIndex
    fun loadAll(): List<Poem> = poems
    fun byId(id: String): Poem? = poems.firstOrNull { it.id == id }
}
