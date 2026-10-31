# 紫微斗数（ZiWeiDouShu）应用 — 应用架构设计

> **用法**：本文件产出第 7 步。**闸门④**：业务人员（领域专家·陈希夷）对 **BPMN 的每个节点**逐项确认「**实现方法（载体不限：自然语言描述 / 程序 / 外部调用等）+ 算法**」后方可实现对应 JobWorker。本模板亦承载**闸门⑩（是否使用数据库）**与**闸门⑪（如何导入/生成完整数据）**的留痕。
> **基线**：`OntologyFrameworkExample/design/Example应用-顶层设计.md` §2–6；同类术数形态 `OntologyFrameworkSiZhuBaZi/design/SiZhuBaZi应用-应用架构设计.md`、`OntologyFrameworkPlumBlossom/design/PlumBlossom应用-应用架构设计.md`；代码基线 `ExampleOntologyJobWorker.java`、`ExampleApplication.java`、`web/ExampleController.java`、`src/main/resources/application.yaml`。

| 项目 | 内容 |
|---|---|
| 应用 | ZiWeiDouShu（紫微斗数 / 陈希夷紫微斗数推命——安星排盘、观星曜宫位、推本命大限流年） |
| 模块坐标 | `com.ocean:OntologyFrameworkZiWeiDouShu:0.0.1-SNAPSHOT` |
| 文档日期 | 2026-10-31 |
| 当前版本 | **v2（第 7 步定稿 · 闸门④已逐节点确认）** |
| 关联流程设计 | 《紫微斗数应用-流程设计》（v2.1 定稿·闸门①＋逐节点 ①-1…①-N 已完成；22 节点，含末尾新增 18 命书详解·用户需求追加） |
| 关联本体设计 | 《紫微斗数应用-本体顶层设计》（v1.4 定稿，闸门②已确认） + 《紫微斗数应用-本体核心设计》（v1.4 定稿，闸门③/⑦已确认） |
| 关联流程文件 | `ontology/bpmn/ziweidoushuProcess.bpmn`（1 开始 + **22 ServiceTask** + 5 排他网关 + 4 汇合点 + 1 结束） |
| 激活 Profile | `ZiWeiDouShuBPMN` |
| Worker 类 | `ZiWeiDouShuOntologyJobWorker`（**单类多方法**：22 个 `@JobWorker`，以 jobType 字面量分发） |
| 进程 id | `ZiWeiDouShuChartProcess` |

> 依据：定稿流程设计 + 已确认本体设计 + 实际生成的 BPMN（22 serviceTask / 5 exclusiveGateway）。**算法一律来自业务确认，不得臆造；每条实现口径可追溯至《紫微斗數全書》（[宋]陈抟（陈希夷）撰）具体篇章。**
> **实现载体总则**（对齐《本体核心设计》§5「由程序实现」清单）：**简单判定/分类用 SWRL（R1~R25）；大表查取（安星/紫微定位/庙陷/四化/流曜起例）与历法/数值计算、取宫叠合、综合评级一律由程序编排、表型外置 OBDA(MySQL)（★铁律 13/14）**。

---

## 1. 架构总览与分层

```
┌─────────── 前端层 ─────────────────────────────┐
│ static/index.html（生辰录入 · 直接排盘/验盘 · 轮询）│
└───────────▼────────────────────────────────────┘
┌─────────── API 层 ─────────────────────────────┐
│ ZiWeiDouShuController（/api/ziweidoushu/*）     │
└───────────▼────────────────────────────────────┘
┌─────────── 编排层（Zeebe）──────────────────────┐
│ ziweidoushuProcess.bpmn（ZiWeiDouShuChartProcess）│
│ 22 serviceTask · G1/G1b/G2/G3/G4 排他网关 · J1~J4 │
└───────────▲────────────────────────────────────┘
            │ jobType 字面量绑定
┌───────────┴── 应用层 ─────────────────────────┐
│ ZiWeiDouShuOntologyJobWorker（22 个 jobType）  │
│ ZiWeiDouShuApplication                         │
└──┬──────────┬──────────┬───────────────────────┘
┌──▼───────┐┌─▼────────┐┌─▼──────────┐
│本体层     ││数据层    ││消息层       │
│OWL/SWRL  ││Ontop→MySQL││RabbitMQ    │
│Openllet  ││(查表/运行态)││(告警外发)   │
└──────────┘└──────────┘└────────────┘
```

**双轨并行**（顶层设计 §2.4 / 流程设计 §3.2）：三合轨（节点 7/9/10）与飞星轨（节点 6/11）自节点 6 分野、于节点 11「流派差异标注」汇合，二轨并陈不预设单轨。

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
| `com.ocean:Utilities` | 0.0.1-SNAPSHOT | 共享工具（`OntologyWorkerSupport`/`ProcessOrchestrator`/`RabbitMqHandler`） |
| `org.apache.jena:jena-arq` / `jena-rdfconnection` | 6.1.0 | SPARQL / 连接 |
| `net.sourceforge.owlapi:owlapi-parsers` | 5.5.1 | 本体解析 |

> `OntopOBDAHandler` 依赖须保留 `log4j-slf4j2-impl` 排除（对齐 Example）。

### 2.2 模块间依赖
```
OntologyFrameworkZiWeiDouShu
        ├── OpenlletResolver
        ├── OntopOBDAHandler
        └── Utilities
```

## 3. 组件与包结构

### 3.1 主代码（`src/main/java/com/ocean/ontologyframework/ziweidoushu/`）
| 文件 | 职责 |
|---|---|
| `ZiWeiDouShuApplication.java` | `@SpringBootApplication`；`setAdditionalProfiles("ZiWeiDouShuBPMN")`；原生访问告警 |
| `ZiWeiDouShuOntologyJobWorker.java` | `@Component @Profile("ZiWeiDouShuBPMN")`，继承 `OntologyWorkerSupport`；**22 个 `@JobWorker`**（jobType 见 §4） |
| `web/ZiWeiDouShuController.java` | `@RestController`；`POST /api/ziweidoushu/chart`、`GET /api/ziweidoushu/chart/{key}`、`GET /api/ziweidoushu/health`（第 5 步已产出） |

### 3.2 前端（`src/main/resources/static/`）
| 文件 | 职责 |
|---|---|
| `index.html` | 生辰录入（公历/农历含闰月 · 年月日时 · 性别 · 出生地经度 · 命主类别 · 直接排盘/验盘 · 推演深度 · 运限粒度）；轮询流程状态并分区渲染十二宫命盘 / 本命·大限·流年批断 / 结构化输出 / 告警（第 5 步已产出） |

### 3.3 环境启停脚本（`<模块根>/start-env.cmd`）
> **照 `OntologyFrameworkExample/start-env.cmd` 的形态**生成，一键**准备并启动**运行环境；与 `ZiWeiDouShuApplication` 及 `EnvPrepare-0.0.1-SNAPSHOT.jar` 同目录。

| 步骤 | 命令 | 说明 |
|---|---|---|
| 1 | `EnvPrepare-0.0.1-SNAPSHOT.jar start` | **新窗口常驻**拉起 application.yaml 中**已声明**的服务（MySQL / Ontop / RabbitMQ / Camunda **按需**——未声明者 EnvPrepare 自动 `[SKIP]`），对**已声明的持久化服务**（MySQL 库 / RabbitMQ 资源）**缺则建、有则留**，不删已有数据 |
| 2 | `EnvPrepare-0.0.1-SNAPSHOT.jar init` | 可选：**清空重建已声明的持久化服务**后再常驻启动——由脚本交互式询问是否重建触发 |

- 脚本用 `%~dp0` 定位自身（**不硬编码路径**）、先 `chcp 936 >nul`、`errorlevel` 校验并在失败时 `pause`。
- **须在模块根放置 `EnvPrepare-0.0.1-SNAPSHOT.jar`（可执行 fat jar）**：来源 `OntologyMachine/build/EnvPrepare-0.0.1-SNAPSHOT.jar`。
- **停止环境 = 关闭该常驻窗口**。
- **编码/换行硬约束**：`.cmd` 须存 **GBK(ANSI) + CRLF**。

### 3.4 IDEA 运行配置（`<工作区根>/.idea/runConfigurations/ZiWeiDouShuApplication.xml`）
> **Spring Boot 类型**运行配置，主类 = `ZiWeiDouShuApplication`——启动该应用即由 Spring 注册并运行 `ZiWeiDouShuOntologyJobWorker` 的全部 `@JobWorker`。
> ⚠️ **落点：工作区根（IDEA 工程根 = `OntologyMachine/`）的 `.idea/runConfigurations/`**，不放模块目录内（聚合工程下子模块 `.idea` 不被加载）。

| 项 | 值 |
|---|---|
| 配置名 | `ZiWeiDouShuApplication` |
| 类型 | `SpringBootApplicationConfigurationType`（Spring Boot） |
| `<module name>` | `OntologyFrameworkZiWeiDouShu` |
| `SPRING_BOOT_MAIN_CLASS` | `com.ocean.ontologyframework.ziweidoushu.ZiWeiDouShuApplication` |
| `WORKING_DIRECTORY` | `$MODULE_WORKING_DIR$` |
| `VM_PARAMETERS` | `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow` |

## 4. BPMN 节点 ⇄ JobWorker 契约表（核心）

> **一一对应**：BPMN 每个 `serviceTask` 的 `zeebe:taskDefinition type` ⇄ 一个 `@JobWorker(type)`。共 **21** 个（jobType 唯一）。此表由 `scripts/validate_structure.py` 做机检。

| # | BPMN 节点 id | jobType | 业务规则（来自流程设计 §3） | 实现方法（载体不限·业务确认） | 算法（业务确认） | 输入变量 | 输出变量 | 确认状态 |
|---|---|---|---|---|---|---|---|---|
| 1 | `Task_ParseInput` | `parseBirthInput` | 公历/农历识别互转 · 真太阳时校正 · 历法边界 · 昼夜判定 · 登记性别/类别/模式/深度 | **程序**（历法换算，★13 外置）+ **本体**（登记 `chart:BirthInput` / `calendar:*`） | 公农历互转（含闰月）；真太阳时＝均时差＋经度差 `(经度−120)/15` 小时＋夏令时/历史时区；**闰月按下月**〔卷二·安身命例〕；**时辰交界前归**〔卷三·論人生時要審的確〕；**昼夜＝卯至申为昼** | `birth, calendar, gender, birthPlace, inputMode, subjectType, depth, granularity` | `chartBase, dayNight, inputMode, subjectType, depth` | ☑已确认 |
| 2 | `Task_LifePalaces` | `determineLifePalaces` | 安身命宫 · 安十二宫 · 定五行局（纳音） · 安命主身主 | **程序**（安命/宫序/纳音数值计算）+ **本体** `chart:MingChartSkeleton`/`LifePalace`/`BodyPalace`/`LifeMaster`/`BodyMaster`/`core:FivePhaseBureau` | 由生月生时起命宫、男女顺逆安十二宫；身宫以生时起顺数；五行局依六十甲子纳音（水二/木三/金四/土五/火六）；命主身主依卷二·安命主/安身主 | `chartBase` | `chartSkeleton, lifeLord, bodyLord` | ☑已确认 |
| 3 | `Task_MainStars` | `placeMainStars` | 安十四主星 · 定南北斗归属 | **程序查表**（`starLayoutMap` 紫微定位表）+ **本体** `star:FourteenMainStar`/`layout:StarPlacement` | 由五行局与生日安紫微（依卷二·安紫微星定位表，金四局初一在亥/火六局初一在酉），余十三主星布列；北斗紫微/天机/太阳/武曲/天同/廉贞，南斗天府/太阴/贪狼/巨门/天相/天梁/七杀/破军 | `chartBase, chartSkeleton` | `mainStars` | ☑已确认 |
| 4 | `Task_AuxStars` | `placeAuxiliaryStars` | 安辅佐星 · 安煞忌星 | **程序查表**（`starLayoutMap`）+ **本体** `star:AuxiliaryStar`/`MaleficStar` | 文昌文曲按时、左辅右弼按月、天魁天钺按年干、天马按年支、禄存按年干；擎羊陀罗依禄存、**火铃俱按生年支三合局**〔卷二·安火铃二星诀〕、天空地劫按时、天伤天使 | `chartBase, chartSkeleton` | `auxiliaryStars` | ☑已确认 |
| 5 | `Task_MiscStars` | `placeMiscStars` | 安杂曜 · 出生年（太岁）流曜基准盘 | **程序查表**（`deityRuleMap`）+ **本体** `star:MiscStar`/`star:YearlyDeity`/`star:FourFlyingStar`/`star:FlyingTriSha` | 杂曜全纳（三台八座/龙池凤阁/台辅封诰/天刑天姚/天哭天虚/红鸾天喜/天德月德解神/截路空亡/旬中空亡）；**出生年**流曜基准：流年十二神、流禄/流羊/流陀、斗君、太岁杀禄之**出生年安法**（供节点 14 按流年重算） | `chartBase, chartSkeleton` | `miscStars, taiSuiBase` | ☑已确认 |
| 6 | `Task_FourTransform` | `placeFourTransform` | 生年干四化 · 宫干五鼠遁 · 十二长生 | **程序查表**（`sihuaMap`/`palaceStemMap`）+ **SWRL R7/R14** + **本体** `layout:Transformation`/`chart:PalaceStem`/`layout:TwelveStagePlacement` | 生年干四化（化禄/权/科/忌落宫）；宫干五鼠遁（年干起十二宫干，飞星派宫干飞化之基）；十二长生按五行局起长生（水二申/木三亥/金四巳/土五申/火六寅），**男命顺数、女命逆数**〔卷二〕。**〔飞星轨〕分野点** | `chartBase, chartSkeleton` | `fourTransform, palaceStems, twelveStages` | ☑已确认 |
| V | `Task_VerifyChart` | `verifyChart` | 验盘校正（以预排盘命盘反推时辰） | **程序**（多维比对加权，**闭世界/加权判定**）+ **本体** `diagnosis:ChartVerification` | 以预排盘命盘为基准，用相貌/性情/已知事件特征逐宫反推核对，给候补时辰集与定盘说明；校正后时辰≠输入则回填 `生辰基准` 回流节点 2；**闭环轮次上限 5**（超限标记「需人工复核」并终止）。**〔依据〕I·61，全書未见系统论述，属实务通行法，标【待考·中】** | `chartSkeleton, mainStars, auxiliaryStars, miscStars, fourTransform, subjectTraits` | `chartVerdict, correctedTime, candidateTimes, verifyNote, verifyRounds` | ☑已确认 |
| 7 | `Task_Brightness` | `assessBrightness` | 定庙旺利陷 · 星辰五行生克 · 十二宫弱强 | **程序查表**（`brightnessMap`）+ **SWRL R6** + **本体** `state:Brightness`/`PhaseRelation`/`PalaceStrength` | 逐星逐宫定庙/旺/得地/利益/平和/不得地/陷，冲突按**来源优先级**（《全書》卷二·星辰屬性 / 卷三·诸星庙陷为第一权威、参照典籍次之、并列标注）；星辰五行生克制化；十二宫弱强/落闲。**〔三合轨〕；【触发 SWRL】** | `chartSkeleton, mainStars, auxiliaryStars, miscStars` | `brightnessTable, starRelations, palaceStrength` | ☑已确认 |
| 8 | `Task_StarNature` | `interpretStarNature` | 星曜化气 · 主星/辅煞/杂曜所主 | **本体** `state:StarCharacter`（化气·**静态**）/`pattern:StarMeaning` + **程序**（编排取文，保留出处） | 十四主星化气（紫微尊/天机善/太阳贵/武曲财/天同福/廉贞囚/天府令/太阴富/贪狼桃花/巨门暗/天相印/天梁荫/七杀权/破军耗，依卷一·諸星問答論，未载者标通行源）；主星/辅煞/杂曜所主（性格/事象/吉凶倾向与歌诀）。**★化气（静态）与四化（动态 R7）严格分列** | `mainStars, brightnessTable` | `starNature, transformationQi` | ☑已确认 |
| 9 | `Task_EmptyPalace` | `handleEmptyPalace` | 空宫借星 · 借光规则 · 空宫特殊格局 | **程序**（**闭世界否定**：本宫无主星）+ **SWRL R11** + **本体** `palace:EmptyPalace` | 空宫借对宫主星入本宫论断；借光规则（借入星光强弱）；空宫格局断法。**前置：先于节点 10**。**〔依据〕I·62，采后世通行借星+借光规则，标【待考·中】** | `chartSkeleton, mainStars` | `emptyPalaceResult` | ☑已确认 |
| 10 | `Task_Surround` | `analyzeSurroundPalaces` | 三方四正 · 对宫冲照 · 邻宫相夹 · 辅煞交互 · 星曜组合断法 | **程序**（取宫变换）+ **SWRL R9/R10/R12** + **本体** `palace:SanFangSiZheng`/`Adjoining`/`pattern:StarCombination` | 三方四正取宫（本宫＋对宫＋三合二宫）；对宫冲照；邻宫相夹（紫府/日月/昌曲/羊陀/火铃/魁钺/左右/劫空夹命，依卷三·諸格断语）；辅煞与主星交互（同宫加减力/夹宫/会照）；星曜组合断法（双星/三星同宫、三方四正复合象义）。**〔三合轨〕；【触发 SWRL】** | `chartSkeleton, mainStars, auxiliaryStars, emptyPalaceResult` | `surroundPalaces, clampRelations, combinationMeaning` | ☑已确认 |
| 11 | `Task_Pattern` | `determinePattern` | 成格判定 · 命格高低 · 四化飞星深层 · 流派差异标注 | **程序**（多格并见**取高格**编排、流派**主取**编排）+ **SWRL R12/R13/R23** + **本体** `pattern:StarPattern`/`SpecialStructure`/`PalaceStarQualification`/`PatternGrade`/`ClassicText`/`fortune:FlyingTransformation`/`diagnosis:SchoolVariant` | 引用赋论总纲与骨髓赋立格；星曜格局成格判定（科权禄主/左右朝垣/文星朝命/紫府朝垣/君臣庆会/紫微七杀化权/紫府同临巳亥/日月同临 等，含定富/贵/贫贱/杂局）；命格高低（论人命入格/格星数高下/定富贵贫贱十等）；四化飞星深层（化入化出/宫干飞化/飞星落宫）。**〔飞星轨〕汇合点；⑱取高格余格备参、⑲主基线优先+异说并陈；【触发 SWRL】** | `mainStars, brightnessTable, surroundPalaces, combinationMeaning, palaceStems, fourTransform, emptyPalaceResult` | `patternResult, patternGrade, flyingStars, schoolNote` | ☑已确认 |
| 12 | `Task_Natal` | `generateNatalReading` | 本命综合断（性情形貌·六亲·婚姻·事业·财帛·疾病·寿夭孤克·男女命同异） | **本体** `reading:Temperament`/`Kinship`/`Marriage`/`Career`/`Wealth`/`Disease`/`Longevity`/`GenderDifference` + **程序**（编排，⑳ 命书体＋结构化并存、保留出处） | 命宫星曜断性情形貌；六亲（父母/兄弟/夫妻/子女宫）；婚姻（夫妻宫，含女命）；事业官禄（含公卿…曹吏官阶）；财帛；疾病疾厄；寿夭孤克残疾（寿夭淫荡/残疾/孤克/出家/商贾/艺术）；男女命同异。**已剥离运限**（行限宫歌→15、阴骘延寿→16）。**【触发 SWRL】** | `chartSkeleton, starNature, patternResult, surroundPalaces` | `natalReading` | ☑已确认 |
| W | `Task_FemaleReading` | `analyzeFemaleReading` | 女命专论 | **本体** `reading:LadyFortune` + **程序** | 女命富贵贫贱、旺夫克子/旺子伤夫、纯和清贵/浊滥娼淫、滚浪桃花（卷一·女命骨髓賦；卷三·論男女命同異） | `chartSkeleton, patternResult, natalReading` | `femaleReading` | ☑已确认 |
| C | `Task_ChildReading` | `analyzeChildReading` | 小儿命专论 | **本体** `reading:ChildFortune` + **程序** | 小儿命、小儿关煞（金锁铁蛇关、竹萝三限）、克亲、童限（卷三·論小兒命/定小兒生時訣/論小兒剋親）；㉑ P2 全纳 | `chartSkeleton, patternResult, natalReading` | `childReading` | ☑已确认 |
| 13 | `Task_MajorLimit` | `analyzeMajorLimit` | 起大限 · 顺逆 · 大限十年祸福 · 安限 | **程序**（起限/顺逆推算）+ **SWRL R8/R8b** + **本体** `fortune:GreatLimit`/`chart:PilotLimit` | 起大限按五行局（水二/木三/金四/土五/火六局）；**阳男阴女顺、阴男阳女逆**〔卷二·安大限诀〕；大限宫星曜断十年吉凶；安限（童限/斗君/太岁杀禄）。**【触发 SWRL】** | `chartSkeleton, subjectType, taiSuiBase` | `majorLimit, majorLimitReading` | ☑已确认 |
| 14 | `Task_AnnualFortune` | `analyzeAnnualFortune` | 小限与太岁叠加 · 按流年干支重算流曜 · 流年太岁吉凶星杀 | **程序查表**（按流年干支重算）+ **SWRL R15/R24/R25** + **本体** `fortune:AnnualFortune`/`SmallLimit`/`RecalculatedDeity`/`LimitTaboo` | 小限（A·12 小限部分）与太岁叠加断吉凶；**按流年干支重算**流禄/流羊/流陀、流年十二神（B·21）、斗君——**不得沿用出生年太岁流曜**；流年太岁吉凶星杀、流曜引动；大小限过十二支所忌。粒度 `granularity∈{年,月,日}`（默认年），本期至年，月/日 预留 R1。**【触发 SWRL】** | `chartSkeleton, majorLimit, taiSuiBase, granularity` | `annualChart, annualReading, annualRecomputedStars` | ☑已确认 |
| 15 | `Task_Overlay` | `analyzePalaceOverlay` | 叠宫断法 · 羊陀迭并/七杀重逢 · 行限宫歌 | **程序**（三盘叠合编排）+ **SWRL R16/R17** + **本体** `fortune:OverlayReading`/`TriggeredPattern`/`LimitSong` | 叠宫断法（大限宫叠本命宫、流年宫叠大限宫之三盘叠加，I·51，标【待考·中】）；羊陀迭并/七杀重逢凶格引动之年；行限宫歌（立命行限宫吉凶歌，H·48，分南北斗）。**【触发 SWRL】** | `chartSkeleton, majorLimit, annualChart` | `overlayReading, evilPatternTrigger, limitPalaceSong` | ☑已确认 |
| 16 | `Task_Aggregate` | `aggregateReading` | 综合结论汇总 · 命书式结论 · 结构化输出 · 读告警标志 | **程序**（编排，㉕ 命书体＋JSON Schema）+ **本体** `reading:ReadingConclusion`/`StructuredOutput`/`ChartGraphic`（K·65）+ **SWRL R18~R21** | 汇总命盘（十二宫/主星/辅煞/杂曜/四化/庙陷/大限）与本命·大限·流年批断，生成命书式结论（含阴骘延寿劝诫 H·49）；输出结构化批断（JSON Schema）；读 SWRL 推导的触发标志（大凶/寿元关/重大疾病）写回 `alertTriggered` 供 G4 判定。**K·65 命盘图形化规范由此产出** | `chartSkeleton, natalReading, femaleReading, childReading, majorLimitReading, annualReading, overlayReading` | `reading, structuredOutput, alertTriggered` | ☑已确认 |
| A | `Task_SendAlert` | `sendAlert` | 外发告警消息（大凶/寿元关/重大疾病；无数值阈值） | **外部调用**（`RabbitMqHandler.send`）+ **SWRL R21**（`alertTriggered=true` → `SwrlRuleTriggerListener`） | SWRL 命中即外发（exchange `ziweidoushu.alert.exchange`(direct) / routing-key `ziweidoushu.alert` / queue `ziweidoushuQueue`，content_type=application/json）；外发失败**仅记日志、不阻断主流程** | `chartBase, reading` | `messageStatus` | ☑已确认 |
| 17 | `Task_PersistReturn` | `persistAndReturn` | 结果落库与返回（命例记录，脱敏） | **程序**（落库）+ **本体** `reading:CaseRecord` | 命主生辰/命盘/批断结果入库（命例记录）；㉗ **脱敏、仅存必要字段**，不留存可识别身份信息；返回前端（响应结构 status/variables/chart/reading）。〔预留〕R2 历史查询占位 | `chartBase, chartSkeleton, reading, messageStatus` | `caseRecord, response` | ☑已确认 |
| 18 | `Task_Interpret` | `interpretResult` | 命书详解与白话解读（大模型 · 末尾新增） | **外部调用**（通用能力 `com.ocean.utilities.ChatAgent`，经 Spring AI OpenAI 兼容接口调 MaaS 大模型） | 将节点 16 汇总命书（`reading`）与关键中间变量送大模型：①**命书详解**＝依《全書》义理逐环节（十二宫/主星辅煞落宫庙陷/化气所主/四化飞星/格局/三方四正夹拱/大限流年）详解推断依据并引篇章印证（原文仅作印证）；②**白话解读**＝转写为通俗白话、专业术语逐项白话浅释。**大模型未装配/不可用/失败 → 双键（`aiInterpretation`/`aiInterpretationPlain`）降级空串、不阻断**（用户需求 2026-10-31：纯大模型生成、不读本体） | `reading` | `aiInterpretation, aiInterpretationPlain` | ☑已确认（用户需求追加 2026-10-31） |

> **网关 ⇄ 条件（BPMN 已落地，互斥且完备）**：
> - **G1 `Gateway_Mode`**（输入模式）：`inputMode = "直接排盘"` → J1；`"验盘"` → `Task_VerifyChart`。
> - **G1b `Gateway_ChartVerdict`**（定盘结论）：`chartVerdict = "需校正"` → 回流节点 2；`"无需校正"` → J1。
> - 汇合 **J1 `Gateway_MergeJ`** → 节点 7。
> - **G2 `Gateway_Subject`**（命主类别）：`subjectType = "成人女"`→W；`"小儿"`→C；`"成人男"`→汇合 **J2**。
> - **G3 `Gateway_Depth`**（推演深度）：`depth = "本命"`→J3；`"本命+大限"`→13→J3；`"本命+大限+流年"`→13→14→15→J3；汇合 **J3 `Gateway_DepthMerge`** → 节点 16。
> - **G4 `Gateway_Level`**（吉凶级别）：`alertTriggered = true`→A；`false`→汇合 **J4 `Gateway_LevelMerge`** → 节点 **18（大模型命书详解）** → 节点 17。

## 5. 逐节点实现方法与算法确认（闸门④）

> 对**每个节点**记录：确认的问题、业务给出的**实现方法**（载体不限）与**算法口径**、边界处理。**未确认不得实现该 Worker。** 下表「业务结论」为**经领域专家·陈希夷逐节点确认（2026-10-31，闸门④通过）** 的口径（依《本体核心设计》§5「由程序实现」清单与 R1~R25 分工；出处可回原著定位）。

| 节点 id | 确认问题 | 实现方法（载体与内容） | 业务结论（算法口径，待确认） | 边界/异常处理 | 确认人 | 日期 |
|---|---|---|---|---|---|---|
| `Task_ParseInput` | 历法/真太阳时/边界/昼夜如何实现？ | 程序：公农历互转＋经度时差＋时区；本体登记 `chart:BirthInput`/`calendar:*` | 真太阳时＝均时差＋`(经度−120)/15`h＋夏令时/历史时区（默认启用，缺经度回退北京时间平太阳时 ②）；闰月按下月 ①〔卷二〕；时辰交界前归 ③〔卷三〕；昼夜卯至申 ④ | 非法日期/缺经度/缺时辰/农历转换越界 → 校验失败终止（回 `INCIDENT`） | 陈希夷 | 2026-10-31 |
| `Task_LifePalaces` | 命身宫/十二宫/五行局/命主身主起例？ | 程序数值计算＋本体 | 由生月生时起命宫、男女顺逆安十二宫；身宫以生时起顺数 ⑦〔卷二·安身命例〕；五行局依六十甲子纳音；命主身主依卷二·安命主/安身主 ⑧ | 历法边界致时辰/五行局不可定 → 标记「需人工确认」并终止 | 陈希夷 | 2026-10-31 |
| `Task_MainStars` | 紫微起宫与十四主星布列？ | 程序查表（`starLayoutMap` 定位表）＋本体 | 依卷二·安紫微星定位表/诀（金四局初一在亥…），口径唯一、不采起寅/起丑异说 ⑨ | 定位表缺项 → 降级标记并终止 | 陈希夷 | 2026-10-31 |
| `Task_AuxStars` | 辅佐/煞忌安法（火铃异说取舍）？ | 程序查表（`starLayoutMap`）＋本体 | 文昌曲按时/辅弼按月/魁钺按年干/天马按年支/禄存按年干；羊陀依禄存；**火铃俱按生年支三合** ⑩〔卷二·安火铃二星诀〕 | 无 | 陈希夷 | 2026-10-31 |
| `Task_MiscStars` | 杂曜取舍与太岁流曜基准？ | 程序查表（`deityRuleMap`）＋本体 | 杂曜全纳（含 P2）⑪；截空/旬空依《全書》起例 ⑫；出生年流曜**基准**（流禄/流羊/流陀/十二神/斗君/太岁杀禄） | 无 | 陈希夷 | 2026-10-31 |
| `Task_FourTransform` | 四化/宫干/十二长生口径？ | 程序查表（`sihuaMap`/`palaceStemMap`）＋R7/R14＋本体 | 生年干四化；宫干五鼠遁；十二长生依卷二（火寅/木亥/水申/金巳/土申起长生、**男顺女逆**）⑬ | 无 | 陈希夷 | 2026-10-31 |
| `Task_VerifyChart` | 验盘是否纳入、候补判定与轮次上限？ | 程序（多维比对加权）＋本体 | **纳入** ⑤；**多维比对加权**（相貌+性情+已知事件）⑥；**轮次上限 5**，超限标记需人工复核并终止 ㉛；【待考·中】 | 缺 `subjectTraits` → 校验失败终止；超 5 轮 → 终止并标记 | 陈希夷 | 2026-10-31 |
| `Task_Brightness` | 庙陷来源与生克口径？ | 程序查表（`brightnessMap`）＋R6＋本体 | 冲突以**《全書》为第一权威**（卷二·星辰屬性/卷三·诸星庙陷）、参照典籍次之、并列标注 ⑭；五行生克制化；十二宫弱强 | 无 | 陈希夷 | 2026-10-31 |
| `Task_StarNature` | 化气/所主口径来源？ | 本体（`state:StarCharacter`/`pattern:StarMeaning`）＋程序编排 | 化气依卷一·諸星問答論（未载者标通行源）⑮；主星/辅煞/杂曜所主逐条取原文；**化气(静态)与四化(动态)分列** | 无 | 陈希夷 | 2026-10-31 |
| `Task_EmptyPalace` | 借星/借光/空宫格局口径？ | 程序（闭世界否定）＋R11＋本体 | 采后世通行借星＋借光规则，标【待考·中】⑰ | 「本宫无主星」由程序闭世界判定 | 陈希夷 | 2026-10-31 |
| `Task_Surround` | 三方四正/夹宫/交互取法？ | 程序取宫＋R9/R10/R12＋本体 | 三方四正取宫；夹宫依卷三·諸格断语 ⑯，未载者标通行+待考；组合断法与交互同节（I·52/I·64） | 无 | 陈希夷 | 2026-10-31 |
| `Task_Pattern` | 多格并见与流派差异主取？ | 程序编排＋R12/R13/R23＋本体 | 成格判定（含定富/贵/贫贱/杂局）；**取高格、余格备参** ⑱；命格高低十等；**主基线优先+异说并陈** ⑲；四化飞星深层（飞星轨） | 多格冲突 → 取高格，余格挂 `SchoolVariant`/备参 | 陈希夷 | 2026-10-31 |
| `Task_Natal` | 本命断语措辞与范围？ | 本体 `reading:*`＋程序编排 | 命书体（保留《全書》文言）＋结构化字段并存 ⑳；专司本命（性情形貌/六亲/婚姻/事业/财帛/疾病/寿夭孤克/男女命同异），**已剥离运限** | 无 | 陈希夷 | 2026-10-31 |
| `Task_FemaleReading` | 女命专论口径？ | 本体 `reading:LadyFortune`＋程序 | 卷一·女命骨髓賦／卷三·論男女命同異 | 无 | 陈希夷 | 2026-10-31 |
| `Task_ChildReading` | 小儿关煞是否纳入？ | 本体 `reading:ChildFortune`＋程序 | 纳入（P2 全纳）㉑：金锁铁蛇关/竹萝三限/克亲/童限 | 无 | 陈希夷 | 2026-10-31 |
| `Task_MajorLimit` | 起限/顺逆口径？ | 程序推算＋R8/R8b＋本体 | 依卷二·安大限诀：**阳男阴女顺、阴男阳女逆** ㉒；大限十年祸福；安限（童限/斗君/太岁杀禄） | 无 | 陈希夷 | 2026-10-31 |
| `Task_AnnualFortune` | 流年粒度与流曜重算？ | 程序查表（按流年干支重算）＋R15/R24/R25＋本体 | **粒度可选 {年/月/日}、默认年**，本期至年、月/日 预留 R1 ㉓；**按流年干支重算**流曜（**不得沿用出生年**）；大小限过十二支所忌 | 非年粒度 → 回退年级并标注 | 陈希夷 | 2026-10-31 |
| `Task_Overlay` | 三盘叠加与凶格引动口径？ | 程序叠合＋R16/R17＋本体 | 采后世通行三盘叠加断法，标【待考·中】㉔；羊陀迭并/七杀重逢；行限宫歌（H·48，分南北斗） | 无 | 陈希夷 | 2026-10-31 |
| `Task_Aggregate` | 输出格式与告警判据？ | 程序编排＋本体＋R18~R21 | **命书体＋JSON Schema 并存** ㉕；含阴骘延寿劝诫 H·49；告警综合判据由程序编排、SWRL 落最终标志；**不设数值阈值** ㉖ | 无 | 陈希夷 | 2026-10-31 |
| `Task_SendAlert` | 外发通道与失败策略？ | 外部调用 `RabbitMqHandler`＋R21 | SWRL 命中即外发；仅三类告警（大凶/寿元关/重大疾病）㉖ | 外发失败 → 记日志、不阻断主流程 | 陈希夷 | 2026-10-31 |
| `Task_PersistReturn` | 隐私口径？ | 程序落库＋本体 | **脱敏、仅存必要字段**，不留存可识别身份信息 ㉗；返回前端 status/variables/chart/reading | 落库失败 → 回 `INCIDENT` | 陈希夷 | 2026-10-31 |
| `Task_Interpret` | 大模型解读的来源与降级？ | 外部调用 `com.ocean.utilities.ChatAgent`（Spring AI OpenAI 兼容接口） | **纯大模型生成、不读本体**：先出「命书详解」（依《全書》逐环节详解推断依据、引篇章印证），再出「白话解读」（通俗白话、术语逐项浅释）㉜ | 大模型未装配/不可用/失败 → 双键降级空串、不阻断流程 | 陈希夷 | 2026-10-31 |

## 6. 前端与 API 设计

| 端点 | 方法 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| `/api/ziweidoushu/chart` | POST | `birth, calendar, gender, birthPlace, mode(直接排盘/验盘), subjectType(成人男/成人女/小儿), depth(本命/本命+大限/本命+大限+流年), granularity(年/月/日), subjectTraits(验盘须填)` | `instanceKey, processId, inputMode, depth` | 启动推命流程 |
| `/api/ziweidoushu/chart/{key}` | GET | 路径 `key` | `status, variables{...}`（完成时含 `chartBase/chartSkeleton/mainStars/auxiliaryStars/miscStars/taiSuiBase/fourTransform/palaceStems/twelveStages/chartVerdict/brightnessTable/starNature/emptyPalaceResult/surroundPalaces/patternResult/patternGrade/flyingStars/natalReading/femaleReading/childReading/majorLimit/annualChart/overlayReading/reading/structuredOutput/alertTriggered/messageStatus/aiInterpretation/aiInterpretationPlain/caseRecord`） | 查询状态/结果 |
| `/api/ziweidoushu/health` | GET | — | `engineAvailable, processId, bpmnPath` | 健康检查 |

> Controller 已由第 5 步产出（`web/ZiWeiDouShuController.java`），已覆盖上表；本步据流程变量核对并保持同构，如需补充返回字段在闸门④后一并处理。

## 7. 配置（`application.yaml`）

| 键 | 值 |
|---|---|
| `server.port` | **9089** |
| `camunda.client.grpc-address` / `rest-address` | `http://localhost:26500` / `http://localhost:9080` |
| `ontology.main-path` | `ontology/ziweidoushu-all.owl` |
| `ontology.abox-path` | （第 9 步分离 / 第 11 步入库后新增） |
| `ontology.obda-path` / `obda-properties-path` | `ontology/database/ziweidoushu.obda` / `ontology/database/ziweidoushu.properties`（第 11 步产出） |
| `ontology.bpmn-path` | `ontology/bpmn/ziweidoushuProcess.bpmn` |
| `env-prepare.mysql.db-name` | `ziweidoushudb` |
| `env-prepare.rabbitmq.*` | exchange `ziweidoushu.alert.exchange`（direct）/ queue `ziweidoushuQueue` / routing-key `ziweidoushu.alert` |
| `env-prepare.*` | **按需声明**（需 MySQL/Ontop/RabbitMQ/Camunda 四服务；`*-home` 按本机实际安装目录） |

## 8. 解耦与端到端数据流

1. 用户录入生辰（直接排盘/验盘 · 命主类别 · 推演深度）→ 2. `POST /chart` 启动 `ZiWeiDouShuChartProcess` → 3. 节点 1 解析校正写 `chartBase` → 4. 节点 2~6 安星排盘（本体 `BackendService` 推理 + Ontop/程序查表）→ 5. **G1 输入模式**（验盘支经 [V] 验盘校正 → G1b 定盘结论，需校正则回流节点 2）→ J1 → 6. 节点 7~12 观星曜宫位与本命综合断（三合轨 7/9/10 与飞星轨 6/11 于节点 11 汇合）→ 7. **G2** 命主类别（W 女命/C 小儿）→ J2 → **G3** 推演深度（13→14→15）→ J3 → 8. 节点 16 汇总 + SWRL 告警标志写回 `alertTriggered` → 9. **G4** → 节点 A 经 RabbitMQ 外发 → J4 → 10. 节点 18 大模型命书详解与白话解读（大模型不可用降级空串不阻断）→ 11. 节点 17 落库（脱敏）并返回 → 12. 前端轮询渲染十二宫命盘、批断与大模型解读。

## 9. API 复用声明（★铁律 21）

- 逻辑一律经 `BackendService` / `OntologyWorkerSupport`（继承，勿手写初始化链路）/ `OBDAHandler` / `ProcessOrchestrator` / `RabbitMqHandler` / `OntologyLabelMatcher` / `OpenlletTuning` / `MiniReasoningContextManager` 等**现存 API**。
- **未重写** `Reference/` 三份 API 文档中的任何 API（清单见 `references/API复用清单.md`）。
- 若确需新增能力：**内置映射/查表**（安星起例 / 紫微定位 / 庙陷 / 四化 / 宫干 / 流曜起例）随生成脚本维护并经 OBDA(MySQL) 外置，**不新增平台 API**。

## 10. 业务确认项（闸门④已确认）

| # | 问题 | 业务结论（陈希夷，2026-10-31） |
|---|---|---|
| 1 | 22 个 Worker 的「实现方法与算法」是否认可？ | **☑认可**（逐节点确认，依 §5 口径：程序计算/查表 OBDA + 本体 + SWRL 简化 R1~R25；节点 18 为末尾新增·用户需求追加） |
| 2 | 端口 9089 / Profile `ZiWeiDouShuBPMN` / 库名 `ziweidoushudb` 是否可用？ | **☑认可**（沿用既定模块坐标） |
| 3 | 断语措辞（命书体文言＋结构化）是否合宜？ | **☑认可**（流程设计 ⑳/㉕ 已裁定口径） |
| 4 | 查表型数据（安星/庙陷/四化/流曜）外置 OBDA 是否维持？ | **☑维持**（本体核心设计 §9 第 2/12 项已确认外置） |

---

## 闸门④留痕
| 日期 | 确认范围（节点集） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-31 | 初稿：21 个节点（1~17 + V/W/C/A）实现方法与算法建议口径 | 码道 | 提交领域专家逐节点确认（闸门④）。 |
| 2026-10-31 | **逐节点确认：21 个节点（1~17 + V/W/C/A）实现方法与算法全部通过** | **陈希夷（领域专家）** | **闸门④通过 ☑**：21 节点实现方法与算法逐项认可，无修改点；§4 确认状态列全部置 ☑；§5 确认人/日期回填；准予据此实现全部 `@JobWorker`（单类多方法，21 个 jobType）。 |

---

## 闸门⑩留痕（是否使用数据库 · 第 11 步执行前）

| 日期 | 是否使用数据库 | 库表结构来源（客户提供 / 新建 / 导入已有） | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-31 | ☑**使用**（MySQL + Ontop OBDA） | **新建**（依《本体核心设计》§8 规划表 · 14 张） | **陈希夷（领域专家）** | **闸门⑩通过 ☑**：使用 MySQL 库 `ziweidoushudb`，表结构新建、以 §8 规划表为准（`ganzhi_stem`/`ganzhi_branch`/`sexagenary_cycle`/`nayin`/`bureau_stage`/`true_solar_time`/`leap_month`/`star_layout`/`brightness_map`/`sihua_map`/`palace_stem_map`/`deity_rule`/`case_record`/`alert_flag`）；**迁移边界**＝查表型历法个体（天干/地支/六十甲子/纳音/五行局/十二长生/闰月/真太阳时）自 `ziweidoushu-abox.owl` 迁出、**DB 为唯一真源**，静态术语（阴阳/五行/星曜名录/化气/四化类型/十二宫义/告警标志）留 ABox；**数据规模**＝可枚举表全量 + 起例表（安星/庙陷/四化/宫干/流曜）取代表性（全集留第 12 步）。 |

> 若使用数据库：客户提供的库表结构须以其实际给出为准，**不得臆造**（★8/10）；不使用时第 11 步产出为空。**规划表**见《本体核心设计》§8（`ganzhi_stem`/`ganzhi_branch`/`sexagenary_cycle`/`nayin`/`bureau_stage`/`true_solar_time`/`leap_month`/`star_layout`/`brightness_map`/`sihua_map`/`palace_stem_map`/`deity_rule`/`case_record`/`alert_flag`）。

## 闸门⑪留痕（如何导入/生成完整数据 · 第 12 步执行前）

| 日期 | 数据来源（客户提供 / AI 生成 / 结合） | 导入方式 | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-31 | **结合**（AI 依《全書》生成 + **陈希夷校订**） | **沿用现表逐张补全至全集**（不新增表、不改 DDL 结构）：DB 侧重灌 `data.sql`，ABox 侧追加 `pattern:StarPattern` 个体 | **陈希夷（领域专家）** | **闸门⑪通过 ☑**：可枚举表补至《全書》全集——`sexagenary_cycle` 6→**60**（卷二·六十花甲子納音歌）、`nayin` 6→**30**（同上）、`star_layout` 2→**150**（紫微定位表 5局×30日·卷二·安紫微定位棋盘图）、`brightness_map` 0→**240**（星辰庙陷 星×地支宫·卷二·星辰庙陷「庙/旺/得地/利益/平和/不得地/陷」）、`deity_rule` 10→**42**（流禄10+流羊10+流陀10+博士十二神12·卷二 起例诸条）；ABox `pattern` 由 52→**81** 个体（贵局 11→27、富局 3→6、贫贱局 3→8、杂局 3→8·卷一·定富/贵/贫贱/杂局）。其余表（天干10/地支12/五行局5/十二长生12/四化40/宫干5）本已全量；`true_solar_time`/`leap_month` 非枚举随命例保留代表样；`case_record`/`alert_flag` 运行态留空。**数据库侧与 .owl 侧同步补齐**。 |

---

## 变更记录

| 日期 | 版本 | 说明 | 作者 |
|---|---|---|---|
| 2026-10-31 | v1（初稿·待闸门④） | 据 v2 定稿流程设计（21 节点）+ v1.4 定稿本体设计 + 实际 BPMN（21 serviceTask/5 网关/4 汇合点）产出；含 §4 节点⇄jobType 契约表（21）、§5 逐节点实现方法与算法确认（闸门④待确认）、前端/API、配置、数据流、API 复用声明、待确认项；闸门④/⑩/⑪留痕节就位 | 码道 |
| 2026-10-31 | v2（第 7 步定稿·闸门④已确认） | **闸门④通过**：§4 确认状态列 21 项全部置 ☑；§5 确认人/日期回填（陈希夷，2026-10-31）；§10 待确认项转为已确认；闸门④留痕补录 | 码道 |
| 2026-10-31 | v3（第 11 步·闸门⑩已执行） | **闸门⑩通过**：§闸门⑩留痕补录（使用 MySQL `ziweidoushudb` + Ontop OBDA，14 表新建依 §8，迁移边界＝查表型历法个体迁出、DB 唯一真源，起例表取代表性）。产出 `ontology/database/{createdb_tables.ddl,data.sql,ziweidoushu.obda,ziweidoushu.properties}`；自 `ziweidoushu-abox.owl` 迁出 53 个查表型历法个体；`case_record` 列对齐程序写入契约（birth_year/gender/chart_summary/reading）。验证：`ontop validate` 通过 + SPARQL 虚拟取数成功；回归 `AllZiWeiDouShuSuiteTest` 22/22 全绿（`persistAndReturn` 真实落库）；`validate_structure.py` ERROR 2→1（仅剩第 13 步 SKOS `.ttl`） | 码道 |
| 2026-10-31 | v4（第 12 步·闸门⑪已执行） | **闸门⑪通过**：§闸门⑪留痕补录（数据来源＝AI 依《全書》生成 + 陈希夷校订；导入方式＝沿用现表逐张补全至全集）。DB 侧 `data.sql` 补全：六十甲子 60 / 纳音 30 / 紫微定位 150 / 星辰庙陷 240 / 流曜起例 42（14 表行数已核验；`createdb_tables.ddl` 未改结构）；ABox 侧 `ziweidoushu-abox.owl` 追加 `pattern:StarPattern` 29 个体（`pattern` 52→81）。验证：`ontop validate` 通过、SPARQL 取数 `SexagenaryCycle=60`/`NaYin=30`；回归 `AllZiWeiDouShuSuiteTest` 22/22 全绿；`validate_structure.py` ERROR=1（仍为第 13 步 SKOS，符预期） | 码道 |
| 2026-10-31 | v5（末尾新增·用户需求追加） | **追加节点 18 `Task_Interpret` / `interpretResult`**（命书详解与白话解读·大模型）：照八字范式于 J4 后、落库前新增独立 JobWorker；纯大模型生成、不读本体；`ChatAgent` 注入（`@Autowired(required=false)`），`application.yaml` 的 `spring.ai.openai.*` 配置已就绪、无需改 pom；双键降级空串不阻断。同步：BPMN（22 serviceTask）、Controller（`aiInterpretation`/`aiInterpretationPlain` 回传）、前端（两节 + 自研 `renderMarkdown` 渲染）。验证：编译通过；`validate_structure.py` 契约 22=22、ERROR=0；回归 `AllZiWeiDouShuSuiteTest` 全绿。用户需求裁定（2026-10-31）：纯大模型生成不读本体、独立节点、引入 Markdown 渲染 | 码道 |
| 2026-10-31 | v6（经典符合性审查与修正） | **据《全書》逐篇复核并修正**：①节点 7 庙旺利陷由「五行生克近似」改为**查卷二·星辰庙陷表**（内置 21×12 表 + `brightnessOf`，与 `brightness_map` 同源；逐格比对 **240/240**）；②补**星辰落闲**（卷二·定十二宫星辰落闲，节点 7 输出 `idleStars`）；③节点 5 补**丧门/白虎/吊客/官府**（卷二·四飞星诀）与**飞天三杀 奏书/将军/直符**（卷二·安飞天三杀诀）；④`determinePattern` 按卷一扩至**定富局 6 / 定贵局 18+ / 定贫贱局 7**；⑤出处标注校正（卷三→卷二）；⑥**DB `sihua_map` 壬化科由通行异说「左辅」改回《全書》卷二「天府」**，与 Java 内置表统一（40/40）。同步产出《ZiWeiDouShu应用-经典符合性审查报告》。验证：编译 EXIT 0；`ZiWeiDouShuStarTableTest` 9/9 通过 | 码道 |
