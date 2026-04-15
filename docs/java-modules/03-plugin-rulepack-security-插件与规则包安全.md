# Plugin + RulePack + Security 模块详解

对应目录：

- `src/main/java/com/project/javasecurityoptimizer/plugin`
- `src/main/java/com/project/javasecurityoptimizer/rulepack`
- `src/main/java/com/project/javasecurityoptimizer/security`

这是项目里“扩展能力 + 供应链安全 + 审计追踪”的核心组合。

---

## 1. 为什么这三个模块要一起看

- 插件模块决定“谁来分析”
- 规则包模块决定“分析规则从哪来、能不能信任”
- 安全模块决定“关键操作是否可追踪、可审计”

三者构成闭环：**可扩展 + 可验证 + 可追责**。

---

## 2. 插件架构（plugin）

### 2.1 核心接口：LanguagePlugin

统一契约包含三件事：

- `meta()`：插件元信息（语言、能力、版本范围）
- `analyze()`：执行分析
- `validateRulePack()`：校验规则包

这意味着插件不只是“执行器”，还包含规则包验证能力。

### 2.2 JavaLanguagePlugin：内建实现

- 持有 `JavaAnalysisEngine`
- 持有 `RulePackValidator`
- `analyze` 直接委托给引擎
- `validateRulePack` 委托给校验器

知识点：
- 组合复用比继承更清晰
- 插件类本身保持薄层，业务复杂度在专门服务中

### 2.3 PluginManagerService：插件注册与健康管理

关键职责：

- 语言名标准化（默认 java，小写化）
- 插件注册和查询
- 启动健康快照（`@PostConstruct`）
- 插件可用性判断与友好提示

可用性判断维度：

- 是否注册
- engine version 是否兼容
- 是否是 placeholder（实现降级）

知识点：
- 运行时能力检测是“可插拔系统”稳定运行的前提。

---

## 3. 版本兼容策略（plugin + rulepack）

项目有两套兼容检查逻辑：

- `PluginManagerService.isCompatible`：插件元信息的范围判断
- `EngineVersionRange` / `RulePackValidator`：规则包兼容检查

范围语法支持：

- 精确版本：`1.0.0`
- 区间版本：`[1.0.0,2.0.0)` 等

知识点：
- 版本兼容不是字符串比较，要按数字段比较。
- `-SNAPSHOT` 等后缀需做归一化处理。

---

## 4. 规则包导入流程（rulepack）

入口：`RulePackController.importRulePack()`

主要步骤：

1. 校验 `packageFile` 必填
2. 解析 manifest（`manifestJson` 或 `manifestFile`）
3. 解析环境参数（DEV/PROD）
4. 保存上传包到临时文件
5. 构造 `RulePackSecurityContext`
6. 调用 `RulePackImportService.importPack`
7. 返回导入结果（成功或详细错误）
8. finally 清理临时文件

知识点：
- 控制器只做协议和输入校验，核心校验逻辑下沉到 service。

---

## 5. RulePackImportService：业务总入口

`importPack` 的三段式逻辑：

### 5.1 权限边界检查

- `securityContext.isPackagePathAuthorized(packageFile)`
- 越界则直接失败 `PERMISSION_OUT_OF_BOUNDS`

### 5.2 规则包完整性和兼容性校验

- 调用 `RulePackValidator.validate(...)`
- 若失败直接返回错误码和信息

### 5.3 安装与审计

- 校验通过后 `localRepository.install(manifest)`
- 记录安全审计事件（成功/失败）

知识点：
- “先鉴权，再校验，再入库”是安全流程的标准顺序。

---

## 6. RulePackValidator：安全核心

### 6.1 校验和（Checksum）

- 读取包文件 bytes
- 用 `MessageDigest(SHA-256)` 计算实际摘要
- 与 manifest checksum 比较

### 6.2 兼容性校验

- engine version range 是否包含当前版本
- 目标环境（DEV/PROD）是否在允许列表

### 6.3 签名验签

- 组装 canonical payload（固定字段顺序，保证签名稳定）
- Base64 解码签名和公钥
- `KeyFactory` 解析公钥
- `Signature` 做验签

### 6.4 可信密钥校验

- 非 permissive 模式下，keyId 必须在受信任仓中
- 算法、公钥与声明必须一致

知识点：
- 验签不是“只看签名能否通过”，还要验证签名者身份是否受信任。

---

## 7. 安全审计模块（security）

### 7.1 SecurityAuditService

记录三类事件：

- 规则包导入（`RULE_PACK_IMPORT`）
- 任务取消（`TASK_CANCEL`）
- 修复应用（`FIX_APPLY`）

实现细节：

- 同时写日志和内存最近事件队列（最多 200）
- 提供 `recentEvents(limit)` 查询
- 提供 `noop()` 便于测试替身

### 7.2 SecurityAuditController

- `POST /security/audit/fix-apply`：记录修复应用审计
- `GET /security/audit/events`：查询最近审计事件

输入要求：

- `taskId`、`fixId` 必填
- `confirmed` 必须为 true（显式确认）

知识点：
- 高风险操作要“显式确认”而非隐式执行。

---

## 8. Spring 配置注入（RulePackApiConfiguration）

使用 `@Configuration + @Bean` 明确装配：

- `RulePackLocalRepository`（当前是内存实现）
- `RulePackValidator`
- `ObjectMapper`
- `RulePackImportService`

并通过 `@Value` 注入 engine version，支持环境配置。

知识点：
- 通过配置解耦“代码逻辑”和“部署参数”。

---

## 9. 测试如何验证安全链路

重点看 `RulePackImportServiceTest`：

- 校验通过导入成功
- engine 版本不兼容
- checksum 不一致
- 路径越权
- PROD 环境使用 DEV key 导入失败
- 环境兼容列表不包含当前环境
- 失败时是否写审计事件

这些测试覆盖了“供应链安全”的主要失败面。

---

## 10. 常见陷阱与增强建议

- 陷阱1：canonical payload 字段变更导致旧签名失效  
  建议：签名版本化，保留向后兼容策略。

- 陷阱2：permissive 模式误用于生产  
  建议：在 PROD 强制要求 trusted key store 非空。

- 陷阱3：只校验签名，不校验路径权限  
  建议：权限边界必须作为第一关。

- 陷阱4：错误信息过于笼统  
  建议：统一错误码 + 人类可读 message。

---

## 11. 动手练习

1. 为 `RulePackValidator` 增加 `SHA384withRSA` 支持，并补测试。  
2. 增加“规则包撤销列表（revocation list）”检查能力。  
3. 将 `SecurityAuditService` 的最近事件改为可持久化存储。  
4. 在导入 API 增加请求方身份字段并写入审计 metadata。  
