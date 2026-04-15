# Plugin + RulePack + Security 源码精读（第二版）

主目录：

- `src/main/java/com/project/javasecurityoptimizer/plugin`
- `src/main/java/com/project/javasecurityoptimizer/rulepack`
- `src/main/java/com/project/javasecurityoptimizer/security`

核心主线：  
**请求进入后，系统如何选择插件、校验规则包可信性、并记录关键安全操作。**

---

## 1. 插件子系统：先判定“能不能干”

入口类：`PluginManagerService`

### 1.1 启动时做了什么

- 构造器里注册内建插件（Java + Cpp placeholder）
- `@PostConstruct` 执行 `startupHealthCheck` 生成健康快照

### 1.2 请求来时做了什么

- `normalizeLanguage`：语言规范化（空值默认 java）
- `resolve(language)`：查找插件
- `healthOf(language)`：生成可用性状态

可用性判定由 `buildHealthStatus` 决定：

- 版本不兼容 -> `UNAVAILABLE`
- 仅占位实现 -> `DEGRADED`
- 实现完整且兼容 -> `AVAILABLE`

---

## 2. JavaLanguagePlugin：薄代理设计

这个类很短，但很关键：

- `analyze` 委托给 `JavaAnalysisEngine`
- `validateRulePack` 委托给 `RulePackValidator`

好处：

- 插件层不重复实现分析和校验
- 便于复用现有核心能力
- 后续替换具体实现时影响面小

---

## 3. 规则包导入调用链（从 API 到安装）

推荐按这条线读：

1. `RulePackController.importRulePack`
2. `RulePackImportService.importPack`
3. `RulePackValidator.validate`
4. `RulePackLocalRepository.install`
5. `SecurityAuditService.recordRulePackImport`

这是一条典型“输入校验 -> 安全校验 -> 状态落库 -> 审计记录”的企业链路。

---

## 4. RulePackController：协议层要点

### 4.1 manifest 输入兼容

- 支持 `manifestJson`（字符串）或 `manifestFile`（上传文件）
- 二者至少一个必填

### 4.2 上传文件处理

- 写入临时文件
- 构造 `RulePackSecurityContext`
- finally 清理临时文件（best effort）

### 4.3 错误返回

- 参数问题返回 `400`
- IO 问题返回 `500`
- 校验失败返回结构化错误对象（含 errorCode）

---

## 5. RulePackImportService：业务编排器

`importPack` 分三关：

### 第一关：路径授权

- `securityContext.isPackagePathAuthorized(packageFile)`
- 不通过直接 `PERMISSION_OUT_OF_BOUNDS`

### 第二关：规则包校验

- 调用 `validator.validate(...)`
- 包含 checksum、兼容性、签名三组校验

### 第三关：安装与审计

- `localRepository.install(manifest)`
- `recordRulePackImport(..., success=true, ...)`

失败也会记审计，这对安全追踪非常关键。

---

## 6. RulePackValidator：逐段精读

### 6.1 checksum 校验

- 文件读字节
- `MessageDigest.getInstance("SHA-256")`
- 与 manifest 中的 checksum 比较

### 6.2 兼容性校验

- `EngineVersionRange.parse(...).contains(currentEngineVersion)`
- `supportsEnvironment(environment)` 判断环境白名单

### 6.3 签名校验

- `canonicalPayload(manifest)` 生成固定串
- Base64 解码签名
- 解析公钥（RSA/EC）
- `Signature.verify(...)`

### 6.4 Trusted Key 绑定

非 permissive 模式下必须：

- keyId 在可信仓中
- 算法一致
- 公钥一致

这避免了“随便给个能验过的公钥”这种绕过。

---

## 7. SecurityAuditService：操作可追踪

### 7.1 支持的审计动作

- `RULE_PACK_IMPORT`
- `TASK_CANCEL`
- `FIX_APPLY`

### 7.2 存储策略

- 日志系统：长期留痕
- 内存双端队列：快速查询最近事件（最多 200）

### 7.3 NOOP 模式

- `SecurityAuditService.noop()` 方便测试环境不产生噪声

---

## 8. SecurityAuditController：高风险操作确认

`/fix-apply` 接口要求：

- `taskId` 非空
- `fixId` 非空
- `confirmed == true`

这是典型“显式确认”机制，防止误触发敏感操作。

---

## 9. 配置层：RulePackApiConfiguration

用 `@Bean` 装配：

- validator
- repository
- importService
- objectMapper

并通过 `@Value` 读取 engine version（默认 `1.1.0`）。

这体现了“同一业务，不同环境参数可配置”的工程原则。

---

## 10. 测试怎么读最有效

`RulePackImportServiceTest` 建议按“失败优先”阅读：

1. engine 版本不兼容
2. checksum 不匹配
3. 路径越权
4. DEV key 在 PROD 使用
5. 环境不兼容
6. 拒绝时是否写审计

读完你会对安全链路的防线层次非常清楚。

---

## 11. 常见改造入口

- 增加新签名算法：`keyFactoryAlgorithm` + 测试
- 强化信任体系：`RulePackTrustedKeyStore`（支持证书链）
- 增加导入来源身份：controller 增字段，audit metadata 落地
- 提升可观测性：审计事件接入外部存储和检索

---

## 12. 推荐练习

1. 增加签名算法 `SHA384withRSA`，并补充正反测试。  
2. 为 `RulePackController` 增加导入人字段并写入审计 metadata。  
3. 把最近审计事件从内存队列迁移到持久化仓储。  
4. 给 `healthOf` 增加诊断码，方便前端细粒度提示。  
