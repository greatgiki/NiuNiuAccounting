# 安卓本地记账 App 后端接口文档

## 1. 文档说明

本文档描述当前工程中已经实现、供前端和 ViewModel 调用的 Kotlin 后端接口。

本文中的“接口”是 App 进程内的 Kotlin API，不是 HTTP、REST 或 RPC 接口。账本读写都在本机；唯一的出站请求是汇率刷新（中国外汇交易中心人民币中间价，以及补齐缺币种的备用源）。调用链如下：

```text
Compose UI
  -> ViewModel
  -> Use Case / Repository
  -> Room DAO
  -> SQLite
```

前端只应调用以下公开层：

```text
domain/repository/   Repository 接口
domain/usecase/      流水写入用例
domain/time/         日期范围换算
core/money/          金额输入解析
core/storage/        初始化状态和本地配置
```

前端不得直接调用 `data/local/dao`、Room Entity 或 `WalletDatabase`。

当前接口版本对应：

```text
应用版本：1.0
Room Schema：v1
包名：com.example.localledger
最低 Android 版本：API 26
```

## 2. 接入方式

### 2.1 Hilt 注入

Repository 接口、Use Case、`DateRangeResolver`、`BackendInitializer` 和 `SettingsStore` 均可通过 Hilt 注入。

ViewModel 示例：

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val statisticsRepository: StatisticsRepository,
    private val dateRangeResolver: DateRangeResolver,
) : ViewModel()
```

应用必须使用工程中的 `LocalLedgerApplication`，数据库和 Repository 均由 Hilt 维护单例。不要自行创建 `WalletDatabase`。

### 2.2 协程约定

- 查询观察接口返回 `Flow<T>`，应在 ViewModel 中持续收集。
- 写入接口均为 `suspend`，应在 `viewModelScope.launch` 中调用。
- Repository 不维护“当前账本”隐式状态，每次调用都必须传入明确的 `ledgerId`。
- 账本切换时，推荐使用 `flatMapLatest` 切换相关查询。
- Room 表变化后，对应 `Flow` 会重新发射最新数据。

示例：

```kotlin
val transactions = currentLedgerId
    .filterNotNull()
    .flatMapLatest { ledgerId ->
        transactionRepository.observeTransactions(ledgerId)
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
```

## 3. 通用数据约定

### 3.1 ID

所有实体 ID 均为字符串 UUID。

| 对象 | 新增时 ID 来源 |
|---|---|
| 账本 | `LedgerRepository.createLedger` 自动生成并返回 |
| 流水 | 保存命令的 `transactionId = null` 时自动生成并返回 |
| 账户 | 调用方生成 UUID，放入 `Account.id` |
| 分类 | 调用方生成 UUID，放入 `Category.id` |

调用方生成示例：

```kotlin
val id = UUID.randomUUID().toString()
```

### 3.2 金额

所有金额都使用人民币“分”为单位的 `Long`：

```text
0.01 元 -> 1
12.50 元 -> 1250
100 元 -> 10000
```

业务计算和数据库中禁止使用 `Double` 或 `Float`。

金额输入可通过 `MoneyParser` 转换：

```kotlin
val amountMinor = MoneyParser.yuanToMinor("12.50") // 1250L
```

`MoneyParser.yuanToMinor` 的规则：

- 自动去除首尾空格。
- 最多允许两位小数。
- 金额必须大于 0。
- 超出 `Long` 范围时拒绝。
- 非法时抛出 `LedgerException.InvalidAmount`。

账户的 `initialBalanceMinor` 可以为正数、零或负数；只有流水的 `amountMinor` 必须大于 0。

### 3.3 时间

以下时间字段均为 UTC epoch milliseconds：

```text
createdAt
updatedAt
transactionAt
DateTimeRange.startInclusive
DateTimeRange.endExclusive
```

日期查询统一使用半开区间：

```text
[startInclusive, endExclusive)
```

UI 不应自行拼接 `23:59:59.999`。应使用 `DateRangeResolver` 将本地日期转换成时间戳区间。

### 3.4 排序与归档

- 账本按“未归档优先、创建时间升序”返回。
- 账户按“未停用优先、`sortOrder` 升序、创建时间升序”返回。
- 分类按“未停用优先、`sortOrder` 升序、创建时间升序”返回。
- 流水按“交易时间倒序、创建时间倒序”返回。
- 归档账户和分类仍保留在查询结果及历史流水中。
- 新建或编辑流水不能使用已归档的账本、账户或分类。

## 4. 枚举

### 4.1 `TransactionType`

```kotlin
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
}
```

| 值 | 含义 | 是否进入收支统计 |
|---|---|---|
| `EXPENSE` | 支出 | 计入支出 |
| `INCOME` | 收入 | 计入收入 |
| `TRANSFER` | 转账 | 不计入收入或支出 |

### 4.2 `CategoryType`

```kotlin
enum class CategoryType {
    EXPENSE,
    INCOME,
}
```

分类创建后不允许修改类型。停用分类不会影响历史数据。

### 4.3 `AccountType`

```kotlin
enum class AccountType {
    CASH,
    BANK_CARD,
    E_WALLET,
    CREDIT_CARD,
    OTHER,
}
```

### 4.4 `ThemeMode`

```kotlin
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
```

## 5. 初始化接口

### 5.1 `BackendInitializer`

包路径：

```kotlin
com.example.localledger.core.storage.BackendInitializer
```

公开成员：

```kotlin
val state: StateFlow<InitializationState>

fun start()

suspend fun initialize(): InitializationState
```

应用的 `LocalLedgerApplication` 已在 `onCreate` 中自动调用 `start()`。一般情况下，前端只需观察 `state`，不需要再次调用 `start()`。

`initialize()` 可用于用户点击“重试”时重新执行初始化。

### 5.2 `InitializationState`

```kotlin
sealed interface InitializationState {
    data object Initializing : InitializationState
    data class Ready(val settings: AppSettings) : InitializationState
    data class Error(val error: LedgerException.LocalStorage) : InitializationState
}
```

| 状态 | 含义 | UI 建议 |
|---|---|---|
| `Initializing` | 创建目录、打开数据库、执行初始化 | 显示加载状态 |
| `Ready` | 后端可用 | 进入主界面 |
| `Error` | 本地存储不可用 | 显示错误和重试操作 |

初始化流程会：

1. 创建 App 专属外部目录下的 `data/` 和 `config/`。
2. 打开 `data/ledger.db`。
3. 安装数据库完整性约束。
4. 空数据库中创建默认“日常账本”。
5. 创建 4 个预置账户、9 个支出分类和 5 个收入分类。
6. 读取或创建 `config/settings.json`。
7. 当配置中的当前账本不存在或已归档时，切换到第一个可用账本。

观察示例：

```kotlin
val appState = backendInitializer.state
    .map { state ->
        when (state) {
            InitializationState.Initializing -> AppUiState.Loading
            is InitializationState.Ready -> AppUiState.Content(
                currentLedgerId = state.settings.currentLedgerId,
            )
            is InitializationState.Error -> AppUiState.Error(state.error.message.orEmpty())
        }
    }
```

## 6. 配置接口

### 6.1 `AppSettings`

```kotlin
data class AppSettings(
    val currentLedgerId: String,
    val lastAccountIdByLedger: Map<String, String> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
```

配置只保存可重建的界面偏好，不保存交易数据。

### 6.2 `SettingsStore`

```kotlin
suspend fun readOrCreate(defaultLedgerId: String): AppSettings

suspend fun update(
    transform: (AppSettings) -> AppSettings,
): AppSettings
```

#### `readOrCreate`

读取配置文件。文件不存在、损坏或内容不可解析时，以 `defaultLedgerId` 创建新配置。

#### `update`

在进程内互斥更新配置，并通过 Android `AtomicFile` 原子替换文件。返回更新后的完整配置。

切换当前账本：

```kotlin
val settings = settingsStore.update { current ->
    current.copy(currentLedgerId = targetLedgerId)
}
```

记录某账本最近使用的账户：

```kotlin
settingsStore.update { current ->
    current.copy(
        lastAccountIdByLedger = current.lastAccountIdByLedger +
            (ledgerId to accountId),
    )
}
```

更新主题：

```kotlin
settingsStore.update { current ->
    current.copy(themeMode = ThemeMode.DARK)
}
```

注意：

- `SettingsStore` 当前不直接提供配置 `Flow`。
- `BackendInitializer.state` 中的 `Ready.settings` 是初始化完成时的快照。
- 调用 `SettingsStore.update` 后，App 级 ViewModel 应同步更新自己的当前账本或主题状态。
- Repository 不会自动读取 `currentLedgerId`，调用查询时仍需传入目标账本 ID。

## 7. 账本接口

### 7.1 `Ledger`

```kotlin
data class Ledger(
    val id: String,
    val name: String,
    val currencyCode: String = "CNY",
    val createdAt: Long,
    val isArchived: Boolean = false,
)
```

### 7.2 `LedgerRepository`

```kotlin
interface LedgerRepository {
    fun observeLedgers(): Flow<List<Ledger>>
    suspend fun createLedger(name: String): String
    suspend fun updateLedger(ledger: Ledger)
    suspend fun archiveLedger(ledgerId: String)
}
```

#### `observeLedgers`

观察全部账本，包括已归档账本。

返回顺序：

```text
未归档账本 -> 已归档账本
同组内按 createdAt 升序
```

#### `createLedger`

```kotlin
suspend fun createLedger(name: String): String
```

行为：

- 去除名称首尾空格。
- 空名称抛出 `InvalidName`。
- 自动生成账本 UUID。
- 币种固定为 `CNY`。
- 在同一事务中创建预置账户和分类。
- 返回新账本 ID。
- 不会自动修改 `SettingsStore.currentLedgerId`。

示例：

```kotlin
val ledgerId = ledgerRepository.createLedger("家庭账本")
settingsStore.update { it.copy(currentLedgerId = ledgerId) }
```

#### `updateLedger`

```kotlin
suspend fun updateLedger(ledger: Ledger)
```

用于修改账本名称或归档状态。后端保留数据库中的 `createdAt` 和 `currencyCode`，调用方传入的这两个字段不会覆盖原值。

账本不存在时抛出 `LedgerNotFound`。

#### `archiveLedger`

```kotlin
suspend fun archiveLedger(ledgerId: String)
```

将 `isArchived` 更新为 `true`，不会删除账本、账户、分类或流水。

接口不会自动修改当前账本配置。归档当前账本前，前端应先选择另一个未归档账本并更新 `SettingsStore`。

## 8. 账户接口

### 8.1 `Account`

```kotlin
data class Account(
    val id: String,
    val ledgerId: String,
    val name: String,
    val type: AccountType,
    val initialBalanceMinor: Long = 0,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
```

新增账户时：

- 调用方生成 `id`。
- `ledgerId` 必须指向存在且未归档的账本。
- 后端以当前时间写入 `createdAt` 和 `updatedAt`，调用方传入值不作为最终值。

编辑账户时：

- 使用相同 `id` 调用 `saveAccount`。
- 不允许把已有账户移动到其他账本。
- 后端保留原 `createdAt` 并刷新 `updatedAt`。

### 8.2 `AccountBalance`

```kotlin
data class AccountBalance(
    val account: Account,
    val balanceMinor: Long,
)
```

余额计算规则：

```text
初始余额
+ 收入
- 支出
- 转出
+ 转入
```

### 8.3 `AccountRepository`

```kotlin
interface AccountRepository {
    fun observeAccounts(ledgerId: String): Flow<List<Account>>
    fun observeAccountBalances(ledgerId: String): Flow<List<AccountBalance>>
    suspend fun saveAccount(account: Account)
    suspend fun archiveAccount(accountId: String)
}
```

#### `observeAccounts`

观察指定账本的全部账户，包括已停用账户。

#### `observeAccountBalances`

观察指定账本的全部账户及当前余额：

- 不受统计日期范围限制。
- 包含没有流水的账户。
- 包含已停用账户。
- 流水编辑或删除后自动重新计算。

#### `saveAccount`

新增示例：

```kotlin
val now = System.currentTimeMillis()
accountRepository.saveAccount(
    Account(
        id = UUID.randomUUID().toString(),
        ledgerId = ledgerId,
        name = "招商银行",
        type = AccountType.BANK_CARD,
        initialBalanceMinor = 100_000,
        sortOrder = 10,
        createdAt = now,
        updatedAt = now,
    ),
)
```

后端校验：

- 名称不能为空。
- 账本必须存在且未归档。
- 编辑时账户不能移动到其他账本。

#### `archiveAccount`

将账户标记为停用，不删除历史流水。已停用账户不能再用于新建或编辑流水。

## 9. 分类接口

### 9.1 `Category`

```kotlin
data class Category(
    val id: String,
    val ledgerId: String,
    val name: String,
    val type: CategoryType,
    val iconKey: String,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
```

新增分类的 ID 由调用方生成。后端负责最终的创建和更新时间。

### 9.2 `CategoryRepository`

```kotlin
interface CategoryRepository {
    fun observeCategories(
        ledgerId: String,
        type: CategoryType,
    ): Flow<List<Category>>

    suspend fun saveCategory(category: Category)
    suspend fun archiveCategory(categoryId: String)
}
```

#### `observeCategories`

按账本和分类类型观察分类，结果包含已停用分类。

#### `saveCategory`

后端校验：

- 名称不能为空。
- `iconKey` 不能为空。
- 账本必须存在且未归档。
- 编辑时分类不能移动到其他账本。
- 分类创建后不能修改 `type`。

新增示例：

```kotlin
val now = System.currentTimeMillis()
categoryRepository.saveCategory(
    Category(
        id = UUID.randomUUID().toString(),
        ledgerId = ledgerId,
        name = "宠物",
        type = CategoryType.EXPENSE,
        iconKey = "pets",
        sortOrder = 20,
        createdAt = now,
        updatedAt = now,
    ),
)
```

#### `archiveCategory`

将分类标记为停用，不删除历史流水。已停用分类仍会显示在历史明细和分类统计中。

## 10. 流水接口

### 10.1 `Transaction`

```kotlin
data class Transaction(
    val id: String,
    val ledgerId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val transactionAt: Long,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
```

### 10.2 `TransactionDetail`

```kotlin
data class TransactionDetail(
    val transaction: Transaction,
    val accountName: String,
    val toAccountName: String?,
    val categoryName: String?,
    val categoryIconKey: String?,
)
```

该模型用于明细展示，已经包含关联对象的显示名称。即使账户或分类已停用，历史明细仍可正常显示。

### 10.3 `SaveTransactionCommand`

```kotlin
data class SaveTransactionCommand(
    val transactionId: String? = null,
    val ledgerId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val transactionAt: Long,
    val note: String? = null,
)
```

字段规则：

| 流水类型 | `accountId` | `toAccountId` | `categoryId` |
|---|---|---|---|
| `EXPENSE` | 必填 | 必须为 `null` | 必填且为支出分类 |
| `INCOME` | 必填 | 必须为 `null` | 必填且为收入分类 |
| `TRANSFER` | 必填，表示转出账户 | 必填，表示转入账户 | 必须为 `null` |

新增与编辑规则：

| `transactionId` | 行为 |
|---|---|
| `null` | 新增流水，后端生成 UUID |
| 非空 | 编辑对应流水；不存在时失败 |

编辑流水时不能改变其所属账本。

### 10.4 `TransactionFilter`

```kotlin
data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val range: DateTimeRange? = null,
    val keyword: String = "",
    val limit: Int? = null,
)
```

筛选规则：

- 多个非空条件之间使用 AND。
- `type = null` 表示不限流水类型。
- `categoryId = null` 表示不限分类。
- `accountId` 同时匹配普通流水账户、转账的转出账户和转入账户。
- `range = null` 表示不限日期。
- `keyword` 仅搜索备注，忽略英文字母大小写。
- `%` 和 `_` 按普通字符处理，不作为 SQL 通配符。
- `limit = null` 表示不主动限制数量。
- `limit` 非空时必须大于 0。

### 10.5 `TransactionRepository`

```kotlin
interface TransactionRepository {
    fun observeTransactions(
        ledgerId: String,
        filter: TransactionFilter = TransactionFilter(),
    ): Flow<List<TransactionDetail>>

    suspend fun saveTransaction(command: SaveTransactionCommand): String

    fun observeTransaction(transactionId: String): Flow<TransactionDetail?>

    suspend fun deleteTransaction(ledgerId: String, transactionId: String)
}
```

#### `observeTransactions`

观察指定账本中符合条件的流水，按交易时间倒序返回。

示例：查询某月的某个支出分类：

```kotlin
val range = dateRangeResolver.resolve(
    StatisticsPeriod.Month(YearMonth.of(2026, 7)),
)

val flow = transactionRepository.observeTransactions(
    ledgerId = ledgerId,
    filter = TransactionFilter(
        type = TransactionType.EXPENSE,
        categoryId = categoryId,
        range = range,
        keyword = "午餐",
    ),
)
```

查询首页最近 10 条流水：

```kotlin
transactionRepository.observeTransactions(
    ledgerId = ledgerId,
    filter = TransactionFilter(limit = 10),
)
```

#### `observeTransaction`

按全局 ID 观察单笔流水详情，用于编辑页加载已有流水。不带账本作用域，流水不存在时发射 `null`。

#### `saveTransaction`

该接口同时支持新增和编辑，并在 Room 事务中执行完整校验和写入。

后端校验：

- 金额必须大于 0。
- 账本必须存在且未归档。
- 所有关联账户必须存在、未停用并属于该账本。
- 分类必须存在、未停用并属于该账本。
- 收支分类类型必须与流水类型一致。
- 转账双方账户不能相同。
- 收支和转账字段形态必须符合上表。
- 编辑时原流水必须存在且不能移动到其他账本。

成功后返回流水 ID。

建议前端通过对应 Use Case 保存，不直接调用该写入接口。

#### `deleteTransaction`

按账本 ID 和流水 ID 硬删除流水：

- SQL 为 `DELETE FROM transactions WHERE id = :id AND ledger_id = :ledgerId`。
- 流水不存在，或不属于指定账本时，抛出 `TransactionNotFound`，不区分这两种情况。
- 删除不可撤销。
- 删除后明细、余额和统计 `Flow` 自动刷新。

## 11. 流水 Use Case

### 11.1 `SaveIncomeExpenseUseCase`

```kotlin
suspend operator fun invoke(
    command: SaveTransactionCommand,
): String
```

只接受 `EXPENSE` 或 `INCOME`。传入 `TRANSFER` 时抛出 `InvalidTransactionType`。

保存支出示例：

```kotlin
val transactionId = saveIncomeExpenseUseCase(
    SaveTransactionCommand(
        ledgerId = ledgerId,
        type = TransactionType.EXPENSE,
        amountMinor = MoneyParser.yuanToMinor("35.80"),
        accountId = accountId,
        categoryId = categoryId,
        transactionAt = System.currentTimeMillis(),
        note = "午餐",
    ),
)
```

保存收入示例：

```kotlin
saveIncomeExpenseUseCase(
    SaveTransactionCommand(
        ledgerId = ledgerId,
        type = TransactionType.INCOME,
        amountMinor = 800_000,
        accountId = bankAccountId,
        categoryId = salaryCategoryId,
        transactionAt = System.currentTimeMillis(),
    ),
)
```

### 11.2 `SaveTransferUseCase`

```kotlin
suspend operator fun invoke(
    command: SaveTransactionCommand,
): String
```

只接受 `TRANSFER`。转账只保存一条流水。

```kotlin
saveTransferUseCase(
    SaveTransactionCommand(
        ledgerId = ledgerId,
        type = TransactionType.TRANSFER,
        amountMinor = 50_000,
        accountId = bankAccountId,
        toAccountId = walletAccountId,
        categoryId = null,
        transactionAt = System.currentTimeMillis(),
        note = "转入电子钱包",
    ),
)
```

### 11.3 `DeleteTransactionUseCase`

```kotlin
suspend operator fun invoke(ledgerId: String, transactionId: String)
```

UI 应在调用前显示二次确认对话框。流水不属于 `ledgerId` 时按不存在处理。

```kotlin
deleteTransactionUseCase(ledgerId, transactionId)
```

## 12. 日期范围接口

### 12.1 `StatisticsPeriod`

```kotlin
sealed interface StatisticsPeriod {
    data class Day(val date: LocalDate) : StatisticsPeriod
    data class Month(val yearMonth: YearMonth) : StatisticsPeriod
    data class Year(val year: Int) : StatisticsPeriod
    data class Custom(
        val startDate: LocalDate,
        val endDate: LocalDate,
    ) : StatisticsPeriod
}
```

自定义日期的 `startDate` 和 `endDate` 均为包含边界。`DateRangeResolver` 会把结束日期转换为次日零点的排他边界。

### 12.2 `DateTimeRange`

```kotlin
data class DateTimeRange(
    val startInclusive: Long,
    val endExclusive: Long,
)
```

`startInclusive` 必须小于 `endExclusive`，否则构造时抛出 `IllegalArgumentException`。

### 12.3 `DateRangeResolver`

```kotlin
fun resolve(period: StatisticsPeriod): DateTimeRange
fun year(year: Int): DateTimeRange
fun zoneId(): ZoneId
```

所有换算都使用调用时的设备默认时区。

时区语义说明：归日、归月使用查询时刻的设备默认时区，属有意的显示语义。设备时区变化后，同一笔历史流水可能被归入不同的本地日期或月份，历史统计结果随之变化，该行为为预期行为（跨时区回归测试待补充）。

示例：

```kotlin
val dayRange = dateRangeResolver.resolve(
    StatisticsPeriod.Day(LocalDate.of(2026, 7, 21)),
)

val monthRange = dateRangeResolver.resolve(
    StatisticsPeriod.Month(YearMonth.of(2026, 7)),
)

val yearRange = dateRangeResolver.resolve(
    StatisticsPeriod.Year(2026),
)

val customRange = dateRangeResolver.resolve(
    StatisticsPeriod.Custom(
        startDate = LocalDate.of(2026, 7, 1),
        endDate = LocalDate.of(2026, 7, 21),
    ),
)
```

自定义范围结束日期早于开始日期时抛出 `LedgerException.InvalidDateRange`。

## 13. 统计接口

### 13.1 `IncomeExpenseSummary`

```kotlin
data class IncomeExpenseSummary(
    val incomeMinor: Long,
    val expenseMinor: Long,
) {
    val balanceMinor: Long
}
```

```text
balanceMinor = incomeMinor - expenseMinor
```

转账不进入以上三个字段。

### 13.2 `CategoryTotal`

```kotlin
data class CategoryTotal(
    val categoryId: String,
    val categoryName: String,
    val iconKey: String,
    val amountMinor: Long,
)
```

仅用于支出分类统计，按金额降序返回。

### 13.3 `DailyTotal`

```kotlin
data class DailyTotal(
    val date: LocalDate,
    val incomeMinor: Long,
    val expenseMinor: Long,
)
```

日趋势只返回范围内实际有收入或支出流水的日期，不自动补齐空白日期。

### 13.4 `MonthlyTotal`

```kotlin
data class MonthlyTotal(
    val yearMonth: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
)
```

年度月趋势固定返回 12 项；没有流水的月份金额为 0。

### 13.5 `StatisticsRepository`

```kotlin
interface StatisticsRepository {
    fun observeSummary(
        ledgerId: String,
        range: DateTimeRange,
    ): Flow<IncomeExpenseSummary>

    fun observeExpenseCategories(
        ledgerId: String,
        range: DateTimeRange,
    ): Flow<List<CategoryTotal>>

    fun observeDailyTrend(
        ledgerId: String,
        range: DateTimeRange,
    ): Flow<List<DailyTotal>>

    fun observeMonthlyTrend(
        ledgerId: String,
        year: Int,
    ): Flow<List<MonthlyTotal>>
}
```

#### `observeSummary`

观察时间范围内的总收入和总支出。转账被排除。

#### `observeExpenseCategories`

观察时间范围内的支出分类排行。收入和转账被排除。

#### `observeDailyTrend`

按设备本地日期聚合范围内的收入和支出。聚合在 Kotlin 中完成，以避免 SQLite 按 UTC 日期分组造成时区错误。

#### `observeMonthlyTrend`

按设备本地月份聚合指定年份的收入和支出，固定返回 1 月至 12 月。

月度统计组合示例：

```kotlin
val range = dateRangeResolver.resolve(
    StatisticsPeriod.Month(YearMonth.now()),
)

val statistics = combine(
    statisticsRepository.observeSummary(ledgerId, range),
    statisticsRepository.observeExpenseCategories(ledgerId, range),
    statisticsRepository.observeDailyTrend(ledgerId, range),
) { summary, categories, trend ->
    StatisticsUiState(
        incomeMinor = summary.incomeMinor,
        expenseMinor = summary.expenseMinor,
        balanceMinor = summary.balanceMinor,
        categories = categories,
        dailyTrend = trend,
    )
}
```

## 14. 错误接口

所有可展示的领域写入错误继承自：

```kotlin
sealed class LedgerException : Exception
```

| 错误类型 | 默认消息 | 常见来源 |
|---|---|---|
| `InvalidName` | 名称不能为空 | 新建或编辑账本、账户、分类 |
| `InvalidAmount` | 金额必须大于 0 且不超过支持范围 | 金额解析、保存流水 |
| `InvalidTransactionType` | 流水类型与账户或分类字段不匹配 | 流水命令字段组合错误 |
| `SameTransferAccount` | 转出账户和转入账户不能相同 | 保存转账 |
| `InvalidDateRange` | 结束日期不能早于开始日期 | 自定义统计范围 |
| `LedgerNotFound` | 账本不存在 | 账本或关联对象操作 |
| `LedgerArchived` | 账本已归档 | 向已归档账本写入 |
| `AccountNotFound` | 账户不存在 | 保存流水、停用账户 |
| `AccountArchived` | 账户已停用 | 保存流水 |
| `AccountLedgerMismatch` | 账户不属于当前账本 | 保存账户或流水 |
| `CategoryNotFound` | 分类不存在 | 保存流水、停用分类 |
| `CategoryArchived` | 分类已停用 | 保存流水 |
| `CategoryLedgerMismatch` | 分类不属于当前账本 | 保存分类或流水 |
| `CategoryTypeMismatch` | 分类类型与流水类型不一致 | 保存分类或流水 |
| `TransactionNotFound` | 流水不存在 | 编辑或删除流水 |
| `TransactionLedgerMismatch` | 不能将流水移动到其他账本 | 编辑流水 |
| `ArithmeticOverflow` | 金额累计超出支持范围 | 趋势聚合或结余计算 |
| `LocalStorage` | 本地数据存储不可用 | 目录、数据库或配置文件失败 |

ViewModel 处理示例：

```kotlin
viewModelScope.launch {
    try {
        saveIncomeExpenseUseCase(command)
        events.send(UiEvent.SaveSucceeded)
    } catch (error: LedgerException) {
        events.send(
            UiEvent.ShowMessage(
                error.message ?: "本地操作失败",
            ),
        )
    }
}
```

`DateTimeRange` 构造失败和非法 `TransactionFilter.limit` 使用 `IllegalArgumentException`，调用方应在构造筛选条件前完成基本 UI 校验。

查询 `Flow` 在本地读取失败时会以异常结束收集。页面级状态流建议使用 `catch` 转换为错误状态：

```kotlin
repository.observeTransactions(ledgerId)
    .map<List<TransactionDetail>, DetailUiState> { rows ->
        DetailUiState.Content(rows)
    }
    .catch { error ->
        emit(DetailUiState.Error(error.message ?: "读取本地数据失败"))
    }
```

## 15. 推荐的前端调用边界

| 前端功能 | 推荐调用接口 |
|---|---|
| 应用启动 | `BackendInitializer.state` |
| 当前账本切换 | `SettingsStore.update` + App ViewModel 状态 |
| 账本管理 | `LedgerRepository` |
| 账户管理与余额 | `AccountRepository` |
| 分类管理 | `CategoryRepository` |
| 明细和检索 | `TransactionRepository.observeTransactions` |
| 新增/编辑收支 | `SaveIncomeExpenseUseCase` |
| 新增/编辑转账 | `SaveTransferUseCase` |
| 删除流水 | `DeleteTransactionUseCase` |
| 日期范围换算 | `DateRangeResolver` |
| 汇总和图表 | `StatisticsRepository` |
| 金额文本输入 | `MoneyParser` |

## 16. 重要业务边界

1. 所有 Repository 查询都要求明确传入 `ledgerId`，不会隐式读取当前账本。
2. 不同账本的账户、分类和流水不能交叉关联。
3. 收入和支出必须选择类型一致的分类。
4. 转账必须选择两个不同账户，且只保存一条流水。
5. 转账不进入收入、支出或分类统计。
6. 账户余额不永久存储，每次根据初始余额和流水计算。
7. 账户和分类只支持停用，不支持删除。
8. 流水删除是硬删除，不可恢复。（计划变更：删除将要求同时传入账本 ID，跨账本删除按流水不存在处理。）
9. 已停用账户和分类保留历史展示，但不能用于新流水或编辑流水。
10. 归档账本不会自动切换当前账本，前端必须更新配置状态。
11. 流水修改和删除后，明细、统计及余额通过 Room `Flow` 自动刷新。
12. 数据只保存在当前安装 App 的专属目录；卸载、清除数据或恢复出厂设置会删除账本。

## 17. 本地存储位置

运行时目录：

```text
/storage/emulated/0/Android/data/com.example.localledger/files/
  data/
    ledger.db
    ledger.db-wal
    ledger.db-shm
  config/
    settings.json
```

App 申请 `INTERNET`，只用于刷新汇率。不申请以下权限：

```text
READ_EXTERNAL_STORAGE
WRITE_EXTERNAL_STORAGE
MANAGE_EXTERNAL_STORAGE
```

云备份和设备迁移均明确排除业务数据。

## 18. 源码位置

| 内容 | 文件 |
|---|---|
| Repository 接口 | `app/src/main/java/com/example/localledger/domain/repository/Repositories.kt` |
| 领域模型 | `app/src/main/java/com/example/localledger/domain/model/Models.kt` |
| 流水 Use Case | `app/src/main/java/com/example/localledger/domain/usecase/TransactionUseCases.kt` |
| 领域错误 | `app/src/main/java/com/example/localledger/domain/error/LedgerException.kt` |
| 日期范围 | `app/src/main/java/com/example/localledger/domain/time/DateRangeResolver.kt` |
| 金额解析 | `app/src/main/java/com/example/localledger/core/money/MoneyParser.kt` |
| 初始化状态 | `app/src/main/java/com/example/localledger/core/storage/BackendInitializer.kt` |
| 配置存储 | `app/src/main/java/com/example/localledger/core/storage/SettingsStore.kt` |
| Hilt 绑定 | `app/src/main/java/com/example/localledger/di/BackendModule.kt` |
