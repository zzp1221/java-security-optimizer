# Plugin + RulePack + Security 模块方法级学习清单（第三版）

模块目录：

- `src/main/java/com/project/javasecurityoptimizer/plugin`
- `src/main/java/com/project/javasecurityoptimizer/rulepack`
- `src/main/java/com/project/javasecurityoptimizer/security`

---

## 1. 类级阅读顺序

1. `LanguagePlugin`
2. `JavaLanguagePlugin`
3. `PluginManagerService`
4. `RulePackController`
5. `RulePackImportService`
6. `RulePackValidator`
7. `RulePackSecurityContext`
8. `SecurityAuditService`
9. `SecurityAuditController`

---

## 2. PluginManagerService 必读方法

### `normalizeLanguage(String language)`

- 作用：语言标准化
- 关键点：空值默认 `java`

### `resolve(String language)`

- 作用：查找插件实例

### `healthOf(String language)` / `allHealthStatus()`

- 作用：输出健康状态
- 关键点：UNAVAILABLE / DEGRADED / AVAILABLE 三态

### `buildHealthStatus(LanguagePlugin plugin)`

- 作用：根据兼容性和实现完整性组装状态

### `isCompatible(String expectedRange, String currentVersion)`

- 作用：版本范围比较
- 关键点：支持区间表达式

---

## 3. JavaLanguagePlugin 必读方法

### `meta()`

- 作用：插件身份和能力声明

### `analyze(...)`

- 作用：把请求委托给分析引擎

### `validateRulePack(...)`

- 作用：把校验委托给 `RulePackValidator`

---

## 4. RulePackController 必读方法

### `importRulePack(...)`

- 作用：规则包导入 API
- 关键点：文件上传、manifest 解析、临时文件管理、环境参数解析

### `parseManifest(...)`

- 作用：兼容 `manifestJson` 与 `manifestFile`

### `parseEnvironment(String environment)`

- 作用：环境白名单解析（DEV/PROD）

---

## 5. RulePackImportService 必读方法

### `importPack(...)`

- 作用：导入主流程
- 三步：路径授权 -> 规则包校验 -> 安装与审计

关键副作用：
- 失败和成功都会写安全审计事件

---

## 6. RulePackValidator 必读方法（安全核心）

### `validate(...)`

- 作用：总校验入口
- 子流程：checksum -> compatibility -> signature

### `checksum(Path packageFile)`

- 作用：计算 SHA-256

### `verifySignature(...)`

- 作用：执行验签
- 关键点：使用 canonical payload

### `resolveTrustedKey(...)`

- 作用：在非 permissive 模式下做 keyId/算法/公钥一致性检查

### `canonicalPayload(RulePackManifest manifest)`

- 作用：拼装签名原文（字段顺序必须稳定）

---

## 7. SecurityAuditService 必读方法

### `recordRulePackImport(...)`
### `recordTaskCancel(...)`
### `recordFixApply(...)`

- 作用：记录三类关键审计事件

### `recentEvents(int limit)`

- 作用：读取最近审计事件
- 关键点：有上限保护

---

## 8. SecurityAuditController 必读方法

### `recordFixApply(FixApplyAuditRequest request)`

- 作用：记录修复应用
- 关键点：`taskId/fixId/confirmed` 三重校验

### `recentEvents(int limit)`

- 作用：审计查询 API

---

## 9. 方法级常见陷阱

- `canonicalPayload` 字段顺序变化导致签名全量失效。  
- 误把 permissive 模式用于生产。  
- 只校验签名不校验 package 路径授权。  
- 审计接口不要求显式确认导致误操作难追责。  

---

## 10. 改造练习（方法级）

1. 为 `isCompatible` 增加非法版本串的错误诊断码。  
2. 为 `RulePackValidator` 增加新算法并补反向测试。  
3. 给 `recordRulePackImport` 增加 operator/sourceIp 元数据。  
4. 把 `recentEvents` 后端存储替换为持久化仓储。  

---

## 11. 打卡标准

- 能完整口述规则包导入的三道校验关口。  
- 能解释 permissive 与 trusted key store 的差异。  
- 能独立新增一个审计字段并贯穿 controller/service。  
- 能补 1 个安全失败场景测试。  
