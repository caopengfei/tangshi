package com.rakor.tangshi.audio

import android.media.AudioManager
import android.media.ToneGenerator

/** 按键提示音（家长设置可关）。ToneGenerator 非线程安全，主线程使用。 */
class KeyTonePlayer(private val enabled: () -> Boolean) {
    private var tone: ToneGenerator? = null

    fun focusMove() {
        if (enabled()) beep(ToneGenerator.TONE_PROP_BEEP, 40)
    }

    fun confirm() {
        if (enabled()) beep(ToneGenerator.TONE_PROP_ACK, 60)
    }

    private fun beep(t: Int, ms: Int) {
        runCatching {
            tone?.release()
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, 45).also { it.startTone(t, ms) }
        }
    }

    fun release() {
        runCatching { tone?.release() }
        tone = null
    }
}
