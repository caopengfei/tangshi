package com.rakor.tangshi.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.repo.ProgressRepository
import com.rakor.tangshi.data.repo.ProgressStatus
import com.rakor.tangshi.data.repo.ProgressSummary
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.FocusableCard
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.components.StatusBadge
import com.rakor.tangshi.ui.theme.KaiTi
import com.rakor.tangshi.ui.theme.T
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** 网格规格（规格 5.4）：4 列 × 2 行 = 每页 8 张卡；列间距/边缘留白给焦点放大留余地 */
private const val GRID_COLS = 4
private const val GRID_ROWS = 2
private const val PAGE_SIZE = GRID_COLS * GRID_ROWS
private val GridGap = 12.dp
private val GridEdgePad = 8.dp

/** 筛选维（规格 5.4）：全部 / 未学习 / 学习中 / 已会背 */
internal enum class LibraryFilter(val label: String) {
    ALL("全部"), NOT_STARTED("未学习"), LEARNING("学习中"), MASTERED("已会背")
}

/** 纯函数：按筛选维过滤诗列表。未学习 = 无进度行（NOT_STARTED 不落 DB 行） */
internal fun filterPoems(filter: LibraryFilter, poems: List<Poem>, summaries: List<ProgressSummary>): List<Poem> {
    val byId = summaries.associateBy { it.poemId }
    return when (filter) {
        LibraryFilter.ALL -> poems
        LibraryFilter.NOT_STARTED -> poems.filter { byId[it.id] == null }
        LibraryFilter.LEARNING -> poems.filter { byId[it.id]?.status == ProgressStatus.LEARNING }
        LibraryFilter.MASTERED -> poems.filter { byId[it.id]?.status == ProgressStatus.MASTERED }
    }
}

/**
 * 全部唐诗页数据：诗库快照（一次性同步加载）+ 全量进度汇总（observe 实时刷新，
 * 学完返回本屏状态徽章/计数/筛选结果自动更新）。
 */
class LibraryViewModel(private val poems: PoemRepository, progress: ProgressRepository) : ViewModel() {
    val summaries: StateFlow<List<ProgressSummary>> =
        progress.observeAllSummaries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val all: List<Poem> = poems.loadAll()
}

/** 手写 factory（不引 DI 框架）；顶层单例，避免每次重组重建 factory（照抄 HomeScreen 模式） */
private val libraryViewModelFactory = viewModelFactory {
    initializer { LibraryViewModel(AppGraph.instance.poems, AppGraph.instance.progress) }
}

@Composable
private fun libraryViewModel(): LibraryViewModel = viewModel(factory = libraryViewModelFactory)

/**
 * 全部唐诗页（规格 5.4）：顶栏标题+计数、筛选胶囊行、4×2 诗卡网格、按键提示+页码。
 *
 * 翻页选型：用 [LazyHorizontalGrid]（rows = Fixed(2)，一列满 2 行自动换到下一列）
 * 而非手动 Chunked 分页——TV 上"焦点移动即翻页"：D-Pad 把焦点移出可视区时网格自动
 * 滚动跟进，无需手写翻页与翻页后的焦点落位管理（手动分页最易丢焦点、代码量大）。
 * "每页 8 张"的视觉约定由卡宽 =（可视宽 − 边缘留白×2 − 列间距×3）/ 4 保证；
 * 页码由焦点所在卡的索引推算（focusIndex / 8 + 1），显示在按键提示条右侧。
 *
 * 筛选胶囊直接复用 [FocusablePillButton]：filled = 选中态恰好给出规格要求的三种视觉
 * （选中且聚焦=实心+白描边；选中未聚焦=实心；未选中=描边），无需额外包装。
 */
@Composable
fun LibraryScreen(onPoem: (String) -> Unit) {
    val vm = libraryViewModel()
    val g = AppGraph.instance
    val summaries by vm.summaries.collectAsState()
    val all = vm.all

    // 筛选状态与焦点所在卡索引（页码推算用），本屏局部 remember 即可
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var focusedIndex by remember { mutableStateOf(0) }

    val gridState = rememberLazyGridState()
    val filtered = remember(filter, all, summaries) { filterPoems(filter, all, summaries) }
    val summaryById = remember(summaries) { summaries.associateBy { it.poemId } }

    // 页码：当前焦点卡所在页 / 总页数（无结果时不显示）
    val pageCount = ((filtered.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val page = (focusedIndex / PAGE_SIZE + 1).coerceIn(1, pageCount)

    // 切换筛选：网格回到顶部，页码归位（首次组合 filter==ALL、网格本就在顶部，为空操作）
    LaunchedEffect(filter) {
        focusedIndex = 0
        if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
            gridState.scrollToItem(0)
        }
    }

    // 初始焦点落网格首卡；进屏自动落焦的那次不响提示音（Task 6 模式）
    val firstCardFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // LazyGrid 的 item 完成首次布局后 requester 才挂上：先等一帧，未挂上再让帧重试
        withFrameNanos { }
        repeat(3) {
            if (runCatching { firstCardFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    val firstFocus = remember { mutableStateOf(true) }
    val tone: (Boolean) -> Unit = { focused ->
        if (focused) {
            if (firstFocus.value) firstFocus.value = false else g.keyTone.focusMove()
        }
    }

    CreamBackground {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            // 顶栏：标题 + 计数 | 右侧返回提示（B 走系统返回，无返回按钮）
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "全部唐诗",
                    fontSize = 22.sp, letterSpacing = 4.sp,
                    color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "共 ${all.size} 首 · 已学 ${summaries.size} 首",
                    fontSize = 11.sp, color = T.TextMuted, maxLines = 1
                )
                Text(
                    "← 返回",
                    Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    color = T.TextSubtle, fontSize = 13.sp, maxLines = 1
                )
            }

            Spacer(Modifier.height(8.dp))

            // 筛选行：选中=实心渐变白字，未选中=描边；聚焦动效由 FocusablePillButton 自管
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilter.entries.forEach { f ->
                    FocusablePillButton(
                        f.label,
                        if (f == LibraryFilter.MASTERED) Icons.Filled.ThumbUp else null,
                        filled = filter == f,
                        onClick = { filter = f },
                        onFocusChange = tone
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 网格区：4 列 × 2 行，横向滚动；一卡一诗（无诗句预览，Task 3 评审决定）
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val cardWidth = (maxWidth - GridEdgePad * 2 - GridGap * (GRID_COLS - 1)) / GRID_COLS
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("这个分类还没有唐诗哦", color = T.TextSubtle)
                    }
                } else {
                    LazyHorizontalGrid(
                        modifier = Modifier.fillMaxSize(),
                        rows = GridCells.Fixed(GRID_ROWS),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(GridGap),
                        verticalArrangement = Arrangement.spacedBy(GridGap),
                        contentPadding = PaddingValues(horizontal = GridEdgePad)
                    ) {
                        itemsIndexed(filtered, key = { _, p -> p.id }) { index, poem ->
                            val summary = summaryById[poem.id]
                            val status = summary?.status ?: ProgressStatus.NOT_STARTED
                            FocusableCard(
                                Modifier
                                    .width(cardWidth)
                                    .fillMaxHeight()
                                    .then(if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier),
                                corner = 16,
                                onFocusChange = { f ->
                                    if (f) focusedIndex = index
                                    tone(f)
                                },
                                onClick = { onPoem(poem.id) }
                            ) {
                                // 中部：诗名 + 诗人（整体水平垂直居中，诗人不带朝代前缀）
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            poem.title,
                                            fontSize = 19.sp, letterSpacing = 3.sp, lineHeight = 26.sp,
                                            color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            fontFamily = KaiTi
                                        )
                                        Text(poem.poet, fontSize = 11.sp, color = T.TextMuted, maxLines = 1)
                                    }
                                }
                                // 底部：状态徽章贴底居中
                                Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                                    StatusBadge(status, summary?.progress ?: 0)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            KeyHintBar("◀ ▲▼▶ 浏览　OK 学习　B 返回", "第 $page / $pageCount 页".takeIf { filtered.isNotEmpty() })
        }
    }
}
