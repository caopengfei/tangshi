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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.rakor.tangshi.ui.theme.KaiTi
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
 * 播放语义（两键模型，替代原"朗读整首/逐句跟读"）：
 * - 朗读：只朗读箭头所指句，播完即停、不切下一句（播完回空闲，箭头回到目标位置）；
 *   连读生效时禁用（灰显、跳过焦点）。
 * - 连读：整首循环开关——从第 1 句顺序播到末句后回卷句首，往复直到再按一次"连读"停止；
 *   生效时按钮 highlight 常驻强调，◀▶ 为循环内跳句。
 * - OK（诗行区聚焦）：空闲 → 朗读箭头句；播放/暂停中 → 播放/暂停。
 * 箭头（selected）：空闲=朗读目标；连读中自动跟随播放位置；单句播放中可移开指定下一句目标。
 * 切诗：顶栏"第 N 首"两侧 ◀▶ 芯片（首/末尾对应侧禁用）或遥控频道键 CH-/CH+；切换即重建
 * 播放器与进度观察，光标/滚动复位，焦点落回诗行区。
 * 原家长设置"跟读模式（逐句暂停/连续播放）"随本模型移除。
 * 两种模式下暂停都不清除高亮。播放倍率取家长设置（默认 1.0，作用在音频 0.85 生成基准之上）。
 */
/** 指示槽宽度：容纳"◀ 正在读"徽章（12sp 约占 52dp），起始光标圆点同挂此槽 */
private val INDICATOR_SLOT = 60.dp

@Composable
fun DetailScreen(poemId: String, onBack: () -> Unit) {
    val g = AppGraph.instance
    // 页内当前诗：顶栏 ◀▶ 芯片 / 频道键切换时改写；播放器、进度观察、光标均随 key 重建复位
    var currentPoemId by remember { mutableStateOf(poemId) }
    val poems = remember { g.poems.loadAll() }
    val poemIndex = poems.indexOfFirst { it.id == currentPoemId }
    val prevPoem = poems.getOrNull(poemIndex - 1)
    val nextPoem = poems.getOrNull(poemIndex + 1)
    val poem = remember(currentPoemId) { g.poems.byId(currentPoemId) }
    if (poem == null) {
        // 路由参数异常的兜底，正常导航不会走到
        CreamBackground {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到这首诗", color = T.TextSubtle)
            }
        }
        return
    }

    val lineCount = poem.lines.size
    val scope = rememberCoroutineScope()

    // B 键返回 = 系统返回键，显式接 onBack 保持路由契约
    BackHandler(onBack = onBack)

    // 播放器：进屏创建、离屏释放、切诗重建
    val context = LocalContext.current
    val player = remember(currentPoemId) { PoemPlayer(ExoPlayer.Builder(context).build()) }
    val currentLine by player.currentLine.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val shape by player.shape.collectAsState()

    var selected by remember(currentPoemId) { mutableStateOf(0) }   // 0-based 箭头句：空闲=朗读目标；连读=跟随播放位置
    var showMasteredDialog by remember { mutableStateOf(false) }
    var showAudioError by remember { mutableStateOf(false) }
    var poemAreaFocused by remember { mutableStateOf(false) }   // 诗行卡聚焦视觉（橙描边）

    // 本诗进度（底部提示条）
    val progressState by remember(currentPoemId) { g.progress.observeProgress(currentPoemId) }.collectAsState(initial = null)
    val progress = progressState?.progress ?: 0

    // 打开事件（只打开不播放不落行，repo 内为显式接缝）；离屏释放播放器
    DisposableEffect(currentPoemId) {
        scope.launch { g.progress.onPoemOpened(currentPoemId) }
        onDispose { player.release() }
    }

    // 播到哪句记哪句（repo 对行号有集合去重+越界过滤，重复调用安全）
    LaunchedEffect(currentPoemId) {
        player.currentLine.collect { line ->
            if (line > 0) g.progress.onLinePlayed(currentPoemId, line - 1)
        }
    }

    // 连读中播放推进 → 箭头跟随到当前句（光标=播放位置）；单句朗读的箭头即目标句，无需跟随
    LaunchedEffect(currentLine) {
        if (shape == PoemPlayer.Shape.LOOP && currentLine > 0) selected = currentLine - 1
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

    // 朗读：只读箭头所指句（selected），播完即停，不切下一句
    fun readSelectedLine() {
        player.playLineOnce(currentPoemId, selected + 1, speed = g.speedMultiplier)
    }

    // ◀▶：移动箭头句（coerce 到范围）；连读中 = 循环内跳句继续，空闲/单句时仅移动朗读目标
    fun moveSelection(delta: Int) {
        val next = (selected + delta).coerceIn(0, lineCount - 1)
        if (next == selected) return
        selected = next
        if (shape == PoemPlayer.Shape.LOOP) player.seekToLine(selected + 1)
    }

    // OK（诗行区聚焦时）：空闲 → 朗读箭头句；播放/暂停中 → 播放/暂停
    fun okOnPoemArea() {
        if (shape == PoemPlayer.Shape.IDLE) readSelectedLine()
        else player.togglePause()
    }

    // 高亮句（0-based）：播放/暂停中显示所在句（连读随播放推进；暂停不清除），空闲清除
    val highlightIdx = if (shape != PoemPlayer.Shape.IDLE && currentLine > 0) currentLine - 1 else -1

    // 连读模式的开始位置光标：selected 尚未开始播放（currentLine==0）或已移到别句时，
    // 在选中句上以小圆点标出"朗读将从这句开始"（调用处保证与正在读高亮互斥）；
    // 跟读模式高亮即选中句，无需光标
    // 箭头：空闲时恒显示（朗读目标）；播放/暂停中与播放位置不重合（用户移开）时显示
    val showCursor = shape == PoemPlayer.Shape.IDLE || selected != currentLine - 1

    val playStatus = when {
        shape == PoemPlayer.Shape.IDLE -> ""
        isPlaying -> (if (shape == PoemPlayer.Shape.LOOP) "连读中" else "朗读中") +
            " · 第 ${currentLine.coerceAtLeast(1)} 句"
        else -> "已暂停"
    }

    CreamBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶栏：返回提示 | 诗名·朝代·诗人 | ◀ 上一首 · 序号 · 下一首 ▶
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "← 返回（B 键返回）",
                    Modifier.weight(1f),
                    color = T.TextSubtle, fontSize = 13.sp, maxLines = 1
                )
                Text(
                    "${poem.title} · ${poem.poet}",
                    Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    color = T.TextTitle, fontSize = 24.sp, letterSpacing = 4.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    fontFamily = KaiTi
                )
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                ) {
                    // 切诗芯片：到首/末尾时对应侧禁用；频道键 CH-/CH+ 为等价捷径
                    FocusablePillButton(
                        "◀",
                        enabled = prevPoem != null,
                        onClick = { prevPoem?.let { currentPoemId = it.id } },
                        onFocusChange = tone
                    )
                    Text(
                        "第 ${poem.order} / ${poems.size} 首",
                        color = T.TextSubtle, fontSize = 11.sp, maxLines = 1
                    )
                    FocusablePillButton(
                        "▶",
                        enabled = nextPoem != null,
                        onClick = { nextPoem?.let { currentPoemId = it.id } },
                        onFocusChange = tone
                    )
                }
            }

            // 主区：左诗文跟读 + 操作按钮 | 右创作背景
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 诗行卡 = 一个焦点目标（初始焦点落这里，聚焦橙描边），◀▶/OK 在其聚焦时生效
                    FocusCard(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                poemAreaFocused = it.isFocused
                                tone(it.isFocused)
                            }
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionLeft -> { moveSelection(-1); true }
                                    Key.DirectionRight -> { moveSelection(1); true }
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { okOnPoemArea(); true }
                                    Key.ChannelUp -> { nextPoem?.let { currentPoemId = it.id }; true }    // CH+ 下一首
                                    Key.ChannelDown -> { prevPoem?.let { currentPoemId = it.id }; true }  // CH- 上一首
                                    else -> false   // 其余按键（含 B/返回、▲▼焦点）交还系统
                                }
                            }
                            .focusable(),
                        focused = poemAreaFocused
                    ) {
                        // 诗块整体在卡内居中；行间仍保持同一起点（不逐行居中），字格纵向对齐。
                        // 块宽含左侧 60dp 指示槽，居中会让文字右偏半槽，offset 抵消
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(Modifier.offset(x = -INDICATOR_SLOT / 2)) {
                                poem.lines.forEachIndexed { i, line ->
                                Row(verticalAlignment = Alignment.Bottom) {
                                    // 指示槽：常驻固定宽度。"正在读"徽章贴槽左，起始光标圆点贴槽右
                                    // （紧邻诗句），诗字起点恒定，不随标记出现或逐句移动而横跳
                                    Box(Modifier.width(INDICATOR_SLOT)) {
                                        if (showCursor && i == selected) {
                                            Icon(
                                                Icons.Filled.PlayArrow, null,
                                                Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(end = 6.dp, bottom = 6.dp)
                                                    .size(18.dp),
                                                tint = T.Orange
                                            )
                                        }
                                        if (i == highlightIdx) {
                                            Text(
                                                "▶ 正在读",
                                                Modifier.align(Alignment.BottomStart),
                                                color = T.Orange, fontSize = 12.sp, maxLines = 1
                                            )
                                        }
                                    }
                                    PoemLine(line, highlighted = i == highlightIdx)
                                }
                            }
                        }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 朗读：只读箭头句；连读生效时禁用（灰显、不可聚焦）
                            FocusablePillButton(
                                "朗读", Icons.Filled.PlayArrow, highlight = true,
                                enabled = shape != PoemPlayer.Shape.LOOP,
                                onClick = { readSelectedLine() },
                                onFocusChange = tone
                            )
                            // 连读：整首循环开关——按一次开始（从第 1 句循环到末句回卷），再按一次停止
                            FocusablePillButton(
                                "连读", Icons.Filled.Replay,
                                highlight = shape == PoemPlayer.Shape.LOOP,
                                onClick = {
                                    if (shape == PoemPlayer.Shape.LOOP) {
                                        player.stopPlayback()
                                    } else {
                                        selected = 0
                                        player.playContinuousLoop(currentPoemId, lineCount, speed = g.speedMultiplier)
                                    }
                                },
                                onFocusChange = tone
                            )
                            FocusablePillButton(
                                "我会背了", Icons.Filled.ThumbUp,
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
                val bgScroll = remember(currentPoemId) { ScrollState(0) }
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
                                Key.ChannelUp -> { nextPoem?.let { currentPoemId = it.id }; true }    // CH+ 下一首
                                Key.ChannelDown -> { prevPoem?.let { currentPoemId = it.id }; true }  // CH- 上一首
                                else -> false
                            }
                        }
                        .focusable(),
                    focused = bgFocused
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 22.dp, vertical = 18.dp)
                            .verticalScroll(bgScroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📖 这首诗的意思", color = T.Orange, fontSize = 15.sp, letterSpacing = 2.sp)
                        Text(poem.meaning, color = T.TextBody, fontSize = 16.sp, lineHeight = 28.sp)
                        Text("🌙 诗人为什么写这首诗？", color = T.Orange, fontSize = 15.sp, letterSpacing = 2.sp)
                        Text(poem.background, color = T.TextBody, fontSize = 16.sp, lineHeight = 28.sp)
                    }
                }
            }

            KeyHintBar("◀ ▶ 逐句移动　OK 播放/暂停　B 返回", "顶栏 ◀▶ 或 CH± 切换上一首/下一首 · 已学进度 $progress%")
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
                    scope.launch { g.progress.markMastered(currentPoemId) }
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
