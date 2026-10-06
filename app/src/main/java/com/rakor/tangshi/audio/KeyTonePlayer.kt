package com.rakor.tangshi.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * 按键提示音（家长设置可关）。ToneGenerator 非线程安全，主线程使用。
 *
 * ToneGenerator 实例懒创建并缓存复用：per-beep 创建/释放会产生原生 AudioTrack
 * 开销（10-100ms），在遥控方向键连按频率下可感知延迟。同一实例重复 startTone
 * 会打断上一次发声，符合提示音语义；初始化失败置空以便下次重试。
 */
class KeyTonePlayer(private val enabled: () -> Boolean) {
    private var tone: ToneGenerator? = null

    fun focusMove() {
        if (enabled()) beep(ToneGenerator.TONE_PROP_BEEP, 40)
    }

    fun confirm() {
        if (enabled()) beep(ToneGenerator.TONE_PROP_ACK, 60)
    }

    private fun beep(t: Int, ms: Int) {
        if (!enabled()) return
        val gen = tone ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 45) }
            .onFailure { Log.w("KeyTonePlayer", "ToneGenerator init failed", it) }
            .getOrNull() ?: return
        tone = gen
        runCatching { gen.startTone(t, ms) }
    }

    fun release() {
        runCatching { tone?.release() }
        tone = null
    }
}
