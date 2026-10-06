// 规则（规格 7.2）:
// 1) 进入详情页(打开)且播放过任一句 → status=LEARNING（只打开不播放不落行）
// 2) progress = 去重已播句数/总句数*100（整数除法）
// 3) markMastered → MASTERED + progress=100 + playedLines 全句；之后任何 onLinePlayed/onPoemOpened 不改 status/progress（lastPlayedAt 仍更新）
// 4) streak: 当日 study_log 有记录则延续，断一天清零；今天还没学习时按昨天起算（纯函数 companion）
// 5) todayPoemIds = lastPlayedAt 是今天的诗
package com.rakor.tangshi.data.repo

import com.rakor.tangshi.data.db.PoemDatabase
import com.rakor.tangshi.data.db.PoemProgressEntity
import com.rakor.tangshi.data.db.StudyLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PoemProgress(val status: String, val progress: Int, val lastPlayedAt: Long)
data class HomeStats(val learned: Int, val mastered: Int, val streakDays: Int, val todayPoemIds: Set<String>)
data class ProgressSummary(val poemId: String, val status: String, val progress: Int, val lastPlayedAt: Long)

/** 进度状态常量（DB 存的就是这些字符串值；NOT_STARTED 不落行，仅供调用方语义判断） */
object ProgressStatus {
    const val LEARNING = "LEARNING"
    const val MASTERED = "MASTERED"
    const val NOT_STARTED = "NOT_STARTED"
}

class ProgressRepository(private val db: PoemDatabase, private val lineCountOf: (String) -> Int) {
    private val dao = db.progressDao()
    private fun today(): String = LocalDate.now().toString()

    private suspend fun touchToday() {
        val log = dao.allLog().firstOrNull { it.date == today() } ?: StudyLogEntity(today(), 0)
        dao.upsertLog(log.copy(poemsTouched = log.poemsTouched + 1))
    }
    private suspend fun save(id: String, status: String, progress: Int, played: Set<Int>, last: Long, masteredAt: Long?) =
        dao.upsert(PoemProgressEntity(id, status, progress, played.joinToString(","), last, masteredAt))

    /** 规格 7.2：只打开不播放不落行/不改状态 —— 显式接缝，Task 7 打开详情页时调用 */
    suspend fun onPoemOpened(id: String) { /* intentional no-op */ }
    suspend fun onLinePlayed(id: String, lineIndex: Int) {
        val now = System.currentTimeMillis()
        val cur = dao.get(id)
        val total = lineCountOf(id).coerceAtLeast(1)
        val played = cur?.playedLines?.split(",")?.filter { it.isNotBlank() }?.map { it.toInt() }?.toSet() ?: emptySet()
        val newPlayed = (played + lineIndex).filter { it in 0 until total }.toSet()   // 越界行号不落账，进度不会超100
        val mastered = cur?.status == ProgressStatus.MASTERED
        val status = if (mastered) ProgressStatus.MASTERED else ProgressStatus.LEARNING
        val progress = if (mastered) 100 else newPlayed.size * 100 / total
        save(id, status, progress, newPlayed, now, cur?.masteredAt)
        touchToday()
    }
    suspend fun markMastered(id: String) {
        val cur = dao.get(id)
        val total = lineCountOf(id).coerceAtLeast(1)
        save(id, ProgressStatus.MASTERED, 100, (0 until total).toSet(),
            cur?.lastPlayedAt ?: System.currentTimeMillis(),
            cur?.masteredAt ?: System.currentTimeMillis())
    }
    fun observeProgress(id: String): Flow<PoemProgress?> =
        dao.observeAll().map { list -> list.firstOrNull { it.poemId == id } }
            .map { it?.let { e -> PoemProgress(e.status, e.progress, e.lastPlayedAt) } }
    /** 全部诗的进度汇总（Library/Home 列表用，Task 6/8 消费） */
    fun observeAllSummaries(): Flow<List<ProgressSummary>> =
        dao.observeAll().map { l -> l.map { ProgressSummary(it.poemId, it.status, it.progress, it.lastPlayedAt) } }
    fun observeStats(): Flow<HomeStats> = dao.observeAll().map { list ->
        HomeStats(
            learned = list.size,
            mastered = list.count { it.status == ProgressStatus.MASTERED },
            streakDays = streakDays(dao.allLog().map { it.date }.toSet(), LocalDate.now()),
            todayPoemIds = list.filter { sameDay(it.lastPlayedAt) }.map { it.poemId }.toSet()
        )
    }
    suspend fun todayPoemIds(): Set<String> =
        dao.observeAll().first().filter { sameDay(it.lastPlayedAt) }.map { it.poemId }.toSet()
    suspend fun wipeAll() { dao.clearProgress(); dao.clearLog() }

    private fun sameDay(epochMs: Long): Boolean =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

    companion object {
        /** 纯函数：连续天数。今天缺卡则从昨天起算。dates 为 yyyy-MM-dd。 */
        fun streakDays(dates: Set<String>, today: LocalDate): Int {
            var d = if (dates.contains(today.toString())) today else today.minusDays(1)
            var n = 0
            while (dates.contains(d.toString())) { n++; d = d.minusDays(1) }
            return n
        }
    }
}
