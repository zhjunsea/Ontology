# 本体应用框架示例（Example）— 本体核心设计（核心类、属性与关系）

> **用法**：本文件产出**第 4 步**。**闸门③**：业务人员确认核心类/属性/关系后方可生成第 6 步本体文件。
> **拆分来源**：由《Example应用-本体设计.md》拆分而来（原「TBox / SWRL / ABox / OBDA / SKOS」章节）。
> **上游**：《Example应用-本体顶层设计》（第 3 步）。
> **基线**：`ontology/{pizza.owl, pizza-components.owl, pizza-components-rules.owl, pizza-processes.owl, pizza-*-abox.owl, database/myPizza.obda}`。

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkExample / Example（本体应用框架示例模块） |
| 模块坐标 | `com.ocean:OntologyFrameworkExample:0.0.1-SNAPSHOT` |
| 文档日期 | 2026-11-02 |
| 文档版本 | **v1.0（自《Example应用-本体设计》拆分）** |
| 上游 | 《Example应用-本体顶层设计》（v1.0） |
| 关联流程 | 《Example应用-流程设计》；`ontology/bpmn/myPizzaBakingProcess.bpmn` |
| 命名空间 | core `http://example.org/pizza/core`、components `http://example.org/pizza/components`、components-rules `http://example.org/pizza/components-rules`、processes `http://example.org/pizza/processes` |
| 产出本体 | `ontology/pizza-all.owl`（总入口）+ 4 个 TBox 文件 + 3 个 ABox 文件 |

> **依据（强制）**：本文件逐条标注出处（本体文件 / 流程 / OBDA 映射 / Worker）；无明确依据处标 **【待考】**。约束须写成 `owl:Restriction`，不写于注释；数值属性用数值类型。

---

## 1. 核心类体系（明细）

> 展开自《本体顶层设计》§5 主干框架，细化为**可落库的类层级**。

```
【披萨类别域 · core】
Pizza                                   （顶层类；hasCrust/hasSauce/hasCheese/hasTopping 构成约束）
├── SauceCategoryPizza                  （按酱汁分类）
│   ├── TomatoBasedPizza
│   ├── WhitePizza
│   ├── MarinaraPizza
│   └── NoSaucePizza
├── RegionalStylePizza                  （按地域分类）
│   ├── ItalianTraditionalPizza
│   ├── AmericanPizza
│   ├── ChineseStylePizza
│   └── OtherGlobalPizza
├── FlavorBasedPizza                    （按风味分类）
│   ├── VegetarianPizza
│   ├── WhiteSaucePizza
│   └── SeafoodPizza
├── NeapolitanPizza / GenericNeapolitanPizza / MargheritaPizza
├── ChicagoDeepDishPizza / DetroitPizza / RomanTondaPizza / RomanAlTaglioPizza /
│   SicilianPizza / NewYorkPizza / StLouisPizza / CaliforniaPizza / HawaiianPizza
├── PekingDuckPizza / MalatBeefPizza / DurianPizza / FrenchPizza /
│   ArgentinianPizza / JapaneseKoreanPizza
└── ItalianStyleWhiteSeafoodPizza       （白酱 + 海鲜，多继承叶子）

【组件域 · components】
PizzaComponent                          （组件根）
├── Crust                               （饼底）
│   ├── NeapolitanCrust / NewYorkCrust / StLouisCrackerCrust / RomanTondaCrust /
│   │   FrenchThinCrust / ChicagoDeepDishCrust / DetroitCrust / SicilianCrust /
│   │   RomanAlTaglioCrust / ArgentinianThickCrust        （10 个叶子）
├── Sauce                               （酱汁）
│   └── TomatoSauce（NeapolitanTomatoSauce / AmericanTomatoSauce）/ WhiteSauce / OtherSauce
├── Cheese                              （奶酪）
│   └── BuffaloMozzarella / LowMoistureMozzarella / Parmesan / ProvelCheese
└── Topping                             （配料；五类互斥）
    └── MeatTopping / VegetableTopping / SeafoodTopping / HerbTopping / FruitTopping
LowStockComponent                       （低库存派生根）
└── LowStockCrust / LowStockSauce / LowStockCheese / LowStockTopping   （SWRL 推导）

【工序域 · processes】
ProcessStep                             （工序节点）
ProcessExecution                        （执行记录）
```

### 1.1 受控词表（枚举以子类 / 受控个体表达）

| 词表 | 表达 | 取值 | 出处 |
|---|---|---|---|
| **组件大类** | `PizzaComponent` 子类 | Crust / Sauce / Cheese / Topping | `pizza-components.owl` |
| **配料五类** | `Topping` 子类（互斥） | Meat / Vegetable / Seafood / Herb / Fruit Topping | `pizza-components.owl` |
| **饼底种类** | `Crust` 子类 | 10 种（见 §1） | `pizza-components.owl` |
| **低库存类** | `LowStockComponent` 子类 | LowStockCrust / Sauce / Cheese / Topping | `pizza-components-rules.owl` |
| **工序类型** | `ProcessStep` 受控个体 | 揉制 / 醒发 / 成型 / 烘烤 等 | `pizza-processes-abox.owl` + BPMN |

---

## 2. 对象属性

### 2.1 披萨构成（core）

| 属性 | 特性 | `owl:inverseOf` | 定义域 | 值域 | 出处 |
|---|---|---|---|---|---|
| `hasCrust` | Functional | `isCrustOf` | `Pizza` | `Crust` | `pizza.owl`（`Pizza` 基数=1） |
| `hasSauce` | Functional | `isSauceOf` | `Pizza` | `Sauce` | `pizza.owl` |
| `hasCheese` | Functional | `isCheeseOf` | `Pizza` | `Cheese` | `pizza.owl` |
| `hasTopping` | —（可多值） | `isToppingOf` | `Pizza` | `Topping` | `pizza.owl` |

### 2.2 工序关联（processes）

| 属性 | 定义域 | 值域 | 出处 |
|---|---|---|---|
| `hasProcessStep` | `Pizza` | `ProcessStep` | `pizza-processes.owl` |
| `hasProcessExecution` | `ProcessStep` | `ProcessExecution` | `pizza-processes.owl` |
| `executesStep` | `ProcessExecution` | `ProcessStep` | `pizza-processes.owl` |
| `producesComponent` | `ProcessStep` | `PizzaComponent` | `pizza-processes.owl` |
| `usesComponent` | `ProcessStep` | `PizzaComponent` | `pizza-processes.owl` |

---

## 3. 数据属性

> 类型：数值用 `xsd:decimal`/`xsd:integer`；文本 `xsd:string`；日期 `xsd:dateTime`/`xsd:date`；布尔 `xsd:boolean`。

### 3.1 组件属性（components）

| 属性 | 类型 | 定义域 | 出处 |
|---|---|---|---|
| `crustThicknessMm` | `xsd:decimal` | `Crust` | `pizza-components.owl` |
| `bakingTemperatureCelsius` | `xsd:decimal` | `PizzaComponent`（工艺） | `pizza-components.owl` |
| `bakingTimeSeconds` | `xsd:integer` | `PizzaComponent`（工艺） | `pizza-components.owl` |
| `flourType` | `xsd:string` | `Crust` | `pizza-components.owl` |
| `fermentation` | `xsd:string` | `Crust` | `pizza-components.owl` |
| `status` | `xsd:string` | `PizzaComponent` | `pizza-components.owl` |
| `supplier` | `xsd:string` | `PizzaComponent` | `pizza-components.owl` |
| `batchNumber` | `xsd:string` | `PizzaComponent` | `pizza-components.owl` |
| `price` | `xsd:decimal` | `PizzaComponent` | `pizza-components.owl`（比价用） |
| `purchaseDate` | `xsd:date` | `PizzaComponent` | `pizza-components.owl` |
| `shelfLife` | `xsd:string` | `PizzaComponent` | `pizza-components.owl` |
| `stockQuantity` | `xsd:integer` | `PizzaComponent` | `pizza-components.owl`（SWRL 阈值判据） |
| `isDataValid` | `xsd:boolean` | `PizzaComponent` | `pizza-components.owl` |

> **`name` / `type`**：由 OBDA 映射以 SQL 列形式提供（`myPizza.obda` 的 `{name}` / `{type}`），程序经 SPARQL 以 `:name` / `:type` 访问（`get-component` Worker）；本体侧对应实体名称/类型。

### 3.2 工序属性（processes）

| 属性 | 类型 | 定义域 | 出处 |
|---|---|---|---|
| `operator` | `xsd:string` | `ProcessExecution` | `pizza-processes.owl` |
| `startTime` | `xsd:dateTime` | `ProcessExecution` | `pizza-processes.owl` |
| `endTime` | `xsd:dateTime` | `ProcessExecution` | `pizza-processes.owl` |
| `duration` | `xsd:decimal`（秒） | `ProcessStep` | `pizza-processes.owl`（BPMN 工序耗时查询） |

### 3.3 披萨属性（core）

| 属性 | 类型 | 定义域 | 出处 |
|---|---|---|---|
| `stackingOrder` | `xsd:integer` | `Pizza` | `pizza.owl`（配料叠放次序） |

---

## 4. 约束（`owl:Restriction`）

| 类 | 属性 | 约束 | 出处 |
|---|---|---|---|
| `Pizza` | `hasCrust` | `owl:cardinality 1`（有且仅一个饼底） | `pizza.owl` |
| `Pizza` | `hasSauce` | `owl:cardinality 1` | `pizza.owl` |
| `Pizza` | `hasCheese` | `owl:cardinality 1` | `pizza.owl` |
| `TomatoBasedPizza` | `hasSauce` | 值域限定为番茄酱类（`TomatoSauce` 系列） | `pizza.owl` |
| `WhitePizza` | `hasSauce` | 值域限定为 `WhiteSauce`；且**排除**番茄酱 | `pizza.owl` |
| `MarinaraPizza` | `hasCheese` | `owl:cardinality 0`（无奶酪） | `pizza.owl` |
| `VegetarianPizza` | `hasTopping` | 值域限定为 `VegetableTopping`/`HerbTopping` | `pizza.owl` |
| `PizzaComponent` | `stockQuantity` | 值域 `xsd:nonNegativeInteger` | `pizza-components.owl` |
| `Crust` | `crustThicknessMm` | 值域区间约束（按叶子品种） | `pizza-components.owl` |

> **说明**：披萨类别与组件 / 值域的**硬性约束**以 `owl:Restriction` 落地；组件**库存阈值判定**（如 `stockQuantity < 20`）为**动态比较**，改由 SWRL 判定（§5）。

---

## 5. SWRL 规则（`pizza-components-rules.owl`）

共 **5 条**规则，用于**库存预警自动推导**（`swrl:Imp` = 5）：

| 规则 | 条件（body） | 结论（head） | 阈值 | 出处 |
|---|---|---|---|---|
| **R1** NeapolitanCrust 低库存 | `NeapolitanCrust(?x) ∧ stockQuantity(?x,?q) ∧ swrlb:lessThan(?q,20)` | → `LowStockCrust(?x)` | 20 | 规则1 |
| **R2** Crust 低库存 | `Crust(?x) ∧ stockQuantity(?x,?q) ∧ swrlb:lessThan(?q,20)` | → `LowStockCrust(?x)` | 20 | 规则2 |
| **R3** Cheese 低库存 | `Cheese(?x) ∧ stockQuantity(?x,?q) ∧ swrlb:lessThan(?q,10)` | → `LowStockCheese(?x)` | 10 | 规则3 |
| **R4** Sauce 低库存 | `Sauce(?x) ∧ stockQuantity(?x,?q) ∧ swrlb:lessThan(?q,15)` | → `LowStockSauce(?x)` | 15 | 规则4 |
| **R5** Topping 低库存 | `Topping(?x) ∧ stockQuantity(?x,?q) ∧ swrlb:lessThan(?q,10)` | → `LowStockTopping(?x)` | 10 | 规则5 |

> 规则命中后，`SwrlRuleTriggerListener` 检测新推导个体 → 触发回调 → `RabbitMqHandler.send()` 外发消息到 `example.low-stock.exchange`（routing-key `low.stock.alert` → 队列 `exampleQueue`）。

---

## 6. 关系与约束的一致性检查要点

1. **对象属性互逆配对**：`hasCrust/isCrustOf`、`hasSauce/isSauceOf`、`hasCheese/isCheeseOf`、`hasTopping/isToppingOf` 须成对声明。
2. **函数属性（Functional）**：`hasCrust`、`hasSauce`、`hasCheese` 每实例至多 1 值；`hasTopping` 可多值。
3. **基数闭合**：`Pizza` 的 `hasCrust`/`hasSauce`/`hasCheese` 与 SWRL 结论不冲突。
4. **值域 / 定义域闭合**：`hasCrust` 值域仅 `Crust`；`hasSauce` 值域仅 `Sauce`；`hasCheese` 值域仅 `Cheese`；`hasTopping` 值域仅 `Topping` 五子类。
5. **数值类型**：价格 / 厚度用 `xsd:decimal`；库存 / 耗秒用 `xsd:integer`；时间用 `xsd:dateTime`；布尔用 `xsd:boolean`。
6. **无硬编码业务数据**：库存阈值以 SWRL 字面量表达，程序不含业务分支常量。

---

## 7. ABox 典型个体设计

> 覆盖「披萨实例 → 组件实例（含价格库存）→ 工序实例（含耗时）」的**最小闭环**，足以驱动流程 / 测试。

| 个体 | 类型 | 关键断言 | 出处 |
|---|---|---|---|
| `MargheritaPizza` 等披萨实例 | `Pizza` 子类 | `hasCrust` / `hasSauce` / `hasCheese` / `hasTopping` | `pizza-abox.owl` |
| `NeapolitanCrustInstance` 等 | `Crust` | `stockQuantity`、`price`、`supplier` | `pizza-components-abox.owl` |
| 各组件实例 | `Sauce`/`Cheese`/`Topping` | `price`、`stockQuantity`、`status` | `pizza-components-abox.owl` |
| 工序实例 | `ProcessStep` | `duration`（工序耗时） | `pizza-processes-abox.owl` |
| 运行态披萨实例 | `MyPizza_<timestamp>` | 经 OBDA 映射由 MySQL 提供 | `myPizza.obda` |

> 静态 ABox（`.owl`）用于**推理 / 一致性校验 / SKOS 交叉校验**；运行态 MySQL（经 OBDA）用于**业务数据读写**；两者经 Ontop 映射桥接。

---

## 8. OBDA / SKOS

### 8.1 OBDA 映射与数据库

| 项 | 值 | 出处 |
|---|---|---|
| 数据库 | `mypizzadb` | `createdb_tables.ddl` |
| 表 | `pizza_components`（组件主表）、`crust_components`（饼底子表）、`myPizza`（披萨实例表） | `createdb_tables.ddl` |
| OBDA 映射 | `ontology/database/myPizza.obda`（**3 条**：`component_business`、`crust_mapping`、`mypizza_complete`） | `myPizza.obda` |
| 连接配置 | `ontology/database/myPizza.properties` | — |
| SPARQL 端点 | `http://localhost:8080/sparql` | `application.yaml`（`env-prepare.ontop-port: 8080`） |

### 8.2 SKOS 术语表（`pizza-terminology.ttl`）

| 本体属性 | 中文标签 |
|---|---|
| hasCrust | 饼底 |
| hasCheese | 奶酪 |
| hasSauce | 酱汁 |
| hasTopping | 配料 |

> `get-component` Worker 通过 `OntologyLabelMatcher` 匹配标签（如「酱汁」）确定流程分支（按 `matchedWord` 走番茄 / 白酱分支）。

---

## 9. 待业务确认项

| # | 问题 | 助手建议 | 业务结论 |
|---|---|---|---|
| Q-C1 | **类体系与主干分支是否齐全** | 披萨类别域 + 组件域 + 库存预警域 + 工序域 | ☐ 待确认 |
| Q-C2 | **对象 / 数据属性是否满足各节点所需数据** | 见 §2/§3 | ☐ 待确认 |
| Q-C3 | **约束口径** | 固定值 / 基数用 `owl:Restriction`，动态库存阈值用 SWRL | ☐ 待确认 |
| Q-C4 | **SWRL 规则范围** | R1–R5（库存预警推导）；比价取最低价留程序 | ☐ 待确认 |
| Q-C5 | **典型 ABox 覆盖范围** | §7 最小闭环（披萨 3 例 + 组件 + 工序） | ☐ 待确认 |

---

## 变更记录

| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-11-02 | 由《Example应用-本体设计.md》拆分为「本体顶层设计」+「本体核心设计」（本文件）；本文件承载核心类明细 / 对象属性 / 数据属性 / `owl:Restriction` / SWRL / 一致性检查 / 典型 ABox / OBDA·SKOS | 码道 |
