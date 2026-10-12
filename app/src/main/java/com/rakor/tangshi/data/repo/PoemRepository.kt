package com.rakor.tangshi.data.repo

import android.content.Context
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
    /** 搜索拼音索引；缺文件/坏文件降级空表（诗句键运行时可算，搜索不崩） */
    private val searchIndex: SearchIndex by lazy {
        runCatching {
            context.assets.open("content/search_index.json").bufferedReader().use { r ->
                json.decodeFromString<SearchIndex>(r.readText())
            }
        }.getOrDefault(SearchIndex())
    }
    fun loadSearchIndex(): SearchIndex = searchIndex
    fun loadAll(): List<Poem> = poems
    fun byId(id: String): Poem? = poems.firstOrNull { it.id == id }
}
