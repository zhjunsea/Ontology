# PlumBlossom应用 — 本体核心设计（核心类、属性与关系）

> **用法**：本文件产出第 4 步。**闸门③**：业务人员确认核心类/属性/关系后方可生成第 6 步本体文件。本文件并承载第 6 步**闸门⑦**（典型 ABox 数据确认）的留痕（见 §7 与文末「闸门⑦留痕」）。
> **基线**：`OntologyFrameworkExample/design/Example应用-本体设计.md`（TBox / SWRL 章节）。

| 项目 | 内容 |
|---|---|
| 应用 | PlumBlossom（梅花易数占断） |
| 文档日期 | 2026-10-20 |
| 当前版本 | **v3.1**（v3 基础上经邵康节回原著逐条核验与自洽性修订；对齐第 2 步流程、第 3 步 v3 顶层设计） |
| 上游 | 《PlumBlossom应用-本体顶层设计》（**闸门②已确认，v3 定稿**） |
| 关联流程 | 《PlumBlossom应用-流程设计》（闸门①已确认，v11 定稿） |
| 产出本体（规划） | `ontology/plumblossom-all.owl`（单文件，含典型 ABox） |

> 依据（**强制**）：每条类/属性/约束/SWRL 规则均标注出处（★铁律 10/12/14）。自节点 2 起的方法与数据追溯至原著 `design/故宫珍藏善本：梅花易数 ([宋] 邵康节 著 周浩良 整理).epub`（卷之一~五 + 附录）。IRI 前缀简称：`core:`/`div:`/`yijing:`/`rel:`/`sym:`/`ome:`/`jud:`/`cal:` 对应顶层设计 §4.1 命名空间。

> **v2 修订说明**：初稿只覆盖「流程节点自身所需」概念；经复核发现**流程运转还需一批支撑概念**（周易卦辞/爻辞、八卦全属性、干支历法、四不占戒律），v2 曾按「不新增命名空间」归入既有 8 个 TBox 子本体。
> **v3 修订说明**：第 3 步闸门②确认的 v3 顶层设计**已把「周易经传」单列**为 `YijingDomain` 并新增 `yijing` 命名空间与 `plumblossom-yijing.owl`；本版将 `HexagramDictum`（周易卦辞）、`LineDictum`（周易爻辞）、`HexagramSequence`（卦序）、`OppositeNature`（诸卦反对性情）**迁至 `yijing:`**，并同步前缀。
> **v3.1 修订说明（回原著核验 + 自洽性修订）**：以邵康节视角，将本设计逐条**回原著核验**（每卷每篇逐项命中，见 §10），确认**无编造、出处有据**；同时修正 v3 的**内部自洽性缺陷**——① 统一「一撮金爻诀」类名为 `jud:YicuoJinAdmonition`；② 体系标记由 `div:` 改归 `jud:`（`jud:divinationSystem`/`jud:hasMethod`），使 R11/R12 定义域闭合；③ 补齐 SWRL 中引用但未定义的谓词（`jud:processFlag`/`jud:isMajorResult`/`cal:datumFivePhase`/`cal:naYinPhase`），并将 `trigramPairMap` 明确为**内置映射表**；④ 修正 R9 主体归属（由 `JudgementDomain` 承载吉凶映射）；⑤ 修正 §7 中 ABox 断言属性名（`hasHexagramDictum`/`hasLineDictum`）。

---

## 1. 核心类体系（明细）

> 展开自《本体顶层设计》§5 顶层类框架；主干分支不变，细化为**可落库的类层级**。标注 `★` 者为流程运转**支撑概念**。命名空间对齐第 3 步 v3。

```
PlumBlossomEntity（本体事物根 · core:PlumBlossomEntity）
│
├── div:DivinationInput（起卦域）
│   ├── div:DivinationMethod（起卦法类别，17 子类；含枚举 div:methodCode）
│   │   ├── div:NumberBasedMethod（先天·以数起卦）
│   │   │   ├── div:YearMonthDayHourMethod（年月日时起例）
│   │   │   ├── div:ObjectCountMethod（物占数）
│   │   │   ├── div:SoundCountMethod（声音占·闻声次数）
│   │   │   ├── div:CharacterMethod（字占，含 2-字占细分 1~11 字以上各子法）
│   │   │   ├── div:ZhangChiMethod（丈尺占）
│   │   │   ├── div:ChiCunMethod（尺寸物占）
│   │   │   └── div:YicuoJinMethod（一撮金法·二字笔画起卦）
│   │   └── div:ImageBasedMethod（后天·端法·以象起卦）
│   │       ├── div:PersonObserveMethod（为人占）、div:SelfObserveMethod（自己占）
│   │       ├── div:MovingThingMethod（占动物）、div:StillThingMethod（占静物）
│   │       ├── div:ThingHexagramMethod（物卦起例·端法后天）
│   │       ├── div:WindMethod（风觉占）、div:BirdMethod（鸟占）
│   │       ├── div:HeardSoundMethod（听声音占）
│   │       └── div:ShapeMethod（形物占）、div:ColorMethod（验色占）
│   ├── div:Precept（心法戒律·四不占；4 个体：不诚/无事/不义/重卦）★支撑【S1】
│   └── div:InputDatum（起卦输入数据；时间/数字/文字/物象/方位/外应等）
│
├── div:DivinationSubject（占断事项域）
│   └── jud:SubjectCategory（事项类别，18 子类，原著卷之二十八章占例）
│         （天时/人事/家宅/屋舍/婚姻/生产/饮食/求谋/求名/求财/交易/出行/
│           行人/谒见/失物/疾病/官讼/坟墓）
│
├── core:HexagramDomain（卦象域）
│   ├── core:Trigram（八卦；8 子类：Qian/Dui/Li/Zhen/Xun/Kan/Gen/Kun）★属性补齐
│   ├── core:Direction（八方；8 子类：东/南/西/北/东南/西南/东北/西北）★支撑【S3】
│   ├── core:Line（爻）
│   │   ├── core:YinLine（阴爻）、core:YangLine（阳爻）
│   │   └── core:MovingLine（动爻；一卦一爻动）
│   └── core:Hexagram（重卦，由上下卦组成）
│         ├── core:OriginalHexagram（本卦）、core:MutualHexagram（互卦）
│         ├── core:ChangedHexagram（变卦）
│         └── core:SixBitHexagram（六十四卦名录，术语型）——其卦辞/爻辞参见 `yijing:` 域
│
├── yijing:YijingDomain（周易经传域）★支撑【S2/S5/S6】（对齐顶层设计 §5，v3 单列）
│   ├── yijing:HexagramDictum（周易卦辞）——六十四卦各一（术语型）
│   ├── yijing:LineDictum（周易爻辞）——三百八十四爻各一（术语型）
│   ├── yijing:HexagramSequence（卦序）——六十四卦次第 1~64
│   └── yijing:OppositeNature（诸卦反对性情）——杂卦两两反对之卦义
│
├── rel:YiliDomain（易理域）
│   ├── rel:FivePhase（五行；5 子类：金/木/水/火/土）
│   ├── rel:ShengKeRelation（生克关系；2 子类：Sheng 生 / Ke 克）
│   ├── rel:BodyUseRelation（体用关系：体卦·用卦·体用配置）
│   ├── rel:VitalityState（卦气旺衰；5 子类：旺/相/休/囚/死）
│   ├── rel:TrigramNature（八卦性情；8 值：健/顺/起/止/陷/丽/说/入）★支撑【S3】
│   ├── rel:TrigramOrder（八卦定阴阳次序；阴阳 + 长幼男女，8 个体）★支撑【S3】
│   └── sym:ImageSymbol（万物类象；按八卦与类象维度组织）
│
├── ome:OmenDomain（外应域）
│   ├── ome:ThreeEssentials（三要：耳/目/心）
│   └── ome:TenResponses（十应；10 子类：正/互/变/方/日/刻/外/天时/地理/人事）
│
├── cal:CalendarDomain（历法域）★支撑【S4】
│   ├── cal:HeavenlyStem（十天干；10 子类：甲…癸）
│   ├── cal:EarthlyBranch（十二地支；12 子类：子…亥）
│   ├── cal:SexagenaryCycle（六十甲子）
│   ├── cal:NaYin（纳音；60 组，含 30 名）
│   ├── cal:LifeStage（五行长生；5：木生在亥/火生于寅/金生于巳/水土长生居申）
│   └── cal:CalendarDatum（历法数据：干支/季节/月令）
│
└── jud:JudgementDomain（断验域）
    ├── jud:Auspiciousness（吉凶等级；5 子类：大吉/吉/平/凶/大凶）
    ├── jud:YicuoJinAdmonition（一撮金爻诀断辞；附等级 jud:YicuoJinGrade 6 值）
    ├── jud:ResponsePeriod（应期：类型 + 范围）
    └── jud:SelfCultivationAdvice（修身建议）
```

> 说明：`div:DivinationSubject` 与 `jud:SubjectCategory` 同指「事项类别」，前者为域归属类、后者为枚举实现（合并于 `jud:`）。`jud:YicuoJinGrade` 为附录一撮金六等（上上/上中/中中/中平/下中/下下），经 SWRL 映射到 `jud:Auspiciousness` 五级（见 §5）。
> **两类「辞」的归属**：`yijing:HexagramDictum`/`yijing:LineDictum` 为**《周易》经传之辞**（外部经典，按先天/后天体系条件引用）；`jud:YicuoJinAdmonition` 为**附录·一撮金之爻诀**（梅花易数自成一系，恒引用）。二者来源、引用条件不同，**不得混为一类**（对齐流程§7-8、§7-12）。

### 1.1 支撑域说明（流程运转所需的周易卦爻辞 / 八卦全属性 / 历法 / 戒律）★

> 本节逐项说明支撑概念**为何为流程运转所必需**，并给出原著出处（**出处均经回原著核验命中，见 §10**）。

| 支撑类/属性 | 用途（对应流程环节） | 原著出处（已核验） |
|---|---|---|
| `yijing:HexagramDictum`（周易卦辞） | 节点 12「成卦之后先看《周易》爻辞」、断验引卦辞辅助；先天体系不引、后天体系引 | 卷之二·占卜总诀「成卦之后先看《周易》爻辞，以断吉凶」；卷之二·先天后天论「后天则用爻辞，兼用卦辞」 |
| `yijing:LineDictum`（周易爻辞） | 节点 12 断吉凶辅助；节点 10 占物类例「观其爻辞」（如得乾「潜龙勿用」为不可用之物）；节点 16 物类判断 | 卷之三·占物类例「凡看物数，看其成卦，观其爻辞」；卷之二·占卜总诀 |
| `core:Direction`（八方）+ 八卦方位 | 节点 2 配「八卦方位图」；节点 3 端法后天「方位为下卦」；节点 11 方应/地理应 | 卷之一·八卦方位图「右离南坎北，震东兑西，人则介乎其中」（原文仅列四正，余四隅巽东南/乾西北/艮东北/坤西南依指南§三补全，见 §9-7） |
| 八卦阴阳/长幼（`core:trigramYinYang`/`core:trigramFamilyRole`） | 节点 4「八卦定阴阳次序」（定人物长幼男女之应） | 卷之三·八卦定阴阳次序（乾父/震长男/坎中男/艮少男；坤母/巽长女/离中女/兑少女） |
| `rel:TrigramNature`（八卦性情） | 节点 9/10/16「卦义性情」；占婚姻八卦性情；杂卦反对性情 | 卷之五·论八卦性情「乾健也、坤顺也、震起也、艮止也、坎陷也、离丽也、兑说也、巽入也」 |
| 卦象符（`core:trigramSymbol`） | 前端展示八卦/六十四卦卦象；节点 3 成卦 | 卷之五·八卦取象「乾为天、坤为地、震为雷、巽为风、坎为水、离为火、艮为山、兑为泽」 |
| `yijing:OppositeNature`（诸卦反对性情） | 节点 10/16 卦义性情（乾刚坤柔/比欣师忧…） | 卷之三·诸卦反对性情「杂卦性情反对是」 |
| `cal:HeavenlyStem`/`cal:EarthlyBranch`+配属 | 节点 8 卦气旺衰（干支所属五行）、节点 13 应期（干支日/卦气） | 卷之一·十天干/十二地支；卷之五·天干地支所属五行 |
| `cal:NaYin`/`cal:SexagenaryCycle` | 节点 13 应期「以纳音属断应期之应」 | 卷之五·六十甲子纳音歌 |
| `cal:LifeStage` | 节点 13 应期、长生（木生在亥等） | 卷之五·五行相生地支「木生在亥、火生于寅、金生于巳、水土长生居申」 |
| `div:Precept`（四不占戒律） | 节点 1 心法戒律校验（四不占） | 卷之一·观梅占「不动不占，不因事不占」推四不占；附录·一撮金「诚心祈祷、不可再占」 |

> **卦象符归属说明**：第 3 步 §5 将「卦象符」随 `YijingDomain.HexagramSequence` 一并登记；第 4 步细化为 `core:trigramSymbol`（**八卦取象之数据属性**，定义域 `core:Trigram`），语义为「取象」（卷之五·八卦取象），作为八卦自身属性，与卦序（`yijing:HexagramSequence`，六十四卦次第）分列。

---

## 2. 对象属性

| 属性 | 特性（函数/传递/…） | `owl:inverseOf` | 定义域 | 值域 | 出处 |
|---|---|---|---|---|---|
| core:hasUpperTrigram | Functional | core:isUpperTrigramOf | core:Hexagram | core:Trigram | 卷之一·卦数起例（上卦下卦相重） |
| core:hasLowerTrigram | Functional | core:isLowerTrigramOf | core:Hexagram | core:Trigram | 卷之一·卦数起例 |
| core:hasLine | — | core:isLineOf | core:Hexagram | core:Line | 卷之一·爻以六除（重卦六爻） |
| core:hasMovingLine | Functional | core:isMovingLineOf | core:Hexagram | core:MovingLine | 卷之一·爻以六除（一爻动） |
| core:mutualOf | Functional | core:hasMutualHexagram | core:MutualHexagram | core:OriginalHexagram | 卷之一·互卦止用八卦 |
| core:changedFrom | Functional | core:hasChangedHexagram | core:ChangedHexagram | core:OriginalHexagram | 卷之一·爻以六除（动爻反转） |
| core:hasDirection | Functional | core:isDirectionOf | core:Trigram | core:Direction | 卷之一·八卦方位图 |
| core:hasTrigramNature | Functional | core:isTrigramNatureOf | core:Trigram | rel:TrigramNature | 卷之五·论八卦性情 |
| core:hasTrigramOrder | Functional | core:isTrigramOrderOf | core:Trigram | rel:TrigramOrder | 卷之三·八卦定阴阳次序 |
| yijing:hasHexagramDictum | Functional | yijing:isHexagramDictumOf | core:SixBitHexagram | yijing:HexagramDictum | 卷之二·先天后天论（后天兼用卦辞） |
| yijing:hasLineDictum | — | yijing:isLineDictumOf | core:SixBitHexagram | yijing:LineDictum | 卷之二·占卜总诀（先看周易爻辞） |
| yijing:oppositeOf | Symmetric | yijing:oppositeOf | core:SixBitHexagram | core:SixBitHexagram | 卷之三·诸卦反对性情（杂卦反对两两相对） |
| rel:hasBodyTrigram | Functional | rel:isBodyTrigramOf | rel:BodyUseRelation | core:Trigram | 卷之三·体用互变之诀（动者为用、静者为体） |
| rel:hasUseTrigram | Functional | rel:isUseTrigramOf | rel:BodyUseRelation | core:Trigram | 卷之三·体用互变之诀 |
| rel:hasFivePhase | Functional | rel:isFivePhaseOf | core:Trigram | rel:FivePhase | 卷之一·八宫所属五行 |
| rel:generates（生） | Transitive | rel:generatedBy | rel:FivePhase | rel:FivePhase | 卷之一·五行生克 |
| rel:overcomes（克） | Transitive | rel:overcomeBy | rel:FivePhase | rel:FivePhase | 卷之一·五行生克 |
| rel:hasVitalityState | Functional | rel:isVitalityStateOf | core:Trigram | rel:VitalityState | 卷之二·衰旺论；卷之三·体用衰旺之诀 |
| cal:hasStem | Functional | cal:isStemOf | cal:CalendarDatum | cal:HeavenlyStem | 卷之一·年月日时起例 |
| cal:hasBranch | Functional | cal:isBranchOf | cal:CalendarDatum | cal:EarthlyBranch | 卷之一·十二地支 |
| cal:stemFivePhase | Functional | — | cal:HeavenlyStem | rel:FivePhase | 卷之五·天干地支所属五行 |
| cal:branchFivePhase | Functional | — | cal:EarthlyBranch | rel:FivePhase | 卷之五·天干地支所属五行 |
| cal:hasNaYin | Functional | cal:isNaYinOf | cal:SexagenaryCycle | cal:NaYin | 卷之五·六十甲子纳音歌 |
| **cal:naYinPhase** | Functional | — | cal:NaYin | rel:FivePhase | 卷之五·六十甲子纳音歌（纳音属） |
| **cal:datumFivePhase** | Functional | — | cal:CalendarDatum | rel:FivePhase | 卷之五·天干地支所属五行（供旺衰/应期取值） |
| cal:hasLifeStage | Functional | — | rel:FivePhase | cal:LifeStage | 卷之五·五行相生地支（长生） |
| sym:hasImageSymbol | — | sym:isImageOf | core:Trigram | sym:ImageSymbol | 卷之一·八卦万物属类/八卦万物类占 |
| sym:belongsToDimension | — | — | sym:ImageSymbol | sym:SymbolDimension | 卷之一·八卦万物类占（28 分栏） |
| sym:modernMapping | — | — | sym:ImageSymbol | sym:ModernEntity | 流程§7-5（现代映射扩展层，默认关闭） |
| ome:hasThreeEssential | — | — | ome:OmenDomain | ome:ThreeEssentials | 卷之二·三要灵应篇 |
| ome:hasTenResponse | — | — | ome:OmenDomain | ome:TenResponses | 卷之二·十应目论；卷之三·占卜十应诀 |
| jud:belongsToCategory | — | — | div:DivinationInput | jud:SubjectCategory | 卷之二·十八章占例（以类而推） |
| **jud:hasMethod** | Functional | — | jud:JudgementDomain | div:DivinationMethod | 卷之一·起卦诸篇（占断实例所据起卦法；供 R12 体系判定） |
| jud:hasAuspiciousness | Functional | jud:isAuspiciousnessOf | jud:JudgementDomain | jud:Auspiciousness | 卷之二·体用总诀/占卜总诀 |
| jud:hasYicuoJinAdmonition | — | jud:admonitionOfHexagram | jud:JudgementDomain | jud:YicuoJinAdmonition | 附录·一撮金（以爻诀断之） |
| jud:hasYicuoJinGrade | Functional | — | jud:YicuoJinAdmonition | jud:YicuoJinGrade | 附录·一撮金（逐爻吉凶等级） |
| jud:hasResponsePeriod | Functional | jud:isResponsePeriodOf | jud:JudgementDomain | jud:ResponsePeriod | 卷之三·克应/断占总诀 |
| jud:hasAdvice | — | — | jud:JudgementDomain | jud:SelfCultivationAdvice | 卷之二·观物洞玄歌（立意，应用层增益） |

> **内置映射表（非本体个体）**：`trigramPairMap` 为「上卦×下卦 → 六十四卦名」的内置映射（供 R15 合成卦名），实现于查询/规则层，**不建模为本体个体**（避免 64×64 冗余；名录本身已由 `core:SixBitHexagram` 承载）。

---

## 3. 数据属性

| 属性 | 类型（数值用 `xsd:integer`/`xsd:decimal`） | 定义域 | 出处 |
|---|---|---|---|
| core:trigramNumber（先天卦数 1~8） | xsd:integer | core:Trigram | 卷之一·卦数起例（乾1兑2离3震4巽5坎6艮7坤8） |
| core:linePosition（爻位 1~6） | xsd:integer | core:Line | 卷之一·爻以六除 |
| core:hexagramName | xsd:string | core:Hexagram | 卷之五·六十四卦次第歌 |
| core:yinYang（阴/阳） | xsd:string（枚举） | core:Line | 卷之一·爻以六除 |
| core:trigramSymbol（卦象符/取象） | xsd:string | core:Trigram | 卷之五·八卦取象（乾为天…兑为泽） |
| core:trigramYinYang（阴/阳） | xsd:string（枚举） | core:Trigram | 卷之三·八卦定阴阳次序 |
| core:trigramFamilyRole（父/长男/中男/少男/母/长女/中女/少女） | xsd:string（枚举） | core:Trigram | 卷之三·八卦定阴阳次序 |
| yijing:hexagramSequence（卦序 1~64） | xsd:integer | yijing:HexagramSequence | 卷之五·六十四卦次第歌 |
| yijing:hexagramDictumText（卦辞文本） | xsd:string | yijing:HexagramDictum | 卷之二·先天后天论 |
| yijing:lineDictumText（爻辞文本） | xsd:string | yijing:LineDictum | 卷之二·占卜总诀 |
| yijing:lineDictumPosition（爻位 1~6） | xsd:integer | yijing:LineDictum | 卷之二·占卜总诀（重卦六爻） |
| yijing:oppositeNatureText（杂卦反对性情文本） | xsd:string | core:SixBitHexagram | 卷之三·诸卦反对性情 |
| div:methodCode（起卦法代码） | xsd:string（枚举） | div:DivinationMethod | 卷之一·起卦诸篇；附录·一撮金 |
| div:preceptName（戒律名） | xsd:string（枚举） | div:Precept | 卷之一·观梅占（四不占）；附录·一撮金 |
| div:strokeCount（笔画数） | xsd:integer | div:InputDatum | 卷之一·字占；附录·一撮金（数其笔画，一点一撇亦算） |
| div:rawNumber（起卦原始数） | xsd:integer | div:InputDatum | 卷之一·卦数起例 |
| rel:shengKeType（生克类型 1~5） | xsd:integer | rel:BodyUseRelation | 卷之二·体用总诀（用生体/比和/体克用/体生用/用克体） |
| rel:vitality（旺相休囚死） | xsd:string（枚举） | rel:VitalityState | 卷之一·卦气旺衰；卷之五·五行四时旺相休囚表 |
| rel:natureText（性情文本：健/顺/起/止/陷/丽/说/入） | xsd:string（枚举） | rel:TrigramNature | 卷之五·论八卦性情 |
| sym:symbolText（类象文本） | xsd:string | sym:ImageSymbol | 卷之一·八卦万物属类/八卦万物类占 |
| ome:omenText（外应文本） | xsd:string | ome:TenResponses | 卷之二·十应目论 |
| jud:divinationSystem（先天/后天体系） | xsd:string（枚举） | jud:JudgementDomain | 卷之二·先天后天论；流程§7-8 |
| jud:usesZhouyiDictum（是否引周易卦爻辞） | xsd:boolean | jud:JudgementDomain | 卷之二·先天后天论；流程§7-8（先天不引/后天引/一撮金恒引） |
| jud:auspiciousnessLevel（大吉=5…大凶=1） | xsd:integer | jud:Auspiciousness | 流程§4；卷之二·占卜总诀（俱吉则大吉/俱凶则大凶） |
| jud:yicuoJinGradeValue（上上=6…下下=1） | xsd:integer | jud:YicuoJinGrade | 附录·一撮金 |
| jud:processDescription（过程描述） | xsd:string | jud:JudgementDomain | 卷之二·体用总诀（本卦/互卦/变卦分主初始/过程/结果） |
| jud:processFlag（过程标记，如「末后不吉」） | xsd:string | jud:JudgementDomain | 卷之三·体用互变之诀（变卦克体，事末后不吉） |
| jud:isMajorResult（重大结果标记） | xsd:boolean | jud:JudgementDomain | 流程节点15（大吉/大凶外发）；顶层设计§1（规则驱动事件） |
| jud:responseValue / jud:responseUnit | xsd:decimal / xsd:string | jud:ResponsePeriod | 卷之三·克应（远年/近月/又近日）；流程§7-9 |
| cal:ganzhiYear/Month/Day/Hour | xsd:string | cal:CalendarDatum | 卷之一·年月日时起例（干支序数） |
| cal:season | xsd:string（枚举） | cal:CalendarDatum | 卷之一·卦气旺衰 |
| cal:stemName（甲…癸） | xsd:string（枚举） | cal:HeavenlyStem | 卷之一·十天干 |
| cal:branchName（子…亥） | xsd:string（枚举） | cal:EarthlyBranch | 卷之一·十二地支 |
| cal:branchAnimal（鼠/牛/虎…猪） | xsd:string | cal:EarthlyBranch | 卷之一·十二地支（子水鼠、丑土牛…） |
| cal:zhiOrder（子=1…亥=12） | xsd:integer | cal:EarthlyBranch | 卷之一·年月日时起例（子年一数…亥年十二数） |
| cal:naYinName（海中金…大海水） | xsd:string | cal:NaYin | 卷之五·六十甲子纳音歌 |
| cal:lifeStageText（木生在亥…） | xsd:string | cal:LifeStage | 卷之五·五行相生地支 |

---

## 4. 约束（`owl:Restriction`）

| 类 | 属性 | 约束 | 出处 |
|---|---|---|---|
| core:Hexagram | core:hasUpperTrigram | `owl:cardinality 1`（重卦必有一上卦） | 卷之一·卦数起例 |
| core:Hexagram | core:hasLowerTrigram | `owl:cardinality 1`（必有一定下卦） | 卷之一·卦数起例 |
| core:Hexagram | core:hasLine | `owl:cardinality 6`（重卦六爻） | 卷之一·爻以六除 |
| core:OriginalHexagram | core:hasMovingLine | `owl:cardinality 1`（**一卦一爻动**） | 卷之一·爻以六除；顶层设计§2.2-2 |
| core:MutualHexagram | core:mutualOf | `owl:cardinality 1` | 卷之一·互卦止用八卦 |
| core:ChangedHexagram | core:changedFrom | `owl:cardinality 1` | 卷之一·爻以六除 |
| core:Trigram | core:hasDirection | `owl:cardinality 1`（一卦一方位） | 卷之一·八卦方位图 |
| core:Trigram | core:hasTrigramNature | `owl:cardinality 1`（一卦一性情） | 卷之五·论八卦性情 |
| core:Trigram | core:hasTrigramOrder | `owl:cardinality 1` | 卷之三·八卦定阴阳次序 |
| core:SixBitHexagram | yijing:hasHexagramDictum | `owl:cardinality 1`（一卦一卦辞） | 卷之二·先天后天论 |
| core:SixBitHexagram | yijing:hasLineDictum | `owl:cardinality 6`（一卦六爻辞） | 卷之二·占卜总诀（重卦六爻） |
| yijing:LineDictum | yijing:lineDictumPosition | `owl:cardinality 1`（爻辞位 1~6） | 卷之二·占卜总诀 |
| cal:CalendarDatum | cal:hasStem | `owl:cardinality 1` | 卷之一·年月日时起例 |
| cal:CalendarDatum | cal:hasBranch | `owl:cardinality 1` | 卷之一·十二地支 |
| rel:BodyUseRelation | rel:hasBodyTrigram | `owl:cardinality 1` | 卷之三·体用互变之诀 |
| rel:BodyUseRelation | rel:hasUseTrigram | `owl:cardinality 1` | 卷之三·体用互变之诀 |
| core:Trigram | rel:hasFivePhase | `owl:cardinality 1`（一卦一五行） | 卷之一·八宫所属五行 |
| jud:JudgementDomain | jud:hasAuspiciousness | `owl:cardinality 1`（单一吉凶等级） | 流程§4（吉凶等级为枚举值，每次只取一个） |
| jud:YicuoJinAdmonition | jud:hasYicuoJinGrade | `owl:cardinality 1` | 附录·一撮金 |
| jud:JudgementDomain | jud:hasResponsePeriod | `owl:maxCardinality 1` | 卷之三·克应 |

> **硬性**：以上约束均在 OWL 中以 `owl:Restriction` 表达，**不写于注释**（★12）；「一卦一爻动」是本应用与六爻法的根本区别（顶层设计§2.2-2）。

---

## 5. SWRL 规则

> 规则用于把「生克/旺衰/一撮金爻诀/周易辞引用/历法配属」等业务判据下沉为本体推理，程序只查询（★12/14）。变量命名沿用 Ontop/Openllet 约定。**所引谓词均在 §2/§3 有定义（自洽）。**

| 规则 | 条件（body） | 结论（head） | 出处 |
|---|---|---|---|
| R1 体用判定 | `OriginalHexagram(?h) ∧ hasMovingLine(?h, ?m) ∧ linePosition(?m, ?p) ∧ swrlb:lessThanOrEqual(?p, 3)` | → `hasUseTrigram(?h, ?lower)`（动爻在下卦→下卦为用、上卦为体） | 卷之三·体用互变之诀（动者为用）；流程节点4 |
| R2 体用判定（上卦） | `OriginalHexagram(?h) ∧ hasMovingLine(?h, ?m) ∧ linePosition(?m, ?p) ∧ swrlb:greaterThan(?p, 3)` | → `hasUseTrigram(?h, ?upper)`（动爻在上卦→上卦为用、下卦为体） | 卷之三·体用互变之诀；流程节点4 |
| R3 用生体·最吉 | `BodyUseRelation(?r) ∧ hasUseTrigram(?r, ?u) ∧ hasBodyTrigram(?r, ?b) ∧ hasFivePhase(?u, ?pu) ∧ hasFivePhase(?b, ?pb) ∧ generates(?pu, ?pb)` | → `shengKeType(?r, 1)`（用生体=最吉，进益之喜） | 卷之二·体用总诀 |
| R4 比和·次吉 | `BodyUseRelation(?r) ∧ hasUseTrigram(?r, ?u) ∧ hasBodyTrigram(?r, ?b) ∧ hasFivePhase(?u, ?pu) ∧ hasFivePhase(?b, ?pu)` | → `shengKeType(?r, 2)`（体用比和=次吉，百事顺遂） | 卷之二·体用总诀 |
| R5 体克用·小吉 | `BodyUseRelation(?r) ∧ ... ∧ generates(?pb, ?pu) ∧ ...`（体五行克用五行） | → `shengKeType(?r, 3)`（体克用=小吉） | 卷之二·体用总诀 |
| R6 体生用·有耗 | `... overcomes`（体五行生用五行，反为耗） | → `shengKeType(?r, 4)`（体生用=有耗，耗失之患） | 卷之二·体用总诀 |
| R7 用克体·最凶 | `... overcomes(?pu, ?pb)` | → `shengKeType(?r, 5)`（用克体=最凶，诸事凶） | 卷之二·体用总诀 |
| R8 变卦克体·末后不吉 | `ChangedHexagram(?c) ∧ changedFrom(?c, ?h) ∧ ... 变卦五行克体五行` | → `jud:processFlag(?j, "末后不吉")` | 卷之三·体用互变之诀（变卦克体，事于末后必有不吉） |
| R9 一撮金·六等映射 | `JudgementDomain(?j) ∧ jud:hasYicuoJinAdmonition(?j, ?a) ∧ jud:hasYicuoJinGrade(?a, ?g) ∧ swrlb:equal(?g, 6)` | → `jud:hasAuspiciousness(?j, jud:DaJi)`（上上→大吉；上中→吉、中中/中平→平、下中→凶、下下→大凶 类推） | 流程§7-11（六等↔五级合流） |
| R10 重大吉凶→告警 | `JudgementDomain(?j) ∧ hasAuspiciousness(?j, ?a) ∧ (Auspiciousness(?a) ∧ (auspiciousnessLevel(?a, 5) ∨ auspiciousnessLevel(?a, 1)))` | → `jud:isMajorResult(?j, true)` | 流程节点15（大吉/大凶外发）；顶层设计§1（规则驱动事件） |
| R11 后天体系·启用周易卦爻辞 | `JudgementDomain(?j) ∧ jud:divinationSystem(?j, "后天")` | → `jud:usesZhouyiDictum(?j, true)`（后天卦引爻辞兼卦辞） | 卷之二·先天后天论「后天则用爻辞，兼用卦辞」；流程§7-8 |
| R12 先天体系·不引周易爻辞 | `JudgementDomain(?j) ∧ jud:divinationSystem(?j, "先天") ∧ jud:hasMethod(?j, ?m) ∧ div:DivinationMethod(?m) ∧ notEqual(?m, div:YicuoJinMethod)` | → `jud:usesZhouyiDictum(?j, false)`（先天止以卦论、不甚用爻辞；一撮金除外） | 卷之二·先天后天论「先天卦断吉凶，止以卦论，不甚用《易》之爻辞」；流程§7-8、§7-12 |
| R13 干支→五行 传导 | `CalendarDatum(?c) ∧ hasStem(?c, ?s) ∧ cal:stemFivePhase(?s, ?p)` | → `cal:datumFivePhase(?c, ?p)`（供旺衰/应期取值） | 卷之五·天干地支所属五行 |
| R14 纳音→五行 | `SexagenaryCycle(?x) ∧ hasNaYin(?x, ?n) ∧ cal:naYinPhase(?n, ?p)` | → `cal:datumFivePhase(?x, ?p)`（以纳音属断应期之应） | 卷之五·六十甲子纳音歌；流程节点13 |
| R15 六十四卦合成 | `Hexagram(?h) ∧ hasUpperTrigram(?h, ?u) ∧ hasLowerTrigram(?h, ?l) ∧ trigramPairMap(?u, ?l, ?name)` | → `hexagramName(?h, ?name)`（上下卦相重→六十四卦名录） | 卷之一·卦数起例；卷之五·六十四卦次第歌；流程节点3 |

> **说明**：R3~R7 为「体用生克 5 级」的核心判据，规则组须与 `rel:shengKeType` 一致；R9 承载附录一撮金断法（六等经 `JudgementDomain` 归一为五级）；R10 供 `SwrlRuleTriggerListener` 检测 `jud:isMajorResult=true` 个体 → RabbitMQ 外发（流程节点15）；R11/R12 落实先天/后天「是否引周易卦爻辞」的体系差异（`jud:divinationSystem` + `jud:hasMethod`，一撮金经 `notEqual` 豁免，对齐流程§7-12）；R13/R14 把历法数据接入五行判据；R15 由上下卦经内置 `trigramPairMap` 合成六十四卦名（免程序硬编码名录）。

---

## 6. 关系与约束的一致性检查要点

1. **对象属性互逆配对**：`hasUpperTrigram/isUpperTrigramOf`、`hasLowerTrigram/isLowerTrigramOf`、`hasLine/isLineOf`、`hasBodyTrigram/isBodyTrigramOf`、`hasUseTrigram/isUseTrigramOf`、`mutualOf/hasMutualHexagram`、`changedFrom/hasChangedHexagram`、`generates/generatedBy`、`overcomes/overcomeBy`、`hasDirection/isDirectionOf`、`hasTrigramNature/isTrigramNatureOf`、`hasTrigramOrder/isTrigramOrderOf`、`yijing:hasHexagramDictum/yijing:isHexagramDictumOf`、`yijing:hasLineDictum/yijing:isLineDictumOf`、`jud:hasYicuoJinAdmonition/jud:admonitionOfHexagram`、`hasStem/isStemOf`、`hasBranch/isBranchOf`、`hasNaYin/isNaYinOf` 均须互逆声明；`yijing:oppositeOf` 须声明 `owl:SymmetricProperty`。
2. **基数闭合**：`Hexagram` 上下卦各 1、六爻恰 6、本卦动爻恰 1；`BodyUseRelation` 体/用各 1；`Trigram` 五行恰 1、方位恰 1、性情恰 1、次序恰 1；`SixBitHexagram` 卦辞恰 1、爻辞恰 6；`CalendarDatum` 天干/地支各 1；`JudgementDomain` 吉凶等级恰 1。
3. **值域/定义域闭合**：`hasFivePhase` 值域仅为 `FivePhase` 5 子类；`hasAuspiciousness` 值域仅为 5 子类；`generates/overcomes` 定义域与值域均为 `FivePhase`；`hasDirection` 值域仅为 `Direction` 8 子类；`stemFivePhase`/`branchFivePhase`/`hasLifeStage`/`cal:naYinPhase`/`cal:datumFivePhase` 值域为 `FivePhase`/`LifeStage`；`jud:divinationSystem`/`jud:usesZhouyiDictum`/`jud:isMajorResult`/`jud:hasMethod` 定义域均为 `jud:JudgementDomain`（R11/R12 闭合）。
4. **SWRL 与 Restriction 一致**：R1/R2 推出的体用分工不得与本卦动爻位约束冲突；R10 的 `jud:isMajorResult` 与 `auspiciousnessLevel ∈ {1,5}` 对应；R11/R12 的 `usesZhouyiDictum` 标记不得与 `divinationSystem`（先天/后天）判定冲突。
5. **两套吉凶体系并存不矛盾**：五级（主流程）与一撮金六等经 R9 单向映射，六等为源、五级为归一化结果。
6. **两类「辞」区分**：`yijing:HexagramDictum`/`yijing:LineDictum`（周易经传）与 `jud:YicuoJinAdmonition`（一撮金爻诀）来源、引用条件不同，不得混类；引用条件由 R11/R12 约束。
7. **无硬编码业务数据**：五行生克、类象、六十四卦名录、方位、性情、干支五行等以个体/约束表达，程序不含分支常量（★12/14）。

---

## 7. ABox 典型个体设计（落地于第 6 步 · 闸门⑦）

| 个体 | 类型 | 关键断言 | 说明 |
|---|---|---|---|
| `core:QianTrigram` … `core:KunTrigram` | core:Trigram | trigramNumber 1~8；`rel:hasFivePhase` 金/土/木/水/火；hasDirection；hasTrigramNature；hasTrigramOrder；trigramSymbol | 八卦 8 个术语型个体（卷之一·八宫所属五行/八卦方位图；卷之五·论八卦性情/八卦取象） |
| `core:Dir_East` … `core:Dir_NorthWest` | core:Direction | 方位名（东/南/西/北/东南/西南/东北/西北） | 八方 8 个体（卷之一·八卦方位图；四隅依指南§三） |
| `rel:Nature_Qian` … `rel:Nature_Xun` | rel:TrigramNature | natureText 健/顺/起/止/陷/丽/说/入 | 八卦性情 8 个体（卷之五·论八卦性情） |
| `rel:Order_Qian` … `rel:Order_Kun` | rel:TrigramOrder | trigramYinYang；trigramFamilyRole | 八卦阴阳次序 8 个体（卷之三·八卦定阴阳次序） |
| `core:Hexagram_i`（六十四卦） | core:SixBitHexagram | hexagramName；hasUpperTrigram；hasLowerTrigram；yijing:hasHexagramDictum；yijing:hasLineDictum×6；yijing:oppositeOf；yijing:oppositeNatureText | 六十四卦名录（卷之五·六十四卦次第歌；卷之三·诸卦反对性情） |
| `yijing:Seq_01` … `yijing:Seq_64` | yijing:HexagramSequence | hexagramSequence 1~64 | 六十四卦卦序 64 个体（卷之五·六十四卦次第歌） |
| `yijing:Dictum_Hex_i` | yijing:HexagramDictum | hexagramDictumText | 六十四卦卦辞（《周易》文本，来源见 §9-6） |
| `yijing:LineDictum_i_p`（64×6） | yijing:LineDictum | lineDictumText；lineDictumPosition 1~6 | 周易爻辞（384 条，来源见 §9-6） |
| `yijing:Opposite_i` | yijing:OppositeNature | oppositeNatureText | 诸卦反对性情（卷之三·诸卦反对性情，粒度见 §9-8） |
| `div:Precept_BuCheng` … `div:Precept_ChongGua` | div:Precept | preceptName 不诚/无事/不义/重卦 | 四不占戒律 4 个体（卷之一·观梅占；附录·一撮金） |
| `jud:Cat_TianShi` … `jud:Cat_FenMu` | jud:SubjectCategory | 名称标签 | 18 事项类别个体（卷之二十八章占例） |
| `jud:DaJi` … `jud:DaXiong` | jud:Auspiciousness | auspiciousnessLevel 5…1 | 吉凶等级 5 个体（流程§4） |
| `jud:Grade_ShangShang` … `jud:Grade_XiaXia` | jud:YicuoJinGrade | yicuoJinGradeValue 6…1 | 一撮金六等 6 个体（附录·一撮金） |
| `rel:Vitality_Wang` … `rel:Vitality_Si` | rel:VitalityState | 名称标签 | 旺相休囚死 5 个体（卷之二·衰旺论） |
| `ome:Response_Zheng` … `ome:Response_RenShi` | ome:TenResponses | 名称标签 | 十应 10 个体（卷之三·占卜十应诀） |
| `sym:Symbol_Qian_Tian` … | sym:ImageSymbol | symbolText；belongsToDimension | 典型类象个体（卷之一·八卦万物属类，节选） |
| `cal:Stem_Jia` … `cal:Stem_Gui` | cal:HeavenlyStem | stemName 甲…癸；stemFivePhase（甲乙木/丙丁火/戊己土/庚辛金/壬癸水） | 十天干 10 个体（卷之一·十天干；卷之五·天干地支所属五行） |
| `cal:Branch_Zi` … `cal:Branch_Hai` | cal:EarthlyBranch | branchName 子…亥；branchAnimal；zhiOrder 1~12；branchFivePhase | 十二地支 12 个体（卷之一·十二地支；卷之五·天干地支所属五行） |
| `cal:Cycle_JiaZi` … `cal:Cycle_GuiHai` | cal:SexagenaryCycle | hasNaYin | 六十甲子（卷之五·六十甲子纳音歌） |
| `cal:NaYin_HaiZhongJin` … `cal:NaYin_DaHaiShui` | cal:NaYin | naYinName；cal:naYinPhase | 纳音 30 名（卷之五·六十甲子纳音歌） |
| `cal:Life_Mu` … `cal:Life_ShuiTu` | cal:LifeStage | lifeStageText（木生在亥/火生于寅/金生于巳/水土长生居申） | 五行长生 5 个体（卷之五·五行相生地支） |
| `jud:PB_<timestamp>`（运行态） | jud:JudgementDomain | divinationSystem；hasMethod；hasAuspiciousness；processDescription；hasResponsePeriod；isMajorResult | 典型占断实例，第 11 步入库 |

> **闸门⑦**：第 6 步生成的单文件本体中的**典型 ABox 数据**须在本节登记并交业务人员确认（个体集/类型/关键断言/代表性）后方可进入第 7 步。

### 7.1 第 6 步实际产出登记（`ontology/plumblossom-all.owl` · 待闸门⑦确认）

> 单文件本体（TBox 全量 + 典型 ABox）：**71 类 / 62 对象属性（含逆）/ 41 数据属性 / 19 条 `owl:Restriction` / 14 条 SWRL（R1~R14）/ 245 个体**（经 XML 良构与 RDF 解析校验通过；Openllet 一致性待第 7 步环境中验证）。

| ABox 组 | 规模 | 类型 | 覆盖 | 备注 |
|---|---|---|---|---|
| 八卦 | 8 | `core:Trigram` | 全 | 含先天数/五行/方位/性情/次序/卦象符 |
| 八方 | 8 | `core:Direction` | 全 | 四正依原著，四隅依指南§三 |
| 五行/生克 | 5 + 10 | `rel:FivePhase`/`rel:Sheng`/`rel:Ke` | 全 | 生成/克各 5 对 |
| 旺衰 | 5 | `rel:VitalityState` | 全 | 旺相休囚死 |
| 八卦性情 | 8 | `rel:TrigramNature` | 全 | 健顺起止陷丽说入 |
| 八卦次序 | 8 | `rel:TrigramOrder` | 全 | 阴阳 + 长幼男女 |
| 吉凶等级 | 5 | `jud:Auspiciousness` | 全 | 大吉…大凶 |
| 一撮金六等 | 6 | `jud:YicuoJinGrade` | 全 | 上上…下下 |
| 十应/三要 | 10 + 3 | `ome:TenResponses`/`ome:ThreeEssentials` | 全 | 正/互/变/方/日/刻/外/天时/地理/人事 |
| 四不占 | 4 | `div:Precept` | 全 | 不诚/无事/不义/重卦 |
| 事项类别 | 18 | `jud:SubjectCategory` | 全 | 卷之二十八章 |
| 天干/地支 | 10 + 12 | `cal:HeavenlyStem`/`cal:EarthlyBranch` | 全 | 含五行/生肖/序 |
| 五行长生 | 4 | `cal:LifeStage` | 全 | 木/火/金/水土 |
| 纳音/六十甲子 | 6 + 6 | `cal:NaYin`/`cal:SexagenaryCycle` | **代表性** | 全量留待第 12 步扩全集 |
| 类象维度/类象 | 8 + 8 | `sym:SymbolDimension`/`sym:ImageSymbol` | **代表性** | 每卦一代表象 |
| 卦序 | 64 | `yijing:HexagramSequence` | 全 | 1~64（六十四卦次第歌） |
| 六十四卦名录 | **2（乾/坤）** | `core:SixBitHexagram` | **代表性** | 含上下卦 + 六爻 + 1 卦辞 + 6 爻辞 + 反对性情 |
| 周易卦辞/爻辞 | 2 + 12 | `yijing:HexagramDictum`/`yijing:LineDictum` | **代表性** | 乾/坤经文（外部引入《周易》）；**全 64+384 留待第 12 步** |

> **与 §7 原表的差异（待闸门⑦裁定）**：设计 §7 期望「六十四卦全名录 + 卦辞 64 + 爻辞 384」。第 6 步按 SKILL「典型 ABox」定位，仅纳入**代表性**子集（乾/坤两卦的完整经文），其余六十二卦、全集卦辞爻辞、纳音/甲子全量**留待第 12 步「ABox 扩至全集」**。此为**步次定位差异**（典型 vs 全集），非建模变更，提请业务确认。

> **第 11 步（闸门⑩）落位**：表内 `yijing:Seq_*`（64）、`yijing:Dictum_*`（2）、`yijing:LineDictum_*`（12）与 `cal:` 天干/地支/六十甲子/纳音/长生（38）共 **116 个体已迁入 MySQL**（见 §8），并从 `plumblossom-abox.owl` 移除；ABox 现 **129 个体**。`cal:` 全量（六十甲子 60 / 纳音 30，原仅代表性 6/6）随第 12 步扩全集时经 DB 增补。

> **第 12 步（闸门⑪）落位（全集）**：ABox 由 129 扩至 **1133 个体**——
> - **六十四卦名录**：由 2（乾坤）扩至 **64**（`core:Hexagram_01..64`；每卦含 `hasUpperTrigram`/`hasLowerTrigram` + 6 个爻个体 `core:Hexagram_<nn>_Line<pos>`（YangLine/YinLine）+ `yijing:hasHexagramDictum` 与 6 个 `yijing:hasLineDictum` 引用，文本经 OBDA 从 DB 取）；爻个体 **384**（192 阳 / 192 阴）。
> - **类象全集**：`sym:ImageSymbol` **559** 项 + `sym:SymbolDimension` **27**（原 8，新增 19：人事/身体/家宅/饮食/生产/求名/谋望/交易/求利/出行/谒见/疾病/官讼/坟墓/方道/五色/姓字/数目/五味）；依原著卷之一·八卦万物类占全表（8 卦 × 27 栏），命名 `sym:Symbol_<卦>_<维度>_<序号>`。原 8 个代表性类象（`sym:Symbol_<卦>`）由全集取代（其「八卦取象」语义已由 `core:trigramSymbol` 承载）。
> - **命名迁移**：`core:QianHexagram`/`KunHexagram` → `core:Hexagram_01`/`02`；`yijing:Dictum_Qian`→`Dictum_Hex_01`、`LineDictum_Qian_1`→`LineDictum_01_1` 等（仅 ABox 内自引用）。诸卦反对性情粒度（§9-8）维持乾坤一例，仍待确认。

---

## 8. OBDA / SKOS 预留

- **OBDA（第 11 步 · 闸门⑩已落地）**：历法/干支与周易文本**入库** MySQL `plumblossomdb`（9 表），经 Ontop 虚拟访问，**不迁入本体文件**（顶层设计§1）。落地产物（`ontology/database/`）：`createdb_tables.ddl`（建库 + 9 表结构）、`data.sql`（历法 38 + 周易文本 78 + 占断样本 6）、`plumBlossom.obda`（8 个 mappingId：`calendar_heavenly_stem` / `calendar_earthly_branch` / `calendar_sexagenary_cycle` / `calendar_nayin` / `calendar_life_stage` / `yijing_hexagram_sequence` / `yijing_hexagram_dictum` / `yijing_line_dictum` / `jud_divination_result`）、`plumBlossom.properties`（`ontop.ontologyFile=ontology/plumblossom-abox.owl`）。
  - 映射目标 IRI 以短前缀形式 `<cal:{name}>` / `<yijing:{name}>` 生成，与本体实体 IRI 逐字一致；`rdfs:label` 用标准前缀写法。
  - **数据边界（闸门⑩ + 第 12 步闸门⑪扩全集）**：历法 **116**（天干 10 / 地支 12 / 纳音 30 / 六十甲子 60 / 长生 4）+ 周易文本 **512**（六十四卦序 64 / 卦辞 64 / 爻辞 384）共 **628 个体入库**（DB 为唯一真源，本体文件仅保留结构性 ABox）；运行态占断实例 `PB_*` ↔ `divination_result` 表（样本 6 条）。第 11 步初始为历法 38 + 周易文本 78，第 12 步扩至全集；`yijing:` 卦辞/爻辞命名改为 `Dictum_Hex_<nn>` / `LineDictum_<nn>_<pos>`。
  - ⚠️ 起卦法 `jud:hasMethod` 的值在本体中是 `div:DivinationMethod` 的**子类**（如 `YearMonthDayHourMethod` / `YicuoJinMethod`，非个体），库中 `method` 列存其本地名。
- **SKOS（第 13 步）**：八卦/六十四卦/五行/十应/事项类别/方位/性情/天干/地支/周易卦爻辞名的**中英术语**↔ 本体 IRI；供 `OntologyLabelMatcher` 中文关键词匹配（对齐 Example §8）。

---

## 9. 待业务确认项

| # | 问题 | 说明 | 业务结论 |
|---|---|---|---|
| 1 | 一撮金爻诀断辞是否全会入 ABox？ | 附录爻诀仅对部分爻配辞（如「乾为天」只列九二/九四/上九）；是全录还是节选典型？ | **待确认** |
| 2 | 现代类象映射扩展层是否本期启用？ | 流程§7-5 定为「分离扩展层、默认关闭」；`sym:modernMapping` 是否第 12 步扩全集时启用？ | **待确认** |
| 3 | 应期推理中「干支日」取值方式？ | 应期类型/范围由 SWRL 推导，具体干支日经 OBDA 取；SWRL 是否直接读 `cal:` 个体？ | **待确认** |
| 4 | `SubjectCategory` 是否支持「其他/自定义」个体？ | 流程§7-6 允许用户自定义类别；本体是否预留 `jud:Cat_Custom`？ | **待确认** |
| 5 | 体用生克 5 级是否直接落 `shengKeType` 整数？ | R3~R7 结论用 1~5 整数编码，是否与前端展示枚举一致？ | **待确认** |
| 6 | 周易卦辞/爻辞数据来源与范围？ | 原著**未收录**六十四卦完整卦辞/爻辞表（仅零引）；`yijing:HexagramDictum`/`yijing:LineDictum` 须引入《周易》经传文本 | **已确认（邵康节）**：**全引入 64 卦辞 + 384 爻辞**，经 DB（OBDA）承载，ABox 仅保留 `yijing:Dictum_Hex_<nn>`/`LineDictum_<nn>_<pos>` 引用（第 12 步闸门⑪落地） |
| 7 | 八卦方位是否取全八方？ | 原著卷之一·八卦方位图仅列「离南坎北，震东兑西」四正（**已回原著核实**）；余四隅依指南§三补全 | **待确认** |
| 8 | 杂卦反对性情建模粒度？ | 卷之三·诸卦反对性情为歌诀（乾刚坤柔/比欣师忧…） | **待确认** |
| 9 | 干支历法落 ABox 还是 OBDA 运行态？ | `cal:HeavenlyStem`/`EarthlyBranch`/`SexagenaryCycle`/`NaYin`/`LifeStage` 为静态术语，可入 ABox；或全经 OBDA 从 MySQL 取 | **已确认（第 11 步闸门⑩，邵康节）**：干支历法（天干/地支/六十甲子/纳音/长生）**经 OBDA 入库** `calendar_*` 表，不落 ABox；ABox 中相应个体已移除（见 §8） |
| 10 | 四不占戒律是否建模为 `div:Precept` 个体？ | 流程§7-1 定「仅程序化校验无事/重卦；不诚/不义改前端承诺勾选」 | **待确认** |
| 11 | 周易经传是否单列 TBox 子本体？ | 第 3 步 v3 已定单列 `YijingDomain` 与 `plumblossom-yijing.owl`（本版已按此迁移 `yijing:` 前缀） | **已定（第 3 步 v3）**：单列 `yijing`；拆分粒度第 10 步再议 |

---

## 10. 复核结论（对齐第 2/3 步 · 回原著核验）★v3.1

> 以邵康节视角，对本核心设计逐项**回原著核验**并对照第 2 步流程、第 3 步顶层设计。

### 10.1 出处核验（回原著逐条命中，无编造）

| 出处（本设计标注） | 原著卷次·位置 | 命中原文（节录） |
|---|---|---|
| 卷之一·八卦方位图 | 卷之一 | 「八卦方位图／右离南坎北，震东兑西，人则介乎其中」 |
| 卷之一·八卦万物属类 / 八卦万物类占 / 观梅占（不动不占） | 卷之一 | 「八卦万物属类（）」「八卦万物类占」「不动不占，不因事不占」 |
| 卷之二·占卜总诀（先看周易爻辞） / 先天后天论 | 卷之二 | 「成卦之后先看《周易》爻辞，以断吉凶」「先天后天论」 |
| 卷之三·八卦定阴阳次序 / 诸卦反对性情 / 占物类例 / 体用互变之诀 | 卷之三 | 「八卦定阴阳次序」「诸卦反对性情」「凡看物数…观其爻辞」「动者为用」 |
| 卷之五·八卦取象 / 论八卦性情 / 天干地支所属五行 / 六十甲子纳音歌 / 六十四卦次第歌 / 五行相生地支 | 卷之五 | 「八卦取象／乾为天」「论八卦性情／乾健也」「天干地支所属五行」「六十甲子纳音」「六十四卦次第歌」「木生在亥…土长生居申」 |
| 附录·一撮金（诚心祈祷/不可再占/爻诀六等） | 附录 | 「须要诚心祈祷…不可再占」 |

> **结论**：本设计全部出处**可回原文定位、无编造**（★铁律 8/9/10）。原著未收录之内容（六十四卦完整卦辞/爻辞）已在 §9-6 显式声明并转为「外部引入」，未伪造出处。

### 10.2 流程节点覆盖（第 2 步 18 ServiceTask → 本体落点）

| 节点 | 本体落点 |
|---|---|
| 1 心法戒律校验 | `div:Precept`（4 个体） |
| 2 输入解析与起卦法路由 | `div:DivinationMethod`（17）、`div:InputDatum`、`core:Direction`、`core:hasDirection` |
| 2-字占 字占细分 | `div:CharacterMethod`、`div:strokeCount` |
| 3 起卦成卦 | `core:Hexagram`/`Line`/`Trigram`、R15、`cal`（取爻加时） |
| 4 定体用 | `rel:BodyUseRelation`（R1/R2）、`rel:TrigramOrder` |
| 5 察互卦 | `core:MutualHexagram`/`mutualOf` |
| 6 观变卦 | `core:ChangedHexagram`/`changedFrom` |
| 7 五行生克 | `rel:FivePhase`/`ShengKeRelation`、R3~R7 |
| 8 卦气旺衰 | `rel:VitalityState`、R13 |
| 9 万物类象 | `sym:ImageSymbol`、`rel:TrigramNature` |
| 10 分类占断 | `jud:SubjectCategory`、`yijing:LineDictum`、`yijing:OppositeNature` |
| 11 参外应 | `ome:ThreeEssentials`/`TenResponses` |
| 12 断吉凶·以理统数 | `jud:Auspiciousness`、R9~R12 |
| 13 定应期 | `jud:ResponsePeriod`、`cal:NaYin`/`LifeStage`、R14 |
| 14 修身建议 | `jud:SelfCultivationAdvice` |
| 15 外发告警 | R10 → `jud:isMajorResult` |
| 16 汇总 | 各域输出汇总 |

> 三个排他网关条件（戒律校验结果、起卦法是否字占、吉凶级别判定）均由对应枚举/布尔个体承载，BPMN 分支可查询判定。

### 10.3 顶层设计覆盖（第 3 步 6 概念域 + S1~S6 → 展开）

| 顶层域/支撑 | 展开状态 |
|---|---|
| 起卦域/占断事项域/卦象域/易理域/外应域/断验域 | 全部展开为明细类（见 §1） |
| S1 四不占戒律 → `div:Precept` ✓ | S2 周易经传 → `yijing:HexagramDictum`/`LineDictum` ✓ |
| S3 八卦全属性 → `core:Direction`/`hasTrigramNature`/`hasTrigramOrder` ✓ | S4 历法干支 → `cal:CalendarDomain` ✓ |
| S5 卦象符/卦序 → `core:trigramSymbol` + `yijing:HexagramSequence` ✓ | S6 诸卦反对性情 → `yijing:OppositeNature`/`oppositeOf` ✓ |

### 10.4 v3.1 修正的自洽性缺陷（v3 遗留，已闭合）

① `jud:LineAdmonition` → `jud:YicuoJinAdmonition`（命名统一）；② 体系标记 `div:divinationSystem` → `jud:divinationSystem`（+`jud:hasMethod`），R11/R12 定义域闭合；③ 补齐 `jud:processFlag`/`jud:isMajorResult`/`cal:datumFivePhase`/`cal:naYinPhase` 定义；④ R9 主体归 `JudgementDomain`；⑤ §7 ABox 断言名更正（`hasHexagramDictum`/`hasLineDictum`）；⑥ `trigramPairMap` 明确为内置映射表。

---

## 闸门③留痕

| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-20 | v1（初稿） | — | 展开顶层设计 §5 为明细类体系（起卦域 17 起卦法子类）；设计对象属性 22 项、数据属性 17 项、`owl:Restriction` 12 项、SWRL 规则 R1~R10；ABox 典型个体登记 |
| 2026-10-20 | v2（支撑内容扩充） | — | 补入「流程运转支撑内容」：`HexagramDictum`/`LineDictum`、`Direction`、`TrigramNature`、`TrigramOrder`、`Precept`、`CalendarDomain`；对象属性 +12、数据属性 +15、约束 +8、SWRL +5（R11~R15）。（**注**：该稿为越位产物——第 3 步当时尚未定稿） |
| 2026-10-20 | v3（对齐第 3 步 v3） | — | 新建 `yijing` 命名空间，周易经传单列 `yijing:YijingDomain`；相关属性/约束/SWRL/ABox 前缀由 `core:` 迁至 `yijing:` |
| 2026-10-20 | **v3.1（回原著核验 + 自洽性修订）** | 邵康节 | 以邵康节视角逐条**回原著核验**（§10：出处全部命中，无编造）；对照第 2 步流程 18 节点与第 3 步顶层 6 域+S1~S6 全覆盖（§10.2/10.3）；修正 6 处自洽性缺陷（§10.4）；§9-6 据业务裁定更新为「全引入 64+384」 |
| 2026-10-20 | **v3.1（定稿）** | 邵康节 | **闸门③通过**：认可 v3.1 本体核心设计定稿，准予进入第 5 步（BPMN）/ 第 6 步（单文件本体，含典型 ABox，闸门⑦） |

---

## 闸门⑦留痕（典型 ABox 数据 · 第 6 步）

| 日期 | 确认范围（典型个体集/覆盖场景） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-20 | §7.1 登记的 245 个体（含 17 类枚举全量 + 乾/坤两卦完整经文 + 代表性纳音/甲子/类象） | 邵康节 | **闸门⑦通过**：认可典型 ABox（含乾/坤代表性六十四卦，全集延至第 12 步）；接受 R8（变卦上卦五行克体卦五行）与 R15（trigramPairMap 内置映射）处理及 UI `season` 选填项。准予进入第 7 步 |
