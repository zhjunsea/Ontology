# 本体应用框架示例（Example）— 顶层设计

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkExample / Example（本体应用框架示例模块） |
| 模块坐标 | `com.ocean:OntologyFrameworkExample:0.0.1-SNAPSHOT` |
| 文档日期 | 2026-10-09 |
| 关联流程 | 《Example应用-流程设计》；`ontology/bpmn/myPizzaBakingProcess.bpmn` |
| 关联本体 | `ontology/pizza-all.owl`（TBox+ABox 总入口，复用 Pizza 本体） |
| 关联测试 | `ExampleBpmnRealEngineTest`、`ExampleSwrlRabbitMqTest` |

> 依据：`pom.xml`、`src/main/java`、`src/main/resources/application.yaml`、`ontology/`、`src/test/java` 的实际内容。

---

## 1. 概述与定位

`OntologyFrameworkExample`（应用简称 **Example**）是本工程中的**本体应用框架模板模块**，用于展示「前端输入 → BPMN 流程驱动 → JobWorker 本体推理 → 结果回显」的完整链路，可作为新本体应用的脚手架。

它与 `OntologyFrameworkPizza`（Pizza）是同工程内**并列、独立**的应用模块，共享底层的 `OpenlletResolver`、`OntopOBDAHandler` 与 `Utilities` 三个能力模块。Example 复用 Pizza 的本体体系和烘焙流程，但**只使用烘焙流程**（不含 Pizza 的设计流程和表单），并新增了前端页面与 REST Controller。

**核心特征：**

- **本体即约束**：业务约束由 OWL TBox 表达，Openllet 推理器在运行时判定，非硬编码。
- **BPMN 管编排**：流程拓扑定义在 BPMN 中，部署到 Camunda 8 / Zeebe 由引擎驱动。
- **Ontop 虚拟取数**：业务数据留在 MySQL，经 Ontop OBDA 映射以 SPARQL 视角读写。
- **前端可交互**：Web 页面提供披萨类型选择，启动流程并轮询显示最终结果。
- **消息外发**：低库存事件经 SWRL 推理自动触发 RabbitMQ 消息外发。

---

## 2. 技术栈与模块依赖

### 2.1 关键依赖（`pom.xml`）

| 依赖 | 版本 | 作用 |
|---|---|---|
| `spring-boot-starter-parent` | 4.1.0 | 父 POM |
| `spring-boot-starter` / `-web` | 4.1.0(父) | 应用容器与 Web 服务器 |
| `io.camunda:spring-boot-starter-camunda-sdk` | 8.9.6 | Camunda 8 / Zeebe 客户端 |
| `io.camunda:zeebe-process-test-extension` | 8.9.6 | 内存引擎端到端测试 |
| `com.ocean:OpenlletResolver` | 0.0.1-SNAPSHOT | OWL API + Openllet 推理能力 |
| `com.ocean:OntopOBDAHandler` | 0.0.1-SNAPSHOT | Ontop OBDA 映射与 SPARQL/SQL 执行 |
| `com.ocean:Utilities` | 0.0.1-SNAPSHOT | 共享工具（ProcessOrchestrator / RabbitMqHandler 等） |
| `spring-boot-starter-amqp` | 4.1.0 | RabbitMQ |
| `org.apache.jena:jena-arq` / `jena-rdfconnection` | 6.1.0 | RDF 处理 |

### 2.2 模块间依赖

```
OntologyFrameworkExample (Example)
        │ compile
        ├── OpenlletResolver        （推理：OWL API + Openllet）
        ├── OntopOBDAHandler        （虚拟数据：Ontop OBDA → MySQL）
        └── Utilities               （共享工具：ProcessOrchestrator / RabbitMqHandler / OntologyWorkerSupport）
        ✗  不依赖 OntologyFrameworkPizza
```

---

## 3. 运行时架构与分层

```
┌──────────────────────────────────────────────────────────────┐
│ 前端层（Web 页面）                                              │
│   static/index.html（披萨类型选择 → 启动流程 → 轮询结果）        │
└───────────────▼──────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ API 层（REST Controller）                                       │
│   ExampleController（POST /baking 启动、GET /baking/{key} 查询）│
└───────────────▼──────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ 编排层（BPMN，部署到 Zeebe）                                    │
│   myPizzaBakingProcess.bpmn (PizzaMakingStandardProcess)       │
│   由 Camunda 8 / Zeebe 引擎按拓扑驱动，serviceTask 派发作业       │
└───────────────▲──────────────────────────────────────────────┘
                │ jobType 字面量绑定（BPMN type ⇄ @JobWorker(type)）
┌───────────────┴──────────────────────────────────────────────┐
│ 应用层（Spring Boot 应用 = JobWorker）                          │
│   ExampleOntologyJobWorker（9 个 @JobWorker）                   │
│   ExampleApplication（入口，Profile=ExampleBPMN）               │
└───┬─────────────────────────┬──────────────────┬──────────────┘
    │                         │                  │
┌───▼─────────────┐  ┌────────▼────────┐  ┌──────▼────────────┐
│ 领域/本体层      │  │ 数据访问层       │  │ 消息层            │
│ OWL TBox+ABox   │  │ Ontop OBDA      │  │ RabbitMQ          │
│ Openllet 推理    │  │ → MySQL         │  │ 低库存告警外发     │
│ SWRL 规则        │  │ 3 条映射         │  │                   │
└─────────────────┘  └─────────────────┘  └───────────────────┘
```

- **前端层**负责用户交互与结果展示；**API 层**负责流程启动与状态查询；**编排层**管流程拓扑；**应用层**管每步计算；**本体层**管约束；**数据层**提供实例数据；**消息层**做异步外发。
- 七者通过 **HTTP / jobType 字面量 / 本体 IRI / OBDA 映射** 松耦合连接。

---

## 4. 组件与包结构

### 4.1 主代码（`src/main/java/com/ocean/ontologyframework/example/`）

| 文件 | 职责 |
|---|---|
| `ExampleApplication.java` | `@SpringBootApplication` + `@ComponentScan("com.ocean")`；`setAdditionalProfiles("ExampleBPMN")` 激活 worker Profile |
| `ExampleOntologyJobWorker.java` | `@Component @Profile("ExampleBPMN")`，继承 `OntologyWorkerSupport`；9 个 `@JobWorker`（与 Pizza 同构） |
| `web/ExampleController.java` | `@RestController`；POST `/api/example/baking` 启动流程；GET `/api/example/baking/{key}` 查询状态与结果 |

### 4.2 前端（`src/main/resources/static/`）

| 文件 | 职责 |
|---|---|
| `index.html` | 披萨类型选择页面（3 个按钮），启动流程后每秒轮询状态，流程完成时显示最终结果 |

### 4.3 九类 JobWorker（与 Pizza 同构，复用同一套 jobType）

| # | jobType | 能力 |
|---|---|---|
| 1 | `insert-component` | 插入组件个体 |
| 2 | `update-individual` | 更新个体属性 |
| 3 | `delete-component` | 删除个体 |
| 4 | `query-instances` | 按根类检索实例 |
| 5 | `query-property-value-ontology` | 查本体属性值 |
| 6 | `query-property-value-db` | 查数据库属性值 |
| 7 | `get-component` | 求最佳匹配组件 → 取最低价实例 → 标签匹配 |
| 8 | `pizza-limitation` | 解析组件约束与可选项 |
| 9 | `pizza-summary-generator` | 组装摘要 + 一致性校验 + 写库 |

---

## 5. 解耦机制

### 5.1 前端 ↔ API ↔ 流程引擎

前端通过 HTTP POST 启动流程，获取 `instanceKey` 后每秒轮询 GET 接口查询流程状态。API 层通过 `ProcessOrchestrator`（Utilities 模块）与 Camunda 客户端交互，不直接操作 Zeebe API。

### 5.2 BPMN ↔ JobWorker

BPMN 与 JobWorker **无代码级引用**，靠 `zeebe:taskDefinition type` 与 `@JobWorker(type)` 字面量绑定。

### 5.3 Worker ↔ 本体 / 数据 / 消息

- **本体推理**：经 `BackendService` 取 `ReasonerService`/`OntologyService`，Openllet 做 TBox 推理。
- **数据访问**：经 `OBDAHandler.executeAboxQuery(sparql)` → Ontop → MySQL；写入经 `InsertService`/`UpdateService`/`DeleteService` 按表自动拆分。
- **消息外发**：`SwrlRuleTriggerListener` 监听推理结果，触发 `RabbitMqHandler.send()` 外发消息。

---

## 6. 端到端数据流

1. **用户选择**：前端页面选择披萨类型（ItalianStyleWhiteSeafoodPizza / MargheritaPizza / VegetarianPizza）。
2. **启动流程**：POST `/api/example/baking` → `ProcessOrchestrator.start()` 创建流程实例。
3. **本体推理**：Worker 查询 TBox 约束，解析组件类型与可选项。
4. **Ontop 取数**：SPARQL → SQL → MySQL，取组件实例与价格。
5. **计算/写库**：`pizza-summary-generator` 写入 `myPizza` 表，回写流程变量。
6. **结果回显**：前端轮询 GET `/api/example/baking/{key}`，流程 COMPLETED 后显示披萨摘要、实例 IRI、本体验证状态。
7. **消息外发**（异步）：低库存经 SWRL 推理 → `SwrlRuleTriggerListener` → RabbitMQ Exchange → Queue。

---

## 7. 配置与运行

### 7.1 配置（`application.yaml`）

| 键 | 值 |
|---|---|
| `server.port` | `9083` |
| `camunda.client.grpc-address` / `rest-address` | `localhost:26500` / `localhost:9080` |
| `ontology.main-path` | `ontology/pizza-all.owl` |
| `ontology.bpmn-path` | `ontology/bpmn/myPizzaBakingProcess.bpmn` |
| `env-prepare.mysql.db-name` | `mypizzadb` |
| `env-prepare.rabbitmq.exchange` | `example.low-stock.exchange` |
| `env-prepare.rabbitmq.queue` | `exampleQueue` |

### 7.2 Profile

- 应用入口激活 Profile `ExampleBPMN`（`ExampleOntologyJobWorker` 依赖该 Profile）。
- 测试 `ExampleSwrlRabbitMqTest` 使用 `ExampleJunitTest`（不激活 worker，直接操作 BackendService）。

### 7.3 外部依赖

运行需 Camunda 8（`localhost:26500`）、Ontop（`8080`）、MySQL（`3306`）、RabbitMQ（`5672`）就绪。通过 `EnvPrepare start --config application.yaml` 启动环境。

---

## 8. 关键设计决策

1. **模板定位**：本模块作为新本体应用的脚手架，结构清晰可复制。
2. **复用 Pizza 本体**：本体文件和 BPMN 流程复用 Pizza 模块，不重复定义。
3. **TBox/ABox 分离**：`pizza-all.owl` 为总入口；TBox 与 ABox 分层组织。
4. **前端全链路**：从页面输入到流程结束结果展示，完整端到端链路。
5. **SWRL + RabbitMQ**：测试覆盖 SWRL 规则触发与消息外发，验证事件驱动能力。
6. **不含设计流程**：与 Pizza 不同，本模块只含烘焙流程，不含设计流程和表单。

---

## 9. 关键文件清单

- 构建：`pom.xml`
- 主代码：`src/main/java/.../example/{ExampleApplication, ExampleOntologyJobWorker, web/ExampleController}.java`
- 前端：`src/main/resources/static/index.html`
- 配置：`src/main/resources/application.yaml`
- 流程：`ontology/bpmn/myPizzaBakingProcess.bpmn`
- 本体：`ontology/{pizza*.owl, pizza-terminology.ttl, catalog-v001.xml}`
- 数据库：`ontology/database/{myPizza.obda, myPizza.properties, createdb_tables.ddl, data.sql}`
- 测试：`src/test/java/.../example/{ExampleBpmnRealEngineTest, ExampleSwrlRabbitMqTest}.java`
