---
name: ontology-app-builder
description: 本体应用构建工作流技能。以「业务场景」为输入，按 14 步流程产出「流程设计 → 本体顶层设计 → 本体核心设计 → BPMN(含 UI) → 单文件本体(含典型 ABox) → 应用架构与 JobWorker → 测试设计/用例 → TBox/ABox 分离 → TBox 拆分 → ABox 入库(库环境/OBDA) → ABox 扩至全集 → SKOS 词汇表 → 全流程测试」全套工件。所有模板与判据均取自 OntologyFrameworkExample，并强制满足 Reference/公共铁律.md（22 条）。触发条件：用户要求新建/构建本体应用、把某业务场景落地为可运行的本体应用、生成 BPMN/本体/JobWorker/测试文档/SKOS 词汇表，或提到「本体应用构建」「ontology app builder」「14 步流程」「按 Example 搭新应用」。
---

# ontology-app-builder Skill

把**一个业务场景**端到端落地为**可运行的本体应用模块**（与 `OntologyFrameworkExample` 同构）。

> **一句话定位**：本技能是「本体应用构建」的编排器 + 规范集 + 模板集 + 校验器；它本身不产出业务答案，只保证构建过程**严格遵循业务、有据可依、可复现、可验收**。

---

## 0. 强制约束（P0，最高优先级）

1. **完全按业务，禁止编造（★铁律 8/9/10）**：每一条公理、每一个算法分支、每一个 BPMN 分支条件都必须能指到**出处**（业务方原始场景描述、权威领域资料、或已定稿的设计文件）。查不到出处 → 标注「待考」，**不得硬写、不得为让测试通过而改本体/算法**。
2. **一切参考 OntologyFrameworkExample**：所有产出的**文件形态、目录布局、命名、文档结构**均以 `OntologyFrameworkExample/` 的对应文件为模板基线。**映射关系见 `references/Example-文件映射.md`**。
3. **强制满足 `Reference/公共铁律.md`（22 条）**：本技能的全部动作受其约束；可操作的落地检查表见 `references/公共铁律-强制基线.md`。领域相关铁律按新应用所属领域另行补充（模板见 `references/领域铁律-补充.md`）。
4. **人工闸门不可跳过**：全流程共 **11 个**业务人员确认闸门（详见 §3.1 闸门表）。既有的 6 个为 ①第 2 步、②第 3 步、③第 4 步、④第 7 步、⑤第 8 步、⑥第 13 步；本版新增 5 个：**⑦第 6 步（确认典型 ABox 数据）**、**⑧第 9 步执行前（确认是否 TBox/ABox 分离）**、**⑨第 10 步（确认是否拆分）**、**⑩第 11 步（确认是否使用数据库）**、**⑪第 12 步（确认如何导入/生成完整数据）**。闸门未获确认前，**禁止**进入下一步。**不允许 AI 自行发挥或替代业务裁定**（★铁律 20）。
5. **禁改禁区模块（★铁律 7/15）**：绝对禁止修改 `OntologyResolver`/`OpenlletResolver`、`OntopOBDAHandler` 中的任何文件；改动前后用 `git status --porcelain` 核验。
6. **复用现存 API（★铁律 21）**：禁止重写 `Reference/` 三份 API 文档中已存在的 API；清单见 `references/API复用清单.md`。
7. **非工程文件归位（★铁律 16）**：脚本/日志/比对产物/快照一律写入 `<工作区>/HWCodeArtsDir/`，**不得**落在工程目录内。

> 冲突裁决顺序：**领域权威 / 业务方定稿 > 本体定义 > 代码**（★铁律 9）。

---

## 1. 适用范围与前置

**适用**：在 `OntologyMachine` 下新建一个本体应用模块（如 `OntologyFrameworkXxx`），或对一个既有应用补齐上述工件。

**前置（开工前必须就绪）**：
- 一份**业务场景描述**（用户提供；这是全流程唯一的业务真相来源）。
- 已安装的环境能力模块：`OpenlletResolver`、`OntopOBDAHandler`、`Utilities`（由 `OntologyFrameworkPlatform` 分发或本机 Maven 仓库提供）。
- 环境服务（按需）：MySQL(3306) / Camunda-Zeebe(26500·9080) / Ontop(8080) / RabbitMQ(5672·15672)，由 `EnvPrepare` 拉起。

**参考模板工程**：`OntologyMachine/OntologyFrameworkExample/`（本技能一切形态的基线）。

---

## 2. 产出总览

新应用模块的标准布局（**与 Example 同构**；`Xxx` = 应用简称，`xxx` = 小写简称）：

```
OntologyFrameworkXxx/
├── design/                                  ← 设计文档（人工确认载体）
│   ├── <Xxx>应用-顶层设计.md                 ← 第 1 步产出
│   ├── <Xxx>应用-流程设计.md                 ← 第 2 步产出（闸门①）
│   ├── <Xxx>应用-本体顶层设计.md             ← 第 3 步产出（闸门②）
│   ├── <Xxx>应用-本体核心设计.md             ← 第 4 步产出（闸门③）
│   ├── <Xxx>应用-应用架构设计.md             ← 第 7 步产出（闸门④）
│   └── <Xxx>应用-测试设计文档.md             ← 第 8 步产出（闸门⑤）
├── ontology/
│   ├── bpmn/<xxx>Process.bpmn               ← 第 5 步产出
│   ├── <xxx>-all.owl                        ← 第 6 步产出（单文件：TBox + 典型 ABox；闸门⑦确认典型 ABox）
│   ├── [TBox/ABox 分离] <xxx>-tbox.owl / <xxx>-abox.owl   ← 第 9 步产出（两个 owl 文件）
│   ├── [TBox 拆分] <xxx>-*.owl              ← 第 10 步产出
│   ├── <xxx>-terminology.ttl                ← 第 13 步产出（SKOS）
│   ├── catalog-v001.xml                     ← IRI→本地文件映射
│   └── database/{createdb_tables.ddl, data.sql, <xxx>.obda, <xxx>.properties}  ← 第 11 步产出
├── src/main/java/.../{<Xxx>Application, <Xxx>OntologyJobWorker, web/<Xxx>Controller}.java  ← 第 7 步产出
├── src/main/resources/{application.yaml, static/index.html}                ← 第 5/7 步产出
├── src/test/java/.../{All<Xxx>SuiteTest, <Xxx>BpmnRealEngineTest, <Xxx>Swrl...Test}.java  ← 第 8/14 步产出
├── pom.xml
├── start-env.cmd                            ← 第 7 步产出（一键环境启停脚本，照 Example 形态）
├── EnvPrepare-<version>.jar                 ← 第 7 步产出（环境启动器可执行 jar，须与 start-env.cmd 同目录＝模块根；取自 build/ 或 -project.zip）
└── .idea/runConfigurations/<Xxx>Application.xml  ← 第 7 步产出（IDEA 运行配置；Working directory = $MODULE_WORKING_DIR$）
```

> 每份产出的**模板**见 `templates/`；每步的**输入/动作/验收判据**见 `references/步骤规范.md`。

---

## 3. 执行流程（14 步）

> 每步格式：**目标 · 输入 · 动作 · 产出（参考 Example 文件）· 闸门 · 验收**。

### 第 1 步　明确业务场景与目标范围
- **目标**：把用户口述的「业务场景」固化为可引用的**场景与范围**描述（含目标、边界、不做的部分）。
- **输入**：业务场景描述。
- **动作**：抽取「目标 / 范围 / 角色 / 关键对象 / 关键约束」；列出**不在范围**项；对无法确认处标注「待业务确认」，**不得臆造**。
- **产出**：`design/<Xxx>应用-顶层设计.md`（§概述与定位、§范围；参考 `OntologyFrameworkExample/design/Example应用-顶层设计.md` §1–2）。
- **闸门**：无（但「待业务确认」项须带入第 2 步一并确认）。
- **验收**：场景描述中每个业务名词都能在后续步骤找到落点（类/属性/流程节点/术语），或显式声明为不建模。

### 第 2 步　流程设计（闸门①）
- **目标**：输出**流程设计文件**，交业务人员修改并**定稿**。
- **输入**：第 1 步场景与范围。
- **动作**：把场景拆成「环节 → 输入 → 处理 → 输出 → 分支条件」；用纯业务语言描述（**不写技术实现**）；标出需要业务裁定的分支与边界。
- **产出**：`design/<Xxx>应用-流程设计.md`（参考 `Example应用-流程设计.md`，其业务原文样例见 `Example应用-.../披萨制作和烘培的流程是.txt`）。
- **闸门①（人工）**：业务人员修改并定稿流程设计文件。**未定稿不得进入第 3 步。**
- **验收**：流程的每个环节都有明确的输入/输出；每个排他分支都有**互斥且完备**的条件（为第 5 步 BPMN 网关做准备）。

### 第 3 步　本体顶层设计（闸门②）
- **目标**：做**本体顶层设计**——界定本体的设计理念、范围边界、资产组织、命名空间/import 体系与顶层类框架，交业务人员确认。
- **输入**：第 1、2 步产出。
- **动作**：确定本体要表达的业务概念范围（与流程设计对齐）；规划 TBox/ABox 分层与资产组织；定义命名空间与 `owl:imports` 拓扑；勾出**顶层类层级框架**（根类与主干分支）；每条设计决策标注业务/资料出处（★铁律 10/12）。
- **产出**：`design/<Xxx>应用-本体顶层设计.md`（参考 `Example应用-本体设计.md` 的理念/资产组织/命名空间章节）。
- **闸门②（人工）**：业务人员确认本体顶层设计是否正确。**未确认不得进入第 4 步。**
- **验收**：顶层类框架可覆盖流程设计中的每个业务概念域；命名空间/import 方案闭合；无硬编码业务数据（★铁律 12/14）。

### 第 4 步　本体核心类、属性与关系设计（闸门③）
- **目标**：在已确认的顶层设计上，细化**核心类、对象/数据属性、约束（`owl:Restriction`）、SWRL 规则与关系**，交业务人员确认。
- **输入**：第 3 步已确认的本体顶层设计。
- **动作**：设计类层级明细、对象属性（含 `owl:inverseOf`、定义域/值域）、数据属性（数值用数值类型）、约束（`owl:Restriction`）、SWRL 规则；每条约束标注出处（★铁律 10/12/14）；区分 TBox 与 ABox。
- **产出**：`design/<Xxx>应用-本体核心设计.md`（参考 `Example应用-本体设计.md` 的 TBox/SWRL 章节）。
- **闸门③（人工）**：业务人员确认核心类/属性/关系是否正确。**未确认不得进入第 5/6 步。**
- **验收**：类/属性覆盖率可追溯到流程设计的每个业务概念；约束用 `owl:Restriction`（非注释）；无硬编码业务数据（★铁律 12/14）。

### 第 5 步　生成 BPMN（含 UI）
- **目标**：按**定稿流程设计**生成 BPMN 流程文件，并配套 UI（启动/展示页面）。
- **输入**：定稿的流程设计文件（第 2 步）。
- **动作**：把每个环节落成 `serviceTask`（`zeebe:taskDefinition type` = jobType 字面量）、每个分支落成排他网关（条件互斥且覆盖）；产出启动/查询 REST + 前端页面；同步补 **BPMNDI** 图形信息。
- **产出**：`ontology/bpmn/<xxx>Process.bpmn`（参考 `Example/ontology/bpmn/myPizzaBakingProcess.bpmn`）；UI 参考 `Example/src/main/resources/static/index.html` + `web/ExampleController.java`。
- **闸门**：无独立闸门，但**须以定稿流程设计为唯一依据**。
- **验收**：节点齐备、连线双向一致、Start→End 可达、网关分支条件互斥、每个 `serviceTask` 的 jobType 均有后续 `@JobWorker` 承接（铁律 4 的结构校验要求）。

### 第 6 步　生成单文件本体（含典型 ABox 数据）
- **目标**：按**已确认的本体设计**（顶层 + 核心）生成**单个本体文件**，并**内含典型 ABox 数据**（足以驱动示例/测试的代表性个体）。
- **输入**：第 3、4 步确认的本体设计。
- **动作**：生成 `ontology/<xxx>-all.owl`（单文件：TBox + 典型 ABox）；写 `catalog-v001.xml`（IRI→本地文件）；数值属性用数值类型（★铁律 12）；SWRL 规则可先行合并于本文件。**本步不拆分**（拆分放到第 9/10 步）。生成后**把本文件中的「典型 ABox 数据」交业务人员确认**（闸门⑦）——确认代表性个体（类型、关键断言、覆盖的业务场景）是否正确、是否足以驱动示例/测试。
- **产出**：`ontology/<xxx>-all.owl` + `catalog-v001.xml`（参考 `Example/ontology/pizza-all.owl` 的组织形态，但此处为**单文件**）；典型 ABox 确认记录留痕于《<Xxx>应用-本体核心设计》§ABox 典型个体设计。
- **闸门⑦（人工）**：业务人员确认单文件本体中的**典型 ABox 数据**（个体集、类型、关键断言、代表性）。**未确认不得进入第 7 步。**（TBox 已在闸门②/③确认；本体文件须与设计逐条对应。）
- **验收**：本体可被 Openllet 一致性校验通过；imports（若有）闭合可解析；`owl:Restriction` 与设计表一致；典型 ABox 数据可被推理器加载，且已获闸门⑦确认。

### 第 7 步　应用架构设计与 JobWorker（闸门④）
- **目标**：参照 Example 生成**应用架构设计文件**；对 **BPMN 的每个节点**向业务人员确认该节点的**实现方法与算法**，以 **JobWorker** 方式实现逻辑。**「实现方法」载体不限**——可以是一段**自然语言描述**、一段**程序**、一个**外部调用**（外部系统/服务/API/脚本）等任意形式；须**逐节点**确认其实现方法与其算法。
- **输入**：第 2/3/4/5 步产出。
- **动作**：
  1. 产出应用架构设计（分层、组件、包结构、端点、数据流）——参考 `Example应用-顶层设计.md` §3–7；
  2. **逐节点**与业务人员确认该节点的**实现方法与算法**（这是闸门④的核心）：实现方法载体不限（自然语言描述 / 程序 / 外部调用等），并说明其业务规则与算法口径；
  3. 实现 `<Xxx>OntologyJobWorker`（`@JobWorker(type=...)`，每个节点一个）、`<Xxx>Application`、`web/<Xxx>Controller`、`application.yaml`；
  4. 逻辑一律经 `BackendService`/`OntologyService`/`OBDAHandler` 等现存 API（★铁律 21），**禁止重写**；
  5. **创建 IDEA Run Configuration**（主类 = `<Xxx>Application`，Spring Boot 类型）：在工程 `.idea/runConfigurations/<Xxx>Application.xml` 新建，**最关键的是把 `Working directory` 设为 `$MODULE_WORKING_DIR$`**（IDEA 内建宏，解析为该模块根 `<Xxx>模块名/`）——即 `<option name="WORKING_DIRECTORY" value="$MODULE_WORKING_DIR$" />`。原因：`application.yaml` 里的相对路径（`ontology/...`、`ontology/database/...`、`ontology/bpmn/...`）都以**模块根**为基准解析，EnvPrepare 亦依赖工作目录**向上查找** `application.yaml`；若沿用默认 `$PROJECT_DIR$`（工程根），本体/映射/脚本路径会解析错误。
  6. **生成配套的环境启停脚本** `start-env.cmd`（置于**模块根**，与 `<Xxx>Application` 及 EnvPrepare jar 同目录）：**照 `OntologyFrameworkExample/start-env.cmd` 的形态**生成——一键**准备并启动**运行环境，依次调用 `EnvPrepare-<version>.jar`：`createdb`（建库 + 灌典型 ABox 数据）→ `start`（**新窗口常驻**拉起 application.yaml 中的 RabbitMQ / MySQL / Ontop / Camunda）→ `create-rabbitmq`（建 MQ 资源）；脚本用 `%~dp0` 定位自身（不硬编码路径）、先 `chcp 936 >nul`、逐步 `errorlevel` 校验并在失败时 `pause`。**停止环境 = 关闭该常驻窗口**（脚本回显已说明「关闭此窗口即停止全部服务」）。**编码/换行硬约束**：`.cmd` 须存 **GBK(ANSI) + CRLF**（UTF-8 或 LF 会被 cmd 按 GBK 解析致行错位/`set` 失效，见「公共铁律-强制基线」相关项）。
  7. **放置环境启动器可执行 jar**：把 `EnvPrepare-<version>.jar`（可执行 fat jar）**复制到模块根**（与 `start-env.cmd` 同目录）——`start-env.cmd` 以 `%~dp0EnvPrepare-<version>.jar` 定位它，**缺则该脚本直接报「未找到 EnvPrepare 可执行 jar」而失败**。来源：`OntologyMachine/build/EnvPrepare-<version>.jar`（由 `OntologyFrameworkPlatform` 构建产出），或 `OntologyFrameworkExample-*-project.zip` 内已注入的同名 jar。
- **产出**：`design/<Xxx>应用-应用架构设计.md` + 主代码三件套 + `application.yaml` + `.idea/runConfigurations/<Xxx>Application.xml` + **`start-env.cmd`（环境启停脚本）** + **`EnvPrepare-<version>.jar`（环境启动器可执行 jar，置于模块根）**（参考 `ExampleOntologyJobWorker.java`、`ExampleApplication.java`、`web/ExampleController.java`、`application.yaml`、`OntologyFrameworkExample/start-env.cmd`）。
- **闸门④（人工）**：业务人员对**每个 BPMN 节点**的**实现方法与算法**逐项确认（实现方法载体不限：自然语言描述 / 程序 / 外部调用等）。**未确认不得实现对应 Worker。**
- **验收**：BPMN 每个 serviceTask 的 jobType ⇄ `@JobWorker(type)` 一一对应；每个 Worker 的**实现方法与算法**均能指到确认记录；无硬编码业务数据（★铁律 14）；`.idea/runConfigurations/<Xxx>Application.xml` 已创建且 **`Working directory` = `$MODULE_WORKING_DIR$`**；**`start-env.cmd` 已生成于模块根、为 GBK + CRLF，且含 `createdb` / `start` / `create-rabbitmq` 三步（照 Example 形态）**；**模块根含 `EnvPrepare-<version>.jar`（可执行）且与 `start-env.cmd` 同目录**（`scripts/validate_structure.py` 机检，缺失报 ERROR）。

### 第 8 步　测试设计与用例（闸门⑤）
- **目标**：参照 Example 构建**测试文档**，确认后输出**测试用例**；用例需**驱动 BPMN** 完成测试，尽量覆盖**全场景，尤其边界场景**。
- **输入**：第 2–7 步产出。
- **动作**：
  1. 产出测试设计文档（分层策略、环境与配置、测试数据、用例清单、覆盖矩阵）——参考 `OntologyFrameworkPizza/design/披萨应用（Pizza）测试用例设计文档.md` 结构；
  2. 实现测试类：BPMN 端到端（真实引擎 / `zeebe-process-test` 内存引擎）、SWRL+消息、本体合规、SKOS 词汇表；聚合入口 `All<Xxx>SuiteTest`；
  3. **正向 + 边界 + 负向**用例齐备；BPMN 测试以**生产 Worker** 消费（不另写桩）。
- **产出**：`design/<Xxx>应用-测试设计文档.md` + `src/test/java/.../{All<Xxx>SuiteTest, <Xxx>BpmnRealEngineTest, ...}`（参考 `ExampleBpmnRealEngineTest.java`、`ExampleSwrlRabbitMqTest.java`、`AllExampleSuiteTest.java`）。
- **闸门⑤（人工）**：业务人员/负责人确认测试设计文档后，方可固化用例。
- **验收**：每个流程节点与每个本体约束都有对应用例；边界场景显式列示；测试可驱动 BPMN 走完拓扑（铁律 4/8）。

### 第 9 步　TBox/ABox 分离为两个 owl 文件
- **触发条件**：ABox **不参与推理、且量较大**（如 > 200 个个体）时执行分离。
- **目标**：把单文件本体中的 TBox 与 ABox **分成两个独立的 owl 文件**；**ABox 不进数据库**（数据库在后续第 11 步处理），仅作独立本体文件。
- **输入**：第 6 步的单文件本体。
- **动作**（按 ★铁律 11/55/56）：
  0. **执行前**与业务人员确认**是否进行 TBox/ABox 分离**（闸门⑧）：若不分离 → **跳过本步**（保持单文件）；若分离 → 继续；
  1. 拆分出 `<xxx>-tbox.owl`（术语/约束/规则）与 `<xxx>-abox.owl`（个体断言）；
  2. 主入口 `<xxx>-all.owl` 对 ABox 的 `owl:imports` **注释掉**（保留说明，★55）；ABox 由应用侧独立加载；
  3. 更新 `catalog-v001.xml`（IRI→本地文件映射）。
- **产出**：`ontology/<xxx>-tbox.owl`、`ontology/<xxx>-abox.owl`（+ 更新 `<xxx>-all.owl`、`catalog-v001.xml`）（参考 `Example/ontology/{pizza-all.owl, pizza-abox.owl, ...}` 的分离形态）；分离决策留痕于《<Xxx>应用-本体顶层设计》§TBox/ABox 分层策略。
- **闸门⑧（人工·执行前）**：业务人员确认**是否进行 TBox/ABox 分离**。**未确认不得拆分。**涉及建模口径变更时一并确认。
- **验收**：分离后 TBox 仍可独立推理；主本体一致性不因分离被破坏（★55：ABox 只搬不删）；两个文件各自可解析。若闸门⑧判定不分离，则本步产出为空且已留痕说明。

### 第 10 步　TBox 拆分（条件触发 · 闸门⑨）
- **触发条件**：**TBox 本身较大**时，按业务划分为多个小本体文件，做到**高内聚、低耦合**。
- **动作**：先与业务人员确认**是否进行 TBox 拆分**及**拆分粒度**（闸门⑨）；确认拆分后，按业务域切分子本体（如核心/组件/规则/工序），用 `owl:imports` + `catalog-v001.xml` 组织；保持每域内聚、域间低耦合。
- **产出**：拆分后的 `ontology/<xxx>-*.owl` + 更新 `catalog-v001.xml`（参考 Example 的多文件 TBox 组织）；拆分决策留痕于《<Xxx>应用-本体顶层设计》§TBox/ABox 分层策略。
- **闸门⑨（人工）**：业务人员确认**是否进行 TBox 拆分**（及拆分粒度）。**未确认不得拆分**（若不拆分则本步产出为空且已留痕说明）。
- **验收**：拆分不改变整体语义；各子模块可单独加载校验；imports 无循环。

### 第 11 步　典型 ABox 数据导入数据库（库环境 + OBDA）
- **目标**：把**典型的 ABox 数据导入数据库**，并完成**数据库环境搭建**与 **OBDA 映射、数据库属性文件**的生成。
- **输入**：第 6/9 步的 ABox 数据与本体。
- **动作**：
  0. **执行前**与业务人员确认**是否使用数据库**（闸门⑩）：若不使用 → **跳过本步**（ABox 仍以 owl 文件承载）；若使用 → 继续，并确认**库表结构的来源**（三选一：①**客户提供**现成库表结构；②按本体**新建**库表（生成 `createdb_tables.ddl`）；③**导入已有**数据库表结构）。**客户提供的库表结构须以客户实际给出的为准，不得臆造**（★铁律 8/10）；
  1. 生成建库脚本 `createdb_tables.ddl` 与数据脚本 `data.sql`（典型 ABox 数据入库）；
  2. 生成 Ontop 映射 `<xxx>.obda` 与连接配置 `<xxx>.properties`；
  3. 应用配置补充 `ontology.obda-path` / `ontology.obda-properties-path`；加载**复用** `OntologyService`（不要自建 manager）；
  4. 用 `EnvPrepare createdb` 建库灌数、`EnvPrepare start` 拉起环境（★1/2/19）。
- **产出**：`ontology/database/{createdb_tables.ddl, data.sql, <xxx>.obda, <xxx>.properties}`（参考 `Example/ontology/database/{createdb_tables.ddl, data.sql, myPizza.obda, myPizza.properties}`）；用库决策与库表结构来源留痕于《<Xxx>应用-应用架构设计》。
- **闸门⑩（人工·执行前）**：业务人员确认**是否使用数据库**；若使用，确认**库表结构来源**（客户提供 / 新建 / 导入已有）。**未确认不得建库、不得入库。**涉及建模口径变更时一并确认。
- **验收**：数据库建成、典型数据入库；本体经 OBDA 可 SPARQL 访问（`http://localhost:8080/sparql`）；映射与本体 IRI 一致。若闸门⑩判定不使用数据库，则本步产出为空且已留痕说明。

### 第 12 步　ABox 扩展到场景完整数据（闸门⑪）
- **目标**：把 ABox **从典型数据扩展到整个场景所需的完整数据**，确保**完全通过**。
- **动作**：**执行前**与业务人员确认**如何导入完整数据**（闸门⑪）——即完整数据的**来源与导入方式**（如：客户提供数据文件导入 / 由 AI 依据业务规则在典型数据基础上**生成** / 二者结合）；确认后补齐 ABox/业务数据至全集（`.owl` 与数据库两侧同步）；重跑全套（`All<Xxx>SuiteTest`）；任何失败**先判本体/数据对错，再判测试期望**（★铁律 8）。
- **产出**：完整数据（`ontology/<xxx>-abox.owl` + `database/data.sql`）+ 回归报告；导入/生成方式留痕于《<Xxx>应用-应用架构设计》。
- **闸门⑪（人工·执行前）**：业务人员确认**如何导入/生成完整数据**（数据来源与导入方式）。**未确认不得扩展数据。**
- **验收**：全集下全套通过，无 `@Disabled`、无跳过（除环境依赖项并显式说明）；完整数据与其来源可追溯到确认记录。

### 第 13 步　构建 SKOS 词汇表（闸门⑥）
- **目标**：根据**现有本体**构建 SKOS 词汇表。
- **输入**：第 6/9/10 步定稿的 TBox。
- **动作**：把业务术语/标签映射为 SKOS（如 `skos:prefLabel`/同义词），供 `OntologyLabelMatcher`/`SkosSynonymReader` 使用；术语**来源于本体**，不得凭空造词。
- **产出**：`ontology/<xxx>-terminology.ttl`（参考 `Example/ontology/pizza-terminology.ttl`）。
- **闸门⑥（人工）**：业务人员确认术语与同义词表。
- **验收**：术语表与本体 IRI 一一对应（交叉校验）；无孤立/无对应本体的词条。

### 第 14 步　词汇表测试与全流程测试
- **目标**：完成**词汇表测试**及**全流程测试**。
- **动作**：
  1. SKOS 结构 + 与 OWL 的交叉校验测试（参考 Pizza 的 `validation/PizzaSkosTerminologyTest` 思路）；
  2. 端到端全流程回归（含第 12 步全集数据）；用统一套件入口。
- **产出**：词汇表测试类 + 全流程回归结果。
- **验收**：SKOS 测试通过；`All<Xxx>SuiteTest` 全绿；构建链（`EnvPrepare install → platform package → [offline-repo] → 应用 test`）可复现（★铁律 1/2/19）。

---

## 3.1 人工闸门汇总（共 11 个）

| 闸门 | 步骤 | 确认内容 | 留痕载体 | 未确认时的动作 |
|---|---|---|---|---|
| ① | 第 2 步 | 流程设计文件（修改并定稿） | 《<Xxx>应用-流程设计》 | 停在定稿，不得进入本体顶层设计 |
| ② | 第 3 步 | 本体顶层设计 | 《<Xxx>应用-本体顶层设计》 | 停在确认，不得进入核心类/属性/关系设计 |
| ③ | 第 4 步 | 本体核心类、属性与关系 | 《<Xxx>应用-本体核心设计》 | 停在确认，不得生成本体文件 |
| ④ | 第 7 步 | **每个 BPMN 节点**的**实现方法（载体不限）+ 算法** | 《<Xxx>应用-应用架构设计》 | 停在该节点，不得实现其 Worker |
| ⑤ | 第 8 步 | 测试设计文档 | 《<Xxx>应用-测试设计文档》 | 停在确认，不得固化用例 |
| ⑥ | 第 13 步 | SKOS 词汇表 | `ontology/<xxx>-terminology.ttl` | 停在确认，不得进入第 14 步 |
| ⑦ | 第 6 步 | 单文件本体中的**典型 ABox 数据** | 《<Xxx>应用-本体核心设计》§ABox 典型个体设计 | 停在确认，不得进入第 7 步 |
| ⑧ | 第 9 步（执行前） | **是否进行 TBox/ABox 分离** | 《<Xxx>应用-本体顶层设计》§TBox/ABox 分层策略 | 停在确认，不执行拆分 |
| ⑨ | 第 10 步 | **是否进行 TBox 拆分**（及拆分粒度） | 《<Xxx>应用-本体顶层设计》§TBox/ABox 分层策略 | 停在确认，不执行拆分 |
| ⑩ | 第 11 步（执行前） | **是否使用数据库**；若用，**库表结构来源**（客户提供 / 新建 / 导入已有） | 《<Xxx>应用-应用架构设计》 | 停在确认，不建库、不入库 |
| ⑪ | 第 12 步（执行前） | **如何导入/生成完整数据**（数据来源与导入方式） | 《<Xxx>应用-应用架构设计》 | 停在确认，不扩展数据 |

> 每个闸门须记录：确认人、确认时间、确认结论（或修改点）。**记录写入对应设计文档的表头或变更记录节**（载体见上表「留痕载体」列）。
> 闸门④~⑪ 中的 ⑦~⑪ 为本版新增；①~⑥ 的编号保持不变（历史引用不受影响）。

---

## 4. 铁律落地检查表（强制）

**权威源**：`../Reference/公共铁律.md`（22 条）。**操作化检查表**：`references/公共铁律-强制基线.md`（逐条给出「在本流程的落点」与「校验方式」）。

构建过程中的关键红线（摘录）：
- ★7/15 禁改 `OpenlletResolver`/`OntopOBDAHandler`；`git status --porcelain` 核验。
- ★8/9/10 本体正确性 > 测试通过率；一切有领域依据；严禁编造。
- ★11/55/56 TBox/ABox 分离的正确落地方式（第 9 步分成两个 owl，ABox 不入库；第 11 步入库）。
- ★20 改动前找用户确认（对应 §3.1 十一个闸门）。
- ★21 有现存 API 必须复用（见 `references/API复用清单.md`）。
- ★16 非工程文件写入 `HWCodeArtsDir/`。
- ★1/2/19 reactor 构建 / surefire `useManifestOnlyJar=false` / 本体固化后回归。

---

## 5. 参考资源索引

| 资源 | 路径 | 用途 |
|---|---|---|
| 公共铁律（权威源） | `../Reference/公共铁律.md` | 22 条强制基线全文 |
| 铁律强制基线（检查表） | `references/公共铁律-强制基线.md` | 逐条落地点 + 校验方式 |
| 每步规范 | `references/步骤规范.md` | 14 步的输入/动作/产出/验收 |
| Example 文件映射 | `references/Example-文件映射.md` | 14 步产出 ↔ Example 对应文件 |
| API 复用清单 | `references/API复用清单.md` | 禁止重写的 API（★21） |
| 领域铁律补充 | `references/领域铁律-补充.md` | 为新领域追加专属铁律的方法与模板 |
| 设计文档模板 | `templates/*.md` | 顶层/流程/本体顶层/本体核心/应用架构/测试/词汇表 |
| 结构校验脚本 | `scripts/validate_structure.py` | 校验新应用工程工件齐备与契约一致 |

---

## 6. 反模式（禁止）

- ❌ 在业务人员定稿前先生成 BPMN/本体/代码（跳闸门）。
- ❌ 把「本体顶层设计」与「核心类/属性/关系」混为一步、跳过闸门②或③。
- ❌ 第 6 步先行拆分本体文件（拆分应放到第 9/10 步）。
- ❌ 第 6 步的典型 ABox 未经业务确认（闸门⑦）即进入第 7 步。
- ❌ 未经闸门⑧/⑨确认即拆分本体（TBox/ABox 分离 / TBox 拆分）。
- ❌ 未经闸门⑩确认即建库/入库；客户提供库表结构时自行臆造表结构。
- ❌ 未经闸门⑪确认即把 ABox 扩展为完整数据；完整数据来源不可追溯。
- ❌ 第 9 步把 ABox 写进数据库（入库属第 11 步；第 9 步只分两个 owl 文件）。
- ❌ 把节点「实现方法」窄化为只有「算法」——实现方法载体不限（自然语言描述 / 程序 / 外部调用等），须逐节点确认。
- ❌ 用测试通过率反推本体（★8/60）——失败必须先问「本体还是测试期望错了」。
- ❌ 把约束写在注释里而非 `owl:Restriction`（★12）。
- ❌ 在 Java 里硬编码业务数据/变量名/模板（★14）。
- ❌ 重写 `Reference/` 已有 API（★21）；修改禁区模块（★7）。
- ❌ 凭空造术语（第 13 步必须来源于本体）、凭空造约束出处（★10）。
- ❌ 把 `HWCodeArtsDir/` 之外的工程目录当作临时产物堆放处（★16）。
