package com.rakor.tangshi.di

import android.content.Context
import com.rakor.tangshi.audio.KeyTonePlayer
import com.rakor.tangshi.data.db.poemDatabase
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.repo.ProgressRepository
import com.rakor.tangshi.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手写依赖容器（不引 DI 框架），在 [com.rakor.tangshi.TangshiApp] 初始化后全局可用。
 *
 * 设置读侧接线：DataStore 是 suspend 流，而 keyTone beep 在主线程同步路径上，
 * 不能现场读——故 [startCollecting] 用常驻协程把 settings 收进 @Volatile 内存缓存字段，
 * 各屏同步读缓存即可（app 生命周期=进程生命周期，scope 无需取消）。
 */
class AppGraph(context: Context) {
    val poems = PoemRepository(context)
    val progress = ProgressRepository(poemDatabase(context)) { id -> poems.byId(id)?.lines?.size ?: 4 }
    val settings = SettingsRepository(context)

    // 首次 DataStore 落盘前默认值兜底（与 Settings 默认一致：提示音开）
    @Volatile var keyToneEnabled: Boolean = true
        private set

    val keyTone = KeyTonePlayer { keyToneEnabled }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Application onCreate 调一次；进程存活期间持续收集设置到内存缓存 */
    fun startCollecting() {
        appScope.launch {
            settings.settings.collect {
                keyToneEnabled = it.keyTone
            }
        }
    }

    /** Application onCreate 调一次：把 1.4MB 诗库 JSON 与搜索索引的解析放到
     *  后台线程预热，避免首屏/进搜索时主线程撞上未完成的解析而阻塞。 */
    fun warmUp() {
        appScope.launch { poems.loadAll() }
        appScope.launch { poems.loadSearchIndex() }
    }

    companion object { lateinit var instance: AppGraph }
}
