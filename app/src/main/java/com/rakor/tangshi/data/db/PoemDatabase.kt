package com.rakor.tangshi.data.db

import android.content.Context
import androidx.room.*
import androidx.room.Entity as E
import androidx.room.PrimaryKey as PK

@E(tableName = "poem_progress")
data class PoemProgressEntity(
    @PK val poemId: String,
    val status: String,          // LEARNING/MASTERED（NOT_STARTED 不落行）
    val progress: Int,
    val playedLines: String,     // 逗号分隔行号(0-based)，已播句去重集合
    val lastPlayedAt: Long,
    val masteredAt: Long?
)
@E(tableName = "study_log") data class StudyLogEntity(@PK val date: String, val poemsTouched: Int)

@Dao interface ProgressDao {
    @Query("SELECT * FROM poem_progress WHERE poemId = :id") suspend fun get(id: String): PoemProgressEntity?
    @Query("SELECT * FROM poem_progress") fun observeAll(): kotlinx.coroutines.flow.Flow<List<PoemProgressEntity>>
    @Upsert suspend fun upsert(e: PoemProgressEntity)
    @Query("DELETE FROM poem_progress") suspend fun clearProgress()
    @Query("DELETE FROM study_log") suspend fun clearLog()
    @Query("SELECT * FROM study_log") suspend fun allLog(): List<StudyLogEntity>
    @Upsert suspend fun upsertLog(e: StudyLogEntity)
}

@Database(entities = [PoemProgressEntity::class, StudyLogEntity::class], version = 1, exportSchema = false)
abstract class PoemDatabase : RoomDatabase() { abstract fun progressDao(): ProgressDao }

fun poemDatabase(context: Context, name: String = "tangshi.db"): PoemDatabase =
    Room.databaseBuilder(context, PoemDatabase::class.java, name).build()

/** 测试用内存库（不落盘，免文件名去重） */
fun poemInMemoryDatabase(context: Context): PoemDatabase =
    Room.inMemoryDatabaseBuilder(context, PoemDatabase::class.java).build()
