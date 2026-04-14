# Desktop 容器（Tauri v2）

本目录实现了 `01-桌面容器模块` 的第一批交付物：

- 命令：
  - `selectProjectDir`
  - `startEngine`
  - `submitTask`
  - `cancelTask`
- sidecar 生命周期管理：
  - 单实例启动
  - 异常退出自动重启
  - 状态事件 `engine-status`
- 目录权限白名单：
  - 仅允许 `selectProjectDir` 授权后的目录用于后续任务

并已补充 `04-安全加固与权限治理（第三批）` 的关键安全措施：

- 敏感路径拦截：禁止将系统敏感目录加入授权白名单。
- 默认禁网：`startEngine` 仅允许 loopback `baseUrl`（`127.0.0.1/localhost/::1`）。
- 风险动作阻断：仅允许 `java/java.exe` 作为 sidecar 命令，并阻断高风险启动参数（如 `-javaagent`）。
- 操作确认：`cancelTask` 需要 `confirmText` 且必须等于 `CANCEL:{taskId}`。

## 目录结构

- `src-tauri/src/lib.rs`：命令、状态管理、sidecar 进程监督逻辑
- `src-tauri/tauri.conf.json`：Tauri 配置
- `src-tauri/capabilities/default.json`：默认能力声明

## 命令请求示例

```ts
// startEngine
{
  projectDir: "D:/workspace/repo",
  command: "java",
  args: ["-jar", "./services/analysis-java/target/analysis-java.jar"],
  baseUrl: "http://127.0.0.1:18765"
}
```

```ts
// submitTask
{
  projectDir: "D:/workspace/repo",
  payload: {
    taskId: "task-001",
    mode: "incremental"
  }
}
```

```ts
// cancelTask
{
  taskId: "task-001",
  confirmText: "CANCEL:task-001"
}
```
