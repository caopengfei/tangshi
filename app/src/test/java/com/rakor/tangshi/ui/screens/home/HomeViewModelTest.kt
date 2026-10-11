package com.rakor.tangshi.ui.screens.home

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.repo.ProgressStatus
import com.rakor.tangshi.data.repo.ProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** HomeViewModel 当前诗选择（纯函数，普通 JUnit） */
class HomeViewModelTest {
    private fun poem(id: String, order: Int) = Poem(id, order, "诗$id", "诗人", "唐", emptyList(), "")
    private fun row(poemId: String, status: String, last: Long) =
        ProgressSummary(poemId, status, 50, last)
    private val poems = listOf(poem("a", 1), poem("b", 2), poem("c", 3))

    @Test fun `最近学习的LEARNING优先`() {
        val s = listOf(row("a", ProgressStatus.LEARNING, 100), row("b", ProgressStatus.LEARNING, 200))
        assertEquals("b", HomeViewModel.selectCurrent(s, poems)?.id)
    }
    @Test fun `MASTERED行跳过_落第一首未学`() {
        val s = listOf(row("a", ProgressStatus.MASTERED, 999))
        assertEquals("b", HomeViewModel.selectCurrent(s, poems)?.id)   // a 有行(会背)，第一个无行的是 b
    }
    @Test fun `全部会背_回退第一首`() {
        val s = listOf(
            row("a", ProgressStatus.MASTERED, 100),
            row("b", ProgressStatus.MASTERED, 200),
            row("c", ProgressStatus.MASTERED, 300)
        )
        assertEquals("a", HomeViewModel.selectCurrent(s, poems)?.id)
    }
    @Test fun `未知诗ID不崩溃`() {
        val s = listOf(row("x", ProgressStatus.LEARNING, 500), row("y", ProgressStatus.LEARNING, 100))
        assertEquals("a", HomeViewModel.selectCurrent(s, poems)?.id)   // firstOrNull 忽略未知行 → 第一首未学
        assertNull(HomeViewModel.selectCurrent(s, emptyList()))        // 空诗库 → null 而非抛异常
    }
}
