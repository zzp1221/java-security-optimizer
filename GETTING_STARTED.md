# Java Security Optimizer - 快速开始

本项目是离线代码安全优化器，包含后端分析引擎、前端工作台和桌面容器。

## 1. 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+（前端）

## 2. 拉取与安装

```bash
git clone git@github.com:zzp1221/java-security-optimizer.git
cd java-security-optimizer
```

## 3. 启动后端

```bash
mvn spring-boot:run
```

默认端口：`http://localhost:8080`

## 4. 启动前端（可选）

```bash
cd apps/web-ui
npm install
npm run dev
```

默认端口：`http://localhost:5173`

## 5. 运行测试

```bash
mvn test
```

## 6. 常见目录

- `src/main/java`：后端核心实现
- `src/test/java`：测试代码
- `apps/web-ui`：前端工作台
- `apps/desktop`：桌面容器（Tauri）
