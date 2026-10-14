package com.rakor.tangshi.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 逐句播放器壳：透传 ExoPlayer，只做三件事——
 * 1) 把 currentLine/isPlaying/shape/error 暴露为 StateFlow 供 Compose 收集；
 * 2) 三种播放形态（Shape）：单句朗读（播完即停）、整首连读循环（末句回卷句首，直至停止）、
 *    单句循环（游戏听音题专用）；切换即整列表替换；
 * 3) 播放失败（语音包缺失等）置 error 并暂停，由 UI 呈现提示。
 * 音频资源约定：assets/audio/{poemId}/line{N}.opus（Task 12 生成）。
 */
class PoemPlayer(private val exo: ExoPlayer) {

    /** 播放形态：空闲（无装载）/ 单句朗读（播完即停，含游戏听音题的单句循环）/ 整首连读循环 */
    enum class Shape { IDLE, SINGLE, LOOP }

    /** 当前句（1-based；0=未播放） */
    val currentLine = MutableStateFlow(0)
    val isPlaying = MutableStateFlow(false)
    val shape = MutableStateFlow(Shape.IDLE)

    /** 播放失败（音频缺失等）→ UI 显示一次性提示条，消费后由 UI 复位 */
    val error = MutableStateFlow(false)

    /** LINE_LOOP 激活行号（1-based；0=非循环）。每次装载的 PLAYLIST_CHANGED 与每次循环回绕的
     *  REPEAT 都会触发 transition，且单项列表下 currentMediaItemIndex 恒为 0，
     *  回调据此回写 loopLine 而非按索引推，避免把跟读行号覆盖成 1 */
    private var loopLine = 0

    /** 本次暂停是否由「退到后台」引起（回前台自动续播的判据；用户停止/游戏作答暂停都不置此位） */
    private var pausedInBackground = false

    private fun setShape(v: Shape) {
        exoShape = v
        shape.value = v
    }

    private var exoShape = Shape.IDLE

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                currentLine.value = if (loopLine > 0) loopLine
                else exo.currentMediaItemIndex.takeIf { it >= 0 }?.plus(1) ?: 0
            }

            override fun onPlaybackStateChanged(state: Int) {
                // 单句朗读播完 → 复位空闲：清除高亮、箭头回到目标位置
                if (state == Player.STATE_ENDED && exoShape == Shape.SINGLE) {
                    currentLine.value = 0
                    setShape(Shape.IDLE)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                this@PoemPlayer.error.value = true
                exo.pause()
            }
        })
    }

    /** 朗读：只装载 line 一句（1-based），播完即停（END 复位 IDLE），不切下一句 */
    fun playLineOnce(poemId: String, line: Int) {
        loopLine = 0
        setShape(Shape.SINGLE)
        exo.setMediaItems(listOf(lineItem(poemId, line)))
        exo.repeatMode = Player.REPEAT_MODE_OFF
        currentLine.value = line
        exo.prepare()
        exo.play()
    }

    /** 连读：整首从第 1 句顺序播到末句后回卷句首，循环往复；停止用 [stopPlayback] */
    fun playContinuousLoop(poemId: String, lineCount: Int) {
        loopLine = 0
        setShape(Shape.LOOP)
        exo.setMediaItems((1..lineCount).map { lineItem(poemId, it) }, 0, 0)
        exo.repeatMode = Player.REPEAT_MODE_ALL
        currentLine.value = 1
        exo.prepare()
        exo.play()
    }

    /** 连读中跳到指定句（1-based）继续循环 */
    fun seekToLine(line: Int) {
        if (exoShape != Shape.LOOP) return
        exo.seekTo((line - 1).coerceAtLeast(0), 0)
        currentLine.value = line
    }

    /** 单句循环：只装载 line 一句并单曲循环（游戏听音题面复用；归入 SINGLE 形态） */
    fun playLineLoop(poemId: String, line: Int) {
        loopLine = line
        setShape(Shape.SINGLE)
        exo.setMediaItems(listOf(lineItem(poemId, line)))
        exo.repeatMode = Player.REPEAT_MODE_ONE
        currentLine.value = line
        exo.prepare()
        exo.play()
    }

    /** 停止播放（连读关闭）：清空装载，复位空闲 */
    fun stopPlayback() {
        exo.stop()
        exo.clearMediaItems()
        loopLine = 0
        pausedInBackground = false
        currentLine.value = 0
        setShape(Shape.IDLE)
    }

    fun pause() { exo.pause() }

    /**
     * 退到后台（主页键/切应用/息屏）暂停朗读：ExoPlayer 自身不随 Activity 进出后台停声——
     * 缺这层按主页键回桌面后诗还在念（2026-10-14 实机验证：HOME 后 AudioTrack 仍 state:started）。
     * 判据用 playWhenReady 而非 isPlaying，把「刚按 OK 还在加载」也算进来，否则缓冲期按主页键
     * 会等加载完在后台念出来；再叠 mediaItemCount>0 排除无装载的空转。游戏作答暂停、用户
     * OK 停止都不会置位（那些是主动暂停，回前台不该自己响）。
     */
    fun pauseForBackground() {
        if (exo.playWhenReady && exo.mediaItemCount > 0) {
            pausedInBackground = true
            exo.pause()
        }
    }

    /** 回前台：只续播「因退后台而暂停」的那一次，其余暂停状态一概不动 */
    fun resumeAfterBackground() {
        if (!pausedInBackground) return
        pausedInBackground = false
        exo.play()
    }

    fun release() {
        loopLine = 0
        pausedInBackground = false
        exo.release()
    }

    private fun lineItem(poemId: String, line: Int) = MediaItem.fromUri("asset:///audio/$poemId/line$line.opus")
}
