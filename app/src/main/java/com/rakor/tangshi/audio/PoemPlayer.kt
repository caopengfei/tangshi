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

    /** LINE_LOOP 激活行号（1-based；0=非循环）。每次装载的 PLAYLIST_CHANGED 与每次循环回绕的
     *  REPEAT 都会触发 transition，且单项列表下 currentMediaItemIndex 恒为 0，
     *  回调据此回写 loopLine 而非按索引推，避免把跟读行号覆盖成 1 */
    private var loopLine = 0

    /** 跟读模式（家长设置）逐句暂停：连读中每句播完（自动切下一句时）暂停，等孩子按 OK 继续 */
    private var autoPauseBetweenLines = false

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                currentLine.value = if (loopLine > 0) loopLine
                else exo.currentMediaItemIndex.takeIf { it >= 0 }?.plus(1) ?: 0
                // 逐句暂停：仅自动顺播到下一句时停（句首暂停，OK 继续）；末句播完走 ENDED 无 transition，自然停
                if (autoPauseBetweenLines && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    exo.pause()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                this@PoemPlayer.error.value = true
                exo.pause()
            }
        })
    }

    /** 整首连读：从 startLine（1-based）起顺序播放全诗，播完即停；
     *  autoPauseBetweenLines=true（跟读模式=逐句暂停）时每句播完自动暂停在下一句开始前 */
    fun playContinuous(
        poemId: String,
        lineCount: Int,
        startLine: Int = 1,
        speed: Float = 1f,
        autoPauseBetweenLines: Boolean = false
    ) {
        loopLine = 0
        this.autoPauseBetweenLines = autoPauseBetweenLines
        val startIndex = (startLine - 1).coerceIn(0, lineCount - 1)
        exo.setMediaItems((1..lineCount).map { lineItem(poemId, it) }, startIndex, 0)
        // 显式 preset 起始句：首句起播不触发 onMediaItemTransition（仅后续句切换才反映索引）
        currentLine.value = startIndex + 1
        exo.playbackParameters = PlaybackParameters(speed)
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.prepare()
        exo.play()
    }

    /** 单句循环：只装载 line 一句并单曲循环（跟读按钮，不受逐句暂停设置影响） */
    fun playLineLoop(poemId: String, line: Int, speed: Float = 1f) {
        loopLine = line
        autoPauseBetweenLines = false
        exo.setMediaItems(listOf(lineItem(poemId, line)))
        exo.playbackParameters = PlaybackParameters(speed)
        exo.repeatMode = Player.REPEAT_MODE_ONE
        currentLine.value = line
        exo.prepare()
        exo.play()
    }

    fun togglePause() { if (exo.isPlaying) exo.pause() else exo.play() }

    fun pause() { exo.pause() }

    fun release() {
        loopLine = 0
        exo.release()
    }

    private fun lineItem(poemId: String, line: Int) = MediaItem.fromUri("asset:///audio/$poemId/line$line.opus")
}
