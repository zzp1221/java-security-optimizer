# Governance 模块详解：规则不只是“运行”，还要“治理”

对应目录：`src/main/java/com/project/javasecurityoptimizer/governance`

这个模块解决的问题是：  
**不同项目/工作区如何按策略启用规则、管理参数、累计分析指标、处理误报反馈。**

---

## 1. 这个模块在系统中的位置

分析引擎负责“发现问题”，治理模块负责“决定怎么跑、怎么长期优化规则效果”。

你可以把它看成“策略中台”的雏形。

---

## 2. 核心类关系

- `RuleGovernanceService`：治理入口服务
- `ProjectRulePolicy`：某 workspace 的策略快照
- `ProjectRulePolicyRepository`：策略仓储接口
- `InMemoryProjectRulePolicyRepository`：内存实现
- `RuleParameterSchema`：参数定义与清洗
- `RuleTemplateType`：模板类型（如可靠性/性能/可维护性）
- `RuleGovernanceDashboard`：指标视图对象
- `FalsePositiveFeedback`：误报反馈模型

---

## 3. 模板规则集 + 项目策略叠加

`resolveExecutableRuleSet` 的逻辑非常关键：

1. 找到 workspace 对应策略（没有就按 fallbackTemplate 生成默认策略）
2. 先拿模板规则集
3. 加上“手动启用”规则
4. 再去掉“手动禁用”规则
5. 输出最终可执行规则集

这是一个“基线模板 + 差异覆盖”的经典策略模型。

知识点：
- `LinkedHashSet` 既去重又保持可预测顺序，适合规则集合。

---

## 4. 参数治理：RuleParameterSchema 的价值

保存策略时，`saveProjectPolicy` 会先调用 `parameterSchema.sanitize(...)`：

- 清洗非法参数
- 补齐默认值
- 约束范围

为什么重要：

- 规则参数往往来自外部输入，不可信
- 不做 sanitize 可能导致规则行为失控

知识点：
- 参数校验应该靠“schema + sanitize”统一管理，而不是散落在每个调用点。

---

## 5. 指标累计与仪表盘

`RuleGovernanceService.recordAnalysisMetrics` 会把 `RuleExecutionMetrics` 按 workspace 累加到 DashboardAccumulator。

累加维度：

- rule 命中次数
- rule 执行耗时
- rule 误报次数

输出 `dashboard(workspaceId)` 即可得到治理看板数据。

知识点：
- 治理要看趋势，不是看单次分析结果。

---

## 6. 误报反馈闭环

流程：

1. 用户标记某条 issue 为误报（`markFalsePositive`）
2. 反馈写入 `feedbackByWorkspace`
3. 仪表盘 false-positive 计数同步增加

这给后续规则调优提供数据基础。

---

## 7. 并发与数据结构选择

本模块主要用：

- `ConcurrentHashMap`：workspace 维度数据隔离存储
- `DashboardAccumulator` 内部 `synchronized`：保证指标累加原子性
- `EnumMap`：模板类型到规则集映射，语义清晰、性能稳定

知识点：
- map 级并发 + 对象级同步，是常见的分层并发控制手法。

---

## 8. 仓储抽象（Repository Pattern）

为什么 `ProjectRulePolicyRepository` 要抽象接口：

- 当前可用内存实现快速迭代
- 后续可切换 DB 实现，不影响服务层业务

这是“可演进架构”的典型做法。

---

## 9. 与其他模块的协同点

- 与 `analysis`：治理模块给分析引擎提供规则集和参数基线
- 与 `task`：可按 workspace 维度组合调度与治理数据
- 与 `security`：误报/策略变更操作可接入审计（后续可增强）

---

## 10. 设计优点与现实限制

### 优点

- 策略计算逻辑清晰（模板 + 覆盖）
- 指标聚合轻量，易扩展
- 仓储接口提前解耦

### 限制

- 当前是内存存储，重启丢数据
- 缺少策略变更历史与审批流程
- 误报反馈未与具体 issue 实例建立更强关联

---

## 11. 你可以做的增强

1. 增加持久化仓储（MySQL/PostgreSQL）并保留接口不变。  
2. 新增策略版本号与回滚能力。  
3. 增加“规则成本评分”（命中收益 vs 执行耗时）。  
4. 对误报反馈增加“复核状态”（待确认/已确认/驳回）。  

---

## 12. 动手练习

1. 新增模板类型 `SECURITY_STRICT`，定义一组高优先级规则。  
2. 在 dashboard 中加入“平均单次规则耗时”。  
3. 编写测试：验证禁用规则优先级高于模板默认启用。  
4. 增加策略导入导出（JSON），并做 schema 校验。  
