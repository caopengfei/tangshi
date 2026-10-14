package com.rakor.tangshi.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.data.repo.ProgressRepository
import com.rakor.tangshi.data.repo.SettingsRepository
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.components.cardRestingShadow
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.segmentPillBorder
import com.rakor.tangshi.ui.components.segmentPillColors
import com.rakor.tangshi.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "已清除"提示条停留时长（照 GameSetupScreen 题池提示模式） */
private const val HINT_DISMISS_MS = 2600L

/** 设置卡封顶高度：两项设置内容量小，定太高卡内会留大片空白；按内容量取 240dp，
 *  整块在主区垂直居中（卡内内容再整体居中，见 [SettingCard]） */
private val SettingCardMaxHeight = 240.dp

/**
 * 家长设置（规格 5.8，无家长门直接进入）。
 *
 * 焦点模型：设置项（提示音胶囊、清除按钮）是独立焦点目标，◀▲▼▶ 由焦点系统
 * 自然导航；初始焦点落第一枚胶囊（withFrameNanos 等窗口挂载后落焦，Task 6/7 模式）。
 * 选中态=胶囊 highlight（与玩法范围/筛选同视觉），键音分段胶囊聚焦=4dp 主橙描边+放大不反色
 * （segment，规格 §1 分段组例外）。
 *
 * 清除学习进度：二次确认对话框，安全默认焦点=取消（不可逆动作，Task 7 模式）；
 * 确认后 wipeAll 并弹"已清除"提示条，不导航（留在本页）。
 */
@Composable
fun SettingsScreen() {
    val vm = settingsViewModel()
    val g = AppGraph.instance
    val s by vm.settings.collectAsState()

    var showWipeDialog by remember { mutableStateOf(false) }
    var showWipedHint by remember { mutableStateOf(false) }

    // "已清除"提示条自动消退
    LaunchedEffect(showWipedHint) {
        if (showWipedHint) {
            delay(HINT_DISMISS_MS)
            showWipedHint = false
        }
    }

    // 初始焦点落第一枚胶囊；进屏自动落焦的那次不响提示音（Task 6 模式）
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }
    val firstFocus = remember { mutableStateOf(true) }
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
            // 顶栏：标题 | 返回提示
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "家长设置",
                    Modifier.weight(1f),
                    fontSize = 26.sp, letterSpacing = 5.sp,
                    color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text("← 返回", color = T.TextSubtle, fontSize = 13.sp, maxLines = 1)
            }

            // 两块设置卡并排（各占一半宽），整块在主区垂直居中（2026-10-14 改版）：原先全宽一行一项，
            // 标签钉最左、控件钉最右，单行横跨 896dp，中间约 800dp 全是死白，两块卡看上去像断裂的
            // 空条。改并排卡后卡宽 ~438dp，图标+标题+控件收在近处（沿用首页统计卡的圆形图标语言），
            // 两卡横向铺满、纵向由封顶高度定形，主区不再有一大片空白
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.fillMaxWidth().heightIn(max = SettingCardMaxHeight),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingCard(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = "按键提示音",
                        description = "按键操作时播放轻提示音",
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { vm.setKeyTone(true) },
                                colors = segmentPillColors(s.keyTone),
                                border = segmentPillBorder(s.keyTone),
                                modifier = Modifier.focusTone(tone).focusRequester(focusRequester)
                            ) {
                                if (s.keyTone) {
                                    TvIcon(Icons.Filled.Check, null)
                                    Spacer(Modifier.width(6.dp))
                                }
                                TvText("开")
                            }
                            Button(
                                onClick = { vm.setKeyTone(false) },
                                colors = segmentPillColors(!s.keyTone),
                                border = segmentPillBorder(!s.keyTone),
                                modifier = Modifier.focusTone(tone)
                            ) {
                                if (!s.keyTone) {
                                    TvIcon(Icons.Filled.Check, null)
                                    Spacer(Modifier.width(6.dp))
                                }
                                TvText("关")
                            }
                        }
                    }
                    SettingCard(
                        icon = Icons.Filled.DeleteSweep,
                        title = "清除学习进度",
                        description = "清空全部学习记录，需二次确认",
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        WipeButton(
                            onFocusChange = tone,
                            onClick = { showWipeDialog = true }
                        )
                    }
                }
            }

            // 底栏：按键提示 | 关于
            KeyHintBar("← ↑↓ → 选择　OK 修改　后退键 返回", "关于：版本 1.0 · 唐诗数据来源说明")
        }

        // 已清除提示条（覆盖顶部，不挤压布局；停留本页不导航）
        if (showWipedHint) {
            Text(
                "已清除全部学习进度",
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .background(T.ChipBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                color = T.Orange, fontSize = 13.sp
            )
        }
    }

    // 清除学习进度 → 二次确认对话框（确认=清除 / 取消=保留）
    if (showWipeDialog) {
        // 安全默认焦点：M3 对话框默认聚焦确认按钮（清除=不可逆动作），
        // 等对话框窗口挂载后把初始焦点改落到"保留"（Task 7 模式）
        val safeFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            safeFocus.requestFocus()
        }
        // 配色由 TangshiColorScheme 全槽位提供（容器=surfaceContainerHigh 白卡、文字/按钮主橙），
        // 这里只保留与页面卡片一致的圆角；不再逐项覆写颜色，避免与主题双源维护
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("清除学习进度", color = T.TextTitle) },
            shape = RoundedCornerShape(18.dp),
            text = { Text("确定清除全部学习进度吗？此操作不可恢复。", color = T.TextBody) },
            confirmButton = {
                TextButton(onClick = {
                    showWipeDialog = false
                    vm.wipeAll()
                    showWipedHint = true
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWipeDialog = false },
                    modifier = Modifier.focusRequester(safeFocus)
                ) { Text("保留") }
            }
        )
    }
}

/**
 * 设置卡：圆形图标 + 标题 + 说明 + 控件，整块内容在卡内垂直居中。
 *
 * 图标底与首页统计卡同款（ChipBg 圆底 + 主橙字形），让设置项与首页的卡片语言一致；
 * 卡高由外部 fillMaxHeight + 主区封顶高度决定，内容整体居中——卡高略有余量时上下均分，
 * 不像 weight 撑开那样把内容顶到卡顶、底部留一大片空白。
 */
@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.cardRestingShadow(20),
        shape = RoundedCornerShape(20.dp),
        colors = SurfaceDefaults.colors(containerColor = T.Card)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).background(T.ChipBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(26.dp), tint = T.Orange)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    title, color = T.TextTitle, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(description, color = T.TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(22.dp))
            control()
        }
    }
}

/** "清除 →" 入口：官方 Surface 焦点描边+缩放，TV 焦点可见性硬要求 */
@Composable
private fun WipeButton(onFocusChange: (Boolean) -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.focusTone(onFocusChange),
        shape = ClickableSurfaceDefaults.shape(shape = shape),   // 交互式 Surface 的 shape 是 ClickableSurfaceShape
        colors = ClickableSurfaceDefaults.colors(
            containerColor = T.Idle,
            contentColor = T.Orange,
            focusedContainerColor = T.ChipBg,   // 聚焦升浅橙底（原语义），不反深色
            focusedContentColor = T.Orange,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, T.Orange), shape = shape)
        ),
    ) {
        Text(
            "清除 →",
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = T.Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

/** 设置流 + 写侧直通 + 清除进度（suspend 落 repo，viewModelScope 驱动） */
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val progressRepo: ProgressRepository
) : ViewModel() {
    val settings: StateFlow<SettingsRepository.Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.Settings())

    fun setKeyTone(v: Boolean) = viewModelScope.launch { settingsRepo.setKeyTone(v) }

    /** 清空全部学习记录 + study_log（Home/Library 各流由 Room 自动刷新） */
    fun wipeAll() = viewModelScope.launch { progressRepo.wipeAll() }
}

/** 手写 factory（不引 DI 框架）；顶层单例，避免每次重组重建 factory（照 GameViewModel 模式） */
private val settingsViewModelFactory = viewModelFactory {
    initializer { SettingsViewModel(AppGraph.instance.settings, AppGraph.instance.progress) }
}

@Composable
fun settingsViewModel(): SettingsViewModel = viewModel(factory = settingsViewModelFactory)
