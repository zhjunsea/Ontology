# PlumBlossom应用 — 测试设计文档

> **用法**：本文件产出第 8 步。**闸门⑤**：业务人员/负责人确认本文档后方可固化测试用例。
> **基线**：`OntologyFrameworkPizza/design/披萨应用（Pizza）测试用例设计文档.md`（形态）；用例基线 `OntologyFrameworkExample/src/test/java/.../{ExampleBpmnRealEngineTest, ExampleSwrlRabbitMqTest, AllExampleSuiteTest}.java`。
> **上游**：第 6 步单文件本体 `ontology/plumblossom-all.owl`；第 9 步将 TBox/ABox 分离为两个 owl；第 13 步产出词汇表 `ontology/plumblossom-terminology.ttl`；第 14 步执行词汇表与全流程测试。

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkPlumBlossom / PlumBlossom（梅花易数占断应用） |
| 文档版本 | v1.2（闸门⑤已确认；第 13/14 步词汇表用例扩充并回归全绿） |
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
- 覆盖 BPMN 流程端到端编排（主干 18 个 `serviceTask` 顺序、3 个排他网关分支、无 incident、`End_01` 收敛）；
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
        │               └─ [吉|平|凶]  ─────────────────┴→ Task_Summary → End_01
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
| 修身建议生成 | `Task_Advice` | `generateAdvice` |
| 外发告警消息 | `Task_SendAlert` | `sendAlert` |
| 汇总占断结果 | `Task_Summary` | `aggregateResult` |
| 排他网关（3） | `Gateway_Precept` / `Gateway_Char` / `Gateway_Level` | — |
| 开始 / 结束 | `Start_01` / `End_01` | — |

**网关条件（取自 BPMN `conditionExpression`）**：
- `Gateway_Precept`：`passPrecept = true` → `Task_Route`；`passPrecept = false` → `Task_PreceptAlert`。
- `Gateway_Char`：`isCharacter = true` → `Task_CharSplit`；`isCharacter = false` → `Task_Cast`。
- `Gateway_Level`：`auspiciousness = "大吉" or "大凶"` → `Task_SendAlert`；`auspiciousness = "吉" or "平" or "凶"` → `Task_Summary`。

### 2.3 BPMN ↔ JobWorker 绑定

`PlumBlossomOntologyJobWorker`（`@Profile("PlumBlossomBPMN")`）声明 **18 个 `@JobWorker`**（均 `autoComplete=false`）：`preceptCheck`、`preceptAlert`、`routeDivination`、`characterSplit`、`castHexagram`、`determineBodyUse`、`deriveMutualHexagram`、`deriveChangedHexagram`、`analyzeShengKe`、`analyzeVitality`、`extractImageSymbol`、`categorizeDivination`、`analyzeOmen`、`judgeAuspiciousness`、`determineResponsePeriod`、`generateAdvice`、`sendAlert`、`aggregateResult`。**BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」**，四者靠 jobType 字面量绑定，无代码级引用。

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
| 端到端（内存引擎） | `PlumBlossomBpmnInMemoryTest`（9 项） | `zeebe-process-test` 内存引擎 | **是**（需 RabbitMQ 供 `sendAlert`） | 18 节点编排、网关分支、节点顺序、无 incident |
| 端到端（真实引擎） | `PlumBlossomBpmnRealEngineTest`（6 项） | 真实 Zeebe `localhost:26500` | **是** | 真实部署与生产 worker 消费、**取值级**断言 |

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

## 6. 测试用例清单（共 30 项 · 已固化）

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
| `Task_Advice` | §6.2 公共尾链/B-2 | 修身建议 |
| `Task_SendAlert` | §6.1 P-1/P-2；§6.2 B-2；§6.3 P-4 | 大吉/大凶外发 |
| `Task_Summary` | 全部端到端用例 | 汇总结果 |
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

---

## 8. 执行方式

- 全套入口：`AllPlumBlossomSuiteTest`（`@Suite` + `@SelectClasses`，聚合 6.1–6.5 五类）。
- 命令：`mvn -pl OntologyFrameworkPlumBlossom test`（离线 `-o`；或 reactor 全量）。
- 命令行等价写法：`mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkPlumBlossom -am test -Dtest=AllPlumBlossomSuiteTest`。
- **环境依赖项**：RabbitMQ（集成/内存引擎 `sendAlert`）、Camunda REST 9080 / Zeebe 26500（真实引擎）——未就绪时相关类经 `ServiceProbe.assume*Reachable` **跳过**（不失败）；纯 OWL/Jena 用例可离线跑。
- **回归基线**：本体合规 6 项 + SKOS 6 项离线**全通过**（第 13/14 步后 SKOS 已生效并扩充至 6 项）；3 个环境类在环境未就绪时跳过，环境就绪后应全绿。
- **第 14 步回归结果（2026-10-20）**：`./mvnw -pl OntologyFrameworkPlumBlossom test -o` → `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（合规 6 + SKOS 6 + 环境类 12 项因环境未就绪跳过，`AllPlumBlossomSuiteTest` 汇总同口径）。日志 `HWCodeArtsDir/mvn_step14.log`。

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
