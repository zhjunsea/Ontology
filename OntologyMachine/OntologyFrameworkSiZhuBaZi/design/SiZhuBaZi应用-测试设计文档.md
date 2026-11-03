# SiZhuBaZi应用 — 测试设计文档

> **用法**：本文件产出第 8 步。**闸门⑤**：业务人员/负责人确认本文档后方可固化测试用例（写入 `src/test`）。
> **基线**：`OntologyFrameworkExample/design` 测试用例形态；同类术数形态 `OntologyFrameworkPlumBlossom/design/PlumBlossom应用-测试设计文档.md` 与 `OntologyFrameworkPlumBlossom/src/test/java/.../AllPlumBlossomSuiteTest.java`。
> **上游**：第 6 步单文件本体 `ontology/sizhubazi-all.owl`；第 9 步将 TBox/ABox 分离为两个 owl；第 14 步执行词汇表与全流程测试。

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkSiZhuBaZi / SiZhuBaZi（四柱命理 / 八字推命） |
| 文档版本 | **v1.1（含第 8 步缺陷修正留痕，见 §10）** |
| 生成日期 | 2026-10-29 |
| 关联本体 | `ontology/sizhubazi-all.owl`（第 9 步后 `sizhubazi-tbox.owl` / 子本体 / `sizhubazi-abox.owl`）；术语表 `ontology/sizhubazi-terminology.ttl`（第 13 步） |
| 关联流程 | 《SiZhuBaZi应用-流程设计》；`ontology/bpmn/sizhubaziProcess.bpmn`（`SiZhuBaZiProcess`），Camunda 8 / Zeebe |
| 测试代码位置 | `src/test/java/com/ocean/ontologyframework/sizhubazi/`（+ `validation` 子包） |
| 测试资源位置 | `src/test/resources/`（如需） |

> 本文档所有方法名、输入、断言**逐条取自 BPMN 契约（§4）与本体核心设计（R1~R24）**，未作发明。

---

## 1. 目的与范围

**目的**：明确 SiZhuBaZi 应用的质量保证方式——测什么、怎么测、用什么数据、判定依据、如何执行。

**范围**：
- 覆盖 BPMN 流程端到端编排（21 ServiceTask 主干顺序、G1/G2/G3 网关分支、无 incident）；
- 覆盖本体合规（可解析 / 一致性 / 枚举型个体全量 / 代表性个体 / Restriction 与 SWRL 结构）；
- 覆盖 SWRL 告警标志与 RabbitMQ 消息外发（大凶 / 寿元关 / 重大疾病，无数值阈值）；
- 覆盖 SKOS 术语结构 + OWL 交叉校验（第 13/14 步，依赖 `sizhubazi-terminology.ttl`）。

**不含**：历法换算的**高精度**天文校核（以节气表为准，程序近似）；《穷通宝鉴》调候条文（**已落地**，见节点 9，`tiaohou_clause` 120 条）。

## 2. 被测系统概述

### 2.1 业务链路
```
Start → 节点1 输入解析 → G1 模式 ─┬(单命)→ 节点2 排四柱 → 节点3~14 逐环 → G2 对象类型 ─┬(女命)→ W ┐
                                 │                                                 ├(小儿)→ C ┤→ 汇聚 → 节点15 劝诫
                                 └(合婚)→ M1 合婚 ──────────────────────────────────┘
→ 汇合J → 节点16 汇总(写 alertTriggered) → G3 级别 ─┬(触发)→ A 外发 ┐→ 汇聚 → 节点17 落库返回 → End
                                                  └(不触发)────────┘
```

### 2.2 BPMN 元素 ID 与 jobType
| 环节 | 元素 ID | jobType / 类型 |
|---|---|---|
| 1 输入解析与时辰校正 | `Task_ParseInput` | `parseBirthInput` |
| 2 排四柱 | `Task_CastPillars` | `castFourPillars` |
| 3 纳音·藏干·日柱特性 | `Task_NayinHiddenStems` | `analyzeNayinHiddenStems` |
| 4 干支作用关系 | `Task_GanzhiRelations` | `analyzeGanzhiRelations` |
| 5 十神与六亲 | `Task_TenGodsKin` | `assignTenGodsKin` |
| 6 神煞排布 | `Task_Deities` | `arrangeDeities` |
| 7 日主旺衰与十二宫 | `Task_Strength` | `assessDayMasterStrength` |
| 8 格局判定 | `Task_Pattern` | `determinePattern` |
| 9 取用 | `Task_YongShen` | `selectYongShen` |
| 10 命局层次 | `Task_ChartLevel` | `judgeChartLevel` |
| 11 大运流年 | `Task_FortuneCycles` | `analyzeFortuneCycles` |
| 12 应期与运限 | `Task_Timing` | `inferTimingAndStages` |
| 13 岁运综合 | `Task_Synthesize` | `synthesizeFortuneChart` |
| 14 分类断语 | `Task_CategoryVerdicts` | `generateCategoryVerdicts` |
| M1 合婚 | `Task_MarriageMatch` | `analyzeMarriageMatch`（条件·G1 合婚） |
| W 女命专论 | `Task_FemaleChart` | `analyzeFemaleChart`（条件·G2 女命） |
| C 小儿关杀 | `Task_ChildChart` | `analyzeChildChart`（条件·G2 小儿） |
| 15 修身处世劝诫 | `Task_MoralAdvice` | `generateMoralAdvice` |
| 16 综合结论汇总 | `Task_Aggregate` | `aggregateReading` |
| A 外发告警 | `Task_SendAlert` | `sendAlert`（条件·G3 触发） |
| 17 结果落库与返回 | `Task_PersistReturn` | `persistAndReturn` |
| 模式网关 | `Gateway_Mode` | 排他（`inputMode = 合婚/单命`） |
| 对象网关 | `Gateway_Subject` | 排他（`subjectType = 女命/小儿/男命`） |
| 级别网关 | `Gateway_Level` | 排他（`alertTriggered = true/false`） |
| 汇合点 | `Gateway_MergeJ` / `Gateway_SubjectMerge` / `Gateway_LevelMerge` | 排他汇聚 |

### 2.3 BPMN ↔ JobWorker 绑定
`SiZhuBaZiOntologyJobWorker`（`@Component @Profile("SiZhuBaZiBPMN") extends OntologyWorkerSupport`）声明 **21** 个 `@JobWorker`（`autoComplete = false`，手动 `completeResult`）：`parseBirthInput / castFourPillars / analyzeNayinHiddenStems / analyzeGanzhiRelations / assignTenGodsKin / arrangeDeities / assessDayMasterStrength / determinePattern / selectYongShen / judgeChartLevel / analyzeFortuneCycles / inferTimingAndStages / synthesizeFortuneChart / generateCategoryVerdicts / analyzeMarriageMatch / analyzeFemaleChart / analyzeChildChart / generateMoralAdvice / aggregateReading / sendAlert / persistAndReturn`。**BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」。**

### 2.4 关键类
`SiZhuBaZiApplication`（`@SpringBootApplication`，激活 `SiZhuBaZiBPMN`）、`SiZhuBaZiOntologyJobWorker`（单类 21 方法）、`web/SiZhuBaZiController`（`/api/sizhubazi/*`）；测试侧 `SiZhuBaZiOntologyJobWorker` 生产实例（**不写桩**）。

## 3. 测试分层与策略

| 层级 | 测试类 | 依赖引擎 | 是否需环境 | 关注点 |
|---|---|---|---|---|
| 本体合规（纯 OWL+Openllet） | `validation/SiZhuBaZiOntologyComplianceTest`（6 项） | 无 | 否 | 解析/一致性（TBox∪ABox）/TBox 独立一致性/域知识完整性/Restriction+SWRL 结构 |
| 端到端（内存引擎） | `SiZhuBaZiBpmnInMemoryTest`（9 项） | `EngineFactory`（zeebe-process-test） | 需 RabbitMQ | BPMN 编排、节点顺序、网关分支、无 incident、边界/负向 |
| 端到端（真实引擎） | `SiZhuBaZiBpmnRealEngineTest`（1 项） | 真实 Zeebe `localhost:26500` | 需 Camunda+RabbitMQ | 真实部署与消费 |
| SWRL+MQ | `SiZhuBaZiSwrlRabbitMqTest`（3 项） | 无引擎 | 需 RabbitMQ | 告警标志命中→消息投递、告警拓扑与载荷形态、非告警不投递 |
| 本体术语（纯 Jena） | `validation/SiZhuBaZiSkosTerminologyTest`（第 13/14 步） | 无 | 否 | SKOS 结构 + OWL 交叉校验 |

**设计原则**：① 分层解耦可离线回归（本体合规用例排前）；② 端到端由**生产 Worker 驱动**（不写桩）；③ 本体为唯一裁判（合规用例直读 owl）；④ 用例间以流程变量快照/独立实例隔离。

## 4. 测试环境与配置

- 配置源：`src/main/resources/application.yaml`（同一 yaml 驱动 Spring 亦被 EnvPrepare 读取）。
- 测试激活 Profile：`SiZhuBaZiBPMN`。
- 关键键：`ontology.main-path`=`ontology/sizhubazi-all.owl`、`ontology.abox-path`（第 9 步）、`ontology.obda-path`/`ontology.obda-properties-path`（第 11 步）、`ontology.bpmn-path`=`ontology/bpmn/sizhubaziProcess.bpmn`；`camunda.client.grpc-address=localhost:26500`；`server.port=9088`。

### 4.1 外部依赖端点
| 依赖 | 端点 / 载体 | 使用者 |
|---|---|---|
| Ontop SPARQL | `http://localhost:8080/sparql`（第 11 步） | 集成测试 / worker |
| MySQL | `jdbc:mysql://localhost:3306/sizhubazidb`（第 11 步） | OBDA 读写 |
| RabbitMQ | AMQP（exchange `sizhubazi.alert.exchange` / routing-key `fortune.alert.critical`） | 消息用例 |
| Zeebe | 内存引擎 / 真实 `localhost:26500` | 端到端 |

### 4.2 真实环境前置
1. `start-env.cmd`（EnvPrepare `start --config src/main/resources/application.yaml`）；2. 启动 `SiZhuBaZiApplication`（激活 `SiZhuBaZiBPMN`）订阅作业；3. （第 11 步）初始化 `sizhubazidb` 库表；4. Zeebe Gateway 可达。

## 5. 测试数据设计

| 类别 | 来源 | 说明 |
|---|---|---|
| 单命样例 | 程序构造（`birth`） | 如 `1990-05-15 08:30`、男命、`calendar=公历`、`subjectType=男命`、`granularity=年` |
| 女命样例 | 程序构造 | `subjectType=女命` |
| 小儿样例 | 程序构造 | `subjectType=小儿` |
| 合婚样例 | 程序构造 | `inputMode=合婚` + `partnerBirth`（双方生辰） |
| 边界样例 | 程序构造 | 缺 `birth`；缺 `partnerBirth`（合婚）；早晚子时（23:30）；农历输入（含闰月，实算换算）；农历年份越界（<1900，标【待考】） |
| 本体数据 | `ontology/sizhubazi-all.owl`（第 6 步） | 枚举型个体全量 + 代表性个体 |

> 变量选取原则：避免写入违反本体约束的 ABox 数据（有据可依，★8/10）。

## 6. 测试用例清单

> 编号约定：**P** 正向 / **B** 边界 / **N** 负向。用例须**驱动 BPMN** 走完拓扑，尤其覆盖边界场景。

### 6.1 本体合规 `validation/SiZhuBaZiOntologyComplianceTest`（6 项）
> 输入口径（第 9 步后）：ABox 依铁律 11 不被主入口 `sizhubazi-all.owl` import，故本类**显式加载 `sizhubazi-abox.owl`**（与应用侧 `ontology.abox-path` 独立加载一致），对两文件 `importsClosure()` 的**并集**断言。P-1 对 `aboxOntology` 推理（其 imports 闭包 = TBox∪ABox，等价分离前单文件一致性）；P-5 单独校验 TBox 独立可推理。

| ID | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|
| P-1 | `ontologyLoadsAndIsConsistent` | `sizhubazi-abox.owl`（闭包含 TBox） | 可解析且 `Openllet isConsistent`（TBox∪ABox 合并视图） |
| P-2 | `enumeratedIndividualsComplete` | 个体签名（并集） | 阴阳 2 / 五行 5 / 天干 10 / 地支 12 / 十神 10 / 层次 4 / 告警 3 / 月建 12 齐备 |
| P-3 | `representativeIndividualsPresent` | 个体签名（并集） | 六十甲子/纳音/藏干/节气/神煞/格局代表性个体齐备 |
| P-4 | `restrictionsAndSwrlRulesPresent` | imports 闭包（并集） | `SubClassOf`（含 `owl:Restriction`）与 `SWRL_RULE`（R1~R24）计数 > 0 |
| P-5 | `tboxStandaloneConsistentWithoutAbox` | `sizhubazi-all.owl` | 主入口闭包**不含 ABox**（铁律 11）且 TBox **独立可推理**、一致 |
| B-1 | `fourPillarsCardinalityPresent` | 闭包 | `chart:FourPillars` 四柱各 `owl:cardinality 1` 约束存在 |

### 6.2 端到端（内存引擎）`SiZhuBaZiBpmnInMemoryTest`（9 项）
| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `t_single_male_full_chain` | 单命·男命 | 流程 `COMPLETED`、无 incident、`reading` 非空、`fourPillars` 非空 |
| P-2 | `t_single_female_subject_branch` | 单命·女命 | 走 G2「女命」分支，`femaleVerdict` 非空 |
| P-3 | `t_marriage_mode_branch` | 合婚（G1） | 走 `Task_MarriageMatch`，`marriageConclusion` 非空 |
| B-1 | `t_child_subject_branch` | 单命·小儿 | 走 G2「小儿」分支，`childVerdict` 非空 |
| B-2 | `t_late_zi_pillar_rolls_next_day` | 23:30 生辰 | 日柱按子初换日进位（与次日 00:30 同柱） |
| B-3 | `t_lunar_input_not_blocked` | 农历输入（1990 五月十五） | 换算为公历 1990-06-07、`LUNAR_UNCONVERTED=false`、流程不阻断 |
| B-4 | `t_lunar_out_of_range_not_blocked` | 农历年份越界（1899） | 无法换算 → 标【待考】、按原值登记、流程不阻断 |
| N-1 | `t_missing_birth_raises_incident` | 缺 `birth` | `parseBirthInput` 抛错 → incident，不入主干 |
| N-2 | `t_marriage_without_partner_raises_incident` | 合婚缺 `partnerBirth` | `analyzeMarriageMatch` 抛错 → incident |

### 6.3 端到端（真实引擎）`SiZhuBaZiBpmnRealEngineTest`（1 项）
| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `t_real_engine_single_male_end_to_end` | 真实 Zeebe | 部署 `SiZhuBaZiProcess`、启动实例、生产 Worker 消费完毕 |

### 6.4 SWRL+MQ `SiZhuBaZiSwrlRabbitMqTest`（3 项）
| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `t_alert_topology_declared_and_delivered` | 告警拓扑与投递 | exchange `sizhubazi.alert.exchange` / routing-key `fortune.alert.critical` / queue `sizhubaziAlertQueue` 声明成功且消息可达 |
| P-2 | `t_send_alert_payload_shape` | `sendAlert` 载荷形态 | 消息体含告警类型/命局关键字段，结构完整 |
| N-1 | `t_non_alert_routing_key_not_delivered` | 非告警路由键 | 未命中告警时不投递至告警队列 |

### 6.5 词汇表测试（第 13/14 步）
| ID | 方法 | 校验点 | 预期 |
|---|---|---|---|
| P-1 | `skosTerminologyStructure` | `sizhubazi-terminology.ttl` | 含 `skos:Concept` 且与 OWL 类/个体交叉一致 |
| P-2 | `synonymCoverage` | 异名（偏官＝七杀 等） | 术语表覆盖六书异名（★S4） |

## 7. 覆盖矩阵

### 7.1 流程节点覆盖
| BPMN 节点 id | 覆盖用例 | 说明 |
|---|---|---|
| `Task_ParseInput` | P-1/B-2（内存） | 含早晚子时边界；农历换算 B-3（越界回退 B-4）；缺参负向 N-1 |
| `Task_CastPillars` | P-1（内存） | 四柱非空 |
| `Task_NayinHiddenStems`~`Task_CategoryVerdicts`（3~14） | P-1（内存） | 主干逐环执行、无 incident |
| `Task_MarriageMatch` | P-3（内存） | G1 合婚分支 |
| `Task_FemaleChart` | P-2（内存） | G2 女命分支 |
| `Task_ChildChart` | B-1（内存） | G2 小儿分支 |
| `Task_Aggregate`/`Task_SendAlert` | P-1/P-2/N-1（SWRL+MQ） | 告警标志 + 外发/非外发 |
| `Task_PersistReturn` | P-1（内存） | `caseRecord` 非空 |

### 7.2 本体约束覆盖
| 约束（`owl:Restriction` / SWRL） | 覆盖用例 |
|---|---|
| `chart:FourPillars` 四柱 `cardinality 1` | 本体合规 B-1 |
| R1~R24（SWRL 结构） | 本体合规 P-4 |
| 枚举型个体全量（阴阳/五行/干支/十神/层次/告警/月建） | 本体合规 P-2 |

### 7.3 边界场景清单
| # | 边界/负向场景 | 用例 | 预期行为 |
|---|---|---|---|
| 1 | 缺 `birth` | 内存 N-1 | Worker 抛错 → incident，不入主干 |
| 2 | 合婚缺 `partnerBirth` | 内存 N-2 | `analyzeMarriageMatch` 抛错 |
| 3 | 早晚子时（23:30） | 内存 B-2 | 日柱进位次日 |
| 4 | 农历输入（1990 五月十五） | 内存 B-3 | 换算为公历 1990-06-07、`LUNAR_UNCONVERTED=false`、流程不阻断 |
| 4b | 农历年份越界（1899） | 内存 B-4 | 标【待考】、按原值登记、流程不阻断 |
| 5 | OBDA 未就绪（第 11 步前） | P-1（内存） | 查表降级、流程不阻断 |

## 8. 执行方式

- 全套入口：`AllSiZhuBaZiSuiteTest`（`@Suite` + `@SelectClasses`，聚合上述类；纯离线用例排前）。
- 命令：`./mvnw -pl OntologyFrameworkSiZhuBaZi -am test -Dtest=AllSiZhuBaZiSuiteTest`（或 reactor 全量）。
- 环境依赖项须在文档中显式标注（RabbitMQ / Camunda）。

## 9. 待确认项

| # | 问题 | 结论 |
|---|---|---|
| 1 | 5 类测试用例（合规 5 / 内存 9 / 真实引擎 1 / SWRL-MQ 3 / 词汇表 2）是否齐备？ | 通过（闸门⑤，见下） |
| 2 | 边界用例（早晚子时 / 农历换算含越界回退 / 缺参负向）取舍是否合宜？ | 待闸门⑤确认 |
| 3 | 词汇表测试并入第 13/14 步，是否认可？ | 待闸门⑤确认 |

---

## 闸门⑤留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-29 | v1（初稿） | 码道 | **待业务人员/负责人确认（闸门⑤）**——确认后方可固化测试用例（写入 `src/test`）。 |
| 2026-10-29 | v1 | 业务人员/负责人 | **通过**——认可 §6 用例清单与 §7 覆盖矩阵；据以固化测试用例并跑通 `AllSiZhuBaZiSuiteTest`。 |

---

## 10. 第 8 步缺陷修正留痕

> 固化测试用例并跑通回归过程中发现 2 处缺陷，均**先定位根因、经负责人裁定修复方案后**修正。修正均属「程序/本体的可判定性缺陷」，非领域算法变更。

### 10.1 缺陷 D1：BPMN 开始事件常量输出映射覆盖入参（致分支不可达）
| 项 | 内容 |
|---|---|
| 现象 | `Gateway_Mode`（G1 合婚）与 `Gateway_Subject`（G2 女命/小儿）分支**永不可达** |
| 根因 | `sizhubaziProcess.bpmn` 的 `Start_01` 以 4 条常量 `zeebe:output` 映射强制写 `inputMode="单命"`/`subjectType="男命"`/`alertTriggered=false`/`granularity="年"`；按 Camunda/Zeebe 语义，**开始事件 output mapping 会覆盖创建实例时传入的同名变量** |
| 修正 | **移除** `Start_01` 的 4 条常量输出映射；默认值改由 Worker `orDefault` 兜底（`parseBirthInput` 默认 单命/男命/年；`aggregateReading` 在 G3 前写 `alertTriggered`） |
| 裁定 | 负责人：采用「移除开始事件输出映射」方案（推荐） |
| 验证 | 内存引擎用例 P-1/P-2/P-3/B-1 分支可达 |

### 10.2 缺陷 D2：本体 ABox 数据属性值携带语言标签致本体逻辑不一致
| 项 | 内容 |
|---|---|
| 现象 | `validation/SiZhuBaZiOntologyComplianceTest` P-1 失败：`Openllet isConsistent = false` |
| 定位 | 二分最小化 → 极小不一致集为**单条** `DataPropertyAssertion(tengod:tenGodName, 个体, "伤官"@zh)`；TBox 单独一致、无不可满足类 → 不一致源自 ABox 的**字面量类型** |
| 根因 | ABox 中**数据属性断言值**带 `xml:lang="zh"`（被解析为 `rdf:langString`），而对应数据属性声明 `rdfs:range xsd:string`；OWL 2 中 `rdf:langString ⊓ xsd:string = ∅` → 该类断言不可满足 |
| 基线对照 | `OntologyFrameworkExample/ontology/pizza-abox.owl` 的 `xml:lang="zh"` **仅用于 `rdfs:label`/`rdfs:comment` 注解**，数据属性取值从**不带语言标签**；PlumBlossom 同病，仅因分文件加载把该类三元组降级为注解而"侥幸"一致 |
| 修正（方案A） | 剥离 ABox 中 **~174 条数据属性断言** 及 **6 处 SWRL 字面量**（`男/阳/阴/顺/逆`）上的 `xml:lang="zh"`（改纯字面量 → `xsd:string`）；`rdfs:label`/`rdfs:comment` 注解**保留** `@zh`。语义无损，严格对齐 Example 基线 |
| 裁定 | 负责人：**方案A**（剥离数据属性值语言标签） |
| 验证 | `isConsistent = true`；`SiZhuBaZiOntologyComplianceTest` **5/5 通过**；`AllSiZhuBaZiSuiteTest` 全绿 |

### 10.3 回归结论
- `./mvnw -o -pl OntologyFrameworkSiZhuBaZi test -Dtest=AllSiZhuBaZiSuiteTest` → **BUILD SUCCESS**。
- 离线合规用例 **5/5 通过**；内存引擎 8 / 真实引擎 1 / SWRL-MQ 3 因 RabbitMQ·Camunda 未就绪，经 `ServiceProbe.assumeAllReachable` **正常跳过**（环境就绪后可实跑）。

---

## 11. 第 9 步变更留痕（TBox/ABox 分离对测试的影响）

> 第 9 步将单文件 `sizhubazi-all.owl` 拆为 `sizhubazi-tbox.owl` + `sizhubazi-abox.owl`，主入口改为聚合 TBox（ABox imports 依**铁律 11** 注释掉）。此举改变了本体合规测试的加载口径，遂**修改测试程序**（§3.2：测试问题优先改测试）。

| 项 | 内容 |
|---|---|
| 影响 | 原来 `SiZhuBaZiOntologyComplianceTest` 只加载 `sizhubazi-all.owl`，其 `importsClosure()` 曾含 ABox；分离后主入口闭包**仅 TBox**，P-2/P-3 枚举/代表性个体断言缺失（找 ABox 个体） |
| 修改 | 合规测试**显式加载 `sizhubazi-abox.owl`**（与应用侧 `ontology.abox-path` 独立加载一致），对两文件闭包**并集**断言；P-1 改为对 `aboxOntology` 推理（闭包 = TBox∪ABox）；**新增 P-5**：断言主入口闭包不含 ABox 且 TBox 独立可推理、一致 |
| 用例数 | 本体合规 **5 项 → 6 项**（新增 `tboxStandaloneConsistentWithoutAbox`） |
| 验证 | 拆分等价性：`tbox ∪ abox` 与原单文件**图同构**（rdflib `isomorphic=True`）；`SiZhuBaZiOntologyComplianceTest` **6/6 通过**；`AllSiZhuBaZiSuiteTest` **BUILD SUCCESS** |

---

## 12. 第 10 步变更留痕（TBox 拆分子本体对测试的影响）

> 第 10 步将 `sizhubazi-tbox.owl` 按业务域拆为 **12 子本体**，`sizhubazi-tbox.owl` 改聚合入口（显式 imports 12 子本体）。**测试用例数与断言口径不变**（合规用例经 `importsClosure()` 合并视图，自动覆盖新拓扑），无需改测试程序。

| 项 | 内容 |
|---|---|
| 影响 | 无（合规测试对 imports 闭包断言，子本体经 `tbox` 聚合入口自动纳入） |
| 用例数 | 本体合规仍 **6 项**（无变更） |
| 关键校验 | ① 子本体并集与原 TBox **图同构**（rdflib `isomorphic=True`）；② imports 单向无环（12 子本体逐一核对）；③ 解析无 `Entity not properly recognized` 告警（修正前有 7 条——跨域约束误置致 OWLAPI 无法识别，修正后与基线一致为 0） |
| 修正 | 跨域 `owl:Restriction` 按 `owl:onProperty` 所属域归属（7 条由 chart 改归 tengod/yongshen/fortune/reading），详见《本体顶层设计》§4.2 注 |
| 验证 | `SiZhuBaZiOntologyComplianceTest` **6/6 通过**；`AllSiZhuBaZiSuiteTest` **BUILD SUCCESS** |

---

## 13. 第 11 步变更留痕（典型 ABox 入库 / OBDA 对测试的影响）

> 第 11 步（闸门⑩决策：**使用数据库 + 按本体新建库表 + A 迁移口径**）将查表型历法个体自 `sizhubazi-abox.owl` 迁出至 MySQL（`sizhubazidb`，11 张表），DB 为唯一真源、经 Ontop OBDA 虚拟访问。合规测试据此调整离线断言口径。

| 项 | 内容 |
|---|---|
| 影响 | 迁出天干/地支/六十甲子/纳音/藏干/节气/月建 **7 类共 56 个体**；ABox 仅保留结构性/静态个体；P-2/P-3 不再离线断言这些个体存在 |
| 修改 | P-2/P-3 改为「断言保留个体齐备」+「文本级断言（`rdf:about`）迁出个体不再被声明」；**新增 P-6** `step11DatabaseArtifactsAndMigration`（DDL/data/obda/properties 齐备 + ★23 `.obda` 禁 `#` 注释 + ★防回归 `.obda` 禁 `<prefix:...>` 写法） |
| 用例数 | 本体合规 **6 项 → 7 项**（新增 P-6） |
| 缺陷修复 | **Ontop OBDA 前缀映射缺陷**：原 `.obda` 将前缀名写成 `<core:{name}>`，而 Ontop 原生映射把 `<prefix:...>` 当作「绝对 IRI（scheme=prefix）」、**不展开前缀**，导致 target 三元组被**静默丢弃**（映射 0 命中，endpoint 仅返回本体三元组、DB 数据不可见）。修正为**裸写前缀名**（`core:{name}`），DB 映射恢复命中 |
| 验证 | ① `ontop validate --xml-catalog ... -m ... -t ...` → `Validation completed`；② endpoint SPARQL 经 OBDA 取数：天干 10 / 地支 12 / 六十甲子 6 / 纳音 6 / 藏干 4 / 节气 6 / 月建 12，`hasYinYang`(10)/`hasFivePhase`(28)/`hasNaYin`(6)/`hasHiddenStem`(4) 链接目标均解析为实体 IRI（`core:Huo`/`core:Yang` 等）；③ `SiZhuBaZiOntologyComplianceTest` **7/7 通过**；④ `AllSiZhuBaZiSuiteTest` **BUILD SUCCESS**（离线用例 7 项全绿；MQ/BPMN 集成用例因本机无 RabbitMQ 依设计整体跳过，不影响本体侧回归） |

> **遗留观察（供后续参考）**：同类 `<prefix:...>` 写法亦存在于参考应用 `OntologyFrameworkPlumBlossom/ontology/database/plumBlossom.obda`（`<cal:{name}>` 等），其 OBDA 映射在当前 Ontop 版本下同样 0 命中；本应用已修正，其它应用如启用 Ontop 端点需同步修正（本次不越界改动）。

---

## 14. 第 12 步变更留痕（ABox 扩至全集 / 数据全集入库）

> 第 12 步（闸门⑪决策：**数据来源＝AI 依六书生成 + 客户校订；落地＝表格族入 DB、结构个体留 `abox.owl`**）将全集数据入库 MySQL（`sizhubazidb`），`data.sql` 由典型扩至全集；DDL/OBDA 映射不变。

| 项 | 内容 |
|---|---|
| 影响 | `data.sql` 由典型扩至**全集**：天干 10 / 地支 12 / 六十甲子 6→**60** / 纳音 6→**30** / 藏干 4→**28** / 节气 6→**24** / 月建 12 / 神煞起例 12→**95** / 十神映射 10→**100**；`case_record`/`alert_flag` 为运行态，全集不含历史数据（留空）。结构性 ABox 个体（阴阳/五行/十神名录/宫位/六亲/层次/告警标志/格局名录/神煞名录/长生十二宫）保留在 `sizhubazi-abox.owl`，未变 |
| 来源 | **AI 依六书推演生成 + 客户校订**：纳音（渊海子平卷之一·论六十花甲子纳音并注解；三命通会卷一·论纳音取象）、藏干（渊海子平·又地支藏遁歌）、节气（渊海子平·论节候歌）、神煞（三命通会卷二诸神煞篇）、十神（渊海子平卷之一·论日为主）；`nayin.nayin_nature` 吉凶三分**已落地**（依《三命通会》卷一·释六十甲子性质吉凶归算，吉 8/平 6/凶 16；原标【待考】已消除） |
| 修改 | **新增 P-7** `step12FullDataScriptComplete`（离线文本级：断言 `data.sql` 各族全集边界项 + 神煞起例 95 条 / 十神映射 100 条）；生成脚本 `HWCodeArtsDir/gen_sizhubazi_full_data.py`（十神/六十甲子/纳音配对由算法推导，避免手算误差） |
| 用例数 | 本体合规 **7 项 → 8 项**（新增 P-7） |
| 缺陷修正 | 生成脚本初版 `YY_OF_GAN`（天干阴阳）数组写错（乙丙丁戊己庚辛壬阴阳颠倒），会连带十神映射全部算错；已修正并重新生成（十天干阴阳、十神 100 条逐条复核无误） |
| 验证 | ① MySQL 灌入后各表行数：stem 10 / branch 12 / cycle 60 / nayin 30 / hidden 28 / term 24 / month 12 / deity 95 / tengod 100；② Ontop endpoint SPARQL 取数：`HeavenlyStem` 10 / `EarthlyBranch` 12 / `SexagenaryCycle` 60 / `NaYin` 30 / `HiddenStem` 28 / `SolarTerm` 24 / `MonthOrder` 12，对象属性 `hasStem`/`hasBranch`/`hasNaYin` 各 60、`hasHiddenStem` 28 均解析为实体 IRI；③ `SiZhuBaZiOntologyComplianceTest` **8/8 通过**；④ `AllSiZhuBaZiSuiteTest` **BUILD SUCCESS**（MQ/BPMN 集成用例因本机无 RabbitMQ 依设计跳过） |

---

## 15. 第 32 步变更留痕（农历换算实装 / 待考项落地对测试的影响）

> 客户「待考全部落地」指令后，农历→公历换算由「表未就绪标【待考】」改为**内置 1900–2100 农历数据表实算**（含闰月）；相应调整边界用例。

| 项 | 内容 |
|---|---|
| 影响 | 节点 1 `parseBirthInput` 对「农历」输入实算换算，仅年份越界（<1900/>2100）、该月无闰却标闰、格式异常时回退标【待考】并按原值登记（不臆造）；`chartBase` 的 `LUNAR_UNCONVERTED` 语义由「表未就绪」改为「回退未换算」 |
| 用例调整 | 内存 `B-3` 断言由「`LUNAR_UNCONVERTED=true`」改为「换算为公历 1990-06-07、`LUNAR_UNCONVERTED=false`、不阻断」；**新增 `B-4`**（农历年份越界 1899 → 标【待考】、按原值登记、不阻断）。内存用例 **8 项 → 9 项** |
| 新增单元测试 | `SiZhuBaZiLunarConvertTest`（**8 项**）：结构化/字符串农历、闰月（2020 闰四月/2023 闰二月）、边界年（1900/2100）、越界与非法闰月回退，全量经反射直调 `lunarToSolar` 校验 |
| 回归 | `mvn -o compile` EXIT=0；`SiZhuBaZiLunarConvertTest` 8/8；`SiZhuBaZiBpmnInMemoryTest` **11/11**（含 B-3/B-4） |

---

## 16. 第 33 步变更留痕（经典起例核验 + 套件并入离线用例 + 双跑幂等修复）

> 触发：客户「**按章节逐字逐句检查**」要求。新增一组**经典起例单元测试**，以可执行证据逐条核验六书起例；并将全部离线用例并入总入口套件；修复整模块 `mvn test` 下集成用例「同 JVM 双跑」失败。

| 项 | 内容 |
|---|---|
| 新增单元测试 | `SiZhuBaZiClassicRulesTest`（**9 项**，全离线、纯反射直调私有起例）：年柱立春分年、月柱五虎遁（含子月）、日柱 JDN 锚定（2000-01-01 戊午 / 2024-01-01 甲子 / 1949-10-01 甲子）、时柱五鼠遁（10 日干子时全表）、纳音**六十甲子全表 60 组**、天乙贵人（三命通会卷二口诀）、寄生十二宫长生（三命通会卷二阴阳生死）、命宫起例（三命通会卷二原例「甲子年三月戌时→丁卯宫」） |
| 套件更新 | `AllSiZhuBaZiSuiteTest` 并入 3 个离线类（`SiZhuBaZiClassicRulesTest` / `SiZhuBaZiSolarTermTest` / `SiZhuBaZiLunarConvertTest`），使「一个总入口」覆盖全部离线核验；离线用例排前、集成用例排后 |
| **缺陷修复** | 整模块 `mvn test` 时 `SiZhuBaZiBpmnInMemoryTest`（`@SpringBootTest` + 静态内存引擎）被 surefire 直接发现**与**套件 `@SelectClasses` 引用，**同 JVM 双跑**：第二次复用 Spring 缓存的旧 `ApplicationContext`（其 `DynamicPropertySource` 已随首次 `@AfterAll` 停掉引擎），`testClient` 指向已停引擎 → 部署失败（11 项报「应成功部署流程」）。修复：加 `@DirtiesContext(classMode = AFTER_CLASS)`，令每次执行重建上下文/引擎，双跑幂等 |
| 用例数 | 经典起例 **+9**；套件内联类 **5 → 8**（含新增类） |
| 验证 | ① `-Dtest=SiZhuBaZiClassicRulesTest` **9/9**；② 套件 `-Dtest=AllSiZhuBaZiSuiteTest` **52/52**；③ 整模块 `mvn test` **EXIT=0**，各测试类去重汇总 **56 项 0 失败 0 错误 0 跳过** |

---

## 17. 第 33 步（续）· 经典起例扩容（十神/藏干/三合神煞/六合冲害/五合/相刑/大运/禄刃）

> 承接「按章节逐字逐句检查」，将 `SiZhuBaZiClassicRulesTest` 由 9 项扩容至 **17 项**，把其余关键起例亦以可执行断言固化。

| 新增用例 | 断言要点 | 典籍依据 |
|---|---|---|
| `tenGodIndexTable` | 甲日主对十干依次 比劫食伤/偏正财/偏正官/偏正印；丙日主依次 偏正印/比劫食伤/偏正财/偏正官 | 渊海子平卷之二·各十神论 |
| `hiddenStemsTable` | 十二支藏干全表（子癸…亥壬甲）逐支核对 | 渊海子平·又地支藏遁歌；三命通会卷一·论人元司事 |
| `sanheAndShenSha` | 三合局分组（申子辰/寅午戌/巳酉丑/亥卯未）+ 驿马/桃花/华盖/将星四表逐组核对 | 三命通会卷二·驿马/咸池/将星华盖 |
| `liuheChongHai` | 六合（子丑…午未）/六冲（子午…巳亥）/六害（子未…酉戌）三表 | 三命通会卷二·支元六合/冲击/六害 |
| `ganWuHeHua` | 天干五合化气（甲己土 乙庚金 丙辛水 丁壬木 戊癸火），甲庚不合 | 三命通会·论十干合/化气 |
| `sanXing` | 寅巳申/丑戌未三刑、子卯相刑、辰午酉亥自刑；反例子丑/寅午不刑 | 三命通会卷二·论三刑 |
| `greatFortunesDirection` | 自月柱顺/逆行六十甲子（丙寅 → 顺 丁卯戊辰己巳 / 逆 乙丑甲子癸亥） | 三命通会·论大运（排运之法） |
| `luAndYangren` | 十干禄（甲寅…癸子）与羊刃（阳干有刃、阴干无刃 -1） | 渊海子平·论禄；三命通会·论阳刃 |

| 项 | 内容 |
|---|---|
| 用例数 | 经典起例 **9 → 17**；去重汇总 **56 → 64** |
| 验证 | ① `-Dtest=SiZhuBaZiClassicRulesTest` **17/17**；② 整模块 `mvn test` **EXIT=0**，去重汇总 **64 项 0 失败 0 错误 0 跳过** |



## 18 第 34 步（续）·逐章逐句全量复检与按原文修正

| 项 | 内容 |
|---|---|
| 复检方式 | 六典籍 `classic_text` 全量 **921** 条回原文（`design/六书全文整理/*.txt`）锚点比对 + 15 张条文表结构审计 + 调候 120 条完整性校验 |
| 数据结构缺陷 | **0**（锚点校验仅 1 例噪声；无重键；无缺失） |
| 章节缺陷（已修正） | **4**：①《滴天髓》CT_DTS_35 篇名 `通神论·一、夫妻`→`六亲论·一、夫妻`；②《滴天髓》拆出 CT_DTS_46「六亲论·十二从象」（源行 L1252），编号 46–62→47–63；③《穷通宝鉴》CT_QT_120 `月壬水`→`三月壬水`；④《神峰通考》CT_SF_77 `丁曰定格`→`丁日定格` |
| 计数变化 | 滴天髓 62→**63**；classic_text 921→**922**；六书正文合计 968→**969** |
| 断言同步 | `SiZhuBaZiOntologyComplianceTest.classicRows` 921→**922** |
| 验证 | 整模块 `mvn test` EXIT=0（见第 34 步回归） |

## 19 第 36 步·渊海子平 epub 目录全量复核（图题/卷题缺口补录）

| 项 | 内容 |
|---|---|
| 复检方式 | 以渊海子平 epub `toc.ncx`（**303 条 navPoint**）为权威目录，与 `data.sql` 全库做集合 diff（`target/toc_coverage.py`） |
| 缺口（6 条） | 1 图题（阴阳顺逆生旺死绝之图，navPoint#22）+ 5 卷题（卷之三/四/五/六/七，navPoint#151/194/222/283/298） |
| 处置 | `fu_clause` +1（`FU_YH_154`，图，【图录】）；`classic_text` +5（`CT_YH_149/192/220/281/296`，【卷题】）；原 `FU_YH_154~162` 顺移为 **155~163**（编号 1~163 连续） |
| 计数变化 | `fu_clause` 208→**209**；`classic_text` 922→**927** |
| 断言同步 | `SiZhuBaZiOntologyComplianceTest`：`fuRows 208→209`、`classicRows 922→927`，并新增第 36 步入库断言（`FU_YH_154`/`CT_YH_149/192/220/281/296`） |
| 复检 | `target/toc_coverage.py` 复跑「全库未出现 **0 条**」；余 6 个 `CT_YH` 空号全为已分流条目（图/格） |
| 验证 | 整模块 `mvn test` EXIT=0，去重汇总 **64 项 0 失败 0 错误 0 跳过** |

## 20 第 37 步·六典籍易混字「已/巳/己」交叉复核（数据用字修正，无测试影响）

| 项 | 内容 |
|---|---|
| 复检方式 | 本体库 `classic_text` 与整理本双向**单字级**交叉比对（`已` 上下 9 字窗口），再回原书 EPUB/docx 及四庫全書本/ctext 核字 |
| 修正（12 处） | DB `CT_SMTH_214`「被巳伤」→「被己伤」；整理本《三命通会》10 处、《神峰通考》1 处（「丙已戊」→「丙己戊」） |
| 复检结果 | 六书整理本交叉比对 **0 差异**；DB 全量「已」可疑上下文全为副词「已」，无须再改 |
| 保留异文 | 「庚日相逢时已/巳生」1 处（四庫作巳、底本/坊本作已），不臆改 |
| 测试影响 | **无**（仅字符用字修正，不涉及表结构/断言数值）；整模块 `mvn test` 复跑见回归 |

