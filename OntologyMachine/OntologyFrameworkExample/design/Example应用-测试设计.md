# 本体应用框架示例（Example）— 测试设计

> **用法**：本文件产出**第 8 步**。**闸门⑤**：业务人员/负责人确认本文档后方可固化测试用例。
> **基线（强制，依据实际内容）**：`src/test/java/com/ocean/ontologyframework/example/{AllExampleSuiteTest, ExampleBpmnRealEngineTest, ExampleSwrlRabbitMqTest}.java`、`src/main/resources/application.yaml`、`ontology/bpmn/myPizzaBakingProcess.bpmn`。
> **形态基线**：《LatticeTower应用-测试设计文档》《IndustryInsight应用-测试设计文档》。
> **★ 重要**：本文件以**当前工程实际测试**为准——Example 现有测试类 **2 个**（共 5 个用例）：`ExampleBpmnRealEngineTest`（3）+ `ExampleSwrlRabbitMqTest`（2），由 `AllExampleSuiteTest` 聚合。Example **不含**本体合规测试 / 内存引擎端到端测试（该类用例在 `OntologyFrameworkPizza` 中）。

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkExample / Example（本体应用框架示例） |
| 文档版本 | v1.0 |
| 生成日期 | 2026-11-02 |
| 关联本体 | `ontology/pizza-all.owl` + 4 TBox 文件 + 3 ABox 文件；术语表 `ontology/pizza-terminology.ttl` |
| 关联流程 | 《Example应用-流程设计》；`ontology/bpmn/myPizzaBakingProcess.bpmn`（processId=`PizzaMakingStandardProcess`），Camunda 8 / Zeebe |
| 关联架构 | 《Example应用-应用架构设计》§4 契约表、§5 逐节点实现 |
| 测试代码位置 | `src/test/java/com/ocean/ontologyframework/example/` |
| 测试激活 Profile | `ExampleBPMN`（`ExampleBpmnRealEngineTest`）/ `ExampleJunitTest`（`ExampleSwrlRabbitMqTest`） |
| 测试套件入口 | `AllExampleSuiteTest`（`@Suite @SelectClasses`） |

> 本文档用例的方法名、输入变量、断言口径均取自现有测试源码，未作发明。

---

## 1. 目的与范围

**目的**：明确 Example 应用的质量保证方式——测什么、怎么测、用什么数据、判定依据、如何执行。

**范围**：
- **BPMN 流程端到端（真实引擎）**：三种披萨类型驱动烘焙流程，生产 `ExampleOntologyJobWorker` 消费全部 serviceTask，流程到达 `COMPLETED`，无 incident。
- **SWRL 规则 + RabbitMQ 消息外发（集成）**：低库存组件插入 → SWRL 推导 `LowStock*` → `SwrlRuleTriggerListener` 回调 → 消息经 Exchange/Queue/Binding 完整投递。
- **本体推理链路**：经 `BackendService` 加载 TBox、`OBDAHandler` 初始化、推理器快照 / 恢复（测试隔离）。

**不含**：
- 前端 `static/index.html` 的浏览器交互与渲染（人工 / UAT，不在自动化范围）；
- 平台公共 API（`BackendService` / `OBDAHandler` / `ProcessOrchestrator`）自身单元测试（属基础库模块）；
- 本体合规（可解析性 / 一致性 / 结构计数）测试——**Example 当前无该测试类**（如需可复用 Pizza 模块形态新建，见 §9）。

---

## 2. 被测系统概述

### 2.1 业务链路

```
Start_01
  → [DoughMix] → [DoughFerment] → [CrustForm]           （工艺三段：查本体工序耗时）
  → [GetSauce] → ◇ Gateway_SauceChoice ─┬─ 白酱 → [WhiteSauce] ┐
                                         └─ 番茄红酱 → [TomatoSauce] ┤
  → ◇ Gateway_SauceMerge ◀──────────────────────────────────────┘
  → [GetCheese] → ◇ Gateway_CheeseChoice ─┬─ 是 → [BaseCheese] ┐
                                           └─ 否 ───────────────┤
  → [GetTopping] → ◇ Gateway_0xkqhps ─→ [ToppingSpread]
  → [GetTopCheese] → ◇ Gateway_TopCheeseChoice ─┬─ 有封层 → [TopCheese] ┐
                                                 └─ 否 ─────────────────┤
  → ◇ Gateway_TopCheeseMerge ◀──────────────────────────────────────────┘
  → [Bake] → [Decorate] → [Finish] → End_01
```
> 15 个 serviceTask + 6 个排他网关；分支由 `matchedWord`（标签匹配结果）与流程变量驱动。

### 2.2 BPMN 元素 ID 与 jobType

| 环节 | 元素 ID | jobType / 类型 |
|---|---|---|
| 开始事件 | `Start_01` | startEvent |
| 面团原料准备与揉制 | `Task_DoughMix` | `query-property-value-ontology` |
| 面团分割排气与二次醒发 | `Task_DoughFerment` | `query-property-value-ontology` |
| 饼底手工成型 | `Task_CrustForm` | `query-property-value-ontology` |
| 获取酱汁 | `Task_GetSauce` | `get-component` |
| 酱汁类型 | `Gateway_SauceChoice` | exclusiveGateway |
| 铺番茄红酱 / 铺白酱 | `Task_TomatoSauce` / `Task_WhiteSauce` | `query-property-value-ontology` |
| 酱汁汇合 | `Gateway_SauceMerge` | exclusiveGateway |
| 获取奶酪 | `Task_GetCheese` | `get-component` |
| 是否添加奶酪？ | `Gateway_CheeseChoice` | exclusiveGateway |
| 基础奶酪铺设 | `Task_BaseCheese` | `query-property-value-ontology` |
| 获取顶层配料 | `Task_GetTopping` | `get-component` |
| 是否有顶层配料 | `Gateway_0xkqhps` | exclusiveGateway |
| 顶层配料铺设 | `Task_ToppingSpread` | `query-property-value-ontology` |
| 获取封层 | `Task_GetTopCheese` | `get-component` |
| 是否需要封层？ | `Gateway_TopCheeseChoice` | exclusiveGateway |
| 补铺表层奶酪封层 | `Task_TopCheese` | `query-property-value-ontology` |
| 封层汇合 | `Gateway_TopCheeseMerge` | exclusiveGateway |
| 高温烘烤成型 / 出炉后处理与装饰 / 成品切割检验出品 | `Task_Bake` / `Task_Decorate` / `Task_Finish` | `query-property-value-ontology` |
| 结束事件 | `End_01` | endEvent |

### 2.3 BPMN ↔ JobWorker 绑定

`ExampleOntologyJobWorker`（`@Component @Profile("ExampleBPMN")`，继承 `OntologyWorkerSupport`，`@PostConstruct initOntologyPipeline()`）声明 **2 个 `@JobWorker(autoComplete=false)`**：

- `query-property-value-ontology` ← `handleQueryPropertyValue`（11 个节点使用）
- `get-component` ← `handleGetComponent`（4 个节点使用）

> **BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」。** 阈值 / 值经本体运行时读取，Java 侧不含硬编码业务分支常量。

### 2.4 关键类

`ExampleApplication`（`@SpringBootApplication`，`setAdditionalProfiles("ExampleBPMN")`）、`ExampleOntologyJobWorker`、`web/ExampleController`（`/api/example/*`、`/health`）。
测试侧：`AllExampleSuiteTest`（套件入口）、`ExampleBpmnRealEngineTest`、`ExampleSwrlRabbitMqTest`。

---

## 3. 测试分层与策略

| 层级 | 测试类 | 依赖引擎 | 是否需环境 | 关注点 |
|---|---|---|---|---|
| 端到端（真实引擎） | `ExampleBpmnRealEngineTest`（3 项） | 真实 Zeebe `localhost:26500`（REST 9080） | **是**（Camunda + Ontop + MySQL） | 部署 / 三披萨类型全流程完成 / 无 incident |
| 集成（SWRL + 消息） | `ExampleSwrlRabbitMqTest`（2 项） | 无 | **是**（RabbitMQ + MySQL） | SWRL 触发回调 / 消息端到端投递 / content_type |
| 本体合规（拟建） | `ExampleOntologyComplianceTest`（拟建，见 §9） | 无 | 否 | 可解析 / 一致性 / 结构（**当前未落地**） |

**设计原则**：
1. **端到端由生产 Worker 驱动（不写桩）**——e2e 加载生产 `ExampleOntologyJobWorker`，不使用测试替身；
2. **环境不就绪即跳过（不失败）**——用 `ServiceProbe.assumeReachable` / `assumeAllReachable` 断言前置，缺少服务则 `assume` 跳过；
3. **用例隔离**——`ExampleSwrlRabbitMqTest` 逐用例快照并**原子恢复**本体与推理器基线，避免污染；`ExampleBpmnRealEngineTest` 于 `@AfterAll` 清理写入的 `MyPizza%` 实例；
4. **本体为唯一裁判**——断言以 OWL / SWRL 为准，不硬编码业务常量。

---

## 4. 测试环境与配置

- **配置源**：`src/main/resources/application.yaml`（同一 yaml 既被 Spring 读取，也被 `EnvPrepare` 读取）。
- **激活 Profile**：`ExampleBPMN`（真实引擎）/ `ExampleJunitTest`（SWRL+消息）。
- **关键键**：`ontology.main-path`（`ontology/pizza-all.owl`）/ `ontology.abox-path` / `ontology.obda-path` / `ontology.obda-properties-path` / `ontology.bpmn-path`；`camunda.client.grpc-address`、`rest-address`；`server.port`（9083）。

### 4.1 外部依赖端点

| 依赖 | 端点 | 使用者 |
|---|---|---|
| Camunda 8 / Zeebe | `localhost:26500`（gRPC）/ `localhost:9080`（REST） | `ExampleBpmnRealEngineTest` |
| MySQL | `localhost:3306`（库 `mypizzadb`） | 两测试均用 |
| Ontop | `localhost:8080` | 本体推理 / OBDA |
| RabbitMQ | `localhost:5672` / `15672` | `ExampleSwrlRabbitMqTest` |

### 4.2 真实环境前置

1. 双击模块根 `start-env.cmd`（内部 `EnvPrepare start`，读 `application.yaml`）拉起 **Camunda + MySQL + Ontop + RabbitMQ**；
2. 启动 `ExampleApplication`（激活 `ExampleBPMN`）订阅作业 —— **BPMN 端到端用例依赖本应用 Worker 已就绪**，否则会因等待 job 而超时；
3. 浏览器 / REST 可达 `localhost:9083`。

---

## 5. 测试数据设计

| 类别 | 来源 | 说明 |
|---|---|---|
| 披萨类型（正向） | 代码常量 | `ItalianStyleWhiteSeafoodPizza` / `MargheritaPizza` / `VegetarianPizza`（IRI 见 §6.1） |
| 低库存组件（SWRL） | 程序构造 | `stockQuantity` 低于阈值（如 NeapolitanCrust stock=3 / 2），触发 `LowStockCrust` |
| 组件属性 | 程序构造 | `name`、`type`、`supplier`、`price`、`stockQuantity` |
| 本体基线快照 | 运行时采集 | `ExampleSwrlRabbitMqTest` 逐用例快照 / 还原 |
| 工序 ABox | 本体既有 | `pizza-processes-abox.owl`（BPMN 工序耗时） |

> 变量选取原则：**不写入违反本体约束的 E2E 变量**；SWRL 用例的低库存值为**刻意越阈**，用于触达低库存分支。

---

## 6. 测试用例清单

### 6.1 端到端测试 `ExampleBpmnRealEngineTest`（3 项，真实引擎）

> `@SpringBootTest(classes=ExampleApplication.class)`、`@ActiveProfiles("ExampleBPMN")`、`@Timeout(300s)`；`@BeforeAll` 经 `ServiceProbe.assumeReachable("Camunda REST 网关", LOCALHOST, 9080)` 前置；`@BeforeEach` 幂等部署 BPMN。

| ID | Order | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|---|
| E-1 | 1 | `bakingProcessCompletesItalianStyle` | `pizzaType=http://example.org/pizza/core/ItalianStyleWhiteSeafoodPizza` | 部署成功；流程在 2 min 内 `COMPLETED` |
| E-2 | 2 | `bakingProcessCompletesMargherita` | `pizzaType=…/MargheritaPizza` | 同上 |
| E-3 | 3 | `bakingProcessCompletesVegetarian` | `pizzaType=…/VegetarianPizza` | 同上 |

> `@AfterAll cleanupWrittenPizzaInstances`：清理 `myPizza` 表中 `name LIKE 'MyPizza%'` 的记录（环境未就绪则跳过）。

### 6.2 SWRL + RabbitMQ 测试 `ExampleSwrlRabbitMqTest`（2 项，集成）

> `@SpringBootTest(classes=ExampleApplication.class)`、`@ActiveProfiles("ExampleJunitTest")`；`@BeforeAll` 经 `ServiceProbe.assumeAllReachable(..., 5672, 3306)` 前置并初始化 `OBDAHandler`/`BackendService`；`@BeforeEach`/`@AfterEach` 快照并**原子恢复**本体基线。

| ID | Order | 方法 | 场景 | 预期断言（关键） |
|---|---|---|---|---|
| S-1 | 1 | `testSwrlRuleTriggerListenerCallback` | 启动 `SwrlRuleTriggerListener` 监控 `LowStockCrust`；插入低库存 `NeapolitanCrust`（stock=3） | 10 s 内触发回调；生成的 MQ JSON 含 `name`/`type`/`supplier`/`stockQuantity`/`price` |
| S-2 | 2 | `testSwrlRuleTriggerEndToEndDelivery` | 声明 Direct Exchange `example.low-stock.exchange` / Queue `exampleQueue` / binding `low.stock.alert`；插入低库存（stock=2）；从队列消费 | 回调触发；`exampleQueue` 收到消息且与发送 JSON **完全一致**；`content_type=application/json`；RabbitMQ API 确认 `publish>0` |

> 目标类 IRI：`http://example.org/pizza/components/LowStockCrust`；实例命名空间 `http://example.org/pizza/components-abox/`。

---

## 7. 覆盖矩阵

### 7.1 流程节点覆盖

| BPMN 节点 | 覆盖用例 | 说明 |
|---|---|---|
| `Start_01` / `End_01` | E-1/E-2/E-3 | 每实例校验经过 |
| 11 个 `query-property-value-ontology` 节点 | E-1/E-2/E-3 | 工艺 / 铺料 / 烘烤 / 装饰 / 检验等 |
| 4 个 `get-component` 节点 | E-1/E-2/E-3 | 酱汁 / 奶酪 / 配料 / 封层 |
| 6 个排他网关 | E-1/E-2/E-3 | 依 `pizzaType` 走不同分支（三类型覆盖主分支组合） |

### 7.2 本体 / 推理覆盖

| 关注点 | 覆盖用例 |
|---|---|
| 推理链路初始化（`BackendService`） | S-1/S-2 |
| SWRL 低库存规则（R1/R2 …） | S-1/S-2（`LowStockCrust` 触发） |
| 消息外发通道 | S-2（Exchange/Queue/Binding + content_type） |
| 本体一致性 / 结构计数 | **未覆盖**（拟建合规测试，见 §9） |

### 7.3 边界 / 负向场景

| # | 场景 | 用例 | 预期行为 |
|---|---|---|---|
| 1 | 组件库存越阈 | S-1/S-2 | SWRL 推导 `LowStock*` 并外发告警 |
| 2 | 环境不就绪 | E-*/S-* | `assume` 跳过（不失败） |
| 3 | 无效披萨类型 | **未覆盖** | 当前无用例（`get-component` 有降级分支，可补负向用例） |

---

## 8. 执行方式

- 全套入口：`AllExampleSuiteTest`（`@Suite @SelectClasses`，聚合 `ExampleBpmnRealEngineTest` + `ExampleSwrlRabbitMqTest`）。
- 命令：
  - 本模块：`./mvnw -pl OntologyFrameworkExample -am test`
  - 仅套件：`./mvnw -f OntologyMachine/pom.xml -pl OntologyFrameworkExample -am test -Dtest=AllExampleSuiteTest`
  - 仅 BPMN 端到端：`-Dtest=ExampleBpmnRealEngineTest`
  - 仅 SWRL+消息：`-Dtest=ExampleSwrlRabbitMqTest`
- **环境依赖**：两测试类均**依赖外部服务**（见 §4.1）；环境未就绪时经 `ServiceProbe` 跳过。**BPMN 端到端需本应用 Worker 已启动**。

---

## 9. 待确认项

| # | 问题 | 结论 |
|---|---|---|
| Q-T1 | 是否新建「本体合规」测试 `ExampleOntologyComplianceTest`（可解析 / 一致性 / 结构计数，离线） | ☐ 待定：可参照 `OntologyFrameworkPizza` 形态补建，使本模块具备离线回归能力 |
| Q-T2 | 是否新建「内存引擎」端到端测试（`zeebe-process-test-extension` 已在依赖中） | ☐ 待定：可离线跑 BPMN 拓扑，摆脱对真实引擎的依赖 |
| Q-T3 | 是否补「无效披萨类型」负向用例 | ☐ 待定 |
| Q-T4 | 旧文档 Worker 计数（9）与实际（2）的差异是否回改 | ☐ 待定（见《应用架构设计》§11） |

---

## 变更记录

| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-11-02 | 新增《Example应用-测试设计》（第 8 步）：以**实际测试**为准（2 类 5 用例），登记与旧文档差异与待补项 | 码道 |
