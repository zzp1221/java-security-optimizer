# Task + Storage 模块方法级学习清单（第三版）

模块目录：

- `src/main/java/com/project/javasecurityoptimizer/task`
- `src/main/java/com/project/javasecurityoptimizer/storage`

---

## 1. 类级阅读顺序

1. `TaskSubmitRequest`
2. `TaskController`
3. `TaskSchedulerService`
4. `TaskRecord`
5. `TaskStateMachine`
6. `TaskDiagnostics`

---

## 2. TaskController 必读方法

### `submitTask(@RequestBody TaskSubmitRequest request)`

- 作用：提交任务 API
- 关键点：`IllegalArgumentException -> 400`

自测：
- 为什么 controller 不直接做复杂参数校验？

### `cancelTask(String taskId)` / `retryTask(String taskId)`

- 作用：状态操作 API
- 关键点：冲突场景统一用 `409`

自测：
- 取消失败和任务不存在应分别返回什么？

---

## 3. TaskSchedulerService 必读方法（核心）

### `start()` / `stop()`

- 作用：生命周期控制
- 关键点：worker 和 analysisExecutor 的启动与回收

### `submit(TaskSubmitRequest request)`

- 作用：创建并入队任务
- 关键点：参数归一化、插件健康检查、taskId 去重、状态迁移到 QUEUED

### `toAnalyzeTaskRequest(TaskSubmitRequest request)`

- 作用：DTO 转换层
- 关键点：字符串路径转 `Path`，毫秒转 `Duration`

### `workerLoop()`

- 作用：单线程调度主循环
- 关键点：`queue.take()`、状态断言、调用 `runManagedTask`

### `runManagedTask(ManagedTask task)`

- 作用：执行任务并处理成功/超时/异常
- 关键点：future + timeout + failureCategory 分类

### `handleTaskError(...)`

- 作用：重试或失败落地
- 关键点：attempt 与 maxRetries 判定

### `cancel(String taskId)`

- 作用：取消任务
- 关键点：区分 QUEUED/CREATED 与 RUNNING 的取消路径

### `retry(String taskId)`

- 作用：手动重试
- 关键点：仅允许 FAILED/CANCELLED 状态

### `diagnostics()`

- 作用：生成运行态诊断
- 关键点：失败原因聚合、时长分桶、规则命中 TOP

---

## 4. TaskRecord 必读方法

### `create(...)`

- 作用：统一创建任务初始实体

### `markQueued()` / `markRunning(...)`

- 作用：推进中间状态

### `finishCompleted(...)` / `finishFailed(...)` / `finishCancelled(...)`

- 作用：进入终态并计算耗时
- 关键点：统一调用 `computeDurationMillis`

### `markRetryScheduled()`

- 作用：尝试次数 +1 并回队列状态

---

## 5. TaskStateMachine 必读方法

### `canTransit(from, to)`

- 作用：查询状态迁移是否合法

### `assertTransit(from, to)`

- 作用：非法迁移立即失败

---

## 6. 方法级常见陷阱

- `submit` 忘记 `putIfAbsent`，会出现 taskId 冲突覆盖。  
- `runManagedTask` 忘记检查 `cancelRequested`，会出现取消后仍完成。  
- `handleTaskError` attempt 语义不清，可能造成重试次数偏差。  
- `diagnostics` 聚合未过滤终态，会导致统计失真。  

---

## 7. 改造练习（方法级）

1. 在 `cancel` 增加“取消原因”参数并透传到审计。  
2. 在 `workerLoop` 增加“排队等待时长”事件。  
3. 给 `handleTaskError` 增加指数退避重试策略。  
4. 给 `diagnostics` 增加 P95 耗时与按 workspace 过滤。  

---

## 8. 打卡标准

- 能解释 `submit -> workerLoop -> runManagedTask` 的线程边界。  
- 能说清 `cancel` 在不同状态下的行为差异。  
- 能手动画出 TaskStatus 转移图。  
- 能独立补 1 个重试或取消相关并发测试。  
