# OntologyFrameworkPlatform 发行包

本目录为 `OntologyFrameworkPlatform` 模块构建产出的**本体应用开发平台发行包**，
用于开发与运行类似 `OntologyFrameworkTCM` 的新本体应用。

## 内容

- `OntopOBDAHandler-*.jar`、`OpenlletResolver-*.jar`、`Utilities-*.jar` — 基础库
- `EnvPrepare-*.jar` — 可执行环境启动工具
- `OntologyFrameworkExample-*-project.zip` — 示例**完整工程包**（含 EnvPrepare 可执行 jar 与一键环境脚本 `start-env.cmd`，解压即可导入 IDEA；双击脚本一键备好环境）
- `OntologyFrameworkExample-*-sources.jar` — 示例工程源码（仅看代码参考）
- `lib/` — 第三方依赖集合
- `DEPENDENCIES.txt` / `DEPENDENCY-TREE.txt` — 依赖清单
- `新应用开发与部署指南.md` — **请从此文档开始**

## 快速开始

- **一键环境（推荐）**：解压 `OntologyFrameworkExample-0.0.1-SNAPSHOT-project.zip`，进入工程根，双击 `start-env.cmd`，即自动完成：建库+灌数（createdb）→ 启动环境服务（start，新窗口常驻）→ 创建 RabbitMQ 资源（create-rabbitmq）。
- **手工启动**：

  ```bash
  java -jar EnvPrepare-0.0.1-SNAPSHOT.jar start -c <新应用>/src/main/resources/application.yaml
  ```

详见《新应用开发与部署指南.md》。
