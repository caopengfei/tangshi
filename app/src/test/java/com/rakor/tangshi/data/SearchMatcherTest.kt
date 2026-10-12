package com.rakor.tangshi.data

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.model.SearchIndex
import com.rakor.tangshi.data.search.SearchMatcher
import org.junit.Assert.*
import org.junit.Test

/**
 * 搜索匹配纯函数（2026-10-12 改版）：
 * - 汉字命中 = 标题/诗句/诗人名 contains；
 * - 拼音命中 = buildKeys 合并键 contains（诗句全拼+首字母 / 标题全拼 / 标题首字母 /
 *   诗人全拼 / 诗人首字母），首字母（LB/CQMYG）与全拼（chuangqian）双通道；
 * - 删诗人过滤；关键字为空 = 全量命中；索引缺键 = 降级（诗句键仍工作）。
 */
class SearchMatcherTest {
    private val poems = TestFixtures.poems()
    private val index = TestFixtures.searchIndex()
    private fun keys(p: Poem) = SearchMatcher.buildKeys(p, index)

    @Test fun `关键字命中汉字`() = assertTrue(SearchMatcher.matches(poems[0], "月光", keys(poems[0])))
    @Test fun `关键字命中诗人汉字`() = assertTrue(SearchMatcher.matches(poems[0], "李白", keys(poems[0])))
    @Test fun `标题汉字命中`() = assertTrue(SearchMatcher.matches(poems[0], "静夜思", keys(poems[0])))
    @Test fun `关键字命中全拼`() = assertTrue(SearchMatcher.matches(poems[0], "chuangqian", keys(poems[0])))
    @Test fun `关键字命中诗句首字母`() = assertTrue(SearchMatcher.matches(poems[0], "cqmyg", keys(poems[0])))
    @Test fun `关键字命中标题全拼`() = assertTrue(SearchMatcher.matches(poems[0], "jingyesi", keys(poems[0])))
    @Test fun `关键字命中标题首字母`() = assertTrue(SearchMatcher.matches(poems[0], "jys", keys(poems[0])))
    @Test fun `关键字命中诗人全拼`() = assertTrue(SearchMatcher.matches(poems[0], "libai", keys(poems[0])))
    @Test fun `关键字命中诗人首字母`() = assertTrue(SearchMatcher.matches(poems[0], "lb", keys(poems[0])))
    @Test fun `索引缺键降级诗句键仍工作`() {
        val noIndex = SearchMatcher.buildKeys(poems[0], SearchIndex())
        assertTrue(SearchMatcher.matches(poems[0], "cqmyg", noIndex))
        assertTrue(SearchMatcher.matches(poems[0], "chuangqian", noIndex))
    }
    @Test fun `不命中`() = assertFalse(SearchMatcher.matches(poems[0], "xyz", keys(poems[0])))
    @Test fun `空关键字命中全部`() = assertTrue(poems.all { SearchMatcher.matches(it, "", keys(it)) })
    @Test fun `V 形输入与 U 形等价`() = assertTrue(SearchMatcher.matches(poems[1], "lvshui", keys(poems[1])))
    @Test fun `带调首字归一`() = assertTrue(SearchMatcher.matches(poems[1], "eee", keys(poems[1])))

    /** 隔离守卫 lineInitKey：eeqx 只能经首字母段命中（全拼段为 eeequ…无此子串）——
     *  去调缺失时 init 段为 éééqx 该断言必红；eee 走全拼通道守不住此缺陷 */
    @Test fun `首字母通道跨带调字`() = assertTrue(SearchMatcher.matches(poems[1], "eeqx", keys(poems[1])))
}
