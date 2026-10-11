# Compose for TV 迁移设计（引入 androidx.tv:tv-material 重构 UI）

日期：2026-10-11
状态：已确认（用户批准）

## 背景与动机

项目当前是"纯手写 TV 焦点体系"：`ui/components/Shared.kt` 里的 `tvFocusable` / `FocusCard` / `FocusableCard` / `FocusablePillButton` 承担全部焦点视觉与点击语义，8 个屏幕各自用 `FocusRequester` + `withFrameNanos` + `onFocusChanged` 接线，全项目零 `androidx.tv` 依赖。

目标是引入 Compose for TV 官方组件库最新稳定版 `androidx.tv:tv-material:1.1.0`（2026-05 发布），用官方组件替换手写原语，官方默认焦点样式承接焦点视觉，降低自维护成本并与官方 TV 应用观感对齐。

## 用户决策（已确认）

1. **全面采用官方组件**：屏幕直接使用 tv-material 组件，删除自定义焦点原语（否决"保留组件 API 只换内部实现"的底层替换方案）。
2. **焦点视觉改用官方默认样式**：废止 2026-10-08 规格书的 1.06 缩放 / 4dp 橙边 / 暖影规范及其守护测试，采用 tv-material 默认 scale + 描边。
3. **工具链最小够用升级**：只升到满足 tv-material 1.1.0 的组合，不追最新。
4. **保留现有暖色调**：`T` 调色板、米白底、橙色主色、KaiTi 字体、风格D 不变，映射进 tv-material 主题。
5. **迁移路线方案 B（两阶段）**：工具链先行独立验收，再做组件替换；组件替换按屏拆任务。

## 目标与范围

**范围内**：

- Compose BOM / Kotlin / KSP 最小升级（见阶段 1 版本表）
- 引入 `androidx.tv:tv-material:1.1.0`
- 主题层切换：`TangshiTheme` 改用 tv-material `MaterialTheme`，`T` 调色板映射进 tv `ColorScheme`
- 8 个屏幕的组件替换（删除 Shared.kt 三大自定义原语）
- 焦点音效、MENU 键、Channel 翻页、焦点陷阱等行为在官方组件上重新接线
- 规格书与回归测试更新

**范围外（明确不动）**：

- 导航结构与路由（`AppNavHost` / `Routes`）
- 各屏幕的信息架构与布局
- ExoPlayer 音频链路、TTS 预生成链路
- 字体（KaiTi / LXGW WenKai）、`T` 调色板本身
- AGP 8.7.3、Gradle 8.11.1、compileSdk 35、targetSdk 35、minSdk 23
- 图标库 `material-icons-extended`
- material3 `AlertDialog` / `TextButton`（tv-material 无对话框组件，确认弹窗继续用 material3）

## 阶段 1：工具链与主题（独立提交，可整体回退）

### 版本表（全部经 Maven 仓库实证核验）

| 项 | 现状 | 目标 | 理由 |
|---|---|---|---|
| composeBom | 2024.12.01 | **2026.02.00** | 恰好 Compose 1.10.3（tv-material 1.1.0 的编译基线）+ material3 1.4.0，最小够用 |
| kotlin（android / compose / serialization 三插件） | 2.0.21 | **2.1.20** | tv-material 1.1.0 声明 kotlin-stdlib 2.1.20；三插件版本必须一致 |
| ksp | 2.0.21-1.0.27 | **2.1.20-1.0.31** | 与 Kotlin 严格对齐（已验证存在） |
| 新依赖 | — | **androidx.tv:tv-material:1.1.0** | 最新 stable |
| AGP / Gradle / compileSdk / minSdk | 8.7.3 / 8.11.1 / 35 / 23 | 全部不动 | tv-material 与 Compose 1.10.3 的 AAR 元数据：minCompileSdk=35、minAGP=8.6.0、minSdk=23，全部满足；Gradle 8.11.1 在 Kotlin 2.1.20 支持区间内 |

其余依赖（navigation 2.8.5、lifecycle 2.8.7、activity 1.9.3、media3、room 等）不动——旧库 + 新 Compose runtime 向前兼容。

### 主题切换

- `TangshiTheme` 内的 `androidx.compose.material3.MaterialTheme` 换成 `androidx.tv.material3.MaterialTheme`（tv-material 自带的同名主题对象），`colorScheme` 由 `T` 调色板构造 tv `ColorScheme`，`T` 仍是唯一色彩来源；Typography 保留 KaiTi `titleLarge`。
- material3 依赖保留，仅服务 `AlertDialog`/`TextButton`：在对话框外层嵌套提供 material3 `MaterialTheme`（同色映射）。两套主题的 CompositionLocal 类型不同（`androidx.tv.material3.*` vs `androidx.compose.material3.*`），互不冲突。
- 本阶段现有自定义组件原样保留——只换引擎不换零件。

### 阶段 1 验收门

`gradlew testDebugUnitTest` + `assembleDebug` 全绿，szj_tv 模拟器实机跑通（现有视觉与行为不变），才进入阶段 2。

### 已识别回退路径

若 compose compiler（Kotlin 2.1.20 内置）与 Compose runtime 1.10.3 存在 ABI 兼容问题（编译报错或运行时 `NoSuchMethodError`）：把 Kotlin 升到 2.2.x 对应 KSP 版本，BOM 不动；仍不行则 BOM 逐级上调。此决策点在阶段 1 内解决，不带入阶段 2。

## 阶段 2：组件终态与行为接线

### Shared.kt 处置

**删除**（被官方组件取代）：`tvFocusable`、`FocusCard`、`FocusableCard`、`FocusablePillButton`、`FocusScale(1.06f)`、`CardShadow`。

**保留**（app 特有复合组件，与焦点体系正交）：`StatusBadge`、`KeyHintBar`、`CreamBackground`、`PoemText`（Pinyin 逐字排版）。

### 组件映射（8 屏统一执行）

| 现有 | 官方替代 | 说明 |
|---|---|---|
| `FocusableCard`（自管理焦点卡） | `ClassicCard` / `Card` | 默认焦点 scale + 描边，样式走 `CardDefaults` |
| `FocusCard`（静态白底容器） | 非交互 `Surface` | 无焦点态 |
| `FocusablePillButton` normal 模式 | `Button` | 官方默认即胶囊 + 焦点效果 |
| `FocusablePillButton` highlight 模式（静置选中态） | `Button` + 自定义样式参数（`ButtonDefaults.colors/border`，rest 态即强调档） | 保持"未聚焦也高亮"的语义；不引入 `Chip` |
| `FocusablePillButton` segment 模式（焦点不反色） | `Button` + 自定义样式参数（focusedBorder=4dp，scale 用官方默认） | 分段观感靠样式参数收敛，不靠手写 |
| 焦点可点容器（键盘格、诗词行等） | 交互式 `Surface(onClick=…)` | 替代 `tvFocusable` |
| 确认对话框 | **保留 material3 `AlertDialog`** | tv-material 无对话框组件 |

### 行为接线（功能全部保持，只换挂载点）

- **焦点/按键音效**（现 31 处 `onFocusChange` 钩子）：收敛为共享扩展 `Modifier.focusTone()`，挂在官方组件的 `modifier` 参数上。实现时优先验证 tv-material `interactionSource` 是否发 `FocusInteraction`，不行则用外部 `onFocusChanged`（两条路都在官方 API 内）。应用目前只有焦点移动音（`KeyTonePlayer.focusMove()`，`confirm()` 无调用点），不新增确认音。
- **MENU 键回设置页**（AppNavHost 根 Box）、**ChannelUp/ChannelDown 翻页**（DetailScreen 容器级 `onPreviewKeyEvent`）、**Search 焦点陷阱**（`focusProperties.exit`）、**初始焦点**（`withFrameNanos` + `FocusRequester`）：与官方组件无耦合，原样保留。
- **禁用态陷阱**：tv-material 明确 disabled Surface **仍可聚焦**（官方设计），而现规格要求禁用药丸不可聚焦——禁用处继续包 `focusProperties { canFocus = false }`，列为逐屏检查项。
- **焦点驱动分页**（LibraryScreen）、**长文滚动**（DetailScreen）：挂官方组件的 `modifier`，逻辑不动。
- **glow**：本迁移全部组件使用官方默认样式，而官方默认 glow = Glow.None（未启用发光）；API<28 降级规则因此不涉及本应用。

### 执行顺序

按屏拆任务：Shared 原语替换（含主题收尾）→ home → library → detail → search → game（setup/question/result）→ settings → 收尾（删残余、规格书、测试重写）。每屏完成后编译 + 单测。

## 规格书处置

- `2026-10-08-focus-style-design.md`：被本设计取代。改写为记录"焦点视觉已切换 tv-material 官方默认（scale + 描边），旧的 1.06 缩放 / 4dp 橙边 / 暖影规范废止"，保留仍有效的交互语义（禁用药丸不可聚焦、焦点音效、分段模式不反色等语义迁移到新载体）。
- `2026-10-06-tangshi-tv-app-design.md`（风格D 总规格）：不动正文，追加迁移附注指向本设计——屏幕结构、交互流程、配色、字体不变，仅焦点视觉与组件来源改由官方组件承担。
- 本设计为阶段 1/2 的唯一事实来源。

## 测试处置

| 测试 | 处置 |
|---|---|
| `CardShadowTest` | **删除**——`CardShadow` 常量随旧组件废止，官方默认样式（glow = None，未启用发光）由官方测试守护 |
| `ThemeColorSchemeTest` | **保留**——对话框仍走 m3 主题（`TangshiColorScheme` 继续在用），原断言继续有效 |
| 新增 `TvColorSchemeTest` | 守护"T 调色板 → tv `ColorScheme` 映射"，尤其 `border` 槽位（官方焦点描边取色处） |
| `LibraryFilterTest`、`HomeViewModelTest`、`PoemRepositoryTest`、`SettingsRepositoryTest`、`AudioAssetsTest` | 保留；仅当编译引用被删组件时做机械适配 |
| 新增 | 每屏迁移不新增单测（组件替换无逻辑变化）；阶段收尾补一条 tv `ColorScheme` 关键色槽位断言 |

## 验证策略

1. **编译 + 单测**：`gradlew testDebugUnitTest` + `assembleDebug`，全绿才算完成。
2. **实机走查**（szj_tv 模拟器；阶段结束全量过一遍，阶段 2 每屏至少过本屏）：焦点移动/确认音效、MENU 回设置、ChannelUp/Down 翻页、Search 焦点陷阱、禁用药丸不可聚焦、游戏三屏流程。
3. **视觉验收**：模拟器截图对照两条基准——"官方默认焦点"与"暖色保留"；注意 Read 直接看 PNG 会串图，用 PIL 像素采样交叉校验。
4. **回退策略**：阶段 1 独立提交可整体回退；阶段 2 按屏提交，单屏出问题单屏回退。
5. **装机**：小米盒子 adb 连不上（已知），验收只用 szj_tv 模拟器；出包只出 release（debug 签名冲突，已知）。

## 风险与对策

| 风险 | 对策 |
|---|---|
| compose compiler 2.1.20 与 runtime 1.10.3 ABI 不兼容 | 阶段 1 验收门拦截；回退路径见上（Kotlin → 2.2.x） |
| material3 1.3.x → 1.4.0 行为差异影响 AlertDialog | 阶段 1 验收门含对话框走查；仅用 Text/TextButton/AlertDialog/darkColorScheme，均为稳定 API |
| tv-material disabled 仍可聚焦导致禁用态误触 | 逐屏检查项 + 实机走查"禁用药丸"项 |
| 官方默认焦点样式在暖色底上对比度不足 | 阶段 2 每屏视觉验收发现即用 `ButtonDefaults`/`CardDefaults` 样式参数定制（仍属官方样式系统），不回退手写 |
| BOM 跳两个大版本引发 Robolectric/资源测试回归 | 阶段 1 验收门全量单测；失败则逐项排查，不带病进入阶段 2 |
