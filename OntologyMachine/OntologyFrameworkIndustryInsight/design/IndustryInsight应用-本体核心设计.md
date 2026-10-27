# 行业洞察（IndustryInsight）应用 — 本体核心设计（核心类、属性与关系）

> **用法**：本文件产出**第 4 步**。**闸门③**：业务人员确认核心类/属性/关系后方可生成第 6 步本体文件。本文件并承载第 6 步**闸门⑦**（典型 ABox 数据确认）的留痕（见 §7 与文末「闸门⑦留痕」）。
> **基线**：`OntologyFrameworkExample/design/Example应用-本体设计.md`（TBox / SWRL 章节）。
> **上游**：《IndustryInsight应用-本体顶层设计》（v1.1 定稿，闸门② 通过）。

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkIndustryInsight / IndustryInsight（行业洞察） |
| 文档日期 | 2026-10-22 |
| 文档版本 | **v1.0** |
| 上游 | 《IndustryInsight应用-本体顶层设计》（v1.1 定稿） |
| 关联流程 | 《IndustryInsight应用-流程设计》（v1.2 定稿） |
| 命名空间 | 单一前缀 `http://example.org/industryinsight#`（前缀 `ii:`） |
| 产出本体（规划） | `ontology/industryinsight-all.owl`（单文件，含典型 ABox） |

> **依据（强制）**：本文件逐条标注业务/资料出处（★铁律 10/12）；无公开权威依据处标 **【待考】**，不臆造、不硬凑。**不写入任何特定行业的背景事实/数值**——凡行业事实一律由业务方随「洞察场景」提供，本体只承载**框架与判据**（★12/14）。
> **硬性**：约束必须写成 `owl:Restriction`（★12）；属性「值/约束」不混用；数值属性用数值类型（`xsd:decimal`/`xsd:integer`）。

---

## 1. 核心类体系（明细）

> 前缀 `ii:` = `http://example.org/industryinsight#`。节点编号为**流程 v1.2**。括号内为**约束/说明**。

```
ii:IndustryInsightConcept                    （本体根类）
│
├── ii:ScenarioDomain                         【洞察场景域】节点 1/1'
│   ├── ii:InsightScenario                    （洞察场景：行业/领域、地域、时间窗、洞察目标、解析来源；exactly 1 scenarioText、exactly 1 parseSource）
│   ├── ii:ScenarioValidation                 （场景校验结果：通过/不通过 + 缺失要素）
│   └── ii:ScenarioClarification              （场景澄清提示：缺失项 + 提示文本；条件性节点 1'）
│
├── ii:HypothesisDomain                       【假设与验证域】节点 2 + 节点 10
│   ├── ii:InitialHypothesisTree              （初始假设树；hasHypothesis min 3, max 5）
│   ├── ii:Hypothesis                         （假设：内容 + 分支 + 待验证点 + 预期证据来源）
│   ├── ii:HypothesisValidationLog            （假设验证日志：逐条回填证据链）
│   ├── ii:InsightConsistencyMatrix           （关键洞察一致性矩阵：印证/矛盾）
│   ├── ii:ConflictResolution                 （冲突消解：矛盾点 → 消解结论/并存说明）
│   ├── ii:PendingHypothesis                  （待验证假设：不能当场消解的矛盾点）
│   ├── ii:KeyAssumption                      （关键假设：内容 + 依赖洞察 + 不确定性等级 + 若被证伪影响）
│   ├── ii:ValidationPlan                     （验证计划：验证问题 + 数据需求 + 途径 + 责任角色 + 时间点）
│   └── ii:AssumptionInsightMapping           （假设-洞察映射）
│
├── ii:AnalysisDomain                         【分析工具产物域】节点 3/4/5/6/7 共用
│   ├── ii:AnalysisTool                       （分析工具；受控个体，见 §1.2）
│   ├── ii:ToolFinding                        （单工具结论：exactly 1 referencesTool + 出处/【待考】）
│   ├── ii:ConsensusPoint                     （共识点）
│   ├── ii:DivergencePoint                    （分歧点）
│   ├── ii:AnalysisSummary                    （总结分析）
│   └── ii:QualityChecklist                   （质量检查清单：清单项 + 自检结果 + 资源匹配度）
│
├── ii:FiveLooksInsight                       （五看洞察根类，便于统一关系）
│   ├── ii:IndustryTrendInsight               （看行业/趋势）节点 3
│   ├── ii:MarketCustomerInsight              （看市场/客户）节点 4
│   │   └── ii:DecisionMakingUnit             （决策链 DMU 角色：采购决策者/影响者/使用者）
│   ├── ii:CompetitionInsight                 （看竞争）节点 5
│   └── ii:SelfInsight                        （看自己）节点 6
│
├── ii:OpportunityDomain                      【机会域】节点 7/8
│   ├── ii:Opportunity                        （机会点，统一格式；exactly 1 hasPriority）
│   ├── ii:OpportunityList                    （机会点清单；hasOpportunity min 1）
│   ├── ii:SpanPosition                       （SPAN 定位：市场吸引力 × 竞争实力 → 象限）
│   └── ii:OpportunityPriority                （机会点优先级：高/中/低）
│
├── ii:StrategyDomain                         【三定决策域】节点 11/12/13
│   ├── ii:StrategicControlPoint              （战略控制点；exactly 1 hasControlPointType）
│   ├── ii:Objective                          （目标：SMART 五要素 + 关联控制点 + BSC 维度）
│   ├── ii:Strategy                           （策略：举措 + 里程碑 + 风险应对 + 支撑目标 + 路径节奏 + 资源匹配度）
│   ├── ii:OnePageStrategy                    （一页纸战略）
│   └── ii:KeyInsightConversionTable          （关键洞察转化表：洞察-策略映射）
│
├── ii:ReportDomain                           【报告域】节点 14
│   ├── ii:InsightReport                      （洞察报告；hasSoWhat exactly 1）
│   ├── ii:SoWhat                             （So What：立即做/停止做/继续观察）
│   ├── ii:RefreshSignal                      （有效期与刷新信号）
│   └── ii:LayeredDeliverable                 （分层交付物）
│
└── ii:EventDomain                            【事件域】节点 9
    └── ii:OutboundInsightMessage             （关键机会/风险外发消息）

【推理派生类（SWRL 推导，非人工断言）】
├── ii:HighPriorityOpportunity                （由 hasPriority=高 推导）
└── ii:KeyRisk                                （由 riskLevel=高 或 触发条件推导）
```

### 1.1 受控词表（枚举以子类/受控个体表达）

| 词表 | 表达 | 取值 | 出处 |
|---|---|---|---|
| **机会点优先级** | `ii:OpportunityPriority` 受控个体 | 高 / 中 / 低 | 《流程设计》Q6 |
| **战略控制点类型** | `ii:ControlPointType` 受控个体 | 技术专利 / 成本优势 / 客户黏性·转换成本 / 品牌 / 渠道壁垒 / 标准话语权（候选，以业务口径为准）【待考】 | 《流程设计》Q5 |
| **数据来源等级** | `ii:SourceLevel` 受控个体 | 一手访谈 / 权威第三方报告 / 公开新闻 / 内部经验 | 《流程设计》Q25 |
| **置信度** | `ii:ConfidenceLevel` 受控个体 | 高 / 中 / 低 | 《流程设计》Q25 |
| **解析来源** | `ii:ParseSource` 受控个体 | LLM / Fallback（应用兜底） | 《流程设计》Q13 |
| **假设分支** | `ii:HypothesisBranch` 受控个体 | 行业 / 市场 / 竞争 / 自身 / 机会 | 《流程设计》Q29 |
| **BSC 维度** | `ii:BscDimension` 受控个体 | 财务 / 客户 / 内部流程 / 学习与成长 | 《流程设计》Q21 |
| **路径节奏** | `ii:PathPhase` 受控个体 | 短期速赢 / 中期攻坚 / 长期布局 | 《流程设计》Q22 |
| **交付层级** | `ii:DeliverableLevel` 受控个体 | 高管战略洞察摘要 / 业务机会与行动指南 / 产品需求与技术趋势白皮书 | 《流程设计》Q27 |

### 1.2 分析工具清单（`ii:AnalysisTool` 受控个体，供 `ToolFinding` 引用）

| 归属节点 | 工具个体 |
|---|---|
| 节点 3 看行业/趋势 | PESTEL、PorterFiveForces、ValueChainProfitPool、TechHypeCycle、DisruptiveVariable |
| 节点 4 看市场/客户 | STP、APPEALS、JTBD、KANO、CustomerJourney |
| 节点 5 看竞争 | CompetitiveMatrix、RedBlueTeamWargame、CompetitorStrategicAssumption、EcoNicheGame |
| 节点 6 看自己 | ResourceCapabilityRadar、BusinessModelCanvas、VRIO、GapAnalysis |
| 节点 7 看机会 | OpportunityMap、AnsoffMatrix、OpportunityCapabilityFit、SPAN、FAN |
| 节点 8 SPAN 定位 | SPAN（正式） |
| 节点 12/13（辅助） | SMART、OGSM、BSC、4P、StrategyDecoding |

> 工具个体命名采用英文标识（IRI 稳定）；中文标签由第 13 步 SKOS 提供（§8）。

---

## 2. 对象属性

| 属性 | 特性（函数/传递/…） | `owl:inverseOf` | 定义域 | 值域 | 出处 |
|---|---|---|---|---|---|
| `ii:derivedFrom` | — | `ii:derivedInsight` | `ii:FiveLooksInsight` ∪ `ii:Opportunity` ∪ `ii:InsightReport` | `ii:InsightScenario` | 流程 v1.2 全流程锚点 |
| `ii:belongsToScenario` | Functional | — | `ii:IndustryInsightConcept` | `ii:InsightScenario` | 同上 |
| `ii:hasHypothesis` | — | `ii:hypothesisOf` | `ii:InitialHypothesisTree` | `ii:Hypothesis` | 《流程设计》Q29 |
| `ii:hasValidationLog` | Functional | — | `ii:InitialHypothesisTree` | `ii:HypothesisValidationLog` | 《流程设计》Q29 |
| `ii:evidencedBy` | — | `ii:evidenceFor` | `ii:Hypothesis` | `ii:ToolFinding` ∪ `ii:FiveLooksInsight` | 流程 v1.2 节点 3–6 回填 |
| `ii:basedOn` | — | — | `ii:Opportunity` | `ii:FiveLooksInsight` | 《流程设计》Q18（★10 可追溯） |
| `ii:hasToolFinding` | — | `ii:findingOf` | `ii:AnalysisDomain` ∪ `ii:FiveLooksInsight` | `ii:ToolFinding` | 流程 v1.2 三段式 |
| `ii:hasConsensusPoint` | — | `ii:consensusOf` | `ii:AnalysisDomain` ∪ `ii:FiveLooksInsight` | `ii:ConsensusPoint` | 流程 v1.2 求同存异 |
| `ii:hasDivergencePoint` | — | `ii:divergenceOf` | `ii:AnalysisDomain` ∪ `ii:FiveLooksInsight` | `ii:DivergencePoint` | 同上 |
| `ii:hasAnalysisSummary` | Functional | `ii:summaryOf` | `ii:AnalysisDomain` ∪ `ii:FiveLooksInsight` | `ii:AnalysisSummary` | 流程 v1.2 MaaS 总结 |
| `ii:referencesTool` | Functional | — | `ii:ToolFinding` | `ii:AnalysisTool` | §1.2 |
| `ii:hasQualityChecklist` | Functional | — | `ii:FiveLooksInsight` | `ii:QualityChecklist` | 《流程设计》Q26 |
| `ii:hasDmu` | — | — | `ii:MarketCustomerInsight` | `ii:DecisionMakingUnit` | 《流程设计》Q15 |
| `ii:hasOpportunity` | — | `ii:opportunityOf` | `ii:OpportunityList` | `ii:Opportunity` | 《流程设计》Q18 |
| `ii:hasSpanPosition` | Functional | — | `ii:Opportunity` | `ii:SpanPosition` | 《流程设计》Q19 |
| `ii:hasPriority` | Functional | — | `ii:Opportunity` | `ii:OpportunityPriority` | 《流程设计》Q6 |
| `ii:hasControlPoint` | — | `ii:controlPointOf` | `ii:StrategyDomain` ∪ `ii:Opportunity` | `ii:StrategicControlPoint` | 《流程设计》Q20 |
| `ii:hasControlPointType` | Functional | — | `ii:StrategicControlPoint` | `ii:ControlPointType` | 《流程设计》Q5 |
| `ii:hasObjective` | — | `ii:objectiveOf` | `ii:StrategicControlPoint` | `ii:Objective` | 《流程设计》Q21 |
| `ii:hasStrategy` | — | `ii:strategyOf` | `ii:Objective` | `ii:Strategy` | 《流程设计》Q22 |
| `ii:mapsToStrategy` | — | — | `ii:KeyInsightConversionTable` | `ii:Strategy` | 《流程设计》Q28（策略≥1洞察） |
| `ii:mapsFromInsight` | — | — | `ii:KeyInsightConversionTable` | `ii:FiveLooksInsight` | 同上 |
| `ii:includesInsight` | — | `ii:includedInReport` | `ii:InsightReport` | `ii:FiveLooksInsight` ∪ `ii:Opportunity` ∪ `ii:StrategicControlPoint` ∪ `ii:Objective` ∪ `ii:Strategy` | 《流程设计》Q23 |
| `ii:hasSoWhat` | Functional | — | `ii:InsightReport` | `ii:SoWhat` | 《流程设计》Q27 |
| `ii:hasRefreshSignal` | — | — | `ii:InsightReport` | `ii:RefreshSignal` | 《流程设计》Q27 |
| `ii:hasLayeredDeliverable` | — | — | `ii:InsightReport` | `ii:LayeredDeliverable` | 《流程设计》Q27 |
| `ii:triggeredBy` | — | — | `ii:OutboundInsightMessage` | `ii:HighPriorityOpportunity` ∪ `ii:KeyRisk` | 《流程设计》Q7/Q8 |
| `ii:relatesToInsight` | — | — | `ii:OutboundInsightMessage` | `ii:FiveLooksInsight` | 《流程设计》Q8 |
| `ii:resolves` | — | — | `ii:ConflictResolution` | `ii:DivergencePoint` | 《流程设计》Q24 |
| `ii:marksPending` | — | — | `ii:InsightConsistencyMatrix` | `ii:PendingHypothesis` | 《流程设计》Q24 |
| `ii:mapsAssumption` | — | — | `ii:AssumptionInsightMapping` | `ii:KeyAssumption` | 《流程设计》Q24 |
| `ii:plansValidationOf` | — | — | `ii:ValidationPlan` | `ii:KeyAssumption` | 《流程设计》Q24 |

---

## 3. 数据属性

> 类型：数值用 `xsd:decimal`/`xsd:integer`；文本 `xsd:string`；日期 `xsd:date`。每条标出处；无出处标【待考】。

| 属性 | 类型 | 定义域 | 出处 |
|---|---|---|---|
| `ii:scenarioText` | `xsd:string` | `ii:InsightScenario` | 《流程设计》Q4 |
| `ii:industryDomain` | `xsd:string` | `ii:InsightScenario` | 《流程设计》Q4 |
| `ii:region` | `xsd:string` | `ii:InsightScenario` | 《流程设计》Q4 |
| `ii:timeWindow` | `xsd:string` | `ii:InsightScenario` | 《流程设计》Q4 |
| `ii:insightGoal` | `xsd:string` | `ii:InsightScenario` | 《流程设计》Q4 |
| `ii:parseSource` | `xsd:string`（LLM/Fallback） | `ii:InsightScenario` | 《流程设计》Q13 |
| `ii:validationResult` | `xsd:string`（通过/不通过） | `ii:ScenarioValidation` | 《流程设计》节点 1 |
| `ii:missingElement` | `xsd:string` | `ii:ScenarioValidation` | 同上 |
| `ii:clarificationText` | `xsd:string` | `ii:ScenarioClarification` | 《流程设计》节点 1' |
| `ii:hypothesisStatement` | `xsd:string` | `ii:Hypothesis` | 《流程设计》Q29 |
| `ii:hypothesisBranch` | `xsd:string`（受控） | `ii:Hypothesis` | 《流程设计》Q29 |
| `ii:pendingVerificationPoint` | `xsd:string` | `ii:Hypothesis` | 《流程设计》Q29 |
| `ii:expectedEvidenceSource` | `xsd:string` | `ii:Hypothesis` | 《流程设计》Q29 |
| `ii:uncertaintyLevel` | `xsd:string`（高/中/低） | `ii:KeyAssumption` | 《流程设计》Q24 |
| `ii:falsificationImpact` | `xsd:string` | `ii:KeyAssumption` | 《流程设计》Q24 |
| `ii:validationApproach` | `xsd:string` | `ii:ValidationPlan` | 《流程设计》Q24 |
| `ii:validationRole` | `xsd:string`（外部） | `ii:ValidationPlan` | 《流程设计》Q24 |
| `ii:validationTimepoint` | `xsd:string` | `ii:ValidationPlan` | 《流程设计》Q24 |
| `ii:findingConclusion` | `xsd:string` | `ii:ToolFinding` | 流程 v1.2 三段式 |
| `ii:citation` | `xsd:string`（出处 / 【待考】） | `ii:ToolFinding` ∪ `ii:FiveLooksInsight` ∪ `ii:Opportunity` ∪ `ii:StrategicControlPoint` ∪ `ii:Objective` ∪ `ii:Strategy` | ★8/9/10 |
| `ii:sourceLevel` | `xsd:string`（受控） | （横切，上述结论类） | 《流程设计》Q25 |
| `ii:confidence` | `xsd:string`（受控） | （横切，上述结论类） | 《流程设计》Q25 |
| `ii:opportunityName` | `xsd:string` | `ii:Opportunity` | 《流程设计》Q18 |
| `ii:targetCustomerScene` | `xsd:string` | `ii:Opportunity` | 《流程设计》Q18 |
| `ii:painpointEvidence` | `xsd:string` | `ii:Opportunity` | 《流程设计》Q18 |
| `ii:marketSizeEstimate` | `xsd:decimal` / `xsd:string` | `ii:Opportunity` | 《流程设计》Q18【待考】 |
| `ii:competitiveGap` | `xsd:string` | `ii:Opportunity` | 《流程设计》Q18 |
| `ii:requiredCapability` | `xsd:string` | `ii:Opportunity` | 《流程设计》Q18 |
| `ii:priorityValue` | `xsd:string`（高/中/低） | `ii:OpportunityPriority` | 《流程设计》Q6 |
| `ii:spanAttractiveness` | `xsd:decimal` | `ii:SpanPosition` | 《流程设计》Q6【待考：量纲】 |
| `ii:spanCompetitiveness` | `xsd:decimal` | `ii:SpanPosition` | 《流程设计》Q6【待考：量纲】 |
| `ii:spanQuadrant` | `xsd:string` | `ii:SpanPosition` | 《流程设计》Q6 |
| `ii:controlPointRationale` | `xsd:string` | `ii:StrategicControlPoint` | 《流程设计》Q20 |
| `ii:sustainability` | `xsd:string` | `ii:StrategicControlPoint` | 《流程设计》Q20 |
| `ii:leadingIndicator` | `xsd:string` | `ii:StrategicControlPoint` | 《流程设计》Q20 |
| `ii:objectiveExpression` | `xsd:string` | `ii:Objective` | 《流程设计》Q21 |
| `ii:smartSpecific..TimeBound` | `xsd:boolean` | `ii:Objective` | 《流程设计》Q7 |
| `ii:metric` | `xsd:string` | `ii:Objective` | 《流程设计》Q7 |
| `ii:timeLimit` | `xsd:string` | `ii:Objective` | 《流程设计》Q7 |
| `ii:bscDimension` | `xsd:string`（受控） | `ii:Objective` | 《流程设计》Q21 |
| `ii:strategyAction` | `xsd:string` | `ii:Strategy` | 《流程设计》Q22 |
| `ii:resourceInput` | `xsd:string` | `ii:Strategy` | 《流程设计》Q22 |
| `ii:resourceFit` | `xsd:string` | `ii:Strategy` | 《流程设计》Q22 |
| `ii:pathPhase` | `xsd:string`（受控） | `ii:Strategy` | 《流程设计》Q22 |
| `ii:riskResponse` | `xsd:string` | `ii:Strategy` | 《流程设计》Q22 |
| `ii:milestone` | `xsd:string` | `ii:Strategy` | 《流程设计》Q22 |
| `ii:soWhatAction` | `xsd:string`（立即做/停止做/继续观察） | `ii:SoWhat` | 《流程设计》Q27 |
| `ii:reportValidity` | `xsd:string` | `ii:RefreshSignal` | 《流程设计》Q27 |
| `ii:refreshTrigger` | `xsd:string` | `ii:RefreshSignal` | 《流程设计》Q27 |
| `ii:deliverableLevel` | `xsd:string`（受控） | `ii:LayeredDeliverable` | 《流程设计》Q27 |
| `ii:insightSkillPath` | `xsd:string` | `ii:InsightReport` | 《流程设计》Q23 |
| `ii:messagePayload` | `xsd:string`（JSON） | `ii:OutboundInsightMessage` | 《流程设计》节点 9 |
| `ii:riskLevel` | `xsd:string`（高/中/低） | `ii:KeyRisk` | 《流程设计》Q8 |
| `ii:checklistItem` | `xsd:string` | `ii:QualityChecklist` | 《流程设计》Q26 |
| `ii:checklistResult` | `xsd:string`（达标/未达标） | `ii:QualityChecklist` | 《流程设计》Q26 |
| `ii:resourceMatchingNote` | `xsd:string` | `ii:QualityChecklist` | 《流程设计》Q26 |
| `ii:dmuRole` | `xsd:string`（采购决策者/影响者/使用者） | `ii:DecisionMakingUnit` | 《流程设计》Q15 |
| `ii:dmuConcern` | `xsd:string` | `ii:DecisionMakingUnit` | 《流程设计》Q15 |

---

## 4. 约束（`owl:Restriction`）

| 类 | 属性 | 约束 | 出处 |
|---|---|---|---|
| `ii:InsightScenario` | `ii:scenarioText` | `exactly 1` | 《流程设计》Q4 |
| `ii:InsightScenario` | `ii:parseSource` | `exactly 1` | 《流程设计》Q13 |
| `ii:InitialHypothesisTree` | `ii:hasHypothesis` | `min 3` ∧ `max 5` | 《流程设计》Q29（3–5 个核心假设） |
| `ii:InitialHypothesisTree` | `ii:hasValidationLog` | `exactly 1` | 《流程设计》Q29 |
| `ii:ToolFinding` | `ii:referencesTool` | `exactly 1` | 流程 v1.2（逐工具） |
| `ii:ToolFinding` | `ii:findingConclusion` | `exactly 1` | 同上 |
| `ii:FiveLooksInsight` | `ii:hasAnalysisSummary` | `exactly 1` | 流程 v1.2（MaaS 总结） |
| `ii:FiveLooksInsight` | `ii:hasQualityChecklist` | `exactly 1` | 《流程设计》Q26 |
| `ii:Opportunity` | `ii:hasPriority` | `exactly 1` | 《流程设计》Q6 |
| `ii:Opportunity` | `ii:hasSpanPosition` | `exactly 1` | 《流程设计》Q19 |
| `ii:OpportunityList` | `ii:hasOpportunity` | `min 1` | 《流程设计》Q18 |
| `ii:StrategicControlPoint` | `ii:hasControlPointType` | `exactly 1` | 《流程设计》Q5 |
| `ii:InsightReport` | `ii:hasSoWhat` | `exactly 1` | 《流程设计》Q27 |
| `ii:InsightReport` | `ii:hasRefreshSignal` | `min 1` | 《流程设计》Q27 |
| `ii:OutboundInsightMessage` | `ii:triggeredBy` | `min 1` | 《流程设计》Q8 |
| `ii:Strategy` | `ii:mapsToStrategy`（反向，经转化表） | 每条策略 **≥1** 条洞察依据 | 《流程设计》Q28 |

> 数值约束（如 SPAN 阈值、市场规模量纲）为业务口径，标 **【待考】**，**不硬编码**于本体，待业务确认后以数据属性/规则补齐。

---

## 5. SWRL 规则（规划，明细留第 6 步落地）

| 规则 | 条件（body） | 结论（head） | 出处 |
|---|---|---|---|
| R1 高优先级机会点 | `Opportunity(?o) ∧ hasPriority(?o, ?p) ∧ OpportunityPriority(?p) ∧ priorityValue(?p, "高")` | `HighPriorityOpportunity(?o)` | 《流程设计》Q7 |
| R2 关键风险 | `Opportunity(?o) ∧ riskLevel(?o, ?r) ∧ swrlb:equal(?r, "高")` | `KeyRisk(?o)` | 《流程设计》Q8 |
| R3 外发触发 | `HighPriorityOpportunity(?o) ∨ KeyRisk(?o)` | `requiresOutbound(?o, true)` | 《流程设计》节点 9 |

> 规则命中后，`SwrlRuleTriggerListener` 检测新推导个体 → 回调 → `RabbitMqHandler.send()` 外发（与 Example 同构）。规则明细与阈值（如分档口径）**以业务确认为准**【待考】。

---

## 6. 关系与约束的一致性检查要点

1. **对象属性互逆成对**：`hasHypothesis/hypothesisOf`、`hasOpportunity/opportunityOf`、`hasControlPoint/controlPointOf`、`hasObjective/objectiveOf`、`hasStrategy/strategyOf`、`derivedFrom/derivedInsight` 均成对声明。
2. **函数属性（Functional）**：`belongsToScenario`、`hasValidationLog`、`hasAnalysisSummary`、`referencesTool`、`hasQualityChecklist`、`hasSpanPosition`、`hasPriority`、`hasControlPointType`、`hasSoWhat` —— 每实例至多 1 值。
3. **基数闭合**：§4 的 `exactly 1`/`min` 约束与 SWRL 结论不冲突；派生类（`HighPriorityOpportunity`/`KeyRisk`）仅由规则推导，**不人工断言**。
4. **值域/定义域闭合**：`basedOn`/`evidencedBy` 的取值限定为五看洞察或工具结论；`citation`/`sourceLevel`/`confidence` 覆盖所有「结论类」。
5. **数值类型**：`marketSizeEstimate`/`spanAttractiveness`/`spanCompetitiveness` 用 `xsd:decimal`；布尔校验项 `smart*` 用 `xsd:boolean`。
6. **【待考】不硬编码**：所有量纲/阈值/行业事实以【待考】标记，本体不写入具体数值（★12/14）。

---

## 7. ABox 典型个体设计（落地于第 6 步 · 闸门⑦）

> 覆盖「一个洞察场景 → 初始假设树 → 五看结论 → 机会点与优先级 → 交叉验证/假设 → 三定 → 报告」的**最小闭环**。示例用**中性占位**（不绑定任何特定行业事实/数值），行业事实一律【待考】。

| 个体 | 类型 | 关键断言 | 说明 |
|---|---|---|---|
| `ii:Scenario_Example` | `ii:InsightScenario` | scenarioText="示例洞察场景"、parseSource=Fallback | 泛化占位，不含具体行业 |
| `ii:Validation_Example` | `ii:ScenarioValidation` | validationResult="通过" | |
| `ii:HypTree_Example` | `ii:InitialHypothesisTree` | hasHypothesis = 3 个 `Hypothesis` | 演示 3–5 假设 |
| `ii:Hyp_1..3` | `ii:Hypothesis` | hypothesisBranch ∈ {行业, 市场, 机会} | 占位 |
| `ii:ValidationLog_Example` | `ii:HypothesisValidationLog` | — | 回填证据链 |
| `ii:IndustryTrend_Example` | `ii:IndustryTrendInsight` | hasToolFinding = 5、hasAnalysisSummary = 1 | 五工具 |
| `ii:MarketCustomer_Example` | `ii:MarketCustomerInsight` | hasToolFinding = 5、hasDmu ≥1 | 含 DMU |
| `ii:Competition_Example` | `ii:CompetitionInsight` | hasToolFinding = 3 | 三工具 |
| `ii:Self_Example` | `ii:SelfInsight` | hasToolFinding = 4 | 四工具 |
| `ii:OppList_Example` | `ii:OpportunityList` | hasOpportunity ≥1 | |
| `ii:Opp_1` | `ii:Opportunity` | hasPriority=高、basedOn=IndustryTrend_Example | 驱动 R1/R3 |
| `ii:Span_1` | `ii:SpanPosition` | spanQuadrant="高吸引-强竞争" | |
| `ii:ConsistencyMatrix_Example` | `ii:InsightConsistencyMatrix` | marksPending ≥0 | |
| `ii:KeyAssumption_1` | `ii:KeyAssumption` | uncertaintyLevel="中" | |
| `ii:ControlPoint_1` | `ii:StrategicControlPoint` | hasControlPointType=技术专利 | |
| `ii:Objective_1` | `ii:Objective` | smart* = true | 数值【待考】 |
| `ii:Strategy_1` | `ii:Strategy` | pathPhase="短期速赢" | |
| `ii:Report_Example` | `ii:InsightReport` | hasSoWhat=1、hasRefreshSignal≥1 | |
| `ii:Outbound_Example` | `ii:OutboundInsightMessage` | triggeredBy=Opp_1 | 条件性 |

> 具体个体集/命名/覆盖场景于第 6 步交业务确认（闸门⑦）。

---

## 8. OBDA / SKOS 预留

- **OBDA（第 11 步）**：五看洞察素材、机会点、三定结论等运行态数据经 Ontop 映射到 MySQL（库名 `industryinsight`）；规划表如 `insight_scenario`、`tool_finding`、`opportunity`、`control_point`、`objective`、`strategy` 等；映射至同一 IRI 命名空间 `http://example.org/industryinsight#`。
- **SKOS（第 13 步，已产出）**：`ontology/industryinsight-terminology.ttl` —— 1 个 `skos:ConceptScheme`（IRI `http://example.org/industryinsight/term`）+ **156 个 `skos:Concept`**（与本体 TBox 的 49 类 / 44 对象属性 / 63 数据属性一一对应），概念 IRI 为 `http://example.org/industryinsight/term#<Local>`；每概念含中文 `skos:prefLabel` + 英文名/惯用异名 `skos:altLabel` + `skos:exactMatch` 回指本体实体。**ABox 个体（如 23 个 `Tool_*`）不收录**（个体系实例数据，非规范术语）。

---

## 9. 待业务确认项

> 承接《顶层设计》§5、《流程设计》§7、《本体顶层设计》§7。以下为**第 4 步**需业务裁定之点，**未经确认不进入第 6 步**。

| # | 问题 | 助手建议 | 业务结论 |
|---|---|---|---|
| Q-C1 | **类体系与主干分支是否齐全** | 8 大域 + 五看根类 `FiveLooksInsight` + 推理派生类 | ✅ 已确认（2026-10-22） |
| Q-C2 | **对象/数据属性是否满足各节点所需数据** | 见 §2/§3，逐节点对齐流程 v1.2 | ✅ 已确认（2026-10-22） |
| Q-C3 | **约束（基数/函数/值域）口径** | 见 §4；数值量纲【待考】不硬编码 | ✅ 已确认（2026-10-22） |
| Q-C4 | **SWRL 规则范围** | R1–R3（高优先级机会点/关键风险/外发触发）；阈值【待考】 | ✅ 已确认（2026-10-22） |
| Q-C5 | **受控词表取值** | 见 §1.1（控制点类型等以业务口径为准） | ✅ 已确认（2026-10-22） |
| Q-C6 | **典型 ABox 覆盖范围** | §7 最小闭环（中性占位、行业事实【待考】） | ✅ 已确认（2026-10-22）（闸门⑦ 复核） |

---

## 闸门③留痕

| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-22 | v1.0 | 业务方（用户） | **通过**：8 大域明细类/属性/约束/SWRL/典型 ABox 确认；Q-C1…Q-C6 全部采纳助手建议。**进入第 5 步（生成 BPMN 含 UI）**。 |

---

## 闸门⑦留痕（典型 ABox 数据 · 第 6 步）

| 日期 | 确认范围（典型个体集/覆盖场景） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-22 | 单文件本体 `industryinsight-all.owl` 中的典型 ABox（最小闭环：场景→假设树→五看→机会点→交叉验证→三定→报告→外发消息；中性占位） | 业务方（用户） | **通过**：个体集/类型/关键断言/代表性确认。进入第 7 步 |

---

## 闸门⑥留痕（SKOS 词汇表 · 第 13 步）

| 日期 | 确认范围（规范术语/同义词/命名空间/收录边界） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-22 | `ontology/industryinsight-terminology.ttl`：156 概念（TBox 一一对应）+ 166 `skos:altLabel`（英文名/惯用异名）+ 156 `skos:exactMatch`；概念 IRI `.../industryinsight/term#<Local>`；**不收录 ABox 个体（23 个 `Tool_*`）**；12 个逆属性标签由正向属性语义推导 | 业务方（用户） | **通过**：规范术语/同义词集/命名空间/收录边界全部确认。进入第 14 步（词汇表测试与全流程测试） |

---

## 变更记录

| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-10-22 | 第 4 步初稿：本体核心设计（8 大域明细类 + 对象/数据属性 + owl:Restriction 约束 + SWRL 规则 + 一致性检查 + 典型 ABox 设计 + OBDA/SKOS 预留 + §9 待确认项） | — |
| 2026-10-22 | **第 4 步 v1.0 定稿**：闸门③ 通过；Q-C1…Q-C6 全部采纳助手建议。进入第 5 步 | 业务方（用户） |
| 2026-10-22 | **第 6 步完成**：生成单文件本体 `ontology/industryinsight-all.owl`（TBox + SWRL + 典型 ABox）与 `catalog-v001.xml`；**闸门⑦ 通过**（典型 ABox 确认）。进入第 7 步 | 业务方（用户） |
| 2026-10-22 | **命名空间缺陷修复**：`industryinsight-all.owl` 原以 `xml:base="http://example.org/industryinsight#"` + 相对 IRI（如 `rdf:about="AnalysisDomain"`）声明，按 RFC 3986 解析后实体 IRI 实为 `http://example.org/AnalysisDomain`（丢失 `industryinsight#`），与已确认的单一前缀不符，并导致第 13 步 SKOS 交叉校验失败。经客户确认，将全部相对 `rdf:about`/`rdf:resource`（236+369 处）批量改为 `#Local`，实体 IRI 复原为 `http://example.org/industryinsight#<Local>`；同步 `IndustryInsightOntologyJobWorker` 的 `scenarioIri` 与词汇表 `exactMatch` 目标对齐 | 业务方（用户） |
| 2026-10-22 | **第 13 步完成**：产出 SKOS 词汇表 `ontology/industryinsight-terminology.ttl`（156 概念 + 166 别名 + 156 exactMatch）；**闸门⑥ 通过**。回归 23/23 全绿（SKOS 用例激活） | 业务方（用户） |
