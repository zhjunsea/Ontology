# 紫微斗数（ZiWeiDouShu）应用 — 本体核心设计（核心类、属性与关系）

> **用法**：本文件产出第 4 步。**闸门③**：业务人员（领域专家·陈希夷）确认核心类/属性/关系后方可生成第 6 步本体文件。本文件并承载第 6 步**闸门⑦**（典型 ABox 数据确认）的留痕（见 §7 与文末「闸门⑦留痕」）。
> **基线**：`OntologyFrameworkExample/design/Example应用-本体设计.md`（理念 / 资产组织 / TBox / SWRL 章节）；同类术数形态见 `OntologyFrameworkSiZhuBaZi/design/SiZhuBaZi应用-本体核心设计.md`、`OntologyFrameworkPlumBlossom/design/PlumBlossom应用-本体核心设计.md`。

| 项目 | 内容 |
|---|---|
| 应用 | ZiWeiDouShu（紫微斗数 / 陈希夷紫微斗数推命——安星排盘、观星曜宫位、推本命大限流年） |
| 模块坐标 | `com.ocean:OntologyFrameworkZiWeiDouShu:0.0.1-SNAPSHOT` |
| 文档日期 | 2026-10-31 |
| 当前版本 | **v1（初稿·待闸门③确认）** |
| 上游 | 《紫微斗数应用-本体顶层设计》（**闸门②已确认，v1 定稿**） |
| 关联流程 | 《紫微斗数应用-流程设计》（v2 定稿，闸门①＋逐节点确认 ①-1…①-N 已完成；21 节点） |
| 产出本体（规划） | `ontology/ziweidoushu-all.owl`（单文件，含典型 ABox）；第 9/10 步拆为 `ziweidoushu-tbox.owl` / 子本体 / `ziweidoushu-abox.owl` |
| 推理器 | Openllet（经 OpenlletResolver） |
| 虚拟数据 | Ontop OBDA → MySQL（农历干支 / 节气 / 真太阳时校正 / 五行局 / 安星查表 / 庙陷表 / 神煞起例等） |
| 消息外发 | SWRL → RabbitMQ（大凶 / 寿元关 / 重大疾病标志） |

> **依据（强制）**：每条类/属性/约束/SWRL 均标注出处（★铁律 8/9/10/12/14）。自节点 2 起的方法与数据追溯至原著**具体篇章**（**不允许编造**）；查询不到者一律标【待考·轻/中/重】，不硬写。原著 ＝ **《紫微斗數全書》**（[宋]陈抟（陈希夷）撰）——语料归档于 `<工作目录>/HWCodeArtsDir/zwds-txt/`（卷一卷二卷三）。
> **IRI 前缀简称**：`core:`/`calendar:`/`chart:`/`star:`/`layout:`/`state:`/`palace:`/`pattern:`/`fortune:`/`reading:`/`diagnosis:`/`rules:`，对应顶层设计 §4.1 命名空间（`http://example.org/ziweidoushu/<模块名>`）。
> **节点对齐**：本文件展开自顶层设计 §5 顶层类框架，逐条对齐流程设计 §3 的 **21 节点（1~17 + V/W/C/A）**；并覆盖顶层设计 §2.1(A) 21 概念域 + §2.1(B) 7 支撑概念域（S1~S7）。
> **★两类「化」分列（硬性）**：`state:StarCharacter`（星曜**化气·静态**，如紫微化气为尊）与 `layout:Transformation`（**四化·动态**，化禄/权/科/忌，生年干/宫干引动）**分属不同子本体与不同类，属性不得互换**（顶层设计 §1；流程设计 §3）。

---

## 1. 核心类体系（明细）

> 展开自《本体顶层设计》§5 顶层类框架；主干分支不变，细化为**可落库的类层级**。标注 `★` 者为流程运转**支撑概念**（对应顶层设计 §2.1(B) 的 S1~S7）。

```
core:ZiWeiDouShuEntity（本体事物根）

├── core:CalendarDomain（历法基础域）★【S1/S2】
│   ├── core:YinYang（阴阳；2 子类）—— core:Yang / core:Yin
│   ├── core:FivePhase（五行；5 子类）—— core:Jin / core:Mu / core:Shui / core:Huo / core:Tu
│   ├── core:HeavenlyStem（十天干；10 子类）—— core:Stem_Jia … core:Stem_Gui
│   ├── core:EarthlyBranch（十二地支；12 子类）—— core:Branch_Zi … core:Branch_Hai
│   ├── core:SexagenaryCycle（六十甲子；按干支组合 60）
│   ├── core:NaYin（纳音）—— 纳音取象（六十花甲子納音歌）
│   ├── core:FivePhaseBureau（五行局；5 子类）—— core:Ju_Shui2 / Ju_Mu3 / Ju_Jin4 / Ju_Tu5 / Ju_Huo6
│   ├── core:TwelveStage（十二长生；12 子类）—— core:Stage_ChangSheng … core:Stage_Yang
│   ├── calendar:TrueSolarTime（真太阳时 / 经度校正）
│   ├── calendar:LeapMonth（闰月归属）
│   └── calendar:CalendarBoundary（历法边界：时辰交界/昼夜判定/时区·夏令时；枚举）
│
├── chart:ChartDomain（排盘域）
│   ├── chart:BirthInput（生辰输入：公历/农历·年月日时·性别·出生地经度·命主类别·输入模式·推演深度·运限粒度）
│   ├── chart:NatalChart（命盘）
│   ├── chart:Palace（宫；12 子类）—— Palace_Ming(命)/XiongDi(兄弟)/FuQi(夫妻)/ZiNv(子女)/CaiBo(财帛)/JiE(疾厄)/QianYi(迁移)/JiaoYou(交友[仆役])/GuanLu(官禄)/TianZhai(田宅)/FuDe(福德)/FuMu(父母)
│   ├── chart:LifePalace（命宫）/ chart:BodyPalace（身宫）
│   ├── chart:PalaceStem（宫干 / 五鼠遁）
│   ├── chart:LifeMaster（命主）/ chart:BodyMaster（身主）
│   ├── chart:MingChartSkeleton（命盘骨架：十二宫 + 命身宫 + 五行局 + 命主身主）
│   └── chart:PilotLimit（限：大限/童限/小限 之起法结果；见 fortune:）
│
├── star:StarDomain（星曜域）
│   ├── star:Star（星曜根）
│   │   ├── star:FourteenMainStar（十四主星）
│   │   │   ├── star:BeiDouStar（北斗）—— 紫微/天机/太阳/武曲/天同/廉贞
│   │   │   └── star:NanDouStar（南斗）—— 天府/太阴/贪狼/巨门/天相/天梁/七杀/破军
│   │   ├── star:AuxiliaryStar（辅佐星）—— 左辅/右弼/文昌/文曲/天魁/天钺/天马/禄存
│   │   ├── star:MaleficStar（煞忌星）—— 擎羊/陀罗/火星/铃星/天空/地劫/天伤/天使
│   │   └── star:MiscStar（杂曜）—— 三台/八座/龙池/凤阁/台辅（★安台辅封诀）/封诰（★安封诰诀）/天刑/天姚/天哭/天虚/红鸾/天喜/天德/月德/解神/截路空亡/旬中空亡
│   └── star:YearlyDeity（太岁诸神 / 流曜）—— 博士…官府(12) + 流禄/流羊/流陀 + 斗君 + 太岁杀禄 + ★star:FourFlyingStar（丧门/白虎/吊客/官府 四飞星）+ ★star:FlyingTriSha（飞天三杀：奏书/将军/直符）
│
├── layout:LayoutDomain（安星落宫域）
│   ├── layout:StarPlacement（星曜落宫）—— 主星盘/辅煞盘/杂曜盘/太岁流曜基准盘
│   ├── layout:Transformation（四化·**动态**）—— 化禄/权/科/忌（生年干/宫干引动，含落宫）
│   └── layout:TwelveStagePlacement（十二长生落宫）
│
├── state:StateDomain（星曜状态域）
│   ├── state:Brightness（庙旺利陷；7 子类）—— 庙/旺/得地/利益/平和/不得地/陷
│   ├── state:PhaseRelation（星辰五行生克制化）
│   ├── state:PalaceStrength（十二宫弱强 / 星辰落闲宫）
│   └── state:StarCharacter（星曜化气·**静态**）—— 十四主星固有化气（尊/善/贵/财/福/囚/令/富/桃花/暗/印/荫/权/耗）
│
├── palace:PalaceDomain（宫位体系域）
│   ├── palace:PalaceMeaning（十二宫义）
│   ├── palace:SanFangSiZheng（三方四正）—— 本宫 + 对宫 + 三合二宫 + 冲照
│   ├── palace:Adjoining（夹宫）—— 邻宫相夹（紫府夹/日月夹/昌曲夹/羊陀夹/火铃夹/魁钺夹/左右夹/劫空夹）
│   └── palace:EmptyPalace（空宫借星 / 借光规则 / 空宫特殊格局）
│
├── pattern:PatternDomain（格局与星情域）
│   ├── pattern:ClassicText（赋论总纲 + 骨髓赋 + ★羅序：太微赋/形性赋/星垣论/斗数准绳/斗数发微论/重补斗数彀率/增补太微赋/骨髓赋/女命骨髓赋/羅序）
│   ├── pattern:StarPattern（星曜格局）—— 科权禄主格/左右朝垣格/文星朝命格/兼文武格/对面朝斗格/石中隐玉格/火贪格/紫府朝垣/君臣庆会/紫微七杀化权/紫府同临巳亥/日月同临 等；★补：**诸格论 19 子目**（对面朝斗/科权禄主/左右朝垣/兼文武/文星朝命/石中隐玉/火贵 等）+ **卷三夹宫诸格**（紫府夹命/日月夹命/昌曲夹命/羊陀夹忌/火铃夹命/魁钺夹命/左右夹命/劫空夹命；夹身：三夹凶·六夹吉）+ **定局格名代表**（见下行）
│   ├── pattern:SpecialStructure（★定局：定富局/定贵局/定贫贱局/定杂局）—— **富局 6 格**（财荫夹印/日月夹财/财禄夹马/荫印拱身/日月照璧/金灿光辉）+ **贵局 27 格**（日月夹命/日出扶桑/月落亥宫·月朗天门/月生沧海/辅弼拱主/君臣庆会/财印夹禄/禄马佩印/坐贵向贵/马头带剑/七杀朝斗/日月并明/明珠出海/日月同临/刑囚夹印/科权禄拱/贪火相逢/武曲守垣/府相朝垣/紫府朝垣/文星暗拱/权禄生逢/羊刃入庙/巨机居卯/明禄暗禄/科明暗禄/金舆扶驾）+ **贫贱局 8 格** + **杂局 8 格**【卷一·定贵局 等；★据 `c2` 卷一 行 604–631 补正：贵局实为 27 格】
│   ├── pattern:PalaceStarQualification（★十二宫诸星得地合格诀/失陷破格诀/得地富贵论/失陷贫贱论）
│   ├── pattern:PatternGrade（命格高低：论人命入格 / 格星数高下 / ★定富贵贫贱十等论（十等：福寿/聪明/威勇/文职/武职/刑名/富贵/贫贱/疾殀/僧道））
│   ├── pattern:StarCombination（星曜组合断法：双星/三星同宫、三方四正复合象义）
│   └── pattern:StarMeaning（诸星所主 / 星情）—— 主星所主 / 辅煞星所主 / 杂曜所主 / ★论诸星同垣各司所宜分别富贵贫贱夭寿（星情格语）
│
├── fortune:FortuneDomain（运限域）
│   ├── fortune:GreatLimit（大限 / 起限 / 顺逆 / 童限）
│   ├── fortune:SmallLimit（小限）
│   ├── fortune:AnnualFortune（流年 / 太岁）
│   ├── fortune:RecalculatedDeity（流年重算流曜：流禄/流羊/流陀/流年十二神/斗君）
│   ├── fortune:OverlayReading（叠宫断法 / 三盘叠加：本命↔大限↔流年）
│   ├── fortune:TriggeredPattern（凶格引动：羊陀迭并 / 七杀重逢）
│   ├── fortune:LimitSong（行限宫歌（分南北斗）；★含「太岁并小限到十二宫」入庙化吉/不入庙化凶/所值吉凶星断）
│   ├── fortune:LimitTaboo（★大小限星辰过十二宫遇十二支所忌）
│   └── fortune:FlyingTransformation（宫干飞化 / 化入化出·飞星轨）
│
├── reading:ReadingDomain（断语域）
│   ├── reading:Temperament（性情形貌）
│   ├── reading:Kinship（六亲：父母/兄弟/夫妻/子女）
│   ├── reading:Marriage（婚姻）
│   ├── reading:Career（事业官禄）
│   ├── reading:Wealth（财帛）
│   ├── reading:Disease（疾病疾厄）
│   ├── reading:Longevity（寿夭孤克残疾；★含 定人残疾/破相/聪明/富足/贫贱/作盗贼/一身驳杂、出家僧道、商贾、艺术、贫富先后）
│   ├── reading:GenderDifference（男女命同异）
│   ├── reading:LadyFortune（女命专论）
│   ├── reading:ChildFortune（小儿命 / 关煞 / 童限）
│   ├── reading:Career 官阶分类（★定公卿/定两官府/定文官/定武官/定曹吏）
│   └── reading:Admonition（阴骘延寿劝诫）
│
├── diagnosis:DiagnosisDomain（出处与流派标注域）★【S3】
│   ├── diagnosis:SourceNote（出处标注：书名·卷·篇）
│   ├── diagnosis:SchoolVariant（流派分歧 / 双轨标注：三合轨 / 飞星轨）
│   ├── diagnosis:ToBeVerified（【待考·轻/中/重】标记）
│   └── diagnosis:ChartVerification（验盘：校正后时辰 / 候补时辰集 / 定盘说明）
│
└── OutputSubdomain（输出与存档域，跨域归入既有命名空间）
    ├── reading:ReadingConclusion（综合结论 / 命书）
    ├── reading:StructuredOutput（结构化批断 / JSON Schema）
    ├── reading:ChartGraphic（命盘图形化规范：十二宫方形布局与图示约定）【S7·K65】
    ├── rules:AlertFlag（告警标志：大凶 / 寿元关 / 重大疾病）★【S5】
    └── reading:CaseRecord（命例记录 / 隐私存档）★【S6】
```

> **说明（输出域的落位）**：顶层设计 §5 的 `OutputDomain` 不单独设子本体（§3 的 12 子本体已含 `rules` 而无 `output`）。故其成员按语义归入既有命名空间——`ReadingConclusion`/`StructuredOutput`/`ChartGraphic`/`CaseRecord` 归 `reading:`（批断输出/图形规范/存档）、`AlertFlag` 归 `rules:`（该命名空间角色即「含告警标志推导」，照 Example 中 `LowStock*` 由规则推导的先例）。为一处**落位细化**，提请注意（见 §9-1）。
> **与顶层框架的对应**：上列 12 个主干分支与顶层设计 §5 一一对应；`core:` 根（`ZiWeiDouShuEntity`）承载所有分支。**★两类「化」分列**：`state:StarCharacter`（静态化气）与 `layout:Transformation`（动态四化）分属 `state:`/`layout:`，本文件全链不得混指。

---

### 1.1 支撑域说明（流程运转所必需的历法基础 / 历法时辰 / 出处标注 / 术语 / 告警 / 命例）★

> 逐项说明支撑概念**为何为流程运转所必需**，并给出原著出处（对齐顶层设计 §2.1(B) S1~S7）。**出处均可回原文定位，见 §10 复核。**

| 支撑域 | 展开的类/属性 | 用途（对应流程节点） | 原著出处 |
|---|---|---|---|
| **S1 基础历法体系** | `core:YinYang`/`FivePhase`/`HeavenlyStem`/`EarthlyBranch`/`SexagenaryCycle`/`NaYin`/`FivePhaseBureau`/`TwelveStage`；`core:hasStem`/`hasBranch`/`hasNaYin`/`hasBureau`/`generates`/`overcomes` | 节点 2（五行局/纳音）、节点 6（十二长生）、节点 13（起大限）、节点 14（流年干支）之共同基础 | 卷二·起五行寅例/六十花甲子納音歌/星辰屬性诸条（星曜五行）；卷二·十二长生（男顺女逆） |
| **S2 历法与时辰** | `calendar:TrueSolarTime`/`LeapMonth`/`CalendarBoundary`；`calendar:hasTrueSolarTime`/`hasLeapMonth`/`hasBoundary` | 节点 1（时辰校正/历法边界）之共同基础 | 卷二·安身命例（闰月按下月）；卷三·論人生時要審的確（时辰交界前归）；**【真太阳时/时区为历法惯例，全書之外，待考·中】** |
| **S3 出处与流派标注** | `diagnosis:SourceNote`/`SchoolVariant`/`ToBeVerified`/`ChartVerification`；`diagnosis:hasSourceNote`/`hasSchoolVariant`/`hasToBeVerified`/`hasChartVerification` | 全流程——★铁律 9/10「不臆造唯一断语、须指到出处」的承载机制（节点 7 庙陷来源、节点 11 流派并陈、节点 V 验盘） | 顶层设计 §2.4 流派基线；卷一·諸格论（并陈之据）；I·63 来源优先级 |
| **S4 术语同义与规范** | `diagnosis:Synonym`（同义词/别名）；经第 13 步 SKOS `term` 承载 | 全流程——供 `SkosSynonymReader`/`OntologyLabelMatcher` 名称归一 | 《全書》异名（紫微＝帝座、贪狼＝桃花、巨门＝暗、天相＝印、天梁＝荫、擎羊＝刑、陀罗＝忌、破军＝耗、交友＝仆役宫）：卷一·諸星問答論；卷二·星辰屬性诸条 |
| **S5 吉凶标志与告警** | `rules:AlertFlag`（大凶 / 寿元关 / 重大疾病，枚举个体）；`rules:hasAlertFlag`/`alertTriggered` | 节点 A（外发告警）：SWRL 命中即外发，无数值阈值 | 顶层设计（消息外发）；《全書》卷三断法；客户裁定 ㉖（2026-10-31） |
| **S6 命例与隐私** | `reading:CaseRecord`；`reading:hasCaseRecord`/`caseBirthData`/`caseResult` | 节点 17（结果落库与返回） | 顶层设计 K·56；客户裁定 ㉗（2026-10-31：**脱敏、仅存必要字段**） |
| **S7 跨切面规范** | `reading:ChartGraphic`（K·65 命盘图形化规范）；`diagnosis:SchoolVariant`（J·55 参照典籍引用标注） | 跨节点：K·65 落节点 16→17；J·55 落节点 3/5/6/7/9/10/11/13 | 顶层设计 §2.1·J·55、K·65；流程设计 §3.5 |

---

### 1.2 全篇目复核补遗对应类（★本次「全文查漏」新增/细化，映射既有 66 项）

> 据《全書》**实际篇目**（正文多于 EPUB 目录）逐篇补全；下列类/属性为本次新增或细化，**其范围项均为既有 66 项**（对应顶层设计 §2.1(C) 表）。

| 补遗篇目（《全書》出处） | 新增/细化类 | 关键属性（§2/§3） | 备注 |
|---|---|---|---|
| 卷一·羅序 | `pattern:ClassicText`（增「羅序」文本型） | `pattern:classicTextName`（增「羅序」） | 序文源流；挂 `diagnosis:SourceNote` |
| 卷一·定富局/定贵局/定贫贱局/定杂局 | **`pattern:SpecialStructure`（新增）** | `pattern:structureType`（富局/贵局/贫贱局/杂局） | **★据《全書》卷一补正：定贵局实为 27 格（原记 11 格有漏）；富局 6 格、贵局 27 格、贫贱局 8 格、杂局 8 格名全枚举于 `pattern:SpecialStructure` label**，代表性格名录入 `pattern:StarPattern`（`pattern:patternName`） |
| 卷一·十二宫诸星得地合格诀/失陷破格诀/得地富贵论/失陷贫贱论 | **`pattern:PalaceStarQualification`（新增）** | `pattern:qualificationType`（得地合格/失陷破格/得地富贵/失陷贫贱） | 逐宫逐星得/失判定（节点 11） |
| 卷一·定富贵贫贱十等论 | `pattern:PatternGrade`（细化十等） | `pattern:patternGradeName`（十等**全枚举**：福寿/聪明/威勇/文职/武职/刑名/富贵/贫贱/疾殀/僧道） | 与卷三·论人命入格/论格星数高下 并用 |
| 卷一·诸格论 19 子目 | `pattern:StarPattern`（增格名，**全枚举**） | `pattern:patternName` | 对斗/科权禄/左右朝垣/兼文武/文星朝命/石中隐玉/火贪 等 |
| 卷三·夹宫诸格（篇名之下子目） | `pattern:StarPattern`（增格名，**全枚举**） | `pattern:patternName` | 紫府/日月/昌曲/羊陀/火铃/魁钺/左右/劫空 夹命；夹身：三夹凶·六夹吉 |
| 卷一·论有/无商贾、艺术、出家僧道、内犯孤克、寿夭淫荡、定人残疾/破相/聪明/富足/贫贱/作盗贼/一身驳杂 | `reading:Longevity`（分类扩展） | `reading:longevityType`（枚举扩充） | 断语分类（节点 12） |
| 卷二·安丧门白虎吊客官府四飞星诀 | **`star:FourFlyingStar`（新增）** | `star:deityName`（丧门/白虎/吊客/官府） | 起法：太岁前二丧门·后二吊客·对照白虎/官府 |
| 卷二·安飞天三杀诀（奏书/将军/直符） | **`star:FlyingTriSha`（新增）** | `star:deityName`（奏书/将军/直符） | 飞天三杀起法 |
| 卷二·安台辅封诀 / 安封诰诀 | `star:MiscStar`（分列台辅/封诰） | `star:starName` | 原合称「台辅封诰」拆为两种 |
| 卷二·论诸星分属南北斗化吉凶并分属五行 | `state:StarCharacter`（**出处校正**）+ `core:hasFivePhase` 补出处 | `state:characterName`/`core:phaseName` | **化气/五行/主宫总表**（含辅煞杂流曜） |
| 卷二·紫微定位棋盘图（水二…火六局） | `core:FivePhaseBureau`（定位表出处点名） | `core:bureauNumber`/`core:stageStartBranch` | 安紫微定位（节点 3，A·5） |
| 卷二·安小限诀 | `fortune:SmallLimit`（出处点名） | `fortune:smallLimitBranch` | 起小限（男顺女逆） |
| 卷二·定公卿/定两官府/定文官/定武官/定曹吏 | `reading:Career`（官阶枚举） | `reading:careerRank`（新增） | 官禄分类 |
| 卷三·论人生时安命吉凶 | `calendar:CalendarBoundary`（佐证） | `calendar:boundaryType` | 人生时安命（节点 1/V） |
| 卷三·论命先贫后富 | `reading:Wealth`（贫富先后） | `reading:wealthTrend`（新增） | 断语（节点 12/15） |
| 卷三·论大小限星辰过十二宫遇十二支所忌诀 | **`fortune:LimitTaboo`（新增）** | `fortune:tabooBranch`（新增，12 支所忌） | 运限（节点 13/14/15） |
| 卷三·论诸星同垣各司所宜分别富贵贫贱夭寿 | `pattern:StarMeaning`/`StarPattern`（星情格语）+ 庙陷表出处点名 | `pattern:starMeaningText` | 诸星庙旺陷定位 + 格语（节点 7/11） |

---

## 2. 对象属性

> 特性列标注函数（Functional）/对称（Symmetric）/传递（Transitive）/逆（Inverse）；每条标出处。**互逆属性成对声明**（§6-1）。

| 属性 | 特性 | `owl:inverseOf` | 定义域 | 值域 | 出处 |
|---|---|---|---|---|---|
| core:hasYinYang | Functional | core:isYinYangOf | core:CalendarEntity | core:YinYang | 卷二·星辰屬性诸条（星曜阴阳） |
| core:hasFivePhase | Functional | core:isFivePhaseOf | core:CalendarEntity | core:FivePhase | 卷二·星辰屬性诸条（星曜五行属） |
| core:generates（生） | Transitive | core:generatedBy | core:FivePhase | core:FivePhase | 卷二·論星辰生剋制化 |
| core:overcomes（克） | Transitive | core:overcomeBy | core:FivePhase | core:FivePhase | 卷二·論星辰生剋制化 |
| core:hasStem | Functional | core:isStemOf | core:SexagenaryCycle | core:HeavenlyStem | 卷二·六十花甲子納音歌 |
| core:hasBranch | Functional | core:isBranchOf | core:SexagenaryCycle | core:EarthlyBranch | 卷二·六十花甲子納音歌 |
| core:hasNaYin | Functional | core:isNaYinOf | core:SexagenaryCycle | core:NaYin | 卷二·六十花甲子納音歌 |
| core:hasBureau | Functional | core:isBureauOf | core:SexagenaryCycle∨core:NaYin | core:FivePhaseBureau | 卷二·起五行寅例（以纳音定五行局） |
| core:hasTwelveStage | — | core:isTwelveStageOf | core:FivePhaseBureau | core:TwelveStage | 卷二·十二长生（按五行局起长生，男顺女逆；局定长生布十二宫，见 §基数闭合「布十二长生恰 12」） |
| calendar:hasTrueSolarTime | Functional | calendar:isTrueSolarTimeOf | chart:BirthInput | calendar:TrueSolarTime | 历法惯例（客户裁定 ②）；非《全書》【待考·中】 |
| calendar:hasLeapMonth | Functional | calendar:isLeapMonthOf | chart:BirthInput | calendar:LeapMonth | 卷二·安身命例（闰月按下月） |
| calendar:hasBoundary | Functional | calendar:isBoundaryOf | chart:BirthInput | calendar:CalendarBoundary | 卷三·論人生時要審的確（时辰交界）；历法惯例（客户裁定 ③④） |
| chart:hasBirthInput | Functional | chart:isBirthInputOf | chart:NatalChart | chart:BirthInput | 流程节点 1 |
| chart:hasSkeleton | Functional | chart:isSkeletonOf | chart:NatalChart | chart:MingChartSkeleton | 卷二·安身命例/安十二宮例 |
| chart:hasPalace | — | chart:isPalaceOf | chart:NatalChart | chart:Palace | 卷二·安十二宮例 |
| chart:hasLifePalace | Functional | chart:isLifePalaceOf | chart:NatalChart | chart:LifePalace | 卷二·安身命例 |
| chart:hasBodyPalace | Functional | chart:isBodyPalaceOf | chart:NatalChart | chart:BodyPalace | 卷二·安身命例 |
| chart:hasBureau | Functional | chart:isBureauOf | chart:NatalChart | core:FivePhaseBureau | 卷二·起五行寅例 |
| chart:hasPalaceStem | Functional | chart:isPalaceStemOf | chart:Palace | core:HeavenlyStem | 卷二（推算宫干·五鼠遁）【待考·中】 |
| chart:hasBranch | Functional | chart:isBranchOf | chart:Palace | core:EarthlyBranch | 卷二·安十二宮例 |
| chart:hasLifeMaster | Functional | chart:isLifeMasterOf | chart:NatalChart | chart:LifeMaster | 卷二·安命主 |
| chart:hasBodyMaster | Functional | chart:isBodyMasterOf | chart:NatalChart | chart:BodyMaster | 卷二·安身主 |
| star:hasStar | — | star:isStarOf | chart:NatalChart | star:Star | 卷二·安南北斗諸星訣 等（安星） |
| star:isBeiDou | Functional | star:isBeiDouOf | star:FourteenMainStar | xsd:boolean（分类；亦可子类承载） | 卷一·諸星問答論；卷二·星辰屬性（南北斗归属） |
| layout:placedIn（星曜落宫） | Functional | layout:hostsStar | star:Star | chart:Palace | 卷二·安南北斗諸星訣/安文昌文曲星訣 等 |
| layout:hasStarPlacement | — | layout:isStarPlacementOf | chart:NatalChart | layout:StarPlacement | 卷二·各安星诀（主星盘/辅煞盘/杂曜盘） |
| layout:hasTransformation（四化·动态） | — | layout:isTransformationOf | chart:NatalChart∨star:Star | layout:Transformation | 卷一·諸星問答論·問化祿/化權/化科/化忌星所主若何；卷二（四化安法） |
| layout:transformsTo | Functional | layout:isTransformedFrom | layout:Transformation | star:Star | 卷一·諸星問答論（干→星化） |
| layout:hasTwelveStagePlacement | — | layout:isTwelveStagePlacementOf | chart:NatalChart | layout:TwelveStagePlacement | 卷二·十二长生（按五行局布十二长生） |
| state:hasBrightness（庙陷） | Functional | state:isBrightnessOf | layout:StarPlacement | state:Brightness | 卷二·星辰屬性诸条；卷三·诸星庙陷 |
| state:hasStarCharacter（化气·静态） | Functional | state:isStarCharacterOf | star:FourteenMainStar | state:StarCharacter | 卷一·諸星問答論（十四主星化气） |
| state:hasPhaseRelation | — | state:isPhaseRelationOf | star:Star | state:PhaseRelation | 卷二·論星辰生剋制化 |
| state:hasPalaceStrength | Functional | state:isPalaceStrengthOf | chart:Palace | state:PalaceStrength | 卷二·定十二宮弱強/定十二宮星辰落閑 |
| palace:hasPalaceMeaning | Functional | palace:isPalaceMeaningOf | chart:Palace | palace:PalaceMeaning | 卷二·安十二宮例；卷一·斗數骨髓賦 |
| palace:hasSanFang | — | palace:isSanFangOf | chart:Palace | palace:SanFangSiZheng | 卷一·太微賦（三方四正） |
| palace:oppositeOf（对宫） | Symmetric | palace:oppositeOf | chart:Palace | chart:Palace | 卷二·定十二宮弱強（对宫冲照） |
| palace:tripleWith（三合） | Symmetric | palace:tripleWith | chart:Palace | chart:Palace | 卷一·太微賦（三合宫） |
| palace:adjacentWith（夹宫·邻宫） | Symmetric | palace:adjacentWith | chart:Palace | chart:Palace | 卷三·諸格断语（邻宫相夹） |
| palace:borrowsFrom（空宫借星） | Functional | palace:lendsTo | chart:Palace | chart:Palace | 【后世通行规则，待考·中】 |
| palace:hasEmptyPalace | Functional | palace:isEmptyPalaceOf | chart:Palace | palace:EmptyPalace | 卷二·安十二宮例（空宫）；【留待 I·62】 |
| pattern:hasStarMeaning（诸星所主） | — | pattern:isStarMeaningOf | star:Star | pattern:StarMeaning | 卷一·諸星問答論（诸星所主） |
| pattern:hasStarPattern（星曜格局） | — | pattern:isStarPatternOf | chart:NatalChart∨chart:Palace | pattern:StarPattern | 卷一·諸格论；卷三·諸格断语 |
| pattern:hasClassicText | — | pattern:isClassicTextOf | pattern:StarPattern | pattern:ClassicText | 卷一·太微賦/形性賦/星垣論… |
| pattern:hasPatternGrade | Functional | pattern:isPatternGradeOf | chart:NatalChart | pattern:PatternGrade | 卷三·論人命入格/論格星數高下；★卷一·定富貴貧賤十等論 |
| pattern:hasSpecialStructure（★定局） | — | pattern:isSpecialStructureOf | chart:NatalChart | pattern:SpecialStructure | ★卷一·定富局/定貴局/定貧賤局/定雜局 |
| pattern:hasPalaceStarQualification（★十二宫诸星得地/失陷论） | — | pattern:isPalaceStarQualificationOf | chart:Palace | pattern:PalaceStarQualification | ★卷一·十二宮諸星得地合格訣/失陷破格訣/得地富貴論/失陷貧賤論 |
| pattern:hasStarCombination | — | pattern:isStarCombinationOf | chart:Palace | pattern:StarCombination | 卷一·諸格论；卷三·諸格断语 |
| fortune:hasGreatLimit | — | fortune:isGreatLimitOf | chart:NatalChart | fortune:GreatLimit | 卷二·安大限訣/安童限訣；卷三·論大限十年禍福 |
| fortune:hasSmallLimit | — | fortune:isSmallLimitOf | chart:NatalChart | fortune:SmallLimit | 卷二（安小限）；卷三·論二限太歲吉凶 |
| fortune:hasAnnualFortune | — | fortune:isAnnualFortuneOf | chart:NatalChart | fortune:AnnualFortune | 卷三·論流年太歲吉凶星殺 |
| fortune:hasRecalculatedDeity | — | fortune:isRecalculatedDeityOf | fortune:AnnualFortune | fortune:RecalculatedDeity | 卷二·安流祿流羊流陀訣/安斗君訣（按流年干支重算） |
| fortune:hasTriggeredPattern | — | fortune:isTriggeredPatternOf | fortune:AnnualFortune∨fortune:GreatLimit | fortune:TriggeredPattern | 卷三·論羊陀迭並/論七殺重逢 |
| fortune:hasLimitTaboo（★大小限过十二支所忌） | — | fortune:isLimitTabooOf | fortune:AnnualFortune∨fortune:GreatLimit | fortune:LimitTaboo | ★卷三·論大小限星辰過十二宮遇十二支所忌訣 |
| fortune:overlayReading | — | fortune:isOverlayReadingOf | fortune:AnnualFortune∨fortune:GreatLimit | fortune:OverlayReading | 卷三·論大限十年禍福/論二限太歲吉凶；【三盘叠加为后世通行断法，待考·中】 |
| fortune:hasLimitSong | — | fortune:isLimitSongOf | fortune:GreatLimit∨fortune:AnnualFortune | fortune:LimitSong | 卷三·論立命行限宮歌 |
| fortune:hasFlyingTransformation | — | fortune:isFlyingTransformationOf | chart:Palace∨fortune:AnnualFortune | fortune:FlyingTransformation | 卷一·諸星問答論四化所主（深义）；【飞星派化入化出，全書未载，待考·中】 |
| reading:hasReading | — | reading:isReadingOf | chart:NatalChart | reading:ReadingItem | 卷三·談星要論（分类断） |
| reading:hasTemperament | — | reading:isTemperamentOf | chart:NatalChart | reading:Temperament | 卷一·形性賦 |
| reading:hasKinship | — | reading:isKinshipOf | chart:NatalChart | reading:Kinship | 卷一·斗數骨髓賦；卷三·談星要論 |
| reading:hasMarriage | — | reading:isMarriageOf | chart:NatalChart | reading:Marriage | 卷一·女命骨髓賦；卷三·論男女命同異 |
| reading:hasCareer | — | reading:isCareerOf | chart:NatalChart | reading:Career | 卷三·談星要論；卷一·諸格论 |
| reading:hasWealth | — | reading:isWealthOf | chart:NatalChart | reading:Wealth | 卷三·談星要論 |
| reading:hasDisease | — | reading:isDiseaseOf | chart:NatalChart | reading:Disease | 卷一·斗數骨髓賦；卷三·談星要論 |
| reading:hasLongevity | — | reading:isLongevityOf | chart:NatalChart | reading:Longevity | 卷一·論壽夭淫蕩/論定人殘疾/論人命內犯孤剋者 |
| reading:hasGenderDifference | — | reading:isGenderDifferenceOf | chart:NatalChart | reading:GenderDifference | 卷三·論男女命同異 |
| reading:hasLadyFortune | — | reading:isLadyFortuneOf | chart:NatalChart | reading:LadyFortune | 卷一·女命骨髓賦；卷三·論男女命同異 |
| reading:hasChildFortune | — | reading:isChildFortuneOf | chart:NatalChart | reading:ChildFortune | 卷三·論小兒命/定小兒生時訣/論小兒剋親 |
| reading:hasAdmonition | — | reading:isAdmonitionOf | chart:NatalChart | reading:Admonition | 卷三·論陰騭延壽 |
| diagnosis:hasSourceNote | — | diagnosis:isSourceNoteOf | core:ZiWeiDouShuEntity | diagnosis:SourceNote | 顶层设计 §2.1(B)·S3；★铁律 9/10 |
| diagnosis:hasSchoolVariant | — | diagnosis:isSchoolVariantOf | core:ZiWeiDouShuEntity | diagnosis:SchoolVariant | 顶层设计 §2.4（双轨并陈） |
| diagnosis:hasToBeVerified | — | diagnosis:isToBeVerifiedOf | core:ZiWeiDouShuEntity | diagnosis:ToBeVerified | ★铁律 8/9/10 |
| diagnosis:hasChartVerification | Functional | diagnosis:isChartVerificationOf | chart:NatalChart | diagnosis:ChartVerification | 【验盘，实务通行法，待考·中】（I·61） |
| reading:hasConclusion | Functional | reading:isConclusionOf | chart:NatalChart | reading:ReadingConclusion | 流程节点 16（汇总） |
| reading:hasStructuredOutput | Functional | reading:isStructuredOutputOf | chart:NatalChart | reading:StructuredOutput | 顶层设计 K·66 |
| rules:hasAlertFlag | — | rules:isAlertFlagOf | chart:NatalChart | rules:AlertFlag | 顶层设计（消息外发）；客户裁定 ㉖ |
| reading:hasCaseRecord | Functional | reading:isCaseRecordOf | chart:NatalChart | reading:CaseRecord | 顶层设计 K·56（节点 17） |

> **内置映射表（非本体个体）**：`starLayoutMap`（安星起例：以年干/年支/生月/生时/五行局+生日 → 星落宫）、`brightnessMap`（星 × 宫 → 庙陷）、`sihuaMap`（生年干 → 四化星）、`palaceStemMap`（年干起五鼠遁 → 宫干）、`deityRuleMap`（流曜十二神/斗君起例）为**内置映射/查表**，实现于查询/规则层或经 OBDA（MySQL 安星/庙陷/神煞查找表），**不建模为冗余个体**（对齐 Example / SiZhuBaZi 做法）。星曜名录与十二宫义本身由 `star:Star`/`palace:PalaceMeaning` 承载。

---

## 3. 数据属性

> **硬性**：数值一律用数值类型（`xsd:integer`/`xsd:decimal`/`xsd:boolean`），枚举用 `xsd:string`（★12）。

| 属性 | 类型 | 定义域 | 出处 |
|---|---|---|---|
| core:yinYangName（阴/阳） | xsd:string（枚举） | core:YinYang | 卷二·星辰屬性诸条 |
| core:phaseName（金/木/水/火/土） | xsd:string（枚举） | core:FivePhase | 卷二·星辰屬性诸条 |
| core:stemName（甲…癸） | xsd:string（枚举） | core:HeavenlyStem | 卷二·六十花甲子納音歌 |
| core:stemYinYang（甲丙戊庚壬阳 / 乙丁己辛癸阴） | xsd:string（枚举） | core:HeavenlyStem | 甲丙戊庚壬阳/乙丁己辛癸阴（命理通例） |
| core:branchName（子…亥） | xsd:string（枚举） | core:EarthlyBranch | 卷二·安十二宮例 |
| core:branchAnimal（鼠…猪） | xsd:string | core:EarthlyBranch | 十二生肖（历法惯例） |
| core:branchOrder（子=1…亥=12） | xsd:integer | core:EarthlyBranch | 卷二·安十二宮例 |
| core:naYinName（海中金…大海水） | xsd:string | core:NaYin | 卷二·六十花甲子納音歌 |
| core:bureauName（水二局/木三局/金四局/土五局/火六局） | xsd:string（枚举） | core:FivePhaseBureau | 卷二·起五行寅例 |
| core:bureauNumber（2/3/4/5/6） | xsd:integer | core:FivePhaseBureau | 卷二·起五行寅例（局数即起限岁数） |
| core:stageName（长生/沐浴/冠带/临官/帝旺/衰/病/死/墓/绝/胎/养） | xsd:string（枚举） | core:TwelveStage | 卷二·十二长生 |
| core:stageStartBranch（长生所起地支：申/亥/巳/申/寅） | xsd:string | core:FivePhaseBureau | 卷二·十二长生（火寅/木亥/水申/金巳/土申） |
| calendar:longitude（出生地经度） | xsd:decimal | calendar:TrueSolarTime | 历法惯例（客户裁定 ②）；非《全書》 |
| calendar:leapMonthRule（按下月） | xsd:string（枚举） | calendar:LeapMonth | 卷二·安身命例 |
| calendar:boundaryType（时辰交界/昼夜/时区/夏令时） | xsd:string（枚举） | calendar:CalendarBoundary | 卷三·論人生時要審的確；历法惯例（客户裁定 ③④） |
| chart:birthDate（公历或农历生辰） | xsd:string | chart:BirthInput | 流程节点 1 |
| chart:calendarType（公历/农历） | xsd:string（枚举） | chart:BirthInput | 流程节点 1 |
| chart:birthHour（时辰） | xsd:string | chart:BirthInput | 流程节点 1 |
| chart:gender（男/女） | xsd:string（枚举） | chart:BirthInput | 流程节点 1（大限顺逆依此） |
| chart:subjectType（成人男/成人女/小儿） | xsd:string（枚举） | chart:BirthInput | 流程 §3（G2 分支） |
| chart:mode（直接排盘/验盘） | xsd:string（枚举） | chart:BirthInput | 流程 §3（G1 分支；I·61） |
| chart:depth（本命/本命+大限/本命+大限+流年） | xsd:string（枚举） | chart:BirthInput | 流程 §3（G3 分支） |
| chart:granularity（年/月/日） | xsd:string（枚举） | chart:BirthInput | 客户裁定 ㉓（默认年；月/日 预留 R1） |
| chart:palaceName（命/兄弟/夫妻/子女/财帛/疾厄/迁移/交友/官禄/田宅/福德/父母） | xsd:string（枚举） | chart:Palace | 卷二·安十二宮例 |
| chart:palaceOrder（宫位序 1…12） | xsd:integer | chart:Palace | 卷二·安十二宮例 |
| chart:lifeMasterName / chart:bodyMasterName | xsd:string | chart:LifeMaster∨chart:BodyMaster | 卷二·安命主/安身主 |
| star:starName（紫微/天机/…） | xsd:string（枚举） | star:Star | 卷一·諸星問答論；卷二·安南北斗諸星訣 |
| star:starCategory（主星/辅佐/煞忌/杂曜/流曜） | xsd:string（枚举） | star:Star | 卷一·諸星問答論（分类） |
| star:starAlias（别名，如紫微＝帝座、贪狼＝桃花） | xsd:string | star:Star | 卷一·諸星問答論（化气/异名） |
| star:deityName（博士…官府；流禄/流羊/流陀；斗君；★丧门/白虎/吊客/官府 四飞星；★飞天三杀 奏书/将军/直符） | xsd:string（枚举） | star:YearlyDeity（含 FourFlyingStar/FlyingTriSha） | 卷二·博士/力士/青龍…诸条；安流祿流羊流陀訣；安斗君訣；★安喪門白虎吊客官府四飛星訣；★安飛天三殺訣 |
| layout:placementPalace（落宫） | xsd:string | layout:StarPlacement | 卷二·各安星诀 |
| layout:transformationType（化禄/化权/化科/化忌） | xsd:string（枚举） | layout:Transformation | 卷一·諸星問答論·問化祿/化權/化科/化忌星所主若何 |
| layout:transformationOrigin（生年干/宫干/流年干） | xsd:string（枚举） | layout:Transformation | 卷二（四化安法）；【宫干飞化为飞星派，待考·中】 |
| state:brightnessName（庙/旺/得地/利益/平和/不得地/陷） | xsd:string（枚举） | state:Brightness | 卷二·星辰屬性诸条；卷三·诸星庙陷 |
| state:characterName（尊/善/贵/财/福/囚/令/富/桃花/暗/印/荫/权/耗） | xsd:string（枚举） | state:StarCharacter | 卷一·諸星問答論（化气） |
| state:phaseRelationType（生/克/制/化） | xsd:string（枚举） | state:PhaseRelation | 卷二·論星辰生剋制化 |
| state:palaceStrengthLevel（强/弱/落闲） | xsd:string（枚举） | state:PalaceStrength | 卷二·定十二宮弱強/定十二宮星辰落閑 |
| palace:palaceMeaningText（宫义文本） | xsd:string | palace:PalaceMeaning | 卷一·斗數骨髓賦；卷二·安十二宮例 |
| palace:sanFangPosition（本宫/对宫/三合） | xsd:string（枚举） | palace:SanFangSiZheng | 卷一·太微賦 |
| palace:adjoiningType（紫府夹/日月夹/昌曲夹/羊陀夹/火铃夹/魁钺夹/左右夹/劫空夹） | xsd:string（枚举） | palace:Adjoining | 卷三·諸格断语 |
| palace:emptyPalaceRule（借星/借光/空宫格局） | xsd:string | palace:EmptyPalace | 【后世通行规则，待考·中】 |
| pattern:patternName（科权禄主格/左右朝垣格/文星朝命格/紫府朝垣/君臣庆会/…） | xsd:string（枚举） | pattern:StarPattern | 卷一·諸格论；卷三·諸格断语 |
| pattern:patternGradeName（贵/富/平常/贫贱 等第；★十等枚举） | xsd:string（枚举） | pattern:PatternGrade | 卷三·論人命入格/論格星數高下；★卷一·定富貴貧賤十等論 |
| pattern:structureType（富局/贵局/贫贱局/杂局）〔★新增〕 | xsd:string（枚举） | pattern:SpecialStructure | ★卷一·定富局/定貴局/定貧賤局/定雜局 |
| pattern:qualificationType（得地合格/失陷破格/得地富贵/失陷贫贱）〔★新增〕 | xsd:string（枚举） | pattern:PalaceStarQualification | ★卷一·十二宮諸星得地合格訣/失陷破格訣/得地富貴論/失陷貧賤論 |
| pattern:combinationType（双星同宫/三星四正/三方四正复合） | xsd:string（枚举） | pattern:StarCombination | 卷一·諸格论；卷三·諸格断语 |
| pattern:starMeaningText（星情断语文本） | xsd:string | pattern:StarMeaning | 卷一·諸星問答論（诸星所主） |
| pattern:classicTextName（羅序/太微赋/形性赋/星垣论/斗数准绳/斗数发微论/重补斗数彀率/增补太微赋/骨髓赋/女命骨髓赋） | xsd:string（枚举） | pattern:ClassicText | 卷一卷首诸赋 + ★羅序 |
| fortune:limitRange（大限所辖年龄区间） | xsd:string | fortune:GreatLimit | 卷二·安大限訣（按五行局起限） |
| fortune:limitDirection（顺行/逆行） | xsd:string（枚举） | fortune:GreatLimit | 卷二·安大限訣（阳男阴女顺/阴男阳女逆） |
| fortune:smallLimitBranch（小限落宫地支） | xsd:string | fortune:SmallLimit | 卷二（安小限） |
| fortune:annualGanzhi（流年干支） | xsd:string | fortune:AnnualFortune | 卷三·論流年太歲吉凶星殺 |
| fortune:recalcDeityType（流禄/流羊/流陀/流年十二神/斗君） | xsd:string（枚举） | fortune:RecalculatedDeity | 卷二·安流祿流羊流陀訣/安斗君訣 |
| fortune:triggeredPatternType（羊陀迭并/七杀重逢） | xsd:string（枚举） | fortune:TriggeredPattern | 卷三·論羊陀迭並/論七殺重逢 |
| fortune:tabooBranch（大小限所忌地支）〔★新增〕 | xsd:string（枚举） | fortune:LimitTaboo | ★卷三·論大小限星辰過十二宮遇十二支所忌訣 |
| fortune:overlayLevel（本命↔大限↔流年） | xsd:string（枚举） | fortune:OverlayReading | 卷三·論大限十年禍福/論二限太歲吉凶；【待考·中】 |
| fortune:limitSongText（行限宫歌文本） | xsd:string | fortune:LimitSong | 卷三·論立命行限宮歌 |
| fortune:flyingDirection（化入/化出） | xsd:string（枚举） | fortune:FlyingTransformation | 【飞星派，全書未载，待考·中】 |
| reading:readingText（断语文本） | xsd:string | reading:ReadingItem | 卷三·談星要論；卷一卷三各断语篇 |
| reading:readingCategory（性情/六亲/婚姻/事业/财帛/疾病/寿夭/男女/女命/小儿/劝诫） | xsd:string（枚举） | reading:ReadingItem | 对应各断语篇 |
| reading:diseaseOrgan（脏腑／症象） | xsd:string | reading:Disease | 卷一·斗數骨髓賦；卷三·談星要論 |
| reading:longevityType（寿夭/残疾/破相/聪明/富足/贫贱/孤克/盗贼/一身驳杂/出家/商贾/艺术）〔★枚举扩充〕 | xsd:string（枚举） | reading:Longevity | 卷一·論壽夭淫蕩/論定人殘疾/破相/聰明/富足/貧賤/作盜賊/一身駁雜/論人命內犯孤剋者/論出家僧道之命/論有無商賈之命/論有無藝術者 |
| reading:careerRank（公卿/两官府/文官/武官/曹吏）〔★新增〕 | xsd:string（枚举） | reading:Career | ★卷二·定公卿/定兩官府/定文官/定武官/定曹吏 |
| reading:wealthTrend（先贫后富/先富后贫）〔★新增〕 | xsd:string（枚举） | reading:Wealth | ★卷三·論命先貧後富 |
| reading:conclusionText（命书结论） | xsd:string | reading:ReadingConclusion | 流程节点 16 |
| reading:structuredSchema（JSON Schema 名） | xsd:string | reading:StructuredOutput | 顶层设计 K·66 |
| reading:chartGraphicRule（命盘布局/图示约定） | xsd:string | reading:ChartGraphic | 顶层设计 K·65 |
| reading:caseBirthData（命例生辰·脱敏） | xsd:string | reading:CaseRecord | 客户裁定 ㉗ |
| reading:caseResult（命例批断结果） | xsd:string | reading:CaseRecord | 顶层设计 K·56 |
| rules:alertType（大凶/寿元关/重大疾病） | xsd:string（枚举） | rules:AlertFlag | 顶层设计（消息外发）；客户裁定 ㉖ |
| rules:alertTriggered（是否命中） | xsd:boolean | rules:AlertFlag | 流程节点 A（SWRL 命中即外发） |
| diagnosis:sourceBook（书名） | xsd:string | diagnosis:SourceNote | ★铁律 9/10 |
| diagnosis:sourceVolume（卷/篇） | xsd:string | diagnosis:SourceNote | ★铁律 9/10 |
| diagnosis:variantTrack（三合轨/飞星轨） | xsd:string（枚举） | diagnosis:SchoolVariant | 顶层设计 §2.4 |
| diagnosis:variantText（分歧判据文本） | xsd:string | diagnosis:SchoolVariant | 顶层设计 §2.4；卷一·諸格论 |
| diagnosis:toBeVerifiedNote（【待考·轻/中/重】说明） | xsd:string | diagnosis:ToBeVerified | ★铁律 8/9/10 |
| diagnosis:verificationNote（定盘说明） | xsd:string | diagnosis:ChartVerification | 【验盘，待考·中】（I·61） |
| diagnosis:candidateHours（候补时辰集） | xsd:string | diagnosis:ChartVerification | 【验盘，待考·中】（客户裁定 ⑥） |

---

## 4. 约束（`owl:Restriction`）

> **硬性**：约束必须写成 `owl:Restriction`，**不得写在注释里**（★12）；属性「值/约束」不混用；数值属性用数值类型。

| 类 | 属性 | 约束 | 出处 |
|---|---|---|---|
| chart:NatalChart | chart:hasBirthInput | `owl:cardinality 1` | 流程节点 1 |
| chart:NatalChart | chart:hasPalace | `owl:cardinality 12`（十二宫必足） | 卷二·安十二宮例 |
| chart:NatalChart | chart:hasLifePalace | `owl:cardinality 1`（命宫唯一） | 卷二·安身命例 |
| chart:NatalChart | chart:hasBodyPalace | `owl:cardinality 1`（身宫唯一） | 卷二·安身命例 |
| chart:NatalChart | chart:hasBureau | `owl:cardinality 1`（五行局唯一） | 卷二·起五行寅例 |
| chart:NatalChart | chart:hasLifeMaster | `owl:cardinality 1` | 卷二·安命主 |
| chart:NatalChart | chart:hasBodyMaster | `owl:cardinality 1` | 卷二·安身主 |
| chart:Palace | chart:hasBranch | `owl:cardinality 1`（一宫一支） | 卷二·安十二宮例 |
| chart:Palace | chart:hasPalaceStem | `owl:cardinality 1`（一宫一干） | 卷二（推算宫干）【待考·中】 |
| core:HeavenlyStem | core:hasYinYang | `owl:cardinality 1` | 命理通例 |
| core:HeavenlyStem | core:hasFivePhase | `owl:cardinality 1` | 卷二·星辰屬性诸条 |
| core:EarthlyBranch | core:hasFivePhase | `owl:cardinality 1` | 卷二·星辰屬性诸条 |
| core:SexagenaryCycle | core:hasStem / hasBranch / hasNaYin | 各 `owl:cardinality 1` | 卷二·六十花甲子納音歌 |
| core:FivePhaseBureau | core:hasTwelveStage | `owl:cardinality 12`（局定长生布十二宫） | 卷二·十二长生 |
| star:Star | star:starName | `owl:cardinality 1` | 卷二·安南北斗諸星訣 |
| star:FourteenMainStar | state:hasStarCharacter | `owl:cardinality 1`（一主星一化气·静态） | 卷一·諸星問答論 |
| layout:StarPlacement | layout:placedIn | `owl:cardinality 1`（一星位唯一宫） | 卷二·各安星诀 |
| layout:StarPlacement | state:hasBrightness | `owl:maxCardinality 1`（一星位至多一庙陷；**与宫相关，按来源优先级**） | 卷二·星辰屬性；卷三·诸星庙陷；I·63 |
| layout:Transformation | layout:transformationType | `owl:cardinality 1` | 卷一·諸星問答論·四化 |
| layout:Transformation | layout:transformsTo | `owl:cardinality 1`（一化唯一星） | 卷一·諸星問答論·四化 |
| chart:Palace | palace:oppositeOf | `owl:cardinality 1`（对宫唯一） | 卷二·定十二宮弱強 |
| chart:Palace | palace:hasSanFang | `owl:cardinality 1`（三方四正唯一组合） | 卷一·太微賦 |
| pattern:StarPattern | pattern:patternName | `owl:cardinality 1` | 卷一·諸格论 |
| pattern:SpecialStructure | pattern:structureType | `owl:cardinality 1`（一局一类型） | ★卷一·定富局/定貴局/定貧賤局/定雜局 |
| pattern:PalaceStarQualification | pattern:qualificationType | `owl:cardinality 1` | ★卷一·十二宮諸星得地/失陷 四论 |
| pattern:PatternGrade | pattern:patternGradeName | `owl:cardinality 1` | 卷三·論格星數高下；★卷一·定富貴貧賤十等論 |
| chart:NatalChart | pattern:hasPatternGrade | `owl:maxCardinality 1`（命格等第唯一；余格备参） | 卷三·論格星數高下；客户裁定 ⑱ |
| fortune:GreatLimit | fortune:limitDirection | `owl:cardinality 1`（顺/逆唯一） | 卷二·安大限訣 |
| fortune:GreatLimit | fortune:limitRange | `owl:cardinality 1`（所辖年龄区间唯一） | 卷二·安大限訣 |
| fortune:AnnualFortune | fortune:annualGanzhi | `owl:cardinality 1` | 卷三·論流年太歲吉凶星殺 |
| fortune:LimitTaboo | fortune:tabooBranch | `owl:cardinality 1` | ★卷三·論大小限星辰過十二宮遇十二支所忌訣 |
| chart:NatalChart | fortune:hasGreatLimit | `owl:maxCardinality 12`（大限每宫一限） | 卷二·安大限訣 |
| chart:NatalChart | reading:hasConclusion | `owl:cardinality 1`（综合结论唯一） | 流程节点 16 |
| chart:NatalChart | reading:hasStructuredOutput | `owl:cardinality 1`（结构化输出唯一） | 顶层设计 K·66 |
| chart:NatalChart | diagnosis:hasChartVerification | `owl:maxCardinality 1` | 【验盘】（I·61） |
| rules:AlertFlag | rules:alertType | `owl:cardinality 1` | 顶层设计（消息外发）；客户裁定 ㉖ |
| reading:CaseRecord | reading:caseBirthData | `owl:cardinality 1`（命例必有生辰） | 客户裁定 ㉗ |
| diagnosis:SourceNote | diagnosis:sourceBook / sourceVolume | 各 `owl:cardinality 1`（出处必有书名·卷篇） | ★铁律 9/10 |

---

## 5. SWRL 规则

> **分工**（SKILL §3.2）：**简单判定/分类用 SWRL**；**复杂逻辑/数值计算留给程序**。所引谓词均在 §2/§3 有定义（自洽）。变量命名沿用 Ontop/Openllet 约定。**编写须避开易错点**（见文末「SWRL 编写要点」）。
> **不该编码为 SWRL 的**（跨方案比较、闭世界否定、历法/安星数值计算）在表中显式注明「**由程序实现**」及理由。

| 规则 | 条件（body） | 结论（head） | 出处 |
|---|---|---|---|
| R1 干支→阴阳/五行传导 | `core:HeavenlyStem(?s) ∧ core:stemFivePhase(?s, ?p)` / `core:stemYinYang(?s, ?y)` | → `core:hasFivePhase(?s, ?p) ∧ core:hasYinYang(?s, ?y)` | 卷二·星辰屬性诸条（干支属） |
| R2 五行相生（生） | `core:FivePhase(?a) ∧ core:FivePhase(?b) ∧ core:generates(?a, ?b)` | → （传递闭包，供星辰生克制化取用） | 卷二·論星辰生剋制化 |
| R3 五行相克（克） | `core:FivePhase(?a) ∧ core:FivePhase(?b) ∧ core:overcomes(?a, ?b)` | → （传递闭包） | 卷二·論星辰生剋制化 |
| R4 安星落宫赋值 | `star:Star(?st) ∧ layout:placementPalace(?st, ?plc)`（经 `starLayoutMap` 内置表/OBDA：年干/年支/生月/生时/五行局+生日 → 宫） | → `layout:placedIn(?st, ?plc)`（星落宫） | 卷二·安南北斗諸星訣/安文昌文曲星訣 等。**由程序/OBDA 查表**（安星表庞大，避免逐星冗余） |
| R5 五行局→紫微起宫（定位表） | `core:FivePhaseBureau(?ju) ∧ chart:birthDay(?c, ?d)`（经 `starLayoutMap`：金四局初一在亥…） | → 紫微落宫（供 R4 展开） | 卷二·安南北斗諸星訣。**由程序/查表**（紫微定位表） |
| R6 庙陷判定 | `layout:StarPlacement(?sp) ∧ ... 命中 brightnessMap（星×宫）` | → `state:hasBrightness(?sp, ?b)` | 卷二·星辰屬性；卷三·诸星庙陷。**查表由程序/OBDA**；冲突按 I·63 来源优先级并列 |
| R7 四化落宫（生年干） | `layout:Transformation(?t) ∧ layout:transformationOrigin(?t,"生年干") ∧ ... 经 sihuaMap（干→化星）` | → `layout:transformsTo(?t, ?st) ∧ layout:hasTransformation(?f, ?t)` | 卷一·諸星問答論·四化；卷二（四化安法）。**查表由程序/OBDA** |
| R8 大限起限顺逆 | `chart:NatalChart(?f) ∧ chart:gender(?i,"男") ∧ chart:yearStemYinYang(?f,"阳")` | → `fortune:limitDirection(?gl,"顺")`（阳男阴女顺） | 卷二·安大限訣（阳男阴女从命前一宫顺行） |
| R8b 大限起限顺逆（阴男） | `... gender(?i,"男") ∧ yearStemYinYang(?f,"阴")` | → `fortune:limitDirection(?gl,"逆")`（阴男阳女逆） | 卷二·安大限訣（阴男阳女从命后一宫逆行） |
| R9 三方四正取宫 | `chart:Palace(?p) ∧ chart:hasBranch(?p, ?b) ∧ ... 对宫/三合二宫` | → `palace:hasSanFang(?p, ?s) ∧ palace:oppositeOf(?p, ?op)` | 卷一·太微賦（三方四正）。**取宫算法由程序**（宫序变换），SWRL 承接断言 |
| R10 夹宫判定 | `chart:Palace(?p) ∧ palace:adjacentWith(?p, ?q) ∧ ... 两邻宫星曜组合命中（紫府/日月/昌曲/羊陀/火铃/魁钺/左右/劫空）` | → `palace:adjoiningType(?p, ?ty)` | 卷三·諸格断语（夹命诸格） |
| R11 空宫借星 | `chart:Palace(?p) ∧ chart:hasStar(?p, none) ∧ palace:oppositeOf(?p, ?op) ∧ ... 对宫有主星` | → `palace:borrowsFrom(?p, ?op) ∧ palace:emptyPalaceRule(?p,"借星")` | 卷二·安十二宮例（空宫）；**采通行借星+借光规则，待考·中**（I·62） |
| R12 成格判定·分类 | `pattern:StarPattern(?pt) ∧ pattern:patternName(?pt, ?n) ∧ ... 成格条件命中` | → `pattern:hasStarPattern(?f, ?pt)`（成格/破格分类） | 卷一·諸格论；卷三·諸格断语。**简单分类用 SWRL，组合成格综合由程序** |
| R13 命格高低·等第 | `chart:NatalChart(?f) ∧ ... 星数高下 + 成格质量` | → `pattern:hasPatternGrade(?f, ?g)` | 卷三·論人命入格/論格星數高下。**综合评级由程序计算**（客户裁定 ⑱ 取高格余格备参） |
| R14 十二长生顺逆 | `chart:NatalChart(?f) ∧ chart:gender(?i,"男") ∧ chart:yearStemYinYang(?f,"阳")` | → 十二长生顺布（男顺）/ 逆布（女逆） | 卷二·十二长生（男命顺数、女命逆数） |
| R15 流年流曜重算 | `fortune:AnnualFortune(?y) ∧ fortune:annualGanzhi(?y, ?gz) ∧ ... 经 deityRuleMap（流禄/流羊/流陀/流年十二神/斗君）` | → `fortune:hasRecalculatedDeity(?y, ?d)` | 卷二·安流祿流羊流陀訣/安斗君訣。**查表由程序/OBDA**（**不得沿用出生年流曜**） |
| R16 叠宫断法·三盘叠加 | `fortune:OverlayReading(?o) ∧ fortune:hasGreatLimit(?f,?gl) ∧ fortune:hasAnnualFortune(?f,?y) ∧ ... 宫位叠合` | → `fortune:overlayReading(?y, ?o)` | 卷三·論大限十年禍福/論二限太歲吉凶。**叠合编排由程序**；【后世通行断法，待考·中】 |
| R17 凶格引动·羊陀迭并/七杀重逢 | `fortune:TriggeredPattern(?tp) ∧ ... 限内羊陀/七杀叠合命中` | → `fortune:hasTriggeredPattern(?y, ?tp)` | 卷三·論羊陀迭並/論七殺重逢 |
| R18 **告警·大凶** | `chart:NatalChart(?f) ∧ ... 限内羊陀火铃空劫忌凑合 / 恶格引动`（★简化判据） | → `rules:hasAlertFlag(?f, rules:AlertDaXiong)` | 顶层设计（消息外发）；客户裁定 ㉖。**「大凶」综合判据由程序编排**（多因子），SWRL 只落最终标志 |
| R19 **告警·寿元关** | `chart:NatalChart(?f) ∧ ... 倒限 / 寿星受制` | → `rules:hasAlertFlag(?f, rules:AlertShouYuanGuan)` | 顶层设计（消息外发）；客户裁定 ㉖ |
| R20 **告警·重大疾病** | `chart:NatalChart(?f) ∧ reading:hasDisease(?f, ?d) ∧ ... 疾厄宫恶曜凑合` | → `rules:hasAlertFlag(?f, rules:AlertJiBing)` | 卷一·斗數骨髓賦；卷三·談星要論；客户裁定 ㉖ |
| R21 **告警·命中即外发** | `chart:NatalChart(?f) ∧ rules:hasAlertFlag(?f, ?a)` | → `rules:alertTriggered(?a, true)`（供 `SwrlRuleTriggerListener` 检测 → RabbitMQ 外发） | 流程节点 A；顶层设计 §1（规则驱动事件） |
| R22 出处绑定 | `core:ZiWeiDouShuEntity(?x) ∧ diagnosis:sourceBook(?n, ?bk) …`（每条判定断言挂对应 `SourceNote`） | → `diagnosis:hasSourceNote(?x, ?n)` | ★铁律 9/10；顶层设计 §2.1(B)·S3。**由程序在生成期绑定**（非推理） |
| R23 ★定富局/定贵局成格 | `pattern:SpecialStructure(?s) ∧ pattern:structureType(?s,"贵局") ∧ ... 组成条件命中（日月夹命/日出扶桑/月落亥宫/月生沧海/辅弼拱主/君臣庆会/财印夹禄/禄马佩印/坐贵向贵/马头带剑 十格新录 + 七杀朝斗/日月并明/明珠出海/日月同临/刑囚夹印/科权禄拱/贪火相逢/武曲守垣/府相朝垣/紫府朝垣/文星暗拱/权禄生逢/羊刃入庙/巨机居卯/明禄暗禄/科明暗禄/金舆扶驾）` | → `pattern:hasSpecialStructure(?f, ?s)`；并落 `pattern:StarPattern` | ★卷一·定貴局（**27 格**，据 `c2` 行 604–631）/定富局/定貧賤局/定雜局。**简单成格分类用 SWRL，多格综合由程序** |
| R24 ★大小限过十二支所忌 | `fortune:AnnualFortune(?y) ∧ fortune:annualGanzhi(?y, ?gz) ∧ ... 限过某支命中所忌` | → `fortune:hasLimitTaboo(?y, ?t)`（`fortune:tabooBranch`） | ★卷三·論大小限星辰過十二宮遇十二支所忌訣。**所忌表查取由程序/OBDA** |
| R25 ★行限到宫入庙/不入庙 | `fortune:AnnualFortune(?y) ∧ ... 太岁并小限到宫之庙陷` | → `fortune:hasLimitSong(?y, ?s)`（入庙化吉/不入庙化凶/所值吉凶星） | ★卷三·論立命行限宮歌 + 論諸星同垣各司所宜分別富貴貧賤夭壽（庙陷定位）。**庙陷查表由程序** |

> **说明**：
> - R1~R3 为干支/五行的**基础传导**；R4/R5/R6/R7/R15 依赖**内置映射表 / OBDA 查表**（安星、紫微定位、庙陷、四化、流曜起例属**查表/计算**，★铁律 13 → 外置）。
> - R8/R8b/R14 落实**大限顺逆**（阳男阴女顺、阴男阳女逆）与**十二长生男顺女逆**（据卷二·安大限訣、卷二·十二长生）。
> - **化气（静态）不设 SWRL 推导**：`state:hasStarCharacter` 为星曜固有属性，直接以 ABox 断言表达（R? 无），与动态四化（R7）分列。
> - R18~R21 为**告警标志推导**：SWRL 命中 → `rules:alertTriggered=true` → `SwrlRuleTriggerListener` → RabbitMQ（流程节点 A）；**不设人为数值阈值**（客户裁定 ㉖）。
> - **明确「由程序实现」**（不属于 SWRL）：① 农历/干支/节气/真太阳时换算出四柱（★铁律 13）；② 安星表 / 紫微定位表 / 庙陷表 / 四化表 / 流曜起例的大表查取；③ 三方四正/夹宫/叠宫的取宫与叠合编排；④ 多格并见的「取高格」与流派「主取」编排；⑤ 命格高低/大凶的综合评级（多因子加权）；⑥ 闭世界否定（如「空宫无主星」）。理由：**跨方案比较、闭世界否定、数值计算**均不适合 SWRL。

---

## 6. 关系与约束的一致性检查要点

1. **对象属性互逆配对**：`hasYinYang/isYinYangOf`、`hasFivePhase/isFivePhaseOf`、`generates/generatedBy`、`overcomes/overcomeBy`、`hasStem/isStemOf`、`hasBranch/isBranchOf`、`hasNaYin/isNaYinOf`、`hasBureau/isBureauOf`、`hasTwelveStage/isTwelveStageOf`、`hasBirthInput/isBirthInputOf`、`hasPalace/isPalaceOf`、`hasLifePalace/isLifePalaceOf`、`hasBodyPalace/isBodyPalaceOf`、`hasPalaceStem/isPalaceStemOf`、`hasLifeMaster/isLifeMasterOf`、`hasBodyMaster/isBodyMasterOf`、`placedIn/hostsStar`、`hasTransformation/isTransformationOf`、`transformsTo/isTransformedFrom`、`hasBrightness/isBrightnessOf`、`hasStarCharacter/isStarCharacterOf`、`hasPalaceMeaning/isPalaceMeaningOf`、`hasSanFang/isSanFangOf`、`borrowsFrom/lendsTo`、`hasStarMeaning/isStarMeaningOf`、`hasStarPattern/isStarPatternOf`、`hasStarCombination/isStarCombinationOf`、`hasPatternGrade/isPatternGradeOf`、`hasGreatLimit/isGreatLimitOf`、`hasSmallLimit/isSmallLimitOf`、`hasAnnualFortune/isAnnualFortuneOf`、`hasRecalculatedDeity/isRecalculatedDeityOf`、`hasTriggeredPattern/isTriggeredPatternOf`、`hasLimitSong/isLimitSongOf`、`hasFlyingTransformation/isFlyingTransformationOf`、`hasReading/isReadingOf`、`hasConclusion/isConclusionOf`、`hasStructuredOutput/isStructuredOutputOf`、`hasCaseRecord/isCaseRecordOf`、`hasSourceNote/isSourceNoteOf`、`hasSchoolVariant/isSchoolVariantOf`、`hasToBeVerified/isToBeVerifiedOf`、`hasChartVerification/isChartVerificationOf` 均须互逆声明；`palace:oppositeOf`/`tripleWith`/`adjacentWith` 须声明 `owl:SymmetricProperty`；`core:generates`/`core:overcomes` 须声明 `owl:TransitiveProperty`（**注意**：五行生克为循环，传递闭包须受控，推理时避免跨环爆炸）。
2. **基数闭合**：`NatalChart` 十二宫恰 12、命身宫/五行局/命主身主各恰 1；`Palace` 干/支各恰 1；`SexagenaryCycle` 干/支/纳音各恰 1；`FivePhaseBureau` 布十二长生恰 12；`FourteenMainStar` 化气恰 1；`StarPlacement` 落宫恰 1、庙陷至多 1；`Transformation` 类型/目标星各恰 1；`GreatLimit` 方向/区间各恰 1；`NatalChart` 综合结论/结构化输出各恰 1；`SourceNote` 书名/卷篇各恰 1。
3. **值域/定义域闭合**：`hasFivePhase` 值域仅 `core:FivePhase` 5 子类；`hasYinYang` 值域仅 `core:YinYang` 2 子类；`hasBureau` 值域仅 `core:FivePhaseBureau` 5 子类；`hasBrightness` 值域仅 `state:Brightness` 7 子类；`hasStarCharacter` 值域仅 `state:StarCharacter`；`transformsTo` 值域仅 `star:Star`；`generates`/`overcomes` 定义域与值域均为 `FivePhase`；`hasAlertFlag` 值域仅 `rules:AlertFlag` 枚举；`hasSourceNote`/`hasSchoolVariant`/`hasToBeVerified` 定义域为 `core:ZiWeiDouShuEntity`（全实体可挂出处）。
4. **SWRL 与 Restriction 一致**：R8/R8b 推出的 `limitDirection` 不得与 `cardinality 1` 冲突（顺/逆互斥）；R7 推出的 `hasTransformation` 须满足 `transformationType cardinality 1`；R18~R20 的 `AlertFlag` 与 `alertType cardinality 1` 对应；R13 的 `hasPatternGrade` 须满足 `maxCardinality 1`（余格挂 `SchoolVariant`/备参，不落主值）。
5. **★两类「化」严格分列**：`state:StarCharacter`（化气·静态，星曜固有）与 `layout:Transformation`（四化·动态，干引动）**分属 `state:`/`layout:` 不同命名空间与不同类**；`state:hasStarCharacter` 与 `layout:hasTransformation` 定义域/语义互不重叠，**全链不得混指**（顶层设计 §1；流程设计 §3）。
6. **三盘粒度并存不矛盾**：`chart:NatalChart`（本命盘，恒定）与 `fortune:GreatLimit`/`SmallLimit`/`AnnualFortune`（大限/小限/流年，可变）分属 `chart:`/`fortune:`，不得混指；叠宫（R16）以 `OverlayReading` 显式承载。
7. **流曜基准与重算分列**：出生年太岁流曜（`star:YearlyDeity` 作基准，节点 5）与流年重算流曜（`fortune:RecalculatedDeity`，节点 14，R15）分属不同类，**不得沿用出生年流曜**（流程设计 §3.3）。
8. **无硬编码业务数据**：五行生克、星曜名录、十二宫义、化气、庙陷等级、格局名录、告警三类、干支/宫位配属等以**个体/子类/约束**表达，程序不含分支常量（★12/14）。
9. **出处全覆盖**：每条类/属性/约束/SWRL 的「出处」列非空；无法定位者标【待考·轻/中/重】并建 `diagnosis:ToBeVerified` 个体（★铁律 8/9/10）。
10. **告警外发链闭合**：`rules:hasAlertFlag` → R21 `rules:alertTriggered=true` → `SwrlRuleTriggerListener` → RabbitMQ；仅三类（大凶/寿元关/重大疾病），无数值阈值（客户裁定 ㉖）。

---

## 7. ABox 典型个体设计（落地于第 6 步 · 闸门⑦）

> 登记第 6 步单文件本体拟纳入的**典型 ABox 数据**（个体集/类型/关键断言/代表性），交业务确认后方可进入第 7 步（闸门⑦）。**全集扩展留待第 12 步（闸门⑪）**。

| 个体 | 类型 | 关键断言 | 说明（出处） |
|---|---|---|---|
| `core:Yang` / `core:Yin` | core:YinYang | yinYangName | 阴阳 2 个体（命理通例） |
| `core:Jin` … `core:Tu` | core:FivePhase | phaseName；`core:generates`/`overcomes` 生成/克各 5 对 | 五行 5 + 生克 10（卷二·論星辰生剋制化） |
| `core:Stem_Jia` … `core:Stem_Gui` | core:HeavenlyStem | stemName；stemYinYang；hasYinYang；hasFivePhase | 十天干 10（卷二·星辰屬性） |
| `core:Branch_Zi` … `core:Branch_Hai` | core:EarthlyBranch | branchName；branchAnimal；branchOrder；hasFivePhase | 十二地支 12（卷二·安十二宮例） |
| `core:Ju_Shui2` … `core:Ju_Huo6` | core:FivePhaseBureau | bureauName；bureauNumber；stageStartBranch；hasTwelveStage | 五行局 5（卷二·起五行寅例） |
| `core:Stage_ChangSheng` … `core:Stage_Yang` | core:TwelveStage | stageName | 十二长生 12（卷二·十二长生） |
| `core:JiaZi` … `core:GuiHai`（**代表性**：甲子/丙寅/戊辰等 6 例，全量留第 12 步） | core:SexagenaryCycle | hasStem；hasBranch；hasNaYin；hasBureau | 六十甲子代表性（卷二·六十花甲子納音歌） |
| `core:NaYin_HaiZhongJin` …（**代表性** 6 名） | core:NaYin | naYinName | 纳音代表性（卷二·六十花甲子納音歌） |
| `calendar:Month_Yin` … `calendar:Month_Chou`（月建）| calendar:LeapMonth 等 | — | 月建/闰月（卷二·安身命例） |
| `star:ZiWei`/`star:TianJi`/`star:TaiYang` … `star:PoJun` | star:FourteenMainStar（`BeiDouStar`/`NanDouStar`） | starName；starCategory；isBeiDou；**state:hasStarCharacter**（化气·静态） | 十四主星 14（卷一·諸星問答論；卷二·安南北斗諸星訣） |
| `star:ZuoFu`/`star:YouBi`/`star:WenChang`/`star:WenQu`/`star:TianKui`/`star:TianYue`/`star:TianMa`/`star:LuCun` | star:AuxiliaryStar | starName；starAlias | 辅佐星 8（卷一·諸星問答論；卷二·安文昌文曲星訣 等） |
| `star:QingYang`/`star:TuoLuo`/`star:HuoXing`/`star:LingXing`/`star:TianKong`/`star:DiJie`/`star:TianShang`/`star:TianShi` | star:MaleficStar | starName；starAlias（擎羊＝刑、陀罗＝忌） | 煞忌星 8（卷一·諸星問答論；卷二·安擎羊陀羅二星訣 等） |
| `star:SanTai`/`star:BaZuo`/`star:LongChi`/`star:FengGe`/…（**代表性**：红鸾/天喜/天刑/天姚/天哭/天虚 等） | star:MiscStar | starName | 杂曜代表性（卷二·安三台八座二星訣 等） |
| `star:Deity_Boshi` … `star:Deity_Guanfu`（十二神）| star:YearlyDeity | deityName | 太岁诸神 12（卷二·博士/力士/青龍…诸条） |
| `state:Char_ZiWei` … `state:Char_PoJun` | state:StarCharacter | characterName（尊/善/贵/财/福/囚/令/富/桃花/暗/印/荫/权/耗） | 十四主星化气（**静态**）（卷一·諸星問答論） |
| `layout:Sihua_Lu`/`Sihua_Quan`/`Sihua_Ke`/`Sihua_Ji` | layout:Transformation | transformationType；transformationOrigin | 四化类型（**动态**）4（卷一·諸星問答論·四化） |
| `palace:Palace_Ming` … `palace:Palace_FuMu` | chart:Palace | palaceName；palaceOrder | 十二宫 12（卷二·安十二宮例） |
| `palace:Meaning_Ming` … `palace:Meaning_FuMu` | palace:PalaceMeaning | palaceMeaningText | 十二宫义 12（卷一·斗數骨髓賦；卷二·安十二宮例） |
| `rules:AlertDaXiong` / `rules:AlertShouYuanGuan` / `rules:AlertJiBing` | rules:AlertFlag | alertType | 告警标志 3（顶层设计（消息外发）；客户裁定 ㉖） |
| `pattern:Structure_FuJu`/`Structure_GuiJu`/`Structure_PinJianJu`/`Structure_ZaJu` | pattern:SpecialStructure | structureType；★关联 StarPattern（贵局 **27** 格） | ★定富/贵/贫贱/杂 局（卷一；★据 `c2` 行 604–631 补正 贵局 11→27） |
| `pattern:Gui_QiShaChaoDou`/`Gui_RiYueBingMing`/`Gui_MingZhuChuHai`/`Gui_RiYueTongLin`/`Gui_KeQuanLuGong`/`Gui_FuXiangChaoYuan`/`Gui_ZiFuChaoYuan`/`Gui_WenXingAnGong`/`Gui_JuJiJuMao`/`Gui_MingLuAnLu`/`Gui_KeMingAnLu`（**贵局代表 11 格**；全集 27 格留第 12 步） | pattern:StarPattern | patternName（七杀朝斗格/日月并明格/…） | ★定贵局格名代表个体（卷一） |
| `pattern:Fu_CaiYinJiaYin`/`Fu_RiYueJiaCai`/`Fu_JinCanGuangHui`（**富局代表 3 格**）；`pattern:Pin_ShengBuFengShi`/`Pin_MaLuoKongWang`/`Pin_RiYueCangHui`（**贫贱局代表 3 格**）；`pattern:Za_FengYunJiHui`/`Za_KuMuFengChun`/`Za_YiJinHuanXiang`（**杂局代表 3 格**）（全集留第 12 步） | pattern:StarPattern | patternName（财荫夹印格/日月夹财格/…） | ★定富/贫贱/杂局格名代表个体（卷一） |
| `pattern:Ge_DuiMianChaoDou`/`Ge_KeQuanLuZhu`/`Ge_ZuoYouChaoYuan`/`Ge_JianWenWu`/`Ge_WenXingChaoMing`/`Ge_ShiZhongYinYu`/`Ge_HuoGui`（**诸格论代表 7 格**） | pattern:StarPattern | patternName（对面朝斗格/科权禄主格/…） | ★卷一·诸格论 19 子目格名代表个体 |
| `pattern:Jia_ZiFuJiaMing`/`Jia_RiYueJiaMing`/`Jia_ChangQuJiaMing`/`Jia_YangTuoJiaJi`/`Jia_HuoLingJiaMing`/`Jia_KuiYueJiaMing`/`Jia_ZuoYouJiaMing`/`Jia_JieKongJiaMing`（**夹宫代表 8 格**） | pattern:StarPattern | patternName（紫府夹命格/日月夹命格/…） | ★卷三·夹宫诸格代表个体 |
| `pattern:Grade_FuShou`/`Grade_CongMing`/`Grade_WeiYong`/`Grade_WenZhi`/`Grade_WuZhi`/`Grade_XingMing`/`Grade_FuGui`/`Grade_PinJian`/`Grade_JiYao`/`Grade_SengDao`（**十等全**） | pattern:PatternGrade | patternGradeName（福寿/聪明/威勇/文职/武职/刑名/富贵/贫贱/疾殀/僧道） | ★定富贵贫贱十等论（卷一） |
| `pattern:Qual_DeDiHeGe`…（**代表性**） | pattern:PalaceStarQualification | qualificationType | ★十二宫诸星得地/失陷 四论（卷一） |
| `fortune:Taboo_Yin`…（**代表性**） | fortune:LimitTaboo | tabooBranch | ★大小限过十二支所忌（卷三） |
| `star:Deity_SangMen`/`BaiHu`/`DiaoKe`/`GuanFu`（四飞星） | star:FourFlyingStar | deityName | ★安丧门白虎吊客官府四飞星诀（卷二） |
| `star:Deity_ZouShu`/`JiangJun`/`ZhiFu`（飞天三杀） | star:FlyingTriSha | deityName | ★安飞天三杀诀（卷二） |
| `diagnosis:SN_J1_01`…`SN_J3_20`（**全篇章索引 89 条**：卷一 23 + 卷二 46 + 卷三 20）+ `SN_J1Q_01`…`SN_J1Q_35`（**諸星問答論·星曜所主子目 35 条**）；合计新增 124、含原代表性 3 = **127** | diagnosis:SourceNote | rdfs:label（卷X·篇名 / 諸星問答論·问XX所主）；sourceBook；sourceVolume | ★**全篇章索引**（出处标注全集，明细枚举层补遗 v1.3）：逐卷逐篇 + 星曜所主子目录入，使本体字面包含《全書》全部篇/诀名与星问（★铁律 9/10） |
| `chart:Case_Demo01`（**典型命例 1~2 例**） | chart:NatalChart | hasBirthInput；hasPalace；hasLifePalace/hasBodyPalace；hasBureau；hasLifeMaster/hasBodyMaster；layout:hasStarPlacement；layout:hasTransformation；pattern:hasStarPattern；reading:hasConclusion；reading:hasCaseRecord | 典型命例（节点 2~17 全链样例）；全集留第 12 步 |
| `chart:Verification_Demo01`（**代表性**：验盘样例） | diagnosis:ChartVerification | verificationNote；candidateHours | 验盘样例（**待考·中**）（I·61） |

> **典型 ABox 规模（规划）**：枚举型个体**全量**（阴阳/五行/天干/地支/五行局/十二长生/十四主星/辅佐/煞忌/太岁诸神/化气/四化类型/十二宫/宫义/告警/出处代表作）+ **代表性**（六十甲子/纳音/杂曜/安星起例/庙陷表/样例命盘）。**全集**留待第 12 步「ABox 扩至全集 / 入库」。
> **闸门⑦**：第 6 步生成的单文件本体中的典型 ABox 须在本节登记并交业务人员确认（个体集/类型/关键断言/代表性）后方可进入第 7 步。留痕见文末「闸门⑦留痕」。

---

## 8. OBDA / SKOS 预留

> ✅ **第 11 步已落地（2026-10-31·闸门⑩通过）**：`ontology/database/` 四件套（`createdb_tables.ddl`/`data.sql`/`ziweidoushu.obda`/`ziweidoushu.properties`）产出；库 `ziweidoushudb` 建成（14 表）；查表型历法个体（天干/地支/六十甲子/纳音/五行局/十二长生/闰月/真太阳时）已自 `ziweidoushu-abox.owl` 迁出、**DB 为唯一真源**；起例表（安星/庙陷/四化/宫干/流曜）取代表性（全集留第 12 步）。`ontop validate` 通过、SPARQL 虚拟取数成功；回归 22/22 全绿（`persistAndReturn` 真实落库）。

- **OBDA（第 11 步 · 闸门⑩）**：**查表型/运行态数据入库 MySQL**，经 Ontop 虚拟访问，**不迁入本体文件**（顶层设计 §1）。规划表（`ontology/database/`）：
  | 规划表 | 承载 | 对应本体 IRI 前缀 |
  |---|---|---|
  | `ganzhi_stem` / `ganzhi_branch` | 天干/地支（名·阴阳·五行·序） | `core:` |
  | `sexagenary_cycle` / `nayin` | 六十甲子/纳音 | `core:` |
  | `bureau_stage` | 五行局 / 十二长生（局→长生所起支 + 十二宫布列） | `core:` |
  | `true_solar_time` / `leap_month` | 真太阳时校正参数 / 闰月归属 | `calendar:` |
  | `star_layout` | **安星起例表**（年干/年支/生月/生时/五行局+生日 → 星落宫）；含紫微定位表 | `layout:` |
  | `brightness_map` | **庙陷表**（星 × 宫 → 庙/旺/得地/利益/平和/不得地/陷） | `state:` |
  | `sihua_map` | 四化表（生年干 → 化禄/权/科/忌 之星） | `layout:` |
  | `palace_stem_map` | 年干起五鼠遁 → 十二宫干 | `chart:` |
  | `deity_rule` | 流曜起例（流禄/流羊/流陀 / 流年十二神 / 斗君 / 太岁杀禄） | `star:` / `fortune:` |
  | `case_record` | 运行态命例记录（`reading:CaseRecord`，**脱敏**） | `reading:` |
  | `alert_flag` | 运行态告警标志 | `rules:` |
  - 映射目标 IRI 以**裸写前缀名**生成（`core:{name}` / `layout:{name}` / ...，**不加尖括号**），与本体实体 IRI 逐字一致；仅**完整 IRI** 才用 `<...>`；`rdfs:label` 用标准前缀写法（**注**：写成 `<core:{name}>` 会被 Ontop 误判为「绝对 IRI」而不展开、致映射 0 命中——参照 SiZhuBaZi 第 11 步已修正经验）。
  - ✍️ **数据边界**：静态术语（阴阳/五行/天干/地支/五行局/十二长生/星曜名录/化气/四化类型/十二宫义/告警标志）留 ABox；**查表型安星/紫微定位/庙陷/四化/宫干/流曜起例/命例运行态**入 MySQL（DB 为唯一真源）。
  - ✅ **全集规模（第 12 步 · 闸门⑪，已落地 2026-10-31）**：天干 10 / 地支 12 / 五行局 5 / 十二长生 12 / **六十甲子 60** / **纳音 30** / 十四主星 14 / 辅佐星 8 / 煞忌星 8 / 杂曜 ~20 / 太岁诸神 12 / 化气 14 / 四化类型 4 / 十二宫 12 / 宫义 12 / **紫微定位表 150**（5局×30日）/ **庙陷表 240**（21星×12宫）/ **流曜起例 42**（流禄10+流羊10+流陀10+十二神12）/ **格局 pattern 81**（贵27/富6/贫贱8/杂8 + 诸格论 + 十等 + 夹宫）；`case_record` / `alert_flag` 为运行态（全集不含历史数据，留空）。数据来源：**AI 依《全書》生成 + 客户（陈希夷）校订**（逐条附出处；卷二·六十花甲子納音歌/安紫微定位棋盘图/安禄存星诀/安擎羊陀罗二星诀/安十二宫太岁杀禄诀/星辰庙陷表，卷一·定富/贵/贫贱/杂局；无明确出处者标【待考】）。DB 侧与 `.owl` 侧**同步补齐**。
- ✅ **SKOS（第 13 步 · 闸门⑥，已落地 2026-10-31）**：产出 `ontology/ziweidoushu-terminology.ttl`（`skos:ConceptScheme` 根 `http://example.org/ziweidoushu/term`）。
  - **规范概念 374 条**（全部取自本体 `rdfs:label`，不凭空造词）：类 158（`owl:Class`）+ 对象属性 76（`owl:ObjectProperty`）+ 数据属性 81（`owl:DatatypeProperty`）+ 星曜个体 59（ABox `star:*`，承载星曜别名）。
  - **标签与关系**：每概念 `skos:prefLabel@zh`+`@en`；`skos:broader` 365 条（子类→父类 / 属性→定义域）；同 zh 名实体（`hasBranch`/`hasBureau`/`LeapMonth` 等 10 项）作 `prefLabel` 消歧（附域后缀）保「一名一概念」。
  - **同义/别名 `skos:altLabel` 20 条，逐条标源**：星曜化气异名（紫微＝帝座、贪狼＝桃花、巨门＝暗、天相＝印、天梁＝荫、擎羊＝刑、陀罗＝忌、破军＝耗〈卷一·諸星問答論〉）；十二宫（交友宫＝仆役宫/奴仆宫〈卷二·安十二宫〉）；六十花甲子/花甲子、纳音五行、十四正曜、五行局、庙旺利陷 等。
  - **OWL↔SKOS 双向链接**：本体侧 12 子本体 + abox 星曜个体携带 `skos:exactMatch → term`（Example 方向，共 374 条）；概念侧携带 `skos:exactMatch → 本体 IRI`。
  - **并入**：`ziweidoushu-tbox.owl` 增 `owl:imports http://example.org/ziweidoushu/term`；`catalog-v001.xml` 登记 `.../term → ziweidoushu-terminology.ttl`。供 `OntologyLabelMatcher`/`SkosSynonymReader` 名称归一（对齐顶层设计 S4、Example §8）。
  - 验证：`ZiWeiDouShuSkosTerminologyTest` 6/6（P-1~P-4 + N-1~N-2）；回归 `AllZiWeiDouShuSuiteTest` 全绿；`validate_structure.py` ERROR=0。

---

## 9. 待业务确认项

| # | 问题 | 说明 | 建议结论（**待确认**·2026-10-31） |
|---|---|---|---|
| 1 | **OutputDomain 命名空间落位** | 顶层设计 §5 有 `OutputDomain` 分支，但 §4.1 命名空间（16 项）/§3 子本体（12 个）均无 `output` | 建议**不新增命名空间**：`ReadingConclusion`/`StructuredOutput`/`ChartGraphic`/`CaseRecord` 归 `reading:`、`AlertFlag` 归 `rules:`（照 Example `LowStock*` 由规则推导先例） |
| 2 | **安星/紫微定位/庙陷/四化/流曜 的落位** | 查表 vs SWRL vs 程序 | ✅**已确认**（2026-10-31 第 5 轮全文核查·陈希夷）：**查表型落 OBDA（MySQL）+ 程序编排**；SWRL 只承接简单判定与最终标志（R18~R21） |
| 3 | **紫微起宫口径（定位表）** | 起寅/起丑异说（A·5【待考·重】） | 已裁定**依《全書》卷二安紫微星定位表/诀**（客户裁定 ⑨）；**由程序查表** |
| 4 | **大凶/寿元关/重大疾病 判据** | 是否设数值阈值？ | 建议**不设阈值**，SWRL 命中即标志（客户裁定 ㉖）；综合判据由程序编排（R18~R20） |
| 5 | **飞星派「化入化出」建模** | I·53/I·60，全書未载 | 建议**建 `fortune:FlyingTransformation` 并标【待考·中】**；三合轨/飞星轨经 `diagnosis:SchoolVariant` 双轨并陈（客户裁定 ⑲） |
| 6 | **借星/借光/空宫格局口径** | I·62，全書未载 | 建议**采后世通行借星+借光规则并标【待考·中】**（客户裁定 ⑰） |
| 7 | **三盘叠加（叠宫断法）口径** | I·51，全書未单列 | 建议**采后世通行三盘叠加断法并标【待考·中】**（客户裁定 ㉔） |
| 8 | **验盘（候补时辰/定盘）口径** | I·61，全書未系统论述 | 建议**采实务通行法**（多维比对加权），标【待考·中】；**回流轮次上限 5**（客户裁定 ⑥㉛） |
| 9 | **命例隐私口径** | 生辰/命盘/批断结果 | 建议**脱敏、仅存必要字段**（客户裁定 ㉗） |
| 10 | **参照典籍（J·55）是否纳入** | 补《全書》未备之安星细节与断法 | 建议**暂缓**（本轮先以《全書》为主）；留待有文本后定（客户裁定 ㉘） |
| 11 | **《全書》考据修正回填顶层设计** | 逐节点确认已裁定：十二长生（#58）、十四主星化气（#57）、夹宫诸格（#64） | 建议**回填顶层设计 #57/#58/#64**（须客户确认后执行）；本核心设计已按修正后口径设计 |
| 12 | **安星起例表 / 庙陷表是否入库** | 规模较大 | ✅**已确认**（2026-10-31 第 5 轮全文核查·陈希夷）：**入库 MySQL**（查表型），**本体仅存概念占位、不建冗余个体**（★铁律 13/14）；残余「未逐字落本体」内容经裁定**维持外置/代表性承接**，判定核查收敛 |
| 13 | **★定富局/定贵局/定贫贱局/定杂局 是否建 `pattern:SpecialStructure` 类？** | 卷一·四「定局」篇；富/贵/贫贱/杂 四类，**贵局实为 27 格**（原记 11 格有漏，据 `c2` 卷一 行 604–631 补正） | 建议**新增 `pattern:SpecialStructure`**（`structureType` 4 枚举），贵局 **27 格**并挂 `pattern:StarPattern` |
| 14 | **★大小限过十二支所忌 是否建 `fortune:LimitTaboo` 类？** | 卷三·論大小限星辰過十二宮遇十二支所忌訣 | 建议**新增 `fortune:LimitTaboo`**（`tabooBranch` 12 枚举），查表型所忌由程序/OBDA |
| 15 | **★十二宫诸星得地/失陷 四论 是否建 `pattern:PalaceStarQualification` 类？** | 卷一·四论（得地合格/失陷破格/得地富贵/失陷贫贱） | 建议**新增 `pattern:PalaceStarQualification`**（节点 11 成格分类） |
| 16 | **★全篇目复核补遗是否确认（不扩范围）？** | 新增 §2.1(C) 补遗表（16 组），全部映射既有 66 项 | 建议**确认按 §2.1(C) 补遗**；**不扩范围、不动 R1~R4** |
| 17 | **★「明细枚举层」缺口补全方式（篇名之下格名/论名/子目）** | 第 6 步本体下钻比对发现 6 处缺口（★定贵局 11→27、定富局6、定贫贱/杂局各8、十等10、诸格论19子目、卷三夹宫诸格）；查表/全集「篇名之下」明细未逐个录入本体 | 建议**采「类 `rdfs:label` 全枚举 + 格名代表性个体」**：类 label 全枚举全部格名/论名/子目，并给关键格建代表性个体（挂 `patternName`/`patternGradeName`/`structureType`）；**全集仍留第 12 步**（客户裁定） |

> 第 2 步流程设计 §7 的 **①~㉛ 待裁定已全部确认**；本表据此给出**建议结论**，待闸门③一并确认或带入第 5 步。
> **复核结论**见 §10（回原著核验）。

---

## 10. 复核结论（对齐第 2/3 步 · 回原著核验）★v1

> 以领域专家（陈希夷）视角，对本核心设计逐项**回原著核验**并对照第 2 步流程、第 3 步顶层设计。

### 10.1 出处核验（回原著逐条命中，无编造）

| 出处（本设计标注） | 原著卷次·位置 | 命中原文（节录） |
|---|---|---|
| 卷二·安身命例 / 安十二宮例 | 卷二 | 「安身命例」「安十二宮例」（闰月「闰月正月生者要在二月内起安身命」） |
| 卷二·起五行寅例 / 六十花甲子納音歌 | 卷二 | 「起五行寅例」「六十花甲子納音歌」（纳音定五行局） |
| 卷二·安南北斗諸星訣 | 卷二 | 「安南北斗諸星訣」（安紫微及十四主星，含定位表） |
| 卷二·安文昌文曲星訣 / 安左輔右弼星訣 / 安天魁天鉞訣 / 安天馬星訣 / 安祿存星訣 | 卷二 | 各安辅佐星诀 |
| 卷二·安擎羊陀羅二星訣 / **安火鈴二星訣** / 天空地劫訣 / 安天傷天使訣 | 卷二 | 各安煞忌星诀（火铃俱按生年支三合） |
| 卷二·安三台八座二星訣 / 安龍池鳳閣訣 / 安天刑天姚星訣 / 安紅鸞天喜訣 / 安天德月德解神訣 / 安截路空亡訣 / 安旬中空亡訣 | 卷二 | 各安杂曜诀 |
| 卷二·博士/力士/青龍/小耗/將軍/奏書/飛廉/喜神/病符/大耗/伏兵/官府 | 卷二 | 太岁诸神（流年十二神）起例 |
| 卷二·安大限訣 / 安童限訣 / 安流祿流羊流陀訣 / 安斗君訣 / 安十二宮太歲殺祿訣 / 定男女竹蘿三限 | 卷二 | 限与流曜起法（阳男阴女顺/阴男阳女逆） |
| 卷二·安命主 / 安身主 | 卷二 | 命主/身主定法 |
| 卷二·星辰屬性诸条 | 卷二 | 各星五行/阴阳/化气/庙陷条目 |
| 卷二·論星辰生剋制化 | 卷二 | 「論星辰生剋制化」（星辰五行生克） |
| 卷二·定十二宮弱強 / 定十二宮星辰落閑 | 卷二 | 「定十二宮弱強」「定十二宮星辰落閑」 |
| 卷二·十二长生（男顺女逆） | 卷二 | 十二长生起宫与顺逆（火局寅/木局亥/水局申/金局巳/土局申；男顺女逆） |
| 卷二·論安命金鎖鐵蛇關 | 卷二 | 「論安命金鎖鐵蛇關」（小儿关煞） |
| 卷一·太微賦 / 形性賦 / 星垣論 / 斗數準繩 / 斗數發微論 / 重補斗數彀率 / 增補太微賦 | 卷一 | 赋论总纲（立命格局/三方四正） |
| 卷一·斗數骨髓賦 / 女命骨髓賦 | 卷一 | 男命/女命骨髓赋（十二宫义/分类断） |
| 卷一·諸星問答論（诸主星篇 / 羊陀火铃空劫篇 / 四化所主 / 天刑天姚天哭天虚所主） | 卷一 | 「問紫微星所主若何」…「問化祿星所主若何」（化气/所主） |
| 卷一·論科權祿主格 / 論左右朝垣格 / 論文星朝命格 等诸格 | 卷一 | 诸格局成立条件 |
| 卷一·論壽夭淫蕩 / 論定人殘疾 / 論人命內犯孤剋者 / 論出家僧道之命 | 卷一 | 寿夭孤克残疾断 |
| 卷三·談星要論 | 卷三 | 「談星要論」（分类论断） |
| 卷三·論人命入格 / 論格星數高下 | 卷三 | 命格高低等第 |
| 卷三·論大限十年禍福何如 / 論二限太歲吉凶 / 論行限分南北斗 / 論流年太歲吉凶星殺 | 卷三 | 运限断法 |
| 卷三·論羊陀迭並 / 論七殺重逢 | 卷三 | 凶格引动 |
| 卷三·論立命行限宮歌 | 卷三 | 行限宫歌 |
| 卷三·諸格断语 | 卷三 | 夹宫诸格（紫府/日月/昌曲/羊陀/火铃/魁钺/左右/劫空夹命；三夹六夹） |
| 卷三·論男女命同異 / 論小兒命 / 定小兒生時訣 / 論小兒剋親 / 論陰騭延壽 | 卷三 | 男女命同异/小儿/劝诫 |
| 卷三·論人生時要審的確 | 卷三 | 「子亥二时…上午刻属昨夜亥时，下午刻属今日子时」（时辰交界） |
| ★卷一·羅序 | 卷一 | 「希夷公紫微斗數集也…時陳子去希夷公，一十八代」 |
| ★卷一·諸格論（19 子目） | 卷一 | 「論對面朝鬥格」「論科權祿主格」「論石中隱玉格」「論貪狼遇火名為火貴格」「論人命內犯孤剋者」「論定人殘疾」「論定人作盜賊」等 |
| ★卷一·定富貴貧賤十等論 | 卷一 | 「定富貴貧賤十等論」 |
| ★卷一·十二宮諸星得地合格訣 / 失陷破格訣 / 得地富貴論 / 失陷貧賤論 | 卷一 | 四篇全文 |
| ★卷一·定富局 / 定貴局 / 定貧賤局 / 定雜局 | 卷一 | 「財蔭夾印/日月夾財/財祿夾馬/蔭印拱身/日月照璧/金燦光輝」（富局6）｜「日明夾命/日出扶桑/月落亥宮/月生滄海/輔弼拱主/君臣慶會/財印夾祿/祿馬佩印/坐貴向貴/馬頭帶劍/七殺朝鬥/日月並明/明珠出海/日月同臨/刑囚夾印/科權祿拱/貪火相逢/武曲守垣/府相朝垣/紫府朝垣/文星暗拱/權祿生逢/羊刃入廟/巨機居卯/明祿暗祿/科明暗祿/金輿扶駕」（**貴局 27 格**）｜「生不逢時/祿逢兩殺/馬落空亡/日月藏輝/財與囚仇/一生孤貧/君子在野/兩重華蓋」（貧賤局8）｜「風雲際會/錦上添花/祿衰馬困/衣錦還鄉/步數無依/水上駕星/吉凶相伴/枯木逢春」（雜局8） |
| ★卷一·定貴局 **27 格**（明细枚举层补正） | **卷一 `c2` 行 604 篇题「定贵局」→ 行 605–631 逐格释义** | 逐格「格名 + 释义」：日月夹命…马头带剑（10 新录）+ 七杀朝斗/日月并明/明珠出海/日月同临/刑囚夹印/科权禄拱/贪火相逢/武曲守垣/府相朝垣/紫府朝垣/文星暗拱/权禄生逢/羊刃入庙/巨机居卯/明禄暗禄/科明暗禄/金舆扶驾（原「见前批注」/明录）；**此前设计仅记 11 格，漏 16 格** |
| ★卷一·定富贵贫贱十等论（10 论） | 卷一 | 「定富貴貧賤十等論」（福寿/聪明/威勇/文职/武职/刑名/富贵/贫贱/疾殀/僧道） |
| ★卷三·夹宫诸格（明细枚举层补正） | 卷三 | 夹宫诸格（紫府夹命/日月夹命/昌曲夹命/羊陀夹忌/火铃夹命/魁钺夹命/左右夹命/劫空夹命；夹身：三夹凶·六夹吉） |
| ★卷二·安喪門白虎吊客官府四飛星訣 | 卷二 | 「流年太歲前二位是喪門，後二位是吊客，喪門對照安白虎，吊客對照安官府」 |
| ★卷二·安飛天三殺訣 | 卷二 | 「安飛天三殺訣 即奏書、將軍、直符」 |
| ★卷二·安台輔封訣 / 安封誥訣 | 卷二 | 两诀分列 |
| ★卷二·論諸星分屬南北斗化吉凶並分屬五行 | 卷二 | 「紫微屬土中天星南北斗化帝座為官祿主；天機屬木南斗化善為兄弟主；天府屬土南斗化令星…」（化气·五行·主宫总表） |
| ★卷二·紫微定位棋盤圖 | 卷二 | 「水二局」「木三局」「金四局」「土五局」「火六局」棋盘表 |
| ★卷二·安小限訣 | 卷二 | 「不論陰陽男俱順數不論陰陽女俱逆數」 |
| ★卷二·定公卿 / 定兩官府 / 定文官 / 定武官 / 定曹吏 | 卷二 | 官阶五定 |
| ★卷三·論人生時安命吉凶 | 卷三 | 全文 |
| ★卷三·論命先貧後富 | 卷三 | 全文（先成后败/先大后小） |
| ★卷三·論二限太歲吉凶 | 卷三 | 全文（大限/小限/太岁 独守·相逢·相冲·太岁冲羊陀七杀） |
| ★卷三·論大小限星辰過十二宮遇十二支所忌訣 | 卷三 | 全文（十二支所忌） |
| ★卷三·論諸星同垣各司所宜分別富貴貧賤夭壽 | 卷三 | 「紫微 庙丑未午 旺寅申卯酉巳亥 平子 无陷…」（诸星庙旺陷定位 + 星情格语） |

> **结论**：本设计全部出处**可回原文定位、无编造**（★铁律 8/9/10）。原著未收录/后世通行者（**真太阳时**、**借星/借光/空宫格局**、**飞星派化入化出**、**三盘叠加**、**验盘**、**宫干飞化口径**、**参照典籍**）已在正文/§9 显式标【待考·轻/中】并转 `diagnosis:ToBeVerified`/`SchoolVariant`，未伪造出处。

### 10.2 流程节点覆盖（第 2 步 21 ServiceTask → 本体落点）

| 节点 | 本体落点 |
|---|---|
| 1 输入解析与时辰校正 | `chart:BirthInput`、`calendar:TrueSolarTime`/`LeapMonth`/`CalendarBoundary` |
| 2 定命身宫与十二宫 | `chart:NatalChart`/`Palace`/`LifePalace`/`BodyPalace`/`FivePhaseBureau`/`LifeMaster`/`BodyMaster`、R?（起五行局） |
| 3 安十四主星 | `star:FourteenMainStar`、`layout:StarPlacement`；R4/R5 |
| 4 安辅佐煞忌星 | `star:AuxiliaryStar`/`MaleficStar`、`layout:StarPlacement`；R4 |
| 5 安杂曜与太岁诸神 | `star:MiscStar`/`YearlyDeity`、`layout:StarPlacement`；R4 |
| 6 安四化与宫干、十二长生 | `layout:Transformation`、`chart:PalaceStem`、`layout:TwelveStagePlacement`；R7/R14 |
| V 验盘校正 | `diagnosis:ChartVerification` |
| 7 定庙旺利陷与星辰生克 | `state:Brightness`/`PhaseRelation`/`PalaceStrength`；R6 |
| 8 星曜化气与诸星所主 | `state:StarCharacter`（**静态**）、`pattern:StarMeaning` |
| 9 空宫处理 | `palace:EmptyPalace`、`palace:borrowsFrom`；R11 |
| 10 三方四正·夹宫·星曜组合与辅煞交互 | `palace:SanFangSiZheng`/`Adjoining`、`pattern:StarCombination`；R9/R10 |
| 11 格局判定 | `pattern:StarPattern`/`ClassicText`/`PatternGrade`、`fortune:FlyingTransformation`、`diagnosis:SchoolVariant`；R12/R13 |
| 12 本命综合断 | `reading:Temperament`/`Kinship`/`Marriage`/`Career`/`Wealth`/`Disease`/`Longevity`/`GenderDifference` |
| W 女命专论 | `reading:LadyFortune` |
| C 小儿命专论 | `reading:ChildFortune` |
| 13 大限推演 | `fortune:GreatLimit`；R8/R8b |
| 14 流年小限推演 | `fortune:SmallLimit`/`AnnualFortune`/`RecalculatedDeity`；R15 |
| 15 叠宫断法与凶格引动 | `fortune:OverlayReading`/`TriggeredPattern`/`LimitSong`；R16/R17 |
| 16 综合结论汇总 | `reading:ReadingConclusion`/`StructuredOutput`/`Admonition`/`ChartGraphic`、`reading:hasConclusion` |
| A 外发告警 | `rules:AlertFlag`；R18~R21 |
| 17 结果落库与返回 | `reading:CaseRecord`、`reading:hasCaseRecord` |

> 五个排他网关（G1/G1b/G2/G3/G4）条件由对应枚举/布尔个体承载（如 `chart:mode`→输入模式分支、`chart:subjectType`→命主类别分支、`chart:depth`→推演深度分支、`rules:alertTriggered`→外发分支），BPMN 分支可查询判定。

### 10.3 顶层设计覆盖（第 3 步 §5 顶层类框架 + S1~S7 → 展开）

| 顶层域/支撑 | 展开状态 |
|---|---|
| CalendarDomain/ChartDomain/StarDomain/LayoutDomain/StateDomain/PalaceDomain/PatternDomain/FortuneDomain/ReadingDomain/DiagnosisDomain | **全部展开为明细类**（见 §1） |
| S1 基础历法 → `core:CalendarDomain` ✓ | S2 历法时辰 → `calendar:` ✓ |
| S3 出处与流派 → `diagnosis:SourceNote`/`SchoolVariant`/`ToBeVerified`/`ChartVerification` ✓ | S4 术语同义 → `diagnosis:Synonym` + 第 13 步 SKOS ✓ |
| S5 告警标志 → `rules:AlertFlag` ✓ | S6 命例与隐私 → `reading:CaseRecord` ✓ |
| S7 跨切面 → `reading:ChartGraphic`（K·65）、`diagnosis:SchoolVariant`（J·55）✓ | OutputDomain → 跨域归入 `reading:`/`rules:`（§9-1 提请注意）✓ |
| **★两类「化」分列** → `state:StarCharacter`（静态）/`layout:Transformation`（动态）✓（§6-5） | — |

### 10.4 v1 说明

- v1 为初稿：类/属性/约束/SWRL 依据顶层设计 §5 与流程 §3 展开，出处逐条回原著核验（§10.1）。
- **v1.1 全篇目复核补遗**：按客户「据《全書》**全文**（非仅目录）查漏」要求，逐篇比对《全書》正文实际篇目（**正文多于 EPUB 目录**），补入 **16 组**此前未点名/未细化的篇目（见 §1.2 与顶层设计 §2.1(C)）：新增类 `pattern:SpecialStructure`（富/贵/贫贱/杂局）、`pattern:PalaceStarQualification`（十二宫诸星得地/失陷四论）、`fortune:LimitTaboo`（大小限过十二支所忌）；新增/细化 `star:FourFlyingStar`（四飞星）、`star:FlyingTriSha`（飞天三杀）、`reading:Longevity`（残疾/破相/聪明/富足/贫贱/盗贼/驳杂/僧道/商贾/艺术）、`reading:Career`（公卿…曹吏）、`reading:Wealth`（先贫后富）、`pattern:PatternGrade`（十等）、`pattern:ClassicText`（羅序）；**化气出处校正**（并入 卷二·论诸星分属南北斗化吉凶并分属五行）；新增 SWRL **R23~R25**。**不扩范围、不动 R1~R4**。
- **v1.2 明细枚举层补遗**：对第 6 步单文件本体再作「**篇名之下格名/论名/子目**」下钻比对，补正 **6 处明细枚举层缺口**（**均在既有在范围项之内，不扩范围**）：①**卷一·定貴局实为 27 格**（原记 11 格，漏 16 格；据 `c2` 卷一 行 604–631）；②定富局 6 格；③定贫贱局 8 格 / 定杂局 8 格；④定富贵贫贱十等论 10 论；⑤诸格论 19 子目格名；⑥卷三·夹宫诸格。落地＝**类 `rdfs:label` 全枚举 + 格名代表性个体**（§1.2 / §7：`pattern:StarPattern` 代表 35、`pattern:PatternGrade` 10）；回写 §1.2/§5(R23)/§7/§9(新增第 17 项)/§10.1，保持「顶层设计↔核心设计↔本体」三侧一致。
- **v1.3 全篇章索引（出处标注全集）**：以第 6 步本体与《全書》**epub 正文完整大纲**（`zwds-corpus/OPS` 三卷 xhtml：h1~h4 标题 + `<p>` 篇/诀名；并核 `c1` 全覽版=三卷并集，无卷四/跋）逐条比对，发现**卷二全部安星诀、卷三全部论述、部分卷一赋论篇名、諸星問答論各星所主子目**此前**未逐字落入本体**（仅由概念类覆盖）。补入 **`diagnosis:SourceNote` 全篇章索引 124 条**（篇章 89＝卷一 23/卷二 46/卷三 20 + 星曜所主子目 35），逐条带 `sourceBook`+`sourceVolume`=「卷X·篇名」，使本体**字面包含《全書》全部篇/诀名与星问**；+原代表性 3 条 = **127**（triples 2578→3074）。残余未逐字命中者均**非缺失**：**庙陷表** 43 行（**查表型，留 OBDA/第 12 步**）、卷三「太岁并小限」行限歌 12 条（`fortune:LimitSong`）、十二宫子目（`chart:Palace`）、断人/断语/格名条件副题（`reading:Longevity`/`pattern:StarMeaning`/`pattern:StarPattern` 已覆盖）。
- **v1.4 第 5 轮全文核查收敛（用户裁决 · 2026-10-31）**：以《全書》epub 正文完整大纲与三卷 XHTML（`zwds-corpus/OPS`）再作逐条穷尽复核。结论：全书**概念层**（类/属性/约束/SWRL）与**篇/诀名·星问**层（`diagnosis:SourceNote` 124 条）已**全覆盖**；残余「未逐字落入本体」仅 4 类——①**庙陷表**（星×宫，约 20 星）、②卷三**太岁并小限行限歌** 12 条、③**断语/星情格语**、④**十二宫子目**。经**领域专家陈希夷裁决**：**维持既定设计**（庙陷表/安星表/四化/流曜按铁律 13/14 留 OBDA(MySQL)+程序；行限歌/断语/子目以「代表性个体+SourceNote」承接），**判定本轮核查收敛**（验收口径＝「概念与篇题全覆盖」）。§9 第 2、12 项就此**确认**；**本体无需新增个体**（保持 938 行 / 3074 triples）。
- 待确认项（§9）与【待考】项（真太阳时、借星/借光、化入化出、三盘叠加、验盘、宫干飞化、参照典籍）须在闸门③由领域专家裁定。

---

## SWRL 编写要点（易错清单，落实于第 5/6 步）

> 1. **字面量序列化**：SWRL 内阈值/常量字面量须写成 **property-element** 形式（`rdf:first` 携带 `rdf:datatype`），**不得**写成 `<rdf:Description rdf:datatype="...">` 作子元素，否则严格解析器判为非良构。
> 2. **`owl:withRestrictions` 列表**：facet 值须为**合法 RDF 列表**（推荐 `rdf:parseType="Collection"`），否则报 `Possible malformed list: rdf:first triple missing` 且 facet 丢失（Openllet 继而报 `A data range is defined without facet restrictions`）。
> 3. **Openllet RETE 限制**：`swrlb:equal(?a,?b)` 比较**两个变量**会抛 `UnsupportedOperationException: Builtin using unsafe variables`（启动期阻断）——须改为**共享同一变量**的 join 写法。
> 4. **`greaterThan/lessThan` + 匿名数据填充**：遇基数约束产生的匿名项会 NPE（`term1 is null`），属引擎边界、非本体错误，应避免在规则中对这类匿名项取值。
> 5. **回写生成源**：修改 SWRL 须回改生成脚本、不手改产物，保持可复现。

---

## 闸门③留痕

| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-31 | v1（初稿） | 码道 | 展开顶层设计 §5 为明细类体系（11 主干域 + 支撑 S1~S7 + OutputSubdomain）；设计对象属性 66 项、数据属性 74 项、`owl:Restriction` 36 项、SWRL 规则 R1~R22（含 R8b）；ABox 典型个体登记；待确认项 12 项；出处回原著核验（§10.1）。**待客户审阅**。 |
| 2026-10-31 | v1.1（全篇目复核补遗） | 码道 | 据客户「《全書》**全文**（非仅目录）查漏」要求：逐篇比对正文实际篇目，补入 16 组（卷一羅序/诸格论19子目/定富贵贫贱十等论/十二宫诸星得地·失陷四论/定富·贵·贫贱·杂局；卷二四飞星诀/飞天三杀诀/台辅封·封诰诀/化气·五行总表/紫微定位棋盘图/安小限诀/定公卿等；卷三论人生时安命吉凶/论命先贫后富/大小限过十二支所忌/诸星同垣各司所宜）；新增类 3、细化类 6、SWRL R23~R25；**化气出处校正**。**不扩范围**。**待客户审阅（闸门③）**。 |
| 2026-10-31 | **v1.1（定稿）** | 领域专家·陈希夷 | **闸门③通过**：认可 v1.1 全部类/属性/约束/SWRL（R1~R25）及 **16 组全篇目复核补遗**；**采纳 3 个新增类**（`pattern:SpecialStructure` 定富/贵/贫贱/杂局、`pattern:PalaceStarQualification` 十二宫诸星得地·失陷四论、`fortune:LimitTaboo` 大小限过十二支所忌）；**按 §9 建议结论执行**。准予进入**第 5 步（BPMN 含 UI）**与**第 6 步（单文件本体，闸门⑦）**。 |
| 2026-10-31 | v1.2（明细枚举层补遗） | 领域专家·陈希夷 | 认可对第 6 步本体「篇名之下格名/论名/子目」下钻核查，**补正 6 处明细枚举层缺口**（★定貴局 **11→27 格**、定富局 6、定贫贱局 8、定杂局 8、十等 10、诸格论 19 子目、卷三夹宫诸格）；裁定补全方式＝**类 `rdfs:label` 全枚举 + 格名代表性个体**（全集留第 12 步）；**同步回写顶层设计 §2.1(C) 与核心设计 §1/§1.2/§5/§7/§9/§10.1**。**不扩范围**。 |

---

## 闸门⑦留痕（典型 ABox 数据 · 第 6 步）

| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-31 | v1.3（本体 938 行 / 3074 triples） | 领域专家·陈希夷 | **典型 ABox 交付**：枚举型个体全量（阴阳/五行/天干/地支/五行局/十二长生/十四主星/辅佐/煞忌/太岁诸神/化气/四化类型/十二宫/宫义/告警/出处代表作）+ 代表性（六十甲子/纳音/杂曜/安星起例/庙陷表/样例命盘）；`pattern:StarPattern` 35 / `pattern:PatternGrade` 11 / `pattern:SpecialStructure` 4 / `diagnosis:SourceNote` 127。**第 5 轮全文核查收敛**：经陈希夷裁决，残余 4 类（庙陷表/行限歌/断语/十二宫子目）**维持设计口径**（查表型外置 OBDA + 代表性承接），判定「概念与篇题全覆盖」收敛，**不改本体**。**准予进入第 7 步**。 |


---

## 变更记录

| 日期 | 变更 | 确认人 |
|---|---|---|
| 2026-10-31 | v1 初稿：据顶层设计 §5（顶层类框架 + S1~S7）与流程设计 §3（21 节点）产出本体核心设计；**★两类「化」严格分列**（化气·静态 `state:StarCharacter` / 四化·动态 `layout:Transformation`）；明细类 11 主干域；对象属性、数据属性、`owl:Restriction`、SWRL 规则 R1~R22；ABox 典型个体登记；OBDA/SKOS 预留；待确认项 12 项；复核结论回原著核验 | 码道 |
| 2026-10-31 | v1.1 全篇目复核补遗：逐篇比对《全書》**正文实际篇目**（多于 EPUB 目录），补入 16 组篇目对应概念；新增类 `pattern:SpecialStructure`/`pattern:PalaceStarQualification`/`fortune:LimitTaboo`，细化 `star:FourFlyingStar`/`FlyingTriSha`/`reading:Longevity·Career·Wealth`/`pattern:PatternGrade·ClassicText`；**化气出处校正**；新增 SWRL R23~R25；§1.2 补遗表、§10.1 核验 17 行、§9 待确认项扩至 16 项。**不扩范围** | 码道 |
| 2026-10-31 | v1.2 **明细枚举层补遗**：对第 6 步本体作「篇名之下格名/论名/子目」下钻比对，补正 6 处缺口——★**定貴局 11→27 格**（据 `c2` 卷一 行 604–631）、定富局 6、定贫贱局 8、定杂局 8、定富贵贫贱十等论 10、诸格论 19 子目、卷三夹宫诸格；落地＝类 `rdfs:label` 全枚举 + 格名代表性个体（`pattern:StarPattern` 35 / `pattern:PatternGrade` 10）；回写 §1 类树、§1.2、§5(R23)、§7（新增 5 行个体登记）、§9（新增第 17 项）、§10.1（新增 3 行出处）、§10.4。**不扩范围、不动 R1~R4** | 码道 |
| 2026-10-31 | v1.3 **全篇章索引（出处标注全集）**：以《全書》epub 正文完整大纲逐条比对本体，发现卷二安星诀/卷三论述/部分卷一赋论篇名/諸星問答論星问未逐字入本体；补入 `diagnosis:SourceNote` 全篇章索引 **124 条**（篇章 89＝卷一 23/卷二 46/卷三 20 + 星曜所主子目 35），使本体字面包含全部篇/诀名与星问（含原代表性 3 = 127）；triples 2578→3074；回写 §7、§10.4 | 码道 |
| 2026-10-31 | v1.4 **第 5 轮全文核查收敛（用户裁决）**：以 epub 正文完整大纲 + 三卷 XHTML 逐条穷尽复核；结论＝概念层 + 篇/诀名·星问层（SourceNote 124）全覆盖，残余 4 类（庙陷表/行限歌/断语/十二宫子目）经陈希夷裁决**维持既定设计**（查表型外置 OBDA + 代表性承接），判定收敛、不改本体；§9 第 2/12 项确认；回填 **闸门⑦留痕** | 码道 |
| 2026-10-31 | v1.5 **第 11 步 OBDA 落地（闸门⑩）**：§8 标注「已落地」——`ontology/database/` 四件套产出、库 `ziweidoushudb` 建成 14 表（§8 规划表）；查表型历法个体迁出、DB 唯一真源；起例表取代表性；`case_record` 列对齐程序写入契约（`birth_year`/`gender`/`chart_summary`/`reading`） | 码道 |
| 2026-10-31 | v1.6 **第 12 步 ABox 扩至全集（闸门⑪）**：§8「全集规模」标注「已落地」——DB 侧补全（六十甲子 60 / 纳音 30 / 紫微定位 150 / 星辰庙陷 240 / 流曜起例 42）＋ `.owl` 侧补 **`pattern:StarPattern` 29 个体**（`pattern` 52→81：贵 11→27、富 3→6、贫贱 3→8、杂 3→8），DB 与本体两侧同步；逐条出处（卷二/卷一具体篇章）。**不扩范围、不改 TBox、不动 DDL 结构** | 码道 |
| 2026-10-31 | v1.7 **第 13 步 SKOS 术语表落地（闸门⑥）**：§8「SKOS」标注「已落地」——产出 `ontology/ziweidoushu-terminology.ttl`；规范概念 **374**＝类 158+对象属性 76+数据属性 81+星曜个体 59；`prefLabel@zh/@en`、`altLabel` 20 条逐条标源、`broader` 365 条、同 zh 名 10 项消歧；**本体侧注入 `skos:exactMatch → term` 374 条**；`ziweidoushu-tbox.owl` 增 `owl:imports .../term`、`catalog-v001.xml` 登记 term。测试侧启用 `ZiWeiDouShuSkosTerminologyTest`（6/6）。回归 `AllZiWeiDouShuSuiteTest` 全绿、`validate_structure.py` ERROR=0 | 码道 |
| 2026-10-31 | v1.8 **经典符合性审查与修正**：以《全書》卷二逐篇复核，**庙陷/落闲/神煞/格局四类实现与原文对齐**——①节点 7 庙陷改**查卷二·星辰庙陷表**（`state:Brightness` 由 `brightness_map` 240 组查取，非生克近似）；②补 `star:MiscStar` 之 **丧门/白虎/吊客/官府**（`YearlyDeity`）与 **奏书/将军/直符**（`FlyingTriSha`，卷二·安飞天三杀诀）；③`pattern:StarPattern` 命中逻辑按卷一·定富/定贵/定贫贱局**逐条落地**（格名个体已在 v1.6 就位）；④出处校正（卷三→卷二）。产出《ZiWeiDouShu应用-经典符合性审查报告》 | 码道 |
| 2026-11-01 | v1.9 **第三轮经典符合性审查与修正**：逐条回原著再核「格名个体 ⇄ 命中逻辑」并查杂曜起例/长生——①「七杀朝斗」命中补**寅申**（`isQiShaChaoDou`＝寅申子午；卷三·谈星要论）；②「龙池」起宫正为**辰上起子顺**（`longChi`/`fengGe`；卷二·安龙池凤阁诀）；③补 `pattern:StarPattern` 命中之 **对面朝斗格/石中隐玉格/兼文武格**（卷一·诸格论）与 **禄马佩印/日月藏辉**（定贵局/定贫贱局）；④「火贵格」改按「**三合照身命**」（原误同宫）；⑤十二长生天干注「通行取象·待考」（卷二·安长生诀不言干）。验证：编译 EXIT 0；离线 `ZiWeiDouShu*` 30/30 全绿 | 码道 |
| 2026-11-01 | v1.10 **第四轮经典符合性审查与修正（命中逻辑补遗，不改 TBox）**：将已建模的 `pattern:PalaceStarQualification`（卷一·十二宫诸星得地合格诀/失陷破格诀/得地富贵论/失陷贫贱论）与 `pattern:SpecialStructure`（定杂局 8 格）**由「仅建类/个体」补至程序命中逻辑**——`judgePalaceZhiGrid` 按命宫地支判得地/失陷格、`analyzeMajorLimit` 判运限杂局；定贵局「明珠出海/文星暗拱/明禄暗禄/科明暗禄」（原文「见前批注」）维持〔待考〕不臆断。**不扩范围、不改 TBox/DDL、不动 SWRL** | 码道 |
| 2026-11-01 | v1.11 **第五轮经典符合性审查与修正（命中逻辑补遗，不改 TBox）**：将已建模的 `pattern:PatternGrade`（卷一·定富贵贫贱十等论 10 枚举个体）、`fortune:LimitSong`（太岁并小限到十二宫入庙化吉/不入庙化凶/所值吉凶星，SWRL R25）、`fortune:LimitTaboo`（大小限过十二支所忌，SWRL R24）**由「仅建类/个体/SWRL」补至程序命中逻辑**——`determinePattern` 增 `judgeTenGrades`、`analyzeAnnualFortune` 增 `TAISUI_XIAOXIAN`/`LIMIT_TABOO` 查表、`analyzePalaceOverlay` 更正行限宫歌口径。**不扩范围、不改 TBox/DDL、不动 SWRL** | 码道 |
| 2026-11-01 | v1.12 **第六轮经典符合性审查与修正（命中逻辑补遗，不改 TBox）**：将已建模的 `pattern:PalaceStarQualification` 之**「得地富贵论 / 失陷贫贱论」两类**（四论齐备）与 `pattern:StarMeaning`（卷三·论诸星同垣各司所宜分别富贵贫贱夭寿，`starMeaningText`）**由「仅建类/属性」补至程序命中逻辑**——`judgePalaceZhiGrid` 增 `PALACE_FUGUI`/`PALACE_PINJIAN`、`determinePattern` 增 `STAR_TONGYUAN`（约 200 条）＋`judgeTongYuan` 逐条判定；并**修正 `戌`/`戍` 字形缺陷**（`PALACE_ZHI_GE`/`PALACE_ZHI_PO` 戌支恢复命中）。庙陷表与卷二·星辰庙陷 **240/240 一致**（卷三汇总异文标〔待考〕）。**不扩范围、不改 TBox/DDL、不动 SWRL** | 码道 |
| 2026-11-03 | v1.13 **第八轮经典符合性审查与修正（命中逻辑补遗，不改 TBox）**：将已建模的 **`reading:Longevity` / `reading:longevityType`（12 值枚举）** 与 **`state:PhaseRelation` / `state:phaseRelationType`（生/克/制/化）** **由「仅建类/属性」补至程序命中逻辑**——①节点 12 `generateNatalReading` 增 `judgeLongevity`（卷一·诸格论 12 格：商贾/艺术/僧道/孤克/寿夭淫荡/残疾/破相/聪明/富足/贫贱/盗贼/一身驳杂，输出 `longevityReading`；`c2` L510-544，出处由误标「諸星問答論」校正为「诸格论」）；②节点 7 `assessBrightness` 增 `palaceStarRelation`（卷二·论星辰生克制化「星曜落宫」：宫位五行×星曜五行→宫生星/星生宫/宫克星(受制:X入Y乡)/星克宫/比和，输出 `phaseRelations`，落地「金入火乡/火入水乡/水入土乡/土入木乡 俱为受制」；`c3` L137-140）。**不扩范围、不改 TBox/DDL、不动 SWRL、不动 BPMN** | 码道 |
| 2026-11-03 | v1.14 **第九轮经典符合性审查与修正（【待考】项外部资源落地，含 TBox/ABox/术语补遗）**：①**更正误标**——步数无依/水上驾星/锦上添花系**《全書》卷一·定杂局原文**（`c2` L643/646/647），非「语义主观」，节点 13 落地（锦上添花可判、余二标〔运限重算〕）；②落地 **定贵局 明珠出海/文星暗拱/明禄暗禄/科明暗禄**（`pattern#Gui_*`，成格条件采〔外部通行法·非《全書》原文〕）；③`placeMiscStars` 增置 **恩光/天贵/天官/天福/天厨/天巫/天才/天寿/孤辰/寡宿/华盖/咸池** 12 杂曜（〔外部通行法·非《全書》原文〕）。**本体同步（本轮确有 TBox/ABox/术语改动）**：`star:MiscStar` 类 `rdfs:label` 扩展以纳补遗 12 曜（保留《全書》原列、补遗段显注外部来源）；`ziweidoushu-abox.owl` 增 12 个 `star:MiscStar` 个体（`EnGuang/TianGui/TianGuan/TianFuXing/TianChu/TianWu/TianCai/TianShou/GuChen/GuaSu/HuaGai/XianChi`，注：`天府` 已占 `TianFu` 故 `天福` 用 `TianFuXing`）；`ziweidoushu-terminology.ttl` 增 12 个 SKOS 术语（`skos:broader term:MiscStar`，逐条带「〔外部通行法·非《全書》原文〕」注释）。④`feiTianSanSha` 组内序来源升级为〔外部通行法·非《全書》原文〕；⑤节点 7 增卷二↔卷三庙陷异文登记（`brightnessVariantNote`）。**不改 DDL、不动 SWRL、不动 BPMN**；本体推理合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.15 **第十轮经典符合性审查与修正（运限序列模块落地，不改 TBox/ABox）**：将卷一·定杂局 **「步数无依 / 水上驾星」** 由文本待判升为**程序客观命中逻辑**——`fortune:SpecialStructure`（杂局 8 格）之 `pattern#Za_BuShuWuYi`/`pattern#Za_ShuiShangJiaXing` 原已声明而程序仅作文本标注，现落地：①新增 `yunxianFortuneSign`（三方四正吉凶符号：吉星/凶星＋主星庙旺(+)/落陷(-)＋生年四化禄权科(+)/忌(-)）与 `annualFortuneSign`（以太岁宫为流年命宫、叠加流禄/流羊/流陀）；②新增序列判定 `isBuShuWuYi`（「前限接后限连绵不分」＝大限十二限吉凶同号、无转折）、`isShuiShangJiaXing`（「一年好一年不好」＝流年吉凶逐年交替）；③节点 13 `analyzeMajorLimit` 产出 `miscPattern` 条件判定＋新结果键 `yunxianSequence`。落地口径〔释读·非逐字原文〕；**不改 TBox/ABox/DDL、不动 SWRL、不动 BPMN**；`ZiWeiDouShuStarTableTest` 30/30、本体合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.16 **第十一轮经典符合性审查与修正（运限重算 + 竹萝三限落地，不改 TBox/ABox）**：将运限类逻辑补至程序命中——①节点 13 `analyzeMajorLimit` 增 **大限重算**（大限宫/大限宫干四化/大限十二神/大限吉凶符号）＋**小限重算**＋**大小二限相遇**，新增结果键 `majorLimitRecalc`；②**竹萝三限**（卷二·定男女竹萝三限）：`isZhuLuoSanXian`（三方四正七杀破军俱见）/`hasJuMenOrSha`（加巨暗凶星作三方四正定议），命宫／当前大限宫分判，大小二限相遇且见竹萝三限→作死限断；③节点 14 `analyzeAnnualFortune` 增 **流年四化**落宫，`annualFortuneSign` 并入流年四化，完成「流年实际引动星曜」。**不改 TBox/ABox/DDL、不动 SWRL、不动 BPMN**；`ZiWeiDouShuStarTableTest` 33/33、本体合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.17 **第十二轮经典符合性审查与修正（流月/流日粒度㉓落地，不改 TBox/ABox）**：节点 14 `analyzeAnnualFortune` 实现 月/日 粒度——①**流月**依卷二·安斗君诀（「即月将星是也」：斗君为流月正月命宫、逐月顺行一宫，`flowMonthPalace`）；②**流日**〔外部通行法·非《全書》原文：《全書》仅言「日值恶杀」未立起例〕（流月命宫起初一、逐日顺行一宫，`flowDayPalace`）；③新增结果键 `flowMonth`/`flowDay`；未知粒度回退年级并标注。至此 ㉓「年/月/日」三粒度全部落地（衔接 R1 分秒级应期）。**不改 TBox/ABox/DDL、不动 SWRL、不动 BPMN**；`ZiWeiDouShuStarTableTest` 34/34、本体合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.18 **第十三轮经典符合性审查与修正（空宫借星/四盘叠合外部比对落地 + 庙陷异文复核，不改 TBox/ABox）**：将剩余【待考·中】项补外部比对并落地——①**空宫借星（I·62）**：`palace:EmptyPalace` 之借星补至程序命中逻辑——新增 `borrowMainStars`（借对宫**十四主星**、辅煞杂曜**不借**）、`borrowedBrightness`（借光·庙陷**随对宫原地**、四化随星），由【待考·中】升级为〔外部通行法·非《全書》原文〕（《全書》语料 0 命中成条借星口诀）；②**四盘叠合（I·51）**：`fortune:OverlayReading` 补至四盘体用编排——新增 `overlayStack`（**本命为体**、**大限/流年/流月/流日为用**逐层叠入），由静态「三盘叠合·待考」文本改为**计算后四盘定位**（复用 `daXianPalace`/`douJun`/`flowMonthPalace`/`flowDayPalace`），同升级为〔外部通行法·非《全書》原文〕；③**卷三·诸星庙陷汇总异文复核**——21 星×12 支逐支比对，**正相左者仍唯二**（天机@辰/文曲@寅），无第三处（禄存差异属结构性不可能：禄存不入四墓库）。**不改 TBox/ABox/DDL、不动 SWRL、不动 BPMN**；`ZiWeiDouShuStarTableTest` 36/36、本体合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.19 **第十四轮经典符合性审查与修正（I·61 验盘外部比对落地，不改 TBox/ABox）**：将诊疗类 `diagnosis:ChartVerification`（验盘/定盘）由「默认无需校正」桩补至**程序客观命中逻辑**——①候补时辰（原时±1 或输入 `candidateTimes`）；②`lifePalaceBranch`/`bodyPalaceBranch`（卷二·安身命例）＋`candidateMingStars`（以五行局+生日重排紫微定位取候补盘命宫主星）；③多维加权 `verifyCandidateScore`（`diagnosis:ChartVerification` 之 **命宫主星特征 w3＋时系星昌曲痣癍 w2＋五行局体质 w1**），`chartVerdict` 四态（定盘确认/建议改时辰/候补并列·需人工复核/特征不足·需人工复核）＋`correctedTime`/`candidateTimes`；④特征词表 `VERIFY_STAR_TRAITS`/`VERIFY_BUREAU_TRAITS` 依大德山人《紫微斗数论定盘》、91demi《时辰校正》等〔外部通行法·非《全書》原文〕（《全書》未见系统验盘论述）。由【待考·中】升级为〔外部通行法·非《全書》原文〕。**不改 TBox/ABox/DDL、不动 SWRL、不动 BPMN**；`ZiWeiDouShuStarTableTest` 39/39、本体合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.20 **第十五轮经典符合性审查与修正（〔待考·释读〕外部比对 + 得地合格诀生年干落地 + 验盘契约修复，不改 TBox/ABox）**：①**〔待考·释读〕三类外部比对**——卷一得地/失陷四论（`palace:ZhiGe/ZhiPo`+`Fugui/Pinjian`）、卷三同垣举例式（`pattern:TongYuan`）、卷三论格星数高下「数维」（`pattern:GeXingShu`）：联网（zhouqianli/yi958/newdu/古文岛/白话直译；18zw 宁雪天、shunshi.ai）证实通行本**亦不补客观算法**（「数」原文未展开定义、诸说不一）→ **维持取文不臆断**（登记外部释读：数＝宫、阴阳＝气机清浊）。②**得地合格诀生年干落地**：`palace:ZhiGe` 之「生年干」条件（源《全書》c2 L556-568 明文：子「庚辛乙癸」等）由**声明补至程序命中逻辑**——`PALACE_ZHI_GE` 增第 5 列、`judgePalaceZhiGrid(…, yearGan)` 判「星象 × 生干」（合→相合、不合→格减等）；「丙戍」形讹取「丙戊」、寅行「申巳庚」含非天干讹字标〔取文·不臆断〕。③**验盘 BPMN 契约修复**：`diagnosis:ChartVerification` 之 `chartVerdict` 改为流程网关值（「需校正/无需校正」），四态判读详情另存 `verifyVerdict`（修正 Worker↔BPMN 契约不符之真机 incident）。**不改 TBox/ABox/DDL、不动 SWRL、不动 BPMN**；`ZiWeiDouShuStarTableTest` 40/40、本体合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.21 **第十六轮经典符合性审查与修正（全量按章节复审 + 形性賦/先貧後富/二限太歲落地，不改 TBox/ABox）**：以 c0 目录为权威章节清单做**全量（非抽样）覆盖审计**（卷一19+卷二37+卷三20）。①**卷一·形性賦**由 0 落地——节点 8 新增 `STAR_XINGXING`（25 星形性原文，对应 `state:StarCharacter` 之「形性」维，与「化气」并出分列）→ 输出 `starXingXing`；②**卷三·論命先貧後富**落地——节点 13 新增 `judgeXianPinHouFu`（复用大限吉凶序列，前/后段净号判「先贫后富·先小后大 / 先富后贫·先成后败」）→ 输出 `xianPinHouFu`（口径〔释读·运限序列〕）；③**卷三·論二限太歲吉凶**落地——节点 13 新增 `judgeTwoLimitsTaiSui`（**太岁宫为流年命宫**；`fortune:GreatLimit`/`fortune:SmallLimit`/`fortune:AnnualFortune` 之独守·相逢·相冲，及太岁冲羊陀七杀；两限吉凶 `combineWord` 合成）→ 输出 `twoLimitsTaiSui`。**登记**：卷三 `論人生時要審的確`/`定小兒生時訣`（说明类·无判定，取文）。**不改 TBox/ABox/DDL、不动 SWRL、不动 BPMN**；`ZiWeiDouShuStarTableTest` 43/43、本体合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.22 **第十七轮经典符合性审查与修正（卷一五赋文具名格落地 + 举例式取文登记）**：对卷一 5 篇赋文（太微賦/增補太微賦/斗數準繩/斗數發微論/重補斗數彀率）逐句取具名格比对。**①新增 4 个 `pattern:StarPattern` ABox 个体**（《全書》原文直接落地）：`pattern#Ge_RiLiZhongTian`（日丽中天·太陽居午守命）、`pattern#Ge_ShuiChengGuiE`（水澄桂萼·太陰居子守命）、`pattern#Ge_ZhongShuiChaoDong`（众水朝东·文耗居寅卯）、`pattern#Ge_JinYuPengZhi`（金舆捧栉·紫微坐命庫辰戌丑未）；同步更新 `pattern.owl` 之 `StarPattern` label 枚举。**②举例式取文登记**：善蔭朝綱/貴入貴鄉/財居財位/祿文拱命/善福居空位/桃花犯主為至淫/衣錦惹天香（原文仅举格名无客观条件，`determinePattern` 登记于 `patternResult`，不臆断、不另立个体）；金殿傳臚/翰林清異/爛穀堆金/高商豪客与卷三同垣已落地条同义合并。**仅增 ABox 个体，不改 TBox 类/属性、不动 DDL/SWRL/BPMN**；`ZiWeiDouShuStarTableTest` 44/44、本体合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.23 **第十八轮经典符合性审查与修正（卷二/卷三断语类逐句核 + 太岁小限十二宫吉凶「生干择段」）**：通读卷三（c4 全 476 行）、卷二（c3）逐章比对。①**更正**——卷三「论太岁小限星辰庙陷遇十二宫中吉凶」（c4 L105-177）**实已落地**（§22 清单漏收，对应 `fortune:AnnualFortune` 之流年吉凶查表）；②**精化落地**——节点 14 增 `taisuiXiaoxianGanHit`：按**本命生干**择「入庙化吉（发福）/不入庙化凶（悔吝招官非）/两段皆见/两段未列」，新增 `extractGanConditions`（干支如「甲子」「丙午」之干不取）；**《全書》原文直接落地**。②**诸星同垣各司所宜补录 8 条**（c4 L178-476 逐句比对，日月/贪狼/巨门条无客观条件断语→登记取文）。卷二复核：安星诀/棋盘图/诸星分属南北斗化吉凶并分属五行/星辰庙陷/生克制化等均已落地。**不改 TBox/ABox/DDL、不动 SWRL/BPMN**；`ZiWeiDouShuStarTableTest` 46/46、本体合规 13/13、SKOS 6/6 全绿 | 码道 |

| 2026-11-03 | v1.24 **第十九轮经典符合性审查与修正（卷二·十二宫各星吉凶诀全量落地 + §22 覆盖矩阵补正）**：复扫卷二（c3）全篇发现 **§22「卷二全部落地」口径漏收整章**——**2.2–2.13「一 命宫～十二父母」**（c0 目录，c3 **L458–1282，约 824 行**，各宫各星「论／入男命吉凶诀／入女命吉凶诀／入限吉凶诀」）此前**既无 SourceNote 亦无代码落点**。①**ABox 补声明**：新增 `diagnosis#SN_J2_48..59`（命宮/兄弟/妻妾/子女/財帛/疾厄/遷移/奴僕/官祿/田宅/福德/父母，对应 `diagnosis:SourceNote`，`sourceVolume` 各归本宫）；另补 `SN_J2_47`（安三台八座二星訣，程序原已落地 `placeMiscStars` 但缺独立 SourceNote）。②**原文语料落地**：新增资源 `src/main/resources/ziweidoushu/palace_classic.tsv`（813 行、12 宫、逐行带 c3 行号）；程序新增 `loadPalaceClassic()`/`palaceStarQuote(宫,星)`，节点 9 输出 **`palaceClassic`**（按命宫逆行十二宫 × 该宫主星取原文首句＋回原行号）。**断语类按原文登记、取首句印证、不臆断、不程式化成格条件（合 ★8/10：不臆造、可追溯；19：全書第一权威）**。**未改 TBox 类/属性、不改 DDL/SWRL/BPMN**（ABox 仅增 13 个 SourceNote 个体）；`ZiWeiDouShuStarTableTest` **47/47**、本体合规 13/13、SKOS 6/6 全绿 | 码道 |
| 2026-11-03 | v1.25 **第二十轮经典符合性审查与修正（命宫「宫位×生年干→财官格/贵格/凶」可客观判定 + BPMN 输出映射缺陷修正）**：承接 v1.24 §25.3 目标①，将命宫生干财官格由「取文」升为「可客观判定」。①**判定器**：新增 `judgeMingGongGe(star, palaceZhi, birthGan)`，以 `palace_classic.tsv` 原文为单一真源，按「主星块→句（句号作用域复位 carry）/子句→最近宫位→生年干」逐句择取；命**无条件结构化句**（宫/地/命坐＋生年干）即回原文句＋c3 行号并断〔吉/凶/吉凶参/平〕，**含会照/逢遇附加条件者标〔条件式·待考〕**，原文未载则空串——**沿用 ★8/10 不臆造、可追溯；★19 全書第一权威**。②**声明≠落地修正**：BPMN `Task_StarNature` ioMapping 补齐 `chartSkeleton/auxiliaryStars/chartBase` 入参与 `starXingXing/palaceClassic/mingGongGe` 输出，使 v1.24 的 `palaceClassic` 与本轮 `mingGongGe` 真正落入流程变量。③**未改 TBox 类/属性、不改 DDL/SWRL**（ABox 无新增），仅 BPMN ioMapping 补映射（不改流程结构/节点/连线）。全网格审计（14×12×10=1680）硬判 536／条件式 3／空串 1141（皆原文未载，不臆造）。`ZiWeiDouShuStarTableTest` **48/48**、本体合规 13/13、SKOS 6/6、真实引擎 P-1 实测 `mingGongGe` 落地全绿 | 码道 |
| 2026-11-03 | v1.26 **第二十轮·目标②（卷二「定十二宫弱强/定十二宫星辰落闲」与十二宫各星吉凶诀断语交叉复核 + 节点 7 输出映射缺陷修正）**：①**规范表核对**——`state:PalaceStrength` 落地的 `IDLE_STAR`（8 星）与 c3 L133-134 逐字一致；`palaceStrength` 男女强弱势与 c3 L130-131 逐字一致（女命「夫君」→夫妻宫释读）；`state:PhaseRelation` 之生克序与 L137-140「金入火乡/火入水乡/水入土乡/土入木乡受制」一致。②**断语交叉**——卷二十二宫各星吉凶诀 9 处「落闲宫」断语：表内星（天机 L494/天梁 L712/七杀 L733）落闲即引（一致）；表外星（天同/廉贞/擎羊/火星/铃星/忌星）原文无定闲支，属断语泛论/举例式，不矛盾，仅落陷时以〔举例式·原文无定闲支·待考〕引文。③**落地**：`IDLE_VERSE` 原文登记 + `inIdleTable/idleVerseOf`；节点 7 增 `idleVerseNote`（表内落闲引原文断语＋c3 行号；表外落陷举例式待考）。④**声明≠落地修正**：BPMN `Task_Brightness` 补齐 `chartBase` 入参与 `brightnessVariants/phaseRelations/idleStars/idleVerseNote` 输出（原缺 chartBase 致 weak/strong 恒女命）。**未改 TBox 类/属性、DDL、SWRL**（ABox 无新增；断语源属 `SN_J2_48` 命宫章）；`ZiWeiDouShuStarTableTest` **49/49**（新增 `idleVerseCrossCheck`）、真实引擎 P-1 实测 `palaceStrength=男命`、`idleVerseNote` 落地 | 码道 |
| 2026-11-03 | v1.27 **第二十轮·目标③（重大事件逐流年回推 `subjectTraits` 结构化事件表 + 节点 14 映射补全）**：①**结构化事件回推**〔外部通行法·释读·非《全書》原文；仅验盘/复盘参考，不改《全書》第一权威〕：新增 `eventPalaceExpectation(seg)`（事件关键词→`{本命事项宫索引, 期望吉凶}`，夫妻/子女/父母/官禄/财帛/迁移/疾厄/兄弟/田宅/奴仆，未识别 null）与 `majorEventBacktrace(traits, at)`（**按事件分隔符切块**避免相邻年份窗口交叉误归类；年份→流年干支→事项宫流年定位 `targetBranch=floorMod(flowBranch-pe[0],12)`→`annualFortuneSign` 三方四正吉凶符号→附流年四化落宫与原述片段；给「合/待核/中性」；无事件 null）。②节点 14 `analyzeAnnualFortune` 增结果键 **`majorEventBacktrace`**。③**声明≠落地修正**：BPMN `Task_AnnualFortune` 原仅 4 input/3 output，补 `zeebe:input`(chartBase/mainStars/auxiliaryStars/miscStars/subjectTraits/targetMonth/targetDay) 与 `zeebe:output`(flowMonth/flowDay/taisuiXiaoxian/limitTaboo/majorEventBacktrace)。**未改 TBox 类/属性、DDL、SWRL**（ABox 无新增；本模块为程序释读逻辑，非本体断言）；`ZiWeiDouShuStarTableTest` **50/50**（新增 `majorEventBacktraceTest`）、真实引擎 P-2 实测 `majorEventBacktrace` 落地（共 2 项/事项宫＝/流年四化）、`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS** | 码道 |
| 2026-11-03 | v1.28 **第二十轮·收官（煞凑条件客观化为布尔判定「羊陀火铃冲会／巨宿羊陀」等）**：将断语附加之**煞凑条件**由〔条件式·待考〕升为可复算布尔判定（《全書》原文所载条件之客观复算，不改第一权威）。①新增 `detectShaCou(text)`（长匹配识别→所需煞曜集：羊→擎羊/陀→陀罗/火→火星/铃→铃星/巨→巨门；范围：同宫/三方四正）、`shaCouSatisfied(sc,palaceZhi,at)`（同宫或三方四正是否悉见所需煞曜）、`shaCouDescribe`。②`judgeMingGongGe` 增 4 参重载（传实际星曜盘 `at`；3 参委托、at=null 退化原待考）：条件式命中→取句中「生人」后全文（截断于下一个「生人」）→煞凑布尔核验、含「陷」叠验 `isFallen`→客观断凶/吉；「不喜…生人」→客观判凶。③节点 7 `idleVerseNote` 表内星断语含煞凑（七杀 L733「巨宿羊陀」）→据盘布尔核验。④节点 8 接线传 `at`。**未改 TBox 类/属性、DDL、SWRL、BPMN**（纯程序释读逻辑，非本体断言）；`ZiWeiDouShuStarTableTest` **51/51**（新增 `shaCouBoolJudgeTest`）、`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS**。**全网格审计原「条件式·待考 3」全部落地为客观布尔判定；第二十轮收官** | 码道 |
| 2026-11-03 | v1.29 **第二十一轮（三方四正吉曜会照条件客观化为布尔判定）**：将「并见吉曜成格」类会照条件升为可复算布尔判定（《全書》「三方四正会照」通例之客观复算/释读，不改第一权威；`judgeTongYuan` 原 AND 判定不变、不覆盖）。①新增 `judgeSanFangJiYao(mingIdx, at, fourTransform)`——取命宫三方四正 `{+0,+4,+8,+6}` 星曜并集，判五组吉曜（昌曲会命/辅弼拱命/魁钺坐拱/禄马交驰/科权禄拱＝`isHuaInSanFang`）＋「三方无凶杀」（会煞==0），统计会吉/会煞数→「会吉（三方无凶杀）/会煞偏重/吉煞交参/中和（无吉无煞）」综合；`yn(boolean)`输出是/否；mingIdx<0→「命宫未定，不判」。②节点 11 `handleDeterminePattern` 并入 `patternResult` 末尾并 `result.put("sanFangJiYao",...)` 单独出参。③**声明≠落地修正**：BPMN `Task_Pattern` 补 `chartBase/chartSkeleton/auxiliaryStars/miscStars`（handler 实读）→11 input，并新增 `palaceZhiGrid`/`sanFangJiYao` 2 output。**未改 TBox 类/属性、DDL、SWRL**（纯程序释读逻辑，非本体断言；BPMN 仅补 ioMapping）；`ZiWeiDouShuStarTableTest` **52/52**（新增 `sanFangJiYaoJudgeTest`）、真实引擎 P-2 实测 `sanFangJiYao` 键落地、`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS** | 码道 |
| 2026-11-03 | v1.30 **第二十二轮（`judgeTongYuan` 之「加吉/加煞/昌曲…」OR 并列条件客观化·释读）**：为「举例式·取文」行补 OR 并列条件客观复核（《全書》会照通例之复算/释读，不改第一权威；`judgeTongYuan` 原 AND 判定不变、不覆盖）。①新增 `tongYuanOrNote(text, anchorBranch, at)`——先合称（`加吉`＝左辅/右弼/文昌/文曲/天魁/天钺、`加煞`＝擎羊/陀罗/火星/铃星/地空/地劫）后专称（`昌曲/左右/魁钺/禄马/羊陀/火铃`），按锚宫（该星/该宫所落之支）三方四正取星并集，每组「任一命中→是(命中星)/否」；**否定守卫**（含「无吉/无煞」不据「吉曜/煞」字样触发，避免语义反转）。②`judgeTongYuan` 的 `!hasCond` 分支分流：有 OR 复核→单列 `orJudged`（输出「〔OR条件客观复核 Y 条·释读非原文〕」），无→仍入 `pend`。**未改 TBox 类/属性、DDL、SWRL、BPMN**（纯程序释读逻辑，非本体断言）；`ZiWeiDouShuStarTableTest` **53/53**（新增 `tongYuanOrNoteTest`，原 `tongYuanTable`/`tongYuanSupplementRows` 不破）、真实引擎 P-2 实测将 26 条举例式行升为 OR 客观复核（余 50 条仍待考）、`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS** | 码道 |
| 2026-11-03 | v1.31 **第二十三轮（卷三同垣「自含条件」举例式行客观判定）**：对断语自含可判定条件者据本盘给出是/否客观核验（《全書》断语明言之星位/庙陷/四化事实之复算·释读，不改第一权威；无客观条件者仍待考不臆造）。①新增 `tongYuanObjective(text, mingIdx, at, yearGan, skeleton)` 覆盖 10 条：命无正曜（命宫无十四主星）、禄存十二宫中皆入庙（禄存在盘）、禄存守于财宅（落财帛/田宅）、天机加恶杀同宫（同宫见羊陀火铃）、太阳化忌（`SIHUA[yearGan][3]`＝太阳）、贪狼入庙（`brightnessOf`＝庙）、武曲迁移（在迁移宫）、文昌…文科拱照（在命宫三方四正）、右弼左辅…（辅/弼在命或迁）、府相朝垣（天府天相俱在命宫三方四正），输出`〔客观·是/否（依据）·释读〕`。②`judgeTongYuan` 待考分流改为「先自含条件客观→再 OR 复核→余待考」，新增「〔自含条件客观判定 W 条·释读〕」段。**未改 TBox 类/属性、DDL、SWRL、BPMN**（纯程序释读逻辑，非本体断言）；`ZiWeiDouShuStarTableTest` **54/54**（新增 `tongYuanObjectiveTest`）、真实引擎 P-2 实测 `命中 7｜自含条件客观 10｜OR 复核 25｜待考 41`（待考由 50 降至 41）、`AllZiWeiDouShuSuiteTest` **28/28 BUILD SUCCESS** | 码道 |
| 2026-11-03 | v1.32 **第二十四轮（卷三同垣剩余待考行按〔外部通行法／释读／纳音〕补客观判定 + 待考按因分类）**：对批次①自含条件之外的剩余待考行按因分流落地（外部通行法/释读/纳音分级标注·非《全書》原文者明注，不改第一权威；无客观条件者仍待考不臆造）。①新增 `tongYuanObjectiveFull(text, mingIdx, at, yearGan, skeleton, male, fourTransform)`——先调 `tongYuanObjective`（批次①），否则按 20+ 条规则判定：A1 天梁守照吉相逢、A2 巨陷天机、A3 二曲旺宫（文曲庙旺）、A4 贪狼会杀无吉曜、A5 巨日命宫寅位、A6 巨日立申宫、A7 七杀临身终是夭、A8 明禄暗禄、A9 禄存厚重多衣禄、A10 禄逢冲破、A11 生处劫空、A12 劫空临财福、A13 天马四生妻宫、A14 命有吉星官杀重、A15 双禄守命、A16 双禄重返、A17 日月命身居丑未三方无吉、B1 女命端正太阳星、B2 七杀单居福德、C1 身命俱吉、C2 身吉命凶、C3 命弱身强、D1 纳音墓库（水二局，余局「本条不适用」）。②`objNote`/`objNoteExt` 分级标注〔客观〕/〔释读〕/〔外部通行法·非《全書》原文〕。③`judgeTongYuan` 的 `pend` 段新增「待考·按因」统计（运限流年→转节点13-15核／纳音生旺死绝→宜历法节点统一／夹拱法原文未定义／性质比喻→不臆造）。**未改 TBox 类/属性、DDL、SWRL、BPMN**（纯程序释读逻辑，非本体断言）；`ZiWeiDouShuStarTableTest` **55/55**（新增 `tongYuanObjectiveFullTest` 反射覆盖巨日寅位/七杀临身/禄存厚重/生处劫空/命有吉星官杀重/女命端正太阳/身命俱吉/纳音墓库/双禄守命/双禄重返/日月命身居丑未）、真实引擎 P-2 实测 `命中 7｜自含条件客观 33｜OR 复核 25｜待考 18`（待考 41→18）、`AllZiWeiDouShuSuiteTest` **30/30 BUILD SUCCESS** | 码道 |
| 2026-11-03 | v1.33 **第二十五轮（卷三·谈星要论其余断诀逐句核对——羊陀迭并/七杀重逢缺陷修正 + 流年太岁/大限十年祸福/小儿命/男女命同异/谈星要论开篇落地）**：对卷三·谈星要论（c4 L11-177）逐节核「声明⇔落地」，修正 2 缺陷 + 新增 5 客观判定（不改第一权威，纯程序释读）。①**缺陷修正·七杀重逢**：原 `countStar(at,"七杀")>=2` 对单星七杀恒 false（永假）→ 改 `judgeQiShaReFeng(mingIdx,at,flowGan)`（本命七杀守命宫三方四正 + 流年流羊/流陀冲照或对冲）。②**缺陷修正·羊陀迭并**：原「命宫擎羊∧前宫陀罗」仅为羊陀夹命近似 → 改 `judgeYangTuoDieBing`（本命羊/陀在命宫或迁移 + 流年流羊/流陀冲会同宫或对宫）。③`judgeLiuNianTaiSui`（论流年太岁吉凶星杀 c4 L83-84）。④`judgeDaXianShiNian`（论大限十年祸福 c4 L75-76，节点 13）。⑤`judgeXiaoErMing`（论小儿命 c4 L58-61，节点 C）。⑥`judgeNanNvTongYi`（论男女命同异 c4 L55-57，节点 W）。⑦`judgeTanXingYaoLun`（谈星要论开篇 c4 L12-21，节点 11 patternResult）。论阴骘延寿（c4 L85-86）德行劝善无可算条件，标〔不臆造〕。**未改 TBox 类/属性、DDL、SWRL、BPMN**（新判定字符串全部并入既有输出变量）；`ZiWeiDouShuStarTableTest` **56/56**（新增 `juanSanRemainingTest`）、套件非引擎部分 **19/19**（本体推理13+SKOS6）BUILD SUCCESS；引擎门控测试待 MySQL80/Camunda/RabbitMQ 完整环境就绪复跑（当前沙箱 MySQL80 未注册、`sc.exe` 被拦） | 码道 |
| 2026-11-04 | v1.34 **第二十六轮（卷一·諸星問答論 37 问全落地——补全辅/煞/杂曜所主 + 化禄权科忌 + 二总论）**：修正「諸星問答論已落地」之不全——原仅十四主星化气 `STAR_CHARACTER`（14），原文该节共 37 问涵盖辅/煞/杂曜所主。①新增 `STAR_QA_CHARACTER`（20 条：文昌/文曲/左辅/右弼/天魁/天钺/禄存/天马/擎羊/陀罗/火星/铃星/天空/地劫/天伤/天使/天刑/天姚/天哭/天虚 之所主，原文取文）；②新增 `SIHUA_QA`（化禄/化权/化科/化忌所主四条）；③羊陀二星总论／羊陀火铃四星总论静态取文。节点 8 `interpretStarNature` 将之并入既有 `starNature` 输出（不改输出键/BPMN）。**未改 TBox 类/属性、DDL、SWRL、BPMN**（纯取文）；`ZiWeiDouShuStarTableTest` **57/57**（新增 `zhuXingWenDaCoverageTest`）、套件非引擎 **19/19** BUILD SUCCESS；引擎门控测试待 MySQL80/Camunda/RabbitMQ 环境就绪复跑 | 码道 |
| 2026-11-04 | v1.35 **第二十七轮（斗數骨髓賦审计——夹拱格客观落地）**：审计卷一·斗數骨髓賦（c2 L392-476），多数具名格与既有 `STAR_TONGYUAN`／各定局格表重叠已落地，真正缺口为**夹拱格**。新增 `judgeJiaGong(mingIdx, at)`：取命宫前后两邻宫，判原文对星分坐两夹（吉夹 魁钺/日月/昌曲/辅弼/禄；凶夹 空劫→贫贱、羊陀→乞丐），并入节点 11 `patternResult`（不改输出键/BPMN）。第 24 轮卷三「夹拱法原文未定义」之待考由骨髓赋对星集收敛为「已定义·见节点11夹格判」。**未改 TBox 类/属性、DDL、SWRL、BPMN**；`ZiWeiDouShuStarTableTest` **58/58**（新增 `jiaGongJudgeTest`）、套件非引擎 **19/19** BUILD SUCCESS；引擎门控测试待环境就绪复跑 | 码道 |
| 2026-11-04 | v1.36 **第二十八轮（卷一·女命骨髓賦逐句具名格复核）**：新增 `judgeFemaleBoneMarrow(mingIdx, mingStars, at, skeleton)` 对女命骨髓賦（c2 L477-495）13+ 句「星→断语」逐条客观复算（府相→子贵夫贤、武曲→寡宿、贪狼→淫佚、七杀→福不荣、空劫羊陀交临→克害、本宫无正曜→生离克害 等；化禄断依节点6），并入节点 W `femaleReading`（不改输出键/BPMN）。`ZiWeiDouShuStarTableTest` **59/59**（新增 `femaleBoneMarrowTest`）、套件非引擎 **19/19** BUILD SUCCESS；禁区零改动 | 码道 |
| 2026-11-04 | v1.37 **第二十九轮（太微賦等五赋举例式格语二次收敛）**：新增 `judgeTaiweiMingGeExtended(mingIdx, at, mingMain, mingSet, skeleton)` 落地 8 具名格（魁钺同行/禄文拱命/日月夹财/刑囚夹印/财居财位/耗居禄位/辅弼单守/杀临身命加恶杀），`taiweiPendingText` 移除已收敛项。并入节点 11 `patternResult`（不改输出键/BPMN）。`ZiWeiDouShuStarTableTest` **60/60**（新增 `taiweiMingGeExtendedTest`）、套件非引擎 **19/19** BUILD SUCCESS；禁区零改动 | 码道 |
| 2026-11-04 | v1.38 **第三十轮（卷二·十二宫各星吉凶诀逐句交叉校验）**：脚本逐行核验 `palace_classic.tsv`——813 内容行＝章节区间 824−11 章节标题行；与 c3 同行原文逐字比对 mismatch=0；宫位归属 misplaced=0；十二宫齐备。卷二十二宫各星吉凶诀内容行 100% 全量落地、原文逐字准确。新增 `palaceClassicCoverageTest`（12宫/计数/合计813/`palaceStarQuote` 回溯）。`ZiWeiDouShuStarTableTest` **61/61**、套件非引擎 **19/19** BUILD SUCCESS；未改语料/代码逻辑 | 码道 |
| 2026-11-04 | v1.39 **第三十一轮（卷一余节深度核验 + 全量覆盖收束）**：核验十二宫得地/失陷四论（`judgePalaceZhiGrid`）与论人各类 12 格（`judgeLongevity`，c2 L510-544）均全落地且忠实原文；§22 复核确认卷一/二/三判定类章节全部落地（唯余验时法无判定项）。测试补全 `longevityAllCategoriesTest`（补齐 9 类→12 类全覆盖）。`ZiWeiDouShuStarTableTest` **62/62**、套件非引擎 **19/19** BUILD SUCCESS；本轮无生产逻辑改动 | 码道 |
| 2026-11-04 | v1.40 **第三十二轮（术者验时法补充落地 + P-2 环境就绪补跑 + 判定类总账收口）**：①新增 `birthTimeVerifyText(hourBranch)` 落地卷三「定小兒生時訣」（c4 L62-65）＋「論人生時要審的確」（c4 L68-69）——按生时支三组取「顶门/面向」原文＋子亥二时「最难定/罗经定真确」取文，并入节点 C `childReading`（不改输出键/BPMN，纯取文不臆断）；②引用勘误：节点 C「定男女竹蘿三限」由误标「卷三·定小兒生時訣」更正为「卷二·c3 L126-129」；③环境就绪后补跑引擎端到端 P-2：内存引擎 9/9、真实引擎 9/9（含 P-2 女命全链无 incident）、星表 63/63、全量套件 37/37 BUILD SUCCESS；④审查报告新增 §41「覆盖-验证」总账表，判定类缺口 0。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.41 **第三十三轮（逐卷逐节缺失复扫 +「本体方式 vs 硬编码」审计）**：逐卷核对确认**无实质缺失（判定类缺口 0）**；本体资源实测＝12 域 TBox（owl:Class 158／ObjectProperty 148／DatatypeProperty 81）＋SKOS 774＋OBDA 13 映射＋SWRL 4 良构规则（R1/R8/R8b/R21）。**实现分层取证**：仅 brightness_map/star_layout/sihua_map 三表走 OBDA-SPARQL，其余按 `rules.owl` §5 注释「R2–R25 由程序/查表/待考」实现（合规）；`determinePattern` 零本体查询、75 常量表（`STAR_TONGYUAN` 258 行等）、告警 `shaCount>=3` 硬编码。**结论**＝「真实本体 schema/术语/映射/4 规则 + 经论证程序兜底」混合架构，非硬编码缺陷；仅 C-1（告警阈值口径矛盾）/C-2（`deity_rule`/`palace_stem_map` 孤儿表＋幻影注释）待整改，B 类断语表可增强下沉 DB+OBDA。审查报告新增 §42。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.42 **第三十四轮（C-1/C-2 整改落地）**：C-1 告警由裸阈值 `shaCount>=3` 重构为 `judgeAlertTypes` 三类客观凶判据（R18 凶格引动/命宫双煞同宫、R19 天梁受制、R20 疾厄恶曜凑合），`alertTriggered` 仍布尔并入既有变量、不新增 ioMapping，新增 `alertType` 于 structuredOutput；C-2 修正 L890 幻影 `deityRuleMap` 注释、`deity_rule`/`palace_stem_map` 登记为预留查表（未接 OBDA）。验证：compile EXIT 0、星表 64/64（新增 `alertCategoriesTest`）、本体推理 13/13；端到端待环境复跑。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.43 **第三十五轮（告警链路复核·D-1）**：复核告警链路发现 `sendAlert` payload 仍写死占位 alertType，与 §43 C-1 脱节。整改：`aggregateReading` 将 `alertType` 提升为流程变量输出（新增输出变量、不改 ioMapping），`sendAlert` 改读实际 `alertType`（空回落「告警（类别未标注）」）。核验 `brightnessOf/isFallen` 空值安全、其余 `>=2` 为「交临/煞重」释读语义非裸阈值、幻影符号清零。验证：compile EXIT 0、星表 64/64、本体推理 13/13、端到端待环境。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.44 **第三十六轮（D-2 复核·告警类别传递）**：复核 BPMN ioMapping——aggregateReading 未映射 alertType、sendAlert 无 alertType 入参，故 §44 D-1 未生效；BPMN 禁区不加映射。改新增 `extractAlertTypeFromReading`，sendAlert 从已映射 `reading` 解析告警类别（空回落未标注）。验证：compile EXIT 0、星表 65/65、本体推理 13/13。审查报告 §44 勘误 + 新增 §45。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.45 **第三十七轮（D-3 贯通 + D-4 递归缺陷修正）**：D-4 `wuhuYinGan` OBDA 未命中回退自我递归→改 `WUHU_YIN_GAN[floorMod(yearGan,5)]`（单测 yunxianRecalcHelpers 转绿，横向排查唯此一处）；D-3 控制器暴露 `alertType`、前端展示真实类别，告警类别全链贯通。验证：compile EXIT 0、星表 65/65。审查报告新增 §46。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.46 **第三十八轮（C-2 孤儿表接入 OBDA）**：OBDA 新增 `rules#PalaceStemRule_{map_id}` / `rules#DeityRule_{rule_id}` 两映射（映射 13→15）；worker 新增 5 缓存 `ONTO_LUCUN/LIUYANG/LIUTUO/DEITY_12/WUHU`，`loadStateFromObda()` 追加 2 条 SPARQL，新增 `lucunBranch/liuyangBranch/liutuoBranch/deityName/wuhuYinGan`（缓存优先回退内置表、非递归 floorMod），替换 15 处直引，`verifyConsistency()` 扩充流曜/五鼠遁/十二神交叉校验。验证：compile EXIT 0；Ontop 实测 `PalaceStemRule=5`、`DeityRule=42` 与内置表全等。审查报告新增 §47。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、EnvPrepare**；OBDA 增 2 映射属授权整改项 | 码道 |
| 2026-11-04 | v1.47 **第三十九轮（孤儿表接入加固）**：十二神定序由 `TreeMap<String>` 改 `TreeMap<Integer>`（`trailingInt(ruleId)` 数值序，消 `_9`/`_10` 字符串序颠倒）；新增 `verifyObdaTableCoverage()` 启动断言（DDL `CREATE TABLE` vs OBDA `FROM` 比对孤儿表，全覆盖 INFO/有缺 WARN，不阻断）；新增 `trailingIntNumericOrderTest`。验证：compile EXIT 0、星表 **66/66**、覆盖自检 DDL=14/映射=14/**缺 0**。审查报告新增 §48。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、EnvPrepare、OBDA 映射内容**；禁区零改动 | 码道 |
| 2026-11-04 | v1.48 **第四十轮（端到端全量回归复核）**：手工拉起 MySQL3306/Ontop8080/RabbitMQ5672/Camunda9080+26500 后复跑 `AllZiWeiDouShuSuiteTest` = **39/39 BUILD SUCCESS**（Ontology 13、BpmnInMemory 9、BpmnRealEngine 9、SwrlRabbitMq 2、SKOS 6）；StarTable 单跑 **66/66**；Ontop 就绪三元组 6405。关键踩坑：Ontop 端点须带 `-x ontology/catalog-v001.xml` 解析 `tbox` import。C-1/D-1~D-4 真实引擎全链**无 regression**；端到端补跑遗留项闭环。B 类断语大表下沉为禁区增强、待授权。审查报告新增 §49。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.49 **第四十一轮（逐卷逐节独立复审 + 本体/硬编码取证）**：纯复审未改代码。实测本体＝具名 `owl:Class` 199（唯一158）/`ObjectProperty`148/`DatatypeProperty`81、SKOS 774/388、SWRL 4（R1/R8/R8b/R21）、OBDA 15→14 表；覆盖复核 29 方法/测试/SourceNote 137/tsv 813 行/0 stub → 缺口 0。**本体化分层**：schema+术语真本体；运行时真本体＝5 张查表(Ontop SPARQL)+R21；格局/断语=程序+常量表（`rules.owl §5` 兜底）；历法/纳音已映射却走 Java 内置表（冗余）。发现 B-1 历法未用足 OBDA、B-2 语料未入库、B-3 煞星集 4/6 两套、B-4 大表下沉待授权；**C 类缺陷 0**。审查报告新增 §50。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.50 **第四十二轮（B-3 煞星集合归一 + 修复天空/地空漏判 + B-1 澄清）**：①真缺陷（C类）——安星为「天空＋地劫」无「地空」，3 处判定（大限十年祸福/加煞/父母妻子劫空）误用「地空」致天空漏判，已统一改 `LIU_SHA` 并补「天空」、保留旧名兼容；②单一真源新增 `SI_SHA`/`LIU_SHA`，`YUNXIAN_SHA_STARS=LIU_SHA`、`SHA_STARS=SI_SHA.toArray`，清 6 处内联字面量；新增 `shaStarSetSingleSourceTest`；③B-1 经 DB 实测澄清（`true_solar_time`/`leap_month` 各仅 1 示例行不可作真源）→历法以 Java 为运行时真源、OBDA 供外部 KG。验证：compile EXIT 0；星表 **68/68**；全量端到端 **39/39 BUILD SUCCESS**。审查报告新增 §51。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.51 **第四十三轮（B-2 可追溯性闭环——ABox `SourceNote` ↔ §41 台账双向交叉核对）**：①ABox `diagnosis:SourceNote` 实测 **137** 条（J1 23/J1Q 35/J2 59/J3 20），属性 137/137 齐（`label`/`sourceBook`恒「紫微斗數全書」/`sourceVolume`），**0 条含原文行号**→追溯仅章节级。②双向核对结论：卷三 20/20、卷一 19 目、卷二 30 安星诀+813 行**全对齐**→落地缺口 0。③声明层 3 类差异：D-1 台账未单列已落地 13（序/二总论/诸格论4 + 落閑/流曜/生剋制化/南北斗分屬/定公卿等5）；D-2 反向缺口 1（安飛天三殺已实现无 SN）；D-3 粒度差 2 问（§35 記37問 vs ABox 35 SN_J1Q+2总论）。④声明质量：Q-1 `SN_J1Q_16..35` 繁简混用 20、Q-2 `SN_J2_48..59` label≠volume 12（只登记）。⑤证据边界：原语料未入库，行号级无法第三方复读；彻底闭环建议入库语料+`SN_*` 增 `diagnosis:corpusRef`（需授权）。审查报告新增 §52；§51 重复编号勘误（前段归 §50 附录A）。**未改 TBox 类/属性、DDL、SWRL、BPMN、Openllet、OBDA、EnvPrepare、ABox/SKOS**；纯只读取证，禁区零改动 | 码道 |
| 2026-11-04 | v1.52 **第四十四轮（遗留项闭环：Q-1/Q-2 ABox 声明规范化 + S-1 回归断言 + B-4 同垣断语大表下沉）**：用户授权动 DDL/OBDA/ABox/SKOS 数据资产后落地：①**Q-1** `SN_J1Q_16..35` 20 条 label/sourceVolume 简体→繁体（10 字形映射，仅转字形）；②**Q-2** `SN_J2_48..59` 12 条 sourceVolume 对齐 label（补括注）→ 不变量 **137/137 `label==sourceVolume`、0 简体字**；③**S-1** SKOS 测试固化：SourceNote 140（137 `SN_*`+3 聚合）、`SN_*`=137、均须 sourceBook+volume、137 个须中文 label+label==volume+无简体字；④**B-4** `STAR_TONGYUAN`258 行下沉：DDL 14→15 表（`tongyuan_rule`）、data.sql +258 INSERT（逐行与内置等值同序）、OBDA 15→16 映射（`pattern:TongYuanRule` 全 IRI + 6 属性，`FROM tongyuan_rule`）、worker 加 `ONTO_TONGYUAN` 缓存 + 第 6 SPARQL + 兜底/完整性258/一致性三重自检 + `judgeTongYuan` 缓存优先（TBox 非必需，循 `DeityRule` 先例）。**验证**：compile EXIT 0；StarTable **69/69**、SKOS **7/7**、全量端到端 **40/40 BUILD SUCCESS**；MySQL `COUNT(*)=258`；Ontop CLI 虚拟 KG 计数 **258**+样例 TY_0001/0002/0003 一致（省略 `-t` 绕开 abox import 解析）。审查报告新增 §53；应用架构 v51。**未改 TBox 类/属性、SWRL、BPMN ioMapping、Openllet、OntopOBDAHandler、EnvPrepare**；改动限 DDL/OBDA/ABox/SKOS+worker 装载分支+测试断言，禁区零改动 | 码道 |
| 2026-11-04 | v1.53（第四十五轮：14 表断语/星情大表下沉 DB+OBDA 全链） | `STAR_CHARACTER/STAR_XINGXING/STAR_QA_CHARACTER/VERIFY_STAR_TRAITS/VERIFY_BUREAU_TRAITS/TAISUI_XIAOXIAN/PALACE_ZHI_GE/PO/FUGUI/PINJIAN/IDLE_VERSE/IDLE_STAR/BRIGHTNESS_VARIANTS/LIMIT_TABOO` 共 **178 行**下沉：DDL **15→29 表**、data.sql **+178 值行**（逐行逐列与内置等值同序）、OBDA **16→30 映射**（统一 `rowSeq/col0..N` + `pattern:<Class>_{seq}` 全 IRI）。worker 通用装载 `loadPatternRows`/`expandTail`/`rows`/`diffRows` + 覆盖率/完整性/一致性三重自检扩至 23 类（缓存优先回退内置同源兜底不变）。**验证**：compile EXIT 0；StarTable **70/70**；全量 **110/110 BUILD SUCCESS**；MySQL 14 表计数全等；Ontop 端点重启后 `星情/断语=14/25/20/14/2/12/12/12/12/10/9/8/2/12`、`断语星情 异0 ✔`。审查报告 §54；应用架构 v52。**未改 TBox 类/属性、SWRL、BPMN ioMapping、Openllet、OntopOBDAHandler、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.54（第四十六/四十七轮：硬编码复查 + SIHUA_QA/STAR_BLOCK_ALL 下沉 + 清理 ELEM_NAME） | **复查**：29 表↔30 映射↔23 装载域三方对齐，无孤儿表/死映射/残留直引/直接 JDBC；修复 `tongyuan_rule` 缺 `DELETE` 幂等缺陷（双跑 EXIT0、258 行稳定）。**下沉**：`SIHUA_QA`(4)/`STAR_BLOCK_ALL`(36) 按 B-4 全链——DDL **29→31 表**、data.sql **DELETE 29→31**、OBDA **30→32 映射**（`pattern:SihuaQa`/`pattern:StarBlockAll` 全 IRI）、worker 加 `ONTO_SIHUA_QA`/`ONTO_STAR_BLOCK_ALL` + 通用装载 + 覆盖率自检 25 类 + 完整性 4/36 + 一致性单列比对 + 读取点缓存优先回退。清理与 `ELEM_NAMES` 重复零引用的 `ELEM_NAME`；保留带本体语义对照的 `TEN_GRADE_NAMES`/`LONGEVITY_TYPES`。**验证**：compile EXIT 0；StarTable **70/70**；全量 **40/40 BUILD SUCCESS**；MySQL 31 表、sihua_qa=4/star_block_all=36；Ontop CLI 两映射正确；端点重启后 `四化所主=4 星名清单=36`、`一致性 异0 ✔`、`DDL 表=31 映射来源表=31 缺0 ✔`。审查报告 §55/§56；应用架构 v53。**未改 TBox 类/属性、SWRL、BPMN ioMapping、Openllet、OntopOBDAHandler、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.55（第四十九轮：C 层资源语料入库——palace_classic 813 行 → classic_corpus 表 + loadPalaceClassic DB 优先） | **落实 §57.4 唯一剩余「文本数据未入库」方向（C 层语料→DB）**：`palace_classic.tsv`（813 行卷二·十二宫各星吉凶诀原文，3 列宫/行号/原文）全量迁入 `classic_corpus`——DDL **31→32 表**、data.sql **+813 INSERT +1 DELETE**、OBDA **32→33 映射**（`pattern:ClassicCorpus_{seq}` 全 IRI + rowSeq/col0/col1/col2，`FROM classic_corpus`，TBox 非必需循 `DeityRule` 先例）。worker 加 `ONTO_CLASSIC_CORPUS` + `loadPatternRows(h,"ClassicCorpus",3)` + 覆盖自检 **26 类** + 完整性 813；`loadPalaceClassic()` 改 **OBDA 优先 / 资源 TSV 兜底**。保真测试 `classicCorpusArtifactsTest`（DDL/OBDA/逐行四列==TSV）。**验证**：compile EXIT 0；StarTable **71/71**；全量 **40/40 BUILD SUCCESS**；MySQL `classic_corpus=813`、`total_tables=32`；端点重启后 `卷二语料库=813 行`、`覆盖自检 26 类全自 OBDA 无回退`、`DDL 表=32 映射来源表=32 缺0 ✔`。审查报告 §58；应用架构 v54。**未改 TBox 类/属性、SWRL、BPMN ioMapping、Openllet、OntopOBDAHandler、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.56（第五十轮：D 层「方法体内联引文」审计闭环——126 处分类台账化，定性不入库） | **承「继续完成剩余工作」审计 §58.3 遗留 D 层**：逐行按花括号深度区分常量块/方法体，排除常量数组体/log/注释/`*Prompt*` 方法，得方法体内 ≥20 汉字中文串 **126 处**。分类：T 释读/说明/模板 **73**、Q 格局·断语原文·判据紧耦合 **38**（`handleDeterminePattern`/`judgeTaiweiMingGe*`/`judgeLongevity`/卷三行限歌等）、P 应用提示语 **13**、R 正则/逻辑 **2**。**结论**：D 层无一是「漏下沉的数据表」；Q 类判据为运行期星盘算法（如 `if(星盘条件){hit.add("格名（出典）：…「原文」")}`，静态表无法承载），`judgeMingGongGe` 原文块已从 `classic_corpus` 读取（内联仅模板/正则），印证「原文存 DB、判据留代码」分工已实现；判据紧耦合 + 取文不改写决定 D 层就地保留，以台账（126 行）实现可追溯闭环。至此 A/C 零遗漏入库、B 既定不双源、D 台账化、E 解码字典。审查报告 §59；台账 `ZiWeiDouShu应用-D层内联引文台账.md`；应用架构 v55。**未改任何代码/DDL/OBDA/ABox/SKOS/TBox/SWRL/BPMN/Openllet/EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.57（第五十一轮：Q 类格局/断语原文数据化——pattern_verse 表 27 条入库 + verse() 缓存优先取文） | **承「处理 Q 类」落地 §59 遗留**：Q 类 38 条中「完整固定展示串」27 条入 `pattern_verse`（handleDeterminePattern 9 / judgeTaiweiMingGe 4 / Extended 9 / judgeLongevity 4 / XINGXIAN_GE 整段 1 / taiweiPendingText 整段 1）；DDL **32→33 表**、data **+27**、OBDA **33→34 映射**。worker 加 `ONTO_PATTERN_VERSE(_MAP)` + `verse(key,fallback)` 缓存优先回退内置字面量，27 处调用点换源、判据算法原样、输出逐字保真；覆盖自检 27 类、完整性 27。余 11 条动态/纯释读 Q 类保留代码（模板+变量静态表无法承载）。**验证**：compile EXIT 0；StarTable **72/72**；BpmnInMemory **9/9**（`格局原文登记=27`、`DDL=33/映射=33 缺0`）；全量 **40/40 BUILD SUCCESS**；MySQL 27/33/27。审查报告 §60；应用架构 v56。**未改 TBox 类/属性、SWRL、BPMN ioMapping、Openllet、OntopOBDAHandler、EnvPrepare**；禁区零改动 | 码道 |
| 2026-11-04 | v1.58（第五十二轮：第四轮「遗漏+硬编码」全项目复查——纯审计·零改动） | **承「再次查看是否还有遗漏，是否有硬编码」做第四轮系统审计**：①**三方对齐** DDL **33 表**（DROP 33）↔ OBDA **34 映射 / 33 distinct 来源表** ↔ data.sql **DELETE 33 / INSERT 31**；`DDL∖OBDA` 与 `OBDA∖DDL` **差集皆空**（零孤儿表/零死映射）；缺 INSERT 仅 `alert_flag`(运行时标志 0 行)/`case_record`(BPMN 写入命例)，设计如此。②**DB 33 表精确行数**全列（pattern_verse=27/classic_corpus=813/tongyuan_rule=258/brightness_map=240/star_layout=150/sexagenary_cycle=60/case_record=48/alert_flag=0…）。③**零数据漂移**：`verse()` 27 调用点（26 字面量+1 常量同源），回退字面量 vs DB `display_text` **26/26 逐字一致、不一致 0**；运行期 `覆盖自检 27 类全自 OBDA 无回退`、`DDL 表=33 映射来源表=33 缺0 ✔`。④**资源目录仅 3 文件**（palace_classic.tsv 已入 classic_corpus 仅兜底）。⑤**Java 中文长串**：worker 217 / Controller 1（提示语）/ 其余 0；花括号 `CONST 85/METHOD_INLINE 123/PROMPT 9/LOG 8`。⑥**8 张 worker 零引用表均已在 OBDA 映射**（外部虚拟 KG/运行表，非遗漏）。**结论**：零孤儿表/零死映射/零幂等隐患/零数据漂移，硬编码残余全落预期层（A/C 零遗漏入库、B-1 既定不双源、D 层 Q27 入表余 11 不可静态化、T/P/R 非数据、E 解码字典保留）。**本轮纯审计、未改任何代码/DDL/data.sql/OBDA/ABox/SKOS/TBox/SWRL/BPMN/Openllet/EnvPrepare**；禁区零改动。审查报告 §61 | 码道 |
