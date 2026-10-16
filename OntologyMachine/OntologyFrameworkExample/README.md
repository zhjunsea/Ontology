# OntologyFrameworkExample — 本体应用框架示例

> **一句话定位**：本模块是 `OntologyMachine` 下**本体应用的参考基线**——以「披萨制作与烘焙」业务场景为例，
> 端到端演示「业务场景 → 流程设计 → 本体（TBox/ABox）→ BPMN → JobWorker → OBDA 入库 → SKOS 词汇表 → 测试」
> 的完整闭环。**所有新本体应用都以本模块为同构模板**（构建流程见文末 §9）。

---

## 1. 这是什么

- **定位**：本体应用**示例工程**，同时是 `ontology-app-builder` 构建技能（14 步流程 / 11 个人工闸门）的**形态基线**；
  新应用的目录布局、命名、文件形态均以本模块为准。
- **业务示例**：Pizza（披萨）制作与烘焙流程——按披萨类型逐项挑选组件、比价、决策，最终产出成本与结果。
- **在框架中的位置**：

```
OntologyMachine/
├── OntopOBDAHandler / OpenlletResolver / Utilities   # 三个基础库（本模块编译期依赖）
├── EnvPrepare                                         # 可执行环境启动工具
├── OntologyFrameworkPlatform                          # 分发模块：把本示例打成工程包/发布包
├── OntologyInstaller                                  # 客户一键安装器（产出发布态 dist zip）
└── OntologyFrameworkExample                           # ★ 本模块：本体应用示例
```

## 2. 项目结构

```
OntologyFrameworkExample/
├── design/                                    # 设计文档（人工闸门确认载体）
│   ├── Example应用-顶层设计.md                 # 概述/范围/应用架构
│   ├── Example应用-流程设计.md                 # 流程设计（闸门①）
│   ├── Example应用-本体设计.md                 # 本体顶层 + 核心设计（闸门②③）
│   └── 披萨制作和烘培的流程是.txt              # 业务原文样例
├── ontology/
│   ├── bpmn/myPizzaBakingProcess.bpmn         # BPMN 流程（processId=PizzaMakingStandardProcess）
│   ├── catalog-v001.xml                       # IRI → 本地文件映射
│   ├── pizza-all.owl                          # 主入口本体（TBox + 典型 ABox）
│   ├── pizza.owl / pizza-components.owl / pizza-components-rules.owl / pizza-processes.owl   # TBox 拆分
│   ├── pizza-abox.owl / pizza-components-abox.owl / pizza-processes-abox.owl                 # ABox 分离
│   ├── pizza-terminology.ttl                  # SKOS 词汇表
│   └── database/{createdb_tables.ddl, data.sql, myPizza.obda, myPizza.properties}            # 库环境 + OBDA 映射
├── src/main/java/com/ocean/ontologyframework/example/
│   ├── ExampleApplication.java                # @SpringBootApplication（激活 Profile=ExampleBPMN）
│   ├── ExampleOntologyJobWorker.java          # JobWorker（jobType：query-property-value-ontology / get-component）
│   └── web/ExampleController.java             # REST（启动流程 / 查询结果）
├── src/main/resources/
│   ├── application.yaml                       # 端口 9083 / Camunda / 本体路径 / 环境项
│   └── static/index.html                      # 前端交互页
├── src/test/java/com/ocean/ontologyframework/example/
│   ├── AllExampleSuiteTest.java               # 总入口套件（@Suite）
│   ├── ExampleBpmnRealEngineTest.java         # BPMN 端到端（真实引擎）
│   └── ExampleSwrlRabbitMqTest.java           # SWRL + 消息
├── .idea/runConfigurations/ExampleApplication.xml   # IDEA 运行配置（Working dir=$MODULE_WORKING_DIR$）
├── start-env.cmd                              # 一键环境脚本（createdb → start → create-rabbitmq）
├── EnvPrepare-0.0.1-SNAPSHOT.jar              # 环境启动器（与 start-env.cmd 同目录；随工程包注入）
├── pom.xml
└── README.md                                  # 本文件
```

## 3. 技术栈与依赖

| 项 | 版本 | 说明 |
|---|---|---|
| Java | 26 | `java.version`（运行需 JDK 26） |
| Spring Boot | 4.1.0 | 父 POM `spring-boot-starter-parent` |
| Camunda / Zeebe 客户端 | 8.9.6 | `spring-boot-starter-camunda-sdk`、`zeebe-process-test-extension`（内存引擎测试） |
| OpenlletResolver / Utilities / OntopOBDAHandler | 0.0.1-SNAPSHOT | `com.ocean` 基础库（推理 / OBDA / 工具） |
| Apache Jena | 6.1.0 | `jena-arq`、`jena-rdfconnection` |
| OWL API | 5.5.1 | `owlapi-parsers` |
| RabbitMQ | — | 可选：`spring-boot-starter-amqp`（消息外发场景） |

**构建插件**：`spring-boot-maven-plugin`（打可执行 fat jar）、`maven-surefire-plugin`（`useManifestOnlyJar=false`）、
`maven-source-plugin`（`jar-no-fork`，产出 `-sources.jar` 供分发参考）。

## 4. 运行前置环境

| 服务 | 端口 | 说明 |
|---|---|---|
| JDK 26 | — | 编译与运行 |
| MySQL | 3306 | 库名 `mypizzadb`（`env-prepare.mysql.db-name`） |
| Camunda 8.x | 26500(gRPC) / 9080(REST) | 流程引擎 |
| Ontop | 8080 | SPARQL 虚拟端点 |
| RabbitMQ | 5672 / 15672 | 可选（低库存告警场景） |

以上服务由 `EnvPrepare` 按 `application.yaml` 一键拉起（见 §6）。

## 5. 构建与打包

### 5.1 依赖基础库

本模块编译期依赖 `com.ocean` 三个基础库。在 reactor 内构建时用 `-am` 一并编译即可；
手工分发场景需先 `mvn install:install-file` 装进本地仓库（详见《新应用开发与部署指南》§3.1）。

### 5.2 打包本应用（产出可执行 jar）

```bash
# 在 OntologyMachine/ 仓库根执行；-am 连带编译三个基础库
./mvnw -pl OntologyFrameworkExample -am package -DskipTests
```

产物（`OntologyFrameworkExample/target/`）：

| 产物 | 说明 |
|---|---|
| `OntologyFrameworkExample-0.0.1-SNAPSHOT.jar` | **可执行 fat jar**（含内嵌依赖，`java -jar` 可直接运行） |
| `OntologyFrameworkExample-0.0.1-SNAPSHOT-sources.jar` | 源码包（仅代码参考，不能当工程导入） |

### 5.3 打包为可分发的「发布包」

本示例对外分发不走手工复制，而由 **`OntologyFrameworkPlatform` 分发模块统一打包**，链路如下：

1. **完整工程包 `project.zip`**（`OntologyFrameworkPlatform` 的 `package-example-project` 产出）：
   `build/OntologyFrameworkExample-0.0.1-SNAPSHOT-project.zip`
   —— 打包本模块全量源码/本体/设计（排除 `target/ logs/ .idea/ out/ *.iml`），并在工程根**注入**
   `EnvPrepare-0.0.1-SNAPSHOT.jar`；解压即标准 Maven 工程，双击 `start-env.cmd` 一键备好环境。
2. **客户发布包（发布态）**：`build/OntologyInstaller-0.0.1-SNAPSHOT-dist.zip`
   （由 `OntologyInstaller` 构建自动产出，即把整个 `build/installer/` 打成单一 zip，对外交付只发这一个文件）。

**完整构建链顺序**（在 `OntologyMachine/` 仓库根执行）：

```bash
./mvnw -pl OntologyFrameworkPlatform -am install -DskipTests   # ① 编译基础库 + 打 project.zip + 清空重建 build/ + 复制分发文档
bash OntologyFrameworkPlatform/tools/gen-offline-repo.sh       # ② 生成离线 Maven 仓库 build/maven-repo
./mvnw -pl OntologyInstaller package -DskipTests               # ③ 重装 build/installer/ 并产出 dist zip
```

## 6. 运行

### 6.1 一键准备并启动环境（推荐）

模块根自带 `start-env.cmd` 与 `EnvPrepare-0.0.1-SNAPSHOT.jar`，**双击 `start-env.cmd`** 依次完成：

1. `createdb` — 建库 + 灌典型 ABox 数据（先 DROP 旧库 → 建表 → 导入 `data.sql`）；
2. `start` — 在**新窗口常驻**拉起 `application.yaml` 中的 RabbitMQ / MySQL / Ontop / Camunda；
3. `create-rabbitmq` — 创建交换机/队列/绑定。

> **停止环境 = 关闭该常驻窗口**（脚本回显「关闭此窗口即停止全部服务」）。

### 6.2 启动应用本体（Worker）

- **IDEA**：运行配置 `ExampleApplication`（主类 `com.ocean.ontologyframework.example.ExampleApplication`）。
  关键设置：`Working directory = $MODULE_WORKING_DIR$`；VM options：
  `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow`。
- **命令行**：

  ```bash
  java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
       -jar target/OntologyFrameworkExample-0.0.1-SNAPSHOT.jar
  ```

### 6.3 访问

浏览器打开 `http://localhost:9083/`（静态页 `index.html`），按页面提示启动流程、查询结果。

## 7. 测试

```bash
./mvnw -pl OntologyFrameworkExample -am test
```

- 总入口：`AllExampleSuiteTest`（聚合 BPMN 端到端、SWRL+消息等）。
- **BPMN 端到端用例需环境已就绪且本应用 Worker 已启动**，否则会因等待 job 而超时。

## 8. 与平台/安装器的关系（发布口径）

- 本模块**不单独发布**；它的分发形态是 `project.zip`（完整工程包）与最终 `dist.zip`（客户发布包）。
- 发布物组成与客户安装方式详见《新应用开发与部署指南》；安装器会把随包的 `maven-repo.zip`（离线仓库）
  与 `ontology-app-builder` SKILL 一并投放/配置到位，使目标机**无网络**也能构建、运行本示例。

## 9. 用本示例创建新应用

推荐用**码道（CodeArts）+「本体应用构建」SKILL（`ontology-app-builder`）**，按 **14 步流程 + 11 个人工闸门**，
把业务场景落地为**与本模块同构、可运行、可验收**的新本体应用。新应用须**含同款 `README.md`**（介绍工程与
打包/发布/运行）并具备**同样的打包配置**（`pom.xml` 含 `spring-boot-maven-plugin` / `surefire(useManifestOnlyJar=false)` /
`maven-source-plugin`），使其可被同样地构建、打包与分发。用法见工程内 `.codeartsdoer/skills/ontology-app-builder/`，
以及《新应用开发与部署指南》§3.0。
