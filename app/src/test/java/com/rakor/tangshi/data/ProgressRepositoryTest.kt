package com.rakor.tangshi.data

import androidx.test.core.app.ApplicationProvider
import com.rakor.tangshi.data.db.poemInMemoryDatabase
import com.rakor.tangshi.data.repo.ProgressRepository
import com.rakor.tangshi.data.repo.ProgressStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressRepositoryTest {
    private val repo = ProgressRepository(
        poemInMemoryDatabase(ApplicationProvider.getApplicationContext())
    ) { 4 }   // lineCountOf: 测试里统一按4句诗

    @Test fun `打开并播放过一句才算学习中`() = runTest {
        repo.onPoemOpened("jing-ye-si")
        assertNull(repo.observeProgress("jing-ye-si").first())   // 只打开不播放=未开始(无行)
        repo.onLinePlayed("jing-ye-si", 0)
        val p = repo.observeProgress("jing-ye-si").first()!!
        assertEquals(ProgressStatus.LEARNING, p.status)
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
        assertEquals(ProgressStatus.MASTERED, p.status); assertEquals(100, p.progress)
        repo.onLinePlayed("jing-ye-si", 2)                        // 会背后继续听不回退
        val p2 = repo.observeProgress("jing-ye-si").first()!!
        assertEquals(ProgressStatus.MASTERED, p2.status); assertEquals(100, p2.progress)
    }
    @Test fun `今日所学与统计`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        val stats = repo.observeStats().first()
        assertEquals(1, stats.learned); assertEquals(0, stats.mastered)
        assertTrue(stats.todayPoemIds.contains("jing-ye-si"))
        assertEquals(1, stats.streakDays)
    }
    @Test fun `多首诗统计`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        repo.onLinePlayed("chun-xiao", 0)
        repo.onLinePlayed("chun-xiao", 1)
        val stats = repo.observeStats().first()
        assertEquals(2, stats.learned)
        assertEquals(setOf("jing-ye-si", "chun-xiao"), stats.todayPoemIds)
    }
    @Test fun `全部汇总`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        repo.onLinePlayed("chun-xiao", 0)
        repo.markMastered("chun-xiao")
        val summaries = repo.observeAllSummaries().first()
        assertEquals(2, summaries.size)
        val jys = summaries.first { it.poemId == "jing-ye-si" }
        val cx = summaries.first { it.poemId == "chun-xiao" }
        assertEquals(ProgressStatus.LEARNING, jys.status); assertEquals(25, jys.progress)
        assertEquals(ProgressStatus.MASTERED, cx.status); assertEquals(100, cx.progress)
    }
    @Test fun `清除进度`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0); repo.wipeAll()
        assertNull(repo.observeProgress("jing-ye-si").first())
        assertEquals(0, repo.observeStats().first().streakDays)   // study_log 一并清空
    }
    @Test fun `越界行号被过滤`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        repo.onLinePlayed("jing-ye-si", 7)                        // 4句诗的越界行号
        val p = repo.observeProgress("jing-ye-si").first()!!
        assertEquals(25, p.progress)                              // 只计第0句，进度不会超100
    }
    @Test fun `连续天数断档清零`() = runTest {
        // streakDays 是纯函数，直接测：今天有记录→连续；昨天有今天无→连续保留；前天断档→清零
        val today = java.time.LocalDate.now()
        assertEquals(3, ProgressRepository.streakDays(setOf(today.toString(), today.minusDays(1).toString(), today.minusDays(2).toString()), today))
        assertEquals(1, ProgressRepository.streakDays(setOf(today.toString(), today.minusDays(2).toString()), today))
        assertEquals(0, ProgressRepository.streakDays(emptySet(), today))
    }
    @Test fun `游戏完成计入当日study_log`() = runTest {
        // 规格 9：小游戏一轮完成只记 study_log（连续天数可延续），不产生任何诗的进度行
        assertEquals(0, repo.observeStats().first().streakDays)
        repo.markTodayStudyLog()
        assertEquals(1, repo.observeStats().first().streakDays)
        assertEquals(0, repo.observeAllSummaries().first().size)
    }
}
