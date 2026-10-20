# PlumBlossom应用 — 应用架构设计

> **用法**：本文件产出第 7 步。**闸门④**：业务人员对 **BPMN 的每个节点**逐项确认「**实现方法（载体不限：自然语言描述 / 程序 / 外部调用等）+ 算法**」后方可实现对应 JobWorker。本模板亦承载**闸门⑩（是否使用数据库）**与**闸门⑪（如何导入/生成完整数据）**的留痕。
> **基线**：`OntologyFrameworkExample/design/Example应用-顶层设计.md` §2–6；代码基线 `ExampleOntologyJobWorker.java`、`ExampleApplication.java`、`web/ExampleController.java`、`src/main/resources/application.yaml`。

| 项目 | 内容 |
|---|---|
| 应用 | PlumBlossom（梅花易数占断） |
| 文档日期 | 2026-10-20 |
| 关联流程设计 | 《PlumBlossom应用-流程设计》（v11 定稿，闸门①已确认） |
| 关联本体设计 | 《PlumBlossom应用-本体顶层设计》（v3）+《PlumBlossom应用-本体核心设计》（v3.1）（均已确认） |
| 关联流程文件 | `ontology/bpmn/plumBlossomProcess.bpmn`（18 ServiceTask + 3 网关） |
| 激活 Profile | `PlumBlossomBPMN` |
| Worker 类 | `PlumBlossomOntologyJobWorker` |

> 依据：定稿流程设计 + 已确认本体设计 + 实际生成的 BPMN。**算法一律来自业务确认，不得臆造。**

---

## 1. 架构总览与分层

```
┌─────────── 前端层 ───────────┐
│ static/index.html（17 类起卦法输入 → 启动 → 轮询结果） │
└───────────▼──────────────────┘
┌─────────── API 层 ───────────┐
│ PlumBlossomController（POST /divination、GET /divination/{key}、GET /health）│
└───────────▼──────────────────┘
┌─────────── 编排层（Zeebe）───┐
│ plumBlossomProcess.bpmn(PlumBlossomDivinationProcess)│
└───────────▲──────────────────┘
            │ jobType 字面量绑定（BPMN type ⇄ @JobWorker(type)）
┌───────────┴── 应用层 ────────┐
│ PlumBlossomOntologyJobWorker(18 个 @JobWorker)│
│ PlumBlossomApplication        │
└──┬──────────┬──────────┬─────┘
┌──▼───────┐┌─▼────────┐┌─▼──────┐
│本体层     ││数据层    ││消息层   │
│OWL/SWRL  ││Ontop→DB  ││RabbitMQ │
│Openllet   ││历法/干支 ││重大吉凶 │
└──────────┘└──────────┘└────────┘
```

## 2. 技术栈与模块依赖

### 2.1 关键依赖（对齐 `OntologyFrameworkExample/pom.xml`）
| 依赖 | 版本 | 作用 |
|---|---|---|
| `spring-boot-starter-parent` | 同 Example | 父 POM |
| `io.camunda:spring-boot-starter-camunda-sdk` | 同 Example | Camunda 8 / Zeebe 客户端 |
| `io.camunda:zeebe-process-test-extension` | 同 Example | 内存引擎端到端测试 |
| `com.ocean:OpenlletResolver` | 0.0.1-SNAPSHOT | OWL API + Openllet 推理 |
| `com.ocean:OntopOBDAHandler` | 0.0.1-SNAPSHOT | Ontop OBDA → MySQL |
| `com.ocean:Utilities` | 0.0.1-SNAPSHOT | 共享工具（含 `ProcessOrchestrator`、`RabbitMqHandler`） |

> `OntopOBDAHandler` 依赖须保留 `log4j-slf4j2-impl` 排除（对齐 Example）。

### 2.2 模块间依赖
```
OntologyFrameworkPlumBlossom
        ├── OpenlletResolver
        ├── OntopOBDAHandler
        └── Utilities
```

## 3. 组件与包结构

### 3.1 主代码（`src/main/java/com/ocean/ontologyframework/plumblossom/`）
| 文件 | 职责 |
|---|---|
| `PlumBlossomApplication.java` | `@SpringBootApplication`；`setAdditionalProfiles("PlumBlossomBPMN")` |
| `PlumBlossomOntologyJobWorker.java` | `@Component @Profile("PlumBlossomBPMN")`，继承 `OntologyWorkerSupport`；18 个 `@JobWorker` |
| `web/PlumBlossomController.java` | `@RestController`；`/api/plumblossom/divination`（启动）、`/divination/{key}`（查询）、`/health` |

### 3.2 前端（`src/main/resources/static/`）
| 文件 | 职责 |
|---|---|
| `index.html` | 按起卦法类别动态渲染输入项，启动流程后每秒轮询状态，完成时展示本卦/体用/互变/生克/旺衰/类象/吉凶/应期/建议 |

### 3.3 环境启停脚本（`<模块根>/start-env.cmd`）
> **照 `OntologyFrameworkExample/start-env.cmd` 的形态**生成，一键**准备并启动**运行环境。

| 步骤 | 命令 | 说明 |
|---|---|---|
| 1 | `EnvPrepare-<version>.jar createdb` | 建库 + 灌典型 ABox 数据 |
| 2 | `EnvPrepare-<version>.jar start` | **新窗口常驻**拉起 RabbitMQ / MySQL / Ontop / Camunda |
| 3 | `EnvPrepare-<version>.jar create-rabbitmq` | 建 MQ 资源 |

- `.cmd` 须存 **GBK(ANSI) + CRLF**；模块根须放 `EnvPrepare-<version>.jar`（可执行 fat jar）。

### 3.4 IDEA 运行配置（`<模块根>/.idea/runConfigurations/PlumBlossomApplication.xml`）
| 项 | 值 |
|---|---|
| 配置名 | `PlumBlossomApplication` |
| 类型 | `SpringBootApplicationConfigurationType` |
| `<module name>` | `OntologyFrameworkPlumBlossom` |
| `SPRING_BOOT_MAIN_CLASS` | `com.ocean.ontologyframework.plumblossom.PlumBlossomApplication` |
| `WORKING_DIRECTORY` | `$MODULE_WORKING_DIR$` |
| `VM_PARAMETERS` | `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow` |

## 4. BPMN 节点 ⇄ JobWorker 契约表（核心）

> **一一对应**：BPMN 每个 `serviceTask` 的 `zeebe:taskDefinition type` ⇄ 一个 `@JobWorker(type)`。

| # | BPMN 节点 id | jobType | 业务规则（流程设计） | 实现方法（载体不限） | 算法（拟） | 输入变量 | 输出变量 | 确认状态 |
|---|---|---|---|---|---|---|---|---|
| 1 | Task_PreceptCheck | preceptCheck | 四不占校验 | `div:Precept` 4 个体约束 + 程序判定 | 无事=subject 空→不通过；重卦=同事项重复占（需历史，见§5-1）；不诚/不义=前端 `preceptPromise` 承诺 | subject, preceptPromise | passPrecept, triggeredPrecept | ✅已确认 |
| 2 | Task_PreceptAlert | preceptAlert | 生成戒律告警 | `div:Precept` 个体 → 告警语映射 | triggeredPrecept → 原文告警语 | triggeredPrecept | alertText | ✅已确认 |
| 3 | Task_Route | routeDivination | 识别 17 类起卦法并路由 | `div:DivinationMethod` 17 子类（`methodCode`） | divinationMethod → 体系(先天/后天)/是否字占/原始数 | divinationMethod, rawInputs | isCharacter, divinationSystem, rawNumber | ✅已确认 |
| 4 | Task_CharSplit | characterSplit | 字占按字数分子法 | `div:CharacterMethod` + 原著字占篇 | 字数→上下卦数（一/二/三/四字以上平仄/十一字以上） | text | upperNumber, lowerNumber | ✅已确认 |
| 5 | Task_Cast | castHexagram | 卦以八除、爻以六除 | R15 内置映射 + 程序取余 | 上/下卦数→八卦；动爻=总数÷6 余（0作6）；重卦→本卦 | rawNumber | originalHexagram, movingLinePosition | ✅已确认 |
| 6 | Task_BodyUse | determineBodyUse | 动者为用、静者为体 | `rel:BodyUseRelation`（R1/R2） | 动爻 1/2/3→下用上体；4/5/6→上用下体 | originalHexagram, movingLinePosition | bodyTrigram, useTrigram | ✅已确认 |
| 7 | Task_Mutual | deriveMutualHexagram | 互卦取中间四爻 | `core:MutualHexagram` + `mutualOf` | 2/3/4 为下、3/4/5 为上；乾坤无互则互其变卦 | originalHexagram | mutualHexagram | ✅已确认 |
| 8 | Task_Changed | deriveChangedHexagram | 动爻反转得变卦 | `core:ChangedHexagram` + `changedFrom` | 动爻阴阳反转、余爻不变 | originalHexagram, movingLinePosition | changedHexagram | ✅已确认 |
| 9 | Task_ShengKe | analyzeShengKe | 体用五行生克 5 级 | `rel:FivePhase` + R3~R7 | 用生体/比和/体克用/体生用/用克体 → 1~5 | bodyTrigram, useTrigram | shengKeType | ✅已确认 |
| 10 | Task_Vitality | analyzeVitality | 卦气旺衰 | `rel:VitalityState` + cal 季节 | 当季与卦五行→旺/相/休/囚/死 | originalHexagram, season | bodyVitality | ✅已确认 |
| 11 | Task_ImageSymbol | extractImageSymbol | 万物类象取象 | `sym:ImageSymbol`（原著类象表） | 本卦/互/变卦按类象表取象，紧扣所问 | originalHexagram, subjectCategory | imageInterpretation | ✅已确认 |
| 12 | Task_Categorize | categorizeDivination | 分类占断 | `jud:SubjectCategory` + yijing 爻辞/反对性情 | 事项类别→分项断法；占物类例引爻辞 | subjectCategory, originalHexagram | categoryVerdict | ✅已确认 |
| 13 | Task_Omen | analyzeOmen | 三要十应互参 | `ome:ThreeEssentials`/`TenResponses` | 以体为主内外合断；无外应则空 | omenDesc, bodyTrigram | omenVerdict | ✅已确认 |
| 14 | Task_Judge | judgeAuspiciousness | 断吉凶·以理统数 | R9~R12 + `jud:Auspiciousness` | 先爻辞→体用生克→克应→动静；定五级+过程描述 | shengKeType, bodyVitality, omenVerdict | auspiciousness, processDescription | ✅已确认 |
| 15 | Task_Response | determineResponsePeriod | 定应期 | `jud:ResponsePeriod` + cal（纳音/长生） | 全卦之数/卦气定级别+范围，依动静调整 | auspiciousness, motion | responsePeriod | ✅已确认 |
| 16 | Task_Advice | generateAdvice | 修身建议 | `jud:SelfCultivationAdvice` | 吉→守正保泰；凶→积德改行 | auspiciousness | advice | ✅已确认 |
| 17 | Task_SendAlert | sendAlert | 重大吉凶外发 | R10 + `RabbitMqHandler` | auspiciousness∈{大吉,大凶}→MQ 外发 | auspiciousness | messageStatus | ✅已确认 |
| 18 | Task_Summary | aggregateResult | 汇总占断结果 | 各域输出汇总 | 组装本卦/互/变/体用/生克/旺衰/类象/吉凶/应期/建议 | auspiciousness | divinationResult | ✅已确认 |

## 5. 逐节点实现方法与算法确认（闸门④）

> 对**每个节点**记录：确认的问题、业务给出的**实现方法**与**算法口径**、边界处理。**未确认不得实现该 Worker。**

| 节点 id | 确认问题 | 实现方法（载体与内容） | 业务结论（算法口径） | 边界/异常处理 | 确认人 | 日期 |
|---|---|---|---|---|---|---|
| Task_PreceptCheck | ①「重卦不占」无历史库如何判定？②「无事」是否仅以 subject 空判定？ | `div:Precept` 四不占个体 + 程序判定 | ①**本次会话内重复**：JVM 会话内 `subject` 去重（`ConcurrentHashMap` 键集），同会话对同一 subject 再次起卦判重卦；跨会话历史不追溯；前端「未重复占/诚心正意」承诺位（`preceptPromise`）携带。②**无事**：`subject` 空白即触发。③**不诚/不义**：`preceptPromise != true` 即触发「不诚不占」 | subject 为 null/空白 → 无事；promise 缺失 → 不诚；会话集命中 → 重卦 | 邵康节 | 2026-10-20 |
| Task_PreceptAlert | 触发条目→告警语映射口径 | `div:Precept` 个体→告警语 | 无事→「无具体事项不可起卦」；重卦→「一事一卦，反复求卦反失其机」；不诚→「意念不纯，请诚心再占」；不义→「此事悖于道义，不可占」 | 未知条目→通用告警语 | 邵康节 | 2026-10-20 |
| Task_Route | 17 类起卦法路由依据 | `div:DivinationMethod` 17 子类（`methodCode`）+程序 | 依原著卷之一十六类+附录一撮金；体系判据：以数起卦（年月日时/物占数/声音占/字占/丈尺占/尺寸物占/一撮金）=**先天**，以象起卦（为人/自己/占动物/占静物/物卦起例/风觉/鸟/听声音/形物/验色）=**后天**；输出 `rawNumber`（编码「上数|下数|动数」）供成卦 | 未知起卦法→以时刻数兜底 | 邵康节 | 2026-10-20 |
| Task_CharSplit | 字占分子法与上下卦数 | `div:CharacterMethod`+原著字占篇 | 一字「左阳右阴」；二字「一字上、一字下」；三字「一字上、二字下」；四字「平分」；四字以上「以平仄取数（平1/上2/去3/入4）」；五字「二上三下」；六字「平分」；七字「三上四下」；八字「平分」；九字「四上五下」；十字「平分」；十一字以上「止用字数平分」 | 空文本→上=下=1；字数不足→按字数兜底 | 邵康节 | 2026-10-20 |
| Task_Cast | 卦以八除、爻以六除 | R15 内置映射（`trigramPairMap`）+程序取余 | 上/下卦数→`((n-1)mod 8)+1` 取八卦；动爻=`((total-1)mod 6)+1`（余0作6）；上下相重→本卦六十四卦名（内置 King-Wen 对表，与 `yijing:Seq_01..64` 名录互参） | 非字占读 `rawNumber`；字占读 `upperNumber/lowerNumber` | 邵康节 | 2026-10-20 |
| Task_BodyUse | 动者为用、静者为体 | `rel:BodyUseRelation`（R1/R2） | 动爻 1/2/3→下卦为用、上卦为体；4/5/6→上卦为用、下卦为体 | 动爻越界→钳制到 1..6 | 邵康节 | 2026-10-20 |
| Task_Mutual | 互卦取中间四爻 | `core:MutualHexagram`+`mutualOf` | 2/3/4 为下、3/4/5 为上；乾坤纯卦无互→互其变卦 | 无互→取变卦为互 | 邵康节 | 2026-10-20 |
| Task_Changed | 动爻反转得变卦 | `core:ChangedHexagram`+`changedFrom` | 动爻阴阳反转、余爻不变 | 动爻越界→钳制 | 邵康节 | 2026-10-20 |
| Task_ShengKe | 体用五行生克 5 级 | `rel:FivePhase`+R3~R7 | 用生体=1(最吉)/比和=2(次吉)/体克用=3(小吉)/体生用=4(有耗)/用克体=5(最凶)；兼查互、变与体之生克 | 五行缺失→比和兜底 | 邵康节 | 2026-10-20 |
| Task_Vitality | season 取值来源 | `rel:VitalityState`+`cal:season` | **前端选填**；缺省由农历月推导（1-3春/4-6夏/7-9秋/10-12冬）；当季与卦五行→旺/相/休/囚/死 | 月数缺失→以「四季」兜底 | 邵康节 | 2026-10-20 |
| Task_ImageSymbol | 万物类象取象 | `sym:ImageSymbol`（原著类象表） | 本卦/互/变卦按类象表取象，紧扣所问（`subjectCategory`）、优先动象 | 无类象→以卦名主象兜底 | 邵康节 | 2026-10-20 |
| Task_Categorize | 分类占断口径 | `jud:SubjectCategory`+`yijing` 爻辞/反对性情 | 事项类别→分项断法（以体为主、用为所应）；占物类例引爻辞 | 未选类别→依生克给通用断语 | 邵康节 | 2026-10-20 |
| Task_Omen | 三要十应互参 | `ome:ThreeEssentials`/`ome:TenResponses` | 以体为主内外合断；无外应则空；静占不论外卦 | `omenDesc` 空→产出空 | 邵康节 | 2026-10-20 |
| Task_Judge | 断吉凶·以理统数 | R9~R12+`jud:Auspiciousness` | 先爻辞→体用生克→克应→动静；综合本/互/变/旺衰/外应定五级+过程描述；`processFlag`（末后不吉）纳入 | 数据缺失→以生克为主判 | 邵康节 | 2026-10-20 |
| Task_Response | 应期档位 | `jud:ResponsePeriod`+`cal` | **按全卦之数分档（近/中/远）**：级别取近/中/远三级，范围按全卦之数换算为日/月/年区间，依动静调整（行速坐迟） | 无 `motion`→按「坐」迟速兜底 | 邵康节 | 2026-10-20 |
| Task_Advice | 修身建议 | `jud:SelfCultivationAdvice` | 吉→守正保泰、戒骄持盈；凶→积德改行、反求诸己 | 平→居中持正 | 邵康节 | 2026-10-20 |
| Task_SendAlert | 重大吉凶外发 | R10+`RabbitMqHandler` | `auspiciousness∈{大吉,大凶}`（level 5/1）→MQ 外发到 `plumblossom.alert.exchange` | MQ 不可用→记错误、不阻断主流程 | 邵康节 | 2026-10-20 |
| Task_Summary | 汇总占断结果 | 各域输出汇总 | 组装本卦/互/变/体用/生克/旺衰/类象/分类/外应/吉凶/过程/应期/建议 | 缺项→以空串占位 | 邵康节 | 2026-10-20 |

## 6. 前端与 API 设计

| 端点 | 方法 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| `/api/plumblossom/divination` | POST | 全量输入变量（§流程§5.2） | `instanceKey`/`processId`/`subject` | 启动流程 |
| `/api/plumblossom/divination/{key}` | GET | 路径 key | `status`/`variables`/结果字段 | 查询状态/结果 |
| `/api/plumblossom/health` | GET | — | `engineAvailable`/`processId`/`bpmnPath` | 健康检查 |

## 7. 配置（`application.yaml`）

| 键 | 值 |
|---|---|
| `server.port` | `9084` |
| `camunda.client.grpc-address` / `rest-address` | `localhost:26500` / `localhost:9080` |
| `ontology.main-path` | `ontology/plumblossom-all.owl`（**第 9 步起仅聚合 TBox**） |
| `ontology.abox-path` | `ontology/plumblossom-abox.owl`（**第 9 步新增**；ABox 聚合入口。**第 11 步起**不再由应用直接加载，改由 OBDA 经 `plumBlossom.properties` 的 `ontop.ontologyFile` 加载，见下行） |
| `ontology.obda-path` / `obda-properties-path` | `ontology/database/plumBlossom.obda` / `.properties`（第 11 步） |
| `ontology.bpmn-path` | `ontology/bpmn/plumBlossomProcess.bpmn` |
| `env-prepare.mysql.db-name` | `plumblossomdb` |
| `env-prepare.rabbitmq.exchange` | `plumblossom.alert.exchange`（direct） |
| `env-prepare.rabbitmq.queue` | `plumblossomAlertQueue`，routing-key `divination.alert.critical` |
| `env-prepare.ontology-catalog` | `ontology/catalog-v001.xml` |
| `env-prepare.ddl-file` / `data-file` | `ontology/database/createdb_tables.ddl` / `data.sql` |

## 8. 解耦与端到端数据流

1. 用户输入起卦信息 → 2. 启动流程 → 3. 戒律校验（前置）→ 4. 起卦成卦 → 5. 体用/互/变 → 6. 生克/旺衰/类象/分类/外应 → 7. 本体推理定吉凶/应期 → 8. 重大吉凶经 RabbitMQ 外发 → 9. 汇总回显。

## 9. API 复用声明（★铁律 21）

- 逻辑一律经 `BackendService` / `OntologyService` / `OBDAHandler` / `ProcessOrchestrator` / `RabbitMqHandler` 等**现存 API**。
- **未重写** `Reference/` 三份 API 文档中的任何 API。
- 若确需新增能力：**暂无**（起卦/成卦等数为本应用业务计算，属应用层；不新增平台 API）。

## 10. 待业务确认项

| # | 问题 | 业务结论 |
|---|---|---|
| 1 | 「重卦不占」如何判定（无历史库）？ | **本次会话内重复+前端承诺**：同一交互内对同一 `subject` 重复起卦即判重卦；跨会话历史不追溯；前端另设「未重复占」承诺位（并入 `preceptPromise`） |
| 2 | 节点 8 季节取值来源（前端选填/由农历月推导）？ | **前端选填**；未填时后端按农历月推导（1-3春/4-6夏/7-9秋/10-12冬） |
| 3 | 应期「级别 + 范围」具体档位口径？ | **按全卦之数分档（近/中/远）**：级别取近/中/远三级，范围按全卦之数换算为日/月/年区间，依动静调整 |

---

## 闸门④留痕
| 日期 | 确认范围（节点集） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-20 | 18 个 ServiceTask 全部（§4 契约表） | 邵康节 | **闸门④通过**：逐节点实现方法与算法口径全部确认，准予实现对应 JobWorker；并裁定 §10 三项（重卦判定=本次会话内重复+前端承诺、season=前端选填缺省由农历月推导、应期=按全卦之数分近/中/远三级） |

---

## 闸门⑩留痕（是否使用数据库 · 第 11 步执行前）

| 日期 | 是否使用数据库 | 库表结构来源（客户提供 / 新建 / 导入已有） | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-20 | **☑使用**（MySQL `plumblossomdb`） | **新建**（AI 依原著/本体裁剪生成 9 表 + 映射 + 样本，经闸门⑩确认） | 邵康节 | **闸门⑩通过**：①**使用数据库**；②数据边界＝**历法 + 周易文本类入库**——历法 38（天干10 / 地支12 / 六十甲子6 / 纳音6 / 长生4）+ 周易文本 78（六十四卦序 64 / 卦辞 2 / 爻辞 12）共 **116 个体**入 `calendar_*` / `yijing_*` 表，并从 `plumblossom-abox.owl` **移除**（DB 为唯一真源，ABox 保留 **129 个体**）；③运行态占断实例 `PB_*` 建 `divination_result` 表并灌典型样本 6 条；④同步改合规测试 P-2（校验 `data.sql` 含 64 卦名）与 `PlumBlossomOntologyJobWorker` 互校逻辑（经 OBDA 查询）。 |

> 若使用数据库：客户提供的库表结构须以其实际给出为准，**不得臆造**（★8/10）；不使用时第 11 步产出为空。

> 本文件亦承载**闸门⑥（SKOS 词汇表）**留痕（见文末同名小节）。

## 闸门⑪留痕（如何导入/生成完整数据 · 第 12 步执行前）

| 日期 | 数据来源（客户提供 / AI 生成 / 结合） | 导入方式 | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-20 | **AI 生成**（依原著《梅花易数》+《周易》经传整理，逐条标注出处） | **延续经数据库入库（OBDA）** | 邵康节 | **闸门⑪通过**：第 12 步「ABox 扩至全集」——六十四卦序 / 卦辞 64 / 爻辞 384 / 纳音 30 / 六十甲子 60 / 类象全集由 AI 依原著与《周易》生成并标注出处；继续入 MySQL 并扩展 OBDA 映射，本体文件仅保留结构性 ABox（DB 为唯一真源，与第 11 步一致）。 |
| 2026-10-20 | — | — | 邵康节 | **命名口径确认**：名录 `core:Hexagram_01..64`、卦辞 `yijing:Dictum_Hex_01..64`、爻辞 `yijing:LineDictum_<nn>_<pos>`（重命名原乾坤个体）；类象 `sym:Symbol_<卦>_<维度>_<序号>`。**类象范围确认**：纳入「八卦万物类占」全表 27 栏（新增 19 个 `sym:SymbolDimension`）。 |

> **第 12 步执行结果（闸门⑪）**：DB 侧 `data.sql` 扩至历法 116（天干10 / 地支12 / 纳音30 / 六十甲子60 / 长生4）+ 周易文本 512（卦序64 / 卦辞64 / 爻辞384）+ 占断样本 6；本体侧 `plumblossom-abox.owl` 由 129 扩至 **1133 个体**（名录 64 + 爻 384 + 类象 559 + 维度 27 等）。回归 `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（合并闭包 `axioms=7256`、`individuals=1133`）。详见《PlumBlossom应用-第12步回归报告》。

---

## 闸门⑥留痕（SKOS 词汇表 · 第 13 步）

> **SKILL 修订（2026-10-20，客户指令）**：本步内涵修订为——SKOS 词汇表用于**解决「不同名同义、同义不同名」**，收录**输入/跨系统交互词汇**的**别名、缩写、习惯用法、口语、书面语/雅称**等，归并到本体的**规范概念**。已同步修订 `OntologyAppBuilder/SKILL.md` 第 13 步、闸门汇总表⑥行及第 14 步校验项，随后**重跑本步**。

**产出**：`ontology/plumblossom-terminology.ttl`（SKOS ConceptScheme 根 `<http://example.org/plumblossom/term>`，命名空间 `.../term/`）。

**构表口径**：
- **规范概念只来源于本体**——收录 TBox 九域中**带 `rdfs:label`** 的全部实体（`owl:Class` 71 / `owl:ObjectProperty` 38 / `owl:DatatypeProperty` 41 = **150** 条），**不得凭空造词**；无 `rdfs:label` 的结构性/逆向属性（如 `core:isUpperTrigramOf`）不收录。
- 每条概念：`skos:prefLabel@zh`（取自 `rdfs:label`）、`skos:altLabel@zh`（同义词/别名）、`skos:definition@zh`（取自 `rdfs:comment`，如有）、`skos:broader`（取自 `rdfs:subClassOf`）、`skos:exactMatch`（指向本体 IRI，如 `<cal:CalendarDatum>`、`<core:Hexagram>`）。
- **同义词（`skos:altLabel`）**：**逐条标注来源**——主要取自原著《梅花易数》（故宫珍藏善本，提取文本 `orig_0000x.txt`）与《梅花易数全面指南》，如 正卦（本卦）、端法/以数起卦（先天起卦法）、风觉鸟占、四不占、十应奥论、三要灵验、体用、生克比和、衰旺、花甲、卦德、类象/取象/万物类占 等。**共 41 个概念带同义词，计 57 条**。（网络检索在本环境不可达，故仅采信原著与仓储内指南两类可追溯来源。）

**交叉校验（生成器自检，已通过）**：概念 150；`exactMatch` 断言 150，全部指向存在的本体术语；**无孤立词条**、**无未覆盖的带标签本体术语**（双向一一对应）；同义词表键全部命中本体术语（无凭空词）。

**接线（闸门⑥已裁定 · 客户确认）**：`plumblossom-tbox.owl` 增 `<owl:imports rdf:resource="http://example.org/plumblossom/term"/>`；`catalog-v001.xml` 增映射 `name="http://example.org/plumblossom/term" uri="plumblossom-terminology.ttl"`。使 `SkosSynonymReader`/`OntologyLabelMatcher` 经 `BackendService` 的 TBox imports 闭包读取 SKOS 注解。此为对已定稿本体的**接线改动**，已依 §3.2 经客户确认后执行。

| 日期 | 术语与同义词表 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-20 | 初版 150 概念（无 altLabel、未接线） | 邵康节 | **退回并给出修订方向**：SKOS 须收录别名/缩写/习惯用法/口语/书面语以解决「不同名同义、同义不同名」；同义词来源取原著与经典；并接线进 TBox。**要求先改 SKILL 第 13 步再重跑**。 |
| 2026-10-20 | `plumblossom-terminology.ttl`（150 概念 + 57 同义词，已接线） | 邵康节 | **闸门⑥通过**：确认 150 条规范概念、57 条同义词/别名及其**来源与归属**均无误，接线方式（`owl:imports` + catalog）确认；准予进入第 14 步。 |

> **第 13 步执行结果（重跑）**：`ontology/plumblossom-terminology.ttl` 共 **150 概念 / 57 同义词**。接线：`plumblossom-tbox.owl`（+`owl:imports`）、`catalog-v001.xml`（+映射）。回归测试同步适配（`PlumBlossomOntologyComplianceTest` 加载与框架一致——为 `.ttl` 补注册 `SimpleIRIMapper`；个体统计剔除 SKOS 命名空间，`individuals(OWL)=1133` 不变）。回归 `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（合并闭包 `closureSize=14`、`axioms=7851`）。详见《PlumBlossom应用-第13步回归报告》。
>
> 注：SKOS 术语表经 `owl:imports` 并入 TBox 后，本体闭包新增 SKOS 三元组（`axioms` 7256→7851，闭包本体数 13→14），但**OWL 本体个体数仍为 1133**（SKOS `skos:Concept` 不计入 OWL 个体）。
>
> **第 14 步执行结果（词汇表测试 + 全流程测试）**：`PlumBlossomSkosTerminologyTest` 由 3 项扩充为 **6 项**——新增 **X-1**（每概念 `skos:exactMatch` 目标存在且为本体已声明实体，无孤立词条）、**X-2**（本体中每个带 `rdfs:label` 实体均有对应概念，无遗漏）双向交叉校验与 **S-1**（同义词非空、中文、不与首选标签重复）。离线回归 `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（合规 6 + SKOS 6 全绿，环境类按 `ServiceProbe` 跳过）。日志 `HWCodeArtsDir/mvn_step14.log`。
