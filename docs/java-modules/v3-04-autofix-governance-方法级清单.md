# AutoFix + Governance 模块方法级学习清单（第三版）

模块目录：

- `src/main/java/com/project/javasecurityoptimizer/autofix`
- `src/main/java/com/project/javasecurityoptimizer/governance`

---

## 1. 类级阅读顺序

1. `FixPlanBuilder`
2. `PatchEngine`
3. `AutoFixService`
4. `RuleGovernanceService`
5. `RuleParameterSchema`
6. `ProjectRulePolicy` / `ProjectRulePolicyRepository`

---

## 2. FixPlanBuilder 必读方法

### `buildFromIssues(List<AnalysisIssue> issues)`

- 作用：从分析问题构建修复计划项
- 关键点：issue 可能无 fixCandidates，要做空处理

### `inferEdits(AnalysisIssue issue, FixCandidate candidate)`

- 作用：规则感知的 edit 推导
- 当前重点：`JAVA.STRING.EQUALITY`

### `rewriteStringEquality(String sourceLine)`

- 作用：将 `==/!=` 改写为 `Objects.equals(...)`
- 风险：文本改写可能误伤复杂表达式

---

## 3. PatchEngine 必读方法

### `buildPlan(List<PatchEdit> edits)`

- 作用：排序 + 冲突检测 + 合并可执行 edits

### `overlap(PatchEdit left, PatchEdit right)`

- 作用：编辑区间是否冲突

学习重点：
- 这是“文本补丁冲突检测”的最小可用实现。

---

## 4. AutoFixService 必读方法（核心）

### `preview(...)`

- 作用：只生成计划与预览，不落盘

### `applyBatch(...)`

- 作用：应用主流程
- 关键步骤：过滤策略 -> 冲突判定 -> 快照 -> 应用 -> 门禁 -> 回滚

### `buildFilteredPlan(...)`

- 作用：按策略过滤 `REVIEW_REQUIRED` edits

### `captureSnapshot(...)`

- 作用：保存回滚原文

### `applyEdits(...)`

- 作用：按文件分组、按行号倒序替换

### `runQualityGate(...)`

- 作用：语法门禁 + 规则复检门禁

### `rollback(...)` / `rollbackInternal(...)`

- 作用：按 rollbackId 恢复快照

### `latestAudits(int limit)`

- 作用：读取最近修复审计记录

---

## 5. RuleGovernanceService 必读方法

### `saveProjectPolicy(ProjectRulePolicy inputPolicy)`

- 作用：策略保存前参数清洗

### `resolveExecutableRuleSet(String workspaceId, RuleTemplateType fallbackTemplate)`

- 作用：模板规则 + enabled - disabled

### `resolveParameters(String workspaceId)`

- 作用：获取运行参数（含默认值与 sanitize）

### `recordAnalysisMetrics(String workspaceId, RuleExecutionMetrics metrics)`

- 作用：累计规则命中/耗时指标

### `markFalsePositive(FalsePositiveFeedback feedback)`

- 作用：记录误报并累计误报计数

### `dashboard(String workspaceId)`

- 作用：导出治理仪表盘

---

## 6. 方法级常见陷阱

- `applyEdits` 替换顺序不对会导致行号漂移。  
- `runQualityGate` 不做复检会把“修了一半”的补丁当成功。  
- `rollbackSnapshots` 仅内存存储，重启丢失回滚能力。  
- `resolveExecutableRuleSet` 忽略 disabled 可能让禁用规则重新生效。  

---

## 7. 改造练习（方法级）

1. 给 `FixPlanBuilder` 增加基于 AST 的改写器（替换文本改写）。  
2. 给 `PatchEngine` 增加“冲突可忽略策略”开关。  
3. 给 `runQualityGate` 增加“仅复检触达规则”与“全规则复检”模式。  
4. 给 `RuleGovernanceService.dashboard` 增加规则收益分指标。  

---

## 8. 打卡标准

- 能手动画出 `applyBatch` 的每个失败分支。  
- 能解释 `failOnConflict` 与 `allowReviewRequired` 的区别。  
- 能说明治理规则集如何从模板和项目策略计算而来。  
- 能独立实现一个小型自动修复规则并通过门禁。  
