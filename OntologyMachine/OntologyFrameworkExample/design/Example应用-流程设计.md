# 本体应用框架示例（Example）— 流程设计

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkExample / Example（本体应用框架示例模块） |
| 模块坐标 | `com.ocean:OntologyFrameworkExample:0.0.1-SNAPSHOT` |
| 文档日期 | 2026-10-09 |
| 关联顶层设计 | 《Example应用-顶层设计》 |
| 流程文件 | `ontology/bpmn/myPizzaBakingProcess.bpmn` |
| 执行引擎 | Camunda 8 / Zeebe（部署后按拓扑驱动） |
| 绑定 Worker | `ExampleOntologyJobWorker`（`@Profile("ExampleBPMN")`） |

> 依据：`myPizzaBakingProcess.bpmn` 全文、`ExampleOntologyJobWorker.java`、`ExampleController.java`、`index.html`、`application.yaml` 的实际内容。

---

## 1. 概述与范围

本模块含**一条可执行流程**，部署到 Camunda 8 / Zeebe，由引擎按 BPMN 拓扑驱动，`serviceTask` 派发作业给 `ExampleOntologyJobWorker`：

| 流程 | process id | 名称 | 定位 | 角色任务 |
|---|---|---|---|---|
| 烘焙流程 | `PizzaMakingStandardProcess` | 通用披萨标准制作工艺流程 | **全自动**：给定披萨类型，按本体取组件、按 `matchedWord` 分支走完制作链 | 无 userTask（全 serviceTask） |

与 Pizza 模块不同，本模块**不含设计流程**（`myPizzaTypeDesignProcess.bpmn`）和表单文件。

---

## 2. 流程总览

```
【烘焙流程 PizzaMakingStandardProcess】  q=query-property-value-ontology  g=get-component

Start
 → 面团原料准备与揉制 ─ 面团分割排气与二次醒发 ─ 饼底手工成型        (q×3)
 → 获取酱汁 (g) → 〔酱汁类型〕─┬ 铺番茄红酱 (q) ┐
                                └ 铺白酱     (q) ┴→ 〔酱汁汇合〕
 → 获取奶酪 (g) → 〔是否添加奶酪？〕→ 基础奶酪铺设 (q)
 → 获取顶层配料 (g) → 〔是否有顶层配料〕→ 顶层配料铺设 (q)
 → 获取封层 (g) → 〔是否需要封层？〕→ 补铺表层奶酪封层 (q) → 〔封层汇合〕
 → 高温烘烤成型 ─ 出炉后处理与装饰 ─ 成品切割检验出品               (q×3)
 → End
```

**流程共 23 个节点**：1 个开始事件、**15 个 ServiceTask**（其中 `query-property-value-ontology` ×11、`get-component` ×4）、**6 个排他网关**、1 个结束事件。

六个排他网关（依 BPMN）：`Gateway_SauceChoice`（酱汁类型）、`Gateway_SauceMerge`（酱汁汇合）、`Gateway_CheeseChoice`（是否添加奶酪？）、`Gateway_TopCheeseChoice`（是否需要封层？）、`Gateway_TopCheeseMerge`（封层汇合）、`Gateway_0xkqhps`（是否有顶层配料）。

---

## 3. 前端交互设计

### 3.1 交互流程

```
用户选择披萨类型（三选一按钮）
  │
  ▼
POST /api/example/baking {pizzaType: IRI}
  │ 返回 {instanceKey, processId, pizzaType}
  ▼
每秒轮询 GET /api/example/baking/{instanceKey}
  │ 返回 {status, variables, componentName, ...}
  ├── status = RUNNING  → 显示"流程运行中"
  ├── status = COMPLETED → 显示最终结果，流程结束
  └── status = INCIDENT/TERMINATED → 显示错误
```

### 3.2 三种披萨类型

| 披萨类型 | IRI | 特点 |
|---|---|---|
| ItalianStyleWhiteSeafoodPizza | `http://example.org/pizza/core/ItalianStyleWhiteSeafoodPizza` | 白酱海鲜披萨 |
|= MargheritaPizza | `http://example.org/pizza/core/MargheritaPizza` | 番茄酱玛格丽特 |
| VegetarianPizza | `http://example.org/pizza/core/VegetarianPizza` | 蔬菜披萨（无酱汁） |

### 3.3 结果展示

流程完成后，前端展示查询接口返回的流程变量，重点为组件结果：

- **组件名**（`componentName`）、**组件价格**（`componentPrice`）、**匹配标签**（`matchedWord`）：由 `get-component` 产出（烘焙链各取料环节）。
- **本体属性值**（`propertyValues`）：由 `query-property-value-ontology` 产出（工序耗时等）。
- 前端对 `selected*` / `component*` / `matchedWord` 前缀的变量做汇总展示。

> **遗留字段说明（以实际代码为准）**：查询接口 `GET /api/example/baking/{key}` 仍返回 `pizzaSummary` / `myPizzaInstanceIri` / `ontologyValidationStatus` 三个键，但由 `getOrDefault(..., "")` 兜底；Example 无对应 jobType 产出，故恒为空串，前端对空值不渲染。这三个键的历史来源是 `OntologyFrameworkPizza`（其 Worker 含 `pizza-summary-generator`）。

---

## 4. API 接口设计

| 接口 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 启动烘焙 | POST | `/api/example/baking` | 入参 `{pizzaType: IRI}`，返回 `{instanceKey, processId, pizzaType}` |
| 查询状态 | GET | `/api/example/baking/{key}` | 返回 `{instanceKey, status, variables}`；`COMPLETED` 时附带 `pizzaSummary` / `myPizzaInstanceIri` / `ontologyValidationStatus`（Example 中恒为空串，见 §3.3） |
| 健康检查 | GET | `/api/example/health` | 返回 `{engineAvailable, processId, bpmnPath}` |
| 首页 | GET | `/` | 返回 `static/index.html` |

---

## 5. JobWorker 绑定

烘焙流程中 ServiceTask 的 `zeebe:taskDefinition type` 绑定到 `ExampleOntologyJobWorker` 的 `@JobWorker`：

Example **只声明 2 类** jobType，二者均被流程使用：

| jobType | Worker 方法 | 能力 | 流程中使用 |
|---|---|---|---|
| `query-property-value-ontology` | `handleQueryPropertyValue` | 经全局推理器查本体属性值（工序耗时等）→ 回写 `propertyValues` | ✓ ×11 |
| `get-component` | `handleGetComponent` | 解析所需组件类型 → 经 Ontop 取最低价实例 + 标签匹配 → 回写 `componentName`/`componentPrice`/`matchedWord` | ✓ ×4 |

Node → jobType 对照（15 个 ServiceTask）：

| ServiceTask | jobType |
|---|---|
| 面团原料准备与揉制 / 面团分割排气与二次醒发 / 饼底手工成型 | `query-property-value-ontology` |
| 获取酱汁 | `get-component` |
| 铺番茄红酱 / 铺白酱 | `query-property-value-ontology` |
| 获取奶酪 | `get-component` |
| 基础奶酪铺设 | `query-property-value-ontology` |
| 获取顶层配料 | `get-component` |
| 顶层配料铺设 | `query-property-value-ontology` |
| 获取封层 | `get-component` |
| 补铺表层奶酪封层 / 高温烘烤成型 / 出炉后处理与装饰 / 成品切割检验出品 | `query-property-value-ontology` |

> **更正（以实际代码为准）**：本文档早期版本称「实际使用 3 种 jobType（含 `pizza-summary-generator`），其余 6 种保留供扩展」，与实现不符。实测 `ExampleOntologyJobWorker.java` 仅声明 `query-property-value-ontology` 与 `get-component`；`pizza-summary-generator` 及 `insert-component` / `update-individual` / `delete-component` / `query-instances` / `query-property-value-db` / `pizza-limitation` **在 Example 中不存在**，仅 `OntologyFrameworkPizza` 声明。

---

## 6. SWRL 规则与消息外发

### 6.1 触发机制

`SwrlRuleTriggerListener` 监听本体推理结果。当插入低库存组件（stockQuantity < 阈值）时，SWRL 规则自动推导为 `LowStockCrust` 等推导类，Listener 检测到新推导实例后触发回调。

### 6.2 消息投递

```
SWRL 推导 → SwrlRuleTriggerListener 回调 → RabbitMqHandler.send(exchange, routingKey, payload)
  │
  ▼
Exchange: example.low-stock.exchange (direct)
  │ routing-key: low.stock.alert
  ▼
Queue: exampleQueue
```

消息载荷为 JSON：`{name, type, supplier, stockQuantity, price}`，`content_type=application/json`。

---

## 7. 测试设计

| 测试类 | Profile | 测试数 | 说明 |
|---|---|---|---|
| `ExampleBpmnRealEngineTest` | ExampleBPMN | 3 | 三种披萨类型 × 烘焙流程端到端（连真实 Camunda） |
| `ExampleSwrlRabbitMqTest` | ExampleJunitTest | 2 | SWRL 触发回调 + 端到端消息投递验证 |

---

## 8. 变更记录

| 日期 | 版本 | 变更内容 |
|---|---|---|
| 2026-10-09 | v1.0 | 首次整理（依据 BPMN 全文 / 源码 / index.html / application.yaml）。 |
| 2026-11-02 | v1.1 | **校准为实际代码状态**：① 节点数由「15 个节点（10 ServiceTask + 2 网关）」更正为**23 个节点（15 ServiceTask + 6 排他网关）**，并补全网关清单；② §3.3 结果展示更正为组件结果（`componentName`/`componentPrice`/`matchedWord`/`propertyValues`），标注 `pizzaSummary` 等三键为遗留空值；③ §4 查询接口响应字段更正；④ §5 JobWorker 绑定由「9 类」更正为**实际 2 类**（`query-property-value-ontology` ×11、`get-component` ×4），删除 Example 中不存在的 7 类 jobType。依据：`myPizzaBakingProcess.bpmn`、`ExampleOntologyJobWorker.java`、`ExampleController.java`、`index.html`。 |
