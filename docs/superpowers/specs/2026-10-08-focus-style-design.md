# 焦点样式加强设计（按钮反色 + 卡片描边加强）

日期：2026-10-08
状态：已确认（用户批准）

> **已废止（2026-10-11）**：焦点视觉已迁移 tv-material 官方组件默认样式（scale + 描边，取色经 T → tv ColorScheme 的 `border` 槽映射为主橙），本文的 1.06 缩放 / 4dp 橙边 / 暖影**实现规范**不再适用。
> 仍然有效的**交互语义**（已迁移至新载体，见迁移规格）：禁用药丸不可聚焦（`focusProperties { canFocus = false }`）、分段组焦点不反色（`segmentPillColors/segmentPillBorder`）、焦点提示音首焦静音（各屏 `tone` lambda）。
> 新事实来源：[2026-10-11-compose-tv-migration-design.md](2026-10-11-compose-tv-migration-design.md)。

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
- **分段选择组例外（2026-10-08 用户实机反馈追加）**：语速、键音、诗库过滤、题数范围这类分段组成员**焦点不反色填充**——焦点保持本体底色（未选中白底 / 选中浅橙底）+ **4dp `Orange` 描边** + 1.06 放大，纯描边强调；橙底白字反色仅用于普通行动按钮（首页入口、朗读、再来一轮等）。组件以 `segment = true` 标记分段组成员。
- 无调用点的非可聚焦 `PillButton`（`Shared.kt`）随之删除（YAGNI，其 filled 逻辑已不存在）。
- **首页四个底部入口取消常驻强调（2026-10-09 用户反馈追加）**：搜索原按 5.1 规格传 `highlight = true` 橙描边突出，与另三个入口视觉不一致。四者改为平级同档（普通描边款），强调职责回归"焦点态才反色"。`highlight` 语义保留，仅剩主行动按钮（朗读、再来一轮）与选中态指示两类用法。
- 首页主卡内嵌"继续诵读"CTA 是卡片装饰视觉（整卡即焦点，非独立按钮），不在本规范范围，保持现状。
- 焦点切换动画：scale 沿用现有 `animateFloatAsState`；背景/文字/**图标**色用 `animateColorAsState` 平滑过渡（反色是大幅色彩翻转，硬切会显得跳；图标不参与会在过渡期出现白底白图标/橙底橙图标的瞬时隐没），同帧开始即可，不额外指定时长。

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
- `FocusablePillButton` 禁用不参与焦点遍历、样式不因本设计改变；游戏语音圆钮是既有「禁用仍可聚焦」设计（重听耗尽置灰但焦点可达），本次仅修复其焦点描边可见性（禁用米白底用主橙边）。
- 长按/pressed 态不存在（纯遥控交互），无需处理。

## §5 验证

1. `./gradlew assembleDebug` 构建通过。
2. 安装到 szj_tv 模拟器，全流程冒烟：首页 → 诗列表 → 详情（播放控制）→ 背诗游戏（设置/答题/结果）→ 搜索 → 家长设置。
3. 逐屏确认：焦点移动时选中态一眼可辨（橙填充 vs 白底描边）；卡片焦点描边明显；禁用、highlight、焦点三种状态互不混淆。
4. 截图对比存档。

## §6 遗留/后续（已接受，2026-10-08 终审落档）

以下项在实施/走查中识别并**明确接受**为后续工作，非静默遗漏：

1. **阴影 elevation 瞬切未动画化**：`FocusCard`/`FocusableCard`/`FocusablePillButton`/游戏自绘钮的 shadow 在聚焦时一帧切换（6↔18 / 0↔14dp），而 scale/描边/底色均有 `animate*AsState` 过渡。原计划统一推迟到「焦点语言第二期」与小控件描边动画一并处理。
2. **焦点尺寸字面量未提常量**：4dp（卡片）/3dp（小控件）/14dp/18dp 散布多处，`Scale`/`CardShadow` 已有常量先例。YAGNI——待第 6 处自绘控件出现时再抽。
3. **动画平滑性未录像验证**：静态截图无法证明反色过渡无跳变，需屏录复核。
4. **数据集限制**：种子集仅 10 首、最长 4 行，「长诗焦点卡与诗文不相撞」只在节选诗上验证过。
5. **模拟器渲染冻结 ×2（环境级）**：走查期间 szj_tv 出现两次出帧停止（输入/焦点正常、画面停留旧帧），重启整机恢复，logcat 无 app 报错；疑 EGL 图形栈问题，与本规格无关，换 `-gpu` 模式或真机复核。
6. **语速行滚动端余量**：`horizontalScroll + 4dp padding` 使端点按钮投影不被齐根裁断，但放大态（1.06）仍会部分超出 4dp 余量——已接受为滚动容器的固有表现。
