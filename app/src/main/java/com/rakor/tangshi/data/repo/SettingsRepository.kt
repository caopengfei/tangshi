package com.rakor.tangshi.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/**
 * 家长设置（规格 5.8）持久层，Preferences DataStore 单文件 "settings"。
 * 一项设置：按键提示音。（原"跟读模式/逐句暂停"随详情页播放交互改版移除，"播放倍速"
 * 随设置项精简移除，DataStore 旧键不再读取。）
 * 读侧是冷流 [settings]；写侧为各 set 的 suspend 函数。主线程不能 suspend 读 →
 * 消费方（AppGraph）用内存缓存字段 + 常驻协程收集。
 */
class SettingsRepository(private val context: Context) {
    data class Settings(
        val keyTone: Boolean = true       // 按键提示音
    )

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            p[booleanPreferencesKey("keyTone")] ?: true
        )
    }

    suspend fun setKeyTone(v: Boolean) = context.dataStore.edit { it[booleanPreferencesKey("keyTone")] = v }
}
