# OntologyFrameworkIndustryInsight — 行业洞察本体应用

> **一句话定位**：本模块是一个**可运行的本体应用**——以「行业洞察（五看三定）」业务场景为例，
> 演示「业务场景 → 流程设计 → 本体（TBox/ABox）→ BPMN → JobWorker → OBDA 入库 → SKOS 词汇表 → 测试」
> 的完整闭环。构建流程同构于参考基线 `OntologyFrameworkExample`（见文末 §9）。

---

## 1. 这是什么

- **业务场景**：用户输入一段「洞察场景」文本（任意行业/领域），应用按华为**「五看三定」**方法论：
  看行业/趋势、看市场/客户、看竞争、看自己、看机会 → 机会点 SPAN 定位与优先级 → 交叉验证与关键假设 →
  定战略控制点、定目标、定策略，最终产出**结构化洞察报告**（Markdown），高优先级机会/关键风险经消息通道外发。
- **在框架中的位置**：

```
OntologyMachine/
├── OntopOBDAHandler / OpenlletResolver / Utilities   # 三个基础库（本模块编译期依赖）
├── EnvPrepare                                         # 可执行环境启动工具
├── OntologyFrameworkPlatform                          # 分发模块：把参考示例打成工程包/发布包
├── OntologyInstaller                                  # 客户一键安装器
└── OntologyFrameworkIndustryInsight                   # ★ 本模块：行业洞察本体应用
```

## 2. 项目结构

```
OntologyFrameworkIndustryInsight/
├── design/                                    # 设计文档（人工闸门确认载体）
│   ├── IndustryInsight应用-顶层设计.md         # 概述/范围/应用架构
│   ├── IndustryInsight应用-流程设计.md         # 流程设计（闸门①）
│   ├── IndustryInsight应用-本体顶层设计.md     # 本体顶层设计（闸门②）
│   ├── IndustryInsight应用-本体核心设计.md     # 本体核心设计（闸门③）
│   └── IndustryInsight应用-应用架构设计.md     # 应用架构 + 节点契约（闸门④）
├── ontology/
│   ├── bpmn/industryInsightProcess.bpmn       # BPMN 流程（processId=IndustryInsightProcess，15 serviceTask + 2 网关）
│   ├── industryinsight-all.owl                # 主入口本体（TBox + 约束 + SWRL + 典型 ABox）
│   └── catalog-v001.xml                       # IRI → 本地文件映射
├── src/main/java/com/ocean/ontologyframework/industryinsight/
│   ├── IndustryInsightApplication.java        # @SpringBootApplication（激活 Profile=IndustryInsightBPMN）
│   ├── IndustryInsightOntologyJobWorker.java  # JobWorker（15 个 @JobWorker，与 BPMN 节点一一对应）
│   └── web/IndustryInsightController.java     # REST（启动流程 / 查询结果 / 健康检查）
├── src/main/resources/
│   ├── application.yaml                       # 端口 9087 / Camunda / 本体路径 / 环境项
│   └── static/index.html                      # 前端交互页
├── start-env.cmd                              # 一键环境脚本（GBK + CRLF）
├── EnvPrepare-0.0.1-SNAPSHOT.jar              # 环境启动器（与 start-env.cmd 同目录）
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
| RabbitMQ | — | 关键机会/风险消息外发（`spring-boot-starter-amqp`） |

**构建插件**：`spring-boot-maven-plugin`（打可执行 fat jar）、`maven-surefire-plugin`（`useManifestOnlyJar=false`）、
`maven-source-plugin`（`jar-no-fork`，产出 `-sources.jar` 供分发参考）。

## 4. 运行前置环境

| 服务 | 端口 | 说明 |
|---|---|---|
| JDK 26 | — | 编译与运行 |
| MySQL | 3306 | 库名 `industryinsight`（`env-prepare.mysql.db-name`） |
| Camunda 8.x | 26500(gRPC) / 9080(REST) | 流程引擎 |
| Ontop | 8081 | SPARQL 虚拟端点（`env-prepare.ontop-port`） |
| RabbitMQ | 5672 / 15672 | 关键机会/风险消息外发 |

以上服务由 `EnvPrepare` 按 `application.yaml` 一键拉起（见 §6）。

## 5. 构建与打包

```bash
# 在 OntologyMachine/ 仓库根执行（离线：加 -o）
./mvnw -pl OntologyFrameworkIndustryInsight -am package -o -DskipTests
```

产物（`OntologyFrameworkIndustryInsight/target/`）：

| 产物 | 说明 |
|---|---|
| `OntologyFrameworkIndustryInsight-0.0.1-SNAPSHOT.jar` | **可执行 fat jar**（`java -jar` 可直接运行） |
| `OntologyFrameworkIndustryInsight-0.0.1-SNAPSHOT-sources.jar` | 源码包（仅代码参考，不能当工程导入） |

## 6. 运行

### 6.1 一键准备并启动环境（推荐）

模块根自带 `start-env.cmd` 与 `EnvPrepare-0.0.1-SNAPSHOT.jar`，**双击 `start-env.cmd`** 即可在
**新窗口常驻**拉起 `application.yaml` 中的 MySQL / RabbitMQ / Ontop / Camunda（缺库/缺队列自动新建）。

> **停止环境 = 关闭该常驻窗口**（脚本回显「关闭此窗口即停止全部服务」）。

### 6.2 启动应用本体（Worker）

- **IDEA**：运行配置 `IndustryInsightApplication`（主类 `com.ocean.ontologyframework.industryinsight.IndustryInsightApplication`）。
  关键设置：`Working directory = $MODULE_WORKING_DIR$`；VM options：
  `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow`。
- **命令行**：

  ```bash
  java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
       -jar target/OntologyFrameworkIndustryInsight-0.0.1-SNAPSHOT.jar
  ```

> **LLM 通道**：各分析节点经 `Utilities.ChatAgent`（Spring AI `ChatClient`）调用 MaaS；
> 未配置 `spring.ai.openai.api-key` 时自动降级（确定性框架/模板、结论置【待考】），**流程不阻断**。

### 6.3 访问

浏览器打开 `http://localhost:9087/`（静态页 `index.html`），输入「洞察场景」启动流程、轮询查询结果。

## 7. 测试

```bash
./mvnw -pl OntologyFrameworkIndustryInsight -am test -o
```

- 总入口：`AllIndustryInsightSuiteTest`（聚合 BPMN 端到端、SWRL + 消息、本体合规、SKOS 用例）。
- **BPMN 端到端用例需环境已就绪且本应用 Worker 已启动**，否则会因等待 job 而超时。

## 8. 与平台/安装器的关系（发布口径）

- 本模块**不单独发布**；分发形态由 `OntologyFrameworkPlatform` / `OntologyInstaller` 统一打包。
- 相关构建链与客户安装方式详见《新应用开发与部署指南》。

## 9. 构建来源（本体应用构建技能）

本应用由**码道（CodeArts）+「本体应用构建」SKILL（`ontology-app-builder`）**按 **14 步流程 + 11 个人工闸门**
构建，形态同构于参考基线 `OntologyFrameworkExample`。用法见工程内 `.codeartsdoer/skills/ontology-app-builder/`。
