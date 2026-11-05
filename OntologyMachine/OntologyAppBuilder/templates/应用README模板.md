# <Xxx> — 本体应用

> **用法**：本模板产出第 7 步（工程骨架）。**照 `OntologyFrameworkExample/README.md` 的形态**生成新应用模块根的
> `README.md`——介绍**工程整体情况**与**打包/发布/运行**方式，使新应用与示例**同构**、可被同样地构建与分发。
> **基线**：`OntologyFrameworkExample/README.md`；`Xxx` = 应用简称，`xxx` = 小写简称。

> **一句话定位**：<应用一句话定位/业务场景概述>。

---

## 1. 这是什么

- **定位**：`OntologyMachine` 下的本体应用模块（与 `OntologyFrameworkExample` 同构）。
- **业务场景**：<用一段话描述业务场景 / 目标 / 范围>。
- **在框架中的位置**：

```
OntologyMachine/
├── OntopOBDAHandler / OpenlletResolver / Utilities   # 三个基础库（编译期依赖）
├── EnvPrepare                                         # 可执行环境启动工具
├── OntologyFrameworkPlatform                          # 分发模块：把应用打成工程包/发布包
├── OntologyInstaller                                  # 客户一键安装器（产出发布态 dist zip）
└── OntologyFramework<Xxx>                             # ★ 本模块
```

## 2. 项目结构

```
OntologyFramework<Xxx>/
├── design/                                    # 设计文档（人工闸门确认载体）
│   ├── <Xxx>应用-顶层设计.md
│   ├── <Xxx>应用-流程设计.md                   # 闸门①
│   ├── <Xxx>应用-本体顶层设计.md               # 闸门②
│   ├── <Xxx>应用-本体核心设计.md               # 闸门③
│   ├── <Xxx>应用-应用架构设计.md               # 闸门④⑩⑪
│   └── <Xxx>应用-测试设计文档.md               # 闸门⑤
├── ontology/
│   ├── bpmn/<xxx>Process.bpmn
│   ├── catalog-v001.xml
│   ├── <xxx>-all.owl                          # 主入口本体（TBox + 典型 ABox）
│   ├── [<xxx>-tbox.owl / <xxx>-abox.owl]      # TBox/ABox 分离（闸门⑧）
│   ├── [<xxx>-*.owl]                          # TBox 拆分（闸门⑨）
│   ├── <xxx>-terminology.ttl                  # SKOS 词汇表（闸门⑥）
│   └── database/{createdb_tables.ddl, data.sql, <xxx>.obda, <xxx>.properties}   # 闸门⑩
├── src/main/java/com/ocean/ontologyframework/<xxx>/
│   ├── <Xxx>Application.java
│   ├── <Xxx>OntologyJobWorker.java
│   └── web/<Xxx>Controller.java
├── src/main/resources/{application.yaml, static/index.html}
├── src/test/java/com/ocean/ontologyframework/<xxx>/{All<Xxx>SuiteTest.java, ...}
├── start-env.cmd
├── EnvPrepare-<version>.jar
├── pom.xml
└── README.md                                  # 本文件
```

## 3. 技术栈与依赖

| 项 | 版本 | 说明 |
|---|---|---|
| Java | <版本> | |
| Spring Boot | <版本> | 父 POM |
| Camunda / Zeebe 客户端 | <版本> | `spring-boot-starter-camunda-sdk`、`zeebe-process-test-extension` |
| OpenlletResolver / Utilities / OntopOBDAHandler | 0.0.1-SNAPSHOT | `com.ocean` 基础库 |
| 其他 | <版本> | |

**构建插件**（与示例一致，保证可打包/可分发）：`spring-boot-maven-plugin`（可执行 fat jar）、
`maven-surefire-plugin`（`useManifestOnlyJar=false`）、`maven-source-plugin`（`jar-no-fork`，产出 `-sources.jar`）。

## 4. 运行前置环境

| 服务 | 端口 | 说明 |
|---|---|---|
| JDK | — | |
| MySQL | 3306 | 库名 <...> |
| Camunda 8.x | 26500 / 9080 | |
| Ontop | 8080 | |
| RabbitMQ | 5672 / 15672 | 可选 |

> **按需**：上表为可用服务全集；具体应用**只声明并启动自己真正使用的服务**——未用的在 `application.yaml` 的 `env-prepare` 中**整块注释/留空**，EnvPrepare 自动 `[SKIP]`（例如仅用 Camunda 的应用不拉起 MySQL/Ontop/RabbitMQ）。

## 5. 构建与打包

### 5.1 依赖基础库

<reactor 内 `-am` 连带编译；或手工 `mvn install:install-file` 装进本地仓库，见《新应用开发与部署指南》§3.1。>

### 5.2 打包本应用

```bash
./mvnw -pl OntologyFramework<Xxx> -am package -DskipTests
```

产物：`target/OntologyFramework<Xxx>-<version>.jar`（可执行 fat jar）+ `-sources.jar`。

### 5.3 打包为可分发的「发布包」

<说明本应用如何被分发模块打成工程包 project.zip 与客户发布包 dist.zip；构建链顺序：platform install → gen-offline-repo → installer package。>

## 6. 运行

### 6.1 一键准备并启动环境（推荐）

模块根自带 `start-env.cmd` 与 `EnvPrepare-<version>.jar`，**双击 `start-env.cmd`** 即启动环境：
默认以 `start` 拉起 `application.yaml` 中**已声明**的服务（MySQL / Ontop / RabbitMQ / Camunda **按需**，未声明者自动 `[SKIP]`），对**已声明的持久化服务**（MySQL 库 / RabbitMQ 资源）**缺则建、有则留**；脚本会先询问是否**重建环境**，
选是则以 `init`（**清空重建已声明的持久化服务**）后启动。**停止环境 = 关闭该常驻窗口**。

### 6.2 启动应用本体（Worker）

- **IDEA**：运行配置 `<Xxx>Application`（`Working directory = $MODULE_WORKING_DIR$`；VM options 按需）。该运行配置位于**工作区根（IDEA 工程根）** `.idea/runConfigurations/`，**不在本模块目录内**（IDEA 只加载工程根，模块内不可见）；同目录还有测试套件入口 `All<Xxx>SuiteTest`。两者 Shorten command line 均为 `ARGS_FILE`（主类配置为 `SHORTEN_COMMAND_LINE`，JUnit 套件为 `shortenClasspath`）。
- **命令行**：

  ```bash
  java -jar target/OntologyFramework<Xxx>-<version>.jar
  ```

### 6.3 访问

浏览器打开 `http://localhost:<端口>/`。

## 7. 测试

```bash
./mvnw -pl OntologyFramework<Xxx> -am test
```

- 总入口：`All<Xxx>SuiteTest`（IDEA 中对应工作区根 `.idea/runConfigurations/All<Xxx>SuiteTest.xml`，`Working directory = $MODULE_WORKING_DIR$`、`shortenClasspath=ARGS_FILE`）。
- **BPMN 端到端用例需环境已就绪且本应用 Worker 已启动。**

## 8. 与平台/安装器的关系（发布口径）

<同示例：本模块不单独发布，分发形态为 project.zip 与 dist.zip；安装器投放离线仓库与 SKILL。>

## 9. 参考资料

- 构建流程：`ontology-app-builder` SKILL（14 步 / 11 闸门）。
- 分发与安装：《新应用开发与部署指南》。
