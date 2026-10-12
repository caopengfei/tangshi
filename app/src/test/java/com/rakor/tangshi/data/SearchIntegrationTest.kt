package com.rakor.tangshi.data

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.model.SearchIndex
import com.rakor.tangshi.data.search.SearchMatcher
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * 真实资产回归：poems.json + search_index.json 全量过匹配器，直测规格示例。
 * 计数锚定当前 325 首语料——poems.json 变更后必须重跑 build_search_index.py，
 * 此处红了即提醒。单测工作目录在 app/ 或仓库根，双路径兜底。
 */
class SearchIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun assetText(rel: String): String =
        listOf(File("src/main/assets/$rel"), File("app/src/main/assets/$rel"))
            .first { it.exists() }.readText()

    private val poems: List<Poem> = json.decodeFromString(assetText("content/poems.json"))
    private val index: SearchIndex = json.decodeFromString(assetText("content/search_index.json"))
    private val keys = poems.associate { it.id to SearchMatcher.buildKeys(it, index) }

    private fun hits(keyword: String): List<String> =
        poems.filter { SearchMatcher.matches(it, keyword, keys.getValue(it.id)) }.map { it.id }

    @Test fun `LB 命中李白全部诗且共 82 首`() {
        val h = hits("LB")
        assertEquals(82, h.size)
        val libai = poems.filter { it.poet == "李白" }.map { it.id }.toSet()
        assertTrue(libai.all { it in h })
    }

    @Test fun `CQMYG 只命中静夜思`() = assertEquals(listOf("jing-ye-si"), hits("CQMYG"))

    @Test fun `DENGGUANQUELOU 命中登鹳雀楼`() = assertEquals(listOf("deng-que-lou"), hits("DENGGUANQUELOU"))

    @Test fun `全拼通道保留 chuangqian 命中静夜思`() = assertTrue("jing-ye-si" in hits("chuangqian"))

    @Test fun `V 形与 U 形全拼等价命中洛阳女儿行`() {
        val v = hits("LUOYANGNVERXING")
        assertEquals(hits("LUOYANGNUERXING"), v)
        assertTrue(v.isNotEmpty())
    }

    /** 索引覆盖闭环：poems.json 每 id 每诗人都有索引条目（防改诗后忘重跑工具） */
    @Test fun `索引完整覆盖诗库`() {
        assertEquals(poems.map { it.id }.toSet(), index.titles.keys)
        assertEquals(poems.map { it.poet }.toSet(), index.poets.keys)
    }
}
