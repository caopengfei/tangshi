package com.rakor.tangshi.ui.screens.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.audio.PoemPlayer
import com.rakor.tangshi.data.model.Line
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.game.QuizGenerator
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.components.PoemCharCell
import com.rakor.tangshi.ui.components.PoemLine
import com.rakor.tangshi.ui.components.POEM_CHAR_CELL
import com.rakor.tangshi.ui.components.PlayerLifecycleEffect
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.normalPillBorder
import com.rakor.tangshi.ui.components.normalPillColors
import com.rakor.tangshi.ui.theme.KaiTi
import com.rakor.tangshi.ui.theme.T
import kotlinx.coroutines.launch

/** 听音题可重听次数（自动播放一次不计入） */
private const val MAX_REPLAYS = 3

/** 选项块作答反馈 */
private enum class Feedback { NORMAL, CORRECT, WRONG }

/**
 * 背诗小游戏 · 题面页（规格 5.6）。mode/scope 从路由参数来。
 * - A 填空补诗：诗全文（目标行缺字为橙色"？"方块，该字拼音不显示以免提示答案）+ 4 枚候选字卡；
 * - B 听音辨诗：进题自动循环朗读目标句（可重听 3 次）+ 诗名选项卡。
 *
 * 播放器生命周期：进屏创建 PoemPlayer、离屏 release；每题装载 playLineLoop 循环到作答，
 * 作答即 pause，切题重新装载；后退键/顶栏 ← 返回 = 放弃本轮回玩法选择页（BackHandler）。
 * 答题判定与推进在 [GameViewModel]：作答后 800ms 反馈（答对块变绿/答错块标红）自动进下一题，
 * 一轮完成时置 DONE 并由本屏导航结算（携带答对数与当日连续学习天数）。
 */
@Composable
fun GameQuestionScreen(
    mode: String,
    scope: String,
    onBack: () -> Unit,
    onFinished: (correct: Int, streakDays: Int) -> Unit
) {
    val vm = gameViewModel()
    val g = AppGraph.instance
    val state by vm.state.collectAsState()

    // 进屏开局（题池不足 → NEED_MORE 空态）
    LaunchedEffect(mode, scope) { vm.start(mode, scope) }

    // 后退键返回 = 放弃本轮回玩法选择页
    BackHandler(onBack = onBack)

    // 一轮完成 → 结算（correct 判星与文案；streak=VM 落账后新鲜读取的当日连续学习天数）
    LaunchedEffect(state.phase) {
        if (state.phase == GamePhase.DONE) onFinished(state.correct, state.finalStreak)
    }

    // 播放器：进屏创建、离屏释放（听音题用；填空题创建但闲置，开销可忽略）
    val context = LocalContext.current
    val player = remember { PoemPlayer(ExoPlayer.Builder(context).build()) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    // 退到后台（主页键/切应用/息屏）自动暂停听音题循环朗读、回前台续播；循环朗读/待作答期间
    // 窗口常亮（同详情页，见 PlayerLifecycleEffect）
    PlayerLifecycleEffect(player)
    var showAudioError by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        player.error.collect { failed ->
            if (failed) {
                showAudioError = true
                player.error.value = false
            }
        }
    }

    // 进屏自动落焦的那次不响提示音（Task 6 模式）
    val firstFocus = remember { mutableStateOf(true) }
    // 填空题诗块是否溢出视口（长诗才在提示条里出现 ▲▼ 翻诗）——由 FillBody 实测上报
    var poemScrollable by remember(state.index) { mutableStateOf(false) }
    val tone: (Boolean) -> Unit = { focused ->
        if (focused) {
            if (firstFocus.value) firstFocus.value = false else g.keyTone.focusMove()
        }
    }

    CreamBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state.phase) {
                GamePhase.IDLE -> Box(Modifier.weight(1f).fillMaxWidth())   // 开局生成中（一帧内）

                GamePhase.NEED_MORE -> {
                    // 题池不足兜底空态（正常路径已被玩法选择页拦截）
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val backFocus = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            withFrameNanos { }
                            backFocus.requestFocus()
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "先去学几首诗再来吧！",
                                color = T.TextTitle, fontSize = 20.sp,
                                letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold
                            )
                            Button(
                                onClick = onBack,
                                colors = normalPillColors(),
                                border = normalPillBorder(),
                                modifier = Modifier.focusTone(tone).focusRequester(backFocus)
                            ) { TvText("返回") }
                        }
                    }
                }

                GamePhase.PLAYING, GamePhase.DONE -> {
                    // 顶栏：题型·第 X 题 | 连对 | 返回提示
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (state.mode == GameViewModel.MODE_FILL) "填空补诗" else "听音辨诗") +
                                " · 第 ${state.index + 1} 题",
                            Modifier.weight(1f),
                            fontSize = 18.sp, letterSpacing = 3.sp,
                            color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Icon(Icons.Filled.ThumbUp, null, Modifier.size(14.dp), tint = T.Orange)
                        Spacer(Modifier.width(4.dp))
                        Text("连对 ${state.streakInRound} 题", color = T.TextSubtle, fontSize = 12.sp)
                        Spacer(Modifier.width(16.dp))
                        Text("← 返回", color = T.TextSubtle, fontSize = 13.sp, maxLines = 1)
                    }

                    val question = state.questions.getOrNull(state.index)
                    if (question != null) {
                        if (state.mode == GameViewModel.MODE_FILL) {
                            val q = (question as Question.Fill).q
                            FillBody(
                                q = q,
                                index = state.index,
                                answered = state.answered,
                                wasCorrect = state.answerWasCorrect,
                                picked = state.picked?.firstOrNull(),
                                modifier = Modifier.weight(1f),
                                tone = tone,
                                onAnswer = vm::answerFill,
                                onScrollableChange = { poemScrollable = it }
                            )
                        } else {
                            val q = (question as Question.Listen).q
                            ListenBody(
                                q = q,
                                index = state.index,
                                answered = state.answered,
                                wasCorrect = state.answerWasCorrect,
                                pickedId = state.picked,
                                player = player,
                                modifier = Modifier.weight(1f),
                                tone = tone,
                                onAnswer = vm::answerListen
                            )
                        }
                    }
                }
            }

            // 底部按键提示（按题型区分）
            when (state.phase) {
                GamePhase.PLAYING, GamePhase.DONE -> KeyHintBar(
                    if (state.mode == GameViewModel.MODE_FILL) {
                        if (poemScrollable) "← → 选择　▲▼ 翻诗　OK 确认" else "← → 选择　OK 确认"
                    } else "OK 播放　← → 选诗"
                )
                GamePhase.NEED_MORE -> KeyHintBar("OK 返回")
                GamePhase.IDLE -> {}
            }
        }

        // 播放失败提示条（覆盖顶部；语音包缺失时听音题无法作答，可返回放弃本轮）
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
}

/**
 * A 填空题面：诗全文 + 候选字。非目标行复用共享 PoemLine；目标行由 [BlankLine] 渲染
 * （缺字位换橙色"？"方块且不显示拼音，避免拼音提示答案）。初始焦点落第一枚候选。
 * 题目切换的焦点复位按 index 键控（data-class 相等的相邻题目也能重新触发）。
 * 诗库最长 120 句（长恨歌），诗全文区可滚动并自动把缺字句带进视口——见下方注释。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FillBody(
    q: QuizGenerator.FillBlank,
    index: Int,
    answered: Boolean,
    wasCorrect: Boolean,
    picked: Char?,
    modifier: Modifier = Modifier,
    tone: (Boolean) -> Unit,
    onAnswer: (Char) -> Unit,
    onScrollableChange: (Boolean) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    // 诗块可滚动：容器高度被 weight(1f) 框定，诗长于视口时（诗库最长 120 句）必须能滚，
    // 否则超出部分出屏。与详情页诗区同一套做法（verticalScroll + bringIntoView 跟随）。
    val poemScroll = remember(index) { ScrollState(0) }
    val scrollStep = with(LocalDensity.current) { 120.dp.toPx() }
    val scope = rememberCoroutineScope()
    // 缺字行的视口跟随器：每题只挂一个，挂在缺字那一行上（见下方 Box）
    val blankBring = remember(index) { BringIntoViewRequester() }
    // 把缺字句带进视口（最小滚动：已经在视口里就不滚）。
    // 必须等诗区高度定稿再滚——首帧测量期间调用会误滚整整一行，
    // 表现为进题时诗的第一句被顶出视口。
    LaunchedEffect(index) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { blankBring.bringIntoView() }
    }
    // 实测溢出后上报（提示条据此才出现 ▲▼ 翻诗）——不用句数阈值硬猜视口容量。
    // key 必须带 index：父层状态按题目 index 重置为 false，若相邻两题都可滚，
    // 只 key scrollable 则值不变、effect 不重跑，提示条就不会出现。
    val scrollable = poemScroll.canScrollForward || poemScroll.canScrollBackward
    LaunchedEffect(index, scrollable) { onScrollableChange(scrollable) }
    LaunchedEffect(index) {
        withFrameNanos { }
        repeat(3) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            // 焦点在 4 枚候选字上（诗块本身不可聚焦），故 ▲▼ 在本层拦截做翻诗；
            // 短诗滚不动时不消费，交还焦点系统，行为与改动前一致。
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> if (poemScroll.canScrollBackward) {
                        scope.launch { poemScroll.scrollBy(-scrollStep) }; true
                    } else false
                    Key.DirectionDown -> if (poemScroll.canScrollForward) {
                        scope.launch { poemScroll.scrollBy(scrollStep) }; true
                    } else false
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 诗全文（重量占位；短诗垂直居中，长诗滚到缺字句）
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(Modifier.verticalScroll(poemScroll)) {
                q.poem.lines.forEachIndexed { i, line ->
                    Box(
                        if (i == q.lineIndex) Modifier.bringIntoViewRequester(blankBring) else Modifier
                    ) {
                        if (i == q.lineIndex) BlankLine(line, q.charIndex) else PoemLine(line, highlighted = false)
                    }
                }
            }
        }

        // 候选字 4 枚（答对变绿 / 答错标红，800ms 后由 VM 推进下一题）
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            q.options.forEachIndexed { j, c ->
                val feedback = when {
                    answered && wasCorrect && c == picked -> Feedback.CORRECT
                    answered && !wasCorrect && c == picked -> Feedback.WRONG
                    else -> Feedback.NORMAL
                }
                OptionBlock(
                    feedback = feedback,
                    modifier = Modifier.then(if (j == 0) Modifier.focusRequester(focusRequester) else Modifier),
                    onClick = { if (!answered) onAnswer(c) },
                    onFocusChange = tone
                ) {
                    Text(
                        c.toString(),
                        Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        fontSize = 22.sp, letterSpacing = 2.sp,
                        color = if (feedback == Feedback.CORRECT) Color.White else T.TextTitle,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 目标行：第 charIndex 字替换为橙色"？"方块（尺寸与字同大）；该字拼音不显示，不提示答案 */
@Composable
private fun BlankLine(line: Line, charIndex: Int) {
    // 水平 12dp 与 PoemLine 的行内边距一致；单字格同用 POEM_CHAR_CELL，目标行与其余诗行对齐
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.Bottom) {
        line.chars.forEachIndexed { j, cd ->
            if (j == charIndex) {
                Column(
                    Modifier.width(POEM_CHAR_CELL),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(" ", fontSize = 11.sp)   // 拼音行占位，保持与其余字对齐
                    Box(
                        Modifier
                            .background(T.Orange, RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("？", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                PoemCharCell(cd.py, cd.c)
            }
        }
    }
}

/**
 * B 听音题面：大播放圆钮（进题自动循环朗读，作答即停；可重听 3 次，用尽置灰）
 * + 提示语 + 诗名选项卡。初始焦点落播放钮；切题重新装载朗读并重置重听次数。
 * 播放装载/重听次数/焦点复位均按 index 键控（data-class 相等的相邻题目也能重新触发）。
 */
@Composable
private fun ListenBody(
    q: QuizGenerator.Listen,
    index: Int,
    answered: Boolean,
    wasCorrect: Boolean,
    pickedId: String?,
    player: PoemPlayer,
    modifier: Modifier = Modifier,
    tone: (Boolean) -> Unit,
    onAnswer: (String) -> Unit
) {
    var replaysLeft by remember { mutableStateOf(MAX_REPLAYS) }

    // 进题自动播放（循环到作答）；作答即停
    LaunchedEffect(index) {
        replaysLeft = MAX_REPLAYS
        player.playLineLoop(q.poem.id, q.lineIndex + 1)
    }
    LaunchedEffect(answered) { if (answered) player.pause() }

    // 初始焦点落播放钮
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(index) {
        withFrameNanos { }
        repeat(3) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    fun replay() {
        if (answered || replaysLeft <= 0) return
        replaysLeft--
        player.playLineLoop(q.poem.id, q.lineIndex + 1)
    }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(6.dp))
        Text("听一听，这是哪首诗？", color = T.TextBody, fontSize = 14.sp)

        RoundPlayButton(
            enabled = replaysLeft > 0 && !answered,
            modifier = Modifier.focusRequester(focusRequester),
            onClick = ::replay,
            onFocusChange = tone
        )
        Text(
            if (replaysLeft > 0) "可重听 $replaysLeft 次" else "重听次数已用完",
            color = T.TextSubtle, fontSize = 12.sp
        )

        // 诗名选项（竖排；答对变绿 / 答错标红，800ms 后由 VM 推进下一题）
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            q.options.forEach { poem ->
                val feedback = when {
                    answered && wasCorrect && poem.id == pickedId -> Feedback.CORRECT
                    answered && !wasCorrect && poem.id == pickedId -> Feedback.WRONG
                    else -> Feedback.NORMAL
                }
                OptionBlock(
                    feedback = feedback,
                    onClick = { if (!answered) onAnswer(poem.id) },
                    onFocusChange = tone
                ) {
                    Text(
                        poem.title,
                        Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                        fontSize = 18.sp, letterSpacing = 2.sp,
                        color = if (feedback == Feedback.CORRECT) Color.White else T.TextTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        fontFamily = KaiTi
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        poem.poet,
                        Modifier.padding(end = 20.dp),
                        fontSize = 11.sp,
                        color = if (feedback == Feedback.CORRECT) Color.White.copy(alpha = 0.85f) else T.TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 56dp 官方橙色播放圆钮；重听次数用尽/已作答置灰（仍可聚焦但 OK 无效——焦点停留在钮上不跳跃，官方 disabled 语义一致） */
@Composable
private fun RoundPlayButton(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit = {}
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .shadow(6.dp, CircleShape, clip = false)   // clip=false：不裁官方焦点 scale/描边（同 cardRestingShadow）
            .focusTone(onFocusChange)
            .size(56.dp),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),   // 交互式 Surface 的 shape 是 ClickableSurfaceShape
        colors = ClickableSurfaceDefaults.colors(
            containerColor = T.Orange,
            contentColor = Color.White,
            focusedContainerColor = T.Orange,   // 橙底不反色（官方默认反到深色会破坏品牌底）
            focusedContentColor = Color.White,
            disabledContainerColor = T.Idle,
            disabledContentColor = T.TextMuted,
        ),
        border = ClickableSurfaceDefaults.border(
            // 橙底上橙描边不可辨，焦点用白色描边（沿用原"焦点白边"语言）
            focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = CircleShape)
        ),
    ) {
        Icon(
            Icons.Filled.VolumeUp, null,
            Modifier.size(28.dp),
            tint = if (enabled) Color.White else T.TextMuted
        )
    }
}

/**
 * 选项块（候选字/诗名卡共用）：官方 Surface 承接焦点描边+缩放，
 * 作答反馈：CORRECT=绿底白字、WRONG=红描边。content 为 RowScope（块内横向排布）。
 */
@Composable
private fun OptionBlock(
    feedback: Feedback,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.shadow(6.dp, shape, clip = false).focusTone(onFocusChange),   // clip=false：不裁官方焦点 scale/描边
        shape = ClickableSurfaceDefaults.shape(shape = shape),   // 交互式 Surface 的 shape 是 ClickableSurfaceShape
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (feedback == Feedback.CORRECT) T.Correct else T.Card,
            contentColor = T.TextTitle,
            // 已作答反馈底色在各状态恒定，焦点不反色（反馈色优先）
            focusedContainerColor = if (feedback == Feedback.CORRECT) T.Correct else T.Card,
            focusedContentColor = T.TextTitle,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (feedback == Feedback.WRONG) Border(border = BorderStroke(3.dp, T.Wrong), shape = shape) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(3.dp, if (feedback == Feedback.WRONG) T.Wrong else T.Orange),
                shape = shape
            )
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
