package com.rakor.tangshi.ui.screens.library

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.repo.ProgressStatus
import com.rakor.tangshi.data.repo.ProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** LibraryScreen 筛选纯函数（规格 5.4）：无行=未学习 / LEARNING / MASTERED / 全部不过滤 */
class LibraryFilterTest {
    private fun poem(id: String, order: Int) = Poem(id, order, "诗$id", "诗人", "唐", emptyList(), "")
    private fun row(poemId: String, status: String, progress: Int) =
        ProgressSummary(poemId, status, progress, 0L)
    private val poems = listOf(poem("a", 1), poem("b", 2), poem("c", 3))

    @Test fun `筛选四维各归其类`() {
        // 无进度行 = 未学习（NOT_STARTED 不落 DB 行）
        val summaries = listOf(row("b", ProgressStatus.LEARNING, 40), row("c", ProgressStatus.MASTERED, 100))
        assertEquals(listOf("a"), filterPoems(LibraryFilter.NOT_STARTED, poems, summaries).map { it.id })
        // LEARNING 按状态过滤，progress 值不参与判断（原样透传给徽章展示）
        assertEquals(listOf("b"), filterPoems(LibraryFilter.LEARNING, poems, summaries).map { it.id })
        // MASTERED
        assertEquals(listOf("c"), filterPoems(LibraryFilter.MASTERED, poems, summaries).map { it.id })
        // 全部 = 不过滤，保持原顺序
        assertEquals(poems, filterPoems(LibraryFilter.ALL, poems, summaries))
    }
}
