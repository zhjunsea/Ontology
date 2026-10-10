# OntologyFrameworkPlatform 发行包

本目录为 `OntologyFrameworkPlatform` 模块构建产出的**本体应用开发平台发行包**，
用于开发与运行类似 `OntologyFrameworkTCM` 的新本体应用。

## 内容

- `OntopOBDAHandler-*.jar`、`OpenlletResolver-*.jar`、`Utilities-*.jar` — 基础库
- `EnvPrepare-*.jar` — 可执行环境启动工具（支持 **service** 与 **portable** 两种 MySQL 模式）
- `OntologyFrameworkExample-*-project.zip` — 示例**完整工程包**（含 EnvPrepare 可执行 jar 与一键环境脚本 `start-env.cmd`，解压即可导入 IDEA；双击脚本一键备好环境）
- `OntologyFrameworkExample-*-sources.jar` — 示例工程源码（仅看代码参考）
- `installer/` — **客户一键安装器**（`OntologyInstaller.jar` + `softwares/` + 工程包，见下）
- `lib/` — 第三方依赖集合
- `DEPENDENCIES.txt` / `DEPENDENCY-TREE.txt` — 依赖清单
- `新应用开发与部署指南.md` — **请从此文档开始**

## 快速开始

- **客户一键安装（推荐给最终用户）**：先执行
  `mvn -pl OntologyInstaller package -DskipTests` 得到 `build/installer/`，把整个
  `installer/` 目录拷给客户，双击运行：

  ```bash
  java -jar OntologyInstaller.jar
  ```

  在 Swing 界面选择「软件安装目录」与「开发工作目录」后点击开始，安装器将自动完成：
  拷贝并解压全部软件到安装目录 → 初始化便携式 MySQL（`mysqld --initialize-insecure`）→
  把示例工程解压到工作目录并生成 `application.yaml` / `start-env.cmd`（指向安装目录下各软件 home）。
  完成后进入工作目录双击 `start-env.cmd` 即可一键拉起环境。

- **开发场景一键环境**：解压 `OntologyFrameworkExample-0.0.1-SNAPSHOT-project.zip`，进入工程根，双击 `start-env.cmd`，即自动完成：建库+灌数（createdb）→ 启动环境服务（start，新窗口常驻）→ 创建 RabbitMQ 资源（create-rabbitmq）。
- **手工启动**：

  ```bash
  java -jar EnvPrepare-0.0.1-SNAPSHOT.jar start -c <新应用>/src/main/resources/application.yaml
  ```

详见《新应用开发与部署指南.md》。
