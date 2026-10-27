# 行业洞察（IndustryInsight）应用 — 应用架构设计

> **用法**：本文件产出**第 7 步**。**闸门④**：业务人员对 **BPMN 的每个节点**逐项确认「**实现方法（载体不限：自然语言描述 / 程序 / 外部调用等）+ 算法**」后方可实现对应 JobWorker。本文件亦承载**闸门⑩（是否使用数据库）**与**闸门⑪（如何导入/生成完整数据）**的留痕。
> **基线**：`OntologyFrameworkExample/design/Example应用-顶层设计.md` §2–6；代码基线 `ExampleOntologyJobWorker.java`、`ExampleApplication.java`、`web/ExampleController.java`、`src/main/resources/application.yaml`。

| 项目 | 内容 |
|---|---|
| 应用 | OntologyFrameworkIndustryInsight / IndustryInsight（行业洞察） |
| 文档日期 | 2026-10-22 |
| 文档版本 | **v1.0** |
| 关联流程设计 | 《IndustryInsight应用-流程设计》（v1.2 定稿） |
| 关联本体设计 | 《IndustryInsight应用-本体顶层设计》（v1.1）+《IndustryInsight应用-本体核心设计》（v1.0）（均已确认） |
| 关联流程文件 | `ontology/bpmn/industryInsightProcess.bpmn`（processId=`IndustryInsightProcess`） |
| 激活 Profile | `IndustryInsightBPMN`（测试 `IndustryInsightJunitTest`） |
| Worker 类 | `IndustryInsightOntologyJobWorker` |

> 依据：定稿流程设计 + 已确认本体设计 + 已生成 BPMN。**算法一律来自业务确认，不得臆造**（★8/9/10）；**本体不含行业事实/数值**，无出处处一律【待考】。

---

## 1. 架构总览与分层

```
┌─────────── 前端层 ───────────┐
│ static/index.html（输入洞察场景 / 轮询展示洞察 SKILL） │
└───────────▼──────────────────┘
┌─────────── API 层 ───────────┐
│ IndustryInsightController（启动/查询/健康） │
└───────────▼──────────────────┘
┌─────────── 编排层（Zeebe）───┐
│ industryInsightProcess.bpmn（IndustryInsightProcess，15 serviceTask + 2 网关）│
└───────────▲──────────────────┘
            │ jobType 字面量绑定
┌───────────┴── 应用层 ────────┐
│ IndustryInsightOntologyJobWorker（15 个 @JobWorker）│
│ IndustryInsightApplication                        │
└──┬──────────┬──────────┬─────┘
┌──▼───────┐┌─▼────────┐┌─▼──────┐
│本体层     ││数据层    ││消息层   │
│OWL/SWRL  ││Ontop→DB  ││RabbitMQ │
└──────────┘└──────────┘└────────┘
            │
   ┌────────▼─────────┐
   │ 外部 LLM（MaaS） │ ← 复用 Utilities 的 ChatAgent（Spring AI ChatClient）
   └──────────────────┘
```

> **LLM 通道（★铁律 21 复用）**：各分析节点统一「经 MaaS 调用大模型」——复用平台 `Utilities` 的 `ChatAgent`（Spring AI `ChatClient` 薄封装，配置走 `spring.ai.openai.*`），**不另写客户端**；system 提示词须显式含「不允许编造、所有内容必须有依据」（★26）。**LLM 未配置/不可用/超时**一律**降级兜底**（确定性启发式或模板化框架、结论置【待考】），**不阻断流程、不内置任何行业词表**。

> **联网检索取证通道（★铁律 10/26）**：各分析节点在分析前可经 `IndustryInsightWebSearch`（复用 `Utilities` 的 `McpClientHandler`，连 Parallel Web Search MCP `web_search`）检索公开资料，作为**可溯源依据**以 `[S#]` 编号注入提示词，并在 SKILL 末尾汇总「参考资料」清单。默认关闭（`industryinsight.websearch.enabled=false`）；关闭/失败/未配置一律返回 `null` 并跳过取证，**不阻断流程、不编造**（呼应设计 Q4「引用带出处公开资料」）。

---

## 2. 技术栈与模块依赖

### 2.1 关键依赖（对齐 `OntologyFrameworkExample/pom.xml`）
| 依赖 | 版本 | 作用 |
|---|---|---|
| `spring-boot-starter-parent` | 3.3.x | 父 POM |
| `io.camunda:spring-boot-starter-camunda-sdk` | 8.5.x | Camunda 8 / Zeebe 客户端 |
| `io.camunda:zeebe-process-test-extension` | 8.5.x | 内存引擎端到端测试 |
| `com.ocean:OpenlletResolver` | 0.0.1-SNAPSHOT | OWL API + Openllet 推理 |
| `com.ocean:OntopOBDAHandler` | 0.0.1-SNAPSHOT | Ontop OBDA → MySQL |
| `com.ocean:Utilities` | 0.0.1-SNAPSHOT | 共享工具（含 `ChatAgent`/`ProcessOrchestrator`/`RabbitMqHandler`） |

> `OntopOBDAHandler` 依赖须保留 `log4j-slf4j2-impl` 排除（对齐 Example）。

### 2.2 模块间依赖
```
OntologyFrameworkIndustryInsight
        ├── OpenlletResolver
        ├── OntopOBDAHandler
        └── Utilities
```

---

## 3. 组件与包结构

### 3.1 主代码（`src/main/java/com/ocean/ontologyframework/industryinsight/`）
| 文件 | 职责 |
|---|---|
| `IndustryInsightApplication.java` | `@SpringBootApplication`；`setAdditionalProfiles("IndustryInsightBPMN")` |
| `IndustryInsightOntologyJobWorker.java` | `@Component @Profile("IndustryInsightBPMN")`，继承 `OntologyWorkerSupport`；**15 个 `@JobWorker`** |
| `web/IndustryInsightController.java` | `@RestController`；`/api/industryinsight/insight`、`/{key}`、`/health`（**已在第 5 步生成**） |

### 3.2 前端（`src/main/resources/static/`）
| 文件 | 职责 |
|---|---|
| `index.html` | 输入「洞察场景」（必填）+ 行业/地域/时间窗（可选），启动后每秒轮询；返回 RUNNING/COMPLETED/INCIDENT，展示洞察报告摘要、分层交付物、报告路径（**已在第 5 步生成**） |

### 3.3 环境启停脚本（模块根 `start-env.cmd`）
> **照 `OntologyFrameworkExample/start-env.cmd` 的形态**生成，一键**准备并启动**运行环境；与 `IndustryInsightApplication` 及 `EnvPrepare-<version>.jar` 同目录。

| 步骤 | 命令 | 说明 |
|---|---|---|
| 1 | `EnvPrepare-<version>.jar start` | **新窗口常驻**拉起 application.yaml 中**已声明**的服务（本应用为 RabbitMQ / Camunda，未声明者自动 `[SKIP]`），并确保已声明的持久化资源（RabbitMQ 资源）**缺则建、有则留**（不删已有数据） |
| 2 | `EnvPrepare-<version>.jar init` | 可选：**清空重建已声明的持久化资源**（本应用为 RabbitMQ 资源）后再常驻启动（危险，含二次确认）——由脚本交互式询问「是否重建」触发 |

- 脚本用 `%~dp0` 定位自身（**不硬编码路径**）、先 `chcp 936 >nul`、`errorlevel` 校验并在失败时 `pause`。
- **须在模块根放置 `EnvPrepare-<version>.jar`（可执行 fat jar）**：脚本以 `%~dp0EnvPrepare-<version>.jar` 定位它，**缺失则运行即报「未找到 EnvPrepare 可执行 jar」而失败**；来源 `OntologyMachine/build/EnvPrepare-<version>.jar` 或 `OntologyFrameworkExample-*-project.zip` 内已注入的同名 jar。
- **停止环境 = 关闭该常驻窗口**。
- **编码/换行硬约束**：`.cmd` 须存 **GBK(ANSI) + CRLF**。

### 3.4 IDEA 运行配置（`<工作区根>/.idea/runConfigurations/IndustryInsightApplication.xml`）
> Spring Boot 类型；主类 = `IndustryInsightApplication`——启动即由 Spring 注册并运行全部 `@JobWorker`。
> ⚠️ **落点：工作区根（IDEA 工程根）的 `.idea/runConfigurations/`**，不要放模块目录内。
> **最关键**：`Working directory` = `$MODULE_WORKING_DIR$`（解析为模块根 `OntologyFrameworkIndustryInsight/`）。

| 项 | 值 |
|---|---|
| 配置名 | `IndustryInsightApplication` |
| 类型 | `SpringBootApplicationConfigurationType`（Spring Boot） |
| `<module name>` | `OntologyFrameworkIndustryInsight` |
| `SPRING_BOOT_MAIN_CLASS` | `com.ocean.ontologyframework.industryinsight.IndustryInsightApplication` |
| `WORKING_DIRECTORY` | `$MODULE_WORKING_DIR$` |
| `VM_PARAMETERS` | `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow` |

---

## 4. BPMN 节点 ⇄ JobWorker 契约表（核心）

> **一一对应**：BPMN 每个 `serviceTask` 的 `zeebe:taskDefinition type` ⇄ 一个 `@JobWorker(type)`。各分析节点统一采用「**MaaS 逐工具分析 → 求同存异 → MaaS 总结 → 兜底**」模式，LLM 不可用即兜底、不阻断（**实现上为降低端到端时延，已将「逐工具分析 + 求同存异」合并为单次 MaaS 调用，每节点共 2 次调用**）。

| # | BPMN 节点 id | jobType | 业务规则（来自流程设计） | 实现方法（载体不限·业务确认） | 算法（业务确认） | 输入变量 | 输出变量 | 确认状态 |
|---|---|---|---|---|---|---|---|---|
| 1 | `Task_ScenarioParse` | `parse-insight-scenario` | 抽取行业/领域、地域、时间窗、洞察目标；校验两要素 | **外部调用**：经 `ChatAgent` 调 MaaS 解析为结构化维度；**降级**：应用兜底（确定性关键词/规则抽取，不内置行业词表） | LLM：JSON 抽取；校验＝「行业」∧「目标」两要素齐备→通过，否则不通过 + 缺失项 | `scenario,industry,region,timeWindow` | `scenarioParsed,validationResult,missingElement,parseSource` | ✅已确认 |
| 1' | `Task_ScenarioClarify` | `return-scenario-clarification` | 据缺失项生成澄清提示并终止 | **程序**：模板映射（缺行业→提示A；缺目标→提示B；过简→提示C） | 确定性模板，无需 LLM | `missingElement` | `clarificationText` | ✅已确认 |
| 2 | `Task_HypothesisTree` | `build-initial-hypothesis-tree` | 提 3–5 核心假设 + 分支 + 待验证点/预期证据；《假设验证日志》框架 | **外部调用**：经 MaaS 生成假设树；**降级**：应用兜底（模板化假设框架、结论置【待考】） | LLM：生成 3–5 假设（行业/市场/竞争/自身/机会分支）；限 3–5 条 | `scenarioParsed` + 背景认知 | `hypothesisTree`（+ 日志框架） | ✅已确认 |
| 3 | `Task_IndustryTrend` | `analyze-industry-trend` | 看行业/趋势（五工具） | **外部调用**：逐工具经 MaaS 分析 + 求同存异（**实现上合并为一次调用**）→ 再经 MaaS 总结；**降级**：兜底 | LLM：五工具各出结论；汇总标共识/分歧；总结产出趋势要点；回填假设日志 | `scenarioParsed,hypothesisTree` + 行业资料 | `industryTrend` | ✅已确认 |
| 4 | `Task_MarketCustomer` | `analyze-market-customer` | 看市场/客户（五工具 + 分层/痛点 + 决策链 DMU） | 同上（逐工具→求同存异→总结） | LLM：STP/$APPEALS/JTBD/KANO/客户旅程 + 分层/痛点〔验证状态〕 + DMU；共识/分歧；总结 | `scenarioParsed,industryTrend` + 市场资料 | `marketCustomer` | ✅已确认 |
| 5 | `Task_Competition` | `analyze-competition` | 看竞争（三工具 + 生态位博弈） | 同上 | LLM：竞争矩阵/蓝军推演/对手战略假设 + 生态位；共识/分歧；总结 | `scenarioParsed,marketCustomer` + 竞争资料 | `competition` | ✅已确认 |
| 6 | `Task_Self` | `analyze-self` | 看自己（四工具） | 同上 | LLM：资源-能力/BMC/VRIO/差距分析；共识/分歧；总结；控制点候选 | `scenarioParsed,competition` + 自身资料 | `selfInsight` | ✅已确认 |
| 7 | `Task_Opportunity` | `identify-opportunities` | 看机会（六工具，含 SPAN/FAN 预筛） | 同上 | LLM：六工具；机会点须可追溯前序洞察（★10）；统一格式清单 + 优先级 | 前序五看洞察 | `opportunityList` | ✅已确认 |
| 8 | `Task_SpanLocate` | `locate-opportunity-span` | 正式 SPAN 定位与优先级分档 | **外部调用 + 程序**：MaaS 辅助定位分档；**降级**：程序按确定性 SPAN 分档规则 | 吸引力（节点4）× 竞争实力（节点5）→ 象限 → 高/中/低；阈值【待考】 | `opportunityList,marketCustomer,competition` | `opportunityPriority,priorityLevel` | ✅已确认 |
| 9 | `Task_SendMessage` | `send-key-insight-message` | G2 触发，高优先级/关键风险外发 | **程序**：确定性组装 + `RabbitMqHandler.send()`；无需 LLM | 组装载荷 `{机会点/风险描述,优先级,关联洞察[],scenarioIri}` 投递 | `opportunityPriority,opportunityList` | `outboundMessage` | ✅已确认 |
| 10 | `Task_CrossValidate` | `cross-validate-hypothesis-plan` | 交叉验证・冲突消解・关键假设/验证计划 | **外部调用**：经 MaaS 生成；**降级**：模板化框架、结论置【待考】 | LLM：一致性矩阵 + 冲突消解 + 待验证假设 + 关键假设清单 + 验证计划 + 假设-洞察映射；只显式化不实做验证 | 五看洞察 + 机会点 + 假设树/日志 | `crossValidation,consistencyMatrix,keyAssumptions,validationPlans` | ✅已确认 |
| 11 | `Task_ControlPoint` | `define-strategic-control-point` | 定战略控制点（聚焦 2–3 个） | **外部调用**：经 MaaS 生成论证；**降级**：程序按类型集合确定性匹配 | LLM：候选清单与筛选矩阵 + 契合矩阵 + 护城河（含领先指标）+ 业务设计 BLM；限 2–3 个 | `opportunityPriority,selfInsight,competition,keyAssumptions` | `controlPoints` | ✅已确认 |
| 12 | `Task_Objective` | `define-objective` | 定目标（SMART 校验） | **外部调用**：经 MaaS 生成校验；**降级**：程序 SMART 确定性齐备性校验 | LLM：SMART 五要素 + 对齐矩阵 + 分解里程碑 + BSC + OGSM；数值由业务填入，缺失【待考】 | `controlPoints,opportunityPriority,keyAssumptions` | `objectives` | ✅已确认 |
| 13 | `Task_Strategy` | `define-strategy` | 定策略（含定节奏/路径 + 资源匹配度） | **外部调用**：经 MaaS 生成论证；**降级**：保留框架、结论置【待考】 | LLM：关键举措/资源投入/风险+里程碑/安索夫/4P/战略解码表 + 路径节奏 + 洞察-策略映射（≥1 洞察）+ 资源匹配度 | `controlPoints,objectives,competition,selfInsight,keyAssumptions` | `strategies,onePageStrategy,keyInsightConversionTable` | ✅已确认 |
| 14 | `Task_Report` | `generate-insight-skill` | 生成洞察 SKILL（可执行指令文件：角色设定 + 任务指令 + 五看三定素材 + 来源） | **程序**：结构化 Markdown 拼装落盘（不额外经 MaaS 重写） | 不引入新事实；保留出处/【待考】与分级/置信度；素材缺失显式列出 | 全部环节结论 | `insightSummary,deliverables,insightSkillPath,ontologyValidationStatus` | ✅已确认 |

---

## 5. 逐节点实现方法与算法确认（闸门④）

> 对**每个节点**记录：确认的问题、业务给出的**实现方法**（载体不限）与**算法口径**、边界处理。**未确认不得实现该 Worker。**

| 节点 id | 确认问题 | 实现方法（载体与内容） | 业务结论（算法口径） | 边界/异常处理 | 确认人 | 日期 |
|---|---|---|---|---|---|---|
| `Task_ScenarioParse` | 解析与校验口径？ | 外部调用（MaaS/`ChatAgent`）+ 应用兜底 | 两要素齐备→通过 | LLM 不可用→兜底；缺要素→不通过 | 业务方 | 2026-10-22 |
| `Task_ScenarioClarify` | 提示口径？ | 程序（模板） | 三类模板 | — | 业务方 | 2026-10-22 |
| `Task_HypothesisTree` | 假设树口径？ | 外部调用 + 兜底 | 3–5 假设 + 待验证点/证据 | LLM 不可用→模板化 | 业务方 | 2026-10-22 |
| `Task_IndustryTrend` | 五工具口径？ | 外部调用（逐工具→求同存异→总结，**实现合并为 2 次调用**）+ 兜底 | 五工具共识/分歧 | 无出处→【待考】 | 业务方 | 2026-10-22 |
| `Task_MarketCustomer` | 五工具+DMU 口径？ | 同上 | 五工具 + 分层/痛点 + DMU | 同上 | 业务方 | 2026-10-22 |
| `Task_Competition` | 三工具+生态位口径？ | 同上 | 三工具 + 生态位 | 同上 | 业务方 | 2026-10-22 |
| `Task_Self` | 四工具口径？ | 同上 | 四工具 + 控制点候选 | 同上 | 业务方 | 2026-10-22 |
| `Task_Opportunity` | 六工具口径？ | 同上 | 六工具（SPAN/FAN 预筛） | 机会点须可追溯 | 业务方 | 2026-10-22 |
| `Task_SpanLocate` | 分档口径？ | 外部调用 + 程序兜底 | 两轴→象限→高/中/低 | 阈值【待考】 | 业务方 | 2026-10-22 |
| `Task_SendMessage` | 触发口径？ | 程序（确定性） | 高优先级/关键风险外发 | 中/低不外发 | 业务方 | 2026-10-22 |
| `Task_CrossValidate` | 交叉验证口径？ | 外部调用 + 兜底 | 六项显式化；不实做验证 | LLM 不可用→模板化 | 业务方 | 2026-10-22 |
| `Task_ControlPoint` | 控制点口径？ | 外部调用 + 兜底 | 聚焦 2–3 个 | 类型集合内 | 业务方 | 2026-10-22 |
| `Task_Objective` | SMART 口径？ | 外部调用 + 兜底 | 五要素齐备性校验 | 数值【待考】 | 业务方 | 2026-10-22 |
| `Task_Strategy` | 策略口径？ | 外部调用 + 兜底 | 含节奏/路径 + 映射 + 资源匹配度 | 每条策略≥1 洞察 | 业务方 | 2026-10-22 |
| `Task_Report` | SKILL 口径？ | 程序（确定性拼装） | 角色设定 + 任务指令 + 内嵌五看三定素材与来源（输入大模型即得洞察报告） | 素材缺失显式列出 | 业务方 | 2026-10-22 |

---

## 6. 前端与 API 设计

| 端点 | 方法 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| `/api/industryinsight/insight` | POST | `{scenario, industry?, region?, timeWindow?}` | `{instanceKey, processId, scenario}` | 启动流程 |
| `/api/industryinsight/insight/{key}` | GET | path `key` | `{instanceKey, status, variables, insightSummary?, deliverables?, insightSkillPath?, ontologyValidationStatus?, clarificationText?}` | 查询状态/结果 |
| `/api/industryinsight/insight/{key}/skill` | GET | path `key` | Markdown 文件流（`text/markdown; charset=UTF-8`，附件名 `industryinsight-skill-<key>.md`） | 下载洞察 SKILL |
| `/api/industryinsight/health` | GET | — | `{engineAvailable, processId, bpmnPath}` | 健康检查 |

---

## 7. 配置（`application.yaml`）

| 键 | 值 |
|---|---|
| `server.port` | `9087` |
| `spring.profiles.active` | `IndustryInsightBPMN` |
| `camunda.client.grpc-address` / `rest-address` | `localhost:26500` / `localhost:9080` |
| `ontology.main-path` | `ontology/industryinsight-all.owl` |
| `[ontology.abox-path]` | （第 9 步分离 / 第 11 步入库后新增） |
| `ontology.bpmn-path` | `ontology/bpmn/industryInsightProcess.bpmn` |
| `ontology.obda-path` / `obda-properties-path` | `ontology/database/industryinsight.obda` / `.../industryinsight.properties`（第 11 步） |
| `industryinsight.websearch.*` | 联网检索取证通道（`enabled` 默认 `false`；`base-url`/`endpoint`/`tool`/`api-key`/`max-results`/`max-excerpt-chars`） |
| `industryinsight.skill.dir` | 洞察 SKILL 落盘目录（默认 `skill`） |
| `spring.ai.openai.*` | MaaS（`ChatAgent`）配置（★21 复用，不新增客户端） |
| `env-prepare.*` | mysql / rabbitmq 等（对齐 Example） |

---

## 8. 解耦与端到端数据流

1. 用户输入「洞察场景」→ 2. `POST /insight` 启动 `IndustryInsightProcess` → 3. `Task_ScenarioParse` 解析校验 →〔G1〕→ 4. 假设树 → 五看（逐节点经 MaaS 分析）→ 5. 机会点 + SPAN →〔G2〕→ 6. 交叉验证/假设 → 三定 → 7. 生成洞察 SKILL（结构化 Markdown 落盘，内嵌五看三定素材与来源）→ 8. 前端轮询回显 SKILL 内容/分层交付物/SKILL 路径（可下载）；9. 高优先级机会/关键风险经 RabbitMQ 外发。

---

## 9. API 复用声明（★铁律 21）

- 逻辑一律经 `BackendService` / `OntologyService` / `OBDAHandler` / `ProcessOrchestrator` / `RabbitMqHandler` / `ChatAgent` 等**现存 API**。
- **未重写** `Reference/` 三份 API 文档中的任何 API（清单见 `references/API复用清单.md`）。
- 若确需新增能力：仅新增本应用专属 `@JobWorker` 与 `web/IndustryInsightController`，不修改公共 API。

---

## 10. 待业务确认项

| # | 问题 | 助手建议 | 业务结论 |
|---|---|---|---|
| Q-A1 | **LLM 通道与降级口径** | 复用 `Utilities.ChatAgent`；不可用即兜底、不阻断、不内置行业词表 | ✅ 采纳（2026-10-22） |
| Q-A2 | **各分析节点「逐工具→求同存异→总结」口径** | 见 §4（节点 3–7） | ✅ 采纳（2026-10-22） |
| Q-A3 | **契约表 jobType 与文件命名** | 见 §4/§3/§7 | ✅ 采纳（2026-10-22） |
| Q-A4 | **逐节点实现方法与算法** | 见 §4/§5 | ✅ 采纳（闸门④，2026-10-22） |

---

## 闸门④留痕
| 日期 | 确认范围（节点集） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-22 | 全部 15 个节点（`Task_ScenarioParse` … `Task_Report`） | 业务方 | ✅ 逐节点「实现方法与算法」全部确认，无修改；Q-A1..Q-A4 全部采纳，同意进入第 7 步代码落地 |

---

## 闸门⑩留痕（是否使用数据库 · 第 11 步执行前）

| 日期 | 是否使用数据库 | 库表结构来源（客户提供 / 新建 / 导入已有） | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-22 | **☑ 不使用** | —（不适用） | 业务方（用户） | **不使用数据库**：跳过第 11 步，ABox 仍以 owl 文件（`industryinsight-all.owl`，含典型 ABox）承载；`application.yaml` 维持 `ontology.abox-path` / `ontology.obda-path` / `obda-properties-path` 注释；第 11 步产出为空并留痕。 |

> 若使用数据库：客户提供的库表结构须以其实际给出为准，**不得臆造**（★8/10）；不使用时第 11 步产出为空。

## 闸门⑪留痕（如何导入/生成完整数据 · 第 12 步执行前）

| 日期 | 数据来源（客户提供 / AI 生成 / 结合） | 导入方式 | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-22 | **不扩充**（沿用第 6 步典型 ABox） | —（不适用） | 业务方（用户） | **不扩充 ABox**：本应用泛化为任意行业，运行态输入由业务方随「洞察场景」提供；典型 ABox（80 个体，含最小闭环）已足以驱动示例与回归测试。第 12 步产出为空并留痕。 |

---

## 变更记录

| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-10-22 | 第 7 步初稿：应用架构设计（分层/技术栈/包结构/15 节点契约表/逐节点实现方法/API/配置/数据流/API 复用 + 闸门④⑩⑪留痕） | — |
| 2026-10-22 | 闸门④通过：15 节点实现方法与算法、Q-A1..Q-A4 全部确认（无修改） | 业务方 |
| 2026-10-22 | **第 11 步执行前闸门⑩**：业务方裁定 **不使用数据库**；第 11 步产出为空并留痕；ABox 继续以 `industryinsight-all.owl` 承载，OBDA 配置保持注释 | 业务方（用户） |
| 2026-10-22 | **第 12 步执行前闸门⑪**：业务方裁定 **不扩充 ABox**；第 12 步产出为空并留痕；沿用第 6 步典型 ABox（80 个体） | 业务方（用户） |
