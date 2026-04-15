# Task + Storage 源码精读（第二版）

主目录：

- `src/main/java/com/project/javasecurityoptimizer/task`
- `src/main/java/com/project/javasecurityoptimizer/storage`

这篇核心问题：  
**任务系统如何在并发环境下保证状态正确、行为可预期。**

---

## 1. 从哪个方法开始读

推荐从 `TaskController.submitTask()` 进入，然后顺着以下调用链：

1. `TaskController.submitTask`
2. `TaskSchedulerService.submit`
3. `TaskSchedulerService.workerLoop`
4. `TaskSchedulerService.runManagedTask`
5. `TaskRecord.finishXXX`

读完这 5 段，你就掌握了 80% 调度逻辑。

---

## 2. submit：把外部请求变成内部任务

### 2.1 参数清洗

- taskId/traceId/workspaceId 默认补齐
- priority 夹在 [-100, 100]
- taskTimeout 有下限保护
- maxRetries 非负

### 2.2 插件可用性门禁

- `normalizeLanguage` + `healthOf`
- plugin 不可用直接拒绝，避免把坏任务放进队列

### 2.3 构造内部对象

- `AnalyzeTaskRequest`：分析引擎需要的参数
- `TaskRecord`：生命周期实体
- `ManagedTask`：运行时容器（events/issues/counters）

### 2.4 入队

- `tasks.putIfAbsent` 保证 taskId 唯一
- 状态机断言 `CREATED -> QUEUED`
- `queue.offer(QueuedTask(...))`

---

## 3. workerLoop：调度内核

`worker` 是单线程，循环执行：

1. `queue.take()` 阻塞取任务
2. 拿到 `ManagedTask`
3. 校验当前状态必须是 `QUEUED`
4. 状态转 `RUNNING`
5. 调 `runManagedTask(task)`

为什么这样设计：

- 单线程推进状态，极大降低竞态复杂度。
- 真正分析动作在另一个线程池，不阻塞调度循环。

---

## 4. runManagedTask：成功、超时、异常的分叉

### 成功分支

- `analysisExecutor.submit(plugin.analyze(...))`
- `future.get(timeout)` 拿结果
- 更新 events/issues/ruleHitCounters
- 状态 `RUNNING -> COMPLETED`

### 超时分支

- 捕获 `TimeoutException`
- `future.cancel(true)`
- `handleTaskError(..., TIMEOUT, ...)`

### 异常分支

- 根因是 `PluginException` -> `PLUGIN_UNAVAILABLE`
- 否则 -> `ANALYSIS_ERROR`

读这段时，重点看每个异常分支是否“最终都有状态落点”。

---

## 5. handleTaskError：重试策略真正落地处

关键逻辑：

- 若 `attempt < maxRetries`：
  - 状态转回 `QUEUED`
  - 记录 retry 事件
  - 重新入队
- 否则：
  - 状态转 `FAILED`
  - 写 failureCategory + reason

这里你要特别留意 `attempt` 的语义是否包含首次执行，避免业务理解偏差。

---

## 6. cancel 与 retry：状态驱动 API

### cancel

- `CREATED/QUEUED`：直接移出队列并置 `CANCELLED`
- `RUNNING`：设置 `cancelRequested`，并标记取消
- 其他状态：返回 false

### retry

- 仅允许 `FAILED/CANCELLED` 重试
- 状态机断言后回到 `QUEUED`

这两个 API 都不是“强行执行”，而是由状态机决定是否允许。

---

## 7. TaskStateMachine：调度安全网

`TRANSITIONS` 是状态迁移白名单：

- `CREATED -> QUEUED/CANCELLED/FAILED`
- `QUEUED -> RUNNING/CANCELLED/FAILED`
- `RUNNING -> QUEUED/COMPLETED/CANCELLED/FAILED`
- 终态到 `ARCHIVED` 等

`assertTransit` 失败直接抛异常，防止系统进入不可解释状态。

---

## 8. TaskRecord：生命周期数据账本

看 `TaskRecord` 时建议按“时间线”理解：

- create：初始化
- markQueued/markRunning：进入执行链
- finishCompleted/finishFailed/finishCancelled：进入终态
- markRetryScheduled：重试次数递增并回队列

`durationMillis` 统一由 `computeDurationMillis` 计算，避免调用方重复计算。

---

## 9. 线程安全实现细节

- 全局映射：`ConcurrentHashMap`
- 队列：`PriorityBlockingQueue`
- 排队序号：`AtomicLong`
- 单任务复合操作：`synchronized(task)`

经验总结：

- 任务系统里“每任务加锁”通常比“全局锁”可扩展性更好。

---

## 10. diagnostics：为什么它很重要

`TaskDiagnostics` 聚合了系统运行健康度：

- 最近完成任务
- 失败原因 TOP
- 时长分桶
- 规则命中 TOP

对你做容量评估、问题排查、规则收益分析都很关键。

---

## 11. 精读时的检查清单

- 每个分支是否有状态终点？
- 每次状态变化是否经过状态机断言？
- cancel/retry 是否可能与运行态并发冲突？
- 失败 reason 是否足够定位问题？

---

## 12. 推荐改造练习

1. 新增 `PAUSED` 状态并支持恢复执行。  
2. 增加任务排队超时（queued too long 自动失败）。  
3. 对 `diagnostics` 增加按 workspace 维度过滤。  
4. 补充“提交后立刻取消”的并发测试。  
