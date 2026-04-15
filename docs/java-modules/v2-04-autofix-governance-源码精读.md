# AutoFix + Governance 源码精读（第二版）

主目录：

- `src/main/java/com/project/javasecurityoptimizer/autofix`
- `src/main/java/com/project/javasecurityoptimizer/governance`

这篇把两个“后处理模块”放在一起看：  
一个负责“改代码”，一个负责“管规则”。

---

## 1. AutoFix 主调用链

推荐顺序：

1. `AutoFixService.preview`
2. `AutoFixService.applyBatch`
3. `PatchEngine.buildPlan`
4. `AutoFixService.applyEdits`
5. `AutoFixService.runQualityGate`
6. `AutoFixService.rollback`

---

## 2. preview 与 apply 的差别

### preview

- 构建 patch plan
- 生成 patch 预览
- 记录审计
- 不写文件

### apply

- 先做上述步骤
- 再快照原文（rollback snapshot）
- 执行 edits
- 跑质量门禁
- 失败则自动回滚

这意味着 preview 是“静态评估”，apply 是“带防护的执行”。

---

## 3. FixPlanBuilder：从 issue 推导 edit

`buildFromIssues` 会遍历 `AnalysisIssue -> FixCandidate`，尝试生成 `PatchEdit`。

目前实现展示了一个真实思路：

- 对 `JAVA.STRING.EQUALITY` 做定向改写
- 从源文件读取问题行
- 推导替换表达式

这不是通用 refactor 引擎，而是“规则驱动的定向修复器”。

---

## 4. PatchEngine：冲突判定策略

`buildPlan` 先排序，再按文件做线性冲突检测：

- 同文件编辑区间重叠 -> 冲突
- 不重叠 -> 可合并进入执行计划

可见当前策略是“保守优先”，宁可不自动合并，也不冒险写坏代码。

---

## 5. applyEdits：为什么按倒序替换

同一文件多个 edit 时，按 `startLine` 倒序应用：

- 先改后面的行，不会影响前面 edit 的行号定位

这是文本编辑器里常见的稳定性技巧，工程价值很高。

---

## 6. runQualityGate：自动修复的生命线

门禁受 `FixApplyStrategy` 控制：

- `runCompileGate`：JavaParser 语法检查
- `runRuleRecheck`：触发增量分析复检对应规则

任何门禁失败都触发回滚，保证“可自动修复”不等于“可自动破坏”。

---

## 7. rollback：最终兜底

`rollbackInternal` 根据 `rollbackId` 找快照并还原：

- 快照在内存 `rollbackSnapshots`
- 逐文件写回原文

当前限制：

- 进程重启后快照丢失
- 大文件快照占内存

你可把它作为后续优化点。

---

## 8. AutoFix 审计线

`recordAudit` 记录：

- operationId
- action（PREVIEW/APPLY/ROLLBACK）
- operator
- success/message
- touchedFiles

这与安全模块能形成闭环：修复动作可追踪、可回放。

---

## 9. Governance 主调用链

从 `RuleGovernanceService` 看：

1. `saveProjectPolicy`
2. `resolveExecutableRuleSet`
3. `resolveParameters`
4. `recordAnalysisMetrics`
5. `markFalsePositive`
6. `dashboard`

它像一个轻量“规则策略引擎”。

---

## 10. 策略计算：模板 + 覆盖

`resolveExecutableRuleSet` 的关键规则：

- 基线 = 模板规则集
- 加 enabledRuleIds
- 去 disabledRuleIds

这是最常见、也最实用的策略模型。

---

## 11. 参数治理：为什么一定要 sanitize

`saveProjectPolicy` 会先 `parameterSchema.sanitize` 再落库。  
好处是把参数合法性规则收敛在 schema，不让脏参数扩散到运行期。

---

## 12. 仪表盘累计：趋势而非单次

`DashboardAccumulator` 聚合：

- 命中次数
- 耗时累计
- 误报累计

这为后续“规则 ROI 评估”提供基础数据。

---

## 13. 这两个模块如何协同

- AutoFix 用 analysis 结果做修复输入
- Governance 用 analysis 指标做策略调优
- 两者都依赖可观测数据和审计来形成闭环

你可以把它们看成“发现问题后的两个方向”：一个立刻修，一个长期管。

---

## 14. 推荐改造练习

1. 给 `FixPlanBuilder` 增加 `LOOP_STRING_CONCAT -> StringBuilder` 自动修复。  
2. 给 `PatchEngine` 增加“可合并编辑”的策略开关。  
3. 给 `RuleGovernanceService` 增加策略版本与回滚。  
4. 给 dashboard 新增“规则单位收益 = 命中 / 耗时”指标。  
