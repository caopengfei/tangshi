package com.rakor.tangshi.data

import androidx.test.core.app.ApplicationProvider
import com.rakor.tangshi.data.db.poemDatabase
import com.rakor.tangshi.data.repo.ProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressRepositoryTest {
    private val repo = ProgressRepository(
        poemDatabase(ApplicationProvider.getApplicationContext<android.content.Context>(), "test-${System.nanoTime()}.db")
    ) { 4 }   // lineCountOf: 测试里统一按4句诗

    @Test fun `打开并播放过一句才算学习中`() = runTest {
        repo.onPoemOpened("jing-ye-si")
        assertNull(repo.observeProgress("jing-ye-si").first())   // 只打开不播放=未开始(无行)
        repo.onLinePlayed("jing-ye-si", 0)
        val p = repo.observeProgress("jing-ye-si").first()!!
        assertEquals("LEARNING", p.status)
        assertEquals(25, p.progress)                              // 4句播1句
    }
    @Test fun `进度按去重句数累计`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        repo.onLinePlayed("jing-ye-si", 0)                        // 重复不计
        repo.onLinePlayed("jing-ye-si", 1)
        assertEquals(50, repo.observeProgress("jing-ye-si").first()!!.progress)
    }
    @Test fun `会背置满且不回退`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        repo.markMastered("jing-ye-si")
        val p = repo.observeProgress("jing-ye-si").first()!!
        assertEquals("MASTERED", p.status); assertEquals(100, p.progress)
        repo.onLinePlayed("jing-ye-si", 2)                        // 会背后继续听不回退
        val p2 = repo.observeProgress("jing-ye-si").first()!!
        assertEquals("MASTERED", p2.status); assertEquals(100, p2.progress)
    }
    @Test fun `今日所学与统计`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        val stats = repo.observeStats().first()
        assertEquals(1, stats.learned); assertEquals(0, stats.mastered)
        assertTrue(stats.todayPoemIds.contains("jing-ye-si"))
        assertEquals(1, stats.streakDays)
    }
    @Test fun `清除进度`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0); repo.wipeAll()
        assertNull(repo.observeProgress("jing-ye-si").first())
    }
    @Test fun `连续天数断档清零`() = runTest {
        // streakDays 是纯函数，直接测：今天有记录→连续；昨天有今天无→连续保留；前天断档→清零
        val today = java.time.LocalDate.now()
        assertEquals(3, ProgressRepository.streakDays(setOf(today.toString(), today.minusDays(1).toString(), today.minusDays(2).toString()), today))
        assertEquals(1, ProgressRepository.streakDays(setOf(today.toString(), today.minusDays(2).toString()), today))
        assertEquals(0, ProgressRepository.streakDays(emptySet(), today))
    }
}
