package com.rakor.tangshi.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.audio.PoemPlayer
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.components.PoemLine
import com.rakor.tangshi.ui.components.cardBorder
import com.rakor.tangshi.ui.components.cardColors
import com.rakor.tangshi.ui.components.cardRestingShadow
import com.rakor.tangshi.ui.components.cardShape
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.highlightPillBorder
import com.rakor.tangshi.ui.components.highlightPillColors
import com.rakor.tangshi.ui.components.normalPillBorder
import com.rakor.tangshi.ui.components.normalPillColors
import com.rakor.tangshi.ui.theme.KaiTi
import com.rakor.tangshi.ui.theme.T
import kotlinx.coroutines.launch

/**
 * 详情学习页（规格 5.2）。VM-in-screen：本屏逻辑薄（播放器壳透传 ExoPlayer、进度直连
 * ProgressRepository），状态全部 hoist 在组合内，不设独立 ViewModel。
 *
 * 焦点模型：诗行区是一个焦点目标（初始焦点落这里），◀▶/OK 按键仅在其聚焦时被
 * onPreviewKeyEvent 接管并消费（后退键等其余按键交还系统）；操作按钮与右侧背景卡
 * 是独立焦点目标，聚焦时 OK 触发按钮自身 onClick，互不拦截（兄弟节点，不在同一条
 * 预览链上）；背景卡聚焦时 ▲▼ 滚动长文。诗行卡聚焦时 ▲▼ 同样优先滚动诗文（长诗浏览），
 * 滚到顶/底后交还焦点系统移焦点；◀▶ 选句与连读推进时视口自动跟随到目标行。
 *
 * 播放语义（开关 + OK 两键模型，替代原"朗读整首/逐句跟读"）：
 * - 连读：循环模式开关（切换按钮，选中常驻 highlight）——只切模式不立即播放；
 *   选中时按 OK 播放不停句，一句接一句自动推进，读到末句回卷句首往复循环；
 * - OK（诗行区聚焦）：空闲 → 连读开=整首循环播放（第 1 句起），连读关=朗读箭头所指句
 *   （播完即停）；播放中 → 停止（回空闲，清高亮）；
 * - 原"朗读"按钮已移除，单句朗读由 OK 承担。
 * 箭头（selected）：空闲=朗读目标；连读中自动跟随播放位置；单句播放中可移开指定下一句目标。
 * 切诗：顶栏"第 N 首"两侧 ◀▶ 芯片（首/末尾对应侧禁用）或遥控频道键 CH-/CH+；切换即重建
 * 播放器与进度观察，光标/滚动复位，焦点落回诗行区。
 * 原家长设置"跟读模式（逐句暂停/连续播放）"随本模型移除。
 * 播放倍率取家长设置（默认 1.0，作用在音频 0.85 生成基准之上）。
 */
/** 指示槽宽度：容纳"正在读"喇叭图标与起始光标箭头，两者同挂此槽、贴槽右紧邻诗句 */
private val INDICATOR_SLOT = 60.dp

// bringIntoView（选句视口跟随）在 foundation 1.7 仍标 @ExperimentalFoundationApi，行为已稳定
@OptIn(ExperimentalFoundationApi::class)
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

    // 诗行区滚动：切诗复位。诗行卡聚焦时 ▲▼ 滚动长诗（滚到头/顶 ▲▼ 交还焦点系统移焦点，
    // 与右背景卡同款交互）；选句（◀▶）与连读推进时视口自动跟随到目标行
    val poemScroll = remember(currentPoemId) { ScrollState(0) }
    val poemScrollStep = with(LocalDensity.current) { 120.dp.toPx() }

    // 后退键返回 = 系统返回键，显式接 onBack 保持路由契约
    BackHandler(onBack = onBack)

    // 播放器：进屏创建、离屏释放、切诗重建
    val context = LocalContext.current
    val player = remember(currentPoemId) { PoemPlayer(ExoPlayer.Builder(context).build()) }
    val currentLine by player.currentLine.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val shape by player.shape.collectAsState()

    var selected by remember(currentPoemId) { mutableStateOf(0) }   // 0-based 箭头句：空闲=朗读目标；连读=跟随播放位置
    // 连读开关：屏内生命周期（切诗不复位，进屏默认关）。只切模式不触发播放，OK 播放时按它决定循环/单句
    var loopEnabled by remember { mutableStateOf(false) }
    var showMasteredDialog by remember { mutableStateOf(false) }
    var showAudioError by remember { mutableStateOf(false) }

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
        player.playLineOnce(currentPoemId, selected + 1)
    }

    // ◀▶：移动箭头句（coerce 到范围）；连读中 = 循环内跳句继续，空闲/单句时仅移动朗读目标
    fun moveSelection(delta: Int) {
        val next = (selected + delta).coerceIn(0, lineCount - 1)
        if (next == selected) return
        selected = next
        if (shape == PoemPlayer.Shape.LOOP) player.seekToLine(selected + 1)
    }

    // OK（诗行区聚焦时）：空闲 → 连读开=整首循环（第 1 句起），连读关=朗读箭头句；播放中 → 停止
    fun okOnPoemArea() {
        if (shape != PoemPlayer.Shape.IDLE) {
            player.stopPlayback()
        } else if (loopEnabled) {
            selected = 0
            player.playContinuousLoop(currentPoemId, lineCount)
        } else {
            readSelectedLine()
        }
    }

    // 高亮句（0-based）：播放中显示所在句（连读随播放推进），空闲（OK 停止）清除
    val highlightIdx = if (shape != PoemPlayer.Shape.IDLE && currentLine > 0) currentLine - 1 else -1

    // 连读模式的开始位置光标：selected 尚未开始播放（currentLine==0）或已移到别句时，
    // 在选中句上以小圆点标出"朗读将从这句开始"（调用处保证与正在读高亮互斥）；
    // 跟读模式高亮即选中句，无需光标
    // 箭头：空闲时恒显示（朗读目标）；播放中与播放位置不重合（用户移开）时显示
    val showCursor = shape == PoemPlayer.Shape.IDLE || selected != currentLine - 1

    val playStatus = when {
        shape == PoemPlayer.Shape.IDLE -> ""
        isPlaying -> (if (shape == PoemPlayer.Shape.LOOP) "连读中" else "朗读中") +
            " · 第 ${currentLine.coerceAtLeast(1)} 句"
        else -> "加载中…"
    }

    CreamBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶栏：返回提示 | 诗名·朝代·诗人 | ◀ 上一首 · 序号 · 下一首 ▶
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "← 返回（后退键返回）",
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
                    // 图标而非 ◀▶ 文字——老盒子（小米盒子4/Android 6）系统字体缺几何符号字形
                    // 官方 disabled Surface 仍可聚焦（官方设计），必须显式 canFocus=false 保持"跳过禁用项"
                    Button(
                        onClick = { prevPoem?.let { currentPoemId = it.id } },
                        enabled = prevPoem != null,
                        colors = normalPillColors(),
                        border = normalPillBorder(),
                        modifier = Modifier
                            .focusTone(tone)
                            .focusProperties { canFocus = prevPoem != null }
                    ) { TvIcon(Icons.Filled.ChevronLeft, null) }
                    Text(
                        "第 ${poem.order} / ${poems.size} 首",
                        color = T.TextSubtle, fontSize = 11.sp, maxLines = 1
                    )
                    Button(
                        onClick = { nextPoem?.let { currentPoemId = it.id } },
                        enabled = nextPoem != null,
                        colors = normalPillColors(),
                        border = normalPillBorder(),
                        modifier = Modifier
                            .focusTone(tone)
                            .focusProperties { canFocus = nextPoem != null }
                    ) { TvIcon(Icons.Filled.ChevronRight, null) }
                }
            }

            // 主区：左诗文跟读 + 操作按钮 | 右创作背景
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 诗行卡 = 一个焦点目标（初始焦点落这里，聚焦橙描边），◀▶/OK 在其聚焦时生效
                    Card(
                        // OK 只走下方 onPreviewKeyEvent 单路径（KeyDown 消费，Enter/NumPadEnter 一并覆盖）。
                        // onClick 必须是空操作：preview 对 KeyUp 返回 false 不消费，官方 handleDPadEnter 会在
                        // KeyUp 上触发 onClick——若此处挂 okOnPoemArea 会双调用（第二次常读到已离开 IDLE 的
                        // 状态而走 stopPlayback，把刚起的播放掐死，2026-10-12 实测双日志确认）。
                        onClick = {},
                        modifier = Modifier
                            .cardRestingShadow(20)
                            .weight(1f)
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .focusTone(tone)
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionLeft -> { moveSelection(-1); true }
                                    Key.DirectionRight -> { moveSelection(1); true }
                                    // ▲▼ 优先滚动诗文（长诗浏览）；滚到顶/底后交还焦点系统移焦点
                                    Key.DirectionUp ->
                                        if (poemScroll.canScrollBackward) {
                                            scope.launch { poemScroll.scrollBy(-poemScrollStep) }
                                            true
                                        } else false
                                    Key.DirectionDown ->
                                        if (poemScroll.canScrollForward) {
                                            scope.launch { poemScroll.scrollBy(poemScrollStep) }
                                            true
                                        } else false
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { okOnPoemArea(); true }
                                    Key.ChannelUp -> { nextPoem?.let { currentPoemId = it.id }; true }    // CH+ 下一首
                                    Key.ChannelDown -> { prevPoem?.let { currentPoemId = it.id }; true }  // CH- 上一首
                                    else -> false   // 其余按键（含后退键）交还系统
                                }
                            },
                        shape = cardShape(20),
                        colors = cardColors(),
                        border = cardBorder(20),
                        // 满屏大卡不缩放（官方默认 1.1 会溢出列布局）；焦点提示靠官方 3dp 描边
                        scale = CardDefaults.scale(focusedScale = 1f),
                    ) {
                        // 诗块整体在卡内居中；行间仍保持同一起点（不逐行居中），字格纵向对齐。
                        // 块宽含左侧 60dp 指示槽，居中会让文字右偏半槽，offset 抵消
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                Modifier
                                    .offset(x = -INDICATOR_SLOT / 2)
                                    .verticalScroll(poemScroll)
                            ) {
                                poem.lines.forEachIndexed { i, line ->
                                // 选句（◀▶）/连读推进 → 视口跟随到目标行（最小滚动使其可见；
                                // 手动 ▲▼ 浏览不改 selected，不受影响）
                                val bring = remember { BringIntoViewRequester() }
                                LaunchedEffect(selected) {
                                    if (i == selected) bring.bringIntoView()
                                }
                                Row(
                                    Modifier.bringIntoViewRequester(bring),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    // 指示槽：常驻固定宽度。"正在读"喇叭图标与起始光标箭头均贴槽右
                                    // （紧邻诗句，不会伸出卡片），诗字起点恒定，不随标记出现或逐句移动而横跳
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
                                            // "正在读"标记用单个矢量图标（喇叭）：文字版贴槽左会随
                                            // 诗块 offset 伸出卡片左边界；贴槽右紧邻诗字恒在卡内，
                                            // 与选句箭头逻辑互斥（同行不会同时出现）
                                            Icon(
                                                Icons.Filled.VolumeUp, null,
                                                Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(end = 2.dp, bottom = 6.dp)
                                                    .size(16.dp),
                                                tint = T.Orange
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
                            // 连读：循环模式开关（切换按钮）——选中时 OK 播放整首循环（末句回卷往复，
                            // 再按 OK 停止）；未选中时 OK 朗读箭头单句。只切模式，不立即播放/停止
                            Button(
                                onClick = { loopEnabled = !loopEnabled },
                                colors = if (loopEnabled) highlightPillColors() else normalPillColors(),
                                border = if (loopEnabled) highlightPillBorder() else normalPillBorder(),
                                modifier = Modifier.focusTone(tone)
                            ) {
                                TvIcon(
                                    if (loopEnabled) Icons.Filled.Check else Icons.Filled.Replay,
                                    null
                                )
                                Spacer(Modifier.width(6.dp))
                                TvText("连读")
                            }
                            Button(
                                onClick = { showMasteredDialog = true },
                                colors = normalPillColors(),
                                border = normalPillBorder(),
                                modifier = Modifier.focusTone(tone)
                            ) {
                                TvIcon(Icons.Filled.ThumbUp, null)
                                Spacer(Modifier.width(6.dp))
                                TvText("我会背了")
                            }
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
                Card(
                    onClick = {},   // OK 无动作（现状即如此）；官方 Card 需要 onClick 才有焦点态
                    modifier = Modifier
                        .cardRestingShadow(20)
                        .weight(1f)
                        .fillMaxHeight()
                        .focusTone(tone)
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
                        },
                    shape = cardShape(20),
                    colors = cardColors(),
                    border = cardBorder(20),
                    scale = CardDefaults.scale(focusedScale = 1f),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 22.dp, vertical = 18.dp)
                            .verticalScroll(bgScroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📖 释义", color = T.Orange, fontSize = 15.sp, letterSpacing = 2.sp)
                        Text(poem.meaning, color = T.TextBody, fontSize = 16.sp, lineHeight = 28.sp)
                        Text("🌙 背景", color = T.Orange, fontSize = 15.sp, letterSpacing = 2.sp)
                        Text(poem.background, color = T.TextBody, fontSize = 16.sp, lineHeight = 28.sp)
                    }
                }
            }

            KeyHintBar("← → 逐句移动　OK 播放/停止　后退键 返回", "顶栏 ← → 或 CH± 切换上一首/下一首 · 已学进度 $progress%")
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
        // 配色同清除学习进度对话框：交给 TangshiColorScheme，调用点只定圆角
        AlertDialog(
            onDismissRequest = { showMasteredDialog = false },
            title = { Text("我会背了", color = T.TextTitle) },
            shape = RoundedCornerShape(18.dp),
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
