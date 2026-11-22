# ZiWeiDouShu 应用 — 测试设计文档

> **用法**：本模板产出第 8 步。**闸门⑤**：业务人员/负责人确认本文档后方可固化测试用例。
> **基线**：`OntologyFrameworkPizza/design/披萨应用（Pizza）测试用例设计文档.md`（形态）；用例基线 `OntologyFrameworkExample/src/test/java/.../{ExampleBpmnRealEngineTest, ExampleSwrlRabbitMqTest, AllExampleSuiteTest}.java`。
> **上游**：第 6 步单文件本体；第 9 步将 TBox/ABox 分离为两个 owl；第 14 步执行词汇表与全流程测试。
> **阶段说明**：本文档为**设计先行**——用例编号、方法名、输入、断言为**冻结预案**（据第 2–7 步定稿工件推导，未臆造）；闸门⑤确认后据此在 `src/test/java/.../ziweidoushu/**` 固化测试类，**最终以测试源码为准**。

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkZiWeiDouShu / ZiWeiDouShu（紫微斗数排盘与批命本体应用） |
| 文档版本 | v2（第 8 步新模板：覆盖矩阵含「覆盖分支/经典依据」+ 新增 §8 经典校验与迭代记录） |
| 生成日期 | 2026-10-31 |
| 关联本体 | `ontology/ziweidoushu-all.owl`（938 行，TBox + 典型 ABox + SWRL 良构子集 R1/R8/R8b/R21）；第 9 步分离出 `ziweidoushu-tbox.owl`/`ziweidoushu-abox.owl`；术语表 `ontology/ziweidoushu-terminology.ttl`（第 13 步） |
| 关联流程 | 《ZiWeiDouShu应用-流程设计》；`ontology/bpmn/ziweidoushuProcess.bpmn`（`ZiWeiDouShuChartProcess`），Camunda 8 / Zeebe |
| 测试代码位置 | `src/test/java/com/ocean/ontologyframework/ziweidoushu/`（`ziweidoushu` 包 + `validation` 子包） |
| 测试资源位置 | `src/test/resources/ziweidoushuInstances.json` |
| 关联设计文档 | 《ZiWeiDouShu应用-顶层设计》《-流程设计》《-本体顶层设计》《-本体核心设计》《-应用架构设计》（闸门④已回填 v2） |

> 本文档所有元素 ID、jobType、约束、SWRL 规则**逐条取自第 2–7 步定稿工件**（BPMN 实际文件、本体核心设计 §4/§5、应用架构设计 §4），未作发明。最终以测试源码与定稿设计文档为准。

---

## 1. 目的与范围

**目的**：明确 ZiWeiDouShu 应用的质量保证方式——测什么、怎么测、用什么数据、判定依据、如何执行。

**范围**：
- 覆盖 BPMN 流程端到端编排（22 个 serviceTask 主干顺序、5 个排他网关 + 4 个汇合点分支、验盘闭环回流、无 incident）；
- 覆盖 Openllet 推理（TBox 子类检索、SWRL 良构子集 R1/R8/R8b/R21 推导与动态响应、一致性校验）；
- 覆盖 SWRL 触发回调与 RabbitMQ 告警消息投递（exchange `ziweidoushu.alert.exchange` / queue `ziweidoushuQueue`）；
- 覆盖 OWL 本体合规校验（JSON 命例逐条验证 `owl:Restriction` 并输出不合规原因）与 SKOS 术语结构/交叉校验（第 13/14 步）。

**不含**：
- 第 9~12 步的 TBox/ABox 分离、拆分、入库（OBDA 读写）、全集扩展——分别于各自步骤完成后纳入回归；
- 前端 UI 交互（`static/index.html`）与 LLM 批注生成（`ChatAgent` 非确定性内容不作断言）。

---

## 2. 被测系统概述

### 2.1 业务链路（`ZiWeiDouShuChartProcess`，纯 `serviceTask`，无 `userTask`）

```
Start_01
  └─ 节点1 Task_ParseInput         输入解析与时辰校正（历法互转/真太阳时/昼夜/登记类别）
  └─ 节点2 Task_LifePalaces        定命身宫与十二宫（命宫/身宫/五行局/命主身主）
  └─ 节点3 Task_MainStars          安十四主星
  └─ 节点4 Task_AuxStars           安辅佐煞忌星
  └─ 节点5 Task_MiscStars          安杂曜与太岁诸神（出生年流曜基准）
  └─ 节点6 Task_FourTransform      安四化与宫干、十二长生
  └─ G1 Gateway_Mode（输入模式）
        ├─ "直接排盘" →─┐
        └─ "验盘" → 节点V Task_VerifyChart 验盘校正
                      └─ G1b Gateway_ChartVerdict
                            ├─ "需校正" → 回流节点2（闭环，轮次上限 5）
                            └─ "无需校正" →─┤
                                            └ 汇合 J1 Gateway_MergeJ → 节点7
  └─ 节点7 Task_Brightness         定庙旺利陷与星辰生克
  └─ 节点8 Task_StarNature         星曜化气与诸星所主
  └─ 节点9 Task_EmptyPalace        空宫处理（借星）
  └─ 节点10 Task_Surround          三方四正·夹宫·星曜组合
  └─ 节点11 Task_Pattern           格局判定（成格/命格高低/四化飞星）
  └─ 节点12 Task_Natal             本命综合断
  └─ G2 Gateway_Subject（命主类别）
        ├─ "成人女" → 节点W Task_FemaleReading 女命专论 →──┤
        ├─ "小儿"  → 节点C Task_ChildReading  小儿命专论 →──┤
        └─ "成人男" →────────────────────────────────┤
                                                      └ 汇合 J2 Gateway_SubjectMerge
  └─ G3 Gateway_Depth（推演深度）
        ├─ "本命"               →──────────────────────────────────────┤
        ├─ "本命+大限"           → 节点13 Task_MajorLimit →──────────────┤
        └─ "本命+大限+流年"      → 节点13 → 节点14 Task_AnnualFortune →
                                    节点15 Task_Overlay →────────────────┤
                                                                        └ 汇合 J3 Gateway_DepthMerge → 节点16
  └─ 节点16 Task_Aggregate         综合结论汇总（写 alertTriggered）
  └─ G4 Gateway_Level（吉凶级别）
        ├─ alertTriggered=true  → 节点A Task_SendAlert 外发告警 →──┤
        └─ alertTriggered=false →────────────────────────────────┤
                                                                 └ 汇合 J4 Gateway_LevelMerge → 节点18
  └─ 节点18 Task_Interpret         命书详解与白话解读（大模型·不可用降级空串不阻断）
  └─ 节点17 Task_PersistReturn     结果落库与返回（命例记录，脱敏）
  └─ End_01
```

### 2.2 BPMN 元素 ID 与 jobType

| # | 环节 | 元素 ID | jobType / 类型 | 输入变量 | 输出变量 |
|---|---|---|---|---|---|
| 1 | 输入解析 | `Task_ParseInput` | `parseBirthInput` | `birth, calendar, gender, birthPlace, inputMode, subjectType, depth, granularity` | `chartBase, dayNight, inputMode, subjectType, depth` |
| 2 | 定命身宫 | `Task_LifePalaces` | `determineLifePalaces` | `chartBase` | `chartSkeleton, lifeLord, bodyLord` |
| 3 | 安十四主星 | `Task_MainStars` | `placeMainStars` | `chartBase, chartSkeleton` | `mainStars` |
| 4 | 安辅煞星 | `Task_AuxStars` | `placeAuxiliaryStars` | `chartBase, chartSkeleton` | `auxiliaryStars` |
| 5 | 安杂曜 | `Task_MiscStars` | `placeMiscStars` | `chartBase, chartSkeleton` | `miscStars, taiSuiBase` |
| 6 | 安四化 | `Task_FourTransform` | `placeFourTransform` | `chartBase, chartSkeleton` | `fourTransform, palaceStems, twelveStages` |
| V | 验盘校正 | `Task_VerifyChart` | `verifyChart` | `chartSkeleton, mainStars, auxiliaryStars, miscStars, fourTransform, subjectTraits` | `chartVerdict, correctedTime, candidateTimes, verifyNote, verifyRounds` |
| 7 | 定庙陷 | `Task_Brightness` | `assessBrightness` | `chartSkeleton, mainStars, auxiliaryStars, miscStars` | `brightnessTable, starRelations, palaceStrength` |
| 8 | 星曜化气 | `Task_StarNature` | `interpretStarNature` | `mainStars, brightnessTable` | `starNature, transformationQi` |
| 9 | 空宫处理 | `Task_EmptyPalace` | `handleEmptyPalace` | `chartSkeleton, mainStars` | `emptyPalaceResult` |
| 10 | 三方四正 | `Task_Surround` | `analyzeSurroundPalaces` | `chartSkeleton, mainStars, auxiliaryStars, emptyPalaceResult` | `surroundPalaces, clampRelations, combinationMeaning` |
| 11 | 格局判定 | `Task_Pattern` | `determinePattern` | `mainStars, brightnessTable, surroundPalaces, combinationMeaning, palaceStems, fourTransform, emptyPalaceResult` | `patternResult, patternGrade, flyingStars, schoolNote` |
| 12 | 本命综合断 | `Task_Natal` | `generateNatalReading` | `chartSkeleton, starNature, patternResult, surroundPalaces` | `natalReading`（结论化：命格总断＋逐项评级＋应事＋寿元） |
| W | 女命专论 | `Task_FemaleReading` | `analyzeFemaleReading` | `chartSkeleton, patternResult, natalReading` | `femaleReading` |
| C | 小儿命专论 | `Task_ChildReading` | `analyzeChildReading` | `chartSkeleton, patternResult, natalReading` | `childReading` |
| 13 | 大限推演 | `Task_MajorLimit` | `analyzeMajorLimit` | `chartSkeleton, subjectType, taiSuiBase` | `majorLimit, majorLimitReading`（结论化：总纲→十年祸福结论→竹萝三限意味） |
| 14 | 流年推演 | `Task_AnnualFortune` | `analyzeAnnualFortune` | `chartSkeleton, majorLimit, taiSuiBase, granularity` | `annualChart, annualReading, annualRecomputedStars` |
| 15 | 叠宫断法 | `Task_Overlay` | `analyzePalaceOverlay` | `chartSkeleton, majorLimit, annualChart` | `overlayReading, evilPatternTrigger, limitPalaceSong` |
| 16 | 综合汇总 | `Task_Aggregate` | `aggregateReading` | `chartSkeleton, natalReading, femaleReading, childReading, majorLimitReading, annualReading, overlayReading` | `reading`（结论化报告·含 markdown 表格）、`structuredOutput`、`alertTriggered` |
| A | 外发告警 | `Task_SendAlert` | `sendAlert` | `chartBase, reading` | `messageStatus` |
| 17 | 落库返回 | `Task_PersistReturn` | `persistAndReturn` | `chartBase, chartSkeleton, reading, messageStatus` | `caseRecord, response` |
| 18 | 命书详解与白话解读（末尾新增·大模型） | `Task_Interpret` | `interpretResult` | `reading` | `aiInterpretation, aiInterpretationPlain` |

**网关与汇合点**：

| 网关 | 元素 ID | 类型 | 条件分支（互斥且完备） |
|---|---|---|---|
| G1 | `Gateway_Mode` | 排他 | `inputMode="直接排盘"`→J1；`"验盘"`→节点V |
| G1b | `Gateway_ChartVerdict` | 排他 | `chartVerdict="需校正"`→回流节点2；`"无需校正"`→J1 |
| J1 | `Gateway_MergeJ` | 排他汇合 | →节点7 |
| G2 | `Gateway_Subject` | 排他 | `subjectType="成人女"`→W；`"小儿"`→C；`"成人男"`→J2 |
| J2 | `Gateway_SubjectMerge` | 排他汇合 | →G3 |
| G3 | `Gateway_Depth` | 排他 | `depth="本命"`→J3；`"本命+大限"`→节点13→J3；`"本命+大限+流年"`→13→14→15→J3 |
| J3 | `Gateway_DepthMerge` | 排他汇合 | →节点16 |
| G4 | `Gateway_Level` | 排他 | `alertTriggered=true`→A；`false`→J4 |
| J4 | `Gateway_LevelMerge` | 排他汇合 | →节点18 |

### 2.3 BPMN ↔ JobWorker 绑定

`ZiWeiDouShuOntologyJobWorker`（`@Component @Profile("ZiWeiDouShuBPMN")`，继承 `OntologyWorkerSupport`）声明 **22 个 `@JobWorker`**（单类多方法，均 `autoComplete=false`，以 jobType 字面量分发）：见 §2.2 全部 jobType。BPMN 部署到 Zeebe 后由引擎按拓扑推进，到达 `serviceTask` 按 `zeebe:taskDefinition type` 派发作业，订阅同名 jobType 的 worker 被回调执行。**BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」**，四者靠 jobType 字面量绑定，无代码级引用。此绑定由 `scripts/validate_structure.py` 机检 22=22。

### 2.4 关键类

`ZiWeiDouShuApplication`、`ZiWeiDouShuOntologyJobWorker`（本模块 `com.ocean.ontologyframework.ziweidoushu` 包）、`web/ZiWeiDouShuController`（`POST /api/ziweidoushu/chart`、`GET /api/ziweidoushu/chart/{key}`、`GET /api/ziweidoushu/health`）；共享工具 `com.ocean.utilities.{OntologyWorkerSupport, OntologyLabelMatcher, RabbitMqHandler, ProcessOrchestrator, ServiceProbe, StopOnTimeoutExtension}`（来自 `com.ocean:Utilities`）、`com.ocean.ontopobdahandler.OBDAHandler`、`com.ocean.openlletresolver.*`；测试侧：`AllZiWeiDouShuSuiteTest`、`ZiWeiDouShuStarTableTest`（离线经典表自检）、`ZiWeiDouShuOntologyTests`、`ZiWeiDouShuBpmnInMemoryTest`、`ZiWeiDouShuBpmnRealEngineTest`、`ZiWeiDouShuSwrlRabbitMqTest`、`validation/ZiWeiDouShuSkosTerminologyTest`、`validation/ZiWeiDouShuOntologyValidator`、`validation/ValidationResult`。

---

## 3. 测试分层与策略

| 层级 | 测试类 | 依赖引擎 | 是否需环境（Camunda/MySQL/Ontop/RabbitMQ） | 关注点 |
|---|---|---|---|---|
| 经典自检（纯反射·离线） | `ZiWeiDouShuStarTableTest` | 无 | 否 | 内置安星/庙陷/落闲/四化/神煞/运限等表与《全書》原文逐格比对（72 项，无需环境） |
| 集成（推理 + 合规） | `ZiWeiDouShuOntologyTests` | 无（Spring 上下文，直连 Ontop/MySQL） | 是 | 子类推理检索、R1/R8/R8b/R21 推导与动态响应、一致性、`owl:Restriction` 合规校验 |
| 端到端（内存引擎） | `ZiWeiDouShuBpmnInMemoryTest` | `EngineFactory` 内存引擎 | 是（需 Ontop/MySQL 供 worker） | BPMN 编排、主干节点顺序、5 网关分支、验盘闭环回流、无 incident |
| 端到端（真实引擎） | `ZiWeiDouShuBpmnRealEngineTest` | 真实 Zeebe `localhost:26500` | 是 | 真实部署与生产 Worker 消费、全场景/边界场景 |
| SWRL + 消息 | `ZiWeiDouShuSwrlRabbitMqTest` | 无（Spring 上下文，RabbitMQ） | 是（RabbitMQ） | R21 命中 → `SwrlRuleTriggerListener` → RabbitMQ 告警投递端到端 |
| 本体术语（纯 Jena） | `validation/ZiWeiDouShuSkosTerminologyTest` | 无 | 否 | SKOS 结构 + OWL 交叉校验（第 13/14 步启用） |

**设计原则**：
1. **分层解耦**——`ZiWeiDouShuSkosTerminologyTest` 纯文件推理，可离线快速回归；需要环境的能力集中在 Spring 集成与两个端到端类。
2. **生产 worker 驱动**——端到端测试不另写 worker，直接由 `ZiWeiDouShuOntologyJobWorker` 消费，覆盖真实计算链路。
3. **本体为唯一裁判**——SWRL 推导、一致性、合规性均运行时解析自本体，不臆造阈值。
4. **隔离与恢复**——`ZiWeiDouShuOntologyTests` 用 `@BeforeEach` 全量公理快照 + `@AfterEach` 原子恢复，保证用例间本体不漂移。
5. **环境依赖自动跳过**——依赖服务不可达时，集成/端到端用例经 `ServiceProbe.assumeReachable(...)` 自动跳过（不硬失败）。

---

## 4. 测试环境与配置

### 4.1 Profile 与配置源

- 配置源：`src/main/resources/application.yaml`（同一 yaml 既驱动 Spring 又被 EnvPrepare 读取）。
- 测试激活 Profile：`ZiWeiDouShuOntologyTests` → `ZiWeiDouShuJunitTest`；`ZiWeiDouShuBpmnInMemoryTest` / `ZiWeiDouShuBpmnRealEngineTest` / `ZiWeiDouShuSwrlRabbitMqTest` → `ZiWeiDouShuBPMN`（与 Worker `@Profile` 一致，确保 Worker 注册订阅）。
- 关键键：`ontology.main-path=ontology/ziweidoushu-all.owl`、`ontology.abox-path=ontology/ziweidoushu-abox.owl`、`ontology.obda-path=ontology/database/ziweidoushu.obda`、`ontology.obda-properties-path=ontology/database/ziweidoushu.properties`、`ontology.bpmn-path=ontology/bpmn/ziweidoushuProcess.bpmn`；`camunda.client.grpc-address=http://localhost:26500`、`camunda.client.rest-address=http://localhost:9080`；`server.port=9089`。

### 4.2 外部依赖端点

| 依赖 | 端点 / 载体 | 使用者 |
|---|---|---|
| Ontop SPARQL | `http://localhost:8080/sparql` | `ZiWeiDouShuOntologyTests`、worker |
| MySQL | `jdbc:mysql://localhost:3306/ziweidoushudb`（`ziweidoushu.properties`） | OBDA 读写（第 11 步起） |
| RabbitMQ | AMQP `localhost:5672` + 管理 API `http://localhost:15672`（`guest/guest`）；exchange `ziweidoushu.alert.exchange`(direct) / queue `ziweidoushuQueue` / routing-key `ziweidoushu.alert` | `ZiWeiDouShuSwrlRabbitMqTest`、节点 A |
| Zeebe | 内存引擎（随机端口）/ 真实 `localhost:26500` | 两个端到端类 |

### 4.3 真实环境前置

1. 启动环境（Camunda 8 / MySQL / Ontop / RabbitMQ）：`EnvPrepare start --config application.yaml`（经模块根 `start-env.cmd`，`start` 缺则建/有则留、`init` 重建）；
2. 启动 `ZiWeiDouShuApplication`（激活 `ZiWeiDouShuBPMN`），使 `ZiWeiDouShuOntologyJobWorker` 订阅作业（HTTP `9089`）；
3. 初始化库表（第 11 步起）：`ontology/database/createdb_tables.ddl` + `data.sql`；
4. Zeebe Gateway 可达 `localhost:26500`。

### 4.4 BPMN 部署方式

- **内存引擎**：`ZiWeiDouShuBpmnInMemoryTest` 通过 `@DynamicPropertySource` 启动 `EngineFactory` 内存引擎，并把其 gateway 地址注入 `camunda.client.*`；用例内 `addResourceFile(bpmnPath)` 部署，进程结束即销毁（Camunda UI 不可见）。
- **真实引擎**：`ZiWeiDouShuBpmnRealEngineTest` 的 `@BeforeEach deploy()` 经 `ProcessOrchestrator.deployIfAbsent(client, PROCESS_ID, List.of(bpmnPath))` 部署 BPMN，由生产 Worker（独立进程）消费。

---

## 5. 测试数据设计

> 输入变量形态取自节点 1 Worker 实现（`resolveBirthDateTime`/`birthCalendar`/`extractLongitude`）：`birth` 可为字符串（`yyyy-MM-dd HH:mm` 等）或 Map（`{datetime|year,month,day,hour,minute,calendar}`）；`birthPlace` 为含经度数字的文本（西经/`W` 取负）。**下列为测试构造数据，非业务断言**；输入选取原则：避免写入违反本体约束的 ABox 数据（有据可依，★8/10）。

### 5.1 输入场景数据（端到端驱动变量）

| 场景 | birth | calendar | gender | birthPlace | inputMode | subjectType | depth | granularity | 触发分支 |
|---|---|---|---|---|---|---|---|---|---|
| S1 主干最短 | `1990-05-15 14:30` | 公历 | 男 | `北京 116.4E` | 直接排盘 | 成人男 | 本命 | 年 | G1a→J1；G2b→J2；G3a→J3；G4b |
| S2 女命·大限 | `1985-11-08 03:20` | 公历 | 女 | `上海 121.5E` | 直接排盘 | 成人女 | 本命+大限 | 年 | W；节点13 |
| S3 小儿·本命 | `2018-02-20 09:00` | 公历 | 男 | `广州 113.3E` | 直接排盘 | 小儿 | 本命 | 年 | C |
| S4 全链·流年 | `1978-07-01 22:10` | 公历 | 男 | `西安 108.9E` | 直接排盘 | 成人男 | 本命+大限+流年 | 年 | 节点13→14→15 |
| S5 验盘闭环 | `1992-03-12 05:00` | 公历 | 男 | `成都 104.1E` | 验盘 | 成人男 | 本命 | 年 | G1b（V；含回流） |
| S6 告警外发 | `1960-01-01 12:00` | 公历 | 男 | `北京 116.4E` | 直接排盘 | 成人男 | 本命+大限+流年 | 年 | G4a→节点A |
| S7 女命·全链 | `1988-09-09 17:30` | 公历 | 女 | `武汉 114.3E` | 直接排盘 | 成人女 | 本命+大限+流年 | 年 | W→13→14→15 |
| S8 小儿·大限 | `2015-06-06 06:30` | 公历 | 女 | `杭州 120.2E` | 直接排盘 | 小儿 | 本命+大限 | 年 | C→13 |

> S6「告警外发」依赖本体 R18~R21 在给定命盘下命中 `alertTriggered=true`；若该命例未命中，则以本体内预置的**告警探针个体**（`rules:AlertFlag` 断言）确保 G4a 分支可达——分支可达性由本体验证，不臆造数值阈值（客户裁定 ㉖）。

### 5.2 边界数据

| # | 边界/负向输入 | 说明 | 预期 |
|---|---|---|---|
| B-1 | `calendar="农历"`（如 `birth="庚午年四月廿一"`） | 农历→公历换算（`LunarCalendarConverter`，1900–2099） | 不失败；`chartBase` 含 `LUNAR_UNCONVERTED=false`，且 `LM/LD` 保留农历月/日（按农历输入换算真公历） |
| B-2 | `birthPlace=""`（缺经度） | 真太阳时回退 | `tst=0.0h`，回退北京时间平太阳时（客户裁定 ②） |
| B-3 | `birthPlace="New York 74.0W"`（西经） | 经度取负 | `tst=(−74−120)/15≈−12.93h` |
| B-4 | 时辰交界 `birth` 含 `05:00`/`17:00` | 昼夜判定（卯至申为昼） | `dayNight` 在边界小时正确翻转（昼/夜） |
| B-5 | 不传 `gender/subjectType/depth/granularity` | Worker 兜底缺省 | 默认 男/成人男/本命/年 |
| B-6 | `birth` 缺失或空 | 非法生辰 | 节点 1 `fail` → 流程 incident（`PARSE_BIRTH_INPUT_FAILED`） |
| B-7 | `birth="not-a-date"` | 无法解析 | 同上，incident |
| B-8 | 验盘轮次达上限 | `verifyRounds` 达 5 | 标「需人工复核」并终止该闭环分支 |
| B-9 | `subjectType="成人男"`（命中 G2 第三支） | 网关完备性 | 不经过 W/C，直达 J2（验证互斥且完备） |
| B-10 | `depth="本命"`（命中 G3 第一支） | 网关完备性 | 不经过 13/14/15，直达 J3 |

### 5.3 本体合规校验数据（`src/test/resources/ziweidoushuInstances.json`）

字段：`name`、`type`、`birth`、`gender`、`palace_count`、`life_palace`、`body_palace`、`bureau`、`pattern_grade`（部分缺省，用于负向）。逐条经 `ZiWeiDouShuOntologyValidator` 验证 `owl:Restriction`。

| # | name | type | palace_count | life_palace | body_palace | bureau | 预期 |
|---|---|---|---|---|---|---|---|
| 1 | 甲·阳男命例 | NatalChart | 12 | 命宫在寅 | 身宫在辰 | 水二局 | 合规 |
| 2 | 乙·阴女命例 | NatalChart | 12 | 命宫在子 | 身宫在亥 | 木三局 | 合规 |
| 3 | 丙·小儿命例 | NatalChart | 12 | 命宫在午 | 身宫在申 | 金四局 | 合规 |
| 4 | 丁·缺宫负例 | NatalChart | 11 | 命宫在斗 | 身宫在未 | 土五局 | **不合规**（`hasPalace` cardinality 12 违约） |
| 5 | 戊·缺命宫负例 | NatalChart | 12 | （缺） | 身宫在酉 | 火六局 | **不合规**（`hasLifePalace` cardinality 1 违约） |

### 5.4 全量矩阵用例数据（`src/test/resources/ziweidoushu/matrix_cases.json`）

数据驱动矩阵用例资源，供 §6.7 `ZiWeiDouShuPatternMatrixTest` 消费，共 **51 例**（46 基础例 + 5 维度专项）。字段：`id`、`birth`、`gender`、`subjectType`、`depth`、`mode`（入参）、`expectPattern`（断言 `patternResult` 含该串，可空）、`expectFeature`（结构性特征，`|` 分隔）、`note`、可选 `subjectTraits`（验盘）。

`expectFeature` 支持前缀：`命宫=地支` / `五行局=xx局` / `紫微=地支`（断言 `mainStars` 含「紫微在<地支>」）/ `空宫=true`（断言 `emptyPalaceResult` 含「宫空」）/ `告警=<类型>`（断言 `alertTriggered=true` 且 `reading`+`structuredOutput`+`result` 含该类型串）；以及维度专项前缀：`四化=`（`fourTransform`）/`化气=`（`transformationQi`）/`形性=`（`starXingXing`）/`所主=`（`starNature`）/`三方四正=`（`surroundPalaces`）/`夹=`（`clampRelations`）/`借星=`（`emptyPalaceResult`）/`宫强弱=`（`palaceStrength`）/`生克=`（`phaseRelations`）/`命格=`（`patternGrade`）/`流派=`（`schoolNote`）/`飞星=`（`flyingStars`）/`大限=`（`majorLimit`，含第1~12限即「十二限」）/`十二限=`（`yunxianSequence`）。

**构造原则：** 全部样例均以引擎口径 `TST=0.0`（应用 Worker `parseBirthInput` 默认 tst=0.0，与离线模型默认 tst=−0.24 不同）选取，确保命宫/五行局与真实排盘一致、边界不漂移。51 例覆盖维度（基线/五行局/阴阳男女/命宫12/空宫/告警3/运限2/女命/小儿/验盘/修复格局11/财禄夹马/众水朝东/**维度专项5**）及 11 个修复格局的判据与释读来源详见 §6.7。

---

## 6. 测试用例清单

> 编号约定：**P** 正向 / **B** 边界 / **N** 负向。用例须**驱动 BPMN** 走完拓扑，尤其覆盖边界场景。方法名为闸门⑤确认后固化于测试源码的冻结名。

### 6.1 `ZiWeiDouShuOntologyTests`（推理 + 合规集成，13 项）

`@SpringBootTest(classes=ZiWeiDouShuApplication.class)` + `@ActiveProfiles("ZiWeiDouShuJunitTest")` + `@TestMethodOrder(OrderAnnotation)`；`@BeforeEach` 全量公理快照 / `@AfterEach` 原子恢复。

| ID | Order | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|---|
| P-1 | 1 | `testOntologyLoadsAndConsistent` | `ziweidoushu-all.owl` | 本体非空加载；Openllet 一致性通过；无不可满足类 |
| P-2 | 2 | `testInferredSubclassesOfStar` | `star:Star` 子类 | `FourteenMainStar/AuxiliaryStar/MaleficStar/MiscStar` 均被检索；每类实例非空 |
| P-3 | 3 | `testSwrlR1StemYinYangFivePhase` | 十天干个体 | 经 R1 推导出 `hasFivePhase`/`hasYinYang`（甲→阳木等） |
| P-4 | 4 | `testSwrlR8R8bGreatLimitDirection` | 阳男 / 阴男命例 | R8 → `limitDirection="顺"`；R8b → `"逆"`；两者不共存（cardinality 1） |
| P-5 | 5 | `testSwrlR21AlertTriggered` | 含 `AlertFlag` 断言的命例 | R21 推得 `rules:alertTriggered(?a,true)`；无标志者不推 |
| P-6 | 6 | `testInferredCardinalityTwelvePalaces` | `NatalChart` 典型个体 | `hasPalace` 恰 12；`hasLifePalace`/`hasBodyPalace`/`hasBureau` 各恰 1 |
| P-7 | 7 | `testTypicalAboxConformsRestrictions` | `ziweidoushuInstances.json` 合规 3 例 | 逐条通过 36 项 `owl:Restriction`；`ValidationResult.passed=true` |
| P-8 | 8 | `testTransformationCardinality` | `layout:Transformation` 典型个体 | `transformationType`/`transformsTo` 各恰 1（R7 与 Restriction 一致） |
| P-9 | 9 | `testPatternGradeMaxCardinality` | `NatalChart` 命格 | `hasPatternGrade` 至多 1（余格备参不落主值） |
| N-1 | 10 | `testMissingPalaceCountRejected`（负） | 丙·缺宫负例（11 宫） | 合规校验报错；`ValidationResult.passed=false`，原因含 `hasPalace` |
| N-2 | 11 | `testMissingLifePalaceRejected`（负） | 戊·缺命宫负例 | 合规校验报错，原因含 `hasLifePalace` |
| N-3 | 12 | `testInconsistentChartDetectedByReasoner`（负） | 人为写入 11 宫公理 | Openllet 报告不一致；`finally` 清理临时公理 |
| N-4 | 13 | `testViolatingDirectionConflict`（负） | 同一大限断言顺+逆 | 与 `limitDirection cardinality 1` 冲突，一致性失败 |

> **回归加固（第 8 步回归期，仅改测试程序）**：
> - SWRL 推导断言（P-3 R1 / P-4 R8·R8b / P-5 R21）改用**独立推理器**：`freshReasoner()` 基于当前 TBox（已含本用例注入的临时公理）从零物化 SWRL，用完 `dispose()`。原因：全局共享推理器会被 `SwrlRuleTriggerListener` 在变更广播中**同步重入 `flush()`**（监听器属禁区不可改），偶发导致 R1 未物化（`hasFivePhase=木` 间歇失败）。独立推理器规避该竞态，**不弱化断言**（仍要求推导出现）。
> - N-3 负例补 `owl:differentIndividuals(b1,b2)`：OWL 无唯一名假设，`hasBureau` cardinality-1 与「同属性两个**相异**值」才构成确定性不一致。
>
> **第 9 步 TBox/ABox 分离加固（第 9 步，仅改测试程序）**：
> - `ZiWeiDouShuOntologyTests.setUp` 于加载 `ontology.main-path`（`ziweidoushu-all.owl`，分离后**仅聚合 TBox**）后，**显式加载 `ontology.abox-path`（`ziweidoushu-abox.owl`）并把其公理并入 TBox 本体**（等价分离前单文件视图），供 P-6/P-8/P-9 等依赖典型 ABox 个体（`chart:Case_Demo01`、`layout:Sihua_Demo_Lu`）的基数用例使用；并断言**主入口 imports 闭包含 `tbox`、不含 `abox`**（★铁律 11）。
> - `ziweidoushu-all.owl` 的 ABox `imports` 已按铁律 11 注释掉；ABox 由应用侧 / Ontop OBDA（第 11 步）独立供给。P-1 等纯 TBox 推理用例不受影响。`ZiWeiDouShuSwrlRabbitMqTest`（仅用 `test-abox#` 动态注入）无需改动。

### 6.2 `ZiWeiDouShuBpmnInMemoryTest`（内存引擎端到端，9 项）

`@SpringBootTest` + `@ActiveProfiles("ZiWeiDouShuBPMN")`；`@DynamicPropertySource` 注入 `EngineFactory` 内存引擎地址；用例内 `addResourceFile(bpmnPath)` 部署。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `chartCompletesOnMainPath` | S1（成人男·本命） | 流程 `COMPLETED`；无 incident；经过节点 1~12、16、17 |
| P-2 | `femaleBranchCompletes` | S2（成人女） | 经过节点 W；`femaleReading` 非空；完成 |
| P-3 | `childBranchCompletes` | S3（小儿） | 经过节点 C；`childReading` 非空；完成 |
| P-4 | `majorLimitBranchCompletes` | S2（本命+大限） | 经过节点 13；`majorLimitReading` 非空 |
| P-5 | `fullDepthBranchCompletes` | S4（本命+大限+流年） | 经过节点 13→14→15；`overlayReading`/`annualReading` 非空 |
| P-6 | `alertBranchCompletes` | S6（alertTriggered=true） | 经过节点 A；`messageStatus` 非空；完成 |
| B-1 | `defaultVariablesFallback` | 仅传 `birth`（不传类别/深度/粒度） | 缺省 成人男/本命/年 生效；完成 |
| B-2 | `verifyingModeLoopsBack` | S5（验盘·无需校正） | 经过节点 V，G1b 走「无需校正」到 J1；完成 |
| N-1 | `invalidBirthCausesIncident` | B-6（缺 birth） | 产生 incident（`PARSE_BIRTH_INPUT_FAILED`）；流程未完成 |

### 6.3 `ZiWeiDouShuBpmnRealEngineTest`（真实引擎端到端，9 项）

`@ActiveProfiles("ZiWeiDouShuBPMN")`，真实 Zeebe `localhost:26500`；`@BeforeAll ServiceProbe.assumeReachable("Camunda REST 网关", LOCALHOST, 9080)`；`@BeforeEach deploy()` 经 `ProcessOrchestrator.deployIfAbsent` 部署；生产 Worker 消费。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `chartProcessCompletesMainPath` | S1 | 真实引擎全流程完成；生产 worker 消费全部 serviceTask；无 incident |
| P-2 | `chartProcessCompletesFemaleFullDepth` | S7 | W + 13→14→15 全链完成 |
| P-3 | `chartProcessCompletesChildMajorLimit` | S8 | C + 节点13 完成 |
| P-4 | `chartProcessCompletesVerifyMode` | S5 | 验盘分支 + G1b 完成 |
| P-5 | `chartProcessCompletesAlertPath` | S6 | 节点 A 外发分支完成 |
| B-1 | `defaultVariablesFallback` | 仅传 `birth` | 缺省值兜底；完成 |
| B-2 | `lunarInputMarkedPending` | B-1（农历） | 完成；`chartBase` 含 `LUNAR_UNCONVERTED=false` 且 `LM=4`、`LD=21`（农历输入保留农历月/日） |
| B-3 | `westLongitudeCorrection` | B-3（西经） | 完成；`chartBase` 的 `TST` 为负 |
| N-1 | `invalidBirthYieldsIncident` | B-6/B-7 | incident；未完成 |

> **回归加固（仅改测试程序）**：`runAndAwaitCompleted` 遇 `INCIDENT/TERMINATED` 时，经 `client.newIncidentSearchRequest()` 读取并附上 incident 明细（`elementId:errorType:errorMessage`）到断言消息，便于定位异常终止根因（复用 `PizzaBpmnRealEngineTest` 同款诊断）。

### 6.4 `ZiWeiDouShuSwrlRabbitMqTest`（SWRL + 消息，2 项）

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `swrlAlertTriggersRabbitMqMessage` | S6 命盘（R21 命中） | `SwrlRuleTriggerListener` 10s 内触发回调；消息投递至 exchange `ziweidoushu.alert.exchange` → queue `ziweidoushuQueue`（routing-key `ziweidoushu.alert`，content_type=application/json） |
| N-1 | `noAlertNoMessage`（负） | 无 `AlertFlag` 的命盘 | 不投递消息；`alertTriggered` 不置 true |

> **回归加固（仅改测试程序）**：P-1 采用**两批注入**（批1：`NatalChart` + `AlertFlag` + `alertType` → flush；批2：`hasAlertFlag` → flush），与生产 `InsertService` 多事件写入模式一致，规避聚合场景下监听器同步重入 flush 取到未同步旧快照（`previousSnapshot` 漏判新增告警）。

### 6.5 `validation/ZiWeiDouShuSkosTerminologyTest`（词汇表，6 项 · 第 13 步已启用）

纯 Jena，无需环境。**第 13 步已产出 `ontology/ziweidoushu-terminology.ttl`，本类已启用并纳入 `AllZiWeiDouShuSuiteTest`。**

> **交叉校验口径（`skos:exactMatch` 方向）**：照 Example（`pizza-components.owl` 类携带 `skos:exactMatch → …/term/…`）——**由 OWL 侧**（12 子本体类/属性 + abox 星曜个体）携带 `skos:exactMatch` 指向术语表概念 IRI；P-2/N-1 据此断言「术语表概念集合 ≡ 本体引用的 term IRI 集合」。

| ID | 方法 | 校验点 | 预期 |
|---|---|---|---|
| P-1 | `skosStructureValid` | 概念/标签/关系结构 | 每 `skos:Concept` 有 `prefLabel`；无孤立概念 |
| P-2 | `conceptMapsToOntologyIri` | `skos:exactMatch` 交叉校验 | 每概念精确对应本体内一个类/属性 IRI |
| P-3 | `prefLabelFromOntology` | 规范名来源 | 每个 `prefLabel` 取自本体标签，无凭空造词 |
| P-4 | `altLabelHasSource` | 同义词/别名来源 | 每条 `altLabel` 为字面量且带语言标签；且**逐条带可追溯「# 源:」注释**（第 14 步加固，对齐前例 S-2） |
| N-1 | `noOrphanConcept`（负） | 无对应本体的词条 | 断言不存在（若存在 → 失败） |
| N-2 | `synonymMapsToCorrectConcept`（负） | 同义归一 | 「不同名同义、同义不同名」归并到正确规范概念（含 `prefLabel` 跨语言唯一，同名冲突项附域后缀消歧） |

> **回归加固（仅改测试程序，第 13 步）**：`ZiWeiDouShuOntologyTests` 自建 `OWLOntologyManager` 仅含 `AutoIRIMapper`（不处理 `.ttl`），而 `ziweidoushu-tbox.owl` 第 13 步起 `owl:imports` 了 `.ttl` 术语表，故补注册 `.ttl` 的 `SimpleIRIMapper`（对齐框架 `OntologyService` 做法），避免 `term` 导入解析失败。**非本体/应用改动。**

### 6.6 `ZiWeiDouShuStarTableTest`（经典表口径自检，72 项 · 离线·无需环境）

纯反射锁定 `ZiWeiDouShuOntologyJobWorker` 的内置安星/庙陷/神煞/运限等表与查表方法，逐格对照《全書》卷二/卷三原文（`@DisplayName` 已注卷次与行号）。**本类无需 Ontop/MySQL/Camunda，是「实际输出 vs 经典」最直接的离线校验点，已纳入 `AllZiWeiDouShuSuiteTest`。**

| 分类 | 校验内容（实际输出＝内置表值） | 经典出处 |
|---|---|---|
| 紫微定位 / 紫府系偏移 | 五局初一紫微取值；紫微系{0,-1,-3,-4,-5,-8} / 天府系{0,1,2,3,4,5,6,10}；杀破狼空三 | 卷二·安紫微定位棋盘图 / 安南北斗諸星訣 |
| 星辰庙陷 / 落闲 | 紫微@午=庙、太阳@子=陷…；紫微子辰亥为闲…；外曜以「平和」兜底 | 卷二·星辰庙陷 / 定十二宫星辰落闲 |
| 四化 / 火铃 / 龙池凤阁 / 飞天三杀 | 甲廉破武阳、壬梁紫府武（天府化科）；火铃按年支三合；龙池子顺辰凤阁子逆戌；飞天三杀四组 | 卷二·安禄权科忌四星变化诀 / 安火铃 / 安龙池凤阁 / 安飞天三杀诀 |
| 七杀朝斗 / 十等论 / 诸格 12 / 形性賦 / 同垣 | 七杀寅申子午为朝斗；福寿/聪明/僧道…；12 格全覆盖；破军背重眉宽… | 卷三·谈星要论 / 卷一·定富贵贫贱十等论 / 诸格论 / 形性賦 / 诸星同垣 |
| 太岁小限 / 大小限所忌 / 二限太岁 / 贫富先后 | 12 支三段吉凶；子命忌寅申…；太岁冲大限…；先贫后富/先富后贫 | 卷三·论太岁小限 / 大小限 / 论二限太岁 / 论命先贫后富 |
| 运限重算 / 竹萝三限 / 流月流日 / 四盘叠合 / 空宫借星 / 验盘 | 宫干五虎遁/大限虚龄/小限男顺女逆；三方四正杀破俱见；斗君起正月；本命为体逐层叠；借对宫主星；候补±1 | 卷二·定男女竹萝三限 + 外部通行法 I·51/I·62/I·61 |
| 杂曜 / 告警 / DDL↔OBDA / 下沉保真 | 〔外部通行〕杂曜表；R18~R21 客观凶判据；DDL 表↔OBDA 映射全覆盖 fail-fast；`data.sql` 与内置表逐行等值 | 〔外部通行法〕+ 核心设计§5 R18~R21 + 本体/库结构 |

### 6.7 `ZiWeiDouShuPatternMatrixTest`（全量数据矩阵端到端，51 + 1 项）

`@SpringBootTest(classes=ZiWeiDouShuApplication.class)` + `@ActiveProfiles("ZiWeiDouShuBPMN")`，连真实 Zeebe `localhost:26500`、生产 Worker（应用 9089）消费；`@BeforeAll ServiceProbe.assumeReachable` 探测 9080（Camunda REST）/9089（应用）不可达则整类 `assumeTrue` 跳过（不阻断离线构建）；`@BeforeEach deployIfAbsent` 部署 BPMN。用例数据驱动自 `src/test/resources/ziweidoushu/matrix_cases.json`（**51 例**），逐例启流程 → 轮询至 `COMPLETED` → 断变量；**低负载错峰**：每例 `sleep(150ms)`（规避 Camunda 9 `Partition-1` 高并发冲垮，见引擎脆弱性记忆）。

| ID | Order | 方法 | 场景 | 预期断言（关键） |
|---|---|---|---|---|
| T-MATRIX | 1 | `t_matrix_all_cases` | 51 例矩阵（分类见下表） | 每例 `chartSkeleton`/`mainStars` 非空；`expectFeature`（`命宫=`/`五行局=`/`紫微=`/`空宫=true`/`告警=` 及维度专项 `四化=`/`化气=`/`形性=`/`所主=`/`三方四正=`/`夹=`/`借星=`/`宫强弱=`/`生克=`/`命格=`/`流派=`/`飞星=`/`大限=`）逐条命中；`expectPattern` 命中 `patternResult`；51/51 全绿 |
| T-INVALID | 2 | `t_invalid_birth_incident` | 缺 `birth`（仅传 gender） | 节点 1 `fail` → 流程状态 `INCIDENT`（`PARSE_BIRTH_INPUT_FAILED`），不 `COMPLETED` |

**51 例分类构成：**

| 分类 | 例数 | id 前缀 | 断言要点 |
|---|---|---|---|
| 三基线金标准 | 3 | `base1~3` | 命宫/五行局/紫微定位 |
| 五行局 5/5 | 5 | `bureau_水二局 … bureau_火六局` | `五行局=` 命中 |
| 阴阳男女 4/4 | 4 | `yg_阳男/阴男/阳女/阴女` | 流程完成（验证顺/逆与男女分支） |
| 命宫 12/12 | 12 | `ming_子 … ming_亥` | `命宫=` 命中 |
| 空宫借星 | 1 | `empty` | `emptyPalaceResult` 含「宫空」 |
| 告警 3 类 | 3 | `alert_重大疾病/寿元关/大凶` | `alertTriggered=true` 且 `reading`/`structuredOutput` 含类型串 |
| 运限 2 档 | 2 | `depth_dx`（本命+大限）/`depth_ln`（+流年） | 完成（覆盖节点 13/14/15） |
| 女命专论 | 1 | `female` | `femaleReading` 非空 |
| 小儿专论 | 1 | `child` | `childReading` 非空 |
| 验盘模式 | 1 | `verify` | 验盘分支完成 |
| **修复格局 11** | 11 | `fix_*` | 各格 `patternResult` 命中（判据/释读见下表） |
| 财禄夹马 | 1 | `cai_lu_jia_ma` | `patternResult` 含「财禄夹马」 |
| 众水朝东 | 1 | `zhong_shui` | `patternResult` 含「众水朝东」 |
| **维度专项** | 5 | `dim_*` | 四化/化气/形性/所主/三方四正/生克制化/命格/流派/飞星/大限十二限/宫强弱/借星/夹逐条命中（明细见下表） |

**维度专项 5 例——覆盖维度、断言与经典依据：**

| id | 命例（birth/性别） | 断言维度（`expectFeature` 前缀＝值） | 经典依据 |
|---|---|---|---|
| `dim_base1_full` | 1978-07-04 11:45 男·金四局 | `四化=`戊干贪禄→子/太权→亥/右科→午/机忌→巳；`化气=`紫微化气为尊、廉贞化气为囚；`形性=`紫微@午：帝座；`所主=`紫微@午：帝座主尊贵；`三方四正=`子宫（子辰申午）；`生克=`紫微(土)在午(火)→宫生星（得生）、廉贞(火)在戌(土)→星生宫（泄气）；`命格=`中格；`流派=`三合轨；`飞星=`生年干戊；`大限=`起限岁=4阳男顺行、第12限114-123岁@子宫；`宫强弱=`男命强宫；`夹=`无成格之夹 | 卷二·安禄权科忌四星变化诀；卷一·諸星問答論（化气/所主）；卷一·形性賦；卷一·三方四正；卷二·论星辰生克制化；卷三·论格星数高下；卷二·安大限诀；卷二·定十二宫弱强 |
| `dim_base2_sihua` | 2008-10-18 20:16 男·金四局 | `四化=`右弼化科→寅、天机化忌→巳；`命格=`中格 | 卷二·安禄权科忌（戊干右弼化科，落支随盘变）；卷三·论格星数高下 |
| `dim_base3_daxian` | 2017-01-28 16:11 男·水二局 | `四化=`太阴化禄→辰、巨门化忌→午；`大限=`起限岁=2阴男逆行、第1限2-11岁@巳宫；`夹=`羊陀夹命宫（巳-未） | 卷二·安大限诀（阴男逆行、局数即起限）；卷二·安羊陀（夹命）；卷二·安禄权科忌 |
| `dim_yangnv` | 1976-07-20 16:31 女·木三局 | `宫强弱=`女命强宫；`四化=`天同化禄→辰、廉贞化忌→丑；`大限=`起限岁=3阳女逆行；`生克=`紫微(土)在酉(金)→星生宫（泄气）；`三方四正=`子宫（子辰申午） | 卷二·定十二宫弱强·女命分宫；卷二·安大限诀（阳女逆行）；卷二·论星辰生克制化；卷一·三方四正 |
| `dim_empty_borrow` | 1985-09-06 18:34 男·土五局 | `空宫=true`；`借星=`申宫空→借对宫寅之天机/太阴、酉宫空→借对宫卯之紫微/贪狼；`四化=`天机化禄→寅、太阴化忌→寅 | 空宫借星〔外部通行法·I·62〕（借对宫主星+借光，非《全書》原文）；卷二·安禄权科忌 |

> **注**：`yunxianSequence`/`annualChart` 为节点 13/14 产出，实测未进入流程变量回读（Camunda 变量落库范围限制），故「十二限」维度以 `majorLimit`（含第 1~12 限）断言，等价覆盖；`phaseRelations`（生克制化）与 `starRelations`（同宫五行生克）均由 `assessBrightness` 节点产出，实测可回读。

**第 33 轮修复的 11 个格局——判据（修复后）与释读来源：**

> 修复要点：原判据存在「死分支」（星名误传只吃地支的 `inSanFang`、判「同宫」而两星恒不同宫、夹拱位在安星几何下不成立）。以下为按《全書》原文语义修正后的可达判据；凡涉经典释读处，代码注释均标「释读：…」+ 原文出处。

| 格局 | 判据（修复后·可达） | 释读与出处 | 验证样例出生 |
|---|---|---|---|
| 机月同梁格 | 命在寅/申，且天机、太阴、天同、天梁会照命三方四正 | 「机月同梁作吏人」卷三·谈星要论 | 1956-11-17 06:15 |
| 日月藏辉 | 太阳、太阴会照命宫三方四正（`inSanFang` 改传巨门地支变量） | 富局·卷一 | 1955-06-17 16:00 |
| 财与囚仇 | 武曲（财）与廉贞（囚）**分守**身命（改「同宫」为分守） | 卷一 | 2000-12-18 08:45 |
| 杀破狼 | 七杀、破军、贪狼三方四正会照 | 卷三·談星要論 | 1968-10-20 17:45 |
| 紫微七杀化权 | 紫微、七杀**同宫**（巳亥），「化权」取权柄之象（原判 `eq(ziwei,qisha)` 前身误设，十干四化七杀不化权，故取象解） | 「紫微七杀化权反作桢梓」卷三·谈星要论 | 1988-12-17 20:00 |
| 财荫夹印 | 天相守命，天梁与禄（禄存/化禄）**分居两侧**来夹 —— **〔释读：原文「武」→禄〕**，按「一星各居一侧（真夹）」判定 | 卷一·定富局 | 1976-02-03 15:45 |
| 财印夹禄 | 武曲、天相（财印）**分居禄存两侧**来夹（一星各居一侧·真夹）—— **〔释读：梁相→武相（财印）〕**（天梁恒与天相相邻、永不同宫，故「梁相」不可达） | 卷一 | 1950-10-11 04:30 |
| 马头带剑 | 擎羊坐午 —— **〔释读：天马→擎羊坐午〕**（「马头」指午宫；天马只落寅申巳亥，天马≡擎羊同宫 0 次） | 卷三·擎羊吉凶诀（羊居午为马头带剑） | 1976-11-05 07:30 |
| 荫印拱身 | 身宫坐天梁/天相，另一来夹拱，勿坐空亡 | 富局·卷一 | 1959-08-22 05:30 |
| 金舆扶驾 | 紫微守命，太阳、太阴**分居紫微前后两侧**（太阳恒在紫微−3＝后方，太阴居前方1~5宫即分居两侧；「前后」非「紧邻±1」，紫微守命于子/丑/寅/午/未/申成格） | 定贵局·卷一「紫微守命前后有日月来夹是也」 | 1990-02-04 04:28 |
| 君子在野 | 命身逢四煞且临陷地（「四杀守身命而临陷地」） | 贫贱局·卷一 | 1954-05-27 03:45 |

---

## 7. 覆盖矩阵

> 覆盖要求：**每个 BPMN 节点、每条排他网关的每个分支（互斥条件两侧）、每个边界/负向场景、每条本体约束都必须有对应用例**，且每项须给出「经典依据」（来自第 1 步登记的《紫微斗數全書》语料与已定稿设计，不得编造）。

### 7.1 流程节点覆盖

> 每个 BPMN 节点（22 serviceTask + 5 排他网关 + 4 汇合点 + 起止）均有对应用例。**覆盖分支**列标注该节点由哪条网关分支到达；**经典依据**取自第 1 步登记的《紫微斗數全書》语料与已定稿设计，工程/大模型节点显式标「非经典断法」。

| BPMN 节点 id | 覆盖用例 | 覆盖分支 | 说明 | 经典依据 |
|---|---|---|---|---|
| `Start_01` | 6.2 P-1~P-6、6.3 P-1~P-5 | 全部入口 | 各场景启动 | 无（起始事件） |
| `Task_ParseInput` | 6.2 P-1/B-1/N-1、6.3 P-1/B-2/B-3/N-1、6.1 P-1 | 全部（G1 前） | 历法/真太阳时/昼夜/缺省/非法 | 历法校正〔程序实现·非《全書》断法〕 |
| `Task_LifePalaces` | 6.2 P-1、6.1 P-6/P-7 | 主干 + G1b「需校正」回流目标 | 命身宫/五行局/命主身主 | 卷二·安命身例 / 定五行局 / 命主身主诀 |
| `Task_MainStars` | 6.2 P-1、6.1 P-2 | 主干 | 十四主星布列 | 卷二·安紫微定位棋盘图 / 安南北斗諸星訣 |
| `Task_AuxStars` | 6.2 P-1 | 主干 | 辅煞星 | 卷二·安魁钺 / 禄存 / 羊陀 / 火铃诀 |
| `Task_MiscStars` | 6.2 P-1 | 主干 | 杂曜与太岁基准 | 卷二·安杂曜 / 太岁诸神 / 安飞天三杀诀 |
| `Task_FourTransform` | 6.2 P-1、6.1 P-8 | 主干 | 四化/宫干/十二长生 | 卷二·安禄权科忌四星变化诀 / 十二长生 |
| `Task_VerifyChart` | 6.2 B-2/B-8、6.3 P-4 | G1「验盘」支 | 验盘、闭环回流、轮次上限 | 验盘定盘〔外部通行法·非《全書》原文〕（I·61） |
| `Gateway_Mode` (G1) | 6.2 P-1/B-2 | 直接排盘 / 验盘 | 输入模式排他网关 | 流程设计（两分支互斥完备） |
| `Gateway_ChartVerdict` (G1b) | 6.2 B-2、6.3 P-4 | 需校正 / 无需校正 | 验盘裁决排他网关 | 验盘定盘〔外部通行法〕（I·61） |
| `Gateway_MergeJ` (J1) | 6.2 P-1 | 直接排盘 / 无需校正 两路汇合→节点7 | 排他汇合 | — |
| `Task_Brightness` | 6.2 P-1 | 主干 | 庙陷/生克/宫强弱 | 卷二·星辰庙陷；卷二·定十二宫星辰落闲 |
| `Task_StarNature` | 6.2 P-1 | 主干 | 化气/所主 | 卷一·諸星問答論（化气所主） |
| `Task_EmptyPalace` | 6.2 P-1 | 主干 | 空宫借星 | 空宫借星〔外部通行法〕（I·62） |
| `Task_Surround` | 6.2 P-1 | 主干 | 三方四正/夹宫/组合 | 卷一·三方四正 / 夹拱诸格（骨髓賦等） |
| `Task_Pattern` | 6.2 P-1、6.1 P-9 | 主干 | 成格/命格高低/四化飞星 | 卷一·定富局/定贵局/定贫贱局；卷三·诸格论 |
| `Task_Natal` | 6.2 P-1 | 主干 | 本命综合断 | 卷一 / 卷三 综合断诀 |
| `Gateway_Subject` (G2) | 6.2 P-1/P-2/P-3、B-9 | 成人女 / 小儿 / 成人男 | 命主类别排他网关 | 流程设计（三分支互斥完备） |
| `Task_FemaleReading` | 6.2 P-2、6.3 P-2 | G2「成人女」支 | 女命专论 | 卷一·女命骨髓賦 |
| `Task_ChildReading` | 6.2 P-3、6.3 P-3 | G2「小儿」支 | 小儿命专论 | 卷三·论小儿 / 论小儿克亲 |
| `Gateway_SubjectMerge` (J2) | 6.2 P-2/P-3 | 三路汇合→G3 | 排他汇合 | — |
| `Gateway_Depth` (G3) | 6.2 P-1/P-4/P-5、B-10 | 本命 / 本命+大限 / 本命+大限+流年 | 推演深度排他网关 | 流程设计（三分支互斥完备） |
| `Task_MajorLimit` | 6.2 P-4/P-5、6.3 P-3、6.1 P-4 | G3「本命+大限」「全链」支 | 起限顺逆/十年祸福 | 卷二·定男女竹萝三限；R8/R8b 起限顺逆 |
| `Task_AnnualFortune` | 6.2 P-5、6.3 P-2 | G3「全链」支 | 流年重算流曜 | 卷三·论太岁小限 / 流年 |
| `Task_Overlay` | 6.2 P-5、6.3 P-2 | G3「全链」支 | 叠宫/凶格引动 | 四盘叠合〔外部通行法〕（I·51）；卷三·谈星要论 |
| `Gateway_DepthMerge` (J3) | 6.2 P-5 | 三路汇合→节点16 | 排他汇合 | — |
| `Task_Aggregate` | 6.2 P-1/P-6 | 主干 | 汇总 / 写 alertTriggered | 综合（非单一经典句） |
| `Gateway_Level` (G4) | 6.2 P-6、6.3 P-5 | alertTriggered true / false | 吉凶级别排他网关 | 告警判据 R18~R21（卷三·谈星要论凶格） |
| `Task_SendAlert` | 6.2 P-6、6.3 P-5、6.4 P-1 | G4「true」支 | 外发告警 | 卷三·谈星要论凶格 + R21 命中即外发 |
| `Gateway_LevelMerge` (J4) | 6.2 P-1 | true / false 两路汇合→节点18 | 排他汇合 | — |
| `Task_Interpret` | 6.2 P-1、6.3 P-1 | 主干 | 命书详解/白话解读（大模型；不可用降级空串不阻断） | 无（大模型生成·非经典断法） |
| `Task_PersistReturn` | 6.2 P-1、6.1 N-1 | 主干 | 落库返回 | 无（工程落库） |
| `End_01` | 所有 `COMPLETED` 用例 | 正常结束 | 结束事件 | 无（结束事件） |

### 7.2 网关分支覆盖

> 5 个排他网关的**每一条分支（互斥条件两侧）**均有对应用例，覆盖其可达与不可达（互斥且完备）。

| 网关 id | 分支/条件（互斥条件原文） | 覆盖用例 | 经典依据 |
|---|---|---|---|
| G1 `Gateway_Mode` | `inputMode="直接排盘"` → J1 | 6.2 P-1（S1） | 流程设计·输入模式 |
| G1 `Gateway_Mode` | `inputMode="验盘"` → 节点V | 6.2 B-2（S5）、6.3 P-4 | 验盘定盘〔I·61〕 |
| G1b `Gateway_ChartVerdict` | `chartVerdict="需校正"` → 回流节点2（轮次上限 5） | 6.2 B-8 | 验盘闭环〔I·61〕 |
| G1b `Gateway_ChartVerdict` | `chartVerdict="无需校正"` → J1 | 6.2 B-2、6.3 P-4 | 验盘定盘〔I·61〕 |
| G2 `Gateway_Subject` | `subjectType="成人女"` → 节点W | 6.2 P-2、6.3 P-2 | 卷一·女命骨髓賦 |
| G2 `Gateway_Subject` | `subjectType="小儿"` → 节点C | 6.2 P-3、6.3 P-3 | 卷三·论小儿命 |
| G2 `Gateway_Subject` | `subjectType="成人男"` → J2（不经 W/C） | 6.2 P-1、B-9 | 流程设计（互斥且完备） |
| G3 `Gateway_Depth` | `depth="本命"` → J3（不经 13/14/15） | 6.2 P-1、B-10 | 流程设计（互斥且完备） |
| G3 `Gateway_Depth` | `depth="本命+大限"` → 13 → J3 | 6.2 P-4、6.3 P-3 | 卷二·定男女竹萝三限 |
| G3 `Gateway_Depth` | `depth="本命+大限+流年"` → 13→14→15→J3 | 6.2 P-5、6.3 P-2 | 卷三·论太岁小限/流年 |
| G4 `Gateway_Level` | `alertTriggered=true` → 节点A | 6.2 P-6、6.3 P-5、6.4 P-1 | 告警 R18~R21（卷三·谈星要论凶格） |
| G4 `Gateway_Level` | `alertTriggered=false` → J4 | 6.2 P-1、6.4 N-1 | 告警判据（无命中不外发） |

### 7.3 本体约束覆盖

> 每条 `owl:Restriction`（核心设计 §4）与每条 SWRL 规则（核心设计 §5，R1~R25）均有对应用例；「经典依据」：结构基数约束属本体建模，SWRL 规则的经典出处逐条登记于核心设计 §5 与《经典符合性审查报告》。

| 约束 / SWRL | 覆盖用例 | 经典依据 |
|---|---|---|
| `NatalChart hasBirthInput cardinality 1` | 6.2 P-1、6.1 N-1 | 核心设计 §4（结构约束） |
| `NatalChart hasPalace cardinality 12` | 6.1 P-6/N-1 | 核心设计 §4（结构约束） |
| `NatalChart hasLifePalace / hasBodyPalace / hasBureau cardinality 1` | 6.1 P-6/N-2 | 核心设计 §4（结构约束） |
| `NatalChart hasLifeMaster / hasBodyMaster cardinality 1` | 6.1 P-6 | 核心设计 §4（结构约束） |
| `chart:Palace hasBranch / hasPalaceStem cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `core:HeavenlyStem hasYinYang / hasFivePhase cardinality 1` | 6.1 P-3/P-7 | 核心设计 §4（结构约束） |
| `core:EarthlyBranch hasFivePhase cardinality 1` | 6.1 P-3 | 核心设计 §4（结构约束） |
| `core:SexagenaryCycle hasStem/hasBranch/hasNaYin cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `core:FivePhaseBureau hasTwelveStage cardinality 12` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `star:Star starName cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `FourteenMainStar hasStarCharacter cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `layout:StarPlacement placedIn cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `layout:StarPlacement hasBrightness maxCardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `layout:Transformation transformationType/transformsTo cardinality 1` | 6.1 P-8 | 核心设计 §4（结构约束，与 R7 一致） |
| `chart:Palace oppositeOf / hasSanFang cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `pattern:StarPattern patternName cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `pattern:SpecialStructure structureType cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `pattern:PalaceStarQualification qualificationType cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `pattern:PatternGrade patternGradeName cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `NatalChart hasPatternGrade maxCardinality 1` | 6.1 P-9 | 核心设计 §4（结构约束） |
| `fortune:GreatLimit limitDirection cardinality 1` | 6.1 P-4/N-4 | 核心设计 §4（结构约束，与 R8/R8b 一致） |
| `fortune:GreatLimit limitRange cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `fortune:AnnualFortune annualGanzhi cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `fortune:LimitTaboo tabooBranch cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| `NatalChart hasGreatLimit maxCardinality 12` | 6.1 P-7、6.2 P-4 | 核心设计 §4（结构约束） |
| `NatalChart hasConclusion / hasStructuredOutput cardinality 1` | 6.2 P-1 | 核心设计 §4（结构约束） |
| `NatalChart hasChartVerification maxCardinality 1` | 6.2 B-2 | 核心设计 §4（结构约束） |
| `rules:AlertFlag alertType cardinality 1` | 6.1 P-5、6.4 P-1 | 核心设计 §4（结构约束） |
| `reading:CaseRecord caseBirthData cardinality 1` | 6.2 P-1 | 核心设计 §4（结构约束） |
| `diagnosis:SourceNote sourceBook/sourceVolume cardinality 1` | 6.1 P-7 | 核心设计 §4（结构约束） |
| **R1** 干支→阴阳/五行 | 6.1 P-3 | 核心设计 §5·R1（经典出处见该节） |
| **R2/R3** 五行相生/相克传递 | 6.1 P-1（本体一致性） | 核心设计 §5·R2/R3 |
| **R4/R5** 安星落宫 / 五行局→紫微起宫 | 6.2 P-1、6.1 P-2 | 核心设计 §5·R4/R5（卷二·安紫微定位棋盘图） |
| **R6** 庙陷判定 | 6.2 P-1 | 核心设计 §5·R6（卷二·星辰庙陷） |
| **R7** 四化落宫（生年干） | 6.1 P-8 | 核心设计 §5·R7（卷二·安禄权科忌四星变化诀） |
| **R8/R8b** 大限起限顺逆 | 6.1 P-4、6.2 P-4、N-4 | 核心设计 §5·R8/R8b |
| **R9** 三方四正取宫 | 6.2 P-1 | 核心设计 §5·R9（卷一·三方四正） |
| **R10** 夹宫判定 | 6.2 P-1 | 核心设计 §5·R10（卷一·夹拱诸格） |
| **R11** 空宫借星 | 6.2 P-1 | 核心设计 §5·R11（空宫借星〔I·62〕） |
| **R12** 成格判定·分类 | 6.2 P-1 | 核心设计 §5·R12（卷一·定富/贵/贫贱局） |
| **R13** 命格高低·等第 | 6.1 P-9 | 核心设计 §5·R13（卷三·论格星数高下） |
| **R14** 十二长生顺逆 | 6.2 P-1、6.1 P-7 | 核心设计 §5·R14（卷二·十二长生） |
| **R15** 流年流曜重算 | 6.2 P-5、6.3 P-2 | 核心设计 §5·R15（卷三·流年） |
| **R16** 叠宫断法·三盘叠加 | 6.2 P-5 | 核心设计 §5·R16（四盘叠合〔I·51〕） |
| **R17** 凶格引动·羊陀迭并/七杀重逢 | 6.2 P-5 | 核心设计 §5·R17（卷三·谈星要论） |
| **R18/R19/R20** 告警·大凶/寿元关/重大疾病 | 6.1 P-5、6.4 P-1/P-2 | 核心设计 §5·R18~R20（卷三·谈星要论凶格） |
| **R21** 告警命中即外发 | 6.1 P-5、6.4 P-1 | 核心设计 §5·R21 |
| **R22** 出处绑定 | 6.1 P-7 | 核心设计 §5·R22（原文行号绑定） |
| **R23** 定富局/定贵局成格 | 6.1 P-7、6.2 P-1 | 核心设计 §5·R23（卷一·定富局/定贵局） |
| **R24** 大小限过十二支所忌 | 6.2 P-5、6.1 P-7 | 核心设计 §5·R24（卷三 L92-101） |
| **R25** 行限到宫入庙/不入庙 | 6.2 P-5、6.1 P-7 | 核心设计 §5·R25 |

### 7.4 边界 / 负向场景清单

| # | 边界/负向场景 | 用例 | 预期行为 | 经典依据 |
|---|---|---|---|---|
| 1 | 农历输入（换算器已就绪） | 6.3 B-2 | 不失败；`LUNAR_UNCONVERTED=false` 且保留农历月/日 | 历法〔程序实现·非《全書》断法〕 |
| 2 | 缺经度（回退平太阳时） | 6.2 B-1、6.3 B-1 | `tst=0.0h` | 真太阳时校正〔程序实现〕 |
| 3 | 西经（负经度） | 6.3 B-3 | `TST` 为负 | 真太阳时校正〔程序实现〕 |
| 4 | 时辰交界（卯/申） | 6.2 P-1 | 昼夜边界正确翻转 | 卷二·昼夜（卯至申为昼） |
| 5 | 类别/深度/粒度缺省 | 6.2 B-1、6.3 B-1 | 默认 成人男/本命/年 | 流程设计（缺省兜底） |
| 6 | 非法/缺失生辰 | 6.2 N-1、6.3 N-1 | incident（`PARSE_BIRTH_INPUT_FAILED`） | 输入校验（工程） |
| 7 | 验盘闭环轮次上限 | 6.2 B-8 | 标「需人工复核」终止 | 验盘定盘〔I·61〕 |
| 8 | 网关互斥完备（成人男/本命） | 6.2 B-9/B-10 | 不经 W/C/13/14/15 | 流程设计（互斥且完备） |
| 9 | 缺宫/缺命宫命例 | 6.1 N-1/N-2 | 合规校验报错 | 核心设计 §4 结构约束 |
| 10 | 顺逆冲突断言 | 6.1 N-4 | 一致性失败 | R8/R8b `limitDirection cardinality 1` |
| 11 | 无告警命盘 | 6.4 N-1 | 不投递消息 | R18~R21 告警判据 |

---

## 8. 经典校验与迭代记录

> 第 8 步为**闭环自校验**：每条用例的实际输出交 **codearts 依据经典逐条判定**，给出改进点；按意见改程序后重跑，再校验，**循环直到该用例结果与经典一致**；**全部用例通过后再从头复跑，直到一整轮零改进点**。所有判定与修改须留痕，且**最终结论必须固化为 JUnit 断言**。判定禁止编造——预期/结论须标注经典出处。
>
> **阶段一·离线子集（2026-11-14，环境停）**：`ZiWeiDouShuBpmnInMemoryTest` / `ZiWeiDouShuBpmnRealEngineTest` / `ZiWeiDouShuSwrlRabbitMqTest` 三类经 `ServiceProbe` 自动跳过；「实际输出 vs 经典」校验主体为**离线可跑项** `ZiWeiDouShuStarTableTest`（72）+ `ZiWeiDouShuOntologyTests`（13）+ `ZiWeiDouShuSkosTerminologyTest`（7）= **92**，经一轮修复后零改进点收敛（见 8.2 第 2 轮）。
>
> **阶段二·全量实跑（2026-11-14，环境恢复）**：EnvPrepare 启动（Ontop 8080 / MySQL 3306 / RabbitMQ 5672·15672 / Camunda 9080 / Zeebe 26500）后，`ZiWeiDouShuBpmnInMemoryTest`（内存引擎 + 生产 Worker）、`ZiWeiDouShuBpmnRealEngineTest`（真实 Zeebe 26500 + 生产 Worker 消费）、`ZiWeiDouShuSwrlRabbitMqTest`（SWRL R21 + RabbitMQ）三类**全部实跑、0 跳过**；端到端命理输出复用的安星/庙陷/四化内核已由 StarTable 72 项逐条经典核对，命宫格断语出处见审查报告实测留痕（如 P-1「命宫天同在亥宫，庚年生人 → 下局〔凶〕（卷二·命宫，c3 L549）」）。全量 `AllZiWeiDouShuSuiteTest` **112/112 全绿、0 跳过、零改进点**（见 8.2 第 3 轮）。

### 8.1 逐用例校验与迭代轨迹

| 用例 id | 轮次 | 实际输出（摘要） | codearts 判定（对/错） | 改进点 | 修改内容（程序/测试/本体） | 经典依据 | 复跑结论 |
|---|---|---|---|---|---|---|---|
| STAR-紫微定位 | R1 | 五局初一紫微：水二丑 / 木三辰 / 金四亥 / 土五午 / 火六酉 | 对 | 无 | — | 卷二·安紫微定位棋盘图 | 通过 |
| STAR-紫府系偏移 | R1 | 紫微系{0,-1,-3,-4,-5,-8} / 天府系{0,1,2,3,4,5,6,10}；破军(10)空三（杀破狼） | 对 | 无 | — | 卷二·安南北斗諸星訣 | 通过 |
| STAR-星辰庙陷 | R1 | 紫微@午=庙、太阳@子=陷、太阴@亥=庙、七杀@子=旺、文曲@申=得地、铃星@卯=利益；表外星「平和」兜底 | 对 | 无 | — | 卷二·星辰庙陷（c1 L1039-1088） | 通过 |
| STAR-星辰落闲 | R1 | 紫微子辰亥 / 贪狼寅辰 / 天相辰戌 / 七杀辰亥 / 天梁巳酉 / 天机巳 / 破军巳申 / 武曲申 | 对 | 无 | — | 卷二·定十二宫星辰落闲（c3 L132-134） | 通过 |
| STAR-四化 | R1 | 甲廉破武阳 … 壬梁紫府武（壬化科＝天府，非「左辅化科」异说） | 对 | 无 | — | 卷二·安禄权科忌四星变化诀（c3 L59-62） | 通过 |
| STAR-火铃/龙池凤阁/飞天三杀 | R1 | 火铃按年支三合；龙池子顺辰凤阁子逆戌；飞天三杀寅午戌→亥卯未等四组 | 对 | 无 | — | 卷二·安火铃/龙池凤阁/安飞天三杀诀 | 通过 |
| STAR-七杀朝斗/十等/诸格12/形性賦/同垣 | R1 | 七杀寅申子午为朝斗；福寿/聪明/僧道；12 格全覆盖；破军背重眉宽 | 对 | 无 | — | 卷三·谈星要论/卷一·十等论/诸格/形性賦 | 通过 |
| STAR-太岁小限/大小限所忌/二限太岁/贫富 | R1 | 12 支三段吉凶；子命忌寅申；太岁冲大限；先贫后富/先富后贫 | 对 | 无 | — | 卷三·论太岁小限/论二限太岁/论命先贫后富 | 通过 |
| STAR-运限重算/竹萝三限/流月日/叠合/空宫/验盘 | R1 | 五虎遁/虚龄/男顺女逆；杀破俱见成格；斗君正月；逐层叠；借对宫；候补±1 | 对 | 无 | — | 卷二·竹萝三限 + 外部通行法 I·51/62/61 | 通过 |
| STAR-告警/DDL↔OBDA/下沉保真 | R1 | R18~R21 客观凶判据；DDL 表↔OBDA 全覆盖 fail-fast；`data.sql` 与内置表逐行等值 | 对 | 无 | — | 核心设计 §5 R18~R21 + 本体/库结构 | 通过 |
| ONT-P1~P9 / N-1~N-4 | R1 | 本体加载一致；子类检索非空；R1/R8/R8b/R21 推导命中；36 项 Restriction；缺宫/缺命宫/顺逆冲突报错 | 对 | 无 | — | 核心设计 §4/§5 | 通过（13/13） |
| SKOS-P1~P4 / N1~N2 | R1 | 每概念有 prefLabel；exactMatch 对应本体 IRI；altLabel 带「# 源:」；无孤立；同名跨语言唯一 | 对 | 无 | — | 术语表/本体标签（第 13 步） | 通过（7/7） |
| **I-1 StarTable 未纳入套件** | R1 | 聚合 `AllZiWeiDouShuSuiteTest` 仅 `@SelectClasses` 5 类，72 项经典自检无法在套件里自动重放 | 错（固化缺口） | 将 StarTableTest 纳入聚合套件 | 改测试程序：`AllZiWeiDouShuSuiteTest` 的 `@SelectClasses`/`@SuiteDisplayName`/javadoc 增列 `ZiWeiDouShuStarTableTest` | 第 8 步动作 7「所有检验须纳入套件可自动重放」 | 修复后已纳入，套件离线实跑 72 项 |
| **I-2 Swrl 环境缺失硬报错** | R1 | 环境停时 `ZiWeiDouShuSwrlRabbitMqTest` 于上下文加载阶段连 MySQL/Ontop 抛错（`Tests run:1 Errors:1`），未优雅跳过 | 错（应跳过） | `@BeforeAll(@Autowired Environment)` 强制先载 Spring 上下文，`ServiceProbe` 守卫失效 | 改测试程序：`@BeforeAll` 改**无参**并先 `ServiceProbe.assumeAllReachable`；env 依赖初始化移入实例 `@BeforeEach`（`initBackendOnce`，null 守卫）——与 BpmnInMemory/BpmnRealEngine 同款跳过模式 | 设计原则⑤「环境依赖自动跳过」 | 修复后 0 run 干净跳过，套件转绿 |
| **INMEM-P1~P6/B1/B2/N1（9 项）** | R3 | 生产 Worker 驱动主干/女命/小儿/大限/全链/验盘/缺省/缺 birth 全链实跑；P-1 命盘（1990-05-15 男）命宫亥·身宫丑·土五局、紫微辰天府子、命宫主星天同，`mingGongGe=命宫天同在亥宫，庚年生人 → 下局〔凶〕（卷二·命宫，c3 L549）`；N-1 缺 birth→`PARSE_BIRTH_INPUT_FAILED` incident | 对 | 无 | — | 命盘安星/庙陷/四化内核复用 StarTable 72 项（已逐条核对，出处见各 STAR-* 行）；命宫格断语出处 c3 L549（审查报告实测留痕已核） | 9/9 通过 |
| **REAL-P1~P5/B1~B3/N1（9 项）** | R3 | 真实 Zeebe(26500) 全链实跑；P-1 落 `chartBase/mingGongGe/palaceClassic/idleVerseNote/phaseRelations`，`palaceStrength` 含「男命」；P-2 女命全链 `majorEventBacktrace` 共 2 项·事项宫＝·流年四化、`sanFangJiYao` 三元布尔齐全；P-5 `alertTriggered=true → messageStatus=SENT`；B-2 农历标 `LUNAR_UNCONVERTED=false`（LM=4、LD=21）；B-3 西经 TST=-12.93h；N-1 缺 birth→INCIDENT | 对 | 无 | — | 命盘内核同上（StarTable 已核）；TST 真太阳时＝程序实现（非《全書》断法）；告警外发依 R18~R21（卷三·谈星要论凶格） | 9/9 通过 |
| **SWRL-MQ P1/N1（2 项）** | R3 | P-1 注入命盘+AlertFlag，经 R21 推得 `alertTriggered=true`，SwrlRuleTriggerListener 回调外发至 `ziweidoushuQueue`，消费到含 alertInstance+「大凶」消息；N-1 无 AlertFlag→不回调、不投递、无 alertTriggered | 对 | 无 | — | R21 告警命中即外发（核心设计 §5·R21；卷三·谈星要论凶格） | 2/2 通过 |

### 8.2 整轮收敛记录

| 轮次 | 覆盖用例总数 | 通过 | 遗留改进点 | 是否零改进点（收敛） |
|---|---|---|---|---|
| 第 1 轮（离线子集） | 93 实跑（Star 72 + Ont 13 + Skos 7 = 92 通过；Swrl 1 报错）/ 两 Bpmn 0 run | 92 | 2（I-1 固化缺口、I-2 Swrl 硬报错） | 否 |
| 第 2 轮（修复后复跑） | 92 通过（StarTable 已入套件）；Swrl / BpmnInMemory / BpmnRealEngine 均干净跳过（0 error） | 92 | **0** | **是（离线子集收敛）** |
| 第 3 轮（环境恢复·全量实跑） | **112 实跑**（Star 72 + Ont 13 + Skos 7 + InMemory 9 + RealEngine 9 + Swrl 2），**0 跳过** | 112 | **0** | **是（全量收敛，0 跳过）** |
| 第 4 轮（代码覆盖率补强·JaCoCo） | **155 实跑**（Star 72 + Ont 13 + Logic 25 + InMemory 9 + RealEngine 9 + Swrl 2 + Skos 7 + Controller 18），**0 跳过** | 155 | **0** | **是（代码覆盖率补强后全量收敛）** |

> **结论**：离线可跑项（StarTable 72 + Ontology 13 + SKOS 7 = 92）经一轮修复后**零改进点收敛**；环境恢复后**全量 112/112 实跑全绿、0 跳过**（含内存引擎 / 真实引擎 / SWRL+RabbitMQ 三类端到端），逐用例经典校验无遗留改进点，**零改进点收敛**。全部结论已固化为 JUnit 断言并由 `AllZiWeiDouShuSuiteTest` 自动重放，第 8 步回归闭环完成。

### 8.2b 阶段三·农历换算缺陷修复与全量重评（2026-11-14）

> **缺陷（确证，非评审误判）**：`ZiWeiDouShuOntologyJobWorker` 公历入参未做农历换算——`resolveLunarMonthDay` 直接取出生串第 2/3 段当农历月/日、`lunarToSolar(String)` 恒返回 `null`，致 `chartBase` 标 `LUNAR_APPROX=true`，S1（公历 1990-05-15）输出 `LM=5;LD=15`（真值农历四月廿一）；且出生年干支/虚岁按公历年而非农历年计算，跨年命例（如公历 1960-01-01 实为农历 1959 年）年柱随之错。缺陷传导安星、四化、格局、大限全链。
>
> **修复**：① 新增 `LunarCalendarConverter`（移植 1900–2099 `LUNAR_INFO` 表算法，提供 `solarToLunar`/`lunarToSolar` 双向换算），独立校验与 Python `lunardate 0.3.0` **12/12 逐例一致**（含 1960-01-01→1959-12-3、1999-12-31→1999-11-24 跨年例）；② 改 `parseBirthInput`：农历入参经 `lunarToSolar` 反推真公历、公历入参经 `solarToLunar` 换算真农历，`chartBase` 新增 `LY`（农历年）字段、`LUNAR_APPROX` 恒 false、仅换算失败才标 `LUNAR_UNCONVERTED`；③ 删死函数 `resolveLunarMonthDay`/`lunarToSolar(String)`，新增 `birthLunarYear`/`birthGanOf`/`birthBranchOf`（读 `LY`，缺则回退公历年）；④ 批量替换 18 处出生年调用点（安命宫/辅星/杂曜/四化/验盘/星情/格局/小儿/大限/流年/叠宫）；**流年/大限路径保持公历年口径**（双历各归其位）。
>
> **验证**：重启后实跑 S1（1990-05-15），`chartBase = LY=1990;LM=4;LD=21;LUNAR_APPROX=false;LUNAR_UNCONVERTED=false`，与 `lunardate` 及 LLM 独立推算（农历四月廿一）一致。
>
> **全量重评（9 层 LLM·依《全書》独立裁判）**：产物见 `target/classic_zwds/summary_llm1_*.json`，改进点汇总 `summary_llm1_all_improvements.json` 共 **260 条**。逐层 overall 分布：安星 8/8、格局 8/8、本命 7/8、庙陷 6/8 判「不准确」；四化 2/8「准确」。`S3_小儿` 因 Camunda 客户端 poll `SocketTimeoutException:11000ms`（环境抖动，命盘已生成）标 `INCIDENT`，非农历逻辑问题。**说明**：260 条改进点集中于辅煞起例、星辰庙陷表、格局成破条件等**流派差异/口径分歧**，须逐条依典籍甄别后迭代，**尚未达零改进点收敛**；本轮仅确证并修复农历换算这一确定性缺陷。
>
> **注**：农历修复属本体应用（JobWorker）逻辑改动，第 8 步原则上固定程序；因系经 `lunardate` 与 LLM 双向佐证的确定性换算缺陷（公历被误当农历致全链错误），经用户授权后修复，特此留痕。

### 8.3 代码级覆盖率度量（JaCoCo，2026-11-14 补强）

> 目的：把「全面覆盖代码」落为**可量化验收**。接入 `jacoco-maven-plugin 0.8.16`（`prepare-agent` + `report`，绑定 `test`），`mvn test` 后产出 `target/site/jacoco`（html/xml/csv）。为补齐 Web 入口与纯逻辑分支，新增两类测试并纳入套件：`ZiWeiDouShuControllerTest`（standalone MockMvc，18 项，覆盖 `/api/ziweidoushu/chart`、`/chart/{key}`、`/health` 全部校验/异常分支，静态打桩 `ProcessOrchestrator`）、`ZiWeiDouShuJobWorkerLogicTest`（25 项，覆盖 JobWorker 纯函数边界/守护分支：地支/天干抽取、格局吉凶、煞凑识别与判定、计数、解析兜底等）。

覆盖率结果（`AllZiWeiDouShuSuiteTest` 155 项全跑后，jacoco csv）：

| 类 | 行覆盖 | 分支覆盖 | 指令覆盖 | 方法覆盖 |
|---|---|---|---|---|
| ZiWeiDouShuController | **100%** (107/107) | **100%** (36/36) | **100%** (703/703) | 100% (7/7) |
| ZiWeiDouShuOntologyJobWorker | 92.39% | 74.60% | 94.28% | 97.82% |
| ZiWeiDouShuOntologyJobWorker.ShaCou | 100% | — | 100% | 100% |
| ZiWeiDouShuApplication（启动类） | 10.00% | 0% | 6.82% | 50% |
| **整体（4 类）** | **92.39%** | **74.76%** | **94.29%** | **97.49%** |
| 业务内核（JobWorker+ShaCou，排除启动类） | 92.40% | 74.60% | 94.28% | 97.83% |

> **判定与口径**：Web 接口层已 **100%** 覆盖；业务内核 JobWorker 行覆盖 92.4%、分支 74.6%。**未覆盖部分性质**：① `ZiWeiDouShuApplication.main`（9 行，Spring Boot 启动入口，常规排除）；② JobWorker 剩余约 294 死行，集中于**异常兜底/罕见分支**与 **LLM 未装配时的解读提示词构建路径**（`buildInterpretUserPrompt`/`buildInterpretPlainUserPrompt` 等），需扩展业务场景或装配可替身模型方可触达，非纯单元可覆盖。故本轮可验收标准为：「流程节点/分支全覆盖（§7）」+「Web 接口 100%」+「业务内核近全覆盖（行 92%/分支 75%）」；如需逼近 100%，下一步须按业务场景扩充端到端输入。

### 8.3b B 类（罕见数据组合·纯判断）残余分支定向补强（2026-11-19）

> 在 §8.3 基础上，针对 JaCoCo 报告中「B 类（罕见数据组合·纯判断/工具函数）残余分支」逐函数定向补测。实测 `ZiWeiDouShuBirthFuzzCoverageTest` 与 `ZiWeiDouShuHuntCasesInMemoryTest` 对残余分支增量 **=0**（与既有 E2E/模糊用例重叠），证明残余分支只能靠定向单测补齐。为此新增五个**离线反射纯函数**测试类（`invoke(null)`、不依赖任何外部环境，断言业务化）并纳入 `AllZiWeiDouShuSuiteTest`：
> - `ZiWeiDouShuBClassResidualCoverageTest`：金舆扶驾/日月夹（`isJinYuFuJia`/`isJiaBothSides`）、太微具名格（`judgeTaiweiMingGe`）、煞凑短语（`detectShaCou`）。
> - `ZiWeiDouShuBClassResidualCoverageTest2`：字符串/整型小工具（`containsAny`/`eq`/`firstLine`/`toInt`/`splitStarSet`/`ganList*`）、五行局/空亡/六合（`bureauOf`/`xunZhongKongwang`/`sixHePartner`）、化禄与夹宫（`huaInPalace`/`palaceHasLu`/`isZhuLuoSanXian`/`hasJuMenOrSha`/`isBuShuWuYi`/`isShuiShangJiaXing`）。
> - `ZiWeiDouShuBClassResidualCoverageTest3`：`isStarBlockOpen`/`classifyGe`、星辰生克（`palaceStarRelation`）、十二宫得地格（`judgePalaceZhiGrid`）、流年太岁（`judgeTwoLimitsTaiSui`）、验盘星（`ziweiHuaNote`/`judgeXianPinHouFu`/`judgeLongevity`）。
> - `ZiWeiDouShuBClassResidualCoverageTest4`：历法干支/四化/小限（`ganOfYear`/`branchOfYear`/`hourBranchOf`/`sihuaStars`/`daXianPalace`/`xiaoXianPalace` 等）、合夹（`starsSplitClamp`/`isJiaMingQuote`）、验盘特征（`verifyStarTraits`/`extractStatedTraits`）、命格高低（`gradeOf`）。
> - `ZiWeiDouShuBClassResidualCoverageTest5`：三方四正计数/运限符号（`countInSanFang`/`hasJiInSanFang`/`yunxianFortuneSign`/`annualFortuneSign`/`fortuneGlyph`）、合夹（`jiaPair`/`isKongJie`/`hasSha`）、羊陀迭并/七杀重逢/流年太岁/大限十年/十等论（`judgeYangTuoDieBing`/`judgeQiShaReFeng`/`judgeLiuNianTaiSui`/`judgeDaXianShiNian`/`judgeTenGrades`）。

| 阶段 | JobWorker 分支覆盖 | 分支率 | 行覆盖 |
|---|---|---|---|
| 基线（存量全量套件） | 3915/4352 | 89.96% | 3933/4060（96.87%） |
| B 类五批定向补测后 | **3995/4352** | **91.80%** | **3954/4060（97.39%）** |

> **净收益**：新增覆盖 **80 个 B 类分支** 与 21 行（分支率 +1.84pp、行率 +0.52pp）；五个测试类共 15 个测试方法，全部离线可跑、随套件回归全绿。
>
> **不可达分支登记（JaCoCo 排除理由）**：`detectShaCou` 经源码分析确证两处**恒不可达防御分支**——① `stars.isEmpty()`：`detectShaCou` 仅在文本命中煞短语时被调用，上游保证星集非空；② 「同宫」scope 对应的空星集侧：数据表中该 scope 恒对应非空星集，该侧永不进入。二者为纯防御性 `null/空集` 检查，无法由任何数据组合触达，依 §8.3 口径「确不可覆盖者须显式排除并登记理由」登记为不可达，不计入补测目标。剩余 357 分支绝大多数属 **A 类**：整盘/经典表驱动的格局判定（`handleDeterminePattern`、`judgeTongYuan`、`judgeMingGongGe`、`verifyConsistency`）、LLM 解读提示词构建（`build*`、`lambda$handleInterpretStarNature$1`）、OBDA 状态装载回调（`lambda$loadStateFromObda$0`）与异常兜底路径，需真实引擎/LLM 装配或整盘经典文本方能触达，非纯单元可覆盖。
>
> **全量 E2E 回归（2026-11-19，环境就绪）**：`mvn -o -f OntologyFrameworkZiWeiDouShu/pom.xml -Dtest=AllZiWeiDouShuSuiteTest test` → **`Tests run: 273, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（JDK 27，740.6s；Camunda 网关 26500 + RabbitMQ + MySQL + Ontop 全就绪，零跳过）**，套件已含 5 个 B 类补测类。JaCoCo 复核与上表一致：JobWorker 分支 **91.80%（3995/4352）**、行 **97.39%（3954/4060）**、指令 98.16%（38877/39605）、方法 **100%（258/258）**。日志 `target/full.out`（stderr 仅 Mockito/ByteBuddy/agrona 动态代理 WARNING，无 ERROR/失败）。

### 8.3c B 类残余分支二次补强：③格局/经典 · ④运限 · ⑥入参历法 · 零星工具（2026-11-19）

> 承接 §8.3b 残余分析（357 分支/84 方法），本轮聚焦用户点名的四类中**可离线反射（`invoke(null)`）触达的纯函数**再补一批，新增四个测试类并纳入 `AllZiWeiDouShuSuiteTest`，合计 **36 个测试方法，逐类实跑全绿**：
>
> - `ZiWeiDouShuBClassResidualCoverageTest6`（**⑥ 入参解析/历法**，6 用例）：`birthCalendar`（Map/字符串/空值→农历/公历）、`resolveBirthDateTime`（datetime 成功/数值回退/字符串多格式/不可解析抛异常）、`parseChartBase`（null/正常/无等号/首字符等号）、`representativeHour`（空/短/缺时/合法/非法字）、`isBranch`；并**反射置静态 `ONTO_*` 缓存字段**精确覆盖 `sihuaStars/ziweiBranch/lucunBranch/liuyangBranch/liutuoBranch/wuhuYinGan/deityName` 的「缓存齐全/缺键回退/越界回退」对侧分支（`@AfterEach` 复位为 null，避免污染）。
> - `ZiWeiDouShuBClassResidualCoverageTest7`（**B 类零星工具**，15 用例）：`parsePlacements`、`palaceStarRelation`（比和/宫生星/宫克星/星生宫/星克宫/无五行/越界）、`brightnessOf`、`isIdleStar`、`isAnyStarBlockOpen`、`detectShaCou`（null/无匹配/羊陀火铃/巨宿羊陀/羊陀冲破/羊火）、`shaCouSatisfied`（null/同宫命中与缺失/三方四正，含直接构造 `ShaCou`）、`isJinYuFuJia`（非紫微守命/越界/日期非法/两侧分居/同侧）、`isBuShuWuYi`/`isShuiShangJiaXing`、`hasJuMenOrSha`、`distillMarkup`、`starsSplitClamp`、`gongFallenWithSha`、`judgePalaceZhiGrid`、`ganListMatches`/`ganListHasSuspectToken`。
> - `ZiWeiDouShuBClassResidualCoverageTest8`（**③ 整盘/格局/经典表判定**，5 用例）：`judgeMingGongGe`（3 参：14 主星×12 支×10 干全矩阵 + 空/边界；4 参重载带含四煞之盘驱动煞凑布尔分支）、`judgeTongYuan`（12 命宫×10 生干×两盘，经重载驱动 `tongYuanObjective*`）、`judgeTanXingYaoLun`（命未定/全命宫×富盘/空盘）、`judgeTaiweiMingGeExtended`、`gradeOf`/`candidateMingStars`/`verifyCandidateScore`/`isAnyStarBlockOpen`。
> - `ZiWeiDouShuBClassResidualCoverageTest9`（**④ 运限**，10 用例）：`judgeLongevity`（越界×富盘/空盘）、`judgeTenGrades`、`judgeXiaoErMing`、`taisuiXiaoxianGanHit`（干/支越界返回空 + 合法非空）、`judgeLiuNianTaiSui`、`majorEventBacktrace`（null/空/无年份/多事件分隔切块含「共 2 项」）、`ziweiLongevityVerdict`、`ziweiMainStarDesc`、`ziweiChartLevelVerdict`/`ziweiPalaceVerdictLine`、`ziweiSuiXianOverlay`（出生信息不足回退 + 正常多年叠看）。
>
> **实跑结果（离线单类回归，JDK 27）**：`Test6` 6/6、`Test7` 15/15、`Test8` 5/5、`Test9` 10/10，`Failures/Errors/Skipped` 均 0，`test-compile` EXIT=0；四类已加入 `AllZiWeiDouShuSuiteTest.@SelectClasses`（现并列于 `...Test`~`...Test9`）。
>
> **说明与边界**：本批仅新增测试代码（未改 `src/main`、未改 TBox/属性/DDL/SWRL）。残余分支中**仍属 A 类（需真实 LLM/OBDA/整盘经典文本或真实引擎 ioMapping）**者——如 `handleDeterminePattern`、`handleAnalyzeAnnualFortune`、`handleAnalyzeMajorLimit`、`handleAnalyzePalaceOverlay`、`handlePersistAndReturn`、LLM `build*`/`lambda$handleInterpretStarNature$*`、`lambda$loadStateFromObda$0`——非纯单元可覆盖，需下一轮整盘矩阵/真实装配补齐；`detectShaCou` 两处恒不可达防御分支维持 §8.3b 登记排除。累计覆盖率已由全量 E2E 复核（见下）。

> **实测覆盖率增量（全量 E2E，JDK 27，环境全就绪，无跳过）**：
>
> | 阶段 | JobWorker 分支覆盖 | 分支率 | 行覆盖 | 行率 |
> |---|---|---|---|---|
> | §8.3b 五批后（基线） | 3995/4352 | 91.80% | 3954/4060 | 97.39% |
> | §8.3c 六~九批后 | **4032/4352** | **92.65%** | **3967/4060** | **97.71%** |
>
> **净收益**：新增覆盖 **37 个分支** 与 **13 行**（分支率 **+0.85pp**、行率 **+0.32pp**）；方法覆盖维持 **100%（258/258）**、指令 **98.29%（38926/39605）**。已达本轮「分支 ≥92.5%」目标。
>
> **全量回归**：`mvn -o -f OntologyFrameworkZiWeiDouShu/pom.xml -Dtest=AllZiWeiDouShuSuiteTest test` → **`Tests run: 309, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（688.3s）**，套件现含 9 个 B 类补测类（`...Test`~`...Test9`）；`full.err` 仅 JDK/agrona/ByteBuddy 警告，`ERROR` 命中 **0**。



### 8.3d 业务分支专项补强（只补业务、不补 IT）（2026-11-19）

> 用户决定「**同意修补所有业务分支，IT 漏洞不修补**」。本轮在前述 §8.3c 基础上，针对残余分支中**业务语义相关且可离线驱动**者再补两批，新增两个测试类并纳入 `AllZiWeiDouShuSuiteTest`，合计 **41 个测试方法，逐类实跑全绿（27/27、14/14）**；未改 `src/main`、未改 TBox/属性/DDL/SWRL/BPMN/OBDA。
>
> - `ZiWeiDouShuBClassResidualCoverageTest10`（**业务纯函数**，27 用例，反射 `invoke(null)`）：`taisuiXiaoxianGanHit`（干/支越界+全扫）、`brightnessVariantNote`、`expandTail`、`verse`（ONTO 命中/空/缺键）、`deityName`（短数组命中/越界回退）、`stripParen`/`shortQuote`、`isAnyStarBlockOpen`（ONTO 含 null/空/命中行）、`isJinYuFuJia`（日月相对全扫）、`judgeTaiweiMingGeExtended`、`hasJuMenOrSha`、`judgeTenGrades`（威勇/富贵 OR 假支）、`judgeTanXingYaoLun`（聚吉/聚凶/缺宫跳过/地空/皆无）、`judgeLongevity`（全命宫×身宫扫+艺术破武未/僧道六星/破相/富足）、`judgePalaceZhiGrid`（全命宫×生干(-1..9) 全星盘）、`bureauOf`（全干×全支）、`palaceBranch`、`palaceStarRelation`（全星×全支含星克宫）、`brightnessOf`（ONTO 缓存命中/空位回退/表外）、`candidateMingStars`、`verifyCandidateScore`（含痣癍/不含）、`gradeOf`、`starsSplitClamp`、`majorEventBacktrace`（空块/未识别/正负中性）、`ziweiLongevityVerdict`、`ziweiPalaceVerdictLine`、`ziweiSuiXianOverlay`（坏 SOLAR/幼龄未起限/正常）。`ONTO_*` 静态缓存经反射注入并在 `@AfterEach` 复位 null。
> - `ZiWeiDouShuBClassResidualCoverageTest11`（**业务 BPMN 节点驱动**，14 用例，Mockito 深桩 `ActivatedJob`/`JobClient`）：以合成整盘 vars 驱动 `handleParseBirthInput`（公历/农历换算/农历越界/空生辰失败）、`handlePlaceMainStars`（正常/局·日越界失败）、`handlePlaceMiscStars`（命身在/缺命身宫）、`handlePlaceFourTransform`（正常/四化星全未安待考）、`handleAssessBrightness`、`handleEmptyPalace`（稀疏含对宫亦空/满盘）、`handleVerifyChart`（正常比对/缺特征 null·空白·不匹配）、`handleGenerateNatalReading`、`handleDeterminePattern`、`handleAnalyzeAnnualFortune`（年/月/日/未知粒度+男女+空盘+无事件）、`handleAnalyzeMajorLimit`（成年/幼龄未起限/缺身宫/空盘）、`handleAnalyzePalaceOverlay`（带月日/无月日/幼龄）、`handleAggregateReading`（富集分节/文星暗拱/空分节）。
>
> **实测覆盖率增量（干净全量口径，JDK 27）**：
>
> | 阶段 | JobWorker 分支覆盖 | 分支率 | 方法覆盖 |
> |---|---|---|---|
> | §8.3c 后（基线） | 4032/4352 | 92.65% | 258/258 100% |
> | §8.3d 业务专项后 | **4099/4352** | **94.19%** | **259/259 100%** |
>
> **净收益**：新增覆盖 **67 个业务分支**（分支率 **+1.54pp**，92.65%→94.19%），指令约 98.64%、方法 100%。其中绝大部分为安星/庙陷/五行局/神煞/星辰生克/十等论·诸格论/寿元/夹拱/重大事件回推/验盘/运限/综合汇总等**算命业务语义分支**；按要求未专门补 OBDA/LLM/告警/持久化等 IT 类分支（如 `loadStateFromObda` 38、`handleInterpretStarNature` 30、`handleSendAlert` 10、`verifyConsistency` 14 等）。
>
> **残余（业务）**：仍余约 253 分支未覆盖，其中业务类多为**死支/布尔互补支/数据形态不可达**——例如：`i<0` 会越界（`deityName`）、`pe==(se+2)%5` 互补支、`judgeTenGrades` 中 `ji&&sha==0` 已在上一 if 返回（后续 AND 的 `sha>0` 假支不可达）、`judgeMingGongGe`/`judgeTongYuan` 部分分支依赖《全書》原文特定命中（数据文本形态），以及真实引擎 ioMapping 相关。这些非新增测试可安全触达，已在代码/数据形态约束下尽量收敛。
>
> **全量回归记录**：`mvn -o -pl OntologyFrameworkZiWeiDouShu test -Dtest=AllZiWeiDouShuSuiteTest` → `Tests run: 349, Failures: 13, Errors: 0, Skipped: 0`。其中**新增 `Test10` 27/27、`Test11` 14/14 全绿**，全部离线/内存业务测试全绿；**13 个失败全部为「真实引擎」三类**（`ZiWeiDouShuBpmnRealEngineTest` 10、`RealEngineDataValue` 1、`PatternMatrix` 2），失败点均为 `ProcessOrchestrator.deployIfAbsent` 对 Camunda 网关 `newDeployResourceCommand` 抛 **HTTP 500 Internal Server Error**（`ProblemException`），即流程部署阶段的环境/网关故障，与本轮测试代码及数据集断言无关；**隔离重跑该三类亦复现同样 13/13 的网关 500**，进一步佐证属环境侧问题（本轮大量离线实跑后本地 Camunda 网关进入 500 状态）。建议**重启本地 Camunda(26500)/Ontop/MySQL 后重跑**，真实引擎三类即可恢复绿（其消费的 Java 处理器与内存引擎同源，分支覆盖不受影响）。


### 8.3e 死支根因复核 + 同垣空值守卫修复 + Test14（2026-11-20）

> 承接 §8.3d。用户质疑「部分死支（L6297/L6528）不合理」，遂**逐行复核源码 + 读取实时 JaCoCo 行级 mb/cb 实测**追根因，并顺带修复 `tongYuanObjectiveFull` 的 Integer 拆箱 NPE、新增 `ZiWeiDouShuBClassResidualCoverageTest14`（纳入套件）。

**① 死支根因（行号以 §8.3d 后源码为准，实测 mb=未覆盖分支数 / cb=已覆盖分支数）**

| 位置 | 实测 | 根因（实质） | 判定 |
|---|---|---|---|
| `judgeLongevity` L6297「破武未宫」 | mb=1 / cb=5 | `artPalace` 已含「未」，且含武曲时前一析取 `artPalace && (贪狼\|\|武曲)` 即真，短路使本析取「武曲==true」侧永不被评估 ⇒ **条件冗余**（一般式「丑未辰戌逢武曲」已覆盖「破武未宫」） | 真·冗余（非测试缺口） |
| `judgePalaceZhiGrid` L6528 `fgArr.length>0 ? : ""` | mb=1 / cb=1（false 侧未到） | 该行处于 `!matched.isEmpty()` 守卫内 ⇒ `row[1]` 必含非「、」token ⇒ `split("、")` 长度恒 ≥1 ⇒ 三元 false 侧不可达 | 真·冗余防御式 |
| `judgeLongevity` L2307/2308 `inSanFang(mingIdx,"左辅"/"右弼")`、L6332 `inSanFang(fumuIdx,"天刑")` | 首操作数恒 false（靠 `jia.contains`/`|| 疾厄擎羊` 兜底） | `inSanFang(int,String)` 内为 `indexOf(ZHI, branchStr)`，**参数必须是「地支串」**；此处却传「星名」⇒ `indexOf=-1` 恒 false。属**调用点传参缺陷（潜在 bug）**，非天然不可达 | 传参缺陷（建议修，涉业务语义待领域确认） |

> 结论：L6297/L6528 为**真·冗余（可安全归类死支）**；而 L2307/2308、L6332 的「恒 false」不合理——是**把星名喂给只吃地支的 `inSanFang`** 的 bug（注释「原实现 inSanFang 传星名恒 false」只修了部分调用点，仍有残留）。修正范式：`inSanFang(idx, branchOfStar(at,"天刑"))`（改后会使「破相·父母逢刑」「辅弼拱主」等命中更准，属业务行为变化，宜经领域复核后再改）。

**② 空值守卫修复（`tongYuanObjectiveFull`，保持既有判定语义）**
- A1「天梁守照吉相逢」：`boolean anchor = liang != null && (liang == mingIdx || inSanFang(mingIdx, ZHI[liang]))`——原 `liang==mingIdx` 在盘无天梁时拆箱 NPE。
- A2「巨陷天机为破格」：`ju != null && isFallen("巨门", ju)`——原 `isFallen(String,int)` 拆箱 NPE。
- 缺星时改为返回「〔客观·否…〕」，对全 14 主星齐备的真实盘行为不变。

**③ 新增 `ZiWeiDouShuBClassResidualCoverageTest14`（4 用例全绿）**：`tongYuanObjectiveFullNullGuards`（A1/A2 缺星不抛 NPE 且返回「否」）、`tongYuanObjectiveFullPresentStars`（天梁守命+三方见吉／巨门在盘正常返回）、`poXiangViaJiEPalaceQingYang`（疾厄擎羊→破相）、`congMingViaWenChang`（文曲守命三方会文昌→聪明）。

**④ 覆盖率增量（干净单次全量口径，删 `jacoco.exec` 后含 Test14）**

| 阶段 | 主类分支 | 分支率 | 方法 |
|---|---|---|---|
| full13（Test13 后） | 4094/4352 | 94.07% | 257/258 |
| full14（Test14 + 空值守卫） | **4100/4358** | **94.08%** | 257/258 |

> 新增 6 个空值守卫分支（分母 4352→4358），Test14 全部覆盖（covered 4094→4100）——即**硬化新增分支零未覆盖**；`$ShaCou` 1/1，合并方法覆盖 258/259。

**⑤ 全量回归记录**：`mvn -o -pl OntologyFrameworkZiWeiDouShu test -Dtest=AllZiWeiDouShuSuiteTest` → **`Tests run: 361, Failures: 14`**。其中 **Test14 4/4 全绿**；**14 项失败均真实引擎类**：`BpmnRealEngine` 10 + `RealEngineDataValue` 1 + `PatternMatrix` 2（＝基线 13），**外加 `ChartGrid` 1**——四者失败点均为 `ProcessOrchestrator.deployIfAbsent → newDeployResourceCommand` 抛 **Camunda 网关 HTTP 500**。`ChartGrid` 在 `full13` 基线时本 **22/22 通过（34s）**，本次运行期间本地网关退化后 **21/22**（另一例 deploy 500）且因网关长时无响应致该用例墙钟异常拉长；**属环境侧故障，与本次 src 改动/断言无关**。建议重启本地 Camunda(26500)/Ontop/MySQL 后重跑以复绿。
**⑥ 环境故障修复 + 真实引擎四类复跑全绿（2026-11-20）**

> 承接 ⑤。经排查确认本地 **Zeebe broker partition-1 处于 `health: unhealthy`**：`zeebe.log` 持续刷 `NoRemoteHandler ... subject command-api-1`；`GET :9080/v2/topology` 该分区 `health=unhealthy`；`:9600/actuator/health` 曾返回 **503**；`lastCompletedChangeId=-1` 长时间无进展——即网关退化导致 `newDeployResourceCommand` 抛 **HTTP 500**，与 ⑤ 记录完全吻合（**确为环境侧，非源码/断言回归**）。
>
> **处置**：停 StandaloneCamunda（旧 PID 51436）→ **备份而非删除** raft 数据（`raft-partition`、`.topology.meta`、`camunda-data` 分别重命名为 `*.bak-20261120-101616`，可回滚）→ 以 `D:\work\Ontology\OntologyMachine` 为工作目录重启（保证 H2 相对路径 `./camunda-data` 解析正确），新 PID 49056。**验证**：`/v2/topology` partition-1 → `role=leader, health=healthy`；`/actuator/health` → `status=UP`（`brokerReady`/`brokerStatus`/`rdbmsStatus` 全 UP，`database=H2`）。
>
> **复跑（仅原失败四类）**：`mvn -o -pl OntologyFrameworkZiWeiDouShu test -Dtest=ZiWeiDouShuBpmnRealEngineTest,ZiWeiDouShuRealEngineDataValueTest,ZiWeiDouShuPatternMatrixTest,ZiWeiDouShuChartGridTest` → **`Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`（总 7:35 min）**。逐类：`BpmnRealEngine` **10/10（43.66s）**、`ChartGrid` **1/1（47.08s）**、`PatternMatrix` **2/2（85.63s）**、`RealEngineDataValue` **1/1（266.8s）**。运行期端到端链路（生产 Worker 消费全部 serviceTask → `sendAlert` 经 RabbitMQ `SENT` → `persistAndReturn` 入库 `case_record`）均正常生效。
>
> **结论**：⑤ 记录的 14 项失败（10+1+2+1）**全部由本地 Zeebe 网关退化引起，非测试代码/数据集断言问题**；重启修复后**四类 14 例全绿**，佐证判定。覆盖率口径（④：主类分支 4100/4358＝94.08%，基线不受本次环境操作影响）。





## 9. 执行方式

- 全套入口：`AllZiWeiDouShuSuiteTest`（`@Suite` + `@SuiteDisplayName` + `@SelectClasses`，聚合 6.1~6.4 四个类 + 词汇表 6.5 + **经典自检 6.6** + **REST 接口层 `ZiWeiDouShuControllerTest` + 纯逻辑 `ZiWeiDouShuJobWorkerLogicTest`**，共 8 类）。
- **覆盖率度量**：`mvn test` 已接入 JaCoCo，报告见 `target/site/jacoco/index.html`；`ZiWeiDouShuControllerTest` / `ZiWeiDouShuJobWorkerLogicTest` 均为**离线可跑**（不依赖外部环境）。
- **离线可跑子集**（无需环境）：`ZiWeiDouShuStarTableTest` + `ZiWeiDouShuOntologyTests` + `ZiWeiDouShuSkosTerminologyTest`，命令 `mvn -o test -Dtest=AllZiWeiDouShuSuiteTest`（环境类将经 `ServiceProbe` 自动跳过）。
- IDEA：右键 `AllZiWeiDouShuSuiteTest` → Run；命令行：`mvn -pl OntologyFrameworkZiWeiDouShu test`（或 reactor 全量 `mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkZiWeiDouShu -am test`）。
- 环境依赖项显式标注：集成类需 Ontop(8080)+MySQL(3306)；端到端类需 Zeebe(26500)；SWRL 消息类需 RabbitMQ；不可达时按 `ServiceProbe` 自动跳过并记日志。
- **回归纪律**：第 8~13 步每步完成后跑 `AllZiWeiDouShuSuiteTest`，全绿方可进入下一步。

---

## 10. 待确认项

| # | 问题 | 结论 |
|---|---|---|
| 1 | 测试 Profile 命名（`ZiWeiDouShuJunitTest` / `ZiWeiDouShuBPMN`）是否认可？ | 待闸门⑤确认 |
| 2 | 输入场景 S1~S8 的构造值是否需按业务指定实际命例？ | 待闸门⑤确认 |
| 3 | 告警分支（G4a）用「预置告警探针个体」保证可达是否认可？ | 待闸门⑤确认 |
| 4 | 词汇表测试类是否本步先占位、第 13 步启用？ | **已定（闸门⑤）**：第 8 步先占位（未产出术语表时经 `assumeTrue` 跳过），第 13 步产出 `terminology.ttl` 后启用并纳入套件 |

---

## 11. 第 14 步·词汇表测试与全流程回归留痕

**执行日期**：2026-10-31　**执行人**：码道　**确认人**：陈希夷（领域专家）

### 11.1 词汇表测试（`validation/ZiWeiDouShuSkosTerminologyTest`，纯 Jena 离线）

- 术语表 `ontology/ziweidoushu-terminology.ttl`（374 概念）就绪，本类**自动启用**（第 13 步产出后不再跳过）。
- 用例 **6/6 全绿**：P-1 结构有效 / P-2 概念映射本体内 IRI / P-3 规范名取自本体 / **P-4 别名标注来源（含「# 源:」可追溯加固）** / N-1 无孤立概念 / N-2 同义归一（一名一概念，含 `prefLabel` 跨语言唯一）。
- **第 14 步加固（仅改测试程序，非本体/应用）**：`P-4 altLabelHasSource` 在原有「字面量 + 语言标签」基础上，**追加逐条 `skos:altLabel` 行须带「# 源:」来源注释**的行级校验（对齐前例 `SiZhuBaZiSkosTerminologyTest.S-2`），使「同义词/别名均可追溯来源」由弱代理（语言标签）升级为**直接校验来源注释**。

### 11.2 全流程回归（`AllZiWeiDouShuSuiteTest`，含第 12 步全集数据）

- **环境**：`EnvPrepare start` 就绪（Ontop 8080 / MySQL 3306 / RabbitMQ 5672·15672 / Camunda 9080 / Zeebe 26500）；`ZiWeiDouShuApplication`（Profile `ZiWeiDouShuBPMN`）启动，22 个 `@JobWorker` 全部注册订阅。
- **结果**：`Tests run: 39, Failures: 0, Errors: 0, Skipped: 0` → **BUILD SUCCESS**（**无跳过**，全部实跑）：

| 测试类 | 用例数 | 结果 |
|---|---|---|
| `ZiWeiDouShuOntologyTests`（推理 + 合规） | 13 | 13/13 ✅ |
| `ZiWeiDouShuBpmnInMemoryTest`（内存引擎端到端） | 9 | 9/9 ✅ |
| `ZiWeiDouShuBpmnRealEngineTest`（真实引擎端到端） | 9 | 9/9 ✅ |
| `ZiWeiDouShuSwrlRabbitMqTest`（SWRL + 消息） | 2 | 2/2 ✅ |
| `validation/ZiWeiDouShuSkosTerminologyTest`（词汇表） | 6 | 6/6 ✅ |
| **合计** | **39** | **39/39 ✅** |

- **构建链**：`OntologyFrameworkZiWeiDouShu` 经 reactor（`-am`，含 `OntopOBDAHandler`/`OpenlletResolver`/`Utilities`）构建通过；应用可执行 jar 打包成功（`spring-boot-maven-plugin` + `surefire(useManifestOnlyJar=false)` + `maven-source-plugin`），`EnvPrepare start → 应用 test` 可复现（★铁律 1/2/19）。
- **机检**：`scripts/validate_structure.py --app-dir OntologyFrameworkZiWeiDouShu --short ZiWeiDouShu` → **ERROR=0** WARN=1 INFO=57；**禁区模块零改动**（`git status --porcelain OntologyResolver OpenlletResolver OntopOBDAHandler` 为空）。

### 11.3 结论

第 14 步**词汇表测试与全流程测试全部通过**，构建链可复现，**14 步流程构建完成**。

---

## 闸门⑤留痕

| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-31 | v1 | **陈希夷（领域专家）** | **闸门⑤通过 ☑**：认可分层策略、用例清单（集成 13 + 内存 9 + 真实 9 + SWRL 消息 2 + 词汇表 6）、覆盖矩阵（节点/约束/边界）与 §9 待确认项结论；**准予固化测试用例**并跑回归。无修改点。 |

---

## 变更记录

| 日期 | 版本 | 修改人 | 说明 |
|---|---|---|---|
| 2026-10-31 | v1（初稿·待闸门⑤） | 码道 | 据第 2–7 步定稿工件（流程设计 21 节点、本体核心设计 §4 约束/§5 SWRL、应用架构设计 §4 契约表、实际 BPMN）产出：分层策略、环境与配置、测试数据、用例清单（集成 13 + 内存 9 + 真实 9 + SWRL 消息 2 + 词汇表 6）、覆盖矩阵（节点/约束/边界）。**待业务人员闸门⑤确认后方可固化测试用例。** |
| 2026-10-31 | v1.1（第 8 步回归加固） | 码道 | 回归期修 4 处测试程序：① SWRL 推导断言（R1/R8/R8b/R21）改**独立推理器**规避共享推理器被监听器同步重入 flush 的竞态；② N-3 补 `owl:differentIndividuals`；③ 实机遇 incident 附明细；④ SWRL 消息 P-1 改**两批注入**。套件 `AllZiWeiDouShuSuiteTest` 33/33 连续多次稳定全绿。**无本体/应用资产改动（第 8 步起固定）。** |
| 2026-10-31 | v1.2（第 13 步·词汇表测试启用） | 码道 | ① §6.5 启用（术语表产出后生效），补注 `skos:exactMatch` 交叉校验方向（OWL 侧携带 → term）；② 词汇表测试类 6.5 纳入 `AllZiWeiDouShuSuiteTest`；③ 回归加固（仅改测试程序）：`ZiWeiDouShuOntologyTests` 自建 manager 补注册 `.ttl` 的 `SimpleIRIMapper`（`AutoIRIMapper` 不处理 `.ttl`），避免 tbox 导入 `term` 失败。**非本体/应用改动（第 8 步起固定）**；回归 `AllZiWeiDouShuSuiteTest` 全绿、SKOS 6/6。 |
| 2026-10-31 | v1.3（第 14 步·词汇表测试与全流程测试） | 码道 | ① 新增 §10「第 14 步·词汇表测试与全流程回归留痕」；② §6.5 P-4 口径升级（追加逐条 `# 源:` 来源注释校验，对齐前例 S-2）；③ 全流程回归 `AllZiWeiDouShuSuiteTest` **39/39 全绿、0 跳过**（集成 13 + 内存 9 + 真实引擎 9 + SWRL 2 + SKOS 6），构建链可复现、`validate_structure.py` ERROR=0、禁区零改动。**仅改测试程序（P-4 加固）；无本体/流程/应用改动（第 8 步起固定）。** 14 步流程构建完成。 |
| 2026-10-31 | v1.4（末尾新增·用户需求追加） | 码道 | **追加节点 18 `Task_Interpret`/`interpretResult`**（命书详解与白话解读·大模型）：同步 §范围（22 serviceTask）、§2.1 链路图、§2.2 契约表、§2.3 机检 22=22、§7.1 覆盖矩阵；真实引擎端到端用例已实跑新节点（大模型未装配时降级空串、流程不阻断、无 incident）。**LLM 生成内容非确定性不作断言**（§不含）。同步：BPMN/Controller/前端/《流程设计》/《应用架构设计》。回归 `AllZiWeiDouShuSuiteTest` 全绿、`validate_structure.py` ERROR=0、禁区零改动。 |
| 2026-11-14 | v2（第 8 步新模板·经典校验闭环） | 码道 | ① §6.6 新增 `ZiWeiDouShuStarTableTest`（72 项离线经典表自检）并**纳入 `AllZiWeiDouShuSuiteTest`**（补第 8 步「所有检验须可自动重放」缺口 I-1）；② §7 覆盖矩阵升级为 7.1 流程节点（增「覆盖分支/经典依据」列）/ 7.2 网关分支覆盖（新，逐网关两侧分支）/ 7.3 本体约束（增经典依据）/ 7.4 边界负向（增经典依据）；③ 新增 §8「经典校验与迭代记录」（8.1 逐用例校验与迭代轨迹 + 8.2 整轮收敛）；④ 修测试程序缺陷 I-2：`ZiWeiDouShuSwrlRabbitMqTest` 环境缺失时由硬报错改为 `ServiceProbe` 优雅跳过（`@BeforeAll` 去 `@Autowired Environment`、初始化移入实例 `@BeforeEach`）；⑤ 章节顺延（§9 执行方式、§10 待确认、§11 第 14 步留痕）。**回归**：离线可跑子集 `AllZiWeiDouShuSuiteTest` **92/92 全绿**（Star 72 + Ont 13 + SKOS 7；环境三类干净跳过），**零改进点收敛**。**无本体/本体应用改动（第 8 步起固定本体与程序；仅改测试程序）。** |
| 2026-11-14 | v2.1（第 8 步·环境恢复全量实跑收敛） | 码道 | 环境恢复后完成第 8 步全量实跑：`ZiWeiDouShuBpmnInMemoryTest` 9/9、`ZiWeiDouShuBpmnRealEngineTest` 9/9、`ZiWeiDouShuSwrlRabbitMqTest` 2/2 三类端到端**实跑全绿 0 跳过**；`AllZiWeiDouShuSuiteTest` **112/112 全绿、0 跳过、BUILD SUCCESS**。① §8 引言拆为「阶段一·离线子集 / 阶段二·全量实跑」两段；② §8.1 增补 INMEM / REAL / SWRL-MQ 三组端到端逐用例经典校验行（命理内核引用已核 StarTable 72 项，命宫格断语出处 c3 L549 经审查报告实测留痕）；③ §8.2 补第 3 轮「全量实跑 112/112 零改进点收敛」。**仅改测试设计文档；无本体/流程/应用改动（第 8 步起固定本体与程序）。** |
| 2026-11-14 | v2.2（第 8 步·代码覆盖率度量与补强） | 码道 | 回应「全面覆盖代码」验收：接入 `jacoco-maven-plugin 0.8.16`（prepare-agent + report，绑定 test，`mvn test` 产出 `target/site/jacoco`）；新增 `ZiWeiDouShuControllerTest`（standalone MockMvc，18 项，覆盖 `/chart`、`/chart/{key}`、`/health` 全部校验/异常分支，静态打桩 `ProcessOrchestrator`）与 `ZiWeiDouShuJobWorkerLogicTest`（25 项，覆盖 JobWorker 纯函数边界/守护分支），二者纳入 `AllZiWeiDouShuSuiteTest`（升级至 8 类）。① 新增 §8.3 代码覆盖率度量（分类覆盖表+口径）；② §8.2 补第 4 轮 155/155；③ §9 更新类数与覆盖率入口。**回归**：`AllZiWeiDouShuSuiteTest` **155/155 全绿、0 跳过、BUILD SUCCESS**；Controller 行/分支/指令/方法均 **100%**，JobWorker 行 92.39%/分支 74.60%，整体行 92.39%/分支 74.76%/指令 94.29%/方法 97.49%（`ZiWeiDouShuApplication` 启动类按惯例排除）。**仅改测试程序与 pom（新增覆盖率插件及两类测试）；无本体/本体应用（Worker/Controller 逻辑）改动（第 8 步起固定本体与程序）。** |
| 2026-11-14 | v2.3（第 8 步·农历换算缺陷修复与全量重评） | 码道 | 确证并修复公历入参未换算农历的系统缺陷：新增 `LunarCalendarConverter`（1900–2099，与 `lunardate 0.3.0` 12/12 一致）、改 `parseBirthInput`（双向换算 + 新增 `LY` 字段 + `LUNAR_APPROX` 恒 false）、删 2 死函数、加 3 农历年辅助函数（`birthLunarYear`/`birthGanOf`/`birthBranchOf`）、替换 18 处出生年调用点（流年/大限保持公历年口径）。S1（1990-05-15）实跑 `chartBase=LY=1990;LM=4;LD=21` 验证生效。全量 9 层 LLM 重评留档（`summary_llm1_*`，260 改进点待逐条甄别、未零收敛；S3 因 Camunda poll `SocketTimeoutException:11000ms` 环境抖动标 INCIDENT，非逻辑）。新增 §8.2b。**改动属本体应用 JobWorker（授权修复确定性换算缺陷）；禁区模块零改动。** |
| 2026-11-17 | v3（第 33 轮·格局死分支修复与全量矩阵测试） | 码道 | ① **修复 `ZiWeiDouShuOntologyJobWorker.determinePattern` 12 处格局判据死分支**（机月同梁/日月藏辉/财与囚仇/杀破狼/紫微七杀化权/财荫夹印/财印夹禄/马头带剑/荫印拱身/金舆扶驾/君子在野等）——原判据存在 `inSanFang` 误传星名、判「同宫」而两星恒不同宫、夹拱位不成立等死分支，修复后按《全書》原文语义可达；凡涉经典释读处代码均标「释读：…」+ 出处。② 新增 §5.4「全量矩阵用例数据」与 §6.7「`ZiWeiDouShuPatternMatrixTest`（46+1 项）」：数据驱动 `matrix_cases.json`（46 例：基线3/五行局5/阴阳男女4/命宫12/空宫/告警3/运限2/女命/小儿/验盘/修复格局11/财禄夹马/众水朝东），含 11 修复格局判据与释读来源表；该类纳入 `AllZiWeiDouShuSuiteTest`（升级至 9 类）。③ **回归**：`ZiWeiDouShuPatternMatrixTest` 实跑 **46/46 + 非法入参 incident 全绿、2 用例 0 失败、BUILD SUCCESS**（连真实 Camunda 9080 + 应用 9089，低负载每例 sleep 150ms）。**改动属本体应用 JobWorker（授权修复格局判定缺陷）；禁区模块零改动。** |
| 2026-11-17 | v4（第 34 轮·推演维度全量覆盖扩展） | 码道 | ① **扩展 `ZiWeiDouShuPatternMatrixTest.assertCase`** 新增 14 个维度断言前缀：`四化=`(fourTransform)/`化气=`(transformationQi)/`形性=`(starXingXing)/`所主=`(starNature)/`三方四正=`(surroundPalaces)/`夹=`(clampRelations)/`借星=`(emptyPalaceResult)/`宫强弱=`(palaceStrength)/`生克=`(phaseRelations)/`命格=`(patternGrade)/`流派=`(schoolNote)/`飞星=`(flyingStars)/`大限=`(majorLimit，含第1~12限)/`十二限=`(yunxianSequence)。② **`matrix_cases.json` 由 46 例扩至 51 例**，新增维度专项 5 例（`dim_base1_full`/`dim_base2_sihua`/`dim_base3_daxian`/`dim_yangnv`/`dim_empty_borrow`），覆盖用户点名全部推演维度：十二宫命盘（命宫12）、格局/四化/强弱、大限盘·十二限、星曜化气与诸星所主、星曜形性、化气表、三方四正、空宫借星结果、星辰生克制化、星盘、命格、四化飞星深层、流派差异标注；断言值均以真实排盘（连 Camunda 9080 + 应用 9089）实跑取值，非臆断。③ §5.4/§6.7 同步（46→51、前缀清单、维度专项明细表+经典依据）。④ **回归**：`ZiWeiDouShuPatternMatrixTest` 实跑 **51/51 + 非法入参 incident 全绿、2 用例 0 失败/0 错误、BUILD SUCCESS**（78.09s，每例 sleep 150ms）。**仅改测试程序与用例数据；无本体/流程/应用改动（禁区零改动）。** |
| 2026-11-17 | v5（第 35 轮·结论化输出改造·文档同步与回归固化） | 码道 | 同步「结论化输出改造（对齐四柱八字范式）」入测试设计：① §2.2 契约表节点 12/16 输出列标注结论化口径（节点 12 `natalReading`＝命格总断＋逐项评吉/凶/平＋应事＋寿元；节点 16 `reading`＝分节报告 markdown 含表格）；② 本轮**未新增/未改动测试断言**（结论化输出为既有 `natalReading`/`reading` 变量内容的编排变化，`expectPattern`/`expectFeature`/`告警=` 前缀所依赖的串仍在），故 §5.4 矩阵用例仍为 51 例、`matrix_cases.json` 不变；③ **回归固化**：`ZiWeiDouShuPatternMatrixTest` 实跑 **51/51 + 非法入参 incident、0 失败/0 错误 BUILD SUCCESS**（连 Camunda 9080 + 应用 9089，每例 sleep 150ms）；全量 `AllZiWeiDouShuSuiteTest` **Tests run 156, Failures 0, Errors 0, Skipped 0, BUILD SUCCESS**。**改动属本体应用 JobWorker（结论编排）；测试程序未改，禁区零改动。** |
| 2026-11-17 | v6（第 36 轮·结论化行文再改造·回归固化） | 码道 | 承 v5 将行文精修为「结论→依据→意味」三段式并去《全書》卷名/原文长句（命格档位、寿元、告警解读、阴骘），同属既有 `natalReading`/`reading` 变量内容编排变化：① `expectPattern`/`expectFeature`/`告警=` 所依赖串仍在，§5.4 矩阵用例仍 **51 例**、`matrix_cases.json` 不变；② `judgeLongevity`/`gradeOf`/`patternGrade` 未改，直接依赖其返回串/值的断言不受影响；③ 回归：`mvn -o test` **Tests run 158 / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS**（含 `ZiWeiDouShuPatternMatrixTest` 全量矩阵、真实引擎端到端、告警 P-5「命中=」解析）。**改动属 JobWorker 行文渲染；测试程序未改，禁区零改动。** |
| 2026-11-17 | v7（第 37 轮·大限断语结论化·回归固化） | 码道 | 承 v6 将节点 13 `analyzeMajorLimit` 之 `majorLimitReading` 行文改为「总纲 → 十年祸福（结论前置·依据后置）→ 竹萝三限（结论+意味）」三段式并剥离卷名/原文长引/〔释读〕标签，同属既有 `majorLimitReading` 变量内容编排变化：① §2.2 契约表节点 13 输出列标注结论化口径；② **未新增/未改动测试断言**——`judgeDaXianShiNian` 判据词「十年安静／成败不一／尚未起限」保留（`juanSanRemainingTest` L1546-1550 直依赖），`isZhuLuoSanXian`/`hasJuMenOrSha` 判据方法不改（`zhuLuoSanXian` 测试不受影响），`expectFeature`/`expectPattern`/`告警=` 所依赖的结构变量与串均不变，§5.4 矩阵用例仍 **51 例**、`matrix_cases.json` 不变；③ 回归：`mvn -o test` **Tests run 158 / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS**（含全量矩阵、真实引擎端到端、告警 P-5）。**改动属 JobWorker 行文渲染；测试程序未改，禁区零改动。** | 码道 |
| 2026-11-17 | v8（第 38 轮·前半部各节点结论化全量收口·回归固化） | 码道 | 承 v7 将报告前半部各节点（8 星曜化气/形性/化气、10 格局/四化飞星/流派、`assessBrightness` 宫强弱、`analyzeSurroundPalaces` 三方四正/生克/组合、14 流年/太岁小限/大小限所忌、15 叠宫/行限宫歌、13 运限格）由「引文堆砌/单纯讲方法」改为「结论→依据→意味」三段式，同属既有变量内容编排变化：① **未新增/未改动测试断言**——矩阵 14 维度 `contains` 断言子串（`紫微@午：帝座，主尊贵`/`紫微化气为尊`/`廉贞化气为囚`/`生年干戊：贪狼化禄→子`/`三合轨`/`男命强宫：…`/`命格=中格`/`无成格之夹` 等）全部保留；`gradeOf` 档位词（上格/中上格/中格/下格/常格）保留，`ziweiGradeTier` 解析不变；`expectPattern` 成格名经 `String.join(hit)` 仍完整输出；`judgeLongevity`/`judgeDaXianShiNian` 等判据与返回串未改；② §5.4 矩阵用例仍 **51 例**、`matrix_cases.json` 不变；③ 回归：`mvn -o test` **Tests run 158 / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS**（含 `ZiWeiDouShuPatternMatrixTest` 全量矩阵、真实引擎端到端、告警 P-5「命中=」解析）。运行期曾遇 Camunda 8.9.6（c8run）broker「No remote message handler...command-api」导致真实引擎/矩阵报错，经重启 Camunda 后恢复，非代码缺陷。**改动属 JobWorker 行文渲染；测试程序未改，禁区零改动。** | 码道 |
| 2026-11-17 | v9（第 39 轮·三方四正表格化·回归固化） | 码道 | 承 v8 将节点 10 `analyzeSurroundPalaces` 的 `surroundPalaces` 在总断与原单行串之后**追加 Markdown 表格**（供前端 `renderMarkdown` 渲染），属既有 `surroundPalaces` 变量内容编排变化：① **未新增/未改动测试断言**——原单行串 `子宫三方四正：子[贪狼]辰[七杀文昌陀罗]申[破军左辅天马]午[紫微右弼擎羊]` 完整保留于表格之前，矩阵 `三方四正=` 维度 `contains` 断言（`ZiWeiDouShuPatternMatrixTest` L178-180）不受影响，`matrix_cases.json` 第 517/550 行期望值无需改动；② §5.4 矩阵用例仍 **51 例**、`matrix_cases.json` 不变；③ 回归：`mvn -o test` **Tests run 158 / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS**（含 `ZiWeiDouShuPatternMatrixTest` 全量矩阵、真实引擎端到端、告警 P-5「命中=」解析）。**改动属 JobWorker 行文渲染；测试程序与用例数据未改，禁区零改动。** | 码道 |
| 2026-11-17 | v10（第 40 轮·岁限叠看补全·回归固化） | 码道 | 承 v9 按《全書》卷三（c4 L76/L78）在节点 16「四、流年吉凶」后新增「五、岁限叠看（大限与流年）」段（`ziweiSuiXianOverlay`/`suiXianVerdict`，逐流年叠断大限基调×流年符号：岁限俱凶→凶；大限凶流年吉→凶中有和缓·基调仍凶宜守；大限吉流年凶→吉中有阻；岁限俱吉→顺遂），属既有 `reading` 变量内容编排新增：① **未新增/未改动测试断言**——新增段仅追加于 `reading`，不触碰 `expectPattern`/`expectFeature`/`告警=`/`大限=`/`十二限=` 所依赖的既有串；② `yunxianFortuneSign`/`annualFortuneSign` 评分算法未改，`annualFortuneSigns`（L794-803，断言流禄+/流羊流陀-/流年化忌-）与 `yunxianFortuneSigns`（L674-695）单测不受影响；§5.4 矩阵用例仍 **51 例**、`matrix_cases.json` 不变；③ 回归：`mvn -o test` **Tests run 158 / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS**（含 `ZiWeiDouShuPatternMatrixTest` 全量矩阵、真实引擎端到端、告警 P-5）。**改动属 JobWorker 行文渲染；测试程序与用例数据未改，禁区零改动。** | 码道 |
| 2026-11-17 | v11（第 41 轮·去引文堆砌·回归固化） | 码道 | 承 v10，按「保留明细子串＋块前加本盘结论」口径改造节点 8 `interpretStarNature`（`starNature`/`starXingXing`/`transformationQi`）与节点 11 `determinePattern`（`patternResult`）：三块于逐星原文前新增**动态本盘结论句**（取命宫首位主星回查 `STAR_CHARACTER`/`STAR_XINGXING` 化气·形性·所主），`patternResult` 新增 `stripParen` 清除成格列表内嵌全角括号卷名/出处/释读括注。回归要点：① 矩阵 `expectFeature` 依赖子串（`化气=紫微化气为尊`/`化气=廉贞化气为囚`/`形性=紫微@午：帝座`/`所主=紫微@午：帝座，主尊贵`）与 `expectPattern`（13 个纯格名前缀：`杀破狼`/`紫微七杀化权`/`荫印拱身`/`财印夹禄`/`金舆扶驾`/`马头带剑` 等）**全部仍 `contains` 命中**——`stripParen` 仅删括号段、格名前缀不变；② `hit` 列表本身不改（参与 `gradeOf`/`judgeMingGeGrade`），仅输出拼装清洗；③ §5.4 矩阵仍 **51 例**、`matrix_cases.json` **不变**（`expectPattern` 均为纯格名、不含括注）；④ 回归：`mvn -o test` **Tests run 158 / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS**；真机 1978男/1976女/2017小儿三块头部结论正确、卷名括注清零。**改动属 JobWorker 行文渲染；测试程序与用例数据未改，禁区零改动。** | 码道 |
| 2026-11-17 | v12（第 42 轮·去引文堆砌第二批·回归固化） | 码道 | 承 v11，按 B 类口径（本盘结论＋关键原文短据·去行号；新增 `shortQuote(s,max)` 剥「（…c3 Lxxx…）」并截断）改造五处过程变量：①`palaceClassic`（节点 8·`palaceStarQuote`）加「印证总断」＋逐宫短据；②`idleVerseNote`（节点 7·`idleVerseOf`）加「落闲总断」＋短断语（保留 `shaCouSatisfied` 煞凑核验结论）；③`taisuiXiaoxian`（节点 14·`TAISUI_XIAOXIAN`）去三段原文直贴→生干择段结论＋短据；④`limitTaboo`（节点 14·`LIMIT_TABOO`）原文整句→结论＋短据；⑤`brightnessVariants`（节点 7·`brightnessVariantNote`）→庙陷异文摘要＋结论。回归要点：① **静态方法/底表全部零改动**——`palaceStarQuote`/`loadPalaceClassic`（`palaceClassicLanding`/`palaceClassicCoverageTest` 依赖：`紫微土`/`c3 L459`/`813`/各宫计数）、`idleVerseOf`（`idleVerseCrossCheck`：`天机化忌落闲宫`/L494/L712/L733）、`TAISUI_XIAOXIAN`（`taisuiXiaoxianTable`）、`LIMIT_TABOO`（`limitTabooTable`）、`taisuiXiaoxianGanHit`（`taisuiXiaoxianGanHitJudge`：`入庙化吉`/`不入庙化凶`/`两段皆见`/`两段未列`）、`brightnessVariantNote`（`brightnessVariants`：`天机@辰`含`利益`+`旺`）**均不受影响**；② `brightnessOf`（`brightnessTable` 单测 紫微@午=庙 等）不变；③ 字段名 `palaceClassic`/`idleVerseNote` 及 `ZiWeiDouShuBpmnRealEngineTest` 之 `containsKey` 断言保留；④ §5.4 矩阵仍 **51 例**、`matrix_cases.json` **不变**；⑤ 回归：`mvn -o test` **Tests run 158 / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS**；真机 1978男/1976女 四变量均结论头＋去行号短据、异文摘要正确（两例本盘无相左）。**改动属 JobWorker 行文渲染；测试程序与用例数据未改，禁区零改动。** | 码道 |
| 2026-11-17 | v13（第 43 轮·「检察员」改用 LLM 做经典符合性评审并据原文修正·回归固化） | 码道 | 以 run configuration 之 LLM（DeepSeek-V4.1-Flash）驱动「检察员」做经典符合性评审（harness `HWCodeArtsDir/zwds_review.py` 原文锚定版，6 例：base1/base2/base3/empty/yang_nv/yin_nan_dx），逐条回《全書》原文裁决后落实 4 处改动，并检验对既有测试契约的影响：①`gongFallenWithSha` 落陷口径收紧（`isMainStar && "陷".equals(brightnessOf)`，不再把「不得地」并入「陷」）——**`judgeLongevity` 判据与返回串零改动**，`juanSanRemainingTest`（`十年安静`/`成败不一`/`尚未起限`）与 `can`/`gu` 星盘用例不受影响（严化后 `gu` 巨门@戌=陷仍命中、`base1` 天同@丑=不得地不再误判孤克），`brightnessTable` 单测（紫微@午=庙 等）不变；②节点 16 `reading` 章节编号 六/七 补正——`extractAlertTypeFromReading`（L3325，按「命中=」解析）不依赖章节号，`告警=` 断言不受影响；③`judgeTenGrades` 文职措辞改「三方会照」——`judgeTenGrades` 测试（L241-255）**不断言「文职」串**，矩阵 `命格=` 等断言不受影响；④**逐宫评级量化重构按「不编造」原则未做**——`yunxianFortuneSign`（L4816）/`ziweiPalaceVerdictLine`（L7081）之多数决口径无原典精确量化依据，`yunxianFortuneSigns`（L653-695）/`annualFortuneSigns` 单测保持原样。回归：编译 EXIT 0；重启 9089 实测章节一~七连续、base1 寿元「贫贱/盗贼/一身驳杂」、文职「三方会照」；`mvn -o test` **Tests run 158 / Failures 0 / Errors 0 / Skipped 0 / BUILD SUCCESS**（含 `ZiWeiDouShuPatternMatrixTest` 矩阵 **51/51**、真实引擎端到端、告警 P-5）；§5.4 矩阵仍 **51 例**、`matrix_cases.json` 不变。详见 `design/ZiWeiDouShu应用-经典符合性评审报告(LLM).md`。**改动属 JobWorker 判据/行文；测试程序与用例数据未改，禁区（OpenlletResolver/OntopOBDAHandler/BPMN ioMapping/EnvPrepare）零改动。** | 码道 |
| 2026-11-17 | v14（第 44 轮·节点 18 命书详解/白话解读格式收紧＋max_tokens 防截断·回归固化） | 码道 | 修复末节点 18 `interpretResult`「AI 命书排版冗杂、末尾截断」：①`ChatAgent` 新增可选 `spring.ai.openai.chat.options.max-tokens`（未配置＝不下发、行为不变），本应用设 16384；②两 system prompt＋两 user prompt 尾部收紧为「粗体小标题分板块、禁多级标题、白话直接正文、字数受控」；③前端 `.llm-section` 阅读样式。**对测试契约影响**：全部改动为**大模型提示词文案／可选 LLM 输出上限／前端渲染样式**，不触碰任何判定逻辑、结构化输出变量与既有断言——LLM 生成内容本属非确定性、不作断言（延续 v1.4 口径），矩阵 `contains` 前缀、`judgeAlertTypes`/`extractAlertType`/`palaceClassic`·`idleVerseNote` `containsKey` 等硬断言全部不受影响；§5.4 矩阵仍 **51 例**、`matrix_cases.json` 不变。**回归**：`mvn -o -q compile` EXIT 0；非真实引擎用例全绿（内存引擎 9/Controller 18/JobWorker 25/本体 13/SKOS 7/内置表 72/SWRL 2）；完整 `mvn -o test` 158 中仅 3 例真实引擎（`ZiWeiDouShuBpmnRealEngineTest`×2、`ZiWeiDouShuPatternMatrixTest`×1）因 Camunda(26500) 未启动超时，属环境非代码回归（用户已停全部进程），待启动 Camunda/Ontop/MySQL/RabbitMQ 后复跑收敛。**改动含通用库 ChatAgent 向后兼容增强＋JobWorker 提示词/前端样式；测试程序与用例数据未改，禁区零改动。** | 码道 |
| 2026-11-17 | v15（第 45 轮·节点 18「命书详解」默认折叠·前端展示优化） | 码道 | 前端 `renderLlmSection` 将「命书详解」改为默认收起的 `<details>` 折叠面板、白话解读为主体；纯 `index.html` 渲染结构调整，**不触碰**判定逻辑/结构化输出/用例数据/任何硬断言，`matrix_cases.json` 与 §5.4 51 例不变；无需重编译、测试契约零影响，展示效果由用户启动 9089 目视复核。 | 码道 |
