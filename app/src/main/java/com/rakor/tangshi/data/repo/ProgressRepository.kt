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

class ProgressRepository(private val db: PoemDatabase, private val lineCountOf: (String) -> Int) {
    private val dao = db.progressDao()
    private fun today(): String = LocalDate.now().toString()

    private suspend fun touchToday() {
        val log = dao.allLog().firstOrNull { it.date == today() } ?: StudyLogEntity(today(), 0)
        dao.upsertLog(log.copy(poemsTouched = log.poemsTouched + 1))
    }
    private suspend fun save(id: String, status: String, progress: Int, played: Set<Int>, last: Long, masteredAt: Long?) =
        dao.upsert(PoemProgressEntity(id, status, progress, played.joinToString(","), last, masteredAt))

    /** 进入详情页。未开始(无行)不落行；MASTERED 不回退。 */
    suspend fun onPoemOpened(id: String) {
        dao.get(id) ?: return
    }
    suspend fun onLinePlayed(id: String, lineIndex: Int) {
        val now = System.currentTimeMillis()
        val cur = dao.get(id)
        val played = cur?.playedLines?.split(",")?.filter { it.isNotBlank() }?.map { it.toInt() }?.toSet() ?: emptySet()
        val newPlayed = played + lineIndex
        val total = lineCountOf(id).coerceAtLeast(1)
        val mastered = cur?.status == "MASTERED"
        val status = if (mastered) "MASTERED" else "LEARNING"
        val progress = if (mastered) 100 else newPlayed.size * 100 / total
        save(id, status, progress, newPlayed, now, cur?.masteredAt)
        touchToday()
    }
    suspend fun markMastered(id: String) {
        val cur = dao.get(id)
        val total = lineCountOf(id).coerceAtLeast(1)
        save(id, "MASTERED", 100, (0 until total).toSet(),
            cur?.lastPlayedAt ?: System.currentTimeMillis(),
            cur?.masteredAt ?: System.currentTimeMillis())
    }
    fun observeProgress(id: String): Flow<PoemProgress?> =
        dao.observeAll().map { list -> list.firstOrNull { it.poemId == id } }
            .map { it?.let { e -> PoemProgress(e.status, e.progress, e.lastPlayedAt) } }
    fun observeStats(): Flow<HomeStats> = dao.observeAll().map { list ->
        HomeStats(
            learned = list.size,
            mastered = list.count { it.status == "MASTERED" },
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
