package com.rakor.tangshi.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 逐句播放器壳：透传 ExoPlayer，只做三件事——
 * 1) 把 currentLine/isPlaying/error 暴露为 StateFlow 供 Compose 收集；
 * 2) 整首连读（CONTINUOUS，顺序播放 line1..n，播完即停）与单句循环（LINE_LOOP，REPEAT_MODE_ONE）
 *    两种装载方式，切换即整列表替换；
 * 3) 播放失败（语音包缺失等）置 error 并暂停，由 UI 呈现提示。
 * 音频资源约定：assets/audio/{poemId}/line{N}.opus（Task 12 生成）。
 */
class PoemPlayer(private val exo: ExoPlayer) {
    enum class Mode { CONTINUOUS, LINE_LOOP }

    /** 当前句（1-based；0=未播放） */
    val currentLine = MutableStateFlow(0)
    val isPlaying = MutableStateFlow(false)

    /** 播放失败（音频缺失等）→ UI 显示一次性提示条，消费后由 UI 复位 */
    val error = MutableStateFlow(false)

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
                // 兜底：setMediaItems 指定的起始句起播不一定触发 onMediaItemTransition，
                // 起播时按当前项同步一次，保证 CONTINUOUS 从任意 startLine 起第一句也高亮/记进度
                if (playing && exo.currentMediaItemIndex >= 0) {
                    currentLine.value = exo.currentMediaItemIndex + 1
                }
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (exo.currentMediaItemIndex >= 0) currentLine.value = exo.currentMediaItemIndex + 1
            }

            override fun onPlayerError(error: PlaybackException) {
                this@PoemPlayer.error.value = true
                exo.pause()
            }
        })
    }

    /** 整首连读：从 startLine（1-based）起顺序播放全诗，播完即停 */
    fun playContinuous(poemId: String, lineCount: Int, startLine: Int = 1, speed: Float = 1f) {
        exo.setMediaItems((1..lineCount).map { lineItem(poemId, it) }, (startLine - 1).coerceIn(0, lineCount - 1), 0)
        exo.playbackParameters = PlaybackParameters(speed)
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.prepare()
        exo.play()
    }

    /** 单句循环：只装载 line 一句并单曲循环 */
    fun playLineLoop(poemId: String, line: Int, speed: Float = 1f) {
        exo.setMediaItems(listOf(lineItem(poemId, line)))
        exo.playbackParameters = PlaybackParameters(speed)
        exo.repeatMode = Player.REPEAT_MODE_ONE
        currentLine.value = line
        exo.prepare()
        exo.play()
    }

    fun togglePause() { if (exo.isPlaying) exo.pause() else exo.play() }

    fun pause() { exo.pause() }

    fun release() { exo.release() }

    private fun lineItem(poemId: String, line: Int) = MediaItem.fromUri("asset:///audio/$poemId/line$line.opus")
}
