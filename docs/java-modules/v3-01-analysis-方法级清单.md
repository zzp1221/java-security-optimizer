# Analysis 模块方法级学习清单（第三版）

模块目录：`src/main/java/com/project/javasecurityoptimizer/analysis`

---

## 1. 类级阅读顺序（先后不可乱）

1. `AnalyzeTaskRequest`
2. `JavaAnalysisEngine`
3. `JavaRule` / `RuleContext`
4. `analysis/rules/AbstractJavaRule`
5. 任选 3 条具体规则（建议 `NullDereferenceRule`、`StringEqualityRule`、`ResourceNotClosedRule`）
6. `AnalyzeTaskResult` / `AnalysisStats` / `RuleExecutionMetrics` / `AnalysisExecutionReport`

---

## 2. 方法级必读清单：JavaAnalysisEngine

### 2.1 `analyze(AnalyzeTaskRequest request)`

- 角色：兼容入口，转发到 `analyzeWithMetrics`
- 输入：完整分析请求
- 输出：分析结果
- 你要确认：为什么不直接让外部调 `analyzeWithMetrics`

自测问题：
- 如果未来要做“无指标模式”，这个方法应如何扩展？

### 2.2 `analyzeWithMetrics(AnalyzeTaskRequest request)`

- 角色：流程编排器
- 关键动作：index -> parse -> runRules -> aggregate
- 副作用：生成 `events`、更新 `ExecutionContext` 统计

自测问题：
- 这里哪几个对象体现了“结果可观测”而不是“只返回 issues”？

### 2.3 `resolveRules(Set<String> requestedRuleIds)`

- 角色：规则集合解析
- 关键逻辑：请求为空时用 `localRepository.activeRuleSet()`
- 降级逻辑：最终为空则回退到全部规则

自测问题：
- `activeRuleSet` 和 `requestedRuleIds` 冲突时，谁优先？

### 2.4 `resolveDegradeRules(AnalyzeTaskRequest request, List<JavaRule> selectedRules)`

- 角色：大文件降级规则选取
- 关键逻辑：只在 `selectedRules` 内取交集

自测问题：
- 为什么降级规则集必须是 selectedRules 的子集？

### 2.5 `indexFiles(AnalyzeTaskRequest request, ExecutionContext executionContext)`

- 角色：文件索引阶段
- 关键逻辑：FULL vs INCREMENTAL 双路径
- 副作用：记录 degradedFiles 和事件

自测问题：
- INCREMENTAL 模式下相对路径如何转换？

### 2.6 `parseFiles(...)`

- 角色：并发 AST 解析
- 关键逻辑：缓存命中、超时、重试、失败记录
- 风险点：线程池大小与 parseTimeout 参数耦合

自测问题：
- 为什么缓存命中后还要 clone CompilationUnit？

### 2.7 `retryParse(...)`

- 角色：解析失败兜底
- 关键逻辑：有限次数重试 + 记录重试事件

自测问题：
- parseRetryCount 设置太大会造成什么问题？

### 2.8 `ensureSymbolIndex(...)`

- 角色：轻量符号索引缓存
- 关键逻辑：按 fingerprint 判定是否重建

自测问题：
- 现在仅存类名，若做跨文件调用图你会加哪些信息？

### 2.9 `runRules(...)`

- 角色：规则并发执行器
- 关键逻辑：降级规则选择、rule cache、超时处理、失败隔离
- 副作用：更新 `issues`/`ruleHitCounts`/`failedRuleIds`

自测问题：
- 单规则失败为何不直接终止任务？

### 2.10 `putWithBound(...)`

- 角色：缓存容量保护
- 当前策略：达到上限直接 clear

自测问题：
- 如何改成 LRU 并尽量少改动调用方？

---

## 3. 方法级必读清单：规则抽象与实现

### `AbstractJavaRule.issue(...)`

- 作用：把 AST 节点位置封装成 `AnalysisIssue`
- 关键点：从 `node.getRange()` 提取行号

### `NullDereferenceRule.analyze(...)`

- 作用：识别 null 分支中的对象调用
- 关键点：`if (x == null)` + then 中 `x.method()`

### `NullDereferenceRule.nullCheckedName(...)`

- 作用：从条件表达式抽取被判空变量名
- 关键点：仅处理 `BinaryExpr.Operator.EQUALS`

---

## 4. 数据对象必读点

### `AnalyzeTaskRequest` 构造器

- 默认值归一化
- 参数非空与边界处理

### `AnalyzeTaskResult` / `AnalysisExecutionReport`

- 区分“业务结果”与“执行报告”
- 这是你做可观测改造时的输出扩展点

---

## 5. 改造练习（方法级）

1. 给 `runRules` 增加“按 ruleId 分组批量执行”实验开关。  
2. 给 `parseFiles` 增加“慢解析文件 TOP N”统计。  
3. 给 `putWithBound` 增加策略参数（CLEAR_ALL/LRU）。  
4. 给 `ExecutionContext` 增加 parse 与 runRules 阶段耗时字段。  

---

## 6. 打卡标准（你学完应达到）

- 能口述 `analyzeWithMetrics` 全流程。  
- 能解释 FULL/INCREMENTAL 在 index 阶段的差异。  
- 能说明 rule cache 的 key 组成与失效条件。  
- 能独立新增一条规则并接入默认规则集。  
