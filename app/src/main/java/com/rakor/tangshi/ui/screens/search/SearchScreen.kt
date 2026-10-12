package com.rakor.tangshi.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.search.SearchMatcher
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.components.cardBorder
import com.rakor.tangshi.ui.components.cardColors
import com.rakor.tangshi.ui.components.cardRestingShadow
import com.rakor.tangshi.ui.components.cardShape
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.normalPillBorder
import com.rakor.tangshi.ui.components.normalPillColors
import com.rakor.tangshi.ui.theme.KaiTi
import com.rakor.tangshi.ui.theme.T

// ---- 屏幕键盘规格：5 行规则网格——A-G / H-N / O-U / V-Z 各一行，末行 删除(跨3列)+搜索(跨4列) ----
private val KEY_ROWS = listOf("ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ")
private val KeySize = 44.dp
private val KeyGap = 8.dp
private val KeyCorner = 10.dp

/** 跨 span 列的键宽（含中间空隙） */
private fun keyWidth(span: Int): Dp = KeySize * span + KeyGap * (span - 1)

/**
 * 搜索页数据：诗库快照 + 每诗合并拼音键缓存（诗句全拼+首字母 / 标题 / 诗人键，
 * 初始化构建一次，之后每个按键只做 String.contains）。
 */
class SearchViewModel(poems: PoemRepository) : ViewModel() {
    val all: List<Poem> = poems.loadAll()
    val keys: Map<String, String> = poems.loadSearchIndex().let { index ->
        all.associate { it.id to SearchMatcher.buildKeys(it, index) }
    }
}

/** 手写 factory（不引 DI 框架）；顶层单例，避免每次重组重建 factory（照 HomeScreen 模式） */
private val searchViewModelFactory = viewModelFactory {
    initializer { SearchViewModel(AppGraph.instance.poems) }
}

@Composable
private fun searchViewModel(): SearchViewModel = viewModel(factory = searchViewModelFactory)

/** 等 requester 挂上再请求焦点（照 LibraryScreen：先等一帧，未挂上再让帧重试） */
private suspend fun requestFocusWhenReady(requester: FocusRequester) {
    withFrameNanos { }
    repeat(3) {
        if (runCatching { requester.requestFocus() }.isSuccess) return
        withFrameNanos { }
    }
}

/**
 * 搜索页（规格 5.3，2026-10-12 改版）：顶栏放大镜+搜索唐诗｜关键字输入行（诗人/诗名/诗句）｜
 * 左结果列表（命中字高亮）+ 右屏幕键盘｜底部按键提示。
 *
 * 焦点状态机：
 * - 进屏初始焦点落关键字框；
 * - 关键字框 OK → 焦点移到键盘首键 A；键盘卡 hasFocus 双向驱动 keyboardActive
 *   （D-pad 从结果区直接进键盘同样亮激活边框），移出键盘区自动退激活；
 * - 键盘"搜索"键 OK / 键盘激活态按 B → 收起键盘（退高亮），焦点回结果列表首行
 *   （空结果回关键字框）；
 * - B 键两层语义：键盘激活态 > 系统返回（退出本屏回首页）。
 *
 * 实时过滤：keyword 变化即重算 results（VM 持每诗合并拼音键，matches 四参重载复用，
 * 避免每次按键重复拼接 325 首键串）。
 */
@Composable
fun SearchScreen(onPoem: (String) -> Unit) {
    val vm = searchViewModel()
    val g = AppGraph.instance

    // 本屏状态：关键字、键盘激活态
    var keyword by remember { mutableStateOf("") }
    var keyboardActive by remember { mutableStateOf(false) }

    // 实时结果（拼音键走 VM 合并键缓存；关键字键每键只 stripTone 一次，每首诗
    // 只剩汉字文本 / 合并键两次 contains——325 首逐键过滤无重复拼接）
    val results = remember(vm.all, vm.keys, keyword) {
        val keywordKey = SearchMatcher.stripTone(keyword.trim())
        vm.all.filter {
            SearchMatcher.matches(it, keyword, vm.keys.getValue(it.id), keywordKey)
        }
    }

    // 焦点落点：关键字框 / 键盘首键 / 结果首行
    val keywordBtnFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { requestFocusWhenReady(keywordBtnFocus) }

    // 进屏自动落焦的那次不响提示音（Task 6 模式）
    val firstFocus = remember { mutableStateOf(true) }
    val tone: (Boolean) -> Unit = { focused ->
        if (focused) {
            if (firstFocus.value) firstFocus.value = false else g.keyTone.focusMove()
        }
    }

    // 收起键盘：焦点回结果首行（空结果回关键字框）
    fun exitKeyboard() {
        keyboardActive = false
        if (runCatching { firstResultFocus.requestFocus() }.isFailure) {
            runCatching { keywordBtnFocus.requestFocus() }
        }
    }

    // B 键两层语义：键盘激活态 > 系统返回（BackHandler 先于系统拦截）
    BackHandler(enabled = keyboardActive) { exitKeyboard() }

    CreamBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
            // 顶栏：珊瑚橙线条放大镜 + 标题 | 右返回提示
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = T.Orange)
                Spacer(Modifier.width(8.dp))
                Text(
                    "搜索唐诗",
                    fontSize = 26.sp, letterSpacing = 5.sp,
                    color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "← 返回",
                    Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    color = T.TextSubtle, fontSize = 13.sp, maxLines = 1
                )
            }

            Spacer(Modifier.height(10.dp))

            // 输入行：关键字框（进键盘激活态）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    // 键盘键常驻组合（requester 恒已挂载），直接移焦点即可；激活边框由
                    // 键盘卡 hasFocus 双向驱动（见下方 onFocusChanged），此处无需置位
                    onClick = { runCatching { firstKeyFocus.requestFocus() } },
                    colors = normalPillColors(),
                    border = normalPillBorder(),
                    modifier = Modifier.focusTone(tone).focusRequester(keywordBtnFocus)
                ) {
                    TvIcon(Icons.Filled.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    TvText("诗人/诗名/诗句：" + keyword.ifEmpty { "点按输入" } + "▏")
                }
            }

            Spacer(Modifier.height(10.dp))

            // 主区：左结果列表 | 右屏幕键盘
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 左：结果列表（空态居中提示）
                Column(Modifier.weight(1.3f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (results.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("没有找到相关唐诗", color = T.TextSubtle)
                            }
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                // 预批修复（Step 9.5）：官方焦点描边外绘，首行顶环需滚动轴顶部留出 10dp 容纳
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                itemsIndexed(results, key = { _, p -> p.id }) { index, poem ->
                                    ResultRow(
                                        poem, keyword,
                                        modifier = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier,
                                        onClick = { onPoem(poem.id) },
                                        onFocusChange = tone
                                    )
                                }
                            }
                        }
                    }
                    if (results.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("共找到 ${results.size} 首 · ↑↓ 浏览　OK 进入学习", fontSize = 12.sp, color = T.TextSubtle)
                    }
                }

                // 右：屏幕键盘白卡；hasFocus 双向驱动激活态（描边高亮）——D-pad 直接进键盘
                // 同样亮边框，移出键盘区自动退激活
                // 激活描边 = 容器活动指示（子键聚焦态驱动），非焦点视觉，故用条件 border；
                // 静态 Surface 无焦点态，官方组件不适用此处
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .cardRestingShadow(18)
                        .then(
                            if (keyboardActive) Modifier.border(4.dp, T.Orange, RoundedCornerShape(18.dp))
                            else Modifier
                        )
                        .onFocusChanged { keyboardActive = it.hasFocus },
                    shape = RoundedCornerShape(18.dp),
                    colors = SurfaceDefaults.colors(containerColor = T.Card)
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(KeyGap, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        KEY_ROWS.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                                row.forEachIndexed { i, ch ->
                                    KeyBox(
                                        KeySize,
                                        onClick = { keyword += ch },
                                        modifier = if (row === KEY_ROWS[0] && i == 0) Modifier.focusRequester(firstKeyFocus) else Modifier,
                                        onFocusChange = tone
                                    ) {
                                        Text(
                                            ch.toString(),
                                            fontSize = 16.sp, color = T.TextBody, fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                            KeyBox(keyWidth(3), onClick = { keyword = keyword.dropLast(1) }, onFocusChange = tone) {
                                Icon(Icons.Filled.Backspace, "删除", Modifier.size(20.dp), tint = T.Orange)
                            }
                            // 实时搜索已生效："搜索"键 = 收起键盘回结果列表
                            KeyBox(keyWidth(4), onClick = { exitKeyboard() }, onFocusChange = tone) {
                                Text(
                                    "搜索",
                                    fontSize = 15.sp, letterSpacing = 2.sp,
                                    color = T.TextTitle, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            KeyHintBar("← ↑↓ → 选择　OK 确认　B 返回")   // 计数只留结果列底部一处
        }
    }
}

/**
 * 结果行：`诗名 · 诗人`（左）+ 首句预览（右）；汉字 keyword 在预览句中的出现染橙加粗，
 * 全拼命中（预览句无对应汉字出现）即普通渲染。
 */
@Composable
private fun ResultRow(
    poem: Poem,
    keyword: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .cardRestingShadow(14)
            .focusTone(onFocusChange),
        shape = cardShape(14),
        colors = cardColors(),
        border = cardBorder(14),
        // 宽行卡不缩放（Task 8 实机发现）：官方 1.1 外扩把右环推到键盘卡底下被遮挡，
        // 与详情页大卡同款处理——border-only 焦点，右环落回 14dp 缝隙内完整可见
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // 诗名用楷体（规格第 4 节诗体内容），诗人仍走系统无衬线
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = KaiTi)) { append(poem.title) }
                    append(" · ${poem.poet}")
                },
                Modifier.weight(1f),
                fontSize = 15.sp, color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(10.dp))
            Text(
                highlightedPreview(poem.lines.firstOrNull()?.text ?: "", keyword),
                Modifier.weight(1.2f),
                fontSize = 10.sp, lineHeight = 14.sp, color = T.TextSubtle,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 首句预览：keyword 出现处染 T.Orange 加粗（找不到出现处 = 全拼命中 → 普通渲染） */
private fun highlightedPreview(text: String, keyword: String): AnnotatedString = buildAnnotatedString {
    append(text)
    val kw = keyword.trim()
    if (kw.isEmpty()) return@buildAnnotatedString
    var idx = text.indexOf(kw)
    while (idx >= 0) {
        addStyle(SpanStyle(color = T.Orange, fontWeight = FontWeight.Bold), idx, idx + kw.length)
        idx = text.indexOf(kw, idx + kw.length)
    }
}

/** 键盘单键：ChipBg 小方块，官方焦点描边+缩放；OK 触发 onClick */
@Composable
private fun KeyBox(
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(KeySize)
            .focusTone(onFocusChange),
        // 交互式 Surface 的 shape 需 ClickableSurfaceShape（focused/pressed 默认沿用 shape）
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(KeyCorner)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = T.ChipBg,
            contentColor = T.TextBody,
            focusedContainerColor = T.ChipBg,   // 键位焦点不反色（保留描边+放大语义）
            focusedContentColor = T.TextBody,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, T.Orange), shape = RoundedCornerShape(KeyCorner))
        ),
    ) {
        // fillMaxSize 必需：Surface 内容按 top-start 放置，不填满则文字顶在键帽左上角
        // （实测偏移 dx=33 dy=20px），填满后 contentAlignment=Center 才真正生效
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}
