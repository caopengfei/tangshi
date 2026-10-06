package com.rakor.tangshi.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/**
 * 家长设置（规格 5.8）持久层，Preferences DataStore 单文件 "settings"。
 * 三项设置：播放倍率（作用在音频已生成的 0.85 基准语速之上，ExoPlayer 变速不变调）、
 * 跟读模式（逐句暂停=朗读每句播完暂停等孩子跟读）、按键提示音。
 * 读侧是冷流 [settings]；写侧为各 set 的 suspend 函数。主线程不能 suspend 读 →
 * 消费方（AppGraph）用内存缓存字段 + 常驻协程收集。
 */
class SettingsRepository(private val context: Context) {
    data class Settings(
        val speed: Float = 1.0f,          // 播放倍率: 慢0.9/正常1.0/快1.15（作用在0.85生成基准之上）
        val lineByLine: Boolean = true,   // 跟读模式: 逐句暂停(默认)/连续播放
        val keyTone: Boolean = true       // 按键提示音
    )

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            p[floatPreferencesKey("speed")] ?: 1.0f,
            p[booleanPreferencesKey("lineByLine")] ?: true,
            p[booleanPreferencesKey("keyTone")] ?: true
        )
    }

    suspend fun setSpeed(v: Float) = context.dataStore.edit { it[floatPreferencesKey("speed")] = v }
    suspend fun setLineByLine(v: Boolean) = context.dataStore.edit { it[booleanPreferencesKey("lineByLine")] = v }
    suspend fun setKeyTone(v: Boolean) = context.dataStore.edit { it[booleanPreferencesKey("keyTone")] = v }
}
