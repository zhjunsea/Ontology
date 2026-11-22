# PlumBlossom应用 — 测试设计文档

> **用法**：本文件产出第 8 步。**闸门⑤**：业务人员/负责人确认本文档后方可固化测试用例。
> **基线**：`OntologyFrameworkPizza/design/披萨应用（Pizza）测试用例设计文档.md`（形态）；用例基线 `OntologyFrameworkExample/src/test/java/.../{ExampleBpmnRealEngineTest, ExampleSwrlRabbitMqTest, AllExampleSuiteTest}.java`。
> **上游**：第 6 步单文件本体 `ontology/plumblossom-all.owl`；第 9 步将 TBox/ABox 分离为两个 owl；第 13 步产出词汇表 `ontology/plumblossom-terminology.ttl`；第 14 步执行词汇表与全流程测试。

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkPlumBlossom / PlumBlossom（梅花易数占断应用） |
| 文档版本 | v2.2（闸门⑤已确认；**v2.2 真实环境补覆盖——3 个环境类实跑 18 项全绿（SWRL/RabbitMQ 3 + 内存引擎 9 + 真实引擎 6）、DomainLoad 集成 8/8 装载成功；JobWorker 指令 94.1%、分支 76.5%、行 93.6%、方法 99.6%（277/278，仅余 `fail` 错误回调）**；此前 v2.1 记录：**分支矩阵累计 223 项（算理/文本纯函数 174 + JobWorker handler 层 32 + Controller web 层 17）**；第十批反射补齐 JobWorker 纯工具/纯计算方法全档分支——`parseIntOrNull`/`iriLocal`/`toneName`/`putIfInt`/`afterBackendServiceReady` 由 0→全覆，`shapeToTrigram`/`colorToTrigram`/`soundToTrigram`/`stillObjectDirectVerdict`/`firstNumber`/`cnDigit`/`parseChineseNumber`/`tenResponseCategory`/`shengKeName`/`shengKeLabel`/`parseShengKeLevel`/`scoreToLevel`/`fivePhaseOfColor`/`generates`/`overcomes`/`seasonFive`/`vitalityOf`/`generatorOf`/`overcomerOf`/`mediatorBetween`/`five*Of`/`remedyLine`/`xiangToNumber`/`dirToTrigram`/`dirFromTextOrDefault` 全档；离线回归 **337 全绿（338 跑 / 1 跳过）**；JobWorker 指令 **86.7%**、分支 **66.1%**、行 **86.2%**、方法 **96.8%**；Controller 分支 **100%**、指令 **95.6%**） |
| 生成日期 | 2026-10-20 |
| 关联本体 | `ontology/plumblossom-all.owl`（TBox+典型 ABox，第 6 步；第 9 步后 imports 各 TBox/ABox）；术语表 `ontology/plumblossom-terminology.ttl`（第 13 步） |
| 关联流程 | 《PlumBlossom应用-流程设计》（v11）；`ontology/bpmn/plumBlossomProcess.bpmn`（`PlumBlossomDivinationProcess`），Camunda 8 / Zeebe |
| 测试代码位置 | `src/test/java/com/ocean/ontologyframework/plumblossom/`（+ `validation` 子包） |
| 测试资源位置 | `src/test/resources/`（暂无；合规用例直接读本体） |

> 本文档所有方法名、输入、断言**逐条取自测试源码**，未作发明。

---

## 1. 目的与范围

**目的**：明确 PlumBlossom 应用的质量保证方式——测什么、怎么测、用什么数据、判定依据、如何执行。

**范围**：
- 覆盖 BPMN 流程端到端编排（主干 19 个 `serviceTask` 顺序、3 个排他网关分支、无 incident、`End_01` 收敛）；
- 覆盖起卦链路的**结构运算**（起卦法路由 → 字占细分 / 起卦成卦 → 定体用 → 互卦 / 变卦 → 五行生克 → 卦气旺衰）；
- 覆盖本体域知识取用（六十四卦名录 `yijing:Seq_*` 互校、`core:*Trigram` 五行/方位、`div:Precept_*` 四不占、`jud:Auspiciousness` 吉凶等级）；
- 覆盖 RabbitMQ 告警外发通道（大吉/大凶 → `plumblossom.alert.exchange`）；
- 覆盖 OWL 本体合规校验（可解析、一致性、Restriction/SWRL 结构）；
- 覆盖 SKOS 术语表结构/交叉校验（依赖第 13 步产出，未产出前整体跳过）。

**不含**：OBDA/数据库读写（第 11 步建库后另行纳入）、TMSD/TCM/NEInsight 等相邻模块测试（同工程内独立维护）。

---

## 2. 被测系统概述

### 2.1 业务链路

**占断流程**（`PlumBlossomDivinationProcess`，纯 `serviceTask`，无 `userTask`）：

```
Start_01(置 passPrecept=false / isCharacter=false / auspiciousness="平")
  └─ Task_PreceptCheck(四不占校验) → Gateway_Precept
        ├─ [passPrecept=true]  → Task_Route(识别17类起卦法) → Gateway_Char
        │     ├─ [isCharacter=true]  → Task_CharSplit(字占细分) ─┐
        │     └─ [isCharacter=false] ───────────────────────────┴→ Task_Cast(起卦成卦)
        │         → Task_BodyUse → Task_Mutual → Task_Changed → Task_ShengKe → Task_Vitality
        │         → Task_ImageSymbol → Task_Categorize → Task_Omen → Task_Judge
        │         → Task_Response → Task_Advice → Gateway_Level
        │               ├─ [大吉|大凶] → Task_SendAlert ─┐
        │               └─ [吉|平|凶]  ─────────────────┴→ Task_Summary → Task_Interpret → End_01
        └─ [passPrecept=false] → Task_PreceptAlert → End_01
```

### 2.2 BPMN 元素 ID 与 jobType

| 环节 | 元素 ID | jobType |
|---|---|---|
| 心法戒律校验 | `Task_PreceptCheck` | `preceptCheck` |
| 返回戒律告警 | `Task_PreceptAlert` | `preceptAlert` |
| 输入解析与起卦法路由 | `Task_Route` | `routeDivination` |
| 字占细分 | `Task_CharSplit` | `characterSplit` |
| 起卦成卦 | `Task_Cast` | `castHexagram` |
| 定体用 | `Task_BodyUse` | `determineBodyUse` |
| 察互卦 | `Task_Mutual` | `deriveMutualHexagram` |
| 观变卦 | `Task_Changed` | `deriveChangedHexagram` |
| 五行生克分析 | `Task_ShengKe` | `analyzeShengKe` |
| 卦气旺衰分析 | `Task_Vitality` | `analyzeVitality` |
| 万物类象取象 | `Task_ImageSymbol` | `extractImageSymbol` |
| 分类占断 | `Task_Categorize` | `categorizeDivination` |
| 参外应 | `Task_Omen` | `analyzeOmen` |
| 断吉凶·以理统数 | `Task_Judge` | `judgeAuspiciousness` |
| 定应期 | `Task_Response` | `determineResponsePeriod` |
| 修身建议生成＋破解方案 | `Task_Advice` | `generateAdvice` |
| 外发告警消息 | `Task_SendAlert` | `sendAlert` |
| 汇总占断结果 | `Task_Summary` | `aggregateResult` |
| 邵康节解读占断结论 | `Task_Interpret` | `interpretResult` |
| 排他网关（3） | `Gateway_Precept` / `Gateway_Char` / `Gateway_Level` | — |
| 开始 / 结束 | `Start_01` / `End_01` | — |

**网关条件（取自 BPMN `conditionExpression`）**：
- `Gateway_Precept`：`passPrecept = true` → `Task_Route`；`passPrecept = false` → `Task_PreceptAlert`。
- `Gateway_Char`：`isCharacter = true` → `Task_CharSplit`；`isCharacter = false` → `Task_Cast`。
- `Gateway_Level`：`auspiciousness = "大吉" or "大凶"` → `Task_SendAlert`；`auspiciousness = "吉" or "平" or "凶"` → `Task_Summary`。

### 2.3 BPMN ↔ JobWorker 绑定

`PlumBlossomOntologyJobWorker`（`@Profile("PlumBlossomBPMN")`）声明 **20 个 `@JobWorker`**（均 `autoComplete=false`）：`preceptCheck`、`preceptAlert`、`routeDivination`、`characterSplit`、`castHexagram`、`determineBodyUse`、`deriveMutualHexagram`、`deriveChangedHexagram`、`analyzeShengKe`、`analyzeVitality`、`extractImageSymbol`、`categorizeDivination`、`analyzeOmen`、`judgeAuspiciousness`、`determineResponsePeriod`、`generateAdvice`、`sendAlert`、`aggregateResult`、`interpretResult`、`fetchDictum`。**BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」**，四者靠 jobType 字面量绑定，无代码级引用。其中 `interpretResult` 依赖 `ChatAgent`（未配置 `spring.ai.openai.*` 时不可用），先产出古典式 `aiInterpretation`、再产出白话式 `aiInterpretationPlain`；`fetchDictum` 依赖 OBDA（库/端点不可用时降级）；二者不可用/失败时相关变量均置 `""` 降级、不阻断流程，故离线端到端用例可正常收敛。

### 2.4 关键类

- 生产侧：`PlumBlossomApplication`、`PlumBlossomOntologyJobWorker`（`com.ocean.ontologyframework.plumblossom`）、`web/PlumBlossomController`。
- 共享工具（来自 `com.ocean:Utilities`）：`ProcessOrchestrator`、`ServiceProbe`、`RabbitMqHandler`、`OntologyWorkerSupport`。
- 测试侧：`AllPlumBlossomSuiteTest`、`PlumBlossomBpmnInMemoryTest`、`PlumBlossomBpmnRealEngineTest`、`PlumBlossomSwrlRabbitMqTest`、`validation/PlumBlossomOntologyComplianceTest`、`validation/PlumBlossomSkosTerminologyTest`。

---

## 3. 测试分层与策略

| 层级 | 测试类 | 依赖引擎 | 是否需环境 | 关注点 |
|---|---|---|---|---|
| 本体合规（纯 OWL API + Openllet） | `validation/PlumBlossomOntologyComplianceTest`（6 项） | 无 | **否** | TBox 一致性、域知识完整性、Restriction/SWRL 结构 |
| 本体术语（纯 Jena） | `validation/PlumBlossomSkosTerminologyTest`（6 项） | 无 | **否** | SKOS 结构 + 与 OWL 本体双向交叉校验 + 同义词（第 13 步词汇表已产出，**已生效**；未产出时跳过） |
| 集成（消息） | `PlumBlossomSwrlRabbitMqTest`（3 项） | 无（Spring 上下文） | **是**（RabbitMQ） | 告警 Exchange/Queue/Binding 端到端投递与报文形状 |
| 端到端（内存引擎） | `PlumBlossomBpmnInMemoryTest`（9 项） | `zeebe-process-test` 内存引擎 | **是**（需 RabbitMQ 供 `sendAlert`） | 20 节点编排、网关分支、节点顺序、无 incident |
| 端到端（真实引擎） | `PlumBlossomBpmnRealEngineTest`（6 项） | 真实 Zeebe `localhost:26500` | **是** | 真实部署与生产 worker 消费、**取值级**断言 |
| 算理分支矩阵·一（纯反射） | `validation/PlumBlossomBranchMatrixTest`（27 项） | 无 | **否** | 起卦模数/八卦爻位/重卦合成/五行生克旺衰/体用 5 级/吉凶回映射/应期/破解/五行属性，逐分支断言 |
| 算理分支矩阵·二（纯反射） | `validation/PlumBlossomBranchMatrixBatch2Test`（31 项） | 无 | **否** | 十应分类与取象/天时占诸卦众寡/起卦法（encodeStartNumbers 多起例）/分类断语吉凶与分项细则 |
| 算理分支矩阵·三（纯反射） | `validation/PlumBlossomBranchMatrixBatch3Test`（18 项） | 无 | **否** | 十应各类之应（tenCategoryNote）/应体生克语气（shengKeTone）/向背坐端·论事十大应·观物洞玄·万物赋·三要灵应诸兆/字占取数四档·取数来源·分句·为人占子法/四不占告示 |
| 算理分支矩阵·四（纯反射） | `validation/PlumBlossomBranchMatrixBatch4Test`（15 项） | 无 | **否** | 日应·鸟兽时日·加姓·写字之应·真生真克·疾病测鬼神与逐爻·饮食·占卦诀·衰旺·动静·卦应/内外动静·一撮金爻诀·体用互变诸断法纯函数/表查 + 卷之一~五照录 getter 非空抽样 |
| 算理分支矩阵·五（纯反射） | `validation/PlumBlossomBranchMatrixBatch5Test`（13 项） | 无 | **否** | 卷之一~五「照录备考」零参 note getter 全量前缀（卷一 5/卷二 11/卷三 21/卷四 8/卷五 14）+ 三要序·导论·总论/十应应期总纲/长文本（屋宅占·器物占·变卦式）/objectDaikaoText 分派/解读规则串/小整数中文化 chineseNumber |
| JobWorker handler 分支矩阵（mock Camunda） | `validation/PlumBlossomBranchMatrixBatch6Test`（32 项） | 无（Mockito mock `ActivatedJob`/`JobClient`） | **否** | 20 个 `@JobWorker` 节点级分支：四不占校验/告示、起卦法路由、字占拆数、起卦成卦（字/数两路）、定体用动爻上下、互卦（乾坤无互/常卦）、变卦、五行生克级、卦气旺衰（显式/农历推/体卦反推）、万物类象（常规/占物）、分类占断（天时/常类/一撮金）、参外应（静占/有应）、断吉凶、定应期、修身建议三支、外发告警跳过、汇总、解读降级、取卦爻辞降级；离线不依赖 Spring/Camunda 引擎/RabbitMQ/MySQL |
| 算理分支矩阵·六（纯反射 helper） | `validation/PlumBlossomBranchMatrixBatch7Test`（35 项） | 无（反射直调私有 helper） | **否** | 反射直达纯算理/文本 helper，逐方法铺「逻辑分支 × 数据分支」：`tenCategoryNote` 诸类之应、`categoryVerdict` 十七类目吉/凶断与分项细则、`objectDivinationText` 体用五关系·器物占·四季趣时·动爻五六、`imageSymbolText` 各类取象、`dirToTrigram`/`dirFromTextOrDefault` 八方、`shape/color/sound/xiang` 取卦数、`fivePhaseOfColor` 五色、`stillObjectDirectVerdict` 圆缺、`xiangBeiNote` 向背、`scoreToLevel` 五级、`relationToBodyHex`、`breakdownNote` 破解、`mutualBodyUseNote`、`shuaiWang`/`zhanGuaJue`/`bodyRelationNotes`、`yicuoJinYaoNote`、`stroke`、相字 `xiangZiCounts`/`xiangZiRule`、`tuXiangBuLu`、`castMethodVerdictText`、`splitCharacters`/`charNumberSource`、`tenResponseJueSummary`、`fivePhaseOf`/`trigramNumberOf`；离线不依赖 Spring/Camunda/RabbitMQ/MySQL |

**设计原则**：
1. **分层解耦**——合规/SKOS 用例纯文件/内存推理，可离线快速回归；需环境的能力集中在集成与两个端到端类。
2. **生产 worker 驱动**——端到端测试不另写桩，直接由 `PlumBlossomOntologyJobWorker` 消费，覆盖真实计算链路。
3. **本体为唯一裁判**——判定据《应用架构设计》§4/§5 确认的算法口径（内置映射表 R15 + 本体域知识基线），不臆造阈值。
4. **环境自适应**——需环境的用例经 `ServiceProbe.assume*Reachable` 在环境未就绪时**跳过**（不失败）；SKOS 用例在词汇表未产出时跳过。
5. **隔离**——端到端用例的 `subject` 一律加唯一时间戳后缀，规避 `preceptCheck` 会话级 `SESSION_SUBJECTS` 去重污染。

---

## 4. 测试环境与配置

### 4.1 Profile 与配置源

- 配置源：`src/main/resources/application.yaml`（同一 yaml 既驱动 Spring 亦被 EnvPrepare 读取）。
- 测试激活 Profile：集成类 → `PlumBlossomJunitTest`；端到端类 → `PlumBlossomBPMN`（生产 worker 同 Profile）。
- 关键键：`ontology.main-path=ontology/plumblossom-all.owl`、`ontology.bpmn-path=ontology/bpmn/plumBlossomProcess.bpmn`；`camunda.client.grpc-address=http://localhost:26500`、`camunda.client.rest-address=http://localhost:9080`；`server.port=9084`；`env-prepare.rabbitmq.*`（exchange `plumblossom.alert.exchange` / queue `plumblossomAlertQueue` / routing-key `divination.alert.critical`）。

### 4.2 外部依赖端点

| 依赖 | 端点 / 载体 | 使用者 |
|---|---|---|
| RabbitMQ | AMQP `localhost:5672`（`guest/guest`） | `PlumBlossomSwrlRabbitMqTest`、`sendAlert`（端到端大吉/大凶分支） |
| Zeebe | 内存引擎（随机端口）/ 真实 `localhost:26500` | 两个端到端类 |
| Camunda REST | `localhost:9080` | 部署/实例状态查询 |
| Ontop SPARQL | `http://localhost:8080/sparql` | **暂不纳入**（第 11 步建库后） |
| MySQL | `jdbc:mysql://localhost:3306/plumblossomdb` | **暂不纳入**（第 11 步） |

### 4.3 真实环境前置

1. 启动环境：`EnvPrepare-<version>.jar start --config application.yaml`（RabbitMQ / Camunda / …）；
2. 启动 `PlumBlossomApplication`（激活 `PlumBlossomBPMN`），使 `PlumBlossomOntologyJobWorker` 订阅作业；
3. RabbitMQ 资源就绪（`EnvPrepare-<version>.jar create-rabbitmq`）；
4. Zeebe Gateway 可达 `localhost:26500`、Camunda REST 可达 `localhost:9080`。

### 4.4 BPMN 部署方式

- **内存引擎**：`PlumBlossomBpmnInMemoryTest` 经 `@DynamicPropertySource` 启动 `EngineFactory` 内存引擎并把其 gateway 地址注入 `camunda.client.*`；用例内 `deployIfAbsent` 部署，进程结束即销毁。
- **真实引擎**：`PlumBlossomBpmnRealEngineTest` 的 `@BeforeEach deploy()` 经 `ProcessOrchestrator.deployIfAbsent(client, "PlumBlossomDivinationProcess", List.of(bpmnPath))` 部署。

---

## 5. 测试数据设计

### 5.1 起卦输入场景（端到端用例共用；字段标识见《流程设计》§5.2）

| 场景 | subject（源） | divinationMethod | 体系 | 关键输入 | 覆盖链路 |
|---|---|---|---|---|---|
| **S1 · 数占（年月日时）** | `今日天时` | `年月日时起例` | 先天 | lunarYear=2026, lunarMonth=8, lunarDay=15, hourZhi=`午`, subjectCategory=`出行`, motion=`行`, season=`秋`, omenDesc=`见喜鹊鸣于东` | Route→(非字占)→Cast→…→全链 |
| **S2 · 字占** | `求谋字占` | `字占` | 先天 | text=`梅花易数`(四字平分), subjectCategory=`求谋`, season=`春` | Route→CharSplit→Cast→…→全链 |
| **S3 · 后天象占（物卦起例）** | `家宅占` | `物卦起例` | 后天 | objectCategory=`老人`, direction=`东`, subjectCategory=`家宅`, omenDesc=`见喜鹊鸣于东` | Route→(后天)→Cast→…→Omen 非空 |
| **S4 · 一撮金** | `官讼占` | `一撮金法` | 先天 | yicuoText=`乾坤`, subjectCategory=`官讼` | Route→(非字占)→Cast→…→全链 |
| **S5 · 缺省兜底** | `失物占` | `年月日时起例` | 先天 | lunarMonth=3（**不传 season**）、**不传 omenDesc**、**不传 motion** | Vitality 由农历月推导、Omen 为空、Response 以「坐」兜底 |

> **变量选取原则**：源码中 `scenario(subject, kv...)` 统一把 `subject` 加唯一时间戳后缀并置 `preceptPromise=true`，规避会话级去重；除 S5 外均显式给出 `season/omenDesc/motion` 以覆盖确定性分支。

### 5.2 四不占（戒律）负向数据

| 场景 | 关键输入 | 期望 `triggeredPrecept` | 期望 `alertText` |
|---|---|---|---|
| N-无事 | `subject=""`（空白） | `无事不占` | `无具体事项不可起卦。` |
| N-不诚 | `subject` 非空, `preceptPromise=false` | `不诚不占` | `意念不纯，请诚心再占。` |
| N-重卦 | 同一 `subject` 在同会话连续起两次 | 第 2 次 `重卦不占` | `一事一卦，反复求卦反失其机。` |

### 5.3 告警外发数据（`sendAlert` 通道）

| 场景 | 关键输入 | 期望 |
|---|---|---|
| 大吉 | payload `auspiciousness=大吉` | 队列收到含该等级 JSON |
| 大凶 | payload `auspiciousness=大凶` | 队列收到含该等级 JSON |
| 非告警路由键 | routingKey=`divination.alert.critical.unbound` | 队列无消息（未绑定） |

### 5.4 本体合规数据

直接以 `ontology/plumblossom-all.owl` 为被测对象，只读不写。

---

## 6. 测试用例清单（共 253 项 · 已固化；含 §6.6~§6.15 分支矩阵 223 项＝算理/文本纯函数 174 + JobWorker handler 层 32 + Controller web 层 17）

> 编号约定：**P** 正向 / **B** 边界 / **N** 负向。**方法名逐条取自测试源码。**

### 6.1 `PlumBlossomSwrlRabbitMqTest`（集成：消息，3 项）

`@SpringBootTest(classes=PlumBlossomApplication.class)` + `@ActiveProfiles("PlumBlossomJunitTest")`；`@BeforeAll requireRabbitMq()`（`ServiceProbe.assumeAllReachable(..., LOCALHOST, 5672)`）。

| ID | Order | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|---|
| P-1 | 1 | `testAlertTopologyDeclaredAndDelivered` | 声明 Exchange/Queue/Binding + `RabbitMqHandler.send(...)` | 队列 5s 内收到消息；报文与发送 JSON 一致；含 `大吉` |
| P-2 | 2 | `testSendAlertPayloadShape` | payload{subject,hexagramName,auspiciousness=大凶,summary} | 报文含 subject/hexagramName/auspiciousness/summary |
| B-1 | 3 | `testNonAlertRoutingKeyNotDelivered` | 用未绑定路由键发送 | 队列 1s 内**无**消息（绑定语义正确） |

### 6.2 `PlumBlossomBpmnInMemoryTest`（端到端·内存引擎，9 项）

`zeebe-process-test` 内存引擎；`@DynamicPropertySource startInMemoryEngine`；`@BeforeAll requireRabbitMq()`；由**生产 worker** 消费。

| ID | 方法 | 场景 | 预期 |
|---|---|---|---|
| P-1 | `testNumberDivinationCompletes` | S1 数占 | 完成、无 incident；`Task_PreceptCheck→Task_Route→Task_Cast→…→Task_Summary` 顺序通过 |
| P-2 | `testCharacterDivinationTakesSplitBranch` | S2 字占 | 经 `Task_CharSplit`；全链顺序通过 |
| P-3 | `testPostnatalSymbolDivinationCompletes` | S3 后天象占 | 全链顺序通过 |
| P-4 | `testYicuoJinCompletes` | S4 一撮金 | 全链顺序通过 |
| B-1 | `testDefaultFallbacks` | S5 缺省 | 完成、无 incident；全链顺序通过 |
| B-2 | `testMajorLevelStillConverges` | S1（可能大吉/大凶） | 经 `Task_Advice`/`Task_Summary`/`End_01`（大吉/大凶亦收敛） |
| N-1 | `testNoThingPreceptAlertsAndTerminates` | `subject=""` | 走 `Task_PreceptCheck→Task_PreceptAlert→End_01` |
| N-2 | `testInsincerePreceptAlerts` | `preceptPromise=false` | 走 `Task_PreceptAlert` 分支 |
| N-3 | `testRepeatedSubjectTriggersChongGua` | 同 subject 两次 | 第 1 次经 `Task_Route`；第 2 次走 `Task_PreceptAlert` 分支 |

### 6.3 `PlumBlossomBpmnRealEngineTest`（端到端·真实引擎，6 项）

`@SpringBootTest(classes=PlumBlossomApplication.class)` + `@ActiveProfiles("PlumBlossomBPMN")`；`@BeforeAll requireCamunda()`（`assumeAllReachable(..., LOCALHOST, 9080, 5672)`）；`@BeforeEach deploy()`；`@Timeout(300s)`。

| ID | Order | 方法 | 场景 | 预期（读取流程变量取值） |
|---|---|---|---|---|
| P-1 | 1 | `testNumberDivinationRealEngine` | S1 | 完成；本卦非空、动爻∈1..6、生克匹配 `[1-5]·`、吉凶∈五级、建议/应期/汇总非空 |
| P-2 | 2 | `testCharacterDivinationRealEngine` | S2 | `isCharacter=true`；结果合法 |
| P-3 | 3 | `testPostnatalRealEngine` | S3 | `divinationSystem=后天`；`omenVerdict` 非空；结果合法 |
| P-4 | 4 | `testMajorLevelAlertRealEngine` | S1 | 完成；结果合法（含 `messageStatus`） |
| B-1 | 5 | `testDefaultFallbackRealEngine` | S5 | `bodyVitality` 非空（农历月推导）；`responsePeriod` 非空（兜底） |
| N-1 | 6 | `testPreceptAlertRealEngine` | `subject=""` | `alertText=无具体事项不可起卦。`；`passPrecept=false` |

### 6.4 `validation/PlumBlossomOntologyComplianceTest`（纯 OWL，6 项）

独立 `OWLOntologyManager` 加载 `ontology/plumblossom-all.owl`（TBox）+ `plumblossom-abox.owl`（ABox）**合并闭包** + Openllet；**离线**。注：本体实体 IRI 保留短前缀形式（如 `yijing:Seq_01`），按签名 IRI **片段包含**断言。第 11 步（闸门⑩）后 P-2 改校验数据脚本 `ontology/database/data.sql`。**第 12 步（闸门⑪）后**合并闭包 `individuals=1133`（六十四卦名录 64 + 爻 384 + 类象 559 + 维度 27）；P-1~P-5、B-1 口径不变（P-3 仍断言 `*Trigram` 个体 = 8）。**第 13 步（闸门⑥）后**：TBox 经 `owl:imports` 并入 SKOS 术语表 `plumblossom-terminology.ttl`，加载逻辑与框架一致——为 `.ttl` 补注册 `SimpleIRIMapper`；因 `AutoIRIMapper` 不解析 `.ttl`；合并闭包 `closureSize=14`、`axioms=7851`，但**个体统计剔除 SKOS 命名空间**，`individuals(OWL)=1133` 不变（`skos:Concept` 不计入 OWL 个体）。

| ID | 方法 | 校验点 | 预期 |
|---|---|---|---|
| P-1 | `ontologyLoadsAndIsConsistent` | 解析 + 一致性 | 加载成功；`reasoner.isConsistent()==true` |
| P-2 | `hexagramSequenceComplete` | `data.sql` 六十四卦序 + ABox 不含 `Seq_*` | `data.sql` 含 `Seq_01`…`Seq_64` =64；本体 ABox 不再保留 `Seq_*`（已入库） |
| P-3 | `trigramKnowledgeComplete` | `*Trigram` 个体 | =8；含乾兑离震巽坎艮坤 |
| P-4 | `preceptEntriesPresent` | `Precept_*` 个体 | =4（BuCheng/BuYi/ChongGua/WuShi） |
| P-5 | `auspiciousnessLevelsPresent` | 吉凶五级 + `Cat_*` | 含 DaJi/Ji/Ping/Xiong/DaXiong；事项类别 ≥18 |
| B-1 | `restrictionsAndSwrlRulesPresent` | SubClassOf / SWRL | 均 >0 |

### 6.5 `validation/PlumBlossomSkosTerminologyTest`（纯 Jena，6 项 · 第 13/14 步生效）

> 依赖第 13 步产出 `ontology/plumblossom-terminology.ttl`；未产出时 `@BeforeAll` 经 `Assumptions.assumeTrue` **整体跳过**，词汇表就绪后自动生效。加载术语表（`RDFDataMgr.loadModel`）并加载 `ontology/` 下全部 `.owl`（TBox 九域）用于交叉校验。术语表 150 概念（类 71 / 对象属性 38 / 数据属性 41）+ 57 同义词（覆盖 41 概念）。

| ID | 方法 | 校验点 | 预期 |
|---|---|---|---|
| P-1 | `declaresConceptScheme` | 方案 + 概念非空 | 根声明 `skos:ConceptScheme`；概念集非空 |
| P-2 | `everyConceptHasChinesePrefLabel` | `skos:prefLabel` | 每概念含 `@zh` 首选标签 |
| B-1 | `broaderTargetsAreWellFormed` | `skos:broader` | 目标均已定义、无自环 |
| X-1 | `everyConceptExactMatchesAnOntologyEntity` | 概念↔本体（无孤立） | 每概念 `skos:exactMatch` 目标存在且为本体已声明的类/对象属性/数据属性 |
| X-2 | `everyLabelledOntologyEntityHasAConcept` | 本体↔概念（无遗漏） | 本体中每个带 `rdfs:label` 的类/属性均有对应概念 |
| S-1 | `synonymsAreWellFormed` | `skos:altLabel` | 数量 >0、均为中文（`@zh`）、不与首选标签重复 |

### 6.6 `validation/PlumBlossomBranchMatrixTest`（算理分支矩阵·一，27 项 · 纯反射离线）

> 反射调用 `PlumBlossomOntologyJobWorker` 私有纯函数（`new` 实例即可，不加载本体/不依赖引擎）；覆盖起卦模数、八卦爻位、重卦合成、五行生克旺衰、体用 5 级、吉凶回映射、应期、破解取材、五行属性映射的**逐分支**断言。

| 组 | 方法 | 校验点（关键断言） |
|---|---|---|
| A 起卦数与卦象 | `mod8Boundary` | `((n-1)%8+8)%8+1`，含 0→8、-1→7、9→1、16→8 |
| | `mod6Boundary` | `((n-1)%6+6)%6+1`，含 0→6、-1→5、12→6 |
| | `bitsMapping` | 乾111/兑110/离101/震100/巽011/坎010/艮001/坤000 |
| | `trigramOfBitsRoundTrip` | 8 卦 `bits↔trigramOfBits` 往返恒等 |
| | `composeAndReconstruct` | 乾/坤六爻重建；六爻合成还原「乾为天/坤为地/天风姤」 |
| | `flipMovingLine` | 初爻动→天风姤、上爻动→泽天夬；0/9 越界钳制到 1/6 |
| | `pairOfLookup` | 卦名→上下卦 {1,1}/{8,8}/{1,5}/{3,6}；未知兜底 {1,1} |
| | `decodeStartNumbers` | "3\|4\|5"→{3,4,5}；"1\|2"→{1,2,0}；非法/无分隔/null→全 0 |
| | `triNumLookup` | 乾1/兑2/坤8；未知与 null 兜底 1 |
| B 五行与象数 | `generatesRelations` | 五正边为真；相克/逆生/同气/未知为假 |
| | `overcomesRelations` | 五正克边为真；相生/被克为假 |
| | `seasonFiveMapping` | 春木/夏火/秋金/冬水/四季土；未知兜底土 |
| | `vitalityFiveBranches` | 以春季木验证旺/相/休/囚/死五支；夏火旺、夏土相 |
| | `seasonFromLunarMonth` | 寅卯春/巳午夏/申酉秋/亥子冬/辰戌丑未四季；越界→四季 |
| | `hourNumLookup` | 子1…亥12；null/未知兜底 1 |
| | `yearBranchNumLookup` | 2024→5(辰)、2020→1(子)、2025→6(巳)；非正兜底 1 |
| | `fivePhaseOfTrigrams` | 乾兑金/离火/震巽木/坎水/艮坤土 |
| | `fiveAttributeMappings` | 方位/色/时令/器物 + `remedyLine` 四端串；null→空串 |
| | `generatorOvercomerMediator` | 生/克某五行反查；通关仅克关系有值、非克为 null |
| C 体用五级与吉凶 | `shengKeLevelFive` | 用生体1/比和2/体克用3/体生用4/用克体5 |
| | `shengKeNameAndLabel` | 五级名称/标签全映射；非法级兜底「体用未定/平」 |
| | `parseShengKeLevelCases` | 取首字符 1~5；非数/越界/空/null→3 |
| | `scoreToLevelCases` | clamp 1~5 → 大吉/吉/平/凶/大凶；上溢/下溢/负值钳制 |
| | `relationToBodyFive` | 比和/生体/克体/体克/体生五支 |
| | `relationToBodyHexCases` | 空卦→比和；重卦取最显著（克体优先） |
| D 应期 | `responsePeriodMotion` | 行/走半其数、坐/卧倍其数、立不改；近/中/远档 + 五行生数参断 + 吉/凶期 |
| | `responsePeriodLevels` | ≤6 近·日 / 7~11 中·月 / >11 远·年；凶判据含「凶」 |

### 6.7 `validation/PlumBlossomBranchMatrixBatch2Test`（算理分支矩阵·二，31 项 · 纯反射离线）

> 覆盖十应分类与取象、天时占诸卦众寡、起卦法 `encodeStartNumbers` 多起例、分类断语 `categoryVerdict` 吉凶与分项细则；`ontologyDomainReady=false`、`ontoLore=null` 走内置兜底。

| 组 | 方法 | 校验点（关键断言） |
|---|---|---|
| E 十应分类取象 | `tenResponseCategoryCases` | 十一类关键词映射 + 未入类/空/null→「外」 |
| | `isMovingOmenCases` | 飞/走/鸣/落→动；静坐/空→静 |
| | `stillObjectDirectCases` | 物圆事成/器缺事败/圆缺参半/无征空串 |
| | `fivePhaseOfColorCases` | 青碧绿木/红紫赤火/白金/黑水/黄(及兜底)土 |
| | `soundToTrigramCases` | 语兑2/扣击震4/金声乾1/火声离3/土声坤8/未知0 |
| | `omenToTrigramCases` | 马乾1/羊兑2/火离3/雷震4/风巽5/水坎6/山艮7/地坤8 |
| | `shapeAndColorCases` | `shapeToTrigram` 全表 + `colorToTrigram` 全表（含未知兜底1） |
| | `xiangBeiCases` | 来/至→向；去/过→背；无征空串 |
| | `directionTrigramCases` | 后天方位→先天卦数全表；`dirFromTextOrDefault` 无方位回退 |
| F 天时占 | `tianShiEmpty` | 卦象未备提示 |
| | `tianShiRainClear` | 坎多主雨/离多主晴/坎离均时晴时雨 |
| | `tianShiQianKun` | 乾主晴明/坤主阴晦/不见坎离晴雨未定 |
| G 起卦法 | `encodeYearMonthDayHour` | 年月日时起例 → "7\|8\|8" |
| | `encodeObjectCount` | 物占数 → "3\|1\|4" |
| | `encodeSound` | 声音占单声 "1\|10\|11"、双声 "1\|5\|16" |
| | `encodeZhangChi` | 丈尺占 → "5\|6\|11" |
| | `encodeChiCun` | 尺寸物占 → "3\|4\|8" |
| | `encodeWind` | 风觉占：南→"5\|3\|9"、东→"5\|4\|10" |
| | `encodeShape` | 形物占（方=8）→ "8\|1\|9" |
| | `encodeColor` | 验色占（青=4）→ "4\|1\|5" |
| | `encodeYiCuoJin` | 一撮金法：外卦在上、内卦在下、总画取动（与 `stroke` 自洽） |
| | `encodeDefault` | 未知起卦法 → "1\|1\|2" |
| | `encodeStillObject` | 占静物（马=1）→ "1\|1\|2" |
| | `encodeAnimal` | 占动物（马在东 → 上1下4）→ "1\|4\|6" |
| | `encodeHumanByImage` | 为人占·取象子法（马=1）→ "1\|1\|2" |
| H 分类断语 | `categoryGood` | 求财·用生体(level1) → 吉断「财可得」 |
| | `categoryBad` | 求财·用克体(level5) → 凶断「损财」 |
| | `categoryMarriage` | 婚姻·用卦乾 → 「端正而长」性情容貌 |
| | `categoryBirth` | 生产·乾为天→主生男 / 坤为地→主生女 |
| | `categoryDisease` | 疾病·坤土克坎水 → 「坤卦克体」测鬼神 |
| | `categoryDefault` | 未录类别 → 「以体为主、用为所应，参互变综合断之」 |

### 6.8 `validation/PlumBlossomBranchMatrixBatch3Test`（算理分支矩阵·三，18 项 · 纯反射离线）

> 覆盖十应各类之应填注、应体生克语气、向背/坐端/十大应/观物洞玄/万物赋/三要灵应诸兆、字占取数与取数来源、分句与为人占子法推断、四不占告示；`ontoLore=null`、`ontoZuoDuan/ontoShiDaYing=null`、`ontoPreceptAdmonition` 为空 Map，全走内置兜底。

| 组 | 方法 | 校验点（关键断言） |
|---|---|---|
| I 十应各类之应 | `tenCategoryTianShi` | 天时：晴霁(离)/雨雪(坎)/雷风(震巽)/default(乾) 四支 |
| | `tenCategoryDiLi` | 地理：茂树(震)/江湖(坎)/窑灶(离)/default(艮) 四支 |
| | `tenCategoryRenShi` | 人事：老人(乾)/老妇(坤)/少男(艮)/少女(兑)/无归属（who 为空） |
| | `tenCategoryOtherCats` | 时令(春木旺/null土旺)/方卦/静物/言语/声音 |
| | `tenCategoryDongWu` | 动物：鹊/鸦/鸿雁/鸡/马 逐兆 + 无兆仅 base |
| | `tenCategoryUnknown` | 未录类别/null → 空串 |
| J 应体生克语气 | `shengKeToneCases` | 比和而吉/逢生（大吉）/见克（不吉）/泄气（体生之，为耗）四支 |
| K 诸兆 helpers | `zuoDuanCases` | 乾宫/坤位命中 + 无方位空串 |
| | `shiDaYingCases` | 裹头/跣足/三坐命中 + 无兆空串 |
| | `dongXuanCases` | 宅气如春(吉)/冷落似秋(凶)/宿火不成 + 无兆空串 |
| | `wanWuCases` | 喧闹/鹊噪/禽鸣命中 + 无兆空串 |
| | `sanYaoCases` | 将叛者/将疑者/吉人/失其守者 + 无兆空串 |
| L 字占取数 | `splitCharactersBasic` | 空=1/1、十一字平分、一/二/三字按笔画 |
| | `splitCharactersTones` | 前端平仄/内置平水韵/四~十字各档字数 |
| | `charNumberSourceCases` | 空/字数/Unihan笔画/前端平仄/内置平水韵/笔画（平仄缺）六值 |
| | `splitClausesCases` | 按句读断句保序；空/null→空列表 |
| | `inferHumanSubCases` | 听语声/书写来意/取象三支 + 空兜底 |
| M 四不占告示 | `preceptAdmonitionCases` | 无事不占/重卦不占/不诚不占/不义不占四文案 + default/null 兜底 |

### 6.9 `validation/PlumBlossomBranchMatrixBatch4Test`（算理分支矩阵·四，15 项 · 纯反射离线）

> 覆盖各「断法」纯函数/表查与卷之一~卷之五「照录备考」零参 getter；`ontoLore=null`/`backendService=null` 走内置兜底（`yicuoJinYaoNote` 直取内置同源表）。

| 组 | 方法 | 校验点（关键断言） |
|---|---|---|
| N 断法纯函数 | `riYingCases` | 日应：寅卯木/巳午火/申酉金/亥子水/辰戌丑未土 + 空/未知空串 |
| | `niaoShouCases` | 鸟兽时日：庚丁(凶刃)/己壬(食禄) + 未知/空 |
| | `jiaXingCases` | 加姓：有姓名「今加×姓」 vs 无姓名示例 |
| | `xieZiCases` | 写字之应：关键词命中 vs 未命中/null 空 |
| | `zhenShengZhenKeCases` | 真生真克：金(真火)/木(真金)/火(真水)/土兜底 |
| | `diseaseCases` | 疾病测鬼神（乾西北/坎北方/未知空）+ 逐爻（天地否标【今动】） |
| | `dietCases` | 饮食：无坎兑/有坎/有兑/坎兑(酒肉醉饱)/坎逢坤(无酒) |
| | `zhanGuaJueCases` | 占卦诀：用卦生体(速成)/克体阻节/null 空 |
| | `shuaiWangCases` | 衰旺：体旺/体衰有生(衰稍吉)/体衰逢克(凶甚)/未详/null 空 |
| | `dongJingCases` | 动静：行/走/坐/卧/立迟速 + 未详 |
| | `guaYingCases` | 卦应 · 内外动静：命中/未知/空 |
| | `yicuoJinCases` | 一撮金爻诀：乾为天二爻命中 / 越界(0/7)/未知卦/空 → 空串 |
| | `mutualBodyUseCases` | 体用互变：天地否命中 / 乾坤无互空 / null 空 |
| | `xiangZiCases` | 相字：空/null/未匹配 → 空串 |
| | `zeroArgNoteGetters` | 卷之一~五照录 getter 非空抽样（9 个） |

### 6.10 `validation/PlumBlossomBranchMatrixBatch5Test`（算理分支矩阵·五，13 项 · 纯反射离线）

> 覆盖卷之一~卷之五「照录备考」零参 note getter 全量、十应/应期总纲、长文本片段与解读规则串；纯反射 `invoke(null)` 离线即跑，不加载本体、不依赖外部服务。

| 组 | 方法 | 校验点（关键断言） |
|---|---|---|
| P 照录 getter | `juanYiNotes` | 卷一 5 篇前缀「卷之一」+ 卷首序「梅花易数序」+ 占法玩法「占法／玩法」 |
| | `juanErNotes` | 卷二 11 篇前缀「卷之二」 |
| | `juanSanNotes` | 卷三 21 篇前缀「卷之三」 |
| | `juanSiNotes` | 卷四 8 篇前缀「卷之四」 |
| | `juanWuNotes` | 卷五 14 篇前缀「卷之五」 |
| | `sanYaoNotes` | 三要灵应篇 序/导论/总论 各前缀 |
| 总纲 | `shiYingNotes` | 十应奥论/占卜十应诀/万物赋应期/先天后天论/十应目论/方应 |
| 长文本 | `longTexts` | 屋宅占「〔屋宅之占诀〕」/器物占「〔器物占〕」/变卦式「〔变卦式八则」 |
| 分派 | `objectDaikao` | 占物类命中（器物占+观其爻辞+变卦式）/非占物·null → 空串 |
| 观物 | `miscNotes` | 观梅数诀序/观物洞玄歌/图象补录/相字取象摘要 |
| 规则串 | `tailorRules` | 解读要求 vs 白话解释要求（非空且互不包含对方标题） |
| 小整数 | `chineseNumberCases` | 0/9/10/15/20/99 + 100/-1 越界回退 `String.valueOf` |
| 诸论 | `zhuLunNotes` | 占卜克应之诀/遗论/卦断遗论/八卦心易体用诀 各前缀 |

---

### 6.11 `validation/PlumBlossomBranchMatrixBatch6Test`（JobWorker handler 分支矩阵，32 项 · Mockito 离线）

> 前五批均覆盖「算理/文本」纯函数；本批补齐 **20 个 `@JobWorker` 方法**的节点级分支。以 Mockito mock Camunda `ActivatedJob`/`JobClient`，驱动 `completeResult(...)`（`newCompleteCommand(k).variables(map).send().join()`）与失败路径 `fail(...)`（`newThrowErrorCommand(k).errorCode(..).errorMessage(..).send().join()`），并用 `ArgumentCaptor` 式捕获内层 `result` 映射逐 handler 断言。纯离线：`new PlumBlossomOntologyJobWorker()` 即可，本体/LLM/OBDA 依赖走内置兜底或空转，**不依赖 Spring / Camunda 引擎 / RabbitMQ / MySQL**。

| 组 | 方法 | 覆盖 handler / 分支（关键断言） |
|---|---|---|
| 四不占校验 | `preceptCheck*` | 无事(subject 空)→pass=false/无事不占；未承诺→不诚不占；不义→不义不占；首占通过、同题重占→重卦不占（`SESSION_SUBJECTS` 去重） |
| 戒律告示 | `preceptAlert*` | 已知名文案（无事/不诚）+ 未知戒律兜底 |
| 起卦法路由 | `routeDivination*` | 字占→先天/`0\|0\|0`；物卦起例→后天；method=null→后天默认编码 `1\|1\|2` |
| 字占拆数 | `characterSplit` | 三字取数，返回上下数与取数来源非空 |
| 起卦成卦 | `castHexagram*` | 非字占由 rawNumber 解码→乾为天/动爻6；字占由上下数+时支加时→乾为天/动爻3 |
| 定体用 | `determineBodyUse` | 地天泰动1（下卦）→体坤用乾；动6（上卦）→体乾用坤（体用互换） |
| 互卦 | `deriveMutualHexagram*` | 乾坤无互→互其变卦（乾为天动1→天风姤）；常卦取中四爻（地天泰→雷泽归妹） |
| 变卦 | `deriveChangedHexagram` | 乾为天动1→天风姤 |
| 五行生克 | `analyzeShengKe` | 体乾(金)被用离(火)克→第 5 级（`5·`） |
| 卦气旺衰 | `analyzeVitality*` | 显式 season=春 体震木→旺；season 缺省由 lunarMonth=7→秋（震木→死）；体卦缺省由本卦+动爻反推（地天泰动1→体坤） |
| 万物类象 | `extractImageSymbol*` | 常规取象非空；占物类（占动物+见器物）补入器物/断物之妙 |
| 分类占断 | `categorizeDivination*` | 天时占走全卦观五行分支；非天时走体用生克分类断（人事）；一撮金法补〔八卦诀〕宫名 |
| 参外应 | `analyzeOmen*` | 无外应→〔静占〕不论外卦；有外应（见圆物）→三要十应参断 |
| 断吉凶 | `judgeAuspiciousness` | 产出吉凶等级 + 过程描述含「先看体用生克」+ processFlag |
| 定应期 | `determineResponsePeriod` | 应期含万物应期/先后天诀/占卜克应之诀，非空 |
| 修身建议 | `generateAdvice` | 大吉/吉→守正保泰（破解空）；凶→积德改行（破解非空）；平→居中持正 |
| 外发告警 | `sendAlert` | 非大吉/大凶→`SKIPPED_NON_MAJOR`（离线不触 MQ） |
| 汇总 | `aggregateResult` | 含「【占断事项】」+ subject；`divinationFactSheet` 非空 |
| 邵康节解读 | `interpretResult` | LLM 未装配→`aiInterpretation`/`aiInterpretationPlain` 空串降级不阻断 |
| 取卦爻辞 | `fetchDictum` | OBDA 未就绪→卦辞/爻辞四项空串降级不阻断 |

### 6.12 `validation/PlumBlossomBranchMatrixBatch7Test`（纯算理/文本 helper 反射矩阵，35 项 · 纯反射离线）

> §6.6~§6.10 以反射覆盖大量纯函数，但 JaCoCo 报告显示仍有大块算理/文本 helper 分支未被逐条触达（部分此前只经 handler 间接取到单一取值）。本批直接反射直调这些私有 helper，按「逻辑分支 × 数据分支」二维铺测，**不经过 handler、不需要 Mockito**，`new PlumBlossomOntologyJobWorker()` 即可离线运行，本体/LLM/OBDA 依赖全为 null 走内置兜底表，不依赖 Spring/Camunda/RabbitMQ/MySQL。

| 组 | 覆盖的私有 helper / 分支（关键断言） |
|---|---|
| 十应分类之应 | `tenCategoryNote`：天时（晴霁/雨雪/雷风/default）·地理（茂树/江湖/窑灶/default）·人事（老人/老妇/少男/少女/无）·时令·方卦·动物（鹊/鸦/雁/鸡/马/默认）·静物·言语·声音·未知→空 |
| 分类占断 | `categoryVerdict` 十七类目 × 吉断(级1)/凶断(级5) 双走；`生产`辨男女·`婚姻`论对方·`疾病`测鬼神/逐爻·`饮食`等特化分支 |
| 占物类断法 | `objectDivinationText`：非占物法→空串；体用五关系（生体/克体/体生/体克/比和）；器物占（兑/坎乾/震巽/default）；观物趣时四季；动爻五六；观物克应 |
| 万物类象 | `imageSymbolText`：十三类目取象（疾病/求财/交易/家宅/屋舍/坟墓/婚姻/官讼/出行/行人/失物/谒见/人事）+ 体用缺省由本卦推；`categorySymbolHint` 诸类 switch |
| 方位/形色声象取数 | `dirToTrigram` 八方+未知、`dirFromTextOrDefault`、`shapeToTrigram`/`colorToTrigram`/`soundToTrigram`/`xiangToNumber` 各分支、`fivePhaseOfColor` 五色、`stillObjectDirectVerdict` 圆缺、`xiangBeiNote` 向背 |
| 吉凶/生克/破解 | `scoreToLevel` 五级+越界夹取、`relationToBodyHex`、`breakdownNote`（缺体/吉/凶通关/平持正）、`mutualBodyUseNote`（乾坤无互/常卦）、`shuaiWangNote`（旺/衰有生/衰无生/未知）、`zhanGuaJueNote`、`bodyRelationNotes` |
| 爻诀/笔画/相字/图象/起卦断 | `yicuoJinYaoNote`（内置兜底/越界/空卦/爻位0）、`stroke`（常用字/Unihan/码位回退）、`xiangZiCounts`/`xiangZiRule`、`tuXiangBuLu`、`castMethodVerdictText`（风觉/鸟/听声音/未知） |
| 字占取数/应期/属性 | `splitCharacters`（空/1/2/3/11字+、前端平仄）、`charNumberSource`、`tenResponseJueSummary`（旺/衰/空）、`fivePhaseOf`、`trigramNumberOf` |

---

### 6.13 `validation/PlumBlossomBranchMatrixBatch8Test`（Web 控制层 + 残余 helper 反射矩阵，11 项 · 离线）

> 前七批覆盖算理/文本纯函数与 JobWorker handler；本批补两块此前空白：**① Web 控制层**——以 `MockMvcBuilders.standaloneSetup(controller)` 启动 `PlumBlossomController`（`CamundaClient` 保持 null，走离线降级），覆盖入参校验/启动降级/状态查询降级/健康检查；**② 残余纯 helper**——反射直调 `applyAppendixFullText`/`applyJuanTextOverrides`（原著全文/五卷正文外置回写装配）、`splitCharacters` 5~10 字、`objectDivinationText` 空体用、附录 getter、`objectDaikaoText`/`baGuaJueCounts`、诸论 getter。纯离线：不依赖 Spring 容器 / Camunda 引擎 / RabbitMQ / MySQL，**不用 Mockito**。

| ID | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|
| B8-01 | `controller_divination_blankSubject` | `POST /api/plumblossom/divination`：body `{}` 与 `{"subject":"   "}` | 均 `400`；`$.error = "subject 不能为空"` |
| B8-02 | `controller_divination_engineNull` | `POST /divination`：`{"subject":"测求职"}`，引擎 null | `503`；`$.error` 非空（`start` 异常降级） |
| B8-03 | `controller_status_offline` | `GET /divination/123`，引擎 null | `200`；`instanceKey=123`、`status="RUNNING"`（`fetchInstance` 捕获异常返 null）、`variables` 存在 |
| B8-04 | `controller_health` | `GET /health` | `200`；`engineAvailable=false`、`processId="PlumBlossomDivinationProcess"`、`bpmnPath` 反射预设值 |
| B8-05 | `applyAppendixFullText_guardBranches` | `null` / 空表 / 值 null / 短数组 / 无同名字段 / 非 String 字段 | 全部安全跳过、不抛异常 |
| B8-06 | `applyJuanTextOverrides_guardBranches` | `null` / 空表 / 非 `JUAN_` 前缀 / 字段不存在 / 文本空 | 全部安全跳过、不抛异常 |
| B8-07 | `splitCharacters_fiveToTen` | 5/6/7/8/9/10 字文本 | 各档返回 `int[2]` 且两数均 >0 |
| B8-08 | `objectDivinationText_blankBodyUse` | 占动物·体用皆空·时节秋·见圆物 | 成篇文本含「占物类断法」 |
| B8-09 | `appendix_getters` | `appendixDoctrineNote`/`appendixTextNote(八卦诀/渔樵对问/邵雍/无关/null)`/`appendixTextCount` | 含「附录」；三命中各含关键词、无关/`null`→`""`；count>0 |
| B8-10 | `objectDaikao_and_bagua` | `objectDaikaoText(占动物/年月日时起例)`；`baGuaJueCounts` | 占物类非空、非占物`""`；`baGuaJueCounts = {8, 64}`（**八宫 × 六十四卦总数**） |
| B8-11 | `lun_getters` | `yiLunNote`/`zhanBuKeYingJueNote` | 均非空白 |

### 6.14 `validation/PlumBlossomBranchMatrixBatch9Test`（Web 控制层引擎成功路径，13 项 · 离线）

> 第八批以 `CamundaClient=null` 覆盖了降级路径（400/503/RUNNING）；本批以 **Mockito mock `CamundaClient` fluent 客户端**（不触达真实引擎/RabbitMQ/MySQL），驱动 `ProcessOrchestrator` 调用链被 mock 后的 **成功/完成/异常状态主路径**：`startDivination` 部署+启动成功、部署失败仍续跑、启动抛异常降级；`getDivinationStatus` 的 COMPLETED 14 字段回填（全/缺省两种）、TERMINATED/INCIDENT/RUNNING 状态分派、`readVariables` 抛异常降级、`fetchInstance` 返 null、`health` 引擎在位、`body=null`/`error(null)` 边界。**说明**：此为本项目**不使用 Mockito 约定**的针对性例外（仅 mock Camunda 客户端即可离线覆盖原需真实引擎的成功路径）。

| ID | 方法 | 输入（mock 编排） | 预期断言（关键） |
|---|---|---|---|
| B9-01 | `divination_success` | 部署空→部署成功；`start` 返 `9527`；body 含 `note:null` | `200`；`instanceKey=9527`、`processId`、`subject` 回显；null 值被过滤 |
| B9-02 | `divination_deployFailsButStartOk` | `newProcessDefinitionSearchRequest` 抛异常（deploy 吞为 false 仅 warn）；`start` 返 `11` | `200`；`instanceKey=11`、`subject` 回显 |
| B9-03 | `divination_startThrows` | `start.send().join()` 抛 `RuntimeException("engine down")` | `503`；`error` 非空 |
| B9-04 | `status_completed_fullFields` | `fetchInstance`=COMPLETED；14 变量齐备 | `200`；`status=COMPLETED`；14 字段均取变量值（`V_<key>`） |
| B9-05 | `status_completed_missingDefaults` | COMPLETED；仅 `alertText` 有值 | `200`；`alertText`=值、缺失字段回退 `""` |
| B9-06 | `status_terminated` | `fetchInstance`=TERMINATED | `status=TERMINATED`；不触发 14 字段（`alertText` 不存在） |
| B9-07 | `status_incident` | ACTIVE + `hasIncident=true` | `status=INCIDENT` |
| B9-08 | `status_running` | ACTIVE + `hasIncident=false` | `status=RUNNING` |
| B9-09 | `status_variablesThrow` | COMPLETED；`readVariables` 抛异常降级空 map | `200`；`variables` 空、`status=COMPLETED`、`alertText=""` |
| B9-10 | `status_instanceNull` | `fetchInstance` 返 null（未导出/404） | `status=RUNNING` |
| B9-11 | `health_enginePresent` | 注入非 null `CamundaClient` | `engineAvailable=true` |
| B9-12 | `divination_nullBody` | 直接调 `startDivination(null)` | `400`（subject 取 null 分支 覆盖 line 33） |
| B9-13 | `divination_startThrowsNoMessage` | `start` 抛无消息 `RuntimeException` | `503`；`error="未知错误"`（覆盖 `error(null)` 分支） |

### 6.15 `validation/PlumBlossomBranchMatrixBatch10Test`（纯工具/纯计算 helper 反射矩阵，28 项 · 纯反射离线）

> 第十批承接 JaCoCo 分析：JobWorker 剩余**完全未覆盖**方法中，凡依赖 Openllet/Ontop/RabbitMQ/LLM 的环境块离线不可达；本批反射直调**纯工具/纯算理**私有方法，将 5 个零覆盖方法与大批「本体优先/内置兜底」分支逐档补齐。纯离线 `new PlumBlossomOntologyJobWorker()`，实例域知识 map 为空、静态本体域字段为 null，全走内置兜底；**不用 Mockito**、不依赖 Spring/Camunda/RabbitMQ/MySQL。

| ID | 方法 | 输入（数据分支） | 预期断言（关键） |
|---|---|---|---|
| B10-01 | `parseIntOrNull` | null/`""`/空白/`"42"`/`" 42 "`/`"abc"`/`"4x"` | null/null/null/42/42/null/null |
| B10-02 | `iriLocal` | null/`http://a/b#C`/`.../x`/`plain`/末尾`#` | null/`C`/`x`/`plain`/`""` |
| B10-03 | `toneName` | 1/2/3/4/0/5 | 平/上/去/入/?/? |
| B10-04 | `putIfInt` | key=null/value=null/空白/`abc`/`" k "`,`" 7 "` | 前4不入表；(k,7) 入表 |
| B10-05 | `afterBackendServiceReady` | OBDA 未就绪（backendService=null） | 安全降级不抛异常 |
| B10-06 | `shapeToTrigram` | 圆/刚/方/柔/仰/覆/长/中刚外柔/内柔外刚/枯槁/文彩/破/未知/null | 1/2/8/5/4/7/5/6/3/3/3/2/1/1 |
| B10-07 | `colorToTrigram` | 青/红紫赤/黄/白/黑/橙/null | 4/3/8/2/6/1/1 |
| B10-08 | `soundToTrigram` | 语/扣/金/火/土/無/空/null | 2/4/1/3/8/0/0/0 |
| B10-09 | `stillObjectDirectVerdict` | 圆/缺/圆缺/平常/空/null | 事成/事败/参半/""/""/"" |
| B10-10 | `firstNumber` | null/空/`3声`/`三声`/`十二声`/`二十三点`/`两声`/`abc` | 0/0/3/3/12/23/2/0 |
| B10-11 | `cnDigit` | 一~九/十/x | 1~9/-1/-1 |
| B10-12 | `parseChineseNumber` | 十二/二十三/两/十/abc | 12/23/2/10/0 |
| B10-13 | `tenResponseCategory` | 春/晴/山/老人/鸟/花/语/鸣/青/字/东/無名/空/null | 时令/天时/地理/人事/动物/静物/言语/声音/五色/写字/方卦/外/外/外 |
| B10-14 | `shengKeName` | 1~5/0 | 用生体/体用比和/体克用/体生用/用克体/体用未定 |
| B10-15 | `shengKeLabel` | 1~5/0 | 大吉/吉/平/凶/大凶/平 |
| B10-16 | `parseShengKeLevel` | `1`/`3x`/`5`/`9`/空/null | 1/3/5/3/3/3 |
| B10-17 | `scoreToLevel` | 1~5/0/9 | 大凶/凶/平/吉/大吉/大凶/大吉（越界 clamp） |
| B10-18 | `fivePhaseOfColor` | 青/红/白/黑/黄/无色 | 木/火/金/水/土/土 |
| B10-19 | `generates` | 相生五对/非相生/未知 | true×5 / false / false |
| B10-20 | `overcomes` | 相克五对/非相克/未知 | true×5 / false / false |
| B10-21 | `seasonFive` | 春/夏/秋/冬/四季/無/null | 木/火/金/水/土/土/土 |
| B10-22 | `vitalityOf` | 春(木当令)下 木/火/水/金/土 | 旺/相/休/囚/死 |
| B10-23 | `generatorOf`/`overcomerOf`/`mediatorBetween` | 反查与通关 | 生火者木/生土者火/生木者水；克木者金/克土者木；金克木通关水/木克土通关火 |
| B10-24 | `fiveDirOf`/`fiveColorOf`/`fiveSeasonOf`/`fiveObjectOf` | 木/火/水/金/未知 | 东方/红/冬/金；未知→null |
| B10-25 | `remedyLine` | 木/金/null/未知 | 含东方/含西方/""/"" |
| B10-26 | `xiangToNumber` | null/空/空白/无匹配 | 1/1/1/1 |
| B10-27 | `dirToTrigram` | 东南/西南/东北/西北/东/南/西/北/中央/null | 5/8/7/1/4/3/2/6/1/1 |
| B10-28 | `dirFromTextOrDefault` | null/空/无方位/东方 | fallback/fallback/fallback/4 |

---

## 7. 覆盖矩阵

### 7.1 流程节点覆盖

| BPMN 节点 id | 覆盖用例 | 说明 |
|---|---|---|
| `Start_01` | 全部端到端用例 | 启动默认变量 |
| `Task_PreceptCheck` | §6.2 P-1…B-2,N-1…N-3；§6.3 全部 | 四不占三分支 |
| `Task_PreceptAlert` | §6.2 N-1…N-3；§6.3 N-1 | 告警语映射 |
| `Task_Route` | S1–S5 全部 | 17 类路由/体系判定 |
| `Task_CharSplit` | §6.2 P-2；§6.3 P-2 | 字占分支 |
| `Task_Cast` | §6.2 P-1…B-1；§6.3 P-1 | 卦以八除/爻以六除 |
| `Task_BodyUse` | §6.2 公共尾链 | 动爻判体用 |
| `Task_Mutual` | §6.2 公共尾链 | 中间四爻；乾坤无互 |
| `Task_Changed` | §6.2 公共尾链 | 动爻反转 |
| `Task_ShengKe` | §6.2 公共尾链；§6.3 P-1 | 5 级生克 |
| `Task_Vitality` | §6.2 B-1；§6.3 B-1 | 显式 season / 农历月兜底 |
| `Task_ImageSymbol` | §6.2 公共尾链 | 类象取象 |
| `Task_Categorize` | §6.2 公共尾链 | 分类占断 |
| `Task_Omen` | §6.2 P-3/B-1；§6.3 P-3 | 外应非空/为空 |
| `Task_Judge` | §6.2 公共尾链；§6.3 P-1 | 五级吉凶 + 过程描述 |
| `Task_Response` | §6.2 公共尾链；§6.3 B-1 | 应期级别/范围 |
| `Task_Advice` | §6.2 公共尾链/B-2；离线反射 `PlumBlossomBreakdownTest` | 修身建议＋破解方案（凶/平三法·吉不设） |
| `Task_SendAlert` | §6.1 P-1/P-2；§6.2 B-2；§6.3 P-4 | 大吉/大凶外发 |
| `Task_Summary` | 全部端到端用例 | 汇总结果＋产出 `divinationFactSheet`（占断依据结构表） |
| `Task_Interpret` | §6.2/§6.3 公共尾链；离线反射 `PlumBlossomInterpretPromptTest`（含单源复用） | 占断事实包（`buildDivinationFactSheet`）含所问之事/体用五行方位旺衰/破解方案；`divinationFactSheet` 已有则原样复用；`ChatAgent` 未装配→空串降级、流程收敛 |
| `Task_Dictum` | §6.2/§6.3 公共尾链 | OBDA 未就绪→空串降级、流程收敛 |
| `Gateway_Precept` | §6.2 N-* vs P-* | passPrecept 分叉 |
| `Gateway_Char` | §6.2 P-2 vs P-1/P-3/P-4 | isCharacter 分叉 |
| `Gateway_Level` | §6.2 B-2 vs 普通 | 大吉/大凶 vs 普通 |
| `End_01` | 全部用例 | 收敛 |

### 7.2 本体约束覆盖

| 约束（`owl:Restriction` / SWRL / 域知识） | 覆盖用例 |
|---|---|
| `div:Precept_*`（四不占） | §6.2 N-1…N-3；§6.3 N-1 |
| `yijing:Seq_*`（六十四卦名录） | §6.4 P-2 |
| `core:*Trigram`（八卦五行/方位） | §6.4 P-3 |
| `rel:FivePhase`（五行生克） | §6.3 P-1（`shengKeType`） |
| `rel:VitalityState` / `cal:season` | §6.3 B-1 |
| `jud:Auspiciousness`（五级） | §6.4 P-5；§6.3 P-1 |
| `jud:ResponsePeriod` | §6.3 B-1 |
| `owl:Restriction` / SWRL R1~R14 | §6.4 B-1 |

### 7.3 边界场景清单

| # | 边界/负向场景 | 用例 | 预期行为 |
|---|---|---|---|
| 1 | subject 空白（无事） | §6.2 N-1；§6.3 N-1 | 不进起卦链，转戒律告警后结束 |
| 2 | 未承诺（不诚） | §6.2 N-2 | 转戒律告警后结束 |
| 3 | 同会话重复 subject（重卦） | §6.2 N-3 | 第 2 次转戒律告警 |
| 4 | `season` 缺省 | §6.2 B-1；§6.3 B-1 | 由农历月推导旺衰 |
| 5 | `omenDesc` 为空 | §6.2 B-1 | `omenVerdict=""`，不抛异常 |
| 6 | `motion` 缺省 | §6.2 B-1；§6.3 B-1 | 应期以「坐」（迟）兜底 |
| 7 | 动爻边界（余 0 取 6；钳制 1..6） | §6.3 P-1（动爻∈1..6） | 体用判定稳定 |
| 8 | 乾坤纯卦无互 | §6.2 公共尾链（P-1 数据） | 互卦取其变卦 |
| 9 | 大吉/大凶分支 | §6.2 B-2；§6.3 P-4 | 经 `Task_SendAlert` 后仍收敛到 `End_01` |
| 10 | 起卦法路由兜底 | S1–S5 覆盖 | 以时刻数兜底，不抛异常 |
| 11 | 非告警路由键不投递 | §6.1 B-1 | 队列无消息 |

### 7.4 核心算理纯函数覆盖（§6.6/§6.7/§6.8/§6.9/§6.10 分支矩阵）

> 下列私有纯函数此前未被系统覆盖（端到端用例仅取「值级」结果，未逐分支），由五个分支矩阵类以反射逐分支断言，`new` 实例离线即可运行。

| 算理域 | 覆盖的私有方法 | 用例（§6.6~§6.9） |
|---|---|---|
| 起卦模数 | `mod8`、`mod6`、`decodeStartNumbers` | A1/A2/A8 |
| 八卦爻位与重卦 | `bits`、`trigramOfBits`、`reconstructLines`、`composeHexagram`、`flip`、`pairOf`、`triNum` | A3–A7、A9 |
| 五行生克 | `generates`、`overcomes`、`generatorOf`、`overcomerOf`、`mediatorBetween` | B1/B2/B10 |
| 卦气旺衰 | `seasonFive`、`vitalityOf`、`seasonFromLunarMonth` | B3/B4/B5 |
| 干支数 | `hourNum`、`yearBranchNum` | B6/B7 |
| 五行属性 | `fivePhaseOf`、`fiveDirOf`、`fiveColorOf`、`fiveSeasonOf`、`fiveObjectOf`、`remedyLine` | B8/B9、E4 |
| 体用五级与吉凶 | `shengKeLevel`、`shengKeName`、`shengKeLabel`、`parseShengKeLevel`、`scoreToLevel` | C1–C4 |
| 对体生克 | `relationToBody`、`relationToBodyHex` | C5/C6 |
| 应期 | `responsePeriodOf` | D1/D2 |
| 十应分类与取象 | `tenResponseCategory`、`isMovingOmen`、`stillObjectDirectVerdict`、`fivePhaseOfColor`、`soundToTrigram`、`omenToTrigram`、`shapeToTrigram`、`colorToTrigram`、`xiangBeiNote`、`dirToTrigram`、`dirFromTextOrDefault` | E1–E9 |
| 天时占 | `tianShiVerdict` | F1–F3 |
| 起卦法路由取数 | `encodeStartNumbers`、`xiangToNumber`、`firstNumber`、`stroke`、`soundToTrigram` | G1–G13 |
| 分类断语 | `categoryVerdict`、`categoryRulePair`、`birthGenderHint`、`diseaseSpiritDetail`、`MARRIAGE_APPEARANCE`/`SPIRIT_NOTE`/`CATEGORY_RULE` 内置表 | H1–H6 |
| 十应各类之应 | `tenCategoryNote`、`shengKeTone`、`TEN_CATEGORY_NOTE` 内置表 | I1–I6、J1 |
| 断验诸兆 | `zuoDuanNote`/`ZUO_DUAN`、`shiDaYingNote`/`SHI_DA_YING`、`dongXuanNote`/`DONG_XUAN_GE`、`wanWuNote`/`WAN_WU_GE`、`sanYaoLingYingNote`/`SAN_YAO_LING_YING` | K1–K5 |
| 字占取数 | `splitCharacters`（两/单参重载）、`fillTones`、`fillTonesByRhyme`、`charNumberSource`、`splitClauses`、`inferHumanSub`、`stroke`、`toneOf` | L1–L5 |
| 四不占告示 | `preceptAdmonition` | M1 |
| 日应/鸟兽时日 | `riYingNote`/`zhiFive`、`niaoShouShiRiNote` | N1/N2 |
| 加姓/写字之应 | `jiaXingNote`、`chineseNumber`、`xieZiZhiYingNote` | N3/N4 |
| 真生真克 | `zhenShengZhenKeNote` | N5 |
| 疾病占 | `diseaseSpiritNote`/`SPIRIT_NOTE`、`diseaseYaoNote`/`QIAN_KUN_DISEASE_YAO` | N6 |
| 饮食占 | `dietNote`/`DIET_FOOD` | N7 |
| 占卦诀/衰旺/动静 | `zhanGuaJueNote`、`shuaiWangNote`、`dongJingNote` | N8–N10 |
| 卦应/内外动静 | `guaYingNote`/`GUA_YING`、`neiWaiDongJingNote`/`NEI_WAI_DONG_JING` | N11 |
| 一撮金爻诀/体用互变 | `yicuoJinYaoNote`/`YICUOJIN_YAO`、`mutualBodyUseNote` | N12/N13 |
| 相字/照录 getter | `xiangZiNote`、卷之一~五零参 note getter | N14/N15 |
| 卷之一~五照录 getter 全量 | `juanYi*`(5)/`juanEr*`(11)/`juanSan*`(21)/`juanSi*`(8)/`juanWu*`(14)、`sanYaoXuNote`/`sanYaoLingYingGaiLunNote`/`sanYaoLingYingZongLunNote`、`meiHuaYiShuXuNote`、`zhanFaWanFaNote` | P1–P6 |
| 十应/应期总纲 | `tenResponsePrinciple`、`tenResponseJueNote`、`wanWuYingQiNote`、`xianHouTianYingQiNote`、`shiYingMuLunNote`、`zhanBuShiYingJueNote` | P7 |
| 长文本/分派/观物 | `wuZhaiZhanText`、`qiWuZhanText`、`bianGuaShiText`、`objectDaikaoText`、`guanMeiShuJueNote`、`dongXuanYinBaNote`、`tuXiangBuLuNote`、`xiangZiDigestText` | P8–P10 |
| 解读规则串/小整数/诸论 | `interpretTailorRules`、`interpretPlainTailorRules`、`chineseNumber`、`zhanBuKeYingJueNote`、`yiLunNote`、`guaDuanYiLunNote`、`baGuaXinYiTiYongJueNote` | P11–P13 |

### 7.5 JobWorker handler 层覆盖（§6.11，mock Camunda 驱动）

> 此前 handler 层为空白区（`src/test` 无一触及 20 个 `@JobWorker`）；§6.11 以 Mockito 逐节点驱动，覆盖 `completeResult`/`fail` 两条终态路径。

| 环节 | 覆盖的 handler | 用例（§6.11） |
|---|---|---|
| 前置校验 | `handlePreceptCheck`、`handlePreceptAlert` | B6-01~05 |
| 路由与起卦 | `handleRouteDivination`、`handleCharacterSplit`、`handleCastHexagram` | B6-06~10 |
| 卦象推演 | `handleDetermineBodyUse`、`handleDeriveMutualHexagram`、`handleDeriveChangedHexagram` | B6-11~14 |
| 生克旺衰 | `handleAnalyzeShengKe`、`handleAnalyzeVitality` | B6-15~18 |
| 类象与分类 | `handleExtractImageSymbol`、`handleCategorizeDivination`、`handleAnalyzeOmen` | B6-19~25 |
| 断验与建议 | `handleJudgeAuspiciousness`、`handleDetermineResponsePeriod`、`handleGenerateAdvice` | B6-26~28 |
| 收尾 | `handleSendAlert`、`handleAggregateResult`、`handleInterpretResult`、`handleFetchDictum` | B6-29~32 |

### 7.6 纯算理/文本 helper 反射补覆盖（§6.12）

> §7.4 已覆盖大部分纯函数；§6.12 以反射直调补齐 JaCoCo 报告中仍残留的 helper 分支，重点为 `tenCategoryNote` 诸类之应、`categoryVerdict` 十七类目吉/凶双断与分项细则、`objectDivinationText` 体用五关系/器物占/四季趣时、`imageSymbolText` 各类取象、方位·形色·声音取卦数、`scoreToLevel`/`breakdownNote`/`mutualBodyUseNote`/`shuaiWangNote` 等吉凶与破解链、`yicuoJinYaoNote`/`stroke`/相字/图象补录、字占取数 `splitCharacters` 多档。

| 数据维度 | 覆盖 helper | 用例（§6.12） |
|---|---|---|
| 十应诸类 | `tenCategoryNote`、`shengKeTone` | B7-01~05 |
| 十七类目吉凶 | `categoryVerdict`、`categoryRulePair`、`birthGenderHint`、`diseaseSpiritDetail`、`dietNote`、`lostItemDetail`、`travelDetail`、`travelerDetail`、`fameDetail`、`wealthDetail` | B7-06~07 |
| 占物类断法 | `objectDivinationText`、`isObjectDivination`、`allTrigramsMulti`、`yinYangCount` | B7-08~11 |
| 万物类象 | `imageSymbolText`、`categorySymbolHint` | B7-12~13 |
| 方位/形色声象 | `dirToTrigram`、`dirFromTextOrDefault`、`shapeToTrigram`、`colorToTrigram`、`soundToTrigram`、`xiangToNumber`、`fivePhaseOfColor`、`stillObjectDirectVerdict`、`xiangBeiNote` | B7-14~19 |
| 吉凶/生克/破解 | `scoreToLevel`、`relationToBodyHex`、`breakdownNote`、`mutualBodyUseNote`、`shuaiWangNote`、`zhanGuaJueNote`、`bodyRelationNotes` | B7-20~25 |
| 爻诀/笔画/相字/图象/起卦断 | `yicuoJinYaoNote`、`stroke`、`xiangZiCounts`、`xiangZiRule`、`tuXiangBuLu`、`castMethodVerdictText` | B7-26~30 |
| 字占/应期/属性 | `splitCharacters`、`charNumberSource`、`tenResponseJueSummary`、`fivePhaseOf`、`trigramNumberOf` | B7-31~35 |

**JaCoCo 覆盖率（`PlumBlossomOntologyJobWorker`，离线合集全跑后 `target/site/jacoco/jacoco.csv`）**：

| 指标 | 覆盖率 |
|---|---|
| 指令 Instruction | **84.2%**（19040/22625） |
| 分支 Branch | **60.1%**（1276/2122） |
| 行 Line | **83.2%**（3249/3907） |
| 方法 Method | **91.4%**（254/278） |

> `prepare-agent` 挂探针、`report`（`test` 阶段后）出 HTML/XML/CSV 报告至 `target/site/jacoco`。未覆盖分支主要为需真实 OBDA/RabbitMQ 的 `fetchDictum` 单值查询、`sendAlert` 大吉/大凶外发、以及部分仅外部数据才走的防御兜底。

---

### 7.7 Web 控制层 + 残余纯 helper 补覆盖（§6.13）

> §7.1~§7.6 聚焦 BPMN 节点、本体约束、算理纯函数与 JobWorker handler；§6.13 首次将 **`web/PlumBlossomController`**（此前覆盖率为 0）纳入覆盖，并以反射补齐残余 helper 装配/分派分支。

**（1）Controller standalone MockMvc（3 端点 · 4 用例，`CamundaClient=null` 走离线降级）**

| 端点 | `PlumBlossomController` 方法 | 覆盖用例 | 分支 / 断言 |
|---|---|---|---|
| `POST /api/plumblossom/divination` | `startDivination` | B8-01 / B8-02 | subject 空/空白→`400`＋`error="subject 不能为空"`；引擎 null（`start` 抛异常）→`503`＋`error` 非空 |
| `GET /api/plumblossom/divination/{key}` | `getDivinationStatus` | B8-03 | 引擎 null→`fetchInstance` 捕获异常返 null→`status="RUNNING"`、`variables` 空表、`200` |
| `GET /api/plumblossom/health` | `health` | B8-04 | `engineAvailable = (client != null)`→false；`processId` 常量；`bpmnPath`（反射预设） |

**（2）残余纯 helper 反射补覆盖**

| 数据维度 | 覆盖 helper | 用例（§6.13） |
|---|---|---|
| 外置文本回写装配 | `applyAppendixFullText`（null/空表/值 null/短数组/无同名字段/非 String） | B8-05 |
| 五卷正文回写装配 | `applyJuanTextOverrides`（null/空表/非 `JUAN_` 前缀/字段不存在/文本空） | B8-06 |
| 字占取数多档 | `splitCharacters`（5/6/7/8/9/10 字） | B8-07 |
| 占物类断法空体用 | `objectDivinationText`（体用皆空） | B8-08 |
| 附录 getter | `appendixDoctrineNote`、`appendixTextNote`、`appendixTextCount` | B8-09 |
| 占物备考 / 八卦诀计数 | `objectDaikaoText`、`baGuaJueCounts`（八宫 × 六十四卦 = `{8,64}`） | B8-10 |
| 诸论 getter | `yiLunNote`、`zhanBuKeYingJueNote` | B8-11 |

**JaCoCo 覆盖率（v1.9，离线合集全跑后 `target/site/jacoco/jacoco.csv`）**：

| 类 | 指令 Instruction | 分支 Branch | 行 Line | 方法 Method |
|---|---|---|---|---|
| `PlumBlossomOntologyJobWorker` | **86.3%**（19515/22625） | **64.3%**（1364/2122） | **85.4%**（3336/3907） | **95.0%**（264/278） |
| `PlumBlossomController` | **46.1%**（147/319） | **61.1%**（11/18） | **58.6%**（34/58） | **100%**（7/7） |

> 对比 v1.8：JobWorker 指令 85.3%/分支 62.5%/行 84.6%/方法 93.9%；Controller 由 0% 首次纳入并跃升至方法覆盖 100%。Controller 剩余未覆盖主要为 `startDivination` 引擎成功路径（需构造可用 `CamundaClient`）与 `getDivinationStatus` 的 COMPLETED 分支。

---

### 7.8 Web 控制层引擎成功路径补覆盖（§6.14，mock CamundaClient）

> §7.7 以 `CamundaClient=null` 覆盖了 Controller 降级路径，遗留引擎成功/完成/异常状态主路径。§6.14 以 **mock `CamundaClient` fluent 客户端**驱动 `ProcessOrchestrator`（`start`/`fetchInstance`/`readVariables`/`deployIfAbsent`）返回预设结果，离线覆盖 Controller 全部主路径分支。

**（1）POST /divination（startDivination）**

| 分支 | 覆盖用例 | 断言 |
|---|---|---|
| 部署+启动成功返回体 | B9-01 | 200；`instanceKey/processId/subject` |
| body 中 null 值过滤 | B9-01 | 不进入 variables |
| deployIfAbsent 失败（吞异常）仍续跑 | B9-02 | 200（warn 不中断） |
| start 抛异常（有/无消息） | B9-03 / B9-13 | 503；`error` 非空 / `"未知错误"` |
| body=null | B9-12 | 400 |

**（2）GET /divination/{key}（getDivinationStatus）**

| 状态分支 | 覆盖用例 | 断言 |
|---|---|---|
| COMPLETED 14 字段全回填 | B9-04 | 14 字段均取变量值 |
| COMPLETED 缺字段回退 `""` | B9-05 | 缺失字段 `""` |
| TERMINATED | B9-06 | `status=TERMINATED`、无 14 字段 |
| INCIDENT（ACTIVE+incident） | B9-07 | `status=INCIDENT` |
| RUNNING（ACTIVE 无 incident） | B9-08 | `status=RUNNING` |
| fetchInstance 返 null | B9-10 | `status=RUNNING` |
| readVariables 抛异常降级 | B9-09 | `variables` 空、仍 200 |

**（3）GET /health**：引擎在位 `engineAvailable=true`（B9-11）。

**JaCoCo 覆盖率（v2.0，离线合集全跑后 `target/site/jacoco/jacoco.csv`）**：

| 类 | 指令 Instruction | 分支 Branch | 行 Line | 方法 Method |
|---|---|---|---|---|
| `PlumBlossomOntologyJobWorker` | **86.3%**（19515/22625） | **64.3%**（1364/2122） | **85.4%**（3336/3907） | **95.0%**（264/278） |
| `PlumBlossomController` | **95.6%**（305/319） | **100.0%**（18/18） | **94.8%**（55/58） | **100%**（7/7） |

> 对比 v1.9：Controller 指令 46.1%→**95.6%**、分支 61.1%→**100%**、行 58.6%→**94.8%**；JobWorker 维持不变（本批仅新增 Controller 用例）。Controller 剩余未覆盖仅 `getDivinationStatus` 的 `catch(Exception)` 503 兜底块（line 100-102）——因 `ProcessOrchestrator.fetchInstance`/`readVariables` 内部均已 `catch(Exception)` 自吞且不抛受检异常，该外层兜底在正常编排下**防御性不可达**。

### 7.9 纯工具/纯计算 helper 反射补覆盖（§6.15）

> 承接第九批 JaCoCo 分析：JobWorker 剩余**完全未覆盖**方法除环境块外，尚有解析/取整/查表等纯工具方法；本批以反射直调逐档补齐，`new PlumBlossomOntologyJobWorker()` 离线即跑、不用 Mockito。

**（1）零覆盖方法由 0→全覆（5 个）**

| 方法 | 分支 | 覆盖用例 |
|---|---|---|
| `parseIntOrNull` | null/空/空白/合法/非法 | B10-01 |
| `iriLocal` | null/含`/`或`#`/无分隔 | B10-02 |
| `toneName` | 1~4/default | B10-03 |
| `putIfInt` | key/value null/空白/非法整数/合法 trim | B10-04 |
| `afterBackendServiceReady` | OBDA 未就绪安全降级 | B10-05 |

**（2）纯算理/查表方法全档（23 个）**：`shapeToTrigram`/`colorToTrigram`/`soundToTrigram`/`stillObjectDirectVerdict`/`firstNumber`/`cnDigit`/`parseChineseNumber`/`tenResponseCategory`/`shengKeName`/`shengKeLabel`/`parseShengKeLevel`/`scoreToLevel`/`fivePhaseOfColor`/`generates`/`overcomes`/`seasonFive`/`vitalityOf`/`generatorOf`/`overcomerOf`/`mediatorBetween`/`fiveDirOf`/`fiveColorOf`/`fiveSeasonOf`/`fiveObjectOf`/`remedyLine`/`xiangToNumber`/`dirToTrigram`/`dirFromTextOrDefault`（B10-06~B10-28）。

**（3）剩余完全未覆盖方法（9 个，均环境依赖·离线不可达）**：`lambda$loadOntologyDomain$0/1/2`（需 Ontop OBDA 查询）、`loadDomainRelations`/`loadTrigramKnowledge`/`verifyOntologyKnowledge`（需 Ontop/Openllet）、`selectDictum`/`selectDictumValue`（需 Ontop OBDA）、`fail`（需 Camunda 客户端失败回调）。纯工具/纯计算类方法已全部覆盖。

**JaCoCo 覆盖率（v2.1，离线合集全跑后 `target/site/jacoco/jacoco.csv`）**：

| 类 | 指令 Instruction | 分支 Branch | 行 Line | 方法 Method |
|---|---|---|---|---|
| `PlumBlossomOntologyJobWorker` | **86.7%**（19622/22625） | **66.1%**（1403/2122） | **86.2%**（3366/3907） | **96.8%**（269/278） |
| `PlumBlossomController` | **95.6%**（305/319） | **100.0%**（18/18） | **94.8%**（55/58） | **100%**（7/7） |

> 对比 v2.0：JobWorker 指令 86.3%→**86.7%**、分支 64.3%→**66.1%**（+1.8pp，+39 分支）、行 85.4%→**86.2%**、方法 95.0%→**96.8%**（+5 方法，完全未覆盖方法由 14→9）；Controller 维持 v2.0（本批未触及）。

### 7.10 真实环境端到端补覆盖（§6.1~§6.3，v2.2）

> 承接 v2.1 结论：JobWorker 剩余 9 个「完全未覆盖、环境依赖」方法离线不可达。本轮**启动真实环境**（MySQL/RabbitMQ/Ontop/Camunda-Zeebe）实跑 pom 中 `excluded` 的 3 个环境类，并把 `PlumBlossomDomainLoadIntegrationTest` 指向**本应用专属 Ontop（8089）**实跑，将其中 5 个方法由 0→全覆。

**（1）环境实测结果（全部全绿）**

| 环境类 | 依赖 | 实跑结果 |
|---|---|---|
| `PlumBlossomSwrlRabbitMqTest`（§6.1） | RabbitMQ 5672 | **3/3 通过** |
| `PlumBlossomBpmnInMemoryTest`（§6.2） | Zeebe 内存引擎 + MySQL(OBDA) + RabbitMQ | **9/9 通过**（生产 Worker 全链路 14 节点） |
| `PlumBlossomBpmnRealEngineTest`（§6.3） | 真实 Zeebe 26500 / Camunda REST 9080 + RabbitMQ | **6/6 通过** |
| `PlumBlossomDomainLoadIntegrationTest`（§4.3，开关型） | 专属 Ontop 8089 + MySQL | **1/1 通过**，域知识装载 **ready=true**、八卦先天数/五行/方位 **8/8** 逐例一致、附录 203/坐端 7/十应 10/卦应 8/域知识 31 |
| **合计** | — | **19/19 通过** |

**（2）环境依赖方法由 0→全覆（5 个）**

| 方法 | 覆盖路径 |
|---|---|
| `loadDomainRelations` | 内存引擎/真实引擎上下文启动 + DomainLoad 集成（OBDA 关系域装载） |
| `loadTrigramKnowledge` | 同上（OBDA 卦知识装载） |
| `verifyOntologyKnowledge` | 同上（本体一致性校验） |
| `selectDictum` | 端到端 `fetchDictum` 节点（卦辞/爻辞取值） |
| `selectDictumValue` | 同上（卦辞/爻辞具体取值分支） |
| `lambda$loadOntologyDomain$0/1/2` | DomainLoad 集成在 8089 正确端点下真实返回行，激活 `dir.forEach`/`xiangZi.computeIfAbsent`/`lore.computeIfAbsent` 三处 lambda |

**（3）唯一剩余完全未覆盖方法（1 个）**：`fail`（32 指令，missed=32）——Camunda 客户端失败回调（`newThrowErrorCommand`），仅在 JobWorker 异常兜底路径触发；覆盖需向 `ActivatedJob`/`JobClient` 注入失败并桩接 `ThrowErrorCommandStep*` 链，属「无 Mockito」约定禁用的手段，且正常编排不可达，故**有意保留为防御性残留**。

**JaCoCo 覆盖率（v2.2，离线合集 + 3 环境类 + DomainLoad 集成 累计 `target/jacoco.exec`）**：

| 类 | 指令 Instruction | 分支 Branch | 行 Line | 方法 Method |
|---|---|---|---|---|
| `PlumBlossomOntologyJobWorker` | **94.1%**（21298/22625） | **76.5%**（1624/2122） | **93.6%**（3655/3907） | **99.6%**（277/278） |
| `PlumBlossomController` | **95.6%**（305/319） | **100.0%**（18/18） | **94.8%**（55/58） | **100%**（7/7） |

> 对比 v2.1：JobWorker 指令 86.7%→**94.1%**（+7.4pp）、分支 66.1%→**76.5%**（+10.4pp，+221 分支）、行 86.2%→**93.6%**、方法 96.8%→**99.6%**（完全未覆盖方法 **9→1**，仅存 `fail`）；Controller 不变（未触及）。

---

## 8. 执行方式

- 全套入口：`AllPlumBlossomSuiteTest`（`@Suite` + `@SelectClasses`，聚合 6.1–6.5 五类）。
- 命令：`mvn -pl OntologyFrameworkPlumBlossom test`（离线 `-o`；或 reactor 全量）。
- 命令行等价写法：`mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkPlumBlossom -am test -Dtest=AllPlumBlossomSuiteTest`。
- **环境依赖项**：RabbitMQ（集成/内存引擎 `sendAlert`）、Camunda REST 9080 / Zeebe 26500（真实引擎）——未就绪时相关类经 `ServiceProbe.assume*Reachable` **跳过**（不失败）；纯 OWL/Jena 用例可离线跑。
- **回归基线**：本体合规 6 项 + SKOS 6 项离线**全通过**（第 13/14 步后 SKOS 已生效并扩充至 6 项）；3 个环境类在环境未就绪时跳过，环境就绪后应全绿。
- **第 14 步回归结果（2026-10-20）**：`./mvnw -pl OntologyFrameworkPlumBlossom test -o` → `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（合规 6 + SKOS 6 + 环境类 12 项因环境未就绪跳过，`AllPlumBlossomSuiteTest` 汇总同口径）。日志 `HWCodeArtsDir/mvn_step14.log`。
- **覆盖率采集（v1.7 起）**：`mvn -o -f OntologyFrameworkPlumBlossom/pom.xml test` 已接入 `jacoco-maven-plugin`（0.8.16，`prepare-agent` + `report`），`test` 阶段后于 `target/site/jacoco/`（`index.html` / `jacoco.csv` / `jacoco.xml`）出覆盖率报告。
- **离线合集回归结果（2026-11-18，v1.7）**：`Tests run: 250, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（JDK 27）——BranchMatrix 27 + Batch2 31 + Batch3 18 + Batch4 15 + Batch5 13 + Batch6 32 + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + SKOS 6。`AllPlumBlossomSuiteTest` 已追加 `PlumBlossomBranchMatrixBatch6Test`。
- **离线合集回归结果（2026-11-18，v1.8）**：排除 3 个需 RabbitMQ/Camunda 的环境类后，`Tests run: 286, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`（JDK 27）——BranchMatrix 27 + Batch2 31 + Batch3 18 + Batch4 15 + Batch5 13 + Batch6 32 + **Batch7 35** + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + SKOS 6（DomainLoadIntegration 依环境跳过 1 项）。`AllPlumBlossomSuiteTest` 已追加 `PlumBlossomBranchMatrixBatch7Test`。**JaCoCo 覆盖率（`PlumBlossomOntologyJobWorker`）**：指令 **85.3%**（19295/22625）、分支 **62.5%**（1326/2122）、行 **84.6%**（3306/3907）、方法 **93.9%**（261/278）；较 v1.7（分支 60.1%）分支覆盖提升约 2.4 个百分点、方法覆盖升至 93.9%。
- **离线合集回归结果（2026-11-18，v1.9）**：排除 3 个需 RabbitMQ/Camunda 的环境类后，`Tests run: 297, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`（JDK 27）——BranchMatrix 27 + Batch2 31 + Batch3 18 + Batch4 15 + Batch5 13 + Batch6 32 + Batch7 35 + **Batch8 11** + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + SKOS 6（DomainLoadIntegration 依环境跳过 1 项）。`AllPlumBlossomSuiteTest` 已追加 `PlumBlossomBranchMatrixBatch8Test`。**JaCoCo 覆盖率**：`PlumBlossomOntologyJobWorker` 指令 **86.3%**（19515/22625）、分支 **64.3%**（1364/2122）、行 **85.4%**（3336/3907）、方法 **95.0%**（264/278）；`PlumBlossomController` 指令 **46.1%**（147/319）、分支 **61.1%**（11/18）、行 **58.6%**（34/58）、方法 **100%**（7/7，首次纳入覆盖）。
- **离线合集回归结果（2026-11-18，v2.0）**：排除 3 个需 RabbitMQ/Camunda 的环境类后，`Tests run: 310, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`（JDK 27）——BranchMatrix 27 + Batch2 31 + Batch3 18 + Batch4 15 + Batch5 13 + Batch6 32 + Batch7 35 + Batch8 11 + **Batch9 13** + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + SKOS 6（DomainLoadIntegration 依环境跳过 1 项）。`AllPlumBlossomSuiteTest` 已追加 `PlumBlossomBranchMatrixBatch9Test`。**JaCoCo 覆盖率**：`PlumBlossomOntologyJobWorker` 指令 **86.3%**、分支 **64.3%**、行 **85.4%**、方法 **95.0%**（维持 v1.9）；`PlumBlossomController` 指令 **95.6%**（305/319）、分支 **100%**（18/18）、行 **94.8%**（55/58）、方法 **100%**（7/7）——较 v1.9 大幅跃升（分支 61.1%→100%），唯 `getDivinationStatus` 防御性 503 兜底块（line 100-102）在正常编排下不可达。
- **离线合集回归结果（2026-11-18，v2.1）**：排除 3 个需 RabbitMQ/Camunda 的环境类后，`Tests run: 338, Failures: 0, Errors: 0, Skipped: 1`、`BUILD SUCCESS`（JDK 27）——BranchMatrix 27 + Batch2 31 + Batch3 18 + Batch4 15 + Batch5 13 + Batch6 32 + Batch7 35 + Batch8 11 + Batch9 13 + **Batch10 28** + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + SKOS 6（DomainLoadIntegration 依环境跳过 1 项）。`AllPlumBlossomSuiteTest` 已追加 `PlumBlossomBranchMatrixBatch10Test`。**JaCoCo 覆盖率**：`PlumBlossomOntologyJobWorker` 指令 **86.7%**（19622/22625）、分支 **66.1%**（1403/2122）、行 **86.2%**（3366/3907）、方法 **96.8%**（269/278，完全未覆盖方法由 14→9，剩余 9 个均环境依赖）；`PlumBlossomController` 维持 v2.0（指令 95.6%、分支 100%、行 94.8%、方法 100%）。
- **真实环境端到端回归结果（2026-11-18，v2.2）**：启动真实环境（MySQL 3306 / RabbitMQ 5672 / Ontop 8080 为他应用实例 + 本应用专属 Ontop 8089 / Camunda REST 9080 / Zeebe 26500）后，实跑 3 个 excluded 环境类：`PlumBlossomSwrlRabbitMqTest` 3/3、`PlumBlossomBpmnInMemoryTest` 9/9、`PlumBlossomBpmnRealEngineTest` 6/6（合计 18/18 全绿）；另将 `PlumBlossomDomainLoadIntegrationTest` 指向专属 Ontop -Dplumblossom.it.endpoint=http://localhost:8089/sparql 实跑 1/1，域知识 ready=true、八卦先天数/五行/方位 8/8 逐例一致（建库 plumblossomdb 并灌 data.sql 后）。JaCoCo 覆盖率（离线+环境+DomainLoad 累计）：`PlumBlossomOntologyJobWorker` 指令 94.1%（21298/22625）、分支 76.5%（1624/2122）、行 93.6%（3655/3907）、方法 99.6%（277/278，完全未覆盖方法 9→1，仅存 `fail` 错误回调）；`PlumBlossomController` 维持（指令 95.6%、分支 100%、行 94.8%、方法 100%）。

---

## 9. 待确认项（结论）

| # | 问题 | 结论 |
|---|---|---|
| 1 | 覆盖粒度 | **全链用例 + 专项边界**（已确认）：以端到端全链用例覆盖多节点，另设专项边界/负向用例 |
| 2 | OBDA/MySQL 用例 | **暂不纳入**，待第 11 步建库后增补 |
| 3 | SKOS 用例 | 本步固化；第 13 步词汇表产出后**已生效**，第 14 步扩充为 6 项（结构 3 + 与本体双向交叉 3）全过；未产出时整体跳过 |
| 4 | 大吉/大凶构造输入 | 以算法口径自然构造（S1 数据），断言「仍收敛」而非锁定等级 |

---

## 闸门⑤留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-20 | v1.0 | 邵康节 | **闸门⑤通过**：确认测试设计文档，准予固化测试用例；确认覆盖粒度采用「**全链用例 + 专项边界**」；OBDA/MySQL 用例留待第 11 步；SKOS 用例待第 13 步词汇表产出后生效 |

---

## 第 14 步回归留痕（词汇表测试 + 全流程测试）
| 日期 | 版本 | 执行 | 结论 |
|---|---|---|---|
| 2026-10-20 | v1.2 | 邵康节 / CodeArts | **第 14 步完成**：`PlumBlossomSkosTerminologyTest` 由 3 项扩充为 **6 项**（新增 X-1/X-2 与本体双向交叉校验、S-1 同义词校验）；离线回归 `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`；本体合规 6 + SKOS 6 全绿，环境类按 `ServiceProbe` 跳过。SKILL 第 14 步验收项（词汇表测试通过 + `AllPlumBlossomSuiteTest` 全绿 + 构建链可复现）均已满足 |

---

## 核心算理分支矩阵留痕（2026-11-18）
| 日期 | 版本 | 执行 | 结论 |
|---|---|---|---|
| 2026-11-18 | v1.3 | CodeArts | **新增核心算理分支矩阵 58 项**：`PlumBlossomBranchMatrixTest`（27 项，§6.6）+ `PlumBlossomBranchMatrixBatch2Test`（31 项，§6.7），纯反射离线，`new` 实例即可运行、不依赖 MySQL/Ontop/Camunda/Spring/RabbitMQ。覆盖起卦模数、八卦爻位与重卦、五行生克旺衰、体用 5 级与吉凶回映射、对体生克、应期分档、破解取材、五行属性映射、十应分类取象、天时占众寡、起卦法多起例、分类断语吉凶与分项细则——此前端到端仅校验「值级」结果、纯函数分支零覆盖，本轮补齐。`AllPlumBlossomSuiteTest` 已追加 `PlumBlossomCastRuleTest`（原有离线类，此前漏挂）及两个分支矩阵类。**离线合集回归**：`Tests run: 172, Failures: 0, Errors: 0, Skipped: 0`（BranchMatrix 27 + Batch2 31 + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + Skos 6），`BUILD SUCCESS`（JDK 27）。 |
| 2026-11-18 | v1.4 | CodeArts | **新增核心算理分支矩阵·三 18 项**：`PlumBlossomBranchMatrixBatch3Test`（§6.8），纯反射离线、`new` 实例即可运行、不依赖 MySQL/Ontop/Camunda/Spring/RabbitMQ。覆盖十应各类之应 `tenCategoryNote`（天时/地理/人事/时令/方卦/动物/静物/言语/声音逐类）与应体生克语气 `shengKeTone`（比和/逢生/见克/泄气）、向背坐端 `zuoDuanNote`·论事十大应 `shiDaYingNote`·观物洞玄 `dongXuanNote`·万物赋 `wanWuNote`·三要灵应 `sanYaoLingYingNote` 诸兆命中/未命中、字占取数 `splitCharacters` 四档与 `charNumberSource` 六值·`splitClauses` 断句·`inferHumanSub` 子法、四不占告示 `preceptAdmonition` 全文案。`AllPlumBlossomSuiteTest` 已追加该类。**离线合集回归**：`Tests run: 190, Failures: 0, Errors: 0, Skipped: 0`（BranchMatrix 27 + Batch2 31 + Batch3 18 + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + Skos 6），`BUILD SUCCESS`（JDK 27）。 |
| 2026-11-18 | v1.5 | CodeArts | **新增核心算理分支矩阵·四 15 项**：`PlumBlossomBranchMatrixBatch4Test`（§6.9），纯反射离线、`new` 实例即可运行、不依赖 MySQL/Ontop/Camunda/Spring/RabbitMQ。覆盖日应 `riYingNote`（五行五支/空/未知）、鸟兽时日 `niaoShouShiRiNote`、加姓 `jiaXingNote`、写字之应 `xieZiZhiYingNote`、真生真克 `zhenShengZhenKeNote`（金/木/火/兜底）、疾病测鬼神 `diseaseSpiritNote` 与逐爻 `diseaseYaoNote`（天地否标【今动】）、饮食 `dietNote`（坎/兑/坎兑/坎逢坤）、占卦诀 `zhanGuaJueNote`、衰旺 `shuaiWangNote`、动静 `dongJingNote`、卦应 `guaYingNote`/内外动静 `neiWaiDongJingNote`、一撮金爻诀 `yicuoJinYaoNote`（含越界/未知降级）、体用互变 `mutualBodyUseNote`（含乾坤无互）、相字 `xiangZiNote`，并抽样 9 个卷之一~五「照录备考」零参 getter 非空。`AllPlumBlossomSuiteTest` 已追加该类。**离线合集回归**：`Tests run: 205, Failures: 0, Errors: 0, Skipped: 0`（BranchMatrix 27 + Batch2 31 + Batch3 18 + Batch4 15 + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + Skos 6），`BUILD SUCCESS`（JDK 27）。 |
| 2026-11-18 | v1.6 | CodeArts | **新增核心算理分支矩阵·五 13 项**：`PlumBlossomBranchMatrixBatch5Test`（§6.10），纯反射离线、`invoke(null)` 即可运行、不加载本体、不依赖 MySQL/Ontop/Camunda/Spring/RabbitMQ。覆盖卷之一~卷之五「照录备考」零参 note getter **全量前缀**（卷一 5／卷二 11／卷三 21／卷四 8／卷五 14，共 59 篇），三要灵应篇序/导论/总论，十应/应期总纲（十应奥论·占卜十应诀·万物赋应期·先天后天论·十应目论·方应），长文本（屋宅占·器物占·变卦式八则），`objectDaikaoText` 占物类命中/非占物降级，观梅数诀序·观物洞玄歌·图象补录·相字摘要，解读/白话两规则串异同，小整数 `chineseNumber`（0/9/10/15/20/99＋越界回退），占卜克应/遗论/卦断遗论/八卦心易体用诀诸论。`AllPlumBlossomSuiteTest` 已追加该类。**离线合集回归**：`Tests run: 218, Failures: 0, Errors: 0, Skipped: 0`（BranchMatrix 27 + Batch2 31 + Batch3 18 + Batch4 15 + Batch5 13 + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + Skos 6），`BUILD SUCCESS`（JDK 27）。 |
| 2026-11-18 | v1.7 | CodeArts | **新增 JobWorker handler 分支矩阵 32 项**：`PlumBlossomBranchMatrixBatch6Test`（§6.11），以 Mockito mock Camunda `ActivatedJob`/`JobClient`，驱动 `completeResult`（`newCompleteCommand(k).variables(map).send().join()`）与 `fail`（`newThrowErrorCommand(k).errorCode(..).errorMessage(..).send().join()`）两条终态路径，逐 handler 覆盖 **20 个 `@JobWorker`** 节点级分支（四不占校验/告示、起卦法路由、字占拆数、起卦成卦字/数两路、定体用动爻上下、互卦乾坤无互/常卦、变卦、五行生克级、卦气旺衰三路、万物类象常规/占物、分类占断天时/常类/一撮金、参外应静占/有应、断吉凶、定应期、修身建议三支、外发告警跳过、汇总、解读降级、取卦爻辞降级）；纯离线 `new` 实例即跑，不依赖 Spring/Camunda 引擎/RabbitMQ/MySQL。**接入 `jacoco-maven-plugin` 0.8.16**（`prepare-agent`+`report`），`PlumBlossomOntologyJobWorker` 覆盖率：指令 **84.2%**、分支 **60.1%**、行 **83.2%**、方法 **91.4%**。`AllPlumBlossomSuiteTest` 已追加该类。**离线合集回归**：`Tests run: 250, Failures: 0, Errors: 0, Skipped: 0`（BranchMatrix 27 + Batch2 31 + Batch3 18 + Batch4 15 + Batch5 13 + Batch6 32 + CastRule 88 + Breakdown 8 + InterpretPrompt 4 + OntologyCompliance 8 + Skos 6），`BUILD SUCCESS`（JDK 27）。 |
| 2026-11-18 | v1.8 | CodeArts | **新增纯算理/文本 helper 反射矩阵 35 项**：`PlumBlossomBranchMatrixBatch7Test`（§6.12），反射直调私有 helper、`new` 实例离线即跑、不用 Mockito、不依赖 Spring/Camunda/RabbitMQ/MySQL。逐方法铺「逻辑分支 × 数据分支」：`tenCategoryNote` 诸类之应、`categoryVerdict` 十七类目吉/凶双断与分项细则、`objectDivinationText` 体用五关系/器物占/四季趣时/动爻五六、`imageSymbolText` 各类取象、`dirToTrigram`/`dirFromTextOrDefault` 八方、`shape/color/sound/xiang` 取卦数、`fivePhaseOfColor`/`stillObjectDirectVerdict`/`xiangBeiNote`、`scoreToLevel`/`relationToBodyHex`/`breakdownNote`/`mutualBodyUseNote`/`shuaiWangNote`/`zhanGuaJueNote`/`bodyRelationNotes`、`yicuoJinYaoNote`/`stroke`/相字 `xiangZiCounts`·`xiangZiRule`/`tuXiangBuLu`/`castMethodVerdictText`、`splitCharacters`/`charNumberSource`/`tenResponseJueSummary`/`fivePhaseOf`/`trigramNumberOf`。`AllPlumBlossomSuiteTest` 已追加该类。**离线合集回归**：`Tests run: 286, Failures: 0, Errors: 0, Skipped: 1`（含 Batch7 35；DomainLoad 依环境跳过 1），`BUILD SUCCESS`（JDK 27）。**JaCoCo**（`PlumBlossomOntologyJobWorker`）：指令 85.3%、分支 62.5%、行 84.6%、方法 93.9%（分支较 v1.7 的 60.1% 提升约 2.4pp）。 |
| 2026-11-18 | v1.9 | CodeArts | **新增 Web 控制层 + 残余 helper 反射矩阵 11 项**：`PlumBlossomBranchMatrixBatch8Test`（§6.13），两部分——① **Controller standalone MockMvc**（`MockMvcBuilders.standaloneSetup`，`CamundaClient=null` 走离线降级）：subject 空/空白→400、引擎 null→503、状态查询 `fetchInstance` 降级→`RUNNING`/空变量/200、`/health`→`engineAvailable=false`；② **残余纯 helper 反射**：`applyAppendixFullText`/`applyJuanTextOverrides` 装配守卫、`splitCharacters` 5~10 字、`objectDivinationText` 空体用、`appendixDoctrineNote`/`appendixTextNote`/`appendixTextCount`、`objectDaikaoText`/`baGuaJueCounts`、`yiLunNote`/`zhanBuKeYingJueNote`。纯离线、**不用 Mockito**、不依赖 Spring 容器/Camunda/RabbitMQ/MySQL。**踩坑**：`baGuaJueCounts` 实返 `{8, 64}`（八宫 × 六十四卦总数），首跑误断言 `{8,8}` 已修正。`AllPlumBlossomSuiteTest` 已追加该类。**离线合集回归**：`Tests run: 297, Failures: 0, Errors: 0, Skipped: 1`（DomainLoad 依环境跳过 1），`BUILD SUCCESS`（JDK 27）。**JaCoCo**：`PlumBlossomOntologyJobWorker` 指令 86.3%/分支 64.3%/行 85.4%/方法 95.0%（分支较 v1.8 的 62.5% 再升约 1.8pp）；`PlumBlossomController` 指令 46.1%/分支 61.1%/行 58.6%/**方法 100%**（由 0% 首次纳入）。环境类仍为禁区/离线不可达。 |
| 2026-11-18 | v2.0 | CodeArts | **新增 Web 控制层引擎成功路径 13 项**：`PlumBlossomBranchMatrixBatch9Test`（§6.14），以 **Mockito mock `CamundaClient` fluent 客户端**驱动 `ProcessOrchestrator`（`start`/`fetchInstance`/`readVariables`/`deployIfAbsent`）预设返回，离线覆盖 Controller 主路径：`startDivination` 部署+启动成功/部署失败续跑/启动抛异常（有/无消息）/body=null；`getDivinationStatus` 的 COMPLETED 14 字段全回填与缺省回退、TERMINATED/INCIDENT/RUNNING 状态分派、`readVariables` 异常降级、`fetchInstance` 返 null；`health` 引擎在位。**说明**：此为本项目「不用 Mockito」约定的针对性例外（仅 mock 客户端即离线覆盖原需真实引擎的成功路径）。**踩坑**：mock 深链需逐层 stub `filter(Consumer)`/`page(Consumer)` 泛型重载；`readVariables` 分页需 `page().totalItems()` 等于条数方可跳出循环。`AllPlumBlossomSuiteTest` 已追加该类。**离线合集回归**：`Tests run: 310, Failures: 0, Errors: 0, Skipped: 1`（DomainLoad 依环境跳过 1），`BUILD SUCCESS`（JDK 27）。**JaCoCo**：`PlumBlossomController` 指令 46.1%→**95.6%**、分支 61.1%→**100%**、行 58.6%→**94.8%**、方法 100%；`PlumBlossomOntologyJobWorker` 维持 86.3%/64.3%/85.4%/95.0%。Controller 唯余 `getDivinationStatus` 防御性 503 兜底块（line 100-102）在正常编排下不可达。 |
| 2026-11-18 | v2.1 | CodeArts | **新增纯工具/纯计算 helper 反射矩阵 28 项**：`PlumBlossomBranchMatrixBatch10Test`（§6.15），反射直调 JobWorker 私有 helper、`new` 实例离线即跑、**不用 Mockito**、不依赖 Spring/Camunda/RabbitMQ/MySQL。承接第九批 JaCoCo 分析，将 **5 个完全未覆盖方法由 0→全覆**（`parseIntOrNull`/`iriLocal`/`toneName`/`putIfInt`/`afterBackendServiceReady`），并逐档补齐 23 个纯算理/查表方法全分支（`shapeToTrigram`/`colorToTrigram`/`soundToTrigram`/`stillObjectDirectVerdict`/`firstNumber`/`cnDigit`/`parseChineseNumber`/`tenResponseCategory`/`shengKeName`/`shengKeLabel`/`parseShengKeLevel`/`scoreToLevel`/`fivePhaseOfColor`/`generates`/`overcomes`/`seasonFive`/`vitalityOf`/`generatorOf`/`overcomerOf`/`mediatorBetween`/`fiveDirOf`/`fiveColorOf`/`fiveSeasonOf`/`fiveObjectOf`/`remedyLine`/`xiangToNumber`/`dirToTrigram`/`dirFromTextOrDefault`）。`AllPlumBlossomSuiteTest` 已追加该类。**离线合集回归**：`Tests run: 338, Failures: 0, Errors: 0, Skipped: 1`（DomainLoad 依环境跳过 1），`BUILD SUCCESS`（JDK 27）。**JaCoCo**（`PlumBlossomOntologyJobWorker`）：指令 86.7%、分支 **66.1%**（64.3%→+1.8pp，+39 分支）、行 86.2%、方法 **96.8%**（95.0%→+5 方法）；**剩余完全未覆盖方法仅 9 个且均环境依赖**（loadOntologyDomain lambda×3 / loadDomainRelations / loadTrigramKnowledge / verifyOntologyKnowledge / selectDictum / selectDictumValue / fail），纯工具/纯计算方法已全部覆盖。`PlumBlossomController` 维持 v2.0。 |
| 2026-11-18 | v2.2 | CodeArts | **真实环境端到端补覆盖（无新增用例，实跑既有环境类）**：启动真实环境（MySQL 3306 / RabbitMQ 5672 / Camunda REST 9080 / Zeebe 26500 / 本应用专属 Ontop 8089）后，实跑 pom `excluded` 的 3 个环境类——`PlumBlossomSwrlRabbitMqTest` 3/3、`PlumBlossomBpmnInMemoryTest` 9/9、`PlumBlossomBpmnRealEngineTest` 6/6（合计 18/18 全绿）；另建库 `plumblossomdb` 并灌 `createdb_tables.ddl`+`data.sql` 后，将 `PlumBlossomDomainLoadIntegrationTest` 指向专属 Ontop（`-Dplumblossom.it.enabled=true -Dplumblossom.it.endpoint=http://localhost:8089/sparql`）实跑 1/1，域知识 `ready=true`、八卦先天数/五行/方位 **8/8 逐例一致**（附录 203 / 坐端 7 / 十应 10 / 卦应 8 / 域知识 31）。**环境依赖方法由 0→全覆 5 个**（`loadDomainRelations`/`loadTrigramKnowledge`/`verifyOntologyKnowledge`/`selectDictum`/`selectDictumValue`）及 3 个 `lambda$loadOntologyDomain$0/1/2`。**JaCoCo**（离线+环境+DomainLoad 累计）：`PlumBlossomOntologyJobWorker` 指令 86.7%→**94.1%**、分支 66.1%→**76.5%**（+10.4pp，+221 分支）、行 86.2%→**93.6%**、方法 96.8%→**99.6%**（完全未覆盖方法 **9→1**）；`PlumBlossomController` 不变。**唯一剩余未覆盖方法 `fail`**（Camunda 失败回调，需 mock JobClient/ThrowErrorCommandStep 链，违反「无 Mockito」约定，故有意保留）。 |
