package com.rakor.tangshi.data

import com.rakor.tangshi.data.model.SearchIndex
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * search_index.json 解析：条目读取 + 缺字段默认（本类覆盖范围）。
 * 注意：真正的降级路径（文件缺失/坏文件/空表 → PoemRepository 捕获 + Log.w）不在本类
 * 覆盖——由 Task 3 集成测试读真实资产间接兜底，勿误以为已被此处测试保护。
 */
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

    @Test fun `条目缺字段降级空串`() {
        val idx = json.decodeFromString<SearchIndex>("""{"poets":{"李白":{"full":"libai"}}}""")
        assertEquals("", idx.poets["李白"]?.init)
    }
}
