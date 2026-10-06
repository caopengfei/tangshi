package com.rakor.tangshi.data.repo

import android.content.Context
import com.rakor.tangshi.data.model.Poem
import kotlinx.serialization.json.Json

class PoemRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val poems: List<Poem> by lazy {
        context.assets.open("content/poems.json").bufferedReader().use { r ->
            json.decodeFromString<List<Poem>>(r.readText())
        }.sortedBy { it.order }
    }
    fun loadAll(): List<Poem> = poems
    fun byId(id: String): Poem? = poems.firstOrNull { it.id == id }
}
