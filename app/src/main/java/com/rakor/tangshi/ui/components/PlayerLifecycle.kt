package com.rakor.tangshi.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rakor.tangshi.audio.PoemPlayer

/**
 * 播音与宿主之间的全部接缝（详情页与游戏听音题各建自己的 [PoemPlayer]，都挂这一处）：
 *
 * 1) 退到后台（主页键/切应用/息屏，ON_STOP）暂停朗读，回前台（ON_START）续播这次暂停。
 *    为什么必须有：ExoPlayer 不随 Activity 进出后台停声，不挂这层按主页键回到桌面后诗还在念
 *    （2026-10-14 实机验证：HOME 后 AudioTrack 仍 state:started、前台已是 TV 桌面）。
 *    口径是「暂停」而非「停止」——回前台接着念，屏幕上「连读中 · 第 N 句」的状态与句号不断档。
 *
 * 2) 播音期间给宿主窗口挂 FLAG_KEEP_SCREEN_ON（2026-10-14 加）。连读一首诗要几分钟，全程没有
 *    按键，系统按「无用户活动」进屏保/熄屏——屏保一起来就 ON_STOP，被上面那条暂停，孩子回来
 *    看到的是一首念了一半停住的诗。判据取「有装载」（shape != IDLE）而不是 isPlaying：句间
 *    装载下一句时 isPlaying 会瞬时落回 false，跟着它挂/摘标志会逐句抖；EmptyState 下无装载，
 *    标志摘掉，空闲浏览诗文时照常让屏保接管。单句朗读也落在同一判据内——只多亮几秒钟，
 *    省一次「听不清重念时屏幕已经黑了」的麻烦。
 *
 *    用窗口标志而不是 View.keepScreenOn/唤醒锁：标志由 WindowManager 只对可见窗口生效，
 *    退到后台自然失效（屏保该来还是来），不必再自己判前后台；也不需要 WAKE_LOCK 权限。
 */
@Composable
fun PlayerLifecycleEffect(player: PoemPlayer) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pauseForBackground()
                Lifecycle.Event.ON_START -> player.resumeAfterBackground()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val shape by player.shape.collectAsState()
    KeepScreenOnEffect(active = shape != PoemPlayer.Shape.IDLE)
}

/**
 * [active] 期间宿主窗口常亮。[active] 转 false 或本组合离屏时摘掉标志。
 * 同屏只应有一处调用（本工程的两个播放页各只建一个播放器），否则后摘的那处会把另一处的标志清掉。
 */
@Composable
private fun KeepScreenOnEffect(active: Boolean) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }
    DisposableEffect(window, active) {
        if (active) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (active) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** 往上剥 ContextWrapper 找宿主 Activity（[LocalContext] 在 Activity 里通常就是这个 Activity，
 *  但主题包装下会是 ContextThemeWrapper，逐层剥才稳） */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
