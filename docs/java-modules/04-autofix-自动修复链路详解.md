# AutoFix 模块详解：从问题到补丁再到回滚

对应目录：`src/main/java/com/project/javasecurityoptimizer/autofix`

这个模块的目标不是“无脑改代码”，而是：  
**在安全边界内，尽量自动化地生成、应用、验证、回滚修复。**

---

## 1. 模块总体流程

你可以把 AutoFix 看成 6 步：

1. 从 issue 生成 fix plan（`FixPlanBuilder`）
2. 从 plan 生成 patch edits
3. 合并 edits 并检测冲突（`PatchEngine`）
4. 预览差异（preview）或实际应用（apply）
5. 质量门禁（语法检查 + 规则复检）
6. 失败自动回滚，或手动回滚

---

## 2. 核心对象与语义

- `FixPlanItem`：某个问题 + 某个修复候选 + 对应 edits
- `PatchEdit`：对单文件某个行区间的替换动作
- `PatchPlan`：合并后的可执行 edits + 冲突列表
- `PatchConflict`：冲突明细（文件/行号/原因）
- `AutoFixResult`：执行结果、回滚ID、预览、质量门禁消息
- `FixApplyStrategy`：应用策略开关（是否失败即停、是否允许 REVIEW_REQUIRED、是否运行门禁）

知识点：
- 把“策略”抽象成对象可以避免方法参数爆炸。

---

## 3. FixPlanBuilder：如何从 issue 推导编辑

`buildFromIssues` 做的事：

- 遍历每个 `AnalysisIssue` 的 fix candidates
- 为每个 candidate 推导对应 `PatchEdit`

当前示例能力主要覆盖 `JAVA.STRING.EQUALITY`：

- 读取问题行源码
- 把 `a == b` / `a != b` 尝试改写为 `Objects.equals(a, b)` 形态

知识点：
- 这是“规则感知修复”的雏形：修复逻辑与规则 ID 关联。
- 自动修复必须保守，宁可不改也不要错改。

---

## 4. PatchEngine：冲突检测核心

`buildPlan` 主要逻辑：

- 按文件 + 行区间排序
- 维护每个文件最近编辑
- 若行区间 overlap，则记录冲突并跳过

冲突判定：

- `left.endLine >= right.startLine && right.endLine >= left.startLine`

知识点：
- 这是一种“线段重叠检测”的工程化应用。
- 先排序再线性扫描，复杂度和实现成本都可控。

---

## 5. AutoFixService：主服务拆解

### 5.1 preview

- 构建过滤后的 patch plan
- 生成差异预览（按文件分组）
- 记录审计日志
- 不写磁盘

### 5.2 applyBatch / applySingle

流程：

1. 构建 plan + preview
2. 若冲突且策略要求 failOnConflict，则直接失败
3. capture snapshot（原文快照）生成 rollbackId
4. 应用 edits 到文件
5. 运行质量门禁
6. 门禁失败则自动回滚
7. 返回成功/失败结果并写审计

知识点：
- “先快照后修改”是实现可回滚的关键。
- apply 不是最终成功，必须经过 gate 才算成功。

### 5.3 rollback

- 通过 rollbackId 找快照
- 逐文件恢复原文
- 成功后移除快照并记录审计

---

## 6. 质量门禁（runQualityGate）

门禁由策略控制：

- `runCompileGate=true`：用 JavaParser 再 parse 一遍修改后文件，做语法检查
- `runRuleRecheck=true`：构造增量请求，调用分析引擎复检触达规则

如果复检后仍有问题：

- 返回 `quality gate failed`，触发自动回滚

知识点：
- 自动修复模块必须“自证正确性”，不是改完就结束。

---

## 7. 文件编辑实现细节（applyEdits）

每个文件的 edits 会：

- 按 `startLine` 降序执行（避免先改前面影响后面行号）
- 基于 `lines.subList(from, to)` 做区间替换
- 最后统一写回文件

这是一种常见“行号稳定编辑”技巧。

---

## 8. 审计与可追踪性

`auditLog` 记录：

- operationId
- action（PREVIEW/APPLY/ROLLBACK）
- operator
- success
- message
- touchedFiles
- timestamp

知识点：
- 修复动作属于“可能破坏代码”的高风险操作，必须可追踪。

---

## 9. 测试覆盖点（AutoFixServiceTest）

目前重点测试：

- `shouldApplyAndRollbackSingleFix`：改动后可回滚到原始内容
- `shouldFailQualityGateAndAutoRollback`：门禁失败自动回滚

你可以继续补充：

- 多文件 batch 顺序和一致性
- 冲突 + failOnConflict=false 的行为
- runRuleRecheck 复检路径

---

## 10. 常见风险与改进方向

- 风险1：文本替换过于脆弱  
  建议：从“按行替换”升级为 AST 级 rewrite。

- 风险2：回滚快照内存压力  
  建议：大文件改成临时文件快照，按需加载。

- 风险3：质量门禁粒度不足  
  建议：增加单元测试门禁、格式化门禁、规则白名单门禁。

- 风险4：并发 apply 可能竞争文件  
  建议：增加文件级锁或工作区级互斥策略。

---

## 11. 动手练习

1. 增加新修复器：`JAVA.PERF.LOOP_STRING_CONCAT` 自动改 `StringBuilder`。  
2. 给 `PatchEngine` 增加“同一区间可合并”的策略（非冲突场景）。  
3. 为 `AutoFixService` 增加“dry-run+统计报告”模式。  
4. 把回滚快照持久化到磁盘，并支持进程重启后回滚。  
