# Local Ledger Android Backend

> 本项目开源协议存在更改可能。

这是按照三份设计文档实现、并继续扩展的 Android 记账应用。账本、账户、分类和流水仍只保存在本机。
应用声明 `INTERNET` 权限，仅用于向公开汇率接口请求汇率；不会上传账本内容。没有网络时使用上次缓存的汇率。

前端接入所需的完整接口说明见 `安卓本地记账App-后端接口文档.md`。

## 已实现

- Room 账本、账户、分类、流水，以及预算、周期规则、发生记录和汇率缓存；外键、复合索引和完整性触发器
- 收入、支出、单流水转账、编辑和硬删除
- 账户实时余额、复合流水筛选、收支汇总、分类排行和本地时区趋势聚合
- 账本、账户、分类 Repository 与归档逻辑
- App 专属外部目录中的 `data/ledger.db` 和原子写入的 `config/settings.json`
- 首次启动默认账本、4 个账户、9 个支出分类和 5 个收入分类
- Hilt 单例注入、领域错误、金额解析和半开日期范围解析
- 月度总预算与分类预算，首页和统计页显示进度、剩余和超支
- 日/周/月/年周期记账，经现有保存用例入账，WorkManager 与启动时补记，按规则和日期去重
- 统计页支出占比、完整分类排行、年度月趋势、长区间补齐空月份，以及可横向滑动的趋势图
- 账本本位币与账户币种、金额符号、汇率缓存，以及跨币种转账的双边金额

## 多币种与汇率

- 账本 `currency_code` 是本位币。首页汇总、统计、预算都按本位币计算。
- 账户各自有 `currency_code`。账户余额和流水上的 `amount_minor` 使用该账户的最小货币单位。
- 收入和支出在保存时折算成 `base_amount_minor`，并记下当时的 `fx_rate`（1 单位账户货币兑换多少本位币）。之后汇率变化不会改写已经入账的汇总。
- 转账同时保存转出金额 `amount_minor` 和转入金额 `to_amount_minor`。两边账户余额分别使用各自金额，因此可以表示不同币种，也可以手工填写实际到账金额。未填写转入金额时，按缓存汇率计算。同币种转账的两边金额相同。
- 汇率优先读取中国外汇交易中心（人民银行授权发布）的人民币汇率中间价，无需密钥：`https://www.chinamoney.com.cn/r/cms/www/chinamoney/data/fx/ccpr.json`。该文件给出最近一个工作日约 9:15（北京时间）的中间价。若这个文件不可用，改读同站 `CcprHisNew` 的最新一条，并按当天 9:15（北京时间）计。
- 中间价以人民币为标价。`USD/CNY` 是 1 外币兑人民币，`100JPY/CNY` 是 100 外币兑人民币，`CNY/KRW` 是 1 人民币兑外币。换算到账本本位币时，先统一成「1 人民币兑多少外币」，再做交叉：`报价 = 外币对人民币 / 本位币对人民币`。本位币不是人民币时同样走这条交叉，不改历史流水。
- 交易中心目前公布美元、欧元、日元、港币、英镑、澳元、加元、新加坡元、瑞士法郎、韩元等，不含新台币。缺的币种用 jsDelivr 上的 [currency-api](https://github.com/fawazahmed0/currency-api) 补齐（`cdn.jsdelivr.net`，大陆一般可访问）。仍缺或中间价整体失败时，再试 Frankfurter（`api.frankfurter.dev`）和 `open.er-api.com`。结果写入 `exchange_rate_snapshots`。设置页显示来源和北京时间的发布时间；刷新失败时继续用上次缓存。

## 数据库

Room schema 当前为 v2。v1 到 v2 的升级在 `MIGRATION_1_2`：账户增加币种，流水增加转入金额、本位币金额和汇率，并新建预算、周期规则、发生记录和汇率缓存表。历史流水按原金额视为本位币金额，历史转账的转入金额等于转出金额。

## 前端接入

前端/ViewModel 只注入 `domain.repository` 中的接口和 `domain.usecase` 中的用例，
不要直接调用 DAO。应用启动状态通过 `BackendInitializer.state` 暴露：

- `Initializing`: 正在创建目录、打开数据库或写入预置数据
- `Ready`: 后端可用，并包含当前 `AppSettings`
- `Error`: 本地存储不可用，可向用户展示领域错误

配置更新通过 `SettingsStore.update` 完成。设置文件损坏或丢失时会按当前可用账本重建，
不会修改数据库账本数据。

## 构建与验证

需要 JDK 17 和 Android SDK 35：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Room Schema 导出到 `app/schemas/`。当前是 Schema v2，包含从导出的 v1 schema 升级到 v2 的迁移测试。
后续每次修改 Schema 都应提升数据库版本、加入显式 Migration，并以导出的旧 Schema 编写升级测试。