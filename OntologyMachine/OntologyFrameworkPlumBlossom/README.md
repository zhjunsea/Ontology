# OntologyFrameworkPlumBlossom — 梅花易数占断本体应用

> **一句话定位**：本模块是 `OntologyMachine` 下**本体应用**——以《梅花易数》（[宋] 邵康节）原著为依据，
> 将「起卦 → 定体用 → 察互卦 → 观变卦 → 论生克旺衰类象 → 断吉凶定应期参外应」的占断流程，
> 落地为「流程设计 → 本体（TBox/ABox）→ BPMN → JobWorker → 前端」的可运行闭环。
> **形态基线**：与本仓 `OntologyFrameworkExample/` 同构。

---

## 1. 这是什么

- **定位**：梅花易数占断应用。用户在前端按原著起卦法（共 17 类）输入，经 Camunda 8 流程编排，
  由 18 个 JobWorker 逐步完成占断，最终回显本卦/互卦/变卦、体用、生克、旺衰、类象、吉凶、应期、修身建议。
- **依据（强制）**：自节点 2 起，各环节方法与数据须追溯到原著
  `design/故宫珍藏善本：梅花易数 ([宋] 邵康节 著 周浩良 整理).epub`（卷之一~五 + 附录）**具体篇章**；
  辅助依据 `design/梅花易数全面指南.md`。

## 2. 项目结构

```
OntologyFrameworkPlumBlossom/
├── design/                                        # 设计文档（人工闸门确认载体）
│   ├── PlumBlossom应用-顶层设计.md                 # 概述/范围/应用架构
│   ├── PlumBlossom应用-流程设计.md                 # 流程设计（闸门①）
│   ├── PlumBlossom应用-本体顶层设计.md             # 本体顶层设计（闸门②）
│   ├── PlumBlossom应用-本体核心设计.md             # 本体核心设计（闸门③）
│   ├── PlumBlossom应用-应用架构设计.md             # 应用架构 + 18 节点契约（闸门④）
│   ├── 梅花易数全面指南.md                         # 业务整理稿（辅助依据）
│   └── 故宫珍藏善本：梅花易数 (...).epub           # 原著
├── ontology/
│   ├── bpmn/plumBlossomProcess.bpmn               # BPMN 流程（processId=PlumBlossomDivinationProcess，18 ServiceTask + 3 网关）
│   ├── catalog-v001.xml                           # IRI → 本地文件映射
│   └── plumblossom-all.owl                        # 主入口本体（TBox + 典型 ABox）
│   # 后续步骤产出：TBox 拆分（第 9 步）/ ABox 分离（第 8 步）/ OBDA 库环境（第 11 步）/ SKOS 词汇表（第 13 步）
├── src/main/java/com/ocean/ontologyframework/plumblossom/
│   ├── PlumBlossomApplication.java                # @SpringBootApplication（激活 Profile=PlumBlossomBPMN）
│   ├── PlumBlossomOntologyJobWorker.java           # JobWorker（18 个 jobType，见 §4）
│   └── web/PlumBlossomController.java             # REST（启动流程 / 查询结果 / 健康检查）
├── src/main/resources/
│   ├── application.yaml                           # 端口 9084 / Camunda / 本体路径 / 环境项
│   └── static/index.html                          # 前端交互页（按起卦法动态渲染输入）
├── .idea/runConfigurations/PlumBlossomApplication.xml  # IDEA 运行配置（Working dir=$MODULE_WORKING_DIR$）
├── start-env.cmd                                  # 一键环境脚本（createdb → start → create-rabbitmq）
├── EnvPrepare-0.0.1-SNAPSHOT.jar                  # 环境启动器（与 start-env.cmd 同目录；随工程包注入）
├── pom.xml
└── README.md                                      # 本文件
```

## 3. 技术栈与依赖

| 项 | 版本 | 说明 |
|---|---|---|
| Java | 26 | `java.version`（运行需 JDK 26） |
| Spring Boot | 4.1.0 | 父 POM `spring-boot-starter-parent` |
| Camunda / Zeebe 客户端 | 8.9.6 | `spring-boot-starter-camunda-sdk`、`zeebe-process-test-extension` |
| OpenlletResolver / Utilities / OntopOBDAHandler | 0.0.1-SNAPSHOT | `com.ocean` 基础库（推理 / OBDA / 工具） |
| Apache Jena | 6.1.0 | `jena-arq`、`jena-rdfconnection` |
| OWL API | 5.5.1 | `owlapi-parsers` |
| RabbitMQ | — | `spring-boot-starter-amqp`（重大吉凶外发） |

**构建插件**：`spring-boot-maven-plugin`（可执行 fat jar）、`maven-surefire-plugin`（`useManifestOnlyJar=false`）。

## 4. 流程与 JobWorker 契约（18 节点）

| # | jobType | 环节 |
|---|---|---|
| 1 | `preceptCheck` | 心法戒律校验（四不占） |
| 2' | `preceptAlert` | 返回戒律告警 |
| 2 | `routeDivination` | 输入解析与起卦法路由（17 类） |
| 2-字占 | `characterSplit` | 字占细分（按字数分子法） |
| 3 | `castHexagram` | 起卦成卦（卦以八除、爻以六除） |
| 4 | `determineBodyUse` | 定体用（动者为用、静者为体） |
| 5 | `deriveMutualHexagram` | 察互卦（取中间四爻） |
| 6 | `deriveChangedHexagram` | 观变卦（动爻阴阳反转） |
| 7 | `analyzeShengKe` | 五行生克分析（体用 5 级） |
| 8 | `analyzeVitality` | 卦气旺衰分析 |
| 9 | `extractImageSymbol` | 万物类象取象 |
| 10 | `categorizeDivination` | 分类占断 |
| 11 | `analyzeOmen` | 参外应（三要十应） |
| 12 | `judgeAuspiciousness` | 断吉凶·以理统数 |
| 13 | `determineResponsePeriod` | 定应期 |
| 14 | `generateAdvice` | 修身建议生成 |
| 15 | `sendAlert` | 外发告警消息（大吉/大凶） |
| 16 | `aggregateResult` | 汇总占断结果 |

> 契约详见《PlumBlossom应用-应用架构设计》§4；实现方法/算法经闸门④逐节点确认。

## 5. 运行前置环境

| 服务 | 端口 | 说明 |
|---|---|---|
| JDK 26 | — | 编译与运行 |
| MySQL | 3306 | 库名 `plumblossomdb`（第 11 步接入 OBDA 后使用） |
| Camunda 8.x | 26500(gRPC) / 9080(REST) | 流程引擎 |
| Ontop | 8080 | SPARQL 虚拟端点 |
| RabbitMQ | 5672 / 15672 | 重大吉凶外发（`plumblossom.alert.exchange`） |

以上服务由 `EnvPrepare` 按 `application.yaml` 一键拉起（见 §7）。

## 6. 构建与打包

### 6.1 依赖基础库

本模块编译期依赖 `com.ocean` 三个基础库。在 reactor 内构建时用 `-am` 一并编译即可。

### 6.2 打包本应用（产出可执行 jar）

```bash
# 在 OntologyMachine/ 仓库根执行；-am 连带编译三个基础库
./mvnw -pl OntologyFrameworkPlumBlossom -am package -DskipTests
```

产物 `OntologyFrameworkPlumBlossom/target/OntologyFrameworkPlumBlossom-0.0.1-SNAPSHOT.jar`（可执行 fat jar）。

## 7. 运行

### 7.1 一键准备并启动环境（推荐）

模块根自带 `start-env.cmd` 与 `EnvPrepare-0.0.1-SNAPSHOT.jar`，**双击 `start-env.cmd`** 依次完成：
`createdb`（建库灌数）→ `start`（新窗口常驻拉起 RabbitMQ / MySQL / Ontop / Camunda）→ `create-rabbitmq`（建 MQ 资源）。

> **停止环境 = 关闭该常驻窗口**。

### 7.2 启动应用本体（Worker）

- **IDEA**：运行配置 `PlumBlossomApplication`（主类 `com.ocean.ontologyframework.plumblossom.PlumBlossomApplication`）。
  关键设置：`Working directory = $MODULE_WORKING_DIR$`；VM options：
  `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow`。
- **命令行**：

  ```bash
  java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
       -jar target/OntologyFrameworkPlumBlossom-0.0.1-SNAPSHOT.jar
  ```

### 7.3 访问

浏览器打开 `http://localhost:9084/`（静态页 `index.html`），填写占断事项与起卦法，点击「起卦占断」，
前端轮询流程状态，完成后展示占断结果。

## 8. 测试

```bash
./mvnw -pl OntologyFrameworkPlumBlossom -am test
```

- 总入口：`AllPlumBlossomSuiteTest`（第 8 步产出）。
- **BPMN 端到端用例需环境已就绪且本应用 Worker 已启动**，否则会因等待 job 而超时。

## 9. 构建状态

本模块按 `ontology-app-builder` SKILL 的 14 步流程推进，当前已完成至**第 7 步（应用架构设计与 JobWorker，
闸门④已确认）**。TBox 拆分（第 9 步）、ABox 分离（第 8 步）、OBDA 入库（第 11 步）、ABox 扩全集（第 12 步）、
SKOS 词汇表（第 13 步）等将在后续步骤产出。
