# 启动白屏优化（16.4s → 0.85s）

日期：2026-10-10
状态：已上线（盒子实测通过）

> 后续更新（同日）：release 签名已从 debug 切换为正式 keystore（`D:\develop\key\rakor.jks`，
> 凭据见根目录 `keystore.properties`，不入库）。签名变更后旧包不能覆盖安装，
> 盒子需卸载重装（学习进度随之清零）；`apksigner verify --print-certs` 确认
> `DN: CN=cao pengfei` 生效。

## 背景与问题

小米盒子（MiBOX4，Android 6.0.1 / API 23）上启动 app 白屏极久，用户反馈"启动好慢，白屏很久"。

实测基线（`adb shell am start -W` + `Displayed` 日志）：

| 设备 | Displayed |
|---|---|
| 小米盒子（优化前，debug 包） | **+16369ms** |
| 模拟器（API 37，debug 包） | +2550ms |

## 诊断结论

1. **dex2oat AOT 编译失败（环境，无法修复）**
   盒子安装后触发 `dex2oat` 报 `Failed to create oat file: /data/dalvik-cache/arm/... Permission denied`（ROM 权限问题），
   app 被迫以**解释模式**运行，Compose/Kotlin 类加载开销被急剧放大。
   无 root；`pm compile` 是 Android 7+ 命令（盒子 6.0.1 没有），环境层无解 → 只能减少解释执行的工作量。

2. **主线程同步解析 1.4MB 诗库 JSON（app 代码，可修）**
   `HomeViewModel` 构造路径（首帧必经）三处同步调 `poems.loadAll()`：
   `val total = poems.loadAll().size`、`current` 上游 `flowOf(poems.loadAll())` 及其初始值。
   325 首 / 1.4MB 的 `kotlinx.serialization` 解析全部落在主线程。

3. **debug 包体积 55MB**，解释执行下 dex 越大首启越慢。

## 方案与改动

| 文件 | 改动 |
|---|---|
| `di/AppGraph.kt` | 新增 `warmUp()`：`appScope.launch { poems.loadAll() }`，IO 线程预热 |
| `TangshiApp.kt` | `onCreate` 调 `AppGraph.instance.warmUp()` |
| `ui/screens/home/HomeScreen.kt` | `HomeViewModel` 异步化：新增 `poemsList`/`poemsReady` StateFlow（`flowOn(Dispatchers.IO)`），`total` 改为 `StateFlow<Int>`，`current` 上游改用 `poemsList`——主线程构造路径不再触碰 JSON；首页诗卡在解析中留白（`poemsReady` 区分"加载中"与"真的空诗库"，不闪"诗库为空"） |
| `app/build.gradle.kts` | 正式 release 签名：读取根目录 `keystore.properties`（gitignore 不入库，缺失时回退 debug 签名）配置 `signingConfigs.release`；`isMinifyEnabled = true`（原有）产出 R8 压缩包 |

音频播放语义（连读开关/OK 播放停止）等其余改动见同日交互改动，不在本文范围。

## 实测结果

| 场景 | Displayed |
|---|---|
| 盒子优化前（debug） | 16369ms |
| 盒子优化后（release，3 次冷启动） | **849 / 854 / 897ms** |
| 模拟器优化后（release 冷启动） | 972ms |

- **约 18 倍提速**；release APK 体积 55MB → 36.8MB（R8）。
- 三次冷启动均无 `FATAL EXCEPTION`；截图验证首页统计（`3 / 325` 异步 total）、诗卡、详情页渲染全部正常。
- 构建：`gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest` 全绿。

## 调试过程中的环境坑（复盘备忘）

1. **盒子前台会被 MIUI TV 系统广告/屏保覆盖**（`SurfaceView` overlay + `ScreenSaverActivity`），
   `mResumedActivity`/`mCurrentFocus` 都是本 app，但 `screencap` 截到的是广告层；
   `uiautomator dump` 在该 ROM 报 `null root node`（UiTestAutomationBridge 不可用）。
   → 验证 UI 需换模拟器；验证启动时间用 `Displayed` 日志（不受 overlay 影响）。
2. **adb server 进程会在两次命令之间被会话回收**，导致 `device not found`/按键静默失败；
   需以 `adb -L tcp:5037 server nodaemon` 挂后台任务托管，或每条命令先 `adb connect`。
3. **tvLauncher 抢前台会冻结 app**（`ActivityManager: freezing ...`），画面停在冻结前一帧，
   造成"按键无效/状态不更新"假象——诊断前先确认 `topResumedActivity`。
4. 老盒子上 `am start -W` 首次安装后启动会包含安装期 dex2oat 尝试失败开销，
   对比启动时间应 force-stop 后冷启动多次取值。
