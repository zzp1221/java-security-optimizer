# Analysis 源码精读（第二版）

主目录：`src/main/java/com/project/javasecurityoptimizer/analysis`

这篇不再泛讲概念，而是带你按真实代码顺序理解：  
**一个分析请求是怎样从输入参数走到最终报告的。**

---

## 1. 阅读入口与最短调用链

先从 `JavaAnalysisEngine` 看：

1. `analyze(request)`  
2. `analyzeWithMetrics(request)`  
3. `indexFiles(...)`  
4. `parseFiles(...)`  
5. `runRules(...)`  
6. 聚合 `AnalyzeTaskResult`

建议你在 IDE 里边看边折叠方法，只保留当前调用路径。

---

## 2. analyzeWithMetrics：主编排器

这个方法是整个 analysis 模块的“总指挥”。

### 2.1 初始化阶段

- 记录开始时间 `Instant start`
- 初始化 `ExecutionContext`（缓存命中计数、降级文件、失败项）
- 初始化 `events`（用于前端进度展示）

这里的设计意图是：结果不仅要有 issue，还要有完整执行轨迹。

### 2.2 三段流水线

- `indexFiles`：决定“分析哪些文件”
- `parseFiles`：把文件变成 AST
- `runRules`：把 AST 交给规则并发执行

每段结束都追加阶段事件，形成可观测进度流。

### 2.3 结果聚合

- `AnalysisStats`：数量型统计
- `RuleExecutionMetrics`：规则命中与耗时
- `AnalysisExecutionReport`：缓存/降级/失败细节

你可以把这三类对象理解成：业务结果、性能结果、运维结果。

---

## 3. indexFiles：全量与增量的分叉点

### FULL 模式

- `Files.walk(projectPath)` 遍历所有文件
- 只保留 `.java`
- 超过 `maxFileSizeBytes` 的文件跳过
- 超过 `degradeFileSizeBytes` 的文件标记降级

### INCREMENTAL 模式

- 用 `changedFiles + impactedFiles` 作为候选
- `normalizePath` 把相对路径补齐为绝对路径
- 仅处理存在的 Java 文件

这段代码的关键是把“分析范围控制”前置，避免无意义计算。

---

## 4. parseFiles：并发解析 + 重试 + AST 缓存

### 4.1 任务提交

- 创建固定线程池：`Executors.newFixedThreadPool(parseConcurrency)`
- 每个文件提交 `StaticJavaParser.parse(path)` 任务

### 4.2 AST 缓存命中逻辑

- 先用 `pathKey(path)` 查缓存
- 对比 `fingerprint(size + lastModifiedTime)`
- 命中则直接复用 AST（clone 后使用）

### 4.3 失败处理

- `future.get(parseTimeout)` 超时或异常后，执行 `retryParse`
- 重试次数由 `parseRetryCount` 决定
- 仍失败则写入 `failedItems` 和事件

这体现了“短超时 + 有限重试 + 最终降级失败”的工程思路。

---

## 5. ensureSymbolIndex：轻量符号索引

它没有做完整符号求解，而是提取 `ClassOrInterfaceDeclaration` 名称列表。

意义：

- 为后续规则或扩展提供类型索引基础
- 成本比完整语义解析低很多

如果以后要增强跨文件语义分析，这里是自然扩展点。

---

## 6. runRules：最关键的并发规则执行器

### 6.1 文件规则集选择

- 普通文件：执行 `rules`
- 降级文件：执行 `degradeRules`

### 6.2 规则结果缓存

- key = `fileFingerprint + # + ruleId`
- 命中则直接复用 issue 列表和耗时

### 6.3 任务执行与收集

- 提交 `RuleTask`（`Callable<RuleExecutionOutcome>`）
- `future.get(ruleTimeout)` 获取结果
- 合并到 `issues/ruleHitCounts/ruleDurationMillis`

### 6.4 失败隔离

- 单规则异常只记录失败，不中断整个分析任务
- 失败规则 ID 放入 `failedRuleIds`

这就是“高可用规则执行器”的核心：容错优先。

---

## 7. RuleTask：为什么要单独内嵌类

`RuleTask.call()` 做了两件事：

- 调用 `rule.analyze(compilationUnit, context)`
- 统计本规则耗时（纳秒转毫秒）

若异常，会包装成带 ruleId 和 filePath 的 `IllegalStateException`，方便定位。

这种做法比“直接抛原异常”更利于线上排错。

---

## 8. 规则层实现怎么读（以 NullDereferenceRule 为例）

建议按这条线：

1. 先看 `id()` 和 `description()`
2. 看 `analyze` 里先找了哪些 AST 节点（`IfStmt`）
3. 看 `nullCheckedName` 如何判定 `x == null`
4. 看 then 分支是否对 `x` 调方法
5. 看 issue 的 severity 和 message

你会发现：规则实现本质是“语法树模式匹配 + 业务语义命名”。

---

## 9. 你改代码时最容易踩的点

- 改动 ruleId：会影响缓存、治理统计、前端映射。
- 调大并发不测压：可能导致解析线程与规则线程互相争资源。
- parse 超时过小：在大文件上会造成大量重试噪声。
- 忽略 degradedFiles：会误判“某些规则为什么没执行”。

---

## 10. 推荐改造练习（进阶）

1. 把 `putWithBound` 从“满了清空”改为真正 LRU。  
2. 在 `runRules` 增加“同规则批处理”模式比较性能。  
3. 给 `ExecutionContext` 增加阶段耗时分布（P50/P95）。  
4. 为每条规则增加“最大失败次数熔断”机制。  
