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
| V | `Task_VerifyChart` | `verifyChart` | 验盘校正（以预排盘命盘反推时辰） | **程序**（多维比对加权，**闭世界/加权判定**）+ **本体** `diagnosis:ChartVerification` | 以候补时辰（原时±1 或指定）**重排命宫**，按**命宫主星性情/相貌（w3）＋时系星昌曲坐命身痣癍（w2）＋五行局体质（w1）**多维加权评分取最高，给候补评分明细与定盘说明；校正后时辰≠输入则回填 `生辰基准` 回流节点 2；**闭环轮次上限 5**（超限标记「需人工复核」并终止）。**〔依据〕I·61，采〔外部通行法·非《全書》原文〕（大德山人《紫微斗数论定盘》等；《全書》未见系统验盘论述）**。**G1b 契约（第十五轮修正）**：`chartVerdict` 取流程网关值（**「需校正」/「无需校正」**），四态判读详情另存 `verifyVerdict` | `chartSkeleton, mainStars, auxiliaryStars, miscStars, fourTransform, subjectTraits, candidateTimes` | `chartVerdict, verifyVerdict, correctedTime, candidateTimes, verifyNote, verifyRounds` | ☑已确认 |
| 7 | `Task_Brightness` | `assessBrightness` | 定庙旺利陷 · 星辰五行生克 · 十二宫弱强 | **程序查表**（`brightnessMap`）+ **SWRL R6** + **本体** `state:Brightness`/`PhaseRelation`/`PalaceStrength` | 逐星逐宫定庙/旺/得地/利益/平和/不得地/陷，冲突按**来源优先级**（《全書》卷二·星辰屬性 / 卷三·诸星庙陷为第一权威、参照典籍次之、并列标注）；星辰五行生克制化（金入火乡/火入水乡/水入土乡/土入木乡受制）；十二宫弱强（男/女分列）、星辰落闲（8 星规范表）；**落闲断语交叉复核**（表内星落闲引原文断语、表外星落陷以〔举例式·待考〕引文；表内星断语含煞凑（如「巨宿羊陀」）者据实际盘面布尔核验）。**〔三合轨〕；【触发 SWRL】** | `chartSkeleton, chartBase, mainStars, auxiliaryStars, miscStars` | `brightnessTable, brightnessVariants, starRelations, phaseRelations, palaceStrength, idleStars, idleVerseNote` | ☑已确认 |
| 8 | `Task_StarNature` | `interpretStarNature` | 星曜化气 · 主星/辅煞/杂曜所主 | **本体** `state:StarCharacter`（化气·**静态**）/`pattern:StarMeaning` + **程序**（编排取文，保留出处） | 十四主星化气（紫微尊/天机善/太阳贵/武曲财/天同福/廉贞囚/天府令/太阴富/贪狼桃花/巨门暗/天相印/天梁荫/七杀权/破军耗，依卷一·諸星問答論，未载者标通行源）；主星/辅煞/杂曜所主（性格/事象/吉凶倾向与歌诀）；**卷一·形性賦**星曜形性（相貌体态/性情心性，25 星原文，第十六轮落地）；**卷二·十二宫各星吉凶诀**（命宫～十二父母，按「宫×主星」取原文首句＋回原行号 c3 Lxxx 印证，第十九轮落地）；**卷二·命宫「宫位×生年干→财官格/贵格/凶」可客观判定**（`judgeMingGongGe`：主星块→最近宫位→生年干命中原文句即断吉/凶并回 c3 行号，条件式标〔待考〕；**煞凑条件「羊陀火铃冲会/巨宿羊陀/不喜…」据实际星曜盘布尔判定**（`detectShaCou`+`shaCouSatisfied`，含陷地叠验），第二十轮落地）。**★化气（静态）与四化（动态 R7）严格分列；「化气」与「形性」并出分列** | `mainStars, auxiliaryStars, chartSkeleton, chartBase, brightnessTable` | `starNature, starXingXing, transformationQi, palaceClassic, mingGongGe` | ☑已确认 |
| 9 | `Task_EmptyPalace` | `handleEmptyPalace` | 空宫借星 · 借光规则 · 空宫特殊格局 | **程序**（**闭世界否定**：本宫无主星）+ **SWRL R11** + **本体** `palace:EmptyPalace` | 空宫借对宫**十四主星**入本宫论断（辅煞杂曜**不借**）；借光规则（庙陷**随对宫原地**）；四化随星；空宫格局断法。**前置：先于节点 10**。**〔依据〕I·62，采〔外部通行法·非《全書》原文〕（借星+借光+四化随星；《全書》未见成条借星口诀）** | `chartSkeleton, mainStars` | `emptyPalaceResult` | ☑已确认 |
| 10 | `Task_Surround` | `analyzeSurroundPalaces` | 三方四正 · 对宫冲照 · 邻宫相夹 · 辅煞交互 · 星曜组合断法 | **程序**（取宫变换）+ **SWRL R9/R10/R12** + **本体** `palace:SanFangSiZheng`/`Adjoining`/`pattern:StarCombination` | 三方四正取宫（本宫＋对宫＋三合二宫）；对宫冲照；邻宫相夹（紫府/日月/昌曲/羊陀/火铃/魁钺/左右/劫空夹命，依卷三·諸格断语）；辅煞与主星交互（同宫加减力/夹宫/会照）；星曜组合断法（双星/三星同宫、三方四正复合象义）。**〔三合轨〕；【触发 SWRL】** | `chartSkeleton, mainStars, auxiliaryStars, emptyPalaceResult` | `surroundPalaces, clampRelations, combinationMeaning` | ☑已确认 |
| 11 | `Task_Pattern` | `determinePattern` | 成格判定 · 命格高低 · 四化飞星深层 · 流派差异标注 | **程序**（多格并见**取高格**编排、流派**主取**编排）+ **SWRL R12/R13/R23** + **本体** `pattern:StarPattern`/`SpecialStructure`/`PalaceStarQualification`/`PatternGrade`/`ClassicText`/`fortune:FlyingTransformation`/`diagnosis:SchoolVariant` | 引用赋论总纲与骨髓赋立格；星曜格局成格判定（科权禄主/左右朝垣/文星朝命/紫府朝垣/君臣庆会/紫微七杀化权/紫府同临巳亥/日月同临 等，含定富/贵/贫贱/杂局；**卷一·太微賦/重補斗數彀率具名格：日丽中天/水澄桂萼/众水朝东/金舆捧栉**，第十七轮落地；仅举格名无客观条件者〔举例式·取文〕登记于 `patternResult`）；命格高低（论人命入格/格星数高下/定富贵贫贱十等）；四化飞星深层（化入化出/宫干飞化/飞星落宫）；**命宫三方四正吉曜会照布尔判定（昌曲会命/辅弼拱命/魁钺坐拱/禄马交驰/科权禄拱五组＋三方无凶杀＋会吉会煞计数→会吉/会煞偏重/吉煞交参/中和，第二十一轮落地）**；**卷三同垣举例式行之 OR 并列条件客观复核（加吉/加煞/昌曲/左右/魁钺/禄马/羊陀/火铃任一，按锚宫三方四正布尔复核，第二十二轮落地）**；**卷三同垣「自含条件」客观判定（命无正曜/禄存守财宅/贪狼入庙/武曲迁移/文昌三方/辅弼命迁/府相朝垣/太阳化忌/天机加恶杀/禄存入庙 10 条，第二十三轮落地）**；**卷三同垣剩余待考行按〔外部通行法／释读／纳音〕补客观判定（天梁守照吉相逢/巨陷天机/二曲旺宫/贪狼会杀无吉/巨日寅位·申宫/七杀临身终是夭/明禄暗禄/禄存厚重/禄逢冲破/生处劫空/劫空临财福/天马四生妻宫/命有吉星官杀重/双禄守命/双禄重返/日月命身居丑未/女命端正太阳/七杀单居福德/身命俱吉·身吉命凶·命弱身强/纳音墓库 等 20+ 条，分级标注·非《全書》原文者明注；无客观条件者保留待考并按因分类：运限流年/纳音生旺死绝/夹拱法/性质比喻，第二十四轮落地）**；**卷三·谈星要论开篇客观判定（八座六宫【身命迁移财官福德】聚吉多无凶→富贵高寿、俱陷聚凶→孤寡夭寿；父母妻子三宫俱带劫空杀忌→僧道之命，c4 L12-21，第二十五轮落地）**。**〔飞星轨〕汇合点；⑱取高格余格备参、⑲主基线优先+异说并陈；【触发 SWRL】** | `chartBase, chartSkeleton, mainStars, auxiliaryStars, miscStars, brightnessTable, surroundPalaces, combinationMeaning, palaceStems, fourTransform, emptyPalaceResult` | `patternResult, patternGrade, palaceZhiGrid, sanFangJiYao, flyingStars, schoolNote` | ☑已确认 |
| 12 | `Task_Natal` | `generateNatalReading` | 本命综合断（性情形貌·六亲·婚姻·事业·财帛·疾病·寿夭孤克·男女命同异） | **本体** `reading:Temperament`/`Kinship`/`Marriage`/`Career`/`Wealth`/`Disease`/`Longevity`/`GenderDifference` + **程序**（编排，⑳ 命书体＋结构化并存、保留出处） | 命宫星曜断性情形貌；六亲（父母/兄弟/夫妻/子女宫）；婚姻（夫妻宫，含女命）；事业官禄（含公卿…曹吏官阶）；财帛；疾病疾厄；寿夭孤克残疾（寿夭淫荡/残疾/孤克/出家/商贾/艺术）；男女命同异。**已剥离运限**（行限宫歌→15、阴骘延寿→16）。**【触发 SWRL】** | `chartSkeleton, starNature, patternResult, surroundPalaces` | `natalReading` | ☑已确认 |
| W | `Task_FemaleReading` | `analyzeFemaleReading` | 女命专论 | **本体** `reading:LadyFortune` + **程序** | 女命富贵贫贱、旺夫克子/旺子伤夫、纯和清贵/浊滥娼淫、滚浪桃花（卷一·女命骨髓賦；卷三·論男女命同異）；**论男女命同异客观判定：女命身命见贪狼/七杀/擎羊→不美、福德七杀单居→主娼婢之嫌（卷三·c4 L55-57，第二十五轮落地）** | `chartSkeleton, patternResult, natalReading` | `femaleReading` | ☑已确认 |
| C | `Task_ChildReading` | `analyzeChildReading` | 小儿命专论 | **本体** `reading:ChildFortune` + **程序** | 小儿命、小儿关煞（金锁铁蛇关、竹萝三限）、克亲、童限（卷三·論小兒命/定小兒生時訣/論小兒剋親）；㉑ P2 全纳；**论小儿命客观判定：命坐庙旺无煞→灾少易养、命坐恶杀缠陷→灾多难养刑克父母（卷三·c4 L58-61，第二十五轮落地）** | `chartSkeleton, patternResult, natalReading` | `childReading` | ☑已确认 |
| 13 | `Task_MajorLimit` | `analyzeMajorLimit` | 起大限 · 顺逆 · 大限十年祸福 · 安限 | **程序**（起限/顺逆推算）+ **SWRL R8/R8b** + **本体** `fortune:GreatLimit`/`chart:PilotLimit` | 起大限按五行局（水二/木三/金四/土五/火六局）；**阳男阴女顺、阴男阳女逆**〔卷二·安大限诀〕；大限宫星曜断十年吉凶；安限（童限/斗君/太岁杀禄）；**运限序列**（步数无依/水上驾星）＋**卷三·论命先贫后富**（`judgeXianPinHouFu`，第十六轮落地）＋**卷三·论二限太岁吉凶**（`judgeTwoLimitsTaiSui`：大限/小限/太岁独守·相逢·相冲·太岁冲羊陀七杀，第十六轮落地）＋**卷三·论大限十年祸福**（`judgeDaXianShiNian`：大限宫无羊陀火铃空劫→十年安静、见煞→成败不一、陷地重煞→官灾死亡之虞，第二十五轮落地）。**【触发 SWRL】** | `chartSkeleton, subjectType, taiSuiBase` | `majorLimit, majorLimitReading, xianPinHouFu, twoLimitsTaiSui` | ☑已确认 |
| 14 | `Task_AnnualFortune` | `analyzeAnnualFortune` | 小限与太岁叠加 · 按流年干支重算流曜 · 流年太岁吉凶星杀 | **程序查表**（按流年干支重算）+ **SWRL R15/R24/R25** + **本体** `fortune:AnnualFortune`/`SmallLimit`/`RecalculatedDeity`/`LimitTaboo` | 小限（A·12 小限部分）与太岁叠加断吉凶；**按流年干支重算**流禄/流羊/流陀、流年十二神（B·21）、斗君——**不得沿用出生年太岁流曜**；流年太岁吉凶星杀、流曜引动；大小限过十二支所忌。粒度 `granularity∈{年,月,日}`（默认年），本期至年，月/日 预留 R1。**【触发 SWRL】** | `chartSkeleton, chartBase, majorLimit, taiSuiBase, granularity, mainStars, auxiliaryStars, miscStars, subjectTraits, targetMonth, targetDay` | `annualChart, annualReading, annualRecomputedStars, flowMonth, flowDay, taisuiXiaoxian, limitTaboo, majorEventBacktrace` | ☑已确认 |
| 15 | `Task_Overlay` | `analyzePalaceOverlay` | 叠宫断法 · 羊陀迭并/七杀重逢 · 行限宫歌 | **程序**（四盘叠合·体用编排）+ **SWRL R16/R17** + **本体** `fortune:OverlayReading`/`TriggeredPattern`/`LimitSong` | 叠宫断法（**四盘叠合**：**本命为体**、**大限/流年/流月/流日为用**逐层叠入，I·51，采〔外部通行法·非《全書》原文〕体用断法）；羊陀迭并/七杀重逢凶格引动之年（**第二十五轮修正：由「原羊陀/七杀+流年流羊流陀冲会」客观判定，弃用永假 `countStar(七杀)>=2`**）；行限宫歌（**立命行限宫歌·按命宫纳音五行遇八卦方位**；另篇·论行限分南北斗 H·48）；**卷三·论流年太岁吉凶星杀客观判定（太岁临命宫·三方吉凶计数，c4 L83-84，第二十五轮落地）**。**【触发 SWRL】** | `chartSkeleton, majorLimit, annualChart` | `overlayReading, evilPatternTrigger, limitPalaceSong` | ☑已确认 |
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
| `Task_VerifyChart` | 验盘是否纳入、候补判定与轮次上限？ | 程序（多维比对加权）＋本体 | **纳入** ⑤；候补时辰（原时±1/指定）**重排命宫**→**命宫主星(w3)+时系星昌曲痣癍(w2)+五行局(w1)** 加权评分取最高 ⑥；采〔外部通行法·非《全書》原文〕（I·61，《全書》未见系统验盘论述）㉛；**轮次上限 5**，超限标记需人工复核并终止 | 缺 `subjectTraits` → 校验失败终止；特征不足/候补并列 → 标「需人工复核」；超 5 轮 → 终止并标记 | 陈希夷 | 2026-10-31 |
| `Task_Brightness` | 庙陷来源与生克口径？ | 程序查表（`brightnessMap`）＋R6＋本体 | 冲突以**《全書》为第一权威**（卷二·星辰屬性/卷三·诸星庙陷）、参照典籍次之、并列标注 ⑭；五行生克制化；十二宫弱强 | 无 | 陈希夷 | 2026-10-31 |
| `Task_StarNature` | 化气/所主口径来源？ | 本体（`state:StarCharacter`/`pattern:StarMeaning`）＋程序编排 | 化气依卷一·諸星問答論（未载者标通行源）⑮；主星/辅煞/杂曜所主逐条取原文；**化气(静态)与四化(动态)分列** | 无 | 陈希夷 | 2026-10-31 |
| `Task_EmptyPalace` | 借星/借光/空宫格局口径？ | 程序（闭世界否定）＋R11＋本体 | 借对宫**十四主星**（辅煞**不借**）＋借光（庙陷**随对宫原地**）＋四化随星；采〔外部通行法·非《全書》原文〕⑰（原著未见成条借星口诀） | 「本宫无主星」由程序闭世界判定 | 陈希夷 | 2026-10-31 |
| `Task_Surround` | 三方四正/夹宫/交互取法？ | 程序取宫＋R9/R10/R12＋本体 | 三方四正取宫；夹宫依卷三·諸格断语 ⑯，未载者标通行+待考；组合断法与交互同节（I·52/I·64） | 无 | 陈希夷 | 2026-10-31 |
| `Task_Pattern` | 多格并见与流派差异主取？ | 程序编排＋R12/R13/R23＋本体 | 成格判定（含定富/贵/贫贱/杂局）；**取高格、余格备参** ⑱；命格高低十等；**主基线优先+异说并陈** ⑲；四化飞星深层（飞星轨） | 多格冲突 → 取高格，余格挂 `SchoolVariant`/备参 | 陈希夷 | 2026-10-31 |
| `Task_Natal` | 本命断语措辞与范围？ | 本体 `reading:*`＋程序编排 | 命书体（保留《全書》文言）＋结构化字段并存 ⑳；专司本命（性情形貌/六亲/婚姻/事业/财帛/疾病/寿夭孤克/男女命同异），**已剥离运限** | 无 | 陈希夷 | 2026-10-31 |
| `Task_FemaleReading` | 女命专论口径？ | 本体 `reading:LadyFortune`＋程序 | 卷一·女命骨髓賦／卷三·論男女命同異 | 无 | 陈希夷 | 2026-10-31 |
| `Task_ChildReading` | 小儿关煞是否纳入？ | 本体 `reading:ChildFortune`＋程序 | 纳入（P2 全纳）㉑：金锁铁蛇关/竹萝三限/克亲/童限 | 无 | 陈希夷 | 2026-10-31 |
| `Task_MajorLimit` | 起限/顺逆口径？ | 程序推算＋R8/R8b＋本体 | 依卷二·安大限诀：**阳男阴女顺、阴男阳女逆** ㉒；大限十年祸福；安限（童限/斗君/太岁杀禄） | 无 | 陈希夷 | 2026-10-31 |
| `Task_AnnualFortune` | 流年粒度与流曜重算？ | 程序查表（按流年干支重算）＋R15/R24/R25＋本体 | **粒度可选 {年/月/日}、默认年**，年/月/日 三粒度均已实现（㉓；月依安斗君诀、日〔外部通行法·非《全書》原文〕，衔接 R1）；**按流年干支重算**流曜（**不得沿用出生年**）；大小限过十二支所忌 | 月/日 → 流月/流日推演；未知粒度 → 回退年级并标注 | 陈希夷 | 2026-10-31 |
| `Task_Overlay` | 叠盘与凶格引动口径？ | 程序叠合＋R16/R17＋本体 | **四盘叠合**（本命为体、大限/流年/流月/流日为用逐层叠入），采〔外部通行法·非《全書》原文〕体用断法㉔；羊陀迭并/七杀重逢；行限宫歌（H·48，分南北斗） | 未起限/无月日输入 → 该层跳过 | 陈希夷 | 2026-10-31 |
| `Task_Aggregate` | 输出格式与告警判据？ | 程序编排＋本体＋R18~R21 | **命书体＋JSON Schema 并存** ㉕；含阴骘延寿劝诫 H·49；告警综合判据由程序编排、SWRL 落最终标志；**不设数值阈值** ㉖ | 无 | 陈希夷 | 2026-10-31 |
| `Task_SendAlert` | 外发通道与失败策略？ | 外部调用 `RabbitMqHandler`＋R21 | SWRL 命中即外发；仅三类告警（大凶/寿元关/重大疾病）㉖ | 外发失败 → 记日志、不阻断主流程 | 陈希夷 | 2026-10-31 |
| `Task_PersistReturn` | 隐私口径？ | 程序落库＋本体 | **脱敏、仅存必要字段**，不留存可识别身份信息 ㉗；返回前端 status/variables/chart/reading | 落库失败 → 回 `INCIDENT` | 陈希夷 | 2026-10-31 |
| `Task_Interpret` | 大模型解读的来源与降级？ | 外部调用 `com.ocean.utilities.ChatAgent`（Spring AI OpenAI 兼容接口） | **纯大模型生成、不读本体**：先出「命书详解」（依《全書》逐环节详解推断依据、引篇章印证），再出「白话解读」（通俗白话、术语逐项浅释）㉜ | 大模型未装配/不可用/失败 → 双键降级空串、不阻断流程 | 陈希夷 | 2026-10-31 |

## 6. 前端与 API 设计

| 端点 | 方法 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| `/api/ziweidoushu/chart` | POST | `birth, calendar, gender, birthPlace, mode(直接排盘/验盘), subjectType(成人男/成人女/小儿), depth(本命/本命+大限/本命+大限+流年), granularity(年/月/日), subjectTraits(验盘须填)` | `instanceKey, processId, inputMode, depth` | 启动推命流程 |
| `/api/ziweidoushu/chart/{key}` | GET | 路径 `key` | `status, variables{...}`（完成时含 `chartBase/chartSkeleton/mainStars/auxiliaryStars/miscStars/taiSuiBase/fourTransform/palaceStems/twelveStages/chartVerdict/brightnessTable/brightnessVariants/starRelations/phaseRelations/palaceStrength/idleStars/idleVerseNote/starNature/starXingXing/palaceClassic/mingGongGe/emptyPalaceResult/surroundPalaces/patternResult/patternGrade/flyingStars/natalReading/femaleReading/childReading/majorLimit/majorLimitReading/xianPinHouFu/twoLimitsTaiSui/annualChart/flowMonth/flowDay/taisuiXiaoxian/limitTaboo/majorEventBacktrace/overlayReading/reading/structuredOutput/alertTriggered/messageStatus/aiInterpretation/aiInterpretationPlain/caseRecord`） | 查询状态/结果 |
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
| 2026-11-01 | v7（第三轮经典符合性审查与修正） | **逐条回原著再核格局/杂曜/长生**：★A4「七杀朝斗」漏判**寅申**（卷三·谈星要论「七杀寅申子午」）→ 抽 `isQiShaChaoDou` 判寅申子午；★A5「龙池」起宫由子起正为**辰起**（卷二·安龙池凤阁诀「龙池子顺辰，凤阁子戌逆」）→ 抽 `longChi`/`fengGe`；★B4–B9 补遗并改正格局——**对面朝斗格/石中隐玉格/兼文武格/禄马佩印/日月藏辉**（卷一·诸格论·定贵局·定贫贱局）＋「**火贵格**」改判**三方四正**（原误同宫）；★C4 十二长生天干加「通行取象·待考」注、C5 天德/月德（流年）·解神（生年）口径备注。验证：编译 EXIT 0；离线 `ZiWeiDouShu*` **30/30** 全绿（`ZiWeiDouShuStarTableTest` **11/11**，新增 龙池凤阁/七杀朝斗 用例） | 码道 |
| 2026-11-01 | v8（第四轮经典符合性审查与修正） | **补遗本命/运限格缺项**：①`determinePattern` 新增**「十二宫诸星得地合格诀 / 失陷破格诀」**按命宫地支逐支判定（`judgePalaceZhiGrid` ＋ `PALACE_ZHI_GE`/`PALACE_ZHI_PO` 表，输出 `palaceZhiGrid` 并并入 `patternResult`；卷一 `c2` L556-578）；②`analyzeMajorLimit` 新增**「定杂局」运限格**判定（`miscPattern`：风云际会/禄衰马困/衣锦还乡/枯木逢春/吉凶相伴，余标待考；卷一·定杂局 `c2` L641-648）＋ **竹萝三限**标注（卷二·定男女竹萝三限）；③定贵局「明珠出海/文星暗拱/明禄暗禄/科明暗禄」（原文「见前批注」条件未明）标〔待考·不臆断〕。验证：编译 EXIT 0；`ZiWeiDouShuStarTableTest` **12/12**（新增 十二宫得地/失陷格 用例）；`AllZiWeiDouShuSuiteTest` 19/19 全绿；`validate_structure.py` ERROR=0 | 码道 |
| 2026-11-01 | v9（第五轮经典符合性审查与修正·运限断诀补遗） | **补卷三·运限断诀与卷一·十等论**：①节点 11 `determinePattern` 新增 **定富贵贫贱十等论**命中（`judgeTenGrades`，输出并入 `patternResult`；卷一 `c2` L545-555）；②节点 14 `analyzeAnnualFortune` 新增 **论太岁小限星辰庙陷遇十二宫中吉凶**逐支三段表（`TAISUI_XIAOXIAN`，输出 `taisuiXiaoxian`；卷三 `c4` L103-172）与 **大小限过十二支所忌**（`LIMIT_TABOO`，输出 `limitTaboo`；卷三 `c4` L92-101）；③节点 15 `analyzePalaceOverlay` 更正 **行限宫歌**——`limitPalaceSong` 改为「立命行限宫歌·纳音五行遇八卦方位」（`XINGXIAN_GE`）＋「论行限分南北斗」（`nanBeiDouSong`），**纠正原「分南北斗」之混同**（卷三 `c4` L102-104 / L79-82）。验证：编译 EXIT 0；`ZiWeiDouShuStarTableTest` **15/15**；`AllZiWeiDouShuSuiteTest` 19/19 全绿；`validate_structure.py` ERROR=0 | 码道 |
| 2026-11-01 | v10（第六轮经典符合性审查与修正·格诀续与星情格语补遗） | **补卷一·得地富贵/失陷贫贱论与卷三·诸星同垣格语**：①`judgePalaceZhiGrid` 增判**「十二宫诸星得地富贵论 / 失陷贫贱论」**（`PALACE_FUGUI`/`PALACE_PINJIAN` 两表，并入 `patternResult`；卷一 `c2` L579-596）——使第四轮 D1 声明的「四论」**全部落地**；②节点 11 `determinePattern` 新增 **卷三「论诸星同垣各司所宜分别富贵贫贱夭寿」全量格语库**（`STAR_TONGYUAN` 约 200 条 + `judgeTongYuan`：按本盘主星落宫/并见星/生年干逐条判定，命中列出、举例式取文标〔待考〕；卷三 `c4` L178-477）；③**修正字形缺陷**：`PALACE_ZHI_GE`/`PALACE_ZHI_PO` 中「戌」误作「戍」→ 戌支两行恢复命中。验证：编译 EXIT 0；`ZiWeiDouShuStarTableTest` **17/17**；`AllZiWeiDouShuSuiteTest` **39/39** 全绿（本体合规 13+内存引擎 9+真实引擎 9+SWRL 2+SKOS 6）；`validate_structure.py` ERROR=0 | 码道 |
| 2026-11-01 | v11（第七轮经典符合性审查与修正·谈星要论命格等第补遗） | **补卷三·谈星要论命格等第诸断**：①节点 11 `determinePattern` 新增 **「论人命入格」五级 / 「看命总纲」四级**判定（`judgeMingGeGrade`：按 入格/庙旺/科权禄/加吉（辅弼昌曲魁钺）/加杀化忌（羊陀火铃·化忌）合参，输出并入 `patternResult`；卷三 `c4` L22-36）；②新增 **「论格星数高下」**（`judgeGeXingShu`：星维三档＋九等区间，数维〔待考·释读〕；卷三 `c4` L37-54）；③新增 **「论人生时安命吉凶」**（`judgeAnMingJiXiong`：六阳时/六阴时安命同组六宫→吉；卷三 `c4` L66-67）；④节点 C `analyzeChildReading` 新增 **「论小儿克亲」**（`judgeKeQin`：生年支×生时支→先克父/母，输出并入 `childReading`；卷三 `c4` L70-71）。验证：编译 EXIT 0；`ZiWeiDouShuStarTableTest` **21/21**；`AllZiWeiDouShuSuiteTest` **39/39** 全绿；`validate_structure.py` ERROR=0；禁区零改动 | 码道 |
| 2026-11-03 | v12（第八轮经典符合性审查与修正·诸格论12格与星辰生克制化补遗） | **补两处「本体已声明⇔程序未落地」**：①节点 12 `generateNatalReading` 新增 **卷一·诸格论 12 格判定**（`judgeLongevity`＋`LONGEVITY_TYPES`：商贾/艺术/僧道/孤克/寿夭淫荡/残疾/破相/聪明/富足/贫贱/盗贼/一身驳杂；输出 `longevityReading`，对应 `reading:Longevity`/`reading:longevityType`；**出处由误标「諸星問答論」校正为「卷一·诸格论」** `c2` L510-544）；②节点 7 `assessBrightness` 新增 **卷二·论星辰生克制化「星曜落宫」五行生克**（`palaceStarRelation`＋`ELEM_NAMES`：宫位五行×星曜五行→宫生星/星生宫/宫克星(受制:X入Y乡)/星克宫/比和；输出 `phaseRelations`，对应 `state:PhaseRelation`/`state:phaseRelationType`，落地「金入火乡/火入水乡/水入土乡/土入木乡 俱为受制」；`c3` L137-140）。验证：编译 EXIT 0；`ZiWeiDouShuStarTableTest` **23/23**；`AllZiWeiDouShuSuiteTest` **51/51** 全绿；禁区零改动 | 码道 |
| 2026-11-03 | v13（第九轮经典符合性审查与修正·【待考】项外部资源落地） | **「引入外部资源」落地【待考】项，并更正一处误标**：①**更正误标**——步数无依/水上驾星/锦上添花原标「语义主观〔待考〕」，经核为**《全書》卷一·定杂局原文**（锦上添花「谓限破恶星而行吉地是也」`c2` L643；步数无依「前限接后限连绵不分是也」L646；水上驾星「一年好一年不好是也」L647），节点 13 `analyzeMajorLimit` 落地（锦上添花按「限宫带恶星而三方四正得吉」判定；步数无依/水上驾星属运限序列，标〔运限重算〕）；②节点 11 `determinePattern` 落地 **定贵局 明珠出海/文星暗拱/明禄暗禄/科明暗禄**（对应 `pattern#Gui_MingZhuChuHai/Gui_WenXingAnGong/Gui_MingLuAnLu/Gui_KeMingAnLu`，本体原已声明而程序未命中），成格条件采**〔外部通行法·非《全書》原文〕**（明珠出海＝太阳辰守命/戌太阴冲照或反之；文星暗拱＝命坐吉星、迁财官三方昌曲化科朝拱；明禄暗禄＝命宫坐禄且暗合〈六合〉宫坐禄；科明暗禄＝化科守命且暗合宫坐禄）；③节点 5 `placeMiscStars` 增置 **恩光/天贵/天官/天福/天厨/天巫/天才/天寿/孤辰/寡宿/华盖/咸池** 12 杂曜（《全書》语料 0 命中，采**〔外部通行法·非《全書》原文〕**，输出 `miscStarsNote`）；④`feiTianSanSha` 组内序来源由【待考·中】升级为〔外部通行法·非《全書》原文〕（奏书/将军/直符＝三合长生/帝旺/墓）；⑤节点 7 `assessBrightness` 增 **卷二↔卷三庙陷异文登记**（`brightnessVariantNote`：天机@辰 卷二「利益」/卷三「旺」、文曲@寅 卷二「平和」/卷三「陷」；⑲主基线优先、异说并陈；输出 `brightnessVariants`）。本体同步：`star:MiscStar` label 扩展、abox 增 12 星个体、terminology 增 12 术语。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **27/27**；`AllZiWeiDouShuSuiteTest` BUILD SUCCESS；禁区零改动 | 码道 |
| 2026-11-03 | v14（第十轮经典符合性审查与修正·运限序列模块落地） | **实现「运限序列模块」，将卷一·定杂局「步数无依/水上驾星」由文本待判升为客观落地**：①新增 **运限吉凶符号** `yunxianFortuneSign(at,idx,四化)`（某宫三方四正：吉星/凶星＋主星庙旺(+)/落陷(-)＋生年四化禄权科(+)/忌(-) → 净差符号 +1/0/-1）与 **流年符号** `annualFortuneSign(at,流年支,流禄,流羊,流陀,四化)`（以太岁宫为流年命宫，叠加流曜）；②新增序列判定 `isBuShuWuYi(int[])`（**步数无依**「前限接后限连绵不分是也」＝大限十二限吉凶同号、全序无转折）与 `isShuiShangJiaXing(int[])`（**水上驾星**「一年好一年不好是也」＝流年吉凶逐年严格交替，≥4 年）；③节点 13 `analyzeMajorLimit` 构建 **大限 12 限吉凶序列**＋**流年 6 年吉凶序列**，条件化产出 `miscPattern`（步数无依/水上驾星「成/未成」）并新增结果键 **`yunxianSequence`**（附序列与「吉/凶/平」图例）。落地口径〔释读·非逐字原文〕，吉凶符号客观可复算；《全書》第一权威不变。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **30/30**；`AllZiWeiDouShuSuiteTest` BUILD SUCCESS；禁区零改动（未改 TBox/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v15（第十一轮经典符合性审查与修正·运限重算 + 竹萝三限落地） | **实现「运限重算」并落地卷二·定男女竹萝三限**：①节点 13 `analyzeMajorLimit` 增 **大限重算**（当前大限宫依虚龄、大限宫干五虎遁 `palaceStemIndex`、大限四化依大限宫干、大限十二神起禄存于大限宫干之禄存、大限吉凶符号 `yunxianFortuneSign`）＋**小限重算**（`xiaoXianPalace`，卷二·安小限诀男顺女逆）＋**大小二限相遇**判定；新增结果键 **`majorLimitRecalc`**；②**竹萝三限**（卷二·定男女竹萝三限「三方四正七杀破军俱作竹萝三限，若再加巨暗凶星便作三方四正定议，若大小二限相遇作死限断」`c1` L757-759）：新增 `isZhuLuoSanXian`（三方四正七杀破军俱见）/`hasJuMenOrSha`（加巨暗凶星作三方四正定议），命宫／当前大限宫分判，大小二限相遇且见竹萝三限→作死限断；`zhuLuo` 由静态待考文本改为计算后判定；③节点 14 `analyzeAnnualFortune` 增 **流年四化**（依流年干）落宫，`annualFortuneSign` 并入流年四化（流禄权科+/流忌-）——完成「流年实际引动星曜」重算。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **33/33**；`AllZiWeiDouShuSuiteTest` BUILD SUCCESS；禁区零改动（未改 TBox/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v16（第十二轮经典符合性审查与修正·流月/流日粒度㉓落地） | **推进 ㉓：节点 14 `analyzeAnnualFortune` 实现 月/日 粒度**：①粒度 `GRAN∈{年,月,日}`——年（流年干支重算流曜）；月→追加**流月**（新增 `flowMonthPalace`，依卷二·安斗君诀「即月将星是也」：斗君为流月正月命宫、逐月顺行一宫，输出十二月命宫表＋目标流月吉凶符号）；日→追加**流月＋流日**（新增 `flowDayPalace`〔外部通行法·非《全書》原文：《全書》仅言「日值恶杀」未立起例〕：流月命宫起初一、逐日顺行一宫）；未知粒度回退年级并标注；②目标月/日取输入 `targetMonth`/`targetDay`（缺省＝当前月/日）；③新增结果键 **`flowMonth`/`flowDay`**，`annualChart` 标注「㉓ 月/日 粒度已支持」。至此 ㉓ 三粒度全部落地（衔接 R1）。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **34/34**；`AllZiWeiDouShuSuiteTest` BUILD SUCCESS；禁区零改动（未改 TBox/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v17（第十三轮经典符合性审查与修正·空宫借星/四盘叠合外部比对落地 + 庙陷异文复核） | **对剩余【待考·中】项做补充外部比对并落地，兼复核卷三庙陷异文**：①**空宫借星（I·62）**——节点 9 `handleEmptyPalace` 新增 `borrowMainStars`（借对宫**十四主星**、辅煞杂曜**不借**）与 `borrowedBrightness`（借光·庙陷**随对宫原地**、四化随星），由【待考·中】升级为〔外部通行法·非《全書》原文〕；②**四盘叠合（I·51）**——节点 15 `handleAnalyzePalaceOverlay` 新增 `overlayStack`（**本命为体**、**大限/流年/流月/流日为用**逐层叠入），由原静态「三盘叠合·待考」文本改为**计算后四盘定位**（复用 `daXianPalace`/`douJun`/`flowMonthPalace`/`flowDayPalace`；未起限或无月/日输入则该层跳过），同升级为〔外部通行法·非《全書》原文〕；③**卷三·诸星庙陷汇总异文复核**——21 星×12 支逐支比对，**正相左者仍唯二**（天机@辰/文曲@寅），无第三处（禄存「十二宫皆入庙」与卷二兜底「平和」于丑辰未戌之差异属**结构性不可能**：禄存不入四墓库）。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **36/36**（新增 `emptyPalaceBorrow`/`overlayStacking`）；`AllZiWeiDouShuSuiteTest` BUILD SUCCESS；禁区零改动（未改 TBox/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v18（第十四轮经典符合性审查与修正·I·61 验盘外部比对落地） | **联网检索验盘（定盘）通行法并客观落地，清空最后一项【待考·中】**：节点 V `verifyChart` 由「默认无需校正」桩改为**多维比对加权**——①候补时辰 `hourCandidates`（原时±1）＋`parseCandidateHours`（输入 `candidateTimes` 优先）；②`lifePalaceBranch`/`bodyPalaceBranch`（卷二·安身命例）＋`candidateMingStars`（以五行局+生日**重排紫微定位**取候补盘命宫主星）；③特征词表 `VERIFY_STAR_TRAITS`（14 主星性情/相貌）/`VERIFY_BUREAU_TRAITS`（水二局皮肤白、火六局面色偏红）/昌曲痣癍；④`verifyCandidateScore`（命宫主星 **w3**＋时系星 **w2**＋五行局 **w1**）逐盘评分取最高；⑤`chartVerdict` 四态（定盘确认/建议改时辰/候补并列·需人工复核/特征不足·需人工复核）＋`correctedTime`＋`candidateTimes`（评分明细）。外部来源：大德山人《紫微斗数论定盘》、91demi《时辰校正》、郑书羽《定盘流程》；由【待考·中】升级为〔外部通行法·非《全書》原文〕。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **39/39**（新增 `verifyChartCandidates`/`verifyChartScoring`/`verifyStarTraitTable`）；`AllZiWeiDouShuSuiteTest` BUILD SUCCESS；禁区零改动（未改 TBox/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v19（第十五轮经典符合性审查与修正·〔待考·释读〕外部比对 + 得地合格诀生年干落地 + 验盘契约修复） | **①〔待考·释读〕三类外部比对**：得地/失陷四论、同垣举例式、星数高下「数维」——联网证实通行本（zhouqianli/yi958/newdu/古文岛/白话直译；18zw 宁雪天、shunshi.ai）**亦不补客观算法**（「数」原文未展开定义，诸说不一）→ **维持取文不臆断**（登记外部释读：数＝宫、阴阳＝气机清浊）。**②〔缺陷·修正〕得地合格诀生年干**：`PALACE_ZHI_GE` 增第 5 列生年干（子「庚辛乙癸」等，源《全書》c2 L556-568 明文），`judgePalaceZhiGrid(…, yearGan)` 落地「星象 × 生干」判定；「丙戍」形讹取「丙戊」，寅行「申巳庚」含非天干讹字 → 标〔取文·不臆断〕；新增 `ganListMatches`/`ganListHasSuspectToken`。**③〔缺陷·修正〕验盘 BPMN 契约**：`handleVerifyChart` 原四态 `chartVerdict` 与 `Gateway_ChartVerdict`（唯「需校正/无需校正」、无 default 流）**不符**致真机 `CONDITION_ERROR` → Worker 改输出网关值（建议改时辰⇒「需校正」、余⇒「无需校正」），四态详情另存 `verifyVerdict`；控制器同步透出；**未改 BPMN**。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **40/40**（改 `palaceZhiGrid`、新增 `palaceZhiGridGanSuspect`）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**（P-4 真机验盘回流校正完成）；禁区零改动（未改 TBox/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v21（第十七轮经典符合性审查与修正·卷一五赋文具名格落地 + 举例式取文登记） | **承第十六轮「续查卷一赋文未落地格语」，对卷一 5 篇赋文（太微賦/增補太微賦/斗數準繩/斗數發微論/重補斗數彀率，c2 L16-140）逐句取具名格，与代码格表（`determinePattern` 定局 + `STAR_TONGYUAN`）及本体 ABox（`pattern#*`）逐条比对。①**新增 4 具名格落地**（《全書》原文直接落地）：**日丽中天**（太陽居午守命）、**水澄桂萼**（太陰居子守命）、**众水朝东**（文耗居寅卯＝昌曲·破军守命寅/卯）、**金舆捧栉**（紫微坐命庫辰戌丑未）；新增 ABox `pattern#Ge_RiLiZhongTian`/`Ge_ShuiChengGuiE`/`Ge_ZhongShuiChaoDong`/`Ge_JinYuPengZhi` 个体 + `pattern.owl` `StarPattern` label 枚举；`determinePattern` 抽出静态方法 `judgeTaiweiMingGe` 落地并并入 `patternResult`。②**举例式取文登记**（`taiweiPendingText`）：善蔭朝綱/貴入貴鄉/財居財位/祿文拱命/善福居空位/桃花犯主為至淫/衣錦惹天香（无客观条件，不臆断、不另立个体）；金殿傳臚/翰林清異/爛穀堆金/高商豪客＝卷三同垣已落地条同义合并。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **44/44**（新增 `taiweiMingGeJudge`）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**（本体合规 13/13）；**仅增 ABox 个体，未改 TBox 类/属性、未改 DDL/SWRL/BPMN** | 码道 |
| 2026-11-03 | v22（第十八轮经典符合性审查与修正·卷二/卷三断语类逐句核 + 太岁小限十二宫吉凶「生干择段」精化） | **通读卷三（c4 全 476 行）、卷二（c3）逐章比对**：①**更正 §22 清单漏收**——卷三「论太岁小限星辰庙陷遇十二宫中吉凶」（c4 L105-177，12 支 × 入庙化吉/不入庙化凶/所值吉凶星）**实已落地**（`TAISUI_XIAOXIAN` + 节点 14）；②**精化落地**：节点 14 `analyzeAnnualFortune` 之 `taisuiXiaoxian` 增 `taisuiXiaoxianGanHit(birthGan,flowBranch)`——按**本命生干**择「入庙化吉（发福）/不入庙化凶（悔吝招官非）/两段皆见/两段未列（减半论）」；新增 `extractGanConditions`（抽取段内生干条件天干，**干支之干如「甲子」「丙午」不取**）。《全書》原文直接落地。③**诸星同垣补录 8 条**（c4 L178-476 逐句比对）：日月条 5/贪狼条 1/巨门条 2 无客观条件断语 → 按既有机制**登记取文**（`pend`〔举例式·待考取文〕）。④卷二复核：安星诀 30 门、紫微定位棋盘图、诸星分属南北斗化吉凶并分属五行、星辰庙陷、生克制化等均已落地，无新增缺口。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **46/46**（新增 `taisuiXiaoxianGanHitJudge`/`tongYuanSupplementRows`）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**；禁区零改动（未改 TBox/ABox/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v23（第十九轮经典符合性审查与修正·卷二·十二宫各星吉凶诀全量落地 + §22 覆盖矩阵补正） | **复扫卷二（c3）全篇，发现 §22「卷二全部落地」口径漏收整章**：卷二 **2.2–2.13「一 命宫～十二父母」**（c0 目录，c3 **L458–1282，约 824 行**，各宫各星「论／入男命吉凶诀／入女命吉凶诀／入限吉凶诀」）此前**既无 SourceNote 亦无代码落点**。①**原文语料外置落地**：新增资源 `src/main/resources/ziweidoushu/palace_classic.tsv`（**813 行、12 宫齐全**，逐行带 c3 行号），新增 `loadPalaceClassic()`/`palaceStarQuote(宫,星)`（取该宫该主星原文首句＋回原行号）；节点 9 `interpretStarNature` 增结果键 **`palaceClassic`**（按命宫逆行十二宫 × 该宫主星逐宫印证）。②**补声明**：ABox 增 `SN_J2_48..59`（命宮/兄弟/妻妾/子女/財帛/疾厄/遷移/奴僕/官祿/田宅/福德/父母）；另补 `SN_J2_47`（安三台八座二星訣，程序原已落地 `placeMiscStars` 但缺独立 SourceNote）。**断语类按原文登记、取首句印证、不臆断、不程式化成格条件**。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **47/47**（新增 `palaceClassicLanding`）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**；**仅增 ABox 个体 + 资源文件 + 读数代码，未改 TBox 类/属性、未改 DDL/SWRL/BPMN** | 码道 |
| 2026-11-03 | v24（第二十轮经典符合性审查与修正·命宫「宫位×生年干→财官格/贵格/凶」可客观判定 + BPMN 输出映射缺陷修正） | **承接 §25.3 目标①，将命宫各主星生干财官格由「取文」升为「可客观判定」**：①新增 `judgeMingGongGe(star, palaceZhi, birthGan)`（+ `isStarBlockOpen/isAnyStarBlockOpen/zhiChars/ganChars/classifyGe`），以 `palace_classic.tsv` 原文为**单一真源**，按「主星块→句/子句（句号作用域复位 carry）→最近宫位→生年干」择取，命中「无条件结构化句」即回原文句＋c3 行号并断〔吉/凶/吉凶参/平〕；含会照/逢遇等附加条件者标〔条件式·待考〕，原文未载则空串（不臆造）。②硬判模板兼容「子宫喜丁己庚…」「午宫入庙喜甲丁己…」「若在亥地庚生人…」「六甲生人命坐四墓…」「六丙六戊生人…」及 OCR 讹字「丁庚甲入生人」。③节点 8 结果增 **`mingGongGe`** 键。④**〔声明≠落地缺陷修正〕** 复核发现 BPMN `Task_StarNature` 仅映射 `starNature/transformationQi`、且未传 `chartSkeleton/auxiliaryStars/chartBase`，致 `palaceClassic`/`mingGongGe` **无法落入流程变量**——已补 `zeebe:input`（auxiliaryStars/chartSkeleton/chartBase）与 `zeebe:output`（starXingXing/palaceClassic/mingGongGe），真实引擎 P-1 实测可落地（天同亥·庚→下局〔凶〕c3 L549）。全网格审计（14×12×10=1680）硬判 536／条件式 3／空串 1141。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **48/48**（新增 `mingGongGeJudge`）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**；禁区（OpenlletResolver/OntopOBDAHandler/EnvPrepare、TBox 类/属性、DDL、SWRL）零改动，仅改 BPMN ioMapping（补映射，不改流程结构/节点/连线） | 码道 |
| 2026-11-03 | v25（第二十轮·目标②：卷二「定十二宫弱强/定十二宫星辰落闲」与十二宫各星吉凶诀**断语交叉复核** + 节点 7 输出映射缺陷修正） | **交叉复核结论 + 落地**：①**规范表核对**——`IDLE_STAR`（8 星：紫微子辰亥/贪狼寅辰/天相辰戌/七杀辰亥/天梁巳酉/天机巳/破军巳申/武曲申）与 c3 L133-134 **逐字一致**；`palaceStrength` 男/女强弱势与 c3 L130-131 **逐字一致**（女命原文「夫君」释读为夫妻宫）；`palaceStarRelation` 生克序与 L137-140「金入火乡/火入水乡/水入土乡/土入木乡受制」**一致**。②**断语交叉**——卷二十二宫各星吉凶诀中含「落闲宫」断语 9 处：**表内星**（天机 L494/天梁 L712/七杀 L733）落闲即引断语（一致）；**表外星**（天同 L560/廉贞 L585·586/擎羊 L850/火星·铃星 L889/忌星 L968）原文未定闲支，属**断语泛论/举例式**，不构成与规范表矛盾，仅于该星落陷时以〔举例式·原文无定闲支·待考〕引文（不臆造分支）。③**落地**：新增 `IDLE_VERSE` 原文登记表 + `inIdleTable/idleVerseOf`；节点 7 增结果键 **`idleVerseNote`**（表内星落闲→引原文断语＋c3 行号；表外星落陷→举例式待考）。④**〔声明≠落地缺陷修正〕** 复核发现 BPMN `Task_Brightness` 仅映射 `brightnessTable/starRelations/palaceStrength`、且**未传 `chartBase`**（致 `male` 恒 false、弱强恒输出女命）——已补 `zeebe:input`(chartBase) 与 `zeebe:output`(brightnessVariants/phaseRelations/idleStars/idleVerseNote)；真实引擎 P-1（1990 男盘）实测 `palaceStrength` 为「男命」、`idleVerseNote` 落地（天梁@巳落闲引 L712；擎羊@酉落陷举例式引 L850）。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **49/49**（新增 `idleVerseCrossCheck`）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**；禁区零改动（未改 TBox/属性/DDL/SWRL；仅 BPMN ioMapping 补映射） | 码道 |
| 2026-11-03 | v26（第二十轮·目标③：重大事件逐流年回推 `subjectTraits` 结构化事件表 + 节点 14 映射补全） | **将「命主自述重大事件（年份+事件）」结构化逐流年回推**〔外部通行法·释读·非《全書》原文；仅验盘/复盘参考，不改《全書》第一权威〕：①新增 `eventPalaceExpectation(seg)`（13 类事件关键词→`{本命事项宫索引, 期望吉凶}`：夫妻/子女/父母/官禄/财帛/迁移/疾厄/兄弟/田宅/奴仆；未识别 null）与 `majorEventBacktrace(traits, at)`（**按事件分隔符 `[；;。\\n]` 切块**避免相邻年份窗口交叉误归类；逐块抽年份→流年干支→事项宫流年定位 `targetBranch=floorMod(flowBranch-pe[0],12)`→`annualFortuneSign` 三方四正吉凶符号→附流年四化落宫与原述片段；给「合/待核/中性」；无事件 null）。②节点 14 `analyzeAnnualFortune` 增结果键 **`majorEventBacktrace`**（null 补「无结构化事件」提示）。③**〔声明≠落地缺陷修正〕** BPMN `Task_AnnualFortune` 原仅 4 input/3 output，无法承载节点 14 已实现之流月/流日/太岁小限/所忌/事件回推——补 `zeebe:input`(chartBase/mainStars/auxiliaryStars/miscStars/subjectTraits/targetMonth/targetDay) 与 `zeebe:output`(flowMonth/flowDay/taisuiXiaoxian/limitTaboo/majorEventBacktrace)。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **50/50**（新增 `majorEventBacktraceTest`）；真实引擎 P-2（女命全链·subjectTraits="2012年结婚；2016年升职"）**1/1**，`majorEventBacktrace` 键落地含「共 2 项/事项宫＝/流年四化」；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**；禁区零改动（未改 TBox/属性/DDL/SWRL；仅 BPMN ioMapping 补映射） | 码道 |
| 2026-11-03 | v27（第二十轮·收官：煞凑条件客观化为布尔判定「羊陀火铃冲会／巨宿羊陀」等） | **将断语附加之煞凑条件由待考文本升为可复算布尔判定**（《全書》原文所载条件之客观复算，不改第一权威）：①新增 `detectShaCou(text)`（长匹配识别羊陀火铃/巨宿羊陀/羊陀冲破等→所需煞曜集+范围）+ `shaCouSatisfied(sc, palaceZhi, at)`（同宫/三方四正是否悉见）+ `shaCouDescribe`。②`judgeMingGongGe` 增 4 参重载（传实际星曜盘 `at`；3 参委托、at=null 退化原待考）：条件式命中→取句中「生人」后全文（截断于下一个「生人」）→煞凑布尔核验、含「陷」叠验 `isFallen`→客观断凶/吉；「不喜…生人」→客观判凶。③节点 7 `idleVerseNote` 表内星断语含煞凑（七杀 L733「巨宿羊陀」等）→据盘布尔核验。④节点 8 `handleInterpretStarNature` 调 `judgeMingGongGe(...,at)`。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **51/51**（新增 `shaCouBoolJudgeTest`）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**；禁区零改动（未改 TBox/属性/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v28（第二十一轮：三方四正吉曜会照条件客观化为布尔判定） | **将「并见吉曜成格」类会照条件升为可复算布尔判定**（《全書》「三方四正会照」通例之客观复算/释读，不改第一权威；`judgeTongYuan` 原 AND 判定不变、不覆盖）：①新增 `judgeSanFangJiYao(mingIdx, at, fourTransform)`——取命宫三方四正 `{+0,+4,+8,+6}` 星曜并集，判五组吉曜（昌曲会命/辅弼拱命/魁钺坐拱/禄马交驰/科权禄拱＝`isHuaInSanFang`）＋「三方无凶杀」（会煞==0），统计会吉/会煞数→「会吉/会煞偏重/吉煞交参/中和」综合；`yn(boolean)`输出是/否。②节点 11 `handleDeterminePattern` 并入 `patternResult` 末尾并 `result.put("sanFangJiYao",...)` 单独出参。③**〔声明≠落地缺陷修正〕** BPMN `Task_Pattern` 原仅 7 input，补 `chartBase/chartSkeleton/auxiliaryStars/miscStars`（handler 实读）→11 input，并新增 `palaceZhiGrid`/`sanFangJiYao` 2 output。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **52/52**（新增 `sanFangJiYaoJudgeTest`）；真实引擎 P-2 **1/1**（`sanFangJiYao` 键落地）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**；禁区零改动（未改 TBox/属性/DDL/SWRL；BPMN 仅补 ioMapping） | 码道 |
| 2026-11-03 | v29（第二十二轮：`judgeTongYuan` 之「加吉/加煞/昌曲…」OR 并列条件客观化·释读） | **为「举例式·取文」行补 OR 并列条件客观复核**（《全書》会照通例之复算/释读，不改第一权威；`judgeTongYuan` 原 AND 判定不变、不覆盖）：①新增 `tongYuanOrNote(text, anchorBranch, at)`——先合称（`加吉`＝辅弼昌曲魁钺、`加煞`＝羊陀火铃空劫）后专称（`昌曲/左右/魁钺/禄马/羊陀/火铃`），按锚宫（该星/该宫所落之支）三方四正取星并集，每组「任一命中→是(命中星)/否」；**否定守卫**（含「无吉/无煞」不据「吉曜/煞」字样触发，避免语义反转）。②`judgeTongYuan` 的 `!hasCond` 分支分流：有 OR 复核→单列 `orJudged`（输出「〔OR条件客观复核 Y 条·释读非原文〕」），无→仍入 `pend`（「〔举例式·待考取文 Z 条〕」）。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **53/53**（新增 `tongYuanOrNoteTest`；原 `tongYuanTable`/`tongYuanSupplementRows` 不破）；真实引擎 P-2 实测将 **26 条**举例式行升为 OR 客观复核（余 50 条仍待考）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**；禁区零改动（未改 TBox/属性/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v30（第二十三轮：卷三同垣「自含条件」举例式行客观判定） | **对断语自含可判定条件者据本盘给出是/否客观核验**（《全書》断语明言之星位/庙陷/四化事实之复算·释读，不改第一权威；无客观条件者仍待考不臆造）：①新增 `tongYuanObjective(text, mingIdx, at, yearGan, skeleton)` 覆盖 10 条——命无正曜（命宫无十四主星）、禄存十二宫中皆入庙（禄存在盘）、禄存守于财宅（禄存落财帛/田宅）、天机加恶杀同宫（天机同宫见羊陀火铃）、太阳化忌（`SIHUA[yearGan][3]`＝太阳）、贪狼入庙（`brightnessOf`＝庙）、武曲迁移（武曲在迁移宫）、文昌…文科拱照（文昌在命宫三方四正）、右弼左辅…（辅/弼在命宫或迁移）、府相朝垣（天府天相俱在命宫三方四正），输出`〔客观·是/否（依据）·释读〕`。②`judgeTongYuan` 待考分流改为「先自含条件客观→再 OR 复核→余待考」，新增「〔自含条件客观判定 W 条·释读〕」段。验证：编译 EXIT 0（JDK27）；`ZiWeiDouShuStarTableTest` **54/54**（新增 `tongYuanObjectiveTest`）；真实引擎 P-2 实测 `命中 7｜自含条件客观 10｜OR 复核 25｜待考 41`（待考由 50 降至 41）；`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**；禁区零改动（未改 TBox/属性/DDL/SWRL/BPMN） | 码道 |
| 2026-11-03 | v31（第二十四轮：卷三同垣剩余待考行按〔外部通行法／释读／纳音〕补客观判定 + 待考按因分类） | **对剩余待考行按因分流落地**：①`tongYuanObjectiveFull` 在自含条件（批次①）之上新增 20+ 条规则（天梁守照吉相逢/巨陷天机/二曲旺宫/贪狼会杀无吉/巨日寅位·申宫/七杀临身终是夭/明禄暗禄/禄存厚重/禄逢冲破/生处劫空/劫空临财福/天马四生妻宫/命有吉星官杀重/双禄守命/双禄重返/日月命身居丑未/女命端正太阳/七杀单居福德/身命俱吉·身吉命凶·命弱身强/纳音墓库），凡属外部通行法/释读者 `objNoteExt` 明注〔外部通行法·非《全書》原文〕或〔释读〕，不改第一权威；②余下无可客观条件者**保留待考并按因分类**（运限流年→转节点13-15核／纳音生旺死绝→宜历法节点统一／夹拱法原文未定义／性质比喻→不臆造）。真实盘分流：`命中 7｜自含条件客观 33｜OR 复核 25｜待考 18`（待考 41→18）。验证：编译 EXIT 0、`ZiWeiDouShuStarTableTest` 55/55、`AllZiWeiDouShuSuiteTest` 30/30。禁区零改动（Openllet/OBDA/EnvPrepare/TBox/DDL/SWRL/BPMN 仅 ioMapping） | 码道 |
| 2026-11-03 | v32（第二十五轮：卷三·谈星要论其余断诀逐句核对——羊陀迭并/七杀重逢缺陷修正 + 流年太岁/大限十年祸福/小儿命/男女命同异/谈星要论开篇落地） | **对卷三 · 谈星要论（c4 L11-177）逐节核「声明⇔落地」，修正 2 处缺陷 + 新增 5 处客观判定**：①**缺陷修正·七杀重逢**——原 `countStar(at,"七杀")>=2` 对单星七杀恒 false（永假、声明未落地），改 `judgeQiShaReFeng`（本命七杀守命宫三方四正 + 流年流羊/流陀冲照/对冲）；②**缺陷修正·羊陀迭并**——原「命宫擎羊∧前宫陀罗」为羊陀夹命近似，改 `judgeYangTuoDieBing`（本命羊/陀在命/迁 + 流年流羊/流陀冲会同宫或对宫）；③新增 `judgeLiuNianTaiSui`（论流年太岁吉凶星杀 c4 L83-84：太岁临命宫·三方吉凶计数）；④新增 `judgeDaXianShiNian`（论大限十年祸福 c4 L75-76：无煞→十年安静/见煞→成败不一/陷地重煞→官灾死亡之虞）；⑤新增 `judgeXiaoErMing`（论小儿命 c4 L58-61：庙旺无煞→易养/恶杀缠陷→难养刑克父母）；⑥新增 `judgeNanNvTongYi`（论男女命同异 c4 L55-57：女命身命见贪狼/七杀/擎羊→不美、福德七杀单居→娼婢之嫌）；⑦新增 `judgeTanXingYaoLun`（谈星要论开篇 c4 L12-21：八座六宫聚吉/聚凶、父母妻子三宫劫空杀忌→僧道），并入节点 11 `patternResult`。论阴骘延寿（c4 L85-86）属德行劝善、无命盘可算条件，标〔原文德行说·不臆造〕不落地。验证：编译 EXIT 0；`ZiWeiDouShuStarTableTest` **56/56**（新增 `juanSanRemainingTest`）；套件非引擎部分 **19/19**（本体推理 13 + SKOS 6）BUILD SUCCESS；引擎门控测试（内存/真实/SWRL）依赖 MySQL80/Camunda/RabbitMQ 完整环境，当前沙箱 MySQL80 未注册且 `sc.exe` 被安全策略拦截，待环境就绪复跑。禁区零改动（Openllet/OBDA/EnvPrepare/TBox/DDL/SWRL/BPMN 仅 ioMapping，新判定字符串全部并入既有输出变量） | 码道 |
