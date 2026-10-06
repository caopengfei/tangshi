package com.rakor.tangshi

import android.app.Application
import com.rakor.tangshi.di.AppGraph

class TangshiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.instance = AppGraph(this)
    }
}
