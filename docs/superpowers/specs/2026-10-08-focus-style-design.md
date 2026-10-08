# 焦点样式加强设计（按钮反色 + 卡片描边加强）

日期：2026-10-08
状态：已确认（用户批准）

## 背景与问题

TV 端全遥控交互，焦点态是唯一的位置指示。当前实现（`ui/components/Shared.kt`）焦点视觉只有 1.06 倍放大 + 描边变化：

- 描边款按钮未选中 2dp `OrangeSoft`、选中 3dp `OrangeSoft`——只差 1dp，几乎不可辨；
- `highlight` 按钮选中前后描边颜色和宽度完全相同，焦点仅剩微放大；
- 填充款按钮选中只多一圈白边；
- 卡片描边 3dp `OrangeSoft`，与白底对比弱。

用户在模拟器实机确认：首页底部入口排、详情页播放控制、游戏选项/卡片、全局焦点均偏弱，选中与未选中区分不明显。

## 决策（用户已确认）

1. **去掉填充款按钮**，全局只保留描边款本体。
2. **描边款焦点采用方案 B（反色填充）**：选中态直接翻转为橙色渐变填充 + 白字。
3. 卡片与小控件同步加强（描边 + 阴影），不做反色（卡片有正文，反色破坏可读性）。

可视化对比存档：`.superpowers/brainstorm/20261008-focus-100000/content/focus-approach.html`。

## §1 按钮统一规范

适用于 `PillButton` 与 `FocusablePillButton`，**删除 `filled` 参数**。

| 状态 | 样式 |
|---|---|
| 未选中（普通） | 白底（`T.Card`）+ 2dp `OrangeSoft` 描边 + `TextBody` 字 |
| 未选中（highlight） | `ChipBg` 底 + 3dp `Orange` 描边 + `Orange` 字（保留现强调档） |
| 选中（两种档位相同） | 橙渐变填充（`OrangeLight → Orange` 横向）+ 白字 + 白图标 + scale 1.06 + 暖橙投影 |
| 禁用 | 现状不变：alpha 0.45 + 不可聚焦 |

- 填充色由"常驻样式"降级为"焦点指示"，是全局最强的选中信号。
- 原 `filled = true` 的主行动按钮（详情"朗读"、游戏"再来一轮"等）改为 `highlight = true`，保证未选中时仍有常驻强调，焦点态同样反色。
- **条件式 `filled`**（分段/开关组的选中指示：语速、键音、诗库过滤、题数范围、连读开关）映射为 `highlight = <同一条件>`——选中项以 highlight 档常驻强调，未选中项为普通描边款。
- 无调用点的非可聚焦 `PillButton`（`Shared.kt`）随之删除（YAGNI，其 filled 逻辑已不存在）。
- 首页主卡内嵌"继续诵读"CTA 是卡片装饰视觉（整卡即焦点，非独立按钮），不在本规范范围，保持现状。
- 焦点切换动画：scale 沿用现有 `animateFloatAsState`；背景/文字色用 `animateColorAsState` 平滑过渡（反色是大幅色彩翻转，硬切会显得跳），两者同帧开始即可，不额外指定时长。

## §2 卡片与小控件焦点加强

| 组件 | 未选中 | 选中 |
|---|---|---|
| `FocusCard` / `FocusableCard`（诗卡、内容卡） | 现状不变（白底、无描边、6dp 阴影） | 描边 **4dp `Orange`**（原 3dp `OrangeSoft`）、阴影 18dp（原 14dp）、scale 1.06 |
| 游戏选项块（自绘） | 现状不变 | 焦点描边统一为 **3dp `Orange`**（原浅橙/偏细），scale 不变 |
| 搜索键盘键 / 选中浮层项 / 设置"清除"小按钮 | 现状不变 | 焦点描边统一为 **3dp `Orange`**（现 2dp），scale 不变 |
| 游戏语音圆钮（常驻橙渐变填充） | 现状不变 | 焦点描边 **3dp 白色**（橙底上橙描边不可辨；沿用填充视觉"焦点白边"语言） |

首页底部入口排 4 个按钮属于 §1 描边款，自动获得 B 反色，无需单独处理。

## §3 实现落点

- `ui/components/Shared.kt`
  - `PillButton` / `FocusablePillButton`：删 `filled` 分支，实现反色焦点逻辑；`highlight` 语义不变。
  - `FocusCard` / `FocusableCard`：焦点描边 4dp `Orange`、阴影 18dp。
- 调用点清理（`filled =` 实参）：`HomeScreen` / `DetailScreen` / `LibraryScreen` / `SettingsScreen` / `SearchScreen` / `GameSetupScreen` / `GameQuestionScreen` / `GameResultScreen`；原填充款主按钮补 `highlight = true`。
- 游戏选项块、键盘键的自绘焦点描边处按 §2 调色值。
- 不新增色值，复用 `T.Orange` / `T.OrangeLight` / `T.ChipBg`。

## §4 错误处理与边界

- `highlight` 与焦点同时存在时以焦点反色为准（highlight 只影响未选中态）。
- 禁用按钮不参与焦点遍历，样式不因本设计改变。
- 长按/pressed 态不存在（纯遥控交互），无需处理。

## §5 验证

1. `./gradlew assembleDebug` 构建通过。
2. 安装到 szj_tv 模拟器，全流程冒烟：首页 → 诗列表 → 详情（播放控制）→ 背诗游戏（设置/答题/结果）→ 搜索 → 家长设置。
3. 逐屏确认：焦点移动时选中态一眼可辨（橙填充 vs 白底描边）；卡片焦点描边明显；禁用、highlight、焦点三种状态互不混淆。
4. 截图对比存档。
