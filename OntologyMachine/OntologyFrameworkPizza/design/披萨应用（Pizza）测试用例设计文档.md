# 披萨应用（Pizza）测试用例设计文档

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFrameworkPizza / Pizza（披萨本体驱动定制与制作示例应用） |
| 文档版本 | v1.0 |
| 生成日期 | 2026-09-30 |
| 关联本体 | `ontology/pizza-all.owl`（导入 `pizza.owl`(/core)、`pizza-components.owl`(/components)、`pizza-processes.owl`(/processes)、`pizza-components-rules.owl`(/components-rules) 及 ABox）；术语表 `ontology/pizza-terminology.ttl`(/term) |
| 关联流程 | 《披萨应用-流程设计》；`ontology/bpmn/myPizzaBakingProcess.bpmn`（`PizzaMakingStandardProcess`）与 `ontology/bpmn/myPizzaTypeDesignProcess.bpmn`（`PizzaOntologyDesignProcess`），Camunda 8 / Zeebe |
| 测试代码位置 | `OntologyFrameworkPizza/src/test/java/com/ocean/ontologyframework/pizza/`（`pizza` 包 + `validation` 子包） |
| 测试资源位置 | `OntologyFrameworkPizza/src/test/resources/pizzaInstances.json` |

> 本文档是对**现有已实现测试**的工程化描述（设计说明 + 用例清单 + 覆盖矩阵），所有方法名、输入、断言均逐条取自测试源码，未作任何发明。个别代码内注释/日志文字与断言值不一致处，均以**断言实际值**为准并在 §11 标注。

---

## 1. 目的与范围

**目的**：明确 Pizza 示例应用的质量保证方式——测什么、怎么测、用什么数据测、判定依据是什么、如何执行。

**范围**：
- 覆盖两条 BPMN 流程（烘焙 `PizzaMakingStandardProcess` / 设计 `PizzaOntologyDesignProcess`）的端到端编排；
- 覆盖 Ontop OBDA 读写（插入/更新/删除/查询、多表自动拆分与事务原子性/回滚、正向映射完整性）；
- 覆盖 Openllet 推理（TBox 子类推理检索、SWRL 实时推导与动态响应、一致性校验）；
- 覆盖 SWRL 触发回调与 RabbitMQ 低库存告警投递（Exchange/Queue/Binding 端到端）；
- 覆盖 OWL 本体合规性校验（JSON 披萨实例逐条验证并输出不合规原因）与 SKOS 术语表结构/交叉校验。

**不含**：塔架中段（TMSD）与中医经方（TCM）相关测试（同工程内独立维护）。

---

## 2. 被测系统概述

### 2.1 业务链路

**烘焙流程**（`PizzaMakingStandardProcess`，纯 `serviceTask`，无 `userTask`）：

```
Start_01(置 totalCost=0/totalDuration=0)
  └─ 和面/发酵/成型：DoughMix → DoughFerment → CrustForm（query-property-value-ontology 累加 duration）
  └─ 取料：GetSauce → GetCheese → GetTopping → GetTopCheese（get-component，按 matchedWord 走排他网关分支）
  └─ 烘焙/装饰/收尾：Bake → Decorate → Finish
  └─ End_01
```

**设计流程**（`PizzaOntologyDesignProcess`，1 个 start 表单 + 4 个 `userTask`）：

```
StartEvent_1(pizza_start_form)
  └─ Task_Reasoning（pizza-limitation，输出 12 个约束变量）
  └─ 4 组「排他网关 + userTask」：GW_Crust/UT_Crust(pizza_crust_form)、GW_Cheese/UT_Cheese(pizza_cheese_form)、
     GW_Sauce/UT_Sauce(pizza_sauce_form)、GW_Topping/UT_Topping(pizza_topping_form)
     （网关条件 needCrust/needCheese/needSauce/needTopping = true/false）
  └─ Task_Summary（pizza-summary-generator：组装摘要 + TBox 一致性校验 + 写入 MyPizza_<ts> 实例）
  └─ EndEvent_1
```

### 2.2 BPMN 元素 ID 与 jobType

**烘焙流程**（`PizzaMakingStandardProcess`）：

| 环节 | 元素 ID | jobType |
|---|---|---|
| 和面 | `Task_DoughMix` | `query-property-value-ontology` |
| 发酵 | `Task_DoughFerment` | `query-property-value-ontology` |
| 成型 | `Task_CrustForm` | `query-property-value-ontology` |
| 取酱 | `Task_GetSauce` | `get-component` |
| 取奶酪 | `Task_GetCheese` | `get-component` |
| 取配料 | `Task_GetTopping` | `get-component` |
| 取顶料 | `Task_GetTopCheese` | `get-component` |
| 烘焙 | `Task_Bake` | `query-property-value-ontology` |
| 装饰 | `Task_Decorate` | `query-property-value-ontology` |
| 收尾 | `Task_Finish` | `query-property-value-ontology` |
| 排他网关 | `Gateway_SauceChoice` / `Gateway_SauceMerge` / `Gateway_CheeseChoice` / `Gateway_0xkqhps` / `Gateway_TopCheeseChoice` / `Gateway_TopCheeseMerge` | （按 `matchedWord` 分支） |
| 开始 / 结束 | `Start_01` / `End_01` | — |

**设计流程**（`PizzaOntologyDesignProcess`）：

| 环节 | 元素 ID | 类型 / jobType |
|---|---|---|
| 开始事件 | `StartEvent_1` | form `pizza_start_form` |
| 需求解析 | `Task_Reasoning` | `pizza-limitation` |
| 饼底选择 | `GW_Crust` / `UT_Crust` | 网关 / userTask（`pizza_crust_form`） |
| 奶酪选择 | `GW_Cheese` / `UT_Cheese` | 网关 / userTask（`pizza_cheese_form`） |
| 酱汁选择 | `GW_Sauce` / `UT_Sauce` | 网关 / userTask（`pizza_sauce_form`） |
| 配料选择 | `GW_Topping` / `UT_Topping` | 网关 / userTask（`pizza_topping_form`） |
| 摘要生成 | `Task_Summary` | `pizza-summary-generator` |
| 结束事件 | `EndEvent_1` | — |

### 2.3 BPMN ↔ JobWorker 绑定

`PizzaOntologyJobWorker`（`@Profile("PizzaBPMNTest")`）声明 **9 个 `@JobWorker`**（均 `autoComplete=false`）：`insert-component`、`update-individual`、`delete-component`、`query-instances`、`query-property-value-ontology`、`query-property-value-db`、`get-component`、`pizza-limitation`、`pizza-summary-generator`。BPMN 部署到 Zeebe 后由引擎按拓扑推进，到达 `serviceTask` 按 taskDefinition type 派发作业，订阅同名 jobType 的 worker 被回调执行。**BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」**，四者靠 jobType 字面量绑定，无代码级引用。

### 2.4 关键类

`PizzaApplication`、`PizzaOntologyJobWorker`（本模块 `com.ocean.ontologyframework.pizza` 包）；共享工具 `com.ocean.utilities.{OntologyLabelMatcher,RabbitMqHandler,StopOnTimeoutExtension}`（来自 `com.ocean:Utilities` 模块）；测试侧：`OntologyFrameworkPizzaTests`、`PizzaBakingProcessInMemoryTest`、`PizzaBpmnRealEngineTest`、`validation/PizzaSkosTerminologyTest`、`validation/PizzaOntologyValidator`、`validation/ValidationResult`、`ConsistencyTest`。

---

## 3. 测试分层与策略

| 层级 | 测试类 | 依赖引擎 | 是否需环境（Camunda/MySQL/Ontop/RabbitMQ） | 关注点 |
|---|---|---|---|---|
| 集成（OBDA + 推理） | `OntologyFrameworkPizzaTests`（21 项） | 无（Spring 上下文，直连 Ontop/MySQL/RabbitMQ） | **是** | 子类推理检索、联合推理查询、增删改、多表拆分事务、SWRL、Listener/MQ、本体合规校验 |
| 端到端（内存引擎） | `PizzaBakingProcessInMemoryTest`（3 项） | `EngineFactory` 内存引擎 | **是**（需 Ontop/MySQL 供 worker） | 烘焙 BPMN 编排、主干节点顺序、无 incident |
| 端到端（真实引擎） | `PizzaBpmnRealEngineTest`（6 项） | 真实 Zeebe `localhost:26500` | **是** | 两条 BPMN 真实部署与消费、userTask 自动完成 |
| 本体术语（纯 Jena） | `validation/PizzaSkosTerminologyTest`（6 项） | 无 | 否 | SKOS 结构 + OWL 交叉校验 |
| 诊断（人工） | `ConsistencyTest`（`main`，无 `@Test`） | 无 | 否 | 独立 OWLManager + Openllet 一致性打印 |

**设计原则**：
1. **分层解耦**——`PizzaSkosTerminologyTest` / `ConsistencyTest` 纯文件/内存推理，可离线快速回归；需要环境的能力集中在 Spring 集成与两个端到端类。
2. **生产 worker 驱动**——端到端测试不另写 worker，直接由 `PizzaOntologyJobWorker` 消费，测试覆盖真实计算链路。
3. **本体为唯一裁判**——SWRL 推导、一致性、合规性均运行时解析自本体，不臆造阈值。
4. **隔离与恢复**——`OntologyFrameworkPizzaTests` 用 `@BeforeEach` 全量公理快照 + `@AfterEach` 原子恢复，保证用例间本体不漂移。

---

## 4. 测试环境与配置

### 4.1 Profile 与配置源

- 配置源：`src/main/resources/application.yaml`（同一 yaml 既驱动 Spring 又被 EnvPrepare 读取）。
- 测试激活 Profile：`OntologyFrameworkPizzaTests` → `PizzaJunitTest`；`PizzaBakingProcessInMemoryTest` / `PizzaBpmnRealEngineTest` → `PizzaBPMNTest`。
- 关键键：`ontology.main-path=ontology/pizza-all.owl`、`ontology.obda-path`、`ontology.obda-properties-path`、`ontology.bpmn-path`、`ontology.design-bpmn-path`；`camunda.client.grpc-address=http://localhost:26500`、`camunda.client.rest-address=http://localhost:9080`；`server.port=9081`。

### 4.2 外部依赖端点

| 依赖 | 端点 / 载体 | 使用者 |
|---|---|---|
| Ontop SPARQL | `http://localhost:8080/sparql` | `OntologyFrameworkPizzaTests`（`ONTOP_ABOX_ENDPOINT`）、worker |
| MySQL | `jdbc:mysql://localhost:3306/mypizzadb`（`myPizza.properties`） | OBDA 读写、`@AfterAll` 清理 |
| RabbitMQ | AMQP + 管理 API `http://localhost:15672`（`guest/guest`） | 场景19/20 |
| Zeebe | 内存引擎（随机端口）/ 真实 `localhost:26500` | 两个端到端类 |

### 4.3 真实环境前置

1. 启动环境（Camunda 8 / MySQL / Ontop / RabbitMQ）：`EnvPrepare` 模块（Java，`com.ocean.envprepare.EnvPrepare`，子命令 `start`）；
2. 启动 `PizzaApplication`（激活 `PizzaBPMNTest`），使 `PizzaOntologyJobWorker` 订阅作业；
3. 初始化库表：`ontology/database/createdb_tables.ddl` + `data.sql`；
4. Zeebe Gateway 可达 `localhost:26500`。

### 4.4 BPMN 部署方式

- **内存引擎**：`PizzaBakingProcessInMemoryTest` 通过 `@DynamicPropertySource` 启动 `EngineFactory` 内存引擎，并把其 gateway 地址注入 `camunda.client.*`；用例内 `addResourceFile(bpmnPath)` 部署，进程结束即销毁（Camunda UI 不可见）。
- **真实引擎**：`PizzaBpmnRealEngineTest` 的 `@BeforeEach deploy()` 部署两条 BPMN **以及 forms 目录下全部 `.form`**（`design-bpmn-path` 的同级目录扫描），由 `CamundaClient` 执行。

---

## 5. 测试数据设计

### 5.1 三输入披萨类型（两条 BPMN 共用）

| 常量 | IRI |
|---|---|
| `PIZZA_TYPE_ITALIAN_STYLE` | `http://example.org/pizza/core/ItalianStyleWhiteSeafoodPizza` |
| `PIZZA_TYPE_MARGHERITA` | `http://example.org/pizza/core/MargheritaPizza` |
| `PIZZA_TYPE_VEGETARIAN` | `http://example.org/pizza/core/VegetarianPizza` |

`ontology/bpmn/测试输入数据.txt` 另以 3 段 JSON 给出上述 3 个 `pizzaType`。

### 5.2 设计流程 userTask 变量（`PizzaBpmnRealEngineTest`，按 pizzaType 给出符合本体约束的组件实例）

| pizzaType | UT_Crust | UT_Cheese | UT_Sauce | UT_Topping |
|---|---|---|---|---|
| ItalianStyle | `NeapolitanCrustInstance` | `LowMoistureMozzarellaInstance` | `WhiteSauceInstance` | （不完成，无 topping 约束） |
| Margherita | `NeapolitanCrustInstance` | `BuffaloMozzarellaInstance` | `NeapolitanTomatoSauceInstance` | `[BasilInstance]` |
| Vegetarian | `NeapolitanCrustInstance` | `LowMoistureMozzarellaInstance` | （不完成） | `[MushroomInstance]` |

> 变量选取原则（源码注释）：白酱披萨用非番茄酱、Margherita（`hasTopping hasValue Basil`）用番茄酱+罗勒、Vegetarian（`hasTopping allValuesFrom VegetableTopping`）用蔬菜配料且无 sauce 约束——避免写入违反本体的 ABox 数据。

### 5.3 本体合规校验数据（`src/test/resources/pizzaInstances.json`，10 条）

字段：`name`、`type`、`price`、`production_date`、`crust_name`、`cheese_name`、`sauce_name`、`topping_name`（部分缺省，如 Marinara 无 `cheese_name`）。

| # | name | type | crust_name | cheese_name | sauce_name | topping_name |
|---|---|---|---|---|---|---|
| 1 | 经典玛格丽特 | MargheritaPizza | NeapolitanCrustInstance | BuffaloMozzarellaInstance | NeapolitanTomatoSauceInstance | BasilInstance |
| 2 | 那不勒斯蘑菇披萨 | GenericNeapolitanPizza1 | NeapolitanCrustInstance | BuffaloMozzarellaInstance | NeapolitanTomatoSauceInstance | MushroomInstance |
| 3 | 芝加哥深盘经典 | ChicagoDeepDishPizza | ChicagoDeepDishCrustInstance | LowMoistureMozzarellaInstance | AmericanTomatoSauceInstance | SalamiInstance |
| 4 | 底特律方形培根披萨 | DetroitPizza | DetroitCrustInstance1 | LowMoistureMozzarellaInstance | AmericanTomatoSauceInstance | BaconInstance |
| 5 | 纽约薄饼辣肠披萨 | NewYorkPizza | NewYorkCrustInstance | LowMoistureMozzarellaInstance | AmericanTomatoSauceInstance | PepperoniInstance |
| 6 | 圣路易斯火腿披萨 | StLouisPizza | StLouisCrackerCrustInstance | ProvelCheeseInstance | OtherSauceInstance | HamInstance |
| 7 | 西西里厚底洋葱披萨 | SicilianPizza | SicilianCrustInstance | ParmesanInstance | AmericanTomatoSauceInstance | OnionInstance |
| 8 | 北京烤鸭披萨 | PekingDuckPizza | FrenchThinCrustInstance | LowMoistureMozzarellaInstance | OtherSauceInstance | RoastDuckInstance |
| 9 | 猫山王榴莲披萨 | DurianPizza | ArgentinianThickCrustInstance | LowMoistureMozzarellaInstance | OtherSauceInstance | DurianInstance |
| 10 | 玛瑞纳拉素食披萨 | MarinaraPizza | RomanTondaCrustInstance | （无） | NeapolitanTomatoSauceInstance | GarlicInstance |

### 5.4 组件测试数据（程序构造）

`OntologyFrameworkPizzaTests` 中的组件以 `NeapolitanCrust`（或其它叶子类）为 type，配 `name/type/supplier/price/stockQuantity`（多表场景追加 `crustThicknessMm/bakingTemperatureCelsius/flourType`）。低库存用例用 `stockQuantity=8/5/3/2`（< 阈值 20）触发 `LowStockCrust`。

---

## 6. 测试用例清单（共 36 项）

### 6.1 `OntologyFrameworkPizzaTests`（OBDA + 推理集成，21 项）

`@SpringBootTest(classes=PizzaApplication.class)` + `@ActiveProfiles("PizzaJunitTest")` + `@TestMethodOrder(OrderAnnotation)`；`@BeforeAll setUp` 初始化 `OBDAHandler`/`BackendService`；`@BeforeEach` 全量公理快照 / `@AfterEach` 原子恢复。

| ID | Order | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|---|
| I-1 | 1 | `testQueryWithInferredSubclasses` | Crust 子类集合 | 子类非空；SPARQL 经 `rdf:type ?cls` + `:type ?type` 检索到饼底-供应商记录；每行 `instance`/`type` 非空 |
| I-2 | 2 | `testQueryWithInferredProperties` | Ontop CONSTRUCT 加载 ABox | ABox 非空；`PizzaComponent` 实例含 `supplier`/`price` 键 |
| I-3 | 3 | `testInsertWithInvalidTypeShouldFail`（负） | type=`SpicyChicken` | `insertComponentAutoSplit` 抛异常（数据库 type 约束） |
| I-4 | 4 | `testInsertDuplicateNameShouldFail`（负） | name=`NeapolitanCrustInstance` 重复 | 抛异常（唯一性约束） |
| I-5 | 5 | `testInsertWithoutTypeShouldFail`（负） | 缺 `type` | 抛 `IllegalArgumentException` |
| I-6 | 6 | `testInsertValidPizzaComponent` | 合法 NeapolitanCrust | 写入成功；SPARQL 查得恰 1 条，`supplier=SupplierX`、`price=12.99` |
| I-7 | 7 | `testUpdateMultipleProperties` | supplier/price/stock 覆盖更新 | 更新成功；`NewSupplier`/`15.50`/`42` |
| I-8 | 8 | `testQueryWithLiveSwrl` | stock=8 注入 | Openllet 推导为 `LowStockCrust`；finally 清理临时公理 |
| I-9 | 9 | `testSwrlDynamicResponseOnStockChange` | 三阶段 50→5→30 | 阶段1(50) 不推导；阶段2(5) 推导；阶段3(30) 撤销推导 |
| I-10 | 10 | `testSwrlRuleTriggerListenerCallbackWithRabbitMQ` | stock=3 写入 | `SwrlRuleTriggerListener` 10s 内触发回调；回调 IRI 含个体名 |
| I-11 | 11 | `testDeleteExistingComponent` | 先插后删 | 前置查到 1 条；删除后 SPARQL 返回空 |
| I-12 | 12 | `testDeleteNonExistentIsIdempotent` | 删除不存在个体 | 不抛异常；删前删后均查不到 |
| I-13 | 13 | `testMultiTableWriteAndQuery` | 8 属性（跨 components/crust 两表） | 全部属性命中映射缓存；联表查得 1 条且各值一致 |
| I-14 | 14 | `testForwardMappingCompleteness` | 映射缓存 | 10 个属性均有映射变量；缓存只读（`UnsupportedOperationException`）；未映射属性返回 `null` |
| I-15 | 15 | `testMultiTableWriteTransactionRollback`（负） | `crustThicknessMm=-999.99` | 抛出异常；回滚后主表无残留 |
| I-16 | 16 | `testUpdateAcrossMultipleTables` | price 9.99→18.88、crustThicknessMm 4→5 | 跨表更新成功；JOIN 键自动填充；断言 `price=18.88`、`thickness=5` |
| I-17 | 17 | `testUpdateAcrossTablesShouldRollbackOnSubTableFailure`（负） | 属性 `crustThicknessMm_notexist` | 抛 `IllegalStateException`（含「无有效 OBDA 映射」）；主表 price 保持原值 9.99 |
| I-18 | 18 | `testDeleteAcrossMultipleTablesSuccess` | 先跨表插入 | 跨表删除成功；主表+子表联查与主表单查均返回空 |
| I-19 | 19 | `testSwrlRuleTriggerListenerCallback` | stock=3，Listener 内 `RabbitMqHandler.send` | 回调触发；组装的 JSON 含 name/type/supplier/stockQuantity/price |
| I-20 | 20 | `testSwrlRuleTriggerEndToEndDelivery` | 声明 Exchange/Queue/Binding，stock=2 | 队列消费到消息且与发送 JSON 完全一致；`content_type=application/json`；管理 API 确认 `publish>0` |
| I-21 | 21 | `testPizzaInstanceOntologyComplianceValidation` | `pizzaInstances.json`（10 条） | 验证总数=合规+不合规；合规数>0；不合规均有具体原因描述 |

> 说明：I-16 源码中日志文字写「crustThicknessMm→12」、注释写「4→5」，而断言为 `thickness=5`（初始 `4`）；以断言为准，见 §11。

### 6.2 `PizzaBakingProcessInMemoryTest`（内存引擎端到端，3 项）

`@SpringBootTest(classes=PizzaApplication.class)` + `@ActiveProfiles("PizzaBPMNTest")`；`@DynamicPropertySource` 启动内存引擎并注入地址；`TIMEOUT=3min`。

| ID | 方法 | 输入 | 预期断言（关键） |
|---|---|---|---|
| E-1 | `italianStyleWhiteSeafoodPizzaFlowCompletes` | `ItalianStyleWhiteSeafoodPizza` | 部署 1 个流程（`PizzaMakingStandardProcess`）；实例 `isCompleted().hasNoIncidents()`；经过 `Start_01`/`End_01`；按序经过 10 个主干节点 `MAIN_TASKS` |
| E-2 | `margheritaPizzaFlowCompletes` | `MargheritaPizza` | 同上 |
| E-3 | `vegetarianPizzaFlowCompletes` | `VegetarianPizza` | 同上 |

`MAIN_TASKS = {Task_DoughMix, Task_DoughFerment, Task_CrustForm, Task_GetSauce, Task_GetCheese, Task_GetTopping, Task_GetTopCheese, Task_Bake, Task_Decorate, Task_Finish}`。

### 6.3 `PizzaBpmnRealEngineTest`（真实引擎端到端，6 项）

`@SpringBootTest(classes=PizzaApplication.class)` + `@ActiveProfiles("PizzaBPMNTest")` + `@TestMethodOrder(OrderAnnotation)` + `@Timeout(300s)`；`@BeforeEach deploy()` 部署两 BPMN + 全部 `.form`。

| ID | Order | 方法 | 用例 | 预期断言（关键） |
|---|---|---|---|---|
| R-1 | 1 | `bakingProcessCompletesItalianStyle` | 烘焙·ItalianStyle | 实例在 `WAIT=2min` 内 `COMPLETED` |
| R-2 | 2 | `bakingProcessCompletesMargherita` | 烘焙·Margherita | 同上 |
| R-3 | 3 | `bakingProcessCompletesVegetarian` | 烘焙·Vegetarian | 同上 |
| R-4 | 4 | `designProcessCompletesItalianStyle` | 设计·ItalianStyle | 轮询完成 userTask（`TASK_VARS_ITALIAN_STYLE`）后实例完成 |
| R-5 | 5 | `designProcessCompletesMargherita` | 设计·Margherita | 同上（`TASK_VARS_MARGHERITA`） |
| R-6 | 6 | `designProcessCompletesVegetarian` | 设计·Vegetarian | 同上（`TASK_VARS_VEGETARIAN`） |

**设计流程驱动方式**：`runDesign` 以 500ms 间隔轮询实例的 `userTask`，对 `CREATED` 任务用对应变量完成；同时收集 `incident` 日志；超时（2min）未完成则抛 `AssertionError`。`@AfterAll cleanupWrittenPizzaInstances` 删除 `myPizza` 表中 `name LIKE 'MyPizza%'` 的行。

### 6.4 `validation/PizzaSkosTerminologyTest`（SKOS + OWL 交叉校验，6 项）

`@BeforeAll loadModels` 加载 `ontology/pizza-terminology.ttl` 及 `ontology` 目录下全部 `.owl`。

| ID | 方法 | 预期断言（关键） |
|---|---|---|
| S-1 | `declaresConceptScheme` | `http://example.org/pizza/term` 声明为 `skos:ConceptScheme` |
| S-2 | `everyConceptHasChinesePrefLabel` | 概念集合非空；每个概念均有 `@zh` 首选标签 |
| S-3 | `broaderTargetsAreWellFormed` | `skos:broader` 目标均已定义、无自环 |
| S-4 | `owlExactMatchTargetsAreAllDefined` | OWL 中指向 `term/` 的 `skos:exactMatch` 目标均在术语表有定义 |
| S-5 | `previouslyMissingTermsArePresent` | 概念含 `hasCrust/hasSauce/hasCheese/hasTopping/ItalianStyleWhiteSeafoodPizza`（历史缺口回归） |
| S-6 | `fusionConceptHasBothBroader` | `ItalianStyleWhiteSeafoodPizza` 同时 `broader` 指向 `WhiteSaucePizza` 与 `SeafoodPizza` |

### 6.5 辅助与诊断（不计入 JUnit 用例）

| 类 | 作用 |
|---|---|
| `validation/PizzaOntologyValidator`（`AutoCloseable`） | 单条披萨实例校验器：基础字段校验 → 构建主体公理 → 经 `OBDAHandler` 动态查组件附加属性 → `safeVerifyAndDBExecution` 一致性检测 → `finally` 回滚临时公理 |
| `validation/ValidationResult` | 校验结果 record（`valid`/`invalid` + `violations`） |
| `ConsistencyTest`（`main`） | 独立构造小本体，`OpenlletReasonerFactory` 打印一致性与逻辑公理；供人工诊断 |

---

## 7. 本体约束校验清单（判定依据）

命名空间：核心 `/core`、组件 `/components`、流程 `/processes`、组件规则 `/components-rules#`、术语 `/term`；ABox 对应 `*-abox`。

### 7.1 SWRL 规则（`pizza-components-rules.owl`，5 条）

| 规则 | 主体 | 条件 | 结论 |
|---|---|---|---|
| R1 | NeapolitanCrust | stockQuantity < 20 | LowStockCrust |
| R2 | Crust | stockQuantity < 20 | LowStockCrust |
| R3 | Cheese | stockQuantity < 10 | LowStockCheese |
| R4 | Sauce | stockQuantity < 15 | LowStockSauce |
| R5 | Topping | stockQuantity < 10 | LowStockTopping |

> R1 是 R2 的特化冗余（NeapolitanCrust ⊑ Crust），二者并存不影响结论。

### 7.2 库存阈值（`minStockThreshold`）

| 类型 | Crust | Sauce | Cheese | Topping |
|---|---|---|---|---|
| 阈值 | 20 | 15 | 10 | 10 |

### 7.3 校验要点

- **TBox 子类推理**：`getSubClassIris(Crust)` 应返回非空的饼底叶子类集合（I-1）。
- **SWRL 实时推导**：写入 `stockQuantity<阈值` 的个体应被 Openllet 推导出对应 `LowStock*` 类（I-8/I-9）；数据恢复后推导撤销（I-9 阶段3）。
- **一致性**：`reasoner.isConsistent()`（`pizza-summary-generator` 写入前校验；`PizzaOntologyValidator` 经 `safeVerifyAndDBExecution`）。
- **组件厚度约束**（`pizza-components.owl` 内联）：薄饼类 ∈[3.0,7.0]、厚饼类 ∈[20.0,80.0]；越界（如 `-999.99`）触发失败（I-15）。
- **SKOS 交叉校验**：OWL `skos:exactMatch` 与术语表双向一致（S-4/S-5）。

---

## 8. 覆盖矩阵（测试层 × 关注点）

| 关注点 | I 集成 | E 内存E2E | R 真实E2E | S SKOS | 诊断 |
|---|---|---|---|---|---|
| 增 / 删 / 改 / 查（OBDA 读写） | ✅ | — | — | — | — |
| 多表拆分事务原子性 / 回滚 | ✅ | — | — | — | — |
| 正向映射完整性 | ✅ | — | — | — | — |
| SWRL 实时推导 + 动态响应 | ✅ | — | — | — | — |
| 触发回调 + MQ 端到端投递 | ✅ | — | — | — | — |
| JSON 实例本体合规校验 | ✅ | — | — | — | ✅ |
| 烘焙 BPMN 编排（顺序 / 无 incident） | — | ✅ | ✅ | — | — |
| 设计 BPMN 编排 + userTask | — | — | ✅ | — | — |
| 三披萨类型（Italian/Margherita/Vegetarian） | — | ✅ | ✅ | — | — |
| SKOS 结构 + OWL 交叉校验 | — | — | — | ✅ | — |
| 本体一致性（Openllet） | ✅ | — | — | — | ✅ |

---

## 9. 执行方式

```bash
# 集成（需 Ontop + MySQL + RabbitMQ 已启动，且 PizzaApplication 可连通端点）
mvn -pl OntologyFrameworkPizza test -Dtest="OntologyFrameworkPizzaTests" -DfailIfNoTests=false

# 内存引擎端到端（需 Ontop + MySQL 供 worker，无需真实 Zeebe）
mvn -pl OntologyFrameworkPizza test -Dtest="PizzaBakingProcessInMemoryTest" -DfailIfNoTests=false

# 真实引擎端到端（需先启动环境 + PizzaApplication，激活 PizzaBPMNTest）
# 环境启动入口：EnvPrepare 模块（Java，com.ocean.envprepare.EnvPrepare，子命令 start）
mvn -pl OntologyFrameworkPizza test -Dtest="PizzaBpmnRealEngineTest" -DfailIfNoTests=false

# SKOS 校验（纯文件，无需环境）
mvn -pl OntologyFrameworkPizza test -Dtest="PizzaSkosTerminologyTest" -DfailIfNoTests=false
```

> IDE 直跑：`src/test` 下各测试类均可作为 JUnit 5 直接运行；需环境者见 §4.3。

---

## 10. 通过准则

1. `OntologyFrameworkPizzaTests` 21 项全绿，本体快照/恢复无漂移告警；
2. `PizzaBakingProcessInMemoryTest` 3 项全绿，流程无 incident、节点顺序一致；
3. `PizzaBpmnRealEngineTest` 6 项全绿，设计流程 userTask 全部自动完成且实例完成；
4. `PizzaSkosTerminologyTest` 6 项全绿；
5. SWRL 推导结论与 §7.1/§7.2 阈值一致；MQ 消息 `content_type=application/json` 且内容与发送完全一致。

---

## 11. 已知边界与待确认项

| 项 | 说明 |
|---|---|
| I-16 数值文字不一致 | 源码日志「crustThicknessMm→12」/ 注释「4→5」与断言 `thickness=5`（初始 `4`）不一致；**以断言为准**，建议统一日志文字 |
| I-10 命名与实际行为 | 方法名含 `WithRabbitMQ`，但回调仅收集 IRI、**未实际发送 MQ**；真正的 MQ 发送在 I-19/I-20 |
| `zeebe-process-test-extension` 作用域 | `pom.xml` 中该依赖**未标 `test` 作用域**，可能进入运行时类路径【待确认是否有意为之】 |
| `camunda.version` 属性 | `8.10.0-alpha2` 仅声明、实际依赖硬编码 `8.9.6`，二者不一致【待确认】 |
| 两套实例来源 | 静态 ABox（`.owl`，个体名多为驼峰无后缀）用于推理；运行态数据来自 MySQL（name 以 `Instance` 结尾）；测试通过 `pizzaInstances.json` 以 `*Instance` 名对齐 DB 实例 |
| `GenericNeapolitanPizza1` / `DetroitCrustInstance1` | JSON 中出现带数字后缀的自定义类型/实例名，非本体标准类；校验以 DB 与本体实际装载为准 |
| 内存引擎不校验本体一致性 | `PizzaBakingProcessInMemoryTest` 只验证编排；一致性由集成层与诊断层覆盖 |
| 设计流程 UT_Crust 输出 | `UT_Crust` 输出含无表单来源的 `crustThickness`【待确认】 |

---

*本文档由 Pizza 测试源码（`OntologyFrameworkPizzaTests` / `PizzaBakingProcessInMemoryTest` / `PizzaBpmnRealEngineTest` / `PizzaSkosTerminologyTest` / `PizzaOntologyValidator` / `ValidationResult` / `ConsistencyTest`）、本体资产（`ontology/*.owl`、`pizza-terminology.ttl`）与两条 BPMN 整理生成，方法名、输入与断言均可回溯至源码。*
