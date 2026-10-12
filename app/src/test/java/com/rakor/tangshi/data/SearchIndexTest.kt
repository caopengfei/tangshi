package com.rakor.tangshi.data

import com.rakor.tangshi.data.model.SearchIndex
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** search_index.json 解析：条目读取 + 缺字段默认双空表（降级路径） */
class SearchIndexTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `解析诗人与标题条目`() {
        val idx = json.decodeFromString<SearchIndex>(
            """{"poets":{"李白":{"full":"libai","init":"lb"}},
               "titles":{"jing-ye-si":{"full":"jingyesi","init":"jys"}}}"""
        )
        assertEquals("lb", idx.poets["李白"]?.init)
        assertEquals("jingyesi", idx.titles["jing-ye-si"]?.full)
    }

    @Test fun `空对象解析为双空表`() {
        val idx = json.decodeFromString<SearchIndex>("{}")
        assertTrue(idx.poets.isEmpty())
        assertTrue(idx.titles.isEmpty())
    }
}
