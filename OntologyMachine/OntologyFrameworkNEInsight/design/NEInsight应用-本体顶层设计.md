# 新能源行业 IT 空间与云空间洞察（NEInsight）— 本体顶层设计

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkNEInsight / NEInsight |
| 文档日期 | 2026-10-12 |
| 关联顶层设计 | 《NEInsight应用-顶层设计》 |
| 关联流程 | 《NEInsight应用-流程设计》（定稿 v1 / 闸门①） |
| 本体资产（规划） | `ontology/neinsight-all.owl`、后续 `neinsight-*.owl`、`catalog-v001.xml` |
| 推理器 | Openllet（经 OpenlletResolver） |

> **本文件为第 3 步产出，即闸门②待确认文件。** 本步只勾出**主干框架**（根类与主要分支、命名空间、import 拓扑）；明细类/属性/约束/SWRL 留待第 4 步。
> 依据：`五看三定` 定稿流程设计（《NEInsight应用-流程设计》）；领域背景出处见《NEInsight应用-顶层设计》§3.2（S1–S6）。凡无公开依据处标 **【待考】**。

---

## 1. 概述与设计理念

本本体是 NEInsight 应用的**业务真相来源**，把「五看三定」方法论与新能源 IT/云空间领域知识，表达为机器可推理的 OWL 知识。

**设计理念（与 Example 同构）：**

1. **本体即约束**：五看三定的分析框架与判据（SPAN 定位、SMART 校验、战略控制点类型）由 TBox 表达，运行时经 Openllet 判定，**非硬编码于 Java**。
2. **TBox/ABox 分层**：术语/约束/规则（TBox）与洞察实例（ABox）分层；先单文件（第 6 步），再分离（第 9 步）、按业务拆分（第 10 步）。
3. **数据虚拟化**：洞察素材留库，经 Ontop OBDA 映射，以 SPARQL 视角读写（第 11 步）。
4. **规则驱动事件**：高优先级机会点/关键风险由 SWRL 推导，触发消息外发。
5. **概念对齐流程**：本体主干类与「洞察域扫描 + 五看 + 三定 + 汇总」环节一一对应（可追溯）。

---

## 2. 设计范围与边界

### 2.1 本体要表达的业务概念域（对齐流程设计 §3）

| 流程环节 | 本体概念域 | 出处 |
|---|---|---|
| 洞察域扫描 | 洞察域（IT 空间 / 云空间 / 联合） | 用户输入（闸门①） |
| 看行业/趋势 | 行业趋势（宏观 PEST、技术趋势、产业链、价值转移） | S1/S2/S3/S4 |
| 看市场/客户 | 市场、客户、客户痛点（Pains）、客户需求（Wants） | S5 |
| 看竞争 | 竞争格局、竞争对手（战略/优势/劣势/动向） | 用户输入 + 【待考】 |
| 看自己 | 自身评估、能力、短板、产业链定位 | S6 |
| 看机会 | 机会点、SPAN 象限、优先级 | 用户输入（SPAN 工具） |
| 定战略控制点 | 战略控制点 + 类型（技术领先/成本/品牌/份额/价值链控制） | 用户输入（闸门①） |
| 定目标 | 目标 + SMART 判据 | 用户输入（SMART） |
| 定策略 | 策略（组织/产品/市场） | 用户输入 |
| 汇总输出 | 洞察结论（聚合） | 用户输入 |

### 2.2 不在本体范围

- 财务测算/估值模型；
- 组织人事/绩效数据；
- 具体行业子赛道的工程/技术细节（如逆变器拓扑）；
- 【待考】的具体数值（市场规模、份额等）**不进本体**。

---

## 3. 本体资产组织（规划）

```
ontology/
├── neinsight-all.owl             ← 总入口（聚合 TBox + ABox）
├── neinsight-tbox.owl / neinsight-abox.owl   ← 第 9 步：分离（两个 owl）
├── neinsight-core.owl / -env.owl / -decision.owl / -rules.owl   ← 第 10 步：TBox 按业务拆分（tbox 改为聚合入口）
├── neinsight-terminology.ttl     ← 第 13 步：SKOS 术语
├── catalog-v001.xml              ← IRI → 本地文件映射
├── bpmn/neInsightProcess.bpmn    ← 第 5 步
└── database/*                    ← 第 11 步：库环境/OBDA
```

---

## 4. 命名空间与 import 架构

### 4.1 命名空间（规划）

| 模块 | 本体 IRI | 角色 |
|---|---|---|
| core | `http://example.org/neinsight/core#` | 根类、洞察域、通用要素 |
| env | `http://example.org/neinsight/env#` | 「五看」环境扫描概念 |
| decision | `http://example.org/neinsight/decision#` | 「三定」决策概念 |
| rules | `http://example.org/neinsight/rules#` | SWRL 规则（SPAN/SMART/外发触发） |
| abox | `http://example.org/neinsight/abox#` | 洞察实例（个体断言） |
| term | `http://example.org/neinsight/term/` | SKOS 词汇表（第 13 步） |

### 4.2 import 拓扑（第 9/10 步落地）

```
neinsight-all        (总入口 · http://example.org/neinsight/all)
  ├── imports neinsight-tbox   (http://example.org/neinsight/tbox)  ← TBox 聚合入口
  │     ├── imports neinsight-core      (http://example.org/neinsight/core)      ← 根类/洞察域/通用要素
  │     ├── imports neinsight-env       (http://example.org/neinsight/env)       ← 五看（imports core）
  │     ├── imports neinsight-decision  (http://example.org/neinsight/decision)  ← 三定（imports core、env）
  │     └── imports neinsight-rules     (http://example.org/neinsight/rules)     ← SWRL（imports core、env、decision）
  └── imports neinsight-abox   (http://example.org/neinsight/abox)  ← 典型 ABox：个体断言（imports tbox）
```
> import 拓扑闭合、无环；全部经 `catalog-v001.xml` 映射到本地文件（★：不硬编码绝对路径）。

> **【实现期修正 · 第 10 步落地记录】** 依第 10 步「TBox 较大时按业务域拆分、内聚低耦合、imports 无环」要求，把原单文件 TBox 拆为 **core / env / decision / rules** 四个子本体：core 自洽（根类、洞察域、`belongsToDomain/hasElement/domainCode`）；env 只依赖 core（五看概念与属性）；decision 依赖 core、env（`addressesOpportunity` 指向 `env#Opportunity`）；rules 依赖 core、env、decision（SWRL 规则跨域引用类与属性，SWRL 变量与 `swrl:Imp` 落于 rules）。`neinsight-tbox.owl` 改为**聚合入口**（仅 `owl:imports` 四子模块，语义与原 TBox 等价）。abox/all 仍只 `imports tbox`，故 Worker 与既有测试读取 `main-path`（all.owl）无需改动。校验：7 文件 XML 合法；imports 无环；类/对象属性/数据属性/SWRL 计数（29/29/16/5）与原 TBox 一致；第 8 步合规测试分离后仍 **9/9 通过**。

> **【实现期修正 · 第 9 步修正记录】** 规范 ★55 要求「ABox 不参与推理且量大（>200 个体）」时，把 `all.owl` 对 ABox 的 `owl:imports` **注释掉**、ABox 由应用侧独立加载。本应用 ABox 仅 21 个体且**参与 SWRL R1–R4 推理**，**触发条件未满足**，经用户确认**保留 `all.owl` 对 ABox 的 import**（Example 亦仅注释部分 ABox 模块、保留推理相关模块）。故：仍产出 `neinsight-tbox.owl` / `neinsight-abox.owl` 两个独立文件 + 更新 catalog；主入口 `neinsight-all.owl` 同时导入两者，Worker 与第 8 步测试读取 main-path 即得完整 TBox+ABox，无需改动。

---

## 5. 顶层类层级框架

```
InsightElement                         （根类：一切战略洞察要素）
├── InsightDomain                      （洞察域：IT 空间 / 云空间 / 联合）
├── EnvironmentScan                    （环境扫描 —— 五看）
│   ├── IndustryTrend                  （行业趋势：宏观/技术/产业链/价值转移）
│   ├── MarketCustomer                 （市场与客户）
│   │   ├── Market                     （市场）
│   │   ├── Customer                   （客户）
│   │   ├── CustomerPainPoint          （客户痛点 Pains）
│   │   └── CustomerNeed               （客户需求 Wants）
│   ├── CompetitiveLandscape           （竞争格局）
│   │   └── Competitor                 （竞争对手）
│   ├── SelfAssessment                 （自身评估）
│   │   ├── Capability                 （能力）
│   │   └── ShortBoard                 （短板）
│   └── Opportunity                    （机会点）
└── Decision                           （决策 —— 三定）
    ├── StrategicControlPoint          （战略控制点）
    ├── Objective                      （目标）
    └── Strategy                       （策略）
```

> 本步只勾出**主干框架**（根类与主要分支）；明细类、对象/数据属性、约束（`owl:Restriction`）、SWRL 规则留待第 4 步。

---

## 6. TBox/ABox 分层策略

| 层 | 内容 | 落点 |
|---|---|---|
| TBox | 类 / 对象属性 / 数据属性 / `owl:Restriction` / SWRL 规则 | 第 6 步单文件，第 9 步分离，第 10 步按 core/env/decision/rules 拆分 |
| ABox | 洞察实例（各域趋势、客户、竞品、机会点、控制点、目标、策略的个体断言） | 第 6 步含典型数据，第 9 步独立成文件，第 11/12 步入库 / 扩全集 |

---

## 7. 待业务确认项（闸门②）

| # | 问题 | 业务结论 |
|---|---|---|
| 1 | 根类与主干分支是否认可（`InsightElement` 下设 EnvironmentScan / Decision） | 待定 |
| 2 | 命名空间是否采用 `http://example.org/neinsight/...` | 待定 |
| 3 | 「五看」→ env、「三定」→ decision 的模块划分是否认可 | 待定 |
| 4 | 「联合」洞察域如何在本体表达（单个体 / 组合） | 待定 |
| 5 | 客户痛点/需求是否独立成类（vs 作为属性值） | 待定 |

---

## 闸门②留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-12 | 确认 v1 | 用户（闸门②确认） | 认可根类 InsightElement 与五看/三定主干；命名空间 `http://example.org/neinsight/...`；五看→env、三定→decision；痛点/需求独立成类；「联合」域用组合个体表达 |
