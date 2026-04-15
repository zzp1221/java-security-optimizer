# Task + Storage 模块详解：任务是如何被可靠调度的

对应目录：

- `src/main/java/com/project/javasecurityoptimizer/task`
- `src/main/java/com/project/javasecurityoptimizer/storage`

这个模块解决的问题是：**分析任务从提交到完成（或失败）全过程如何保持有序、可重试、可取消、可观测。**

---

## 1. 模块分层职责

### `TaskController`（接口层）

- 负责 HTTP 到服务调用的映射
- 对错误场景返回正确状态码（400/404/409）
- 不承载业务状态，仅做协议转换

### `TaskSchedulerService`（调度核心）

- 接收任务并入队
- 推进任务状态（queued -> running -> completed/failed/cancelled）
- 执行超时、重试、取消逻辑
- 汇总任务诊断信息

### `TaskRecord` + `TaskStateMachine`（状态模型）

- `TaskRecord`：任务实体，记录生命周期数据
- `TaskStateMachine`：合法状态转移白名单

---

## 2. 提交任务时发生了什么（submit）

`submit(TaskSubmitRequest)` 的关键步骤：

1. 校验输入（projectPath 必填）
2. 归一化参数（taskId、traceId、workspace、priority、timeout、maxRetries）
3. 根据 language 做插件健康检查
4. 组装 `AnalyzeTaskRequest`
5. 创建 `TaskRecord` 与 `ManagedTask`
6. 原子放入 `tasks` 映射（防 taskId 冲突）
7. 状态转移到 `QUEUED` 并入优先队列

知识点：
- 参数归一化是服务稳定性的第一道防线。
- `putIfAbsent` 体现并发环境下的原子去重。

---

## 3. 队列与优先级：为什么选择 PriorityBlockingQueue

队列元素 `QueuedTask` 实现了 `Comparable`：

- 先按 priority 降序（高优先级先执行）
- 同优先级按 sequence 升序（先到先服务）

这等价于“**优先级调度 + 同级 FIFO**”。

知识点：
- `PriorityBlockingQueue` 适合“生产者/消费者 + 优先级”的任务模型。
- `AtomicLong queueSequence` 用于生成单调递增序号，避免比较不稳定。

---

## 4. workerLoop：单线程调度器的价值

`worker` 是单线程执行器，循环从队列取任务并推进状态。

为什么单线程？

- 状态迁移逻辑更简单
- 降低并发竞态风险
- 更容易推导“先后关系”

注意：
- 真正耗时分析不在 worker 里做，而是提交到 `analysisExecutor`，避免阻塞调度线程。

---

## 5. 执行、超时、失败分类（runManagedTask）

### 成功路径

- `future.get(taskTimeoutMillis, TimeUnit.MILLISECONDS)` 拿结果
- 写入 issues、events、rule hit 统计
- 状态迁移到 `COMPLETED`

### 超时路径

- 抛 `TimeoutException`
- 取消 future
- 失败类别标记为 `TIMEOUT`

### 异常路径

- 如果根因是 `PluginException` -> `PLUGIN_UNAVAILABLE`
- 其他异常 -> `ANALYSIS_ERROR`

知识点：
- “失败分类”比单字符串错误信息更有工程价值，便于告警与统计。

---

## 6. 重试、取消机制（工程必会）

### 重试

- `handleTaskError` 中判断 `attempt < maxRetries`
- 可重试则状态回到 `QUEUED`，重新入队
- 尝试次数在 `TaskRecord.markRetryScheduled()` 中递增

### 取消

- 队列中任务：直接移除并置 `CANCELLED`
- 运行中任务：标记 `cancelRequested`，结束后进入取消态

知识点：
- 取消是“协作式”的：运行中的线程不一定即时终止，需要状态标志配合。

---

## 7. 状态机：为什么要有 TaskStateMachine

`TaskStateMachine` 用 `Map<TaskStatus, EnumSet<TaskStatus>>` 定义合法转移。

好处：

- 业务规则集中管理
- 非法转移可立即失败（`assertTransit`）
- 便于测试覆盖和后期扩展（比如新增 `PAUSED`）

这是调度系统避免“状态雪崩”的关键。

---

## 8. 线程安全策略（高频面试点）

本模块没有“一个大锁”，而是按数据边界选择工具：

- 全局任务表：`ConcurrentHashMap`
- 队列：`PriorityBlockingQueue`
- 序号：`AtomicLong`
- 单任务对象：`synchronized(task)` 保护复合状态更新

设计原则：
- 让“共享可变状态”尽可能少，锁粒度尽可能小。

---

## 9. 任务诊断（TaskDiagnostics）

调度服务暴露诊断聚合：

- 最近任务指标（耗时、问题数、结束时间）
- 失败原因 TOP
- 耗时分布桶（<5s、5-30s、30-120s、>=120s）
- 规则命中 TOP

知识点：
- 服务不仅要“做事”，还要“解释自己做得如何”。

---

## 10. TaskRecord：生命周期对象设计

`TaskRecord` 里包含：

- 元信息：taskId、traceId、workspaceId
- 状态字段：status、attempt、maxRetries、archived
- 时间字段：createdAt、startedAt、finishedAt、durationMillis
- 结果字段：issueCount、failureReason、failureCategory、reportPath

方法设计特点：

- `markRunning`、`finishCompleted`、`finishFailed` 等方法封装状态变化
- 避免外部直接改字段，保持语义一致

---

## 11. API 语义与异常映射

`TaskController` 的典型设计：

- 提交参数错 -> `400`
- 查询不存在 -> `404`
- 不能取消/重试 -> `409`

这能让前端明确区分“请求错了”与“状态不允许”。

---

## 12. 测试如何保障这套系统

重点看 `TaskSchedulerServiceE2ETest`：

- 任务从 queued 到 completed 的完整链路
- 超时后重试、最终失败分类是否正确
- diagnostics 是否能反映真实执行

学习重点：
- E2E 测试不是为了覆盖每个分支，而是验证“关键业务承诺”。

---

## 13. 常见坑与改进建议

- 坑1：重试次数和 attempt 定义不统一  
  建议：明确 attempt 是否包含首次执行，并写注释/测试。

- 坑2：取消后仍写入完成态  
  建议：在关键节点都检查 `cancelRequested`。

- 坑3：失败原因过于粗糙  
  建议：统一错误码 + 可读 message 双轨输出。

- 坑4：诊断维度不足  
  建议：增加按 workspace、language、ruleSet 的聚合。

---

## 14. 动手练习

1. 新增任务状态 `PAUSED`，支持 pause/resume API。  
2. 增加“任务最大排队时长”控制，超时未执行自动失败。  
3. 为 `TaskDiagnostics` 增加“平均耗时/95分位耗时”。  
4. 给取消流程补一组并发竞态测试（提交后立刻取消、运行中取消、重试中取消）。  
