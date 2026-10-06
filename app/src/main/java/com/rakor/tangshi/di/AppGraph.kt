package com.rakor.tangshi.di

import android.content.Context
import com.rakor.tangshi.audio.KeyTonePlayer
import com.rakor.tangshi.data.db.poemDatabase
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.repo.ProgressRepository

/**
 * 手写依赖容器（不引 DI 框架），在 [com.rakor.tangshi.TangshiApp] 初始化后全局可用。
 */
class AppGraph(context: Context) {
    val poems = PoemRepository(context)
    val progress = ProgressRepository(poemDatabase(context)) { id -> poems.byId(id)?.lines?.size ?: 4 }
    val keyTone = KeyTonePlayer { true }   // Task 11 接 DataStore 开关

    companion object { lateinit var instance: AppGraph }
}
