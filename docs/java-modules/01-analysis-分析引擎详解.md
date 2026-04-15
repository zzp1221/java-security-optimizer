# Analysis 模块详解：静态分析引擎是如何工作的

对应源码目录：`src/main/java/com/project/javasecurityoptimizer/analysis`  
你可以把这个模块理解为：**把“Java 源码文件”转换成“可执行规则 + 可观测报告”的核心引擎**。

---

## 1. 模块职责与边界

### 输入

- `AnalyzeTaskRequest`：项目路径、分析模式（FULL/INCREMENTAL）、规则集、并发参数、超时、降级策略等。

### 输出

- `AnalyzeTaskResult`：问题列表（`AnalysisIssue`）、统计信息（`AnalysisStats`）、进度事件（`ProgressEvent`）、规则执行指标（`RuleExecutionMetrics`）、执行报告（`AnalysisExecutionReport`）。

### 不负责的事情

- 不直接处理 HTTP（由 `task` 控制器/服务负责）
- 不负责任务生命周期（由 `TaskSchedulerService` 负责）
- 不负责规则包导入与安全校验（由 `rulepack/security` 负责）

---

## 2. 核心数据模型（Java 知识点）

### 2.1 不可变请求对象与默认值归一化

`AnalyzeTaskRequest` 是 final 类，构造器里做了大量“参数纠偏”：

- 文件大小阈值默认值
- parse/rule 并发度默认值（与 CPU 核心相关）
- timeout 默认值（`Duration`）
- retry 默认值

知识点：
- 防御式编程：入口就把不可靠参数转成可靠参数。
- 不可变对象思想：构造后只读，降低并发场景中的状态复杂度。

### 2.2 record 作为轻量 DTO

`AnalysisIssue`、`CacheStats`、`RuleExecutionMetrics`、`ProgressEvent` 等大量使用 record。

知识点：
- record 非常适合“分析结果载体”
- 减少样板代码，让注意力集中在业务字段

### 2.3 规则契约

`JavaRule` 统一规则接口：

- `id()`：规则标识（稳定主键）
- `description()`：人类可读描述
- `analyze(CompilationUnit, RuleContext)`：规则执行入口

知识点：
- 接口定义“扩展协议”，是插件化规则体系的最小单元。

---

## 3. 主流程拆解（JavaAnalysisEngine）

主方法：`analyzeWithMetrics()`，建议把它当成 4 段流水线。

### 3.1 阶段A：索引（indexFiles）

- FULL 模式：`Files.walk(projectPath)` 遍历 `.java` 文件。
- INCREMENTAL 模式：只处理 changed + impacted 文件。
- 过滤超大文件，记录跳过事件。
- 大文件触发降级标记（后续只跑降级规则集）。

知识点：
- NIO 文件 API：`Path`、`Files.walk`、`Files.exists`、`Files.size`
- 模式分支：全量与增量分析的工程差异

### 3.2 阶段B：解析（parseFiles）

- 用线程池并发调用 `StaticJavaParser.parse(path)`。
- 每个文件都计算 fingerprint（size + mtime）。
- 命中 AST 缓存则直接复用。
- 解析失败后按 `parseRetryCount` 重试。
- 构建符号索引缓存（类型名列表）。

知识点：
- 并发工具：`ExecutorService`、`Future`、`Callable`
- 超时控制：`future.get(timeout, TimeUnit.MILLISECONDS)`
- 重试策略：失败可恢复时进行有限重试
- 缓存一致性：fingerprint 是缓存有效性基础

### 3.3 阶段C：执行规则（runRules）

- 为每个文件和规则提交任务。
- 文件被标记 degraded 时，仅执行 `degradeRules`。
- 支持规则结果缓存（rule + fileFingerprint 组合键）。
- 单条规则失败不拖垮整次任务，失败规则会计入 `failedRuleIds`。

知识点：
- “错误隔离”原则：单规则失败不等于任务失败
- 线程安全聚合：`Collections.synchronizedList` + Map merge
- 失败可观测：事件流 + failedItems

### 3.4 阶段D：报告聚合

- 聚合统计：文件数、问题数、失败规则数、耗时
- 输出缓存命中指标和降级文件列表
- 返回统一结果对象供上层消费

知识点：
- 观测优先设计：不仅要有结果，还要知道“过程发生了什么”

---

## 4. 并发与线程安全设计细节

### 4.1 为什么拆成多个线程池

- 解析池与规则池关注点不同，便于分别调优。
- 避免某一阶段阻塞影响另一阶段调度。

### 4.2 为什么缓存用 ConcurrentHashMap

- 读多写少场景，性能和实现复杂度平衡较好。
- 配合 `putWithBound` 做简单容量控制（达到上限时清空）。

### 4.3 可能的改进点（你可作为练习）

- 用 LRU 替代“满了就 clear”
- 规则执行支持更细粒度熔断
- 解析与规则阶段的指标上报到监控系统

---

## 5. 规则开发机制（以 NullDereferenceRule 为例）

### 5.1 规则实现步骤

1. 继承 `AbstractJavaRule`
2. 实现 `id` / `description`
3. 在 `analyze` 中遍历 AST 节点（如 `IfStmt`、`MethodCallExpr`）
4. 构造 `AnalysisIssue` 返回

### 5.2 AST 模式匹配思路

在 `NullDereferenceRule` 中：

- 先识别 `if (x == null)` 这样的条件
- 再扫描 then 分支里是否调用了 `x.someMethod()`
- 命中就报 Critical 问题

知识点：
- Java 21 pattern matching `instanceof` 用法（`instanceof NameExpr nameExpr`）
- AST 分析的核心是“语义近似”而不是文本匹配

---

## 6. 性能策略：增量、降级、缓存

### 增量分析

- 只分析 changed/impacted 文件，缩短反馈时间。

### 降级规则集

- 大文件只跑关键规则（例如空指针、资源泄漏、硬编码凭据），保证可用性优先。

### 缓存层次

- AST 缓存：避免重复 parse
- 符号缓存：避免重复类型索引
- 规则缓存：避免同规则重复计算

这三个组合，体现了“正确性优先 + 性能兜底”的工程取舍。

---

## 7. 测试如何覆盖这个模块（看哪些测试最有价值）

重点看 `JavaAnalysisEngineTest`：

- 主流程测试：能否跑出多规则结果
- 增量模式测试：changed + impacted 是否生效
- 失败隔离测试：一条规则崩溃是否被隔离
- 缓存命中测试：第二次运行是否有 cache hits
- 降级测试：大文件是否仅跑降级规则

学习方法：
- 不要只看断言值，先看它构造了什么输入场景。

---

## 8. 常见坑与最佳实践

- 坑1：规则 ID 不稳定  
  后果：缓存、治理、统计全失效。  
  建议：ID 视为协议字段，禁止随意改。

- 坑2：规则内部抛异常  
  后果：分析中断或数据污染。  
  建议：规则内部尽量捕获可预期异常，返回空结果而不是抛出。

- 坑3：盲目提高并发度  
  后果：上下文切换开销和 GC 压力上升。  
  建议：从 CPU/IO 特征出发压测后调参。

---

## 9. 面试/答辩可直接复用的话术

- “我们把静态分析拆成 index、parse、rule-run 三阶段，并用分层缓存降低重复计算成本。”
- “规则执行采用失败隔离，单规则失败不会影响整体任务可用性。”
- “增量分析 + 降级规则集是为了在大仓库场景中保证反馈时延可控。”

---

## 10. 动手练习

1. 新增规则：检测 `System.out.println` 在生产代码中的使用。  
2. 给规则缓存改造成 LRU，比较前后内存占用和命中率。  
3. 增加“规则级别超时统计”，并写一个测试验证超时规则被记为失败。  
