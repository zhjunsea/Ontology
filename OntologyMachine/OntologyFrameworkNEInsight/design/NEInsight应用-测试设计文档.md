# NEInsight 应用 — 测试设计文档

> **用法**：本文件为第 8 步产出。**闸门⑤**：业务人员/负责人确认本文档后方可固化测试用例。
> **基线**：`OntologyFrameworkExample/src/test/java/.../{ExampleBpmnRealEngineTest, ExampleSwrlRabbitMqTest, AllExampleSuiteTest}.java`（形态）；模板 `OntologyAppBuilder/templates/测试设计文档模板.md`。
> **上游**：第 6 步单文件本体 `ontology/neinsight-all.owl`；第 9 步将 TBox/ABox 分离为两个 owl；第 13/14 步产出并测试 SKOS 词汇表。

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkNEInsight / NEInsight |
| 文档版本 | v1（待闸门⑤确认） |
| 生成日期 | 2026-10-12 |
| 关联本体 | `ontology/neinsight-all.owl`（聚合 TBox + ABox；ABox 仅洞察域 + 逐节点方法论，**不含业务实例数据**）；命名空间 `core#/env#/decision#/rules#/abox#` |
| 关联流程 | 《NEInsight应用-流程设计》（闸门①）；`ontology/bpmn/neInsightProcess.bpmn`（`NewEnergyInsightProcess`），Camunda 8 / Zeebe |
| 关联架构 | 《NEInsight应用-应用架构设计》（闸门④）§4 节点⇄jobType 契约、§5 逐节点算法 |
| 测试代码位置 | `src/test/java/com/ocean/ontologyframework/neinsight/`（+ `validation/` 子包） |
| 测试资源位置 | `src/test/resources/` |

> 本文档所有方法名、输入、断言**逐条取自测试源码 / 已确认设计**，未作发明。凡数值/阈值标 **【待考】**。

---

## 1. 目的与范围

**目的**：明确 NEInsight 应用的质量保证方式——测什么、怎么测、用什么数据、判定依据、如何执行。

**范围**：
- 覆盖 BPMN 流程端到端编排（11 个 serviceTask 顺序、1 个排他网关分支、无 incident、Start→End 可达；输入为洞察场景）；
- 覆盖入口「输入分析」节点（`analyze-input`）：大模型结构化抽取（身份/场景等关键信息、场景精炼）与不可用/失败时的确定性降级（离线，`NEInsightInputAnalysisTest`）；
- 覆盖输入分析**通用能力**与**必要信息门禁/多轮澄清**：必要信息 `{scenario,identity,industry,objective}` 缺失判定与追问生成、客户显式字段覆盖、**与业务无关守卫**（提示词/降级不得含业务词）（离线，`analyze/InputAnalysisTest`）；
- 覆盖 Openllet 推理（子类检索、互逆闭合、基数约束、SWRL R1/R1b/R2/R3 推导、一致性校验）；
- 覆盖 SWRL 推导结果驱动的 RabbitMQ 消息外发（高优先级机会点告警）；
- 覆盖 OWL 本体合规校验与 SKOS 术语结构/交叉校验（第 14 步）；
- 覆盖 Ontop OBDA 读写（第 11/12 步入库后接入）。

**不含**：EnvPrepare 环境的安装/启停本身（属相邻能力）；`OpenlletResolver`/`OntopOBDAHandler` 内核 API 的单元测试（属各库自身）。

## 2. 被测系统概述

### 2.1 业务链路（「五看三定」）
```
[流程外] 输入分析/澄清(POST /api/neinsight/analyze：抽取 + 缺失追问；必要信息齐全方可启动)
   ↓
Start(输入:洞察场景 insightScenario + scenario/identity/industry/objective) → 输入分析(复用同一能力→refinedScenario/身份)
   → 看行业/趋势 → 看市场/客户 → 看竞争 → 看自己
   → 看机会(SPAN) → 网关②优先级 ─┬ (HIGH)    → 高优先级标记+外发(RabbitMQ) ┐
                                   └ (非 HIGH) ──────────────────────────────┴→ 定战略控制点 → 定目标(SMART) → 定策略 → 汇总输出 → 生成洞察报告(SKILL→大模型→报告) → End
```

### 2.2 BPMN 元素 ID 与 jobType
| 环节 | 元素 ID | jobType |
|---|---|---|
| 流程开始 | `Start_01` | —（startEvent，输入 `insightScenario`） |
| 输入分析（大模型） | `Task_AnalyzeInput` | `analyze-input` |
| 看行业/趋势 | `Task_ScanIndustryTrend` | `scan-industry-trend` |
| 看市场/客户 | `Task_ScanMarketCustomer` | `scan-market-customer` |
| 看竞争 | `Task_ScanCompetition` | `scan-competition` |
| 看自己 | `Task_ScanSelf` | `scan-self` |
| 看机会（SPAN） | `Task_ScanOpportunity` | `scan-opportunity` |
| 机会点优先级 | `Gateway_PriorityChoice` | —（exclusiveGateway） |
| 高优先级标记+外发 | `Task_MarkHighPriority` | `mark-high-priority` |
| 定战略控制点 | `Task_DefineControlPoint` | `define-control-point` |
| 定目标（SMART） | `Task_DefineObjective` | `define-objective` |
| 定策略 | `Task_DefineStrategy` | `define-strategy` |
| 汇总输出 | `Task_Summarize` | `summarize-insight` |
| 生成洞察报告 | `Task_GenerateReport` | `generate-report` |
| 流程结束 | `End_01` | —（endEvent） |

### 2.3 BPMN ↔ JobWorker 绑定
`NEInsightOntologyJobWorker`（`@Profile("NEInsightBPMN")`）声明 **12 个** `@JobWorker`（`autoComplete=false`，显式完成/抛错）：`analyze-input`（入口输入分析）、`scan-industry-trend`、`scan-market-customer`、`scan-competition`、`scan-self`、`scan-opportunity`、`mark-high-priority`、`define-control-point`、`define-objective`、`define-strategy`、`summarize-insight`、`generate-report`（生成洞察报告）（原 13 个，随「输入 = 洞察场景」改造移除 `scan-it-space`/`scan-cloud-space`/`scan-combined-space`；随后新增入口 `analyze-input` 输入分析，10 → 11；再于汇总后新增 `generate-report`，11 → 12）。**BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」。**

### 2.4 关键类
`NEInsightApplication`（`setAdditionalProfiles("NEInsightBPMN")`）、`NEInsightOntologyJobWorker`、`analyze/InputAnalysis`（输入分析通用能力）、`web/NEInsightController`（POST `/api/neinsight/analyze`、POST `/api/neinsight/insight`、GET `/api/neinsight/insight/{key}`、GET `/api/neinsight/insight/{key}/skill`、GET `/api/neinsight/insight/{key}/report`、GET `/api/neinsight/health`）；测试侧 `NEInsightBpmnRealEngineTest` / `NEInsightSwrlRabbitMqTest` / `NEInsightSkillGenerationTest` / `NEInsightReportGenerationTest` / `NEInsightInputAnalysisTest` / `analyze/InputAnalysisTest` / `web/NEInsightSkillDownloadTest` / `web/NEInsightReportDownloadTest` / `validation/NEInsightOntologyComplianceTest` / `AllNEInsightSuiteTest`。

## 3. 测试分层与策略

| 层级 | 测试类 | 依赖引擎 | 是否需环境 | 关注点 |
|---|---|---|---|---|
| 本体合规+推理（纯 Openllet） | `validation/NEInsightOntologyComplianceTest`（约 9 项） | 无 | 否 | 一致性、互逆闭合、基数、SWRL R1/R1b/R2/R3 |
| SWRL + 消息 | `NEInsightSwrlRabbitMqTest`（约 3 项） | 无 | RabbitMQ | 高优先级外发、非高不外发、队列投递 |
| 端到端（真实引擎） | `NEInsightBpmnRealEngineTest`（3 项） | 真实 Zeebe `localhost:26500` | 是 | 场景驱动编排、网关②分支、无 incident |
| 词汇表（纯 Jena） | `validation/NEInsightSkosTerminologyTest`（第 14 步） | 无 | 否 | SKOS 结构 + OWL 交叉校验 |
| 输入分析通用能力（离线，纯函数） | `analyze/InputAnalysisTest`（5 项） | 无 | 否 | 必要信息齐全性判定 / 缺失追问 / 客户字段覆盖 / **与业务无关守卫**（提示词与降级不得含具体业务词） |
| 入口输入分析（离线，Mockito） | `NEInsightInputAnalysisTest`（4 项） | 无 | 否 | `analyze-input`：大模型结构化抽取（含 ```json 包裹）/ 可用但缺字段 / 不可用降级 / 非 JSON 降级；断言 `refinedScenario`/`inputIdentity`/`inputAnalysisSource`/`inputMissingFields` |
| SKILL 产出（纯 Openllet） | `NEInsightSkillGenerationTest`（1 项） | 无 | 否 | `summarize-insight` 生成**围绕输入场景**的洞察 SKILL：角色设定（含联网检索能力）+任务指令骨架 + 逐节点给出 ①🔎 联网检索指令 ②🧮 数据加工与分析要求 + 📚 方法论来源（**不再内嵌本体业务实例数据**）→ `skill/neinsight-<key>.md` |
| 报告生成（离线，桩函数） | `NEInsightReportGenerationTest`（4 项） | 无 | 否 | `generate-report`：读 SKILL → 组装含「联网检索结果」的提示词 → 大模型 `chat` 生成《洞察报告》并落盘 `report/neinsight-<key>-洞察报告.md` + 回写 `insightReportPath`；大模型不可用降级为直接落盘 SKILL 内容 |
| 联网检索解析（离线，纯函数） | `retrieval/InsightWebRetrieverTest`（6 项） | 无 | 否 | 检索结果解析/裁剪（bocha/tavily 两种响应结构、条数上限、摘要截断、坏输入容错）与「未配置 api-key / 通道关闭」降级 |
| 下载端点（离线，纯函数） | `web/NEInsightSkillDownloadTest`（2 项） | 无 | 否 | `GET /api/neinsight/insight/{key}/skill`：按 `insightSkillPath`/命名规则定位 SKILL 并作为附件返回全文（`text/markdown` + `attachment` 文件名）；文件缺失 → 404 友好错误 |
| 报告下载端点（离线，纯函数） | `web/NEInsightReportDownloadTest`（2 项） | 无 | 否 | `GET /api/neinsight/insight/{key}/report`：按 `insightReportPath`/命名规则定位报告并作为附件返回全文（`text/markdown` + `attachment`）；文件缺失 → 404 友好错误 |
| 集成（OBDA + 推理） | 第 11/12 步接入后补充 | 无 | Ontop+MySQL | 增删改查/事务/正向映射 |

**设计原则**：① 分层解耦，本体合规层可离线回归；② 端到端由**生产 Worker 驱动**（不写桩）；③ 本体为唯一裁判；④ 用例间本体快照/恢复隔离（`@BeforeEach` 全量快照，`@AfterEach` 原子恢复）。

## 4. 测试环境与配置

- 配置源：`src/main/resources/application.yaml`（同一 yaml 驱动 Spring，亦被 EnvPrepare 读取）。
- 测试激活 Profile：本体合规/SWRL 用 `NEInsightJunitTest`；端到端用 `NEInsightBPMN`（装配生产 Worker）。
- 关键键：`ontology.main-path`（`ontology/neinsight-all.owl`）、`ontology.bpmn-path`（`ontology/bpmn/neInsightProcess.bpmn`）；`camunda.client.*`；`server.port`（9084）；`neinsight.skill.dir`（洞察 SKILL 落盘目录，默认 `skill`，SKILL 测试以 `@TempDir` 覆盖）；第 11 步后新增 `ontology.obda-path` / `obda-properties-path`。

### 4.1 外部依赖端点
| 依赖 | 端点 / 载体 | 使用者 |
|---|---|---|
| Zeebe | 真实 `localhost:26500` | 端到端测试（`NEInsightBpmnRealEngineTest`） |
| Ontop SPARQL | `http://localhost:8080/sparql` | 第 11/12 步集成测试 / worker 落库 |
| MySQL | `jdbc:mysql://localhost:3306/neinsightdb` | OBDA 读写（第 11 步后） |
| RabbitMQ | AMQP `localhost:5672` + 管理 API | 消息用例（外发通道 `neinsight.opportunity.exchange`） |

### 4.2 真实环境前置
1. `EnvPrepare start --config application.yaml`（拉起 Zeebe/Ontop/MySQL/RabbitMQ）；
2. 启动 `NEInsightApplication`（激活 `NEInsightBPMN`）订阅作业（或由 `@SpringBootTest` 在测试 JVM 内装配 Worker）；
3. 初始化库表（第 11 步后）；
4. Zeebe Gateway `26500`、RabbitMQ 可达。

## 5. 测试数据设计

| 类别 | 来源 | 说明 |
|---|---|---|
| 洞察场景 | 测试常量 `SCENARIO`（如「面向双碳目标的新能源企业 IT 与云协同数据平台建设」） | 流程唯一业务输入 `insightScenario`；检索根域固定联合域 `COMBINED` |
| 高优先级机会点 | `withTempAxioms` 临时注入（`marketAttractiveness=4.5`、`competitivePosition=4.0`、`hasSpanQuadrant→Q_HighHigh`、`belongsToDomain→CLOUD_SPACE`） | 触发 SWRL R1→`HIGH`、R2→`flaggedForAlert=true`（**业务实例不内置**；用例后自动清理） |
| 低分机会点（边界） | `withTempAxioms` 临时注入（两轴均 <3.0） | 触发 SWRL R1b→`LOW` |
| 目标 | `withTempAxioms` 临时注入（`isMeasurable=true`、`isRelevant=true`、`hasTimeframe="2028"`、`hasMetric=…`） | 触发 SWRL R3→`smartCompliant=true`（**业务实例不内置**；用例后自动清理） |
| 控制点 | （不再内置） | 三定·控制点：节点保留拓扑但不再检索/输出本体实例数据（改造 v12） |

> 变量选取原则：避免写入违反本体约束的 ABox 数据（有据可依，★8/10）；边界用临时公理（`withTempAxioms`）+ 用例后恢复。

## 6. 测试用例清单

> 编号约定：**P** 正向 / **B** 边界 / **N** 负向。用例须**驱动 BPMN** 走完拓扑，尤其覆盖边界场景。

### 6.1 本体合规 + 推理 `NEInsightOntologyComplianceTest`（约 9 项，无环境）
| ID | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|
| P-1 | `ontologyIsConsistent` | `neinsight-all.owl` | `reasoner.isConsistent()==true` |
| P-2 | `inversePropertiesClosed` | `belongsToDomain ⇄ hasElement`（及 §2 各互逆对） | 断言 `owl:inverseOf` 双向声明；对 `belongsToDomain` 断言可经逆向检索到要素 |
| P-3 | `cardinalityRestrictionsHold` | §4 约束类 | `Opportunity` 的 `hasSpanQuadrant` 限定基数 1、`marketAttractiveness/competitivePosition` **至多 1**；`Customer` `hasPainPoint` ≥1；`StrategicControlPoint` `hasControlPointType=1`/`addressesOpportunity≥1`；`Objective` `isMeasurable=1`/`hasMetric≥1`；`Strategy` `strategyOf≥1` |
| P-4 | `swrlR1DerivesHighPriority` | `withTempAxioms` 注入两轴 >3.0 的机会点 | 读 `opportunityPriority` == `"HIGH"`（推理所得；用例后自动清理） |
| P-5 | `swrlR2DerivesFlaggedForAlert` | `withTempAxioms` 注入高优先级机会点 | 读 `flaggedForAlert` == `true`（推理所得） |
| P-6 | `swrlR3DerivesSmartCompliant` | `withTempAxioms` 注入满足 SMART 前置的目标 | 读 `smartCompliant` == `true`（推理所得） |
| P-7 | `swrlR1bDerivesLowPriority` | `withTempAxioms` 注入两轴 <3.0 的机会点 | 读 `opportunityPriority` == `"LOW"`（`withTempAxioms` 结束后自动清理） |
| B-1 | `missingAxisNoPriority` | `withTempAxioms` 注入缺 `competitivePosition` 的机会点 | `opportunityPriority` 无推断值（不臆造） |
| N-1 | `quadrantCardinalityViolationDetected` | `withTempAxioms` 注入同一机会点挂 2 个**互异** `SPANQuadrant`（补 `owl:differentFrom`） | `validateAxioms` 抛 `InconsistentOntologyException`（函数属性+互异→真正违规） |

> **【实现期修正 · 修正记录（2026-10-12）】**
> 1. **两轴基数**：`Opportunity.marketAttractiveness/competitivePosition` 由 `exact 1` 放宽为 `max 1`（见《本体核心设计》§4 修正记录）。原因：Openllet 为「缺轴」生成匿名数据填充，SWRL 比较内建函数遇匿名项抛 NPE。放宽后 B-1「缺轴→不推断」可正常执行。
> 2. **N-1**：`hasSpanQuadrant` 为函数属性，两个不同填充会被推断为同一（一致）；须补 `owl:differentFrom` 才真正违反「至多 1」。且 `BackendService.validateAxioms` 在检出不一致时会抛 `InconsistentOntologyException`（非返回 false），故断言改为 `assertThrows`；用例后由 `@AfterEach` 快照原子恢复隔离。

### 6.2 SWRL + RabbitMQ `NEInsightSwrlRabbitMqTest`（约 3 项，需 RabbitMQ）
| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `highPriorityOpportunityDispatches` | 声明 `neinsight.opportunity.exchange`(direct)/`neinsightOpportunityQueue`/`opportunity.high.priority` 拓扑；对 `withTempAxioms` 注入的高优先级机会点走外发 | 队列收到 JSON，含 `opportunityIri`/`priority=HIGH`/`flaggedForAlert=true` |
| N-1 | `nonHighNotDispatched` | 低分机会点（临时） | 不外发；`alertDispatched=false` |
| B-1 | `queuePrewarmPurge` | 用例前清空队列 | 队列初始为空 |

### 6.3 端到端（真实引擎）`NEInsightBpmnRealEngineTest`（3 项，需 Zeebe+Worker(+MQ)）
| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `insightCompletesForScenario` | `insightScenario=SCENARIO` | 全流程 `COMPLETED`；无 incident；`opportunityPriority=NONE`（不内嵌实例数据）；恒走「非高优先级」分支、外发不触发（`alertDispatched` 无该变量）；`smartCompliant=false`；`ontologyValidationStatus=CONSISTENT` |
| P-2 | `insightProducesSkill` | `insightScenario=SCENARIO` | 全流程 `COMPLETED`；产出 `instanceIri` 与 `insightSkillPath`，且 SKILL 文件实际落盘 |
| B-1 | `blankScenarioRejected` | POST `/api/neinsight/insight {insightScenario:"   "}` 及缺省 | HTTP 400（Controller 校验） |

### 6.4 词汇表测试 `validation/NEInsightSkosTerminologyTest`（第 14 步）
| ID | 方法 | 校验点 | 预期 |
|---|---|---|---|
| P-1 | SKOS 结构 | `skos:prefLabel`(@zh/@en) 齐备、无孤立词条 | 通过 |
| P-2 | OWL 交叉校验 | 术语 IRI 与 TBox 一一对应 | 通过 |

### 6.5 第 12 步回归记录（ABox 扩展至场景完整数据，2026-10-12）
| 项 | 命令 / 脚本 | 结果 |
|---|---|---|
| 本体重生成 | `python HWCodeArtsDir/neinsight_owl_gen.py` | 7 文件写出；`neinsight-abox.owl` 个体 **21 → 44** |
| OWL 结构校验 | `python HWCodeArtsDir/check_owl_step10.py` | 类 29 / 对象属性 29 / 数据属性 16 / SWRL 5；imports 无环；7 文件 XML 合法；individuals=44 |
| DB/OBDA 对齐 | `python HWCodeArtsDir/check_db_obda.py` | DDL 表 19 = OBDA 表 19；`TOTAL ISSUES = 0` |
| 建库灌数 | `EnvPrepare createdb --config .../application.yaml` | `neinsightdb` 重建；DDL/data.sql `rc=0`；各业务表行数合计 41（+域 3 = 44 个体） |
| SPARQL 验证 | `ontop endpoint ... -m neinsight.obda -t neinsight-all.owl` | 三元组 **130 → 279**；`MacroFactor`(2)/`ShortBoard`(1)/`hasMacroFactor`(2)/`hasShortBoard`(1)/3 机会点两轴均正确 |
| 本体合规测试 | `mvn -o -Dtest=NEInsightOntologyComplianceTest test` | **P-1..N-1 9/9 通过**（含 P-1 全 ABox 一致性） |

> **环境依赖说明**：§6.5 中「本体重生成 / OWL 结构校验 / DB/OBDA 对齐 / 本体合规测试」为**离线可执行**；「建库灌数 / SPARQL 验证」需 MySQL（服务 `MySQL80`、库 `neinsightdb`）与 Ontop CLI（`ontop-cli-5.5.0`）。§6.2（RabbitMQ）、§6.3（Zeebe+Worker）用例需对应外部环境，**本次未在离线环境执行**，其断言由 §6.1/§6.5 的本体侧证据间接支撑。

### 6.6 第 14 步回归记录（词汇表测试 + 全流程回归，2026-10-12）
| 项 | 命令 / 脚本 | 结果 |
|---|---|---|
| 词汇表结构 + OWL 交叉校验 | `mvn -o -Dtest=NEInsightSkosTerminologyTest test` | **7/7 通过**（ConceptScheme、@zh+@en prefLabel、broader 无悬空/无自环、OWL exactMatch 目标⊆概念、无孤立词条、关键术语、同义词） |
| 离线 SKOS 预校验 | `python HWCodeArtsDir/check_skos.py` | 概念 74 = exactMatch 目标 74；broader 26；`ISSUES = 0` |
| 本体合规（全集 ABox） | `mvn -o -Dtest=NEInsightOntologyComplianceTest test` | **9/9 通过** |
| 套件聚合 | `AllNEInsightSuiteTest`（`@SelectClasses`） | 已并入 `NEInsightSkosTerminologyTest`；离线子集（合规 9 + 词汇表 7）**全绿** |

> **全流程回归口径**：`AllNEInsightSuiteTest` 含 §6.2（需 RabbitMQ）与 §6.3（需 Zeebe+Worker+MQ）两个环境依赖类，**离线环境不执行**（非跳过、非 `@Disabled`，为显式环境依赖）；本应用的环境无关用例（§6.1、§6.4）已全部通过。完整链路 `EnvPrepare install → platform package → [offline-repo] → 应用 test` 的**环境相关段**需在具备 MySQL/Ontop/RabbitMQ/Zeebe 的机器上复现（★1/2/19）。

### 6.7 洞察 SKILL 产出（离线）`NEInsightSkillGenerationTest`（1 项，无环境）
> 覆盖《应用架构设计》§8.1：`summarize-insight` 以「角色设定 + 任务指令」为骨架、逐节点给出 ①🔎 联网检索指令（检索关键词/信息源/需采集字段）、②🧮 数据加工与分析要求 + 📚 方法论来源（**不再内嵌本体业务实例数据**），生成 Markdown 洞察 SKILL 落盘。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `generatesInsightSkill` | mock `ActivatedJob`/`JobClient`（`insightScenario=SCENARIO`），`skillDir` 指向 `@TempDir` | 生成 `neinsight-<key>.md`（**围绕输入场景**）；含「角色设定」（战略洞察分析师，声明**联网检索能力**）与「任务指令」骨架（要求**先联网采集再加工分析**），洞察内容**按 BPMN 流程节点顺序逐步构建**（【五看】节点 1~5、【三定】节点 6~8，含节点 id/jobType）；**8 个五看三定节点均含「🔎 联网检索指令」（检索关键词/建议信息源/需采集的数据字段）与「🧮 数据加工与分析要求」**；含流程起点「输入洞察场景」、检索根域（`联合空间`） |

> 该用例为**离线**（纯 Openllet，无需引擎），可直接回归 SKILL 生成逻辑，避免依赖真实 Zeebe/Worker。

### 6.8 输入分析通用能力（离线）`analyze/InputAnalysisTest`（5 项，无环境）
> 覆盖 `InputAnalysis` 纯函数：必要信息 `{scenario,identity,industry,objective}` 齐全性判定、缺失字段与追问生成、客户显式字段覆盖抽取结果、确定性降级，以及**与业务无关守卫**（`SYSTEM_PROMPT` 与降级输出不得包含任何具体行业/身份词）。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `providedCompleteIsReady` | 客户显式提供全部必要字段 | `ready=true`；`missingFields=[]`；`source="PROVIDED"`；**不调用大模型**（桩抛异常即验证） |
| P-2 | `missingRequiredProducesQuestions` | 仅提供自由文本、无大模型 | `ready=false`；`source="HEURISTIC"`；`missingFields=[identity,industry,objective]`；`questions` 与缺失项一一对应 |
| P-3 | `parsesLlmJson` | 大模型返回（```json 包裹的）JSON | 解析为结构化信息；`source="LLM"`；缺失项仍被标记 |
| P-4 | `explicitFieldsOverrideExtraction` | 大模型结果 + 客户显式字段 | 客户字段覆盖大模型结果 |
| B-1 | `promptAndFallbackAreBusinessAgnostic` | 任意输入 | `SYSTEM_PROMPT` 不含任何业务词；降级 `identity/industry/objective` 均为空 |

### 6.9 入口输入分析节点（离线）`NEInsightInputAnalysisTest`（4 项，无环境）
> 覆盖《应用架构设计》§4 契约 #1 / §5：入口 `analyze-input` 复用 `InputAnalysis` 解析客户输入、抽取身份/场景等关键信息并精炼场景；大模型不可用/失败降级为确定性提取（**不猜测业务**）。测试给生产 Worker 注入 `LlmClient` 桩函数（`new LlmClient((sys,user)->...)` / `configure(false,...)`），以 Mockito `RETURNS_DEEP_STUBS` 捕获 `complete.variables`。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `llmUsedExtractsStructuredInfo` | `LlmClient` 返回（```json 包裹的）严格 JSON | `inputAnalysisSource="LLM"`；`refinedScenario` 取精炼场景；`inputIdentity` 取模型结果；`inputMissingFields=[]`；`inputAnalysisResult` 含 `scenario`/`industry` |
| P-2 | `llmPartialJsonMarksMissing` | 大模型返回 JSON 但缺必要字段 | `inputAnalysisSource="LLM"`；`inputMissingFields=[industry,objective]`（触发追问） |
| B-1 | `llmUnavailableFallsBackToHeuristic` | `LlmClient.configure(false,...)`（不可用） | `inputAnalysisSource="HEURISTIC"`；`refinedScenario` 回退原输入；`inputIdentity=""`（**不猜测业务**）；`inputMissingFields` 含 `identity/industry/objective` |
| B-2 | `llmReturnsGarbageFallsBack` | 大模型返回非 JSON 文本 | 降级 `HEURISTIC`，不抛异常；`refinedScenario` 回退原输入 |

> 上述两节均为**离线**（不连引擎、不发真实 HTTP），直接回归输入分析与入口节点逻辑。

### 6.10 洞察方法本体化（改造 v6，离线）
> 覆盖《本体核心设计》§10 / 《应用架构设计》§4 #11：逐节点「洞察方法」由本体 `core#InsightMethod` 读取渲染，替代 Java 硬编码。由 **§6.7 SKILL 用例**（断言 8 个五看三定节点均含 🔎 检索指令 + 🧮 加工分析要求 + 📚 方法论来源）+ **§6.1 合规用例**（断言 `InsightMethod` 类/属性/8 个体存在于本体）+ **§6.4 词汇表用例**（`InsightMethod`/7 属性/8 个体 OWL↔term 双向 exactMatch）联合覆盖，无独立新增用例。

| 校验点 | 覆盖用例 | 预期 |
|---|---|---|
| 8 个 `InsightMethod` 个体存在且 `appliesToNode` 对齐 jobType | §6.1（合规）/§6.4（词汇表） | 类/属性/个体齐备，SKOS 镜像一致 |
| 方法约束（appliesToNode/methodOutput/sourceRef min 1） | §6.1 | 缺失即不一致 → 合规测试失败 |
| 逐节点「🔎 检索指令 + 🧮 加工分析要求 + 📚 方法论来源」由本体渲染 | §6.7 SKILL 用例 | 8 节点均含三类内容；来源按「能联网附链接、否则标权威出处」 |
| 来源可溯（`sourceRef` 含方法论出处/链接） | §6.7 + 本体抽查 | 波特五力/SMART 带链接，其余标权威名称 |

### 6.11 洞察 SKILL 下载端点（改造 v7，离线）`web/NEInsightSkillDownloadTest`（2 项，无环境）
> 覆盖《应用架构设计》§6 下载端点 / §8 步骤 10：前端 COMPLETED 后可直接下载本次流程自动生成的 SKILL。不依赖真实引擎——Controller 的 `CamundaClient` 为 null 时读取流程变量失败会**自动回退按命名规则定位**（`<neinsight.skill.dir>/neinsight-<key>.md`），故可用 `@TempDir` 构造 SKILL 文件直接驱动下载逻辑；同时覆盖「路径归属校验 + 404 兜底」。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| D-1 | `downloadsSkillByNamingFallback` | 临时目录存在 `neinsight-<key>.md`（`client=null` → 变量读取失败回退命名规则） | HTTP 200；`Content-Disposition` 含 `attachment` 与 `neinsight-<key>.md`；`Content-Type` 含 `text/markdown`；响应体 == SKILL 全文 |
| D-2 | `returns404WhenSkillMissing` | 目标 SKILL 文件不存在 | HTTP 404，响应体为结构化错误（`error` 含「未找到」） |

> 安全口径：定位到的文件须落在配置 `neinsight.skill.dir` 规范化基目录之内（`Path.startsWith`），否则回退命名规则，**防路径穿越**；不硬编码绝对路径（★21）。

### 6.12 洞察报告生成（改造 v9，离线）`NEInsightReportGenerationTest`（4 项，无环境）
> 覆盖《应用架构设计》§4 契约 #12 / §8 步骤 10：`generate-report` 读取汇总产出的洞察 SKILL，**组装含「联网检索结果」的提示词**后交大模型生成《洞察报告》并落盘，回写 `insightReportPath`；大模型不可用/失败时降级为直接落盘 SKILL 内容。测试给生产 Worker 注入 `LlmClient` 桩函数并以 Mockito `RETURNS_DEEP_STUBS` 捕获 `complete.variables`；`skillDir`/`reportDir` 均指向 `@TempDir`。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `generatesReportViaLlm` | 临时目录预置 `neinsight-<key>.md`；`LlmClient` 桩返回报告文本 | 落盘 `report/neinsight-<key>-洞察报告.md`，内容为大模型输出；`insightReportPath` 回写为该文件绝对路径 |
| P-2 | `promptIncludesRetrieval` | 以 `buildReportUserPrompt` 传入非空检索片段 | user 提示词含「## 联网检索结果」区块、节点标识与来源 URL，并含「标注来源 URL」 |
| P-3 | `promptWithoutRetrieval` | 检索片段为空串 | user 提示词标注「未能获取联网检索结果」，且不含「## 联网检索结果」区块 |
| B-1 | `fallsBackToSkillContentWhenLlmUnavailable` | `LlmClient.configure(false,...)`（不可用） | 报告仍落盘，内容等于 SKILL 原文（降级不阻断）；`insightReportPath` 非空 |

> 该用例为**离线**（不连引擎、不发真实 HTTP），直接回归报告生成/降级与提示词拼接逻辑。联网检索通道的实现见 §6.14（走外部 Web 检索 **REST API**，默认博查、可切 Tavily）；检索通道的**真实连通性**由集成测试按所选 provider 覆盖。

### 6.13 洞察报告下载端点（改造 v8，离线）`web/NEInsightReportDownloadTest`（2 项，无环境）
> 覆盖《应用架构设计》§6 下载端点 / §8 步骤 11：前端 COMPLETED 后可直接下载本次流程自动生成的洞察报告。不依赖真实引擎——Controller 的 `CamundaClient` 为 null 时读取流程变量失败会**自动回退按命名规则定位**（`<neinsight.report.dir>/neinsight-<key>-洞察报告.md`），故可用 `@TempDir` 构造报告文件直接驱动下载逻辑；同时覆盖「路径归属校验 + 404 兜底」。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| D-1 | `downloadsReportByNamingFallback` | 临时目录存在 `neinsight-<key>-洞察报告.md`（`client=null` → 变量读取失败回退命名规则） | HTTP 200；`Content-Disposition` 含 `attachment` 与 `neinsight-<key>`；`Content-Type` 含 `text/markdown`；响应体 == 报告全文 |
| D-2 | `returns404WhenReportMissing` | 目标报告文件不存在 | HTTP 404，响应体为结构化错误（`error` 含「未找到」） |

> 安全口径：定位到的文件须落在配置 `neinsight.report.dir` 规范化基目录之内（`Path.startsWith`），否则回退命名规则，**防路径穿越**；不硬编码绝对路径（★21）。

### 6.14 洞察报告联网检索（改造 v9/v10/v11，离线）`retrieval/InsightWebRetrieverTest`（6 项，无环境）
> 覆盖《应用架构设计》§4 #12 / §8 步骤 10 的检索解析与降级：`InsightWebRetriever` 走**外部 Web 检索 REST API**（默认 `provider=bocha`，可切 `tavily`；Bearer 鉴权），将响应解析为紧凑 Markdown 来源清单（bocha 取 `webPages.value[]`、tavily 取 `results[]`）；REST 调用本身复用通用 `Utilities#RestClientHandler`（改造 v11，其单测见 `Utilities/src/test/.../RestClientHandlerTest`）；此处仅以**纯函数**回归解析/裁剪/容错与「未配置 api-key / 通道关闭返回空」，**不发起真实联网**（真实连通性由集成测试按 provider 覆盖）。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| R-1 | `rendersBochaResults` | bocha 正常 JSON（`webPages.value[]`，含空 name/缺 summary） | 输出 `- 标题 —— URL：摘要` 3 行；空 name 回退 URL；summary 缺省回退 snippet、空白压缩 |
| R-2 | `rendersTavilyResults` | tavily 正常 JSON（`results[]`，含空 title/content） | 输出 2 行；空 title 回退 URL；content 空白压缩 |
| R-3 | `limitsAndTruncates` | 条数上限 1、摘要上限 3 字符 | 仅 1 行；摘要按上限截断加「…」 |
| R-4 | `toleratesBadInput` | `null`/空白/非 JSON/结构不匹配（bocha↔tavily 交叉） | 均返回空串，不抛异常 |
| R-5 | `disabledReturnsEmpty` | 默认启用；`enabled=false` | `isEnabled()` 默认 true；关闭后 `retrieve` 直接返回空 Map（不联网） |
| R-6 | `unconfiguredKeySkips` | 未配置 `api-key` | `isConfigured()=false`；`retrieve` 返回空 Map（不联网、降级） |

> 安全/降级口径：检索通道不可用或某节点检索失败时**跳过并记日志**（不抛异常），报告退回「仅据 SKILL 与本体基线」生成（见 §6.12 与《应用架构设计》§4 #12）。

### 6.15 不内置业务实例数据（改造 v12，离线回归）
> 覆盖《应用架构设计》§8/§8.1、改造 v12：NEInsight **不再内置本体业务实例数据（内部知识基线不通用）**。五看/三定 8 个节点保留 BPMN 拓扑，但不再检索/输出本体实例数据（仅回写 `domain`/`scenario`/方法论说明）；`scan-opportunity` 恒输出 `opportunityPriority=NONE`、`define-objective` 恒输出 `smartCompliant=false`；`mark-high-priority` 与 SWRL R1–R4 保留定义但不再触发。ABox 由 44 个体精简为 **11**（3 域 + 8 方法论 `IM_*`），OBDA/DDL/data.sql 仅保留域表。

| 项 | 命令 | 结果 |
|---|---|---|
| 离线子集回归 | `mvn -o -pl OntologyFrameworkNEInsight test -Dtest=NEInsightOntologyComplianceTest,NEInsightSkosTerminologyTest,InputAnalysisTest,NEInsightInputAnalysisTest,NEInsightSkillGenerationTest,NEInsightReportGenerationTest,InsightWebRetrieverTest,NEInsightSkillDownloadTest,NEInsightReportDownloadTest` | **40/40 通过**（合规 P-1~N-1 9 项、SKILL 1 项、其余离线 30 项） |
| 用例口径调整 | — | 合规 P-2/P-4/P-5/P-6 与 SWRL/端到端 P-1 由「依赖 `abox#Opp_SmartEnergyCloud`/`Obj_1`」改为 **`withTempAxioms` 临时注入**；SKILL 用例移除「📊 本体实例数据」断言；端到端 P-1 期望改为 `opportunityPriority=NONE`/`smartCompliant=false`/外发不触发 |

> **环境依赖说明**：`NEInsightSwrlRabbitMqTest`（RabbitMQ）与 `NEInsightBpmnRealEngineTest`（Zeebe+Worker）为显式环境依赖，离线环境不执行。

## 7. 覆盖矩阵

### 7.1 流程节点覆盖
| BPMN 节点 id | 覆盖用例 | 说明 |
|---|---|---|
| `Task_AnalyzeInput` | P-1~B-1(§6.8) + P-1~B-2(§6.9) | 输入分析通用能力（必要信息判定/追问/业务无关守卫）+ 入口节点（抽取/降级） |
| `Task_ScanIndustryTrend` | P-1/P-2(§6.3) | 五看·趋势 |
| `Task_ScanMarketCustomer` | P-1/P-2(§6.3) | 五看·市场/客户 |
| `Task_ScanCompetition` | P-1/P-2(§6.3) | 五看·竞争 |
| `Task_ScanSelf` | P-1/P-2(§6.3) | 五看·自己 |
| `Task_ScanOpportunity` | P-1(§6.3) + P-4/P-7/B-1(§6.1) | SPAN 定位与优先级 |
| `Gateway_PriorityChoice` | P-1(高)(§6.3) | 高优先级分支 |
| `Task_MarkHighPriority` | P-1(§6.3) + P-1(§6.2) | 外发 |
| `Task_DefineControlPoint` | P-1/P-2(§6.3) | 三定·控制点 |
| `Task_DefineObjective` | P-1(§6.3) + P-6(§6.1) | 三定·目标(SMART) |
| `Task_DefineStrategy` | P-1/P-2(§6.3) | 三定·策略 |
| `Task_Summarize` | P-1/P-2(§6.3) + P-1(§6.7) | 汇总/一致性校验/生成洞察 SKILL 落盘 |
| `Task_GenerateReport` | P-1/P-2(§6.3) + P-1/P-2/P-3/B-1(§6.12) + R-1~R-6(§6.14) | 读 SKILL → 联网检索 → 大模型生成洞察报告并落盘/降级 |

> 原 `Gateway_DomainChoice` 与 3 个域扫描节点（`Task_ScanITSpace`/`_CloudSpace`/`_CombinedSpace`）已随改造移除；网关②在**不内置业务实例数据**（改造 v12）后恒走「非高优先级」分支（`opportunityPriority=NONE`），高优先级分支与外发不触发（其 SWRL R1/R2 逻辑由 §6.1 P-4/P-5、§6.2 P-1 以临时注入覆盖）。

### 7.2 本体约束覆盖
| 约束（`owl:Restriction` / SWRL） | 覆盖用例 |
|---|---|
| `InsightElement.belongsToDomain` min 1 | P-2(§6.1) |
| `Opportunity.hasSpanQuadrant` =1 | P-3 / N-1(§6.1) |
| `Opportunity.marketAttractiveness/competitivePosition` =1 | P-3 / B-1(§6.1) |
| `Customer.hasPainPoint` min 1 | P-3(§6.1) |
| `StrategicControlPoint.hasControlPointType` =1 / `addressesOpportunity` min 1 | P-3(§6.1) |
| `Objective.isMeasurable` =1 / `hasMetric` min 1 | P-3(§6.1) |
| `Strategy.strategyOf` min 1 | P-3(§6.1) |
| `InsightMethod.appliesToNode/methodOutput/sourceRef` min 1 | §6.1（合规）/§6.4（词汇表） |
| SWRL R1 / R1b（SPAN→优先级） | P-4 / P-7(§6.1) |
| SWRL R2（高优先级→外发标记） | P-5(§6.1) + P-1(§6.2) |
| SWRL R3（SMART 校验） | P-6(§6.1) |

### 7.3 边界场景清单
| # | 边界/负向场景 | 用例 | 预期行为 |
|---|---|---|---|
| 1 | 空洞察场景 | B-1(§6.3) | HTTP 400，不启动流程 |
| 2 | 检索根域无高优先级机会点 | 网关②非高分支（当前数据下由 §6.1 B-1 覆盖） | `opportunityPriority != "HIGH"` → 直达三定，流程不中断 |
| 3 | 机会点缺 SPAN 轴评分 | B-1(§6.1) | 不推断优先级（不臆造） |
| 4 | 同一机会点挂多个 SPAN 象限 | N-1(§6.1) | 基数校验拦截 |
| 5 | 非高优先级机会点 | N-1(§6.2) | 不外发 |
| 6 | RabbitMQ 不可达 | P-1(§6.2) 前置校验 | 外发失败仅记录，不阻断三定与汇总（Worker 捕获异常） |
| 7 | 必要信息（`scenario`/`identity`/`industry`/`objective`）缺失 | B-1(§6.8) / B-1(§6.9) | 标记 `missingFields` 并生成追问；`/api/neinsight/insight` 返回 400，**不启动流程** |

## 8. 执行方式

- 全套入口：`AllNEInsightSuiteTest`（`@Suite` + `@SelectClasses`，聚合 `validation/NEInsightOntologyComplianceTest`、`validation/NEInsightSkosTerminologyTest`、`analyze/InputAnalysisTest`、`NEInsightInputAnalysisTest`、`NEInsightSkillGenerationTest`、`NEInsightReportGenerationTest`、`retrieval/InsightWebRetrieverTest`、`web/NEInsightSkillDownloadTest`、`web/NEInsightReportDownloadTest`、`NEInsightSwrlRabbitMqTest`、`NEInsightBpmnRealEngineTest`）。
- 命令：`mvn -pl OntologyFrameworkNEInsight test`（或 reactor 全量 `mvn -pl OntologyFrameworkNEInsight -am test`）。
- 环境依赖项（Zeebe/Ontop/MySQL/RabbitMQ）已在上表显式标注；纯本体层可离线执行。

## 9. 待确认项（闸门⑤）

| # | 问题 | 结论 |
|---|---|---|
| 1 | 测试分层（本体合规 / SWRL+消息 / 真实引擎端到端 / 词汇表 / OBDA 集成）是否认可 | ✅ 已认可 |
| 2 | 用例清单（§6：正向+边界+负向）是否齐备、是否认可 | ✅ 已认可（B-1/N-1 于实现期按引擎约束修正，见 §6.1 修正记录） |
| 3 | 覆盖矩阵（§7：改造后 11 节点 + 全部约束 + 边界清单）是否认可 | ✅ 已认可（域扫描节点移除后 10 节点；入口新增 `analyze-input` 后 11 节点） |
| 4 | 测试 Profile 命名（`NEInsightJunitTest` / `NEInsightBPMN`）与套件入口 `AllNEInsightSuiteTest` 是否认可 | ✅ 已认可 |
| 5 | 【待考】阈值/量纲在测试中以占位（3.0）驱动，确认后替换 | ✅ 占位继续，待业务口径替换 |
| 6 | 入口输入分析测试（§6.8：`NEInsightInputAnalysisTest`，3 项，离线）是否纳入分层与套件 | ✅ 已认可（2026-10-13 改造 v4） |
| 7 | 输入分析通用能力测试（§6.8：`analyze/InputAnalysisTest`，5 项）与入口节点测试（§6.9，4 项）是否纳入分层与套件；必要信息门禁/追问用例是否齐备 | ✅ 已认可（2026-10-13 改造 v5） |
| 8 | 洞察 SKILL 下载端点测试（§6.11：`web/NEInsightSkillDownloadTest`，2 项，离线）是否纳入分层与套件 | ✅ 已认可（2026-10-13 改造 v7） |
| 9 | 洞察报告生成（§6.12：`NEInsightReportGenerationTest`，2 项，离线）与报告下载端点（§6.13：`web/NEInsightReportDownloadTest`，2 项，离线）是否纳入分层与套件 | ✅ 已认可（2026-10-13 改造 v8） |
| 10 | 洞察报告联网检索（§6.14：`retrieval/InsightWebRetrieverTest`，6 项，离线）与报告生成提示词扩展（§6.12 升为 4 项）是否纳入分层与套件 | ✅ 已认可（2026-10-14 改造 v9；检索 v10 REST 化升为 6 项） |
| 11 | 不再内置本体业务实例数据（改造 v12）：节点不再检索/输出实例、SKILL 去掉 ①本体实例数据、ABox 精简为 11、SWRL/外发保留定义不触发；用例改临时注入 | ✅ 已认可（2026-10-14 改造 v12） |

---

## 闸门⑤留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-12 | v1 | 用户 | 认可测试分层/用例清单/覆盖矩阵/Profile 与套件入口，同意实现测试类；并确认两处实现期修正：B-1 放宽两轴基数为 max 1、N-1 补 owl:differentFrom 并断言抛异常。 |
| 2026-10-12 | 改造 v2 | 用户（改造确认） | 输入改场景：端到端用例改为 `insightCompletesForScenario`/`insightProducesSkill`/`blankScenarioRejected`（3 项，原 4 项域用例移除）；SKILL 用例输入改 `insightScenario`；覆盖矩阵 13→10 节点 |
| 2026-10-12 | 改造 v3 | 用户（改造确认） | §6.7 SKILL 用例断言扩充：新增对「联网检索指令（检索关键词/建议信息源/需采集的数据字段）」与「数据加工与分析要求」的校验，并断言 **8 个五看三定节点均含**该两类指令；用例数与分层不变 |
| 2026-10-13 | 改造 v4 | 用户（改造确认） | 入口新增「输入分析」节点：§2.2/§2.3 加 `Task_AnalyzeInput`/`analyze-input`（节点 10 → 11）；§3 分层与套件纳入 `NEInsightInputAnalysisTest`（新 §6.8，3 项，离线）；§7.1 覆盖矩阵加该节点；端到端用例断言核对 `refinedScenario`（精炼场景） |
| 2026-10-13 | 改造 v5 | 用户（改造确认） | 输入处理新增**必要信息门禁 + 多轮澄清**：新增 §6.8 `analyze/InputAnalysisTest`（5 项：必要信息判定/追问/覆盖/业务无关守卫）并把 `NEInsightInputAnalysisTest` 升为 §6.9（3 → 4 项，含「可用但缺字段」）；§2.4 关键类加 `InputAnalysis` 与 `/analyze`；§7.1/§7.3 增必要信息相关覆盖；§8 套件纳入 `InputAnalysisTest` |
| 2026-10-13 | 改造 v6 | 用户（改造确认） | 逐节点洞察方法本体化：新增 §6.10（`InsightMethod` 类/7 属性/8 个体，由 §6.1 合规 + §6.4 词汇表 + §6.7 SKILL 用例联合覆盖）；§7.2 增 `InsightMethod` 三约束覆盖行；无独立新增用例；回归：`mvn -o test` 合计 **64 项全绿**（含真实引擎 e2e 3 项），`validate_structure.py` **ERROR=0**（BPMN 契约 11/11 绑定 OK）。 |
| 2026-10-13 | 改造 v7 | 用户（改造确认） | 前端新增「⬇ 下载 SKILL」入口 + 后端下载端点 `GET /api/neinsight/insight/{key}/skill`：新增 §6.11 `web/NEInsightSkillDownloadTest`（2 项，离线：命名规则定位/附件返回 + 缺失 404）；§3 分层表与 §8 套件入口纳入该类；回归：`mvn -o test` 合计 **68 项全绿**（含真实引擎 e2e 3 项），`validate_structure.py` **ERROR=0**。 |
| 2026-10-13 | 改造 v8 | 用户（改造确认） | 汇总后新增「生成洞察报告」节点（`generate-report`，11 → 12）：新增 §6.12 `NEInsightReportGenerationTest`（2 项：大模型生成/降级落盘）+ §6.13 `web/NEInsightReportDownloadTest`（2 项：命名规则定位/附件返回 + 缺失 404）；§2.2/§2.3 加 `Task_GenerateReport`/`generate-report`（节点 11 → 12）；§3 分层表、§7.1 覆盖矩阵、§8 套件入口同步；回归：`mvn -o test` 全绿，`validate_structure.py` **ERROR=0**（BPMN 契约 **12/12** 绑定 OK）。 |
| 2026-10-14 | 改造 v9 | 用户（改造确认） | 洞察报告增**联网检索**（应用侧接入检索通道再喂模型）：§6.12 升为 4 项（新增 P-2/P-3 提示词拼接）、新增 §6.14 `retrieval/InsightWebRetrieverTest`（4 项，离线解析/裁剪/容错/关闭降级）；§3 分层表、§7.1 覆盖矩阵、§8 套件入口（`AllNEInsightSuiteTest` 纳入该类）同步；检索通道**复用通用 `McpClientHandler`**，真实连通性由 `Utilities#McpSearchEndpointIT` 覆盖；回归：`mvn -o -pl OntologyFrameworkNEInsight test` 全绿，`validate_structure.py` **ERROR=0**（BPMN 契约 **12/12** 绑定 OK）。 |
| 2026-10-14 | 改造 v10 | 用户（改造确认） | 检索通道由 MCP 改为**外部 Web 检索 REST API**：§6.14 升为 6 项（R-1/R-2 分别覆盖 bocha/tavily 两种响应结构解析，新增 R-6 未配置 api-key 降级）；§3 分层表、§7.1 覆盖矩阵同步；HTTP 采用 JDK `HttpClient`（同 `LlmClient`），不再依赖 `McpClientHandler`；回归：`mvn -o -pl OntologyFrameworkNEInsight test` 全绿，`validate_structure.py` **ERROR=0**（BPMN 契约 **12/12** 绑定 OK）。 |
| 2026-10-14 | 改造 v11 | 用户（改造确认） | REST 通道沉淀为通用能力：`Utilities` 新增 `RestClientHandler`（§6.14 的 REST 调用改由其承载，解析/降级用例与项数**不变**，仍 6 项）；`Utilities` 内新增 `RestClientHandlerTest`（5 项，离线，本地 `HttpServer` 桩：头/体透传、非 2xx 语义、URL 拼接、传输失败包装）；`InsightWebRetrieverTest` 无需改动。回归：`mvn -o -pl Utilities test`（12 项）与 `mvn -o -pl OntologyFrameworkNEInsight test`（92 项）全绿，`validate_structure.py` **ERROR=0**（BPMN 契约 **12/12** 绑定 OK）。 |
| 2026-10-14 | 改造 v12 | 用户（改造确认） | **不再内置本体业务实例数据**：§5 测试数据高优先级机会点/目标改 `withTempAxioms` 临时注入；§6.1 P-2/P-4/P-5/P-6、§6.2 P-1 输入改临时注入；§6.3 P-1 期望改为 `opportunityPriority=NONE`/`smartCompliant=false`/外发不触发；§6.7 去「①本体 ABox 实例数据」断言；新增 §6.15 回归记录；回归：离线子集 **40/40 全绿**（`NEInsightSwrlRabbitMqTest`/`NEInsightBpmnRealEngineTest` 为显式环境依赖，离线不执行）。 |
