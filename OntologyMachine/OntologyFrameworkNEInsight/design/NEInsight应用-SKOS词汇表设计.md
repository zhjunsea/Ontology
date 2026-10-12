# NEInsight 应用 — SKOS 词汇表设计

> **用法**：本模板产出第 13 步。**闸门⑥**：业务人员确认术语与同义词表后方可进入第 14 步。
> **基线**：`OntologyFrameworkExample/ontology/pizza-terminology.ttl`（形态）；构建依据为本应用已定稿 TBox。
> **硬性**：术语**来源于本体**，每个概念必须能对应到本体 IRI（★10：不得凭空造词）。

| 项目 | 内容 |
|---|---|
| 应用 | NEInsight（新能源行业 IT 空间与云空间洞察） |
| 文档日期 | 2026-10-12 |
| 关联本体 | `ontology/neinsight-all.owl`（已定稿 TBox：core/env/decision/rules） |
| 产出文件 | `ontology/neinsight-terminology.ttl` |
| 命名空间 | `http://example.org/neinsight/term/`（`term:` 前缀） |
| 概念方案 | `http://example.org/neinsight/term`（`skos:ConceptScheme` + `owl:Ontology`） |
| 消费方 | `OntologyLabelMatcher` / `SkosSynonymReader` |

---

## 1. 目的与范围

**目的**：把 NEInsight 领域核心业务术语标准化为 SKOS 概念体系，统一 OWL 本体与其他系统中的词义，实现「同名同义、同义同名」。

**范围**：覆盖本应用已定稿 TBox 的**全部类（29）**、**对象属性（29）**与**数据属性（16）**，共 **74** 个 SKOS 概念；概念与本体 IRI **一一对应**，无孤立词条、无造词。术语来源：《NEInsight应用-本体顶层设计》《NEInsight应用-本体核心设计》（闸门②/③）。

## 2. 术语体系（概念树）

```
term:InsightElement  (战略洞察要素)
├── term:InsightDomain  (洞察域)
│   ├── term:ITSpace            (IT 空间)
│   ├── term:CloudSpace         (云空间)
│   └── term:CombinedSpace      (联合空间)
├── term:EnvironmentScan  (环境扫描 / 五看基类)
│   ├── term:IndustryTrend      (行业趋势)
│   │   ├── term:MacroFactor    (宏观因素)
│   │   └── term:ValueTransfer  (价值转移)
│   ├── term:MarketCustomer     (市场与客户)
│   │   ├── term:Market         (市场)
│   │   └── term:Customer       (客户)
│   │       ├── term:CustomerPainPoint (客户痛点)
│   │       └── term:CustomerNeed      (客户需求)
│   ├── term:CompetitiveLandscape (竞争格局)
│   │   └── term:Competitor     (竞争对手)
│   ├── term:SelfAssessment     (自身评估)
│   │   ├── term:Capability     (能力)
│   │   └── term:ShortBoard     (短板)
│   └── term:Opportunity        (机会点)
│       └── term:SPANQuadrant   (SPAN 象限)
└── term:Decision  (决策 / 三定)
    ├── term:StrategicControlPoint (战略控制点)
    │   └── term:ControlPointType  (控制点类型)
    ├── term:Objective            (目标)
    └── term:Strategy             (策略)
        ├── term:OrgStrategy      (组织策略)
        ├── term:ProductStrategy  (产品策略)
        └── term:MarketStrategy   (市场策略)
```

> 类概念层级用 `skos:broader` 表达，与本体 `rdfs:subClassOf` **同构**（26 条 broader 边）；对象/数据属性概念为顶层概念。

## 3. 概念清单（术语 ⇄ 本体 IRI 映射）

> 每行给出对应本体 IRI（类/属性），用于第 14 步交叉校验。

| SKOS 概念 | `prefLabel`@zh | `prefLabel`@en | 对应本体 IRI | `skos:broader` | `skos:altLabel`(@zh) |
|---|---|---|---|---|---|
| `term:InsightElement` | 战略洞察要素 | Insight Element | `http://example.org/neinsight/core#InsightElement` | — | — |
| `term:InsightDomain` | 洞察域 | Insight Domain | `http://example.org/neinsight/core#InsightDomain` | `term:InsightElement` | — |
| `term:ITSpace` | IT 空间 | IT Space | `http://example.org/neinsight/core#ITSpace` | `term:InsightDomain` | — |
| `term:CloudSpace` | 云空间 | Cloud Space | `http://example.org/neinsight/core#CloudSpace` | `term:InsightDomain` | — |
| `term:CombinedSpace` | 联合空间 | Combined Space | `http://example.org/neinsight/core#CombinedSpace` | `term:InsightDomain` | — |
| `term:EnvironmentScan` | 环境扫描（五看基类） | Environment Scan | `http://example.org/neinsight/env#EnvironmentScan` | `term:InsightElement` | — |
| `term:IndustryTrend` | 行业趋势 | Industry Trend | `http://example.org/neinsight/env#IndustryTrend` | `term:EnvironmentScan` | 行业趋势 / 产业趋势 |
| `term:MacroFactor` | 宏观因素 | Macro Factor | `http://example.org/neinsight/env#MacroFactor` | `term:IndustryTrend` | — |
| `term:ValueTransfer` | 价值转移 | Value Transfer | `http://example.org/neinsight/env#ValueTransfer` | `term:IndustryTrend` | 价值转移方向 |
| `term:MarketCustomer` | 市场与客户 | Market Customer | `http://example.org/neinsight/env#MarketCustomer` | `term:EnvironmentScan` | 市场与客户 |
| `term:Market` | 市场 | Market | `http://example.org/neinsight/env#Market` | `term:MarketCustomer` | — |
| `term:Customer` | 客户 | Customer | `http://example.org/neinsight/env#Customer` | `term:MarketCustomer` | — |
| `term:CustomerPainPoint` | 客户痛点 | Customer Pain Point | `http://example.org/neinsight/env#CustomerPainPoint` | `term:MarketCustomer` | — |
| `term:CustomerNeed` | 客户需求 | Customer Need | `http://example.org/neinsight/env#CustomerNeed` | `term:MarketCustomer` | — |
| `term:CompetitiveLandscape` | 竞争格局 | Competitive Landscape | `http://example.org/neinsight/env#CompetitiveLandscape` | `term:EnvironmentScan` | 竞争格局 |
| `term:Competitor` | 竞争对手 | Competitor | `http://example.org/neinsight/env#Competitor` | `term:CompetitiveLandscape` | — |
| `term:SelfAssessment` | 自身评估 | Self Assessment | `http://example.org/neinsight/env#SelfAssessment` | `term:EnvironmentScan` | 自身评估 / 自评 |
| `term:Capability` | 能力 | Capability | `http://example.org/neinsight/env#Capability` | `term:SelfAssessment` | — |
| `term:ShortBoard` | 短板 | Short Board | `http://example.org/neinsight/env#ShortBoard` | `term:SelfAssessment` | — |
| `term:Opportunity` | 机会点 | Opportunity | `http://example.org/neinsight/env#Opportunity` | `term:EnvironmentScan` | 战略机会点 / 机会 |
| `term:SPANQuadrant` | SPAN 象限（分类值类，不继承 Opportunity） | SPAN Quadrant | `http://example.org/neinsight/env#SPANQuadrant` | — | SPAN 象限 |
| `term:Decision` | 决策（三定基类） | Decision | `http://example.org/neinsight/decision#Decision` | `term:InsightElement` | — |
| `term:StrategicControlPoint` | 战略控制点 | Strategic Control Point | `http://example.org/neinsight/decision#StrategicControlPoint` | `term:Decision` | 战略控制点 / 控制点 |
| `term:ControlPointType` | 控制点类型（分类值类，不继承 StrategicControlPoint） | Control Point Type | `http://example.org/neinsight/decision#ControlPointType` | — | 控制点类型 |
| `term:Objective` | 目标 | Objective | `http://example.org/neinsight/decision#Objective` | `term:Decision` | 战略目标 / 目标 |
| `term:Strategy` | 策略 | Strategy | `http://example.org/neinsight/decision#Strategy` | `term:Decision` | 战略策略 / 策略 |
| `term:OrgStrategy` | 组织策略 | Org Strategy | `http://example.org/neinsight/decision#OrgStrategy` | `term:Strategy` | — |
| `term:ProductStrategy` | 产品策略 | Product Strategy | `http://example.org/neinsight/decision#ProductStrategy` | `term:Strategy` | — |
| `term:MarketStrategy` | 市场策略 | Market Strategy | `http://example.org/neinsight/decision#MarketStrategy` | `term:Strategy` | — |
| `term:belongsToDomain` | 属于洞察域 | belongs To Domain | `http://example.org/neinsight/core#belongsToDomain` | — | — |
| `term:hasElement` | 含洞察要素 | has Element | `http://example.org/neinsight/core#hasElement` | — | — |
| `term:hasMacroFactor` | 含宏观因素 | has Macro Factor | `http://example.org/neinsight/env#hasMacroFactor` | — | — |
| `term:macroFactorOf` | 属于行业趋势 | macro Factor Of | `http://example.org/neinsight/env#macroFactorOf` | — | — |
| `term:indicatesValueTransfer` | 指示价值转移 | indicates Value Transfer | `http://example.org/neinsight/env#indicatesValueTransfer` | — | — |
| `term:targetsMarket` | 面向市场 | targets Market | `http://example.org/neinsight/env#targetsMarket` | — | — |
| `term:marketOf` | 市场的聚合方 | market Of | `http://example.org/neinsight/env#marketOf` | — | — |
| `term:hasCustomer` | 含客户 | has Customer | `http://example.org/neinsight/env#hasCustomer` | — | — |
| `term:customerOf` | 客户所属市场 | customer Of | `http://example.org/neinsight/env#customerOf` | — | — |
| `term:hasPainPoint` | 含客户痛点 | has Pain Point | `http://example.org/neinsight/env#hasPainPoint` | — | — |
| `term:painPointOf` | 痛点所属客户 | pain Point Of | `http://example.org/neinsight/env#painPointOf` | — | — |
| `term:hasNeed` | 含客户需求 | has Need | `http://example.org/neinsight/env#hasNeed` | — | — |
| `term:needOf` | 需求所属客户 | need Of | `http://example.org/neinsight/env#needOf` | — | — |
| `term:involvesCompetitor` | 涉及竞争对手 | involves Competitor | `http://example.org/neinsight/env#involvesCompetitor` | — | — |
| `term:competitorIn` | 竞争对手所属格局 | competitor In | `http://example.org/neinsight/env#competitorIn` | — | — |
| `term:hasCapability` | 含能力 | has Capability | `http://example.org/neinsight/env#hasCapability` | — | — |
| `term:capabilityOf` | 能力所属评估 | capability Of | `http://example.org/neinsight/env#capabilityOf` | — | — |
| `term:hasShortBoard` | 含短板 | has Short Board | `http://example.org/neinsight/env#hasShortBoard` | — | — |
| `term:shortBoardOf` | 短板所属评估 | short Board Of | `http://example.org/neinsight/env#shortBoardOf` | — | — |
| `term:hasOpportunity` | 含机会点 | has Opportunity | `http://example.org/neinsight/env#hasOpportunity` | — | — |
| `term:opportunityOf` | 机会点所属域 | opportunity Of | `http://example.org/neinsight/env#opportunityOf` | — | — |
| `term:hasSpanQuadrant` | 有 SPAN 象限 | has Span Quadrant | `http://example.org/neinsight/env#hasSpanQuadrant` | — | — |
| `term:addressesOpportunity` | 针对机会点 | addresses Opportunity | `http://example.org/neinsight/decision#addressesOpportunity` | — | — |
| `term:opportunityAddressedBy` | 机会点被控制点针对 | opportunity Addressed By | `http://example.org/neinsight/decision#opportunityAddressedBy` | — | — |
| `term:hasControlPointType` | 有控制点类型 | has Control Point Type | `http://example.org/neinsight/decision#hasControlPointType` | — | — |
| `term:hasObjective` | 有目标 | has Objective | `http://example.org/neinsight/decision#hasObjective` | — | — |
| `term:objectiveOf` | 目标所属控制点 | objective Of | `http://example.org/neinsight/decision#objectiveOf` | — | — |
| `term:hasStrategy` | 有策略 | has Strategy | `http://example.org/neinsight/decision#hasStrategy` | — | — |
| `term:strategyOf` | 策略所属目标 | strategy Of | `http://example.org/neinsight/decision#strategyOf` | — | — |
| `term:domainCode` | 洞察域编码 | domain Code | `http://example.org/neinsight/core#domainCode` | — | — |
| `term:marketAttractiveness` | 市场吸引力评分 | market Attractiveness | `http://example.org/neinsight/env#marketAttractiveness` | — | — |
| `term:competitivePosition` | 竞争地位评分 | competitive Position | `http://example.org/neinsight/env#competitivePosition` | — | — |
| `term:opportunityPriority` | 机会点优先级 | opportunity Priority | `http://example.org/neinsight/env#opportunityPriority` | — | — |
| `term:trendDirection` | 趋势方向 | trend Direction | `http://example.org/neinsight/env#trendDirection` | — | — |
| `term:painSeverity` | 痛点严重度 | pain Severity | `http://example.org/neinsight/env#painSeverity` | — | — |
| `term:capabilityScore` | 能力评分 | capability Score | `http://example.org/neinsight/env#capabilityScore` | — | — |
| `term:flaggedForAlert` | 是否外发标记 | flagged For Alert | `http://example.org/neinsight/env#flaggedForAlert` | — | — |
| `term:cpTypeCode` | 控制点类型编码 | cp Type Code | `http://example.org/neinsight/decision#cpTypeCode` | — | — |
| `term:hasMetric` | 目标衡量指标 | has Metric | `http://example.org/neinsight/decision#hasMetric` | — | — |
| `term:targetValue` | 目标值 | target Value | `http://example.org/neinsight/decision#targetValue` | — | — |
| `term:hasTimeframe` | 目标时限 | has Timeframe | `http://example.org/neinsight/decision#hasTimeframe` | — | — |
| `term:isMeasurable` | 可衡量 | is Measurable | `http://example.org/neinsight/decision#isMeasurable` | — | — |
| `term:isRelevant` | 相关性 | is Relevant | `http://example.org/neinsight/decision#isRelevant` | — | — |
| `term:hasRationale` | 策略依据 | has Rationale | `http://example.org/neinsight/decision#hasRationale` | — | — |
| `term:smartCompliant` | 符合 SMART | smart Compliant | `http://example.org/neinsight/decision#smartCompliant` | — | — |

## 4. 标签与同义词规范

| 项 | 规则 |
|---|---|
| `skos:prefLabel` | 每语言恰一条；本应用主语言 `@zh` + `@en`（74 个概念各齐备） |
| `skos:altLabel` | 同义词/别名（受 `SkosSynonymReader` 消费）；本表共 17 条，如 机会点↔战略机会点、行业趋势↔产业趋势 |
| `skos:definition` | 业务定义，由本体结构（类标签 / 属性定义域·值域）派生，可追溯 |
| 概念方案头 | `skos:ConceptScheme` + `owl:Ontology` 双类型；`dct:created` / `dct:description` |

## 5. 交叉校验设计（第 14 步执行）

| 校验项 | 方法 | 通过判据 |
|---|---|---|
| 结构校验 | 纯 Jena 解析 TTL | 每个 `skos:Concept` 有 `prefLabel`(@zh)；`broader` 指向存在概念且无自环 |
| 本体交叉 | OWL `skos:exactMatch` ↔ 概念集 | 本体中所有指向 `term/` 的 `skos:exactMatch` 目标均在词汇表中定义 |
| 无造词 | 词汇 ⊆ 本体术语 | 无孤立词条（每个概念均有 OWL `skos:exactMatch` 反向引用） |

> **离线预校验（第 13 步即时执行）**：`HWCodeArtsDir/check_skos.py` → `SKOS concepts = 74 | OWL exactMatch targets = 74 | broader = 26 | ISSUES = 0`。

## 6. 待确认项

| # | 问题 | 业务结论 |
|---|---|---|
| 1 | 74 个概念（类 29 / 对象属性 29 / 数据属性 16）是否覆盖所需业务术语 | 认可 |
| 2 | 中文首选标签与英文标签是否认可 | 认可 |
| 3 | 同义词（`skos:altLabel`，如 机会点↔战略机会点）是否认可 | 认可 |

---

## 闸门⑥留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-12 | v1 | 用户 | 74 个概念及其 @zh/@en 首选标签、altLabel 同义词全部认可，一致通过，闸门⑥定稿。无修改点。确认后进入第 14 步（词汇表测试 + 全流程回归）。 |

