# OntologyFrameworkSiZhuBaZi — 四柱命理（八字推命）本体应用

> **一句话定位**：本模块是 `OntologyMachine` 下**本体应用**——以四柱命理经典「六书」
> （《渊海子平》《子平真诠》《三命通会》《滴天髓》，辅以《穷通宝鉴》《神峰通考》）为依据，
> 将「排四柱 → 纳音藏干 → 干支作用 → 十神六亲 → 神煞空亡 → 日主旺衰 → 格局 → 取用 → 层次 →
> 岁运应期 → 分类批断 → 合婚/女命/小儿专论 → 修身劝诫 → 汇总外发」的批命流程，
> 落地为「流程设计 → 本体（TBox/ABox）→ BPMN → JobWorker → 前端」的可运行闭环。
> **形态基线**：与本仓 `OntologyFrameworkExample/` 同构；同类术数参照 `OntologyFrameworkPlumBlossom/`。

---

## 1. 这是什么

- **定位**：四柱命理（八字推命）应用。用户在前端录入生辰（公历/农历、性别、对象类型、经度、单命/合婚、输出粒度），
  经 Camunda 8 流程编排，由 21 个 JobWorker 逐步完成批命，最终回显排盘（四柱/大运/纳音/藏干）、
  十神六亲、神煞空亡、旺衰、格局、取用（用/相/忌）、层次、岁运应期、分类批断、合婚/女命/小儿专论、
  修身劝诫与告警外发结果。
- **依据（强制）**：自节点 2 起，各环节方法与数据须追溯到经典原文
  `design/六书原文/` 与 `design/六书全文整理/`（渊海子平 / 子平真诠 / 三命通会 / 滴天髓）**具体篇章**；
  合婚依《神峰通考》，调候依《穷通宝鉴》（文本未取得者标【待考】）。

## 2. 项目结构

```
OntologyFrameworkSiZhuBaZi/
├── design/                                        # 设计文档（人工闸门确认载体）
│   ├── SiZhuBaZi应用-顶层设计.md                   # 概述/范围/应用架构
│   ├── SiZhuBaZi应用-流程设计.md                   # 流程设计（闸门①）
│   ├── SiZhuBaZi应用-本体顶层设计.md               # 本体顶层设计（闸门②）
│   ├── SiZhuBaZi应用-本体核心设计.md               # 本体核心设计（闸门③）
│   ├── SiZhuBaZi应用-应用架构设计.md               # 应用架构 + 21 节点契约（闸门④⑩⑪）
│   ├── 六书原文/                                   # 原著（渊海子平 / 子平真诠 / 三命通会 / 滴天髓）
│   └── 六书全文整理/                               # 六书整理稿（业务依据）
├── ontology/
│   ├── bpmn/sizhubaziProcess.bpmn                 # BPMN 流程（processId=SiZhuBaZiProcess，21 ServiceTask + 3 网关）
│   ├── catalog-v001.xml                           # IRI → 本地文件映射
│   └── sizhubazi-all.owl                          # 主入口本体（TBox + 典型 ABox）
│   # 后续步骤产出：TBox 拆分（第 9 步）/ ABox 分离（第 8 步）/ OBDA 库环境（第 11 步）/ SKOS 词汇表（第 13 步）
├── src/main/java/com/ocean/ontologyframework/sizhubazi/
│   ├── SiZhuBaZiApplication.java                   # @SpringBootApplication（激活 Profile=SiZhuBaZiBPMN）
│   ├── SiZhuBaZiOntologyJobWorker.java             # JobWorker（21 个 jobType，见 §4）
│   └── web/SiZhuBaZiController.java                # REST（启动流程 / 查询结果 / 健康检查）
├── src/main/resources/
│   ├── application.yaml                           # 端口 9088 / Camunda / 本体路径 / 环境项
│   └── static/index.html                          # 前端交互页（生辰录入 · 单命/合婚）
├── .idea/runConfigurations/SiZhuBaZiApplication.xml  # IDEA 运行配置（Working dir=$MODULE_WORKING_DIR$）
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
| 1 | `parseBirthInput` | 生辰录入解析与真太阳时校正（公历/农历 · 早晚子时） |
| 2 | `castFourPillars` | 排四柱 · 大运/小运/交运 · 胎元 · 命宫 |
| 3 | `analyzeNayinHiddenStems` | 纳音 · 藏干 · 日柱特性 |
| 4 | `analyzeGanzhiRelations` | 干支作用（五合/六合/三合/冲/穿/刑/墓库） |
| 5 | `assignTenGodsKin` | 十神 · 六亲 · 宫位（十神生克制化） |
| 6 | `arrangeDeities` | 神煞（贵人/禄马/凶煞）· 空亡 |
| 7 | `assessDayMasterStrength` | 日主旺衰 · 寄生十二宫 · 中和 |
| 8 | `determinePattern` | 格局 · 成败救应 · 高低 |
| 9 | `selectYongShen` | 取用（用/相/忌）· 多法并陈 |
| 10 | `judgeChartLevel` | 命局层次 · 清浊真假 |
| 11 | `analyzeFortuneCycles` | 大运/流年 · 特殊岁运格局 |
| 12 | `inferTimingAndStages` | 应期 · 运限法 |
| 13 | `synthesizeFortuneChart` | 岁运与原局综合辩证断 |
| 14 | `generateCategoryVerdicts` | 分类批断（性情/疾病/六亲/分类事项） |
| M1 | `analyzeMarriageMatch` | 合婚（双方复用 2~14 全链 + 互补判定） |
| W | `analyzeFemaleChart` | 女命专论 |
| C | `analyzeChildChart` | 小儿关杀 |
| 15 | `generateMoralAdvice` | 修身处世劝诫 |
| 16 | `aggregateReading` | 汇总批断结果（命书体）· 告警标志 |
| A | `sendAlert` | 外发告警消息（大凶/寿元关/重大疾病） |
| 17 | `persistAndReturn` | 结果落库与返回 |

> 契约详见《SiZhuBaZi应用-应用架构设计》§4；实现方法/算法经闸门④逐节点确认。

## 5. 运行前置环境

| 服务 | 端口 | 说明 |
|---|---|---|
| JDK 26 | — | 编译与运行 |
| MySQL | 3306 | 库名 `sizhubazidb`（第 11 步接入 OBDA 后使用） |
| Camunda 8.x | 26500(gRPC) / 9080(REST) | 流程引擎 |
| Ontop | 8080 | SPARQL 虚拟端点 |
| RabbitMQ | 5672 / 15672 | 重大吉凶外发（`sizhubazi.alert.exchange`） |

以上服务由 `EnvPrepare` 按 `application.yaml` 一键拉起（见 §7）。

## 6. 构建与打包

### 6.1 依赖基础库

本模块编译期依赖 `com.ocean` 三个基础库。在 reactor 内构建时用 `-am` 一并编译即可。

### 6.2 打包本应用（产出可执行 jar）

```bash
# 在 OntologyMachine/ 仓库根执行；-am 连带编译三个基础库
./mvnw -pl OntologyFrameworkSiZhuBaZi -am package -DskipTests
```

产物 `OntologyFrameworkSiZhuBaZi/target/OntologyFrameworkSiZhuBaZi-0.0.1-SNAPSHOT.jar`（可执行 fat jar）。

## 7. 运行

### 7.1 一键准备并启动环境（推荐）

模块根自带 `start-env.cmd` 与 `EnvPrepare-0.0.1-SNAPSHOT.jar`，**双击 `start-env.cmd`** 后在**新窗口常驻**拉起 `application.yaml` 中**已声明**的服务（RabbitMQ / MySQL / Ontop / Camunda **按需**，未声明者自动 `[SKIP]`）：

- 默认 **`start`** — 已声明的持久化服务（MySQL 库 / RabbitMQ 资源）**缺则建、有则留**（不删任何已有数据）；
- 脚本**交互询问是否重建环境**（`[y/N]`）：选 `y` 则以 **`init`** 先清空重建已声明的持久化服务，再启动。

> **停止环境 = 关闭该常驻窗口**。

### 7.2 启动应用本体（Worker）

- **IDEA**：运行配置 `SiZhuBaZiApplication`（主类 `com.ocean.ontologyframework.sizhubazi.SiZhuBaZiApplication`）。
  关键设置：`Working directory = $MODULE_WORKING_DIR$`；VM options：
  `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow`。
- **命令行**：

  ```bash
  java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow \
       -jar target/OntologyFrameworkSiZhuBaZi-0.0.1-SNAPSHOT.jar
  ```

### 7.3 访问

浏览器打开 `http://localhost:9088/`（静态页 `index.html`），填写生辰与批命模式，点击「开始推演」，
前端轮询流程状态，完成后展示批命结果。

## 8. 测试

```bash
./mvnw -pl OntologyFrameworkSiZhuBaZi -am test
```

- 总入口：`AllSiZhuBaZiSuiteTest`（第 8 步产出）。
- **BPMN 端到端用例需环境已就绪且本应用 Worker 已启动**，否则会因等待 job 而超时。

## 9. 构建状态

本模块按 `ontology-app-builder` SKILL 的 14 步流程推进，当前已完成至**第 7 步（应用架构设计与 JobWorker）**。
TBox 拆分（第 9 步）、ABox 分离（第 8 步）、OBDA 入库（第 11 步）、ABox 扩全集（第 12 步）、
SKOS 词汇表（第 13 步）等将在后续步骤产出。
