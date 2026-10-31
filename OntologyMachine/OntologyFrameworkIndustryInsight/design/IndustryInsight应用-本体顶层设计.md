# 行业洞察（IndustryInsight）应用 — 本体顶层设计

> **用法**：本文件产出**第 3 步**。**闸门②**：业务人员确认本体顶层设计后方可进入第 4 步（核心类/属性/关系设计）。
> **基线**：`OntologyFrameworkExample/design/Example应用-本体设计.md`（理念 / 资产组织 / 命名空间章节）。

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkIndustryInsight / IndustryInsight（行业洞察） |
| 文档日期 | 2026-10-22 |
| 文档版本 | **v1.2（对齐流程 v1.3）** |
| 关联顶层设计 | 《IndustryInsight应用-顶层设计》 |
| 关联流程 | 《IndustryInsight应用-流程设计》（**v1.3**，闸门① + 逐节点①-1…①-15 全部通过） |
| 本体资产（规划） | `ontology/industryinsight-all.owl`、后续 `industryinsight-*.owl`、`catalog-v001.xml` |
| 推理器 | Openllet（经 OpenlletResolver） |
| 虚拟数据 | Ontop OBDA → MySQL（库名 `industryinsight`） |

> 依据（**强制**）：本文件逐条标注业务/资料出处（★铁律 10/12）；凡无公开权威依据之处一律标注 **【待考】**，不臆造、不硬凑；无法由助手确认之点登记于 §7「待业务确认项」，带入闸门②确认。**不写入任何特定行业的背景事实/数值**（本应用泛化为任意行业，行业事实由业务方随「洞察场景」提供，见《顶层设计》§3.2）。

---

## 1. 概述与设计理念

IndustryInsight 的本体是应用的**业务真相来源**：用 OWL 表达**「五看三定」战略洞察方法论的框架与判据**（如 SPAN 定位规则、SMART 目标校验、战略控制点类型判定、机会点优先级分档、洞察结论-证据链），由 Openllet 在运行时推理判定，**程序不硬编码业务规则**（★铁律 12/14）。

**核心设计理念**（与 Example 同构）：

- **本体即约束**：五看三定的分析框架与判据由 TBox 定义，程序只查询不判断。
- **TBox/ABox 分离**：术语定义（类、属性、约束、规则）与实例断言（个体）分层组织，TBox 稳定、ABox 可变。
- **数据虚拟化**：运行态洞察素材（行业趋势 / 市场客户 / 竞争 / 自身 / 机会等）留在 MySQL，经 Ontop OBDA 映射以 SPARQL 统一访问，不迁入本体文件。
- **结论驱动可追溯**：从「五看结论」到「交叉验证・冲突消解・结论应用计划」直至「洞察结论与关键判断」，洞察结论与证据链、印证/矛盾关系全部入本体，使「先五看、后依五看三定」的过程**可审计**。
- **规则驱动事件**：SWRL 规则在推理时自动推导「高优先级机会点 / 关键风险」，触发 RabbitMQ 消息外发。
- **LLM 产物可追溯**：各环节「经 MaaS 调用大模型」产生的工具结论/共识/分歧/总结，作为可追溯的**分析产物**入本体（或其承载结构），每条结论保留**出处/【待考】**（★铁律 8/9/10/26）；**本体自身不内置任何行业词表**。

> **一句话定位**：IndustryInsight 本体是「五看三定」方法论的**本体化框架**——把一套纸面战略洞察框架，变成**输入任意行业场景即可执行**、有类约束、有规则、有数据支撑、可复现、可追溯的知识底座。

---

## 2. 设计范围与边界

### 2.1 本体要表达的业务概念域（逐条，对齐**流程 v1.3**）

| # | 概念域 | 承载环节（v1.3 编号） | 说明 |
|---|---|---|---|
| 1 | **洞察场景域** | 节点 1 / 1' | 洞察场景（行业/领域、地域、时间窗、洞察目标、解析来源）、场景校验结果（通过 / 不通过 + 缺失要素）、场景澄清提示 |
| 2 | **分析工具产物域** | 节点 2/3/4/5/6 共用 | 分析工具（PESTEL / 波特五力 / … / SPAN / FAN / OGSM 等）、逐工具**工具结论**、**共识点**、**分歧点**、**总结分析**（三段式：MaaS 逐工具分析 → 求同存异 → MaaS 总结）、质量检查清单（含资源匹配度） |
| 3 | **五看洞察域** | 节点 2–5 | 行业趋势洞察、市场与客户洞察（含**决策链 DMU**）、竞争洞察、自身洞察 |
| 4 | **机会域** | 节点 6/7 | 机会点（统一格式）、机会点清单、SPAN 定位（预筛 + 正式）、机会点优先级（高/中/低） |
| 5 | **洞察结论与机会共识域** | 节点 9 + 节点 10 | 洞察结论与关键判断（3–5 核心洞察结论 + 分支 + 支撑要点/预期证据）、结论证据台账（五看逐条回填证据链）；关键洞察一致性矩阵、冲突消解、待确认判断、关键判断清单、结论应用计划、结论-策略映射 |
| 6 | **三定决策域** | 节点 11/12/13 | 战略控制点（含领先指标）、目标（SMART 五要素）、策略（含资源匹配度/路径节奏）、一页纸战略、关键洞察转化表 |
| 7 | **报告域** | 节点 14 | 洞察报告（结构化汇总，含出处/【待考】与缺失项）、So What、有效期与刷新信号、分层交付物 |
| 8 | **事件域** | 节点 8 | 关键机会/风险外发消息 |

### 2.2 不在本体范围（显式声明，不建模）

1. **特定行业背景事实/数值**：本应用泛化为任意行业，行业事实由业务方随场景提供（或其标注出处的公开资料）；未经确认的数字一律 **【待考】**，**不写入本体**（★铁律 12/14）。
2. **替代人的战略决策**：本体只做「洞察的结构化与判定辅助」，最终决策由业务方作出（★20）。
3. **真实一手数据采集 / 财务测算 / 估值模型**：不建模型（见《顶层设计》§2.2）。
4. **组织人事 / 绩效考核**：不建模。**流程外人工/运营机制**（评审与承诺、持续运营、跨职能对齐，见《流程设计》§8）不纳入本体。
5. **大模型自身 / MaaS 服务**：LLM 是**外部服务调用**（复用 `Utilities` 的 `ChatAgent`），非本体建模对象；本体只承载**其产物的结构化结果与出处**。
6. **BPMN 的编排顺序与网关**：由流程（BPMN）定义，本体不含顺序/网关（与 Example 一致）。

---

## 3. 本体资产组织（规划）

> 照 Example 形态：**第 6 步先出单文件**（TBox + 典型 ABox），**第 9 步 TBox/ABox 分离**，**第 10 步 TBox 按业务域拆分**，**第 13 步 SKOS**，**第 11 步库环境/OBDA**。

```
ontology/
├── industryinsight-all.owl               ← 第 6 步：单文件（TBox + 典型 ABox），总入口
│
├── [第 9 步：TBox/ABox 分离]
│   ├── industryinsight-tbox.owl
│   └── industryinsight-abox.owl
│
├── [第 10 步：TBox 按业务域拆分]
│   ├── industryinsight-core.owl          ← 洞察场景 + 根类
│   ├── industryinsight-analysis.owl      ← 分析工具产物（工具结论/共识/分歧/总结/质量清单）
│   ├── industryinsight-five-looks.owl    ← 五看洞察（含决策链 DMU）
│   ├── industryinsight-opportunity.owl   ← 机会点 / SPAN / 优先级
│   ├── industryinsight-conclusion.owl    ← 洞察结论与机会共识（一致性矩阵/冲突消解/结论应用计划 + 洞察结论与关键判断）
│   ├── industryinsight-strategy.owl      ← 三定：控制点/目标/策略/一页纸战略/转化表
│   ├── industryinsight-report.owl        ← 洞察报告（So What/刷新信号/分层交付）
│   └── industryinsight-rules.owl         ← SWRL 规则
│
├── industryinsight-terminology.ttl       ← 第 13 步：SKOS 术语（中英）
├── catalog-v001.xml                      ← IRI → 本地文件映射
│
├── bpmn/industryInsightProcess.bpmn       ← 第 5 步产出
└── database/                             ← 第 11 步：库环境 / OBDA
    ├── createdb_tables.ddl
    ├── data.sql
    ├── industryinsight.obda
    └── industryinsight.properties
```

> **闸门⑧/⑨ 留痕**：第 9 步执行前须确认**是否做 TBox/ABox 分离**（闸门⑧）；第 10 步前须确认**是否做 TBox 拆分及粒度**（闸门⑨）。两项决策记录于文末「闸门⑧/⑨留痕」。

---

## 4. 命名空间与 import 架构

### 4.1 命名空间（已确认：单一前缀）

> **Q-T1 口径（已确认 2026-10-22）**：采用**单一命名空间** `http://example.org/industryinsight#`；第 10 步按业务域拆分时，各子本体**共用同一命名空间**，仅以**文件粒度**组织（不采用按模块命名空间）。

| 文件（规划） | 本体 IRI | 角色 |
|---|---|---|
| core | `http://example.org/industryinsight#` | TBox：洞察场景 + 根类 |
| analysis | `http://example.org/industryinsight#` | TBox：分析工具产物 |
| five-looks | `http://example.org/industryinsight#` | TBox：五看洞察 |
| opportunity | `http://example.org/industryinsight#` | TBox：机会点/SPAN/优先级 |
| conclusion | `http://example.org/industryinsight#` | TBox：洞察结论与机会共识域 |
| strategy | `http://example.org/industryinsight#` | TBox：三定 |
| report | `http://example.org/industryinsight#` | TBox：洞察报告 |
| rules | `http://example.org/industryinsight#` | TBox：SWRL 规则 |
| terminology（SKOS） | `http://example.org/industryinsight/term#` | SKOS 术语（第 13 步） |
| abox | `http://example.org/industryinsight#` | ABox：典型/运行态个体 |
| all | `http://example.org/industryinsight#` | 总入口 |

> 各子本体共用同一 IRI 前缀，仅**文件名**区分业务域；`catalog-v001.xml` 负责 IRI → 本地文件映射。

### 4.2 import 拓扑（规划）

```
industryinsight-all (总入口)
  ├── imports core
  ├── imports analysis
  ├── imports five-looks
  ├── imports opportunity
  ├── imports conclusion
  ├── imports strategy
  ├── imports report
  ├── imports rules
  ├── imports terminology
  └── imports abox                 （第 6 步单文件内含典型 ABox；第 9 步分离后按「推理需要即加载」决定是否保留）
```

> 运行加载入口为 `ontology/industryinsight-all.owl`（`application.yaml` 的 `ontology.main-path`）。
> TBox/ABox 分离（第 9 步）后，运行态数据由 OBDA(MySQL) 提供，`industryinsight.obda` 映射至同一 IRI 命名空间。

---

## 5. 顶层类层级框架（主干）

> 本步只勾出**主干框架**（根类与主要分支），明细类/属性/约束留待第 4 步。节点编号为**流程 v1.3**。

```
IndustryInsightConcept （本体根类）
│
├── ScenarioDomain                      【洞察场景域】节点 1/1'
│   ├── InsightScenario                 （洞察场景：行业/领域、地域、时间窗、洞察目标、解析来源）
│   ├── ScenarioValidation              （场景校验结果：通过/不通过 + 缺失要素）
│   └── ScenarioClarification           （场景澄清提示：缺失项 + 提示文本；条件性节点 1'）
│
├── AnalysisDomain                      【分析工具产物域】节点 2/3/4/5/6 共用（五看 + 看机会）
│   ├── AnalysisTool                    （分析工具：PESTEL / 波特五力 / 价值链利润池 / 技术成熟度曲线 /
│   │                                    颠覆性变量 / STP / $APPEALS / JTBD / KANO / 客户旅程 /
│   │                                    竞争矩阵 / 蓝军推演 / 对手战略推演 / 资源-能力 / BMC / VRIO / 差距分析 /
│   │                                    机会地图 / Ansoff / 机会-能力匹配 / SPAN / FAN 等）
│   ├── ToolFinding                     （单工具结论：工具 + 结论 + 出处/【待考】）
│   ├── ConsensusPoint                  （共识点：多工具共同指向）
│   ├── DivergencePoint                 （分歧点：结论不一致处，各自保留）
│   ├── AnalysisSummary                 （总结分析：经 MaaS 对工具结果与共识/分歧的总结）
│   └── QualityChecklist                （质量检查清单：清单项 + 自检结果 + 资源匹配度）
│
├── FiveLooksDomain                     【五看洞察域】节点 2–5
│   ├── IndustryTrendInsight            （看行业/趋势：PESTEL/波特五力/价值链利润池/技术成熟度曲线/颠覆性变量 结论）
│   ├── MarketCustomerInsight           （看市场/客户：STP/$APPEALS/JTBD/KANO/客户旅程 结论）
│   │   └── DecisionMakingUnit          （决策链 DMU 角色：采购决策者/影响者/使用者及其诉求差异）
│   ├── CompetitionInsight              （看竞争：竞争矩阵/蓝军推演/对手战略推演/生态位博弈 结论）
│   └── SelfInsight                     （看自己：资源-能力/BMC/VRIO/差距分析 结论）
│
├── OpportunityDomain                   【机会域】节点 6/7
│   ├── Opportunity                     （机会点，统一格式：名称/目标客群场景/客户痛点证据/市场规模估算/竞争空隙/所需核心能力）
│   ├── OpportunityList                 （机会点清单）
│   ├── SpanPosition                    （SPAN 定位：市场吸引力 × 竞争实力 → 象限）
│   └── OpportunityPriority             （机会点优先级：高/中/低）
│
├── ConclusionDomain                    【洞察结论与机会共识域】节点 9 + 节点 10
│   ├── InsightConsistencyMatrix        （关键洞察一致性矩阵：相互印证 / 相互矛盾）节点 9
│   ├── ConflictResolution              （冲突消解：矛盾点 → 消解结论或并存说明）节点 9
│   ├── PendingJudgment                 （待确认判断：不能当场消解的矛盾点）节点 9
│   ├── KeyJudgment                     （关键判断：内容 + 依赖洞察 + 不确定性等级 + 若不成立影响）节点 9
│   ├── ActionPlan                      （结论应用计划：验证问题 + 数据需求 + 验证途径 + 责任角色 + 时间点）节点 9
│   ├── ConclusionStrategyMapping       （结论-策略映射）节点 9
│   ├── InsightConclusionSet            （洞察结论与关键判断：3–5 核心洞察结论，行业/市场/竞争/自身/机会分支）节点 10
│   ├── InsightConclusion               （洞察结论：内容 + 分支 + 支撑要点 + 证据来源）节点 10
│   └── ConclusionEvidenceLog           （结论证据台账：五看各环节逐条回填的证据链）节点 10
│
├── StrategyDomain                      【三定决策域】节点 11/12/13
│   ├── StrategicControlPoint           （战略控制点：类型 + 依据 + 对应机会点 + 可持续性 + 领先指标）
│   ├── Objective                       （目标：SMART 五要素 + 关联控制点 + BSC 维度）
│   ├── Strategy                        （策略：举措 + 里程碑 + 风险应对 + 支撑目标 + 路径节奏 + 资源匹配度）
│   ├── OnePageStrategy                 （一页纸战略：机会/控制点/目标/策略收敛）
│   └── KeyInsightConversionTable       （关键洞察转化表：洞察-策略映射，每条策略 ≥1 条核心洞察依据）
│
├── ReportDomain                        【报告域】节点 14
│   ├── InsightReport                   （洞察报告：五看 + 交叉验证/洞察结论 + 三定 + 机会点优先级 + 出处/【待考】 + 缺失项）
│   ├── SoWhat                          （So What：立即做 / 停止做 / 继续观察）
│   ├── RefreshSignal                   （有效期与刷新信号：判断被推翻 / 重大政策 / 竞品颠覆性动作等）
│   └── LayeredDeliverable              （分层交付物：高管摘要 / 业务机会与行动指南 / 产品需求与技术趋势白皮书）
│
└── EventDomain                         【事件域】节点 8
    └── OutboundInsightMessage          （关键机会/风险外发消息：描述 + 优先级 + 关联洞察）
```

> **关系主干（规划，明细留待第 4 步）**：`InsightScenario` 为全流程锚点；`InsightConclusionSet`/`InsightConclusion` 经 `derivedFrom` 关联到 `InsightScenario`，五看各洞察经 `validates`/`evidences` 回填 `ConclusionEvidenceLog`；各洞察（五看）经 `derivedFrom` 关联到 `InsightScenario`；`Opportunity` 经 `basedOn` 关联五看洞察；`InsightConsistencyMatrix`/`ConflictResolution`/`KeyJudgment`/`ActionPlan` 关联五看洞察与 `Opportunity`；`StrategicControlPoint` / `Objective` / `Strategy` 沿三定链路关联；`InsightReport` 经 `includes*` 聚合全部结论，并聚合 `SoWhat`/`RefreshSignal`/`LayeredDeliverable`；`OutboundInsightMessage` 关联高优先级 `Opportunity`。

---

## 6. TBox/ABox 分层策略

| 层 | 内容 | 落点 |
|---|---|---|
| **TBox** | 类层级、对象/数据属性、约束（`owl:Restriction`）、SWRL 规则 | 第 6 步单文件，第 9/10 步拆分 |
| **ABox** | 个体断言（洞察场景实例、洞察结论集/结论证据台账实例、各洞察实例、机会点实例、控制点/目标/策略实例等） | 第 6 步含**典型**数据，第 9 步独立成文件，第 11/12 步入库/扩全集 |

> **典型 ABox**（闸门⑦确认）：第 6 步将给出**足以驱动示例/测试**的代表性个体——覆盖「一个洞察场景 → 五看结论 → 机会点与优先级 → 五看结论交叉验证与机会共识 → 洞察结论与关键判断 → 三定 → 报告」的最小闭环（具体个体集于第 6 步交业务确认）。
> **运行态数据**：真实洞察素材经 Ontop OBDA 从 MySQL 读写，与静态 ABox 双轨（与 Example §6 同构）。
> **第 9 步决策（闸门⑧，2026-10-22）**：**不做 TBox/ABox 分离**——本应用 ABox 规模小（80 个体），`industryinsight-all.owl` 继续作为**单文件总入口**承载 TBox + 典型 ABox（第 9 步产出为空并留痕）。第 10 步 TBox 拆分据此评估；第 11 步入库时**从单文件抽取** ABox。详见文末「闸门⑧留痕」。
> **第 10 步决策（闸门⑨，2026-10-22）**：**不做 TBox 拆分**——TBox 仅 49 类、规模小，保持单文件 `industryinsight-all.owl` 承载全部 TBox（第 10 步产出为空并留痕）。详见文末「闸门⑨留痕」。

---

## 7. 待业务确认项

> 承接《顶层设计》§5、《流程设计》§7。以下为**第 3 步（本体顶层设计）**需业务裁定的点，**未经确认不进入第 4 步**。

| # | 问题 | 助手建议 | 业务结论 |
|---|---|---|---|
| Q-T1 | **命名空间方案** | **单一前缀** `http://example.org/industryinsight#`，各子本体共用命名空间、仅以文件粒度组织 | ✅ 已确认（2026-10-22） |
| Q-T2 | **顶层根类与主干分支划分** | 根类 `IndustryInsightConcept`；**8 大主干域**（场景/分析产物/五看/机会/洞察结论与机会共识/三定/报告/事件） | ✅ 已确认（2026-10-22） |
| Q-T3 | **分析工具产物是否入本体** | 是——`ToolFinding`/`ConsensusPoint`/`DivergencePoint`/`AnalysisSummary` 建模，使「求同存异」可追溯 | ✅ 已确认（2026-10-22） |
| Q-T4 | **战略控制点类型集合本体化** | 沿用《流程设计》Q5 并扩展候选类型（技术专利/成本优势/客户黏性·转换成本/品牌/渠道壁垒/标准话语权），作为枚举/子类 | ✅ 已确认（2026-10-22） |
| Q-T5 | **机会点优先级档位本体化** | 高/中/低三档（与《流程设计》一致），由 SPAN 象限映射 | ✅ 已确认（2026-10-22） |
| Q-T6 | **五看三定「分析工具」是否本体化为工具类** | 是——建模为 `AnalysisTool` 枚举/子类（PESTEL / 波特五力 / … / SPAN / FAN / OGSM 等），供 `ToolFinding` 引用 | ✅ 已确认（2026-10-22） |
| Q-T7 | **SWRL 规则范围** | 覆盖「高优先级机会点 / 关键风险」推导（供节点 8 外发）；具体规则明细留第 4 步 | ✅ 已确认（2026-10-22） |
| Q-T8 | **洞察结论与机会共识域本体化口径**（新增，对齐 v1.2） | 建模 `InsightConclusionSet`/`InsightConclusion`/`ConclusionEvidenceLog`/`InsightConsistencyMatrix`/`ConflictResolution`/`PendingJudgment`/`KeyJudgment`/`ActionPlan`/`ConclusionStrategyMapping`，使「先五看看结论→交叉验证消解→三定」全程可追溯 | ✅ 已确认（2026-10-22） |
| Q-T9 | **辅助概念本体化口径**（新增，对齐 v1.2） | 决策链 DMU（`DecisionMakingUnit`，属市场客户域）、质量检查清单（`QualityChecklist`，属分析产物域）、资源匹配度（`Strategy` 属性）、数据源分级/置信度（横切**数据属性**，非独立类） | ✅ 已确认（2026-10-22） |
| Q-T10 | **报告增强本体化口径**（新增，对齐 v1.2） | `SoWhat` / `RefreshSignal` / `LayeredDeliverable` 建模为 `ReportDomain` 子类，供报告聚合 | ✅ 已确认（2026-10-22） |

---

## 闸门②留痕

| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-22 | v1.1 | 业务方（用户） | **通过**：8 大主干域 + 根类 `IndustryInsightConcept` 确认；Q-T2…Q-T10 **全部采纳助手建议**；Q-T1 单一前缀。**进入第 4 步（本体核心类/属性/关系设计，闸门③）**。 |

---

## 闸门⑧留痕（是否 TBox/ABox 分离 · 第 9 步执行前）

| 日期 | 是否分离 | 依据（个体规模/是否参与推理） | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-22 | **☑ 不分离** | 当前 ABox 仅 **80 个个体**（<200）、单文件共 905 公理 / 49 类；典型 ABox 参与一致性/推理校验，规模小 | 业务方（用户） | **不分离**：`industryinsight-all.owl` 继续作为**单文件总入口**承载 TBox + 典型 ABox。第 9 步产出为空并留痕（闸门⑧判定不分离）；第 10 步 TBox 拆分随之评估；第 11 步入库时改**从单文件抽取** ABox。 |

## 闸门⑨留痕（是否 TBox 拆分 · 第 10 步前）

| 日期 | 是否拆分 | 拆分粒度（业务域划分） | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-22 | **☑ 不拆分** | —（不适用） | 业务方（用户） | **不拆分**：TBox 仅 49 类、规模小，且已裁定不分离 ABox，故保持单文件 `industryinsight-all.owl` 承载全部 TBox。第 10 步产出为空并留痕。 |

---

## 变更记录

| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-10-22 | 第 3 步初稿：本体顶层设计（理念/范围边界/资产组织/命名空间/顶层类框架/分层策略 + §7 待确认项） | — |
| 2026-10-22 | **v1.1（对齐流程 v1.2）**：概念域由 7 域扩为 **8 域**（新增「洞察结论与机会共识域」）；顶层类框架同步 15 环节（新增 `ScenarioClarification`/`InsightConclusionSet`/`InsightConclusion`/`ConclusionEvidenceLog`/`InsightConsistencyMatrix`/`ConflictResolution`/`PendingJudgment`/`KeyJudgment`/`ActionPlan`/`ConclusionStrategyMapping`/`QualityChecklist`/`DecisionMakingUnit`/`KeyInsightConversionTable`/`SoWhat`/`RefreshSignal`/`LayeredDeliverable` 等）；§3 拆分列表加 `industryinsight-conclusion.owl`；§4 命名空间定**单一前缀**；§7 加 Q-T8/Q-T9/Q-T10 | — |
| 2026-10-22 | **第 3 步 v1.1 定稿**：闸门② 通过；Q-T2…Q-T10 全部采纳助手建议。进入第 4 步 | 业务方（用户） |
| 2026-10-22 | **第 9 步执行前闸门⑧**：业务方裁定 **不分离**（ABox 80 个体，规模小）；第 9 步产出为空并留痕；`industryinsight-all.owl` 保持单文件；§6 加决策说明 | 业务方（用户） |
| 2026-10-22 | **第 10 步执行前闸门⑨**：业务方裁定 **不拆分 TBox**（49 类，规模小）；第 10 步产出为空并留痕；§6 加决策说明 | 业务方（用户） |
| 2026-10-31 | **v1.2（对齐流程 v1.3，业务反馈③）**：按「**先五看、后依五看三定**」调整——概念域顺序改为 场景 → 分析产物 → 五看 → 机会 → **洞察结论与机会共识** → 三定 → 报告 → 事件；顶层类框架将 `ConclusionDomain` 由场景域之后**后移至机会域之后**（节点 9 + 节点 10）；全篇节点编号与 `流程 v1.2` 对齐更新为 `流程 v1.3` | 业务方（用户） |
