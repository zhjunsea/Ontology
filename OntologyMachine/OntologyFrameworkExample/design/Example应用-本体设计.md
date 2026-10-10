# 本体应用框架示例（Example）— 本体设计

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkExample / Example（本体应用框架示例模块） |
| 模块坐标 | `com.ocean:OntologyFrameworkExample:0.0.1-SNAPSHOT` |
| 文档日期 | 2026-10-09 |
| 关联顶层设计 | 《Example应用-顶层设计》 |
| 关联流程 | 《Example应用-流程设计》 |
| 本体资产 | `ontology/{pizza*.owl, pizza-terminology.ttl, catalog-v001.xml}` |
| 数据资产 | `ontology/database/{createdb_tables.ddl, data.sql, myPizza.obda, myPizza.properties}` |
| 推理器 | Openllet（经 `OpenlletResolver` 能力模块） |
| 虚拟数据 | Ontop OBDA → MySQL |
| 复用来源 | Pizza 模块本体体系（`OntologyFrameworkPizza/ontology/`） |

> 本模块本体文件复用自 Pizza 模块，不重复定义。本文档从 Example 模块视角描述本体架构与使用方式。

---

## 1. 概述与设计理念

Example 模块的本体是应用的**业务真相来源**：用 OWL 表达「披萨类别 → 组件构成/取值范围/工艺步骤」的约束，由 Openllet 在运行时推理判定，程序不硬编码业务规则。

**核心设计理念：**

- **本体即约束**：业务约束由 TBox 定义，程序只查询不判断。
- **TBox/ABox 分离**：术语定义（类、属性、约束）与实例断言（个体）分层组织，TBox 稳定、ABox 可变。
- **数据虚拟化**：运行态数据留在 MySQL，经 Ontop OBDA 映射以 SPARQL 统一访问，不迁入本体文件。
- **规则驱动事件**：SWRL 规则在推理时自动推导低库存类，触发 RabbitMQ 消息外发。

---

## 2. 本体资产组织

```
ontology/
├── pizza-all.owl              ← 总入口（import 全部子本体）
│
├── [TBox — 术语定义]
│   ├── pizza.owl              ← 披萨类层级、对象属性、约束
│   ├── pizza-components.owl   ← 组件类层级、数据属性、低库存类
│   ├── pizza-components-rules.owl ← SWRL 规则（库存预警）
│   └── pizza-processes.owl    ← 工序类、步骤属性
│
├── [ABox — 实例断言]
│   ├── pizza-abox.owl         ← 披萨实例（23 个）
│   ├── pizza-components-abox.owl ← 组件实例（39 个，含价格/库存）
│   └── pizza-processes-abox.owl  ← 工序实例（含耗时）
│
├── [术语]
│   └── pizza-terminology.ttl  ← SKOS 中英术语表
│
├── [解析目录]
│   └── catalog-v001.xml       ← IRI → 本地文件映射
│
├── [流程]
│   └── bpmn/myPizzaBakingProcess.bpmn
│
└── [数据库]
    ├── createdb_tables.ddl    ← 建库建表
    ├── data.sql              ← 初始数据
    ├── myPizza.obda          ← Ontop OBDA 映射
    └── myPizza.properties    ← Ontop 连接配置
```

---

## 3. 命名空间与 import 架构

### 3.1 命名空间

| 模块 | 本体 IRI | 角色 |
|---|---|---|
| core | `http://example.org/pizza/core` | TBox：披萨类体系 |
| components | `http://example.org/pizza/components` | TBox：组件类体系 |
| components-rules | `http://example.org/pizza/components-rules` | TBox：SWRL 规则 |
| processes | `http://example.org/pizza/processes` | TBox：工序类体系 |
| term | `http://example.org/pizza/term` | SKOS 术语 |
| core-abox | `http://example.org/pizza/core-abox` | ABox：披萨实例 |
| components-abox | `http://example.org/pizza/components-abox` | ABox：组件实例 |
| processes-abox | `http://example.org/pizza/processes-abox` | ABox：工序实例 |
| all | `http://example.org/pizza/all` | 总入口 |

### 3.2 import 拓扑

```
pizza-all (总入口)
  ├── imports core          (pizza.owl)
  │     ├── imports components  (pizza-components.owl)
  │     │     └── imports components-rules  (SWRL)
  │     ├── imports processes   (pizza-processes.owl)
  │     └── imports term        (pizza-terminology.ttl)
  ├── imports components
  ├── imports processes
  └── imports processes-abox
```

运行加载入口为 `ontology/pizza-all.owl`（`application.yaml` 的 `ontology.main-path`）。

---

## 4. TBox 架构

### 4.1 披萨类体系（`pizza.owl`）

```
Pizza（顶层，hasCrust 基数=1）
├── SauceCategoryPizza（按酱汁分类）
│   ├── TomatoBasedPizza    （必有番茄酱 + 奶酪）
│   ├── WhitePizza          （必有白酱、必无番茄酱、必有奶酪）
│   ├── MarinaraPizza       （番茄酱、无奶酪）
│   └── NoSaucePizza        （无酱）
├── RegionalStylePizza（按地域分类）
│   ├── ItalianTraditionalPizza（饼底/酱/奶酪各1、配料≥1）
│   ├── AmericanPizza
│   └── ...
├── FlavorBasedPizza（按风味分类）
│   ├── VegetarianPizza         （≡ 全素配料 + 有奶酪）
│   ├── WhiteSaucePizza
│   └── SeafoodPizza
│
├── MargheritaPizza（配料恰1且为罗勒）
├── ItalianStyleWhiteSeafoodPizza（白酱 + 海鲜，多继承叶子）
└── ...
```

**对象属性**：`hasCrust`（Functional）、`hasSauce`（Functional）、`hasCheese`（Functional）、`hasTopping`（可多值），各有 inverseOf。

### 4.2 组件类体系（`pizza-components.owl`）

```
PizzaComponent（根）
├── Crust（饼底，minStockThreshold=20）
│   ├── NeapolitanCrust, ThinCrust, ...（10 个叶子，带厚度区间约束）
├── Cheese（奶酪，minStockThreshold=10）
│   ├── BuffaloMozzarella, LowMoistureMozzarella, ...
├── Sauce（酱汁，minStockThreshold=15）
│   ├── NeapolitanTomatoSauce, WhiteSauce, ...
└── Topping（配料，minStockThreshold=10）
    ├── MeatTopping, VegetableTopping, SeafoodTopping, ...（五类互斥）
```

**数据属性**：`price`、`name`、`supplier`、`stockQuantity`、`status`、`batchNumber`、`purchaseDate`、`shelfLife` 等。

**低库存类**：`LowStockCrust`/`LowStockSauce`/`LowStockCheese`/`LowStockTopping`，由 SWRL 规则推理得到。

### 4.3 工序类体系（`pizza-processes.owl`）

`ProcessStep`（独立工序节点）+ `ProcessExecution`（执行记录），通过 `hasProcessStep` 关联到披萨类。具体工序串接由 BPMN 定义，本体不含顺序/网关。

---

## 5. SWRL 规则（`pizza-components-rules.owl`）

共 5 条规则，用于**库存预警自动推导**：

| 规则 | 条件 | 结论 | 阈值 |
|---|---|---|---|
| Crust 低库存 | `Crust(?x) ∧ stockQuantity(?x, ?q) ∧ lessThan(?q, 20)` | `LowStockCrust(?x)` | 20 |
| Sauce 低库存 | `Sauce(?x) ∧ stockQuantity(?x, ?q) ∧ lessThan(?q, 15)` | `LowStockSauce(?x)` | 15 |
| Cheese 低库存 | `Cheese(?x) ∧ stockQuantity(?x, ?q) ∧ lessThan(?q, 10)` | `LowStockCheese(?x)` | 10 |
| Topping 低库存 | `Topping(?x) ∧ stockQuantity(?x, ?q) ∧ lessThan(?q, 10)` | `LowStockTopping(?x)` | 10 |

规则命中后，`SwrlRuleTriggerListener` 检测新推导个体 → 触发回调 → `RabbitMqHandler.send()` 外发消息到 `example.low-stock.exchange`。

---

## 6. ABox 与数据库双轨实例

本模块存在两套实例来源：

| 来源 | 用途 | 个体命名约定 |
|---|---|---|
| 静态 ABox（`.owl` 文件） | 本体推理、一致性校验、SKOS 交叉校验 | `NeapolitanCrustInstance` 等 |
| 运行态 MySQL（经 OBDA） | 业务数据读写、流程中组件查询 | `MyPizza_<timestamp>` 等 |

两者通过 Ontop OBDA 映射桥接，应用以 SPARQL 统一视角访问。

---

## 7. OBDA 映射与数据库

### 7.1 数据库表

| 表名 | 说明 |
|---|---|
| `pizza_components` | 组件主表（name, type, supplier, price, stock_quantity） |
| `crust_components` | 饼底子表（name, crust_type） |
| `myPizza` | 披萨实例表（type, name, price, production_date, crust_name, cheese_name, sauce_name, topping_name） |

### 7.2 Ontop 端点

- 端点：`http://localhost:8080/sparql`
- 映射文件：`ontology/database/myPizza.obda`（3 条映射）
- 连接配置：`ontology/database/myPizza.properties`
- XML Catalog：`ontology/catalog-v001.xml`（IRI → 本地文件解析）

---

## 8. SKOS 术语表（`pizza-terminology.ttl`）

提供中文标签映射，用于 `OntologyLabelMatcher` 将本体 IRI 匹配到中文关键词：

| 本体属性 | 中文标签 |
|---|---|
| hasCrust | 饼底 |
| hasCheese | 奶酪 |
| hasSauce | 酱汁 |
| hasTopping | 配料 |

`get-component` Worker 通过标签匹配确定流程分支（如"酱汁"匹配后按 `matchedWord` 走番茄/白酱分支）。

---

## 9. Example 模块使用本体的方式

| 场景 | 使用方式 | 涉及组件 |
|---|---|---|
| 流程启动 | 传入披萨类型 IRI 作为流程变量 | `pizzaType` |
| 组件查询 | TBox 推理取约束 → ABox/MySQL 取最低价实例 | `get-component` Worker |
| 属性查询 | 查本体属性值（如工序耗时） | `query-property-value-ontology` Worker |
| 摘要生成 | TBox 一致性校验 + 写入 MySQL | `pizza-summary-generator` Worker |
| 库存预警 | SWRL 推导低库存 → RabbitMQ 外发 | `SwrlRuleTriggerListener` |
| 前端展示 | 流程完成后回显摘要/IRI/验证状态 | `ExampleController` → `index.html` |
