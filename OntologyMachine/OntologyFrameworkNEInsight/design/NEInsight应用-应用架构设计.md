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
│ NEInsightOntologyJobWorker(12)  │
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
| `com.ocean:Utilities` | 0.0.1-SNAPSHOT | ProcessOrchestrator / RabbitMqHandler / OntologyWorkerSupport / LlmClient（通用大模型客户端，入口 `analyze-input` 使用；见《Reference/Utilities-API》§13） |
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
| `NEInsightOntologyJobWorker.java` | `@Component @Profile("NEInsightBPMN")`，继承 `OntologyWorkerSupport`；12 个 `@JobWorker`（入口 `analyze-input` 输入分析 + 五看三定 + 汇总 `summarize-insight` + 生成报告 `generate-report`；原 13 个，随改造移除 3 个域扫描 Worker）；入口节点复用 `analyze/InputAnalysis` |
| `analyze/InputAnalysis.java` | 输入分析**通用能力**（静态工具，**与业务无关**）：抽取结构化信息、判定必要信息 `{scenario,identity,industry,objective}` 是否齐全、对缺失项生成追问；供 Worker（入口节点）与 Controller（澄清接口）复用（★21） |
| `web/NEInsightController.java` | `@RestController`；POST `/api/neinsight/analyze` 输入分析/澄清；POST `/api/neinsight/insight` 启动（**必要信息齐全才启动**）；GET `/api/neinsight/insight/{key}` 查询；GET `/api/neinsight/insight/{key}/skill` **下载洞察 SKILL**；GET `/api/neinsight/insight/{key}/report` **下载洞察报告**；GET `/api/neinsight/health` |

### 3.2 前端（`src/main/resources/static/`）
| 文件 | 职责 |
|---|---|
| `index.html` | **输入洞察场景（文本）** → 启动流程 → 每秒轮询 → 展示洞察摘要、实例 IRI、本体验证状态与洞察 SKILL/报告路径，并**提供「⬇ 下载 SKILL 文件」「⬇ 下载洞察报告」入口**（指向对应下载端点） |

## 4. BPMN 节点 ⇄ JobWorker 契约表（核心）

> **一一对应**：BPMN 每个 `serviceTask` 的 `zeebe:taskDefinition type` ⇄ 一个 `@JobWorker(type)`。此表由 `scripts/validate_structure.py` 做机检。

| # | BPMN 节点 id | jobType | 业务规则（来自流程设计 §3） | 算法（业务确认） | 输入变量 | 输出变量 | 确认状态 |
|---|---|---|---|---|---|---|---|
| 1 | `Task_AnalyzeInput` | `analyze-input` | **输入分析（入口）**：调用大模型解析客户输入的「洞察场景」，抽取身份/场景/行业/客户/目标等关键信息，并**对场景做准确精炼**（★「场景信息要获取准确」） | 经通用能力 `analyze/InputAnalysis.analyze(llm, ...)`：LLM 可用时 `LlmClient.chat(SYSTEM_PROMPT, ...)` → `parse()`（要求模型只输出严格 JSON，容忍 ```json 代码块包裹）；不可用/解析失败/字段缺失时降级为确定性提取（**仅取场景原文、其余字段留空，不内置任何业务词表**）。并据此判定必要信息 `{scenario,identity,industry,objective}` 是否齐全、对缺失项生成追问；精炼场景为空则回退原文 | `insightScenario`,`scenario`,`identity`,`industry`,`objective`（后四者由启动时经澄清确认后传入） | `inputAnalysisResult`,`refinedScenario`,`inputAnalysisSource`,`inputIdentity`,`inputMissingFields` | ☐待确认 |
| 2 | `Task_ScanIndustryTrend` | `scan-industry-trend` | 看行业/趋势（S1–S4） | 查检索根域下 `IndustryTrend` 及其 `indicatesValueTransfer`/`hasMacroFactor`，标注「趋势＞优势」价值转移 | `insightScenario` | `industryTrendResult` | ☐待确认 |
| 3 | `Task_ScanMarketCustomer` | `scan-market-customer` | 看市场/客户（S5） | 查 `Market`/`Customer` + `hasPainPoint`/`hasNeed`，汇总客户画像与痛点/需求 | `insightScenario` | `marketCustomerResult` | ☐待确认 |
| 4 | `Task_ScanCompetition` | `scan-competition` | 看竞争 | 查 `CompetitiveLandscape` + `involvesCompetitor→Competitor`，汇总竞品（【待考】不指名，取 label） | `insightScenario` | `competitionResult` | ☐待确认 |
| 5 | `Task_ScanSelf` | `scan-self` | 看自己（S6） | 查 `SelfAssessment` + `hasCapability`/`hasShortBoard`，汇总能力评分与短板 | `insightScenario` | `selfAssessmentResult` | ☐待确认 |
| 6 | `Task_ScanOpportunity` | `scan-opportunity` | 看机会（SPAN 定位） | 查检索根域 `Opportunity` 的 `marketAttractiveness`/`competitivePosition`/`hasSpanQuadrant`；经推理（SWRL R1/R1b）得 `opportunityPriority` | `insightScenario` | `opportunityResult`,`opportunityPriority` | ☐待确认 |
| 7 | `Task_MarkHighPriority` | `mark-high-priority` | 高优先级标记 + 外发 | 读 `opportunityPriority`；HIGH 时经 SWRL R2 推 `flaggedForAlert=true`，并经 `RabbitMqHandler` 外发告警 | `insightScenario` | `alertDispatched` | ☐待确认 |
| 8 | `Task_DefineControlPoint` | `define-control-point` | 定战略控制点 | 针对高优先级机会点（`addressesOpportunity`），从枚举 `ControlPointType`{技术领先/成本优势/品牌/市场份额/价值链控制} 选定控制点 | `insightScenario` | `controlPointResult` | ☐待确认 |
| 9 | `Task_DefineObjective` | `define-objective` | 定目标（SMART） | 依控制点制定目标；经 SWRL R3 校验 `smartCompliant`（M/R/T 齐备） | `insightScenario` | `objectiveResult`,`smartCompliant` | ☐待确认 |
| 10 | `Task_DefineStrategy` | `define-strategy` | 定策略 | 依目标制定策略（`OrgStrategy`/`ProductStrategy`/`MarketStrategy`），`hasStrategy` 关联 | `insightScenario` | `strategyResult` | ☐待确认 |
| 11 | `Task_Summarize` | `summarize-insight` | 汇总输出 | 生成**围绕输入场景**的「洞察报告生成 SKILL」——以「角色设定 + 任务指令」为骨架、按「五看三定」逐节点给出 **①🔎 联网检索指令（检索关键词/信息源/需采集字段）②🧮 数据加工与分析要求 + 📚 方法论来源**（**取自本体 `InsightMethod` 个体**，见改造 v6；**不再内嵌本体业务实例数据**，见改造 v12），落盘 `skill/neinsight-<instanceKey>.md` 供大模型真正联网检索、加工、分析后生成洞察报告；Openllet 一致性校验；回写摘要/实例 IRI/校验状态/SKILL 路径 | `insightScenario`（经 `currentScenario` **优先取入口精炼的 `refinedScenario`**，回退 `insightScenario`） | `insightSummary`,`instanceIri`,`ontologyValidationStatus`,`insightSkillPath` | ☐待确认 |
| 12 | `Task_GenerateReport` | `generate-report` | 生成洞察报告 | 读取汇总产出的洞察 SKILL（流程变量 `insightSkillPath`；缺失/非法时按命名规则回退 `<neinsight.skill.dir>/neinsight-<instanceKey>.md`，须落 SKILL 目录内），**先按 SKILL「三、洞察内容」各节点的「🔎 联网检索指令」逐节点联网采集外部公开数据**（检索关键词取自本体 `core#InsightMethod` 的 `retrievalKeyword`，★21 不硬编码业务词表；经模块内 `retrieval/InsightWebRetriever` 走**外部 Web 检索 REST API**（默认 `provider=bocha`，可切 `tavily`；Bearer 鉴权；每节点裁剪为紧凑来源清单），再连同 SKILL 一并交大模型 `LlmClient.chat(system, user)` 加工分析生成《洞察报告》并落盘 `report/neinsight-<instanceKey>-洞察报告.md`；**检索通道不可用/未启用 → 退回「仅据 SKILL 与本体基线」生成**，大模型不可用/失败 → 再**降级为直接落盘 SKILL 内容**；报告写盘失败仅记日志；回写报告路径 | `insightScenario`（经 `currentScenario` 优先取 `refinedScenario`）、`insightSkillPath` | `insightReportPath` | ☐待确认 |

> 1 个排他网关：`Gateway_PriorityChoice`（`opportunityPriority = "HIGH"` / `!= "HIGH"`），条件互斥且完备。原 `Gateway_DomainChoice`（洞察域选择）已随改造移除。
>
> **输入与检索根域**：流程唯一业务输入为 `insightScenario`（洞察场景文本）。域概念并入场景表达后不再由用户选择；Worker 内部检索根域固定为**联合域 `COMBINED`**（覆盖 IT 空间与云空间全部要素），沿 `hasElement` 展开的 ABox/OBDA 检索口径**保持不变**。原 3 个域扫描 Worker（`scan-it-space`/`scan-cloud-space`/`scan-combined-space`）与 `domainBaseline` 变量已移除。

## 5. 逐节点算法确认（闸门④）

> 对**每个节点**记录：确认的问题、业务给出的算法口径、边界处理。**未确认不得实现该 Worker。** 待业务填写。

| 节点 id | 确认问题 | 业务结论（算法口径） | 边界/异常处理 | 确认人 | 日期 |
|---|---|---|---|---|---|
| `Task_AnalyzeInput` | 入口如何解析客户输入、如何保证场景准确、缺失必要信息怎么办？ | 认可（实现口径：调用大模型按严格 JSON 抽取 `identity`/`scenario`/`industry`/`customer`/`objective`/`region`/`timeframe`/`keyEntities`/`confidence`，其中 `scenario` 为对输入场景的精炼、忠于原文、不添加原文没有的信息；结果经 `refinedScenario` 供后续节点与 SKILL 生成复用。**必要信息 `{scenario,identity,industry,objective}` 缺失时，由 Web 层澄清接口前置追问补齐后方可启动**，流程本身保持全自动、拓扑不变） | 大模型未配置（`llm==null`）/不可用/返回非严格 JSON → 降级为确定性提取（仅取场景原文、其余字段留空 → `inputAnalysisSource=HEURISTIC` 并触发追问），流程不中断；精炼场景为空 → 回退原输入 | | |
| `Task_ScanIndustryTrend` | 趋势与价值转移如何取？ | 待确认 | 无价值转移则不标注 | | |
| `Task_ScanMarketCustomer` | 痛点/需求口径？ | 待确认 | 无痛点 → 记录为空（注意 `Customer` min 1 约束） | | |
| `Task_ScanCompetition` | 竞品是否指名？ | 待确认（【待考】：不指名，仅取 label） | 无竞品 → 空表 | | |
| `Task_ScanSelf` | 能力评分量纲？ | 待确认（【待考】） | 无能力项 → 空 | | |
| `Task_ScanOpportunity` | SPAN 分档阈值？ | 待确认（【待考】，暂用 3.0 占位，由 SWRL R1/R1b 判定） | 缺两轴评分 → 优先级不推断 | | |
| `Task_MarkHighPriority` | 外发触发条件与通道？ | 待确认（高优先级机会点/关键风险 → `neinsight.opportunity.exchange`） | 非 HIGH 不进入本节点（网关②保证） | | |
| `Task_DefineControlPoint` | 控制点选择规则？ | 待确认 | 无机会点 → 报错/兜底 | | |
| `Task_DefineObjective` | SMART 判据量纲？ | 待确认（【待考】） | 不合 SMART → `smartCompliant=false`，不阻断 | | |
| `Task_DefineStrategy` | 策略维度口径？ | 待确认 | 无目标 → 报错 | | |
| `Task_Summarize` | 展示/留档口径？ | 认可（实现口径：以「角色设定 + 任务指令」为骨架 + 逐节点给出 ①联网检索指令 ②数据加工与分析要求 + 📚 方法论来源（取自本体 `InsightMethod`；不再内嵌本体业务实例数据）→ 生成「给大模型生成洞察报告的 SKILL」落盘 `skill/neinsight-<instanceKey>.md`） | SKILL 写盘失败 → 仅记日志、不阻断流程；一致性校验失败 → `ontologyValidationStatus=INCONSISTENT` | | |
| `Task_GenerateReport` | 汇总后如何生成洞察报告、报告如何供下载？ | 认可（实现口径：读取洞察 SKILL → **按 SKILL 各节点「🔎 联网检索指令」经 `retrieval/InsightWebRetriever` 逐节点联网采集外部公开数据** → 连同 SKILL 交大模型 `chat` 加工分析生成《洞察报告》→ 落盘 `report/neinsight-<instanceKey>-洞察报告.md`（配置 `neinsight.report.dir`）→ 回写 `insightReportPath`，经 `GET /api/neinsight/insight/{key}/report` 下载） | 检索通道不可用 → 退回「仅据 SKILL 与本体基线」生成；大模型不可用/失败 → 降级为直接落盘 SKILL 内容；报告写盘失败 → 仅记日志、不阻断流程 | | |

## 6. 前端与 API 设计

| 端点 | 方法 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| `/api/neinsight/analyze` | POST | `{insightScenario, scenario?, identity?, industry?, objective?, ...}` | `{analysis, missingFields, questions, ready, source}` | **输入分析/澄清**：抽取信息并返回缺失必要信息与追问；前端据此多轮补齐 |
| `/api/neinsight/insight` | POST | `{insightScenario, scenario?, identity?, industry?, objective?, ...}` | `{instanceKey, processId, insightScenario, analysis}` | 启动洞察流程；场景为空 → 400；**必要信息未齐全 → 400（附 `missingFields`/`questions`）** |
| `/api/neinsight/insight/{key}` | GET | path `key` | `{instanceKey, status, variables, insightSummary, instanceIri, ontologyValidationStatus, insightSkillPath, insightReportPath}` | 查询状态/结果（含 SKILL / 报告落盘路径） |
| `/api/neinsight/insight/{key}/skill` | GET | path `key` | SKILL 文件（`text/markdown; charset=UTF-8`，`Content-Disposition: attachment; filename="neinsight-<key>.md"`） | **下载本次流程自动生成的洞察 SKILL**（`skill/neinsight-<key>.md`）：优先取流程变量 `insightSkillPath`，缺失/非法时按命名规则回退；两路径均须落在配置 `neinsight.skill.dir` 之内（防路径穿越）；文件不存在 → 404 |
| `/api/neinsight/insight/{key}/report` | GET | path `key` | 报告文件（`text/markdown; charset=UTF-8`，`Content-Disposition: attachment`） | **下载本次流程自动生成的洞察报告**（`report/neinsight-<key>-洞察报告.md`）：优先取流程变量 `insightReportPath`，缺失/非法时按命名规则回退；两路径均须落在配置 `neinsight.report.dir` 之内（防路径穿越）；文件不存在 → 404 |
| `/api/neinsight/health` | GET | — | `{engineAvailable, processId, bpmnPath, llmAvailable}` | 健康检查 |

## 7. 配置（`application.yaml`）

| 键 | 值 |
|---|---|
| `server.port` | `9084` |
| `spring.application.name` | `ontology-framework-neinsight` |
| `camunda.client.grpc-address` / `rest-address` | `localhost:26500` / `localhost:9080` |
| `camunda.client.mode` | `self-managed`（`auth.type=none`） |
| `ontology.main-path` | `ontology/neinsight-all.owl` |
| `neinsight.skill.dir` | `skill`（洞察 SKILL 落盘目录，相对模块工作目录；`summarize-insight` 生成） |
| `neinsight.report.dir` | `report`（洞察报告落盘目录，相对模块工作目录；`generate-report` 生成） |
| `neinsight.websearch.enabled` | `true`（联网检索通道开关；关闭 → 报告退回「仅据 SKILL 与本体基线」生成） |
| `neinsight.websearch.provider` | `bocha`（检索服务商：`bocha`/`tavily`；纯 REST、Bearer 鉴权） |
| `neinsight.websearch.base-url` / `path` | `https://api.bochaai.com` / （留空按 provider 取默认：bocha=`/v1/web-search`、tavily=`/search`） |
| `neinsight.websearch.api-key` | （检索服务 API Key，Bearer 令牌；未配置则跳过联网检索、报告退化生成） |
| `neinsight.websearch.max-results-per-node` / `excerpt-chars` | `4` / `600`（每节点最多结果条数 / 每条摘要最大字符数，控制送入大模型的体积） |
| `neinsight.websearch.freshness` / `timeout-seconds` | `noLimit` / `15`（结果时效过滤（仅 bocha）/ 单次检索请求超时秒数） |
| `llm.timeout-seconds` | `300`（单次大模型调用超时；洞察报告生成属长任务，实测约 80~110s，须显著大于输入分析耗时） |
| `[ontology.abox-path]` | （第 9 步分离后新增，指向 `ontology/neinsight-abox.owl`） |
| `ontology.obda-path` / `obda-properties-path` | `ontology/database/neinsight.obda` / `ontology/database/neinsight.properties`（第 11 步） |
| `ontology.bpmn-path` | `ontology/bpmn/neInsightProcess.bpmn` |
| `env-prepare.mysql.db-name` | `neinsightdb` |
| `env-prepare.rabbitmq.exchange` / `queue` / `routing-key` | `neinsight.opportunity.exchange` / `neinsightOpportunityQueue` / `opportunity.high.priority` |
| `env-prepare.ontology-catalog` | `ontology/catalog-v001.xml` |
| `env-prepare.ddl-file` / `data-file` | `ontology/database/createdb_tables.ddl` / `ontology/database/data.sql` |

## 8. 解耦与端到端数据流

1. **输入场景**：前端输入洞察场景（自由文本）。
2. **输入分析 / 澄清（前置）**：前端 POST `/api/neinsight/analyze` → `InputAnalysis.analyze()` 抽取信息并返回 `{analysis, missingFields, questions, ready}`；`ready=false` 时前端引导客户多轮补充（填写必要字段或补充描述）直至齐全。
3. **启动流程**：POST `/api/neinsight/insight {insightScenario, scenario, identity, industry, objective}` → 服务端校验必要信息齐全（不齐返回 400 附缺失项与追问）→ `ProcessOrchestrator.start()`。
4. **入口输入分析（流程内兜底）**：BPMN 入口 `analyze-input` 复用同一 `InputAnalysis`，解析客户输入、抽取身份/场景等关键信息并**精炼场景**（`refinedScenario`）；大模型不可用/失败则降级为确定性提取。该结果作为后续各节点与 SKILL 生成的结构化输入（★「场景信息要获取准确」）。
5. **五看扫描（方法论口径）**：Worker 各节点**不再查询/输出本体业务实例数据**，仅回写洞察域/场景/方法论说明（检索根域固定联合域）；检索与加工口径见 SKILL，由 `generate-report` 联网采集。
6. **SPAN 定位**：`scan-opportunity` 不再内嵌机会点实例，`opportunityPriority` 恒为 `NONE`；网关②恒走「非高优先级」分支（高优先级分支与外发不触发，SWRL R1/R1b/R2 定义保留）。
7. **外发**（定义保留，当前不触发）：`mark-high-priority` 节点保留，因不再提供机会点实例数据，恒跳过外发 → `alertDispatched=false`。
8. **三定**：控制点/目标（SMART R3）/策略逐节点产出。
9. **汇总与 SKILL 生成**：`summarize-insight` 一致性校验 + 逐节点组装**围绕该场景（优先采用入口精炼场景）**的「洞察报告生成 SKILL」（①🔎 联网检索指令 + ②🧮 数据加工与分析要求 + 📚 方法论来源；**不再内嵌本体业务实例数据**；落盘 `skill/neinsight-<instanceKey>.md`）+ 回写摘要/实例 IRI/校验状态/SKILL 路径。
10. **生成洞察报告（含联网检索）**：`generate-report` 读取上一步落盘的 SKILL，**先按 SKILL 各节点「🔎 联网检索指令」经 `retrieval/InsightWebRetriever` 逐节点联网采集外部公开数据**，再连同 SKILL 导入大模型 `LlmClient.chat()` 加工分析生成《洞察报告》并落盘 `report/neinsight-<instanceKey>-洞察报告.md`；检索通道不可用时退回「仅据 SKILL 与本体基线」生成，大模型不可用/失败降级为直接落盘 SKILL 内容；回写 `insightReportPath`。
11. **结果回显与下载**：前端轮询 GET，COMPLETED 后显示洞察摘要、实例 IRI、本体验证状态、SKILL/报告路径与关键变量，并提供「⬇ 下载 SKILL 文件」（GET `/api/neinsight/insight/{key}/skill`）与「⬇ 下载洞察报告」（GET `/api/neinsight/insight/{key}/report`）入口，点击即下载对应文件。

### 8.1 洞察报告生成 SKILL（Markdown）产出

`summarize-insight` 生成 `skill/neinsight-<instanceKey>.md`——一份**可直接输入大模型**的提示词 SKILL（大模型据此生成洞察报告），口径为 **「TBox = 分析的维度与方法、ABox = 逐节点方法论（`core:InsightMethod`）+ 逐节点联网检索与数据加工分析指令；不内置业务实例数据」**：

- **角色设定 + 任务指令（SKILL 骨架）**：明确大模型扮演「战略洞察分析师」角色、**具备联网检索能力**，任务=**按「三、洞察内容」所列 BPMN 流程节点的先后顺序逐节点逐步构建**《新能源行业洞察报告》（依次完成「五看」各节点 → 「三定」各节点 → 汇总结论）；任务指令要求**每个节点先联网采集数据、再做加工分析**（**以本体逐节点方法论为知识基线、不再内嵌业务实例数据**；结论须完全基于联网采集的外部公开数据并标注来源），结论有据可依、来源可溯、缺失项标注「待补充」、规范 Markdown 输出；
- **洞察内容（内嵌输入·按 BPMN 流程逐步构建）**：即本次流程采集到的待洞察内容——**严格按 BPMN `NewEnergyInsightProcess` 的 serviceTask 执行顺序逐节排列**，每节标注对应节点（`节点 id` / `jobType`）与其产出的流程变量，每节含两部分：**①🔎 联网检索指令**（依据输入场景动态生成的检索关键词 / 建议信息源 / 需采集数据字段）、**②🧮 数据加工与分析要求**（该节点数据应如何加工分析、产出什么结论），并附 **📚 方法论来源**。流程起点「输入洞察场景」→【五看】（看行业/趋势、看市场/客户、看竞争、看自己、看机会）→【三定】（定战略控制点、定目标、定策略）；①②均**由本体 `core#InsightMethod` 个体读取后渲染**（**不再检索本体业务实例数据**、**不硬编码业务数据**——★21）——`appendMethodFromOntology(sceneLabel, jobType)` 按 jobType 检索该方法个体（`retrievalKeyword`/`dataSource`/`dataField`/`analysisStep`/`methodOutput`/`sourceRef`），输出「🔎 联网检索指令 + 🧮 数据加工与分析要求 + 📚 方法论来源」，也**不在 Java 中硬编码方法**（★12/★21）。②的检索关键词/信息源在 `generate-report` 环节**由程序侧检索通道 `retrieval/InsightWebRetriever` 实际执行**（见 §4 #12／§8 步骤 10；改造 v9 将 v3「检索由大模型按指令执行、程序侧不接检索」**反转为「程序侧接入检索通道后再交大模型分析」**，以兑现「大模型据 SKILL 真正联网检索」）；
- **数据校验与提示**：附本体一致性状态与数据缺失（待补）项提示，供大模型撰写时标注；
- **落盘**：目录取自配置 `neinsight.skill.dir`（默认 `skill`，相对模块工作目录，**不硬编码绝对路径**）；文件名 `neinsight-<instanceKey>.md`；写盘失败仅记日志、不阻断流程。SKILL 路径经流程变量 `insightSkillPath` 回写，可由查询接口获取，或经下载端点 `GET /api/neinsight/insight/{key}/skill` 直接下载该文件。

**洞察报告产出**：紧接着的 `generate-report` 节点读取该 SKILL，**先按 SKILL 各节点「🔎 联网检索指令」经 `retrieval/InsightWebRetriever` 逐节点联网采集外部公开数据**（走外部 Web 检索 **REST API**：`provider`/`base-url`/`path`/`api-key`/`freshness`/每节点条数与摘要长度由 `neinsight.websearch.*` 配置，Bearer 鉴权，REST 调用复用通用 `Utilities#RestClientHandler`；检索失败仅记日志、跳过该节点），再连同 SKILL 交大模型 `LlmClient.chat()` 加工分析生成《洞察报告》（Markdown，外部数据标注来源 URL），落盘 `report/neinsight-<instanceKey>-洞察报告.md`（目录取自 `neinsight.report.dir`），并回写流程变量 `insightReportPath`；检索通道不可用时退回「仅据 SKILL 与本体基线」生成，大模型不可用/失败时降级为直接落盘 SKILL 内容（保证流程不阻断），可由下载端点 `GET /api/neinsight/insight/{key}/report` 直接下载。

## 9. API 复用声明（★铁律 21）

- 逻辑一律经 `BackendService` / `OntologyService` / `OBDAHandler` / `ProcessOrchestrator` / `RabbitMqHandler` / `OntologyWorkerSupport` 等**现存 API**。
- **未重写** `Reference/` 三份 API 文档中的任何 API（清单见 `references/API复用清单.md`）。
- 入口「输入分析」所需的大模型能力**复用通用 `LlmClient`**（由 TCM 既有实现提升至 `Utilities` 共用，包 `com.ocean.utilities`，★21「先扩展现有 API 再复用」，未重写等价功能）；NEInsight 经 `@Autowired(required=false) LlmClient` 注入并复用其 `chat`/`isAvailable`。
- 新增模块内通用能力 `analyze/InputAnalysis`（NEInsight 模块内静态工具，供 Worker 与 Controller 复用，避免重复实现）。
- 新增公用 API：`Utilities#LlmClient`（见《Reference/Utilities-API》§14）、`Utilities#RestClientHandler`（§13）；其余无新增。
- 洞察报告「联网检索」由模块内 `retrieval/InsightWebRetriever` 走**外部 Web 检索 REST API**（默认 `provider=bocha`，可切 `tavily`；Bearer 鉴权），其 HTTP 调用**复用通用 `Utilities#RestClientHandler`**（★21；改造 v11 起 `InsightWebRetriever` 与 `LlmClient` 的 REST 调用均统一复用该类，不再各自手写 JDK `java.net.http.HttpClient`；v10 起不再依赖 `McpClientHandler`）。

## 10. 待业务确认项

| # | 问题 | 业务结论 |
|---|---|---|
| 1 | ~~13 个节点 ⇄ 13 个 jobType~~ → ~~10 个节点 ⇄ 10 个 jobType~~ → 11 个节点 ⇄ 11 个 jobType 命名是否认可 | 认可（域扫描节点已移除；入口新增 `analyze-input` 输入分析，10 → 11） |
| 2 | 各节点算法口径（§5）是否认可 | 认可 |
| 3 | SPAN 分档阈值 / SMART 量纲 / 竞品口径（【待考】） | 认可（保持占位，SWRL 以 3.0 占位阈值驱动） |
| 4 | 外发通道命名是否沿用流程设计 §6 | 认可 |
| 5 | 端口 9084 / Profile `NEInsightBPMN` / 库名 `neinsightdb` | 认可 |
| 6 | 输入改为洞察场景（`insightScenario`）、检索根域固定联合域、五看三定与 SKILL 围绕场景 | 认可（2026-10-12 改造） |
| 7 | SKILL 由「仅内嵌洞察内容（骨架）」扩为「逐节点内嵌 ①本体实例数据 + ②联网检索指令 + ③数据加工与分析要求」，检索由大模型按指令执行（程序侧不接检索） | 认可（2026-10-12 改造 v3） |
| 8 | 入口新增「输入分析」节点（`analyze-input`）：调用大模型解析客户输入、抽取身份/场景等关键信息并精炼场景；大模型不可用时降级为确定性启发式提取 | 认可（2026-10-13 改造 v4） |
| 9 | 通用大模型客户端 `LlmClient` 由 TCM 提升至 `Utilities` 共用（TCM 改为引用，NEInsight 复用；★21） | 认可（2026-10-13 改造 v4） |
| 10 | 输入处理新增「必要信息齐全性门禁 + 服务端多轮澄清」：必要信息 = `{scenario, identity, industry, objective}`，缺失即经 `/api/neinsight/analyze` 追问、齐全后方可启动；并**清除代码中一切具体业务硬编码**（提示词示例、启发式词表、检索关键词模板中的业务词） | 认可（2026-10-13 改造 v5） |
| 11 | 逐节点「洞察方法」由 Java 硬编码迁移为**本体个体**（`core#InsightMethod`，7 属性/8 个体），Worker 改为 `appendMethodFromOntology` 从本体读取渲染；SKILL 的「🔎 检索指令 + 🧮 加工分析要求 + 📚 方法论来源」均来自本体 | 认可（2026-10-13 改造 v6） |
| 12 | 前端新增「⬇ 下载洞察 SKILL」入口，后端增下载端点 `GET /api/neinsight/insight/{key}/skill`（优先取流程变量 `insightSkillPath`、按命名规则回退；仅允许命中 `neinsight.skill.dir` 目录内文件；不存在 → 404） | 认可（2026-10-13 改造 v7） |
| 13 | 汇总后新增「生成洞察报告」节点（`generate-report`）：读 SKILL → 大模型生成《洞察报告》→ 落盘 `report/` → 回写 `insightReportPath`；后端增下载端点 `GET /api/neinsight/insight/{key}/report`；大模型不可用降级为直接落盘 SKILL 内容 | 认可（2026-10-13 改造 v8） |
| 14 | 洞察报告「联网检索」实现口径：由**程序侧检索通道** `retrieval/InsightWebRetriever`（走**外部 Web 检索 REST API**：默认 `provider=bocha`，可切 `tavily`，Bearer 鉴权）按 SKILL 各节点「🔎 联网检索指令」逐节点采集外部公开数据，再连同 SKILL 交大模型加工分析成文；并放宽 `llm.timeout-seconds`（20 → 300，报告生成为长任务） | 认可（2026-10-14 改造 v9；检索通道 v10 REST 化） |
| 15 | **NEInsight 不再内置本体业务实例数据（内部知识基线不通用）**：五看/三定 8 个节点保留流程拓扑但**不再检索/输出本体实例数据**（`industryTrendResult` 等仅透传场景/域/方法论说明）；ABox 由 44 个体精简为 **11**（3 域个体 + 8 方法论 `IM_*`）；SKILL 去掉逐节点①本体实例数据，改为「**方法论基线 + 场景×方法论检索指令 + 数据加工分析**」；`opportunityPriority` 恒 `NONE`、`smartCompliant` 恒 `false`，SWRL R1–R4 与 `mark-high-priority`/RabbitMQ 外发**保留定义但不再触发**；OBDA/DB 同步仅留域表 | 认可（2026-10-14 改造 v12） |

---

## 闸门④留痕
| 日期 | 确认范围（节点集） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-12 | 全部节点（改造后 10 个） | 用户（改造确认） | 输入改场景（`insightScenario`）；移除 `Task_ScanITSpace`/`_CloudSpace`/`_CombinedSpace`（13→10）；检索根域固定联合域；SKILL 围绕场景组织 |
| 2026-10-12 | 汇总节点 `Task_Summarize`（§8.1 SKILL 口径） | 用户（改造确认 v3） | SKILL 逐节点增加「🔎 联网检索指令 + 🧮 数据加工与分析要求」（共 8 个五看三定节点）；大模型据此真正联网检索、加工、分析，而非仅按骨架作答；程序侧不新增检索依赖（§5 算法与 §4 契约不变） |
| 2026-10-12 | 13 个 ServiceTask（域扫描×3、五看×5、外发×1、三定×3、汇总×1） | 用户 | 13 节点算法全部认可；按《应用架构设计》§4/§5 实现 `NEInsightOntologyJobWorker` 及 Application/application.yaml/pom.xml；【待考】阈值保持占位（SWRL 以 3.0 占位），不伪造。 |
| 2026-10-13 | 入口节点 `Task_AnalyzeInput`（改造 v4） | 用户（改造确认） | 流程起点新增「输入分析」`analyze-input`：大模型解析客户输入、抽取身份/场景等关键信息并精炼场景（`refinedScenario`），作为后续节点与 SKILL 生成输入；不可用/失败降级启发式；`currentScenario` 改为优先取 `refinedScenario`。节点计数 10 → 11（BPMN 增 `Task_AnalyzeInput`，DI 整体重排）。 |
| 2026-10-13 | 入口输入处理（改造 v5） | 用户（改造确认） | 新增 `analyze/InputAnalysis` 通用能力（**与业务无关**）；Controller 增 `/api/neinsight/analyze` 澄清接口，`/insight` 增必要信息门禁（`{scenario,identity,industry,objective}` 不齐 → 400 附 `missingFields`/`questions`）；BPMN 拓扑**不变**（`analyze-input` 复用同一能力作流程内兜底）；删除硬编码业务词（提示词示例 / `guessIdentity`/`guessIndustry` 词表 / 检索关键词模板业务词）。 |
| 2026-10-13 | 逐节点洞察方法（改造 v6） | 用户（改造确认） | 方法迁入本体 `core#InsightMethod`（7 属性/8 个体）；Worker 增 `appendMethodFromOntology`/`insightMethod`/`dataStrings` 并替换 8 处硬编码调用；SKILL 的「📚 方法论来源」按「能联网附链接、否则标权威出处」口径填写。**契约/拓扑不变**（11 节点 ⇄ 11 jobType），仅方法数据来源由 Java 改为本体。 |
| 2026-10-13 | 前端下载入口 + 下载端点（改造 v7） | 用户（改造确认） | 页面 COMPLETED 分支新增「⬇ 下载 SKILL 文件」（`<a download>` 指向下载端点）；Controller 增 `GET /api/neinsight/insight/{key}/skill`（读 `insightSkillPath`，缺失/非法时按 `neinsight.skill.dir/neinsight-<key>.md` 回退；仅允许命中 SKILL 目录内文件（`startsWith` 规范化基目录），防路径穿越；不存在 → 404）。**节点/契约/拓扑不变**（11 节点 ⇄ 11 jobType）。 |
| 2026-10-13 | 生成洞察报告节点 + 报告下载端点（改造 v8） | 用户（改造确认） | 汇总之后新增节点 `Task_GenerateReport`/`generate-report`（**11 → 12 节点 / jobType**）：读 SKILL → 大模型 `chat` 生成《洞察报告》→ 落盘 `report/neinsight-<instanceKey>-洞察报告.md`（配置 `neinsight.report.dir`）→ 回写 `insightReportPath`；大模型不可用/失败降级为直接落盘 SKILL 内容；Controller 增 `GET /api/neinsight/insight/{key}/report`（读 `insightReportPath`，缺失/非法按命名规则回退，仅命中报告目录内文件；不存在 → 404）；页面 COMPLETED 分支新增「⬇ 下载洞察报告」；BPMN 增 `Task_GenerateReport` + 接线/DI，`validate_structure.py` 机检 `serviceTask=12 ⇄ @JobWorker=12` OK。 |
| 2026-10-14 | 生成洞察报告·联网检索（改造 v9） | 用户（改造确认） | 采纳「**应用侧接入检索通道再喂模型**」：`generate-report` 先按 SKILL 各节点「🔎 联网检索指令」经新增模块内 `retrieval/InsightWebRetriever`（复用通用 `McpClientHandler`）逐节点联网采集外部公开数据，再连同 SKILL 交大模型加工分析成文；**翻转 v3「检索由大模型按指令执行、程序侧不接检索」口径**；检索失败退回「仅据 SKILL 与本体基线」生成、大模型失败再降级落盘 SKILL；`application.yaml` 增 `neinsight.websearch.*`，`llm.timeout-seconds` 20 → 300（根因：报告生成长任务实测 ~80~110s，原 20s 超时导致降级为 SKILL 原文）。**节点/契约/拓扑不变**（12 节点 ⇄ 12 jobType）。 |
| 2026-10-14 | 检索通道 REST 化（改造 v10） | 用户（改造确认） | 检索服务由 MCP（`Parallel web_search`）改为**外部 Web 检索 REST API**：`InsightWebRetriever` 支持 `provider=bocha`（默认，`POST https://api.bochaai.com/v1/web-search`，Bearer，解析响应 `webPages.value[]`）与 `provider=tavily`（`POST https://api.tavily.com/search`，解析 `results[]`）；`application.yaml` 的 `neinsight.websearch.*` 增 `provider`/`path`/`api-key`/`freshness`/`timeout-seconds`、移除 `endpoint`/`tool`；未配置 `api-key` 时跳过联网检索（报告退化生成）；HTTP 采用 JDK `HttpClient`（同 `LlmClient`），不再依赖 `McpClientHandler`。**节点/契约/拓扑不变**（12 节点 ⇄ 12 jobType）。 |
| 2026-10-14 | REST 通道沉淀为通用能力（改造 v11） | 用户（改造确认） | 在 `Utilities` 新增**通用 REST 客户端** `RestClientHandler`（与 `McpClientHandler` 并列，构成「REST 通道 / MCP 通道」两类通用外部调用能力；构建器 + `get/postJson/exchange`，支持默认头、Bearer 鉴权、超时、URL 拼接；非 2xx 不算异常、传输失败抛 `RestException`）；`LlmClient` 与 `InsightWebRetriever` 的 REST 调用**统一改为复用该类**（消除重复手写 JDK `HttpClient`）；`McpClientHandler.http` 增自定义请求头重载。**节点/契约/拓扑/降级口径不变**（12 节点 ⇄ 12 jobType）；回归 `Utilities`（12 项）与 `mvn -o -pl OntologyFrameworkNEInsight test`（92 项）全绿。 |

---

## 闸门⑩留痕（第 11 步前 · 是否使用数据库）

| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-12 | v1 | 用户 | **使用关系库 OBDA**：以 `neinsightdb`（MySQL）承载 ABox，经 Ontop OBDA（`ontology/database/neinsight.obda` + `neinsight.properties`）以 SPARQL 读写；`application.yaml` 增 `ontology.obda-path`/`obda-properties-path`。库侧只灌断言事实，SWRL 派生值留空供回写。 |

## 闸门⑪留痕（第 12 步前 · 如何导入/生成完整数据）

| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-12 | v1 | 用户 | **导入/生成完整数据口径**：`EnvPrepare createdb` 依据 `ontology/database/{createdb_tables.ddl,data.sql}` 重建库并灌数（ABox 由 21 扩至 44 个体，覆盖五看三定全场景）；OBDA 映射与 DDL/`data.sql` 三者对齐、0 issue；`InsightMethod` 8 个体随本体生成器产出，同源镜像至 `terminology.ttl`。 |
