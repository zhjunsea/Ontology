# 行业洞察（IndustryInsight）— 顶层设计

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkIndustryInsight / IndustryInsight（行业洞察本体应用） |
| 模块坐标 | `com.ocean:OntologyFrameworkIndustryInsight:0.0.1-SNAPSHOT` |
| 文档日期 | 2026-10-22 |
| 关联流程 | 《IndustryInsight应用-流程设计》；`ontology/bpmn/industryInsightProcess.bpmn` |
| 关联本体 | `ontology/industryinsight-all.owl` |
| 关联架构 | 《IndustryInsight应用-应用架构设计》（第 7 步产出） |
| 关联测试 | `IndustryInsightBpmnRealEngineTest`、`IndustryInsightSwrlRabbitMqTest` |

> 本文件为**第 1 步**产出（场景与目标范围）。按 SKILL 铁律 ★10「有依据」，全文逐条标注出处；凡无公开权威依据之处一律标注 **【待考】**，不臆造、不硬凑；无法由助手确认之点登记于 §5「待业务确认项」，带入第 2 步闸门①一并确认。
> 运行时架构 / 组件 / 数据流等**架构细节**移交第 7 步《IndustryInsight应用-应用架构设计》，本文件不重复。

---

## 1. 概述与定位

`OntologyFrameworkIndustryInsight`（简称 **IndustryInsight**）把华为**「五看三定」战略洞察方法论**落地为一个**可运行的本体应用**：以用户输入的**「行业洞察场景」**为**唯一业务输入**（**任意行业**，例如「面向双碳目标的新能源企业 IT 与云协同数据平台建设」「某地区智慧物流行业机会洞察」等），按 BPMN 流程**围绕该场景**逐环节执行——

> **五看**（洞察输入）：看行业/趋势 → 看市场/客户 → 看竞争 → 看自己 → 看机会
> **三定**（决策输出）：定战略控制点 → 定目标 → 定策略

每个环节由 JobWorker 驱动**本体推理**与 **Ontop 虚拟取数**，逐环节产出**围绕该洞察场景**的结构化洞察素材（即「要洞察的内容」），并在机会点环节做 SPAN 定位与优先级判定；最终由汇总环节**产出一份围绕该场景的洞察报告**（结构化、可追溯；具体载体与落盘形态见 §5 Q1/Q2，待业务确认）。

> **输入/输出契约**：输入 = 一段**行业洞察场景**文本；输出 = 一份**围绕该场景组织、可交付业务方**的**洞察报告**（呈现形态待业务确认，见 §5 Q2）。

它与 `OntologyFrameworkExample`（Example）、`OntologyFrameworkPizza`（Pizza）、`OntologyFrameworkPlumBlossom`（PlumBlossom）是同工程内**并列、独立**的应用模块，共享底层 `OpenlletResolver`、`OntopOBDAHandler`、`Utilities` 三个能力模块。

**核心特征**（与 Example 同构）：

- **本体即约束**：五看三定的分析框架与判据（如 SPAN 定位规则、SMART 目标校验、战略控制点类型判定）由 OWL TBox 表达，Openllet 运行时判定，**非硬编码**。
- **BPMN 管编排**：「五看三定」各分析环节与分支（机会点优先级）定义在 BPMN，部署到 Camunda 8 / Zeebe 由引擎驱动；流程输入为行业洞察场景。
- **Ontop 虚拟取数**：洞察素材（行业趋势 / 市场客户 / 竞争 / 自身 / 机会等）留在 MySQL，经 Ontop OBDA 映射以 SPARQL 视角读写。
- **前端可交互**：Web 页面**输入行业洞察场景**，启动流程并轮询显示洞察摘要与最终洞察报告。
- **消息外发**：高优先级机会点 / 关键风险经 SWRL 推理自动触发 RabbitMQ 消息外发（供战略例会订阅）。

> **一句话定位**：IndustryInsight 是「五看三定」方法论的**通用本体化执行器**——把一套纸面战略洞察框架，变成**输入任意行业场景即可执行**、有流程、有本体约束、有数据支撑、可复现、可追溯的应用。

---

## 2. 范围与边界

### 2.1 在范围

1. **方法论编排**：把「五看三定」拆成若干可执行的流程环节，落成 BPMN `serviceTask`，由 JobWorker 逐环节执行。
2. **洞察对象建模**：行业/趋势、市场/客户、竞争、自身、机会点、战略控制点、目标、策略——各概念的本体建模（类 / 属性 / 约束 / 规则）。
3. **输入 = 行业洞察场景**：前端输入一段**行业洞察场景**文本，流程围绕该场景展开；行业/领域由输入的场景内容表达，不设行业选择网关（**待业务确认**，见 §5 Q3）。
4. **判据本体化**：
   - 机会点 **SPAN 定位**（市场吸引力 × 竞争地位 → 优先级分级）；
   - 目标 **SMART** 校验（具体 / 可衡量 / 可实现 / 相关性 / 有时效）；
   - **战略控制点**类型判定（类型集合待业务确认，见 §5 Q5）。
5. **结构化输出**：汇总环节产出**围绕该洞察场景的洞察报告**（含各环节结论、机会点优先级、控制点、目标、策略），并回显前端。
6. **事件外发**：高优先级机会点 / 关键风险经 SWRL 触发消息外发。

### 2.2 不在范围（显式声明，不建模）

1. **替代人的战略决策**：本应用只做「洞察的结构化与判定辅助」，最终战略决策仍由业务方作出（★20）。
2. **真实一手市场数据采集**：不接入付费数据库 / 一手调研；洞察素材来自**公开信息**，缺失处标 **【待考】**（见 §3）。
3. **财务测算 / 估值 / 财务模型**：不做营收预测与 ROI 精算（如需，另立模块）。
4. **组织人事 / 绩效考核**：不涉及部门编制、KPI 分配等人事内容。
5. **具体行业子赛道的深度技术方案**：如光伏逆变器拓扑、风机控制算法等工程细节，不在洞察层建模。

---

## 3. 业务依据与出处（★10 有依据、不编造）

> 本应用**唯一业务真相来源** = 用户提供的「五看三定」方法论 + 公开权威资料。凡引用公开资料，标注来源；凡无公开权威依据的具体数值 / 事实，一律标注 **【待考】**，**不写入本体、不作硬编码**。

### 3.1 方法论依据（用户提供）

- 华为**「五看三定」**方法论：脱胎于 IBM 业务领先模型（BLM），是华为战略规划（SP）与年度业务计划（BP）的核心工具。**来源：用户本次输入**。
  - 五看：看行业/趋势、看市场/客户、看竞争、看自己、看机会。
  - 三定：定战略控制点、定目标、定策略。
  - 相关工具：**SPAN 图**（战略定位分析）、**SMART** 原则。

### 3.2 领域背景依据（公开资料，供第 2/4 步引用）

> 本应用**泛化为任意行业**，故不再预置特定行业背景事实。凡后续步骤需要引用的行业事实/数值，**一律要求业务方提供带出处的资料**（或引用公开权威文档并标注来源）；未经确认的具体数字一律 **【待考】**，不写入本体。

- **来源：用户本次输入**（方法论为通用框架，不绑定特定行业）。

### 3.3 「有依据」的落地口径

- 每个本体公理（类 / 属性 / 约束 / SWRL）在设计文档中标注**出处**（用户输入 / 公开资料 / 【待考】）；
- 【待考】项**不进入本体**，仅在设计文档中登记；
- 禁止「为让测试通过」而改本体或编造数据（★8/9）。

---

## 4. 技术栈与模块依赖（规划）

### 4.1 关键依赖（拟同 Example 对齐，最终以第 7 步 `pom.xml` 为准）

| 依赖 | 版本 | 作用 |
|---|---|---|
| `spring-boot-starter-parent` | 4.1.0 | 父 POM |
| `spring-boot-starter` / `-web` | 4.1.0(父) | 应用容器与 Web 服务器 |
| `io.camunda:spring-boot-starter-camunda-sdk` | 8.9.6 | Camunda 8 / Zeebe 客户端 |
| `com.ocean:OpenlletResolver` | 0.0.1-SNAPSHOT | OWL API + Openllet 推理能力 |
| `com.ocean:OntopOBDAHandler` | 0.0.1-SNAPSHOT | Ontop OBDA 映射与 SPARQL/SQL 执行 |
| `com.ocean:Utilities` | 0.0.1-SNAPSHOT | 共享工具（ProcessOrchestrator / RabbitMqHandler 等） |
| `spring-boot-starter-amqp` | 4.1.0 | RabbitMQ |
| `org.apache.jena:jena-arq` / `jena-rdfconnection` | 6.1.0 | RDF 处理 |

### 4.2 模块间依赖

```
OntologyFrameworkIndustryInsight (IndustryInsight)
        │ compile
        ├── OpenlletResolver        （推理：OWL API + Openllet）
        ├── OntopOBDAHandler        （虚拟数据：Ontop OBDA → MySQL）
        └── Utilities               （共享工具：ProcessOrchestrator / RabbitMqHandler / OntologyWorkerSupport）
        ✗  不依赖其它应用模块
```

---

## 5. 待业务确认项（带入第 2 步闸门①）

| # | 待确认项 | 助手建议 | 结论 |
|---|---|---|---|
| Q1 | **应用简称与模块名** | IndustryInsight / `OntologyFrameworkIndustryInsight` | ✅ 已确认（2026-10-22 用户答复） |
| Q2 | **输出形态** | 汇总环节产出「围绕该场景的洞察报告」；载体（直接 Markdown 报告 / 供大模型生成报告的 SKILL / 二者结合）**待业务确认** | 待定 |
| Q3 | **是否保留「行业/领域」作为显式维度** | 泛化为「输入场景表达行业」，不设行业选择网关 | 待定 |
| Q4 | **洞察场景的字段口径** | 场景文本（必填）+ 可选补充（行业 / 地域 / 时间窗）【待考：以业务方口径为准】 | 待定 |
| Q5 | **战略控制点类型集合** | 技术领先 / 成本优势 / 品牌 / 市场份额 / 价值链控制（一般口径）【待考：以业务方口径为准】 | 待定 |
| Q6 | **机会点优先级分级口径** | SPAN 象限 → 高/中/低三档【待考：分档阈值以业务方为准】 | 待定 |
| Q7 | **目标 SMART 判据的量纲** | 仅校验「是否具备可量化指标 / 时限 / 相关性」，具体数值由业务填入【待考】 | 待定 |
| Q8 | **消息外发触发条件** | 出现「高优先级机会点」或「关键风险」时外发 | 待定 |
| Q9 | **命名细则** | 端口 `9087`、Profile `IndustryInsightBPMN` / `IndustryInsightJunitTest`、库名 `industryinsight`、IRI 前缀 `.../industryinsight#`（端口冲突将于第 7 步前校验） | 待定 |
| Q10 | **洞察结论的展示与留档口径** | 汇总环节产出洞察报告并落盘，前端展示摘要与报告路径 | 待定 |

---

## 6. 关键文件清单（规划，最终以实际产出为准）

- 设计：`design/IndustryInsight应用-{顶层设计, 流程设计, 本体顶层设计, 本体核心设计, 应用架构设计, 测试设计文档}.md`
- 主代码：`src/main/java/.../industryinsight/{IndustryInsightApplication, IndustryInsightOntologyJobWorker, web/IndustryInsightController}.java`
- 前端：`src/main/resources/static/index.html`
- 配置：`src/main/resources/application.yaml`
- 流程：`ontology/bpmn/industryInsightProcess.bpmn`
- 本体：`ontology/{industryinsight*.owl, industryinsight-terminology.ttl, catalog-v001.xml}`
- 数据库：`ontology/database/{industryinsight.obda, industryinsight.properties, createdb_tables.ddl, data.sql}`
- 测试：`src/test/java/.../industryinsight/{AllIndustryInsightSuiteTest, IndustryInsightBpmnRealEngineTest, ...}.java`

---

## 闸门⓪留痕

| 日期 | 定稿版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-22 | v1.0 | 业务方（用户） | **通过**。同意按本稿「目标与范围」定稿。§5 中 Q1 已确认，**Q2–Q10 待第 2 步《流程设计》一并确认**（不阻塞第 1 步定稿）。 |

---

## 变更记录

| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-10-22 | 第 1 步初稿：场景与目标范围（由 NEInsight 泛化为 IndustryInsight 重建） | — |
| 2026-10-22 | 闸门⓪通过，目标与范围定稿（Q2–Q10 带入第 2 步） | 业务方（用户） |
