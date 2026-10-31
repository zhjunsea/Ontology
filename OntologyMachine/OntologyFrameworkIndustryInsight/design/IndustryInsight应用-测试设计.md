# 行业洞察（IndustryInsight）应用 — 测试设计文档

> **用法**：本文件产出**第 8 步**。**闸门⑤**：业务人员/负责人确认本文档后方可固化测试用例（`src/test/java/...`）。
> **基线**：`OntologyAppBuilder/templates/测试设计文档模板.md`（形态）；形态基线 `OntologyFrameworkPlumBlossom/src/test/...`（同代 15+ 节点 JobWorker 应用：`AllPlumBlossomSuiteTest` / `PlumBlossomBpmnInMemoryTest` / `PlumBlossomBpmnRealEngineTest` / `PlumBlossomSwrlRabbitMqTest` / `validation/{PlumBlossomOntologyComplianceTest, PlumBlossomSkosTerminologyTest}`）。
> **上游**：第 6 步单文件本体 `ontology/industryinsight-all.owl`；第 7 步应用架构设计与 BPMN；第 9 步将 TBox/ABox 分离为两个 owl；第 11 步 ABox 入库（Ontop/OBDA）；第 13 步产出 SKOS 词汇表；第 14 步执行词汇表与全流程测试。
> **自第 8 步起本体与程序固定**：测试不得为「跑绿」而改本体或算法（★8/9/10）；发现缺陷→提缺陷、走客户确认流程。

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkIndustryInsight / IndustryInsight（行业洞察·五看三定） |
| 文档版本 | v1.3（v1.0 经闸门⑤确认；v1.1 同步测试代码固化结果；v1.2 记录第 13 步 SKOS 用例激活与第 14 步词汇表/全流程测试执行结果；v1.3 对齐流程 v1.3「先五看、后依五看三定」重排节点） |
| 生成日期 | 2026-10-22 |
| 关联本体 | `ontology/industryinsight-all.owl`（第 9 步后 imports 各 TBox/ABox 入口）；术语表 `ontology/industryinsight-terminology.ttl`（第 13 步） |
| 关联流程 | 《IndustryInsight应用-流程设计》（v1.3）；`ontology/bpmn/industryInsightProcess.bpmn`（processId=`IndustryInsightProcess`），Camunda 8 / Zeebe |
| 关联架构 | 《IndustryInsight应用-应用架构设计》（v1.0，闸门④已确认）§4 契约表 |
| 测试代码位置 | `src/test/java/com/ocean/ontologyframework/industryinsight/`（+ `validation` 子包） |
| 测试资源位置 | `src/test/resources/`（本应用无外部测试资源文件，用例数据由程序构造，见 §5） |
| 测试激活 Profile | `IndustryInsightBPMN`（`@ActiveProfiles`；与生产 Worker 注册的 `@Profile("IndustryInsightBPMN")` 一致，见附录 Q-T2） |

> 本文档用例的**方法名、输入变量、断言口径**均取自现有生产工件（`IndustryInsightOntologyJobWorker`、`industryInsightProcess.bpmn`、`industryinsight-all.owl`）与形态基线源码，未作发明；尚未落地的测试类以「**拟建**」标注。

---

## 1. 目的与范围

**目的**：明确 IndustryInsight 应用的质量保证方式——测什么、怎么测、用什么数据、判定依据、如何执行。

**范围**：
- 覆盖 **BPMN 流程端到端编排**：主干 13 节点顺序、G1「场景校验结果」/ G2「机会点优先级」两个排他网关分支、无 incident、实例完成；
- 覆盖 **本体合规**（离线）：可解析性、Openllet 一致性、8 大主干域/分析工具/控制点类型/优先级/SPAN 等域知识完整性、`owl:Restriction` 与 SWRL 规则结构；
- 覆盖 **Ontop OBDA 读写与 Openllet 推理**（第 11 步入库后生效）：正向映射完整性、增/删/改/查、SWRL 推导（`HighPriorityOpportunity` / `KeyRisk` / `requiresOutbound`）；
- 覆盖 **关键机会/风险消息外发**（节点 8）与 RabbitMQ 投递；
- 覆盖 **SKOS 术语结构 + 与 OWL 交叉校验**（第 13 步词汇表产出后生效）。

**不含**：
- 前端 `static/index.html` 的浏览器交互与渲染（人工/UAT，不在自动化范围）；
- MaaS（大模型）服务本身的质量与其返回内容正确性（仅测「通道可用→采用、不可用→兜底、流程不阻断」的**行为**，★26 由生产提示词保证，不在测试内断言行业事实真伪）；
- 平台公共 API（`BackendService` / `OBDAHandler` / `ProcessOrchestrator` / `RabbitMqHandler` / `ChatAgent`）自身的单元测试（属 `Utilities` / `OpenlletResolver` / `OntopOBDAHandler` 模块）。

## 2. 被测系统概述

### 2.1 业务链路
```
Start_01
  → [1] Task_ScenarioParse
  → ◇ Gateway_ScenarioCheck(G1) ─┬─不通过→ [1'] Task_ScenarioClarify → End_01
                                 └─通过──↓
  → [2] Task_IndustryTrend → [3] Task_MarketCustomer → [4] Task_Competition → [5] Task_Self
  → [6] Task_Opportunity → [7] Task_SpanLocate
  → ◇ Gateway_PriorityCheck(G2) ─┬─高──→ [8] Task_SendMessage ─┐
                                 └─中/低───────────────────────┤
  → [9] Task_CrossConsensus → [10] Task_InsightConclusion → [11] Task_ControlPoint → [12] Task_Objective
  → [13] Task_Strategy → [14] Task_Report → End_01
```
> **主干（校验通过且优先级非「高」）**经过 13 个 serviceTask：1、2、3、4、5、6、7、9、10、11、12、13、14。

### 2.2 BPMN 元素 ID 与 jobType
| 环节 | 元素 ID | jobType / 类型 |
|---|---|---|
| 开始事件 | `Start_01` | startEvent |
| [1] 洞察场景解析与校验 | `Task_ScenarioParse` | `parse-insight-scenario` |
| G1 场景校验结果 | `Gateway_ScenarioCheck` | exclusiveGateway（条件：`validationResult`） |
| [1'] 返回场景澄清提示 | `Task_ScenarioClarify` | `return-scenario-clarification` |
| [2] 看行业/趋势 | `Task_IndustryTrend` | `analyze-industry-trend` |
| [3] 看市场/客户 | `Task_MarketCustomer` | `analyze-market-customer` |
| [4] 看竞争 | `Task_Competition` | `analyze-competition` |
| [5] 看自己 | `Task_Self` | `analyze-self` |
| [6] 看机会 | `Task_Opportunity` | `identify-opportunities` |
| [7] 机会点 SPAN 定位与优先级判定 | `Task_SpanLocate` | `locate-opportunity-span` |
| G2 机会点优先级 | `Gateway_PriorityCheck` | exclusiveGateway（条件：`priorityLevel`） |
| [8] 关键机会/风险消息外发 | `Task_SendMessage` | `send-key-insight-message` |
| [9] 五看结论交叉验证与机会共识 | `Task_CrossConsensus` | `cross-validate-insight-consensus` |
| [10] 洞察结论与关键判断 | `Task_InsightConclusion` | `distill-insight-conclusion` |
| [11] 定战略控制点 | `Task_ControlPoint` | `define-strategic-control-point` |
| [12] 定目标（SMART 校验） | `Task_Objective` | `define-objective` |
| [13] 定策略（含定节奏/定路径） | `Task_Strategy` | `define-strategy` |
| [14] 生成洞察 SKILL | `Task_Report` | `generate-insight-skill` |
| 结束事件 | `End_01` | endEvent |

> **网关条件（取自 BPMN `<conditionExpression>`）**：
> - G1 通过：`= validationResult = "通过"` → `Task_InsightConclusion`；不通过：`= validationResult = "不通过"` → `Task_ScenarioClarify`。
> - G2 高：`= priorityLevel = "高"` → `Task_SendMessage`；中/低：`= priorityLevel != "高"` → `Task_CrossConsensus`。

### 2.3 BPMN ↔ JobWorker 绑定
`IndustryInsightOntologyJobWorker`（`@Component @Profile("IndustryInsightBPMN")`，继承 `OntologyWorkerSupport`，`@PostConstruct initOntologyPipeline()`）声明 **15 个 `@JobWorker(autoComplete = false)`**，与上表 15 个 serviceTask 一一对应：
`parse-insight-scenario`｜`return-scenario-clarification`｜`distill-insight-conclusion`｜`analyze-industry-trend`｜`analyze-market-customer`｜`analyze-competition`｜`analyze-self`｜`identify-opportunities`｜`locate-opportunity-span`｜`send-key-insight-message`｜`cross-validate-insight-consensus`｜`define-strategic-control-point`｜`define-objective`｜`define-strategy`｜`generate-insight-skill`。

**BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」。**
- 各分析节点统一模式：`runToolAnalysis`＝「经 MaaS 分析（一次调用内依次产出各工具结论 + 求同存异〔共识/分歧〕）→ 再次经 MaaS 总结」（**实现上将「逐工具分析 + 求同存异」合并为单次调用以降低时延**）；单次生成型节点走 `runGenerative`；LLM 未装配/不可用（`ChatAgent` 为 `@Autowired(required = false)`，`isAvailable()==false`）→ **确定性兜底**，结论置【待考】，**不阻断流程、不内置行业词表**（★26）。
- 关键输出（写入流程顶层变量）：`scenarioParsed`/`validationResult`/`missingElement`/`parseSource`（节点 1）、`clarificationText`（1'）、`industryTrend`（2）、`marketCustomer`（3）、`competition`（4）、`selfInsight`（5）、`opportunityList`（6）、`opportunityPriority`/`priorityLevel`（7）、`outboundMessage`（8）、`crossValidation`（9）、`insightConclusion`（10）、`controlPoints`（11）、`objectives`（12）、`strategies`/`onePageStrategy`/`keyInsightConversionTable`（13）、`insightSummary`/`deliverables`/`insightSkillPath`/`ontologyValidationStatus`（14）。

### 2.4 关键类
`IndustryInsightApplication`（`@SpringBootApplication`，`setAdditionalProfiles("IndustryInsightBPMN")`）、`IndustryInsightOntologyJobWorker`、`web/IndustryInsightController`（`/api/industryinsight/insight`、`/{key}`、`/health`）。
测试侧拟建：`AllIndustryInsightSuiteTest`（套件入口）、`IndustryInsightBpmnInMemoryTest`、`IndustryInsightBpmnRealEngineTest`、`IndustryInsightKeyInsightMessageTest`、`validation/IndustryInsightOntologyComplianceTest`、`validation/IndustryInsightSkosTerminologyTest`。

## 3. 测试分层与策略

| 层级 | 测试类（拟建） | 依赖引擎 | 是否需环境 | 关注点 |
|---|---|---|---|---|
| 本体合规（纯 OWLAPI+Openllet） | `validation/IndustryInsightOntologyComplianceTest`（6 项） | 无 | **否** | 可解析/一致性/域知识完整性/Restriction/SWRL 结构 |
| 本体术语（纯 Jena） | `validation/IndustryInsightSkosTerminologyTest`（3 项，**第 13 步生效**） | 无 | **否** | SKOS 结构 + 与 OWL 交叉校验 |
| 集成（OBDA + 推理） | `IndustryInsightObdaInferenceTest`（4 项，**第 11 步后生效**） | 无 | 是 | 正向映射/增删改查/推理/SWRL 推导 |
| 集成（消息外发 + 高分支） | `IndustryInsightKeyInsightMessageTest`（3 项） | `EngineFactory` | 是（MQ 5672） | G2「高」分支路由 + RabbitMQ 投递 |
| 端到端（内存引擎） | `IndustryInsightBpmnInMemoryTest`（6 项） | `EngineFactory` | 否 | BPMN 编排、节点顺序、无 incident、G1/G2 分支 |
| 端到端（真实引擎） | `IndustryInsightBpmnRealEngineTest`（5 项） | 真实 Zeebe `localhost:26500` | 是 | 真实部署与消费、变量取值级校验 |

**设计原则**：
1. **分层解耦、可离线回归**——纯离线用例（本体合规）置于套件最前，环境未就绪时先拿到本体结论，不被后续集成用例连坐跳过；
2. **端到端由生产 Worker 驱动（不写桩）**——e2e 一律加载生产 `IndustryInsightOntologyJobWorker`，唯一例外是覆盖「高优先级」分支时对**外部 LLM 依赖**用测试替身（见 §6.4 与 Q-T1）；
3. **本体为唯一裁判**——合规/推理断言以 OWL/SWRL 为准；本体个体 IRI 以短前缀形式存储，断言一律用**签名片段「包含」匹配**，不硬编码完整 IRI（吸取 PlumBlossom 实测经验）；
4. **用例间隔离**——e2e 用例各自持有独立流程实例（`System.nanoTime()` 后缀保证 scenario 唯一），互不污染；本体类用例只读加载、不写回。

## 4. 测试环境与配置

- **配置源**：`src/main/resources/application.yaml`（同一 yaml 既被 Spring 读取，也被 `EnvPrepare` 读取）。
- **测试激活 Profile**：`IndustryInsightBPMN`（`@ActiveProfiles`）。
- **关键键**：`ontology.main-path`（`ontology/industryinsight-all.owl`）/（第 9 步后 `ontology.abox-path`）/（第 11 步后 `ontology.obda-path`、`obda-properties-path`）/ `ontology.bpmn-path`；`camunda.client.grpc-address`、`rest-address`；`server.port`（9087）。
- **内存引擎覆盖**：用例经 `@DynamicPropertySource` 将 `camunda.client.grpc-address` / `rest-address` 指向 `EngineFactory.create()` 的网关地址，并置 `prefer-rest-over-grpc=false`。

### 4.1 外部依赖端点
| 依赖 | 端点 / 载体 | 使用者 |
|---|---|---|
| Ontop SPARQL | `http://localhost:8081/sparql` | OBDA 集成用例 / worker（第 11 步后） |
| MySQL | `jdbc:mysql://localhost:3306/industryinsight` | OBDA 读写（第 11 步后） |
| RabbitMQ | AMQP `localhost:5672`（exchange `industryinsight.insight.exchange` / queue `industryInsightQueue` / routing-key `key.insight.alert`） | 消息外发用例（节点 9） |
| Zeebe | 内存引擎（`EngineFactory`）/ 真实 `localhost:26500`（REST `localhost:9080`） | 端到端 |

> **离线自动化行为**：LLM 未配置时，节点 7 兜底分档恒为「中」，G2 走**中/低**分支、**不触发**节点 8，故 `IndustryInsightBpmnInMemoryTest` 与本体合规用例**均不依赖任何外部服务**、可 100% 离线全绿。

### 4.2 真实环境前置
1. `start-env.cmd`（内部 `EnvPrepare-0.0.1-SNAPSHOT.jar start`，读 `application.yaml`）拉起 RabbitMQ / MySQL / Ontop / Camunda；
2. 启动 `IndustryInsightApplication`（激活 `IndustryInsightBPMN`）订阅作业；
3. （第 11 步后）初始化库表并灌入 ABox；
4. Zeebe Gateway `localhost:26500` 可达、BPMN 已部署。

> 依赖服务不可达时，对应用例经 `ServiceProbe.assumeAllReachable(...)` **整体跳过**（JUnit Assumption），而非失败（对齐既有约定）。

## 5. 测试数据设计

| 类别 | 来源 | 说明 |
|---|---|---|
| 洞察场景（正向） | 程序构造 | 含「行业/领域」词（如 `行业`）与「洞察目标」词（如 `洞察`/`分析`/`增长`）且长度 ≥ 6 的场景文本 |
| 洞察场景（边界/负向） | 程序构造 | 缺行业、缺目标、过简（长度 < 6）、空串；见 §7.3 |
| 场景补充字段 | 程序构造 | `industry` / `region` / `timeWindow`（可选），用于触发节点 1 的「行业」要素判定 |
| 分析工具个体 | 本体既有（`AnalysisTool` 23 个 `Tool_*`） | 合规用例校验完整性；不改写 |
| 控制点类型个体 | 本体既有（`ControlPointType` 6 个） | 合规用例校验 |
| 优先级/SPAN 个体 | 本体既有（`Priority_High/Mid/Low`、`Span_1/Span_2`） | 合规/推理用例校验 |
| 典型 ABox 最小闭环 | 本体既有（`Scenario_Example` … `Report_Example`、`Outbound_Example`） | 合规用例校验关键个体齐备 |

> 变量选取原则：**不写入违反本体约束的 ABox 数据**（有据可依，★8/10）。e2e 变量均为流程驱动用「场景/补充字段」，不直接拼装 ABox；本体相关断言只读校验。

## 6. 测试用例清单

> 编号约定：**P** 正向 / **B** 边界 / **N** 负向。用例须**驱动 BPMN** 走完拓扑，尤其覆盖边界场景。
> 断言常量（供各用例复用）：
> `MAIN_CHAIN = [Task_ScenarioParse, Task_IndustryTrend, Task_MarketCustomer, Task_Competition, Task_Self, Task_Opportunity, Task_SpanLocate, Task_CrossConsensus, Task_InsightConclusion, Task_ControlPoint, Task_Objective, Task_Strategy, Task_Report]`
> `CLARIFY_BRANCH = [Task_ScenarioParse, Gateway_ScenarioCheck, Task_ScenarioClarify, End_01]`
> `HIGH_BRANCH_TAIL = [Task_SpanLocate, Gateway_PriorityCheck, Task_SendMessage, Task_CrossConsensus, Task_ControlPoint, Task_Objective, Task_Strategy, Task_Report]`

### 6.1 本体合规测试 `validation/IndustryInsightOntologyComplianceTest`（拟建，6 项，离线）
| ID | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|
| P-1 | `ontologyLoadsAndIsConsistent` | 加载 `ontology/industryinsight-all.owl`（AutoIRIMapper 解析 imports 闭包） | 本体可解析、`OpenlletReasoner.isConsistent()==true` |
| P-2 | `rootAndDomainsComplete` | 同上 | 签名内含根类 `IndustryInsightConcept` 与 8 大主干域：`ScenarioDomain`/`ConclusionDomain`/`AnalysisDomain`/`FiveLooksInsight`/`OpportunityDomain`/`StrategyDomain`/`ReportDomain`/`EventDomain` |
| P-3 | `analysisToolsComplete` | 同上 | `AnalysisTool` 个体 = **23**（`Tool_*`），且含五看工具（`Tool_PESTEL`…`Tool_FAN`）；计数用签名片段包含匹配 |
| P-4 | `controlPointTypesComplete` | 同上 | `ControlPointType` 个体 = **6**（`ControlPointType_TechPatent`/`CostAdvantage`/`CustomerLockIn`/`Brand`/`ChannelBarrier`/`StandardVoice`） |
| P-5 | `exampleAboxMinimalLoopPresent` | 同上 | 典型 ABox 最小闭环关键个体齐备：`Scenario_Example`/`ConclusionSet_Example`/`IndustryTrend_Example`/`MarketCustomer_Example`/`Competition_Example`/`Self_Example`/`OppList_Example`/`ControlPoint_1`/`Report_Example`/`Outbound_Example`；`Priority_High/Mid/Low`、`Span_1/Span_2` 齐备 |
| B-1 | `restrictionsAndSwrlRulesPresent` | 同上 | 存在 `SubClassOf`（含 `owl:Restriction`）公理 **= 22**；存在 SWRL 规则 **= 4**（R1/R2/R3a/R3b）>0 |

> **加载策略**：第 9 步前本体为单文件，`main-path` 即闭包；第 9 步 TBox/ABox 分离后，本类改为「TBox 入口 + ABox 入口」合并视图加载（对齐 PlumBlossom 合规用例的 `main-path + abox-path` 双路径合并口径）。

### 6.2 SKOS 术语测试 `validation/IndustryInsightSkosTerminologyTest`（**已固化**，3 项，**第 13 步词汇表产出后已启用**）
| ID | 方法 | 校验点 | 预期 |
|---|---|---|---|
| P-1 | `terminologyParses` | `ontology/industryinsight-terminology.ttl` 可解析 | 解析成功、`skos:ConceptScheme` 存在 |
| P-2 | `coreClassesHavePrefLabel` | 每个核心 OWL 类有对应 `skos:prefLabel` | 核心类全覆盖，无孤儿概念 |
| P-3 | `prefLabelCrossChecksWithOwl` | 术语表与 OWL `skos:exactMatch` 交叉校验 | 交叉引用双向一致；多对多/冲突显式报告 |

> 本类在第 13 步（闸门⑥）后**才可运行**；在此之前套件内以「未产出词汇表→跳过」处理（`Assumptions.assumeTrue(Files.isRegularFile(termFile))`）。
>
> **执行结果（第 13/14 步，2026-10-22）**：随 `ontology/industryinsight-terminology.ttl` 产出（156 概念 + 165 `skos:altLabel` + 156 `skos:exactMatch`），本类 3 项**已激活并通过（3/3）**。词汇表命名空间 `.../industryinsight/term#<Local>` 与本体既有 `skos:exactMatch` 一致；`skos:altLabel` 来源可溯（本体实体英文名 + 流程设计环节惯用名）。同期修正本体相对 IRI 命名空间缺陷（见《本体核心设计》变更记录），使 `exactMatch` 目标与本体实体对齐。

### 6.3 OBDA + 推理集成测试 `IndustryInsightObdaInferenceTest`（拟建，4 项，**第 11 步 ABox 入库后启用**）
> ⚠️ 本类依赖第 11 步（闸门⑩/⑪）确定的**库表结构与 OBDA 映射**，其用例细节须在第 11 步后**据实际 DDL/映射细化**（此处仅为结构意图，**不臆造字段**，★8/10 → 见 Q-T3）。
| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `positiveMappingRoundTrip` | 经 Ontop SPARQL 查 `InsightScenario`/`Opportunity` | 正向映射取回实体，字段与库表一致 |
| P-2 | `insertReadUpdateDelete` | 写入满足 Restriction 的 `Opportunity` → 查/改/删 | 增删改查闭环、事务一致 |
| P-3 | `swrlDerivesHighPriority` | 写入 `hasPriority=Priority_High` 的 `Opportunity` | 经 Openllet 推得其为 `HighPriorityOpportunity`（R1）；风险等级=高 → `KeyRisk`（R2）；两者 `requiresOutbound=true`（R3a/R3b） |
| N-1 | `restrictionViolationRejected` | 写入缺 `hasPriority`/`hasSpanPosition` 的 `Opportunity` | 违反 `owl:Restriction`，一致性校验失败或写入被拒 |

> 环境前置：Ontop `localhost:8081` + MySQL `localhost:3306`；不可达则整类跳过。

### 6.4 关键洞察消息外发测试 `IndustryInsightKeyInsightMessageTest`（拟建，3 项，需 RabbitMQ）
> **唯一使用测试替身的用例**：以内存引擎部署同一生产 BPMN，注入测试 `ChatAgent` 替身使节点 7 返回「优先级：高」，从而驱动 G2「高」分支（离线兜底恒为「中」，该分支否则不可达）。生产 Worker 与 BPMN 不变，仅替换**外部 LLM 依赖**（等价「未接 MaaS 的服务替身」）。是否采纳见 **Q-T1**。
| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `highPriorityTriggersOutbound` | 注入「返回高」的 LLM 替身 + 通过场景；RabbitMQ 可用 | 实例完成、无 incident；`hasPassedElement("Task_SendMessage")`；`priorityLevel=="高"`；MQ 收到 routing-key `key.insight.alert` 消息，payload 含 `优先级=高` 与 `scenarioIri` |
| B-1 | `nonHighSkipsOutbound` | 不注入替身（走兜底，「中」） | `hasPassedElement("Task_CrossConsensus")` 且 **未**经过 `Task_SendMessage`；无 MQ 投递 |
| N-1 | `mqUnreachableDegradesGracefully` | 替身返回「高」但 MQ 不可达 | 消息投递走 `catch` 降级、`outboundMessage` 含「投递失败（降级，不阻断）」；**流程仍完成**、无 incident |

> 环境前置：RabbitMQ `localhost:5672`；不可达则整类跳过（N-1 以子用例内联模拟连接失败为准，不依赖真实不可达）。

### 6.5 内存引擎端到端测试 `IndustryInsightBpmnInMemoryTest`（拟建，6 项，离线）
> 生产 `IndustryInsightOntologyJobWorker` 驱动；`run()` 统一断言 `isCompleted().hasNoIncidents().hasPassedElement("Start_01").hasPassedElement("End_01")`。
| ID | 方法 | 场景（输入） | 预期 |
|---|---|---|---|
| P-1 | `fullScenarioCompletes` | `scenario="新能源汽车行业洞察与增长机会分析"`（含行业+目标词）；可选 `industry/region/timeWindow` | 完成、无 incident；`hasPassedElementsInOrder(MAIN_CHAIN)`；`validationResult=="通过"`、`priorityLevel=="中"`、`insightSkillPath` 非空、`ontologyValidationStatus` 含「通过」、`insightSummary` 含「行业洞察报告」 |
| P-2 | `clarificationBranchOnMissingGoal` | `scenario="新能源汽车行业"`（含行业、无目标词） | `hasPassedElementsInOrder(CLARIFY_BRANCH)`；**未**经过 `Task_IndustryTrend`；`validationResult=="不通过"`、`missingElement` 含「洞察目标」、`clarificationText` 非空；实例完成 |
| B-1 | `defaultFallbackCompletes` | 仅 `scenario`（含 `行业`+`分析`，无 industry/region/timeWindow） | 完成、无 incident；`hasPassedElementsInOrder(MAIN_CHAIN)`；`parseSource=="兜底"` |
| B-2 | `tooShortScenarioClarifies` | `scenario="新能源"`（长度 < 6） | `hasPassedElementsInOrder(CLARIFY_BRANCH)`；`missingElement` 含「文本过简」 |
| N-1 | `missingIndustryClarifies` | `scenario="洞察增长机会"`（含目标、无行业词） | `hasPassedElementsInOrder(CLARIFY_BRANCH)`；`missingElement` 含「行业/领域」 |
| N-2 | `emptyScenarioClarifies` | `scenario=""` | `hasPassedElementsInOrder(CLARIFY_BRANCH)`；`missingElement` 含「行业/领域」与「洞察目标」 |

### 6.6 真实引擎端到端测试 `IndustryInsightBpmnRealEngineTest`（拟建，5 项，需 Camunda+MQ）
> 经真实 Zeebe 部署并驱动，`ProcessOrchestrator.start/fetchInstance/statusOf/readVariables` 读取变量做**取值级**校验；`@BeforeEach` 经 `deployIfAbsent` 部署。
| ID | Order | 方法 | 场景 | 预期 |
|---|---|---|---|---|
| P-1 | 1 | `fullScenarioRealEngine` | 通过场景 | `status=="COMPLETED"`、无 incident；`validationResult=="通过"`、`priorityLevel ∈ {高,中,低}`、`insightSkillPath` 非空、`insightSummary` 含「行业洞察报告」 |
| P-2 | 2 | `clarifyRealEngine` | 缺目标场景 | `validationResult=="不通过"`、`clarificationText` 非空、实例完成 |
| P-3 | 3 | `parseSourceRecorded` | 通过场景 | `parseSource ∈ {"MaaS","兜底"}`（接入 MaaS 时为 `MaaS`，否则 `兜底`） |
| B-1 | 4 | `defaultFallbackRealEngine` | 仅 scenario | 完成、无 incident；`insightConclusion`/`industryTrend`/…/`strategies` 各变量非空（兜底框架含【待考】） |
| N-1 | 5 | `reportStructuralCheck` | 通过场景 | `ontologyValidationStatus` 含「通过」（五看三定各环节齐备）；SKILL 已落盘至 `skill/industryinsight-<key>.md` |

## 7. 覆盖矩阵

### 7.1 流程节点覆盖
| BPMN 节点 id | 覆盖用例 | 说明 |
|---|---|---|
| `Start_01` | 6.5 全部、6.6 P-1…B-1 | 每个 `run()` 统一断言 |
| `Task_ScenarioParse` | 6.5 P-1/P-2/B-1/B-2/N-1/N-2、6.6 P-1/P-2/P-3 | 校验/兜底两态 |
| `Gateway_ScenarioCheck` | 6.5 P-2/B-2/N-1/N-2（不通过）、P-1/B-1（通过） | 两分支均覆盖 |
| `Task_ScenarioClarify` | 6.5 P-2/B-2/N-1/N-2、6.6 P-2 | 澄清分支 |
| `Task_IndustryTrend` | 6.5 P-1/B-1、6.6 P-1/B-1 | 主链 |
| `Task_MarketCustomer` | 6.5 P-1/B-1、6.6 P-1/B-1 | 主链 |
| `Task_Competition` | 6.5 P-1/B-1、6.6 P-1/B-1 | 主链 |
| `Task_Self` | 6.5 P-1/B-1、6.6 P-1/B-1 | 主链 |
| `Task_Opportunity` | 6.5 P-1/B-1、6.6 P-1/B-1 | 主链 |
| `Task_SpanLocate` | 6.5 P-1/B-1、6.4 P-1/B-1、6.6 P-1/B-1 | 分档 |
| `Gateway_PriorityCheck` | 6.4 P-1（高）、6.4 B-1 与 6.5/6.6（中/低） | 两分支均覆盖 |
| `Task_SendMessage` | 6.4 P-1/N-1 | 仅「高」分支（见 Q-T1） |
| `Task_CrossConsensus` | 6.5 P-1/B-1、6.4 P-1/B-1、6.6 P-1/B-1 | 主链/高分支汇合 |
| `Task_InsightConclusion` | 6.5 P-1/B-1、6.6 P-1/B-1 | 主链 |
| `Task_ControlPoint` | 6.5 P-1/B-1、6.4 P-1、6.6 P-1/B-1 | 主链 |
| `Task_Objective` | 6.5 P-1/B-1、6.6 P-1/B-1 | 主链 |
| `Task_Strategy` | 6.5 P-1/B-1、6.6 P-1/B-1 | 主链 |
| `Task_Report` | 6.5 P-1/B-1、6.6 P-1/N-1 | 落盘与结构校验 |
| `End_01` | 6.5 全部、6.6 全部 | 统一断言 |

### 7.2 本体约束覆盖
| 约束（`owl:Restriction` / SWRL） | 覆盖用例 |
|---|---|
| `InsightScenario`：`scenarioText`=1、`parseSource`=1 | 6.1 P-5、B-1 |
| `InsightConclusionSet`：`hasConclusion` 3–5、`hasEvidenceLog`=1 | 6.1 P-5、B-1；6.5 P-1（洞察结论集非空） |
| `ToolFinding`：`referencesTool`=1、`findingConclusion`=1 | 6.1 B-1 |
| 五看（IndustryTrend/MarketCustomer/Competition/Self）Insight：`hasAnalysisSummary`=1、`hasQualityChecklist`=1 | 6.1 B-1 |
| `Opportunity`：`hasPriority`=1、`hasSpanPosition`=1 | 6.1 P-5、B-1；6.3 N-1 |
| `OpportunityList`：`hasOpportunity` ≥1 | 6.1 P-5、B-1 |
| `StrategicControlPoint`：`hasControlPointType`=1 | 6.1 P-5、B-1；6.3 P-2 |
| `InsightReport`：`hasSoWhat`=1、`hasRefreshSignal` ≥1 | 6.1 P-5、B-1 |
| `OutboundInsightMessage`：`triggeredBy` ≥1 | 6.1 P-5、B-1 |
| SWRL **R1**（Opportunity 优先级「高」→ `HighPriorityOpportunity`） | 6.1 B-1（存在性）；6.3 P-3（推导） |
| SWRL **R2**（Opportunity `riskLevel`=「高」→ `KeyRisk`） | 6.1 B-1；6.3 P-3 |
| SWRL **R3a**（`HighPriorityOpportunity`→`requiresOutbound=true`） | 6.1 B-1；6.3 P-3 |
| SWRL **R3b**（`KeyRisk`→`requiresOutbound=true`） | 6.1 B-1；6.3 P-3 |

### 7.3 边界场景清单
| # | 边界/负向场景 | 用例 | 预期行为 |
|---|---|---|---|
| 1 | 场景缺「洞察目标」 | 6.5 P-2、6.6 P-2 | `validationResult=不通过` → 澄清分支 → 结束 |
| 2 | 场景缺「行业/领域」 | 6.5 N-1 | 同上，`missingElement` 含「行业/领域」 |
| 3 | 场景文本过简（<6） | 6.5 B-2 | 同上，`missingElement` 含「文本过简」 |
| 4 | 空场景 | 6.5 N-2 | 同上，缺两类要素 |
| 5 | 补充字段全缺省 | 6.5 B-1、6.6 B-1 | 兜底抽取，走主链完成，无 incident |
| 6 | LLM 未装配（离线） | 6.5 全部 | 各分析/生成节点兜底，结论【待考】，流程不阻断 |
| 7 | 优先级「高」触发外发 | 6.4 P-1 | 经 `Task_SendMessage` 投递 MQ（需替身，见 Q-T1） |
| 8 | 优先级「中/低」不外发 | 6.4 B-1、6.5/6.6 主链 | 跳过 `Task_SendMessage` 直达 `Task_CrossConsensus` |
| 9 | MQ 不可达 | 6.4 N-1 | 投递失败降级、`outboundMessage` 记录、流程仍完成 |
| 10 | 违反本体 Restriction 的写入 | 6.3 N-1（第 11 步后） | 一致性校验失败/写入被拒 |

## 8. 执行方式

- **全套入口**：`AllIndustryInsightSuiteTest`（`@Suite` + `@SelectClasses`，按「离线在前」聚合：`IndustryInsightOntologyComplianceTest` → `IndustryInsightSkosTerminologyTest` → `IndustryInsightKeyInsightMessageTest` → `IndustryInsightBpmnInMemoryTest` → `IndustryInsightBpmnRealEngineTest`）。**`IndustryInsightObdaInferenceTest` 于第 11 步（闸门⑩）据实际 DDL/映射细化后登记**（见 §6.3、Q-T3）。
- **命令**：
  - 完整套件：`./mvnw -pl OntologyFrameworkIndustryInsight test -o`（或 IDEA 右键 `AllIndustryInsightSuiteTest` → Run）
  - 仅离线回归：`./mvnw -pl OntologyFrameworkIndustryInsight test -o -Dtest=IndustryInsightOntologyComplianceTest,IndustryInsightBpmnInMemoryTest`
- **环境依赖标注**：`IndustryInsightKeyInsightMessageTest`（MQ 5672）、`IndustryInsightBpmnRealEngineTest`（Camunda 9080/26500 + MQ 5672）、`IndustryInsightObdaInferenceTest`（Ontop 8081 + MySQL 3306，第 11 步后）在服务不可达时**整体跳过**；其余用例**可离线全绿**。

## 9. 待确认项

| # | 问题 | 助手建议 | 业务/技术结论 |
|---|---|---|---|
| Q-T1 | **G2「高优先级」分支（`Task_SendMessage`）如何覆盖？** 离线兜底恒为「中」，该分支不可达。 | 方案 A（推荐）：新增 `IndustryInsightKeyInsightMessageTest`，以**测试 `ChatAgent` 替身**令节点 8 返回「高」，驱动高分支并校验 MQ 投递；生产 Worker/BPMN 不变。方案 B：不设替身，该分支仅在**接入 MaaS 的真实引擎**运行中覆盖（离线不覆盖）。 | **✅ 采纳方案 A**（业务方，2026-10-22）：新增 `IndustryInsightKeyInsightMessageTest`，以测试 `ChatAgent` 替身令节点 8 返回「高」，覆盖 G2 高分支 + MQ 投递；生产 Worker/BPMN 不变。 |
| Q-T2 | **测试激活 Profile 命名** | 用 `IndustryInsightBPMN`（Worker 注册所在 Profile，与 PlumBlossom 一致、零程序改动）。《应用架构设计》表内「测试 `IndustryInsightJunitTest`」拟据此统一。 | **✅ 采纳**（业务方，2026-10-22）：测试统一激活 `IndustryInsightBPMN`（Worker 注册所在 Profile，零程序改动）；《应用架构设计》表内「测试 `IndustryInsightJunitTest`」据此统一。 |
| Q-T3 | **OBDA/推理集成用例细节** | 依赖第 11 步（闸门⑩/⑪）确定的库表结构与 OBDA 映射，届时据实际 DDL 细化 6.3 用例（不臆造字段）。 | |
| Q-T4 | **e2e 用例数/断言强度** | 6.5（6 项）+ 6.6（5 项）为建议基线，可按业务关注点增减（如为「看机会可追溯性」增专用断言）。 | |
| Q-T5 | **报告落盘目录断言** | 6.6 N-1 断言 `reports/industryinsight-report-<key>.md` 存在；若 CI 需隔离落盘目录，拟以 `-Dindustryinsight.report.dir=` 覆盖。 | |

---

## 闸门⑤留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-22 | v1.0 | 业务方/负责人 | ✅ 通过：确认测试设计（6 类用例分层 / 覆盖矩阵 / 执行方式）；采纳 Q-T1 方案 A、Q-T2 统一 Profile 为 `IndustryInsightBPMN`。授权进入「固化测试代码」。 |

---

## 变更记录
| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-10-22 | 第 8 步初稿：测试设计（目的范围/被测系统概述/分层策略/环境配置/数据设计/6 类用例清单/覆盖矩阵/执行方式/待确认项 + 闸门⑤留痕）；用例数据取自生产 Worker、BPMN、本体与 PlumBlossom 形态基线 | — |
| 2026-10-22 | 闸门⑤确认（业务方）：通过测试设计 v1.0；Q-T1 采纳方案 A、Q-T2 统一 `IndustryInsightBPMN`；据此固化 6 类测试 + 套件入口 | 业务方 |
| 2026-10-22 | 第 8 步固化（v1.1）：按本文档落地 6 类测试 + 套件入口于 `src/test/java/com/ocean/ontologyframework/industryinsight/`（OBDA 类待第 11 步，见 §6.3）；**离线编译通过**；**离线回归 12/12**（本体合规 6、内存 e2e 6）；**全量套件 20/20 全绿**（本体合规 6、内存 e2e 6、真实引擎 5、消息外发/高分支 3；SKOS 0 待第 13 步） | 助手 |
| 2026-10-22 | **第 13 步（闸门⑥ 通过）**：产出 `ontology/industryinsight-terminology.ttl`；SKOS 用例由 0 跳过转为 **3/3 通过**；同步修正本体相对 IRI 命名空间缺陷（详见《本体核心设计》变更记录）。 | 助手 |
| 2026-10-22 | **第 14 步（词汇表测试 + 全流程测试）**：`mvn -o test` 全量回归 **23/23 全绿**（本体合规 6、SKOS 3、消息外发/高分支 3、内存流程 6、真实引擎 5；服务可达时含真实引擎与 MQ 实测）；服务不可达时集成用例经 `ServiceProbe` 断言按设计自动跳过（离线用例不受连坐）。术语表 `altLabel` 来源已抽查可溯。 | 助手 |
| 2026-10-31 | **v1.3（对齐流程 v1.3，业务反馈③）**：按「**先五看、后依五看三定**」重排——业务链路/元素表/覆盖矩阵节点顺序调整为 2–6 五看 → 7 SPAN → 8 条件外发 → 9 交叉验证与机会共识 → 10 洞察结论 → 11–13 三定 → 14 SKILL；`MAIN_CHAIN` 常量同步重排；澄清分支「未经过」断言由 `Task_InsightConclusion` 改为 `Task_IndustryTrend`（主链首个分析节点）；变量清单 `crossValidation`（9）、`insightConclusion`（10） | 助手 |
