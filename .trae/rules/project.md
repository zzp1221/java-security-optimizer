code-optimizer/
  apps/
    desktop/                 # Tauri 容器（Rust）
    web-ui/                  # React + Monaco（TS）
  services/
    analysis-java/           # JavaParser 分析引擎（Java）
  packages/
    protocol/                # 统一 DTO/Schema（TS + JSON Schema）
    plugin-sdk/              # 语言插件接口（TS定义 + Java定义）
    rule-pack-sdk/           # 规则包 manifest、签名、校验工具
  infra/
    scripts/                 # 构建、打包、签名脚本
    docs/                    # 架构文档、ADR、规则开发手册
  testdata/
    java-large-project/      # 大仓库压测样例
项目目录