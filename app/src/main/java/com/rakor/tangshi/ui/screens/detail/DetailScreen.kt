package com.rakor.tangshi.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.rakor.tangshi.audio.PoemPlayer
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.components.PoemLine
import com.rakor.tangshi.ui.theme.T
import kotlinx.coroutines.launch

/**
 * 详情学习页（规格 5.2）。VM-in-screen：本屏逻辑薄（播放器壳透传 ExoPlayer、进度直连
 * ProgressRepository），状态全部 hoist 在组合内，不设独立 ViewModel。
 *
 * 焦点模型：诗行区是一个焦点目标（初始焦点落这里），◀▶/OK 按键仅在其聚焦时被
 * onPreviewKeyEvent 接管并消费（B/返回等其余按键交还系统）；三个操作按钮与右侧背景卡
 * 是独立焦点目标，聚焦时 OK 触发按钮自身 onClick，互不拦截（兄弟节点，不在同一条
 * 预览链上）；背景卡聚焦时 ▲▼ 滚动长文。
 *
 * 模式语义（状态互斥，模式总以最后按下的按钮为准，装载即整列表替换）：
 * - 朗读（CONTINUOUS）：从 selected 句起顺序播放全诗，REPEAT_MODE_OFF，高亮随实际播放句走；
 *   家长设置"跟读模式=逐句暂停"（默认）时每句播完自动暂停在下一句开始前，OK 继续下一句；
 * - 逐句跟读（LINE_LOOP）：循环播放 selected 句，REPEAT_MODE_ONE，高亮=选中句，
 *   此时 ◀▶ 即时切换循环句（边移动边读）；连读下 ◀▶ 只移动"从哪句开始"的光标，不打断播放。
 * 两种模式下暂停都不清除高亮。播放倍率取家长设置（默认 1.0，作用在音频 0.85 生成基准之上）。
 */
@Composable
fun DetailScreen(poemId: String, onBack: () -> Unit) {
    val g = AppGraph.instance
    val poem = remember(poemId) { g.poems.byId(poemId) }
    if (poem == null) {
        // 路由参数异常的兜底，正常导航不会走到
        CreamBackground {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到这首诗", color = T.TextSubtle)
            }
        }
        return
    }

    val total = remember { g.poems.loadAll().size }
    val lineCount = poem.lines.size
    val scope = rememberCoroutineScope()

    // B 键返回 = 系统返回键，显式接 onBack 保持路由契约
    BackHandler(onBack = onBack)

    // 播放器：进屏创建、离屏释放
    val context = LocalContext.current
    val player = remember(poemId) { PoemPlayer(ExoPlayer.Builder(context).build()) }
    val currentLine by player.currentLine.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    var selected by remember { mutableStateOf(0) }   // 0-based 选中句（◀▶ 移动）
    var mode by remember { mutableStateOf(PoemPlayer.Mode.CONTINUOUS) }
    var started by remember { mutableStateOf(false) } // 本次进屏是否发起过播放（OK 未播放过=开始）
    var showMasteredDialog by remember { mutableStateOf(false) }
    var showAudioError by remember { mutableStateOf(false) }

    // 本诗进度（底部提示条）
    val progressState by remember(poemId) { g.progress.observeProgress(poemId) }.collectAsState(initial = null)
    val progress = progressState?.progress ?: 0

    // 打开事件（只打开不播放不落行，repo 内为显式接缝）；离屏释放播放器
    DisposableEffect(poemId) {
        scope.launch { g.progress.onPoemOpened(poemId) }
        onDispose { player.release() }
    }

    // 播到哪句记哪句（repo 对行号有集合去重+越界过滤，重复调用安全）
    LaunchedEffect(poemId) {
        player.currentLine.collect { line ->
            if (line > 0) g.progress.onLinePlayed(poemId, line - 1)
        }
    }

    // 播放失败 → 顶部提示条（常驻至离屏；复位 error 使后续失败可再次触发）
    LaunchedEffect(player) {
        player.error.collect { failed ->
            if (failed) {
                showAudioError = true
                player.error.value = false
            }
        }
    }

    // 初始焦点落诗行区；进屏自动落焦的那次不响提示音（Task 6 模式）
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(poem) { focusRequester.requestFocus() }
    val firstFocus = remember { mutableStateOf(true) }
    val tone: (Boolean) -> Unit = { focused ->
        if (focused) {
            if (firstFocus.value) firstFocus.value = false else g.keyTone.focusMove()
        }
    }

    // 按当前模式从 selected 句装载播放（模式切换即整列表替换，见类注释）；
    // 语速/跟读模式在启动时读 AppGraph 内存缓存（家长设置，AppGraph.startCollecting 常驻收集）
    fun startWithCurrentSelection() {
        started = true
        when (mode) {
            PoemPlayer.Mode.CONTINUOUS -> player.playContinuous(
                poemId, lineCount, selected + 1,
                speed = g.speedMultiplier,
                autoPauseBetweenLines = g.lineByLine
            )
            PoemPlayer.Mode.LINE_LOOP -> player.playLineLoop(poemId, selected + 1, speed = g.speedMultiplier)
        }
    }

    // ◀▶：移动选中句（coerce 到范围）；跟读模式下即时切句循环，连读下只移动光标
    fun moveSelection(delta: Int) {
        val next = (selected + delta).coerceIn(0, lineCount - 1)
        if (next == selected) return
        selected = next
        // 不判 started：mode==LINE_LOOP 蕴含 started（模式只能经"逐句跟读"按钮进入，按钮同时置 started）
        if (mode == PoemPlayer.Mode.LINE_LOOP) player.playLineLoop(poemId, selected + 1, speed = g.speedMultiplier)
    }

    // OK（诗行区聚焦时）：未发起过播放 → 按当前模式从 selected 句开始；否则播放/暂停
    fun okOnPoemArea() {
        if (!started) startWithCurrentSelection() else player.togglePause()
    }

    // 高亮句（0-based）：跟读=选中句；连读=实际播放句（currentLine 0=无高亮）；暂停不清除
    val highlightIdx = when (mode) {
        PoemPlayer.Mode.LINE_LOOP -> selected
        PoemPlayer.Mode.CONTINUOUS -> currentLine - 1
    }

    // 连读模式的开始位置光标：selected 尚未开始播放（currentLine==0）或已移到别句时，
    // 在选中句上以小圆点标出"朗读将从这句开始"（调用处保证与正在读高亮互斥）；
    // 跟读模式高亮即选中句，无需光标
    val showCursor = mode == PoemPlayer.Mode.CONTINUOUS && (currentLine == 0 || selected != currentLine - 1)

    val playStatus = when {
        !started -> ""
        isPlaying -> (if (mode == PoemPlayer.Mode.LINE_LOOP) "跟读中" else "朗读中") +
            " · 第 ${currentLine.coerceAtLeast(1)} 句"
        else -> "已暂停"
    }

    CreamBackground {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶栏：返回提示 | 诗名·朝代·诗人 | 序号
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "← 返回（B 键返回）",
                    Modifier.weight(1f),
                    color = T.TextSubtle, fontSize = 13.sp, maxLines = 1
                )
                Text(
                    "${poem.title} · ${poem.dynasty} · ${poem.poet}",
                    Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    color = T.TextTitle, fontSize = 22.sp, letterSpacing = 4.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1
                )
                Text(
                    "第 ${poem.order} / $total 首",
                    Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    color = T.TextSubtle, fontSize = 11.sp, maxLines = 1
                )
            }

            // 主区：左诗文跟读 + 操作按钮 | 右创作背景
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 诗行区 = 一个焦点目标（初始焦点落这里），◀▶/OK 在其聚焦时生效
                    Column(
                        Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged { tone(it.isFocused) }
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionLeft -> { moveSelection(-1); true }
                                    Key.DirectionRight -> { moveSelection(1); true }
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { okOnPoemArea(); true }
                                    else -> false   // 其余按键（含 B/返回）交还系统
                                }
                            }
                            .focusable(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        poem.lines.forEachIndexed { i, line ->
                            PoemLine(line, highlighted = i == highlightIdx, cursor = showCursor && i == selected)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FocusablePillButton(
                                "朗读", Icons.Filled.PlayArrow, filled = true,
                                onClick = {
                                    mode = PoemPlayer.Mode.CONTINUOUS
                                    startWithCurrentSelection()
                                },
                                onFocusChange = tone
                            )
                            FocusablePillButton(
                                "逐句跟读", Icons.Filled.Replay, filled = false,
                                onClick = {
                                    mode = PoemPlayer.Mode.LINE_LOOP
                                    startWithCurrentSelection()
                                },
                                onFocusChange = tone
                            )
                            FocusablePillButton(
                                "我会背了", Icons.Filled.ThumbUp, filled = false,
                                onClick = { showMasteredDialog = true },
                                onFocusChange = tone
                            )
                        }
                        if (playStatus.isNotEmpty()) {
                            Spacer(Modifier.weight(1f))
                            Text(playStatus, color = T.TextSubtle, fontSize = 12.sp)
                        }
                    }
                }

                // 右：创作背景（可聚焦：▲▼ 滚动长文——Task 14 更长文本也可达；
                // 内容未溢出时 ▲▼ 不消费，交还焦点系统自然移出）
                val bgScroll = rememberScrollState()
                val bgScrollStep = with(LocalDensity.current) { 60.dp.toPx() }
                var bgFocused by remember { mutableStateOf(false) }
                FocusCard(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged {
                            bgFocused = it.isFocused
                            tone(it.isFocused)
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionUp ->
                                    if (bgScroll.canScrollBackward) {
                                        scope.launch { bgScroll.scrollBy(-bgScrollStep) }
                                        true
                                    } else false
                                Key.DirectionDown ->
                                    if (bgScroll.canScrollForward) {
                                        scope.launch { bgScroll.scrollBy(bgScrollStep) }
                                        true
                                    } else false
                                else -> false
                            }
                        }
                        .focusable(),
                    focused = bgFocused
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                            .verticalScroll(bgScroll),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🌙 诗人为什么写这首诗？", color = T.Orange, fontSize = 13.sp, letterSpacing = 2.sp)
                        Text(poem.background, color = T.TextBody, lineHeight = 26.sp)
                    }
                }
            }

            KeyHintBar("◀ ▶ 逐句移动　OK 播放/暂停　B 返回", "已学进度 $progress%")
        }

        // 播放失败提示条（覆盖在顶部，不挤压布局；常驻至离屏）
        if (showAudioError) {
            Text(
                "音频加载失败，请检查语音包",
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .background(T.ChipBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                color = T.Orange, fontSize = 13.sp
            )
        }
    }

    // 我会背了 → 确认对话框（确认=标记会背 / 取消=再听听）
    if (showMasteredDialog) {
        // 安全默认焦点：M3 对话框默认聚焦确认按钮（标记会背=不可逆动作），
        // 等对话框窗口挂载后把初始焦点改落到"再听听"
        val safeFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            safeFocus.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showMasteredDialog = false },
            title = { Text("我会背了", color = T.TextTitle) },
            text = { Text("确定已经会背这首诗了吗？", color = T.TextBody) },
            confirmButton = {
                TextButton(onClick = {
                    showMasteredDialog = false
                    scope.launch { g.progress.markMastered(poemId) }
                }) { Text("标记会背") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showMasteredDialog = false },
                    modifier = Modifier.focusRequester(safeFocus)
                ) { Text("再听听") }
            }
        )
    }
}
