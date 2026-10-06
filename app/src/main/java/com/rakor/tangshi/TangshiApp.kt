package com.rakor.tangshi

import android.app.Application
import com.rakor.tangshi.di.AppGraph

class TangshiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.instance = AppGraph(this)
        // 常驻收集家长设置到 AppGraph 内存缓存（keyTone beep/播放器启动等主线程同步读）
        AppGraph.instance.startCollecting()
    }
}
