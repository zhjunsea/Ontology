# ZiWeiDouShu 应用 — 测试设计文档

> **用法**：本模板产出第 8 步。**闸门⑤**：业务人员/负责人确认本文档后方可固化测试用例。
> **基线**：`OntologyFrameworkPizza/design/披萨应用（Pizza）测试用例设计文档.md`（形态）；用例基线 `OntologyFrameworkExample/src/test/java/.../{ExampleBpmnRealEngineTest, ExampleSwrlRabbitMqTest, AllExampleSuiteTest}.java`。
> **上游**：第 6 步单文件本体；第 9 步将 TBox/ABox 分离为两个 owl；第 14 步执行词汇表与全流程测试。
> **阶段说明**：本文档为**设计先行**——用例编号、方法名、输入、断言为**冻结预案**（据第 2–7 步定稿工件推导，未臆造）；闸门⑤确认后据此在 `src/test/java/.../ziweidoushu/**` 固化测试类，**最终以测试源码为准**。

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkZiWeiDouShu / ZiWeiDouShu（紫微斗数排盘与批命本体应用） |
| 文档版本 | v1（初稿·待闸门⑤） |
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
| 12 | 本命综合断 | `Task_Natal` | `generateNatalReading` | `chartSkeleton, starNature, patternResult, surroundPalaces` | `natalReading` |
| W | 女命专论 | `Task_FemaleReading` | `analyzeFemaleReading` | `chartSkeleton, patternResult, natalReading` | `femaleReading` |
| C | 小儿命专论 | `Task_ChildReading` | `analyzeChildReading` | `chartSkeleton, patternResult, natalReading` | `childReading` |
| 13 | 大限推演 | `Task_MajorLimit` | `analyzeMajorLimit` | `chartSkeleton, subjectType, taiSuiBase` | `majorLimit, majorLimitReading` |
| 14 | 流年推演 | `Task_AnnualFortune` | `analyzeAnnualFortune` | `chartSkeleton, majorLimit, taiSuiBase, granularity` | `annualChart, annualReading, annualRecomputedStars` |
| 15 | 叠宫断法 | `Task_Overlay` | `analyzePalaceOverlay` | `chartSkeleton, majorLimit, annualChart` | `overlayReading, evilPatternTrigger, limitPalaceSong` |
| 16 | 综合汇总 | `Task_Aggregate` | `aggregateReading` | `chartSkeleton, natalReading, femaleReading, childReading, majorLimitReading, annualReading, overlayReading` | `reading, structuredOutput, alertTriggered` |
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

`ZiWeiDouShuApplication`、`ZiWeiDouShuOntologyJobWorker`（本模块 `com.ocean.ontologyframework.ziweidoushu` 包）、`web/ZiWeiDouShuController`（`POST /api/ziweidoushu/chart`、`GET /api/ziweidoushu/chart/{key}`、`GET /api/ziweidoushu/health`）；共享工具 `com.ocean.utilities.{OntologyWorkerSupport, OntologyLabelMatcher, RabbitMqHandler, ProcessOrchestrator, ServiceProbe, StopOnTimeoutExtension}`（来自 `com.ocean:Utilities`）、`com.ocean.ontopobdahandler.OBDAHandler`、`com.ocean.openlletresolver.*`；测试侧：`AllZiWeiDouShuSuiteTest`、`ZiWeiDouShuOntologyTests`、`ZiWeiDouShuBpmnInMemoryTest`、`ZiWeiDouShuBpmnRealEngineTest`、`ZiWeiDouShuSwrlRabbitMqTest`、`validation/ZiWeiDouShuSkosTerminologyTest`、`validation/ZiWeiDouShuOntologyValidator`、`validation/ValidationResult`。

---

## 3. 测试分层与策略

| 层级 | 测试类 | 依赖引擎 | 是否需环境（Camunda/MySQL/Ontop/RabbitMQ） | 关注点 |
|---|---|---|---|---|
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
| B-1 | `calendar="农历"`（如 `birth="庚午年四月廿一"`） | 农历→公历表未就绪（第 11 步 OBDA 落地前） | 不失败；`chartBase` 含 `LUNAR_UNCONVERTED=true`，按原值登记并标【待考】 |
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
| B-2 | `lunarInputMarkedPending` | B-1（农历） | 完成；`chartBase` 含 `LUNAR_UNCONVERTED=true` |
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

---

## 7. 覆盖矩阵

### 7.1 流程节点覆盖

> 每个 BPMN 节点（21 serviceTask + 5 排他网关 + 4 汇合点 + 起止）均有对应用例。

| BPMN 节点 id | 覆盖用例 | 说明 |
|---|---|---|
| `Start_01` | 6.2 P-1~P-6、6.3 P-1~P-5 | 各场景启动 |
| `Task_ParseInput` | 6.2 P-1/B-1/N-1、6.3 P-1/B-2/B-3/N-1、6.1 P-1 | 历法/真太阳时/昼夜/缺省/非法 |
| `Task_LifePalaces` | 6.2 P-1、6.1 P-6/P-7 | 命身宫/五行局/命主身主 |
| `Task_MainStars` | 6.2 P-1、6.1 P-2 | 十四主星布列 |
| `Task_AuxStars` | 6.2 P-1 | 辅煞星 |
| `Task_MiscStars` | 6.2 P-1 | 杂曜与太岁基准 |
| `Task_FourTransform` | 6.2 P-1、6.1 P-8 | 四化/宫干/十二长生 |
| `Task_VerifyChart` | 6.2 B-2/B-8、6.3 P-4 | 验盘、闭环回流、轮次上限 |
| `Gateway_Mode` (G1) | 6.2 P-1/B-2 | 直接排盘 / 验盘 |
| `Gateway_ChartVerdict` (G1b) | 6.2 B-2、6.3 P-4 | 需校正/无需校正 |
| `Gateway_MergeJ` (J1) | 6.2 P-1 | 汇合→节点7 |
| `Task_Brightness` | 6.2 P-1 | 庙陷/生克/宫强弱 |
| `Task_StarNature` | 6.2 P-1 | 化气/所主 |
| `Task_EmptyPalace` | 6.2 P-1 | 空宫借星 |
| `Task_Surround` | 6.2 P-1 | 三方四正/夹宫/组合 |
| `Task_Pattern` | 6.2 P-1、6.1 P-9 | 成格/命格高低 |
| `Task_Natal` | 6.2 P-1 | 本命综合断 |
| `Gateway_Subject` (G2) | 6.2 P-1/P-2/P-3、B-9 | 成人女/小儿/成人男 |
| `Task_FemaleReading` | 6.2 P-2、6.3 P-2 | 女命专论 |
| `Task_ChildReading` | 6.2 P-3、6.3 P-3 | 小儿命专论 |
| `Gateway_SubjectMerge` (J2) | 6.2 P-2/P-3 | 汇合→G3 |
| `Gateway_Depth` (G3) | 6.2 P-1/P-4/P-5、B-10 | 本命/大限/全链 |
| `Task_MajorLimit` | 6.2 P-4/P-5、6.3 P-3、6.1 P-4 | 起限顺逆/十年祸福 |
| `Task_AnnualFortune` | 6.2 P-5、6.3 P-2 | 流年重算流曜 |
| `Task_Overlay` | 6.2 P-5、6.3 P-2 | 叠宫/凶格引动 |
| `Gateway_DepthMerge` (J3) | 6.2 P-5 | 汇合→节点16 |
| `Task_Aggregate` | 6.2 P-1/P-6 | 汇总/写 alertTriggered |
| `Gateway_Level` (G4) | 6.2 P-6、6.3 P-5 | true→A / false→J4 |
| `Task_SendAlert` | 6.2 P-6、6.3 P-5、6.4 P-1 | 外发告警 |
| `Gateway_LevelMerge` (J4) | 6.2 P-1 | 汇合→节点18 |
| `Task_Interpret` | 6.2 P-1、6.3 P-1 | 命书详解/白话解读（大模型；不可用降级空串不阻断） |
| `Task_PersistReturn` | 6.2 P-1、6.1 N-1 | 落库返回 |
| `End_01` | 所有 `COMPLETED` 用例 | 正常结束 |

### 7.2 本体约束覆盖

> 每条 `owl:Restriction`（核心设计 §4）与每条 SWRL 规则（核心设计 §5，R1~R25）均有对应用例。

| 约束 / SWRL | 覆盖用例 |
|---|---|
| `NatalChart hasBirthInput cardinality 1` | 6.2 P-1、6.1 N-1 |
| `NatalChart hasPalace cardinality 12` | 6.1 P-6/N-1 |
| `NatalChart hasLifePalace / hasBodyPalace / hasBureau cardinality 1` | 6.1 P-6/N-2 |
| `NatalChart hasLifeMaster / hasBodyMaster cardinality 1` | 6.1 P-6 |
| `chart:Palace hasBranch / hasPalaceStem cardinality 1` | 6.1 P-7 |
| `core:HeavenlyStem hasYinYang / hasFivePhase cardinality 1` | 6.1 P-3/P-7 |
| `core:EarthlyBranch hasFivePhase cardinality 1` | 6.1 P-3 |
| `core:SexagenaryCycle hasStem/hasBranch/hasNaYin cardinality 1` | 6.1 P-7 |
| `core:FivePhaseBureau hasTwelveStage cardinality 12` | 6.1 P-7 |
| `star:Star starName cardinality 1` | 6.1 P-7 |
| `FourteenMainStar hasStarCharacter cardinality 1` | 6.1 P-7 |
| `layout:StarPlacement placedIn cardinality 1` | 6.1 P-7 |
| `layout:StarPlacement hasBrightness maxCardinality 1` | 6.1 P-7 |
| `layout:Transformation transformationType/transformsTo cardinality 1` | 6.1 P-8 |
| `chart:Palace oppositeOf / hasSanFang cardinality 1` | 6.1 P-7 |
| `pattern:StarPattern patternName cardinality 1` | 6.1 P-7 |
| `pattern:SpecialStructure structureType cardinality 1` | 6.1 P-7 |
| `pattern:PalaceStarQualification qualificationType cardinality 1` | 6.1 P-7 |
| `pattern:PatternGrade patternGradeName cardinality 1` | 6.1 P-7 |
| `NatalChart hasPatternGrade maxCardinality 1` | 6.1 P-9 |
| `fortune:GreatLimit limitDirection cardinality 1` | 6.1 P-4/N-4 |
| `fortune:GreatLimit limitRange cardinality 1` | 6.1 P-7 |
| `fortune:AnnualFortune annualGanzhi cardinality 1` | 6.1 P-7 |
| `fortune:LimitTaboo tabooBranch cardinality 1` | 6.1 P-7 |
| `NatalChart hasGreatLimit maxCardinality 12` | 6.1 P-7、6.2 P-4 |
| `NatalChart hasConclusion / hasStructuredOutput cardinality 1` | 6.2 P-1 |
| `NatalChart hasChartVerification maxCardinality 1` | 6.2 B-2 |
| `rules:AlertFlag alertType cardinality 1` | 6.1 P-5、6.4 P-1 |
| `reading:CaseRecord caseBirthData cardinality 1` | 6.2 P-1 |
| `diagnosis:SourceNote sourceBook/sourceVolume cardinality 1` | 6.1 P-7 |
| **R1** 干支→阴阳/五行 | 6.1 P-3 |
| **R2/R3** 五行相生/相克传递 | 6.1 P-1（本体一致性） |
| **R4/R5** 安星落宫 / 五行局→紫微起宫 | 6.2 P-1、6.1 P-2 |
| **R6** 庙陷判定 | 6.2 P-1 |
| **R7** 四化落宫（生年干） | 6.1 P-8 |
| **R8/R8b** 大限起限顺逆 | 6.1 P-4、6.2 P-4、N-4 |
| **R9** 三方四正取宫 | 6.2 P-1 |
| **R10** 夹宫判定 | 6.2 P-1 |
| **R11** 空宫借星 | 6.2 P-1 |
| **R12** 成格判定·分类 | 6.2 P-1 |
| **R13** 命格高低·等第 | 6.1 P-9 |
| **R14** 十二长生顺逆 | 6.2 P-1、6.1 P-7 |
| **R15** 流年流曜重算 | 6.2 P-5、6.3 P-2 |
| **R16** 叠宫断法·三盘叠加 | 6.2 P-5 |
| **R17** 凶格引动·羊陀迭并/七杀重逢 | 6.2 P-5 |
| **R18/R19/R20** 告警·大凶/寿元关/重大疾病 | 6.1 P-5、6.4 P-1/P-2 |
| **R21** 告警命中即外发 | 6.1 P-5、6.4 P-1 |
| **R22** 出处绑定 | 6.1 P-7 |
| **R23** 定富局/定贵局成格 | 6.1 P-7、6.2 P-1 |
| **R24** 大小限过十二支所忌 | 6.2 P-5、6.1 P-7 |
| **R25** 行限到宫入庙/不入庙 | 6.2 P-5、6.1 P-7 |

### 7.3 边界场景清单

| # | 边界/负向场景 | 用例 | 预期行为 |
|---|---|---|---|
| 1 | 农历输入（表未就绪） | 6.3 B-2 | 不失败；标 `LUNAR_UNCONVERTED`【待考】 |
| 2 | 缺经度（回退平太阳时） | 6.2 B-1、6.3 B-1 | `tst=0.0h` |
| 3 | 西经（负经度） | 6.3 B-3 | `TST` 为负 |
| 4 | 时辰交界（卯/申） | 6.2 P-1 | 昼夜边界正确翻转 |
| 5 | 类别/深度/粒度缺省 | 6.2 B-1、6.3 B-1 | 默认 成人男/本命/年 |
| 6 | 非法/缺失生辰 | 6.2 N-1、6.3 N-1 | incident（`PARSE_BIRTH_INPUT_FAILED`） |
| 7 | 验盘闭环轮次上限 | 6.2 B-8 | 标「需人工复核」终止 |
| 8 | 网关互斥完备（成人男/本命） | 6.2 B-9/B-10 | 不经 W/C/13/14/15 |
| 9 | 缺宫/缺命宫命例 | 6.1 N-1/N-2 | 合规校验报错 |
| 10 | 顺逆冲突断言 | 6.1 N-4 | 一致性失败 |
| 11 | 无告警命盘 | 6.4 N-1 | 不投递消息 |

---

## 8. 执行方式

- 全套入口：`AllZiWeiDouShuSuiteTest`（`@Suite` + `@SuiteDisplayName` + `@SelectClasses`，聚合 6.1~6.4 四个类 + 词汇表测试类 6.5——第 13 步 `terminology.ttl` 就绪后已纳入）。
- IDEA：右键 `AllZiWeiDouShuSuiteTest` → Run；命令行：`mvn -pl OntologyFrameworkZiWeiDouShu test`（或 reactor 全量 `mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkZiWeiDouShu -am test`）。
- 环境依赖项显式标注：集成类需 Ontop(8080)+MySQL(3306)；端到端类需 Zeebe(26500)；SWRL 消息类需 RabbitMQ；不可达时按 `ServiceProbe` 自动跳过并记日志。
- **回归纪律**：第 8~13 步每步完成后跑 `AllZiWeiDouShuSuiteTest`，全绿方可进入下一步。

---

## 9. 待确认项

| # | 问题 | 结论 |
|---|---|---|
| 1 | 测试 Profile 命名（`ZiWeiDouShuJunitTest` / `ZiWeiDouShuBPMN`）是否认可？ | 待闸门⑤确认 |
| 2 | 输入场景 S1~S8 的构造值是否需按业务指定实际命例？ | 待闸门⑤确认 |
| 3 | 告警分支（G4a）用「预置告警探针个体」保证可达是否认可？ | 待闸门⑤确认 |
| 4 | 词汇表测试类是否本步先占位、第 13 步启用？ | **已定（闸门⑤）**：第 8 步先占位（未产出术语表时经 `assumeTrue` 跳过），第 13 步产出 `terminology.ttl` 后启用并纳入套件 |

---

## 10. 第 14 步·词汇表测试与全流程回归留痕

**执行日期**：2026-10-31　**执行人**：码道　**确认人**：陈希夷（领域专家）

### 10.1 词汇表测试（`validation/ZiWeiDouShuSkosTerminologyTest`，纯 Jena 离线）

- 术语表 `ontology/ziweidoushu-terminology.ttl`（374 概念）就绪，本类**自动启用**（第 13 步产出后不再跳过）。
- 用例 **6/6 全绿**：P-1 结构有效 / P-2 概念映射本体内 IRI / P-3 规范名取自本体 / **P-4 别名标注来源（含「# 源:」可追溯加固）** / N-1 无孤立概念 / N-2 同义归一（一名一概念，含 `prefLabel` 跨语言唯一）。
- **第 14 步加固（仅改测试程序，非本体/应用）**：`P-4 altLabelHasSource` 在原有「字面量 + 语言标签」基础上，**追加逐条 `skos:altLabel` 行须带「# 源:」来源注释**的行级校验（对齐前例 `SiZhuBaZiSkosTerminologyTest.S-2`），使「同义词/别名均可追溯来源」由弱代理（语言标签）升级为**直接校验来源注释**。

### 10.2 全流程回归（`AllZiWeiDouShuSuiteTest`，含第 12 步全集数据）

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

### 10.3 结论

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
