# Compose for TV 迁移实现计划（tv-material 1.1.0 重构 UI）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 引入 `androidx.tv:tv-material:1.1.0`，用官方组件替换全部手写 TV 焦点原语（`tvFocusable`/`FocusCard`/`FocusableCard`/`FocusablePillButton`），焦点视觉切换官方默认样式，暖色调与全部交互行为保持不变。

**Architecture:** 两阶段——阶段 1 只升级工具链（BOM/Kotlin/KSP）并把主题切到 tv-material `MaterialTheme`（`T` 调色板映射，m3 主题嵌套保留给对话框），实机验收后进入阶段 2：先在 `Shared.kt` 加官方样式助手（`focusTone` + `ButtonDefaults`/`CardDefaults` 工厂），再按屏替换 8 个屏幕的 33 处直接调用点与 6 个私有组件内实现，最后删除旧原语与守护测试、更新规格书、全量验收。

**Tech Stack:** Kotlin 2.1.20 / Compose BOM 2026.02.00（Compose 1.10.3 + material3 1.4.0）/ androidx.tv:tv-material 1.1.0 / KSP 2.1.20-1.0.31 / AGP 8.7.3 + Gradle 8.11.1 + compileSdk 35 + minSdk 23（全部不动）。

**规格书:** `docs/superpowers/specs/2026-10-11-compose-tv-migration-design.md`（唯一事实来源；其中组件映射、行为接线、测试处置、验收策略与本计划一一对应）。

---

## 关键背景（执行者必读）

1. **官方默认焦点语义**（tv-material 1.1.0 源码实证）：
   - `Card`：rest 无描边，focused = **3dp `colorScheme.border` 描边 + scale 1.1**，无 glow（`Glow.None`），focused 容器**不**换色。`colorScheme.border` 经本设计映射 = `T.Orange`。
   - `Button`：胶囊（`CircleShape`），focused = **容器反色**（默认 `focusedContainerColor = onSurface`）+ scale 1.1。本计划把反色目标映射为主橙（暖色决策）。
   - 交互式 `Surface`：`ClickableSurfaceDefaults` 同上机制，`enabled=false` 时**仍可聚焦**（官方设计）——禁用药丸必须另加 `focusProperties { canFocus = false }`。
2. **颜色双主题**：`TangshiTheme` = tv `MaterialTheme`（外层，给官方组件）+ m3 `MaterialTheme`（内层，给 `AlertDialog`/`TextButton`，两套 CompositionLocal 类型不同不冲突）。组件内**无显式颜色**的 `Text`/`Icon` 必须用 `androidx.tv.material3.Text/Icon`（别名 `TvText`/`TvIcon`），它们才读得到官方组件提供的 `LocalContentColor`；m3 `Text`/`Icon` 读的是 m3 主题的色，拿不到焦点态变化。
3. **音效**：8 个屏幕各有一份相同的 `tone` lambda（首焦静音 + `g.keyTone.focusMove()`），**本计划不改它**，只把挂载点从自定义组件的 `onFocusChange` 参数改为 `Modifier.focusTone(...)`。
4. **行为红线**（实机验收必查）：MENU 键回设置页、ChannelUp/Down 翻页、Search 浮层焦点陷阱、初始焦点、禁用药丸不可聚焦、焦点音效首焦静音——全部必须与迁移前一致。
5. **已知视觉差异（预期，交实机视觉验收判定）**：焦点缩放 1.06→官方 1.1（大卡按布局豁免）；暖影→官方默认投影色；药丸内图标颜色统一跟随 `contentColor`（旧实现图标恒橙、文字棕色，官方组件单色无法拆分）；药丸文字字号走官方 `labelLarge`；渐变填充（游戏播放钮/焦点反色）改官方单色。

---

# 阶段 1：工具链与主题（独立验收门）

### Task 1: 版本升级 + 引入 tv-material

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 修改 `gradle/libs.versions.toml`**

`[versions]` 段三处：

```toml
agp = "8.7.3"
kotlin = "2.1.20"          # 原 2.0.21
composeBom = "2026.02.00"  # 原 2024.12.01；恰好含 Compose 1.10.3（tv-material 1.1.0 编译基线）+ material3 1.4.0
tvMaterial = "1.1.0"       # 新增
```

`[libraries]` 段新增（放在 compose 组之后）：

```toml
tv-material = { group = "androidx.tv", name = "tv-material", version.ref = "tvMaterial" }
```

`[plugins]` 段 ksp 版本：

```toml
ksp = { id = "com.google.devtools.ksp", version = "2.1.20-1.0.31" }
```

注意：`kotlin-android` / `kotlin-compose` / `kotlin-serialization` 三个插件都是 `version.ref = "kotlin"`，随 `kotlin = "2.1.20"` 一起生效，不用逐个改。

- [ ] **Step 2: 修改 `app/build.gradle.kts`**

在 `implementation(libs.compose.material3)` 之后加一行：

```kotlin
    implementation(libs.tv.material)
```

- [ ] **Step 3: 编译 + 全量单测**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`（现有 10 个测试类全绿；若 Material3 1.4.0 或 Compose 1.10.3 引入回归，此处是第一个拦截点）

- [ ] **Step 3b: 规避 BOM 连带的 lint 崩溃（审查发现，2026-10-11 追加）**

在 `app/build.gradle.kts` 的 `android { ... }` 块内（`buildTypes` 之后、`compileOptions` 之前）加：

```kotlin
    lint { disable += "NullSafeMutableLiveData" }
```

原因：BOM 2026.02.00 → compose 1.10.3 → 依赖链把 `androidx.lifecycle` 解析到 2.9.4，其 `lifecycle-livedata-core-2.9.4.aar` 内嵌 lint.jar 的 `NonNullableMutableLiveDataDetector` 与 AGP 8.7.3 自带 lint 运行时 UAST API 不兼容，`lintVitalAnalyzeRelease` 抛 `IncompatibleClassChangeError`，`assembleRelease` 必挂（已在干净 worktree 双向二分实证：仅 BOM 升级触发）。此行是不违反"AGP 不动"约束下的最小修复（另一条路是升 AGP 8.9.1，属计划约束外）。

- [ ] **Step 4: 打 debug 包 + release 包（双门禁）**

Run: `./gradlew :app:assembleDebug` 然后 `./gradlew :app:assembleRelease`
Expected: 均 `BUILD SUCCESSFUL`（release 门禁是本仓库真实发布路径——lintVital 必过；Task 15 验收同样复查此门禁）

- [ ] **Step 5: 提交**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: 升级 Compose BOM 2026.02.00 + Kotlin 2.1.20 + 引入 tv-material 1.1.0"
```

**失败回退路径（规格书已批准）：** 若报 compose compiler / kotlin metadata 兼容错误，把 `kotlin` 升到 2.2.x 对应稳定版、`ksp` 同步为 `2.2.x-y.z.w`（KSP 版本表以 maven 实查为准），BOM 不动；仍失败则 BOM 逐级上调。此决策只在阶段 1 内解决，不带入阶段 2。

---

### Task 2: tv ColorScheme 映射 + TangshiTheme 切换（TDD）

**Files:**
- Create: `app/src/test/java/com/rakor/tangshi/ui/theme/TvColorSchemeTest.kt`
- Modify: `app/src/main/java/com/rakor/tangshi/ui/theme/Theme.kt`

- [ ] **Step 1: 写失败测试**

创建 `app/src/test/java/com/rakor/tangshi/ui/theme/TvColorSchemeTest.kt`：

```kotlin
package com.rakor.tangshi.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * T 调色板 → tv ColorScheme（tv-material）映射的回归测试。
 * 与 ThemeColorSchemeTest（守护 m3 对话框方案）互补：本测试守护官方组件主题。
 */
class TvColorSchemeTest {

    @Test
    fun `官方焦点描边取 border 槽位 映射为主橙`() {
        // tv Card/Button 的 focusedBorder 默认读 colorScheme.border（3dp），
        // 这是"官方默认焦点描边"在本应用呈现主橙的唯一落点
        assertEquals(T.Orange, TangshiTvColorScheme.border)
    }

    @Test
    fun `背景与表面保持暖米色`() {
        assertEquals(T.BgMid, TangshiTvColorScheme.background)
        assertEquals(T.BgMid, TangshiTvColorScheme.surface)
        assertNotEquals(TangshiTvColorScheme.surface, TangshiTvColorScheme.surfaceVariant)
    }

    @Test
    fun `反色语义槽位映射暖色`() {
        // Button 默认 focusedContainerColor = onSurface、inverseOnSurface 供反色文字
        assertEquals(T.TextTitle, TangshiTvColorScheme.onSurface)
        assertEquals(T.Card, TangshiTvColorScheme.inverseOnSurface)
        assertEquals(T.Orange, TangshiTvColorScheme.primary)
    }

    @Test
    fun `遮罩与错误色与 m3 方案一致`() {
        assertEquals(T.Scrim, TangshiTvColorScheme.scrim)
        assertEquals(T.Wrong, TangshiTvColorScheme.error)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rakor.tangshi.ui.theme.TvColorSchemeTest"`
Expected: FAIL，`Unresolved reference: TangshiTvColorScheme`

- [ ] **Step 3: 在 Theme.kt 实现 tv 映射与主题切换**

`app/src/main/java/com/rakor/tangshi/ui/theme/Theme.kt` 修改：

import 区新增（保留全部现有 m3 import）：

```kotlin
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Typography as TvTypography
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
```

在 `TangshiColorScheme` 之后、`TangshiTheme` 之前新增：

```kotlin
/**
 * tv-material 配色：T 调色板 → tv ColorScheme 全槽位映射（规格 2026-10-11 §阶段1）。
 *
 * 两条关键映射：
 *  1. border = T.Orange —— 官方 Card/Button 的 focusedBorder 默认从 colorScheme.border 取色，
 *     这是"官方默认焦点描边"在本应用呈现主橙的落点（TvColorSchemeTest 守护）。
 *  2. surface = BgMid（米色）而非 Card —— 与 m3 方案同约束，避免 tonalElevation 掺色。
 *
 * tv ColorScheme 没有 surfaceContainer* 层级（对话框不走它），
 * m3 的 [TangshiColorScheme] 继续保留，仅供 AlertDialog/TextButton 使用。
 */
internal val TangshiTvColorScheme = tvDarkColorScheme(
    primary = T.Orange,
    onPrimary = Color.White,
    primaryContainer = T.BadgeBg,
    onPrimaryContainer = T.TextTitle,
    inversePrimary = T.OrangeLight,
    secondary = T.OrangeSoft,
    onSecondary = Color.White,
    secondaryContainer = T.ChipBg,
    onSecondaryContainer = T.TextBody,
    tertiary = T.Amber,
    onTertiary = Color.White,
    tertiaryContainer = T.BadgeBg,
    onTertiaryContainer = T.TextTitle,
    background = T.BgMid,
    onBackground = T.TextBody,
    surface = T.BgMid,
    onSurface = T.TextTitle,
    surfaceVariant = T.Idle,
    onSurfaceVariant = T.TextBody,
    surfaceTint = T.Orange,
    inverseSurface = T.TextTitle,
    inverseOnSurface = T.Card,
    error = T.Wrong,
    onError = Color.White,
    errorContainer = T.BadgeBg,
    onErrorContainer = T.TextTitle,
    border = T.Orange,
    borderVariant = T.TextSubtle,
    scrim = T.Scrim,
)
```

`TangshiTheme` 整体替换为（tv 主题在外、m3 主题嵌套在内）：

```kotlin
@Composable
fun TangshiTheme(content: @Composable () -> Unit) =
    TvMaterialTheme(
        colorScheme = TangshiTvColorScheme,
        typography = TvTypography(
            titleLarge = TextStyle(
                fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
                letterSpacing = 4.sp, color = T.TextTitle
            )
        )
    ) {
        // 对话框仍走 material3（tv-material 无 Dialog 组件）；同一 T 色板全槽位映射，
        // 约束见上方 TangshiColorScheme 注释与 ThemeColorSchemeTest
        MaterialTheme(
            colorScheme = TangshiColorScheme,
            typography = Typography(
                titleLarge = TextStyle(
                    fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
                    letterSpacing = 4.sp, color = T.TextTitle
                )
            ),
            content = content
        )
    }
```

- [ ] **Step 4: 运行新测试与既有主题测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rakor.tangshi.ui.theme.*"`
Expected: PASS（`TvColorSchemeTest` 4 条 + `ThemeColorSchemeTest` 5 条）

- [ ] **Step 5: 全量单测 + 编译**

Run: `./gradlew :app:testDebugUnitTest` 然后 `./gradlew :app:assembleDebug`
Expected: 均 `BUILD SUCCESSFUL`

- [ ] **Step 6: 提交**

```bash
git add app/src/test/java/com/rakor/tangshi/ui/theme/TvColorSchemeTest.kt app/src/main/java/com/rakor/tangshi/ui/theme/Theme.kt
git commit -m "feat: TangshiTheme 切 tv-material MaterialTheme——T 调色板映射 tv ColorScheme，m3 嵌套留给对话框"
```

---

### Task 3: 阶段 1 实机验收（进入阶段 2 的闸门）

**Files:** 无代码改动（发现问题回 Task 1/2 修复后重跑本任务）

- [ ] **Step 1: 安装并启动**

Run:
```bash
./gradlew :app:installDebug
adb shell monkey -p com.rakor.tangshi -c android.intent.category.LEANBACK_LAUNCHER 1
```
Expected: szj_tv 模拟器上应用正常启动到首页（`adb devices` 先确认设备在线；如离线参考记忆里的 szj_tv 启动方法）

- [ ] **Step 2: 走查清单（此阶段视觉应与迁移前完全一致）**

- [ ] 首页渲染正常（暖色奶油底、主卡、统计列、四入口）
- [ ] 焦点移动有提示音、首焦静音；OK 进出正常
- [ ] 设置页「清除学习进度」对话框：白卡底、非深灰（m3 嵌套主题生效的证据）
- [ ] 详情页「我会背了」对话框同样正常
- [ ] MENU 键回设置页；ChannelUp/Down 在详情页翻诗
- [ ] 搜索页浮层焦点陷阱、键盘激活边框正常

- [ ] **Step 3: 记录结论**

走查全过 → 直接进入阶段 2（本任务无提交）。任何一条不过 → 修复并回到本任务 Step 1，**不带病进入阶段 2**。

---

# 阶段 2：组件替换（按屏提交，单屏可回退）

### Task 4: Shared.kt 新增官方样式助手（增量，旧原语暂留）

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/components/Shared.kt`

- [ ] **Step 1: 新增 import**

在 `import com.rakor.tangshi.ui.theme.T` 之前加：

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
```

（`shadow`、`RoundedCornerShape`、`onFocusChanged`、`dp`、`Color`、`CircleShape` 均已存在。）

- [ ] **Step 2: 在文件末尾（`CreamBackground` 之后）追加助手**

```kotlin
// ===== 官方组件样式助手（tv-material + T 调色板；规格 2026-10-11 §组件映射）=====

/** 焦点音效挂载点：官方组件没有 onFocusChange 参数，统一挂 modifier 链首转发（全 app 28 处钩子的归宿） */
fun Modifier.focusTone(tone: (Boolean) -> Unit): Modifier =
    onFocusChanged { tone(it.isFocused) }

/** 白卡静态悬浮影（6dp，非焦点视觉；焦点视觉全走官方 border/scale） */
fun Modifier.cardRestingShadow(corner: Int): Modifier =
    shadow(6.dp, RoundedCornerShape(corner.dp))

/** 普通药丸：rest 白底 2dp 浅橙描边棕字；focused 官方反色语义 → 主橙底白字 */
@Composable
fun normalPillColors() = ButtonDefaults.colors(
    containerColor = T.Card,
    contentColor = T.TextBody,
    focusedContainerColor = T.Orange,
    focusedContentColor = Color.White,
    disabledContainerColor = T.Card.copy(alpha = 0.45f),
    disabledContentColor = T.TextBody.copy(alpha = 0.45f),
)

@Composable
fun normalPillBorder() = ButtonDefaults.border(
    border = Border(BorderStroke(2.dp, T.OrangeSoft), shape = CircleShape)
)

/** 强调药丸（主行动按钮 / 选中常驻强调）：rest ChipBg 底 3dp 橙描边橙字；focused 同普通档反色 */
@Composable
fun highlightPillColors() = ButtonDefaults.colors(
    containerColor = T.ChipBg,
    contentColor = T.Orange,
    focusedContainerColor = T.Orange,
    focusedContentColor = Color.White,
)

@Composable
fun highlightPillBorder() = ButtonDefaults.border(
    border = Border(BorderStroke(3.dp, T.Orange), shape = CircleShape)
)

/** 分段组药丸：焦点不反色（规格 §1 分段例外），focused 升 4dp 主橙描边；rest 选中=强调档本体 */
@Composable
fun segmentPillColors(selected: Boolean) = ButtonDefaults.colors(
    containerColor = if (selected) T.ChipBg else T.Card,
    contentColor = if (selected) T.Orange else T.TextBody,
    focusedContainerColor = if (selected) T.ChipBg else T.Card,
    focusedContentColor = if (selected) T.Orange else T.TextBody,
)

@Composable
fun segmentPillBorder(selected: Boolean) = ButtonDefaults.border(
    border = Border(
        BorderStroke(if (selected) 3.dp else 2.dp, if (selected) T.Orange else T.OrangeSoft),
        shape = CircleShape
    ),
    focusedBorder = Border(BorderStroke(4.dp, T.Orange), shape = CircleShape)
)

/** 官方卡片形状 / 配色 / 焦点描边（3dp colorScheme.border=主橙，官方默认），圆角跟随卡片 */
@Composable
fun cardShape(corner: Int) = CardDefaults.shape(shape = RoundedCornerShape(corner.dp))

@Composable
fun cardColors() = CardDefaults.colors(containerColor = T.Card, contentColor = T.TextTitle)

@Composable
fun cardBorder(corner: Int) = CardDefaults.border(
    focusedBorder = Border(
        BorderStroke(3.dp, MaterialTheme.colorScheme.border),
        RoundedCornerShape(corner.dp)
    )
)
```

- [ ] **Step 3: 编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest` 然后 `./gradlew :app:assembleDebug`
Expected: 均 `BUILD SUCCESSFUL`（纯增量，不改任何现有行为）

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/components/Shared.kt
git commit -m "feat: Shared 增官方样式助手——focusTone/药丸三档 ButtonDefaults 工厂/card 三件套"
```

---

### Task 5: HomeScreen 迁移

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 调整 import**

删除：
```kotlin
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusableCard
import com.rakor.tangshi.ui.components.FocusablePillButton
```
新增：
```kotlin
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.ui.components.cardBorder
import com.rakor.tangshi.ui.components.cardColors
import com.rakor.tangshi.ui.components.cardRestingShadow
import com.rakor.tangshi.ui.components.cardShape
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.normalPillBorder
import com.rakor.tangshi.ui.components.normalPillColors
```

- [ ] **Step 2: 主卡 FocusableCard → 官方 Card（HomeScreen.kt:187-196 调用头）**

将：

```kotlin
                FocusableCard(
                    Modifier
                        .weight(1.55f)
                        .fillMaxHeight()
                        .zIndex(1f)   // 聚焦放大时压过右列邻卡，避免描边被盖
                        .focusRequester(focusRequester),
                    scaleFactor = 1.03f,   // 大卡小倍率：1.06 会挤压两侧布局
                    onFocusChange = tone,
                    onClick = { poem?.let { onPoem(it.id) } }
                ) {
```

替换为：

```kotlin
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
```

诗卡内容（原 197-269 行的 `val p = poem` 起整段）**原样不动**。

- [ ] **Step 3: 四个底部入口药丸 → 官方 Button（HomeScreen.kt:284-292）**

将：

```kotlin
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusablePillButton(
                    "搜索", Icons.Filled.Search,
                    onClick = onSearch, onFocusChange = tone
                )
                FocusablePillButton("全部唐诗", onClick = onLibrary, onFocusChange = tone)
                FocusablePillButton("背诗小游戏", Icons.Filled.SportsEsports, onClick = onGames, onFocusChange = tone)
                FocusablePillButton("家长设置", Icons.Filled.Settings, onClick = onSettings, onFocusChange = tone)
            }
```

替换为：

```kotlin
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
```

- [ ] **Step 4: StatCard 的 FocusCard → 静态 Surface（HomeScreen.kt:302）**

将：

```kotlin
    FocusCard(modifier, focused = false, corner = 18) {
```

替换为：

```kotlin
    Surface(
        modifier = modifier.cardRestingShadow(18),
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = T.Card)
    ) {
```

（StatCard 内容体原样不动：`Surface` 内容是 `BoxScope`，原 lambda 体只有一个自成一体的 `Row`，无 `ColumnScope` 成员引用，直接可编译。）

- [ ] **Step 5: 编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest` 然后 `./gradlew :app:assembleDebug`
Expected: 均 `BUILD SUCCESSFUL`

- [ ] **Step 6: 本屏实机冒烟**

安装启动后走查：主卡焦点官方描边+1.03 缩放、四入口反色焦点、统计卡静态白卡、焦点音效、初始焦点落主卡。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/home/HomeScreen.kt
git commit -m "refactor: 首页迁移 tv-material——主卡 Card/入口 Button/统计卡 Surface"
```

---

### Task 6: LibraryScreen 迁移

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/library/LibraryScreen.kt`

- [ ] **Step 1: 调整 import**

删除：
```kotlin
import com.rakor.tangshi.ui.components.FocusableCard
import com.rakor.tangshi.ui.components.FocusablePillButton
```
新增：
```kotlin
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.ui.components.cardBorder
import com.rakor.tangshi.ui.components.cardColors
import com.rakor.tangshi.ui.components.cardRestingShadow
import com.rakor.tangshi.ui.components.cardShape
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.segmentPillBorder
import com.rakor.tangshi.ui.components.segmentPillColors
```

- [ ] **Step 2: 筛选药丸 → Button（LibraryScreen.kt:186-197）**

将：
```kotlin
            // 筛选行：选中=浅橙底橙描边橙字（highlight），聚焦=4dp 主橙描边不反色（segment）；动效由 FocusablePillButton 自管
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilter.entries.forEach { f ->
                    FocusablePillButton(
                        f.label,
                        if (f == LibraryFilter.MASTERED) Icons.Filled.ThumbUp else null,
                        highlight = filter == f,
                        segment = true,
                        onClick = { filter = f },
                        onFocusChange = tone
                    )
                }
            }
```
替换为：
```kotlin
            // 筛选行：选中=强调档本体（浅橙底橙描边），焦点不反色升 4dp 主橙描边（segment 官方样式参数）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilter.entries.forEach { f ->
                    Button(
                        onClick = { filter = f },
                        colors = segmentPillColors(filter == f),
                        border = segmentPillBorder(filter == f),
                        modifier = Modifier.focusTone(tone)
                    ) {
                        if (f == LibraryFilter.MASTERED) {
                            TvIcon(Icons.Filled.ThumbUp, null)
                            Spacer(Modifier.width(6.dp))
                        }
                        TvText(f.label)
                    }
                }
            }
```

- [ ] **Step 3: 网格诗卡 FocusableCard → Card（LibraryScreen.kt:221-232 调用头）**

将：
```kotlin
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
```
替换为：
```kotlin
                            Card(
                                onClick = { onPoem(poem.id) },
                                modifier = Modifier
                                    .cardRestingShadow(16)
                                    .width(cardWidth)
                                    .fillMaxHeight()
                                    .then(if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier)
                                    .focusTone { f ->
                                        if (f) focusedIndex = index
                                        tone(f)
                                    },
                                shape = cardShape(16),
                                colors = cardColors(),
                                border = cardBorder(16),
                            ) {
```
卡片内容体（原 233-254 行）**原样不动**（顶层 `Box(Modifier.weight(1f)...)` 依赖 `ColumnScope`，`Card` 内容正是 `ColumnScope`，直接可编译）。

- [ ] **Step 4: 编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest` && `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 本屏实机冒烟**

网格卡焦点官方描边、分段筛选选中/焦点态、`focusedIndex` 分页指示（页码提示）正常、焦点音效正常。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/library/LibraryScreen.kt
git commit -m "refactor: 诗库页迁移 tv-material——筛选分段 Button + 网格诗卡 Card"
```

---

### Task 7: DetailScreen 迁移

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/detail/DetailScreen.kt`

- [ ] **Step 1: 调整 import**

删除：
```kotlin
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusablePillButton
```
新增：
```kotlin
import androidx.compose.ui.focus.focusProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.ui.components.cardBorder
import com.rakor.tangshi.ui.components.cardColors
import com.rakor.tangshi.ui.components.cardRestingShadow
import com.rakor.tangshi.ui.components.cardShape
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.highlightPillBorder
import com.rakor.tangshi.ui.components.highlightPillColors
import com.rakor.tangshi.ui.components.normalPillBorder
import com.rakor.tangshi.ui.components.normalPillColors
```

- [ ] **Step 2: 顶栏切诗芯片（禁用药丸陷阱）——DetailScreen.kt:258-264 与 269-274**

将左芯片：
```kotlin
                    FocusablePillButton(
                        "",
                        icon = Icons.Filled.ChevronLeft,
                        enabled = prevPoem != null,
                        onClick = { prevPoem?.let { currentPoemId = it.id } },
                        onFocusChange = tone
                    )
```
替换为：
```kotlin
                    // 官方 disabled Surface 仍可聚焦（官方设计），必须显式 canFocus=false 保持"跳过禁用项"
                    Button(
                        onClick = { prevPoem?.let { currentPoemId = it.id } },
                        enabled = prevPoem != null,
                        colors = normalPillColors(),
                        border = normalPillBorder(),
                        modifier = Modifier
                            .focusTone(tone)
                            .focusProperties { canFocus = prevPoem != null }
                    ) { TvIcon(Icons.Filled.ChevronLeft, null) }
```

右芯片同法（`nextPoem`、`ChevronRight`）：
```kotlin
                    Button(
                        onClick = { nextPoem?.let { currentPoemId = it.id } },
                        enabled = nextPoem != null,
                        colors = normalPillColors(),
                        border = normalPillBorder(),
                        modifier = Modifier
                            .focusTone(tone)
                            .focusProperties { canFocus = nextPoem != null }
                    ) { TvIcon(Icons.Filled.ChevronRight, null) }
```

- [ ] **Step 3: 连读/我会背了药丸——DetailScreen.kt:380-391**

将：
```kotlin
                            FocusablePillButton(
                                "连读",
                                icon = if (loopEnabled) Icons.Filled.Check else Icons.Filled.Replay,
                                highlight = loopEnabled,
                                onClick = { loopEnabled = !loopEnabled },
                                onFocusChange = tone
                            )
                            FocusablePillButton(
                                "我会背了", Icons.Filled.ThumbUp,
                                onClick = { showMasteredDialog = true },
                                onFocusChange = tone
                            )
```
替换为：
```kotlin
                            Button(
                                onClick = { loopEnabled = !loopEnabled },
                                colors = if (loopEnabled) highlightPillColors() else normalPillColors(),
                                border = if (loopEnabled) highlightPillBorder() else normalPillBorder(),
                                modifier = Modifier.focusTone(tone)
                            ) {
                                TvIcon(
                                    if (loopEnabled) Icons.Filled.Check else Icons.Filled.Replay,
                                    null
                                )
                                Spacer(Modifier.width(6.dp))
                                TvText("连读")
                            }
                            Button(
                                onClick = { showMasteredDialog = true },
                                colors = normalPillColors(),
                                border = normalPillBorder(),
                                modifier = Modifier.focusTone(tone)
                            ) {
                                TvIcon(Icons.Filled.ThumbUp, null)
                                Spacer(Modifier.width(6.dp))
                                TvText("我会背了")
                            }
```

- [ ] **Step 4: 诗行卡 FocusCard → 官方 Card（DetailScreen.kt:286-319 调用头）**

先删状态（DetailScreen.kt:146）：
```kotlin
    var poemAreaFocused by remember { mutableStateOf(false) }   // 诗行卡聚焦视觉（橙描边）
```
（`poemAreaFocused` 全文件仅在这处赋值与 `focused =` 传参使用，删除后无残余。）

将调用头：
```kotlin
                    FocusCard(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                poemAreaFocused = it.isFocused
                                tone(it.isFocused)
                            }
                            .onPreviewKeyEvent { e ->
```
替换为：
```kotlin
                    Card(
                        // OK 由下方 onPreviewKeyEvent 消费（Enter/NumPadEnter 一并覆盖）；
                        // onClick 兜底同语义，双保险不重复触发（preview 消费后 clickable 收不到）
                        onClick = { okOnPoemArea() },
                        modifier = Modifier
                            .cardRestingShadow(20)
                            .weight(1f)
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .focusTone(tone)
                            .onPreviewKeyEvent { e ->
```
原 preview 处理器内部（含 `Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { okOnPoemArea(); true }`）**原样保留**。

将尾部：
```kotlin }
                            .focusable(),
                        focused = poemAreaFocused
                    ) {
```
替换为：
```kotlin
                            },
                        shape = cardShape(20),
                        colors = cardColors(),
                        border = cardBorder(20),
                        // 满屏大卡不缩放（官方默认 1.1 会溢出列布局）；焦点提示靠官方 3dp 描边
                        scale = CardDefaults.scale(focusedScale = 1f),
                    ) {
```
（原 `.focusable()` 删除——官方 `Card` 自带可聚焦；卡内容体原 320-373 行原样不动。）

- [ ] **Step 5: 背景卡 FocusCard → Card（DetailScreen.kt:404-433）**

先删状态（DetailScreen.kt:404）：
```kotlin
                var bgFocused by remember { mutableStateOf(false) }
```
将调用头：
```kotlin
                FocusCard(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged {
                            bgFocused = it.isFocused
                            tone(it.isFocused)
                        }
                        .onPreviewKeyEvent { e ->
```
替换为：
```kotlin
                Card(
                    onClick = {},   // OK 无动作（现状即如此）；官方 Card 需要 onClick 才有焦点态
                    modifier = Modifier
                        .cardRestingShadow(20)
                        .weight(1f)
                        .fillMaxHeight()
                        .focusTone(tone)
                        .onPreviewKeyEvent { e ->
```
原 preview 处理器内部**原样保留**。将尾部：
```kotlin
                        .focusable(),
                    focused = bgFocused
                ) {
```
替换为：
```kotlin
                        },
                    shape = cardShape(20),
                    colors = cardColors(),
                    border = cardBorder(20),
                    scale = CardDefaults.scale(focusedScale = 1f),
                ) {
```
（背景卡内容体原 434-445 行原样不动。）

- [ ] **Step 6: 编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest` && `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`（若 `onFocusChanged`/`mutableStateOf`/`focusable` import 报未使用**警告**可忽略；报**错误**才处理）

- [ ] **Step 7: 本屏实机冒烟**

诗行卡：初始落焦、◀▶ 逐句、▲▼ 滚动、OK 播放/停止、CH± 翻诗、焦点官方 3dp 描边不缩放；背景卡 ▲▼ 滚动；顶栏芯片禁用侧焦点跳过（到第一首时按左，焦点不得落上左芯片）；连读 highlight 档、我会背了对话框、焦点音效。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/detail/DetailScreen.kt
git commit -m "refactor: 详情页迁移 tv-material——诗卡/背景卡 Card + 药丸 Button（禁用 canFocus 陷阱已处理）"
```

---

### Task 8: SearchScreen 迁移

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt`

- [ ] **Step 1: 调整 import**

删除：
```kotlin
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusScale
import com.rakor.tangshi.ui.components.FocusableCard
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.tvFocusable
```
新增：
```kotlin
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import androidx.compose.foundation.BorderStroke
import com.rakor.tangshi.ui.components.cardBorder
import com.rakor.tangshi.ui.components.cardColors
import com.rakor.tangshi.ui.components.cardRestingShadow
import com.rakor.tangshi.ui.components.cardShape
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.normalPillBorder
import com.rakor.tangshi.ui.components.normalPillColors
```
（`border`、`RoundedCornerShape`、`focusProperties` 本文件已有，勿重复添加。）

- [ ] **Step 2: 筛选行两枚药丸（SearchScreen.kt:216-229）**

将：
```kotlin
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
```
替换为：
```kotlin
                Button(
                    onClick = { poetPickerOpen = true },
                    colors = normalPillColors(),
                    border = normalPillBorder(),
                    modifier = Modifier.focusTone(tone).focusRequester(poetBtnFocus)
                ) { TvText("诗人：${poetFilter ?: "全部"} ▾") }
                Button(
                    // 键盘键常驻组合（requester 恒已挂载），直接移焦点即可；激活边框由
                    // 键盘卡 hasFocus 双向驱动（见下方 onFocusChanged），此处无需置位
                    onClick = { runCatching { firstKeyFocus.requestFocus() } },
                    colors = normalPillColors(),
                    border = normalPillBorder(),
                    modifier = Modifier.focusTone(tone)
                ) {
                    TvIcon(Icons.Filled.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    TvText("诗名/诗句关键字：" + keyword.ifEmpty { "点按输入" } + "▏")
                }
```

- [ ] **Step 3: 键盘白卡 FocusCard → 静态 Surface + 激活态描边（SearchScreen.kt:270-277）**

将：
```kotlin
                FocusCard(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { keyboardActive = it.hasFocus },
                    focused = keyboardActive,
                    corner = 18
                ) {
```
替换为：
```kotlin
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
```
键盘内容体（原 278-313 行 `Column(...)` 起）**原样不动**。

- [ ] **Step 4: 诗人浮层白卡 → 静态 Surface（SearchScreen.kt:330-337）**

将：
```kotlin
                FocusCard(
                    Modifier
                        .width(600.dp)
                        .heightIn(max = 420.dp)
                        .focusProperties { exit = { pickerFirstFocus } },
                    focused = false,
                    corner = 20
                ) {
```
替换为：
```kotlin
                Surface(
                    modifier = Modifier
                        .width(600.dp)
                        .heightIn(max = 420.dp)
                        .cardRestingShadow(20)
                        .focusProperties { exit = { pickerFirstFocus } },
                    shape = RoundedCornerShape(20.dp),
                    colors = SurfaceDefaults.colors(containerColor = T.Card)
                ) {
```
浮层内容体（原 338-368 行）**原样不动**。焦点陷阱 `focusProperties.exit` 与官方组件无耦合，原样有效。

- [ ] **Step 5: ResultRow 的 FocusableCard → Card（SearchScreen.kt:387）**

将：
```kotlin
    FocusableCard(modifier.fillMaxWidth(), corner = 14, onFocusChange = onFocusChange, onClick = onClick) {
```
替换为：
```kotlin
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .cardRestingShadow(14)
            .focusTone(onFocusChange),
        shape = cardShape(14),
        colors = cardColors(),
        border = cardBorder(14),
    ) {
```
结果行内容体（原 388-410 行）原样不动。

- [ ] **Step 6: KeyBox 内部重写为交互式 Surface（SearchScreen.kt:427-451）**

将整个 `KeyBox` 函数体（从 `@Composable` 注释「键盘单键」到函数收尾 `}`）替换为：

```kotlin
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
        shape = RoundedCornerShape(KeyCorner),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = T.ChipBg,
            contentColor = T.TextBody,
            focusedContainerColor = T.ChipBg,   // 键位焦点不反色（保留描边+放大语义）
            focusedContentColor = T.TextBody,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, T.Orange), RoundedCornerShape(KeyCorner))
        ),
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}
```

- [ ] **Step 7: PickerItem 内部重写为交互式 Surface（SearchScreen.kt:455-477）**

将整个 `PickerItem` 函数替换为：

```kotlin
/** 浮层诗人项："姓名 诗数"小卡，官方焦点描边+缩放 */
@Composable
private fun PickerItem(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.focusTone(onFocusChange),
        shape = RoundedCornerShape(12.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = T.ChipBg,
            contentColor = T.TextBody,
            focusedContainerColor = T.ChipBg,
            focusedContentColor = T.TextBody,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, T.Orange), RoundedCornerShape(12.dp))
        ),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            fontSize = 14.sp, color = T.TextBody
        )
    }
}
```

- [ ] **Step 8: 编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest` && `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: 本屏实机冒烟**

筛选药丸、结果行焦点、键盘进退（焦点激活边框双向驱动）、键位焦点描边、浮层焦点陷阱与首项落焦、`KeyBox` 禁用无（无禁用场景）、焦点音效。

- [ ] **Step 10: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt
git commit -m "refactor: 搜索页迁移 tv-material——药丸 Button/结果卡 Card/键盘与浮层 Surface"
```

---

### Task 9: GameSetupScreen 迁移

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/game/GameSetupScreen.kt`

- [ ] **Step 1: 调整 import**

删除：
```kotlin
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusScale
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.tvFocusable
```
新增：
```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.ui.components.cardColors
import com.rakor.tangshi.ui.components.cardRestingShadow
import com.rakor.tangshi.ui.components.cardShape
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.segmentPillBorder
import com.rakor.tangshi.ui.components.segmentPillColors
```
（`RoundedCornerShape` 已有。）

- [ ] **Step 2: GameModeCard 内部重写（GameSetupScreen.kt:188-229）**

将 `GameModeCard` 函数（含 KDoc）整体替换为：

```kotlin
/**
 * 玩法大卡：图标 + 标题 + 副标题 + 小字说明。
 * 选中态实现：selected 时 rest 即 4dp 主橙描边常显；聚焦（无论选没选中）4dp 描边 + 官方缩放。
 * 焦点视觉全部由官方 Card 的 border/scale 参数承接（规格 2026-10-11 §组件映射）。
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
    val selectedBorder = Border(BorderStroke(4.dp, T.Orange), RoundedCornerShape(20.dp))
    Card(
        onClick = onClick,
        modifier = modifier
            .cardRestingShadow(20)
            .focusTone(onFocusChange),
        shape = cardShape(20),
        colors = cardColors(),
        border = CardDefaults.border(
            border = if (selected) selectedBorder else Border.None,
            focusedBorder = selectedBorder
        ),
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
```

- [ ] **Step 3: 题目范围三枚分段药丸（GameSetupScreen.kt:153-161）**

将：
```kotlin
                    ).forEach { (value, label) ->
                        FocusablePillButton(
                            label,
                            highlight = scope == value,
                            segment = true,
                            onClick = { scope = value },
                            onFocusChange = tone
                        )
                    }
```
替换为：
```kotlin
                    ).forEach { (value, label) ->
                        Button(
                            onClick = { scope = value },
                            colors = segmentPillColors(scope == value),
                            border = segmentPillBorder(scope == value),
                            modifier = Modifier.focusTone(tone)
                        ) { TvText(label) }
                    }
```

- [ ] **Step 4: 编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest` && `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 本屏实机冒烟**

两张玩法大卡：选中卡 rest 4dp 橙描边、聚焦描边+官方缩放、OK 开始；范围三选一选中/焦点态；焦点音效。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/game/GameSetupScreen.kt
git commit -m "refactor: 游戏选择页迁移 tv-material——玩法大卡 Card + 范围分段 Button"
```

---

### Task 10: GameQuestionScreen 迁移

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/game/GameQuestionScreen.kt`

- [ ] **Step 1: 调整 import**

删除：
```kotlin
import com.rakor.tangshi.ui.components.CardShadow
import com.rakor.tangshi.ui.components.FocusScale
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.tvFocusable
```
新增：
```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.normalPillBorder
import com.rakor.tangshi.ui.components.normalPillColors
```
（`shadow`、`RoundedCornerShape`、`border`、`CircleShape` 均已有。）

- [ ] **Step 2: NEED_MORE 空态「返回」药丸（GameQuestionScreen.kt:151-155）**

将：
```kotlin
                            FocusablePillButton(
                                "返回",
                                onClick = onBack, onFocusChange = tone,
                                modifier = Modifier.focusRequester(backFocus)
                            )
```
替换为：
```kotlin
                            Button(
                                onClick = onBack,
                                colors = normalPillColors(),
                                border = normalPillBorder(),
                                modifier = Modifier.focusTone(tone).focusRequester(backFocus)
                            ) { TvText("返回") }
```

- [ ] **Step 3: RoundPlayButton 内部重写（GameQuestionScreen.kt:430-462）**

将整个 `RoundPlayButton` 函数（含 KDoc）替换为：

```kotlin
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
            .shadow(6.dp, CircleShape)
            .focusTone(onFocusChange)
            .size(56.dp),
        shape = CircleShape,
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
            focusedBorder = Border(BorderStroke(3.dp, Color.White), CircleShape)
        ),
    ) {
        Icon(
            Icons.Filled.VolumeUp, null,
            Modifier.size(28.dp),
            tint = if (enabled) Color.White else T.TextMuted
        )
    }
}
```
（调用点 `GameQuestionScreen.kt:382-387` 传 `onFocusChange = tone` 不变。）

- [ ] **Step 4: OptionBlock 内部重写（GameQuestionScreen.kt:468-497）**

将整个 `OptionBlock` 函数（含 KDoc）替换为：

```kotlin
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
        modifier = modifier.shadow(6.dp, shape).focusTone(onFocusChange),
        shape = shape,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (feedback == Feedback.CORRECT) T.Correct else T.Card,
            contentColor = T.TextTitle,
            // 已作答反馈底色在各状态恒定，焦点不反色（反馈色优先）
            focusedContainerColor = if (feedback == Feedback.CORRECT) T.Correct else T.Card,
            focusedContentColor = T.TextTitle,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (feedback == Feedback.WRONG) Border(BorderStroke(3.dp, T.Wrong), shape) else Border.None,
            focusedBorder = Border(
                BorderStroke(3.dp, if (feedback == Feedback.WRONG) T.Wrong else T.Orange),
                shape
            )
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
```
（调用点 285/401 行的 `onFocusChange = tone` 与内容 lambda 不变；内容文字均显式传色，不受 contentColor 影响。）

- [ ] **Step 5: 编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest` && `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 本屏实机冒烟**

填空/听音两题型：选项块焦点描边、答对绿底/答错红描边、800ms 反馈正常；播放圆钮焦点白边、置灰仍可聚焦但 OK 无效；NEED_MORE 返回钮；焦点音效。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/game/GameQuestionScreen.kt
git commit -m "refactor: 游戏答题页迁移 tv-material——选项块/播放钮 Surface + 返回 Button"
```

---

### Task 11: GameResultScreen 迁移

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/game/GameResultScreen.kt`

- [ ] **Step 1: 调整 import**

删除：
```kotlin
import com.rakor.tangshi.ui.components.CardShadow
import com.rakor.tangshi.ui.components.FocusablePillButton
```
新增：
```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Button
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.highlightPillBorder
import com.rakor.tangshi.ui.components.highlightPillColors
import com.rakor.tangshi.ui.components.normalPillBorder
import com.rakor.tangshi.ui.components.normalPillColors
```
（本文件原先无 `Spacer`/`width` import，药丸内容需要。）

- [ ] **Step 2: 按钮行（GameResultScreen.kt:104-112）**

将：
```kotlin
                FocusablePillButton(
                    "再来一轮", Icons.Filled.PlayArrow, highlight = true,
                    onClick = onAgain, onFocusChange = tone,
                    modifier = Modifier.focusRequester(focusRequester)
                )
                FocusablePillButton(
                    "返回首页", Icons.Filled.Home,
                    onClick = onHome, onFocusChange = tone
                )
```
替换为：
```kotlin
                Button(
                    onClick = onAgain,
                    colors = highlightPillColors(),
                    border = highlightPillBorder(),
                    modifier = Modifier.focusTone(tone).focusRequester(focusRequester)
                ) {
                    TvIcon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    TvText("再来一轮")
                }
                Button(
                    onClick = onHome,
                    colors = normalPillColors(),
                    border = normalPillBorder(),
                    modifier = Modifier.focusTone(tone)
                ) {
                    TvIcon(Icons.Filled.Home, null)
                    Spacer(Modifier.width(6.dp))
                    TvText("返回首页")
                }
```

- [ ] **Step 3: ResultStar 暖影改默认投影（GameResultScreen.kt:124-127）**

将：
```kotlin
        Modifier.then(
            if (reached) Modifier.shadow(10.dp, CircleShape, ambientColor = CardShadow, spotColor = CardShadow)
            else Modifier
        )
```
替换为（`CardShadow` 常量随旧体系废止，投影色走默认）：
```kotlin
        Modifier.then(
            if (reached) Modifier.shadow(10.dp, CircleShape)
            else Modifier
        )
```

- [ ] **Step 4: 编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest` && `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 本屏实机冒烟**

三星光环、再来一轮 highlight 档焦点、返回首页普通档、焦点音效。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/game/GameResultScreen.kt
git commit -m "refactor: 结算页迁移 tv-material——双按钮 Button + 星星投影去 CardShadow"
```

---

### Task 12: SettingsScreen 迁移

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/settings/SettingsScreen.kt`

- [ ] **Step 1: 调整 import**

删除：
```kotlin
import com.rakor.tangshi.ui.components.FocusCard
import com.rakor.tangshi.ui.components.FocusScale
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.tvFocusable
```
新增：
```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.rakor.tangshi.ui.components.cardRestingShadow
import com.rakor.tangshi.ui.components.focusTone
import com.rakor.tangshi.ui.components.segmentPillBorder
import com.rakor.tangshi.ui.components.segmentPillColors
```
（`border`、`RoundedCornerShape` 已有；本文件原无 `width` import，药丸内容需要。）

- [ ] **Step 2: 提示音开/关两枚分段药丸（SettingsScreen.kt:122-138）**

将：
```kotlin
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
```
替换为：
```kotlin
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
```

- [ ] **Step 3: SettingCard 的 FocusCard → 静态 Surface（SettingsScreen.kt:212）**

将：
```kotlin
    FocusCard(modifier, focused = false, corner = 18) {
```
替换为：
```kotlin
    Surface(
        modifier = modifier.cardRestingShadow(18),
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = T.Card)
    ) {
```
（SettingCard 体内层 `Column(...)` 结构原样不动。）

- [ ] **Step 4: WipeButton 内部重写（SettingsScreen.kt:225-242）**

将整个 `WipeButton` 函数（含 KDoc）替换为：

```kotlin
/** "清除 →" 入口：官方 Surface 焦点描边+缩放，TV 焦点可见性硬要求 */
@Composable
private fun WipeButton(onFocusChange: (Boolean) -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.focusTone(onFocusChange),
        shape = shape,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = T.Idle,
            contentColor = T.Orange,
            focusedContainerColor = T.ChipBg,   // 聚焦升浅橙底（原语义），不反深色
            focusedContentColor = T.Orange,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, T.Orange), shape)
        ),
    ) {
        Text(
            "清除 →",
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = T.Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
    }
}
```

- [ ] **Step 5: 编译 + 单测**

Run: `./gradlew :app:testDebugUnitTest` && `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 本屏实机冒烟**

设置卡静态白卡、开/关分段选中与焦点态、清除入口焦点描边、清除对话框（白卡底）、焦点音效。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/settings/SettingsScreen.kt
git commit -m "refactor: 设置页迁移 tv-material——设置卡 Surface + 分段 Button + 清除钮 Surface"
```

---

### Task 13: 删除旧原语与 CardShadowTest

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/components/Shared.kt`
- Delete: `app/src/test/java/com/rakor/tangshi/ui/components/CardShadowTest.kt`

- [ ] **Step 1: 确认零引用**

Run:
```bash
grep -rn "tvFocusable\|FocusCard(\|FocusableCard(\|FocusablePillButton\|FocusScale\|CardShadow" app/src/main/java app/src/test
```
Expected: 只剩 `Shared.kt` 内部的定义与 `CardShadowTest.kt` 一处。若屏幕目录还有残余 → 回对应屏任务补齐后再继续。

- [ ] **Step 2: 删除 Shared.kt 旧原语**

删除以下代码块（含 KDoc）：
- `CardShadow` 常量（L46-50，含注释）
- `FocusScale` 常量（L52-53）
- `tvFocusable`（L55-70）
- `FocusCard`（L72-95）
- `FocusableCard`（L97-133）
- `FocusablePillButton`（L135-200）

- [ ] **Step 3: 清理 Shared.kt 失效 import**

删除（新助手与保留组件不再使用）：

```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
```

保留：`background`、`shadow`、`Box`、`Row`、`Spacer`、`Arrangement`、`fillMaxSize`、`fillMaxWidth`、`padding`、`size`、`width`、`RoundedCornerShape`、`Icon`/`Text`(m3)、`Icons`/`ThumbUp`、`Composable`、`Alignment`、`Modifier`、`Brush`、`Color`、`dp`、`onFocusChanged`、`T`、`BoxScope`，以及 Task 4 新增的 5 个 tv/assist import。

- [ ] **Step 4: 删除测试**

```bash
git rm app/src/test/java/com/rakor/tangshi/ui/components/CardShadowTest.kt
```

- [ ] **Step 5: 编译 + 全量单测**

Run: `./gradlew :app:testDebugUnitTest` && `./gradlew :app:assembleDebug`
Expected: 均 `BUILD SUCCESSFUL`，测试数比删除前少 1 条

- [ ] **Step 6: 复查引用归零**

Run: `grep -rn "tvFocusable\|FocusCard(\|FocusableCard(\|FocusablePillButton\|FocusScale\|CardShadow" app/src/main/java app/src/test`
Expected: 无输出

- [ ] **Step 7: 各屏无用 import 收尾清理**

Run: `./gradlew :app:assembleDebug 2>&1 | grep -i "unused import"`
Expected: 逐条清理警告涉及的 import（预期集中在：`SearchScreen` 的 `animateFloatAsState`、`GameSetupScreen` 的 `animateFloatAsState`、`GameQuestionScreen` 的 `animateFloatAsState`/`SolidColor`、`DetailScreen` 的 `focusable`、`SettingsScreen` 的 `border`——以编译器实际输出为准，删后重编译确认警告消失且构建成功）。
（Kotlin 未用 import 只是警告不拦截构建，此步为代码整洁，不允许顺手改动任何行为代码。）

- [ ] **Step 8: 提交**

```bash
git add -A app/src/main/java/com/rakor/tangshi/ui/components/Shared.kt app/src/test/java/com/rakor/tangshi/ui/components/
git commit -m "refactor: 删除手写焦点原语 tvFocusable/FocusCard/FocusableCard/FocusablePillButton 及 CardShadowTest"
```

---

### Task 14: 规格书更新

**Files:**
- Modify: `docs/superpowers/specs/2026-10-08-focus-style-design.md`
- Modify: `docs/superpowers/specs/2026-10-06-tangshi-tv-app-design.md`
- Modify: `docs/superpowers/specs/2026-10-11-compose-tv-migration-design.md`（本计划保存时已带三处修正，随本任务一并提交）

- [ ] **Step 1: 2026-10-08 焦点规格标记废止**

在 `docs/superpowers/specs/2026-10-08-focus-style-design.md` 标题下（日期行之后）插入：

```markdown
> **已废止（2026-10-11）**：焦点视觉已迁移 tv-material 官方组件默认样式（scale + 描边，取色经 T → tv ColorScheme 的 `border` 槽映射为主橙），本文的 1.06 缩放 / 4dp 橙边 / 暖影**实现规范**不再适用。
> 仍然有效的**交互语义**（已迁移至新载体，见迁移规格）：禁用药丸不可聚焦（`focusProperties { canFocus = false }`）、分段组焦点不反色（`segmentPillColors/segmentPillBorder`）、焦点提示音首焦静音（各屏 `tone` lambda）。
> 新事实来源：[2026-10-11-compose-tv-migration-design.md](2026-10-11-compose-tv-migration-design.md)。
```

- [ ] **Step 2: 2026-10-06 总规格追加附注**

在 `docs/superpowers/specs/2026-10-06-tangshi-tv-app-design.md` 文件末尾追加：

```markdown

---

## 附注（2026-10-11）：Compose for TV 组件迁移

焦点视觉与组件来源改由 `androidx.tv:tv-material:1.1.0` 官方组件承担（官方默认焦点样式 + `T` 调色板映射）。**屏幕结构、交互流程、配色、字体、音效行为均不变**；仅焦点视觉实现（缩放/描边/投影的载体）与组件类来源变化。详见 [2026-10-11-compose-tv-migration-design.md](2026-10-11-compose-tv-migration-design.md)。
```

- [ ] **Step 3: 提交（含迁移规格的三处修正）**

```bash
git add docs/superpowers/specs/
git commit -m "docs: 焦点规格标记废止+风格D附注迁移说明——随迁移规格三处修正一并提交"
```

---

### Task 15: 全量验收

**Files:** 无代码改动（发现问题回对应任务修复）

- [ ] **Step 1: 全量单测 + 出包（debug + release 双门禁）**

Run: `./gradlew :app:testDebugUnitTest` && `./gradlew :app:assembleDebug` && `./gradlew :app:assembleRelease`
Expected: 均 `BUILD SUCCESSFUL`（release 是真实发布路径，lintVital 必过——Task 1 追加的 lint disable 是其前提）

- [ ] **Step 2: 实机行为全量走查（szj_tv 模拟器）**

- [ ] 焦点移动音效、首焦静音；OK 确认音无（本就没有）
- [ ] MENU 键回设置页（任意页面）
- [ ] 详情页 ChannelUp/ChannelDown 翻诗；◀▶ 逐句；▲▼ 滚动；OK 播放/停止
- [ ] 详情页首首/末首时禁用切诗芯片焦点跳过（`canFocus=false` 生效）
- [ ] 搜索页：浮层焦点陷阱、键盘激活边框、键位焦点、结果行焦点
- [ ] 诗库页：分段筛选选中/焦点态、网格焦点分页页码
- [ ] 游戏三屏：玩法卡选中态、选项反馈色、播放钮置灰仍可聚焦、结算按钮
- [ ] 设置页：开/关分段、清除入口、对话框白卡底
- [ ] 禁用态官方行为核对：**任何禁用控件按方向键不得落焦**

- [ ] **Step 3: 视觉验收（对照"官方默认焦点 + 暖色保留"两基准）**

模拟器截图逐屏对照；注意 Read 直接看 PNG 会串图，用 PIL 像素采样交叉校验（焦点描边是否主橙 3dp、背景是否暖米色、反色焦点是否主橙底白字）。重点核对「关键背景」第 5 条已知视觉差异清单，逐项判定接受/调整（调整只允许通过官方样式参数 `ButtonDefaults`/`CardDefaults` 完成，不得回退手写焦点代码）。

- [ ] **Step 4: 修复与提交**

发现问题 → 回对应任务修复、重跑本任务；全部通过 → 无代码提交，验收结论记录在对话中。

---

## 计划自检记录

- **规格覆盖**：阶段 1 版本表/主题切换/验收门 = Task 1-3；组件映射表 8 行 = Task 5-12 逐一对应；行为接线 6 条 = 各屏步骤内保留 + Task 15 走查；规格书处置 3 份 = Task 14；测试处置（删 CardShadowTest、保留 ThemeColorSchemeTest、新增 TvColorSchemeTest）= Task 2/13；执行顺序 = Task 5-12 排列；风险表 5 项 = Task 1 回退路径 + Task 15 走查项。
- **无占位符**：所有代码块为可直接落盘的完整代码；调用头替换均标注原文件行号与"内容体不动"的精确边界。
- **类型一致性**：`focusTone` / `normalPillColors` / `segmentPillColors` / `cardBorder` 等助手名在 Task 4 定义、Task 5-12 引用，拼写一致；`TangshiTvColorScheme` 在 Task 2 定义并被测试引用。
