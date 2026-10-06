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
 * - current：当前学习诗 —— 最近学习的诗优先（LEARNING 中 lastPlayedAt 最新），
 *   否则第一首未学，否则第一首
 * - progress：当前诗的诵读进度%（主卡进度条，未学习为 0）
 */
class HomeViewModel(private val poems: PoemRepository, progress: ProgressRepository) : ViewModel() {
    val stats: StateFlow<HomeStats> = progress.observeStats()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeStats(0, 0, 0, emptySet()))

    val current: StateFlow<Poem> =
        combine(progress.observeAllSummaries(), flowOf(poems.loadAll())) { s, all ->
            s.filter { it.status == ProgressStatus.LEARNING }.maxByOrNull { it.lastPlayedAt }
                ?.let { p -> all.first { it.id == p.poemId } }
                ?: all.firstOrNull { p -> s.none { it.poemId == p.id } }
                ?: all.first()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, poems.loadAll().first())

    val progress: StateFlow<Int> =
        combine(progress.observeAllSummaries(), current) { s, poem ->
            s.firstOrNull { it.poemId == poem.id }?.progress ?: 0
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}

/** 手写 factory（不引 DI 框架） */
@Composable
private fun homeViewModel(): HomeViewModel = viewModel(factory = viewModelFactory {
    initializer { HomeViewModel(AppGraph.instance.poems, AppGraph.instance.progress) }
})

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
    val progress by vm.progress.collectAsState()
    val total = remember { g.poems.loadAll().size }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val tone: (Boolean) -> Unit = { if (it) g.keyTone.focusMove() }

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
                    color = T.TextTitle, fontWeight = FontWeight.SemiBold
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
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "当前学习 · 第 ${poem.order} 首",
                            color = T.Orange, fontSize = 13.sp, letterSpacing = 2.sp
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                poem.title,
                                fontSize = 26.sp, letterSpacing = 4.sp,
                                color = T.TextTitle, fontWeight = FontWeight.SemiBold
                            )
                            Text(poem.poet, color = T.TextMuted)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            poem.lines.forEach { line ->
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
                            "继 续 诵 读",
                            Icons.Filled.PlayArrow,
                            filled = true,
                            onClick = { onPoem(poem.id) },
                            onFocusChange = tone
                        )
                    }
                }

                // 右统计列（不可聚焦，仅展示）
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)
                ) {
                    StatCard(Icons.Filled.MenuBook, "已学唐诗", "${stats.learned} / $total")
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
