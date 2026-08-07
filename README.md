# Local Ledger Android Backend

> 本项目开源协议存在更改可能。

这是按照三份设计文档实现的 Android 本地记账后端。工程只包含本地数据访问与业务逻辑，
不声明 `INTERNET` 或公共存储权限，也不包含远程服务。

前端接入所需的完整接口说明见 `安卓本地记账App-后端接口文档.md`。

## 已实现

- Room 四表模型、外键、必需复合索引和完整性触发器
- 收入、支出、单流水转账、编辑和硬删除
- 账户实时余额、复合流水筛选、收支汇总、分类排行和本地时区趋势聚合
- 账本、账户、分类 Repository 与归档逻辑
- App 专属外部目录中的 `data/ledger.db` 和原子写入的 `config/settings.json`
- 首次启动默认账本、4 个账户、9 个支出分类和 5 个收入分类
- Hilt 单例注入、领域错误、金额解析和半开日期范围解析

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

Room Schema 导出到 `app/schemas/`。当前是初始 Schema v1，因此没有历史版本 Migration；
后续每次修改 Schema 都应提升数据库版本、加入显式 Migration，并以导出的旧 Schema 编写升级测试。
=======
