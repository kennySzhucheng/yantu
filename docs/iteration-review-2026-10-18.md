# 《日拱一卒》v1.6.0 复盘与 v1.7.0 升级建议

> 审查日期：2026-10-18
> 审查基准：GitHub `main` @ `cf558d7`（v1.6.0，2026-10-08）+ 本地工作区（内容与远端一致，仅换行/编码差异）
> 方法：GitHub API 调研 → 全量源码阅读（32 个 Kotlin 文件 / 7,703 行）→ 本地构建与 APK 产物验证

---

## 一、GitHub 调研结果

### 1.1 仓库与发布

| 项目 | 值 |
|---|---|
| 仓库 | https://github.com/kennySzhucheng/yantu （公开，MIT） |
| 简介 | 研途 · 备考打卡提醒（注意：已更名「日拱一卒」，仓库描述未同步） |
| 创建 / 最近推送 | 2026-10-06 / 2026-10-08 |
| Star / Fork | 5 / 2 |
| 最新 Release | v1.6.0（2026-10-08），APK 下载 9 次（arm64 6 + v7a 3） |
| 历史 Release | v1.0.0、v1.0.1、v1.4.0、v1.5.0、v1.5.1、v1.6.0（**v1.1–v1.3 无 Release**，仅出现在 README 更新日志） |
| 分发 | GitHub Releases + 蓝奏云（国内主渠道，带提取码） |

### 1.2 Issue：只有 1 条，且非有效反馈

Issue [#1「有用的！」](https://github.com/kennySzhucheng/yantu/issues/1)（CT666225，2026-10-07）
- 正文：「ct路过！」
- 评论：「😘ct！！」（作者回复）、「ct过路」（ZJBIG）
- 结论：**没有可执行的信息**。Discussions 未开启，也没有 issue 模板，所以「用户报 bug」实际发生在 PR 区。

### 1.3 Pull Request：两个真实 bug 修复，都还开着

| PR | 作者 | 标题 | 实际内容 | 状态 |
|---|---|---|---|---|
| [#3](https://github.com/kennySzhucheng/yantu/pull/3) | CT666225 | 修复重大节点提醒被错误续成每日任务 | 节点一次性提醒触发后 `enqueueNext` 无条件续链 → 生成 `daily_node` 每日任务永久自转；修复方案：只允许 4 类周期提醒续链 + 启动时清理遗留 `daily_node`；附 3 个单测 | OPEN，未合入 |
| [#2](https://github.com/kennySzhucheng/yantu/pull/2) | ZJBIG | 修复课表教学周边界，避免开学前误显示课程 | `weekOf` 用截断除法（`DAYS/7+1`），开学前 1–6 天被判为第 1 周；开学日非周一时周次错位。修复：以开学日所在周的周一为起点 + `floorDiv`；附 3 个 JVM 测试 | OPEN，未合入 |

> 两个 PR 的讨论区只有「可以哦 / 不可以哦」等社交内容，没有 code review。我已在现行源码中复核：两处 bug **都真实存在且都未修复**（`Notify.kt:278`、`Timetable.kt:62-63`）。

### 1.4 仓库工程状况

- 无 `.github/`：无 CI、无 issue/PR 模板、无 CONTRIBUTING；README 却已提供反馈入口，用户引导与承接能力不匹配。
- 无 `app/src/test`、无 `androidTest`：**零自动化测试**。
- 无 `INTERNET` 声明（源码层面）——但交付物里有，见 P0-1。
- 仓库内容干净：无 APK、无 keystore、无个人数据；`personal-backup/`、`verify/`、`promo/`、`tools/` 中可能含个人信息的脚本均已 gitignore。

---

## 二、P0：必须优先处理（均已验证）

### P0-1 隐私承诺与交付物冲突：已发布 APK 实际申请了网络权限

- **证据链**
  - 源码 `app/src/main/AndroidManifest.xml` 未声明 `INTERNET`；
  - 但**已发布 APK** `rigong-v1.5.1-arm64.apk` 用 `aapt2 dump permissions` 实测含：
    `INTERNET`、`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE`、`WAKE_LOCK`；
  - 合并清单 blame 显示 `INTERNET` 来源：`com.google.android.datatransport:transport-backend-cct:2.3.3`（ML Kit 中文 OCR 的传递依赖）；`app/build/intermediates/manifest_merge_blame_file/release/.../manifest-merger-blame-release-report.txt:40`。
  - `allowBackup="true"` 且无 `dataExtractionRules` / `fullBackupContent`（`AndroidManifest.xml:9`）→ 系统 Auto Backup 默认可把 DataStore（打卡历史、计划、奖励）备份到云端。
- **矛盾点**：README 第 5、152、187 行明确写「无网络权限」「不截图上传、不统计」「全部在本机，不上传任何服务器」。
- **影响**：隐私卖点失实（也是应用商店 Data Safety 的硬伤）；理论上存在 ML Kit 使用统计上报路径；换机/云恢复会把个人备考数据带出设备。
- **建议**
  1. 用 `tools:node="remove"` 移除 `INTERNET` / `ACCESS_NETWORK_STATE`（保留类、只去权限），并在真机/模拟器验证 OCR 仍可用；或排除 `com.google.android.datatransport` 组并实测；
  2. 增加 `android:dataExtractionRules`（明确禁止云备份）或改 `allowBackup=false`（应用已有自建导出/导入，换机够用）；
  3. README 按事实重写隐私章节（改成「应用不主动联网；OCR 依赖组件声明了网络权限，已移除」）。

### P0-2 跨天不刷新日期：次日仍显示并写入「昨天」的打卡

- `MainActivity.kt:371` `val today = remember { LocalDate.now() }`（无 key、无 onResume 刷新）；`yesterday/dayBefore` 同源（373-376）；`onToggle` 写入 `store.toggleCheckin(today, …)`（554-559）。
- 场景：App 后台过夜 → 点通知/最近任务回来 → 界面仍是昨天 → 用户勾选写进昨天；连续天数、奖励、周报口径全错。
- 建议：`ON_RESUME`/`onNewIntent` 重算日期，或 00:00 ticker；通知 `PendingIntent` 加 `SINGLE_TOP|CLEAR_TOP` 并在 `onNewIntent` 刷新。

### P0-3 「修改考试信息」会静默清空院校/专业/科目/关于配置

- `ConfigManageScreen.kt:183-193` 只写 4 个键（label/date/start/targetScore），经 `MainActivity.kt:472-477` → `UserConfig.putSection`（`UserConfig.kt:32-36`）**整段覆盖** app 段；缺失的 `schoolFrom/schoolTo/subjects/about*` 回落到 `assets/config/app.json` 的空字符串（第 7-13 行）。
- 结果：今日页页脚、设置→关于、目标院校信息被清空，且无确认、无回滚。同样信息在设置页改日期却是「先读后写」（不会清空），两条路径行为不一致。
- 建议：改为读-改-写（`merge` 现有段后写回）；写入前弹确认，并记住最近一次可撤销的值。

### P0-4 阶段序号硬编码：新增第 7 阶段或导入 `id≥7` 必崩

- `TodayScreen.kt:196` 与 `StagesScreen.kt:101`：`"①②③④⑤⑥"[stage.id - 1]`。
- `assets/config/plan.json` 已用到 `id: 6`；计划编辑器新增阶段取 `max(id)+1`（`PlanEditorScreen.kt:303`）→ 第 7 阶段必然 `StringIndexOutOfBoundsException`；同一会话连续新增两次还会生成重复 id。
- 建议：统一的 `stageBadge(id)`（`getOrElse` 兜底 + 序号生成函数）；编辑器分配 id 时基于本地列表去重。

### P0-5 重大节点提醒被续成每日任务（= PR#3）

- `Notify.kt:233-247` 节点分支执行后，`278` 无条件 `Notify.enqueueNext(ctx, kind)`；`node` 落入 `else` → 唯一名 `daily_node`、每天 21:30 自转；且该任务**无 tag**，`scheduleNodeReminders` 的 `cancelAllWorkByTag(TAG_NODE)` 清不掉，设置里的「节点提醒」开关也无效。
- 影响：安装了有未来节点的用户，每天多一次无意义后台唤醒；节点提醒开关「关了还醒」。
- 建议：采用 PR#3 的白名单方案 + 启动时迁移清理 `daily_node`；补单测（PR#3 自带 3 个用例）。

### P0-6 DataStore 无损坏兜底：文件损坏 = 永久打不开

- `Store.kt:18` `preferencesDataStore(name="kaoyan")` 无 `corruptionHandler`；全仓库 0 处 `.catch`；启动期还有 `runBlocking` 读 DataStore（`MainActivity.kt:181/183`、`UserConfig.kt:20-23`）。
- 结果：prefs 损坏/IO 异常 → 每次启动在 composition 抛异常，进不了界面，也没有「导出/重置」入口；用户只能清数据，全部记录丢失。
- 建议：`ReplaceFileCorruptionHandler { emptyPreferences() }`；所有 flow `.catch { emit(默认) }`；启动读取移到 IO 线程；配置加载失败页补「恢复内置 / 导出数据」按钮（`MainActivity.kt:167-175` 目前是死胡同）。

---

## 三、P1：重要但可排在 P0 之后

| # | 问题 | 证据 | 建议 |
|---|---|---|---|
| 1 | `ReminderWorker.doWork` 无 try/finally，一次 IO 异常即断链 | `Notify.kt:162-279`，续链在最后一行 | `try{…} finally{ runCatching{ enqueueNext } }` |
| 2 | 教学周 `weekOf` 边界错误（= PR#2）+ 空课表仍显示「第 N/18 教学周」 | `Timetable.kt:62-66`；`TodayScreen.kt:288` | 采用 PR#2；课表为空时不显示周次 |
| 3 | 向导第 2 步填的科目不进第 3 步计划 | `OnboardingScreen.kt:149-153` → `PlanOptionsForm.kt:38-47` | 用 Step2 的值初始化 PlanOptions |
| 4 | 「稍后导入」静默继承示例计划，三处日期互相矛盾 | `OnboardingScreen.kt:403-408` + `assets/config/plan.json` | 明确提示「将使用内置示例计划」或引导先配置 |
| 5 | `Activity.recreate()` 导致编辑状态丢失、跳回首页 | `MainActivity.kt:228-232`；全库 `rememberSaveable` 为 0 | 状态提升 + rememberSaveable；保存后不重建或保留导航栈 |
| 6 | 课表导入默认把 `semesterStart` 重置为「本周一」 | `ImportPreviewScreen.kt:58-60` | 默认沿用现有学期起始日 + 覆盖确认 |
| 7 | 导入配置/备份失败完全静默，可能半套写入 | `MainActivity.kt:321-355`、`UserConfig.kt:32-36` | 事务式写入 + 成功/失败提示 + 导入前自动备份 |
| 8 | 重跑向导会重写 plan，旧任务 id 的历史打卡变成"孤儿" | `OnboardingScreen.kt:386-408`、`Stats.kt:38-47` | 任务 id 稳定映射或迁移旧记录；重跑前提示影响 |
| 9 | 通知权限被拒后完全静默，无系统设置入口 | `Notify.kt:290-291`、`MainActivity.kt:358-365` | 首页/设置检测并跳 `ACTION_APP_NOTIFICATION_SETTINGS` |
| 10 | 提醒依赖非精确 WorkManager，超窗口即静默跳过；无电池优化/自启动引导 | `Notify.kt:168-182`；`SettingsScreen.kt:333-341` | 加 `ACTION_IGNORE_BATTERY_OPTIMIZATIONS` 入口与厂商设置指引 |
| 11 | 日历读取在主线程（`produceState` 同步查询） | `MainActivity.kt:389-391`、`CalendarEvents.kt:34` | `withContext(Dispatchers.IO)` + 异常兜底 |
| 12 | 依赖全面陈旧：AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12 / Work 2.9.1 / jxl 2.6.12（2011 年停维护） | `gradle/libs.versions.toml` | 升 AGP/Kotlin/Compose；jxl 换 POI 或自研解析（注意包体） |
| 13 | 构建无门禁：签名缺失时静默产出未签名包；minify 关闭；无 CI | `app/build.gradle.kts:14-25/54-59` | 签名缺失 fail-fast；R8+shrinkResources；CI 跑 `assembleDebug + test + lint` |

---

## 四、README 与实现不一致（7 处）

1. **「初试日期全联动」不成立**：设置改日期只写 `app.examDate` + 重排节点通知（`SettingsScreen.kt:417-427`），阶段与 `keyNodes` 仍来自 plan JSON，不会重排。
2. **「换学校/换考试全部可视化」偏乐观**：配置管理没有学校/专业/科目表单，换学校只能「重跑向导」，而向导会连计划一起重写。
3. **v1.6.0「内置配置不再包含任何示例课表与个人偏好」只对课表成立**：`plan.json` 仍是完整 6 阶段示例计划，且与作者真实计划的日期/阶段名同源。
4. **「进度感知自动跳过基础期」未实现**：`PlanTemplate.kt:200-205` 算了 `skipBase` 但从未使用。
5. **「奖励池可自定义」没有编辑入口**：只能导出 JSON 手改或导入配置。
6. **「任何方式解析不出的内容都会列在预览页」过度承诺**：表头识别失败时整张网格被丢弃（`TimetableParser.kt:47-49`，`unparsed` 为空）。
7. **代码有、文档没写**：「恢复内置模板」「应用内置示例」；阶段页策略提示按阶段名关键字匹配；「修改考试信息」的副作用；示例档案「冲刺起步·英二数三」实际生成的是数学二。

---

## 五、新功能候选（约束：离线、无账号、无网络权限）

| 功能 | 用户价值 | 成本 | 风险 | 说明 |
|---|---|---|---|---|
| **桌面小组件**：倒计时 / 今日底线 / 连勤，可一键打卡 | ★★★★★ | 中 | 低 | 最直接的「不断卡」抓手，竞品标配；Glance + DataStore |
| **复盘数据中心**：周/月热力图、科目投入、达标率、断卡预警 | ★★★★★ | 低-中 | 低 | 数据已在 Store/Stats，只差聚合 UI + Canvas；可离线导出图片 |
| **课表 × 计划冲突自适应**：按当天课程/假期算可用时长，自动降级底线 | ★★★★ | 中 | 低 | 全 App 最独特的差异点，课表数据已在手 |
| **番茄钟 / 学习计时**：正计时/番茄，替代「勾选后估时长」 | ★★★★ | 中高 | 中 | 需前台服务与耗电取舍 |
| **错题本 / 复盘笔记**：按科目、套卷归档，随备份导出 | ★★★ | 中高 | 中 | 数据体积与备份格式影响 |
| **阶段验收清单可勾选 + 本地徽章** | ★★★ | 低 | 低 | 复用现有接受标准与奖励样式 |
| **桌面快捷方式**：长按图标直达「今日打卡 / 课表」 | ★★ | 低 | 低 | App Shortcuts + 深链，成本极低 |

---

## 六、工程与发布流程风险（需要你知道的真相）

1. **本地 git 与 GitHub 已经"脱节"**（已核实）
   - 本地 `HEAD = 9230373`（v1.0.1，2026-10-06），该 commit **不存在于远端**；远端 `main = cf558d7`（v1.6.0），两者无共同祖先（`git rev-list --left-right --count HEAD...origin/main` = 7/14）。
   - 也就是说 v1.1–v1.6 的开发在本地是**未提交的 staged 改动**，远端是用 `tools/push_via_api.py` 通过 GitHub Data API 直接写出来的。
   - 后果：`git push` 会被拒；PR 无法用常规流程合并；任何一次 force push 都会抹掉远端历史与两个社区 PR。
   - 好消息：**内容已核对一致**（工作区 vs `origin/main` 仅 `timetable.json` BOM 与 `gradlew.bat` 换行差异）；通过本机 Clash 代理（127.0.0.1:7897）`git fetch` 可用，可按「从 `origin/main` 建分支 → 把两个 PR 以 `refs/pull/N/head` 取回 → 在新分支上做 v1.7.0」的方式无损修复。
2. **本机没有 JDK 在 PATH**，但 `gradlew` 可用 JBR 21（`D:\pycharm\...\jbr`）构建成功（本次已验证 debug 构建 7m17s 通过）。建议把 `JAVA_HOME` 写进 `gradle.properties`（`org.gradle.java.home`）或 README 开发说明。
3. **版本号在 4 处手工维护**：`app/build.gradle.kts:31-32`、`SettingsScreen.kt:303`、`GuideScreen.kt:56-111`（`TUTORIAL_VERSION` + 手写更新内容）、README。漏改任一就会出现「设置里版本号不一致 / 不弹更新内容」。
4. **无 CI、无测试**：两个社区 PR 都自带测试，正好作为测试基建的起点（`testImplementation junit + org.json`）。
5. **发布产物未归档**：v1.6.0 APK 只存在于 Release，构建目录与本地 APK 是旧的；建议保留每个版本的构建日志/校验和。

---

## 七、建议的迭代计划

### v1.6.1（修复版，建议先发，1–2 天）
1. 合并 PR#2 / PR#3（保留原作者署名）+ 补测试；同时修 P0-2/3/4/5/6。
2. 隐私修复：去 `INTERNET`/`ACCESS_NETWORK_STATE`、关云备份、README 隐私章节如实改写（**需在模拟器/真机验证 OCR 仍可用**）。
3. git 历史修复：以 `origin/main` 为基线建分支，再落 v1.6.1 提交，恢复「本地 = 远端」的正常工作流。
4. 更新日志 / `TUTORIAL_VERSION` / README 同步。

### v1.7.0（功能版）
- 复盘数据中心（热力图 + 趋势 + 断卡预警，成本低、感知强）
- 桌面小组件（倒计时 + 今日底线 + 一键打卡）
- 课表 × 计划可用时长自适应（差异化）
- 体验：状态保存（`rememberSaveable`）、导入失败提示、课表导入保留学期起始日、通知权限引导

### 待定
- 番茄钟、错题本、依赖大升级（AGP/Kotlin/Compose）建议单独排期，避免与功能版混在一次发布里。

---

## 八、验证记录

| 检查 | 命令/方法 | 结果 |
|---|---|---|
| 本地构建 | `JAVA_HOME=...jbr ./gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL (7m17s)，产出 3 个 ABI debug APK |
| 发布包权限 | `aapt2 dump permissions rigong-v1.5.1-arm64.apk` | `INTERNET`、`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE`、`WAKE_LOCK` 存在 |
| 权限来源 | release merged manifest blame | `com.google.android.datatransport:transport-backend-cct:2.3.3` |
| PR#2/#3 是否已修 | `grep isRecurringKind / floorDiv` | 均不存在 → 未修 |
| 远端可达性 | `git -c http.proxy=http://127.0.0.1:7897 fetch` | ✅ 成功（直连被 RST） |

---

## 九、本次实施记录（v1.6.1）

### 9.1 已完成的修复

| 类别 | 修复 | 文件 |
|---|---|---|
| 社区 PR | 教学周边界（PR #2，作者 ZJBIG） | `Timetable.kt` + `TimetableTest` |
| 社区 PR | 节点提醒续链（PR #3，作者 CT666225） | `Notify.kt` + `NotifyTest` |
| 隐私 | 移除 `INTERNET` / `ACCESS_NETWORK_STATE`（`tools:node="remove"`）；`allowBackup=false` | `AndroidManifest.xml` |
| 数据正确性 | 修改考试信息改为「读-改-写」局部合并，不再清空档案 | `ConfigManageScreen.kt`、`UserConfig.kt`、`MainActivity.kt` |
| 数据正确性 | 跨天刷新：`ON_RESUME` + 午夜 ticker 重算「今天」 | `MainActivity.kt` |
| 崩溃 | 阶段序号改用 `stageBadge()`（支持 1–20，越界回退）；新增阶段 id 按本地列表分配 | `UiCommon.kt`、`TodayScreen.kt`、`StagesScreen.kt`、`PlanEditorScreen.kt` |
| 可靠性 | DataStore 损坏回退 + IO 异常 `.catch`；启动读取兜底；配置失败页给「恢复内置」按钮 | `Store.kt`、`MainActivity.kt` |
| 可靠性 | `ReminderWorker` 单次异常不断链 | `Notify.kt` |
| 可靠性 | 空课表不再显示「第 N 教学周」；`slotLabels` 越界回退 | `TodayScreen.kt` |
| 体验 | 课表导入默认沿用现有学期起始日 | `ImportPreviewScreen.kt` |
| 体验 | Android 15 edge-to-edge：状态栏/导航栏图标明暗跟随主题；全屏页面 `safeDrawingPadding` | `MainActivity.kt`、`OnboardingScreen.kt`、`GuideScreen.kt` |
| 工程 | 版本号单一来源（`PackageManager` 读取）、JDK/测试说明、`.gitignore` 忽略构建日志 | `SettingsScreen.kt`、`README.md`、`.gitignore` |

### 9.2 验证结果

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | ✅ 11 个用例全部通过（PR 自带 6 + 新增 StageBadge 3 + ConfigMerge 2） |
| `connectedDebugAndroidTest`（Android 15 模拟器） | ✅ 2 个用例通过：安装包无 `INTERNET`/`ACCESS_NETWORK_STATE`；**无网络权限下中文 OCR 正常识别** |
| Release 包权限（aapt2） | ✅ 仅 `POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`、`READ_CALENDAR`、`WAKE_LOCK`、`FOREGROUND_SERVICE` |
| 签名一致性 | ✅ 与 v1.5.1 证书 SHA-256 相同（可覆盖安装保留数据） |
| 版本 | ✅ versionCode 10 / versionName 1.6.1 |
| 模拟器人工验证 | ✅ 向导 → 主界面 → 阶段页 → 设置页正常；状态栏可读；修改考试信息后档案保留 |

### 9.3 产物

- `rigong-v1.6.1-arm64.apk`（21.7 MB）
- `rigong-v1.6.1-v7a.apk`（17.6 MB）
- 构建环境：JDK 21 + Gradle 8.9 + Android SDK 35（脚本见 README 开发者章节）

### 9.4 仍未处理（留给 v1.7.0）

- README 声明的「初试日期全联动」、`skipBase` 未生效、奖励池无编辑入口等文档/实现差距
- `Activity.recreate()` 导致编辑状态丢失、重跑向导改变历史打卡语义
- 通知权限被拒后的系统设置引导、电池优化/自启动引导
- 依赖升级（AGP / Kotlin / Compose / jxl）与 R8、CI
- 桌面小组件、复盘数据中心等新功能

