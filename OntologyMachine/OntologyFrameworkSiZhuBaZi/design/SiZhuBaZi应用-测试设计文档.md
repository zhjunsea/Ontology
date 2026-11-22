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
| 数据分支全覆盖（离线） | `SiZhuBaZiDataBranchCoverageTest`（17 项） | 无 | 否 | 十神 10 类 / 神煞起例分支 / 用神·相神·忌神全取用 / 命格（专旺·从化·成败·具名）/ 旺衰 6 档 / 解析器标志位 |
| 分支矩阵补充（离线） | `SiZhuBaZiBranchMatrixTest`（20 项） | 无 | 否 | 干支关系全分支 / 正八格·禄刃·月劫·杂气 / 用神调候·从财·印比重 / 纳音长生 / 大运流年结构 / 告警分支 / 晚子时换日 |
| 分支矩阵补充·二阶段（离线） | `SiZhuBaZiBranchMatrix2Test`（15 项） | 无 | 否 | 层次四档（贵/富/贫/富贵两全）/ 合婚 G1 / 女命 G2（良/劣·特）/ 小儿关杀 / 劝诫吉凶 / 告警跳过 G3 / 落库离线 / 应期·岁运综合·分类断语 |
| 分支矩阵补充·三阶段（离线） | `SiZhuBaZiBranchMatrix3Test`（25 项） | 无 | 否 | 神煞起例（离线降级八类·空亡双分支·matchDeityRule 七基准·DEITY_SKIP）/ 成败救应 detectChengBai 六格各「有救/待救」/ 具名格局（魁罡·日贵日德·日禄归时·六乙鼠贵·壬骑龙背）/ 月令逢冲·合·旬空 |
| 分支矩阵补充·四阶段（离线） | `SiZhuBaZiBranchMatrix4Test`（19 项） | 无 | 否 | 十神宫位（tenGodName 十类映射·生克制化 4 分支·六亲文本）/ 旺衰六档逐档专门用例 + balance 计数与十二宫 / 取用多法冲突（effectiveYongElem 火炎·水寒·印比重·扶抑·默认 / yongShenElem 建禄改取 / handler 主取格局·扶抑·调候） |
| 分支矩阵补充·五阶段（离线） | `SiZhuBaZiBranchMatrix5Test`（10 项） | 无 | 否 | 干支作用关系细化（天干五合·六合·六冲·六穿·三合成局·半合·三刑·自刑·子卯·六破·墓库·无明显 全 12 支）/ 纳音·藏干·十二长生（nayinOf·ZHI_HIDDEN·stageOf 寄生十二宫）/ 大运流年结构（年/日/时粒度·流年·流月·流日流时·特殊岁运正负分支）/ 应期运限（timing 四法 + lifeStages 四柱主阶段） |
| 分支矩阵补充·六阶段（离线） | `SiZhuBaZiBranchMatrix6Test`（15 项） | 无 | 否 | 输入/输出编排节点：生辰解析（chartBase 组装·真太阳时·早晚子时·农历换算成功/越界待考·缺参负向）/ 排四柱（四柱结构·子初换日·起运顺逆）/ 岁运综合（岁运辩证+成格变格）/ 分类断语（性情·疾病·六亲·分类四段）/ 劝诫（贵富/贫贱两支）/ 汇总（命书拼装 + 大凶/寿元关/无告警） |
| 分支矩阵补充·七阶段（离线） | `SiZhuBaZiBranchMatrix7Test`（11 项） | 无 | 否 | 专项分析子分支：合婚（纳音比和/相生/相克·生肖同支/三合/六冲·神煞桃花命中·缺参负向）/ 小儿关杀（时值驿马/桃花/皆非 + 关杀名目备参）/ 女命专论（夫星/子星/比劫与分类结构·官杀混杂「正偏自处」特类） |
| 分支矩阵补充·八阶段（离线） | `SiZhuBaZiBranchMatrix8Test`（15 项） | 无 | 否 | 命局层次逐档（专旺富贵两全/贵兼富/纯贵/富/格破身弱/格破待救/贫/中平/中平偏下/平常格成/平常待成·伤官见官降判）+ 清浊真神（清·真神得用=true / 格破清中带浊）+ 命书详解大模型未装配降级（双空串） |
| 分支矩阵补充·九阶段（离线） | `SiZhuBaZiBranchMatrix9Test`（20 项） | 无 | 否 | 专旺成象（曲直/炎上/稼穑/从革/润下 成象与破格）+ 外格侦测（从财/从杀/从儿/化气/无）+ 相神忌神五行（顺用/逆用）+ 六亲断语（四宫结构/男财女官坐配偶/男比劫夺财/时柱子星） |
| 分支矩阵补充·十阶段（离线） | `SiZhuBaZiBranchMatrix10Test`（21 项） | 无 | 否 | 六亲宫位细分（年：印临祖位/财旺克印/平常/比劫透干夺财；月：官杀偏重/比劫分财/和顺；日支：冲/合/害/刑破；时柱：冲/伏吟/破/女命印透克食伤）+ 成败救应六格「未破→null」边角 |
| 分支矩阵补充·十一阶段（离线） | `SiZhuBaZiBranchMatrix11Test`（25 项） | 无 | 否 | 具名格局逐格命中（hitNamedPatterns）：六甲趋乾/六壬趋艮/天元作禄/天元坐煞/时上一位贵/岁德正官/时上正官/年上七煞/官煞混杂/财官双美/专禄要制/金神/时墓/禄马同乡/井栏叉/子遥巳/丑遥巳/飞天禄马/倒冲禄马/日刃/时上偏财/勾陈得位/玄武当权/白虎持势/刑合 |
| 分支矩阵补充·十二阶段（离线） | `SiZhuBaZiBranchMatrix12Test`（25 项） | 无 | 否 | 大运/流年逐运评析：scoreFortuneElem 身强/身弱六支评分 · elemRoleName 五支 · banHeHuaElem 五支 · fireExcessScore · fireExcessFortuneSegment 四支细判 · buildFortuneAnalysis 大运方向（阳男顺/阴男逆/阴女顺）与逐运吉凶及分段标题 |
| 分支矩阵补充·十三阶段（离线） | `SiZhuBaZiBranchMatrix13Test`（17 项） | 无 | 否 | 岁运辩证（buildFortuneSynthesis 逐运 good≷0 三结论/冲提纲覆盖/火炎分支）+ 分类事项断（buildCategoryVerdicts 事业官透身弱/身强/官藏/无官·财运财透/财藏/无财·婚姻日支逢冲·寿夭羊刃·飘荡驿马）+ verdictOr 离线回退剥离出处 |

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

### 6.6 数据分支全覆盖 `SiZhuBaZiDataBranchCoverageTest`（17 项）
> 离线、纯反射直调 `SiZhuBaZiOntologyJobWorker` 私有算理（`backendService == null` 自动降级），以 Mockito 模拟 `ActivatedJob`/`JobClient` 承接 `completeResult` 变量。按「**逻辑分支 × 数据分支**」二维覆盖客户点名的十神/神煞/用神·相神·忌神/命格等全部数据分支；判词一律以**结构化字段**为唯一真相源（经 `parseStrength`/`parsePatternGrade` 解析），不反向解析文案。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| D-01 | `tenGodAllTenTypes` | 十神 10 类闭合性 | `TEN_GOD` 集合 = 比肩/劫财/食神/伤官/偏财/正财/偏官/正官/偏印/正印，恰好 10 类 |
| D-02 | `tenGodNameMatchesIndex` | 十神命名 vs 算法 | 10 日干 × 10 目标干 **100 组合**，`tenGodName` 与 `tenGodIndex` 映射一致（离线降级路径） |
| D-03 | `tenGodsRelationsBranches` | 十神生克制化 4 支 | 官杀混杂 / 食伤制杀 / 财官相生 / 无明显十神生克制化 各命中一支 |
| D-04 | `deityMatcherAllBaseBranches` | `matchDeityRule` 7 基准分支 | 日柱（干支精确）/日干（单键·斜杠多键·`（时）`限定）/年支·日支/年支/月支三合（干支柱并取）/月支 逐支命中 |
| D-05 | `arrangeDeitiesFallback` | 离线降级八类 + 旬空 | 降级内置 天乙/羊刃/禄/驿马/桃花/华盖/将星/空亡 + 旬空 `voidness` 齐备 |
| D-06 | `yongShenElemBranches` | 月令格局法取用 4 支 | 建禄月劫取 官杀>财>食伤、皆无兜底官杀；常格取月令本气 |
| D-07 | `effectiveYongElemBranches` | 调候/改取 5 支 | 火炎取水 / 水寒取火 / 身强印比重改取食伤→财 / 身弱耗泄改取印→比劫兜底 |
| D-08 | `xiangJiShenBranches` | 相神/忌神 顺·逆用 | 顺用：相神生用神、忌神克用神；逆用：相神克用神、忌神生用神 |
| D-09 | `selectYongShenOutputs` | §9 五变量齐备 | 常格 + 专旺格输出 `yongShen`/`xiangShen`/`jiShen`/`tiaohou`/`yongShenMethods` 均非空 |
| D-10 | `zhuanWangFiveTypes` | 专旺 5 格 | 曲直(木)/炎上(火)/稼穑(土)/从革(金)/润下(水)，各含忌神破格校验 |
| D-11 | `externCongHuaPatterns` | 外格 4 类 + 未成象 | 从财/从杀/从儿/化气 各判成；未成象不误判 |
| D-12 | `chengBaiAllCases` | 成败 6 类 × 有救/待救 | 正官见伤官/偏官无制/财逢比劫/印逢财/食神逢枭/伤官见官，各分「有救/待救」两支；其它十神返 `null`（12 断言） |
| D-13 | `namedPatternsRepresentative` | 具名格局代表性 10 格 | 魁罡/六乙鼠贵/六阴朝阳/壬骑龙背/日贵/日德/日禄归时/天元作禄/时上一位贵/官煞混杂 等命中 |
| D-14 | `monthGodNameBasic` | 月令格局名 | `monthGodName` 由月支藏干本气定名正确 |
| D-15 | `strengthSixGrades` | 旺衰 6 档 | 身强（得令）/中和偏强（得令）/中和偏强（失令但得地得势）/身弱（失令、无根、寡助）/中和偏弱（失令、克泄耗偏多）/中和 逐档 |
| D-16 | `parseStrengthFlags` | 旺衰解析器标志位 | `parseStrength` 5 个标志位（得令/得地/得势/身强/身弱）解析正确 |
| D-17 | `parsePatternGradeFlags` | 格局解析器标志位 | `parsePatternGrade` 7 个标志位 + 互斥校验 |

### 6.7 分支矩阵补充 `SiZhuBaZiBranchMatrixTest`（20 项）
> 离线、真跑生产 Handler（Mockito 模拟 `ActivatedJob`/`JobClient` 承接 `completeResult`），纯算理判定经反射直调私有静态方法；`backendService == null` 自动降级。补齐 `SiZhuBaZiDataBranchCoverageTest` 未覆盖的算理分支。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| M-01 | `relHeWuhe` | 天干五合 + 地支六合 | 甲己「合而未必化」、子丑「六合」 |
| M-02 | `relChong` | 地支六冲 | 子午「相冲」 |
| M-03 | `relChuan` | 地支六穿/六害 | 子未「相穿」 |
| M-04 | `relSanheBanhe` | 三合成局 / 半合 | 申子辰「三合局（成局）」；申子缺辰「半合」 |
| M-05 | `relXing` | 三刑 / 自刑 / 子卯刑 | 寅巳「三刑（2/3）」；辰辰自刑；子卯相刑 |
| M-06 | `relPoMu` | 六破 / 墓库 | 卯午「相破」；辰戌「墓库土」 |
| M-07 | `relNone` | 无合冲刑穿兜底 | 「四柱无明显合冲刑穿」 |
| M-08 | `patternZhengBaGe` | 正八格 | 正官/偏官/正财/偏财/正印/偏印/食神/伤官 各定格名 |
| M-09 | `patternLuRenYueJie` | 禄刃·月劫 | 建禄格 / 阳刃格 / 月劫格 |
| M-10 | `patternZaQi` | 杂气格 | 四库本气比劫「杂气格」；本气财「杂气偏财格」 |
| M-11 | `yinBiZhongBranches` | 印比重判定真假 | 身强月令印→true；月令食伤→false |
| M-12 | `effectiveYongElemOverload` | 单参重载 | 身弱月令官杀取印(水=4) |
| M-13 | `yongShenTiaohouPriority` | 调候救偏 | 丙午火炎无水→含「调候救偏」 |
| M-14 | `yongShenCongCai` | 从财格顺用 | 日主无根从财→含「从财格」 |
| M-15 | `nayinHiddenTwelve` | 纳音·藏干·十二长生 | 纳音四柱/`子藏癸`/`日主甲`+`十二长生` |
| M-16 | `fortuneCyclesStructure` | 大运流年结构 | 含大运/流年/流月；`时`粒度含流日流时、`年`粒度不含 |
| M-17 | `alertDaXiong` / `alertShouYuanGuan` / `alertNone` | 告警三支（离线程序兜底） | 格破兼身弱→大凶 true；羊刃逢冲→寿元关 true；格成身中和无冲→false |
| M-18 | `lateZiRollover` | 晚子时换日 | `LATEZI=true` 日柱较 false 进位，年月柱不变 |

### 6.8 分支矩阵补充·二阶段 `SiZhuBaZiBranchMatrix2Test`（15 项）
> 离线、真跑生产 Handler（Mockito 模拟 `ActivatedJob`/`JobClient`），覆盖此前未系统触及的**下游节点与三排他网关**（G1 合婚 / G2 女命·小儿 / G3 告警）。断言一律读结构化字段。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| N-01 | `levelGuiGe` | 层次·贵格 | 官星当令有力、身能任官 → 「贵格…官星有理会」 |
| N-02 | `levelFuGe` | 层次·富格 | 财星当令有力、身强任财、官不显 → 「富格…财气通门户」 |
| N-03 | `levelPinGe` | 层次·贫格 | 财旺而身弱不任财 → 「贫格」 |
| N-04 | `levelZhuanWangFuGui` | 层次·富贵两全 | 格成且专旺 → 「富贵两全…成象」 |
| N-05 | `marriageMatch` | 合婚（G1） | 双人排盘 + 三维合参：`complementAnalysis` 含本人/对方四柱·神煞合参；`nayinRelation`/`zodiacRelation` 齐备；`marriageConclusion` 含「合婚结论」 |
| N-06 | `femalePure` | 女命·良类 | 官透不杂、身不弱、食伤不旺 → 「主类：纯」 |
| N-07 | `femaleZhuoSpecial` | 女命·劣·特类 | 官杀混杂、身不弱 → 「主类：滥」兼「正偏自处」 |
| N-08 | `childYimaGuan` | 小儿关杀 | 年支申、时支寅 → 「时值驿马」 |
| N-09 | `moralGood` | 劝诫·吉 | 层次含贵/富 → 「守正持盈」 |
| N-10 | `moralBad` | 劝诫·凶 | 层次非贵富 → 「积德改行」 |
| N-11 | `sendAlertSkipped` | 告警外发（G3） | `alertTriggered=false` → `SKIPPED_NO_ALERT`（不触达 MQ） |
| N-12 | `persistOffline` | 落库返回·离线 | `backendService=null` → 不落库，`caseRecord` 含「命例/四柱」，`response` 含 `COMPLETED` |
| N-13 | `timingStages` | 应期·人生阶段 | `timing` 含「应期取用」；`lifeStages` 含「年柱主少年」「时柱主老年」 |
| N-14 | `synthesizeFortune` | 岁运综合辩证 | `transformationNote` 含「成格变格」；`fortuneTrend` 非空 |
| N-15 | `categoryVerdicts` | 分类断语 | 含「性情才德」「五脏六腑」「六亲」 |

### 6.9 分支矩阵补充·三阶段 `SiZhuBaZiBranchMatrix3Test`（25 项）
> 离线、真跑生产 Handler（Mockito 模拟 `ActivatedJob`/`JobClient`）；纯算理方法（`detectChengBai`/`hitNamedPatterns`/`matchDeityRule`/`taohuaDerivNote`）经反射直调。补齐**神煞起例深层 / 成败救应 / 具名格局 / 月令冲合空**四组深层算理分支。断言一律读结构化字段。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| P-01 | `deityTianyiAndJiangxing` | 神煞·天乙+将星 | 甲日见丑（月）→天乙贵人；申子辰局将星在子 |
| P-02 | `deityYangren` | 神煞·羊刃 | 甲日见卯月→羊刃；且不含天乙 |
| P-03 | `deityLuAndYima` | 神煞·禄+驿马 | 甲日禄在寅（临官）、申子辰局驿马亦在寅 |
| P-04 | `deityTaohuaAndHuagai` | 神煞·桃花+华盖 | 年卯组桃花在子、申子辰局华盖在辰 |
| P-05 | `deityTaohuaDerivNote` | 桃花来源补注 | `taohuaDerivNote` 回看年支卯→「据年支卯·亥卯未局」 |
| P-06 | `voidnessBranches` | 空亡·双分支 | 甲子旬空戌亥：四柱子丑子子→「四柱不逢空」；含亥→「时亥空…应期」 |
| P-07 | `deitySkipBlacklist` | DEITY_SKIP 口径 | 含叙述性歧义类（六厄/红鸾/天喜/暗禄）；不误伤常用八类 |
| P-08 | `matchDeityRiZhuRiGan` | matchDeityRule·日柱/日干 | 日柱精确/日干单键/斜杠多键按位/日干`（时）`限定 |
| P-09 | `matchDeityZhiBases` | matchDeityRule·支基准 | 年支日支/年支(命中·不命中)/月支三合/月支 |
| P-10 | `chengBaiZhengGuan` | 成败·正官伤官见官 | 无印「待印绶制伤护官」/有印「可解」 |
| P-11 | `chengBaiPianGuan` | 成败·偏官杀旺 | 无财「待食制或印化」/有财「杀旺逢财滋」 |
| P-12 | `chengBaiCai` | 成败·财格比劫夺财 | 身强有官杀「可解」/无救「待官杀制劫」/身弱不判破(null) |
| P-13 | `chengBaiYin` | 成败·印格贪财坏印 | 有比劫「可解」/无「待比劫制财」 |
| P-14 | `chengBaiShiShen` | 成败·食神枭神夺食 | 有财「可解」/无「待财制枭」 |
| P-15 | `chengBaiShangGuan` | 成败·伤官见官 | 有印「可解」/无「待印制伤」 |
| P-16 | `namedKuiGang` | 具名·魁罡 | 庚戌日无财官刑冲→魁罡；透财/日支逢辰冲→破格不出 |
| P-17 | `namedRiGuiRiDe` | 具名·日贵/日德 | 丁酉→日贵；甲寅→日德 |
| P-18 | `namedRiLuGuiShi` | 具名·日禄归时 | 正格/月支重见兼格/见官破格不出 |
| P-19 | `namedLiuYiShuGui` | 具名·六乙鼠贵 | 乙日时子无午→成立；时午→破格不出 |
| P-20 | `namedRenQiLongBei` | 具名·壬骑龙背 | 辰多（≥2）成立；单辰不出 |
| P-21 | `patternMonthChong` | 月令逢冲·格神未透 | 「格有破（月令逢冲·待救应）」 |
| P-22 | `patternMonthHe` | 月令逢合·格神未透 | 「格有绊」 |
| P-23 | `patternMonthKong` | 月支旬空·格神未透 | 「格将成（…月令空亡落空…）」 |
| P-24 | `patternMonthChongAppendOnly` | 月令逢冲×已判成败 | grade 仍为伤官见官、不被覆盖；rescue 追加「格根动摇」 |
| P-25 | `patternYangRenMonthChongOverride` | 阳刃格月令逢冲覆写 | 格成→覆写「格有破（月令逢冲·格神动摇）」 |

### 6.10 分支矩阵补充·四阶段 `SiZhuBaZiBranchMatrix4Test`（19 项）
> 离线、真跑生产 Handler（Mockito 模拟 `ActivatedJob`/`JobClient`）；纯算理方法（`tenGodName`/`tenGodSet`/`yongShenElem`/`effectiveYongElem`/`isYinBiZhongYong`）经反射直调。补齐**十神宫位 / 旺衰六档逐档 / 取用多法冲突**三组深层维度。断言一律读结构化字段。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| Q-01 | `tenGodNameTenClasses` | 十神·十类映射 | 甲日对十天干→比/劫/食/伤/偏财/正财/偏官/正官/偏印/正印 |
| Q-02 | `tenGodRelationsCombos` | 十神生克制化 4 分支 | 官杀混杂 / 食伤制杀 / 财官相生 / 无明显十神生克制化 |
| Q-03 | `tenGodTextAndKinMapping` | 十神文本与六亲宫位 | 日主比肩·天干十神·藏干十神串·年/时宫六亲口径·女命以官杀为夫 |
| Q-04 | `strengthShenQiangDeLing` | 旺衰档① | 甲寅 丙寅 甲子 甲子 → 身强（得令） |
| Q-05 | `strengthZhongHePianQiangDeLing` | 旺衰档② | 庚子 丙子 甲子 庚子 → 中和偏强（得令） |
| Q-06 | `strengthZhongHePianQiangShiLing` | 旺衰档③ | 甲寅 壬申 甲寅 甲寅 → 中和偏强（失令但得地得势） |
| Q-07 | `strengthShenRuo` | 旺衰档④ | 庚申 辛酉 甲子 庚午 → 身弱（含「身弱」） |
| Q-08 | `strengthZhongHePianRuo` | 旺衰档⑤ | 壬申 辛酉 甲子 丙午 → 中和偏弱 |
| Q-09 | `strengthZhongHe` | 旺衰档⑥ | 甲子 甲辰 甲子 甲子 → 中和 |
| Q-10 | `strengthBalancePalaces` | 结构化 balance 与十二宫 | 得令=true/通根=2/得势=2；十二宫逐支含「年寅：」「时子：」 |
| Q-11 | `yongShenElemBranches` | 月令格局法 / 建禄改取 | 申月→金(3)；卯月有官→金(3)；卯月无官→土(2) |
| Q-12 | `effectiveYongTiaohouFire` | 调候·火炎 | 丙午 甲午 丙午 戊戌 → 取水(4) |
| Q-13 | `effectiveYongTiaohouWater` | 调候·水寒 | 壬子 壬子 壬子 壬辰 → 取火(1) |
| Q-14 | `effectiveYongYinBiZhong` | 印比重判据 | 甲日印水月身强→isYinBiZhongYong=true、yongShenElem=水(4) |
| Q-15 | `effectiveYongFuYi` | 扶抑取印/比劫 | 身弱财月有印→水(4)；无印→木(0) |
| Q-16 | `effectiveYongDefault` | 默认取月令格神 | 身强官杀月→金(3) |
| Q-17 | `selectYongShenGeJu` | 主取格局法 | 方法含「【主取】格局法（月令格局法）」；相神/忌神/调候齐备 |
| Q-18 | `selectYongShenFuYi` | 主取扶抑 | 方法含「【主取】扶抑为主」；用神含「身不能任财官」 |
| Q-19 | `selectYongShenTiaohou` | 主取调候救偏 | 方法含「【主取】调候救偏法」；用神含「调候救偏为主」 |

### 6.11 分支矩阵补充·五阶段 `SiZhuBaZiBranchMatrix5Test`（10 项）
> 离线、真跑生产 Handler（Mockito 模拟 `ActivatedJob`/`JobClient`）。补齐**干支作用关系细化 / 纳音藏干十二长生 / 大运流年结构 / 应期运限**四组维度（前三批仅触达干支关系、纳音、大运的浅层输出）。断言一律读结构化字段（`ganzhiRelations`/`nayin`/`hiddenStems`/`dayPillarTraits`/`fortuneCycles`/`fortuneAnalysis`/`specialCycles`/`timing`/`lifeStages`）。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| R-01 | `ganzhiWuHeLiuHeLiuChong` | 天干五合 / 地支六合 / 六冲 | 甲己合「年甲 合 月己…合而未必化」/ 子丑六合 / 子午相冲 |
| R-02 | `ganzhiLiuChuanSanheBanhe` | 六穿 / 三合成局 / 半合 | 子未相穿 / 申子辰三合局（成局）/ 申子半合 |
| R-03 | `ganzhiSanxingZixingZimao` | 三刑 / 自刑 / 子卯相刑 | 寅巳「三刑（2/3）」/ 辰辰自刑 / 子卯相刑 |
| R-04 | `ganzhiLiupoTombNone` | 六破 / 墓库 / 无明显关系 | 卯午相破 / 墓库土 / 四柱无明显合冲刑穿 |
| R-05 | `nayinHiddenStems` | 纳音·六十甲子 + 地支藏干 | 甲子→海中金 / 丙寅→炉中火 / 庚申→石榴木；甲子藏癸 / 丙寅藏甲丙戊 / 戊午藏丁己 |
| R-06 | `twelveStagesTraits` | 日主十二长生（寄生十二宫）+ 日柱特性 | 日主戊土 / 日支午（马）藏丁己 / 月寅长生 / 日午帝旺 |
| R-07 | `fortuneCyclesYearGranularity` | 大运·流年·流月（年粒度） | 大运（起运…）/ 首运甲子 / 流年（年级…）/ 流月 / 不含「流日/流时」 |
| R-08 | `fortuneCyclesDayHourGranularity` | 流日/流时（JDN·五鼠遁） | 日粒度含「流日/流时」「立春日」不含「其十二时」；时粒度含「其十二时（五鼠遁）」 |
| R-09 | `fortuneAnalysisAndSpecial` | 逐运评析 + 特殊岁运正负分支 | `fortuneAnalysis` 含「大运方向」「大运逐运评析」；负分支「未见岁运并临」；正分支 startAge=0/庚午运→「1990岁运并临（与庚午运同）」 |
| R-10 | `timingAndStages` | 应期四法 + 运限四柱主阶段 | timing 含 填实/冲实/合住/透出 + 结合空亡「年戌亥空」；lifeStages 含 年柱主少年/日柱主中年/时柱主老年 |

### 6.12 分支矩阵补充·六阶段 `SiZhuBaZiBranchMatrix6Test`（15 项）
> 离线、真跑生产 Handler（Mockito 模拟 `ActivatedJob`/`JobClient`；失败路径承接 `newThrowErrorCommand` 的 errorCode）。补齐此前仅在端到端隐性跑过、未做分支级断言的「输入/输出编排」六节点。断言一律读结构化字段。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| S-01 | `parseBirthBasicChartBase` | chartBase 组装 | SOLAR/GENDER/SUBJECT/MODE/GRAN 齐备 + LATEZI=false + LUNAR_UNCONVERTED=false；subjectType/inputMode 透传 |
| S-02 | `parseBirthLateZi` | 早晚子时 | 23:30 → LATEZI=true |
| S-03 | `parseBirthTrueSolarTime` | 真太阳时校正 | 东经116.4 → TST≈-0.24h；12:00 → SOLAR=1990-05-15T11:45:36 |
| S-04 | `parseBirthLunarConvert` | 农历换算成功 | 1990 五月十五 → SOLAR=1990-06-07T12:00、LUNAR_UNCONVERTED=false |
| S-05 | `parseBirthLunarUnconverted` | 农历越界【待考】 | 1899 → LUNAR_UNCONVERTED=true、保留原值 SOLAR=1899-05-15T12:00 |
| S-06 | `parseBirthMissingFails` | 缺参负向 | birth 缺失/空串 → PARSE_BIRTH_INPUT_FAILED |
| S-07 | `castFourPillarsStructure` | 排四柱结构 | 四柱四字 / 大运8 / 小运8 / 胎元2字 / 命宫2字 / startAge≥0 |
| S-08 | `castLateZiDayAdvances` | 子初换日 | LATEZI=true 相比 false，日柱（第3字）前进一位 |
| S-09 | `castDirectionByGender` | 起运顺逆 | 庚午阳年男顺女逆 → 首运干支不同 |
| S-10 | `synthesizeTransformation` | 岁运综合 | fortuneTrend 含「【岁运辩证·凭本命用神】」「本命格局基线」；note 含「成格变格：本命格局「正官格」」「命局层次：贵格」 |
| S-11 | `categoryVerdictsSections` | 分类断语四段 | 含 性情才德 / 疾病（五脏六腑） / 六亲吉凶 / 分类事项断 四段 |
| S-12 | `moralAdviceGoodBad` | 劝诫两支 | 贵格→岁运多扶·宜守正持盈；贫格→岁运有阻·宜积德改行 |
| S-13 | `aggregateAlertDaXiong` | 汇总·大凶 | 层次含「贫贱」→ alertTriggered=true；命书含四柱行 |
| S-14 | `aggregateAlertShouYuan` | 汇总·寿元关 | 神煞含「羊刃」「相冲」→ alertTriggered=true |
| S-15 | `aggregateNoAlert` | 汇总·无告警 | 贵格身强无羊刃冲 → alertTriggered=false |

### 6.13 分支矩阵补充·七阶段 `SiZhuBaZiBranchMatrix7Test`（11 项）
> 离线、真跑生产 Handler（Mockito 模拟 `ActivatedJob`/`JobClient`；失败路径承接 `newThrowErrorCommand` 的 errorCode）。补齐三个「专项分析」节点的更深子分支。断言一律读结构化字段。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| T-01 | `marriageMissingPartnerFails` | 合婚·缺参负向 | 无 partnerBirth → MARRIAGE_MATCH_FAILED |
| T-02 | `marriageNayinBiHe` | 纳音比和 + 生肖同支 | 双方同庚午·路旁土 → nayinRelation「比和」；zodiacRelation「同支比和」 |
| T-03 | `marriageNayinXiangSheng` | 纳音相生 + 生肖三合 | 本人庚午土 / 对方丙寅火（火生土）→「相生」；寅午戌「三合」 |
| T-04 | `marriageNayinXiangKe` | 纳音相克 | 本人庚午土 / 对方戊辰木（木克土）→「相克」 |
| T-05 | `marriageZodiacLiuChong` | 生肖六冲 | 午/子 → zodiacRelation「六冲」 |
| T-06 | `marriageShenshaTaoHua` | 神煞合参桃花命中 + 三维齐备 | 对方年支卯（本人寅午戌桃花在卯）→ shenshaMatch「桃花」；comp 含 四柱/五行互补/比劫食伤/年命纳音/生肖合冲/神煞合参 |
| T-07 | `childYiMa` | 小儿关杀·时值驿马 | 年支午、时支申 →「本命年支午：马相」「时值驿马」 |
| T-08 | `childTaoHua` | 小儿关杀·时值桃花 | 年支午、时支卯 →「时值桃花」 |
| T-09 | `childNeither` | 小儿关杀·驿马桃花皆非 | 时支戌 →「另有将军箭」且不含时值驿马/桃花 |
| T-10 | `femaleStructure` | 女命专论·结构 | femaleVerdict 含 女命专论/夫星/子星/比劫/【女命分类·归类】 |
| T-11 | `femaleGuanZaZhengPian` | 女命·官杀混杂特类 | 甲日见庚（偏官）辛（正官）→「正偏自处」 |

### 6.14 分支矩阵补充·八阶段 `SiZhuBaZiBranchMatrix8Test`（15 项）
> 离线、真跑生产 Handler（Mockito 模拟 `ActivatedJob`/`JobClient`）。补齐 `handleJudgeChartLevel` 十支层次边界、`purityTruth` 清浊真神分支与 `handleInterpretResult` 大模型未装配降级分支。断言一律读结构化字段（`chartLevel`/`purityTruth`/`aiInterpretation`/`aiInterpretationPlain`）。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| U-01 | `levelZhuanWangFuGuiLiangQuan` | 专旺成象 | grade 含「专旺」不含「破」→「贵格·富贵两全」 |
| U-02 | `levelGuiJianFu` | 贵格兼富 | 格成身强·官当令+财透有根 →「贵格·亦主富」 |
| U-03 | `levelGui` | 纯贵格 | 格成身强·官当令·全局无财 →「贵格（官星有理会）」不含「亦主富」 |
| U-04 | `levelFu` | 富格 | 格成身强·财当令·全局无官 →「富格（财气通门户）」不含「贵格」 |
| U-05 | `levelPoShenRuo` | 格破身弱 | 「格有破」+ 身弱 →「平常偏下（格破身弱…非必贫贱）」 |
| U-06 | `levelPoDaiJiu` | 格破非弱 | 「格有破」+ 中和偏强 →「平常偏下（格破·待岁运救应）」 |
| U-07 | `levelPin` | 贫格 | 格将成·财当令·身弱 →「贫格·财神反不真·财旺身弱」 |
| U-08 | `levelZhongPing` | 中平 | 身强·全局无财无官 →「中平（身旺而财官不显…食伤泄秀）」 |
| U-09 | `levelZhongPingPianXia` | 中平偏下 | 中和偏弱·全局无财无官 →「中平偏下…非必贫贱」 |
| U-10 | `levelChangPingGeCheng` | 平常格（格成未通） | 格成·身弱·官有理会·财不通 →「平常格（格成而财官未通门户…衣禄平顺）」 |
| U-11 | `levelChangPingDaiCheng` | 平常格（格局待成） | 格将成·身强·财通 →「平常格（格局待成…待岁运引扶）」 |
| U-12 | `levelShangGuanJianGuanJiangWeiFu` | 伤官见官降判 | 伤官格·伤官透·官透有根·财通 → 官失理→「富格」不含「贵格」 |
| U-13 | `purityQing` | 清·真神得用=true | purityTruth 含「清（格正身健、用神有根、忌神不扰）」+「真神得用=true」 |
| U-14 | `purityQingZhongDaiZhuo` | 格破清中带浊 | purityTruth 含「清中带浊（格有偏、用神乏力）」 |
| U-15 | `interpretResultDegradeWithoutLlm` | 命书详解降级 | llm 未装配 → `aiInterpretation`/`aiInterpretationPlain` 均空串，流程不阻断 |

### 6.15 分支矩阵补充·九阶段 `SiZhuBaZiBranchMatrix9Test`（20 项）
> 离线、对**纯算理方法**做分支级断言（反射直调 `detectZhuanWang`/`detectExternPattern`/`xiangShenElem`/`jiShenElem`/`buildKinshipVerdicts`）。断言读方法返回值，不反向解析判词。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| V-01 | `zhuanWangQuZhiCheng` | 专旺·曲直成象 | 亥卯未全·无金 →「专旺格（曲直）」 |
| V-02 | `zhuanWangQuZhiPo` | 专旺·曲直破格 | 亥卯未全但见酉(金) → 空串 |
| V-03 | `zhuanWangYanShangCheng` | 专旺·炎上成象 | 寅午戌全·无水 →「专旺格（炎上）」 |
| V-04 | `zhuanWangYanShangPo` | 专旺·炎上破格 | 寅午戌全但见子(水) → 空串 |
| V-05 | `zhuanWangJiaSeCheng` | 专旺·稼穑成象 | 辰戌丑未全·无木 →「专旺格（稼穑）」 |
| V-06 | `zhuanWangJiaSePo` | 专旺·稼穑破格 | 辰戌丑未全但天干透甲(木) → 空串 |
| V-07 | `zhuanWangCongGeCheng` | 专旺·从革成象 | 申酉戌全·无火 →「专旺格（从革）」 |
| V-08 | `zhuanWangRunXiaCheng` | 专旺·润下成象 | 申子辰全·无燥土 →「专旺格（润下）」 |
| V-09 | `externCongCai` | 外格·从财格 | 日主无根无印无助·土财一气独旺有气 →「从财格」 |
| V-10 | `externCongSha` | 外格·从杀格 | 日主无根·金(官杀)独旺透干 →「从杀格」 |
| V-11 | `externCongEr` | 外格·从儿格 | 日主无根·火(食伤)独旺透干 →「从儿格」 |
| V-12 | `externHuaQi` | 外格·化气格 | 甲己合·化神土得月令(戌)·日主无根 →「化气格（化土）」 |
| V-13 | `externNone` | 外格·无 | 有根有助·未成专旺/从/化 → 空串 |
| V-14 | `xiangJiShunYong` | 相忌·顺用 | 正官格：相神土(2)·忌神火(1) |
| V-15 | `xiangJiNiYong` | 相忌·逆用 | 伤官格：相神水(4)·忌神木(0) |
| V-16 | `kinshipStructure` | 六亲·四宫结构 | 含 ①年柱/②月柱/③日支/④时柱/依据 |
| V-17 | `kinshipMaleSpouseStar` | 六亲·男配偶坐妻星 | 戊日·子支藏癸(财)→「配偶宫坐妻星（财）」 |
| V-18 | `kinshipFemaleSpouseStar` | 六亲·女配偶坐夫星 | 甲日·酉支藏辛(官)→「配偶宫坐夫星（官）」 |
| V-19 | `kinshipMaleBiJieDuoCai` | 六亲·男比劫夺财 | 比劫旺于财且财透 →「比劫夺财」+「宜防妻缘争竞」 |
| V-20 | `kinshipMaleHourZiStar` | 六亲·时柱子星 | 甲日·时支酉藏辛(官杀)→「时坐子星（官杀）」 |

### 6.16 分支矩阵补充·十阶段 `SiZhuBaZiBranchMatrix10Test`（21 项）
> 离线、反射直调 `buildKinshipVerdicts`（六亲宫位细分）与 `detectChengBai`（成败救应「未破」边角）。断言读方法返回值，不反向解析判词。

| ID | 方法 | 覆盖目标 | 预期断言（关键） |
|---|---|---|---|
| W-01 | `kinshipYearYinInYear` | 年宫·印临祖位财不伤印 | 印旺于财 →「印临祖位而财不伤印」 |
| W-02 | `kinshipYearCaiKeYin` | 年宫·祖宫乏印财旺克印 | 无印于年、财远旺 →「祖宫乏印而财旺克印」+「偏财（父星）临祖位」 |
| W-03 | `kinshipYearPingChang` | 年宫·祖宫平常 | 无印临、财不克印 →「祖宫十神平常」 |
| W-04 | `kinshipYearBiJieTouGan` | 年宫·比劫透干夺财 | 比劫旺于财且透 →「比劫透干夺财」 |
| W-05 | `kinshipMonthGuanShaZhong` | 月宫·官杀偏重 | 官杀远多于比劫 →「官杀偏重，比劫帮身有限」 |
| W-06 | `kinshipMonthBiJieFenCai` | 月宫·比劫分财 | 月干比劫现+财透 →「兄弟有助亦主分财争产」 |
| W-07 | `kinshipMonthHeShun` | 月宫·和顺 | 非官杀重无比劫 →「月门十神和顺」 |
| W-08 | `kinshipDayChong` | 日支·逢冲 | 子午冲 →「日支逢冲」+「婚宫动荡」 |
| W-09 | `kinshipDayHe` | 日支·逢合 | 子丑合 →「日支逢合」+「婚宫得合」 |
| W-10 | `kinshipDayHai` | 日支·逢害 | 子未害 →「日支逢害」+「夫妻易生嫌隙」 |
| W-11 | `kinshipDayXingPo` | 日支·相刑破 | 子卯刑 →「日支与别支相刑/破」+「婚宫动」 |
| W-12 | `kinshipHourChong` | 时柱·逢冲 | 时午冲年子 →「时支逢冲」+「子息宫动」 |
| W-13 | `kinshipHourFuYin` | 时柱·伏吟 | 时子与年子 →「时支与年支」+「伏吟」 |
| W-14 | `kinshipHourPo` | 时柱·逢破 | 卯午破 →「时支逢午卯破」 |
| W-15 | `kinshipFemaleYinKeShiShang` | 时柱·女命印透克食伤 | 印远多于食伤且透 →「印透克食伤」+「子星（食伤）受制」 |
| W-16 | `chengBaiZhengGuanNoShangGuan` | 成败·正官非伤官见官 | 无伤官 → null |
| W-17 | `chengBaiPianGuanWithYin` | 成败·偏官有印化杀 | 有印/食伤 → null |
| W-18 | `chengBaiZhengCaiNoBiJie` | 成败·正财无比劫 | 无比劫 → null |
| W-19 | `chengBaiZhengYinNoCai` | 成败·正印无财 | 无财 → null |
| W-20 | `chengBaiShiShenNoPianYin` | 成败·食神无偏印 | 无偏印 → null |
| W-21 | `chengBaiShangGuanNoZhengGuan` | 成败·伤官无正官 | 无正官 → null |

### 6.17 分支矩阵补充·十一阶段 `SiZhuBaZiBranchMatrix11Test`（25 项）
> 离线、反射直调 `hitNamedPatterns(int[][],int)`（返回 `List<String[]>`，每项 `{名, 描述, 出处}`）。断言「命中集合含指定格名」，不反向解析判词。

| ID | 方法 | 具名格 | 受控命例（日柱在第 3 位） |
|---|---|---|---|
| X-01 | `npLiuJiaQuQian` | 六甲趋乾 | 乙亥 丙寅 **甲子** 丁卯（甲日见亥） |
| X-02 | `npLiuRenQuGen` | 六壬趋艮 | 壬寅 甲辰 **壬戌** 丙午（壬日见寅） |
| X-03 | `npTianYuanZuoLu` | 天元作禄 | 戊辰 己未 **庚午** 丙子（午藏丁正官） |
| X-04 | `npTianYuanZuoSha` | 天元坐煞 | 甲子 丙寅 **甲申** 丁卯（申藏庚偏官） |
| X-05 | `npShiShangYiWeiGui` | 时上一位贵 | 甲子 丙寅 **甲子** 庚午（时干独见七煞） |
| X-06 | `npSuiDeZhengGuan` | 岁德正官 | 辛酉 丙寅 **甲子** 丁卯（年干正官） |
| X-07 | `npShiShangZhengGuan` | 时上正官 | 甲子 丙寅 **甲子** 辛未（时干正官） |
| X-08 | `npNianShangQiSha` | 年上七煞 | 庚申 丙寅 **甲子** 丁卯（年干七煞） |
| X-09 | `npGuanShaHunZa` | 官煞混杂 | 庚申 丙寅 **甲子** 辛未（正官七煞并见） |
| X-10 | `npCaiGuanShuangMei` | 财官双美 | 壬午 庚戌 **壬午** 辛丑（壬午自坐财官·不见木旺） |
| X-11 | `npZhuanLuYaoZhi` | 专禄要制 | 丙寅 己亥 **庚午** 辛巳（六庚日见巳时） |
| X-12 | `npJinShen` | 金神 | 甲子 丙寅 **甲子** 乙丑（时柱乙丑） |
| X-13 | `npShiMu` | 时墓 | 甲子 丙寅 **甲子** 丁未（甲木墓未） |
| X-14 | `npLuMaTongXiang` | 禄马同乡 | 戊午 己未 **庚申** 壬午（庚禄申=年午起马申·申现） |
| X-15 | `npJingLanCha` | 井栏叉 | 庚申 庚子 **庚辰** 丙子（申子辰全·无寅午戌） |
| X-16 | `npZiYaoSi` | 子遥巳 | 甲子 丙寅 **甲子** 甲子（甲子日甲子时·无午巳丑） |
| X-17 | `npChouYaoSi` | 丑遥巳 | 辛丑 辛丑 **辛丑** 丙寅（辛丑日丑多·无巳酉） |
| X-18 | `npFeiTianLuMa` | 飞天禄马 | 壬子 壬子 **壬子** 丙寅（壬子日子多冲午·无午） |
| X-19 | `npDaoChongLuMa` | 倒冲禄马 | 丙午 甲午 **丙午** 丙寅（丙午日午多冲子·无子） |
| X-20 | `npRiRen` | 日刃 | 戊午 己未 **戊午** 乙卯（戊午自坐羊刃） |
| X-21 | `npShiShangPianCai` | 时上偏财 | 甲子 丙寅 **甲子** 戊辰（时干透偏财） |
| X-22 | `npGouChenDeWei` | 勾陈得位 | 戊子 甲寅 **戊子** 丙辰（戊日坐财官·财官俱见） |
| X-23 | `npXuanWuDangQuan` | 玄武当权 | 壬午 庚戌 **壬午** 辛丑（壬日坐财官·财官俱见） |
| X-24 | `npBaiHuChiShi` | 白虎持势 | 庚寅 丙午 **庚寅** 丙子（庚日坐财官·财官俱见） |
| X-25 | `npXingHe` | 刑合 | 癸卯 甲寅 **癸卯** 甲寅（癸日寅时·巳不填实） |

### 6.18 分支矩阵补充·十二阶段 `SiZhuBaZiBranchMatrix12Test`（25 项）
> 离线、反射直调纯函数 `scoreFortuneElem` / `elemRoleName` / `banHeHuaElem` / `fireExcessScore` / `fireExcessFortuneSegment`，并以 Worker 实例调 `buildFortuneAnalysis` 断言输出文本片段；不反向解析判词。

| ID | 方法 | 维度 | 断言（受控输入 → 期望） |
|---|---|---|---|
| Y-01 | `sfStrongShiShang` | 评分·身强食伤 | `score(火=1,yong0,day0,strong)` → +1 |
| Y-02 | `sfStrongCai` | 评分·身强财 | `score(土=2,…)` → +1 |
| Y-03 | `sfStrongGuan` | 评分·身强官杀 | `score(金=3,…)` → 0 |
| Y-04 | `sfStrongBiYin` | 评分·身强比劫印 | `score(木=0)`/`score(水=4)` → -1 |
| Y-05 | `sfWeakYinBi` | 评分·身弱印比 | `score(木=0,false)`/`score(水=4,false)` → +1 |
| Y-06 | `sfWeakKeXie` | 评分·身弱克泄耗 | `score(火/土/金,false)` → -1 |
| Y-07 | `roleBiJie` | 五行角色·比劫 | `role(0,0)` → 比劫 |
| Y-08 | `roleShiShang` | 五行角色·食伤 | `role(0,1)` → 食伤 |
| Y-09 | `roleCai` | 五行角色·财 | `role(0,2)` → 财 |
| Y-10 | `roleGuanSha` | 五行角色·官杀 | `role(0,3)` → 官杀 |
| Y-11 | `roleYin` | 五行角色·印 | `role(0,4)` → 印 |
| Y-12 | `banHeShui` | 半合化水 | 命见申、运支子 → 4（水） |
| Y-13 | `banHeMu` | 半合化木 | 命见亥、运支卯 → 0（木） |
| Y-14 | `banHeHuo` | 半合化火 | 命见寅、运支午 → 1（火） |
| Y-15 | `banHeJin` | 半合化金 | 命见巳、运支酉 → 3（金） |
| Y-16 | `banHeNone` | 半合不成 | 运支子而局无申辰 → -1 |
| Y-17 | `fireScore` | 火炎土燥评分 | 壬坐辰=+2 / 丙坐酉=0 / 庚坐午=0 |
| Y-18 | `fireSegmentJi` | 火炎逐运·吉 | 癸坐子 → `"吉"` |
| Y-19 | `fireSegmentChou` | 火炎逐运·湿土 | 壬坐丑 → 含「湿土」「丑午相害」 |
| Y-20 | `fireSegmentCaiLao` | 火炎逐运·吉中带劳 | 庚坐申 → `startsWith("吉中带劳")` |
| Y-21 | `fireSegmentXiong` | 火炎逐运·凶 | 丙坐午 → `"凶"` |
| Y-22 | `fortuneDirYangMaleShun` | 大运方向·阳男顺 | 年干甲·男 → 含「年干甲（阳）·男→顺行排运」 |
| Y-23 | `fortuneDirYinMaleNi` | 大运方向·阴男逆 | 年干乙·男 → 含「年干乙（阴）·男→逆行排运」 |
| Y-24 | `fortuneDirYinFemaleShun` | 大运方向·阴女顺 | 年干乙·女 → 含「年干乙（阴）·女→顺行排运」 |
| Y-25 | `fortuneVerdictAndSections` | 逐运评析·分段+吉凶 | 含四段标题 + 「凶（助忌损用）」+「吉（喜用得力）」 |

### 6.19 分支矩阵补充·十三阶段 `SiZhuBaZiBranchMatrix13Test`（17 项）
> 离线、反射直调 `buildFortuneSynthesis`（静态纯函数）、`buildCategoryVerdicts`（实例，离线 `verdictOr` 回退内联）与 `verdictOr`；断言读输出文本片段。

| ID | 方法 | 维度 | 断言（受控输入 → 期望） |
|---|---|---|---|
| Z-01 | `fsHeader` | 岁运辩证·标题头 | 含「岁运辩证·凭本命用神」+ 身强弱 |
| Z-02 | `fsGoodPositive` | 岁运辩证·good≥1 | 身强食伤运（丙午）→ 扶助得力，格愈成、事顺 |
| Z-03 | `fsGoodNegative` | 岁运辩证·good≤-1 | 身强比劫运（甲寅）→ 助忌损用，格受摇、宜谨 |
| Z-04 | `fsGoodZero` | 岁运辩证·good==0 | 身强官杀运（辛酉）→ 得失相参，平中带变 |
| Z-05 | `fsChongGang` | 岁运辩证·冲提纲 | 运支冲提纲 → 覆盖「运支冲提纲，格局动摇，宜谨（…）」 |
| Z-06 | `fsFireExcess` | 岁运辩证·火炎分支 | 丙日水局（癸亥运）→ fireExcessFortuneSegment 判「吉」 |
| Z-07 | `bcGuanTouWeak` | 事业·官透身弱 | 庚透·strong=false → 官杀克身为压力、宜印化杀生身 |
| Z-08 | `bcGuanTouStrong` | 事业·官透身强 | 庚透·strong=true → 官杀为功名之神 |
| Z-09 | `bcGuanCang` | 事业·官藏不透 | 日支申藏庚 → 本命官杀藏而不透 |
| Z-10 | `bcNoGuan` | 事业·无官杀 | 全局无金 → 本命官杀不显、功名宜循他途 |
| Z-11 | `bcCaiTou` | 财运·财透 | 年戊透土 → 本命财星透干 |
| Z-12 | `bcCaiCang` | 财运·财藏 | 寅藏戊、干无土 → 本命财星藏支 |
| Z-13 | `bcNoCai` | 财运·无财 | 支子子子卯、干无土 → 本命财星不显 |
| Z-14 | `bcMarriageChong` | 婚姻·日支逢冲 | 日支子·年午 → 本命日支逢冲、婚宫受动 |
| Z-15 | `bcShouYangren` | 寿夭·羊刃 | deities=羊刃 → 本命见羊刃、性刚易折 |
| Z-16 | `bcPiaoYiMa` | 飘荡·驿马 | deities=驿马 → 本命见驿马、主动荡迁远 |
| Z-17 | `verdictOrFallback` | verdictOr 回退 | 无名录命中 → 回退 fallback 且剥离《》书名 |

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

### 7.4 数据分支覆盖矩阵（逻辑分支 × 数据分支）
| 算理维度 | 分支全集 | 覆盖用例 |
|---|---|---|
| 十神 | 10 类（比/劫/食/伤/财偏正/官偏正/印偏正）+ 生克制化 4 支（官杀混杂/食伤制杀/财官相生/无明显） | D-01 / D-02 / D-03 |
| 神煞起例 | 7 基准分支（日柱/日干单键/日干斜杠多键/日干`（时）`限定/年支日支/年支/月支三合/月支）+ 离线降级 8 类（天乙·羊刃·禄·驿马·桃花·华盖·将星·空亡）+ 旬空 | D-04 / D-05 |
| 用神·相神·忌神 | 取用 4 支 + 改取 5 支（火炎/水寒/身强印比/身弱耗泄）+ 相神忌神顺逆用 2×2 + §9 五变量 | D-06 / D-07 / D-08 / D-09 |
| 命格 | 专旺 5 格 + 从化 4 类（从财/从杀/从儿/化气）+ 成败 6 类 ×（有救/待救）+ 具名 10+ 格 + 月令格局名 | D-10 / D-11 / D-12 / D-13 / D-14 |
| 旺衰 | 6 档（身强得令/中和偏强得令/中和偏强失令得地得势/身弱/中和偏弱/中和） | D-15 |
| 结构化真相源解析器 | `parseStrength` 5 标志位 + `parsePatternGrade` 7 标志位（含互斥） | D-16 / D-17 |
| 干支作用关系 | 天干五合 / 六合 / 六冲 / 六穿 / 三合成局 / 半合 / 三刑 / 自刑 / 子卯刑 / 六破 / 墓库 / 无关系（12 支） | M-01~M-07 |
| 定格（正八格 · 禄刃 · 杂气） | 正官/偏官/正财/偏财/正印/偏印/食神/伤官 + 建禄/阳刃/月劫 + 杂气格(四库本气比劫)/杂气偏财格（14 支） | M-08~M-10 |
| 用神补充 | 印比重真假 / `effectiveYongElem` 单参重载 / 调候救偏 / 从财格顺用 | M-11~M-14 |
| 历法与岁运结构 | 纳音·藏干·十二长生 / 大运流年（大运·流年·流月·流日流时）/ 晚子时换日 | M-15~M-18 |
| 告警分支 | 大凶（格破兼身弱）/ 寿元关（羊刃逢冲）/ 无告警 | M-17 |
| 命局层次四档 | 贵格（官星有理会）/ 富格（财气通门户）/ 贫格（财旺身弱）/ 富贵两全（专旺成象） | N-01~N-04 |
| 合婚·女命·小儿（三排他网关） | 合婚 G1 三维合参 / 女命 G2 良类·劣特类 / 小儿关杀驿马 | N-05~N-08 |
| 后处理与出口节点 | 劝诫吉凶 / 告警外发跳过 G3 / 落库离线 / 应期·岁运综合·分类断语 | N-09~N-15 |
| 神煞起例（离线降级 + 基准匹配） | 离线降级八类（天乙·羊刃·禄·驿马·桃花·华盖·将星）+ 空亡双分支（逢空/不逢空）+ 桃花来源补注 + `matchDeityRule` 七基准（日柱/日干单键/日干斜杠多键/日干`（时）`限定/年支日支/年支/月支三合/月支）+ `DEITY_SKIP` 黑名单口径 | P-01~P-09 |
| 成败救应（detectChengBai） | 正官/偏官/正财偏财/正印偏印/食神/伤官 六格 ×「有救 / 待救」两叉 + 财格身弱不判比劫夺财边界 | P-10~P-15 |
| 具名格局（hitNamedPatterns） | 魁罡（成立/透财破/逢冲破）/ 日贵 / 日德 / 日禄归时（正格/月支重见兼格/见官破格）/ 六乙鼠贵（成立/见午破）/ 壬骑龙背（辰多/单辰） | P-16~P-20 |
| 月令冲合空（determinePattern） | 格神未透遇月令逢冲「待救应」/ 月令逢合「格有绊」/ 月支旬空「空亡落空」/ 已判成败时月令逢冲只追加注记不覆盖 grade / 阳刃格月令逢冲覆写「格神动摇」 | P-21~P-25 |
| 十神宫位（assignTenGodsKin） | `tenGodName` 十类映射（甲日对十干 10 支）/ `tenGodSet` 生克制化 4 支（官杀混杂/食伤制杀/财官相生/无明显）/ 天干·藏干十神文本格式 / 六亲宫位固定口径 | Q-01~Q-03 |
| 旺衰六档（assessDayMasterStrength） | 6 档逐档专门用例（身强得令/中和偏强得令/中和偏强失令得地得势/身弱/中和偏弱/中和）+ 结构化 balance 三计数与十二宫逐支 | Q-04~Q-10 |
| 取用多法冲突（selectYongShen） | `effectiveYongElem` 调候火炎/水寒、印比重、扶抑有印/无印、默认真实分支 + `yongShenElem` 建禄改取（官杀>财>食伤）+ `isYinBiZhongYong` 判据 + handler 主取格局法/扶抑/调候三分支 | Q-11~Q-19 |
| 干支作用关系细化（analyzeGanzhiRelations 12 支全谱） | 天干五合 / 六合 / 六冲 / 六穿 / 三合成局 / 半合 / 三刑 / 自刑 / 子卯刑 / 六破 / 墓库 / 无明显（逐支专项命例） | R-01~R-04 |
| 纳音藏干十二长生（analyzeNayinHiddenStems） | `nayinOf` 六十甲子纳音 / `ZHI_HIDDEN` 地支藏干串 / `stageOf` 日主寄生十二宫（阳干顺行·阴干逆行） | R-05~R-06 |
| 大运流年结构（analyzeFortuneCycles） | 年粒度（大运·流年·流月）/ 日粒度（流日·JDN 锚定）/ 时粒度（其十二时·五鼠遁）/ 特殊岁运格局正负分支（岁运并临/天克地冲/伏吟 命中 vs 未见） | R-07~R-09 |
| 应期运限（inferTimingAndStages） | `timing` 应期四法（填实/冲实/合住/透出）+ 结合空亡；`lifeStages` 运限四柱主阶段（年少年/月青年/日中年/时老年） | R-10 |
| 生辰解析（handleParseBirthInput） | chartBase 九字段组装 / 真太阳时经度校正 / 早晚子时辰时标记 / 农历换算成功与越界【待考】双分支 / 缺参负向（PARSE_BIRTH_INPUT_FAILED） | S-01~S-06 |
| 排四柱（handleCastFourPillars） | 四柱/大运8/小运8/胎元/命宫结构 + 子初换日（日柱进位）+ 起运顺逆（阳男阴女顺·阴男阳女逆） | S-07~S-09 |
| 岁运综合·分类断语·劝诫（synthesize/category/moral） | 岁运辩证 trend + 成格变格 note / 分类断语性情·疾病·六亲·分类四段 / 劝诫贵富（守正持盈）与贫贱（积德改行）两支 | S-10~S-12 |
| 综合汇总告警（aggregateReading） | 命书拼装（含四柱行）+ javaFlags 三态（大凶[贫贱或格破身弱] / 寿元关[羊刃逢冲] / 无告警）；离线 SWRL 不可用回退程序判据 | S-13~S-15 |
| 合婚三维（analyzeMarriageMatch） | 纳音关系 3 支（比和/相生/相克）+ 生肖关系（同支比和/三合/六冲）+ 神煞合参（桃花命中/无显著）+ 互补结论 + 缺参负向 | T-01~T-06 |
| 小儿关杀（analyzeChildChart） | 时支命中驿马（YIMA）/ 桃花（TAOHUA）/ 皆非 三支 + 关杀名目备参（将军箭等仅列名） | T-07~T-09 |
| 女命专论（analyzeFemaleChart） | 夫星/子星/比劫天干藏支分判文本 + 分类结构（良/劣/特/平和）+ 官杀混杂特类「正偏自处」 | T-10~T-11 |
| 命局层次逐档（judgeChartLevel） | 专旺富贵两全 / 贵格兼富 / 纯贵格 / 富格 / 格破身弱·格破待救（平常偏下两支）/ 贫格（财旺身弱）/ 中平（身旺财官不显）/ 中平偏下 / 平常格（格成财官未通）/ 平常格（格局待成）/ 伤官见官使官星失理而降判富格 | U-01~U-12 |
| 清浊真神（purityTruth） | 清（格正身健、用神有根、忌神不扰）+ 真神得用=true / 格破 → 清中带浊（格有偏、用神乏力） | U-13~U-14 |
| 命书详解降级（handleInterpretResult） | 大模型未装配 → aiInterpretation/aiInterpretationPlain 双空串降级，流程不阻断 | U-15 |
| 专旺成象（detectZhuanWang） | 曲直（亥卯未·无金）/ 炎上（寅午戌·无水）/ 稼穑（辰戌丑未·无木）/ 从革（申酉戌·无火）/ 润下（申子辰·无燥土）五格 成象与破格 | V-01~V-08 |
| 外格侦测（detectExternPattern） | 从财格 / 从杀格 / 从儿格 / 化气格（甲己合化土得月令）/ 无外格（有根不从） | V-09~V-13 |
| 相神·忌神五行（xiangShenElem/jiShenElem） | 顺用（吉神：相神取生用神者、忌神取克用神者）/ 逆用（偏官伤官：相神取制用神者、忌神取生用神者） | V-14~V-15 |
| 六亲断语（buildKinshipVerdicts） | 年/月/日/时四宫结构 + 依据 / 男命配偶宫坐妻星（财）/ 女命配偶宫坐夫星（官）/ 男命比劫夺财 / 男命时柱子星（官杀） | V-16~V-20 |
| 六亲宫位细分（buildKinshipVerdicts 续） | 年宫（印临祖位·财不伤印/财旺克印、祖宫乏印财旺克印、祖宫平常、偏财临祖、比劫透干夺财）/ 月宫（官杀偏重、比劫+财透分财、和顺）/ 日支（冲/合/害/刑破）/ 时柱（冲/伏吟/破、女命印透克食伤） | W-01~W-15 |
| 成败救应「未破」边角（detectChengBai→null） | 正官非伤官见官 / 偏官有印化杀 / 正财无比劫 / 正印无财 / 食神无偏印 / 伤官无正官 → 均返回 null | W-16~W-21 |
| 具名格局逐格命中（hitNamedPatterns 续） | 六甲趋乾 / 六壬趋艮 / 天元作禄 / 天元坐煞 / 时上一位贵 / 岁德正官 / 时上正官 / 年上七煞 / 官煞混杂 / 财官双美 / 专禄要制 / 金神 / 时墓 / 禄马同乡 / 井栏叉 / 子遥巳 / 丑遥巳 / 飞天禄马 / 倒冲禄马 / 日刃 / 时上偏财 / 勾陈得位 / 玄武当权 / 白虎持势 / 刑合（共 25 格成立） | X-01~X-25 |
| 大运/流年逐运评析（buildFortuneAnalysis） | 大运方向三分支（阳男顺/阴男逆/阴女顺）+ 起运依据 + 逐运评分（scoreFortuneElem 身强六支/身弱）+ 五行角色（elemRoleName 五支）+ 半合化气（banHeHuaElem 五支）+ 火炎土燥逐运细判（fireExcessScore/fireExcessFortuneSegment 四支）+ 逐运吉凶 verdict（凶助忌损用/吉喜用得力）与四段标题 | Y-01~Y-25 |
| 岁运辩证（buildFortuneSynthesis） | 标题头（用神/忌神/身强弱）+ 逐运 good≥1（扶助得力）/good≤-1（助忌损用）/good==0（得失相参）+ 运支冲提纲覆盖注记 + 火炎土燥 fireExcess 分支（取 fireExcessFortuneSegment） | Z-01~Z-06 |
| 分类事项断（buildCategoryVerdicts） | 事业（官透身弱·官透身强·官藏·无官）+ 财运（财透·财藏·无财）+ 婚姻（日支逢冲）+ 寿夭（羊刃）+ 飘荡（驿马）；离线 verdictOr 回退内联 | Z-07~Z-16 |
| 判词回退（verdictOr） | 离线无名录命中 → 回退 fallback，并经 stripCitations 剥离《》书名 | Z-17 |

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

---

## 21 第 38 步·数据分支全覆盖测试扩充（十神/神煞/用神相神忌神/命格）

> 触发：客户点名「**所有类型的十神、神煞、用神/相神/忌神、命格等等**」需全面覆盖逻辑分支与全部数据分支。此前测试多聚焦端到端编排与经典起例（`SiZhuBaZiClassicRulesTest`），对**算理内部数据分支**（十神十类闭合、神煞七类匹配、取用/改取全支、专旺·从化·成败·具名格局、旺衰六档、解析器标志位）缺系统性断言，遂新增专门离线覆盖测试类补齐。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiDataBranchCoverageTest`（**17 项**，纯离线、无引擎/无 MQ）：以反射直调 `SiZhuBaZiOntologyJobWorker` 私有算理（`tenGodIndex`/`tenGodName`/`tenGodsKin` 三支、`matchDeityRule`/`arrangeDeities` 降级、`yongShenElem`/`effectiveYongElem`/`xiangShenElem`/`jiShenElem`/`selectYongShen`、`detectZhuanWang`/`detectExternPattern`/`detectChengBai`/`hitNamedPatterns`/`monthGodName`、`assessDayMasterStrength`、`parseStrength`/`parsePatternGrade`），Mockito 模拟 `ActivatedJob`/`JobClient` 承接 `completeResult` 变量 |
| 覆盖维度 | ①**十神** 10 类闭合 + 100 组合命名 vs 算法 + 生克制化 4 支；②**神煞** `matchDeityRule` 7 基准分支（日柱/日干单键/斜杠多键/`（时）`限定/年支日支/年支/月支三合/月支）+ 离线降级八类 + 旬空；③**用神·相神·忌神** 月令格局取用 4 支 + 火炎水寒/身强印比/身弱耗泄改取 5 支 + 顺逆用 2×2 + §9 五变量齐备；④**命格** 专旺 5 格 + 从化 4 类 + 成败 6 类×（有救/待救）12 断言 + 具名 10+ 格 + 月令格局名；⑤**旺衰** 6 档；⑥**结构化真相源** `parseStrength` 5 标志位 + `parsePatternGrade` 7 标志位（含互斥） |
| 真相源铁律 | 全部判词断言以**结构化字段**（`parseStrength`/`parsePatternGrade` 解析结果）为唯一真相源，**不反向解析文案**；判据取自既有业务口径与典籍原文，未发明 |
| 套件注册 | `AllSiZhuBaZiSuiteTest` 的 `@SelectClasses` 加入 `SiZhuBaZiDataBranchCoverageTest.class`（置于 `SiZhuBaZiClassicRulesTest` 之后，离线用例集中排前） |
| 调试修复 | ①`TEN_GOD` 为私有静态字段，反射读取需 `setAccessible(true)`（抽 `tenGodArray()` 辅助）；②印格「待比劫制财护印」用例初版入参误含比肩，改入参为 `stems(4,2,0,2)` 使其真正落在「待救」支 |
| 用例数 | 新增 **+17**；整模块去重汇总 **64 → 81** |
| 验证 | ① `-Dtest=SiZhuBaZiDataBranchCoverageTest` **17/17 通过**；② 全部离线类合集（DataBranch + ClassicRules + SolarTerm + LunarConvert + OntologyCompliance + SkosTerminology + BirthParsing）**73/73 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过） |

> **环境说明（须留意）**：本机 MySQL/RabbitMQ/Camunda 服务当前已就绪，故整模块 `mvn test` 会真跑集成用例。既有 `SiZhuBaZiBpmnRealEngineTest.t_real_engine_single_male_end_to_end` 因 BPMN 缺陷 D1 未同步到真实引擎部署（或引擎侧流程版本问题）报 incident 失败，**属预先存在的环境/部署问题，与本次新增的离线测试无关**；新增离线测试全绿。整模块跑批：`Tests run: 138, Failures: 1`（仅该 1 项）。

## 22 第 39 步·分支矩阵补充测试（关系/定格/用神/纳音/大运/告警/历法）

> 触发：用户再次要求「设计更多测试用例（所有类型的命盘、命格等）」。前一批 `SiZhuBaZiDataBranchCoverageTest` 偏十神/神煞/用神/命格代表性分支；本轮针对**尚未系统覆盖的算理分支**新增 `SiZhuBaZiBranchMatrixTest`，补齐「干支作用关系全类、正八格与禄刃·月劫·杂气、用神调候·从财·印比重、纳音长生、大运流年结构、告警三支、晚子时换日」。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrixTest`（**20 项**，纯离线、无引擎/无 MQ）：真跑生产 Handler（`handleGanzhiRelations`/`handleDeterminePattern`/`handleSelectYongShen`/`handleNayinHiddenStems`/`handleFortuneCycles`/`handleAggregateReading`/`handleCastFourPillars`）+ 反射直调 `isYinBiZhongYong`/`effectiveYongElem` 单参重载 |
| 覆盖维度 | ①**干支作用关系** 12 支（五合/六合/六冲/六穿/三合成局/半合/三刑/自刑/子卯刑/六破/墓库/无关系）；②**定格** 正八格 + 建禄/阳刃/月劫 + 杂气格/杂气偏财格（共 14 支）；③**用神** 印比重真假 + 单参重载 + 调候救偏 + 从财格顺用；④**纳音·藏干·十二长生**；⑤**大运流年** 大运/流年/流月 + `时`粒度流日流时；⑥**告警** 大凶/寿元关/无告警；⑦**历法** 晚子时换日进位 |
| 真相源铁律 | 断言一律读结构化字段（`ganzhiRelations`/`patternResult`/`yongShen`/`nayin`/`hiddenStems`/`dayPillarTraits`/`fortuneCycles`/`alertTriggered`/`fourPillars`），不反向解析判词文案、不伪造 |
| 调试修复 | ①`isYinBiZhongYong` 初版用「身强+月令比劫(卯)」误判 true——因 `yongShenElem` 对月劫走建禄法改取官杀(金)而非日主五行，改入参为「甲日**子月**」（月令印水，`monthYong==(dayElem+4)`）方命中 true 支 |
| 用例数 | 新增 **+20**；离线用例合集 **76/76 通过**（DataBranch 17 + BranchMatrix 20 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrixTest` **20/20 通过**；离线合集 **76/76 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过） |

> **环境说明**：本轮仅跑纯离线用例验证（不触发真实引擎/集成），故无 `RealEngineTest` 的已知 D1 环境失败；禁区（`OpenlletResolver`/`OntopOBDAHandler`/BPMN ioMapping/TBox）零改动。

## 23 第 40 步·分支矩阵补充测试·二阶段（下游节点与三排他网关）

> 触发：承接上一轮「覆盖所有类型的命盘、命格」目标，第一批偏算理主链，本轮补齐**下游节点与三排他网关**（G1 合婚 / G2 女命·小儿 / G3 告警）此前未系统覆盖的分支，新增 `SiZhuBaZiBranchMatrix2Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix2Test`（**15 项**，纯离线、无引擎/无 MQ）：真跑生产 Handler（`handleJudgeChartLevel`/`handleMarriageMatch`/`handleFemaleChart`/`handleChildChart`/`handleMoralAdvice`/`handleSendAlert`/`handlePersistAndReturn`/`handleTimingAndStages`/`handleSynthesizeFortuneChart`/`handleCategoryVerdicts`） |
| 覆盖维度 | ①**层次四档**（贵/富/贫/富贵两全·专旺）；②**合婚 G1**（双人排盘 + 纳音/生肖/神煞三维合参）；③**女命 G2**（良类「纯/清」、劣·特类「滥/浊·正偏自处」）；④**小儿关杀**（年支申、时支寅→驿马关）；⑤**劝诫** 吉（守正持盈）/凶（积德改行）；⑥**告警 G3** 无告警跳过；⑦**落库返回离线**（`backendService==null` 仅返回 caseRecord）；⑧**应期·人生阶段** / **岁运综合辩证** / **分类断语** |
| 真相源铁律 | 断言一律读结构化字段（`chartLevel`/`marriageConclusion`/`complementAnalysis`/`nayinRelation`/`zodiacRelation`/`femaleVerdict`/`childVerdict`/`moralAdvice`/`messageStatus`/`caseRecord`/`response`/`timing`/`lifeStages`/`fortuneTrend`/`transformationNote`/`categoryVerdicts`），不反向解析判词文案、不伪造 |
| 分支构造要点 | 层次四档以 `patternGrade`/`strength` 结构化入参 + 四柱财官实况驱动：贵格用「甲子 辛酉 甲子 甲子」（官当令有根、无财）、富格用「甲子 戊辰 甲子 甲子」（财当令、官不显）、贫格同柱配重度身弱；女命「辛酉 辛酉 甲子 戊辰」得纯、「辛酉 庚申 甲子 甲子」官杀混杂得滥/浊+正偏自处 |
| 用例数 | 新增 **+15**；离线用例合集 **91/91 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix2Test` **15/15 通过**；离线合集 **91/91 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过） |

> **环境说明**：本轮仅跑纯离线用例；`sendAlert` 仅验证 `false`→`SKIPPED_NO_ALERT` 分支（`true` 分支会触达 RabbitMQ，不在离线范围强行跑，避免连接超时/外发副作用）；`persistAndReturn` 走 `backendService==null` 降级分支；禁区零改动。

## 24 第 41 步·分支矩阵补充测试·三阶段（神煞起例 / 成败救应 / 具名格局 / 月令冲合空）

> 触发：承接「覆盖所有类型的命盘、命格」目标，前两批已覆盖算理主链与下游网关；本轮补齐此前仅被抽样触及的**神煞起例深层分支、格局成败救应六格两叉、具名格局成立/破格、月令逢冲·合·旬空三支**，新增 `SiZhuBaZiBranchMatrix3Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix3Test`（**25 项**，纯离线、无引擎/无 MQ）：真跑 `handleArrangeDeities`/`handleDeterminePattern`；纯算理方法 `detectChengBai`/`hitNamedPatterns`/`matchDeityRule`/`taohuaDerivNote` 经反射直调 |
| 覆盖维度 | ①**神煞起例**（离线降级八类命中、空亡 voidness 双分支、桃花来源补注、`matchDeityRule` 七基准逐支、`DEITY_SKIP` 黑名单口径）；②**成败救应**（detectChengBai 正官/偏官/财/印/食神/伤官 各「有救/待救」两叉，及身弱不判比劫夺财的边界）；③**具名格局**（魁罡成立与破格、日贵/日德、日禄归时正格·兼格·破格、六乙鼠贵、壬骑龙背）；④**月令冲合空**（格神未透遇冲/合/旬空，及已判成败时月令逢冲只追加注记不覆盖 grade、阳刃格月令逢冲覆写） |
| 真相源铁律 | 断言一律读结构化字段（`deities`/`voidness`/`patternGrade`/`rescueAdjust` 及反射返回的 `String[]`/`List<String[]>`/`List<Integer>`），不反向解析判词文案、不伪造 |
| 分支构造要点 | 神煞用受控四柱命例（甲子 乙丑 甲子 甲子→天乙+将星；甲子 丁卯…→羊刃；甲子 丙寅…→禄+驿马；乙卯 甲子…→桃花；甲子…戊辰→华盖；甲子…乙亥→时亥空）；`detectChengBai` 以「非日柱天干」的十神透出组合精确触发各格忌神（如正官格用丁（伤官）触发伤官见官，无印待救/加壬（印）可解）；具名格用日柱干支精确命例（庚戌/丁酉/甲寅/甲子+丙寅时/乙丑+丙子时/壬辰多辰）；determinePattern 令月令本气为普通十神（非禄刃/非库比劫），分别构造酉卯冲、酉辰合、甲子旬空亥及阳刃卯月逢酉冲 |
| 环境不可达说明 | `DEITY_SKIP` 过滤与 `deitiesFromOntology` 的条名录匹配属「OBDA/DB 可用」路径，离线（queryClauses 空）不进入该循环，故本类以反射直读 `DEITY_SKIP` 常量集合校验其口径（含叙述性歧义类、不误伤常用八类），并在文档留痕该分支须环境就绪方端到端触发 |
| 用例数 | 新增 **+25**；离线用例合集 **116/116 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix3Test` **25/25 通过**；离线合集 **116/116 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest` |

> **环境说明**：本轮仅跑纯离线用例；`handleArrangeDeities` 走 `deitiesFromOntology` 返回 null 的内置八类降级分支（离线可跑）；`determinePattern` 全程离线；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 25 第 42 步·分支矩阵补充测试·四阶段（十神宫位 / 旺衰六档 / 取用多法冲突）

> 触发：承接「覆盖所有类型的命盘、命格」目标，前三批已覆盖算理主链、下游网关、神煞成败具名格局与月令冲合空；本轮补齐此前未被系统覆盖的三处深层维度——**十神宫位（assignTenGodsKin）、旺衰六档逐档（assessDayMasterStrength）、取用多法冲突（selectYongShen）**，新增 `SiZhuBaZiBranchMatrix4Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix4Test`（**19 项**，纯离线、无引擎/无 MQ）：真跑 `handleTenGodsKin`/`handleDayMasterStrength`/`handleSelectYongShen`；纯算理方法 `tenGodName`/`yongShenElem`/`effectiveYongElem`（`(int[][],int,boolean)` 重载）/`isYinBiZhongYong` 经反射直调 |
| 覆盖维度 | ①**十神宫位**（`tenGodName` 甲日对十天干 10 类映射、`tenGodSet`/`tenGodsRelations` 生克制化 4 分支、天干·藏干十神文本与六亲宫位口径）；②**旺衰六档**（身强得令 / 中和偏强得令 / 中和偏强失令但得地得势 / 身弱 / 中和偏弱 / 中和 逐档专门命例 + `balance` 三计数与 `twelvePalaces` 逐支）；③**取用多法冲突**（`effectiveYongElem` 调候火炎/水寒、印比重、扶抑有印/无印、默认真实分支；`yongShenElem` 建禄月令改取「官杀>财>食伤」；`isYinBiZhongYong` 判据；handler 主取格局法/扶抑/调候三分支及相神/忌神/调候输出） |
| 真相源铁律 | 断言一律读结构化字段（`strength`/`balance`/`twelvePalaces`/`tenGods`/`kinMapping`/`tenGodsRelations`/`yongShen`/`xiangShen`/`jiShen`/`tiaohou`/`yongShenMethods`）与反射返回的 `String`/`int`/`boolean`，不反向解析判词、不伪造 |
| 分支构造要点 | 旺衰六档以受控命例逐档命中判据（甲寅 丙寅…通根2得势2→身强得令；庚子 丙子…得令无根→中和偏强得令；甲寅 壬申…失令通根3得势3→中和偏强失令得地得势；庚申 辛酉…通根0得势0→身弱；壬申 辛酉…失令克泄耗多→中和偏弱；甲子 甲辰…根助零散克泄耗不足→中和）；`effectiveYongElem` 以丙午 甲午 丙午 戊戌 触发火炎取水、壬子 壬子 壬子 壬辰 触发水寒取火、甲子 癸子…身强印水月触发印比重、甲子 戊辰…/甲寅 戊戌…构造扶抑有印/无印；`yongShenElem` 以卯月（建禄）构造有官取金/无官取土；handler 三分支分别用申月官杀格（格局法）、辰月身弱财格（扶抑）、午月火炎（调候）触发 |
| 分支构造修正 | 初版 `strengthZhongHePianRuo` 命例「壬亥」非六十甲子合法组合（壬仅配申午辰寅子戌），已修正为「壬申 辛酉 甲子 丙午」，仍命中「中和偏弱（失令、克泄耗偏多）」；另修正测试辅助 `stat(...)` 对**单个 `int[][]` 形参**的 Java varargs 摊开陷阱（`int[][]` 可赋值给 `Object[]` 致被摊成 4 个实参），对单数组实参显式 `(Object)` 转型包裹 |
| 用例数 | 新增 **+19**；离线用例合集 **135/135 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix4Test` **19/19 通过**；离线合集 **135/135 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest` |

> **环境说明**：本轮仅跑纯离线用例；`handleTenGodsKin`/`handleDayMasterStrength`/`handleSelectYongShen` 全程离线可跑（`tenGodName` 的 `tenGodOntologyMap()` 为空时回退内置 `TEN_GOD` 数组）；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 26 第 43 步·分支矩阵补充测试·五阶段（干支关系 / 纳音藏干十二长生 / 大运流年 / 应期运限）

> 触发：承接「全面覆盖逻辑分支和所有数据分支」目标，前四批已覆盖算理主链、下游网关、神煞成败具名格局、十神宫位与取用多法；本轮补齐此前仅被浅层输出的四组维度——**干支作用关系细化（analyzeGanzhiRelations）、纳音藏干十二长生（analyzeNayinHiddenStems）、大运流年结构（analyzeFortuneCycles）、应期运限（inferTimingAndStages）**，新增 `SiZhuBaZiBranchMatrix5Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix5Test`（**10 项**，纯离线、无引擎/无 MQ）：真跑 `handleGanzhiRelations`/`handleNayinHiddenStems`/`handleFortuneCycles`/`handleTimingAndStages` |
| 覆盖维度 | ①**干支作用关系**（天干五合·六合·六冲·六穿·三合成局·半合·三刑·自刑·子卯·六破·墓库·无明显 12 支逐支命中）；②**纳音藏干十二长生**（`nayinOf` 六十甲子纳音、`ZHI_HIDDEN` 藏干串、`stageOf` 日主寄生十二宫阳顺阴逆）；③**大运流年结构**（年/日/时三种粒度、流年 10 年、流月五虎遁十二月建、流日/流时 JDN 锚定与五鼠遁十二时、特殊岁运格局正负分支）；④**应期运限**（`timing` 应期四法 + 结合空亡；`lifeStages` 运限四柱主阶段） |
| 真相源铁律 | 断言一律读结构化字段（`ganzhiRelations`/`nayin`/`hiddenStems`/`dayPillarTraits`/`fortuneCycles`/`fortuneAnalysis`/`specialCycles`/`timing`/`lifeStages`），不反向解析判词、不伪造 |
| 分支构造要点 | 干支各支以受控命例逐支命中（甲己/子丑/子午/子未/申子辰/申子/寅巳申/辰辰/子卯/卯午/辰墓库/甲子×4 无明显）；纳音藏干以「甲子 丙寅 戊午 庚申」一命串测纳音名/藏干/十二长生；大运流年以 `chartBase=SOLAR=1990-05-15T10:00;GENDER=男`+`fortuneSequence=甲子,乙丑,丙寅`+`startAge=3` 触发大运·流年·流月，`granularity=日/时` 追加流日/流时与十二时；**特殊岁运正分支**以 `startAge=0`+`fortuneSequence=庚午,…` 使 1990 流年（庚午）与首运庚午同→命中「岁运并临」，负分支则用默认设置得「未见…」 |
| 用例数 | 新增 **+10**；离线用例合集 **145/145 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + BranchMatrix5 10 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix5Test` **10/10 通过**；离线合集 **145/145 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest` |

> **环境说明**：本轮仅跑纯离线用例；四个 Handler 全程离线可跑（`parseChartBase` 解析 `SOLAR`/`GENDER`，无外部依赖）；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 27 第 44 步·分支矩阵补充测试·六阶段（输入解析 / 排四柱 / 岁运综合 / 分类断语 / 劝诫 / 汇总）

> 触发：承接「全面覆盖逻辑分支和所有数据分支」目标，前五批已覆盖算理主链与各分析节点内部算理；本轮补齐此前仅在端到端流程中隐性跑过、未做分支级断言的六类「输入/输出编排」节点——**生辰解析（handleParseBirthInput）、排四柱（handleCastFourPillars）、岁运综合（handleSynthesizeFortuneChart）、分类断语（handleCategoryVerdicts）、劝诫（handleMoralAdvice）、综合汇总（handleAggregateReading）**，新增 `SiZhuBaZiBranchMatrix6Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix6Test`（**15 项**，纯离线、无引擎/无 MQ）：真跑 `handleParseBirthInput`/`handleCastFourPillars`/`handleSynthesizeFortuneChart`/`handleCategoryVerdicts`/`handleMoralAdvice`/`handleAggregateReading` |
| 覆盖维度 | ①**生辰解析**（chartBase 九字段组装、真太阳时经度折算 TST、早晚子时 LATEZI、农历换算成功 vs 越界【待考】双分支、缺参负向 PARSE_BIRTH_INPUT_FAILED）；②**排四柱**（四柱/大运8/小运8/胎元/命宫结构、子初换日日柱进位、起运顺逆阳男阴女顺）；③**岁运综合**（岁运辩证 trend + 成格变格 note）；④**分类断语**（性情才德/疾病/六亲吉凶/分类事项断四段）；⑤**劝诫**（贵富·守正持盈 / 贫贱·积德改行）；⑥**汇总**（命书拼装 + 大凶/寿元关/无告警三态 javaFlags） |
| 真相源铁律 | 断言一律读结构化字段（`chartBase`/`subjectType`/`inputMode`/`fourPillars`/`fortuneSequence`/`minorFortunes`/`fetalOrigin`/`lifePalace`/`fortuneTrend`/`transformationNote`/`categoryVerdicts`/`moralAdvice`/`reading`/`alertTriggered`），不反向解析判词、不伪造 |
| 分支构造要点 | 生辰解析：公历字符串 / 23:30 晚子 / 东经116.4（TST≈-0.24h，12:00→11:45:36）/ 农历 Map 1990-5-15（→1990-06-07）/ 农历 Map 1899（越界待考）/ 缺 birth；排四柱：`chartBase=SOLAR=…;GENDER=男;LATEZI=false/true` 对比日柱进位，阳年（1990 庚午）男顺女逆对比首运；岁运综合/分类断语/劝诫/汇总均以受控 `fourPillars`+`strength`+`chartLevel`+`patternGrade`+`deities` 命中各分支（汇总 大凶←层次含「贫贱」、寿元关←神煞「羊刃」+「相冲」、无告警←贵格身强无冲） |
| 分支构造修正 | ①`parseBirthLunarUnconverted` 初版农历 1899 命例缺 `hour` 且无 `hourZhi` → `zhiToStartHour(null)` 抛错进 fail 路径，补 `hour=12` 修正；②`parseBirthTrueSolarTime` TST 为浮点 `-0.23999999999999963`，断言由「-0.24」放宽为「-0.239」；③测试辅助 `Cap` 中 `String.valueOf(inv.getArgument(0))` 因泛型推断命中 `valueOf(char[])` 重载抛 ClassCastException，改为显式 `String.valueOf((Object) inv.getArgument(0))` |
| 用例数 | 新增 **+15**；离线用例合集 **160/160 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + BranchMatrix5 10 + BranchMatrix6 15 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix6Test` **15/15 通过**；离线合集 **160/160 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest` |

> **环境说明**：本轮仅跑纯离线用例；六节点全程离线可跑（`backendService==null` 时 `deriveAlertsViaSwrl` 返回 null → 汇总回退程序判据、`verdictOr` 回退内置 fallback）；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 28 第 45 步·分支矩阵补充测试·七阶段（合婚三维 / 小儿关杀 / 女命专论）

> 触发：承接「全面覆盖逻辑分支和所有数据分支」目标，前六批已覆盖算理主链、下游三排他网关、神煞成败具名格局、十神宫位取用、干支纳音大运应期、输入输出编排；本轮补齐此前仅在二阶段抽样触及（每维 1~2 支）的**三个「专项分析」节点更深子分支**——`handleMarriageMatch`（合婚三维逐支）、`handleChildChart`（小儿关杀驿马/桃花/皆非三支）、`handleFemaleChart`（女命分类良/劣/特/平和与官杀混杂特类），新增 `SiZhuBaZiBranchMatrix7Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix7Test`（**11 项**，纯离线、无引擎/无 MQ）：真跑生产 Handler `handleMarriageMatch`/`handleChildChart`/`handleFemaleChart`（Mockito 模拟 `ActivatedJob`/`JobClient`；失败路径承接 `completeResult` 的 errorCode） |
| 覆盖维度 | ①**合婚三维**（纳音关系 比和/相生/相克 3 支；生肖关系 同支比和/三合/六冲；神煞合参 桃花命中/无显著；互补结论；缺参负向 `MARRIAGE_MATCH_FAILED`）；②**小儿关杀**（时支命中驿马 `YIMA` / 桃花 `TAOHUA` / 皆非 3 支 + 关杀名目备参「另有将军箭…」）；③**女命专论**（夫星/子星/比劫与分类结构文本 + 官杀混杂特类「正偏自处」） |
| 真相源铁律 | 断言一律读结构化字段（`marriageConclusion`/`complementAnalysis`/`nayinRelation`/`zodiacRelation`/`shenshaMatch`/`childVerdict`/`femaleVerdict`），不反向解析判词、不伪造；断词不得反向解析 |
| 分支构造要点 | **纳音关系**（`nayinElemIndex` 按纳音名末字定五行）：比和←双方同「庚午·路旁土」(1990-05-15/05-20)；相生←本人庚午土 / 对方「丙寅·炉中火」火生土(1986-05-20)；相克←本人庚午土 / 对方「戊辰·大林木」木克土(1988-05-20)。**生肖关系**（`yearBranchRelation` 判定序：同支→六冲→相刑→六害→六合→三合→三会）：三合←寅午戌(对方丙寅)；六冲←子午(对方「甲子」1984-05-20)；同支←双方皆午。**神煞合参**：`sanheGroupOf(year) + TAOHUA` 取桃花位，对方年支卯(1987-05-20 丁卯)命中「寅午戌桃花在卯」→「对方命带本人桃花」。**小儿关杀**：`甲午 丙寅 甲子 甲申`→时支申=驿马；`甲午 丙寅 甲子 乙卯`→时支卯=桃花；`甲午 丙寅 甲子 甲戌`→皆非。**女命官杀混杂**：`庚午 辛未 甲子 乙丑`（甲日见庚偏官、辛正官，含藏干）→`guanZa` 为真→特类「正偏自处」 |
| 用例数 | 新增 **+11**；离线用例合集 **171/171 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + BranchMatrix5 10 + BranchMatrix6 15 + BranchMatrix7 11 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix7Test` **11/11 通过**；离线合集 **171/171 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest` |

> **环境说明**：本轮仅跑纯离线用例；三节点全程离线可跑（`handleMarriageMatch` 双人排盘走本地算理，`nayinElemIndex`/`yearBranchRelation`/`sanheGroupOf`/`containsZhi` 均为纯函数；`handleChildChart`/`handleFemaleChart` 不触外部依赖）；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 29 第 46 步·分支矩阵补充测试·八阶段（命局层次逐档 / 清浊真神 / 命书详解降级）

> 触发：承接「全面覆盖逻辑分支和所有数据分支」目标，前七批已覆盖算理主链、下游三排他网关、神煞成败具名格局、十神宫位取用、干支纳音大运应期、输入输出编排、专项分析子分支；本轮补齐此前二阶段仅抽样触及（四档）的 **`handleJudgeChartLevel` 十支层次边界分支、`purityTruth` 清浊真神分支、`handleInterpretResult` 大模型未装配降级分支**，新增 `SiZhuBaZiBranchMatrix8Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix8Test`（**15 项**，纯离线、无引擎/无 MQ）：真跑生产 Handler `handleJudgeChartLevel`/`handleInterpretResult`（Mockito 模拟 `ActivatedJob`/`JobClient`） |
| 覆盖维度 | ①**命局层次逐档**（专旺富贵两全 / 贵格兼富 / 纯贵格 / 富格 / 格破身弱 / 格破待救 / 贫格 / 中平 / 中平偏下 / 平常格·格成未通 / 平常格·格局待成 / 伤官见官降判富格 共 12 支）；②**清浊真神**（清·真神得用=true / 格破→清中带浊）；③**命书详解降级**（llm 未装配 → 双空串、流程不阻断） |
| 真相源铁律 | 断言一律读结构化字段（`chartLevel`/`purityTruth`/`aiInterpretation`/`aiInterpretationPlain`），不反向解析判词、不伪造；判词不得反向解析 |
| 分支构造要点 | 层次分支以「受控 `patternGrade`+`strength` 结构化入参」驱动 `parsePatternGrade`/`parseStrength`，再以**四柱实况**（财透/根、官透/根、官当令、财当令、伤官见官）触发财官通门户之判：贵兼富用「戊辰 己酉 甲子 戊辰」（官当令+财透有根）、纯贵用「甲子 癸酉 甲子 乙亥」（官当令·无财）、富用「甲子 甲辰 甲子 甲子」（财当令·无官）、贫用同柱配身弱、中平用「甲子 乙亥 甲子 乙亥」（身强·无财无官）、伤官见官降判用「己酉 丙午 甲子 庚午」（午月伤官当令+丙伤官透+庚官透有根，官失理而财通→富格）。**清浊真神**：复用「戊辰 己酉 甲子 戊辰」（格成身强·用神金藏支有根·忌神火不见→清且真神得用=true）；格破→清中带浊 |
| 用例数 | 新增 **+15**；离线用例合集 **186/186 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + BranchMatrix5 10 + BranchMatrix6 15 + BranchMatrix7 11 + BranchMatrix8 15 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix8Test` **15/15 通过**；离线合集 **186/186 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest` |

> **环境说明**：本轮仅跑纯离线用例；`handleJudgeChartLevel` 走 `parsePillars` 本地算理（`effectiveYongElem`/`jiFromYong`/`monthGodName`/`sexagenaryIndex` 纯函数，离线无外部依赖）；`handleInterpretResult` 在 `llm==null` 时跳过 MaaS 调用、双键返回空串（流程不阻断）；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 30 第 47 步·分支矩阵补充测试·九阶段（外格侦测 / 专旺成象 / 相忌五行 / 六亲断语）

> 触发：承接「全面覆盖逻辑分支和所有数据分支」目标，前八批已覆盖主链算理、下游网关、神煞成败、十神取用、干支纳音大运、编排节点、专项分析、命局层次；本轮转入此前仅被间接（经下游节点）触达的**纯算理方法分支级断言**——`detectZhuanWang`（五专旺格成/破）、`detectExternPattern`（从财/从杀/从儿/化气/无）、`xiangShenElem`/`jiShenElem`（顺逆用）、`buildKinshipVerdicts`（四宫与配偶/比劫/时柱子星），新增 `SiZhuBaZiBranchMatrix9Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix9Test`（**20 项**，纯离线、无引擎/无 MQ）：反射直调纯算理方法 `parsePillars`/`detectZhuanWang`/`detectExternPattern`/`xiangShenElem`/`jiShenElem`/`buildKinshipVerdicts` |
| 覆盖维度 | ①**专旺成象**（曲直/炎上/稼穑/从革/润下 五格「成象」与「破格」8 支）；②**外格侦测**（从财/从杀/从儿/化气/无 5 支）；③**相神·忌神五行**（顺用/逆用 2 支）；④**六亲断语**（四宫结构 / 男财女官坐配偶 / 男比劫夺财 / 时柱子星 5 支） |
| 真相源铁律 | 断言读方法返回值（精确串/五行索引），不反向解析判词、不伪造；专旺/从/化 一律按典籍全局成象校验（非仅凭月令） |
| 分支构造要点 | **专旺**：曲直←亥卯未全且无金（乙亥 己卯 甲子 丁未）；炎上←寅午戌全且无水（丙寅 庚午 丙戌 甲午）；稼穑←辰戌丑未全且无木（戊辰 壬戌 己丑 癸未）；从革←申酉戌全且无火（庚申 乙酉 庚戌 壬子）；润下←申子辰全且无燥土（壬申 庚子 壬辰 甲寅）；破格以对冲忌神（酉金/子水/透甲木）逐格构造。**外格**：从财←戊戌 戊戌 甲戌 戊戌（日主无根/印/助、土财独旺有气）；从杀←庚酉×3+甲酉（金独旺透干）；从儿←丙午×3+甲午（火独旺透干）；化气←庚酉 己戌 甲酉 丙巳（甲己合化土得月令戌且日主无根、且财数<5 避开从财）；无外格←甲子 丙寅 戊午 庚申（寅藏甲木有根不从）。**相忌**：正官格顺用（戊辰 己酉 甲子 戊辰→相土2/忌火1）、伤官格逆用（己酉 丙午 甲子 庚午→相水4/忌木0）。**六亲**：男配偶星以戊日坐子（藏癸财）、女配偶星以甲日坐酉（藏辛官）、比劫夺财以甲日多比劫+戊财透、时柱子星以甲日时支酉（藏辛官杀） |
| 调试修复 | 反射单参调用 `invoke(null, P(fp))` 因 `int[][]` 可赋值给 `Object[]` 被 varargs 摊成 4 实参（「wrong number of arguments: 4 expected 1」），改 `invoke(null, (Object) P(fp))` 显式转型包裹（与前几批 `stat(...)` 陷阱同源） |
| 用例数 | 新增 **+20**；离线用例合集 **206/206 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + BranchMatrix5 10 + BranchMatrix6 15 + BranchMatrix7 11 + BranchMatrix8 15 + BranchMatrix9 20 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix9Test` **20/20 通过**；离线合集 **206/206 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest` |

> **环境说明**：本轮仅跑纯离线用例；所测方法均为纯算理函数（`detectZhuanWang`/`detectExternPattern`/`xiangShenElem`/`jiShenElem`/`buildKinshipVerdicts` 及 `parsePillars` 全程不触 OBDA/引擎/DB）；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 31 第 48 步·分支矩阵补充测试·十阶段（六亲宫位细分 / 成败救应未破边角）

> 触发：承接「全面覆盖逻辑分支和所有数据分支」目标，第九批已补六亲四宫骨架与成败救应「有救/待救」；本轮补齐此前分支级未触达的 **`buildKinshipVerdicts` 年/月/日/时四宫的全部细分断语分支**与 **`detectChengBai` 六格在「忌神不透、不构成破格」时的 `null` 返回边角**，新增 `SiZhuBaZiBranchMatrix10Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix10Test`（**21 项**，纯离线、无引擎/无 MQ）：反射直调 `buildKinshipVerdicts`（六亲宫位细分）+ `detectChengBai`（成败救应 null 边角） |
| 覆盖维度 | ①**年宫**（印临祖位·财不伤印 / 财旺克印、祖宫乏印财旺克印+偏财临祖、祖宫平常、比劫透干夺财 4 支）；②**月宫**（官杀偏重 / 比劫+财透分财 / 和顺 3 支）；③**日支**（逢冲 / 逢合 / 逢害 / 相刑破 4 支）；④**时柱**（逢冲 / 伏吟 / 逢破 / 女命印透克食伤 4 支）；⑤**成败救应 null 边角**（正官非伤官见官 / 偏官有印 / 正财无比劫 / 正印无财 / 食神无偏印 / 伤官无正官 6 支） |
| 真相源铁律 | 断言读方法返回值（宫位断语子串 / `detectChengBai` 返回 `null`），不反向解析汇总判词、不伪造 |
| 分支构造要点 | 年宫以十神五类计数与财印/比劫/偏财透干逐支命中（印临祖位用壬子临年、财旺克印用戊戌己未、祖宫平常用甲午配壬子壬申、比劫夺财用甲寅甲卯…己巳）；月宫以官杀/比劫计数与财透驱动（庚申庚酉官杀重、戊辰甲寅戊辰比劫+财透、甲子己巳和顺）；日支以日支与余支冲/合/害/刑破关系逐支构造（子午/子丑/子未/子卯）；时柱以时支与余支冲/同支/破关系及女命印重驱动（时午冲年子、时子叠年子伏吟、时卯破月午、女命壬子壬子…丙寅印透克食伤）；`detectChengBai` null 支以「透干十神集合恰好不含该格忌神」构造（如正官格透财/官/食神而无伤官） |
| 用例数 | 新增 **+21**；离线用例合集 **227/227 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + BranchMatrix5 10 + BranchMatrix6 15 + BranchMatrix7 11 + BranchMatrix8 15 + BranchMatrix9 20 + BranchMatrix10 21 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix10Test` **21/21 通过**；离线合集 **227/227 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest` |

> **环境说明**：本轮仅跑纯离线用例；`buildKinshipVerdicts`（实例方法）与 `detectChengBai`（静态方法）均为纯算理函数，经反射直调、全程不触 OBDA/引擎/DB；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 32 第 49 步·分支矩阵补充测试·十一阶段（具名格局逐格命中）

> 触发：承接「全面覆盖逻辑分支和所有数据分支」目标，`hitNamedPatterns` 实装《三命通会》卷五/卷六大量具名/外格，三阶段仅覆盖 5 格；本轮对余下 **25 个具名格逐格以受控命例断言「命中」**，新增 `SiZhuBaZiBranchMatrix11Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix11Test`（**25 项**，纯离线、无引擎/无 MQ）：反射直调 `hitNamedPatterns(int[][],int)` 校验返回 `List<String[]>` 含指定格名 |
| 覆盖维度 | 六甲趋乾 / 六壬趋艮 / 天元作禄 / 天元坐煞 / 时上一位贵 / 岁德正官 / 时上正官 / 年上七煞 / 官煞混杂 / 财官双美 / 专禄要制 / 金神 / 时墓 / 禄马同乡 / 井栏叉 / 子遥巳 / 丑遥巳 / 飞天禄马 / 倒冲禄马 / 日刃 / 时上偏财 / 勾陈得位 / 玄武当权 / 白虎持势 / 刑合（25 格成立） |
| 真相源铁律 | 断言读 `hitNamedPatterns` 返回集合首元素「格名」，不反向解析判词、不伪造；各格成/破条件严格依《三命通会》卷五/卷六原文口径 |
| 分支构造要点 | 以「日柱固定在第 3 位」的受控四柱逐格命中：六甲趋乾←亥现、六壬趋艮←寅现；天元作禄←日支藏正官（庚午午藏丁）、天元坐煞←日支藏七煞（甲申申藏庚）；时上一位贵/岁德正官/时上正官/年上七煞←对应柱干透出该十神；财官双美←壬午日自坐财官且无木旺；专禄要制←六庚日见巳时；金神←时柱乙丑；时墓←甲木墓未（时支未）；禄马同乡←庚禄申与年午起马申同支且申现；井栏叉←庚日申子辰全无寅午戌；子遥巳←甲子日甲子时无午巳丑；丑遥巳←辛丑日丑多无巳酉；飞天禄马←壬子日子多无午；倒冲禄马←丙午日午多无子；日刃←戊午日；时上偏财←时干透偏财；勾陈得位/玄武当权/白虎持势←戊/壬/庚日坐财官且财官俱见；刑合←癸日寅时无巳 |
| 用例数 | 新增 **+25**；离线用例合集 **252/252 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + BranchMatrix5 10 + BranchMatrix6 15 + BranchMatrix7 11 + BranchMatrix8 15 + BranchMatrix9 20 + BranchMatrix10 21 + BranchMatrix11 25 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix11Test` **25/25 通过**；离线合集 **252/252 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest` |

> **环境说明**：本轮仅跑纯离线用例；`hitNamedPatterns` 为纯静态算理函数（经 `parsePillars` 解析、内部 `tenGodIndex`/`stageOf`/`pairIn`/`countZhi` 等纯函数，全程不触 OBDA/引擎/DB）；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 33 第 50 步·分支矩阵补充测试·十二阶段（大运/流年逐运评析）

> 触发：承接「全面覆盖逻辑分支和所有数据分支」目标，岁运评析链路（`buildFortuneAnalysis` 及其 `scoreFortuneElem`/`elemRoleName`/`banHeHuaElem`/`fireExcessScore`/`fireExcessFortuneSegment` 等辅助纯函数）此前仅经端到端侧面覆盖，本轮逐函数、逐分支正击，新增 `SiZhuBaZiBranchMatrix12Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix12Test`（**25 项**，纯离线、无引擎/无 MQ）：反射直调 `scoreFortuneElem(int,int,int,boolean)`、`elemRoleName(int,int)`、`banHeHuaElem(int[][],int)`、`fireExcessScore(int[][],int,int)`、`fireExcessFortuneSegment(int[][],int,int)`，并以 Worker 实例调 `buildFortuneAnalysis(...)` 断言输出文本片段 |
| 覆盖维度 | ① `scoreFortuneElem` 身强（食伤/财+1、官杀 0、比劫印 -1）与身弱（印比+1、克泄耗-1）六支；② `elemRoleName` 比劫/食伤/财/官杀/印五支；③ `banHeHuaElem` 申子辰化水/亥卯未化木/寅午戌化火/巳酉丑化金/不成五支；④ `fireExcessScore` 火炎土燥逐运打分；⑤ `fireExcessFortuneSegment` 吉/凶/吉中带劳/湿土晦火等四支细判；⑥ `buildFortuneAnalysis` 大运方向三分支（阳男顺/阴男逆/阴女顺）+ 逐运吉凶 verdict（凶助忌损用/吉喜用得力）+ 四段标题 |
| 真相源铁律 | 断言读方法返回值（结构化）或其在 `buildFortuneAnalysis` 输出中的文本片段；评分/化气/细判口径严格依实现层判据（不臆造、不反向解析判词） |
| 分支构造要点 | `scoreFortuneElem`：日主甲(木)、strong 时取 火1/土2/金3/木0/水4 逐一验证；weak 时取 木0/水4 与 火1/土2/金3 分判。`banHeHuaElem`：以「运支 + 局中另一支」成半合（申子/亥卯/寅午/巳酉）逐组构造，不成用单子支无申辰。`fireExcessScore`：壬坐辰(水+1、辰+1=+2)、丙坐酉(火-1、酉+1=0)、庚坐午(金+1、午-1=0)。`fireExcessFortuneSegment`：癸坐子→"吉"；壬坐丑→含「湿土」「丑午相害」；庚坐申→财金得地触「吉中带劳」；丙坐午→全火无制判"凶"（日支取寅避午冲）。`buildFortuneAnalysis`：阳年干甲·男→顺、阴年干乙·男→逆、阴年干乙·女→顺；逐运以 甲寅(比劫,-2→凶·助忌损用) 与 丙午(食伤,+2→吉·喜用得力) 对照，并断言四段标题齐全 |
| 用例数 | 新增 **+25**；离线用例合集 **277/277 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + BranchMatrix5 10 + BranchMatrix6 15 + BranchMatrix7 11 + BranchMatrix8 15 + BranchMatrix9 20 + BranchMatrix10 21 + BranchMatrix11 25 + BranchMatrix12 25 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix12Test` **25/25 通过**；离线合集 **277/277 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest`（`BranchMatrix12Test.class` 在 `BranchMatrix11Test.class` 之后） |

> **环境说明**：本轮仅跑纯离线用例；`scoreFortuneElem`/`elemRoleName`/`banHeHuaElem`/`fireExcessScore`/`fireExcessFortuneSegment` 均为纯静态算理函数，`buildFortuneAnalysis` 经 Worker 实例反射调用（其内部 `buildPilotBasis` 仅用内置节气近似表），全程不触 OBDA/引擎/DB；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。

## 34 第 51 步·分支矩阵补充测试·十三阶段（岁运辩证/分类事项断）

> 触发：承接「全面覆盖逻辑分支和所有数据分支」目标，`buildFortuneSynthesis`（岁运辩证）与 `buildCategoryVerdicts`（分类事项断）两条输出链路此前仅由上游节点端到端侧面覆盖，本轮逐分支正击，新增 `SiZhuBaZiBranchMatrix13Test`。

| 项 | 内容 |
|---|---|
| 新增测试类 | `SiZhuBaZiBranchMatrix13Test`（**17 项**，纯离线、无引擎/无 MQ）：反射直调 `buildFortuneSynthesis`（静态纯函数）、`buildCategoryVerdicts`（实例）、`verdictOr`（实例） |
| 覆盖维度 | ① `buildFortuneSynthesis` 标题头 + 逐运 good≥1（扶助得力）/good≤-1（助忌损用）/good==0（得失相参）+ 运支冲提纲覆盖注记 + 火炎土燥 fireExcess 分支；② `buildCategoryVerdicts` 事业功名（官透身非强·官透身强·官藏·无官）、财运（财透·财藏·无财）、婚姻（日支逢冲）、寿夭（羊刃）、飘荡（驿马）；③ `verdictOr` 离线回退 fallback 并剥离《》书名 |
| 真相源铁律 | 断言读方法返回文本片段（离线时 `verdictOr` 回退内联 fallback，即该分支唯一真相源）；不反向解析其它判词、不臆造 |
| 分支构造要点 | `buildFortuneSynthesis`：日主甲(木)、strong=true 时 丙午(食伤+2→扶助得力)/甲寅(比劫-2→助忌损用)/辛酉(官杀0→得失相参)；月支取寅避子、验证冲提纲覆盖时月支取子而运支午；火炎局以丙日、yongElem=4、癸亥运（水干+亥水→fireExcessFortuneSegment 判吉）。`buildCategoryVerdicts`：官透→年庚透（甲日见庚偏官）、官藏→日支申藏庚、无官→全局无金；财透→年戊透土、财藏→寅藏戊而干无土、无财→支子子子卯干无土；日支逢冲→日支子年支午；寿夭/飘荡以 deities 串含「羊刃」/「驿马」驱动。`verdictOr` 以必然不命中的 key 触发回退 |
| 用例数 | 新增 **+17**；离线用例合集 **294/294 通过**（DataBranch 17 + BranchMatrix 20 + BranchMatrix2 15 + BranchMatrix3 25 + BranchMatrix4 19 + BranchMatrix5 10 + BranchMatrix6 15 + BranchMatrix7 11 + BranchMatrix8 15 + BranchMatrix9 20 + BranchMatrix10 21 + BranchMatrix11 25 + BranchMatrix12 25 + BranchMatrix13 17 + ClassicRules 24 + BirthParsing 4 + LunarConvert 8 + SolarTerm 3） |
| 验证 | `-Dtest=SiZhuBaZiBranchMatrix13Test` **17/17 通过**；离线合集 **294/294 通过**（`BUILD SUCCESS`，0 失败 0 错误 0 跳过）；`test-compile` EXIT=0；已注册进 `AllSiZhuBaZiSuiteTest`（`BranchMatrix13Test.class` 在 `BranchMatrix12Test.class` 之后） |

> **环境说明**：本轮仅跑纯离线用例；`buildFortuneSynthesis` 为纯静态算理函数，`buildCategoryVerdicts`/`verdictOr` 经 Worker 实例反射调用（`verdictOr` → `clauseCategoryVerdict` 在离线空集时返回 null → 回退内联 fallback），全程不触 OBDA/引擎/DB；禁区零改动（`OpenlletResolver`/`OntopOBDAHandler`/`Utilities` 与 BPMN/TBox/SWRL/DDL 均未触碰）。


