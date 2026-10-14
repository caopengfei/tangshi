package com.rakor.tangshi.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.repo.HomeStats
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.repo.ProgressRepository
import com.rakor.tangshi.data.repo.ProgressStatus
import com.rakor.tangshi.data.repo.ProgressSummary
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 首页数据：
 * - stats：统计卡（已学/连续/已会背）
 * - current：当前学习诗（选择规则见 [selectCurrent]；诗库为空时为 null，不崩）
 * - progress：当前诗的诵读进度%（主卡进度条，未学习为 0）
 * - total：内置诗库总数（统计卡）
 */
class HomeViewModel(private val poems: PoemRepository, progress: ProgressRepository) : ViewModel() {
    /**
     * 诗库异步加载（1.4MB/325 首 JSON）：解析落在 IO 线程（Application 已 warmUp 预热，
     * 通常立即命中缓存），首帧以空表起播——主线程构造路径不再做重活，弱设备白屏明显缩短。
     */
    private val poemsList: StateFlow<List<Poem>> = flow { emit(poems.loadAll()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 诗库加载完成标志：区分"解析中（首页占位）"与"真的空诗库（提示文案）" */
    val poemsReady: StateFlow<Boolean> = flow { poems.loadAll(); emit(true) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val total: StateFlow<Int> = poemsList.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val stats: StateFlow<HomeStats> = progress.observeStats()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeStats(0, 0, 0, emptySet()))

    val current: StateFlow<Poem?> =
        combine(progress.observeAllSummaries(), poemsList) { s, all ->
            selectCurrent(s, all)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val progress: StateFlow<Int> =
        combine(progress.observeAllSummaries(), current) { s, poem ->
            poem?.let { p -> s.firstOrNull { it.poemId == p.id }?.progress } ?: 0
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * 当前学习诗在列表里的位次（1 起；无当前诗为 0），供「当前学习 · 第 N 首」展示。
     * 用**列表位次**而非 `order`：order 是卷内编号（1~603），与诗库总数 325 不同量纲，
     * 直接上屏会出现「第 587 / 325 首」这种自相矛盾的计数。
     */
    val currentPosition: StateFlow<Int> =
        combine(current, poemsList) { p, all -> positionOf(all, p) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    companion object {
        /** 诗在（按 order 排序后的）列表里的位次，1 起；诗为 null 或不在列表时为 0 */
        fun positionOf(all: List<Poem>, poem: Poem?): Int =
            if (poem == null) 0 else all.indexOfFirst { it.id == poem.id } + 1

        /**
         * 首页当前诗：最近学习的诗优先（LEARNING 中 lastPlayedAt 最新），
         * 否则第一首未学，否则第一首。全程 firstOrNull：进度行指向已删除的诗、
         * 或诗库为空时返回 null 而不是抛异常。
         */
        fun selectCurrent(summaries: List<ProgressSummary>, poems: List<Poem>): Poem? =
            summaries.filter { it.status == ProgressStatus.LEARNING }.maxByOrNull { it.lastPlayedAt }
                ?.let { p -> poems.firstOrNull { it.id == p.poemId } }
                ?: poems.firstOrNull { p -> summaries.none { it.poemId == p.id } }
                ?: poems.firstOrNull()
    }
}

/** 手写 factory（不引 DI 框架）；顶层单例，避免每次重组重建 factory */
private val homeViewModelFactory = viewModelFactory {
    initializer { HomeViewModel(AppGraph.instance.poems, AppGraph.instance.progress) }
}

/** 主卡最多完整展示的诗行数，超出显示"⋯⋯ 共 N 句"（全文在详情页） */
private const val HOME_PREVIEW_LINES = 4

@Composable
private fun homeViewModel(): HomeViewModel = viewModel(factory = homeViewModelFactory)

/** 首页（规格 5.1）：顶栏 + 左主卡/右统计列 + 底部入口 + 按键提示 */
@Composable
fun HomeScreen(
    onPoem: (String) -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onGames: () -> Unit,
    onSettings: () -> Unit
) {
    val vm = homeViewModel()
    val g = AppGraph.instance
    val stats by vm.stats.collectAsState()
    val poem by vm.current.collectAsState()
    val total by vm.total.collectAsState()
    val poemsReady by vm.poemsReady.collectAsState()
    val progress by vm.progress.collectAsState()
    val curPos by vm.currentPosition.collectAsState()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(poem) { if (poem != null) focusRequester.requestFocus() }

    // 进屏自动落焦的那次不响提示音，之后的焦点移动才响
    val firstFocus = remember { mutableStateOf(true) }
    val tone: (Boolean) -> Unit = { focused ->
        if (focused) {
            if (firstFocus.value) firstFocus.value = false else g.keyTone.focusMove()
        }
    }

    CreamBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶栏：标题 + 鼓励语
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "唐诗三百首",
                    fontSize = 26.sp, letterSpacing = 5.sp,
                    color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text("小朋友，今天继续加油哦 🌞", color = T.TextSubtle, fontSize = 15.sp)
            }

            // 主区：左主卡（整卡一个焦点落点，OK 进入详情开始诵读）+ 右统计列
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    onClick = { poem?.let { onPoem(it.id) } },
                    modifier = Modifier
                        .cardRestingShadow(20)
                        .weight(1.55f)
                        .fillMaxHeight()
                        .zIndex(1f)   // 聚焦放大时压过右列邻卡，避免描边被盖
                        .focusRequester(focusRequester)
                        .focusTone(tone),
                    shape = cardShape(20),
                    colors = cardColors(),
                    border = cardBorder(20),
                    // 大卡小倍率：官方 1.1 会挤压两侧布局（原 1.03 同值保留）
                    scale = CardDefaults.scale(focusedScale = 1.03f),
                ) {
                    val p = poem
                    if (p == null) {
                        // 诗库异步解析中先留白（不闪"诗库为空"），加载完成仍无诗才提示
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (poemsReady) Text("诗库为空", color = T.TextMuted)
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "当前学习 · 第 $curPos 首",
                                color = T.Orange, fontSize = 14.sp, letterSpacing = 3.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    p.title,
                                    fontSize = 36.sp, letterSpacing = 6.sp,
                                    color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, fontFamily = KaiTi
                                )
                                Text(
                                    p.poet,
                                    Modifier.padding(bottom = 6.dp),
                                    color = T.TextMuted, fontSize = 17.sp, maxLines = 1
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            // 主卡是入口预览：长诗只展示前几句，进度条靠 weight 钉在卡底
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 截断时少展示一句，给"共 N 句"提示行留出高度，避免被进度条裁切
                                val truncated = p.lines.size > HOME_PREVIEW_LINES
                                val preview = p.lines.take(if (truncated) HOME_PREVIEW_LINES - 1 else p.lines.size)
                                preview.forEach { line ->
                                    Text(
                                        line.text, color = T.TextBody,
                                        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 2.sp,
                                        fontFamily = KaiTi
                                    )
                                }
                                if (preview.size < p.lines.size) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "⋯⋯ 共 ${p.lines.size} 句",
                                        color = T.TextMuted, fontSize = 14.sp, letterSpacing = 1.sp
                                    )
                                }
                            }
                            // 诵读进度条
                            Box(
                                Modifier.fillMaxWidth().height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(T.ProgressBarBg)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                                        .fillMaxHeight()
                                        .background(Brush.horizontalGradient(listOf(T.OrangeLight, T.Orange)))
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }

                // 右统计列（不可聚焦，仅展示；三卡等分撑满与主卡同高）
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(Icons.Filled.MenuBook, "已学唐诗", "${stats.learned} / $total", Modifier.weight(1f))
                    StatCard(Icons.Filled.LocalFireDepartment, "连续学习", "${stats.streakDays} 天", Modifier.weight(1f))
                    StatCard(Icons.Filled.ThumbUp, "已会背诵", "${stats.mastered} 首", Modifier.weight(1f))
                }
            }

            // 底部入口：四个平级入口，统一普通描边档（不做常驻强调）
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSearch,
                    colors = normalPillColors(),
                    border = normalPillBorder(),
                    modifier = Modifier.focusTone(tone)
                ) {
                    TvIcon(Icons.Filled.Search, null)
                    Spacer(Modifier.width(6.dp))
                    TvText("搜索")
                }
                Button(
                    onClick = onLibrary,
                    colors = normalPillColors(),
                    border = normalPillBorder(),
                    modifier = Modifier.focusTone(tone)
                ) { TvText("全部唐诗") }
                Button(
                    onClick = onGames,
                    colors = normalPillColors(),
                    border = normalPillBorder(),
                    modifier = Modifier.focusTone(tone)
                ) {
                    TvIcon(Icons.Filled.SportsEsports, null)
                    Spacer(Modifier.width(6.dp))
                    TvText("背诗小游戏")
                }
                Button(
                    onClick = onSettings,
                    colors = normalPillColors(),
                    border = normalPillBorder(),
                    modifier = Modifier.focusTone(tone)
                ) {
                    TvIcon(Icons.Filled.Settings, null)
                    Spacer(Modifier.width(6.dp))
                    TvText("家长设置")
                }
            }

            KeyHintBar("← ↑↓ 选择　OK 确认")
        }
    }
}

/** 统计卡：圆形图标底 + 标签 + 数值两端对齐；modifier 通常传等分撑满右列 */
@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.cardRestingShadow(18),
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = T.Card)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).background(T.ChipBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(24.dp), tint = T.Orange)
                }
                Spacer(Modifier.width(12.dp))
                Text(label, color = T.TextBody, fontSize = 17.sp)
            }
            Text(value, color = T.Orange, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
