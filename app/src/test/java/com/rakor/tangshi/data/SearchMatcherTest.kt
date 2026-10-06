package com.rakor.tangshi.data

import com.rakor.tangshi.data.search.SearchMatcher
import org.junit.Assert.*
import org.junit.Test

/**
 * 搜索匹配纯函数（规格 5.3）：
 * - 汉字命中 = 标题/诗句文本 contains；
 * - 全拼命中 = 诗句逐字拼音去声调连串 contains（拼音数据只在诗句 chars 上，
 *   标题无逐字拼音——标题用汉字搜，控制器裁定，原"诗名全拼"用例改为"标题汉字命中"）；
 * - 诗人过滤完全相等；关键字为空 = 仅诗人过滤。
 */
class SearchMatcherTest {
    private val poems = TestFixtures.poems()

    @Test fun `关键字命中汉字`() { assertTrue(SearchMatcher.matches(poems[0], null, "月光")) }
    @Test fun `关键字命中全拼`() { assertTrue(SearchMatcher.matches(poems[0], null, "chuangqian")) }
    @Test fun `标题汉字命中`() { assertTrue(SearchMatcher.matches(poems[0], null, "静夜思")) }
    @Test fun `不命中`() { assertFalse(SearchMatcher.matches(poems[0], null, "xyz")) }
    @Test fun `诗人过滤`() {
        assertEquals(listOf("yong-e"), poems.filter { SearchMatcher.matches(it, "骆宾王", "") }.map { it.id })
    }
}
