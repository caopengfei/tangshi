# Android 项目：GitHub Actions 自动构建与发版（可复用模板）

> 适用于 Kotlin/Gradle 的 Android（含 TV）项目：打 tag 自动出签名 APK 并发 GitHub Release，手动触发只构建不发版。
> 本文是从 tangshi 项目实际跑通的配置与踩坑总结，模板里把项目相关部分标成 `<尖括号>`，换项目时只改这些。

## 0. 一句话结论

`.github/workflows/release.yml` 里三件事做对就能用：**触发（tag / 手动）+ 签名（可选 Secret）+ 发布（Release 只在 tag 时执行）**。真正容易翻车的是四个环境细节：机器相关的 `gradle.properties`、`gradlew` 可执行位、`environment` 与 Secret 的作用域、以及"没配签名静默回退 debug 签名"这个不报错的坑。

## 1. 仓库侧设置（三个开关，缺一不可）

| 设置 | 位置 | 不做的后果 |
|---|---|---|
| 允许写入 | `Settings → Actions → General → Workflow permissions` 选 **Read and write** | `GITHUB_TOKEN` 建不了 Release，最后一步 403 |
| Actions 启用 | `Settings → Actions → General → Actions permissions` | 工作流根本不跑 |
| 环境（可选但推荐） | `Settings → Environments → New environment`，名字与 YAML 里 `environment:` **完全一致（区分大小写）** | environment Secret 注入为空 |

Secret 放哪都行，同名时 **environment 优先于 repository**：

- `Settings → Environments → <env> → Environment secrets`：可配 Required reviewers（发版需人工点批准），tag 触发后 job 会停在 Waiting。
- `Settings → Secrets and variables → Actions → Repository secrets`：单仓库最省事，无需在 job 上绑 `environment`。

⚠️ **关键**：用了 environment Secret 就必须在 job 上写 `environment: <env>`，否则拿到的值是空字符串 —— 而且是**静默**的（见第 4 节第 5 条）。

## 2. 工作流模板

```yaml
name: Release

on:
  push:
    tags: ['v*']        # 打 tag 才发版
  workflow_dispatch:    # 手动跑 = 只构建、不发版（用来验证配置）

permissions:
  contents: write        # 建 Release 必需

concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false

jobs:
  release:
    name: 构建 Release APK
    runs-on: ubuntu-latest
    environment: <env>   # 用 environment Secret 时必须；用 repository Secret 可删
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 0   # 生成提交清单需要完整历史与全部 tag

      # 版本号从构建脚本里读，避免 tag 与 versionName 各写一份
      - name: 读取版本号
        id: version
        run: |
          VERSION=$(grep -m1 '^val appVersionName' app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
          CODE=$(grep -m1 '^val appVersionCode' app/build.gradle.kts | sed -E 's/.*=[[:space:]]*([0-9]+).*/\1/')
          [ -n "$VERSION" ] && [ -n "$CODE" ] || { echo "::error::解析版本号失败"; exit 1; }
          echo "version=$VERSION" >> "$GITHUB_OUTPUT"
          echo "code=$CODE" >> "$GITHUB_OUTPUT"

      # 防呆：tag 和 versionName 不一致直接失败，避免发错版本号
      - name: 校验 tag 与版本号一致
        if: startsWith(github.ref, 'refs/tags/')
        run: |
          TAG="${GITHUB_REF#refs/tags/}"; TAG="${TAG#v}"
          if [ "$TAG" != "${{ steps.version.outputs.version }}" ]; then
            echo "::error::tag $GITHUB_REF_NAME 与 appVersionName=${{ steps.version.outputs.version }} 不一致"
            exit 1
          fi

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v4

      # 仓库里的 gradle.properties 常带本机专用配置，runner 上必须剔除
      - name: 清理本机专用 Gradle 配置
        run: |
          sed -i '/^org\.gradle\.java\.home=/d' gradle.properties
          sed -i '/^systemProp\.https\?\.proxy/d' gradle.properties
          cat gradle.properties

      # 配了 KEYSTORE_BASE64 就用正式签名；否则由构建脚本回退 debug 签名
      - name: 配置发布签名
        id: sign
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          # 只打印长度/别名，不泄露内容，便于定位"Secret 到底有没有注入"
          echo "B64 长度=${#KEYSTORE_BASE64} KEY_ALIAS=${KEY_ALIAS:-<空>}"
          if [ -z "$KEYSTORE_BASE64" ]; then
            echo "enabled=false" >> "$GITHUB_OUTPUT"
            echo "::warning::未配置 KEYSTORE_BASE64，产物将回退 debug 签名（仅适合自测）"
            exit 0
          fi
          echo "$KEYSTORE_BASE64" | base64 -d > release.jks
          cat > keystore.properties <<EOF
          storeFile=release.jks
          storePassword=$KEYSTORE_PASSWORD
          keyAlias=$KEY_ALIAS
          keyPassword=$KEY_PASSWORD
          EOF
          echo "enabled=true" >> "$GITHUB_OUTPUT"

      - name: 构建 Release APK
        run: |
          chmod +x gradlew          # 见第 4 节第 2 条
          ./gradlew --stacktrace assembleRelease

      - name: 收集产物并计算校验和
        id: apk
        run: |
          APK=$(ls app/build/outputs/apk/release/*.apk | head -n1)
          [ -n "$APK" ] || { echo "::error::未找到 release APK"; exit 1; }
          ( cd "$(dirname "$APK")" && sha256sum "$(basename "$APK")" > "$(basename "$APK").sha256" )
          echo "name=$(basename "$APK")" >> "$GITHUB_OUTPUT"
          echo "dir=$(dirname "$APK")" >> "$GITHUB_OUTPUT"

      # 关键：不信构建脚本，实际读产物签名证书
      - name: 校验 APK 签名
        env:
          SIGNING_ENABLED: ${{ steps.sign.outputs.enabled }}
        run: |
          APK="${{ steps.apk.outputs.dir }}/${{ steps.apk.outputs.name }}"
          APKSIGNER=$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -n1)
          DN=$("$APKSIGNER" verify --print-certs "$APK" | grep -m1 'certificate DN' || true)
          echo "签名证书：$DN"
          if [ "$SIGNING_ENABLED" = "true" ]; then
            echo "$DN" | grep -q 'CN=Android Debug' && {
              echo "::error::配了 KEYSTORE_BASE64 却是 debug 签名，请核对 Secret 内容"
              exit 1; }
          else
            echo "::warning::产物为 debug 签名，仅适合自测"
          fi

      - uses: actions/upload-artifact@v4
        with:
          name: ${{ steps.apk.outputs.name }}
          path: |
            ${{ steps.apk.outputs.dir }}/*.apk
            ${{ steps.apk.outputs.dir }}/*.sha256
          if-no-files-found: error

      # Release 描述 = 上一 tag 以来的提交清单（git log 生成），不附加其他内容
      - name: 生成提交清单
        if: startsWith(github.ref, 'refs/tags/')
        run: |
          TAG="${GITHUB_REF_NAME}"
          # 上一 tag：取当前 tag 父提交可达的最近一个 tag；首个版本则回退为全部历史
          PREV=$(git describe --tags --abbrev=0 "${TAG}^" 2>/dev/null || true)
          if [ -n "$PREV" ]; then
            RANGE="$PREV..$TAG"
          else
            RANGE="$TAG"
            echo "::warning::未找到上一 tag，提交清单将包含全部历史提交"
          fi
          {
            if [ -n "$PREV" ]; then
              echo "## 提交清单"
            else
              echo "## 提交清单（首个版本）"
            fi
            echo
            git log --no-merges --pretty=format:'- %h %s' "$RANGE"
            if [ -n "$PREV" ]; then
              echo
              echo
              echo "**Full Changelog**: https://github.com/${GITHUB_REPOSITORY}/compare/${PREV}...${TAG}"
            fi
          } > release-notes.md

      - name: 创建 GitHub Release
        if: startsWith(github.ref, 'refs/tags/')     # 手动触发时不发版
        uses: softprops/action-gh-release@v2
        with:
          name: <应用名> v${{ steps.version.outputs.version }}
          tag_name: ${{ github.ref_name }}
          body_path: release-notes.md
          fail_on_unmatched_files: true
          files: |
            ${{ steps.apk.outputs.dir }}/*.apk
            ${{ steps.apk.outputs.dir }}/*.sha256
```

## 3. 签名密钥准备

本机 `keystore.properties`（`.gitignore` 掉，不入库）里的四项就是要变成 Secret 的四项：

```properties
storeFile=D:/develop/key/<name>.jks
storePassword=******
keyAlias=******
keyPassword=******
```

生成 base64 并直接进剪贴板：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\develop\key\<name>.jks")) | Set-Clipboard
```

Secret 名：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
`KEYSTORE_BASE64` 缺失即回退 debug 签名 —— 所以用第 2 节里"校验 APK 签名"那步兜底，别指望构建脚本自觉。

## 4. 踩坑清单（按发生概率排序）

| # | 现象 | 原因 | 对策 |
|---|---|---|---|
| 1 | runner 上 Gradle 起不来：`Java home supplied is invalid` | 仓库 `gradle.properties` 写了 `org.gradle.java.home=D:/...` 这类本机路径 | CI 里 sed 删掉；长期做法是**别把机器相关配置提交进仓库**，放 `~/.gradle/gradle.properties` |
| 2 | 依赖全部下载超时 | 同上，`systemProp.http(s).proxyHost=127.0.0.1` 指向本机代理 | 同上 sed 删除 |
| 3 | `./gradlew: Permission denied` | Windows 上提交的 `gradlew` 权限是 `100644` | `chmod +x gradlew`，或 `git update-index --chmod=+x gradlew` 之后提交 |
| 4 | Linux 上 `./gradlew` 报 `: not found` / `$'\r'` | `gradlew` 被 CRLF 化 | 加 `.gitattributes`：`gradlew text eol=lf`；临时可 `sed -i 's/\r$//' gradlew` |
| 5 | **构建成功、Release 也发了，但装上的包签名是 `CN=Android Debug`** | Secret 没注入（拼错名字 / 放错作用域 / 忘写 `environment:`），构建脚本静默回退 debug 签名，工作流仍然绿灯 | ① 打印 Secret 长度做诊断；② 构建后用 `apksigner` 读证书，配了签名却是 debug 就直接 fail（第 2 节） |
| 6 | 发了版本号不对的包 | tag 和 `versionName` 各自维护 | tag 校验步骤，不一致直接失败；版本号只在构建脚本里定义一处，APK 文件名也由它派生 |
| 7 | 想重发同一个版本 | tag 已存在，不会再次触发 | 删 Release → 删远端 tag → 本地 `git tag -d vX` 后在目标提交重建 → 重新 push |
| 8 | `Required reviewers` 开着，tag 推上去"卡住" | 环境需要人工批准 | 到 run 页面点 Approve；不需要审批就别开 |
| 9 | 想验证配置却不想发版 | — | 用 `workflow_dispatch` + Release 步骤 `if: startsWith(github.ref, 'refs/tags/')`，只构建并传 artifact |
| 10 | 一堆 "Node.js 20 is deprecated" 告警 | 上游 action 尚未切 node24，runner 强制升级运行 | 只升有新主版本的（如 `checkout@v5`、`setup-java@v5`）；`upload-artifact@v4` / `gradle/actions@v4` 属正常，等上游 |
| 11 | 提交清单只有一条或为空 / `git describe` 找不到上一 tag | checkout 默认 `fetch-depth: 1`，浅克隆没有历史和 tag | checkout 加 `fetch-depth: 0`（见第 2 节模板） |

## 5. 发版 SOP

```powershell
# 1. 改版本号（构建脚本里唯一的版本定义处）
#    appVersionCode += 1，appVersionName = "1.1"

# 2. 提交 + 打标签，标签必须与 appVersionName 一致
git commit -am "release: v1.1"
git tag v1.1 -m "<应用名> v1.1"
git push origin main
git push origin v1.1        # 这一下触发构建与发版

# 3. 验证（见第 6 节）
```

## 6. 发布后怎么验真

```powershell
# 拿到发布产物 + 比对它自带的校验和
gh release download v1.1 -R <owner>/<repo>   # 或直接网页下载
Get-FileHash .\app-v1.1.apk -Algorithm SHA256   # 与 .sha256 文件内容比对

# 读签名证书
& "$env:ANDROID_HOME\build-tools\<ver>\apksigner.bat" verify --print-certs .\app-v1.1.apk
# 期望：Signer #1 certificate DN: CN=<你的名字>（不是 CN=Android Debug）

# 与本地密钥库对指纹，确认"能覆盖安装"
keytool -list -v -keystore <name>.jks -alias <alias> | Select-String 'SHA256:'
```

无 GitHub CLI / 免登录时，公开仓库可用 REST API 拉产物：
`GET /repos/{owner}/{repo}/releases` 取 asset `id`，再 `GET /repos/{owner}/{repo}/releases/assets/{id}` 带 `Accept: application/octet-stream` 下载。
运行状态与注解（含 `::warning::`/`::error::`）：
`GET /repos/{owner}/{repo}/commits/{sha}/check-runs` → `GET /repos/{owner}/{repo}/check-runs/{id}/annotations`。

## 7. 换项目时的改动清单

- [ ] `<应用名>`、`<env>` 替换
- [ ] 版本号来源：模板假设 `app/build.gradle.kts` 里 `val appVersionName` / `appVersionCode`；用 Groovy DSL 或 `gradle.properties` 时改第 3 步的正则
- [ ] APK 输出路径：模板为 `app/build/outputs/apk/release/`；多 flavor/多 ABI 会变成子目录，改成 `find app/build/outputs -name '*.apk'`
- [ ] 构建任务：`assembleRelease` → flavor 对应任务（`assemble<Flavor>Release`）
- [ ] 需跑测试的话在构建前加 `./gradlew testDebugUnitTest`（Robolectric 类测试在 runner 上慢，可只跑纯 JVM 测试）
- [ ] 应用有 `compileSdk` 要求时确认 runner 镜像自带该 SDK（`ubuntu-latest` 一般含最新几个；否则加 `android-actions/setup-android`）
- [ ] 仓库若把 `gradle.properties` 里的本机配置清干净了，第 2 节的 sed 步骤可删

## 8. 可选增强

- **构建检查前移**：加 `on: push: branches: [main]`，Release 步骤已被 tag 守卫，不会误发版，每次 push 都能知道能不能构建。
- **草稿式发布**：`softprops/action-gh-release` 加 `draft: true`，人工点发布再公开。
- **Release 说明**：工作流自生成，只含提交清单——`git log` 列出上一 tag 以来的提交（`- %h %s` 格式）经 `body_path` 传入；首个版本回退为全部历史提交。不用 `generate_release_notes: true`（会按 PR 汇总，本项目直推 main 时基本为空）。
- **产物瘦身**：多 ABI 项目用 `splits abi` 后按 ABI 上传多个 APK / 打 AAB。
- **签名密钥更安全的放法**：改用 GitHub OIDC / 云 KMS 托管签名密钥（`apksigner` 支持 PKCS#11），避免 base64 私钥进 Secret。

## 9. 本项目落地记录（tangshi，作为实证）

提交（均在 `main`）：

| 提交 | 内容 |
|---|---|
| `0f5ef80` | 新增 `release.yml`：tag 触发构建 + 发 Release，手动触发只构建；README 补章节 |
| `fb5e30e` | job 绑定 `environment: release`（环境 Secret 优先，可配人工审批） |
| `87ecbc9` | 签名显式化：回退 debug 时输出 `::warning::`；新增构建后 `apksigner` 校验；打印 Secret 长度做诊断 |
| `5ab46da` | `actions/setup-java` v4 → v5 |
| `7da3e71` | `actions/checkout` v4 → v5 |

关键运行：

| Run | 触发 | 结果 |
|---|---|---|
| `39895015166` | tag v1.0（首版） | 全绿，**但产物是 debug 签名** —— 沉默失败，即第 4 节第 5 条 |
| `39895983896` | tag v1.0（重发） | 全绿，产物签名 `CN=cao pengfei`，与本地密钥库 SHA256 指纹一致 |
| `39897873323` | workflow_dispatch | 全绿，`setup-java@v5` 生效 |
| `39899650708` | workflow_dispatch | 全绿，`checkout@v5` 生效，Release 步骤按预期 skipped |

首版签名的判定方式：下载 Release 里的 APK → `apksigner verify --print-certs` → `CN=Android Debug`（错）；重发后同法得到 `CN=cao pengfei`，再用 `keytool` 取本地密钥库指纹比对一致（`15ddeeaa…6f320`），并核对 `.sha256` 与下载文件哈希一致。
