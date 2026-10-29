# 牛牛记账

> 本项目开源协议存在更改可能。

Android 本机记账应用，包名 `com.example.localledger`。当前版本 **0.1**（`versionCode` 1，`versionName` `"0.1"`）。设置页「关于」显示的版本来自 `BuildConfig.VERSION_NAME`。

账本、账户、分类和流水只保存在本机。没有账号，也没有自建服务器。系统云备份已关闭（`allowBackup` 为 false）。没有网络时仍可记账；汇率使用上次缓存。

仓库不附带界面截图。截图测试写到 `app/build/screenshots`（部分测试也会写 `/opt/cursor/artifacts/screenshots`），这些目录不纳入版本库。桌面小组件只有一张预览图 `app/src/main/res/drawable-nodpi/widget_ledger_preview.png`。

三份设计文档描述最初的账本模型。和当前应用不一致的地方，以本文件和代码为准：

- `安卓本地记账App-后端接口文档.md`
- `安卓本地记账App-后端设计文档.md`
- `安卓本地记账App-前端设计文档.md`
- `安卓本地记账App-MVP设计方案.md`

自动记账的规则见 `docs/auto-capture.md`。视觉见 `docs/design-system.md`。

## 已实现

- Room 账本、账户、分类、流水，以及预算、周期规则、发生记录和汇率缓存；外键、复合索引和完整性触发器
- 收入、支出、单流水转账、编辑和删除。删除后底部提示可以撤销
- 账户实时余额、复合流水筛选、收支汇总、分类排行和本地时区趋势聚合
- 月度总预算与分类预算；日/周/月/年周期记账，WorkManager 与启动时补记
- 账本本位币、账户币种、汇率缓存，以及跨币种转账的双边金额
- 首页日历、周报和月报；统计页可打开同一套周报、月报
- 微信支付、支付宝账单文件导入（csv 或 xlsx；微信邮件里的加密压缩包需先解压）
- 商家分类记忆
- 语音记账：本机规则解析中文口令，不上传这句话。识别不到时可用系统语音或手输
- 桌面小组件：今日和本月的收入、支出、结余，以及记一笔。应用锁打开时默认遮住金额
- 应用锁：本机 PIN，可选指纹或面容。密钥和 PIN 摘要不进账本数据库
- 底部四页：首页、明细、统计、设置；中间是「记一笔」
- App 专属外部目录中的 `data/ledger.db` 和原子写入的 `config/settings.json`
- 首次启动默认账本、4 个账户、9 个支出分类和 5 个收入分类

## 自动记账

默认关闭。打开后，无障碍服务在本机识别微信、支付宝、云闪付的支付成功页和账单详情，弹出确认单再写入。细则、隐藏金额、服务通知、银行短信和三分钟去重见 `docs/auto-capture.md`。

- 微信或支付宝通知若是「消费**元」这类隐藏金额，先留下没有金额的待确认，并在状态栏提醒。点提醒会打开原来的通知；页面识别到金额后，确认单带上金额
- 在微信里打开「微信支付」或「服务通知」里的交易消息时，读取明文金额和商户，用来补上待确认或新开一笔确认
- 银行短信和常见银行 App 通知是单独开关，默认关闭。打开时才会请求短信权限。不读取短信信箱
- 约三分钟内，金额和方向相同的页面、通知、服务通知和银行短信只记一笔

## AI 助手

默认关闭。密钥由用户自己填写，只保存在应用私有目录的密文里，不写入 `settings.json`，也不进系统云备份。支持 OpenAI Chat Completions 和 Anthropic Messages，以及兼容这两种格式的地址（预设里有 OpenAI、Anthropic、DeepSeek 和自定义）。

开启之后，只有解析这句话或向账本提问时，才会把当次必要的文字发到用户填写的地址。提问前会抹掉卡号和长串数字。没配密钥时，记账、语音规则解析和自动记账仍然可用。自动记账里若商家对不上本机分类，才会把商家名称和分类名单发给该接口猜一个分类。

## 多币种与汇率

- 账本 `currency_code` 是本位币。首页汇总、统计、预算都按本位币计算。
- 账户各自有 `currency_code`。账户余额和流水上的 `amount_minor` 使用该账户的最小货币单位。
- 收入和支出在保存时折算成 `base_amount_minor`，并记下当时的 `fx_rate`（1 单位账户货币兑换多少本位币）。刷新汇率不会自动改历史。更改账本本位币时，按「不晚于该笔流水时间的最近一条汇率缓存」重算；如果流水早于全部缓存，则用当前汇率。设置页可以在确认后按当前汇率重算全部历史本位币金额。账户原币金额和手工填写的转入金额都不改。每次成功刷新都会把当时的汇率追加进 `exchange_rate_history`。
- 转账同时保存转出金额 `amount_minor` 和转入金额 `to_amount_minor`。两边账户余额分别使用各自金额，因此可以表示不同币种，也可以手工填写实际到账金额。未填写转入金额时，按缓存汇率计算。同币种转账的两边金额相同。
- 汇率优先读取中国外汇交易中心（人民银行授权发布）的人民币汇率中间价，无需密钥：`https://www.chinamoney.com.cn/r/cms/www/chinamoney/data/fx/ccpr.json`。该文件给出最近一个工作日约 9:15（北京时间）的中间价。若这个文件不可用，改读同站 `CcprHisNew` 的最新一条，并按当天 9:15（北京时间）计。
- 中间价以人民币为标价。`USD/CNY` 是 1 外币兑人民币，`100JPY/CNY` 是 100 外币兑人民币，`CNY/KRW` 是 1 人民币兑外币。换算到账本本位币时，先统一成「1 人民币兑多少外币」，再做交叉：`报价 = 外币对人民币 / 本位币对人民币`。本位币不是人民币时同样走这条交叉。保存新流水时用当前缓存；改写已入账本位币金额只发生在更换账本本位币或用户确认「按当前汇率重算」时。
- 交易中心目前公布美元、欧元、日元、港币、英镑、澳元、加元、新加坡元、瑞士法郎、韩元等，不含新台币。缺的币种用 jsDelivr 上的 [currency-api](https://github.com/fawazahmed0/currency-api) 补齐（`cdn.jsdelivr.net`，大陆一般可访问）。还缺则先试 `open.er-api.com`，再试 Frankfurter（`api.frankfurter.dev`）。中间价整体失败、或补齐后仍没有本位币时，改用已经按本位币报价的 Frankfurter，缺的再由 `open.er-api.com` 补。结果写入 `exchange_rate_snapshots`。设置页显示来源和北京时间的发布时间；刷新失败时继续用上次缓存。

## 备份与导出

「备份与恢复」把账本导出成文件，可选密码。恢复会替换当前账本、账户、流水、预算、周期规则、汇率缓存和待确认。写入前先在本机留一份安全副本。备份文件的数据库版本必须与当前应用一致（当前 Room schema 为 v6），更高或更低都不能直接恢复。

「导出流水」按日期导出 Excel 或 CSV，由用户自己保管。

## 数据库

Room schema 当前为 v6。迁移都在 `WalletDatabase.kt`，导出的 schema 在 `app/schemas/.../1.json` 到 `6.json`。

- v1 → v2（`MIGRATION_1_2`）：账户币种，流水转入金额、本位币金额和汇率，以及预算、周期规则、发生记录、汇率缓存。历史流水按原金额视为本位币金额，历史转账的转入金额等于转出金额。
- v2 → v3（`MIGRATION_2_3`）：`exchange_rate_history`，并把当时的汇率缓存复制进去。
- v3 → v4（`MIGRATION_3_4`）：流水的外部来源、外部单号和 `origin`，以及 `pending_drafts`。
- v4 → v5（`MIGRATION_4_5`）：待确认增加 `needs_amount`、`sbn_key`、`group_key`。
- v5 → v6（`MIGRATION_5_6`）：流水增加 `counterparty`，并新建 `merchant_category_rules`。

恢复已暂停的周期规则时，可以选择补记错过的日期，或跳到下一次不再补记。删除规则时，可以选择保留或删除已经生成的流水。

## 权限

不申请通讯录、相册、定位或全盘文件访问。清单里与功能对应的权限：

| 权限 | 用途 |
|---|---|
| `INTERNET` | 刷新公开汇率。AI 助手打开后，也用于访问用户填写的接口 |
| `RECEIVE_SMS` | 仅在银行短信开关打开、用户授予之后，接收新短信广播。不读短信信箱 |
| `POST_NOTIFICATIONS` | 待确认、待补金额和离开支付页后的提醒 |
| `SYSTEM_ALERT_WINDOW` | 支付确认悬浮窗 |
| `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE` | 无障碍服务在离开支付页后弹出确认 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 设置里引导用户自行允许，避免后台被系统停掉 |
| `RECORD_AUDIO` | 语音记账。麦克风不是必需硬件 |
| `USE_BIOMETRIC`、`USE_FINGERPRINT` | 应用锁的指纹或面容 |

无障碍服务和通知使用权由系统设置授予，不在上面的安装期权限列表里。说明文字见 `strings.xml` 的 `accessibility_service_description`。

## 前端接入

前端/ViewModel 只注入 `domain.repository` 中的接口和 `domain.usecase` 中的用例，
不要直接调用 DAO。应用启动状态通过 `BackendInitializer.state` 暴露：

- `Initializing`: 正在创建目录、打开数据库或写入预置数据
- `Ready`: 后端可用，并包含当前 `AppSettings`
- `Error`: 本地存储不可用，可向用户展示领域错误

配置更新通过 `SettingsStore.update` 完成。设置文件损坏或丢失时会按当前可用账本重建，
不会修改数据库账本数据。

## 构建与验证

需要 JDK 17 和 Android SDK 35（`compileSdk` / `targetSdk` 35，`minSdk` 26）：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

```bash
bash ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

发布构建会打开 R8。工程里的 release 构建类型没有单独的签名配置，打出来的是未签名包，除非本机另行配置签名。

Room Schema 导出到 `app/schemas/`。当前是 Schema v6，迁移测试覆盖从导出的旧 schema 升到最新版本。
后续每次修改 Schema 都应提升数据库版本、加入显式 Migration，并以导出的旧 Schema 编写升级测试。