# 安卓本地记账 App 前端设计文档

## 1. 文档范围

本文定义完全离线 Android 记账 App 的前端实现方案。产品仅包含账本管理、收支记录、分类与账户、明细与检索、统计五项 MVP 功能。

前端不处理账号登录、网络请求、云同步或在线接口。所有界面数据来自本地 Repository 暴露的 `Flow`。

## 2. 技术选型

```text
Kotlin
Jetpack Compose + Material 3
Navigation Compose
ViewModel
Kotlin Coroutines + Flow
Hilt
```

图表建议使用 Compose Canvas 实现简单的分类占比图和趋势图，避免在 MVP 中引入较重的图表依赖。若后续图表交互变复杂，再评估 Vico。

## 3. 前端架构

采用单向数据流和按功能划分的 MVVM：

```text
Composable UI
  -> 用户事件 UiEvent
ViewModel
  -> UseCase / Repository
Repository
  -> Room 本地数据库
Flow<UiState>
  -> Composable UI
```

每个页面拥有独立的 `UiState`、`UiEvent` 和 `ViewModel`。页面只渲染状态并派发事件，不直接访问 DAO 或执行数据库查询。

建议目录：

```text
ui/
  navigation/
  theme/
  component/
  home/
  transaction/
  ledger/
  account/
  category/
  detail/
  statistics/
  settings/
```

## 4. 导航与页面结构

应用使用五项底部导航：

```text
首页 | 明细 | 记一笔 | 统计 | 设置
```

`记一笔` 是明确的主操作，可使用中间凸出的图标按钮；其他四项使用带图标和文字的普通底部导航项。

导航路由建议：

```text
home
detail?filter=<serialized-filter>
transaction/new?type=EXPENSE
transaction/{transactionId}
statistics
ledger/manage
account/manage?ledgerId={ledgerId}
account/edit?accountId={accountId}
category/manage?type=EXPENSE
category/edit?categoryId={categoryId}
settings
```

不要将完整实体对象放入路由参数。编辑页仅传递 ID，再由 ViewModel 从本地数据库读取。

## 5. 全局状态与交互规范

### 5.1 全局状态

`AppViewModel` 维护当前账本 ID：

```kotlin
data class AppUiState(
    val currentLedgerId: String?,
    val currentLedgerName: String,
    val isReady: Boolean
)
```

当前账本变化后，首页、明细、记一笔和统计页均订阅新账本的 `Flow` 并自动刷新。

### 5.2 通用状态

每个页面都应处理：

- `Loading`：首次读取数据库时使用页面级骨架或进度指示。
- `Content`：正常显示数据。
- `Empty`：显示简短空状态和直接操作入口。
- `Error`：显示本地操作失败原因，并提供重试。

保存、删除等一次性反馈使用 `Snackbar`。硬删除必须先弹出确认对话框，明确说明“删除后无法恢复”。

### 5.3 格式化

- 金额由统一的 `MoneyFormatter` 根据 `amountMinor: Long` 格式化为 `¥12.50`。
- UI 层禁止自行通过 `Double` 计算或格式化金额。
- 日期和时间由统一的 `DateTimeFormatter` 按中文本地化格式显示。
- 数据库存储时间戳，界面按设备时区显示。

## 6. 页面设计

### 6.1 首页

目的：快速看见当前账本的月度概况，并尽快开始记账。

页面内容：

- 顶部：当前账本名称，可点击切换账本。
- 月份选择：上一月、当前月、下一月图标按钮，居中显示年月。
- 收支摘要：收入、支出、结余三项紧凑展示。
- 账户余额摘要：显示账户名和当前余额，点击进入账户管理。
- 最近流水：按时间倒序显示最近 5 至 10 笔。
- 浮动“记一笔”按钮：进入支出记账页。

首页 ViewModel 的核心状态：

```kotlin
data class HomeUiState(
    val ledgerName: String = "",
    val yearMonth: YearMonth = YearMonth.now(),
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val balanceMinor: Long = 0,
    val accountBalances: List<AccountBalanceUi> = emptyList(),
    val recentTransactions: List<TransactionRowUi> = emptyList(),
    val isLoading: Boolean = true
)
```

### 6.2 明细页

目的：核对、查找、编辑和删除流水。

页面内容：

- 顶部搜索图标，点击展开关键词输入框。
- 筛选图标，打开底部筛选面板。
- 按本地日期倒序分组的流水列表。
- 每项显示分类图标、名称、备注、账户、时间、金额。
- 收入用正数样式，支出用负数样式，转账使用中性样式。
- 点击流水进入编辑页。
- 长按或滑动显示删除操作；执行前弹出硬删除确认框。

筛选条件：

```kotlin
data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val keyword: String = ""
)
```

筛选变更后立即更新列表。用户可一键清空全部筛选条件。

### 6.3 记一笔与编辑流水

新增和编辑共用同一页面。顶部标题分别为“记一笔”和“编辑记录”。

布局顺序：

1. 支出、收入、转账分段控件。
2. 分类选择网格。转账时隐藏分类。
3. 金额显示区和固定高度的数字键盘。
4. 账户选择。转账时显示转出账户和转入账户。
5. 日期时间选择。
6. 备注输入。
7. 保存按钮。

交互规则：

- 默认类型为支出。
- 新建时默认账户为最近一次使用的可用账户；没有历史记录时使用第一个可用账户。
- 新建时默认日期为当前时刻。
- 转账时必须选择两个不同账户，否则禁止保存并显示字段错误。
- 金额必须大于 0；未选择分类、账户时禁止保存。
- 保存成功后返回上一页，并由 `Flow` 自动刷新相关页面。
- 编辑页提供删除图标，点击后使用硬删除确认对话框。

### 6.4 统计页

目的：按用户选定时间范围查看收入、支出和结余，并可下钻核对流水。

时间选择控件采用菜单或底部弹窗，提供：

```text
指定日期
指定月份
指定年份
自定义时间段
```

默认显示当前月，即“指定月份”。用户选择后，顶部以清晰文本展示当前范围，例如：

```text
2026年7月
2026年
2026年7月21日
2026年7月1日 至 2026年7月21日
```

页面内容：

- 收入、支出、结余摘要。
- 支出分类占比图和分类排行。
- 分类项可点击，跳转明细页，并附带当前时间范围和分类筛选。
- 指定月份时显示按日收支趋势图。
- 指定年份时显示按月收支趋势图。
- 指定日期和自定义时间段可显示按日趋势；若只有一天，显示汇总即可。
- 各账户当前余额摘要。账户余额为当前余额，不受统计时间范围限制，标题需标明“当前账户余额”。

时间范围选择规则：

- 指定日期：打开日期选择器。
- 指定月份：打开年/月选择器。
- 指定年份：打开年份选择器。
- 自定义时间段：依次选择开始和结束日期；结束日不得早于开始日。

### 6.5 设置页

设置页包含：

- 账本管理入口。
- 账户管理入口。
- 支出分类管理入口。
- 收入分类管理入口。
- 本地数据说明：无网络上传，数据位于本机 App 专属目录，卸载 App 会删除数据。

### 6.6 账本、账户和分类管理页

三类管理页均采用列表加右下角新增按钮。

- 账本：新建、切换、改名、归档。
- 账户：新建、编辑名称、账户类型、初始余额、排序、停用。
- 分类：按收入或支出类型独立展示；支持新建、改名、图标、排序、停用。

被停用的账户和分类保留在“已停用”折叠区域，不能在新建流水中选择。

## 7. 组件设计

建议抽取并复用以下 Compose 组件：

```text
AmountText
AmountKeypad
TransactionTypeSelector
CategoryGrid
AccountSelector
DateRangeSelector
TransactionListItem
TransactionDateGroup
SummaryAmountRow
AccountBalanceList
ExpenseCategoryChart
IncomeExpenseTrendChart
ConfirmDeleteDialog
EmptyState
```

组件保持无业务依赖，数据和回调由页面传入。

## 8. 可访问性与适配

- 所有图标按钮必须有 `contentDescription` 和悬浮提示。
- 金额、类型和图表不能只依赖颜色传达信息，必须有文字或图例。
- 支持深色模式，避免收入、支出仅因主题变化而失去区分。
- 支持系统字体放大，列表行和按钮可换行或扩展高度，不允许文字截断关键金额。
- 目标最低适配宽度为 320dp；平板可使用双栏，左侧显示列表、右侧显示编辑或统计详情。

## 9. 前端测试

### 单元测试

- `MoneyFormatter` 对分、负数、零和大金额的格式化。
- 时间范围转换和显示格式。
- ViewModel 的保存校验、筛选条件、错误状态和加载状态。

### Compose UI 测试

- 新建支出、收入和转账的必填校验。
- 转账双方账户相同时禁止保存。
- 筛选和关键词搜索更新明细列表。
- 删除确认后流水从列表消失。
- 统计选择日期、月份、年份和自定义区间后，标题和汇总更新。
- 点击统计分类后，跳转到正确筛选条件的明细页。

## 10. 前端验收标准

- 全部页面不依赖网络即可正常打开和操作。
- 当前账本切换后，首页、明细、记一笔和统计立即切换到对应数据。
- 记账操作可在少量步骤内完成，金额、账户、分类和日期校验明确。
- 删除必须二次确认，确认后不提供撤销入口。
- 统计可选择指定日期、月份、年份和自定义日期区间。
- 在不同屏幕尺寸、深色模式和字体放大下，金额与主要操作均清晰可见。
