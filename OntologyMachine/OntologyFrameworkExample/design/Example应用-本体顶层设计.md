# 本体应用框架示例（Example）— 本体顶层设计

> **用法**：本文件产出**第 3 步**。**闸门②**：业务人员确认本体顶层设计后方可进入第 4 步（核心类/属性/关系设计）。
> **拆分来源**：由《Example应用-本体设计.md》拆分而来（原「理念 / 资产组织 / 命名空间 / 顶层类框架 / 分层策略」章节）。
> **基线**：`ontology/{pizza*.owl, pizza-terminology.ttl, catalog-v001.xml}`、`OntologyFrameworkPizza/ontology/`（复用来源）、`src/main/resources/application.yaml`。

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkExample / Example（本体应用框架示例模块） |
| 模块坐标 | `com.ocean:OntologyFrameworkExample:0.0.1-SNAPSHOT` |
| 文档日期 | 2026-11-02 |
| 文档版本 | **v1.0（自《Example应用-本体设计》拆分）** |
| 关联顶层设计 | 《Example应用-顶层设计》 |
| 关联流程 | 《Example应用-流程设计》；`ontology/bpmn/myPizzaBakingProcess.bpmn`（processId=`PizzaMakingStandardProcess`） |
| 关联核心设计 | 《Example应用-本体核心设计》（第 4 步，本文件下游） |
| 本体资产 | `ontology/{pizza.owl, pizza-components.owl, pizza-components-rules.owl, pizza-processes.owl, catalog-v001.xml}` + ABox `pizza-{abox,components-abox,processes-abox}.owl` + 术语 `pizza-terminology.ttl` |
| 推理器 | Openllet（经 `OpenlletResolver` 能力模块） |
| 虚拟数据 | Ontop OBDA → MySQL（库名 `mypizzadb`，映射 `ontology/database/myPizza.obda`，3 条映射） |
| 复用来源 | Pizza 模块本体体系（`OntologyFrameworkPizza/ontology/`） |

> 本模块本体文件**复用自 Pizza 模块**，不重复定义。本文档从 Example 模块视角描述本体架构与使用方式，逐条标注出处；凡无权威依据之处标 **【待考】**。

---

## 1. 概述与设计理念

Example 模块的本体是应用的**业务真相来源**：用 OWL 表达「披萨类别 → 组件构成/取值范围/工艺步骤」的约束，由 Openllet 在运行时推理判定，**程序不硬编码业务规则**。

**核心设计理念：**

- **本体即约束**：业务约束由 TBox 定义，程序只查询不判断。
- **TBox/ABox 分离**（铁律 11）：术语定义（类、属性、约束）与实例断言（个体）分层组织，TBox 稳定、ABox 可变。
- **数据虚拟化**：运行态数据留在 MySQL，经 Ontop OBDA 映射以 SPARQL 统一访问，**不迁入本体文件**。
- **规则驱动事件**：SWRL 规则在推理时自动推导低库存类，触发 RabbitMQ 消息外发。

> **一句话定位**：Example 本体是「披萨制作与烘焙」的**约束化知识底座**——把「按类型取组件、按约束决策」的纸面业务规则，变成**有类约束、可推理、可复现**的本体框架，并作为新本体应用的**同构模板**。

---

## 2. 设计范围与边界

### 2.1 本体要表达的业务概念域

| # | 概念域 | 承载载体 | 说明 | 出处 |
|---|---|---|---|---|
| 1 | **披萨类别域** | `pizza.owl`（core） | 披萨类层级（按酱汁 / 地域 / 风味分类）与构成约束 | 业务原文《披萨制作和烘培的流程是.txt》 |
| 2 | **组件域** | `pizza-components.owl` | 组件类层级（饼底 / 酱汁 / 奶酪 / 配料）、数据属性（价格 / 库存 / 供应商等） | 同上 |
| 3 | **库存预警域** | `pizza-components-rules.owl` | 低库存类 + SWRL 规则（`stockQuantity` 阈值 → `LowStock*`） | 同上 + 消息外发需求 |
| 4 | **工序域** | `pizza-processes.owl` | 工序步骤 / 执行记录（`ProcessStep` / `ProcessExecution`），由 BPMN 串接 | `myPizzaBakingProcess.bpmn` |
| 5 | **术语域** | `pizza-terminology.ttl`（SKOS） | 中英术语表，供 `OntologyLabelMatcher` 标签匹配 | 流程 `get-component` 标签匹配需求 |

> **注**：披萨类别 / 组件 / 工序三大域承载全部业务约束；库存预警域承载事件规则；术语域承载中文标签映射。

### 2.2 不在本体范围（显式声明，不建模）

| # | 不建模项 | 理由 | 出处 |
|---|---|---|---|
| 1 | **流程编排拓扑（顺序 / 网关 / 条件）** | 由 BPMN 定义，本体不含流程拓扑 | Example §5 同名原则 |
| 2 | **前端交互与结果回显** | 属应用架构；本体仅建模业务概念 | 《Example应用-顶层设计》 |
| 3 | **组件比价 / 最低价选取算法** | 属程序（JobWorker）职责，本体只表达约束 | `get-component` 实现 |
| 4 | **库存阈值判定过程本身** | 阈值以 SWRL 阈值字面量表达，判定由推理器执行 | `pizza-components-rules.owl` |
| 5 | **消息投递通道（Exchange/Queue/Binding）** | 属应用/环境配置，本体不含 | `application.yaml` |
| 6 | **数据库表结构与 OBDA 映射细节** | 属数据层（第 11 步），本体经同 IRI 命名空间桥接 | `myPizza.obda` |

> **边界原则**：本体只表达「业务概念及其约束 / 关系」；**流程拓扑归 BPMN、运行态数据归 MySQL（经 OBDA）、交互与比价归程序、消息通道归环境**——均不写死进本体。

---

## 3. 本体资产组织

```
ontology/
├── pizza-all.owl              ← 总入口（import 全部子本体 + 工序 ABox）
│
├── [TBox — 术语定义]
│   ├── pizza.owl              ← 披萨类层级、对象属性、约束（core）
│   ├── pizza-components.owl   ← 组件类层级、数据属性、低库存类
│   ├── pizza-components-rules.owl ← SWRL 规则（库存预警，5 条）
│   └── pizza-processes.owl    ← 工序类、步骤属性
│
├── [ABox — 实例断言]
│   ├── pizza-abox.owl         ← 披萨实例
│   ├── pizza-components-abox.owl ← 组件实例（含价格/库存）
│   └── pizza-processes-abox.owl  ← 工序实例（含耗时）
│
├── [术语]
│   └── pizza-terminology.ttl  ← SKOS 中英术语表
│
├── [解析目录]
│   └── catalog-v001.xml       ← IRI → 本地文件映射（9 条）
│
├── [流程]
│   └── bpmn/myPizzaBakingProcess.bpmn
│
└── [数据库]
    ├── createdb_tables.ddl    ← 建库建表（mypizzadb + 3 表）
    ├── data.sql              ← 初始数据
    ├── myPizza.obda          ← Ontop OBDA 映射（3 条）
    └── myPizza.properties    ← Ontop 连接配置
```

> **形态**：TBox 4 文件 + ABox 3 文件 + SKOS 1 文件；`pizza-all.owl` 为运行加载入口。

---

## 4. 命名空间与 import 架构

### 4.1 命名空间

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

### 4.2 import 拓扑

```
pizza-all (总入口, http://example.org/pizza/all)
  ├── imports core          (pizza.owl)
  │     ├── imports components  (pizza-components.owl)
  │     │     └── imports components-rules  (SWRL)
  │     ├── imports processes   (pizza-processes.owl)
  │     └── imports term        (pizza-terminology.ttl)
  ├── imports components
  ├── imports processes
  └── imports processes-abox   （保留：BPMN 工序耗时经全局推理器查询，依「推理需要即加载」不剥离）
```

> `core-abox` / `components-abox` 的 `owl:imports` **已注释**（TBox/ABox 分离，铁律 11）：其运行态数据由 OBDA(MySQL) 提供，`myPizza.obda` 映射至同 IRI 命名空间；应用侧另经 `ontology.abox-path`（`ontology/pizza-processes-abox.owl`）由独立 `OntologyService` 加载 ABox（扁平合并、**不进全局推理器**）。
>
> 运行加载入口为 `ontology/pizza-all.owl`（`application.yaml` 的 `ontology.main-path`）。

---

## 5. 顶层类层级框架（主干）

> 本步只勾出**主干框架**（根类与主要分支），明细类 / 属性 / 约束留待第 4 步（《本体核心设计》）。

```
【披萨类别域 — pizza.owl（core）】
Pizza（顶层）
├── SauceCategoryPizza（按酱汁分类）
│   ├── TomatoBasedPizza    （番茄酱类）
│   ├── WhitePizza          （白酱类）
│   ├── MarinaraPizza       （番茄酱、无奶酪）
│   └── NoSaucePizza        （无酱）
├── RegionalStylePizza（按地域分类）
│   ├── ItalianTraditionalPizza（意式传统）
│   ├── AmericanPizza
│   ├── ChineseStylePizza
│   └── OtherGlobalPizza
├── FlavorBasedPizza（按风味分类）
│   ├── VegetarianPizza
│   ├── WhiteSaucePizza
│   └── SeafoodPizza
├── NeapolitanPizza / MargheritaPizza / …（具名披萨）
└── ItalianStyleWhiteSeafoodPizza（白酱 + 海鲜，多继承叶子）

【组件域 — pizza-components.owl】
PizzaComponent（根）
├── Crust（饼底；含 10 个叶子：NeapolitanCrust / NewYorkCrust / StLouisCrackerCrust /
│          RomanTondaCrust / FrenchThinCrust / ChicagoDeepDishCrust / DetroitCrust /
│          SicilianCrust / RomanAlTaglioCrust / ArgentinianThickCrust）
├── Sauce（酱汁；TomatoSauce / NeapolitanTomatoSauce / AmericanTomatoSauce / WhiteSauce / OtherSauce）
├── Cheese（奶酪；BuffaloMozzarella / LowMoistureMozzarella / Parmesan / ProvelCheese）
└── Topping（配料；MeatTopping / VegetableTopping / SeafoodTopping / HerbTopping / FruitTopping —— 五类互斥）
LowStockComponent（推理派生类根）
├── LowStockCrust / LowStockSauce / LowStockCheese / LowStockTopping（SWRL 推导，非人工断言）

【工序域 — pizza-processes.owl】
ProcessStep / ProcessExecution（工序节点 / 执行记录）

【术语域 — pizza-terminology.ttl（SKOS）】
skos:ConceptScheme（IRI http://example.org/pizza/term）→ skos:Concept（中英标签，回指本体实体）
```

> **说明**：`Pizza` 的构成约束（`hasCrust` / `hasSauce` / `hasCheese` / `hasTopping`）与各子类的等价 / 约束定义在第 4 步细化；**比价取最低价实例等计算由程序承担**，本体只表达约束与关系。

---

## 6. TBox/ABox 分层策略

| 层 | 内容 | 落点 |
|---|---|---|
| TBox | 类 / 属性 / 约束 / SWRL（披萨类别 + 组件 + 库存预警 + 工序） | `pizza.owl` / `pizza-components.owl` / `pizza-components-rules.owl` / `pizza-processes.owl` |
| ABox | 个体断言（披萨实例、组件实例含价格库存、工序实例含耗时） | `pizza-{abox,components-abox,processes-abox}.owl` + 运行态经 OBDA(MySQL) |
| SKOS | 中英术语（供标签匹配） | `pizza-terminology.ttl` |
| 解析 | IRI → 本地文件映射 | `catalog-v001.xml` |

> **分离落地方式**：`pizza-all.owl` 中 `core-abox` / `components-abox` 的 `owl:imports` 已注释；`processes-abox` 依「推理需要即加载」原则保留于主入口。

---

## 7. 待业务确认项（带入闸门②）

| # | 问题 | 助手建议 | 业务结论 |
|---|---|---|---|
| T1 | **命名空间方案** | 沿用 Pizza 多模块命名空间（core / components / components-rules / processes / term） | ☐ 待确认 |
| T2 | **顶层类框架是否覆盖业务** | 披萨类别域 + 组件域 + 库存预警域 + 工序域 + 术语域；如缺漏请补充 | ☐ 待确认 |
| T3 | **组件库存阈值表达方式** | 用 SWRL 阈值字面量（Crust<20 / Sauce<15 / Cheese<10 / Topping<10） | ☐ 待确认 |
| T4 | **是否需要事件域** | 需要——低库存告警经 SWRL → RabbitMQ 外发 | ☐ 待确认 |
| T5 | **ABox 典型数据范围** | 披萨 3 例 + 组件实例 + 工序实例（示例闭环） | ☐ 待确认 |

---

## 变更记录

| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-11-02 | 由《Example应用-本体设计.md》拆分为「本体顶层设计」（本文件）+「本体核心设计」（第 4 步）；本文件承载理念 / 范围 / 资产组织 / 命名空间 / 顶层类框架 / 分层策略 | 码道 |
