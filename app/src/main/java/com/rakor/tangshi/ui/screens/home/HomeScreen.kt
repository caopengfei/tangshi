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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.repo.HomeStats
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.repo.ProgressRepository
import com.rakor.tangshi.data.repo.ProgressStatus
import com.rakor.tangshi.data.repo.ProgressSummary
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusableCard
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.theme.T
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * 首页数据：
 * - stats：统计卡（已学/连续/已会背）
 * - current：当前学习诗（选择规则见 [selectCurrent]；诗库为空时为 null，不崩）
 * - started：当前诗是否已有进度行（决定 CTA 文案 开始/继续诵读）
 * - progress：当前诗的诵读进度%（主卡进度条，未学习为 0）
 * - total：内置诗库总数（统计卡）
 */
class HomeViewModel(private val poems: PoemRepository, progress: ProgressRepository) : ViewModel() {
    val total: Int = poems.loadAll().size

    val stats: StateFlow<HomeStats> = progress.observeStats()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeStats(0, 0, 0, emptySet()))

    val current: StateFlow<Poem?> =
        combine(progress.observeAllSummaries(), flowOf(poems.loadAll())) { s, all ->
            selectCurrent(s, all)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, poems.loadAll().firstOrNull())

    val started: StateFlow<Boolean> =
        combine(progress.observeAllSummaries(), current) { s, poem ->
            poem != null && isStarted(s, poem)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val progress: StateFlow<Int> =
        combine(progress.observeAllSummaries(), current) { s, poem ->
            poem?.let { p -> s.firstOrNull { it.poemId == p.id }?.progress } ?: 0
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    companion object {
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

        /** 有进度行即已开始（LEARNING 或 MASTERED），决定 CTA 文案 开始/继续 */
        fun isStarted(summaries: List<ProgressSummary>, poem: Poem): Boolean =
            summaries.any { it.poemId == poem.id }
    }
}

/** 手写 factory（不引 DI 框架）；顶层单例，避免每次重组重建 factory */
private val homeViewModelFactory = viewModelFactory {
    initializer { HomeViewModel(AppGraph.instance.poems, AppGraph.instance.progress) }
}

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
    val started by vm.started.collectAsState()
    val progress by vm.progress.collectAsState()

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
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶栏：标题 + 鼓励语
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "唐诗三百首",
                    fontSize = 22.sp, letterSpacing = 4.sp,
                    color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text("小朋友，今天继续加油哦 🌞", color = T.TextSubtle)
            }

            // 主区：左主卡 + 右统计列
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FocusableCard(
                    Modifier.weight(1.5f).fillMaxHeight().focusRequester(focusRequester),
                    onFocusChange = tone
                ) {
                    val p = poem
                    if (p != null) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "当前学习 · 第 ${p.order} 首",
                                color = T.Orange, fontSize = 13.sp, letterSpacing = 2.sp
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    p.title,
                                    fontSize = 26.sp, letterSpacing = 4.sp,
                                    color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(p.poet, color = T.TextMuted, maxLines = 1)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                p.lines.forEach { line ->
                                    Text(line.text, color = T.TextBody, lineHeight = 24.sp)
                                }
                            }
                            Spacer(Modifier.weight(1f))
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
                            FocusablePillButton(
                                if (started) "继 续 诵 读" else "开 始 诵 读",
                                Icons.Filled.PlayArrow,
                                filled = true,
                                onClick = { onPoem(p.id) },
                                onFocusChange = tone
                            )
                        }
                    }
                }

                // 右统计列（不可聚焦，仅展示）
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)
                ) {
                    StatCard(Icons.Filled.MenuBook, "已学唐诗", "${stats.learned} / ${vm.total}")
                    StatCard(Icons.Filled.LocalFireDepartment, "连续学习", "${stats.streakDays} 天")
                    StatCard(Icons.Filled.ThumbUp, "已会背诵", "${stats.mastered} 首")
                }
            }

            // 底部入口
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusablePillButton("搜索", Icons.Filled.Search, filled = false, onClick = onSearch, onFocusChange = tone)
                FocusablePillButton("全部唐诗", filled = false, onClick = onLibrary, onFocusChange = tone)
                FocusablePillButton("背诗小游戏", Icons.Filled.SportsEsports, filled = false, onClick = onGames, onFocusChange = tone)
                FocusablePillButton("家长设置", Icons.Filled.Settings, filled = false, onClick = onSettings, onFocusChange = tone)
            }

            KeyHintBar("◀ ▲▼ 选择　OK 确认")
        }
    }
}

/** 统计卡：图标+标签 与 数值 两端对齐 */
@Composable
private fun StatCard(icon: ImageVector, label: String, value: String) {
    FocusCard(Modifier.fillMaxWidth(), focused = false, corner = 18) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = T.Orange)
                Spacer(Modifier.width(8.dp))
                Text(label, color = T.TextBody)
            }
            Text(value, color = T.Orange, fontSize = 18.sp)
        }
    }
}
