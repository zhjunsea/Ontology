# 新能源行业 IT 空间与云空间洞察（NEInsight）— 本体核心设计（核心类、属性与关系）

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkNEInsight / NEInsight |
| 文档日期 | 2026-10-12 |
| 上游 | 《NEInsight应用-本体顶层设计》（已确认 / 闸门②） |
| 关联流程 | 《NEInsight应用-流程设计》（定稿 v1） |
| 产出本体（规划） | `ontology/neinsight-all.owl`（单文件，含典型 ABox） |

> **本文件为第 4 步产出，即闸门③待确认文件。** 确认后方可生成第 6 步本体文件。
> 每条公理标注**出处**（用户输入 / S1–S6 / 【待考】）。**凡【待考】项不进本体**（仅登记）。

---

## 1. 核心类体系（明细）

```
core#InsightElement                        （根类：战略洞察要素）
├── core#InsightDomain                     （洞察域）
│   ├── core#ITSpace                       （IT 空间）
│   ├── core#CloudSpace                    （云空间）
│   └── core#CombinedSpace                 （联合）
├── env#EnvironmentScan                    （环境扫描 —— 五看基类）
│   ├── env#IndustryTrend                  （行业趋势）
│   │   ├── env#MacroFactor                （宏观因素：政治/经济/社会/技术）
│   │   └── env#ValueTransfer              （价值转移）
│   ├── env#MarketCustomer                 （市场与客户）
│   │   ├── env#Market                     （市场）
│   │   ├── env#Customer                   （客户）
│   │   ├── env#CustomerPainPoint          （客户痛点 Pains）★独立类
│   │   └── env#CustomerNeed               （客户需求 Wants）★独立类
│   ├── env#CompetitiveLandscape           （竞争格局）
│   │   └── env#Competitor                 （竞争对手）
│   ├── env#SelfAssessment                 （自身评估）
│   │   ├── env#Capability                 （能力）
│   │   └── env#ShortBoard                 （短板）
│   └── env#Opportunity                    （机会点）
└── decision#Decision                      （决策 —— 三定基类）
    ├── decision#StrategicControlPoint     （战略控制点）
    ├── decision#Objective                 （目标）
    └── decision#Strategy                  （策略）
        ├── decision#OrgStrategy           （组织策略）
        ├── decision#ProductStrategy       （产品策略）
        └── decision#MarketStrategy        （市场策略）

（顶层分类值类，不继承核心要素层级）
env#SPANQuadrant                           （SPAN 象限：Opportunity 的取值分类）
decision#ControlPointType                  （控制点类型：StrategicControlPoint 的取值分类）
```
> 出处：整体主干来自《本体顶层设计》§5（闸门②确认）；`ControlPointType` 取值 {技术领先/成本优势/品牌/市场份额/价值链控制} 来自用户输入（闸门①）。
>
> **【实现期修正 · 修正记录】** `env#SPANQuadrant` 与 `decision#ControlPointType` 原设计为 `⊑ Opportunity` / `⊑ StrategicControlPoint`。因 `Opportunity ⊑ hasSpanQuadrant exactly 1 SPANQuadrant`、`StrategicControlPoint ⊑ hasControlPointType exactly 1 ControlPointType`，子类个体（`Q_HighHigh`/`Type_TechnologyLead`）被迫各自再获一个填充而**导致本体不一致**（Openllet 规则引擎 NPE）。二者语义上是**分类值类**（机会点在 SPAN 两轴上的象限、战略控制点的类型枚举），不是其所描述实体的子类。经用户确认（方案：改父类 → 顶层，保留基数 1），改为顶层分类值类；`hasSpanQuadrant`/`hasControlPointType` 基数 1 设计保持不变。

---

## 2. 对象属性

| 属性 | 特性 | `owl:inverseOf` | 定义域 | 值域 | 出处 |
|---|---|---|---|---|---|
| `core#belongsToDomain` | 函数 | `core#hasElement` | `core#InsightElement` | `core#InsightDomain` | 用户输入（洞察域） |
| `core#hasElement` | — | `core#belongsToDomain` | `core#InsightDomain` | `core#InsightElement` | 由上推出 |
| `env#hasMacroFactor` | — | `env#macroFactorOf` | `env#IndustryTrend` | `env#MacroFactor` | S1/S2/S3 |
| `env#macroFactorOf` | — | `env#hasMacroFactor` | `env#MacroFactor` | `env#IndustryTrend` | 由上推出 |
| `env#indicatesValueTransfer` | — | — | `env#IndustryTrend` | `env#ValueTransfer` | S4 |
| `env#targetsMarket` | — | `env#marketOf` | `env#MarketCustomer` | `env#Market` | S5 |
| `env#marketOf` | — | `env#targetsMarket` | `env#Market` | `env#MarketCustomer` | 由上推出 |
| `env#hasCustomer` | — | `env#customerOf` | `env#Market` | `env#Customer` | S5 |
| `env#customerOf` | — | `env#hasCustomer` | `env#Customer` | `env#Market` | 由上推出 |
| `env#hasPainPoint` | — | `env#painPointOf` | `env#Customer` | `env#CustomerPainPoint` | 用户输入 |
| `env#painPointOf` | — | `env#hasPainPoint` | `env#CustomerPainPoint` | `env#Customer` | 由上推出 |
| `env#hasNeed` | — | `env#needOf` | `env#Customer` | `env#CustomerNeed` | 用户输入 |
| `env#needOf` | — | `env#hasNeed` | `env#CustomerNeed` | `env#Customer` | 由上推出 |
| `env#involvesCompetitor` | — | `env#competitorIn` | `env#CompetitiveLandscape` | `env#Competitor` | 用户输入 |
| `env#competitorIn` | — | `env#involvesCompetitor` | `env#Competitor` | `env#CompetitiveLandscape` | 由上推出 |
| `env#hasCapability` | — | `env#capabilityOf` | `env#SelfAssessment` | `env#Capability` | S6 |
| `env#capabilityOf` | — | `env#hasCapability` | `env#Capability` | `env#SelfAssessment` | 由上推出 |
| `env#hasShortBoard` | — | `env#shortBoardOf` | `env#SelfAssessment` | `env#ShortBoard` | S6 |
| `env#shortBoardOf` | — | `env#hasShortBoard` | `env#ShortBoard` | `env#SelfAssessment` | 由上推出 |
| `env#hasOpportunity` | — | `env#opportunityOf` | `core#InsightDomain` | `env#Opportunity` | 用户输入（看机会） |
| `env#opportunityOf` | — | `env#hasOpportunity` | `env#Opportunity` | `core#InsightDomain` | 由上推出 |
| `env#hasSpanQuadrant` | 函数 | — | `env#Opportunity` | `env#SPANQuadrant` | 用户输入（SPAN 工具） |
| `decision#addressesOpportunity` | — | `decision#opportunityAddressedBy` | `decision#StrategicControlPoint` | `env#Opportunity` | 用户输入（三定） |
| `decision#opportunityAddressedBy` | — | `decision#addressesOpportunity` | `env#Opportunity` | `decision#StrategicControlPoint` | 由上推出 |
| `decision#hasControlPointType` | 函数 | — | `decision#StrategicControlPoint` | `decision#ControlPointType` | 用户输入（闸门①） |
| `decision#hasObjective` | — | `decision#objectiveOf` | `decision#StrategicControlPoint` | `decision#Objective` | 用户输入 |
| `decision#objectiveOf` | — | `decision#hasObjective` | `decision#Objective` | `decision#StrategicControlPoint` | 由上推出 |
| `decision#hasStrategy` | — | `decision#strategyOf` | `decision#Objective` | `decision#Strategy` | 用户输入 |
| `decision#strategyOf` | — | `decision#hasStrategy` | `decision#Strategy` | `decision#Objective` | 由上推出 |

> 说明：`owl:inverseOf` 成对声明，保证关系双向可查；函数属性（`owl:FunctionalProperty`）用于「一要素一域」「一机会一象限」「一控制点一类型」等唯一性。

---

## 3. 数据属性

| 属性 | 类型（★数值用数值类型） | 定义域 | 出处 |
|---|---|---|---|
| `core#domainCode` | `xsd:string` | `core#InsightDomain` | 用户输入 |
| `env#marketAttractiveness` | `xsd:decimal`（1–5 分） | `env#Opportunity` | 用户输入（SPAN）；量纲【待考】 |
| `env#competitivePosition` | `xsd:decimal`（1–5 分） | `env#Opportunity` | 用户输入（SPAN）；量纲【待考】 |
| `env#opportunityPriority` | `xsd:string`（HIGH/MEDIUM/LOW） | `env#Opportunity` | SPAN 推导（见 §5 R1）；阈值【待考】 |
| `env#trendDirection` | `xsd:string` | `env#IndustryTrend` | 用户输入 |
| `env#painSeverity` | `xsd:integer`（1–5） | `env#CustomerPainPoint` | 用户输入；量纲【待考】 |
| `env#capabilityScore` | `xsd:integer`（1–5） | `env#Capability` | S6；量纲【待考】 |
| `decision#cpTypeCode` | `xsd:string` | `decision#ControlPointType` | 用户输入 |
| `decision#hasMetric` | `xsd:string` | `decision#Objective` | 用户输入（SMART·可衡量 M） |
| `decision#targetValue` | `xsd:decimal` | `decision#Objective` | 用户输入（SMART）；数值【待考】 |
| `decision#hasTimeframe` | `xsd:string` | `decision#Objective` | 用户输入（SMART·有时效 T） |
| `decision#isMeasurable` | `xsd:boolean` | `decision#Objective` | 用户输入（SMART·M，供 R3 校验） |
| `decision#isRelevant` | `xsd:boolean` | `decision#Objective` | 用户输入（SMART·相关性 R） |
| `decision#hasRationale` | `xsd:string` | `decision#Strategy` | 用户输入 |
| `decision#smartCompliant` | `xsd:boolean` | `decision#Objective` | SMART 推导（见 §5 R3） |
| `env#flaggedForAlert` | `xsd:boolean` | `env#Opportunity` | 外发推导（见 §5 R2） |

---

## 4. 约束（`owl:Restriction`）

| 类 | 属性 | 约束 | 出处 |
|---|---|---|---|
| `core#InsightElement` | `core#belongsToDomain` | `owl:minQualifiedCardinality 1 (core#InsightDomain)` | 用户输入 |
| `env#Opportunity` | `env#hasSpanQuadrant` | `owl:qualifiedCardinality 1 (env#SPANQuadrant)` | 用户输入（SPAN 必须定位象限） |
| `env#Opportunity` | `env#marketAttractiveness` | `owl:maxQualifiedCardinality 1 (xsd:decimal)` | 用户输入（SPAN 两轴至多各一；缺轴合法，不强制存在） |
| `env#Opportunity` | `env#competitivePosition` | `owl:maxQualifiedCardinality 1 (xsd:decimal)` | 用户输入（SPAN 两轴至多各一；缺轴合法，不强制存在） |
| `env#Customer` | `env#hasPainPoint` | `owl:minQualifiedCardinality 1 (env#CustomerPainPoint)` | 用户输入（看客户须有痛点） |
| `decision#StrategicControlPoint` | `decision#hasControlPointType` | `owl:qualifiedCardinality 1 (decision#ControlPointType)` | 用户输入（闸门①） |
| `decision#StrategicControlPoint` | `decision#addressesOpportunity` | `owl:minQualifiedCardinality 1 (env#Opportunity)` | 用户输入（控制点须源自机会点） |
| `decision#Objective` | `decision#isMeasurable` | `owl:qualifiedCardinality 1 (xsd:boolean)` | 用户输入（SMART·M） |
| `decision#Objective` | `decision#hasMetric` | `owl:minQualifiedCardinality 1 (xsd:string)` | 用户输入（SMART·M） |
| `decision#Strategy` | `decision#strategyOf` | `owl:minQualifiedCardinality 1 (decision#Objective)` | 用户输入（策略须服务目标） |

> **硬性**（★12）：以上约束一律写成 `owl:Restriction`，**不得写在注释里**；「值」与「约束」不混用；数值属性用 `xsd:decimal`/`xsd:integer`。
>
> **【实现期修正 · 修正记录】** 两轴 `marketAttractiveness`/`competitivePosition` 原设计为 `owl:qualifiedCardinality 1`（exact，含 min 1）。因 Openllet 的 DL 基数规则会为「缺轴」生成**匿名数据填充**，而 SWRL `swrlb:greaterThan/lessThan` 比较内建函数遇匿名项会抛 `NullPointerException`（`ComparisonTesters$OrderingTester: term1 is null`），导致缺轴个体使整个推理器崩溃。经用户确认，改为 `owl:maxQualifiedCardinality 1`（**至多 1**，仍保证唯一、不允许多值），使「缺轴」合法且不产生匿名填充；`hasSpanQuadrant`/`hasControlPointType`/`isMeasurable` 的「恰好 1」设计保持不变。

---

## 5. SWRL 规则

| 规则 | 条件（body） | 结论（head） | 出处 |
|---|---|---|---|
| **R1** SPAN→优先级 | `Opportunity(?o) ∧ marketAttractiveness(?o,?a) ∧ competitivePosition(?o,?c) ∧ swrlb:greaterThan(?a,3.0) ∧ swrlb:greaterThan(?c,3.0)` | `opportunityPriority(?o,"HIGH")` | 用户输入（SPAN）；阈值【待考】 |
| **R1b** SPAN→优先级（低） | `Opportunity(?o) ∧ marketAttractiveness(?o,?a) ∧ competitivePosition(?o,?c) ∧ swrlb:lessThan(?a,3.0) ∧ swrlb:lessThan(?c,3.0)` | `opportunityPriority(?o,"LOW")` | 同上 |
| **R2** 高优先级→外发标记 | `Opportunity(?o) ∧ opportunityPriority(?o,"HIGH")` | `flaggedForAlert(?o,true)` | 用户输入（闸门①·外发条件） |
| **R3** SMART 校验 | `Objective(?ob) ∧ isMeasurable(?ob,true) ∧ isRelevant(?ob,true) ∧ hasTimeframe(?ob,?t)` | `smartCompliant(?ob,true)` | 用户输入（SMART） |
| **R4** 机会点归属域 | `Opportunity(?o) ∧ belongsToDomain(?o,?d)` | `hasOpportunity(?d,?o)` | 用户输入（由域反向挂载） |

> 阈值 `3.0`、`"HIGH"/"LOW"` 等为**占位**（【待考】）；业务方给出分级口径后替换。

---

## 6. 关系与约束的一致性检查要点

1. **互逆闭合**：§2 每对 `owl:inverseOf` 双向声明，推理后可双向检索。
2. **基数唯一**：`belongsToDomain` / `hasSpanQuadrant` / `hasControlPointType` / `isMeasurable` 为（限定）基数为 1，避免一要素多域歧义。
3. **值域/定义域闭合**：所有对象属性定义域/值域均落在 §1 类体系内，无悬空 IRI。
4. **SWRL ↔ Restriction 一致**：R1 读 `marketAttractiveness/competitivePosition`（受 §4 「至多 1」约束，保证读值唯一；缺轴时规则不匹配、不推断，不臆造）；R3 读 `isMeasurable/isRelevant/hasTimeframe`（与 §4 约束一致）。
5. **数值类型**：评分/阈值一律 `xsd:decimal`/`xsd:integer`，SWRL 用 `swrlb:greaterThan/lessThan` 数值比较（★12，非字符串比较）。

---

## 7. ABox 典型个体设计（落地于第 6 步）

| 个体 | 类型 | 关键断言 | 说明 / 出处 |
|---|---|---|---|
| `IT_SPACE` | `core#ITSpace` | `domainCode="IT_SPACE"` | 洞察域（闸门①） |
| `CLOUD_SPACE` | `core#CloudSpace` | `domainCode="CLOUD_SPACE"` | 洞察域（闸门①） |
| `COMBINED_SPACE` | `core#CombinedSpace` | `domainCode="COMBINED"` | 联合域（闸门①） |
| `Trend_DoubleCarbon` | `env#IndustryTrend` | `indicatesValueTransfer → VT_NewPowerSystem`；`belongsToDomain → ITSpace` | 「双碳」政策趋势（**S1/S2**） |
| `Trend_NewPowerSystem` | `env#IndustryTrend` | `belongsToDomain → CLOUD_SPACE` | 新型电力系统（**S3/S4**） |
| `VT_NewPowerSystem` | `env#ValueTransfer` | — | 价值转移方向（**S4**） |
| `Market_NewEnergyIT` | `env#Market` | `marketOf → MC_1` | 新能源 IT 市场（**S5**） |
| `MC_1` | `env#MarketCustomer` | `targetsMarket → Market_NewEnergyIT` | 市场与客户聚合（闸门②） |
| `Customer_PowerGen` | `env#Customer` | `hasPainPoint → Pain_DataIsland`；`hasNeed → Need_CloudPlatform` | 新能源发电企业（**S5**） |
| `Pain_DataIsland` | `env#CustomerPainPoint` | `painSeverity=4` | 数据孤岛痛点（量纲【待考】） |
| `Need_CloudPlatform` | `env#CustomerNeed` | — | 能源云/算力需求（**S5**） |
| `Comp_1` | `env#Competitor` | `competitorIn → CL_1` | 竞争对手（**【待考】不指名**） |
| `CL_1` | `env#CompetitiveLandscape` | `involvesCompetitor → Comp_1` | 竞争格局（用户输入） |
| `Self_1` | `env#SelfAssessment` | `hasCapability → Cap_HuaweiICT` | 自身评估（**S6**） |
| `Cap_HuaweiICT` | `env#Capability` | `capabilityScore=4` | 华为 ICT 能力（**S6**，量纲【待考】） |
| `Opp_SmartEnergyCloud` | `env#Opportunity` | `marketAttractiveness=4.5`；`competitivePosition=4.0`；`hasSpanQuadrant → Q_HighHigh`；`belongsToDomain → CLOUD_SPACE` | 机会点示例（触发 R1 → HIGH） |
| `Q_HighHigh` | `env#SPANQuadrant` | — | SPAN 象限（用户输入） |
| `CP_TechnologyLead` | `decision#StrategicControlPoint` | `hasControlPointType → Type_TechnologyLead`；`addressesOpportunity → Opp_SmartEnergyCloud` | 战略控制点（闸门①） |
| `Type_TechnologyLead` | `decision#ControlPointType` | `cpTypeCode="TECH_LEAD"` | 控制点类型（闸门①） |
| `Obj_1` | `decision#Objective` | `isMeasurable=true`；`isRelevant=true`；`hasTimeframe="2028"`；`hasMetric="云业务行业份额"` | 目标（SMART；数值【待考】） |
| `Strat_1` | `decision#MarketStrategy` | `strategyOf → Obj_1` | 策略（用户输入） |

> 上述个体均为**典型数据**（可驱动推理与测试）；具体数值除公开来源（S1–S6）外均【待考】。

---

## 8. OBDA / SKOS 预留

- **OBDA（第 11 步）**：`env#Opportunity` → 表 `neinsight_opportunity`（列：name/domain_code/market_attractiveness/competitive_position/span_quadrant_ref + 回写列 priority/flagged_for_alert）；`decision#Objective` → 表 `neinsight_objective`（列：metric/target_value/timeframe/is_measurable/is_relevant + 回写列 smart_compliant）。映射经 Ontop OBDA 以 SPARQL 读写。
- **SKOS（第 13 步）**：为「行业趋势 / 市场 / 客户 / 竞争 / 自身 / 机会 / 战略控制点 / 目标 / 策略」等核心概念建立 `skos:prefLabel`(@zh/@en) 与 `skos:altLabel`（如 机会点↔Opportunity↔战略机会点），供 `OntologyLabelMatcher`/`SkosSynonymReader` 使用；术语须与本体验 IRI 一一对应。

> **【实现期落地 · 第 12 步记录】** ①将典型 ABox **由 21 个体扩展至 44 个体**（`neinsight-abox.owl`），覆盖「五看三定」全场景：新增 SPAN 象限 `Q_LowLow`；IT 趋势 `Trend_ITInfra`(+`MF_Sovereignty`/`VT_EdgeToCloud`)、云趋势 `Trend_CloudNative`(+`MF_AIDemand`/`VT_AICloud`)；市场 `Market_SmartGrid`、客户 `Customer_GridOperator`(+`Pain_GridSilo` severity=3 / `Need_UnifiedOAM`)；竞争 `CL_2`+`Comp_2`；自身 `Self_2`(+`Cap_Delivery` score=3 / `SB_Talent`)；机会点 `Opp_ITLegacy`(IT,2.0/1.5)、`Opp_CloudDataPlatform`(云,2.8/2.6)（均落 `Q_LowLow`）；三定 `Type_CostAdvantage`/`CP_CostAdvantage`/`Obj_2`/`Strat_2`(ProductStrategy→Obj_2)/`Strat_3`(OrgStrategy→Obj_1)。②扩展**不破坏 TBox 语义与约束**：`Opportunity` 仍恰好 1 象限、两轴至多 1；`Customer` ≥1 痛点；`ControlPoint` 恰好 1 类型且 ≥1 机会点；`Objective` 恰好 1 `isMeasurable` 且 ≥1 `metric`；`Strategy` ≥1 所属目标；新增 `Objective`(Obj_2) 均满足 R3 前置（`isMeasurable`/`isRelevant`/`hasTimeframe`）→ SMART 合规。③库侧同步：DDL 新增两表 `neinsight_macro_factor`（`MacroFactor` ⊂ `IndustryTrend`，持有 `trend_ref`）、`neinsight_short_board`（`ShortBoard` ⊂ `SelfAssessment`，持有 `assessment_ref`），沿用「多侧表持父引用」模式；`data.sql` 重建为 44 个体断言；OBDA 新增 `macro_factor_node`/`trend_has_macro_factor`/`short_board_node`/`assessment_has_short_board` 四条映射。④校验：`check_owl_step10.py`（类 29/对象属性 29/数据属性 16/SWRL 5，individuals 44，imports 无环，7 文件 XML 合法）、`check_db_obda.py`（OBDA↔TBox、DDL↔data.sql **0 issue**）。⑤`EnvPrepare createdb` 重建 `neinsightdb` 成功（17 业务表 + 2 新增表 + 1 关系表 + `neinsight_domain`；各表行数 41 = 个体在业务表分布，合计 44 个体）。⑥`ontop endpoint` 重启成功，SPARQL 可查 **279 三元组**（第 11 步为 130），`MacroFactor`(2)/`ShortBoard`(1)/`hasMacroFactor`(2)/`hasShortBoard`(1) 及 3 个机会点两轴均正确。⑦纯 Openllet 合规测试 `NEInsightOntologyComplianceTest` **9/9 通过**（P-1 一致性已覆盖扩展后全 ABox）。详见《NEInsight应用-测试设计文档》§6.5 回归记录。

> **【实现期落地 · 第 11 步记录】** ①产出 `ontology/database/{createdb_tables.ddl, data.sql, neinsight.obda, neinsight.properties}`；库名 `neinsightdb`。②表结构按 ABox 概念族归并：**17 张业务表 + 1 张关系表**（`neinsight_domain/domain_element/trend/value_transfer/market/customer/customer_pain/customer_need/competition/self_assessment/capability/opportunity/span_quadrant/control_point/control_point_type/objective/strategy`），`name|*_ref` 列值=个体本地名，OBDA 以 IRI 模板 `http://example.org/neinsight/abox#{...}` 还原个体、类模板 `.../{module}#{cls}` 还原类（对齐 `neinsight-abox.owl` 的 21 个个体断言）。③`data.sql` **只灌断言事实**（21 个体 + 2 条 `hasElement` 关系），**不灌** SWRL 派生值（priority/flagged/smartCompliant 建列留 NULL，供 `summarize-insight` 回写），保证 `.owl` 与库两侧一致。④`application.yaml` 增补 `ontology.obda-path` / `obda-properties-path`（加载复用 `OntologyService`，勿自建 manager，★21）。⑤`EnvPrepare createdb` 已建库灌数成功（各表行数 = ABox 个体分布，合计 21 个体）。⑥`ontop endpoint`（`--properties ... -m neinsight.obda -t neinsight-all.owl --xml-catalog catalog-v001.xml`）启动成功，SPARQL `http://localhost:8080/sparql` 可查：三元组 130 条、个体/中文 label/对象关系均正确。**已知引擎行为**：`xsd:decimal` 属性（market_attractiveness/competitive_position）Ontop 会同时产出「词法值」与「规范值」两个 RDF 词项（如 `"4.5"` 与 `"4.50"`，同值异词法），系 Ontop 对 decimal 的内建规范化（与 reasoning 开关无关），不影响 IRI 一致性；消费端按数值比较即可。

---

## 9. 待业务确认项（闸门③）

| # | 问题 | 业务结论 |
|---|---|---|
| 1 | 类体系（§1）是否认可 | 认可 |
| 2 | 对象属性/互逆对（§2）是否认可 | 认可 |
| 3 | 数据属性与数值类型（§3）是否认可 | 认可 |
| 4 | 约束（§4）是否认可（尤其基数=1 的唯一性） | 认可 |
| 5 | SWRL 规则（§5）是否认可（阈值【待考】） | 认可 |
| 6 | ABox 典型个体（§7）是否认可（竞品【待考】不指名） | 认可 |

---

## 闸门③留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-12 | v1 | 用户 | 六项（§1–§7）全部认可，一致通过，闸门③定稿。无修改点；阈值等【待考】项保持占位、不进本体。确认后进入第 5 步（生成 BPMN，含 UI）。 |
