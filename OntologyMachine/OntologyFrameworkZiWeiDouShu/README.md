# OntologyFrameworkZiWeiDouShu — 紫微斗数（陈希夷紫微斗数推命）本体应用

> **一句话定位**：本模块是 `OntologyMachine` 中**本体应用**——以紫微斗数经典《紫微斗數全書》（[宋]陈抟（陈希夷）撰）为依据，
> 将「输入解析 → 定命身宫与十二宫 → 安十四主星 → 安辅佐煞忌 → 安杂曜与太岁诸神 → 安四化·宫干·十二长生 →
> 验盘校正 → 定庙旺利陷 → 星曜化气 → 空宫处理 → 三方四正夹宫 → 格局判定 → 本命综合断 → 女命/小儿专论 →
> 大限/流年/叠宫 → 综合汇总 → 告警外发 → 落库返回」的批命流程，
> 落地于「流程设计 → 本体（TBox/ABox）→ BPMN → JobWorker → 前端」的可运行闭环。
> **形态基线**：与本仓 `OntologyFrameworkExample/`、术数同类 `OntologyFrameworkSiZhuBaZi/` 同构。

---

## 1. 这是什么

- **定位**：紫微斗数推命应用。用户在前端录入生辰（公历/农历、性别、出生地经度、命主类别、直接排盘/验盘、推演深度、运限粒度），
  经 Camunda 8 流程编排，由 21 个 JobWorker 逐步完成安星排盘与批断，最终回显十二宫命盘、本命/大限/流年批断、结构化输出与告警。
- **依据（强制）**：自节点 2 起，各环节方法与数据须溯源到《紫微斗數全書》**具体篇章**（卷一/卷二/卷三），
  语料归档于 `<工作目录>/HWCodeArtsDir/zwds-txt/`。查不到者一律标【待考】。

## 2. 项目结构

```
OntologyFrameworkZiWeiDouShu/
├── design/                                          # 设计文档（人工闸门确认载体）
│   ├── 紫微斗数应用-顶层设计.md
│   ├── 紫微斗数应用-流程设计.md
│   ├── 紫微斗数应用-本体顶层设计.md
│   ├── 紫微斗数应用-本体核心设计.md
│   └── 紫微斗数应用-应用架构设计.md                 # 应用架构 + 21 节点契约（闸门④⑩⑪）
├── ontology/
│   ├── bpmn/ziweidoushuProcess.bpmn                 # BPMN 流程（processId=ZiWeiDouShuChartProcess；21 ServiceTask + 5 网关 + 4 汇合点）
│   ├── catalog-v001.xml                             # IRI → 本地文件映射
│   └── ziweidoushu-all.owl                          # 主入口本体（TBox + 典型 ABox）
│   # 后续步骤产出：ABox 分离（第 8 步）/ TBox 拆分（第 9 步）/ OBDA 库环境（第 11 步）/ SKOS 词汇表（第 13 步）
├── src/main/java/com/ocean/ontologyframework/ziweidoushu/
│   ├── ZiWeiDouShuApplication.java                   # @SpringBootApplication（激活 Profile=ZiWeiDouShuBPMN）
│   ├── ZiWeiDouShuOntologyJobWorker.java             # JobWorker（21 个 jobType，见 §4）
│   └── web/ZiWeiDouShuController.java                # REST（启动流程 / 查询结果 / 健康检查）
├── src/main/resources/
│   ├── application.yaml                             # 端口 9089 / Camunda / 本体路径 / 环境项
│   └── static/index.html                            # 前端交互页（生辰录入 · 直接排盘/验盘）
├── .idea/runConfigurations/ZiWeiDouShuApplication.xml  # IDEA 运行配置（Working dir=$MODULE_WORKING_DIR$）
├── start-env.cmd                                  # 一键环境脚本（start 缺则建有则留 / 交互询问后 init）
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

**构建插件**：`spring-boot-maven-plugin`（可执行 fat jar）、`maven-surefire-plugin`（`useManifestOnlyJar=false`）、
`maven-source-plugin`（`jar-no-fork`，产出 `-sources.jar` 供分发参考）。

## 4. 流程与 JobWorker 契约（21 节点）

| # | jobType | 环节 |
|---|---|---|
| 1 | `parseBirthInput` | 输入解析与时辰校正（公历/农历 · 真太阳时 · 昼夜） |
| 2 | `determineLifePalaces` | 定命身宫与十二宫 · 五行局（纳音） · 命主身主 |
| 3 | `placeMainStars` | 安十四主星（紫微定位表 + 天府） |
| 4 | `placeAuxiliaryStars` | 安辅佐星 · 煞忌星 |
| 5 | `placeMiscStars` | 安杂曜与太岁诸神（流曜基准盘） |
| 6 | `placeFourTransform` | 生年干四化 · 宫干五虎遁 · 十二长生〔飞星轨分野〕 |
| V | `verifyChart` | 验盘校正（条件性任务） |
| 7 | `assessBrightness` | 定庙旺利陷 · 星辰五行生克 · 十二宫弱强〔三合轨〕 |
| 8 | `interpretStarNature` | 星曜化气（静态）与诸星所主 |
| 9 | `handleEmptyPalace` | 空宫处理（借星/借光） |
| 10 | `analyzeSurroundPalaces` | 三方四正 · 夹宫 · 星曜组合〔三合轨〕 |
| 11 | `determinePattern` | 格局判定 · 命格高低 · 四化飞星深层〔飞星轨汇合〕 |
| 12 | `generateNatalReading` | 本命综合断 |
| W | `analyzeFemaleReading` | 女命专论 |
| C | `analyzeChildReading` | 小儿命专论 |
| 13 | `analyzeMajorLimit` | 大限推演 |
| 14 | `analyzeAnnualFortune` | 流年小限推演（按流年干支重算流曜） |
| 15 | `analyzePalaceOverlay` | 叠宫断法与凶格引动 |
| 16 | `aggregateReading` | 综合结论汇总 · 结构化输出 · 告警标志 |
| A | `sendAlert` | 外发告警消息（大凶/寿元关/重大疾病） |
| 17 | `persistAndReturn` | 结果落库（脱敏）与返回 |

> 契约详见《紫微斗数应用-应用架构设计》§4；实现方法/算法经闸门④逐节点确认。

## 5. 运行前置环境

| 服务 | 端口 | 说明 |
|---|---|---|
| JDK 26 | — | 编译与运行 |
| MySQL | 3306 | 库名 `ziweidoushudb`（第 11 步接入 OBDA 后使用） |
| Camunda 8.x | 26500(gRPC) / 9080(REST) | 流程引擎 |
| Ontop | 8080 | SPARQL 虚拟端点 |
| RabbitMQ | 5672 / 15672 | 重大吉凶外发（`ziweidoushu.alert.exchange`） |

以上服务由 `EnvPrepare` 按 `application.yaml` 一键拉起（见 §7）。

## 6. 构建与打包

### 6.1 依赖基础库

本模块编译期依赖 `com.ocean` 三个基础库。在 reactor 内构建时用 `-am` 一并编译即可。

### 6.2 打包本应用（产出可执行 jar）

```bash
# 在 OntologyMachine/ 仓库根执行；-am 连带编译三个基础库
./mvnw -pl OntologyFrameworkZiWeiDouShu -am package -DskipTests
```

产物 `OntologyFrameworkZiWeiDouShu/target/OntologyFrameworkZiWeiDouShu-0.0.1-SNAPSHOT.jar`（可执行 fat jar）。

## 7. 运行

### 7.1 一键准备并启动环境（推荐）

模块根自带 `start-env.cmd` 与 `EnvPrepare-0.0.1-SNAPSHOT.jar`，**双击 `start-env.cmd`** 后在**新窗口常驻**拉起 `application.yaml` 中**已声明**的服务（RabbitMQ / MySQL / Ontop / Camunda **按需**，未声明者自动 `[SKIP]`）：

- 默认 **`start`** —— 已声明的持久化服务（MySQL 库 / RabbitMQ 资源）**缺则建、有则留**（不删任何已有数据）；
- 脚本**交互询问是否重建环境**（`[y/N]`）：选 `y` 则以 **`init`** 先清空重建已声明的持久化服务，再启动。

> **停止环境 = 关闭该常驻窗口**。

### 7.2 启动应用本体（Worker）

- **IDEA**：运行配置 `ZiWeiDouShuApplication`（主类 `com.ocean.ontologyframework.ziweidoushu.ZiWeiDouShuApplication`）。
  关键设置：`Working directory = $MODULE_WORKING_DIR$`；VM options：
  `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow`。
- **命令行**：

  ```bash
  java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
       -jar target/OntologyFrameworkZiWeiDouShu-0.0.1-SNAPSHOT.jar
  ```

### 7.3 访问

浏览器打开 `http://localhost:9089/`（静态页 `index.html`），填写生辰与批命模式，点击「开始推演」，
前端轮询流程状态，完成后展示批命结果。

## 8. 测试

```bash
./mvnw -pl OntologyFrameworkZiWeiDouShu -am test
```

- 总入口：`AllZiWeiDouShuSuiteTest`（第 8 步产出）。
- **BPMN 端到端用例需环境已就绪且本应用 Worker 已启动**，否则会因等待 job 而超时。

## 9. 构建状态

本模块按 `ontology-app-builder` SKILL 的 14 步流程推进，当前已完成至**第 7 步（应用架构设计与 JobWorker）**。
ABox 分离（第 8 步）、TBox 拆分（第 9 步）、OBDA 入库（第 11 步）、ABox 扩至全集（第 12 步）、
SKOS 词汇表（第 13 步）等将在后续步骤产出。
