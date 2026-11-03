# 本体应用框架示例（Example）— 应用架构设计

> **用法**：本文件产出**第 7 步**。**闸门④**：业务人员对 **BPMN 的每个节点**逐项确认「实现方法 + 算法」后方可实现对应 JobWorker。
> **基线（强制，依据实际内容）**：`src/main/java/.../example/{ExampleApplication, ExampleOntologyJobWorker, web/ExampleController}.java`、`src/main/resources/{application.yaml, static/index.html}`、`ontology/bpmn/myPizzaBakingProcess.bpmn`、`ontology/database/myPizza.obda`。
> **形态基线**：《LatticeTower应用-应用架构设计》《IndustryInsight应用-应用架构设计》。
> **★ 重要**：本文件以**当前工程实际内容**为准。**实际 JobWorker 为 2 个**（`query-property-value-ontology`、`get-component`），BPMN 只用这 2 个 jobType；旧版《Example应用-顶层设计》§4.3 / 《Example应用-流程设计》§5 所述「9 个 JobWorker / 3 个在用」为**历史设计意图**，与实际代码不一致，本文件已按实际校正（详见 §4 注）。

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkExample / Example（本体应用框架示例模块） |
| 模块坐标 | `com.ocean:OntologyFrameworkExample:0.0.1-SNAPSHOT` |
| 文档日期 | 2026-11-02 |
| 文档版本 | **v1.0** |
| 关联顶层设计 | 《Example应用-顶层设计》 |
| 关联流程设计 | 《Example应用-流程设计》 |
| 关联本体设计 | 《Example应用-本体顶层设计》+《Example应用-本体核心设计》 |
| 关联流程文件 | `ontology/bpmn/myPizzaBakingProcess.bpmn`（processId=`PizzaMakingStandardProcess`） |
| 激活 Profile | `ExampleBPMN`（测试 `ExampleJunitTest`） |
| Worker 类 | `ExampleOntologyJobWorker` |

---

## 1. 架构总览与分层

```
┌──────────────────────────────────────────────────────────────┐
│ 前端层（Web 页面）                                              │
│   static/index.html（披萨类型选择 → 启动流程 → 轮询结果）        │
└───────────────▼──────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ API 层（REST Controller）                                       │
│   ExampleController（POST /api/example/baking 启动、GET .../{key} 查询）│
│   OntologyInvocationController（Utilities 提供，见 §10）：/api/ontology/* │
└───────────────▼──────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ 编排层（BPMN，部署到 Zeebe）                                    │
│   myPizzaBakingProcess.bpmn (PizzaMakingStandardProcess)       │
│   15 个 serviceTask + 6 个排他网关，由引擎按拓扑驱动             │
└───────────────▲──────────────────────────────────────────────┘
                │ jobType 字面量绑定（BPMN type ⇄ @JobWorker(type)）
┌───────────────┴──────────────────────────────────────────────┐
│ 应用层（Spring Boot 应用 = JobWorker）                          │
│   ExampleOntologyJobWorker（2 个 @JobWorker）                   │
│   ExampleApplication（入口，Profile=ExampleBPMN）               │
└───┬─────────────────────────┬──────────────────┬──────────────┘
    │                         │                  │
┌───▼─────────────┐  ┌────────▼────────┐  ┌──────▼────────────┐
│ 领域/本体层      │  │ 数据访问层       │  │ 消息层            │
│ OWL TBox+ABox   │  │ Ontop OBDA      │  │ RabbitMQ          │
│ Openllet 推理    │  │ → MySQL         │  │ 低库存告警外发     │
│ SWRL 规则(5)     │  │ 3 条映射         │  │                   │
└─────────────────┘  └─────────────────┘  └───────────────────┘
```

- **前端层**负责交互与结果展示；**API 层**负责流程启动 / 状态查询（业务端点 + 通用调用端点）；**编排层**管流程拓扑；**应用层**管每步计算；**本体层**管约束；**数据层**提供实例数据；**消息层**做异步外发。
- 七者通过 **HTTP / jobType 字面量 / 本体 IRI / OBDA 映射** 松耦合连接。

---

## 2. 技术栈与模块依赖

### 2.1 关键依赖（`pom.xml`）

| 依赖 | 版本 | 作用 |
|---|---|---|
| `spring-boot-starter-parent` | 4.1.0 | 父 POM |
| `spring-boot-starter` / `-web` | 4.1.0 | 应用容器与 Web 服务器 |
| `io.camunda:spring-boot-starter-camunda-sdk` | 8.9.6 | Camunda 8 / Zeebe 客户端 |
| `io.camunda:zeebe-process-test-extension` | 8.9.6 | 内存引擎端到端测试 |
| `com.ocean:OpenlletResolver` | 0.0.1-SNAPSHOT | OWL API + Openllet 推理能力 |
| `com.ocean:OntopOBDAHandler` | 0.0.1-SNAPSHOT | Ontop OBDA 映射与 SPARQL/SQL 执行 |
| `com.ocean:Utilities` | 0.0.1-SNAPSHOT | 共享工具（`ProcessOrchestrator` / `RabbitMqHandler` / `OntologyWorkerSupport` / **`OntologyInvocationController`**） |
| `spring-boot-starter-amqp` | 4.1.0 | RabbitMQ |
| `org.apache.jena:jena-arq` / `jena-rdfconnection` | 6.1.0 | RDF 处理 |
| OWL API | 5.5.1 | `owlapi-parsers` |

### 2.2 模块间依赖

```
OntologyFrameworkExample (Example)
        │ compile
        ├── OpenlletResolver        （推理：OWL API + Openllet）
        ├── OntopOBDAHandler        （虚拟数据：Ontop OBDA → MySQL）
        └── Utilities               （共享工具 + 通用调用端点：ProcessOrchestrator / RabbitMqHandler / OntologyWorkerSupport / OntologyInvocationController）
        ✗  不依赖 OntologyFrameworkPizza
```

---

## 3. 组件与包结构

### 3.1 主代码（`src/main/java/com/ocean/ontologyframework/example/`）

| 文件 | 职责 |
|---|---|
| `ExampleApplication.java` | `@SpringBootApplication` + `@ComponentScan("com.ocean")`；`setAdditionalProfiles("ExampleBPMN")` 激活 worker Profile |
| `ExampleOntologyJobWorker.java` | `@Component @Profile("ExampleBPMN")`，继承 `OntologyWorkerSupport`；**2 个 `@JobWorker`**（`query-property-value-ontology`、`get-component`，均 `autoComplete=false`） |
| `web/ExampleController.java` | `@RestController`；`POST /api/example/baking` 启动流程；`GET /api/example/baking/{key}` 查询状态与结果；`GET /api/example/health` |

> **`@ComponentScan("com.ocean")` 的连带效应**：`Utilities` 中的 `OntologyInvocationController`（通用调用端点 `/api/ontology/*`）与各 `@Component` 会被一并装配（见 §10）。这是 Example 无需改代码即具备「被 Python 集成」能力的根因。

### 3.2 前端（`src/main/resources/static/`）

| 文件 | 职责 |
|---|---|
| `index.html` | 披萨类型选择页面（3 个按钮），启动流程后每秒轮询状态，流程完成时显示最终结果 |

### 3.3 环境启停脚本（模块根）

| 文件 | 职责 |
|---|---|
| `start-env.cmd` | 一键拉起 `application.yaml` 中**已声明**的服务（MySQL / Ontop / Camunda / RabbitMQ **按需**；未声明者 `[SKIP]`）；交互询问是否 `init` 重建 |
| `EnvPrepare-0.0.1-SNAPSHOT.jar` | 环境启动器（与脚本同目录） |

### 3.4 IDEA 运行配置（`OntologyFrameworkExample/.idea/runConfigurations/ExampleApplication.xml`）

- 主类 `com.ocean.ontologyframework.example.ExampleApplication`；
- `Working directory = $MODULE_WORKING_DIR$`；
- VM options：`--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow`。

---

## 4. BPMN 节点 ⇄ JobWorker 契约表（核心）

`myPizzaBakingProcess.bpmn`（processId=`PizzaMakingStandardProcess`）含 **15 个 serviceTask + 6 个排他网关 + 1 开始 / 1 结束事件**；serviceTask 经 `zeebe:taskDefinition type` 绑定到 `ExampleOntologyJobWorker` 的 `@JobWorker`。

| # | 元素 ID | 节点名称 | jobType / 类型 |
|---|---|---|---|
| — | `Start_01` | 流程开始 | startEvent |
| 1 | `Task_DoughMix` | 面团原料准备与揉制 | `query-property-value-ontology` |
| 2 | `Task_DoughFerment` | 面团分割排气与二次醒发 | `query-property-value-ontology` |
| 3 | `Task_CrustForm` | 饼底手工成型 | `query-property-value-ontology` |
| 4 | `Task_GetSauce` | 获取酱汁 | `get-component` |
| — | `Gateway_SauceChoice` | 酱汁类型 | exclusiveGateway |
| 5 | `Task_TomatoSauce` | 铺番茄红酱 | `query-property-value-ontology` |
| 6 | `Task_WhiteSauce` | 铺白酱 | `query-property-value-ontology` |
| — | `Gateway_SauceMerge` | 酱汁汇合 | exclusiveGateway |
| 7 | `Task_GetCheese` | 获取奶酪 | `get-component` |
| — | `Gateway_CheeseChoice` | 是否添加奶酪？ | exclusiveGateway |
| 8 | `Task_BaseCheese` | 基础奶酪铺设 | `query-property-value-ontology` |
| 9 | `Task_GetTopping` | 获取顶层配料 | `get-component` |
| — | `Gateway_0xkqhps` | 是否有顶层配料 | exclusiveGateway |
| 10 | `Task_ToppingSpread` | 顶层配料铺设 | `query-property-value-ontology` |
| 11 | `Task_GetTopCheese` | 获取封层 | `get-component` |
| — | `Gateway_TopCheeseChoice` | 是否需要封层？ | exclusiveGateway |
| 12 | `Task_TopCheese` | 补铺表层奶酪封层 | `query-property-value-ontology` |
| — | `Gateway_TopCheeseMerge` | 封层汇合 | exclusiveGateway |
| 13 | `Task_Bake` | 高温烘烤成型 | `query-property-value-ontology` |
| 14 | `Task_Decorate` | 出炉后处理与装饰 | `query-property-value-ontology` |
| 15 | `Task_Finish` | 成品切割检验出品 | `query-property-value-ontology` |
| — | `End_01` | 流程结束 | endEvent |

**统计**：`query-property-value-ontology` × **11**、`get-component` × **4**（合计 15）。

> **★ 与旧文档的差异（据实校正）**：旧版《顶层设计》§4.3 列「9 类 JobWorker」、《流程设计》§5 列「9 个 jobType、烘焙流程用 3 种（含 `pizza-summary-generator`）」；**实际代码**：`ExampleOntologyJobWorker` 仅 2 个 `@JobWorker`，BPMN 仅用 2 个 jobType，且 `pizza-summary-generator` / `pizzaSummary` / `myPizzaInstanceIri` **在 Example 模块内不存在**（仅在 `OntologyFrameworkPizza` 中）。本表以**实际 BPMN / Worker** 为准。

---

## 5. 逐节点实现方法与算法（闸门④）

> 本应用为**确定性 Ontop 取数 + 本体推理**，全流程**不经大模型**。两枚 Worker 的算法如下。

### 节点 A · `query-property-value-ontology`（`handleQueryPropertyValue`）

- **作用**：查本体属性值（如工序 `duration`）。用于工艺链的「面团揉制 / 醒发 / 成型 / 铺酱 / 铺料 / 烘烤 / 装饰 / 检验」等 **11** 个节点。
- **输入变量**：`individualIri`、`propertyIri`。
- **算法**：`queryService.queryPropertyValueInOntology(individualIri, propertyIri)` → 输出 `propertyValues`（列表）。
- **失败**：抛 BPMN error `PROPERTY_QUERY_FAILED`。

### 节点 B · `get-component`（`handleGetComponent`）

- **作用**：按披萨类型求所需组件类型 → 取**最低价**实例 → 标签匹配分支词。用于「获取酱汁 / 奶酪 / 顶层配料 / 封层」等 **4** 个节点。
- **输入变量**：`pizzaType`、`targetProperty`、`classPrefix`、`indPrefix`、`candidateLabels`。
- **算法**：
  1. `queryService.getBestMatchedType(pizzaType, targetProperty)` 取所需组件类型集合；
  2. 依次对候选类型执行 SPARQL（经 `OBDAHandler.executeAboxQuery`）查 `rdf:type` + `:price` 实例，取 **price 最小**者；
  3. `OntologyLabelMatcher.resolveMatchedWord(...)` 做标签匹配，得到 `matchedWord`（用于网关分支）。
- **输出变量**：`componentName`、`componentPrice`、`matchedWord`（无组件 / 全无有效实例时降级为 `("没有该组件", 0.0, 候选首标签)`）。
- **失败**：抛 BPMN error `GET_COMPONENT_FAILED`。

> **★14 职责边界**：本体只承载**约束与关系**；**比价取最低价、标签匹配、SPAQRL 拼装等**由程序承担（★12/14）。

---

## 6. 前端与 API 设计

### 6.1 业务端点（`ExampleController`，`@RequestMapping("/api/example")`）

| 接口 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 启动烘焙 | POST | `/api/example/baking` | 入参 `{pizzaType: IRI}`，返回 `{instanceKey, processId, pizzaType}` |
| 查询状态 | GET | `/api/example/baking/{key}` | 返回 `{instanceKey, status, variables}`；`COMPLETED` 时附 `{pizzaSummary, myPizzaInstanceIri, ontologyValidationStatus}`（取不到则为空串） |
| 健康检查 | GET | `/api/example/health` | 返回 `{engineAvailable, processId, bpmnPath}` |

### 6.2 前端交互（`index.html`）

```
用户选择披萨类型（三选一按钮）
  │  POST /api/example/baking {pizzaType}
  │  → {instanceKey, processId, pizzaType}
  ▼
每秒轮询 GET /api/example/baking/{instanceKey}
  ├── status = RUNNING  → 显示"流程运行中"
  ├── status = COMPLETED → 显示最终结果
  └── status = INCIDENT/TERMINATED → 显示错误
```

### 6.3 三种披萨类型

| 披萨类型 | IRI |
|---|---|
| ItalianStyleWhiteSeafoodPizza | `http://example.org/pizza/core/ItalianStyleWhiteSeafoodPizza` |
| MargheritaPizza | `http://example.org/pizza/core/MargheritaPizza` |
| VegetarianPizza | `http://example.org/pizza/core/VegetarianPizza` |

---

## 7. 配置（`application.yaml`）

| 键 | 值 |
|---|---|
| `server.port` | `9083` |
| `camunda.client.grpc-address` / `rest-address` | `http://localhost:26500` / `http://localhost:9080` |
| `ontology.main-path` | `ontology/pizza-all.owl` |
| `ontology.abox-path` | `ontology/pizza-processes-abox.owl` |
| `ontology.obda-path` / `obda-properties-path` | `ontology/database/myPizza.obda` / `myPizza.properties` |
| `ontology.bpmn-path` | `ontology/bpmn/myPizzaBakingProcess.bpmn` |
| `ontology.invoke.enabled` / `base-path` | `true` / `/api/ontology`（§10 通用调用端点） |
| `env-prepare.mysql.db-name` | `mypizzadb` |
| `env-prepare.rabbitmq.exchange` / `queue` / `routing-key` | `example.low-stock.exchange` / `exampleQueue` / `low.stock.alert` |
| `env-prepare.ontop-port` / `camunda-port` / `camunda-grpc-port` | `8080` / `9080` / `26500` |

> **Profile**：应用入口激活 `ExampleBPMN`（`ExampleOntologyJobWorker` 依赖该 Profile）；`ExampleSwrlRabbitMqTest` 使用 `ExampleJunitTest`（不激活 worker，直接操作 `BackendService`）。

---

## 8. 解耦与端到端数据流

### 8.1 解耦机制

- **前端 ↔ API ↔ 引擎**：前端 HTTP POST 启动、每秒 GET 轮询；API 经 `ProcessOrchestrator`（Utilities）与 Camunda 交互，不直接操作 Zeebe API。
- **BPMN ↔ JobWorker**：无代码级引用，靠 `zeebe:taskDefinition type` 与 `@JobWorker(type)` **字面量绑定**。
- **Worker ↔ 本体 / 数据 / 消息**：本体推理经 `BackendService`；数据访问经 `OBDAHandler.executeAboxQuery(sparql)` → Ontop → MySQL；消息外发经 `SwrlRuleTriggerListener` → `RabbitMqHandler.send()`。

### 8.2 端到端数据流

1. 用户选披萨类型 → POST `/api/example/baking` → `ProcessOrchestrator.start()`（变量 `pizzaType`）。
2. 工艺链：`query-property-value-ontology` 查工序耗时；`get-component` 按类型取最低价组件 + 标签匹配。
3. 网关按 `matchedWord`（酱汁 / 奶酪 / 配料 / 封层）分支。
4. 流程变量随节点累积（`propertyValues` / `componentName` / `componentPrice` / `matchedWord` 等）。
5. 结果回显：前端轮询 GET，`COMPLETED` 后展示。
6. 消息外发（异步、旁路）：低库存经 SWRL 推导 → `SwrlRuleTriggerListener` → RabbitMQ。

---

## 9. API 复用声明（★铁律 21）

- 流程编排复用 `Utilities.ProcessOrchestrator`（`deployIfAbsent` / `start` / `fetchInstance` / `statusOf` / `readVariables`），**不重复实现** Camunda 交互。
- 本体推理链初始化复用 `Utilities.OntologyWorkerSupport`（`initOntologyPipeline` 模板方法）。
- 消息外发复用 `Utilities.RabbitMqHandler`；标签匹配复用 `Utilities.OntologyLabelMatcher`。
- **通用调用端点复用 `Utilities.OntologyInvocationController`**（§10），Example 侧**零新增代码**。

---

## 10. Python 集成（本体应用被 Python 调用的能力）

**结论：Example 已具备该能力，无需改动 Java 代码。** 根因：

1. `ExampleApplication` 以 `@ComponentScan("com.ocean")` 扫描，`Utilities` 中的 `OntologyInvocationController` 被自动装配；
2. 该控制器 `@ConditionalOnProperty(name="ontology.invoke.enabled", havingValue="true", matchIfMissing=true)` —— **默认启用**；
3. `ExampleController` 已注入 `CamundaClient`，故控制器亦能拿到客户端，`/api/ontology/health` 返回 `engineAvailable: true`。

**本次为该应用显式补充的落地物（见交付）**：

| 交付物 | 位置 | 说明 |
|---|---|---|
| 配置声明 | `src/main/resources/application.yaml` | 新增 `ontology.invoke.enabled: true` / `base-path: /api/ontology`（默认即启用，显式声明便于查阅 / 调整） |
| 验证脚本 | `OntologyFrameworkPythonClient/verify_example.py` | 模块自带客户端（镜像 `Utilities/python/ontology_client.py`），端到端验证 Python 调用（实测通过，见 `HWCodeArtsDir/example-verify/VERIFICATION-2026-11-02.md`） |

**通用端点（`/api/ontology`，基路径可配）**：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/invoke` | 同步调用：提交输入 → 等流程终态 → 返回 `InvokeResult` |
| POST | `/invoke-async` | 异步启动，返回 `processInstanceKey` |
| GET | `/invoke/{key}` | 查询快照 |
| GET | `/health` | `{component, engineAvailable}` |

**调用契约（Example）**：

| 项 | 值 |
|---|---|
| 流程 ID | `PizzaMakingStandardProcess` |
| 输入 | `{"pizzaType": "<披萨类型 IRI>"}`（可选：流程链内其余变量） |
| 输出（常用） | `componentName`、`componentPrice`、`matchedWord`、`propertyValues` |

**Python 示例**：

```python
from ontology_client import OntologyClient

client = OntologyClient("http://localhost:9083")          # Example 根地址
assert client.health()["engineAvailable"], "引擎未就绪"

result = client.invoke(
    "PizzaMakingStandardProcess",
    {"pizzaType": "http://example.org/pizza/core/MargheritaPizza"},
    timeout_millis=300_000,
    result_keys=["componentName", "componentPrice", "matchedWord", "propertyValues"],
)
if result["success"]:
    print("本体输出：", result["outputs"])                # Python 拿到结果继续执行
else:
    print("未完成：", result["status"], result["message"])
```

> 详细的操作级说明（端点 / 字段 / 状态语义 / 验证复现）见 `Reference/Utilities-Python集成使用说明.md`（以 Example 为例）。

---

## 11. 待确认项

| # | 问题 | 助手建议 | 结论 |
|---|---|---|---|
| Q-A1 | **Worker 数量口径** | 以实际代码为准（2 个 Worker）；旧文档「9 个」为历史设计意图 | ☐ 待确认 |
| Q-A2 | **旧文档是否回改** | 建议同步回改《顶层设计》§4.3、《流程设计》§5 的 Worker / jobType 计数 | ☐ 待确认 |
| Q-A3 | **是否需要恢复 summary 环节** | 若需回显 `pizzaSummary`，需补 `pizza-summary-generator` Worker 与 BPMN 节点 | ☐ 待确认 |

---

## 变更记录

| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-11-02 | 新增《Example应用-应用架构设计》（第 7 步）：以**实际代码**为准（2 Worker / 15 serviceTask / 6 网关），并补 Python 集成章节（§10）；登记与旧文档差异（§4 注、§11） | 码道 |
