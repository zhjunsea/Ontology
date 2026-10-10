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
【烘焙流程 PizzaMakingStandardProcess】
Start → （揉制→醒发→成型 三段查询本体时长）
      → 获取酱汁 → 酱汁网关 ─┬─ 白酱 ─┐
                              ├─ 番茄红酱 ─┤→ 汇合 → 获取奶酪 → 基础奶酪铺设
                              └─ 无酱料 ──┘
      → 获取配料 → 顶层配料铺设 → 获取封层 → End
```

流程共 15 个节点：1 个开始事件、10 个 ServiceTask、2 个排他网关（酱汁选择/汇合）、1 个结束事件。

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
  │ 返回 {status, variables, pizzaSummary, ...}
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

流程完成后，前端显示：
- **披萨摘要**（pizzaSummary）：披萨类型 + 各组件选择
- **实例 IRI**（myPizzaInstanceIri）：写入数据库的实例地址
- **本体验证**（ontologyValidationStatus）：TBox 一致性与类数

---

## 4. API 接口设计

| 接口 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 启动烘焙 | POST | `/api/example/baking` | 入参 `{pizzaType: IRI}`，返回 `{instanceKey, processId, pizzaType}` |
| 查询状态 | GET | `/api/example/baking/{key}` | 返回 `{status, variables, pizzaSummary, myPizzaInstanceIri, ontologyValidationStatus}` |
| 健康检查 | GET | `/api/example/health` | 返回 `{engineAvailable, processId, bpmnPath}` |
| 首页 | GET | `/` | 返回 `static/index.html` |

---

## 5. JobWorker 绑定

烘焙流程中 ServiceTask 的 `zeebe:taskDefinition type` 绑定到 `ExampleOntologyJobWorker` 的 `@JobWorker`：

| 流程中使用 | jobType | Worker 方法 | 能力 |
|---|---|---|---|
| ✓ | `query-property-value-ontology` | handleQueryPropertyValue | 查本体属性值（制作耗时） |
| ✓ | `get-component` | handleGetComponent | 查最低价组件 + 标签匹配 |
| ✓ | `pizza-summary-generator` | handlePizzaSummaryGenerator | 生成摘要 + 写库 |
| — | `insert-component` | handleInsertComponent | （流程未使用，供扩展） |
| — | `update-individual` | handleUpdateIndividual | （流程未使用，供扩展） |
| — | `delete-component` | handleDeleteComponent | （流程未使用，供扩展） |
| — | `query-instances` | handleQueryInstances | （流程未使用，供扩展） |
| — | `query-property-value-db` | handleQueryPropertyValueDB | （流程未使用，供扩展） |
| — | `pizza-limitation` | handlePizzaLimitation | （流程未使用，供扩展） |

> 烘焙流程实际使用 3 种 jobType，其余 6 种保留供扩展使用。

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
