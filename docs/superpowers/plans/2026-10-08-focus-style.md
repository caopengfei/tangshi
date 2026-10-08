# 焦点样式加强（按钮反色 + 卡片描边）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全局焦点态一眼可辨——胶囊按钮选中反色为橙渐变填充白字，卡片/小控件焦点描边统一加粗到主橙。

**Architecture:** 改动集中在 `ui/components/Shared.kt` 的按钮与卡片组件（焦点视觉唯一来源），随后机械清理 8 个屏幕文件的 `filled` 实参、统一 5 处自绘控件的焦点描边色宽。纯视觉改动，无数据流/逻辑变更。

**Tech Stack:** Kotlin + Jetpack Compose（Compose for TV）、Gradle 8.11.1、szj_tv 模拟器（adb）。

**规格:** `docs/superpowers/specs/2026-10-08-focus-style-design.md`（下称「规格」）

**关于 TDD:** 本计划是纯视觉样式改动，没有可断言的逻辑行为，现有单测（data/game/viewmodel 层）不触及这些组件——不写新单测。每个任务的验证阶梯是：`assembleDebug` 编译通过 → 最终任务在模拟器实机走查（规格 §5）。既有单测套件在 Task 4 回归一次，确保没有连带破坏。

**工作目录:** `D:\develop\src\rakor\tangshi`，模拟器设备 `emulator-5554`（szj_tv，若未启动：`D:/develop/androidsdk/emulator/emulator.exe -avd szj_tv`）。

**行号说明:** 计划中所有行号均为**修改前的原始行号**，仅作定位参考；同一文件连续多处修改时以内容匹配为准，不要按行号顺序盲改。

---

### Task 1: FocusablePillButton 反色重构 + 删 PillButton + 全部调用点迁移

组件与调用点必须在同一次编译中完成（删参数会导致调用点编译失败），故合为一个任务、一次提交。

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/components/Shared.kt`（删 `PillButton` 134-165 行；重写 `FocusablePillButton` 167-214 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/detail/DetailScreen.kt`（235/245/324/332/344 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/home/HomeScreen.kt`（301/304/305/306 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt`（218/226 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/library/LibraryScreen.kt`（191 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/settings/SettingsScreen.kt`（122/128/133/147/152 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/game/GameSetupScreen.kt`（156 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/game/GameQuestionScreen.kt`（152 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/game/GameResultScreen.kt`（105/110 行）

- [x] **Step 1: Shared.kt 加 import**

在 `app/src/main/java/com/rakor/tangshi/ui/components/Shared.kt` 的 import 区（`animateDpAsState` 旁）加：

```kotlin
import androidx.compose.animation.animateColorAsState
```

（注意：不是 `animation.core`——`animateColorAsState` 在 `androidx.compose.animation` 包下，写 `.core.` 会编译报 Unresolved reference。）

- [x] **Step 2: 删除无调用点的 `PillButton`**

删除 Shared.kt 第 132-165 行（KDoc `/** 渐变胶囊按钮… */` 到函数结尾 `}`），即整个 `fun PillButton(...)`。已确认全工程无调用点、无 import。

- [x] **Step 3: 重写 `FocusablePillButton`**

用下面代码整体替换 Shared.kt 第 167-214 行（原 KDoc + `fun FocusablePillButton`）：

```kotlin
/**
 * 可聚焦胶囊按钮：内部自管焦点视觉（放大/描边/反色填充动效）。
 * 统一描边款本体（规格 2026-10-08 焦点样式）：
 *  - 未选中：白底 2dp 浅橙描边棕字；highlight 档：ChipBg 底 3dp 橙描边橙字（常驻强调/分段选中指示）
 *  - 选中（B 反色）：橙渐变填充 + 白字白图标 + 1.06 放大 + 暖橙投影——焦点即最强信号
 * OK 触发 onClick（为 null 时仅可聚焦）；焦点变化回调 onFocusChange（提示音挂钩用）。
 */
@Composable
fun FocusablePillButton(
    text: String,
    icon: ImageVector? = null,
    highlight: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(if (focused) FocusScale else 1f, label = "pillFocusScale")
    val borderWidth by animateDpAsState(
        if (focused) 0.dp else if (highlight) 3.dp else 2.dp,
        label = "pillFocusBorder"
    )
    // 底色在静态底与橙渐变间连续过渡：两端同色即纯色，两端异色即渐变（B 反色）
    val restBg = if (highlight) T.ChipBg else T.Card
    val bgStart by animateColorAsState(if (focused) T.OrangeLight else restBg, label = "pillBgStart")
    val bgEnd by animateColorAsState(if (focused) T.Orange else restBg, label = "pillBgEnd")
    val textColor by animateColorAsState(
        if (focused) Color.White else if (highlight) T.Orange else T.TextBody,
        label = "pillTextColor"
    )
    // 图标色必须与背景/文字同帧动画：硬切会在过渡期出现白底白图标/橙底橙图标的瞬时隐没（c1e2863）
    val iconTint by animateColorAsState(if (focused) Color.White else T.Orange, label = "pillIconTint")
    Row(
        modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .scale(scale)
            // 禁用：半透明灰显，不可聚焦（焦点遍历跳过）、不响应点击
            .then(if (enabled) Modifier else Modifier.alpha(0.45f).focusProperties { canFocus = false })
            .then(
                if (focused) Modifier.shadow(14.dp, shape, ambientColor = CardShadow, spotColor = CardShadow)
                else Modifier
            )
            .background(Brush.horizontalGradient(listOf(bgStart, bgEnd)), shape)
            .border(borderWidth, if (highlight) T.Orange else T.OrangeSoft, shape)
            .then(if (enabled) Modifier.tvFocusable(onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = textColor)
    }
}
```

要点（对应规格 §1）：`filled` 参数已删除；焦点描边收到 0dp（填充分量本身就是信号）；图标未选中恒为橙（与原描边款一致），聚焦转白。

- [x] **Step 4: 调用点迁移（8 个文件，逐条精确替换）**

`DetailScreen.kt`：

```kotlin
// 235 行
"◀", filled = false,            →  "◀",
// 245 行
"▶", filled = false,            →  "▶",
// 324 行（主行动按钮 → highlight）
"朗读", Icons.Filled.PlayArrow, filled = true,   →  "朗读", Icons.Filled.PlayArrow, highlight = true,
// 332 行（连读开关选中指示 → highlight）
filled = shape == PoemPlayer.Shape.LOOP,   →  highlight = shape == PoemPlayer.Shape.LOOP,
// 344 行
"我会背了", Icons.Filled.ThumbUp, filled = false,   →  "我会背了", Icons.Filled.ThumbUp,
```

`HomeScreen.kt`：

```kotlin
// 301 行
filled = false, highlight = true,     →  highlight = true,
// 304 行
FocusablePillButton("全部唐诗", filled = false, onClick = onLibrary, onFocusChange = tone)
  → FocusablePillButton("全部唐诗", onClick = onLibrary, onFocusChange = tone)
// 305 行
FocusablePillButton("背诗小游戏", Icons.Filled.SportsEsports, filled = false, onClick = onGames, onFocusChange = tone)
  → FocusablePillButton("背诗小游戏", Icons.Filled.SportsEsports, onClick = onGames, onFocusChange = tone)
// 306 行
FocusablePillButton("家长设置", Icons.Filled.Settings, filled = false, onClick = onSettings, onFocusChange = tone)
  → FocusablePillButton("家长设置", Icons.Filled.Settings, onClick = onSettings, onFocusChange = tone)
```

`SearchScreen.kt`：删除 218 行与 226 行的 `filled = false,` 两整行。

`LibraryScreen.kt`：

```kotlin
// 191 行（过滤分段选中指示）
filled = filter == f,    →  highlight = filter == f,
```

`SettingsScreen.kt`：

```kotlin
// 122 行
"慢 0.9×", filled = s.speed == 0.9f,    →  "慢 0.9×", highlight = s.speed == 0.9f,
// 128 行
"正常 1.0×", filled = s.speed == 1.0f,  →  "正常 1.0×", highlight = s.speed == 1.0f,
// 133 行
"快 1.15×", filled = s.speed == 1.15f,  →  "快 1.15×", highlight = s.speed == 1.15f,
// 147 行
"开" + if (s.keyTone) " ✓" else "", filled = s.keyTone,
  → "开" + if (s.keyTone) " ✓" else "", highlight = s.keyTone,
// 152 行
"关" + if (!s.keyTone) " ✓" else "", filled = !s.keyTone,
  → "关" + if (!s.keyTone) " ✓" else "", highlight = !s.keyTone,
```

`GameSetupScreen.kt`：

```kotlin
// 156 行（题数范围选中指示）
filled = scope == value,   →  highlight = scope == value,
```

`GameQuestionScreen.kt`：152 行删除 `filled = false,`（保留 `"返回",`）。

`GameResultScreen.kt`：

```kotlin
// 105 行
"再来一轮", Icons.Filled.PlayArrow, filled = true,   →  "再来一轮", Icons.Filled.PlayArrow, highlight = true,
// 110 行
"返回首页", Icons.Filled.Home, filled = false,       →  "返回首页", Icons.Filled.Home,
```

- [x] **Step 5: 全局确认无残留引用**

Run: `grep -rn "filled =" app/src/main/java --include=*.kt`
Expected: 无输出（所有 `filled =` 实参已清理；`Icons.Filled.XXX` 是 `Filled.` 不匹配 `filled =`，大小写敏感不会误报）。

- [x] **Step 6: 编译验证**

Run: `cd D:/develop/src/rakor/tangshi && ./gradlew assembleDebug -q`
Expected: `BUILD SUCCESSFUL`（无编译错误；若报 `filled` 未声明，回查 Step 4 是否有遗漏行）。

- [x] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: 胶囊按钮统一描边款+B反色焦点——删filled参数/PillButton，调用点迁移highlight"
```

---

### Task 2: 卡片焦点描边加强（FocusCard / FocusableCard）

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/components/Shared.kt`（`FocusCard` 71-92 行、`FocusableCard` 99-130 行）

- [x] **Step 1: FocusCard 焦点描边 4dp 主橙、阴影 18dp**

将 `FocusCard` 的 modifier 链（Shared.kt 78-89 行）改为：

```kotlin
        modifier = modifier
            .shadow(
                if (focused) 18.dp else 6.dp,
                RoundedCornerShape(corner.dp),
                ambientColor = CardShadow,
                spotColor = CardShadow
            )
            .background(T.Card, RoundedCornerShape(corner.dp))
            .then(
                if (focused) Modifier.border(4.dp, T.Orange, RoundedCornerShape(corner.dp))
                else Modifier
            ),
```

（变化点：阴影 `14.dp → 18.dp`；描边 `3.dp T.OrangeSoft → 4.dp T.Orange`。）

- [x] **Step 2: FocusableCard 同步加强**

将 `FocusableCard` 的两处焦点值（Shared.kt 111、119、126 行附近）改为：

```kotlin
    val borderWidth by animateDpAsState(if (focused) 4.dp else 0.dp, label = "cardFocusBorder")
```

```kotlin
            .shadow(
                if (focused) 18.dp else 6.dp,
                shape,
                ambientColor = CardShadow,
                spotColor = CardShadow
            )
            .background(T.Card, shape)
            .border(borderWidth, T.Orange, shape)
```

（变化点：描边宽 `3.dp → 4.dp`、颜色 `T.OrangeSoft → T.Orange`、阴影 `14.dp → 18.dp`。）

- [x] **Step 3: 编译验证**

Run: `cd D:/develop/src/rakor/tangshi && ./gradlew assembleDebug -q`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/components/Shared.kt
git commit -m "feat: 卡片焦点描边4dp主橙+阴影18dp加强"
```

---

### Task 3: 自绘小控件焦点描边统一

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt`（448、473 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/game/GameQuestionScreen.kt`（451、489-490 行）
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/settings/SettingsScreen.kt`（256 行）

- [x] **Step 1: SearchScreen 键盘键与浮层项 2dp → 3dp 主橙**

`KeyBox`（448 行）：

```kotlin
            .border(if (focused) 3.dp else 0.dp, T.Orange, RoundedCornerShape(KeyCorner))
```

`PickerItem`（473 行）：

```kotlin
            .border(if (focused) 3.dp else 0.dp, T.Orange, RoundedCornerShape(12.dp))
```

- [x] **Step 2: GameQuestionScreen 语音圆钮焦点白边（橙底上橙描边不可辨）**

451 行：

```kotlin
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else T.OrangeSoft, CircleShape)
```

- [x] **Step 3: GameQuestionScreen 选项块焦点主橙（作答反馈优先）**

489-490 行改为：

```kotlin
            .border(
                if (feedback == Feedback.WRONG || focused) 3.dp else 0.dp,
                if (feedback == Feedback.WRONG) T.Wrong else T.Orange,
                shape
            )
```

（WRONG 红描边优先于焦点橙描边；CORRECT 底色逻辑 487 行不动。）

- [x] **Step 4: SettingsScreen "清除 →" 小按钮焦点 3dp 主橙**

256 行：

```kotlin
            .border(if (focused) 3.dp else 0.dp, T.Orange, shape)
```

- [x] **Step 5: 编译验证**

Run: `cd D:/develop/src/rakor/tangshi && ./gradlew assembleDebug -q`
Expected: `BUILD SUCCESSFUL`

- [x] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: 自绘小控件焦点描边统一——键位/浮层3dp主橙、语音钮白边、选项块主橙反馈优先"
```

---

### Task 4: 回归测试 + 模拟器实机走查（规格 §5）

**Files:**
- 无代码改动（验证任务；发现问题回到对应任务修复后再执行本任务）

- [x] **Step 1: 既有单测回归**

Run: `cd D:/develop/src/rakor/tangshi && ./gradlew testDebugUnitTest -q`
Expected: `BUILD SUCCESSFUL`，无用例失败（与焦点无关的 data/game 层回归，确认无连带破坏）。

- [x] **Step 2: 确认模拟器在线并安装**

```bash
D:/develop/androidsdk/platform-tools/adb.exe devices        # 应有 emulator-5554 device；没有则先启动 szj_tv
cd D:/develop/src/rakor/tangshi && ./gradlew assembleDebug -q
D:/develop/androidsdk/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`

- [x] **Step 3: 启动 app 并逐屏截图（未选中基线 + 焦点态对比）**

准备存档目录：`mkdir -p .superpowers/brainstorm/20261008-focus-100000/verify`

统一截图命令（每屏执行）：`D:/develop/androidsdk/platform-tools/adb.exe exec-out screencap -p > .superpowers/brainstorm/20261008-focus-100000/verify/<名称>.png`
统一按键命令：`D:/develop/androidsdk/platform-tools/adb.exe shell input keyevent <KEYCODE>`（DPAD：UP=19 DOWN=20 LEFT=21 RIGHT=22，OK=66，BACK=4，MENU=82）

走查清单（每屏先截未选中基线，再移动焦点截选中态）：

1. **首页** `adb shell monkey -p com.rakor.tangshi -c android.intent.category.LEANBACK_LAUNCHER 1`
   - `home-base`：初始全景
   - 焦点移到底部"全部唐诗"→ `home-focus-pill`：应为橙渐变填充白字（B 反色），且"搜索"（highlight 档）未选中时是浅橙底橙字
   - 焦点在主卡 → `home-focus-card`：4dp 主橙描边
2. **详情**（OK 进入当前诗）→ `detail-controls`：焦点在"朗读"（highlight 常驻）与"◀/▶"（普通描边）对比；右上创作背景卡聚焦 `detail-focus-card`
3. **背诗小游戏**（返回首页 → 焦点到"背诗小游戏"）→ `game-setup`：题数范围选中项 highlight 橙字、焦点项反色
4. **搜索** → `search-keys`：键盘键聚焦 3dp 主橙描边
5. **家长设置** → `settings-focus`：语速选中项 highlight、焦点项反色；"清除 →"聚焦 3dp 橙边
6. 返回首页按 BACK 退出到桌面

- [x] **Step 4: 逐张查看截图并核对验收标准**

对每张截图确认（对照规格 §1/§2 表格）：
- 选中按钮 = 橙渐变填充 + 白字 + 明显放大 + 暖影；未选中 = 白底描边（或 highlight 浅橙底橙字）
- 卡片选中 = 4dp 主橙描边 + 更深阴影
- 小控件选中 = 3dp 主橙描边；语音圆钮 = 3dp 白描边
- 禁用按钮（详情 ◀ 到第一首时）= 半透明且焦点不落
- 键位提示音/动画无异常（焦点切换平滑，无闪烁跳变）

任一项不达标 → 记录为缺陷，回对应任务修复，重新构建安装后复查本步骤。

- [x] **Step 5: 汇报结果**

在最终回复中给出：构建/测试结论 + 关键截图路径 + 走查通过项清单（或缺陷与修复说明）。此任务无代码改动则不提交；有修复则随修复任务提交。

---

## Self-Review 记录

- **规格覆盖:** §1（按钮反色/删 filled/条件 filled→highlight/删 PillButton/主卡 CTA 不在范围）→ Task 1；§2（卡片 4dp 主橙、键位/浮层/清除钮 3dp 主橙、语音钮白边）→ Task 2/3；§3（实现落点）→ 对应任务 Files；§4（边界：highlight+焦点以焦点为准、禁用不变）→ Task 1 代码（焦点时 borderWidth=0、禁用分支原样保留）；§5（验证）→ Task 4。
- **占位符扫描:** 无 TBD/TODO；所有代码步骤给出完整可粘贴代码或精确行级替换。
- **类型一致性:** `FocusablePillButton(text, icon, highlight, enabled, onClick, onFocusChange, modifier)` 在 Task 1 定义，全部调用点使用命名参数 `highlight =`，与 Task 1 Step 4 一致；组件函数名 `FocusCard`/`FocusableCard`/`KeyBox`/`PickerItem`/`OptionBlock`/`WipeButton` 与现有代码一致。
