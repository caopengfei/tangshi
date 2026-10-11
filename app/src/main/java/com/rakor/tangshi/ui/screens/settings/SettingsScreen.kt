package com.rakor.tangshi.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rakor.tangshi.data.repo.ProgressRepository
import com.rakor.tangshi.data.repo.SettingsRepository
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusScale
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.components.tvFocusable
import com.rakor.tangshi.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "已清除"提示条停留时长（照 GameSetupScreen 题池提示模式） */
private const val HINT_DISMISS_MS = 2600L

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

            // 两块纵向堆叠（全宽卡 corner=18 白底）：提示音 / 清除进度
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingCard(Modifier.weight(1f).fillMaxWidth(), "按键提示音") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusablePillButton(
                            "开",
                            icon = if (s.keyTone) Icons.Filled.Check else null,
                            highlight = s.keyTone,
                            segment = true,
                            onClick = { vm.setKeyTone(true) },
                            onFocusChange = tone,
                            modifier = Modifier.focusRequester(focusRequester)
                        )
                        FocusablePillButton(
                            "关",
                            icon = if (!s.keyTone) Icons.Filled.Check else null,
                            highlight = !s.keyTone,
                            segment = true,
                            onClick = { vm.setKeyTone(false) },
                            onFocusChange = tone
                        )
                    }
                }
                SettingCard(Modifier.weight(1.4f).fillMaxWidth(), "清除学习进度") {
                    Text(
                        "清空全部学习记录，需二次确认",
                        color = T.TextMuted, fontSize = 11.sp
                    )
                    Spacer(Modifier.weight(1f))
                    WipeButton(
                        onFocusChange = tone,
                        onClick = { showWipeDialog = true }
                    )
                }
            }

            // 底栏：按键提示 | 关于
            KeyHintBar("← ↑↓ → 选择　OK 修改　B 返回", "关于：版本 1.0 · 唐诗数据来源说明")
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

/** 设置卡容器：corner=18 白底卡（FocusCard 常显无焦点视觉，焦点落在内部设置项上） */
@Composable
private fun SettingCard(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    FocusCard(modifier, focused = false, corner = 18) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = T.TextBody, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** "清除 →" 入口：橙色小字按钮（独立焦点目标，聚焦橙描边+放大，TV 焦点可见性硬要求） */
@Composable
private fun WipeButton(onFocusChange: (Boolean) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Text(
        "清除 →",
        Modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .scale(if (focused) FocusScale else 1f)
            .background(if (focused) T.ChipBg else T.Idle, shape)
            .border(if (focused) 3.dp else 0.dp, T.Orange, shape)
            .tvFocusable(onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = T.Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
    )
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
