# 安卓本地记账 App 后端设计文档

## 1. 文档范围

本 App 不包含网络服务器、远程 API、登录、云同步或在线数据库。本文中的“后端”指 Android App 内部的本地数据访问层和业务逻辑层：

```text
Repository
Use Case
Room DAO
SQLite 数据库
本地 App 专属文件目录
```

所有数据只存在当前安装 App 的手机本机。

## 2. 技术选型与分层

```text
Kotlin
Room / SQLite
Coroutines + Flow
Hilt
java.time
```

分层职责：

```text
UI / ViewModel
  -> Use Case：业务校验、时间范围换算、写入编排
  -> Repository：统一数据访问接口和事务入口
  -> DAO：Room SQL 查询与持久化
  -> SQLite：账本原始数据
```

UI 不应直接调用 DAO；DAO 不应包含跨表业务决策。

建议目录：

```text
data/
  local/
    entity/
    dao/
    converter/
    database/
  repository/
domain/
  model/
  repository/
  usecase/
  time/
core/
  storage/
  money/
```

## 3. 本地文件目录与数据库初始化

### 3.1 存储位置

首次启动时，使用 Android App 专属外部目录创建统一数据根目录：

```text
/storage/emulated/0/Android/data/<package-name>/files/
```

目录结构：

```text
files/
  data/
    ledger.db
    ledger.db-wal
    ledger.db-shm
  config/
    settings.json
```

`data/` 保存数据库及 SQLite 的 WAL/SHM 运行文件；`config/` 保存非账本配置，例如当前账本 ID、最近一次使用的账户 ID、主题设置。

App 不申请 `INTERNET`、全盘文件访问或媒体读取权限。

### 3.2 初始化流程

应用启动时依次执行：

1. 获取 `context.getExternalFilesDir(null)`。
2. 创建 `data/` 和 `config/`，失败则停止数据库初始化并向 UI 返回本地存储错误。
3. 使用 `data/ledger.db` 创建或打开 Room 数据库。
4. 执行 Room Migration。
5. 检查是否已有账本；若没有则在单一事务中创建默认账本、预置账户和预置分类。
6. 读取或创建 `settings.json`，确定当前账本。

数据库创建示意：

```kotlin
val dataDirectory = File(context.getExternalFilesDir(null), "data")
check(dataDirectory.exists() || dataDirectory.mkdirs())

val databaseFile = File(dataDirectory, "ledger.db")
val database = Room.databaseBuilder(
    context,
    WalletDatabase::class.java,
    databaseFile.absolutePath
).addMigrations(*MIGRATIONS).build()
```

应用必须只维护一个数据库实例。不要在多个 Repository 或 ViewModel 中重复构建 Room 数据库。

### 3.3 卸载与可用性约束

- 数据位于本机 App 专属目录，其他 App 不能直接访问。
- Android 11 及以后，文件管理器通常限制直接浏览 `Android/data`。
- 卸载 App、清除 App 数据或恢复出厂设置会删除该目录。
- MVP 不提供备份恢复，设置页和首次提示必须明确上述风险。

## 4. 数据库模型

所有主键使用字符串 UUID。金额一律以分为单位保存为 `Long`。时间以 UTC epoch milliseconds 保存为 `Long`，按设备时区换算为本地日期。

### 4.1 `ledgers`

```text
id TEXT PRIMARY KEY
name TEXT NOT NULL
currency_code TEXT NOT NULL DEFAULT 'CNY'
created_at INTEGER NOT NULL
is_archived INTEGER NOT NULL DEFAULT 0
```

### 4.2 `accounts`

```text
id TEXT PRIMARY KEY
ledger_id TEXT NOT NULL REFERENCES ledgers(id)
name TEXT NOT NULL
type TEXT NOT NULL
initial_balance_minor INTEGER NOT NULL DEFAULT 0
sort_order INTEGER NOT NULL DEFAULT 0
is_archived INTEGER NOT NULL DEFAULT 0
created_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
```

`type` 取值：`CASH`、`BANK_CARD`、`E_WALLET`、`CREDIT_CARD`、`OTHER`。

### 4.3 `categories`

```text
id TEXT PRIMARY KEY
ledger_id TEXT NOT NULL REFERENCES ledgers(id)
name TEXT NOT NULL
type TEXT NOT NULL
icon_key TEXT NOT NULL
sort_order INTEGER NOT NULL DEFAULT 0
is_archived INTEGER NOT NULL DEFAULT 0
created_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
```

`type` 取值：`EXPENSE`、`INCOME`。分类应归属账本，确保账本间完全隔离。

### 4.4 `transactions`

```text
id TEXT PRIMARY KEY
ledger_id TEXT NOT NULL REFERENCES ledgers(id)
type TEXT NOT NULL
amount_minor INTEGER NOT NULL CHECK(amount_minor > 0)
account_id TEXT NOT NULL REFERENCES accounts(id)
to_account_id TEXT NULL REFERENCES accounts(id)
category_id TEXT NULL REFERENCES categories(id)
transaction_at INTEGER NOT NULL
note TEXT NULL
created_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
```

`type` 取值：`EXPENSE`、`INCOME`、`TRANSFER`。

数据库约束：

- `EXPENSE` 和 `INCOME` 必须有 `category_id`，且 `to_account_id` 为 `NULL`。
- `TRANSFER` 必须有 `to_account_id`，且 `category_id` 为 `NULL`。
- 转账的 `account_id` 和 `to_account_id` 不得相同。
- 所有关联账户、分类与流水必须属于同一 `ledger_id`。
- 账户与分类的 `ledger_id` 创建后不可变，禁止变更所属账本（见第 10 节的触发器要求），防止已被引用的账户或分类跨账本移动造成统计污染。

SQLite 的 `CHECK` 难以可靠验证跨表归属关系，因此该类规则必须由 Use Case 在写入前验证，并用事务防止并发状态不一致。

### 4.5 索引

必须创建的索引：

```text
transactions(ledger_id, transaction_at DESC)
transactions(ledger_id, type, transaction_at DESC)
transactions(ledger_id, category_id, transaction_at DESC)
transactions(ledger_id, account_id, transaction_at DESC)
accounts(ledger_id, is_archived, sort_order)
categories(ledger_id, type, is_archived, sort_order)
```

当数据量增大时，明细筛选和统计都依赖 `ledger_id + transaction_at` 查询，因此这是最关键的复合索引。

## 5. 领域模型与金额规则

领域层使用明确枚举：

```kotlin
enum class TransactionType { EXPENSE, INCOME, TRANSFER }
enum class CategoryType { EXPENSE, INCOME }
enum class AccountType { CASH, BANK_CARD, E_WALLET, CREDIT_CARD, OTHER }
```

金额规则：

```text
1 元 = 100 分
amountMinor 必须大于 0
数据库和业务计算中不使用 Double 或 Float
```

金额输入由 UI 转为 `Long` 后才调用 Use Case；Use Case 仍需要再次验证金额合法性，以避免调用方绕过 UI 校验。

## 6. Repository 接口

核心接口建议如下：

```kotlin
interface LedgerRepository {
    fun observeLedgers(): Flow<List<Ledger>>
    suspend fun createLedger(name: String): String
    suspend fun updateLedger(ledger: Ledger)
    suspend fun archiveLedger(ledgerId: String)
}

interface AccountRepository {
    fun observeAccounts(ledgerId: String): Flow<List<Account>>
    fun observeAccountBalances(ledgerId: String): Flow<List<AccountBalance>>
    suspend fun saveAccount(account: Account)
    suspend fun archiveAccount(accountId: String)
}

interface CategoryRepository {
    fun observeCategories(ledgerId: String, type: CategoryType): Flow<List<Category>>
    suspend fun saveCategory(category: Category)
    suspend fun archiveCategory(categoryId: String)
}

interface TransactionRepository {
    fun observeTransactions(ledgerId: String, filter: TransactionFilter): Flow<List<TransactionDetail>>
    suspend fun saveTransaction(command: SaveTransactionCommand): String
    suspend fun deleteTransaction(ledgerId: String, transactionId: String)
}

interface StatisticsRepository {
    fun observeSummary(ledgerId: String, range: DateTimeRange): Flow<IncomeExpenseSummary>
    fun observeExpenseCategories(ledgerId: String, range: DateTimeRange): Flow<List<CategoryTotal>>
    fun observeDailyTrend(ledgerId: String, range: DateTimeRange): Flow<List<DailyTotal>>
    fun observeMonthlyTrend(ledgerId: String, year: Int): Flow<List<MonthlyTotal>>
}
```

Repository 对外暴露领域模型，不泄露 Room Entity、DAO 或 SQL 类型。

注：以上 `deleteTransaction(ledgerId, transactionId)` 为目标签名；当前代码仍为 `deleteTransaction(transactionId)`，接口、Use Case 与调用方待同步修改。

## 7. 写入用例与事务

### 7.1 保存支出或收入

`SaveIncomeExpenseUseCase` 执行：

1. 验证账本存在且未归档。
2. 验证金额大于 0。
3. 验证账户存在、属于账本且未停用。
4. 验证分类存在、属于账本、未停用，且分类类型与流水类型一致。
5. 新建或更新对应流水。
6. 更新 `updated_at`。

以上写入在 Room `withTransaction` 中执行。虽然单行写入本身是原子的，事务保证日后扩展审计或派生数据时不会改变行为边界。

### 7.2 保存转账

`SaveTransferUseCase` 执行：

1. 验证金额大于 0。
2. 验证源账户和目标账户都存在、属于当前账本且未停用。
3. 验证两个账户 ID 不相同。
4. 确认 `categoryId == null`。
5. 在一个事务中插入或更新一条 `TRANSFER` 流水。

转账仅存为一条交易记录。账户余额查询根据该记录在源账户和目标账户上的不同角色计算，避免生成两条记录带来的重复编辑和重复统计问题。

### 7.3 硬删除

`DeleteTransactionUseCase` 执行：

1. 按 ID 和所属账本读取流水。
2. 校验流水存在且属于指定账本；不属于时按流水不存在处理，不泄露其他账本数据的存在性。
3. 执行 `DELETE FROM transactions WHERE id = :transactionId AND ledger_id = :ledgerId`。
4. 返回删除结果。

删除必须携带账本 ID 是本设计账本隔离原则的一部分：仅持有全局 ID 不足以删除记录。（已定决策，`deleteTransaction` 签名与代码待同步修改）

删除不可撤销，不设置 `deleted_at`，不保留回收站。UI 负责二次确认；Use Case 不假设 UI 一定完成了确认。

### 7.4 账户和分类停用

账户或分类即使已有历史流水也可以停用。停用只更新 `is_archived = 1`：

- 新建或编辑流水时不得选用已停用项。
- 历史流水、统计和余额仍通过关联 ID 显示该项的最后名称和类型。
- 删除账户或分类不属于 MVP，以避免破坏历史关联。

## 8. 时间范围与统计设计

### 8.1 时间存储和时区

`transaction_at` 存储 UTC epoch milliseconds。用户选择本地日期、月份、年份或区间时，由 `DateRangeResolver` 基于设备 `ZoneId` 转换为半开区间：

```text
[startInclusive, endExclusive)
```

例如设备时区下的 2026-07-21：

```text
start = 2026-07-21T00:00:00 at device zone -> epoch millis
end   = 2026-07-22T00:00:00 at device zone -> epoch millis
```

SQL 始终使用：

```sql
transaction_at >= :startInclusive
AND transaction_at < :endExclusive
```

这种半开区间等价于包含用户选择的结束日期全天，且不会出现 `23:59:59.999` 精度边界问题。

时区语义（产品决策，已实现行为）：归日、归月使用查询时刻的设备默认时区，这是有意的显示语义。设备时区变化后，同一笔历史流水可能被归入不同的本地日期或月份，历史统计结果随之变化。该行为为预期行为，不视为缺陷。若未来需求变更为历史归属必须稳定（账本历史不随时区漂移），再引入账本级锁定 `ZoneId` 或冗余存储业务本地日期；该改动涉及 Schema 变更，决策越晚成本越高。

### 8.2 支持的统计范围

```kotlin
sealed interface StatisticsPeriod {
    data class Day(val date: LocalDate) : StatisticsPeriod
    data class Month(val yearMonth: YearMonth) : StatisticsPeriod
    data class Year(val year: Int) : StatisticsPeriod
    data class Custom(val startDate: LocalDate, val endDate: LocalDate) : StatisticsPeriod
}
```

约束：自定义区间的 `endDate` 不得早于 `startDate`。默认范围为当前月。

### 8.3 收支汇总 SQL

收支汇总仅统计收入和支出，转账不参与：

```sql
SELECT
  COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_minor ELSE 0 END), 0) AS income_minor,
  COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_minor ELSE 0 END), 0) AS expense_minor
FROM transactions
WHERE ledger_id = :ledgerId
  AND transaction_at >= :startInclusive
  AND transaction_at < :endExclusive;
```

结余在领域层计算：

```text
balanceMinor = incomeMinor - expenseMinor
```

### 8.4 分类支出统计

```sql
SELECT
  c.id AS category_id,
  c.name AS category_name,
  c.icon_key AS icon_key,
  SUM(t.amount_minor) AS amount_minor
FROM transactions t
JOIN categories c ON c.id = t.category_id
WHERE t.ledger_id = :ledgerId
  AND t.type = 'EXPENSE'
  AND t.transaction_at >= :startInclusive
  AND t.transaction_at < :endExclusive
GROUP BY c.id, c.name, c.icon_key
ORDER BY amount_minor DESC;
```

### 8.5 趋势统计

- 指定月：按本地日期聚合，展示该月按日收入和支出。
- 指定年：按本地月份聚合，展示该年按月收入和支出。
- 指定日：只显示汇总，无需趋势图。
- 自定义区间：区间跨度不超过 62 天时按日聚合；超过 62 天时按月聚合，避免图表点过多。

SQLite 的 UTC 时间戳不能直接可靠地按用户本地日历聚合。MVP 的稳妥实现是：DAO 先按时间范围读取必要的 `transaction_at`、`type` 和 `amount_minor`，再由 Kotlin 使用同一 `ZoneId` 转为 `LocalDate` / `YearMonth` 后聚合。后续需要处理数十万流水时，再引入按本地日期冗余存储和索引的优化方案。

### 8.6 账户余额

账户余额不受统计范围限制，表示当前余额：

```text
初始余额
+ 本账户的收入
- 本账户的支出
- 本账户作为转出账户的转账
+ 本账户作为转入账户的转账
```

可通过单个 SQL 聚合或 Kotlin 汇总获得。结果必须包含没有任何流水的账户，其余额等于初始余额。

## 9. 配置存储

`settings.json` 仅保存可重建的界面偏好，不保存交易数据：

```json
{
  "currentLedgerId": "uuid",
  "lastAccountIdByLedger": {
    "ledger-uuid": "account-uuid"
  },
  "themeMode": "SYSTEM"
}
```

写入配置使用临时文件后原子替换，避免应用中断产生半截 JSON。配置丢失时可重新创建默认值，绝不能影响数据库中的账本数据。

## 10. 数据库迁移与完整性

- 每次 Schema 变化都必须提供 Room `Migration`，禁止在正式版本使用 `fallbackToDestructiveMigration()`。
- 每个 Migration 需要从旧版本真实数据库文件升级的测试。
- 初始预置数据只在数据库为空时插入，且使用固定或可识别的 UUID，避免升级或重启时重复插入。
- 外键约束应开启，并优先使用 `RESTRICT` 防止误删仍被历史流水引用的账户、分类或账本。
- 账户、分类的 `ledger_id` 必须由 `BEFORE UPDATE OF ledger_id` 触发器保护：新旧值不同时 `RAISE(ABORT)`，在数据库层强制账本归属不可变，不依赖 Repository 自觉。（已定决策，代码与测试待实现）
- 每个写入异常必须向上层返回可展示的领域错误，不允许吞掉 SQLite 异常。

## 11. 后端测试

### DAO 与数据库测试

- 新建账本、账户、分类和流水后查询正确。
- 收入、支出、转账分别正确影响账户余额。
- 转账不进入收入、支出及分类统计。
- 删除流水后，余额、汇总和趋势查询正确刷新。
- 使用其他账本的 `ledgerId` 删除流水被拒绝，目标流水不受影响。
- 停用账户或分类不影响历史查询，但新建流水校验失败。
- 账本之间的流水和统计相互隔离。
- 直接更新账户或分类的 `ledger_id` 被数据库触发器拒绝。
- 设备时区变化后，流水的日期和月份归属按新时区重新计算，符合既定的显示语义。
- 复合筛选条件能返回正确明细。

### Use Case 测试

- 金额为零、负数或溢出时拒绝保存。
- 收入和支出分类类型不匹配时拒绝保存。
- 转账双方账户相同时拒绝保存。
- 转账账户、分类不属于当前账本时拒绝保存。
- 指定日、月、年、自定义范围均生成正确的半开时间区间。
- 自定义范围开始日期晚于结束日期时拒绝查询。

### Migration 测试

- 每个已发布数据库版本可平滑升级到最新版本。
- 升级后旧流水、账户和分类数据不丢失，索引存在且查询正常。

## 12. 后端验收标准

- 全部账本数据和配置只写入 App 本机专属目录，无网络依赖。
- 首次启动可以可靠创建目录、数据库、默认账本、预置账户和预置分类。
- 所有金额使用 `Long` 分单位，长期累计不存在浮点误差。
- 收入、支出、转账、编辑、硬删除后，明细、统计和当前账户余额保持一致。
- 统计准确支持指定日、月、年和自定义日期区间；年度汇总按月展示趋势。
- 时间范围包含结束日期全天；统计使用查询时的设备默认时区，时区变化后历史归属随之变化，该行为为预期语义（跨时区回归测试为必补项，当前尚未覆盖）。
- 数据库升级不会因 Schema 变更清空用户账本。
