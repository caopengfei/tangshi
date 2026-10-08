package com.rakor.tangshi.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.search.SearchMatcher
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusScale
import com.rakor.tangshi.ui.components.FocusableCard
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.components.tvFocusable
import com.rakor.tangshi.ui.theme.KaiTi
import com.rakor.tangshi.ui.theme.T
import java.text.Collator
import java.util.Locale

// ---- 屏幕键盘规格：5 行规则网格——A-G / H-N / O-U / V-Z 各一行，末行 删除(跨3列)+搜索(跨4列) ----
private val KEY_ROWS = listOf("ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ")
private val KeySize = 44.dp
private val KeyGap = 8.dp
private val KeyCorner = 10.dp

/** 跨 span 列的键宽（含中间空隙） */
private fun keyWidth(span: Int): Dp = KeySize * span + KeyGap * (span - 1)

/**
 * 搜索页数据：诗库快照 + 每诗全拼键缓存（逐字拼音去声调连串，初始化构建一次，
 * 之后每个按键只做 String.contains）+ 诗人浮层选项（Collator 中文拼音排序）。
 */
class SearchViewModel(poems: PoemRepository) : ViewModel() {
    val all: List<Poem> = poems.loadAll()
    val pinyinKeys: Map<String, String> = all.associate { it.id to SearchMatcher.pinyinKey(it) }
    val poets: List<Pair<String, Int>> = all.groupBy { it.poet }
        .map { it.key to it.value.size }
        .sortedWith(compareBy(Collator.getInstance(Locale.CHINA)) { it.first })
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
 * 搜索页（规格 5.3）：顶栏放大镜+搜索唐诗｜诗人/关键字筛选行｜左结果列表（命中字高亮）+
 * 右屏幕键盘｜诗人选择浮层｜底部按键提示。
 *
 * 焦点状态机：
 * - 进屏初始焦点落"诗人"筛选钮；
 * - 关键字框 OK → 焦点移到键盘首键 A；键盘卡 hasFocus 双向驱动 keyboardActive
 *   （D-pad 从结果区直接进键盘同样亮激活边框），移出键盘区自动退激活；
 * - 键盘"搜索"键 OK / 键盘激活态按 B → 收起键盘（退高亮），焦点回结果列表首行（空结果回筛选行）；
 * - "诗人"钮 OK → poetPickerOpen 浮层，焦点进浮层首项"全部诗人"；浮层白卡限高 420dp +
 *   选项列滚动（聚焦视口外项自动带入），focusProperties.exit 做焦点陷阱（任何向外移动
 *   弹回首项），遮罩下背景焦点全部不可达；OK 选中后焦点回"诗人"钮；
 * - B 键三层语义：诗人浮层 > 键盘激活态 > 系统返回（退出本屏回首页）。
 *
 * 实时过滤：keyword/poetFilter 变化即重算 results（VM 持拼音缓存，matches 四参重载复用，
 * 避免每次按键重复拼接 310 首全拼）。
 */
// 浮层焦点陷阱用 focusProperties.exit（ui 1.7 仍标 @ExperimentalComposeUiApi，行为已稳定）
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(onPoem: (String) -> Unit) {
    val vm = searchViewModel()
    val g = AppGraph.instance

    // 本屏状态：诗人过滤（null=全部）、关键字、键盘激活态、诗人浮层开关
    var poetFilter by remember { mutableStateOf<String?>(null) }
    var keyword by remember { mutableStateOf("") }
    var keyboardActive by remember { mutableStateOf(false) }
    var poetPickerOpen by remember { mutableStateOf(false) }

    // 实时结果（照 Task 8 filterPoems 的 remember 派生模式；拼音键走 VM 缓存、关键字键
    // 每键只 stripTone 一次，每首诗只剩两次 contains——310 首逐键过滤无重编译/重复去声调）
    val results = remember(vm.all, vm.pinyinKeys, poetFilter, keyword) {
        val keywordKey = SearchMatcher.stripTone(keyword.trim())
        vm.all.filter {
            SearchMatcher.matches(it, poetFilter, keyword, vm.pinyinKeys.getValue(it.id), keywordKey)
        }
    }
    // 诗人浮层选项：首项"全部诗人"（选中=不过滤），其余"姓名 诗数"
    val poetOptions: List<Pair<String, Int?>> =
        remember(vm.poets) { listOf("全部诗人" to null) + vm.poets.map { (n, c) -> n to c } }

    // 焦点落点：诗人筛选钮 / 键盘首键 / 结果首行 / 浮层首项
    val poetBtnFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val pickerFirstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { requestFocusWhenReady(poetBtnFocus) }
    LaunchedEffect(poetPickerOpen) { if (poetPickerOpen) requestFocusWhenReady(pickerFirstFocus) }

    // 进屏自动落焦的那次不响提示音（Task 6 模式）
    val firstFocus = remember { mutableStateOf(true) }
    val tone: (Boolean) -> Unit = { focused ->
        if (focused) {
            if (firstFocus.value) firstFocus.value = false else g.keyTone.focusMove()
        }
    }

    // 收起键盘：焦点回结果首行（空结果回诗人筛选钮）
    fun exitKeyboard() {
        keyboardActive = false
        if (runCatching { firstResultFocus.requestFocus() }.isFailure) {
            runCatching { poetBtnFocus.requestFocus() }
        }
    }

    // 关闭诗人浮层：焦点回诗人筛选钮
    fun closePoetPicker() {
        poetPickerOpen = false
        runCatching { poetBtnFocus.requestFocus() }
    }

    // B 键三层语义：诗人浮层 > 键盘激活态 > 系统返回（后注册的优先）
    BackHandler(enabled = keyboardActive && !poetPickerOpen) { exitKeyboard() }
    BackHandler(enabled = poetPickerOpen) { closePoetPicker() }

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

            // 筛选行：诗人筛选（弹浮层）+ 关键字显示框（进键盘激活态）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusablePillButton(
                    text = "诗人：${poetFilter ?: "全部"} ▾",
                    onClick = { poetPickerOpen = true },
                    onFocusChange = tone,
                    modifier = Modifier.focusRequester(poetBtnFocus)
                )
                FocusablePillButton(
                    text = "诗名/诗句关键字：" + keyword.ifEmpty { "点按输入" } + "▏",
                    icon = Icons.Filled.Edit,
                    // 键盘键常驻组合（requester 恒已挂载），直接移焦点即可；激活边框由
                    // 键盘卡 hasFocus 双向驱动（见下方 onFocusChanged），此处无需置位
                    onClick = { runCatching { firstKeyFocus.requestFocus() } },
                    onFocusChange = tone
                )
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Text("共找到 ${results.size} 首 · ▲▼ 浏览　OK 进入学习", fontSize = 12.sp, color = T.TextSubtle)
                    }
                }

                // 右：屏幕键盘白卡；hasFocus 双向驱动激活态（描边高亮）——D-pad 直接进键盘
                // 同样亮边框，移出键盘区自动退激活
                FocusCard(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { keyboardActive = it.hasFocus },
                    focused = keyboardActive,
                    corner = 18
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
            KeyHintBar("◀ ▲▼▶ 选择　OK 确认　B 返回")   // 计数只留结果列底部一处
        }

        // 诗人浮层：半透明遮罩 + 居中白卡；首项"全部诗人"。
        // 白卡限高 + 选项列滚动：310 首后诗人可达数十，防裁剪项"隐形却可聚焦"；
        // 焦点陷阱：白卡上 focusProperties.exit 把任何向外移动弹回首项，
        // 杜绝 D-pad 焦点逃逸到遮罩下的筛选行/结果列表/键盘（遮罩非焦点边界）。
        if (poetPickerOpen) {
            Box(
                Modifier.fillMaxSize().background(T.Scrim),
                contentAlignment = Alignment.Center
            ) {
                FocusCard(
                    Modifier
                        .width(600.dp)
                        .heightIn(max = 420.dp)
                        .focusProperties { exit = { pickerFirstFocus } },
                    focused = false,
                    corner = 20
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("选择诗人", fontSize = 18.sp, color = T.TextTitle, fontWeight = FontWeight.SemiBold)
                            Text("▲▼◀▶ 移动　OK 选中　B 关闭", fontSize = 12.sp, color = T.TextSubtle)
                        }
                        // 选项列可滚动：聚焦视口外选项时 bring-into-view 自动滚入
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            poetOptions.withIndex().chunked(4).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowItems.forEach { (index, option) ->
                                        PickerItem(
                                            text = option.first + (option.second?.let { " $it" } ?: ""),
                                            // index 0 = 全部诗人（poetFilter=null），不按字符串判定
                                            modifier = if (index == 0) Modifier.focusRequester(pickerFirstFocus) else Modifier,
                                            onClick = {
                                                poetFilter = if (index == 0) null else option.first
                                                closePoetPicker()
                                            },
                                            onFocusChange = tone
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
    FocusableCard(modifier.fillMaxWidth(), corner = 14, onFocusChange = onFocusChange, onClick = onClick) {
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

/** 键盘单键：ChipBg 小方块，聚焦橙描边+放大；OK 触发 onClick */
@Composable
private fun KeyBox(
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale else 1f, label = "keyScale")
    Box(
        modifier
            .width(width)
            .height(KeySize)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .scale(scale)
            .background(T.ChipBg, RoundedCornerShape(KeyCorner))
            .border(if (focused) 2.dp else 0.dp, T.Orange, RoundedCornerShape(KeyCorner))
            .tvFocusable(onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** 浮层诗人项："姓名 诗数"小卡，聚焦橙描边+放大 */
@Composable
private fun PickerItem(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale else 1f, label = "pickerScale")
    Box(
        modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .scale(scale)
            .background(T.ChipBg, RoundedCornerShape(12.dp))
            .border(if (focused) 2.dp else 0.dp, T.Orange, RoundedCornerShape(12.dp))
            .tvFocusable(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, fontSize = 14.sp, color = T.TextBody)
    }
}
