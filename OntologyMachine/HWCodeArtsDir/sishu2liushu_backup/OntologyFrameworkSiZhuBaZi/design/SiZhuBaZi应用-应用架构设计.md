# SiZhuBaZi应用 — 应用架构设计

> **用法**：本文件产出第 7 步。**闸门④**：业务人员对 **BPMN 的每个节点**逐项确认「**实现方法（载体不限：自然语言描述 / 程序 / 外部调用等）+ 算法**」后方可实现对应 JobWorker。本模板亦承载**闸门⑩（是否使用数据库）**与**闸门⑪（如何导入/生成完整数据）**的留痕。
> **基线**：`OntologyFrameworkExample/design/Example应用-顶层设计.md` §2–6；同类术数形态 `OntologyFrameworkPlumBlossom/design/PlumBlossom应用-应用架构设计.md`；代码基线 `ExampleOntologyJobWorker.java`、`ExampleApplication.java`、`web/ExampleController.java`、`src/main/resources/application.yaml`。

| 项目 | 内容 |
|---|---|
| 应用 | SiZhuBaZi（四柱命理 / 八字推命） |
| 文档日期 | 2026-10-29 |
| 当前版本 | **v1（初稿）** |
| 关联流程设计 | 《SiZhuBaZi应用-流程设计》（v1 定稿·修订三，闸门①+逐节点①-1…①-21 已完成） |
| 关联本体设计 | 《SiZhuBaZi应用-本体顶层设计》（v1 定稿，闸门②已确认） + 《SiZhuBaZi应用-本体核心设计》（v1 定稿，闸门③/⑦已确认） |
| 关联流程文件 | `ontology/bpmn/sizhubaziProcess.bpmn`（21 ServiceTask + 3 排他网关 + 汇合点 J） |
| 激活 Profile | `SiZhuBaZiBPMN` |
| Worker 类 | `SiZhuBaZiOntologyJobWorker`（**单类多方法**：21 个 `@JobWorker`，以 jobType 字面量分发） |
| 进程 id | `SiZhuBaZiProcess` |

> 依据：定稿流程设计 + 已确认本体设计 + 实际生成的 BPMN（21 serviceTask / 6 exclusiveGateway）。**算法一律来自业务确认，不得臆造。**

---

## 1. 架构总览与分层

```
┌─────────── 前端层 ───────────────────────────┐
│ static/index.html（生辰录入 · 单命/合婚 · 轮询）│
└───────────▼──────────────────────────────────┘
┌─────────── API 层 ───────────────────────────┐
│ SiZhuBaZiController（/api/sizhubazi/*）       │
└───────────▼──────────────────────────────────┘
┌─────────── 编排层（Zeebe）────────────────────┐
│ sizhubaziProcess.bpmn（SiZhuBaZiProcess）     │
│ 21 serviceTask · G1/G2/G3 排他网关 · 汇合点 J │
└───────────▲──────────────────────────────────┘
            │ jobType 字面量绑定
┌───────────┴── 应用层 ───────────────────────┐
│ SiZhuBaZiOntologyJobWorker（21 个 jobType） │
│ SiZhuBaZiApplication                        │
└──┬──────────┬──────────┬────────────────────┘
┌──▼───────┐┌─▼────────┐┌─▼──────────┐
│本体层     ││数据层    ││消息层       │
│OWL/SWRL  ││Ontop→MySQL││RabbitMQ    │
│Openllet  ││(第11步)   ││(告警外发)   │
└──────────┘└──────────┘└────────────┘
```

## 2. 技术栈与模块依赖

### 2.1 关键依赖（对齐 `OntologyFrameworkExample/pom.xml`）
| 依赖 | 版本 | 作用 |
|---|---|---|
| `spring-boot-starter-parent` | 4.1.0 | 父 POM |
| `spring-boot-starter-web` | 4.1.0 | REST + 静态页 |
| `spring-boot-starter-amqp` | 4.1.0 | RabbitMQ 告警外发 |
| `io.camunda:spring-boot-starter-camunda-sdk` | 8.9.6 | Camunda 8 / Zeebe 客户端 |
| `io.camunda:zeebe-process-test-extension` | 8.9.6 | 内存引擎端到端测试（第 8 步） |
| `com.ocean:OpenlletResolver` | 0.0.1-SNAPSHOT | OWL API + Openllet 推理 |
| `com.ocean:OntopOBDAHandler` | 0.0.1-SNAPSHOT | Ontop OBDA → MySQL |
| `com.ocean:Utilities` | 0.0.1-SNAPSHOT | 共享工具（`OntologyWorkerSupport`/`ProcessOrchestrator`/`RabbitMqHandler`/`ChatAgent`） |
| `org.apache.jena:jena-arq` / `jena-rdfconnection` | 6.1.0 | SPARQL / 连接 |
| `net.sourceforge.owlapi:owlapi-parsers` | 5.5.1 | 本体解析 |

> `OntopOBDAHandler` 依赖须保留 `log4j-slf4j2-impl` 排除（对齐 Example）。

### 2.2 模块间依赖
```
OntologyFrameworkSiZhuBaZi
        ├── OpenlletResolver
        ├── OntopOBDAHandler
        └── Utilities
```

## 3. 组件与包结构

### 3.1 主代码（`src/main/java/com/ocean/ontologyframework/sizhubazi/`）
| 文件 | 职责 |
|---|---|
| `SiZhuBaZiApplication.java` | `@SpringBootApplication`；`setAdditionalProfiles("SiZhuBaZiBPMN")`；原生访问告警 |
| `SiZhuBaZiOntologyJobWorker.java` | `@Component @Profile("SiZhuBaZiBPMN")`，继承 `OntologyWorkerSupport`；**21 个 `@JobWorker`**（jobType 见 §4） |
| `web/SiZhuBaZiController.java` | `@RestController`；`POST /api/sizhubazi/divine`、`GET /api/sizhubazi/divine/{key}`、`GET /api/sizhubazi/health`（第 5 步已产出） |

### 3.2 前端（`src/main/resources/static/`）
| 文件 | 职责 |
|---|---|
| `index.html` | 生辰录入（公历/农历 · 性别 · 对象类型 · 年月日时辰 · 经度 · 模式单命/合婚 · 输出粒度）；轮询流程状态并分区渲染排盘 / 批断 / 告警（第 5 步已产出） |

### 3.3 环境启停脚本（`<模块根>/start-env.cmd`）
> **照 `OntologyFrameworkExample/start-env.cmd` 的形态**生成，一键**准备并启动**运行环境；与 `SiZhuBaZiApplication` 及 `EnvPrepare-0.0.1-SNAPSHOT.jar` 同目录。

| 步骤 | 命令 | 说明 |
|---|---|---|
| 1 | `EnvPrepare-0.0.1-SNAPSHOT.jar start` | **新窗口常驻**拉起 application.yaml 中**已声明**的服务（MySQL / Ontop / RabbitMQ / Camunda **按需**——未声明者 EnvPrepare 自动 `[SKIP]`），对**已声明的持久化服务**（MySQL 库 / RabbitMQ 资源）**缺则建、有则留**，不删已有数据 |
| 2 | `EnvPrepare-0.0.1-SNAPSHOT.jar init` | 可选：**清空重建已声明的持久化服务**后再常驻启动——由脚本交互式询问是否重建触发 |

- 脚本用 `%~dp0` 定位自身（**不硬编码路径**）、先 `chcp 936 >nul`、`errorlevel` 校验并在失败时 `pause`。
- **须在模块根放置 `EnvPrepare-0.0.1-SNAPSHOT.jar`（可执行 fat jar）**：来源 `OntologyMachine/build/EnvPrepare-0.0.1-SNAPSHOT.jar`。
- **停止环境 = 关闭该常驻窗口**。
- **编码/换行硬约束**：`.cmd` 须存 **GBK(ANSI) + CRLF**。

### 3.4 IDEA 运行配置（`<工作区根>/.idea/runConfigurations/SiZhuBaZiApplication.xml`）
> **Spring Boot 类型**运行配置，主类 = `SiZhuBaZiApplication`——启动该应用即由 Spring 注册并运行 `SiZhuBaZiOntologyJobWorker` 的全部 `@JobWorker`。
> ⚠️ **落点：工作区根（IDEA 工程根 = `OntologyMachine/`）的 `.idea/runConfigurations/`**，不放模块目录内。

| 项 | 值 |
|---|---|
| 配置名 | `SiZhuBaZiApplication` |
| 类型 | `SpringBootApplicationConfigurationType`（Spring Boot） |
| `<module name>` | `OntologyFrameworkSiZhuBaZi` |
| `SPRING_BOOT_MAIN_CLASS` | `com.ocean.ontologyframework.sizhubazi.SiZhuBaZiApplication` |
| `WORKING_DIRECTORY` | `$MODULE_WORKING_DIR$` |
| `VM_PARAMETERS` | `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow` |

## 4. BPMN 节点 ⇄ JobWorker 契约表（核心）

> **一一对应**：BPMN 每个 `serviceTask` 的 `zeebe:taskDefinition type` ⇄ 一个 `@JobWorker(type)`。共 **21** 个（jobType 唯一）。此表由 `scripts/validate_structure.py` 做机检。

| # | BPMN 节点 id | jobType | 业务规则（来自流程设计 §3） | 实现方法（载体不限·业务确认） | 算法（业务确认） | 输入变量 | 输出变量 | 确认状态 |
|---|---|---|---|---|---|---|---|---|
| 1 | `Task_ParseInput` | `parseBirthInput` | 公历/农历识别互转 · 真太阳时校正 · 历法边界 · 早晚子时 · 登记性别/对象/模式 | **程序**（历法换算，★13 外置）+ **本体**（登记 `chart:BirthInput`） | 公农历互转（含闰月）；真太阳时差＝(经度−120)/15 小时；历法边界规则；**早晚子时＝子初换日 23:00**（客户裁定 ④） | `birth, partnerBirth, calendar, gender, birthPlace, subjectType, inputMode, granularity` | `chartBase, subjectType, inputMode` | ☐待确认 |
| 2 | `Task_CastPillars` | `castFourPillars` | 排四柱 · 大运/小运/交运 · 胎元 · 命宫 | **程序**（五虎遁/五鼠遁数值计算）+ **本体**（`chart:FourPillars` 等）+ **SWRL R5/R6**（起运顺逆） | 五虎遁起月、五鼠遁起时；以节气定月柱；**阳男阴女顺、阴男阳女逆**；**三日折一岁**（`pilotAge`）；胎元/命宫起例 | `chartBase` | `fourPillars, fortuneSequence, startAge, minorFortunes, fetalOrigin, lifePalace` | ☐待确认 |
| 3 | `Task_NayinHiddenStems` | `analyzeNayinHiddenStems` | 纳音 · 藏干 · 日柱特性 | **查表（OBDA MySQL）**（`sexagenary_cycle`/`nayin`/`hidden_stem`）+ **本体** `core:NaYin`/`HiddenStem` | 六十甲子→纳音（30 组）；支藏人元司事；日柱干支特性 | `fourPillars` | `nayin, hiddenStems, dayPillarTraits` | ☐待确认 |
| 4 | `Task_GanzhiRelations` | `analyzeGanzhiRelations` | 干支作用：五合/六合/三合/冲/穿/刑/墓库 · 暗合暗拱遥冲 · 合绊 | **程序**（合冲刑穿判定）+ **SWRL R15** + **本体** `relations:` | 天干五合、地支六合/三合半合/冲/穿/三刑/墓库刑冲；**合而不合/进交退伏**判定；合化结果 | `fourPillars, fortuneSequence` | `ganzhiRelations` | ☐待确认 |
| 5 | `Task_TenGodsKin` | `assignTenGodsKin` | 十神 · 六亲 · 宫位 · 十神生克制化 | **查表（OBDA `ten_god_map`）**+ **本体** `tengod:`（R7 由程序：10×10 查表） | 以日干为参照：生克同类得十神；十神↔六亲/宫位配属；官杀混杂/食伤制杀/财官相生 | `fourPillars, hiddenStems` | `tenGods, kinMapping, tenGodsRelations` | ☐待确认 |
| 6 | `Task_Deities` | `arrangeDeities` | 神煞（贵人吉神/禄马/凶煞）· 空亡（旬空/截路/四大空亡） | **查表（OBDA `deity_rule`）**+ **本体** `deity:`（R13/R14 查表） | 神煞起例（以柱干支查）；旬空起例；异名异法并陈 | `fourPillars, fortuneSequence` | `deities, voidness` | ☐待确认 |
| 7 | `Task_Strength` | `assessDayMasterStrength` | 日主旺衰 · 寄生十二宫 · 中和 | **程序**（多因子综合，**不设数值权重**，客户裁定 ⑰）+ **本体** `yongshen:Vitality`/`core:LifeStage`（R9） | 得令/得地/得势综合；得时不旺·失时不弱；寄生十二宫；中和判定 | `fourPillars, hiddenStems` | `strength, twelvePalaces, balance` | ☐待确认 |
| 8 | `Task_Pattern` | `determinePattern` | 格局（八正格/杂气/外格杂格）· 成败救应 · 高低 | **程序**（分类）+ **SWRL R10**（简化）+ **本体** `pattern:` | 月令取格；成败救应（四吉神破格/四凶神成格）；纯杂 | `fourPillars, tenGods, strength` | `patternResult, rescueAdjust, patternGrade` | ☐待确认 |
| 9 | `Task_YongShen` | `selectYongShen` | 取用（用/相/忌）· 多法冲突并陈 · 调候【待考】 | **程序**（编排「主取」，月令格局法优先）+ **SWRL R11** + **本体** `yongshen:`/`diagnosis:SchoolVariant` | 用神专求月令（主取）；相神/忌神；调候法/旺衰法异法结果挂 `SchoolVariant` | `fourPillars, patternResult, strength` | `yongShen, xiangShen, jiShen, yongShenMethods` | ☐待确认 |
| 10 | `Task_ChartLevel` | `judgeChartLevel` | 命局层次（贵/富/平常/贫贱）· 清浊真假 | **程序**（综合评级，客户裁定 ⑬）+ **本体** `yongshen:Level`/`Purity`（R12） | 格局成败 + 用神有力 + 相神得力 + 忌神受制 → 四档；清浊真假隐显众寡 | `patternResult, yongShen, strength` | `chartLevel, purityTruth` | ☐待确认 |
| 11 | `Task_FortuneCycles` | `analyzeFortuneCycles` | 大运/流年太岁/流月流日流时 · 特殊岁运格局 · 运化气 | **程序**（岁运推算，**默认年级** ⑭）+ **本体** `fortune:`（R16/R17，细化【待考】） | 大运/流年干支；特殊岁运（岁运并临/天克地冲/伏吟/反吟/桃花红鸾）；化气十段锦 | `fourPillars, fortuneSequence, granularity` | `fortuneCycles, specialCycles` | ☐待确认 |
| 12 | `Task_Timing` | `inferTimingAndStages` | 应期（填实/冲实/合住/透出/变应）· 运限法 | **程序** + **本体** `fortune:ResponsePeriod`/`LifeStageLimit`、`deity:Void`（R18） | 应期取用；运限「月管初主、日管中主、时管末主、年则总统之」 | `fourPillars, fortuneCycles, deities, voidness` | `timing, lifeStages` | ☐待确认 |
| 13 | `Task_Synthesize` | `synthesizeFortuneChart` | 岁运与原局综合辩证断（优先级/多势/吉凶转化/叠加） | **程序** + **本体** `fortune:FortuneSynthesis` | 岁运与原局叠加；成格变格；吉凶转化 | `fourPillars, fortuneCycles, timing` | `fortuneTrend, transformationNote` | ☐待确认 |
| 14 | `Task_CategoryVerdicts` | `generateCategoryVerdicts` | 性情相貌才德 · 疾病 · 六亲吉凶 · 分类事项（事业/财运/婚姻/寿夭/飘荡） | **本体** `reading:`（`Temperament`/`Disease`/`KinshipFortune`/`CategoryFortune`）+ **程序**（编排，⑱ 四书原语 + 现代白话，保留出处） | 各类断法逐条取四书原义；疾病以五脏六腑所属干支 | `fourPillars, fortuneCycles, tenGods, deities` | `categoryVerdicts` | ☐待确认 |
| M1 | `Task_MarriageMatch` | `analyzeMarriageMatch` | 合婚：双方各自复用节点 2~14 全链 + 互补判定 | **程序**（编排，双方各跑一轮 2~14）+ **本体** `reading:MarriageMatch`（R23） | 双方八字/五行/神煞/用神互补；《神峰通考》「男重比劫↔女重伤食」正理（⑲ 双方完整分析） | `chartBase, partnerBirth` | `marriageConclusion, complementAnalysis` | ☐待确认 |
| W | `Task_FemaleChart` | `analyzeFemaleChart` | 女命专论（夫子/性情/贵贱） | **本体** `reading:LadyFortune` + **程序** | 女命总诀/女命富贵贫贱篇 | `fourPillars, fortuneCycles` | `femaleVerdict` | ☐待确认 |
| C | `Task_ChildChart` | `analyzeChildChart` | 小儿关杀 | **本体** `reading:ChildFortune` + **程序** | 小儿关杀例 | `fourPillars, fortuneCycles` | `childVerdict` | ☐待确认 |
| 15 | `Task_MoralAdvice` | `generateMoralAdvice` | 修身处世劝诫（吉→守正持盈戒骄；凶→积德改行反求诸己） | **本体** `reading:Admonition` + **程序** | 据层次（节点 10）+ 岁运吉凶（节点 13）+ 分类断（节点 14）；溯《滴天髓》「知命」 | `chartLevel, fortuneTrend, categoryVerdicts` | `moralAdvice` | ☐待确认 |
| 16 | `Task_Aggregate` | `aggregateReading` | 综合结论汇总（命书式）；触发告警标志检测 | **程序**（编排，㉑ 命书体）+ **本体** `reading:ReadingConclusion` + **SWRL R19/R20/R21**（告警标志） | 汇总排盘/十神/神煞/格局/用神/层次/岁运/应期/分类断；告警综合判据由程序编排，SWRL 落最终标志 | `chartBase, fourPillars, chartLevel, fortuneCycles, categoryVerdicts, moralAdvice` | `reading, alertTriggered` | ☐待确认 |
| A | `Task_SendAlert` | `sendAlert` | 外发告警消息（大凶/寿元关/重大疾病；无数值阈值） | **外部调用**（`RabbitMqHandler`）+ **SWRL R22**（`alertTriggered=true` → `SwrlRuleTriggerListener`） | SWRL 命中即外发；外发失败仅记日志、不阻断主流程 | `chartBase, chartLevel, reading` | `messageStatus` | ☐待确认 |
| 17 | `Task_PersistReturn` | `persistAndReturn` | 结果落库与返回（命例历史） | **程序**（落库）+ **本体** `reading:CaseRecord`（⑲ 完整存入含出生地、不脱敏，裁定 ㉓） | 命主生辰/排盘/批断结果入库；返回前端 | `chartBase, fourPillars, reading, messageStatus` | `caseRecord, response` | ☐待确认 |

> **网关 ⇄ 条件（BPMN 已落地）**：G1 `Gateway_Mode`（`inputMode = "合婚"` / `"单命"`）；G2 `Gateway_Subject`（`subjectType = "女命"` / `"小儿"` / `"男命"`）；G3 `Gateway_Level`（`alertTriggered = true` / `false`，由节点 16 读 SWRL 标志写回）。汇聚网关 `Gateway_SubjectMerge`/`Gateway_LevelMerge`；汇合点 `Gateway_MergeJ`（M1 与主链节点 15 后汇聚）。

## 5. 逐节点实现方法与算法确认（闸门④）

> 对**每个节点**记录：确认的问题、业务给出的**实现方法**（载体不限）与**算法口径**、边界处理。**未确认不得实现该 Worker。** 下表「业务结论」为待确认的建议口径（★：由程序/查表承载者依《本体核心设计》§5「由程序实现」标注）。

| 节点 id | 确认问题 | 实现方法（载体与内容） | 业务结论（算法口径，待确认） | 边界/异常处理 | 确认人 | 日期 |
|---|---|---|---|---|---|---|
| `Task_ParseInput` | 历法/真太阳时/边界/子时如何实现？ | 程序：历法换算 + 经度时差；本体登记 `chart:BirthInput` | 早子时/晚子时以子初换日 23:00 分（④）；闰月/夏令时/时区按惯例；缺经度用平太阳时 | 非法日期/缺经度/缺时辰 → 校验失败终止 | | |
| `Task_CastPillars` | 四柱/大运/胎元/命宫起例？ | 程序数值计算 + 本体 + R5/R6 | 五虎遁/五鼠遁；节气定月；阳男阴女顺、阴男阳女逆；三日折一岁 | 历法边界致时辰不可定 → 标记需人工确认并终止 | | |
| `Task_NayinHiddenStems` | 纳音/藏干取数来源？ | 查表（OBDA MySQL） + 本体 | 六十甲子→纳音 30 组；支藏人元司事 | 库不可用 → 降级跳过并告警 | | |
| `Task_GanzhiRelations` | 合冲刑穿的成立与化气？ | 程序判定 + R15 + 本体 | 合而不合/进交退伏；合化结果至多一五行 | 无 | | |
| `Task_TenGodsKin` | 十神如何得？ | 查表（`ten_god_map`） + 本体（R7 由程序） | 以日干为参照 10×10 查表；六亲/宫位配属 | 无 | | |
| `Task_Deities` | 神煞/空亡起例？ | 查表（`deity_rule`） + 本体（R13/R14） | 起例表庞大、异名异法并陈（⑧ 以《三命通会》卷二为主） | 无 | | |
| `Task_Strength` | 旺衰是否设权重？ | 程序多因子 + 本体（R9） | **不设数值权重**（⑰）；得时不旺·失时不弱；中和 | 无 | | |
| `Task_Pattern` | 格局与成败救应？ | 程序分类 + R10 + 本体 | 月令取格；四吉神破格/四凶神成格；纯杂 | 无 | | |
| `Task_YongShen` | 多法冲突如何主取？ | 程序编排 + R11 + 本体 | 用神专求月令（主取）；调候法【待考】、旺衰法异法挂 `SchoolVariant` | 调候文本缺 → 标【待考】 | | |
| `Task_ChartLevel` | 层次四档如何综合？ | 程序评级 + 本体（R12） | 综合评级（⑬）；清浊真假隐显众寡 | 无 | | |
| `Task_FortuneCycles` | 输出粒度与岁运细化？ | 程序推算 + 本体（R16/R17） | **默认年级**（⑭），月/日可选；流日/流时【待考】；特殊岁运细化【待考】 | 【待考】处挂 `diagnosis:ToBeVerified` | | |
| `Task_Timing` | 应期与运限口径？ | 程序 + 本体（R18） | 填实/冲实/合住/透出/变应；运限分主阶段 | 无 | | |
| `Task_Synthesize` | 岁运如何综合辩证？ | 程序 + 本体 | 优先级/多势/吉凶转化/叠加 | 无 | | |
| `Task_CategoryVerdicts` | 断语措辞口径？ | 本体 + 程序编排 | 四书原语 + 现代白话转述、保留出处（⑱）；劝诫属节点 15 | 无 | | |
| `Task_MarriageMatch` | 合婚分析范围？ | 程序编排 + 本体（R23） | 双方各自完整分析（复用 2~14）+ 互补；《神峰通考》正理（⑲） | 缺一方生辰 → 校验失败终止 | | |
| `Task_FemaleChart` | 女命专论口径？ | 本体 + 程序 | 女命总诀/富贵贫贱（对象类型=女命） | 无 | | |
| `Task_ChildChart` | 小儿关杀口径？ | 本体 + 程序 | 小儿关杀例（对象类型=小儿） | 无 | | |
| `Task_MoralAdvice` | 劝诫依据？ | 本体 + 程序 | 层次 + 岁运 + 分类断；《滴天髓》「知命」 | 无 | | |
| `Task_Aggregate` | 输出格式 & 告警判据？ | 程序编排 + 本体 + R19~R21 | **命书体**（㉑）；告警综合判据由程序，**不设阈值**（㉒），SWRL 落最终标志 | 无 | | |
| `Task_SendAlert` | 外发通道与失败策略？ | 外部调用 `RabbitMqHandler` + R22 | SWRL 命中即外发；仅三类告警 | 外发失败 → 记日志不阻断主流程 | | |
| `Task_PersistReturn` | 隐私口径？ | 程序落库 + 本体 | 完整存入（含出生地）、不脱敏（㉓） | 落库失败 → 回 `INCIDENT` | | |

## 6. 前端与 API 设计

| 端点 | 方法 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| `/api/sizhubazi/divine` | POST | `birth, partnerBirth, calendar, gender, birthPlace, subjectType, mode, granularity` | `instanceKey, processId, mode` | 启动推演流程 |
| `/api/sizhubazi/divine/{key}` | GET | 路径 `key` | `status, variables{...}`（完成时含 `fourPillars/tenGods/deities/patternResult/yongShen/chartLevel/fortuneCycles/timing/categoryVerdicts/reading/marriageConclusion/femaleVerdict/childVerdict/moralAdvice/alertTriggered/caseRecord`） | 查询状态/结果 |
| `/api/sizhubazi/health` | GET | — | `engineAvailable, processId, bpmnPath` | 健康检查 |

## 7. 配置（`application.yaml`）

| 键 | 值 |
|---|---|
| `server.port` | **9088**（规避已占端口 9081~9087） |
| `camunda.client.grpc-address` / `rest-address` | `localhost:26500` / `localhost:9080` |
| `ontology.main-path` | `ontology/sizhubazi-all.owl` |
| `ontology.abox-path` | `ontology/sizhubazi-abox.owl`（第 9 步分离后） |
| `ontology.obda-path` / `obda-properties-path` | `ontology/database/sizhubazi.obda` / `ontology/database/sizhubazi.properties` |
| `ontology.bpmn-path` | `ontology/bpmn/sizhubaziProcess.bpmn` |
| `env-prepare.mysql.db-name` | `sizhubazidb` |
| `env-prepare.rabbitmq.*` | exchange `sizhubazi.alert.exchange`（direct）/ queue `sizhubaziAlertQueue` / routing-key `fortune.alert.critical` |
| `env-prepare.*` | 按需声明（需 MySQL/Ontop/RabbitMQ/Camunda 四服务） |

## 8. 解耦与端到端数据流

1. 用户录入生辰（单命/合婚）→ 2. `POST /divine` 启动 `SiZhuBaZiProcess` → 3. 节点 1 解析校正写 `chartBase` → 4. 节点 2 排四柱 → 5. 节点 3~14 逐环推理/查表（本体 `BackendService` + Ontop）→ 6. 网关 G1/G2 分支（合婚/女命/小儿）→ 7. 节点 15/16 汇总 + SWRL 告警标志 → 8. G3 → 节点 A 经 RabbitMQ 外发 → 9. 节点 17 落库并返回 → 10. 前端轮询渲染。

## 9. API 复用声明（★铁律 21）

- 逻辑一律经 `BackendService` / `OntologyWorkerSupport` / `OBDAHandler` / `ProcessOrchestrator` / `RabbitMqHandler` / `ChatAgent` 等**现存 API**。
- **未重写** `Reference/` 三份 API 文档中的任何 API（清单见 `references/API复用清单.md`）。
- 若确需新增能力：**内置映射/查表**（十神/神煞/空亡/纳音/藏干/起例）随生成脚本维护，不新增平台 API。

## 10. 待业务确认项

| # | 问题 | 业务结论（待确认） |
|---|---|---|
| 1 | 21 个 Worker 的实现方法/算法是否认可？ | **已确认（2026-10-29 客户「全部认可」）**：依 §5 口径（程序计算/查表 + 本体 + SWRL 简化） |
| 2 | 端口 9088 是否可用？ | **已确认（2026-10-29）**：使用 9088 |
| 3 | 分项断语白话措辞是否合宜？ | **已确认（2026-10-29）**：依 ⑱ 四书原语 + 现代白话、保留出处 |

---

## 闸门④留痕
| 日期 | 确认范围（节点集） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-29 | 初稿：21 个节点（1~17 + M1/W/C/A）实现方法与算法建议口径 | 码道 | **待领域专家逐节点确认（闸门④）**——确认后方可实现对应 `@JobWorker`。 |
| 2026-10-29 | 全部 21 个节点（1~17 + M1/W/C/A） | 领域专家（客户） | **全部认可**——认可 §5 各节点的「实现方法（程序计算/查表 OBDA + 本体 + SWRL 简化）」与「算法口径」；认可端口 **9088**；认可分类断语「四书原语 + 现代白话、保留出处」；认可《穷通宝鉴》调候、流日/流时相关条文暂标【待考】。**据此进入 `SiZhuBaZiOntologyJobWorker` 实现。** |

---

## 闸门⑩留痕（是否使用数据库 · 第 11 步执行前）

| 日期 | 是否使用数据库 | 库表结构来源（客户提供 / 新建 / 导入已有） | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-29 | ☑使用（查表型历法/神煞起例/十神映射 + 运行态命例/告警入库 MySQL，经 Ontop 虚拟访问） | **新建**（依《本体核心设计》§8 规划表，**11 张表**：ganzhi_stem/ganzhi_branch/sexagenary_cycle/nayin/hidden_stem/solar_term/month_order/deity_rule/ten_god_map/case_record/alert_flag） | 领域专家 | **闸门⑩通过**：采用 **A 迁移口径**——DB 为唯一真源，查表型历法个体自 `sizhubazi-abox.owl` 迁出（7 类 56 个体），ABox 仅保留结构性/静态个体；产出 `ontology/database/{createdb_tables.ddl,data.sql,sizhubazi.obda,sizhubazi.properties}`，`ontop.ontologyFile` 指 `sizhubazi-abox.owl`。经 Ontop OBDA 取数验证（天干10/地支12/六十甲子6/纳音6/藏干4/节气6/月建12）。 |

> 若使用数据库：客户提供的库表结构须以其实际给出为准，**不得臆造**（★8/10）；不使用时第 11 步产出为空。

## 闸门⑪留痕（如何导入/生成完整数据 · 第 12 步执行前）

| 日期 | 数据来源（客户提供 / AI 生成 / 结合） | 导入方式 | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-29 | **结合**：AI 依四书推演生成 + 领域专家抽检校订 | **延续第 11 步分工**：表格族（天干/地支/六十甲子/纳音/藏干/节气/月建）与内置查表（神煞起例/十神映射）入库 MySQL，经 Ontop OBDA 暴露；结构性/分类个体保留 `sizhubazi-abox.owl` | 领域专家 | **闸门⑪通过**：数据来源＝AI 依四书生成 + 客户校订（逐条附出处，无明确出处者标【待考】）；落地＝表格族入 DB（SOT 不变）。全集规模：天干 10 / 地支 12 / 六十甲子 60 / 纳音 30 / 藏干 28 / 节气 24 / 月建 12 / 神煞起例 95 / 十神映射 100。经端点 SPARQL 取数全部命中（`hasStem`/`hasBranch`/`hasNaYin` 各 60、`hasHiddenStem` 28） |

## 条文库接入与断语本命推导 · 修复留痕（第 24 步）

> 触发：排盘结果被指「薄、非本命专属、大运流年只罗列无解释、直接套通用原文」。根因＝断语类节点**从未查询已建好的 22 张条文/名录表**（仅写 `case_record`），改用硬编码通用模板。经客户裁定：**全量接入条文库 + 本命解释**；大运流年**逐年评吉凶 + 应事**。

| 变动对象 | 说明 |
|---|---|
| `SiZhuBaZiOntologyJobWorker`（新增私有 helper） | `queryClauses`（经 `OBDAHandler.executeAboxQuery` 执行 SPARQL；未就绪/异常一律**降级返回空集、不阻断**）及 `clauseDayHour`/`clauseTiaohou`/`clauseNamedPatterns`/`clauseStemSeat`/`clauseMonthBranch`/`clauseKinship`/`clauseLadyFortunes`/`clauseSexagenaryNature` |
| 节点 9 `selectYongShen` | 接入《穷通宝鉴》调候（按日干+月支匹配 `tiaohou_clause`）；原【待考】升级；新增输出 `tiaohou`；用神措辞按本命日主/月令/十神推导 |
| 节点 11 `analyzeFortuneCycles` | 新增 `fortuneAnalysis`：以本命用神/忌神逐运判扶抑得失与主应（运支冲/合/刑日支），流年按用神给吉/平/凶 |
| 节点 14 `generateCategoryVerdicts` | 性情/分类断语按**本命所见十神**推导；新增输出 `clauseCitations`（日时断 / 十干坐支 / 月支断 / 六亲说，原文 + 出处） |
| 节点 3 / 8 / 10 / W | 3 日柱接入 `sexagenary_nature`；8 格局接入 `pattern_catalog` 具名格；10 清浊/隐显/众寡按本命实测；W 接入 `lady_fortune_catalog` 分类 |
| `sizhubaziProcess.bpmn` | 新增 3 条 output 映射：`result.tiaohou`/`result.fortuneAnalysis`/`result.clauseCitations`（BPMN 为显式 `=result.<key>` 映射，新键必须登记） |
| `SiZhuBaZiController` | `getDivineStatus` 增补返回：`dayPillarTraits`/`ganzhiRelations`/`voidness`/`tiaohou`/`purityTruth`/`fortuneAnalysis`/`clauseCitations` |
| `index.html` | `SECTIONS` 增补渲染分组：日柱特性 / 干支关系 / 空亡 / 调候用神 / 清浊真假 / 大运流年·逐年评析 / 典籍原文征引 |

> 回归：`mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test`（不带 `-am`）——Compliance 8/8、SKOS 8/8、BirthParsing 4/4、SWRL 3/3、内存引擎正向用例全过；残余失败为既有引擎/套件类（环境性）与 2 个负向用例断言（与本次改动无关）。

## 节气精度升级 · 留痕（第 31 步）

> 触发：`SiZhuBaZiOntologyJobWorker` 排月柱/年柱/起运原用内置 `S_TERM` 近似算法（误差 <1 天，注释标「客户裁定②；如需高精度以 OBDA 节气表为准」）。客户提出「把节气近似精度做进一步处理」并**裁定采用路径 A**（OBDA 节气表承载精确时刻）。

| 变动对象 | 说明 |
|---|---|
| `calendar` 子本体（`.owl`，回写生成源 `sizhubazi-tbox.owl.presplit.bak` 后由 `split_sizhubazi_tbox_subs.py` 再生成） | 新增类 `calendar:SolarTermInstant`（节气时刻，年度实例）+ 数据属性 `calendar:termYear`/`termIndex`/`termDatetime` + 对象属性 `calendar:instantOf`/`hasInstant` |
| `ontology/database/createdb_tables.ddl` | 新增表 `solar_term_time`（`name`/`term_year`/`term_idx`/`term_local`/`term_name`/`term_datetime`，唯一键 `(term_year,term_idx)`），总表数 23→**24** |
| `ontology/database/data.sql`（由 `HWCodeArtsDir/gen_sizhubazi_full_data.py` 生成） | 新增小节 6b：`solar_term_time` 插入 **2424** 行（1950–2050 共 101 年 ×24 节气）；数据由 `HWCodeArtsDir/sizhubazi_solarterm.py`（Meeus 第 25 章太阳视黄经 + ΔT 多项式 + 牛顿迭代，北京时间）自算 |
| `ontology/database/sizhubazi.obda` | 新增 `mappingId solar_term_time`（→ `calendar:SolarTermInstant`，target 含 `termYear`/`termIndex`/`termDatetime`/`instantOf`） |
| `SiZhuBaZiOntologyJobWorker`（程序） | `solarTermDate(LocalDate)` → `solarTermDateTime(LocalDateTime)`（按年缓存，经 `executeAboxQuery` 查 `solar_term_time`）；`yearPillar`/`monthPillar`/`allJieAround`/`pilotAge` 改用精确时刻比较；年份 <1950 或 >2050、或 DB 不可用时回退内置近似（保留 `S_TERM`），**降级不阻断** |
| 合规测试 / 单元测试 | `SiZhuBaZiOntologyComplianceTest` 增断言（DDL/OBDA/data.sql 含 `solar_term_time`、`SolarTermInstant` 类齐备）；新增 `SiZhuBaZiSolarTermTest`（离线覆盖 dateTime 解析与近似兜底） |
| `design/SiZhuBaZi应用-SKOS词汇表设计.md`（由 `step13_check/gen_design_doc.py` 生成） | 术语表并入 `term:SolarTermInstant`/`instantOf`/`termYear`/`termIndex`/`termDatetime`（SKOS 概念 311） |

> 精度与出处：算法＝Meeus《Astronomical Algorithms》2nd ed. 第 25 章太阳位置（低精度，太阳视黄经 ≈0.01°）＋ Espenak & Meeus ΔT 多项式；交节「日」与公开历法一致，交节「时刻」误差约 ±15 分钟。★禁编造：算法与出处如实标注，不称秒级精度。
