package com.rakor.tangshi.ui.screens.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusScale
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.components.tvFocusable
import com.rakor.tangshi.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 题池不足提示条的停留时长 */
private const val HINT_DISMISS_MS = 2600L

/**
 * 背诗小游戏 · 玩法选择页（规格 5.5）。
 *
 * 焦点模型：两张玩法大卡与右侧三枚范围胶囊都是独立焦点目标（◀▶/▲▼ 由焦点系统自然导航，
 * 与 LibraryScreen 筛选行一致）；OK 落在玩法卡上 = 以该玩法 + 当前选中范围开局。
 *
 * 选中态与聚焦分离（规格 5.5）：范围胶囊用 FocusablePillButton 的 highlight=选中（与 Task 8
 * 筛选三态同视觉）；玩法卡选中态用私有 [GameModeCard]——selected 且未聚焦 = 橙描边常显
 * （复用 FocusCard 的 focused 视觉），聚焦 = 描边 + 放大动画。实现取计划中的"自组私有卡"方案。
 *
 * 题池不足（今日所学/已学全部可能 <5 首）：setup 屏直接拦截（规格 9 提示文案），OK 弹顶部
 * 提示条并停留本屏，不导航；题面屏对 NEED_MORE 仍留兜底空态。
 */
@Composable
fun GameSetupScreen(onBack: () -> Unit, onStart: (mode: String, scope: String) -> Unit) {
    val vm = gameViewModel()
    val g = AppGraph.instance
    val cs = rememberCoroutineScope()

    var mode by remember { mutableStateOf(GameViewModel.MODE_FILL) }
    // 默认"全部"：首启今日池为空会直接拦截（规格 5.5 裁定），见规格注释
    var scope by remember { mutableStateOf(GameViewModel.SCOPE_ALL) }
    var showPoolHint by remember { mutableStateOf(false) }

    // 提示条自动消退
    LaunchedEffect(showPoolHint) {
        if (showPoolHint) {
            delay(HINT_DISMISS_MS)
            showPoolHint = false
        }
    }

    // OK 落在玩法卡：题池够 → 携 mode+scope 开局；不够 → 拦截提示（规格 9）
    fun startWith(modeOfCard: String) {
        mode = modeOfCard
        cs.launch {
            if (vm.hasEnough(scope)) onStart(modeOfCard, scope) else showPoolHint = true
        }
    }

    // 初始焦点落第一张玩法卡；进屏自动落焦的那次不响提示音（Task 6 模式）
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶栏：标题 | 返回提示
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "背诗小游戏",
                    Modifier.weight(1f),
                    fontSize = 26.sp, letterSpacing = 5.sp,
                    color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text("← 返回", color = T.TextSubtle, fontSize = 13.sp, maxLines = 1)
            }

            // 主区：玩法大卡 ×2 | 题目范围
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GameModeCard(
                    Icons.Filled.ThumbUp, "填空补诗", "诗里缺了个字，选出来！",
                    selected = mode == GameViewModel.MODE_FILL,
                    modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focusRequester),
                    onFocusChange = tone,
                    onClick = { startWith(GameViewModel.MODE_FILL) }
                )
                GameModeCard(
                    Icons.Filled.VolumeUp, "听音辨诗", "听朗读，猜猜是哪首诗？",
                    selected = mode == GameViewModel.MODE_LISTEN,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onFocusChange = tone,
                    onClick = { startWith(GameViewModel.MODE_LISTEN) }
                )

                // 右列：题目范围（竖排三选一，highlight=选中，与全部唐诗页筛选同视觉；整列垂直居中）
                Column(
                    Modifier.weight(0.8f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
                ) {
                    Text("题目范围", color = T.TextSubtle, fontSize = 12.sp, letterSpacing = 2.sp)
                    listOf(
                        GameViewModel.SCOPE_TODAY to "今日所学",
                        GameViewModel.SCOPE_LEARNED to "已学全部",
                        GameViewModel.SCOPE_ALL to "全部 ${vm.all.size} 首"
                    ).forEach { (value, label) ->
                        FocusablePillButton(
                            label,
                            highlight = scope == value,
                            segment = true,
                            onClick = { scope = value },
                            onFocusChange = tone
                        )
                    }
                }
            }

            KeyHintBar("◀ ▲▼ 选择玩法和范围　OK 开始")
        }

        // 题池不足提示条（覆盖顶部，不挤压布局）
        if (showPoolHint) {
            Text(
                "先去学几首诗再来吧！",
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
 * 玩法大卡：图标 + 标题 + 副标题 + 小字说明。
 * 选中态实现：selected 时把 FocusCard 的 focused 视觉常显（橙描边），聚焦再叠 scale 动画——
 * 未聚焦的选中卡只有描边不放大，聚焦卡（无论选没选中）都描边 + 放大。
 */
@Composable
private fun GameModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale else 1f, label = "modeCardScale")
    FocusCard(
        modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .scale(scale)
            .tvFocusable(onClick),
        focused = focused || selected
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Icon(icon, null, Modifier.size(40.dp), tint = T.Orange)
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                fontSize = 22.sp, letterSpacing = 3.sp,
                color = T.TextTitle, fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = T.TextBody, fontSize = 14.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text("${GameViewModel.ROUND_LEN} 题一轮 · 支持已学唐诗", color = T.TextSubtle, fontSize = 12.sp)
        }
    }
}
