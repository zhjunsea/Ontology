# NEInsight 应用 — 应用架构设计

> **用法**：本文件为第 7 步产出。**闸门④**：业务人员对 **BPMN 的每个节点**逐项确认「具体流程与算法」后方可实现对应 JobWorker。
> **基线**：`OntologyFrameworkExample/design/Example应用-顶层设计.md` §3–7；代码基线 `ExampleOntologyJobWorker.java`、`ExampleApplication.java`、`web/ExampleController.java`、`src/main/resources/application.yaml`。

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkNEInsight / NEInsight |
| 文档日期 | 2026-10-12 |
| 关联流程设计 | 《NEInsight应用-流程设计》（定稿 v1 / 闸门①） |
| 关联本体设计 | 《NEInsight应用-本体顶层设计》（闸门②） + 《NEInsight应用-本体核心设计》（闸门③） |
| 关联流程文件 | `ontology/bpmn/neInsightProcess.bpmn` |
| 激活 Profile | `NEInsightBPMN` |
| Worker 类 | `NEInsightOntologyJobWorker` |

> 依据：定稿流程设计 + 已确认本体设计 + 实际生成的 BPMN。**算法一律来自业务确认，不得臆造。** 凡数值/阈值标 **【待考】**。

---

## 1. 架构总览与分层

```
┌─────────── 前端层 ─────────────┐
│ static/index.html（洞察场景输入）│
└───────────▼────────────────────┘
┌─────────── API 层 ─────────────┐
│ NEInsightController（启动/查询） │
└───────────▼────────────────────┘
┌─────────── 编排层（Zeebe）──────┐
│ neInsightProcess.bpmn           │
│ (NewEnergyInsightProcess)       │
└───────────▲────────────────────┘
            │ jobType 字面量绑定
┌───────────┴── 应用层 ──────────┐
│ NEInsightOntologyJobWorker(10)  │
│ NEInsightApplication            │
└──┬──────────┬──────────┬───────┘
┌──▼───────┐┌─▼────────┐┌─▼──────┐
│本体层     ││数据层    ││消息层   │
│OWL/SWRL  ││Ontop→DB  ││RabbitMQ │
└──────────┘└──────────┘└────────┘
```

## 2. 技术栈与模块依赖

### 2.1 关键依赖（对齐 `OntologyFrameworkExample/pom.xml`）
| 依赖 | 版本 | 作用 |
|---|---|---|
| `spring-boot-starter-parent` | 4.1.0 | 父 POM |
| `spring-boot-starter` / `-web` | 4.1.0(父) | 容器与 Web |
| `io.camunda:spring-boot-starter-camunda-sdk` | 8.9.6 | Camunda 8 / Zeebe 客户端 |
| `io.camunda:zeebe-process-test-extension` | 8.9.6 | 内存引擎端到端测试 |
| `com.ocean:OpenlletResolver` | 0.0.1-SNAPSHOT | OWL API + Openllet 推理 |
| `com.ocean:OntopOBDAHandler` | 0.0.1-SNAPSHOT | Ontop OBDA → MySQL |
| `com.ocean:Utilities` | 0.0.1-SNAPSHOT | ProcessOrchestrator / RabbitMqHandler / OntologyWorkerSupport |
| `spring-boot-starter-amqp` | 4.1.0 | RabbitMQ |
| `org.apache.jena:jena-arq` / `jena-rdfconnection` | 6.1.0 | RDF 处理 |

> `OntopOBDAHandler` 依赖须保留 `log4j-slf4j2-impl` 排除（对齐 Example）。

### 2.2 模块间依赖
```
OntologyFrameworkNEInsight (NEInsight)
        ├── OpenlletResolver
        ├── OntopOBDAHandler
        └── Utilities
```

## 3. 组件与包结构

### 3.1 主代码（`src/main/java/com/ocean/ontologyframework/neinsight/`）
| 文件 | 职责 |
|---|---|
| `NEInsightApplication.java` | `@SpringBootApplication` + `@ComponentScan("com.ocean")`；`setAdditionalProfiles("NEInsightBPMN")` |
| `NEInsightOntologyJobWorker.java` | `@Component @Profile("NEInsightBPMN")`，继承 `OntologyWorkerSupport`；10 个 `@JobWorker`（原 13 个，随改造移除 3 个域扫描 Worker） |
| `web/NEInsightController.java` | `@RestController`；POST `/api/neinsight/insight` 启动；GET `/api/neinsight/insight/{key}` 查询；GET `/api/neinsight/health` |

### 3.2 前端（`src/main/resources/static/`）
| 文件 | 职责 |
|---|---|
| `index.html` | **输入洞察场景（文本）** → 启动流程 → 每秒轮询 → 展示洞察摘要、实例 IRI、本体验证状态与洞察 SKILL 路径 |

## 4. BPMN 节点 ⇄ JobWorker 契约表（核心）

> **一一对应**：BPMN 每个 `serviceTask` 的 `zeebe:taskDefinition type` ⇄ 一个 `@JobWorker(type)`。此表由 `scripts/validate_structure.py` 做机检。

| # | BPMN 节点 id | jobType | 业务规则（来自流程设计 §3） | 算法（业务确认） | 输入变量 | 输出变量 | 确认状态 |
|---|---|---|---|---|---|---|---|
| 1 | `Task_ScanIndustryTrend` | `scan-industry-trend` | 看行业/趋势（S1–S4） | 查检索根域下 `IndustryTrend` 及其 `indicatesValueTransfer`/`hasMacroFactor`，标注「趋势＞优势」价值转移 | `insightScenario` | `industryTrendResult` | ☐待确认 |
| 2 | `Task_ScanMarketCustomer` | `scan-market-customer` | 看市场/客户（S5） | 查 `Market`/`Customer` + `hasPainPoint`/`hasNeed`，汇总客户画像与痛点/需求 | `insightScenario` | `marketCustomerResult` | ☐待确认 |
| 3 | `Task_ScanCompetition` | `scan-competition` | 看竞争 | 查 `CompetitiveLandscape` + `involvesCompetitor→Competitor`，汇总竞品（【待考】不指名，取 label） | `insightScenario` | `competitionResult` | ☐待确认 |
| 4 | `Task_ScanSelf` | `scan-self` | 看自己（S6） | 查 `SelfAssessment` + `hasCapability`/`hasShortBoard`，汇总能力评分与短板 | `insightScenario` | `selfAssessmentResult` | ☐待确认 |
| 5 | `Task_ScanOpportunity` | `scan-opportunity` | 看机会（SPAN 定位） | 查检索根域 `Opportunity` 的 `marketAttractiveness`/`competitivePosition`/`hasSpanQuadrant`；经推理（SWRL R1/R1b）得 `opportunityPriority` | `insightScenario` | `opportunityResult`,`opportunityPriority` | ☐待确认 |
| 6 | `Task_MarkHighPriority` | `mark-high-priority` | 高优先级标记 + 外发 | 读 `opportunityPriority`；HIGH 时经 SWRL R2 推 `flaggedForAlert=true`，并经 `RabbitMqHandler` 外发告警 | `insightScenario` | `alertDispatched` | ☐待确认 |
| 7 | `Task_DefineControlPoint` | `define-control-point` | 定战略控制点 | 针对高优先级机会点（`addressesOpportunity`），从枚举 `ControlPointType`{技术领先/成本优势/品牌/市场份额/价值链控制} 选定控制点 | `insightScenario` | `controlPointResult` | ☐待确认 |
| 8 | `Task_DefineObjective` | `define-objective` | 定目标（SMART） | 依控制点制定目标；经 SWRL R3 校验 `smartCompliant`（M/R/T 齐备） | `insightScenario` | `objectiveResult`,`smartCompliant` | ☐待确认 |
| 9 | `Task_DefineStrategy` | `define-strategy` | 定策略 | 依目标制定策略（`OrgStrategy`/`ProductStrategy`/`MarketStrategy`），`hasStrategy` 关联 | `insightScenario` | `strategyResult` | ☐待确认 |
| 10 | `Task_Summarize` | `summarize-insight` | 汇总输出 | 生成**围绕输入场景**的「洞察报告生成 SKILL」——以「角色设定 + 任务指令」为骨架、按「五看三定」逐节点内嵌 ①本体 ABox 实例数据 ②🔎 联网检索指令（检索关键词/信息源/需采集字段）③🧮 数据加工与分析要求，落盘 `skill/neinsight-<instanceKey>.md` 供大模型真正联网检索、加工、分析后生成洞察报告；Openllet 一致性校验；回写摘要/实例 IRI/校验状态/SKILL 路径 | `insightScenario` | `insightSummary`,`instanceIri`,`ontologyValidationStatus`,`insightSkillPath` | ☐待确认 |

> 1 个排他网关：`Gateway_PriorityChoice`（`opportunityPriority = "HIGH"` / `!= "HIGH"`），条件互斥且完备。原 `Gateway_DomainChoice`（洞察域选择）已随改造移除。
>
> **输入与检索根域**：流程唯一业务输入为 `insightScenario`（洞察场景文本）。域概念并入场景表达后不再由用户选择；Worker 内部检索根域固定为**联合域 `COMBINED`**（覆盖 IT 空间与云空间全部要素），沿 `hasElement` 展开的 ABox/OBDA 检索口径**保持不变**。原 3 个域扫描 Worker（`scan-it-space`/`scan-cloud-space`/`scan-combined-space`）与 `domainBaseline` 变量已移除。

## 5. 逐节点算法确认（闸门④）

> 对**每个节点**记录：确认的问题、业务给出的算法口径、边界处理。**未确认不得实现该 Worker。** 待业务填写。

| 节点 id | 确认问题 | 业务结论（算法口径） | 边界/异常处理 | 确认人 | 日期 |
|---|---|---|---|---|---|
| `Task_ScanIndustryTrend` | 趋势与价值转移如何取？ | 待确认 | 无价值转移则不标注 | | |
| `Task_ScanMarketCustomer` | 痛点/需求口径？ | 待确认 | 无痛点 → 记录为空（注意 `Customer` min 1 约束） | | |
| `Task_ScanCompetition` | 竞品是否指名？ | 待确认（【待考】：不指名，仅取 label） | 无竞品 → 空表 | | |
| `Task_ScanSelf` | 能力评分量纲？ | 待确认（【待考】） | 无能力项 → 空 | | |
| `Task_ScanOpportunity` | SPAN 分档阈值？ | 待确认（【待考】，暂用 3.0 占位，由 SWRL R1/R1b 判定） | 缺两轴评分 → 优先级不推断 | | |
| `Task_MarkHighPriority` | 外发触发条件与通道？ | 待确认（高优先级机会点/关键风险 → `neinsight.opportunity.exchange`） | 非 HIGH 不进入本节点（网关②保证） | | |
| `Task_DefineControlPoint` | 控制点选择规则？ | 待确认 | 无机会点 → 报错/兜底 | | |
| `Task_DefineObjective` | SMART 判据量纲？ | 待确认（【待考】） | 不合 SMART → `smartCompliant=false`，不阻断 | | |
| `Task_DefineStrategy` | 策略维度口径？ | 待确认 | 无目标 → 报错 | | |
| `Task_Summarize` | 展示/留档口径？ | 认可（实现口径：以「角色设定 + 任务指令」为骨架 + 逐节点内嵌 ①本体 ABox 实例数据 ②联网检索指令 ③数据加工与分析要求 → 生成「给大模型生成洞察报告的 SKILL」落盘 `skill/neinsight-<instanceKey>.md`） | SKILL 写盘失败 → 仅记日志、不阻断流程；一致性校验失败 → `ontologyValidationStatus=INCONSISTENT` | | |

## 6. 前端与 API 设计

| 端点 | 方法 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| `/api/neinsight/insight` | POST | `{insightScenario}` | `{instanceKey, processId, insightScenario}` | 启动洞察流程；场景为空则 400 |
| `/api/neinsight/insight/{key}` | GET | path `key` | `{instanceKey, status, variables, insightSummary, instanceIri, ontologyValidationStatus, insightSkillPath}` | 查询状态/结果（含 SKILL 落盘路径） |
| `/api/neinsight/health` | GET | — | `{engineAvailable, processId, bpmnPath}` | 健康检查 |

## 7. 配置（`application.yaml`）

| 键 | 值 |
|---|---|
| `server.port` | `9084` |
| `spring.application.name` | `ontology-framework-neinsight` |
| `camunda.client.grpc-address` / `rest-address` | `localhost:26500` / `localhost:9080` |
| `camunda.client.mode` | `self-managed`（`auth.type=none`） |
| `ontology.main-path` | `ontology/neinsight-all.owl` |
| `neinsight.skill.dir` | `skill`（洞察 SKILL 落盘目录，相对模块工作目录；`summarize-insight` 生成） |
| `[ontology.abox-path]` | （第 9 步分离后新增，指向 `ontology/neinsight-abox.owl`） |
| `ontology.obda-path` / `obda-properties-path` | `ontology/database/neinsight.obda` / `ontology/database/neinsight.properties`（第 11 步） |
| `ontology.bpmn-path` | `ontology/bpmn/neInsightProcess.bpmn` |
| `env-prepare.mysql.db-name` | `neinsightdb` |
| `env-prepare.rabbitmq.exchange` / `queue` / `routing-key` | `neinsight.opportunity.exchange` / `neinsightOpportunityQueue` / `opportunity.high.priority` |
| `env-prepare.ontology-catalog` | `ontology/catalog-v001.xml` |
| `env-prepare.ddl-file` / `data-file` | `ontology/database/createdb_tables.ddl` / `ontology/database/data.sql` |

## 8. 解耦与端到端数据流

1. **输入场景**：前端输入洞察场景（文本，必填）。
2. **启动流程**：POST `/api/neinsight/insight {insightScenario}` → `ProcessOrchestrator.start()`。
3. **五看扫描**：Worker 逐节点围绕场景查询本体（`BackendService`/`QueryService`；检索根域固定联合域），组装环境扫描结论。
4. **SPAN 推理**：`scan-opportunity` 经 Openllet + SWRL R1/R1b 推优先级；网关②分流。
5. **外发**（异步）：高优先级 → SWRL R2 → `RabbitMqHandler.send()` → `neinsight.opportunity.exchange`。
6. **三定**：控制点/目标（SMART R3）/策略逐节点产出。
7. **汇总与 SKILL 生成**：`summarize-insight` 一致性校验 + 逐节点组装**围绕该场景**的「洞察报告生成 SKILL」（①本体 ABox 实例数据 + ②联网检索指令 + ③数据加工与分析要求；落盘 `skill/neinsight-<instanceKey>.md`）+ 回写摘要/实例 IRI/校验状态/SKILL 路径。
8. **结果回显**：前端轮询 GET，COMPLETED 后显示洞察摘要、实例 IRI、本体验证状态、SKILL 路径与关键变量。

### 8.1 洞察报告生成 SKILL（Markdown）产出

`summarize-insight` 生成 `skill/neinsight-<instanceKey>.md`——一份**可直接输入大模型**的提示词 SKILL（大模型据此生成洞察报告），口径为 **「TBox = 分析的维度与方法、ABox = 待分析的具体实例 + 逐节点联网检索与数据加工分析指令」**：

- **角色设定 + 任务指令（SKILL 骨架）**：明确大模型扮演「战略洞察分析师」角色、**具备联网检索能力**，任务=**按「三、洞察内容」所列 BPMN 流程节点的先后顺序逐节点逐步构建**《新能源行业洞察报告》（依次完成「五看」各节点 → 「三定」各节点 → 汇总结论）；任务指令要求**每个节点先联网采集数据、再做加工分析**（内嵌本体实例数据为内部知识基线，不足时以联网检索补齐并标注来源），结论有据可依、来源可溯、缺失项标注「待补充」、规范 Markdown 输出；
- **洞察内容（内嵌输入·按 BPMN 流程逐步构建）**：即本次流程采集到的待洞察内容——**严格按 BPMN `NewEnergyInsightProcess` 的 serviceTask 执行顺序逐节排列**，每节标注对应节点（`节点 id` / `jobType`）与其产出的流程变量，每节含三部分：**①本体实例数据**（该节点从本体检索到的实例数据）、**②🔎 联网检索指令**（依据输入场景动态生成的检索关键词 / 建议信息源 / 需采集数据字段）、**③🧮 数据加工与分析要求**（该节点数据应如何加工分析、产出什么结论）。流程起点「输入洞察场景」→【五看】（看行业/趋势、看市场/客户、看竞争、看自己、看机会）→【三定】（定战略控制点、定目标、定策略）；①经现存 API（`BackendService#getIndividuals` / `getObjectPropertyAllValueOfIndividual` / `getDataPropertyValueOfIndividual` / `resolveLabel` 等）检索对应类/属性实例数据，**不硬编码业务数据**（★21）；②③由 `appendRetrievalAndAnalysis` 依场景生成，**不接入程序侧检索通道**（检索由大模型按指令执行）；
- **数据校验与提示**：附一致性状态、SMART 合规、外发标记与数据缺失（待补）项，供大模型撰写时标注；
- **落盘**：目录取自配置 `neinsight.skill.dir`（默认 `skill`，相对模块工作目录，**不硬编码绝对路径**）；文件名 `neinsight-<instanceKey>.md`；写盘失败仅记日志、不阻断流程。SKILL 路径经流程变量 `insightSkillPath` 回写，可由查询接口获取。

## 9. API 复用声明（★铁律 21）

- 逻辑一律经 `BackendService` / `OntologyService` / `OBDAHandler` / `ProcessOrchestrator` / `RabbitMqHandler` / `OntologyWorkerSupport` 等**现存 API**。
- **未重写** `Reference/` 三份 API 文档中的任何 API（清单见 `references/API复用清单.md`）。
- 若确需新增能力：<待定；当前无新增>。

## 10. 待业务确认项

| # | 问题 | 业务结论 |
|---|---|---|
| 1 | ~~13 个节点 ⇄ 13 个 jobType~~ → 10 个节点 ⇄ 10 个 jobType 命名是否认可 | 认可（改造后 10 个；域扫描节点已移除） |
| 2 | 各节点算法口径（§5）是否认可 | 认可 |
| 3 | SPAN 分档阈值 / SMART 量纲 / 竞品口径（【待考】） | 认可（保持占位，SWRL 以 3.0 占位阈值驱动） |
| 4 | 外发通道命名是否沿用流程设计 §6 | 认可 |
| 5 | 端口 9084 / Profile `NEInsightBPMN` / 库名 `neinsightdb` | 认可 |
| 6 | 输入改为洞察场景（`insightScenario`）、检索根域固定联合域、五看三定与 SKILL 围绕场景 | 认可（2026-10-12 改造） |
| 7 | SKILL 由「仅内嵌洞察内容（骨架）」扩为「逐节点内嵌 ①本体实例数据 + ②联网检索指令 + ③数据加工与分析要求」，检索由大模型按指令执行（程序侧不接检索） | 认可（2026-10-12 改造 v3） |

---

## 闸门④留痕
| 日期 | 确认范围（节点集） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-12 | 全部节点（改造后 10 个） | 用户（改造确认） | 输入改场景（`insightScenario`）；移除 `Task_ScanITSpace`/`_CloudSpace`/`_CombinedSpace`（13→10）；检索根域固定联合域；SKILL 围绕场景组织 |
| 2026-10-12 | 汇总节点 `Task_Summarize`（§8.1 SKILL 口径） | 用户（改造确认 v3） | SKILL 逐节点增加「🔎 联网检索指令 + 🧮 数据加工与分析要求」（共 8 个五看三定节点）；大模型据此真正联网检索、加工、分析，而非仅按骨架作答；程序侧不新增检索依赖（§5 算法与 §4 契约不变） |
| 2026-10-12 | 13 个 ServiceTask（域扫描×3、五看×5、外发×1、三定×3、汇总×1） | 用户 | 13 节点算法全部认可；按《应用架构设计》§4/§5 实现 `NEInsightOntologyJobWorker` 及 Application/application.yaml/pom.xml；【待考】阈值保持占位（SWRL 以 3.0 占位），不伪造。 |
