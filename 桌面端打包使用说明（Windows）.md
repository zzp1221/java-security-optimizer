# 桌面端打包使用说明（Windows）

本文档用于指导在 Windows 环境下打包本项目桌面端（Tauri 容器）。

## 1. 前置环境

- Windows 10/11 x64
- Rust（stable）与 Cargo
- Visual Studio 2022 Build Tools（勾选 C++ 构建工具与 Windows SDK）
- WebView2 Runtime（Windows 11 通常已内置）

可用以下命令快速检查：

```powershell
rustc -V
cargo -V
```

## 2. 进入目录

```powershell
cd apps/desktop/src-tauri
```

## 3. 调试构建

```powershell
cargo build
```

产物位置：

- `apps/desktop/src-tauri/target/debug/desktop.exe`

## 4. 生产打包

### 方式 A：仅构建发布二进制

```powershell
cargo build --release
```

产物位置：

- `apps/desktop/src-tauri/target/release/desktop.exe`

### 方式 B：生成安装包（推荐）

如果已安装 Tauri CLI，可执行：

```powershell
cargo tauri build
```

常见安装包输出目录：

- `apps/desktop/src-tauri/target/release/bundle/`

## 5. 常见问题

- 缺少 MSVC/SDK：请补装 Visual Studio Build Tools 的 C++ 组件与 Windows SDK。
- WebView2 缺失：安装 Microsoft Edge WebView2 Runtime。
- 首次编译慢：Rust 依赖首次下载和编译耗时较长，属正常现象。
- 权限拦截：请确保本机安全软件未阻止 `cargo`、`rustc` 或产物执行。

## 6. 验收建议

- 启动桌面端后可正常选择项目目录。
- 可触发 `startEngine`、`submitTask`、`cancelTask` 等关键流程。
- 关闭后重启，核心流程仍可正常运行。
