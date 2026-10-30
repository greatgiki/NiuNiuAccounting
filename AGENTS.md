# AGENTS.md — 牛牛记账（NiuNiuAccounting）开发指南

本文件供阅读和修改本项目的 AI agent 使用。内容均来自当前代码；代码变更后请同步更新本文件。

## 1. 概览与技术栈

- 安卓本地记账 App，面向中国用户，数据全部存于本机。
- 语言：Kotlin 2.1.20；UI：Jetpack Compose（BOM 2025.04.01，Material3）。
- 构建：Gradle Kotlin DSL（`build.gradle.kts`、`app/build.gradle.kts`），KSP。
- 数据库：Room 2.7.1，schema 版本 `WALLET_SCHEMA_VERSION = 6`（`app/src/main/java/com/example/localledger/data/local/database/WalletDatabase.kt`），已有 `MIGRATION_1_2` … `MIGRATION_5_6`。
- DI：Hilt（dagger hilt-android 2.56.2，hilt-navigation-compose）。
- compileSdk 35，minSdk 26，targetSdk 35；versionName `0.1`，versionCode `1`。
- 包名 / 源码根：`com.example.localledger` → `app/src/main/java/com/example/localledger/`。
- 其他模块：`macrobenchmark/`（性能基准）；Compose 稳定性配置 `compose_stability.conf`。

## 2. 架构

分层：UI（Compose Screen）→ ViewModel → Repository（domain 接口 + data 实现）→ Room DAO / 远程 / 本地存储。

| 路径 | 作用 |
|---|---|
| `ui/` | 各功能页面与 ViewModel：home、ledger、detail、editor、category、account、statistics、report、budget、calendar、recurring、importing、exporting、backup、ai、voice、settings、lock、startup、widget；公共组件在 `ui/common`、`ui/component`，主题在 `ui/theme` |
| `ui/navigation/` | `AppNavHost.kt`（Navigation Compose 路由 `Routes`，顶层页面用 Pager），`PagerGesture.kt`，`PredictiveBack.kt` |
| `ui/platform/WindowOverscroll.kt` | 全局关闭 overscroll 效果 |
| `domain/` | 纯业务逻辑：model、repository 接口、usecase、money、statistics、report、budget、recurrence、calendar、time、ai、capture、importing、voice、widget、error |
| `data/local/` | Room：entity、dao、database、converter、model |
| `data/repository/` | 各 `*RepositoryImpl`、`DraftRepository`、`PaymentDraftWriter`、`MerchantCategoryBook`、`LedgerRevaluator`、`BillImportRepository` |
| `data/remote/` | `HttpExchangeRateRemote.kt` 汇率 |
| `data/ai`、`data/backup`、`data/export`、`data/work` | AI 网关与密钥存储、备份、导出、WorkManager 任务 |
| `di/` | `BackendModule.kt`、`AiModule.kt` |
| `capture/` | 无障碍自动记账 |
| `notification/` | 通知监听自动记账与草稿通知 |
| `security/` | 应用锁（PIN、生物识别） |
| `widget/` | 桌面小组件 |
| `core/money`、`core/storage` | 金额与本地存储基础设施 |

数据流：Room DAO 以 Flow 暴露 → Repository 映射为 domain 模型（`data/mapper`）→ ViewModel 组合为 UI state → Compose 收集渲染；用户操作经 ViewModel 调用 usecase / repository 写库。

## 3. 功能模块

- **账单增删改查**：`ui/editor`、`ui/detail`、`ui/home`、`ui/ledger`；`data/repository/TransactionRepositoryImpl.kt`。
- **删除可撤销**：`ui/detail/TransactionListViewModel.kt`（`OfferUndo` / `pendingUndo`）。
- **分类 / 账户**：`ui/category`、`ui/account`；`CategoryRepositoryImpl`、`AccountRepositoryImpl`；商户→分类记忆 `MerchantCategoryBook`、`ui/settings/MerchantCategoryScreen.kt`。
- **统计 / 报表 / 预算 / 日历**：`ui/statistics`、`ui/report`、`ui/budget`、`ui/calendar`；`domain/statistics`、`domain/report`、`domain/budget`。
- **周期记账**：`ui/recurring`、`domain/recurrence`、`data/work/RecurringTransactionWorker.kt`。
- **多币种与汇率**：`data/remote/HttpExchangeRateRemote.kt`。来源顺序：中国货币网 CFETS 人民币中间价（人民银行授权发布）→ jsDelivr currency-api 补充 → open.er-api / Frankfurter 填补缺口 → 直连 Frankfurter / open.er-api 兜底。重估在 `LedgerRevaluator`，设置页 `ExchangeRateViewModel`。
- **AI**：`domain/ai`（记账助手、账本问答、工具调用、文本脱敏 `LedgerTextRedactor`）、`data/ai`（`HttpAiProvider`、`AiCredentialStore`）、`ui/ai`。仅在本机直连用户自填 key 的 OpenAI / Anthropic 兼容接口，无自建后端。
- **语音记账**：`ui/voice`、`domain/voice`。
- **自动记账**（默认关闭，识别全部在本机）：
  - 无障碍服务 `capture/PaymentAccessibilityService.kt`，仅监听微信、支付宝、云闪付（`res/xml/payment_accessibility_service.xml`）；页面解析 `domain/capture/PaymentScreenParser.kt`、`PaymentPageRules.kt`。
  - 通知监听 `notification/PaymentNotificationListenerService.kt`、`NotificationCapturePipeline.kt`，解析 `domain/importing/PaymentNotificationParser.kt`。
  - 金额被隐藏的通知（如“消费**元”）生成待补全草稿，提醒点开原通知后由页面识别补金额（`DraftRepository`、`notification/DraftNotifier.kt`、`SourceBillIntents.kt`）。
  - 微信“微信支付 / 服务通知”交易消息：`domain/capture/WeChatServiceNotice.kt`。
  - 银行短信（需用户手动开启并授予权限）：`capture/BankSmsReceiver.kt`、`domain/capture/BankSmsParser.kt`。
  - 跨来源去重：`domain/capture/CaptureDedup.kt`，按金额、收支方向和 3 分钟窗口。
  - 详见 `docs/auto-capture.md`。
- **账单导入**：`ui/importing`、`domain/importing`（CSV / xlsx 解析）。
- **导出**：`data/export`、`ui/exporting`。
- **备份**：`data/backup`（含加密 `BackupCrypto`）、`ui/backup`、自动备份 `data/work/AutoBackup.kt`。
- **应用锁**：`security/`、`ui/lock`。
- **桌面小组件**：`widget/`、`domain/widget`。

## 4. 开发规范

- **构建 / 测试**：`./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`（Windows 用 `gradlew.bat`）。单元测试在 `app/src/test`。
- **数据库**：改动 entity / schema 必须提升 `WALLET_SCHEMA_VERSION` 并新增 `Migration`，不得破坏性迁移丢用户数据。
- **UI**：低调奢华风格（见 `docs/design-system.md`）；任何页面不低于 30 fps、不卡顿；全局禁止 overscroll 回弹 / 拉伸效果，新增可滚动组件也不得出现。
- **网络服务**：只用中国大陆可访问的服务；汇率优先人民银行（CFETS）。
- **隐私**：识别在本机完成，不上传账单数据；AI 只用用户自己的 key 直连。
- **打包**：只产出一个已签名 APK，命名 `NiuNiuAccounting-<sha>.apk`，不带 debug / release 字样。
- **Git**：
  - 作者与提交者均为 `Ge Kai <118527150+greatgiki@users.noreply.github.com>`，不得带 Co-authored-by 或 Cursor 相关 trailer。
  - 开发完推送到分支；用户要求合并前不开 PR。
  - 绝不强制推送 master。
- **文档同步**：改代码时同步更新 `README.md`、`docs/` 和本文件。
