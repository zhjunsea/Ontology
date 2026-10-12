# NEInsight 应用 — 测试设计文档

> **用法**：本文件为第 8 步产出。**闸门⑤**：业务人员/负责人确认本文档后方可固化测试用例。
> **基线**：`OntologyFrameworkExample/src/test/java/.../{ExampleBpmnRealEngineTest, ExampleSwrlRabbitMqTest, AllExampleSuiteTest}.java`（形态）；模板 `OntologyAppBuilder/templates/测试设计文档模板.md`。
> **上游**：第 6 步单文件本体 `ontology/neinsight-all.owl`；第 9 步将 TBox/ABox 分离为两个 owl；第 13/14 步产出并测试 SKOS 词汇表。

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkNEInsight / NEInsight |
| 文档版本 | v1（待闸门⑤确认） |
| 生成日期 | 2026-10-12 |
| 关联本体 | `ontology/neinsight-all.owl`（单文件，含典型 ABox）；命名空间 `core#/env#/decision#/rules#/abox#` |
| 关联流程 | 《NEInsight应用-流程设计》（闸门①）；`ontology/bpmn/neInsightProcess.bpmn`（`NewEnergyInsightProcess`），Camunda 8 / Zeebe |
| 关联架构 | 《NEInsight应用-应用架构设计》（闸门④）§4 节点⇄jobType 契约、§5 逐节点算法 |
| 测试代码位置 | `src/test/java/com/ocean/ontologyframework/neinsight/`（+ `validation/` 子包） |
| 测试资源位置 | `src/test/resources/` |

> 本文档所有方法名、输入、断言**逐条取自测试源码 / 已确认设计**，未作发明。凡数值/阈值标 **【待考】**。

---

## 1. 目的与范围

**目的**：明确 NEInsight 应用的质量保证方式——测什么、怎么测、用什么数据、判定依据、如何执行。

**范围**：
- 覆盖 BPMN 流程端到端编排（10 个 serviceTask 顺序、1 个排他网关分支、无 incident、Start→End 可达；输入为洞察场景）；
- 覆盖 Openllet 推理（子类检索、互逆闭合、基数约束、SWRL R1/R1b/R2/R3 推导、一致性校验）；
- 覆盖 SWRL 推导结果驱动的 RabbitMQ 消息外发（高优先级机会点告警）；
- 覆盖 OWL 本体合规校验与 SKOS 术语结构/交叉校验（第 14 步）；
- 覆盖 Ontop OBDA 读写（第 11/12 步入库后接入）。

**不含**：EnvPrepare 环境的安装/启停本身（属相邻能力）；`OpenlletResolver`/`OntopOBDAHandler` 内核 API 的单元测试（属各库自身）。

## 2. 被测系统概述

### 2.1 业务链路（「五看三定」）
```
Start(输入:洞察场景 insightScenario) → 看行业/趋势 → 看市场/客户 → 看竞争 → 看自己
   → 看机会(SPAN) → 网关②优先级 ─┬ (HIGH)    → 高优先级标记+外发(RabbitMQ) ┐
                                  └ (非 HIGH) ──────────────────────────────┴→ 定战略控制点 → 定目标(SMART) → 定策略 → 汇总输出 → End
```

### 2.2 BPMN 元素 ID 与 jobType
| 环节 | 元素 ID | jobType |
|---|---|---|
| 流程开始 | `Start_01` | —（startEvent，输入 `insightScenario`） |
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
| 流程结束 | `End_01` | —（endEvent） |

### 2.3 BPMN ↔ JobWorker 绑定
`NEInsightOntologyJobWorker`（`@Profile("NEInsightBPMN")`）声明 **10 个** `@JobWorker`（`autoComplete=false`，显式完成/抛错）：`scan-industry-trend`、`scan-market-customer`、`scan-competition`、`scan-self`、`scan-opportunity`、`mark-high-priority`、`define-control-point`、`define-objective`、`define-strategy`、`summarize-insight`（原 13 个，随「输入 = 洞察场景」改造移除 `scan-it-space`/`scan-cloud-space`/`scan-combined-space`）。**BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」。**

### 2.4 关键类
`NEInsightApplication`（`setAdditionalProfiles("NEInsightBPMN")`）、`NEInsightOntologyJobWorker`、`web/NEInsightController`（POST `/api/neinsight/insight`、GET `/api/neinsight/insight/{key}`、GET `/api/neinsight/health`）；测试侧 `NEInsightBpmnRealEngineTest` / `NEInsightSwrlRabbitMqTest` / `NEInsightSkillGenerationTest` / `validation/NEInsightOntologyComplianceTest` / `AllNEInsightSuiteTest`。

## 3. 测试分层与策略

| 层级 | 测试类 | 依赖引擎 | 是否需环境 | 关注点 |
|---|---|---|---|---|
| 本体合规+推理（纯 Openllet） | `validation/NEInsightOntologyComplianceTest`（约 9 项） | 无 | 否 | 一致性、互逆闭合、基数、SWRL R1/R1b/R2/R3 |
| SWRL + 消息 | `NEInsightSwrlRabbitMqTest`（约 3 项） | 无 | RabbitMQ | 高优先级外发、非高不外发、队列投递 |
| 端到端（真实引擎） | `NEInsightBpmnRealEngineTest`（3 项） | 真实 Zeebe `localhost:26500` | 是 | 场景驱动编排、网关②分支、无 incident |
| 词汇表（纯 Jena） | `validation/NEInsightSkosTerminologyTest`（第 14 步） | 无 | 否 | SKOS 结构 + OWL 交叉校验 |
| SKILL 产出（纯 Openllet） | `NEInsightSkillGenerationTest`（1 项） | 无 | 否 | `summarize-insight` 生成**围绕输入场景**的洞察 SKILL：角色设定（含联网检索能力）+任务指令骨架 + 逐节点内嵌 ①ABox 实例数据 ②联网检索指令 ③数据加工与分析要求 → `skill/neinsight-<key>.md` |
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
| 高优先级机会点 | `abox#Opp_SmartEnergyCloud`（`marketAttractiveness=4.5`、`competitivePosition=4.0`、`hasSpanQuadrant→Q_HighHigh`、`belongsToDomain→CLOUD_SPACE`） | 触发 SWRL R1→`HIGH`、R2→`flaggedForAlert=true` |
| 低分机会点（边界） | `withTempAxioms` 临时注入（两轴均 <3.0） | 触发 SWRL R1b→`LOW` |
| 目标 | `abox#Obj_1`（`isMeasurable=true`、`isRelevant=true`、`hasTimeframe="2028"`） | 触发 SWRL R3→`smartCompliant=true` |
| 控制点 | `abox#CP_TechnologyLead`（`hasControlPointType→Type_TechnologyLead`、`addressesOpportunity→Opp_SmartEnergyCloud`） | 三定·控制点 |

> 变量选取原则：避免写入违反本体约束的 ABox 数据（有据可依，★8/10）；边界用临时公理（`withTempAxioms`）+ 用例后恢复。

## 6. 测试用例清单

> 编号约定：**P** 正向 / **B** 边界 / **N** 负向。用例须**驱动 BPMN** 走完拓扑，尤其覆盖边界场景。

### 6.1 本体合规 + 推理 `NEInsightOntologyComplianceTest`（约 9 项，无环境）
| ID | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|
| P-1 | `ontologyIsConsistent` | `neinsight-all.owl` | `reasoner.isConsistent()==true` |
| P-2 | `inversePropertiesClosed` | `belongsToDomain ⇄ hasElement`（及 §2 各互逆对） | 断言 `owl:inverseOf` 双向声明；对 `belongsToDomain` 断言可经逆向检索到要素 |
| P-3 | `cardinalityRestrictionsHold` | §4 约束类 | `Opportunity` 的 `hasSpanQuadrant` 限定基数 1、`marketAttractiveness/competitivePosition` **至多 1**；`Customer` `hasPainPoint` ≥1；`StrategicControlPoint` `hasControlPointType=1`/`addressesOpportunity≥1`；`Objective` `isMeasurable=1`/`hasMetric≥1`；`Strategy` `strategyOf≥1` |
| P-4 | `swrlR1DerivesHighPriority` | `Opp_SmartEnergyCloud` | 读 `opportunityPriority` == `"HIGH"`（推理所得） |
| P-5 | `swrlR2DerivesFlaggedForAlert` | `Opp_SmartEnergyCloud` | 读 `flaggedForAlert` == `true` |
| P-6 | `swrlR3DerivesSmartCompliant` | `Obj_1` | 读 `smartCompliant` == `true` |
| P-7 | `swrlR1bDerivesLowPriority` | `withTempAxioms` 注入两轴 <3.0 的机会点 | 读 `opportunityPriority` == `"LOW"`（`withTempAxioms` 结束后自动清理） |
| B-1 | `missingAxisNoPriority` | `withTempAxioms` 注入缺 `competitivePosition` 的机会点 | `opportunityPriority` 无推断值（不臆造） |
| N-1 | `quadrantCardinalityViolationDetected` | `withTempAxioms` 注入同一机会点挂 2 个**互异** `SPANQuadrant`（补 `owl:differentFrom`） | `validateAxioms` 抛 `InconsistentOntologyException`（函数属性+互异→真正违规） |

> **【实现期修正 · 修正记录（2026-10-12）】**
> 1. **两轴基数**：`Opportunity.marketAttractiveness/competitivePosition` 由 `exact 1` 放宽为 `max 1`（见《本体核心设计》§4 修正记录）。原因：Openllet 为「缺轴」生成匿名数据填充，SWRL 比较内建函数遇匿名项抛 NPE。放宽后 B-1「缺轴→不推断」可正常执行。
> 2. **N-1**：`hasSpanQuadrant` 为函数属性，两个不同填充会被推断为同一（一致）；须补 `owl:differentFrom` 才真正违反「至多 1」。且 `BackendService.validateAxioms` 在检出不一致时会抛 `InconsistentOntologyException`（非返回 false），故断言改为 `assertThrows`；用例后由 `@AfterEach` 快照原子恢复隔离。

### 6.2 SWRL + RabbitMQ `NEInsightSwrlRabbitMqTest`（约 3 项，需 RabbitMQ）
| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `highPriorityOpportunityDispatches` | 声明 `neinsight.opportunity.exchange`(direct)/`neinsightOpportunityQueue`/`opportunity.high.priority` 拓扑；对 `Opp_SmartEnergyCloud` 走外发 | 队列收到 JSON，含 `opportunityIri`/`priority=HIGH`/`flaggedForAlert=true` |
| N-1 | `nonHighNotDispatched` | 低分机会点（临时） | 不外发；`alertDispatched=false` |
| B-1 | `queuePrewarmPurge` | 用例前清空队列 | 队列初始为空 |

### 6.3 端到端（真实引擎）`NEInsightBpmnRealEngineTest`（3 项，需 Zeebe+Worker(+MQ)）
| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `insightCompletesForScenario` | `insightScenario=SCENARIO` | 全流程 `COMPLETED`；无 incident；`opportunityPriority=HIGH`（联合检索）；经 `mark-high-priority` 分支；`smartCompliant=true`；`ontologyValidationStatus=CONSISTENT` |
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
> 覆盖《应用架构设计》§8.1：`summarize-insight` 以「角色设定 + 任务指令」为骨架、逐节点内嵌 ①本体 ABox 实例数据、②🔎 联网检索指令（检索关键词/信息源/需采集字段）、③🧮 数据加工与分析要求，生成 Markdown 洞察 SKILL 落盘。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `generatesInsightSkill` | mock `ActivatedJob`/`JobClient`（`insightScenario=SCENARIO`），`skillDir` 指向 `@TempDir` | 生成 `neinsight-<key>.md`（**围绕输入场景**）；含「角色设定」（战略洞察分析师，声明**联网检索能力**）与「任务指令」骨架（要求**先联网采集再加工分析**），洞察内容**按 BPMN 流程节点顺序逐步构建**（【五看】节点 1~5、【三定】节点 6~8，含节点 id/jobType）；**8 个五看三定节点均含「🔎 联网检索指令」（检索关键词/建议信息源/需采集的数据字段）与「🧮 数据加工与分析要求」**；含流程起点「输入洞察场景」、检索根域（`联合空间`） |

> 该用例为**离线**（纯 Openllet，无需引擎），可直接回归 SKILL 生成逻辑，避免依赖真实 Zeebe/Worker。

## 7. 覆盖矩阵

### 7.1 流程节点覆盖
| BPMN 节点 id | 覆盖用例 | 说明 |
|---|---|---|
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

> 原 `Gateway_DomainChoice` 与 3 个域扫描节点（`Task_ScanITSpace`/`_CloudSpace`/`_CombinedSpace`）已随改造移除；网关②「非高优先级」分支在当前 ABox 数据（联合检索含 HIGH 机会点）下不被端到端命中，其条件由 §6.1 B-1 / §6.2 N-1 从本体侧覆盖。

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

## 8. 执行方式

- 全套入口：`AllNEInsightSuiteTest`（`@Suite` + `@SelectClasses`，聚合 `validation/NEInsightOntologyComplianceTest`、`validation/NEInsightSkosTerminologyTest`、`NEInsightSkillGenerationTest`、`NEInsightSwrlRabbitMqTest`、`NEInsightBpmnRealEngineTest`）。
- 命令：`mvn -pl OntologyFrameworkNEInsight test`（或 reactor 全量 `mvn -pl OntologyFrameworkNEInsight -am test`）。
- 环境依赖项（Zeebe/Ontop/MySQL/RabbitMQ）已在上表显式标注；纯本体层可离线执行。

## 9. 待确认项（闸门⑤）

| # | 问题 | 结论 |
|---|---|---|
| 1 | 测试分层（本体合规 / SWRL+消息 / 真实引擎端到端 / 词汇表 / OBDA 集成）是否认可 | ✅ 已认可 |
| 2 | 用例清单（§6：正向+边界+负向）是否齐备、是否认可 | ✅ 已认可（B-1/N-1 于实现期按引擎约束修正，见 §6.1 修正记录） |
| 3 | 覆盖矩阵（§7：改造后 10 节点 + 全部约束 + 边界清单）是否认可 | ✅ 已认可（改造后 10 节点） |
| 4 | 测试 Profile 命名（`NEInsightJunitTest` / `NEInsightBPMN`）与套件入口 `AllNEInsightSuiteTest` 是否认可 | ✅ 已认可 |
| 5 | 【待考】阈值/量纲在测试中以占位（3.0）驱动，确认后替换 | ✅ 占位继续，待业务口径替换 |

---

## 闸门⑤留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-12 | v1 | 用户 | 认可测试分层/用例清单/覆盖矩阵/Profile 与套件入口，同意实现测试类；并确认两处实现期修正：B-1 放宽两轴基数为 max 1、N-1 补 owl:differentFrom 并断言抛异常。 |
| 2026-10-12 | 改造 v2 | 用户（改造确认） | 输入改场景：端到端用例改为 `insightCompletesForScenario`/`insightProducesSkill`/`blankScenarioRejected`（3 项，原 4 项域用例移除）；SKILL 用例输入改 `insightScenario`；覆盖矩阵 13→10 节点 |
| 2026-10-12 | 改造 v3 | 用户（改造确认） | §6.7 SKILL 用例断言扩充：新增对「联网检索指令（检索关键词/建议信息源/需采集的数据字段）」与「数据加工与分析要求」的校验，并断言 **8 个五看三定节点均含**该两类指令；用例数与分层不变 |
