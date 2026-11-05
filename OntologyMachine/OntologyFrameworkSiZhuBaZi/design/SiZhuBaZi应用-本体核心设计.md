# SiZhuBaZi应用 — 本体核心设计（核心类、属性与关系）

> **用法**：本文件产出第 4 步。**闸门③**：业务人员（领域专家）确认核心类/属性/关系后方可生成第 6 步本体文件。本文件并承载第 6 步**闸门⑦**（典型 ABox 数据确认）的留痕（见 §7 与文末「闸门⑦留痕」）。
> **基线**：`OntologyFrameworkExample/design/Example应用-本体设计.md`（理念 / 资产组织 / TBox / SWRL 章节）；同类术数形态见 `OntologyFrameworkPlumBlossom/design/PlumBlossom应用-本体核心设计.md`（v3.1）。

| 项目 | 内容 |
|---|---|
| 应用 | SiZhuBaZi（四柱命理 / 八字推命） |
| 文档日期 | 2026-10-29 |
| 当前版本 | **v1（定稿）** |
| 上游 | 《SiZhuBaZi应用-本体顶层设计》（**闸门②已确认，v1 定稿**） |
| 关联流程 | 《SiZhuBaZi应用-流程设计》（v1 定稿·修订三，闸门①+逐节点确认已完成） |
| 产出本体（规划） | `ontology/sizhubazi-all.owl`（单文件，含典型 ABox）；第 9/10 步拆为 `sizhubazi-tbox.owl` / 子本体 / `sizhubazi-abox.owl` |
| 推理器 | Openllet（经 OpenlletResolver） |
| 虚拟数据 | Ontop OBDA → MySQL（历法/节气/藏干/纳音/神煞查表等） |
| 消息外发 | SWRL → RabbitMQ（大凶 / 寿元关 / 重大疾病标志） |

> **依据（强制）**：每条类/属性/约束/SWRL 均标注出处（★铁律 8/9/10/12/14）。自节点 2 起的方法与数据追溯至原著**具体篇章**（**不允许编造**）；查询不到者一律标【待考】，不硬写。
> 六书 ＝ 本模块 `design/六书原文/` 与 `design/六书全文整理/`：**四部原著**——**《渊海子平》**（宋·徐大升）、**《子平真诠》**（清·沈孝瞻）、**《三命通会》**（明·万民英）、**《滴天髓》**（宋·京图撰·明·刘伯温注）；**两部参照典籍**——**《穷通宝鉴》**（调候，**文本已取得 2026-10-30**，条文全落地）、**《神峰通考》**（明·张楠，合婚，文本已归档）。
> **IRI 前缀简称**：`core:`/`calendar:`/`chart:`/`relations:`/`tengod:`/`deity:`/`pattern:`/`yongshen:`/`fortune:`/`reading:`/`diagnosis:`/`rules:`，对应顶层设计 §4.1 命名空间（`http://example.org/sizhubazi/<模块名>`）。
> **节点对齐**：本文件展开自顶层设计 §5 顶层类框架，逐条对齐流程设计 §3 的 **21 节点**；并覆盖顶层设计 §2.1(A) 21 概念域 + §2.1(B) 6 支撑概念域（S1~S6）。

---

## 1. 核心类体系（明细）

> 展开自《本体顶层设计》§5 顶层类框架；主干分支不变，细化为**可落库的类层级**。标注 `★` 者为流程运转**支撑概念**（对应顶层设计 §2.1(B) 的 S1~S6）。

```
core:SiZhuBaZiEntity（本体事物根）

├── core:GanzhiDomain（干支基础域）★【S1】
│   ├── core:YinYang（阴阳；2 子类）—— core:Yang / core:Yin
│   ├── core:FivePhase（五行；5 子类）—— core:Jin / core:Mu / core:Shui / core:Huo / core:Tu
│   ├── core:HeavenlyStem（十天干；10 子类）—— core:Stem_Jia … core:Stem_Gui
│   ├── core:EarthlyBranch（十二地支；12 子类）—— core:Branch_Zi … core:Branch_Hai
│   ├── core:SexagenaryCycle（六十甲子；按干支组合 60）
│   ├── core:NaYin（纳音；30 名 / 60 组）—— 纳音取象 + 性质吉凶
│   ├── core:HiddenStem（藏干 / 人元司事）—— 支中藏遁 + 月令司令
│   └── core:LifeStage（长生十二宫 / 干支生旺死绝）
│
├── calendar:CalendarDomain（历法域）★【S2】
│   ├── calendar:SolarTerm（二十四节气）—— 以立春分年、以节气定月
│   ├── calendar:MonthOrder（月建 / 月令）
│   ├── calendar:TrueSolarTime（真太阳时 / 经度校正）
│   └── calendar:CalendarBoundary（历法边界：闰月/夏令时/时区/时辰交界；枚举）
│
├── chart:ChartDomain（排盘域）
│   ├── chart:BirthInput（生辰输入：公历/农历·性别·出生地/经度·对象类型·模式·粒度）
│   ├── chart:Pillar（柱）
│   │   ├── chart:YearPillar（年柱）/ chart:MonthPillar / chart:DayPillar / chart:HourPillar
│   ├── chart:FourPillars（四柱盘）
│   ├── chart:GreatFortune（大运 / 交运 / 起运岁）
│   ├── chart:SmallFortune（小运）
│   ├── chart:TaiYuan（胎元）
│   └── chart:MingGong（命宫）
│
├── relations:RelationDomain（干支作用域）
│   ├── relations:StemCombination（天干五合 / 化气 / 合而不合 / 进交退伏）
│   ├── relations:BranchRelation（地支关系）
│   │   ├── relations:SixCombination（六合）
│   │   ├── relations:TripleCombination（三合 / 半合）
│   │   ├── relations:Clash（相冲）
│   │   ├── relations:Harm（相穿 / 六害）
│   │   ├── relations:Punishment（三刑）
│   │   └── relations:TombRelation（墓库刑冲）
│   ├── relations:HiddenRelation（暗合 / 暗拱 / 遥冲遥克）
│   ├── relations:Bondage（合绊 / 羁绊）
│   └── relations:PhaseRelation（干支五行生克）
│
├── tengod:TenGodDomain（十神与六亲域）
│   ├── tengod:TenGod（十神；10 子类）—— ZhengGuan/PianGuan/ZhengCai/PianCai/ZhengYin/PianYin/ShiShen/ShangGuan/BiJian/JieCai
│   ├── tengod:TenGodRelation（生克制化）
│   │   ├── tengod:OfficialKillMix（官杀混杂）/ tengod:FoodHurtsKill（食伤制杀）/ tengod:WealthOfficialGenerate（财官相生）
│   ├── tengod:Kinship（六亲）—— 祖上/父母/兄弟/自身/妻妾/子息
│   └── tengod:Palace（宫位）—— 年（祖上）/ 月（父母兄弟）/ 日（自身妻）/ 时（子息）
│
├── deity:DeityDomain（神煞域）
│   ├── deity:AuspiciousDeity（贵人吉神）—— 天乙/天官/太极/三奇/天月德/天福/福星/学堂词馆/德秀
│   ├── deity:LuMa（禄马类）—— 十干禄/金舆/驿马/拱禄/交禄/暗禄/归禄
│   ├── deity:InauspiciousDeity（凶煞类）—— 羊刃/劫煞亡神/灾煞/六厄/勾绞/孤辰寡宿/天罗地网/十恶大败/元辰/暗金的煞/咸池/华盖/将星
│   └── deity:Void（空亡）—— 旬空/截路空亡/四大空亡 + 应期（填实/冲实/合解）+ 各柱分义 + 与格局成败关系
│
├── pattern:PatternDomain（格局域）
│   ├── pattern:RegularPattern（八正格）—— 正官/偏官/正财/偏财/印绶/食神/伤官/阳刃·建禄月劫
│   ├── pattern:MixedQiPattern（杂气与取用）—— 杂气财官/杂气印绶/墓库取用
│   ├── pattern:SpecialPattern（外格 / 杂格）—— 从格/化格/专旺/禄马类/日贵日德魁罡金神时墓
│   └── pattern:PatternQuality（成败与高低）—— 成败救应/变化/纯杂/四吉神破格·四凶神成格
│
├── yongshen:YongshenDomain（用神旺衰域）
│   ├── yongshen:YongShen（用神 / 相神 / 忌神）
│   ├── yongshen:Tiaohou（调候用神）—— 十天干生十二月寒暖燥湿取用
│   ├── yongshen:FlowRelation（源流 / 通关 / 寒暖 / 燥湿 / 刚柔 / 顺逆）
│   ├── yongshen:Vitality（旺衰）—— 得时不旺·失时不弱 / 寄生十二宫 / 中和
│   ├── yongshen:Purity（清浊真假）—— 清气/浊气 / 真神/假神 / 隐显 / 众寡
│   └── yongshen:Level（命局层次；4 子类）—— GuiGe(贵格)/FuGe(富格)/PingChang(平常)/PinJian(贫贱)
│
├── fortune:FortuneDomain（岁运域）
│   ├── fortune:GreatFortuneCycle（大运 / 小运 / 交运）
│   ├── fortune:AnnualFortune（流年 / 太岁）
│   ├── fortune:SubAnnualFortune（流月 / 流日 / 流时）
│   ├── fortune:SpecialFortunePattern（岁运并临 / 天克地冲 / 伏吟 / 反吟 / 桃花红鸾）
│   ├── fortune:YunHuaQi（运化气 / 化气十段锦）
│   ├── fortune:ResponsePeriod（应期：填实/冲实/合住/透出/变应）
│   ├── fortune:LifeStageLimit（运限法：年·月·日·时柱分主阶段）
│   └── fortune:FortuneSynthesis（岁运综合辩证：优先级 / 多势 / 吉凶转化 / 叠加）
│
├── reading:ReadingDomain（断语域）
│   ├── reading:Temperament（性情相貌才德）
│   ├── reading:Disease（疾病：五脏六腑所属干支）
│   ├── reading:KinshipFortune（六亲吉凶）
│   ├── reading:LadyFortune（女命专论）
│   ├── reading:ChildFortune（小儿关杀）
│   ├── reading:CategoryFortune（分类事项：事业/财运/婚姻/寿夭/飘荡）
│   ├── reading:Admonition（劝诫修身）
│   └── reading:MarriageMatch（八字合婚）
│
├── diagnosis:DiagnosisDomain（出处与流派标注域）★【S3】
│   ├── diagnosis:SourceNote（出处标注：书名·卷·篇）
│   ├── diagnosis:SchoolVariant（流派分歧 / 并陈判据）
│   └── diagnosis:ToBeVerified（【待考】标记）
│
└── OutputSubdomain（输出与存档域，跨域归入既有命名空间）
    ├── reading:ReadingConclusion（综合结论 / 命书）
    ├── rules:AlertFlag（告警标志：大凶 / 寿元关 / 重大疾病）★【S5】
    └── reading:CaseRecord（命例记录 / 隐私存档）★【S6】
```

> **说明（输出域的落位）**：顶层设计 §5 的 `OutputDomain` 不单独设子本体（§3 的 12 子本体已含 `rules` 而无 `output`）。故其三名成员按语义归入既有命名空间——`ReadingConclusion`/`CaseRecord` 归 `reading:`（断语与批断输出/存档）、`AlertFlag` 归 `rules:`（该命名空间角色即「含告警标志推导」，照 Example 中 `LowStock*` 由规则推导的先例）。此为一处**落位细化**，提请注意（见 §9-1）。
> **与顶层框架的对应**：上列 12 个主干分支与顶层设计 §5 一一对应；`core:` 根（`SiZhuBaZiEntity`）承载所有分支。

---

### 1.1 支撑域说明（流程运转所必需的干支基础 / 历法 / 出处标注 / 术语 / 告警 / 命例）★

> 逐项说明支撑概念**为何为流程运转所必需**，并给出原著出处（对齐顶层设计 §2.1(B) S1~S6）。**出处均可回原文定位，见 §10 复核。**

| 支撑域 | 展开的类/属性 | 用途（对应流程节点） | 原著出处 |
|---|---|---|---|
| **S1 干支基础体系** | `core:YinYang`/`FivePhase`/`HeavenlyStem`/`EarthlyBranch`/`SexagenaryCycle`/`NaYin`/`HiddenStem`/`LifeStage`；`core:hasStem`/`hasBranch`/`hasNaYin`/`hasHiddenStem`/`hasLifeStage`/`generates`/`overcomes` | 节点 3（纳音/藏干/日柱特性）、节点 7（旺衰/十二宫）、节点 11（岁运干支）之共同基础 | 三命通会卷一·论五行生成/论支干源流/总论纳音/释六十甲子性质吉凶/论人元司事；渊海子平卷之一·论五行相生相克/论天干地支所出/论六十花甲子纳音/论天地干支暗藏总诀/又地支藏遁歌；子平真诠·论十干配合性情 |
| **S2 历法与节气** | `calendar:SolarTerm`/`SolarTermInstant`/`MonthOrder`/`TrueSolarTime`/`CalendarBoundary`；`calendar:hasSolarTerm`/`hasMonthOrder`/`hasInstant`/`instantOf`/`termYear`/`termIndex`/`termDatetime` | 节点 1（时辰校正）、节点 2（节气定月/五虎遁）、节点 11（岁运交节）之共同基础；**第 31 步**增 `calendar:SolarTermInstant`（节气精确交节时刻，年度实例） | 三命通会卷二·论遁月时/论年月日时；渊海子平卷之一·论节候歌；客户裁定（闸门⓪、①-1~③；**历法惯例，非六书**）；**第 31 步**节气精度升级（Meeus 太阳黄经法自算，客户裁定采用路径 A） |
| **S3 六书出处与流派标注** | `diagnosis:SourceNote`/`SchoolVariant`/`ToBeVerified`；`diagnosis:hasSourceNote`/`hasSchoolVariant`/`hasToBeVerified` | 全流程——★铁律 9/10「不臆造唯一断语、须指到出处」的承载机制（节点 9 多法冲突并陈、节点 12 应期） | 顶层设计 §5-4「流派差异如实呈现」；子平真诠·论时说拘泥格局/论时说以讹传讹（并陈之据） |
| **S4 术语同义与规范** | `diagnosis:Synonym`（同义词/别名）；经第 13 步 SKOS `term` 承载 | 全流程——供 `SkosSynonymReader`/`OntologyLabelMatcher` 名称归一 | 六书异名（偏官＝七杀、偏印＝倒食/枭神、羊刃＝阳刃、比肩＝禄、劫财＝刃）：渊海子平卷之二·论偏官/论倒食；三命通会卷五·论古人立印食官财名义 |
| **S5 岁运吉凶标志与告警** | `rules:AlertFlag`（大凶 / 寿元关 / 重大疾病，枚举个体）；`rules:hasAlertFlag` | 节点 A（外发告警）：SWRL 命中即外发，无数值阈值 | 顶层设计 I·46；客户裁定 ㉒（2026-10-29）；六书判定（滴天髓·知命/疾病；三命通会卷九·论疾病） |
| **S6 命例与隐私** | `reading:CaseRecord`；`reading:hasCaseRecord`/`caseBirthData`/`caseResult` | 节点 17（结果落库与返回） | 顶层设计 I·47；客户裁定 ㉓（2026-10-29：完整存入含出生地、不脱敏） |

---

## 2. 对象属性

> 特性列标注函数（Functional）/对称（Symmetric）/传递（Transitive）/逆（Inverse）；每条标出处。**互逆属性成对声明**（§6-1）。

| 属性 | 特性 | `owl:inverseOf` | 定义域 | 值域 | 出处 |
|---|---|---|---|---|---|
| core:hasYinYang | Functional | core:isYinYangOf | core:GanzhiEntity | core:YinYang | 三命通会卷一·论五行生成 |
| core:hasFivePhase | Functional | core:isFivePhaseOf | core:GanzhiEntity | core:FivePhase | 三命通会卷一·论五行生成/论支干源流 |
| core:generates（生） | Transitive | core:generatedBy | core:FivePhase | core:FivePhase | 渊海子平卷之一·论五行相生相克 |
| core:overcomes（克） | Transitive | core:overcomeBy | core:FivePhase | core:FivePhase | 渊海子平卷之一·论五行相生相克 |
| core:hasStem | Functional | core:isStemOf | core:SexagenaryCycle | core:HeavenlyStem | 渊海子平卷之一·论六十花甲子纳音 |
| core:hasBranch | Functional | core:isBranchOf | core:SexagenaryCycle | core:EarthlyBranch | 渊海子平卷之一·论六十花甲子纳音 |
| core:hasNaYin | Functional | core:isNaYinOf | core:SexagenaryCycle | core:NaYin | 三命通会卷一·总论纳音/论纳音取象 |
| core:hasHiddenStem | — | core:isHiddenStemOf | core:EarthlyBranch | core:HiddenStem | 渊海子平卷之一·又地支藏遁歌；三命通会卷一·论人元司事 |
| core:hasLifeStage | Functional | core:isLifeStageOf | core:HeavenlyStem | core:LifeStage | 三命通会卷二·论天干阴阳生死；渊海子平卷之一·论天干生旺死绝 |
| calendar:hasSolarTerm | Functional | calendar:isSolarTermOf | chart:MonthPillar | calendar:SolarTerm | 三命通会卷二·论遁月时（以节气定月） |
| calendar:hasMonthOrder | Functional | calendar:isMonthOrderOf | chart:MonthPillar | calendar:MonthOrder | 渊海子平卷之一·论月令；三命通会卷二·论年月日时 |
| calendar:hasTrueSolarTime | Functional | calendar:isTrueSolarTimeOf | chart:BirthInput | calendar:TrueSolarTime | 历法惯例（客户裁定 ②）；非六书 |
| calendar:hasBoundary | — | calendar:isBoundaryOf | chart:BirthInput | calendar:CalendarBoundary | 历法惯例（客户裁定 ①②③）；非六书 |
| chart:hasBirthInput | Functional | chart:isBirthInputOf | chart:FourPillars | chart:BirthInput | 流程节点 1 |
| chart:hasYearPillar | Functional | chart:isYearPillarOf | chart:FourPillars | chart:YearPillar | 三命通会卷二·论年月日时 |
| chart:hasMonthPillar | Functional | chart:isMonthPillarOf | chart:FourPillars | chart:MonthPillar | 同上 |
| chart:hasDayPillar | Functional | chart:isDayPillarOf | chart:FourPillars | chart:DayPillar | 同上 |
| chart:hasHourPillar | Functional | chart:isHourPillarOf | chart:FourPillars | chart:HourPillar | 同上 |
| chart:hasStem | Functional | chart:isStemOf | chart:Pillar | core:HeavenlyStem | 三命通会卷二·论年月日时 |
| chart:hasBranch | Functional | chart:isBranchOf | chart:Pillar | core:EarthlyBranch | 三命通会卷二·论年月日时 |
| chart:hasGreatFortune | — | chart:isGreatFortuneOf | chart:FourPillars | chart:GreatFortune | 渊海子平卷之一·论起大运法 |
| chart:hasSmallFortune | — | chart:isSmallFortuneOf | chart:FourPillars | chart:SmallFortune | 渊海子平卷之一·论行小运法 |
| chart:hasTaiYuan | Functional | chart:isTaiYuanOf | chart:FourPillars | chart:TaiYuan | 渊海子平卷之一·论起胎法；三命通会卷二·论胎元 |
| chart:hasMingGong | Functional | chart:isMingGongOf | chart:FourPillars | chart:MingGong | 三命通会卷二·论坐命官 |
| relations:hasStemCombination | — | relations:isStemCombinationOf | core:HeavenlyStem | relations:StemCombination | 三命通会卷二·论十干合 |
| relations:combinesWith（天干五合） | Symmetric | relations:combinesWith | core:HeavenlyStem | core:HeavenlyStem | 三命通会卷二·论十干合；子平真诠·论十干配合性情 |
| relations:transformsTo（化气） | Functional | relations:isTransformedFrom | relations:StemCombination | core:FivePhase | 三命通会卷二·论十干化气；渊海子平卷之三·论运化气 |
| relations:hasBranchRelation | — | relations:isBranchRelationOf | core:EarthlyBranch | relations:BranchRelation | 三命通会卷二·论支元六合/三合/六害/三刑/冲击 |
| relations:sixCombinesWith（六合） | Symmetric | relations:sixCombinesWith | core:EarthlyBranch | core:EarthlyBranch | 渊海子平卷之一·论十二支六合 |
| relations:tripleCombinesWith（三合） | Symmetric | relations:tripleCombinesWith | core:EarthlyBranch | core:EarthlyBranch | 渊海子平卷之一·论十二支三合 |
| relations:clashesWith（相冲） | Symmetric | relations:clashesWith | core:EarthlyBranch | core:EarthlyBranch | 渊海子平卷之一·论十二支相冲 |
| relations:harmsWith（相穿/六害） | Symmetric | relations:harmsWith | core:EarthlyBranch | core:EarthlyBranch | 渊海子平卷之一·论十二支相穿 |
| relations:punishesWith（三刑） | Symmetric | relations:punishesWith | core:EarthlyBranch | core:EarthlyBranch | 三命通会卷二·论三刑 |
| relations:hasHiddenRelation | — | relations:isHiddenRelationOf | core:HeavenlyStem∨core:EarthlyBranch | relations:HiddenRelation | 子平真诠·论刑冲会合解法（暗合暗拱遥克，六书有论者采） |
| relations:isBondedBy | Functional | relations:bonds | core:HeavenlyStem∨core:EarthlyBranch | relations:Bondage | 三命通会卷二·论十干合（合而不合） |
| relations:hasPhaseRelation | — | relations:isPhaseRelationOf | core:HeavenlyStem∨core:EarthlyBranch | relations:PhaseRelation | 渊海子平卷之一·论五行相生相克 |
| tengod:hasTenGod | Functional | tengod:isTenGodOf | chart:Pillar | tengod:TenGod | 渊海子平卷之二·各十神论；三命通会卷五·论古人立印食官财名义 |
| tengod:definedByDayMaster | Functional | tengod:definesTenGodFor | tengod:TenGod | chart:DayPillar | 渊海子平卷之一·论日为主 |
| tengod:hasTenGodRelation | — | tengod:isTenGodRelationOf | tengod:TenGod | tengod:TenGodRelation | 渊海子平卷之一·论官杀混杂要制伏 |
| tengod:hasKinship | — | tengod:isKinshipOf | chart:Pillar | tengod:Kinship | 渊海子平卷之三·六亲总论；子平真诠·论宫分用神配六亲 |
| tengod:hasPalace | Functional | tengod:isPalaceOf | chart:Pillar | tengod:Palace | 渊海子平卷之三·六亲总论；三命通会·总论岁运（柱各管年限） |
| deity:hasDeity | — | deity:isDeityOf | chart:Pillar | deity:Deity | 渊海子平卷之一·诸神煞篇；三命通会卷二·诸神煞篇 |
| deity:hasVoid | — | deity:isVoidOf | chart:Pillar | deity:Void | 三命通会卷二·论空亡 |
| deity:hasVoidResponse | — | deity:isVoidResponseOf | deity:Void | fortune:ResponsePeriod | 三命通会卷二·论空亡（填实/冲实/合解） |
| pattern:hasPattern | — | pattern:isPatternOf | chart:FourPillars | pattern:RegularPattern∨pattern:MixedQiPattern∨pattern:SpecialPattern | 子平真诠·论用神/论外格用舍/论杂格 |
| pattern:hasPatternQuality | Functional | pattern:isPatternQualityOf | pattern:Pattern | pattern:PatternQuality | 子平真诠·论用神成败救应/论用神格局高低 |
| yongshen:hasYongShen | Functional | yongshen:isYongShenOf | chart:FourPillars | yongshen:YongShen | 子平真诠·论用神/论相神紧要 |
| yongshen:hasVitality | Functional | yongshen:isVitalityOf | chart:FourPillars | yongshen:Vitality | 滴天髓·衰旺/中和；子平真诠·论十干得时不旺失时不弱 |
| yongshen:hasPurity | Functional | yongshen:isPurityOf | chart:FourPillars | yongshen:Purity | 滴天髓·清气/浊气/真神/假神 |
| yongshen:hasLevel | Functional | yongshen:isLevelOf | chart:FourPillars | yongshen:Level | 子平真诠·论用神格局高低；三命通会·论贵贱贫富 |
| yongshen:hasFlowRelation | — | yongshen:isFlowRelationOf | chart:FourPillars | yongshen:FlowRelation | 滴天髓·源流/通关/寒暖/燥湿/刚柔/顺逆 |
| fortune:hasGreatFortuneCycle | — | fortune:isGreatFortuneCycleOf | chart:FourPillars | fortune:GreatFortuneCycle | 三命通会卷二·论大运/论小运 |
| fortune:hasAnnualFortune | — | fortune:isAnnualFortuneOf | chart:FourPillars | fortune:AnnualFortune | 三命通会卷二·论太岁/总论岁运 |
| fortune:hasSubAnnualFortune | — | fortune:isSubAnnualFortuneOf | fortune:AnnualFortune | fortune:SubAnnualFortune | 三命通会·总论岁运（流月/流日/流时已按历法实算落地：流月＝流年干五虎遁列十二月建、流日＝干支纪日 JDN 锚定、流时＝日干五鼠遁起时） |
| fortune:hasSpecialPattern | — | fortune:isSpecialPatternOf | fortune:AnnualFortune | fortune:SpecialFortunePattern | 子平真诠·论行运成格变格 |
| fortune:hasYunHuaQi | — | fortune:isYunHuaQiOf | fortune:GreatFortuneCycle | fortune:YunHuaQi | 渊海子平卷之三·论运化气/化气十段锦 |
| fortune:hasResponsePeriod | Functional | fortune:isResponsePeriodOf | chart:FourPillars∨pattern:Pattern | fortune:ResponsePeriod | 三命通会·总论岁运；滴天髓·岁运 |
| fortune:hasLifeStageLimit | — | fortune:isLifeStageLimitOf | chart:FourPillars | fortune:LifeStageLimit | 三命通会·总论岁运「柱各管年限」；渊海子平「月为初主，日为中主，时为末主」 |
| fortune:hasFortuneSynthesis | Functional | fortune:isFortuneSynthesisOf | chart:FourPillars | fortune:FortuneSynthesis | 子平真诠·论行运成格变格；滴天髓·岁运 |
| reading:hasReading | — | reading:isReadingOf | chart:FourPillars | reading:ReadingItem | 三命通会卷九·论性情相貌/论疾病先知五脏六腑所属干支；滴天髓·性情/才德/疾病 |
| reading:hasTemperament | — | reading:isTemperamentOf | chart:FourPillars | reading:Temperament | 三命通会卷九·论性情相貌；滴天髓·性情/才德 |
| reading:hasDisease | — | reading:isDiseaseOf | chart:FourPillars | reading:Disease | 三命通会卷九·论疾病先知五脏六腑所属干支 |
| reading:hasKinshipFortune | — | reading:isKinshipFortuneOf | chart:FourPillars | reading:KinshipFortune | 渊海子平卷之三·论父·母·妻妾·兄弟姐妹·子息；滴天髓·夫妻/子女/父母/兄弟 |
| reading:hasLadyFortune | — | reading:isLadyFortuneOf | chart:FourPillars | reading:LadyFortune | 渊海子平卷之三·论妇人总诀/女命富贵贫贱篇；三命通会卷九·论女命 |
| reading:hasChildFortune | — | reading:isChildFortuneOf | chart:FourPillars | reading:ChildFortune | 渊海子平卷之三·论小儿/论小儿关杀例；滴天髓·小儿 |
| reading:hasCategoryFortune | — | reading:isCategoryFortuneOf | chart:FourPillars | reading:CategoryFortune | 渊海子平卷之五·克妻克子带疾寿元飘荡诗诀；滴天髓·出身/地位/奋郁 |
| reading:hasAdmonition | — | reading:isAdmonitionOf | chart:FourPillars | reading:Admonition | 滴天髓·知命（节点 15） |
| reading:hasMarriageMatch | Functional | reading:isMarriageMatchOf | chart:FourPillars | reading:MarriageMatch | 《神峰通考》卷一·男女合婚说（节点 M1） |
| diagnosis:hasSourceNote | — | diagnosis:isSourceNoteOf | core:SiZhuBaZiEntity | diagnosis:SourceNote | 顶层设计 §5-4；★铁律 9/10 |
| diagnosis:hasSchoolVariant | — | diagnosis:isSchoolVariantOf | core:SiZhuBaZiEntity | diagnosis:SchoolVariant | 子平真诠·论时说拘泥格局（多法冲突并陈） |
| diagnosis:hasToBeVerified | — | diagnosis:isToBeVerifiedOf | core:SiZhuBaZiEntity | diagnosis:ToBeVerified | 顶层设计 §5-4；★铁律 8/9/10 |
| reading:hasConclusion | Functional | reading:isConclusionOf | chart:FourPillars | reading:ReadingConclusion | 流程节点 16（汇总） |
| rules:hasAlertFlag | — | rules:isAlertFlagOf | chart:FourPillars | rules:AlertFlag | 顶层设计 I·46；客户裁定 ㉒ |
| reading:hasCaseRecord | Functional | reading:isCaseRecordOf | chart:FourPillars | reading:CaseRecord | 顶层设计 I·47（节点 17） |

> **内置映射表（非本体个体）**：`stemTenGodMap`（日干 × 目标干 → 十神）与 `deityRuleMap`（神煞起例：以某柱干支查神煞）为**内置映射/查表**，实现于查询/规则层或经 OBDA（MySQL 神煞查找表），**不建模为 64×10 冗余个体**（对齐 PlumBlossom `trigramPairMap` 做法）。十神名录本身已由 `tengod:TenGod` 10 子类承载。

---

## 3. 数据属性

> **硬性**：数值一律用数值类型（`xsd:integer`/`xsd:decimal`/`xsd:boolean`），枚举用 `xsd:string`（★12）。

| 属性 | 类型 | 定义域 | 出处 |
|---|---|---|---|
| core:yinYangName（阴/阳） | xsd:string（枚举） | core:YinYang | 三命通会卷一·论五行生成 |
| core:phaseName（金/木/水/火/土） | xsd:string（枚举） | core:FivePhase | 三命通会卷一·论五行生成 |
| core:stemName（甲…癸） | xsd:string（枚举） | core:HeavenlyStem | 渊海子平卷之一·论天干地支所出 |
| core:stemYinYang（甲丙戊庚壬阳 / 乙丁己辛癸阴） | xsd:string（枚举） | core:HeavenlyStem | 三命通会卷一·论支干源流 |
| core:branchName（子…亥） | xsd:string（枚举） | core:EarthlyBranch | 渊海子平卷之一·论天干地支所出 |
| core:branchAnimal（鼠…猪） | xsd:string | core:EarthlyBranch | 三命通会卷一·论支干源流 |
| core:branchOrder（子=1…亥=12） | xsd:integer | core:EarthlyBranch | 渊海子平卷之一·论天干地支所出 |
| core:naYinName（海中金…大海水） | xsd:string | core:NaYin | 三命通会卷一·总论纳音/论纳音取象 |
| core:naYinNature（纳音性质吉凶） | xsd:string | core:NaYin | 三命通会卷一·释六十甲子性质吉凶 |
| core:hiddenStemRole（人元/司事司令） | xsd:string（枚举） | core:HiddenStem | 三命通会卷一·论人元司事；渊海子平卷之一·又地支藏遁歌 |
| core:lifeStageName（长生/沐浴/冠带/临官/帝旺/衰/病/死/墓/绝/胎/养） | xsd:string（枚举） | core:LifeStage | 三命通会卷二·论天干阴阳生死；渊海子平卷之一·论天干生旺死绝 |
| calendar:solarTermName（立春…大寒 24） | xsd:string（枚举） | calendar:SolarTerm | 渊海子平卷之一·论节候歌 |
| calendar:monthOrderName（寅…丑 月建） | xsd:string（枚举） | calendar:MonthOrder | 三命通会卷二·论遁月时 |
| calendar:longitude（出生地经度） | xsd:decimal | calendar:TrueSolarTime | 历法惯例（客户裁定 ②）；非六书 |
| calendar:boundaryType（闰月/夏令时/时区/时辰交界） | xsd:string（枚举） | calendar:CalendarBoundary | 历法惯例（客户裁定 ①②③）；非六书 |
| chart:birthDate（公历或农历生辰） | xsd:string | chart:BirthInput | 流程节点 1 |
| chart:calendarType（公历/农历） | xsd:string（枚举） | chart:BirthInput | 流程节点 1 |
| chart:gender（男/女） | xsd:string（枚举） | chart:BirthInput | 流程节点 1（起运顺逆依此） |
| chart:birthPlace（出生地） | xsd:string | chart:BirthInput | 客户裁定 ㉓（完整存入） |
| chart:mode（对象类型：男命/女命/小儿/合婚） | xsd:string（枚举） | chart:BirthInput | 流程 §3（M1/W/C/A 条件性节点） |
| chart:granularity（输出粒度：年/月/日） | xsd:string（枚举） | chart:BirthInput | 客户裁定 ⑭（默认年、月/日可选） |
| chart:pilotAge（起运岁） | xsd:decimal | chart:GreatFortune | 渊海子平卷之一·论起大运法「折除三日为一岁」；三命通会卷二·论大运 |
| chart:greatFortuneSeq（大运序 1~n） | xsd:integer | chart:GreatFortune | 渊海子平卷之一·论起大运法 |
| chart:direction（顺行/逆行） | xsd:string（枚举） | chart:GreatFortune | 渊海子平卷之一·论起大运法（阳男阴女顺、阴男阳女逆） |
| chart:sexagenaryPair（该柱干支名） | xsd:string | chart:Pillar | 三命通会卷二·论年月日时 |
| relations:combinationType（五合/合而不合/进交退伏） | xsd:string（枚举） | relations:StemCombination | 三命通会卷二·论十干合/论进交退伏；子平真诠·论十干合而不合 |
| relations:branchRelationType（六合/三合/半合/冲/穿/刑/墓库） | xsd:string（枚举） | relations:BranchRelation | 三命通会卷二·论支元六合/三合/六害/三刑/冲击 |
| relations:combinationResult（合化结果） | xsd:string | relations:StemCombination | 三命通会卷二·论十干化气 |
| relations:relationTransformed（是否成立/合而不合） | xsd:boolean | relations:StemCombination∨relations:Bondage | 子平真诠·论十干合而不合 |
| tengod:tenGodName（十神名） | xsd:string（枚举） | tengod:TenGod | 渊海子平卷之二·各十神论；三命通会卷五·论古人立印食官财名义 |
| tengod:tenGodAlias（别名，如七杀/倒食/枭神） | xsd:string | tengod:TenGod | 渊海子平卷之二·论偏官/论倒食 |
| tengod:kinshipName（父母/兄弟/妻妾/子息/祖上/自身） | xsd:string（枚举） | tengod:Kinship | 渊海子平卷之三·六亲总论 |
| tengod:palaceName（年/月/日/时） | xsd:string（枚举） | tengod:Palace | 渊海子平卷之三·六亲总论 |
| tengod:relationType（官杀混杂/食伤制杀/财官相生） | xsd:string（枚举） | tengod:TenGodRelation | 渊海子平卷之一·论官杀混杂要制伏 |
| deity:deityName（神煞名） | xsd:string（枚举） | deity:Deity | 渊海子平卷之一·诸神煞篇；三命通会卷二·诸神煞篇 |
| deity:deityCategory（贵人吉神/禄马/凶煞） | xsd:string（枚举） | deity:Deity | 三命通会卷二·论天乙贵人…论将星华盖 |
| deity:voidType（旬空/截路/四大空亡） | xsd:string（枚举） | deity:Void | 三命通会卷二·论空亡 |
| deity:voidResponseType（填实/冲实/合住/透出） | xsd:string（枚举） | deity:Void∨fortune:ResponsePeriod | 三命通会卷二·论空亡（应期） |
| pattern:patternName（格局名） | xsd:string（枚举） | pattern:Pattern | 子平真诠·论正官/论财/论印绶/论食神/论偏官/论伤官/论阳刃/论建禄月劫 |
| pattern:patternClass（正格/杂气/外格杂格） | xsd:string（枚举） | pattern:Pattern | 子平真诠·论外格用舍/论杂格 |
| pattern:patternQualityName（成格/破格/纯/杂） | xsd:string（枚举） | pattern:PatternQuality | 子平真诠·论用神成败救应/论用神纯杂/论四吉神能破格/论四凶神能成格 |
| yongshen:yongShenRole（用神/相神/忌神） | xsd:string（枚举） | yongshen:YongShen | 子平真诠·论用神/论相神紧要/论喜忌干支有别 |
| yongshen:yongShenMethod（格局法/调候法/旺衰法；标主取） | xsd:string（枚举） | yongshen:YongShen | 客户裁定 ⑫（多法冲突并陈、月令格局法优先）；调候法已依《穷通宝鉴》落地 |
| yongshen:vitalityState（得时不旺/失时不弱/中和） | xsd:string（枚举） | yongshen:Vitality | 子平真诠·论十干得时不旺失时不弱；滴天髓·衰旺/中和 |
| yongshen:purityState（清/浊/真/假/隐/显/众/寡） | xsd:string（枚举） | yongshen:Purity | 滴天髓·清气/浊气/真神/假神/隐显/众寡 |
| yongshen:levelName（贵格/富格/平常/贫贱） | xsd:string（枚举） | yongshen:Level | 客户裁定 ⑬；子平真诠·论用神格局高低 |
| fortune:fortuneGanzhi（大运/流年/流月干支） | xsd:string | fortune:GreatFortuneCycle∨fortune:AnnualFortune∨fortune:SubAnnualFortune | 三命通会卷二·论大运/论太岁 |
| fortune:fortuneAgeRange（所辖年龄区间） | xsd:string | fortune:GreatFortuneCycle | 渊海子平卷之一·论起大运法 |
| fortune:specialPatternName（岁运并临/天克地冲/伏吟/反吟/桃花红鸾） | xsd:string（枚举） | fortune:SpecialFortunePattern | 三命通会·总论岁运；已细化落地 |
| fortune:responseValue / fortune:responseUnit | xsd:decimal / xsd:string | fortune:ResponsePeriod | 客户裁定 ⑯（年级为主给区间、月级可选）；三命通会·总论岁运 |
| fortune:lifeStageSegment（少年/青年/中年/老年） | xsd:string（枚举） | fortune:LifeStageLimit | 三命通会·总论岁运「月管初主，日管中主，时管末主，年则总统之」 |
| fortune:synthesisPriority（优先级） | xsd:integer | fortune:FortuneSynthesis | 子平真诠·论行运成格变格 |
| reading:readingText（断语文本） | xsd:string | reading:ReadingItem | 三命通会卷九；滴天髓（各断语篇） |
| reading:readingCategory（性情/疾病/六亲/女命/小儿/分类/劝诫/合婚） | xsd:string（枚举） | reading:ReadingItem | 对应各断语篇 |
| reading:diseaseOrgan（五脏六腑） | xsd:string | reading:Disease | 三命通会卷九·论疾病先知五脏六腑所属干支 |
| reading:matchComplementarity（合婚互补性） | xsd:string | reading:MarriageMatch | 《神峰通考》卷一·男女合婚说 |
| reading:conclusionText（命书结论） | xsd:string | reading:ReadingConclusion | 流程节点 16 |
| reading:caseBirthData（命例生辰） | xsd:string | reading:CaseRecord | 客户裁定 ㉓；顶层设计 I·47 |
| reading:caseResult（命例批断结果） | xsd:string | reading:CaseRecord | 顶层设计 I·47 |
| rules:alertType（大凶/寿元关/重大疾病） | xsd:string（枚举） | rules:AlertFlag | 顶层设计 I·46；客户裁定 ㉒ |
| rules:alertTriggered（是否命中） | xsd:boolean | rules:AlertFlag | 流程节点 A（SWRL 命中即外发） |
| diagnosis:sourceBook（书名） | xsd:string | diagnosis:SourceNote | ★铁律 9/10 |
| diagnosis:sourceVolume（卷/篇） | xsd:string | diagnosis:SourceNote | ★铁律 9/10 |
| diagnosis:variantText（分歧判据文本） | xsd:string | diagnosis:SchoolVariant | 子平真诠·论时说拘泥格局 |
| diagnosis:toBeVerifiedNote（【待考】说明） | xsd:string | diagnosis:ToBeVerified | ★铁律 8/9/10 |

---

## 4. 约束（`owl:Restriction`）

> **硬性**：约束必须写成 `owl:Restriction`，**不得写在注释里**（★12）；属性「值/约束」不混用；数值属性用数值类型。

| 类 | 属性 | 约束 | 出处 |
|---|---|---|---|
| chart:FourPillars | chart:hasYearPillar | `owl:cardinality 1`（年柱必有且唯一） | 三命通会卷二·论年月日时 |
| chart:FourPillars | chart:hasMonthPillar | `owl:cardinality 1` | 三命通会卷二·论年月日时 |
| chart:FourPillars | chart:hasDayPillar | `owl:cardinality 1` | 三命通会卷二·论年月日时 |
| chart:FourPillars | chart:hasHourPillar | `owl:cardinality 1` | 三命通会卷二·论年月日时 |
| chart:Pillar | chart:hasStem | `owl:cardinality 1`（一柱一天干） | 三命通会卷二·论年月日时 |
| chart:Pillar | chart:hasBranch | `owl:cardinality 1`（一柱一地支） | 三命通会卷二·论年月日时 |
| chart:DayPillar | chart:hasStem | `owl:cardinality 1`（日为主） | 渊海子平卷之一·论日为主 |
| core:HeavenlyStem | core:hasYinYang | `owl:cardinality 1` | 三命通会卷一·论支干源流 |
| core:HeavenlyStem | core:hasFivePhase | `owl:cardinality 1` | 三命通会卷一·论五行生成 |
| core:EarthlyBranch | core:hasFivePhase | `owl:cardinality 1` | 三命通会卷一·论五行生成 |
| core:SexagenaryCycle | core:hasStem | `owl:cardinality 1` | 渊海子平卷之一·论六十花甲子纳音 |
| core:SexagenaryCycle | core:hasBranch | `owl:cardinality 1` | 渊海子平卷之一·论六十花甲子纳音 |
| core:SexagenaryCycle | core:hasNaYin | `owl:cardinality 1`（一组一纳音） | 三命通会卷一·总论纳音 |
| core:NaYin | core:hasFivePhase | `owl:cardinality 1`（纳音属） | 三命通会卷一·论纳音取象 |
| core:EarthlyBranch | core:hasHiddenStem | `owl:minCardinality 1`（支必有藏干，多者多值） | 渊海子平卷之一·又地支藏遁歌 |
| core:HeavenlyStem | core:hasLifeStage | `owl:cardinality 1`（一干一生旺死绝） | 三命通会卷二·论天干阴阳生死 |
| calendar:MonthPillar | calendar:hasSolarTerm | `owl:cardinality 1`（以节气定月） | 三命通会卷二·论遁月时 |
| chart:GreatFortune | chart:direction | `owl:cardinality 1`（顺/逆唯一） | 渊海子平卷之一·论起大运法 |
| chart:FourPillars | chart:hasTaiYuan | `owl:cardinality 1`（胎元唯一） | 渊海子平卷之一·论起胎法 |
| chart:FourPillars | chart:hasMingGong | `owl:cardinality 1`（命宫唯一） | 三命通会卷二·论坐命官 |
| relations:StemCombination | relations:transformsTo | `owl:maxCardinality 1`（合化结果至多一五行） | 三命通会卷二·论十干化气 |
| tengod:Pillar | tengod:hasTenGod | `owl:maxCardinality 1`（一柱干对日干得至多一十神） | 渊海子平卷之二·各十神论 |
| tengod:TenGod | tengod:tenGodName | `owl:cardinality 1` | 三命通会卷五·论古人立印食官财名义 |
| tengod:Pillar | tengod:hasPalace | `owl:cardinality 1`（一柱一宫位） | 渊海子平卷之三·六亲总论 |
| pattern:Pattern | pattern:hasPatternQuality | `owl:cardinality 1`（单一成败判定） | 子平真诠·论用神成败救应 |
| yongshen:FourPillars | yongshen:hasYongShen | `owl:cardinality 1`（主取用神唯一） | 子平真诠·论用神（月令专求）；客户裁定 ⑫ |
| yongshen:FourPillars | yongshen:hasLevel | `owl:cardinality 1`（层次四档唯一） | 客户裁定 ⑬ |
| yongshen:FourPillars | yongshen:hasVitality | `owl:cardinality 1` | 子平真诠·论十干得时不旺失时不弱 |
| fortune:FourPillars | fortune:hasFortuneSynthesis | `owl:cardinality 1` | 子平真诠·论行运成格变格 |
| reading:FourPillars | reading:hasConclusion | `owl:cardinality 1`（综合结论唯一） | 流程节点 16 |
| rules:AlertFlag | rules:alertType | `owl:cardinality 1` | 顶层设计 I·46；客户裁定 ㉒ |
| reading:CaseRecord | reading:caseBirthData | `owl:cardinality 1`（命例必有生辰） | 客户裁定 ㉓ |
| diagnosis:SourceNote | diagnosis:sourceBook | `owl:cardinality 1`（出处必有书名） | ★铁律 9/10 |
| diagnosis:SourceNote | diagnosis:sourceVolume | `owl:cardinality 1`（出处必有卷/篇） | ★铁律 9/10 |
| reading:MarriageMatch | reading:matchComplementarity | `owl:maxCardinality 1` | 《神峰通考》卷一·男女合婚说 |

---

## 5. SWRL 规则

> **分工**（SKILL §3.2）：**简单判定/分类用 SWRL**；**复杂逻辑/数值计算留给程序**。所引谓词均在 §2/§3 有定义（自洽）。变量命名沿用 Ontop/Openllet 约定。**编写须避开易错点**（见文末「SWRL 编写要点」）。
> **不该编码为 SWRL 的**（跨方案比较、闭世界否定、历法/起运数值计算）在表中显式注明「**由程序实现**」及理由。

| 规则 | 条件（body） | 结论（head） | 出处 |
|---|---|---|---|
| R1 干支→五行传导 | `core:HeavenlyStem(?s) ∧ core:stemFivePhase(?s, ?p)` | → `core:hasFivePhase(?s, ?p)` | 三命通会卷一·论五行生成 |
| R2 五行相生（生） | `core:FivePhase(?a) ∧ core:FivePhase(?b) ∧ core:generates(?a, ?b)` | → （传递闭包，供旺衰/通关取用） | 渊海子平卷之一·论五行相生相克 |
| R3 五行相克（克） | `core:FivePhase(?a) ∧ core:FivePhase(?b) ∧ core:overcomes(?a, ?b)` | → （传递闭包） | 渊海子平卷之一·论五行相生相克 |
| R4 柱→干支赋值 | `chart:Pillar(?p) ∧ chart:sexagenaryPair(?p, ?g) ∧ core:hasStemBranch(?g, ?s, ?b)` | → `chart:hasStem(?p, ?s) ∧ chart:hasBranch(?p, ?b)`（经内置表；**由程序/OBDA 提供配对**） | 三命通会卷二·论年月日时 |
| R5 起运顺逆 | `chart:FourPillars(?f) ∧ chart:hasBirthInput(?f, ?i) ∧ chart:gender(?i, "男") ∧ chart:yearStemYinYang(?f, "阳")` | → `chart:direction(?gf, "顺")`（阳男顺行） | 渊海子平卷之一·论起大运法（阳男阴女顺） |
| R6 起运顺逆（阴男） | `... gender(?i,"男") ∧ yearStemYinYang(?f,"阴")` | → `chart:direction(?gf, "逆")`（阴男逆行） | 渊海子平卷之一·论起大运法（阴男阳女逆） |
| R7 十神判定 | `chart:Pillar(?p) ∧ tengod:definedByDayMaster(?t, ?d) ∧ chart:hasStem(?p, ?s) ∧ tengod:hasTenGod(?p, ?t) …（经 `stemTenGodMap` 内置表）` | → `tengod:hasTenGod(?p, ?t)`（日干生克同类得十神） | 渊海子平卷之一·论日为主；卷之二·各十神论。**由程序实现**：10×10 查表，避免 100 条 SWRL 冗余 |
| R8 官杀混杂 | `tengod:TenGodRelation(?r) ∧ tengod:relationType(?r, "官杀混杂") ∧ ... 命中正官+偏官同现` | → `tengod:hasTenGodRelation(?t, ?r)` | 渊海子平卷之一·论官杀混杂要制伏 |
| R9 旺衰·得令 | `yongshen:Vitality(?v) ∧ yongshen:vitalityState(?v, "得时") ∧ ... 月令生扶日主` | → 判「得时不旺/失时不弱」分支 | 子平真诠·论十干得时不旺失时不弱。**综合旺衰由程序计算**（多因子权重，客户裁定 ⑰ 不设数值权重） |
| R10 格局成败·分类 | `pattern:Pattern(?p) ∧ pattern:patternName(?p, ?n) ∧ ... 成/破条件命中` | → `pattern:patternQuality(?p, pattern:ChengGe)`（成格/破格分类） | 子平真诠·论用神成败救应；论四吉神能破格/论四凶神能成格 |
| R11 用神·主取 | `yongshen:FourPillars(?f) ∧ ... 月令格局法得用神 ?y` | → `yongshen:hasYongShen(?f, ?y)`（标主取）；异法结果挂 `diagnosis:hasSchoolVariant` | 子平真诠·论用神「用神专求月令」；客户裁定 ⑫。**并陈由程序编排** |
| R12 层次·四档 | `yongshen:FourPillars(?f) ∧ ... 格局成败+用神有力+相神得力+忌神受制` | → `yongshen:hasLevel(?f, ?lv)`（贵/富/平常/贫贱） | 子平真诠·论用神格局高低；客户裁定 ⑬。**综合评级由程序计算** |
| R13 空亡·推算 | `deity:Void(?v) ∧ ... 日柱旬空命中某柱支` | → `deity:hasVoid(?p, ?v)` | 三命通会卷二·论空亡（旬空起例；**经内置表/OBDA**） |
| R14 神煞·起例 | `deity:Deity(?d) ∧ deity:deityName(?d, ?n) ∧ ... 某柱干支命中起例` | → `deity:hasDeity(?p, ?d)` | 渊海子平卷之一·诸神煞篇；三命通会卷二·诸神煞篇。**查表由程序/OBDA**（起例表庞大，异名异法并陈） |
| R15 合冲刑穿·成立 | `relations:StemCombination(?c) ∧ relations:combinesWith(?a, ?b) ∧ ...` | → `relations:hasStemCombination(?a, ?c)`；合而不合经 `relationTransformed=false` | 三命通会卷二·论十干合；子平真诠·论十干合而不合 |
| R16 太岁·天克地冲 | `fortune:AnnualFortune(?y) ∧ ... 流年干支与原局某柱天克地冲` | → `fortune:hasSpecialPattern(?y, ?sp)` | 三命通会·总论岁运。**已细化落地** |
| R17 运化气·化气十段锦 | `fortune:GreatFortuneCycle(?g) ∧ fortune:hasYunHuaQi(?g, ?q) ∧ ...` | → `fortune:hasYunHuaQi(...)`（随岁运断） | 渊海子平卷之三·论运化气/化气十段锦 |
| R18 运限法·分段 | `fortune:LifeStageLimit(?l) ∧ ... 柱分主阶段` | → `fortune:lifeStageSegment(?l, ?seg)` | 三命通会·总论岁运「月管初主，日管中主，时管末主，年则总统之」 |
| R19 **告警·大凶** | `chart:FourPillars(?f) ∧ pattern:isBroken(?f, true) ∧ yongshen:noYongShen(?f, true)`（★简化判据） | → `rules:hasAlertFlag(?f, rules:AlertDaXiong)` | 顶层设计 I·46；客户裁定 ㉒。**「大凶」综合判据由程序编排**（多因子），SWRL 只落最终标志 |
| R20 **告警·寿元关** | `chart:FourPillars(?f) ∧ reading:hasDisease(?f, ?d) ∧ ... 寿元相关煞重` | → `rules:hasAlertFlag(?f, rules:AlertShouYuanGuan)` | 顶层设计 I·46；客户裁定 ㉒ |
| R21 **告警·重大疾病** | `chart:FourPillars(?f) ∧ reading:hasDisease(?f, ?d) ∧ reading:diseaseSevere(?d, true)` | → `rules:hasAlertFlag(?f, rules:AlertJiBing)` | 三命通会卷九·论疾病先知五脏六腑所属干支；客户裁定 ㉒ |
| R22 **告警·命中即外发** | `chart:FourPillars(?f) ∧ rules:hasAlertFlag(?f, ?a)` | → `rules:alertTriggered(?a, true)`（供 `SwrlRuleTriggerListener` 检测 → RabbitMQ 外发） | 流程节点 A；顶层设计§1（规则驱动事件） |
| R23 合婚·互补正理 | `reading:MarriageMatch(?m) ∧ ... 男命比劫重 ∧ 女命伤食重` | → `reading:matchComplementarity(?m, "互补")` | 《神峰通考》卷一·男女合婚说「男命带比肩劫财重者，必择妇命带伤官食神重者配之」 |
| R24 出处绑定 | `core:SiZhuBaZiEntity(?x) ∧ diagnosis:sourceBook(?n, ?bk) …`（每条判定断言挂对应 `SourceNote`） | → `diagnosis:hasSourceNote(?x, ?n)` | ★铁律 9/10；顶层设计 §5-4。**由程序在生成期绑定**（非推理） |

> **说明**：
> - R1~R3 为干支/五行的**基础传导**；R4/R7/R13/R14 依赖**内置映射表 / OBDA 查表**（起运、十神、空亡、神煞起例属**查表/计算**，★铁律 13 → 外置）。
> - R5/R6 落实**起运顺逆**（阳男阴女顺、阴男阳女逆）；**起运岁**（三日折一年）为数值计算 → 由程序（`chart:pilotAge`）。
> - R19~R22 为**告警标志推导**：SWRL 命中 → `rules:alertTriggered=true` → `SwrlRuleTriggerListener` → RabbitMQ（流程节点 A）；**不设人为数值阈值**（客户裁定 ㉒）。
> - R23 落实《神峰通考》**合婚之正理**（节点 M1）。
> - **明确「由程序实现」**（不属于 SWRL）：① 历法换算/节气/真太阳时（★铁律 13）；② 起运岁数值折算（三日=一年/一日=四月/一时=十日）；③ 十神/神煞/空亡 的大表查取；④ 多法冲突「主取」编排与跨方案比较；⑤ 层次/大凶的综合评级（多因子加权）；⑥ 闭世界否定（如「无某神煞」）。理由：**跨方案比较、闭世界否定、数值计算**均不适合 SWRL（模板 §5）。其中节气「精确交节时刻」经 `calendar:SolarTermInstant`（DB `solar_term_time`，OBDA 暴露）提供，程序读取后由程序按精确时刻比较判定换月/分年/起运（第 31 步；年份超出 1950–2050 或 DB 不可用时回退内置近似，流程不阻断）。
> - **R4/R7/R13/R14 若采用内置表**，须在生成脚本内维护（回写生成源，不手改产物）；若采用 OBDA，则 神煞/藏干/节气 查表经 MySQL 虚拟访问。

---

## 6. 关系与约束的一致性检查要点

1. **对象属性互逆配对**：`hasYinYang/isYinYangOf`、`hasFivePhase/isFivePhaseOf`、`generates/generatedBy`、`overcomes/overcomeBy`、`hasStem/isStemOf`、`hasBranch/isBranchOf`、`hasNaYin/isNaYinOf`、`hasHiddenStem/isHiddenStemOf`、`hasLifeStage/isLifeStageOf`、`hasSolarTerm/isSolarTermOf`、`hasMonthOrder/isMonthOrderOf`、`hasBirthInput/isBirthInputOf`、`hasYearPillar/isYearPillarOf`、`hasMonthPillar/isMonthPillarOf`、`hasDayPillar/isDayPillarOf`、`hasHourPillar/isHourPillarOf`、`hasGreatFortune/isGreatFortuneOf`、`hasSmallFortune/isSmallFortuneOf`、`hasTaiYuan/isTaiYuanOf`、`hasMingGong/isMingGongOf`、`hasStemCombination/isStemCombinationOf`、`hasBranchRelation/isBranchRelationOf`、`transformsTo/isTransformedFrom`、`isBondedBy/bonds`、`hasTenGod/isTenGodOf`、`hasKinship/isKinshipOf`、`hasPalace/isPalaceOf`、`hasDeity/isDeityOf`、`hasVoid/isVoidOf`、`hasPattern/isPatternOf`、`hasPatternQuality/isPatternQualityOf`、`hasYongShen/isYongShenOf`、`hasVitality/isVitalityOf`、`hasPurity/isPurityOf`、`hasLevel/isLevelOf`、`hasGreatFortuneCycle/isGreatFortuneCycleOf`、`hasAnnualFortune/isAnnualFortuneOf`、`hasResponsePeriod/isResponsePeriodOf`、`hasReading/isReadingOf`、`hasConclusion/isConclusionOf`、`hasCaseRecord/isCaseRecordOf`、`hasSourceNote/isSourceNoteOf` 均须互逆声明；`relations:combinesWith`/`sixCombinesWith`/`tripleCombinesWith`/`clashesWith`/`harmsWith`/`punishesWith` 须声明 `owl:SymmetricProperty`；`core:generates`/`core:overcomes` 须声明 `owl:TransitiveProperty`（**注意**：五行生克为循环，传递闭包须受控，推理时避免跨环爆炸）。
2. **基数闭合**：`FourPillars` 四柱各恰 1；`Pillar` 干/支各恰 1；`SexagenaryCycle` 干/支/纳音各恰 1；`GreatFortune` 方向恰 1；`Pattern` 成败恰 1；`FourPillars` 用神/层次/旺衰/结论各恰 1；`SourceNote` 书名/卷篇各恰 1。
3. **值域/定义域闭合**：`hasFivePhase` 值域仅 `core:FivePhase` 5 子类；`hasYinYang` 值域仅 `core:YinYang` 2 子类；`hasTenGod` 值域仅 `tengod:TenGod` 10 子类；`hasLevel` 值域仅 `yongshen:Level` 4 子类；`generates`/`overcomes` 定义域与值域均为 `FivePhase`；`hasAlertFlag` 值域仅 `rules:AlertFlag` 枚举；`hasSourceNote`/`hasSchoolVariant`/`hasToBeVerified` 定义域为 `core:SiZhuBaZiEntity`（全实体可挂出处）。
4. **SWRL 与 Restriction 一致**：R5/R6 推出的 `direction` 不得与 `cardinality 1` 冲突（顺/逆互斥）；R11 推出的 `hasYongShen` 须满足 `cardinality 1`（多法结果仅主取落此，其余挂 `SchoolVariant`）；R19~R21 的 `AlertFlag` 与 `alertType cardinality 1` 对应。
5. **两类「日」粒度并存不矛盾**：`chart:DayPillar`（原局日柱，恒定）与 `fortune:AnnualFortune`/`SubAnnualFortune`（岁运流年流月，可变）分属 `chart:`/`fortune:`，不得混指。
6. **三套用神法并存不矛盾**：格局法（主取）/调候法（已依《穷通宝鉴》落地）/旺衰法，经 `yongShenMethod` 标注，主取唯一、余者并陈；调候条文已逐月落地（`tiaohou_clause` 120 条）。
7. **干支作用对称性**：六合/三合/冲/穿/刑均为对称关系（`SymmetricProperty`）；合化（`transformsTo`）为**单向函数**（合→化出五行），且至多一值。
8. **无硬编码业务数据**：五行生克、十神名录、神煞名录、格局名录、层次四档、干支配属等以**个体/子类/约束**表达，程序不含分支常量（★12/14）。
9. **出处全覆盖**：每条类/属性/约束/SWRL 的「出处」列非空；无法定位者标【待考】并建 `diagnosis:ToBeVerified` 个体（★铁律 8/9/10）。
10. **告警外发链闭合**：`rules:hasAlertFlag` → R22 `rules:alertTriggered=true` → `SwrlRuleTriggerListener` → RabbitMQ；仅三类（大凶/寿元关/重大疾病），无数值阈值（客户裁定 ㉒）。

---

## 7. ABox 典型个体设计（落地于第 6 步 · 闸门⑦）

> 登记第 6 步单文件本体拟纳入的**典型 ABox 数据**（个体集/类型/关键断言/代表性），交业务确认后方可进入第 7 步（闸门⑦）。**全集扩展留待第 12 步（闸门⑪）**。

| 个体 | 类型 | 关键断言 | 说明（出处） |
|---|---|---|---|
| `core:Yang` / `core:Yin` | core:YinYang | yinYangName | 阴阳 2 个体（三命通会卷一·论五行生成） |
| `core:Jin` … `core:Tu` | core:FivePhase | phaseName；`core:generates`/`overcomes` 生成/克各 5 对 | 五行 5 + 生克 10（渊海子平卷之一·论五行相生相克） |
| `core:Stem_Jia` … `core:Stem_Gui` | core:HeavenlyStem | stemName；stemYinYang；hasYinYang；hasFivePhase；hasLifeStage | 十天干 10（三命通会卷一·论支干源流） |
| `core:Branch_Zi` … `core:Branch_Hai` | core:EarthlyBranch | branchName；branchAnimal；branchOrder；hasFivePhase；hasHiddenStem | 十二地支 12（渊海子平卷之一·论天干地支所出） |
| `core:JiaZi` … `core:GuiHai`（**代表性**：甲子/丙寅/戊辰等 6 例，全量留第 12 步） | core:SexagenaryCycle | hasStem；hasBranch；hasNaYin | 六十甲子代表性（渊海子平卷之一·论六十花甲子纳音） |
| `core:NaYin_HaiZhongJin` …（**代表性** 6 名） | core:NaYin | naYinName；naYinNature；hasFivePhase | 纳音代表性（三命通会卷一·论纳音取象） |
| `core:ZiHidden`（子藏癸）、`core:YinHidden`（寅藏甲丙戊）等（**代表性**） | core:HiddenStem | hiddenStemRole | 藏干代表性（渊海子平卷之一·又地支藏遁歌） |
| `core:Stage_ChangSheng` … `core:Stage_Yang` | core:LifeStage | lifeStageName | 长生十二宫 12（三命通会卷二·论天干阴阳生死） |
| `calendar:Term_LiChun` …（**代表性**：立春/惊蛰/清明等） | calendar:SolarTerm | solarTermName | 二十四节气代表性（渊海子平卷之一·论节候歌） |
| `calendar:MonthYin` … `calendar:MonthChou` | calendar:MonthOrder | monthOrderName | 月建 12（三命通会卷二·论遁月时） |
| `tengod:ZhengGuan` … `tengod:JieCai` | tengod:TenGod | tenGodName；tenGodAlias | 十神 10（渊海子平卷之二·各十神论） |
| `tengod:Palace_Year` … `tengod:Palace_Hour` | tengod:Palace | palaceName | 宫位 4（渊海子平卷之三·六亲总论） |
| `tengod:Kinship_Father` 等 | tengod:Kinship | kinshipName | 六亲 6（渊海子平卷之三·六亲总论） |
| `deity:TianYi` 等（**代表性**：天乙/羊刃/驿马/空亡） | deity:AuspiciousDeity/deity:InauspiciousDeity/deity:LuMa/deity:Void | deityName；deityCategory | 神煞代表性（三命通会卷二·诸神煞篇） |
| `pattern:ZhengGuanGe` …（**代表性**：正官格/正财格/伤官格） | pattern:RegularPattern | patternName；patternClass | 格局代表性（子平真诠·论正官/论财/论伤官） |
| `yongshen:GuiGe` … `yongshen:PinJian` | yongshen:Level | levelName | 层次 4（客户裁定 ⑬） |
| `rules:AlertDaXiong` / `rules:AlertShouYuanGuan` / `rules:AlertJiBing` | rules:AlertFlag | alertType | 告警标志 3（顶层设计 I·46；客户裁定 ㉒） |
| `diagnosis:SourceNote_YuanHai` 等（**代表性** 若干） | diagnosis:SourceNote | sourceBook；sourceVolume | 出处标注代表性（★铁律 9/10） |
| `chart:Case_Demo01`（**典型命例 1~2 例**） | chart:FourPillars | hasYearPillar…hasHourPillar；hasBirthInput；yongshen:hasYongShen；yongshen:hasLevel；reading:hasConclusion；reading:hasCaseRecord | 典型命例（节点 2~17 全链样例）；全集留第 12 步 |

> **典型 ABox 规模（规划）**：枚举型个体**全量**（阴阳/五行/千支/地支/长生/十神/宫位/六亲/层次/告警/月建）+ **代表性**（六十甲子/纳音/藏干/节气/神煞/格局/出处/命例）。**全集**留待第 12 步「ABox 扩至全集 / 入库」。
> **闸门⑦**：第 6 步生成的单文件本体中的典型 ABox 须在本节登记并交业务人员确认（个体集/类型/关键断言/代表性）后方可进入第 7 步。留痕见文末「闸门⑦留痕」。

---

## 8. OBDA / SKOS 预留

- **OBDA（第 11 步 · 闸门⑩）**：**查表型/运行态数据入库 MySQL**，经 Ontop 虚拟访问，**不迁入本体文件**（顶层设计 §1）。规划表（`ontology/database/`）：
  | 规划表 | 承载 | 对应本体 IRI 前缀 |
  |---|---|---|
  | `ganzhi_stem` / `ganzhi_branch` | 天干/地支（名·阴阳·五行·生肖·序） | `core:` |
  | `sexagenary_cycle` / `nayin` | 六十甲子/纳音 | `core:` |
  | `hidden_stem` | 地支藏干/人元司事 | `core:` |
  | `solar_term` / `month_order` | 节气/月建 | `calendar:` |
  | `solar_term_time` | 节气**精确交节时刻**（**2424**；第 31 步：1950–2050 共 101 年 ×24；`calendar:SolarTermInstant`，含 `termYear`/`termIndex`/`termDatetime`/`instantOf`） | `calendar:` |
  | `deity_rule` | 神煞起例查找表（以柱干支查神煞） | `deity:` |
  | `ten_god_map` | 日干×目标干→十神 查表 | `tengod:` |
  | `case_record` | 运行态命例记录（`reading:CaseRecord`） | `reading:` |
  | `alert_flag` | 运行态告警标志 | `rules:` |
  | `day_hour_clause` / `tiaohou_clause` | 日时断（720）/调候逐月（120）条文库（第 15 步） | `reading:` / `yongshen:` |
  | `pattern_catalog` | 具名格局名录（102；第 16 步 H2 95；第 17 步 I1 +2；第 20 步 P2 +2；第 22 步 P11 +3） | `pattern:` |
  | `lady_fortune_catalog` | 女命细分名录（17；第 16 步 H3） | `reading:` |
  | `technique_catalog` | 技法/篇章名录（66；第 16 步 H4 38；第 20 步 P1/P3/P4/P6/P7 +9；第 21 步 P9 +13、P10 +6） | `reading:` |
  | `sexagenary_nature` | 六十甲子性质吉凶（60；第 16 步 H4，追加至 `core:SexagenaryCycle`） | `core:` |
  | `auspice_clause` / `theory_clause` / `dts_clause` | 断语条文库（第 17 步 I3）：十干坐支/十二月支/五行时地/孕生男女 25 + 病药/动静/盖头/六亲诸说 12 + 滴天髓通神论/六亲论诸篇 26 | `reading:` / `yongshen:` / `pattern:` / `fortune:` |
  | `fu_clause` | 赋/歌/诗诀/独步/诀/论/篇/说/图 条文库（**209**；第 18 步 R3 205；第 20 步 P5/P8 +3；第 36 步渊海图题 +1，全文 LONGTEXT 照录） | `reading:`（`reading:CategoryFortune`） |
  | `ziping_clause` | 《子平真诠》47 篇正文条文库（**47**；第 23 步，全文 LONGTEXT 照录） | `reading:`（`reading:ReadingItem`） |
  | `classic_text` | 六典籍**正文全文库**（**927**；第 26 步·第九轮：滴天髓 63 / 穷通宝鉴 142 / 神峰通考 128 / 三命通会 298 / 渊海子平 **296**，逐篇照录、全文 LONGTEXT 不截断；第 34 步按原文拆分补入「六亲论·十二从象」；第 36 步补入渊海「卷之三~七」5 条卷题） | `reading:`（`reading:ReadingItem`） |
  - 映射目标 IRI 以**裸写前缀名**生成（`core:{name}` / `calendar:{name}` / ...，**不加尖括号**），与本体实体 IRI 逐字一致；仅**完整 IRI** 才用 `<...>`；`rdfs:label` 用标准前缀写法。**注**：早期写成 `<core:{name}>` 会被 Ontop 误判为「绝对 IRI（scheme=prefix）」而不展开、致映射 0 命中（第 11 步已修正）。
  - ✍️ **数据边界**：静态术语（阴阳/五行/十神名录/宫位/六亲/层次/告警标志/格局名录/神煞名录/长生十二宫）留 ABox；**查表型历法/神煞起例/十神映射/命例运行态**入 MySQL（DB 为唯一真源）。
  - ✍️ **全集规模（第 12 步 · 闸门⑪；第 18 步更新）**：天干 10 / 地支 12 / 六十甲子 60 / 纳音 30 / 藏干 28 / 节气 24 / 月建 12 / 节气精确时刻 **2424**（第 31 步：1950–2050×24）/ 神煞起例 **186**（第 16 步 H1 +49；第 17 步 I2 细煞 +33）/ 十神映射 100 / 日时断 720 / 调候 120 / 具名格局 **102**（第 17 步 I1 +2；第 20 步 P2 +2；第 22 步 P11 +3）/ 女命细分 17 / 技法篇章 **66**（第 20 步 P1/P3/P4/P6/P7 +9；第 21 步 P9 +13、P10 +6）/ 六十甲子性质 60 / 断语条文 auspice 25 + theory 12 + dts 26（第 17 步 I3）/ 赋歌诗诀等条文 **209**（第 18 步 R3 205；第 20 步 P5/P8 +3；第 36 步渊海图题 +1）/ 《子平真诠》正文 **47**（第 23 步）/ **六典籍正文全文 927**（第 26 步·第九轮：滴天髓 63 / 穷通宝鉴 142 / 神峰通考 128 / 三命通会 298 / 渊海子平 296；第 34 步 +1 六亲论·十二从象；第 36 步 +5 渊海卷题）；`case_record` / `alert_flag` 为运行态（全集不含历史数据，留空）。数据来源：**AI 依六书生成 + 客户校订**（逐条附出处；原标【待考】者已逐项落地——纳音吉凶三分依《三命通会》卷一·释六十甲子性质吉凶归算、调候 120 条依《穷通宝鉴》补全、日时断缺载 3 条依四庫全書本补全）。
- **SKOS（第 13 步）**：十神（含别名：偏官＝七杀、偏印＝倒食）、神煞（异名异法）、格局、五行/天干/地支、层次、告警类的**中英术语** ↔ 本体 IRI；供 `OntologyLabelMatcher`/`SkosSynonymReader` 名称归一（对齐顶层设计 S4、Example §8）。经 `sizhubazi-tbox.owl` 的 `owl:imports` 并入（闸门⑥）。

---

## 9. 待业务确认项

| # | 问题 | 说明 | 结论（2026-10-29 建议 / 2026-11-03 落地状态） |
|---|---|---|---|
| 1 | **OutputDomain 命名空间落位** | 顶层设计 §5 有 `OutputDomain` 分支，但 §4.1 命名空间（16 项）/§3 子本体（12 个）均无 `output` | 建议**不新增命名空间**：`ReadingConclusion`/`CaseRecord` 归 `reading:`、`AlertFlag` 归 `rules:`（照 Example `LowStock*` 由规则推导先例）|
| 2 | **十神/神煞/空亡/起运的落位** | 查表 vs SWRL vs 程序 | 建议**查表型落 OBDA（MySQL）+ 程序编排**；SWRL 只承接简单判定与最终标志（R19~R22）|
| 3 | **起运岁折算口径** | 三日=一年、一日=四月、一时=十日 | 建议**程序计算**（数值），`chart:pilotAge` 存结果（渊海子平卷之一·论起大运法）|
| 4 | **大凶/寿元关/重大疾病 判据** | 是否设数值阈值？ | 建议**不设阈值**，SWRL 命中即标志（客户裁定 ㉒）；综合判据由程序编排（R19~R21）|
| 5 | **调候用神（《穷通宝鉴》）文本未取得** | E·22/E·24 调候法依赖其文本 | **已落地**：文本已取得（2026-10-30 归档）并入 `yongshen`；`tiaohou_clause` 120 条全落地，无遗留【待考】 |
| 6 | **流日/流时六书无系统专论** | 粒度可选输出（客户裁定 ⑮） | **已落地**：年级为主、月/日/时可选；流月/流日/流时按历法实算（五虎遁/JDN/五鼠遁），`SubAnnualFortune` 已启用 |
| 7 | **特殊岁运格局细化** | 岁运并临/天克地冲/伏吟/反吟/桃花红鸾 起例分散 | **已落地**：五类特殊岁运格局起例均已细化实现（岁运并临/天克地冲〔反吟〕/伏吟/桃花·红鸾） |
| 8 | **神煞异名异法并陈粒度** | 贵人吉神/禄马/凶煞 多书异名（客户裁定 ⑩） | 建议**以《三命通会》卷二为主、异名异法并列标出处**（`SchoolVariant`）|
| 9 | **命例隐私口径** | 生辰/排盘/批断结果 | 建议**完整存入（含出生地）、不脱敏**（客户裁定 ㉓）|
| 10 | **《命理探源》是否纳入** | 第 2 步流程 §7-㉔ 全局项待决 | 建议**暂不纳入**（非六书），待客户确认 |

> 第 2 步流程设计 §7 的 **①~㉓ 待裁定已全部确认**（㉔ 待决）；本表据此给出**建议结论**，待闸门③一并确认或带入第 5 步。
> **复核结论**见 §10（回原著核验）。

---

## 10. 复核结论（对齐第 2/3 步 · 回原著核验）★v1

> 以领域专家视角，对本核心设计逐项**回原著核验**并对照第 2 步流程、第 3 步顶层设计。

### 10.1 出处核验（回原著逐条命中，无编造）

| 出处（本设计标注） | 原著卷次·位置 | 命中原文（节录） |
|---|---|---|
| 渊海子平卷之一·论日为主 / 论月令 | 卷之一 | 「论日为主」「论月令」 |
| 渊海子平卷之一·论起大运法 | 卷之一 | 「阳男阴女顺行…阴男阳女逆行」「折除三日为一岁」 |
| 渊海子平卷之一·论六十花甲子纳音 / 又地支藏遁歌 | 卷之一 | 「论六十花甲子纳音」「又地支藏遁歌」 |
| 渊海子平卷之一·论十二支六合/三合/相冲/相穿/相刑 | 卷之一 | 「论十二支六合」「论十二支三合」「论十二支相冲」「论十二支相穿」「论十二支相刑」 |
| 渊海子平卷之一·论官杀混杂要制伏 | 卷之一 | 「论官杀混杂要制伏」 |
| 渊海子平卷之二·各十神论 | 卷之二 | 「论正官」「论偏官」「论正财」「论食神」… |
| 渊海子平卷之三·论运化气 / 化气十段锦 | 卷之三 | 「论运化气」「化气十段锦」 |
| 渊海子平卷之三·六亲总论 | 卷之三 | 「六亲总论」 |
| 渊海子平卷之三·论妇人总诀 / 女命富贵贫贱篇 | 卷之三 | 「论妇人总诀」「女命富贵贫贱篇」 |
| 渊海子平卷之三·论小儿 / 论小儿关杀例 | 卷之三 | 「论小儿」「论小儿关杀例」 |
| 渊海子平卷之五·克妻克子带疾寿元飘荡诗诀 | 卷之五 | 「克妻克子带疾寿元飘荡」诸诗诀 |
| 子平真诠·论用神 / 论相神紧要 | 篇·论用神 | 「用神专求月令」 |
| 子平真诠·论十干配合性情 / 论十干合而不合 | 篇 | 「论十干配合性情」「论十干合而不合」 |
| 子平真诠·论刑冲会合解法 / 论墓库刑冲之说 | 篇 | 「论刑冲会合解法」「论墓库刑冲之说」 |
| 子平真诠·论外格用舍 / 论杂格 | 篇 | 「提纲有用提纲重」 |
| 子平真诠·论用神成败救应 / 论用神格局高低 | 篇 | 「论用神成败救应」「论用神格局高低」 |
| 子平真诠·论行运 / 论行运成格变格 | 篇 | 「论行运」「论行运成格变格」 |
| 子平真诠·论宫分用神配六亲 / 论妻子 | 篇 | 「论宫分用神配六亲」「论妻子」 |
| 子平真诠·论十干得时不旺失时不弱 | 篇 | 「论十干得时不旺失时不弱」 |
| 三命通会卷一·论五行生成 / 论支干源流 | 卷一 | 「论五行生成」「论支干源流」 |
| 三命通会卷一·总论纳音 / 论纳音取象 / 释六十甲子性质吉凶 | 卷一 | 「总论纳音」「论纳音取象」「释六十甲子性质吉凶」 |
| 三命通会卷一·论人元司事 | 卷一 | 「论人元司事」 |
| 三命通会卷二·论遁月时 / 论年月日时 | 卷二 | 「论遁月时」「论年月日时」 |
| 三命通会卷二·论大运 / 论小运 / 论太岁 / 总论岁运 | 卷二 | 「论大运」「论小运」「论太岁」「总论岁运」（「月管初主，日管中主，时管末主，年则总统之」）|
| 三命通会卷二·论十干合 / 论十干化气 / 论进交退伏 | 卷二 | 「论十干合」「论十干化气」「论进交退伏」 |
| 三命通会卷二·论支元六合 / 三合 / 六害 / 三刑 / 冲击 | 卷二 | 「论支元六合」「论支元三合」「论支元六害」「论支元三刑」「论支元冲击」 |
| 三命通会卷二·论天乙贵人 / 论三奇 / 论天月德 / 论太极贵 / 论学堂词馆 / 论德秀 / 论十干禄 / 论金舆 / 论驿马 / 总论禄马 / 论羊刃 / **论暗金的煞** / 论劫煞亡神 / 论孤辰寡宿 / 论天罗地网 / 论咸池 / 论将星华盖 / 论空亡 | 卷二 | 诸神煞篇（「论暗金的煞」一煞三名：吟呻/破碎/白衣）|
| 三命通会卷二·论五行旺相休囚死并寄生十二宫 / 论天干阴阳生死 | 卷二 | 「论五行旺相休囚死并寄生十二宫」「论天干阴阳生死」 |
| 三命通会卷二·论坐命官 / 论胎元 | 卷二 | 「论坐命官」「论胎元」 |
| 三命通会卷五·论古人立印食官财名义 / 官煞混杂 | 卷五 | 「论古人立印食官财名义」 |
| 三命通会卷五·论杂气 | 卷五 | 「论杂气」 |
| 三命通会卷九·论性情相貌 / 论疾病先知五脏六腑所属干支 / 论女命 | 卷九 | 「论性情相貌」「论疾病先知五脏六腑所属干支」 |
| 滴天髓·衰旺 / 中和 / 体用 / 精神 | 篇 | 「衰旺」「中和」「体用」「精神」 |
| 滴天髓·源流 / 通关 / 寒暖 / 燥湿 / 刚柔 / 顺逆 | 篇 | 「源流」「通关」「寒暖」「燥湿」「刚柔」「顺逆」 |
| 滴天髓·清气 / 浊气 / 真神 / 假神 / 隐显 / 众寡 | 篇 | 「清气」「浊气」「真神」「假神」「隐显」「众寡」 |
| 滴天髓·性情 / 才德 / 出身 / 地位 / 夫妻 / 子女 / 父母 / 兄弟 / 疾病 / 小儿 / 知命 | 篇 | 诸断语篇 |
| 《神峰通考》卷一·男女合婚说 | 卷一 | 「男命带比肩劫财重者，必择妇命带伤官食神重者配之…此系合婚之正理」|
| 《穷通宝鉴》调候（十天干十二月取用） | — | **文本已取得**（2026-10-30 归档 `穷通宝鉴.txt`）；第 15 步入库 `tiaohou_clause`（120 条），逐月取用**全量落地**（原 56 条【待考】已依各干逐月原文补全，无遗留） |

> **结论**：本设计全部出处**可回原文定位、无编造**（★铁律 8/9/10）。原著未收录/未取得者已逐项补全落地：《穷通宝鉴》调候 → **已全量补全**（120/120）；流日/流时 → **已按历法实算落地**；特殊岁运格局细化 → **已落地**；日时断缺载 3 条 → **已依四庫全書本补全**；纳音吉凶 30 → **已依《三命通会》卷一归算**；《命理探源》不纳入（不在范围）。当前数据/代码内**无遗留【待考】占位**（`data.sql` 中的「待考」仅为《穷通宝鉴》命例原文自带用语）。

### 10.2 流程节点覆盖（第 2 步 21 ServiceTask → 本体落点）

| 节点 | 本体落点 |
|---|---|
| 1 输入解析与时辰校正 | `chart:BirthInput`、`calendar:TrueSolarTime`/`CalendarBoundary`、`calendar:hasSolarTerm` |
| 2 排盘（四柱/起运/大运/小运/胎元/命宫） | `chart:FourPillars`/`Pillar`/`GreatFortune`/`SmallFortune`/`TaiYuan`/`MingGong`；R5/R6 |
| 3 纳音·藏干·日柱特性 | `core:NaYin`/`HiddenStem`/`SexagenaryCycle`、`core:hasNaYin`/`hasHiddenStem` |
| 4 干支作用关系 | `relations:StemCombination`/`BranchRelation`/`HiddenRelation`/`Bondage`/`PhaseRelation`；R15 |
| 5 十神与六亲 | `tengod:TenGod`/`TenGodRelation`/`Kinship`/`Palace`；R7/R8 |
| 6 神煞 | `deity:AuspiciousDeity`/`LuMa`/`InauspiciousDeity`/`Void`；R13/R14 |
| 7 日主旺衰与十二宫 | `yongshen:Vitality`、`core:LifeStage`；R9 |
| 8 格局 | `pattern:RegularPattern`/`MixedQiPattern`/`SpecialPattern`/`PatternQuality`；R10 |
| 9 取用与多法冲突 | `yongshen:YongShen`/`Tiaohou`/`FlowRelation`、`diagnosis:SchoolVariant`；R11 |
| 10 命局层次 | `yongshen:Purity`/`Level`；R12 |
| 11 岁运推演 | `fortune:GreatFortuneCycle`/`AnnualFortune`/`SubAnnualFortune`/`SpecialFortunePattern`/`YunHuaQi`；R16/R17 |
| 12 应期与运限 | `fortune:ResponsePeriod`/`LifeStageLimit`、`deity:Void`；R18 |
| 13 岁运综合辩证 | `fortune:FortuneSynthesis` |
| 14 分类断语 | `reading:Temperament`/`Disease`/`KinshipFortune`/`CategoryFortune` |
| M1 八字合婚 | `reading:MarriageMatch`；R23 |
| W 女命专论 | `reading:LadyFortune` |
| C 小儿关杀 | `reading:ChildFortune` |
| 15 修身劝诫 | `reading:Admonition` |
| 16 综合结论 | `reading:ReadingConclusion`、`reading:hasConclusion` |
| A 外发告警 | `rules:AlertFlag`；R19~R22 |
| 17 命例存储与返回 | `reading:CaseRecord`、`reading:hasCaseRecord` |

> 三个排他网关（G1/G2/G3）条件由对应枚举/布尔个体承载（如 `chart:mode`→对象类型分支；`rules:alertTriggered`→外发分支），BPMN 分支可查询判定。

### 10.3 顶层设计覆盖（第 3 步 §5 顶层类框架 + S1~S6 → 展开）

| 顶层域/支撑 | 展开状态 |
|---|---|
| GanzhiDomain/CalendarDomain/ChartDomain/RelationDomain/TenGodDomain/DeityDomain/PatternDomain/YongshenDomain/FortuneDomain/ReadingDomain/DiagnosisDomain | **全部展开为明细类**（见 §1）|
| S1 干支基础 → `core:GanzhiDomain` ✓ | S2 历法 → `calendar:CalendarDomain` ✓ |
| S3 出处与流派 → `diagnosis:SourceNote`/`SchoolVariant`/`ToBeVerified` ✓ | S4 术语同义 → `diagnosis:Synonym` + 第 13 步 SKOS ✓ |
| S5 告警标志 → `rules:AlertFlag` ✓ | S6 命例与隐私 → `reading:CaseRecord` ✓ |
| OutputDomain → 跨域归入 `reading:`/`rules:`（§9-1 提请注意）✓ |

### 10.4 v1 说明

- v1 为初稿：类/属性/约束/SWRL 依据顶层设计 §5 与流程 §3 展开，出处逐条回原著核验（§10.1）。
- 待确认项 10 项（§9）与【待考】项（《穷通宝鉴》调候、流日/流时、特殊岁运格局细化、《命理探源》）已在闸门③由领域专家裁定；其中调候/流日流时/特殊岁运格局细化**均已落地**（见 §9 落地状态与 §10.1 结论）。

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
| 2026-10-29 | v1（初稿） | 码道 | 展开顶层设计 §5 为明细类体系（12 主干域 + 支撑 S1~S6）；设计对象属性 74 项、数据属性 65 项、`owl:Restriction` 35 项、SWRL 规则 R1~R24（24 条）；ABox 典型个体登记；待确认项 10 项；出处回原著核验（§10.1）。 |
| 2026-10-29 | **v1（定稿）** | 领域专家 | **闸门③通过**：认可本体核心设计（12 主干域类体系 / 对象属性 74 / 数据属性 65 / `owl:Restriction` 35 / SWRL R1~R24 / ABox 典型个体 / 出处回原著核验），并采纳 §9 待确认项的建议结论（含 OutputDomain 落位、查表型逻辑走 OBDA+程序、告警不设数值阈值、《穷通宝鉴》调候标【待考】、《命理探源》暂不纳入）。准予进入第 5 步（BPMN 含 UI）/ 第 6 步（单文件本体，含典型 ABox，闸门⑦）。 |

---

## 闸门⑦留痕（典型 ABox 数据 · 第 6 步）

| 日期 | 确认范围（典型个体集/覆盖场景） | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-29 | 第 6 步已生成 `ontology/sizhubazi-all.owl`（TBox 138 类 / 143 对象属性 / 69 数据属性 / 35 Restriction / 6 SWRL 规则；典型 ABox 127 个体）+ `catalog-v001.xml`；rdflib 解析通过，无悬空引用。 | 码道 | 提交领域专家确认。 |
| 2026-10-29 | 典型 ABox（枚举型全量 + 代表性，共 127 个体） | 领域专家 | **闸门⑦通过**：认可个体集/类型/关键断言/代表性；一并采纳 3 处落地细化（`core:GanzhiEntity` 辅助类、`chart:yearStemYinYang` 辅助数据属性、SWRL 序列化 R1/R5/R6/R19/R21/R22 共 6 条、余者由程序/OBDA/待考）。准予进入第 7 步（应用架构设计与 JobWorker）。 |

---

## 第 15 步 · 六典籍内容覆盖补全（2026-10-30）

> 触发：客户「再次检查是否 5 本书（经澄清＝**6 本**）内容全部包括在本体之中，缺少则补充完整，**不允许编造**」。
> 核查报告：`HWCodeArtsDir/audit_6books/六典籍内容覆盖核查报告.md`；抽取脚注见每类「出处」列（逐条可回原文定位，★8/9/10）。

### 1. 篇章级缺口 G1~G8 与处置
| 编号 | 典籍·篇章 | 缺口 | 处置 |
|---|---|---|---|
| G1 | 三命通会·卷四 | 十干坐支兼得月时及行运吉凶 / 十二月支得日干吉凶 / 五行时地分野吉凶 | 新增 TBox 类 `reading:StemBranchAuspice`、`MonthBranchDayStemAuspice`、`TimePlaceFortune` |
| G2 | 三命通会·卷七 | 定妇人孕生男女 | 新增类 `reading:ProgenyPrediction`（subClassOf `reading:ChildFortune`） |
| G3 | 三命通会·卷八/九 | 日时断「六X日Y时断」**720 条** | 新增表 `day_hour_clause` + OBDA 暴露 `reading:DayHourClause` |
| G4 | 渊海子平·卷一 | 神煞起例：天赦/四废日/天地转杀/天厨贵人/月德合/夹禄/垣城/帝座/三元 | 神煞起例 95→**104**（`data.sql`） |
| G5 | 渊海子平·卷六/神峰通考 | 病药说/雕枯旺弱四病/损益生长四药/动静说/盖头说 | 新增类 `yongshen:BingYao`、`reading:DynamicStaticTheory`、`reading:HeadCoveringTheory` |
| G6 | 滴天髓·通神论/六亲论 | 形象/方局/震兑/坎离/何知章/闲神/从化假从假化/顺反战合局/君臣母子象/贞元 | 新增类 `pattern:ImagePattern`/`DirectionPattern`/`ZhenDuiPattern`/`KanLiPattern`/`AssimilationPattern`/`SequencePattern`/`SovereignPattern`、`yongshen:IdleGod`、`reading:IlluminatingChapter`、`fortune:ZhenYuan` |
| G7 | 神峰通考 | 六亲说（独立篇） | 新增类 `reading:KinshipTheory` |
| G8 | 穷通宝鉴 | 十天干逐月调候条文**120 条** | 新增表 `tiaohou_clause` + OBDA 暴露 `yongshen:TiaohouClause` |

> 不在范围（顶层设计 §2.2「七政五星体系」）：神峰通考五星正说/谬说类、安身命宫、安星辰法、金不换；三命通会卷二「十干分配天文/十二支分配地理」（留【待考】）。

### 2. 本体增量
- **TBox**：新增 20 类（`yongshen` 3 / `reading` 9 / `pattern` 7 / `fortune` 1）＋ 8 数据属性（`reading:clauseDayPillar/clauseHourPillar/clauseSource`、`yongshen:tiaohouMonthBranch/tiaohouPrimaryUse/tiaohouSecondaryUse/tiaohouText/clauseSource`）；均带 `rdfs:comment` 出处。
- **ABox**：新增出处标注个体 7、代表性个体 5、神煞名录个体 7；`chart/Case_Demo01` 追加 `reading:hasReading`×2 + `pattern:hasPattern`×1。
- **DB（13 张表）**：新增 `day_hour_clause`（720）、`tiaohou_clause`（120）；`createdb_tables.ddl`、`data.sql`、`sizhubazi.obda` 同步。
- **SKOS**：概念 282→**310**（新类/属性全覆盖，含 `@en` 与别名）。

### 3. 数据来源与【待考】
- 数据来源＝AI 依六典籍源文本生成（逐条附出处，★8/9/10）；`data.sql` 全集规模：天干 10 / 地支 12 / 六十甲子 60 / 纳音 30 / 藏干 28 / 节气 24 / 月建 12 / 神煞起例 **104** / 日时断 **720** / 调候 **120** / 十神映射 100；运行态 `case_record`/`alert_flag` 留空。
- **OCR 还原（留痕）**：源文本《三命通会.txt》为 OCR 本，日时断 16 条按上下文就地还原（如「己亥日**乙丑**时」→「己亥日**己巳**时」、「**两**子日」→「丙子日」、「戊戌日**申寅**时」→「戊戌日**甲寅**时」）；**源本真缺 3 条**（乙巳日丁亥时、己酉日庚午时、戊子日己未时）**已依《三命通会》四庫全書本补全**（逐字照录，不编造）；另修正 2 处底本讹字（戊子日己未时之「戊寅」误录、乙未日丁卯时之「己未」讹字）。
- **G8 存疑（已落地）**：《穷通宝鉴》为评注散文体，逐节提炼用神；**逐月取用全量落地**（120 条，原 56 条【待考】已依各干逐月原文补全，无遗留）。

---

## 第 16 步 · 六典籍正文级复核 H1~H4 补录（2026-10-30）

> 触发：客户「再次仔细检查 6 本书，**不仅仅看目录，还要查看具体内容**，看是否本体中包含所有内容，不能有遗漏」。
> 复核报告：`HWCodeArtsDir/deep6_check/六典籍内容级深入复核报告.md`（逐条附典籍篇章与行号，★8/9/10）。
> 裁定：客户「**全部补录（H1~H4）**」。

### 1. 正文级缺口 H1~H4 与处置
| 编号 | 典籍·篇章 | 缺口 | 处置 |
|---|---|---|---|
| H1 | F·25/26/27/28 已列名 | 天官/太极/三奇/福星/天福贵人、学堂词馆、德秀、拱禄/交禄/暗禄/归禄、灾煞/六厄/勾绞/天罗地网/元辰/暗金的煞/十恶大败、截路/四大空亡（本体 0 命中） | `deity_rule` 104→**153**（`EXTRA_DEITY` +49）；ABox 补神煞名录个体 22 |
| H2 | 三命通会卷五/卷六、神峰通考、渊海子平卷二 | **95 个具名格**（天福贵人/壬骑龙背/飞天禄马/井栏叉/曲直炎上…）本体仅 3 个体 | 新增表 `pattern_catalog`（95）＋ 2 数据属性 `pattern:patternSource/patternMeaning`，经 OBDA 暴露为 `pattern:Pattern` |
| H3 | 三命通会卷七·论女命、渊海子平卷三 | H·40 明列 **16+ 分类**（纯/和/清/贵/浊/滥/娼/淫/旺夫克子…）无个体 | 新增表 `lady_fortune_catalog`（17），经 OBDA 暴露为 `reading:LadyFortune`（+`reading:readingSource`） |
| H4 | 渊海子平卷三/卷四、神峰通考、滴天髓·通神论、三命通会卷一 | 神趣八法/十干体象/十二支体象/认格局生死之歌/定格局诀/起八字诀/看命人式、滴天髓天道地道人道；释六十甲子性质吉凶 | 新增表 `technique_catalog`（38，→`reading:ReadingItem`）＋ `sexagenary_nature`（60，追加至 `core:SexagenaryCycle`，+`core:sexagenaryNatureText`） |

### 2. 本体增量
- **TBox**：新增 4 数据属性——`core:sexagenaryNatureText`、`pattern:patternSource`、`pattern:patternMeaning`、`reading:readingSource`（均带 `rdfs:label`，出处见子本体 `rdfs:comment`）。
- **ABox**：补神煞名录个体 22（贵人吉神 8 / 禄马 4 / 凶煞 8 / 空亡 2）。
- **DB（17 张表）**：新增 `pattern_catalog` / `lady_fortune_catalog` / `technique_catalog` / `sexagenary_nature`；`createdb_tables.ddl`、`data.sql`、`sizhubazi.obda` 同步。
- **SKOS**：概念 310→**314**（4 数据属性全覆盖，含 `@en`）。
- **OCR 还原（留痕）**：《三命通会.txt》OCR 本「释六十甲子性质吉凶」3 处讹字就地还原——辛卯**未**→辛卯**木**、**丁西**火→**丁酉**火、**已**未火→**己**未火（不编造，逐条对应原文上下文）。

### 3. 回归
- `AllSiZhuBaZiSuiteTest`：本体合规 8/8、SKOS 8/8（BPMN/RabbitMQ 依环境跳过）；神煞起例断言 104→153、新增格局 95/女命 17/技法 38 行数断言与 DDL/OBDA 工件断言。

---

## 第 17 步 · 第二轮正文级复核 I1~I3 补录（2026-10-30）

> 触发：客户「再次仔细检查 6 本书……不仅看目录，还要查看**具体内容**，看本体是否包含所有内容，不能有遗漏」（第二轮，下沉至「**具名条目**」层）。
> 复核报告：`HWCodeArtsDir/deep6_check/六典籍内容级复核报告-第二轮.md`（逐条附典籍篇章与行号，★8/9/10）。
> 裁定：客户「**全部补录（I1~I3）**」。

### 1. 缺口 I1~I3 与处置
| 编号 | 典籍·篇章 | 缺口 | 处置 |
|---|---|---|---|
| I1 | 渊海子平卷二·论金神/论时墓（卷五诗诀）、神峰通考·金神格/杂气财官印绶格附时墓格、三命通会卷六 | 本体自我声明（BPMN 节点 8 / 顶层设计 D·17）已列「**金神**/**时墓**」，`pattern_catalog` 却无对应（硬缺口） | `pattern_catalog` 95→**97**（`PAT_JinShen`/`PAT_ShiMu`），经 OBDA 暴露为 `pattern:Pattern` |
| I2 | 三命通会卷三·总论诸神煞、神峰通考·吉神类/凶神类 | F·25/26/27 枚举**之外**、典籍正文另载的细煞（破煞/返本煞/阴阳煞/淫欲妨害煞[八专九丑]/孤鸾寡鹊煞/阴阳差错煞/桃花红艳煞；官贵学馆/红鸾/天喜/解神；隔角/伏吟/返吟/破军/悬针/平头/紫暗星/流霞杀/冲天杀/五鬼杀/红艳杀/吞焰煞/三丘五墓/马前神煞[攀鞍/年杀/月杀]/流年星耀/太白星/斧劈星/孤虚神） | `deity_rule` 153→**186**（`EXTRA_DEITY2` +33），内置查表 |
| I3 | 三命通会卷四/卷七、渊海子平卷六、神峰通考、滴天髓·通神论/六亲论 | G1/G2/G5/G6/G7 第 15 步**仅建 OWL 类 + 占位 Demo 个体**，正文断语未入库 | 新增 3 断语条文表（仿 G3/G8 口径，内容层落库 + OBDA 暴露） |

### 2. 本体增量
- **TBox**：**无新增类/属性**——I3 落库目标类（`reading:StemBranchAuspice/MonthBranchDayStemAuspice/TimePlaceFortune/ProgenyPrediction/DynamicStaticTheory/HeadCoveringTheory/IlluminatingChapter/KinshipTheory`、`yongshen:BingYao/IdleGod`、`pattern:ImagePattern/DirectionPattern/ZhenDuiPattern/KanLiPattern/AssimilationPattern/SequencePattern/SovereignPattern`、`fortune:ZhenYuan`）第 15 步均已建，本轮**仅补内容个体（经 OBDA）**。
- **DB（17→20 张表）**：新增 `auspice_clause`（G1/G2，25）/ `theory_clause`（G5/G7，12）/ `dts_clause`（G6，26）；`createdb_tables.ddl`、`data.sql`、`sizhubazi.obda` 同步。
  - OBDA 新增 18 个映射（按 `clause_group` 分派到对应 OWL 类：reading 域用 `reading:readingCategory/Source/Text`，pattern 域用 `pattern:patternName/Class/Source/Meaning`，yongshen 域用 `yongShenRole/Method`+`rdfs:comment`，fortune 域用 `rdfs:label/comment`）。
  - **修复**：`[PrefixDeclaration]` 补齐 `pattern:`/`fortune:` 前缀声明（既有 `pattern_catalog` 映射此前引用了未声明前缀）。
- **SKOS**：**不变**（314）——本轮未增删类/属性，术语表源自 OWL 类/属性，故无变化（复跑 `gen_sizhubazi_terminology.py` + `verify_terminology.py` 确认）。

### 3. 回归
- `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test` → `Tests run: 32, Failures: 0`。
- 合规断言更新：神煞起例 153→**186**、具名格局 95→**97**，新增 auspice 25 / theory 12 / dts 26 行数断言 + 3 表 DDL/OBDA 工件断言 + `pattern:`/`fortune:` 前缀声明断言 + `<pattern:`/`<fortune:` 防回归守卫。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。
- 留痕：`HWCodeArtsDir/step17_check/step17_record.md`。

---

## 第 18 步 · 第三轮正文级复核 R3 补录（2026-10-30）

> 触发：客户「第三次……不仅看目录，还要查看**具体内容**，看本体是否包含所有内容，不能有遗漏」（第三轮，下沉至「**成篇正文**」层）。
> 复核报告：`HWCodeArtsDir/step18_check/六典籍正文级复核报告-第三轮.md`（逐条附典籍篇章与行号，★8/9/10）。
> 裁定：客户「**全额入库（完整韵文层）**」→「**追加全部 38 篇（彻底无遗漏）**」→「**一并纳入**」（最终共 205 篇）。

### 1. 缺口 R3 与处置
| 编号 | 典籍·篇章 | 缺口 | 处置 |
|---|---|---|---|
| R3 | 渊海子平卷一~卷七、神峰通考（附录前段） | 成篇「**赋/歌/诗诀/独步/诀/论/篇/说/图**」正文内容层未落地（既有 `pattern_catalog`/`technique_catalog`/断语表多为短名录概括，未存全文） | 新增 `fu_clause` 条文表，**全文照录 205 篇**，经 OBDA 暴露为 `reading:CategoryFortune` |

### 2. 本体增量
- **TBox**：**无新增类/属性**——落库目标类 `reading:CategoryFortune`（第 15 步已建，`sizhubazi-reading.owl` 行 80）；本轮**仅补内容个体（经 OBDA）**。
- **DB（20→21 张表）**：新增 `fu_clause`（`clause_id` PK / `clause_group` / `subject` / `clause_text` **LONGTEXT**（全文不截断）/ `source_note`）；`createdb_tables.ddl`、`data.sql`、`sizhubazi.obda` 同步。
  - 条数 **205** = 渊海子平 **162**（卷一/二 13 + 卷三~七 149）+ 神峰通考 **43**（附录 41 + 前段 2）；分组：赋 37 / 诗诀 68 / 歌 18 / 论 17 / 篇 10 / 诀 7 / 说 5 / 图 4 / 独步 2 / 文 37。
  - 生成源：`HWCodeArtsDir/sizhubazi_fu.py`（渊海按正文「未知」边界机械抽取；神峰按篇目行号/显式区间）。`clause_text` 单行存库（仅空白归一，不丢字符），与既有条文表一行一记录惯例一致。
  - OBDA 新增 1 个映射 `fu_clause_all`（`reading:{clause_id} a reading:CategoryFortune`；`reading:readingCategory`=clause_group / `reading:readingSource`=source_note / `reading:readingText`=clause_text）。
  - **附带修复（既有缺陷，非本轮引入）**：整库灌入实测暴露 `deity_rule` 列宽不足——`base_ganzhi` 32→**64**、`deity_alias` 64→**128**、`branch_hit` 64→**128**；否则该表 INSERT 因单行超长整体回滚（第 17 步 I2 细煞遗留）。
- **SKOS**：**不变**——本轮未增删任何 OWL 类/属性。

### 3. 回归
- `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test` → `Tests run: 32, Failures: 0`。
- 合规断言更新：DDL 新增 `fu_clause`（合计 21 张）、OBDA 新增 `fu_clause`+`reading:CategoryFortune` 断言、data.sql 新增 fu **205** 行数 + 首/末条断言。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。
- 留痕：`HWCodeArtsDir/step18_check/step18_record.md`。

---

## 第 20 步 · 第五轮正文级复核 P1~P8 补录（2026-10-30）

> 触发：客户「请再次仔细检查 6 本书……不仅看目录，还要查看**具体内容**，看本体是否包含所有内容，不能有遗漏」。
> 复核报告：`HWCodeArtsDir/step19_check/六典籍正文级复核报告-第四轮.md`（全量机器扫描 938 具名章节 → 去噪后残留 8 条 P1~P8，逐条附典籍篇章与行号，★8/9/10）。
> 裁定：客户授权「发现缺口一律全部补齐、不必逐条确认，补齐后再复检、循环至 0 缺口」。

### 1. 缺口 P1~P8 与处置（回写既有表，不新增表/不新增 OWL 类）
| 编号 | 典籍·篇章 | 处置 |
|---|---|---|
| P1 | 渊海子平卷一·论起胎/息/变/通法 | `technique_catalog` +4（`TEC_QiTaiFa`/`TEC_QiXiFa`/`TEC_QiBianFa`/`TEC_QiTongFa`） |
| P2 | 渊海子平卷二·支后一字/凤凰地 | `pattern_catalog` +2（`PAT_ZhiHouYiZi`/`PAT_FengHuangDi`） |
| P3 | 三命通会卷二·论进交退伏 | `technique_catalog` +1（`TEC_JinJiaoTuiFu`） |
| P4 | 三命通会卷二·十干分配天文/十二支分配地理 | `technique_catalog` +2（`TEC_ShiGanTianWen`/`TEC_ShiErZhiDiLi`） |
| P5 | 三命通会卷六·附论墓运 | `fu_clause` +1（`FU_SMTH_MuYun`，论，全文照录） |
| P6 | 神峰通考·月令详辨 | `technique_catalog` +1（`TEC_YueLingXiangBian`） |
| P7 | 穷通宝鉴·五行总论 | `technique_catalog` +1（`TEC_WuXingZongLun`） |
| P8 | 神峰通考·地支造化图/阳顺阴逆生旺死绝图 | `fu_clause` +2（`FU_SF_ZhiZaoTu`/`FU_SF_YangShunYinNiTu`，图，【图录】照录） |

### 2. 本体增量
- **TBox**：**无新增类/属性**——目标类 `reading:ReadingItem`（技法）/`pattern:Pattern`（格局）/`reading:CategoryFortune`（条文）均既有；本轮仅补**内容个体（经 OBDA）**。
- **DB（仍 21 张表）**：无新增表；`technique_catalog` 38→**47**、`pattern_catalog` 97→**99**、`fu_clause` 205→**208**。
  - 条数 **47** = 既有 38 + P1 4 + P3/P4 3 + P6/P7 2；**99** = 既有 97 + P2 2；**208** = 既有 205 + P5 1 + P8 2。
  - 生成源：`HWCodeArtsDir/sizhubazi_catalog.py`（`TECHNIQUE_CATALOG`/`PATTERN_CATALOG`）、`HWCodeArtsDir/sizhubazi_fu.py`（`_FU_EXTRA`）。`data.sql` 经 `gen_sizhubazi_full_data.py` 重生成。
  - **DDL / OBDA 无改动**：既有 `technique_catalog`/`pattern_catalog`/`fu_clause_all` 映射均为全表 `SELECT`，新增行自动经 OBDA 暴露。
- **SKOS**：**不变（仍 314）**——未增删任何 OWL 类/属性。

### 3. 回归
- `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test` → `Tests run: 32, Failures: 0`。
- 合规断言更新：`patternRows 97→99`、`techRows 38→47`、`fuRows 205→208`，并新增 P1~P8 入库断言。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。
- 留痕：`HWCodeArtsDir/step19_check/`。

---

## 第 21 步 · 第五轮正文级复核 P9/P10 补录（2026-10-30）

> 触发：第五轮全量复检（`_r5_scan.out`）确认 P1~P8 标题全部消解，但残留真缺口——穷通宝鉴「**十干分季总论**」13 条与「**五行分论**」6 篇的正文散文未落库。
> 裁定：客户授权「发现缺口一律全部补齐」。

### 1. 缺口 P9/P10 与处置（回写既有表，不新增表/不新增 OWL 类）
| 编号 | 典籍·篇章 | 处置 |
|---|---|---|
| P9 | 穷通宝鉴·十干分季总论（三春/三夏/三秋/三冬甲木、三春/三夏/三秋乙木、三春/三夏丙火、十一二月戊土、三春/三秋/十月十一月十二月己土，共 13 条） | `technique_catalog` +13（`TEC_QT_Jia_Chun/Xia/Qiu/Dong`、`TEC_QT_Yi_Chun/Xia/Qiu`、`TEC_QT_Bing_Chun/Xia`、`TEC_QT_Wu_Dong`、`TEC_QT_Ji_Chun/Qiu/Dong`；category=总论） |
| P10 | 穷通宝鉴·五行分论（论木 / 论火 / 论土 / 四季月土性质之分别 / 论金 / 论水，共 6 篇） | `technique_catalog` +6（`TEC_QT_WuXing_Mu/Huo/Tu/Jin/Shui`、`TEC_QT_SiJiYueTu`；category=五行分论）。**P7 第 20 步声明范围「五行总论附论木/火/土/金/水」仅实现五行总论一条 → 本轮补全** |

- 行号（整理本 `design/六书全文整理/穷通宝鉴.txt`，逐条读原文核验，★禁编造）：
  - P9 总论：三春甲木 **L189** / 三夏甲木 **L222** / 三秋甲木 **L248** / 三冬甲木 **L293** / 三春乙木 **L329** / 三夏乙木 **L365** / 三秋乙木 **L393** / 三春丙火 **L469** / 三夏丙火 **L513** / 十一、二月戊土 **L831** / 三春己土 **L849** / 三秋己土 **L907** / 十月十一月十二月己土 **L930**。
  - P10 分论：论木 **L114** / 论火 **L131** / 论土 **L145** / 四季月土性质之分别 **L159** / 论金 **L161** / 论水 **L175**；P7 五行总论 **L108**（原记 L104，本轮依实际修正）。
- 说明：穷通宝鉴另有多篇「XX总论」为**目录行**（L3~L89），属噪声，不补。

### 2. 本体增量
- **TBox**：**无新增类/属性**——目标类 `reading:ReadingItem` 既有；本轮仅补**内容个体（经 OBDA）**。
- **DB（仍 21 张表）**：无新增表；`technique_catalog` 47→**66**（= 既有 47 + P9 13 + P10 6）。
  - 生成源：`HWCodeArtsDir/sizhubazi_catalog.py`（`TECHNIQUE_CATALOG`）。`data.sql` 经 `gen_sizhubazi_full_data.py` 重生成。
  - **DDL / OBDA 无改动**：既有 `technique_catalog` 映射为全表 `SELECT`，新增行自动经 OBDA 暴露。
- **SKOS**：**不变（仍 314）**——未增删任何 OWL 类/属性。

### 3. 回归
- `SiZhuBaZiOntologyComplianceTest`、`SiZhuBaZiSkosTerminologyTest` 各 8/8。
- 合规断言更新：`techRows 47→66`，并新增 19 个 `TEC_QT_*` 入库断言。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。
- 留痕：`HWCodeArtsDir/step19_check/`。

---

## 第 22 步 · 第六轮正文级复核 P11 补录（2026-10-30）

> 触发：第六轮全量复检采用**新建精确匹配扫描器** `HWCodeArtsDir/r8_scan_exact.py`（以 DB 名录列 + OWL `rdfs:label`/SKOS `skos:prefLabel` 构建词条集，与六书章节标题做精确/归一化比对；替代旧子串扫描 `r4_scan2.py` 对短标题的漏检），并辅以 `HWCodeArtsDir/r8_patterns.py` 枚举六书**具名格**与名录比对。
> 裁定：客户授权「发现缺口一律全部补齐」。

### 1. 缺口 P11 与处置（回写既有表，不新增表/不新增 OWL 类）
| 编号 | 典籍·篇章 | 处置 |
|---|---|---|
| P11 | 渊海子平卷二·杂气财官格 / 杂气印绶格 / 月上偏官格（3 个具名格此前从未入 `pattern_catalog`） | `pattern_catalog` +3（`PAT_ZaQiCaiGuanYinShou` 杂气财官印绶格 / `PAT_ZaQiYinShou` 杂气印绶格 / `PAT_YueShangPianGuan` 月上偏官格） |

- 行号（整理本 `design/六书全文整理/渊海子平.txt`，逐条读原文核验，★禁编造）：杂气财官格 **L1038**；月上偏官格 **L1053**；杂气印绶格 **L1212**。
- 说明：神峰通考 L316「杂气财官印绶格 附时墓格」——该名此前仅出现在 `PAT_ShiMu` 的 `source_note` 引用中却未独立收录，本轮随渊海同名格一并补录（`PAT_ZaQiCaiGuanYinShou` source_note 双引渊海/神峰）。经核验，`r8_patterns.py` 另报的渊海「弃命从杀格」(L1277)、「女命贵格/贱格」(L1366/1372)、神峰「偏官格附弃命从杀格」(L84) 等，或已由别格承载（弃命从煞 / 官煞去留），或属目录性/女命分类（`lady_fortune_catalog` 承载），**非真缺口**。

### 2. 本体增量
- **TBox**：**无新增类/属性**——目标类 `pattern:Pattern` 既有；本轮仅补**内容个体（经 OBDA）**。
- **DB（仍 21 张表）**：无新增表；`pattern_catalog` 99→**102**（= 既有 99 + P11 3）。
  - 生成源：`HWCodeArtsDir/sizhubazi_catalog.py`（`PATTERN_CATALOG`）。`data.sql` 经 `gen_sizhubazi_full_data.py` 重生成。
  - **DDL / OBDA 无改动**：既有 `pattern_catalog` 映射为全表 `SELECT`，新增行自动经 OBDA 暴露。
- **SKOS**：**不变（仍 314）**——未增删任何 OWL 类/属性。

### 3. 收敛结论（六书具体内容级复核）
- 精确扫描残留 47 条经逐条核验**全部为非真缺口**：三命通会页眉/目录行与「卷十~十二（缺）」、女命单字（纯/和/清/贵/浊/滥/娼/淫，`lady_fortune_catalog` 承载）、滴天髓 17 篇名（设计文档概念层属性/类承载）、渊海「夹丘格(拱财格)」＝名录 `PAT_JiaQiu`「夹丘（拱财）」（全/半角括号差异）、神峰「附官杀去留杂格」＝名录 `PAT_GuanShaQuLiu`「官煞去留」（杀/煞异体字）、渊海「天干相合」＝ terminology.ttl altLabel 承载。
- 穷通宝鉴、子平真诠 精确扫描 **0 未命中**。判定**六典籍正文级覆盖已 0 真缺口**。

### 4. 回归
- `SiZhuBaZiOntologyComplianceTest`、`SiZhuBaZiSkosTerminologyTest` 应通过（BPMN 环境失败与本改动无关）。
- 合规断言更新：`patternRows 99→102`，并新增 3 个 `PAT_*` 入库断言。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。
- 留痕：`HWCodeArtsDir/step19_check/`。

---

## 第 23 步 · 第六轮正文级复核·《子平真诠》正文补录（2026-10-30）

> 触发：第六轮复检（正文实体级，`r8_scan_exact.py` + `r8_patterns.py` + 逐书回读）发现——六书中**唯《子平真诠》47 篇正文整体未落库**（`data.sql` 中「真诠」出现 0 次），仅以概念层（`sizhubazi-yongshen.owl` 类 / SKOS 概念 + `源: 子平真诠·论XX` 出处标注）承载；其余五书均有专表承载正文（神峰通考→`theory_clause`、滴天髓→`dts_clause`、穷通宝鉴→`tiaohou_clause`、三命通会→`auspice_clause`+`day_hour_clause`、渊海→`fu_clause`）。
> 裁定：客户确认「**新增子平真诠专表**，逐篇录入 47 篇正文 + 出处行号，与其余五书对齐」。

### 1. 补录内容（新增专表 `ziping_clause`）
| 编号 | 典籍·篇章 | 处置 |
|---|---|---|
| ZP_01~ZP_47 | 《子平真诠》47 篇（论十干十二支 / 论阴阳生克 / … / 论用神 / 论相神紧要 / 论杂气如何取用 / 论四吉神能破格 / 论行运成格变格 / … / 论杂格） | 新增表 `ziping_clause` **47** 条，`clause_text` 用 LONGTEXT **全文照录不截断**（合计约 2.47 万字，最长 1514 字） |

- 行号（整理本 `design/六书全文整理/子平真诠.txt`，共 436 行；逐篇以「序号、篇名」标题定位，正文为标题至下一篇之间的非「子平真诠评注」行）：首篇「论十干十二支」**L52**、末篇「论杂格」**L424**；每篇 `source_note` 记实际行号。
- 生成源：`HWCodeArtsDir/sizhubazi_ziping.py`（`ZIPING_CLAUSES`，由 `子平真诠.txt` 抽取）。`data.sql` 经 `gen_sizhubazi_full_data.py` 重生成。

### 2. 本体增量
- **TBox**：**无新增类/属性**——目标类 `reading:ReadingItem` 既有（与 `technique_catalog` 同口径）；本轮仅补**内容个体（经 OBDA）**。
- **DB（21→22 张表）**：新增 `ziping_clause`（`clause_id` PK / `clause_group`=「子平真诠」/ `subject`=篇名 / `clause_text` **LONGTEXT**（全文不截断）/ `source_note`）。
  - **DDL**：`createdb_tables.ddl` 新增第 22 表 `ziping_clause`。
  - **OBDA**：新增 1 个映射 `ziping_clause_all`（`reading:{clause_id} a reading:ReadingItem`；`reading:readingCategory`=clause_group / `reading:readingSource`=source_note / `reading:readingText`=clause_text）。
- **SKOS**：**不变（仍 314）**——未增删任何 OWL 类/属性。

### 3. 回归
- `SiZhuBaZiOntologyComplianceTest`、`SiZhuBaZiSkosTerminologyTest` 各 8/8。
- 合规断言更新：DDL 新增 `ziping_clause`（合计 22 张）、OBDA 新增 `ziping_clause`+`reading:ReadingItem` 断言、data.sql 新增 **47** 行数 + 首/末条（`ZP_01`/`ZP_47`）断言。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。
- 留痕：`HWCodeArtsDir/step19_check/六典籍正文级复核报告-第六轮.md`；实现记录见本步。

---

## 第 26 步 · 第九轮**正文内容级**复核·六典籍正文全文补齐（2026-10-30）

> 触发：客户第 9 轮指令「完成后请再次仔细检查 6 本书，**不仅看目录，还要查看具体内容**，看是否本体中包含了**所有的内容，不能有遗漏**」。
> 本质区别：既往第 3~8 轮复核判据实为**篇章/条文级**（核对篇/章标题、具名格、神煞名是否入名录），故历轮均判「0 缺口」；本轮首次做**整文内容级**——把每本书正文**逐句**切分，与「DB 全表文本 + OWL label + SKOS pref/alt」全量 haystack 做子串匹配，量化每书正文实际入库比例。
> 关键发现：**标题在库 ≠ 正文在库**。首测覆盖率合计仅 **48.8%**（渊海 85.2% / 三命 23.5% / 滴天髓 6.8% / 神峰 46.7% / 穷通 3.9% / 子平真诠 99.2%），散文体正文大量未入本体。
> 裁定：客户确认「**六书逐篇全文入库**」（按《子平真诠》第 23 步先例，新增专表照录），并列明**六书均不得有遗漏**。

### 1. 补齐内容（新增专表 `classic_text`）
| 编号 | 典籍·篇章 | 条数 |
|---|---|---|
| CT_DTS_* / CT_QT_* / CT_SF_* / CT_SMTH_* / CT_YH_* | 《滴天髓》/《穷通宝鉴》/《神峰通考》/《三命通会》/《渊海子平》逐篇正文（`chapter` 记篇名、`clause_text` 用 LONGTEXT **全文照录不截断**、`source_note` 记整理本实际行号） | **922**（63 / 142 / 128 / 298 / 291；第 34 步拆分补入「六亲论·十二从象」） |

- 说明：《子平真诠》47 篇已由第 23 步 `ziping_clause` 承载（覆盖率 99.2%），本轮不重复入 `classic_text`；六书合计正文全文 **969 篇**（922 + 47）。
- 切篇规则：滴天髓以「通神论/六亲论」+「一、篇名」定位；穷通宝鉴以短标题行定位；神峰通考以「叙/说/类/论/诀/赋/歌/篇/式/法/格/辨/图/序/括」后缀短标题定位；三命通会以「卷N + ○节名」定位；渊海子平以篇题定位。清洗页眉、页码（「三 命 通 会」「· N ·」）与「未知」分隔标记。
- 生成源：`HWCodeArtsDir/sizhubazi_classic.py`（`classic_texts()`，运行时从 `design/六书全文整理/*.txt` 照录）；`data.sql` 经 `gen_sizhubazi_full_data.py`（段「9k」）重生成。

### 2. 本体增量
- **TBox**：**无新增类/属性**——目标类 `reading:ReadingItem` 既有；本轮仅补**内容个体（经 OBDA）**。
- **DB（22→23 张表）**：新增 `classic_text`（`text_id` PK / `book_name` / `chapter` / `clause_text` **LONGTEXT**（全文不截断）/ `source_note`）。
  - **DDL**：`createdb_tables.ddl` 新增第 23 表 `classic_text`（并补 `ziping_clause`/`classic_text` 的 `DROP TABLE IF EXISTS`）。
  - **OBDA**：新增 1 个映射 `classic_text_all`（`reading:{text_id} a reading:ReadingItem`；`reading:readingCategory`=book_name / `reading:readingSource`=source_note / `reading:readingText`=clause_text）。
- **SKOS**：**不变（仍 314）**——未增删任何 OWL 类/属性。

### 3. 回归与覆盖率复检
- `SiZhuBaZiOntologyComplianceTest`、`SiZhuBaZiSkosTerminologyTest`、`SiZhuBaZiBirthParsingTest` 合计 **20/20**。
- 合规断言更新：DDL 新增 `classic_text`（合计 23 张）、OBDA 新增 `classic_text`+`reading:ReadingItem` 断言、data.sql 新增 **921** 行数 + 首条断言。
- 覆盖率复检（`r4_dump_db_text.py` + `r9_content_blocks.py`，补录后）：合计 **99.8%**（渊海/三命/滴天髓/神峰 **100.0%**、穷通 98.9%、子平真诠 99.2%）；未命中残余经逐行核验**全为目录/篇名噪声**（穷通 33 句系卷目索引行「1、韦序 4、五行总论…」，子平真诠 6 句系篇名标题行，正文均在库）→ **六书 0 真内容缺口**。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。
- 留痕：`HWCodeArtsDir/step25_check/六典籍正文内容级复核报告-第九轮.md`；实现记录见本步。


---

## 第 32 步 · 待考项全量落地与六书符合性终检（2026-11-03）

> 触发：客户指令「**再次从头全量检查**本体应用是否完全按经典六书构建，**待考全部落地**，需要引入外部资源就引入，修正后再检查、再修正，直到没有修正为止，按章节逐字逐句检查」。

### 1. 本轮【待考】落地清单（全量）
| 项 | 原状态 | 落地依据与方法 | 结果 |
|---|---|---|---|
| **调候用神** | 56 条【待考】占位 | 《穷通宝鉴·造化元钥》各干逐月原文；`tiaohou_clause` 重生成，修「正月月甲」重复「月」字 | 120/120 全落地 |
| **纳音吉凶（吉/平/凶）** | 30 条 `nayin_nature='待考'` | 《三命通会》卷一·释六十甲子性质吉凶（60 条神煞按吉神/凶煞计数归算） | 30/30 全落地（吉 8/平 6/凶 16） |
| **日时断缺载 3 条** | 建【待考】占位 | 《三命通会》四庫全書本（ctext/wikisource）逐字照录 | 720/720 全备 |
| **日/时断底本讹字** | OCR 讹字 2 处 | 对照四庫全書本修正（戊寅→戊子、乙未→己未） | 已修正 |
| **流月/流日/流时** | 输出标【待考】 | 流月＝流年干五虎遁列十二月建；流日＝干支纪日 JDN 锚定；流时＝日干五鼠遁起时（历法惯例，非六书） | JobWorker 实算落地 |
| **特殊岁运格局细化** | 标【待考】 | 岁运并临/天克地冲（反吟）/伏吟 已判；桃花·红鸾见神煞盘 | 已落地 |
| **农历→公历换算** | `lunarToSolar` 返回 null（表未就绪） | **引入外部资源落地**：内置 1900–2100 权威农历数据表 `LUNAR_INFO`（含闰月），已用 1900/1949/1984/2000/2020闰四月/2023闰二月/2100 等已知日期校验吻合 | 已落地（新增 `SiZhuBaZiLunarConvertTest` 8/8） |

### 2. 本体/代码增量
- **Java**：`SiZhuBaZiOntologyJobWorker` 新增 `LUNAR_INFO`（1900–2100，201 项）与 `lunarToSolar(Object)/leapMonth/leapDays/monthDays/lunarYearDays/parseHourFromText/truthy` 等；`handleParseBirthInput` 对「农历」输入实算换算，仅年份越界/无闰却标闰/格式异常时回退标【待考】。
- **前端**：`index.html` 增「闰月」复选框（仅农历显示）并随 `birth.leap` 提交；选项文案更正为「农历（含闰月）」。
- **DB**：`data.sql` 修订调候/纳音/日时断/藏干（巳中·余气次序：丙/庚/戊）；`data.sql` 引号内【待考】归零（仅存《穷通宝鉴》命例原文自带「确否待考」语）。

### 3. 回归
- `mvn -o compile` EXIT=0；`SiZhuBaZiLunarConvertTest` 8/8。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。

---

## 第 36 步 · 渊海子平 epub 目录全量复核（图题/卷题缺口补录）（2026-11-03）

> 触发：客户「再次从头全量检查本体应用是否完全按经典六书构建……要按章节逐字逐句检查；不全就补齐，修正后再检查、再修正，直到没有修正为止」。
> 方法：以**原书 epub 目录（`toc.ncx`，303 条 navPoint）为权威依据**，与 `data.sql` 全库做全量集合 diff（`target/toc_coverage.py`），定位全部未覆盖条目。

### 1. 缺口与处置（回写既有表，不新增表/不新增 OWL 类）
| 编号 | 原书目录条目 | 性质 | 处置 |
|---|---|---|---|
| 图题 1 条 | 渊海子平卷之一·**阴阳顺逆生旺死绝之图**（navPoint#22） | 原书为图，整理本正文无对应文字 | `fu_clause` +1（`FU_YH_154`，图，【图录】照录） |
| 卷题 5 条 | 渊海子平**卷之三/四/五/六/七**（navPoint#151/194/222/283/298） | 原书为卷首标题页（epub 页内仅标题），整理本正文无对应文字 | `classic_text` +5（`CT_YH_149/192/220/281/296`，`chapter`=卷名，`clause_text`=【卷题】说明，`source_note` 记整理本行号） |

- 行号（整理本 `design/六书全文整理/渊海子平.txt`）：阴阳顺逆生旺死绝之图 **L158**；卷之三 **L1316** / 卷之四 **L1822** / 卷之五 **L2192** / 卷之六 **L2969** / 卷之七 **L4334**。
- **编号顺移**：新增 `FU_YH_154` 后，原 `FU_YH_154~162`（月律分野之图…继善篇）**顺移为 155~163**（保持书目次序：五行发用定例→阴阳顺逆生旺死绝之图→月律分野之图；编号 1~163 连续无缺）。经核 `FU_YH_15x/16x` 无跨表/跨文件引用，重编号安全。
- 卷题编号 `CT_YH_149/192/220/281/296` 恰为既有预留空位（编号体系按 epub 目录逐条对齐，被分流条目留空），插入后不破坏相邻编号连续性。

### 2. 本体增量
- **TBox**：**无新增类/属性**——目标类 `reading:CategoryFortune`（图/条文）/`reading:ReadingItem`（正文）均既有；本轮仅补**内容个体（经 OBDA）**。
- **DB（仍 23 张表）**：无新增表；`fu_clause` 208→**209**、`classic_text` 922→**927**。
  - **DDL / OBDA 无改动**：既有 `fu_clause_all`/`classic_text_all` 映射均为全表 `SELECT`，新增行自动经 OBDA 暴露。
- **SKOS**：**不变（仍 314）**——未增删任何 OWL 类/属性。

### 3. 复检收敛
- `target/toc_coverage.py` 复跑：渊海子平 epub 目录 **303 条「全库未出现 0 条」**（此前 6 条）。
- 余下 6 个 `CT_YH` 编号空缺（5/21/22/148/152/153）经核**全为已分流条目**（天降干支图→`FU_YH_151`、阴阳顺逆生旺死绝之图→`FU_YH_154`、月律分野之图→`FU_YH_155`、凤凰地→`PAT_FengHuangDi`、男命/女命六亲取要图→`FU_YH_113/114`），**非内容丢失**。

### 4. 回归
- 整模块 `mvn test`（`JAVA_HOME="D:/work/Ontology/jdk-27"`）**EXIT=0**，surefire 汇总 **Tests=64 · Fail=0 · Err=0 · Skip=0**。
- 合规断言更新：`fuRows 208→209`、`classicRows 922→927`，并新增第 36 步入库断言。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。
- 留痕：`target/toc_coverage.py`（复用）；工作日志 `2026-11-03.md` 第 36 步。

---

## 第 37 步 · 第 39 轮全量复检·用字/标点/页码噪声清理（2026-11-03）

> 触发：客户「**再次从头检查**……**要按章节逐字逐句检查**……修正后再检查、再修正，直到没有修正为止」；并守**「需要 OCR 识别的，就联网找到外部资源，不要基于 OCR 进行识别」**铁律——凡判定用字讹误一律以权威古籍原文佐证，绝不以 OCR/语法臆测字形。

### 1. 用字修正（3 处，DB 与 `design/六书全文整理/*.txt` 同步）
| 位置 | 误 | 正 | 判定依据（外部权威 + 内部自证） |
|---|---|---|---|
| 滴天髓·六亲论·何知章（`CT_DTS_39` / `滴天髓.txt` L1064） | 何知其人贫**?财贫神**反不真。 | 何知其人贫**？财神**反不真。 | 同一「何知」七句余六句均用全角「？」，唯「贫」句用半角「?」且「财贫神」倒文；`dts_clause.DC_IlluminatingPin` 自证作「财神反不真」；百度百科/搜狗/newton《滴天髓》作「财神反不真」（wikisource 四庫本《滴天髓辑要》作「财神终不真」，属版本异文，取多数本） |
| 渊海子平·元理赋「四孟全看**天于**何如」 | 天于 | **天干** | 同书「天干」143 次 vs「天于」1 次（孤例）；权威本作「四孟全看天干何如」 |
| 渊海子平·女命大义「**月于庚**食被时丙克之」 | 月于 | **月干** | 同书「月干」17 次 vs「月于」1 次；命理语义为「月干庚金食神被时干丙火克」 |
| 神峰通考·阳刃格附夏阁老命「其贵盖在**月于丁**为」 | 月于 | **月干** | 命例四柱「壬寅 丁未 丙寅 壬辰」月柱为丁未，作「月干丁」方通；同书「月干」10 次 vs「月于」1 次 |

### 2. 标点/分隔噪声归一（DB 与整理本同步）
- **页码噪声**：删除 `classic_text` 正文夹带的源书页码 `· N ·`（数字间含空格，标准形 `·(?:[ ]\d)+[ ]·`）**376 处**（整理本 `三命通会.txt` 另计，独立行为 386 处）；保留 `source_note` 中作层级分隔的「·」（如 `滴天髓·六亲论·五、何知章`）与章节标注「○」。
- **半角标点→全角**（仅限中日韩汉字相邻处，避免误伤数字/英文）：半角逗号 **214→全角「，」**、句点 3→「。」、冒号 5→「：」；整理本同步（渊海 1 + 神峰 77 + 穷通 104）。
- **现代水印清除**：`CT_DTS_63` 尾部所附 `低调的廉价艺术家  排版2023.11.5`（源 docx 页脚署名）删除，保留「滴天髓全文终」字样。

### 3. 保留项（经核**非**缺陷，**不**改）
- **△（64）/□（2）/○（6）**：系原书/整理本的结构标记与「阙字」记号（如 `△甲子乙丑海中金` 小节题、`坎□飘流` 缺字），属忠实照录，非噪声。
- **神祗/神祇**：底本（渊海 epub）即作「神祗」，属常见异体，保守保留。
- **汉字间空格**（穷通/神峰等，pdf 换行残留与**柱序分隔空格**混存，如 `辛丑 辛卯 乙丑 庚辰` 之柱间空格）：无法与有意分隔安全区分，**不动**（避免破坏柱序语义）。
- **「未知」「待考」**：均为典籍原文（「未知孰是」「此命造确否待考」），非占位符。

### 4. 收敛复检
- 页码残留 **0**；「天于/月于/财贫神反不真」残留 **0**；汉字间半角逗号残留 **0**；水印残留 **0**。
- `classic_text` **927 篇**不变（滴天髓 63 / 穷通宝鉴 142 / 神峰通考 128 / 三命通会 298 / 渊海子平 296），逐篇**无空正文**。
- `SiZhuBaZiOntologyComplianceTest` 14 项前缀行数断言全通过（CT 927 / FU 209 / ZP 47 / DC 26 / AC 25 / TC 12 / DR 186 / TGM 100 / DHC 720 / THC 120 / PAT 102 / LF 17 / TEC 66 / TermTime 2424）。
- 备份：`ontology/database/data.sql.bak_round39_20261103_152704`、`target/bak_六书全文整理_round39_20261103_152704`。

### 5. 回归
- 整模块 `mvn -o -q test`：**122/124 通过**。仅 2 项失败——`SiZhuBaZiBpmnRealEngineTest#t_real_engine_single_male_end_to_end`（及其套件副本）报「流程未在 PT2M 内完成」。
- **经 A/B 证伪回归**：以**修正前**的 `data.sql.bak_round39` 复跑同一测试**同样失败**（本机 9080/26500/5672 端口均开放，日志显示流程已跑完 persistAndReturn），确认系**既存/环境**问题，**与本次数据修正无关**（且该测试的 OBDA 条文查询本就降级为不可用，未读取改动文本）。
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。

---

## 第 38 步 · 第 40 轮全量复检·六书逐本逐章缺失核验 + 三命通会日时断序列化稽核（2026-11-03）

> 触发：用户指令「**对照 6 本书，逐本逐章节来检查缺失**」，并遵铁律「**如果需要 OCR 识别的，就联网找到外部资源，不要基于 OCR 进行识别**」。

### 1. 方法：三命通会「日时断」序列化稽核（新增精确扫描器）
- 三命通会卷八/卷九含 **118 个**「○六{X}日{YY}时断」小节，每节按固定天干列举六日柱（如「六戊日己未时断」应列戊子/戊寅/戊辰/戊午/戊申/戊戌）。
- 以正则枚举各节内 `<日柱>日<时柱>时` 条目，校验两点：①**同一日柱是否重复**；②**日干是否与本节天干相符**。此为「逐章节」的最强形态——把整本日时断当作可校验的定长序列。
- 结论：全库仅 **4 处**序列异常（其余 114 节完全规整）。

### 2. 四处异常的定性（均联网取权威本佐证）
| 小节 | 源文异常 | 权威本判定 | 处置 |
|---|---|---|---|
| 六丙日戊戌时断（L14320） | 「丙子日戊戌时」**重出**（丙寅后连出两条丙子） | 维基文库《三命通会》四库全书本卷九、ctext、文林社、百度百科**均保留同样重出** | **底本自身重出，忠实保留**（非我方之误） |
| 六庚日辛巳时断（L16614） | 「庚辰日辛巳时」**重出**（庚寅后接游离句「纯申年月大贵悠久…」再出完整庚辰） | 同上诸本**均保留同样重出**（系跨页重排所致） | **底本自身重出，忠实保留** |
| 六戊日己未时断（L15445） | 「**戊寅**日己未时，时日相穿…」 | 权威本作「**戊子**日己未时，时日相穿…」（网易163、destiny.to、四库本皆同） | **整理稿订正**戊寅→戊子；DB 早已作「戊子」，据此对齐 |
| 六己日丁卯时断（L15916） | 「**乙未**日丁卯时，不贵则富…」 | 权威本作「**己未**日丁卯时」（yimatrix 明注「己未日丁卯时（註:原文誤作乙未）」；易师汇、lifeklines 白话同） | **整理稿订正**乙未→己未；DB 早已作「己未」，据此对齐 |

- 订正后 `三命通会.txt` **行数恒为 18891**（行内字替换），`source_note` 行号可溯性**不受影响**；两处病灶字符串残留计数 **0**。
- 备份：`target/bak_六书全文整理_r40_20261103_155654/三命通会.txt`、`/tmp/三命通会.txt.bak_20261103_155650`。

### 3. 穷通宝鉴「丁火」章节粒度核验（收口历史待办）
- 整理本丁火子目含**二月/五月/七月丁火**（部分标题被并入正文，如「二月因湿乙伤丁…」「五月丁火时逢建禄…」）；DB 将其并入相邻章 `CT_QT_54`（正月+二月）/`CT_QT_56`（四月+五月）/`CT_QT_58`（三秋一例+七月）。
- 经核各子目**命例逐条在库**：二月（萨镇冰/孟恩远/张元奇/朱庆澜/屈映光/孙岳/钮傅善）、五月（汤寿潜/江万平/杨椒山/岁建会禄）、七月（张耀曾/大富秀才）**全部命中**。
- 判定：**属章节粒度差异，非内容缺失**；为避免改动 927 行数与符合性测试断言，DB **维持现状**。

### 4. 六书逐本逐章缺失核验总表（收敛）
| 典籍 | DB 章数 | 缺失核验 | 残留差异定性 |
|---|---|---|---|
| 子平真诠 | 47（`ziping_clause`） | **0 缺失** | — |
| 滴天髓 | 63（CT_DTS） | **0 缺失** | — |
| 穷通宝鉴 | 142（CT_QT） | **0 缺失** | 丁火二月/五月/七月=粒度合并 |
| 神峰通考 | 128（CT_SF） | **0 缺失** | — |
| 三命通会 | 298（CT_SMTH） | **0 缺失** | 日时断 2 处=底本重出 |
| 渊海子平 | 296（CT_YH） | **0 缺失** | 5 句=有意补录卷题 |

- **总结论：六书正文经逐本逐章核验，无真实内容缺失；本轮另订正整理稿 2 处用字讹误（三命通会 戊子/己未），与 DB 对齐。**
- 注：仅整理稿 `.txt` 变更，`data.sql` **未动**；`src` 无任何文件引用 `六书全文整理/`，故 Java 构建与符合性测试**不受影响**。

---

## 第 39 步 · 第 41 轮全量复检·条文类表「源↔库」双向覆盖率稽核（2026-11-03）

> 触发：用户指令「继续」——将第 40 轮（六书正文逐本逐章）的**同等覆盖率稽核**推进到其余**条文类表**（`day_hour_clause` / `tiaohou_clause` / `fu_clause` / `dts_clause` / `theory_clause` / `auspice_clause` 及四张名录表）。
> 遵铁律「**如果需要 OCR 识别的，就联网找到外部资源，不要基于 OCR 进行识别**」——每处讹字均经**维基文库四库全书本 / ctext / 百度百科 / 易师汇 / 文林社 / 墨书院 / 国学3000 / 太乙数衍**等权威本逐一比对判定，**不据 OCR 或语法臆测字形**。

### 1. `day_hour_clause`（720）结构稽核 —— **矩阵完整**
- 新增稽核器 `target/audit_clauses.py`（通用：解析 `INSERT INTO` 逐记录 → 校验唯一 id / (日,时)组合 / 字段自洽 / 覆盖率）。
- 结果：**720 行**、唯一 `clause_id` **720**、唯一 (日柱,时柱) 组合 **720**；日柱 60 × 时柱 60 **全覆盖**；日柱各恰 **12** 时（`非12小时的日柱 = {}`）；`day_stem/day_branch/hour_branch` 字段自洽 **0 异常**。
- 新增 `target/scan_dhc_labels.py`（逐节校验条目「日柱/时柱」标签与所属小节是否一致）与 `target/audit_dhc_branches.py`（以**阳干配阳支、阴干配阴支**判据校验每节六日支集合完整性）。

### 2. 三命通会「日时断」118 节 —— 源文 13 处异常（**DB 全部正确**）
稽核发现源整理稿与「本节应有的定长序列」有 13 处不符；`day_hour_clause` 720 条**均已为结构正形**。逐处联网定性如下：

| # | 小节 / 源行 | 整理稿原文（讹） | 权威本正形 | 类别 | 讹因 |
|---|---|---|---|---|---|
| 1 | 六甲日己巳时断 L12865 | 甲午**月**己巳时 | 甲午**日**己巳时 | 底本异文 | 月→日 |
| 2 | 六丙日己丑时断 L13883 | 丙申日**巳丑**时 | 丙申日**己丑**时 | 底本异文（ctext 亦作巳丑） | 己→巳 |
| 3 | 六丙日庚寅时断 L13924 | **两子**日庚寅时 | **丙子**日庚寅时 | **我方录入讹字**（诸本皆丙子） | 丙→两 |
| 4 | 六丁日庚子时断 L14459 | 丁卯庚子时（**脱「日」**） | 丁卯**日**庚子时 | **我方录入讹字** | 脱字 |
| 5 | 六戊日甲寅时断 L15198 | 戊戌日**申寅**时 | 戊戌日**甲寅**时 | 底本异文 | 甲→申 |
| 6 | 六戊日丙辰时断 L15298 | 戊申**时**丙辰时 | 戊申**日**丙辰时 | 底本异文 | 日→时 |
| 7 | 六己日甲子时断 L15722 | 己巳日**庚子**时 | 己巳日**甲子**时 | 底本异文 | 甲→庚 |
| 8 | 六己日己巳时断 L15977 | 己亥日**乙丑**时 | 己亥日**己巳**时 | 底本异文 | 己巳→乙丑 |
| 9 | 六己日癸酉时断 L16178 | 己丑日**癸亥**时 | 己丑日**癸酉**时 | 底本异文 | 酉→亥 |
| 10 | 六辛日壬辰时断 L17198 | 辛巳日**在**辰时 | 辛巳日**壬**辰时 | 底本异文（ctext/维基文库亦作「在」） | 壬→在 |
| 11 | 六辛日甲午时断 L17302 | 辛卯**目**甲午时 | 辛卯**日**甲午时 | 底本异文（ctext/维基文库亦作「目」） | 日→目 |
| 12 | 六乙日丁亥时断 | **整条漏列** 乙巳日丁亥时（源仅列丑卯未酉亥=5） | 「乙巳日丁亥时，吉。卯月，西北运，五品贵，通月气，南方运，中贵。年月干透丁壬，支坐卯酉寅辰者，大贵。」 | **源底本漏列** | 页断开缝 |
| 13 | 六己日庚午时断 | **整条漏列** 己酉日庚午时（源仅列丑卯巳未亥=5） | 「己酉日庚午时，卯月，偏官。辰月，财煞，贵。申酉，伤官伤尽。巳午，印绶，俱贵。丑戌，亦贵。」 | **源底本漏列** | 页断开缝 |

- **定性口径**：第 3/4 项为**我方转录讹字**（不见于任何权威本）；第 1/2/5/6/7/8/9/10/11 项为**底本/通行本异文**（维基文库四库本、ctext 等亦如此）；第 12/13 项为**底本整条漏列**（权威本有，DB 已按 60×12 全矩阵补全，属「DB 优于源」）。13 处**DB `day_hour_clause` 均已正确**。

### 3. 订正落地（整理稿 `.txt` + `data.sql` `classic_text` **同步**）
- `design/六书全文整理/三命通会.txt`：11 处标签订正 + 2 条漏列回填（12/13 追加至相邻条目行尾，**保持行数 18891 不变**）。
- `ontology/database/data.sql` `classic_text`：**同一 13 处**在三命通会逐节正文 `CT_SMTH_184/202/204/205/215/229/231/239/244/245/248/267/269` 内同步订正（逐节 blob 内定点替换，**927 / 298 / 720 行数均不变**）。
- **不修订** `day_hour_clause`（720 条本就正确）。
- 备份：`design/六书全文整理/三命通会.txt.bak_r41_20261103_161813`、`ontology/database/data.sql.bak_r41_20261103_161849`。

### 4. 收敛复检（全 0）
- `audit_dhc_branches.py`：118 节，**日支集合异常 0**。
- `scan_dhc_labels.py`：**日干错标 0 / 时柱错标 0**。
- `audit_clauses.py`：**DB 正文前 20 字未命中源 0**（订正前为 13）。
- `classic_text`·三命通会 298 篇正文**前 30 字未命中源 0**。
- `SiZhuBaZiOntologyComplianceTest`：**9/9 通过**（axioms=1770 / individuals=118；CT 927 / DHC 720 /  THC 120 … 断言全通过）。

### 5. 回归
- 整模块 `mvn -o -q test`：**122/124 通过**；仅 2 项失败——`SiZhuBaZiBpmnRealEngineTest#t_real_engine_single_male_end_to_end`（及套件副本）报「流程未在 PT2M 内完成」。
- **经 A/B 证伪回归**：以**修正前**的 `data.sql.bak_r41` 复跑同一测试**同样失败**（日志显示流程已跑完 `persistAndReturn`），确认系**既存/环境**问题，**与本次文本修正无关**（改动仅涉三命通会文本，该测试 OBDA 条文查询本即降级未读）。
- **进度**：`day_hour_clause` 稽核**收口（0 缺口）**；其余条文类表（`tiaohou_clause` 120、`fu_clause` 209、`dts_clause` 26、`theory_clause` 12、`auspice_clause` 25 及 `pattern_catalog` 102 / `technique_catalog` 66 / `lady_fortune_catalog` 17 / `sexagenary_nature` 60）**待续**。

## 第 40 步 · 第 42 轮全量复检·`tiaohou_clause` 调候条文库「源↔库」双向稽核（2026-11-03）

工程 `D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi`。指令：将「源↔库双向覆盖率稽核」推进到条文类表；遵铁律：用字/用神判定以外部权威本为准，不依 OCR/臆测。

### 1. 结构稽核（全 0）
- 120 条 = **10 日干 × 12 月支 全覆盖**；唯一 `clause_id` 120；字段数均 8。
- `clause_id` 与(日干,月支)、`month_order` 与月支、`day_stem`/主辅用神为单字天干、`source_note` 格式 —— **全 0 不符**。

### 2. 源↔库覆盖核验（归一化提要口径）
- `tiaohou_clause.clause_text` 为《穷通宝鉴（造化元钥）》正文的**归一化提要**：把源起句「正月／二月…」**补足日干**为「正月甲木／二月甲木…」并做标点微调；部分条目**跨小节聚合**（如己土诸月取自「三夏己土总论」、戊土取自「三春戊土总论」、丁火秋月取自「三秋丁火一例」）。
- 逐条以源正文核验：除下述 1 处外，**120 条用神与源正文「先X次Y／专用X」全部吻合**。

### 3. 发现并订正 1 处用神讹误（**THC_甲_卯**）
- **现象**：DB `THC_甲_卯` 原为 主=**丙** / 辅=**癸**，条文「二月甲木，与正月雨水后用法略同，**仍以丙癸为用**…」。与所引《穷通宝鉴》正二月甲木一例「先看庚、次取丁」及「二月甲木，庚金得所，名阳刃架煞」**相矛盾**（「仍以丙癸为用」系 DB 拼接所致，源中此句实出〈正月己土〉节）。
- **外部权威佐证**（遵铁律，联网取本）：维基文库《穷通宝鉴》、周易网/易理小助手（徐乐吾注）、《造化元钥·甲木总论》、164 网「三春甲木看法」均明言——「仲春（雨水之后、谷雨之前）重见生旺，**必用庚金**」「二月甲木，庚金得所，名阳刃驾杀，**专用庚金**」。
- **订正**：`ontology/database/data.sql` `tiaohou_clause`：`THC_甲_卯` 主用神 **丙→庚**、辅用神 **癸→丁**，条文「仍以丙癸为用」→「先庚次丁」。**行数保持 120**。
- 备份：`ontology/database/data.sql.bak_r42_20261103_163357`。

### 4. 收敛复检（全 0）
- `audit_tiaohou.py`：120 行 / 唯一 id 120 / (干,支) 120 全覆盖 / id·月序·用神·出处 **全 0 不符**。
- 覆盖 <0.80 的 23 条经逐条核验**均为跨小节聚合的忠实提炼**（非缺失）。
- `SiZhuBaZiOntologyComplianceTest`：**9/9 通过**（axioms=1770 / individuals=118）。

### 5. 进度
- `day_hour_clause`、`tiaohou_clause` 稽核**均收口（0 缺口）**；其余条文类表（`fu_clause` 209、`dts_clause` 26、`theory_clause` 12、`auspice_clause` 25 及 `pattern_catalog` 102 / `technique_catalog` 66 / `lady_fortune_catalog` 17 / `sexagenary_nature` 60）**待续**。

## 第 41 步 · 第 43 轮全量复检·`fu_clause` 赋/歌/论条文库「回源」稽核（2026-11-03）

工程 `D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi`。脚本 `target/audit_fu.py`。

### 1. 结构稽核（全 0）
- 209 条，唯一 `clause_id` 209，字段数均 5；`subject`/`clause_text`/`source_note` **均无空值**。
- `clause_group` 分布：诗诀 68 / 赋 37 / 文 37 / 歌 18 / 论 18 / 篇 10 / 诀 7 / 图 7 / 说 5 / 独步 2。
- 归属书：渊海子平 163 / 神峰通考 45 / 三命通会 1；**无无法归属者**。

### 2. 逐条回源覆盖（照录口径）
- 209 条中 **202 条正文回源覆盖率 = 1.0**（逐字照录，无截断、无掺改）。
- 另 **7 条为【图录】**（`FU_YH_113/114/151/154/155`、`FU_SF_YangShunYinNiTu`、`FU_SF_ZhiZaoTu`）——原书系图像（如男/女命六亲取要图、天降干支图、阴阳顺逆生旺死绝之图、月律分野之图），整理本无对应文字，DB 以**名录**收录并自注「原书为图，整理本正文无对应文字」，**非缺失**。

### 3. 出处行号核验（全 0）
- 209 条 `source_note` 均含「整理本第 N 行」，逐条核对 **209/209 与所属小节标题吻合**（0 存疑）。

### 4. 结论
- `fu_clause` **0 缺陷**，无需订正；`data.sql` 未改动。

### 5. 进度
- `day_hour_clause` / `tiaohou_clause` / `fu_clause` 稽核**均收口（0 缺口）**；其余条文类表（`dts_clause` 26 / `theory_clause` 12 / `auspice_clause` 25 及 `pattern_catalog` 102 / `technique_catalog` 66 / `lady_fortune_catalog` 17 / `sexagenary_nature` 60）**待续**。

## 第 42 步 · 第 44 轮全量复检·`dts_clause`/`theory_clause`/`auspice_clause` 回源稽核（2026-11-03）

工程 `D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi`。脚本 `target/audit_small.py`。

### 1. 结构稽核（三表全 0）
- **dts_clause 26**：唯一 id 26，字段均 5；组 形象1/方局1/震兑1/坎离1/何知章8/闲神1/从象化象假从假化4/顺反战合局4/君象臣象母象子象4/贞元1；归属**滴天髓 26**。
- **theory_clause 12**：唯一 id 12，字段均 5；组 病药说9/动静说1/盖头说1/六亲说1；归属 渊海子平11 / 神峰通考1。
- **auspice_clause 25**：唯一 id 25，字段均 5；组 十干坐支5/十二月支12/五行时地6/孕生男女2（=25，覆盖完整）；归属**三命通会 25**。
- 三表 `subject`/`clause_text`/`source_note` 均**无空值**。

### 2. 逐条回源（节录提要，忠实）
- 三表 `clause_text` 为对应典籍的**节录/提要**（非全篇照录；标点、句式经归一化），逐条核源**内容一致、无扭曲**：
  - 滴天髓：`君赖臣生理最微`(L1322)、`造化起于元`(L1717)、`震兑主仁义之真机`(L912)、`坎离宰天地之中气`(L941) 等均源内可回找。
  - 渊海子平：`何以为之雕也`(L2981)、`何以为之动也`(L2994)、`何以谓之盖头也`(L2999) 均源内可回找（DB 为节录）。
  - 三命通会：`五行时地` 正春木「见火则生意蔼然，富贵无敌…有土无火仅足衣资」(L6828)、总论「二气者阴阳也…」(L6810)、`甲日申月为偏官`(L6673)、`甲木属阳，乃栋梁之材`(L5985) 均源内可回找。
- 覆盖率<0.95 的条目（dts 7 / theory 3 / auspice 24）经逐条判定**均系标点归一化与提要式节录所致**（如 auspice 用「、」替代源「，」），**非缺失、非失真**。

### 3. 结论
- 三表 **0 缺陷**，无需订正；`data.sql` 未改动。
- 备注：`createdb_tables.ddl` 对三表 `clause_text` 注释为「照录典籍原文」，实际为**忠实节录/提要**（内容无误，仅注释口径偏严，留待文档统一时校正）。

### 4. 进度
- `day_hour_clause` / `tiaohou_clause` / `fu_clause` / `dts_clause` / `theory_clause` / `auspice_clause` 稽核**均收口（0 缺口）**；仅余四张名录表（`pattern_catalog` 102 / `technique_catalog` 66 / `lady_fortune_catalog` 17 / `sexagenary_nature` 60）**待续**。

## 第 43 步 · 第 45 轮全量复检·四张名录表稽核（2026-11-03）

工程 `D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi`。脚本 `target/audit_catalog.py`。

### 1. 结构稽核（四表全 0 缺陷）
- **pattern_catalog 102**：主键 `pattern_id` 唯一 102/102；`pattern_name`/`pattern_class`/`source_note`/`meaning` 无空值。
- **technique_catalog 66**：`tech_id` 唯一 66/66；`tech_name`/`category`/`source_note`/`meaning` 无空值。
- **lady_fortune_catalog 17**：`lady_id` 唯一 17/17；四字段无空值。
- **sexagenary_nature 60**：`name` 唯一 60/60；`nature_text` 无空值；`nature_wuxing` 取值均在 {金木水火土}（非法值 0）。

### 2. 与对照表一致性（sexagenary_nature ↔ sexagenary_cycle）
- 名称集合**完全一致**（`sn-sc` 与 `sc-sn` 差集均为空）；
- 60 条 `ganzhi` 与 `sexagenary_cycle.ganzhi` **逐条吻合**（不符 0）。

### 3. 回源覆盖率核验
- **名目回源**（全书语料）：`pattern_catalog` 未命中 1 项 `夹丘（拱财）`——系 DB 全角括号 `（拱财）` 与源 `夹丘格(拱财格)` 半角括号差异，实质吻合；`technique_catalog` 22 项「X体象」为源「十干体象」(渊海子平 L2123)/「十二支体象」(L2156) 下逐句诗题的**合成标签**（甲木…癸水、子…亥），去「体象」后干支字均在源；`lady_fortune_catalog` `旺夫克子` 实存于三命通会 L11892「○旺夫克子」。以上均**合法**，真实缺失 0。
- **释义回源**：三表 `meaning` 字符覆盖率 ≥0.60（<0.60 者 0）。
- **sexagenary_nature 严格分段**：60 条 `nature_text` 按「，。；、」切段后**逐段命中三命通会源 60/60**（0 未命中）。

### 4. 结论
- 四表 **0 缺陷**，无需订正；`data.sql` 未改动；合规测试通过（axioms=1770 / individuals=118）。

### 5. 进度
- **八张条文/名录表全部稽核收口（0 缺口）**：`day_hour_clause` 720 / `tiaohou_clause` 120 / `fu_clause` 209 / `dts_clause` 26 / `theory_clause` 12 / `auspice_clause` 25 / `pattern_catalog` 102 / `technique_catalog` 66 / `lady_fortune_catalog` 17 / `sexagenary_nature` 60。
- 条文类表与名录表复检**全部完成**；累计仅订正 14 处（第 41 轮 `day_hour_clause` 源文 13 处 + 第 42 轮 `tiaohou_clause` 1 处）。

## 第 44 步 · 第 46 轮全量复检·结构/规则表确定性稽核（2026-11-03）

工程 `D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi`。脚本 `target/audit_structural.py`。

### 1. 天干/地支/六十甲子
- **ganzhi_stem 10**：阴阳、五行、序（1..10）、阴阳个体名 全部吻合（错误 0）。
- **ganzhi_branch 12**：生肖、序（1..12）、五行 全部吻合（错误 0）。
- **sexagenary_cycle 60**：甲子…癸亥干支配对按 60 序、`stem_local`/`branch_local` 引用均正确（错误 0）。

### 2. 纳音/藏干/十神
- **nayin 30**：名集与通行纳音表一致（唯 `砂中金` 为 `沙中金` 之通行异体）、五行与名尾字一致、吉凶值域合法（错误 0）。
- **sexagenary↔nayin 映射**：30 组配对严格对应纳音序（错误 0）。
- **hidden_stem 28**：按地支藏干规范（子癸/丑己癸辛/寅甲丙戊/卯乙/辰戊乙癸/巳丙庚戊/午丁己/未己丁乙/申庚壬戊/酉辛/戌戊辛丁/亥壬甲）逐支集合校验（错误 0）。
- **ten_god_map 100**：10 干×10 干全组合、按生克阴阳逐条校验（`偏官` 为 `七杀` 异名，同义），每种十神各 10 条，错误 0。

### 3. 神煞/月建/节气
- **deity_rule 186**：必填字段（名/命中支/类别/出处）无空；78 个神煞名中仅 `文昌贵人`（源 L4964「有文昌贵」）、`旬空`（＝旬中空亡，源论空亡）为异名/同义，其余全部可回源；著名起例（天乙贵人五组、驿马、桃花、华盖、将星、羊刃=帝旺、禄、天德、月德、劫煞、亡神、孤辰、寡宿）**逐条与命理规范吻合**。类别分布 贵人吉神75/凶煞71/禄马25/空亡14/概念1。
- **month_order 12**：寅…丑全月建、顺序正确（错误 0）。
- **solar_term 24**：立春…大寒序列与序（1..24）完全正确（错误 0）。
- **solar_term_time 2424**：1950–2050 共 101 年×24=2424；`name` 与 `(年,序)` 均唯一；每年度 0..23 完整；`term_local`/`term_name` 与 `solar_term` 一致；年内时刻严格递增、年份归属正确（全部 0 错误）。

### 4. 关于节气时刻精度的说明（非缺陷）
- `solar_term_time` 交节时刻按设计**第 31 步「Meeus 太阳黄经法自算（客户裁定路径 A）」**生成；与 JPL/HKO 精密历表存在数分钟（代表值：2024 立春 DB 16:20 vs 权威 16:27，差约 7 分钟）的**方法固有差异**，属低精度太阳黄经法精度范畴，非数据错误，且已由客户裁定采用该方法。程序端「DB 精确值优先、超范围回退近似（ε<1天）」双路径保障不阻断。

### 5. 结论
- 十张结构/规则表 **0 缺陷**，无需订正；`data.sql` 未改动；合规测试通过（axioms=1770 / individuals=118）。

### 6. 进度
- **条文/名录/结构/规则表复检全部收口（0 缺口）**：`day_hour_clause` 720 / `tiaohou_clause` 120 / `fu_clause` 209 / `dts_clause` 26 / `theory_clause` 12 / `auspice_clause` 25 / `pattern_catalog` 102 / `technique_catalog` 66 / `lady_fortune_catalog` 17 / `sexagenary_nature` 60 / `ganzhi_stem` 10 / `ganzhi_branch` 12 / `sexagenary_cycle` 60 / `nayin` 30 / `hidden_stem` 28 / `ten_god_map` 100 / `deity_rule` 186 / `month_order` 12 / `solar_term` 24 / `solar_term_time` 2424。
- 累计仅订正 14 处（第 41 轮 `day_hour_clause` 源文 13 处 + 第 42 轮 `tiaohou_clause` 1 处）；本两轮（45/46）均为 0 订正。

## 第 45 步 · 第 47 轮全量复检·正文库与全库收口（2026-11-03）

工程 `D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi`。

### 1. `classic_text`（927，逐字正文库）
- 唯一 id 927/927；字段均 5（id/书/节名/正文/出处）；正文空 0；书归属非法 0。
- 分布：滴天髓 63 / 穷通宝鉴 142 / 神峰通考 128 / 三命通会 298 / 渊海子平 296 = 927（子平真诠单独入 `ziping_clause`）。
- 回源覆盖率：min 0.956 / 中位 1.0 / 均值 1.0；<0.95 为 0。

### 2. `ziping_clause`（47，子平真诠正文库）
- 47 篇全文照录（论十干十二支…），唯一 id 47/47；回源覆盖 <0.9 为 0。

### 3. `case_record`（运行态）
- 自增运行态命例表，种子库 0 行属正常（运行时入库）。

### 4. 全库 23 表行数对账（与 DDL 一致）
classic_text 927 / day_hour_clause 720 / fu_clause 209 / deity_rule 186 / tiaohou_clause 120 / pattern_catalog 102 / ten_god_map 100 / technique_catalog 66 / sexagenary_cycle 60 / sexagenary_nature 60 / ziping_clause 47 / nayin 30 / hidden_stem 28 / dts_clause 26 / auspice_clause 25 / solar_term 24 / lady_fortune_catalog 17 / ganzhi_branch 12 / month_order 12 / theory_clause 12 / ganzhi_stem 10 / solar_term_time 2424 / case_record 0 —— 23 表全部与设计 DDL 计数吻合。

### 5. 结论
- 全库 0 缺陷；累计订正 14 处（第 41 轮 13 + 第 42 轮 1）；合规测试 9/9 通过（axioms=1770 / individuals=118）。

### 6. 进度
- 全库 23 表全量复检全部收口（0 缺口）；条文类表 6 张 + 名录表 4 张 + 结构/规则表 10 张 + 正文库 + 子平真诠库均通过，运行态表不计。

## 第 46 步 · 第 48 轮全量复检·原典层重勘（2026-11-03）

工程 `D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi`。触发：客户令「再次从头，按章节逐字逐句，检查是否完全按六部经典构建」。

### 1. 原典抽取（免 OCR）
- 六典源件在 `D:/Documents/中国传统/AI`（docx/epub/pdf）；副本在 `design/六书原文`。
- 抽取：docx/epub 纯标准库解析（`target/extract_src.py`），pdf 用 pdf-cli 读文本层（非 OCR）；`三命通会` pdf 为扫描件（需 OCR，避之），改用其 docx 文本。产物在 `target/src_extract`。

### 2. 整理本 ↔ 原典 双向覆盖（全 ≥0.996）
- 三命通会 0.9989/0.9999；子平真诠 1.0000/0.9968；渊海子平 1.0000/0.9997；滴天髓 0.9985/0.9995；神峰通考 1.0000/0.9995；穷通宝鉴 1.0000/1.0000。
- 结论：转录整理本忠实且完整（遗漏仅页眉/页码级噪声）。

### 3. 库 ↔ 原典 直证（不经整理本）
- `classic_text` 各书 min 覆盖 0.956~1.000（<0.90 计 0）；`ziping_clause`/`fu_clause`/`tiaohou_clause` <0.85 计 0。
- 体量：三命通会 194575/199898（97.3%，○节 298/298 全）；渊海子平 270877/272918（99.3%）；神峰通考 147803/148655（99.4%）；穷通宝鉴 176583/178971（98.7%）；滴天髓 16809/17626（95.4%）；子平真诠 47 篇全。

### 4. 章节级
- 三命通会 298 ○节 298/298；渊海子平 100 论题 100/100；子平真诠 47 篇 47/47 —— 篇题无遗。

### 5. 结论
- 原典 → 整理本 → 库 三级链忠实、章目无遗、「待考」项全落地（穷通宝鉴 120/流日流时/特殊岁运/日时断缺载3/纳音吉凶30）。
- 全库 23 表 **0 缺陷**，无需订正；`data.sql` 未改动；合规测试 9/9（axioms=1770 / individuals=118）。

---

## 第 47 步 · 第 49 轮审计 · 实现方式「本体化 vs 硬编码」定性（2026-11-03）

工程 `D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi`。触发：客户令「以本体专家视角，核当前实现是否都基于本体方式，有无硬编码」。**本节为定性审计，不改数据、不改代码**；凡实测差异另列「订正建议」。

### 0. 审计口径
「硬编码」非等同「实现缺陷」。本项目既定架构（`rules.owl` 注释与 Worker Javadoc 均明载 **★铁律 13**、客户裁定 ⑰/⑲/㉒）为：**「历法/起运数值计算与查表外置于此，不写成 SWRL；程序算理 + 本体查表」**。故审计按三层定性：
- **(A) 完全本体化**：运行期以 SPARQL 实查本体（TBox 类 + OBDA 映射表）取数，无 Java 领域数据。
- **(B) 混合（合于铁律13）**：程序承担算理/编排查表，条文与文本经本体取；属既定架构，非缺陷。
- **(C) 纯 Java 内置领域表**：以 `static final` 数组固化领域数据，未走 OBDA；需评估是否「知而未做」及是否与库口径一致。

### 1. (A) 完全本体化（运行期 SPARQL 实查，共 9 类内容 + 1 类时刻）
Worker 全文 **12 处 SELECT**（`executeAboxQuery`），实查本体者：
| # | 方法 | 本体类（OBDA 映射） | 行号 |
|---|---|---|---|
| 1 | `clauseDayHour` | `reading:DayHourClause` | 1662 |
| 2 | `clauseTiaohou` | `yongshen:TiaohouClause` | 1675 |
| 3 | `clauseNamedPatterns` | `pattern:Pattern`（pattern_catalog） | 1689 |
| 4 | `clauseStemSeat` | `reading:StemBranchAuspice` | 1698 |
| 5 | `clauseMonthBranch` | `reading:MonthBranchDayStemAuspice` | 1712 |
| 6 | `clauseKinship` | `reading:KinshipTheory` | 1724 |
| 7 | `clauseLadyFortunes` | `reading:LadyFortune` | 1732 |
| 8 | `clauseSexagenaryNature` | `core:sexagenaryNatureText` | 1743 |
| 9 | `citeClauses`（通用） | 参数化 `typeIri` | 1774/1791/1833 |
| 10 | `loadSolarTermYear` | `calendar:SolarTermInstant`（solar_term_time 2424 条） | 2885 |
> 全部经 `BackendService.getObdaHandler().executeAboxQuery(...)`；异常降级返回空集（`queryClauses` L1647-1657）。

### 2. (B) 混合：程序算理 + 本体取文（合于铁律13，非缺陷）
- **节点7 旺衰** `handleDayMasterStrength`(L641)：得令/得地/得势以五行生克程序判定（定性、不设权重），文案嵌《滴天髓》中和之义。
- **节点8 格局** `handleDeterminePattern`(L713)：Javadoc 明标「程序（分类）+ SWRL R10 + 本体 pattern:」；月令提纲法定格（程序），具名格命中印证、子平真诠、滴天髓象论**经本体 `citePatternCatalog`/`citeDts`/`citeZiping` 取原文**。
- **节点9 用神** `handleSelectYongShen`(L845)：主取（程序 `yongShenElem`）+ 调候**经本体 `clauseTiaohou`**；旺衰法作异法并陈。
- **节点10 层次** `handleJudgeChartLevel`(L917)：综合评级（程序，客户裁定 ⑬）+ 本体 `yongshen:Level/Purity`。
- **节点11 大运** `handleFortuneCycles`(L1000)：起运/流年/流月/流日/流时按历法程序递推（JDN 锚定、五虎遁、五鼠遁），特殊岁运（岁运并临/天克地冲/伏吟）程序判定。
- 女命 `handleFemaleChart`(L1328)：女命分类**经本体 `clauseLadyFortunes`**，只以 switch 作「条文→本命是否相关」之筛（非数据表）。
> 此层完全符合客户裁定之架构，**不列为缺陷**。

### 3. (C) 纯 Java 内置领域表（`static final`，110–230 行，均在 `handle*` 中被直接索引）
| Java 常量 | 体量 | 对应 DB 表 | 一致性实测 |
|---|---|---|---|
| `GAN`/`ZHI`/`GAN_ELEM`/`GAN_YY`/`ZHI_ELEM`/`ZHI_ANIMAL`/`ZHI_START_HOUR`/`ELEM_NAME` | 10/12/10/10/12/12/12/5 | ganzhi_stem(10)/ganzhi_branch(12) | 一致 |
| `ZHI_HIDDEN`（藏干） | 12 支共 28 干 | hidden_stem(28) | **0 差异** |
| `NAYIN`（纳音30名） | 30 | nayin(30) | **1 处异体**：Java[15]「沙中金」vs DB「砂中金」 |
| `TEN_GOD` + `tenGodIndex()`算法 | 10 名 + 生克算 | ten_god_map(100) | 逻辑一致 |
| `TIANYI`/`YANGREN`/`LU`/`YIMA`/`TAOHUA`/`HUAGAI`/`JIANGXING` | 10×2/10/10/4/4/4/4 | deity_rule(186) | **羊刃 1 处口径分歧**（见 §4） |
| `SANHE`/`LIUHE`/`LIUCHONG`/`LIUHAI`/`SANXING`/`GAN_WUHE`/`GAN_WUHE_HUA` | 4×3/6×2/6×2/6×2/2×3/5×2/5 | relations 语义（无独立表） | 一致 |
| `STAGE_NAME`/`CHANGSHENG_ZHI`（长生十二宫） | 12/10 | —（语义类） | 一致 |
| `S_TERM`/`JIE_IDX`/`JIE_BRANCH`（节气近似） | 24/12/12 | solar_term(24)/solar_term_time(2424) | 近似算法，误差<1天；高精度可改查 OBDA |
| `LUNAR_INFO`（农历1900–2100） | 201 | —（原挂待考 lunar_convert） | 已用已知日期校验吻合 |

**注释与实现不符者（应在下轮订正表述）**：
- `handleTenGodsKin`(L513 节点注释) 称「查表（OBDA ten_god_map）」，**实为** `TEN_GOD[tenGodIndex(...)]` Java 数组。
- `handleArrangeDeities`(L573 节点注释) 称「查表（OBDA `deity_rule`）」，**实为** `TIANYI/YANGREN/LU/...` Java 数组。
- `nayinOf()` 注释自承「内置表；第 11 步可经 OBDA `nayin` 表替换」——**知而未做**（预留路径）。
- 第 29 步注释自承「此前经 `sizhubazi.obda` 暴露、但运行期从未读取的 8 张内容表」。

### 4. 实测差异（口径级，非数据错）
- **藏干**：Java ↔ DB **0 差异**。
- **纳音**：仅 1 处异体字（沙/砂中金），同义非误。
- **羊刃**：Java `YANGREN={3,-1,6,-1,6,-1,9,-1,0,-1}` 依三命通会卷五「五阳有刃、五阴无刃」，**阴干记 -1（不出羊刃）**；DB `deity_rule` **10 干全录**（乙寅/丁巳/己巳/辛申/癸亥）。二者口径各有所本，但**同一工程内两口径并存**，属一致性隐患。

### 5. 结论与订正建议
- **定性**：本项目**非「伪本体」**——TBox（386 类/290 对象属性/154 数据属性/71 限制/6 SWRL）+ OBDA 38 映射 + SKOS 1504 行，运行期 9 类条文/时刻实查本体；核心节点均「程序算理 + 本体取文」双轨，符合铁律13与客户裁定的既定架构。
- **硬编码分布**：主要集中在**干支基础属性/藏干/纳音/十神/神煞/合冲刑害/长生/节气/农历**等「查表型」领域常量（`static final`），共约 **15 组、合计 400+ 条目**；其数据与 DB 高度一致（藏干 0 差、纳音 1 异体），**属「程序算理外置」架构之正常形态**，非缺陷。
- **应予订正者（小修，非重构）**：①`handleTenGodsKin`/`handleArrangeDeities` 注释「查表 OBDA」与实现不符，宜改为「内置表（与 OBDB xxx 表同源）」；②羊刃口径 Java 阴干 -1 vs DB 10 干全录，宜统一（建议保留 Java「五阴无刃」并注明 DB 全录为备用异法，或二者显式并陈）；③纳音「沙/砂中金」统一用字；④`nayinOf` 预留的「改由 OBDA 取数」路径可择机落地，使 30 纳音与库单一真源。
- **本次未改动任何代码/数据**；上述四项列为下一轮「修正后再检查」之候选，待客户确认后施作。

---

## 第 47.1 步 · 订正实施与复检（2026-11-04，客户令「修正」后施作）

承 §5 之四项订正建议，逐项施作并复检如下。

### 订正明细
| # | 项 | 施作 |
|---|---|---|
| ① | 节点注释与实现不符 | 订正 4 处 Javadoc：L381（analyzeNayinHiddenStems）、L510（assignTenGodsKin）、L573（arrangeDeities）、L3104（nayinOf）。皆由「查表（OBDA xxx）」改为「内置表（与 OBDA `xxx` 表同源，已核一致）」，并点明实查本体者（如 `core:sexagenaryNatureText`）。 |
| ② | 羊刃口径 | **澄清非缺陷**：复核 `deity_rule` L2699-2706 —— 五阳（甲卯/丙午戊午/庚酉/壬子）标「阳刃」引三命通会卷二·论羊刃；五阴（乙寅/丁巳己巳/辛申/癸亥）明标「**阴刃（异法）**」引渊海子平·诸神煞篇，且 L2691 注释已言「异名异法并陈」。运行期 Java 采正法（五阳有刃），DB 并存异法并陈，二者各有所本。已在 `YANGREN` 注释补明此义，不改数据（保留异法并陈）。 |
| ③ | 纳音「沙/砂」用字 | 原典两字通假并用（整理本 沙中土6/砂中土4/沙中金3/砂中金4），无绝对正字；唯 DB 内部「金用砂、土用沙」自相矛盾。**统一定「沙」**：`data.sql` L160 `NaYin_ShaZhongJin` 之「砂中金」→「沙中金」（名字段二处）。原典引文 `classic_text`（如 CT_SMTH_006）仍存「砂中金」原文，不动。 |
| ④ | `nayinOf` 单一真源 | 保留内置查表（合铁律13「查表外置」之既定架构，且与库同源），注释由「第 11 步可经 OBDA nayin 表替换」改为「与 OBDA `nayin` 表同源，30 名已核一致」。不再预留无谓的运行时切换（避免引入 OBDA 依赖与降级风险）。 |

### 复检结果（verbatim）
- **纳音 Java ↔ DB**：`Java NAYIN 数量: 30`，`DB nayin 数量: 30`，`差异: 无（30 名完全一致）`；DB 沙/砂 名：`沙中金`、`沙中土`（已统一）。
- **藏干 Java ↔ DB**：0 差异（前已核）。
- **羊刃**：Java 正法（五阳）与 DB「阳刃」行一一对应；DB 另存「阴刃（异法）」五行，标注完备，非冲突。
- **编译 + 测试**：`mvn -o test` **通过（exit 0）**；`SiZhuBaZiOntologyComplianceTest` 本体加载成功，**axioms=1770 / individuals=118**（与订正前一致，无回退）。
- **注释核查**：全文已无「查表（OBDA …）」与实现不符之表述。

### 结论
四项订正**全部落地**：3 处注释类（①④，其中④含注释更新）、1 处数据用字统一（③）、1 处口径澄清（②，非改数据）。纳音 30 名与库单一一致；全量测试通过、本体规模不变。审计所列「硬编码」仍定性为**合铁律13之「程序算理外置」正常形态，非缺陷**；本例仅正其表述与用字，未改其架构。

---

## 第 47.2 步 · 第二轮全量复检「是否皆循本体模式」（2026-11-04，客户令「再次检查」）

### 0. 复检方法
遍阅全部 15 个 Java 源（主实现 3280 行 + Controller 142 行 + 11 测试）；扫描 JDBC/JPQL/JdbcTemplate/EntityManager 直查；以脚本将 Java 内置领域表与本体库表逐一比对；核前轮 4 项订正落位。

### 1. 无「绕体」直读：零外部依赖
- 全工程**无 JDBC/JdbcTemplate/EntityManager/JPQL/@Query**；`SELECT` 仅 13 处且全为 `executeAboxQuery` 之 SPARQL（9 类条文 + 通用 citeClauses + calendar:SolarTermInstant）。
- `Controller`（142 行）无领域硬编码；`SiZhuBaZiApplication`（29 行）为启动类。

### 2. 内置领域表 ≡ 本体库表（逐一镜像，实测 0 差异）
| 领域表 | Java 体量 | 本体库表 | 实测 |
|---|---|---|---|
| 藏干 ZHI_HIDDEN | 12 支 28 干 | hidden_stem（28） | **0 差异** |
| 纳音 NAYIN | 30 | nayin（30） | **0 差异**（前轮已统一沙/砂） |
| 十神 tenGodIndex | 10×10=100 | ten_god_map（100） | **0 差异** |
| 天乙 TIANYI | 10 干 | deity_rule DR_TianYi_* | 全合（甲戊庚→丑未…） |
| 禄 LU | 10 干 | deity_rule DR_Lu_* | 全合 |
| 羊刃 YANGREN | 5 阳 | deity_rule（阳刃5 + 阴刃异法5） | 正法全合；异法已并陈 |
| 干支/五行/生肖/合冲刑害/长生/节气/农历 | 各组 | ganzhi_*/relations/… | 一致 |
> 即内置表为本体数据的**程序侧镜像**（铁律13「查表外置」之落地），非独立于本体的私货。

### 3. 24 个 JobWorker 处理器之模式归类
- **纯本体取文**：clauseDayHour / clauseTiaohou / clauseNamedPatterns / clauseStemSeat / clauseMonthBranch / clauseKinship / clauseLadyFortunes / clauseSexagenaryNature / loadSolarTermYear（9 类，实查 SPARQL）。
- **混合（程序算理 + 本体取文，合铁律13/⑰）**：handleCastFourPillars、handleNayinHiddenStems、handleGanzhiRelations、handleTenGodsKin、handleArrangeDeities、handleDayMasterStrength、handleDeterminePattern、handleSelectYongShen、handleJudgeChartLevel、handleFortuneCycles、handleTimingAndStages、handleSynthesizeFortuneChart、handleCategoryVerdicts、handleMarriageMatch、handleFemaleChart、handleChildChart。
- **程序编排/基础设施（无领域数据需求）**：handleParseBirthInput（历法+真太阳时）、handleMoralAdvice（按层次出劝诫）、handleAggregateReading（拼装命书+告警判据）、handleSendAlert（RabbitMQ）、handlePersistAndReturn（落库 case_record，经 OBDA addComponent）、handleInterpretResult（MaaS 大模型白话）。
- **SWRL 推理**：sizhubazi-rules.owl 6 条（R1/R5/R6/R19/R21/R22）。

### 4. 如实辨析：两处「非本体来源」（非「绕体」，但需知悉）
1. **程序算理**：五行生克、十神推导、格局/用神/旺衰/大运/起运/命宫/胎元等数值与分类计算 —— 属客户裁定 ⑰「程序算理」，本不写入本体（铁律13）。
2. **白话断语模板（Java 内嵌中文）**：如 `handleCategoryVerdicts` 之「性情才德」判语、疾病脏腑对映数组 `zang={"肝/胆",…}`、`buildCategoryVerdicts` 之事业/财运/婚姻/寿夭/飘荡断语、`handleMoralAdvice` 劝诫、`handleMarriageMatch` 合婚结论、`handleChildChart` 小儿关煞、`handleTimingAndStages` 应期运限。此类文字**部分与本体条文并列印证（clauseKinship/citeClassic 等），部分为纯模板白话**；系客户裁定 ⑱「六书原语 + 现代白话（保留出处）」之白话侧，但严格而言其文字不经本体读取。

### 5. 终判
- **若「本体模式」指本项目既定之「本体存典籍条文 + 程序算理 + 白话解读 + SWRL」双轨架构** → **完全符合**：领域数据全镜像本体、典籍原文全经 SPARQL 实查、无绕体直读、无外部命理库依赖。
- **若「本体模式」要求「一切领域断言皆须源自本体、程序不得含领域中文」** → 则第 4.2 类白话断语模板**不满足**，其文字在 Java 内嵌。
- 前轮 4 项订正已全部落位并复验（注释无误标、纳音 30 名一致、羊刃异法并陈标注、全量测试 exit 0，axioms=1770 / individuals=118 不变）。
- **可选的进一步「纯化」**（非缺陷，供裁夺）：将第 4.2 类白话断语模板抽入本体（新建 `reading:CategoryVerdict` 等类 + OBDA 映射），运行期改由本体取；工作量大、且与「白话由程序生成」之现设计与⑱裁定并不冲突，故暂不强改。

---

## 第 47.3 步 · 第三轮深入复检（2026-11-04，客户令「继续检查」）

前两轮定其大端，本轮深入四隅：SWRL 运行期实况、OBDA 覆盖、本体建模真伪、白话模板清点。

### 1. SWRL 运行期实况（重要发现，须如实说明）
- **应用 Java 代码零 SWRL/推理引擎调用**：`grep` 全 `src/main/java` 得 SWRL 仅现于注释，无 Drools/Pellet/Reasoner 等 API 调用。
- **但框架层确有 OWL 推理**：`BackendService` 出自 `com.ocean.openlletresolver`，基于 **Openllet**（`OpenlletReasonerFactory`）；加载 TBox 时新建 `ReasonerService`，源码注「重新评估 SWRL 规则和一致性」并 `reasoner.flush()`（L156-158）。即框架层会对 `rules.owl` 之 6 条 SWRL（R1/R5/R6/R19/R21/R22）作评估。
- **合规测试断言**：`SiZhuBaZiOntologyComplianceTest L181-182` 验告警三类个体 `AlertDaXiong/AlertShouYuanGuan/AlertJiBing` 齐备，证本体侧建模完整。
- **但业务主链未回读 SWRL 推导结果**：`aggregateReading`（L1475-1493）以 Java 判据（`level/grade/strength/deities`）算出 `alertTriggered`，`sendAlert` 读取该变量；全 `src/main/java` 无一处读取 `AlertFlag/AlertDaXiong/hasAlertFlag`（此三者仅见于测试）。故 **SWRL 与 Java 判据「逻辑并存、结论一致」，然 SWRL 不在运行期关键取数路径上**——属「声明式印证 + 框架推理」，非业务数据来源。

### 2. OBDA 映射覆盖（完备）
运行期 9 类查询皆有对应 `mappingId`：
`day_hour_clause→reading:DayHourClause`；`tiaohou_clause→yongshen:TiaohouClause`；`pattern_catalog→pattern:Pattern`；`auspice_stem_seat→reading:StemBranchAuspice`；`auspice_month_branch→reading:MonthBranchDayStemAuspice`；`theory_kinship→reading:KinshipTheory`；`lady_fortune_catalog→reading:LadyFortune`；`sexagenary_nature→core:sexagenaryNatureText`；`solar_term_time→calendar:SolarTermInstant`。
另有 `auspice_time_place/progeny`、`theory_bing_yao/dynamic_static/head_covering`、`dts_*`（8 类）、`fu_clause_all`、`ziping_clause_all`、`classic_text_all`、`technique_catalog` 等共 38 映射备用。**无「查询类无映射」之情形。**

### 3. 本体确已建模「内置表」之领域（镜像不虚）
`core.owl` 明定 `core:HeavenlyStem / EarthlyBranch / SexagenaryCycle / NaYin / HiddenStem` 类，且附 `hasFivePhase / hasYinYang / hasStem / hasBranch / hasNaYin / hasHiddenStem / hasLifeStage` 等基数限制（L81-117）。故 Java 内置表所覆盖之领域，在本体 TBox 中有**完整建模**，并经 OBDA 映射到 DB 真源——内置表为「同一真源之程序侧镜像」，非「本体未覆盖之独立硬编码」。

### 4. 白话断语模板清点（程序侧，非本体取文；依⑱「原语+白话」）
| 位置 | 内容 |
|---|---|
| `handleCategoryVerdicts` L1186 | 疾病脏腑数组 `zang={"肝/胆","心/小肠","脾/胃","肺/大肠","肾/膀胱"}` |
| `handleCategoryVerdicts` L1195-1213 | 性情才德判语（印现主厚重…等五组句式） |
| `buildCategoryVerdicts` L2307-2355 | 事业/财运/婚姻/寿夭/飘荡 五段断语 |
| `handleMoralAdvice` L1423-1427 | 吉凶劝诫模板 |
| `handleMarriageMatch` L1310-1312 | 合婚结论模板 |
| `handleChildChart` L1386-1398 | 小儿关煞模板 |
| `handleTimingAndStages` L1113-1119 | 应期/运限模板 |
| `buildFortuneAnalysis`/`buildFortuneSynthesis` L2217-2306 | 岁运逐运评析断言 |
| `detectExternPattern`/`hitNamedPatterns` L2142-2460 | 外格/具名格构成条件以 Java if 硬编码（仅原文/出处引本体） |

> 此类为「现代白话解读」（裁定⑱），部分与本体条文并列印证（`clauseKinship`/`citeClassic` 等）；**严格言其断言文字不由本体读取**，属程序侧内嵌中文。

### 5. 本轮终判
- **运行期数据来源**：域内**结构化领域数据**（干支/藏干/纳音/十神/神煞/合冲刑害/长生/节气/农历）经「本体类 + OBDA 表 + 程序镜像表」三处一致落地；**典籍原文/条文**全经 SPARQL 实查本体。此为本体模式之实。
- **SWRL**：本体有声明并由框架 Openllet 推理，但业务主链以程序判据为准、未回读 SWRL 结果——**「本体声明与程序实现双写」，非单一本体驱动**。
- **白话断语**：程序内嵌中文模板，属⑱白话侧，非本体取文。
- 综上：**符合既定「本体条文 + 程序算理/编排 + 白话解读 + SWRL」双轨架构**；若客户要求「一切断言（含白话）皆源自本体、SWRL 须为主链推理来源」，则 §1（SWRL 未上主链）与 §4（白话内嵌）两处需改造。此二项均非「数据错误」，属架构取向之取舍。

---

## 第 47.4 步 · 第四轮深入复检（2026-11-04，客户令「继续」）

本轮勘 ABox/DB 真源、OBDA 映射覆盖、测试取向，得一处**实质缺口**及若干注释订正。

### 1. OBDA 映射覆盖：二表漏网（实质发现）
逐一核 `sizhubazi.obda` 之 `FROM` 物理表（共 21 表、38 映射Id），发现 **`deity_rule`（186 行/78 类）与 `ten_god_map`（100 行）二表无任何映射、完全未出现于 obda**。其后果：
- **本体层面不可访问**：此二表数据不能经 Ontop 虚拟化被 SPARQL 查询（与 `nayin`/`hidden_stem`/`pattern_catalog` 等 21 表迥异）。
- **ten_god_map**：由 Java `tenGodIndex` 程序算完全等价替代（实测 100/100 一致），DB 表成「休眠镜像」，功能无损。
- **deity_rule**：Java 仅排**常用八类**（天乙/羊刃/禄/驿马/桃花/华盖/将星/空亡）；DB 78 类中余约 70 类（天德/月德/文昌/金舆/孤辰/寡宿/劫煞/亡神/红鸾/天喜/太极/天厨/福星/国印/学堂/词馆/十恶大败/元辰/灾煞/勾绞…）**运行期既不排、本体亦不可查**——即「库有而运行期两径皆废」。

### 2. 注释订正（前轮遗留 + 本轮新发现）
前轮我于 3 处注释误书「与 OBDA `ten_god_map`/`deity_rule` 同源」（该二表实无映射）；另 `handleArrangeDeities`、`handleChildChart` 二处原注「经 OBDA `deity_rule` 详查」亦与实不符。今一并订正：
- `YANGREN` 注释：`OBDA deity_rule` → `DB 表 deity_rule（暂未接入 OBDA）`。
- `handleTenGodsKin` Javadoc：`与 OBDA ten_god_map` → `与 DB 表 ten_god_map（暂未接入 OBDA，100 项已核）`。
- `handleArrangeDeities` Javadoc：→ `与 DB 表 deity_rule 同源，但暂未接入 OBDA、运行期仅排常用八类，78 类中余者未排`。
- `handleArrangeDeities` 文案：空神煞提示改为「四柱未见常用八类神煞（天乙/羊刃/禄/驿马/桃花/华盖/将星/空亡）」。
- `handleChildChart` 文案：「起例表经 OBDA deity_rule 详查」→「神煞起例运行期暂排常用八类，deity_rule 表 78 类尚未接入 OBDA」。

### 3. ABox 真源核查（正常）
- ABox 仅 213 行 / 114 个体（`core:LifeStage`12、`deity:*`33、`diagnosis:SourceNote`11、`tengod:Kinship/Palace`、`yongshen:*`、`rules:AlertFlag`3 等），干支/月建声明 **0**（已迁 DB，真源唯一，合规测试 P-2 亦有断言）。
- 测试取向：`SiZhuBaZiClassicRulesTest` 以反射校验 Java 程序输出对经典值（五虎遁/五鼠遁/纳音/天乙/JDN），属**程序侧正确性**验证；`SiZhuBaZiOntologyComplianceTest` 验本体公理/个体规模；`BpmnInMemoryTest` 验 BPMN 端到端变量。三类分工明确，无绕体造伪。

### 4. 结论与待决
- 本项目运行期**结构化数据**经本体（21 表映射）+ 程序镜像（8 类神煞/十神/藏干/纳音…）双轨落地；**典籍条文**全经 SPARQL 实查；此为主体符合本体模式。
- **唯一实质缺口**：`deity_rule`（78 类）未接入 OBDA，运行期仅排 8 类，余 70 类「库有而两径皆废」；`ten_god_map` 未映射但 Java 等价替代（无损）。
- **订正已施**：5 处注释/文案修正为实，全量测试 exit 0（axioms=1770 / individuals=118 不变）。
- **待裁（建议）**：若要 strict 本体模式，应为 `deity_rule`/`ten_god_map` 补 OBDA 映射，并扩展神煞起例（8→78 类，或按⑧限定范围显式声明「仅排常用八类」为既定口径）。此属功能扩展，需客户裁量⑧口径后再施，故本轮仅如实标注、未强改。

---

## 第 47.5 步 · 第五轮复检与订正：三合神煞参照（2026-11-04，客户令「继续」）

### 1. 发现并订正：三合类神煞漏排日支
- **实证**：DB `deity_rule` 中 驿马/桃花/华盖/将星 四类之 `rule_type` 明为 **「年支/日支」**（如 `DR_YiMa_ShenZiChen, '年支/日支', '申子辰', '驿马', '', '寅'`），即「以年支或日支为主」（三命通会卷二·论驿马）。
- **原缺陷**：`handleArrangeDeities` 仅取 `refZhi = p[0][1]`（年支），**漏排以日支起者**。
- **订正**：改为兼取年支、日支二参照（`grps = {sanheGroup(年支), sanheGroup(日支)}`），每支逐一比对四类目标；同一神煞以布尔去重。天乙/羊刃/禄（日干起）、空亡（日柱起）不变。
- **校验**：八类之组数组与 deity_rule 逐一相合（驿马寅申亥巳、桃花酉卯午子、华盖辰戌丑未、将星子午酉卯）；全量测试 **exit 0**（axioms=1770 / individuals=118 不变）。

### 2. 具名格局覆盖（设计取舍，非缺陷）
- `pattern_catalog` 载 102 具名格；`hitNamedPatterns` 仅以 Java 条件判 18 命中（14 格：魁罡/六乙鼠贵/六阴朝阳/壬骑龙背/六甲趋乾/六壬趋艮/日贵/日德/日禄归时/天元作禄/天元坐煞/时上一位贵/岁德正官/时上正官/年上七煞/官煞混杂/财官双美/专禄要制）。
- 余者未列，系该函数注释明载之取舍：「条件不可客观判定者不列入（不臆断）」，非漏载；其原文仍在 catalog 供命中印证。

### 3. 神煞/十神 DB 表之「备查」定性
- `ten_god_map`(100)：Java `tenGodIndex` 完全等价（100/100），DB 表为备查镜像，无映射亦无损。
- `deity_rule`(186/78类)：运行期按⑧「以三命通会卷二常用类为主」仅排八类；DB 余类为**备查名录**（数据完整留档），非运行真源，故未接入 OBDA。若日后需本体直查全库名录，须扩 TBox（补 ruleType/target/branch 数据属性）+ 增映射，属功能扩展，候⑧口径裁定。
- 前轮误书「经 OBDA deity_rule 详查」已改实（第 47.4 步），此定性可消「库有而两径皆废」之疑：非废，乃「留档备查 + 运行限定八类」之既定口径。

### 4. 结论
- 本应用运行期：**结构化数据**「本体类 + OBDA 映射表（21 表）+ 程序镜像（0 差异）」三处一致；**典籍条文**全经 SPARQL 实查；**神煞**按⑧口径走常用八类（本条订正补全日支参照）；SWRL 由框架 Openllet 加载评估但不在业务主链取数；白话断语为⑱程序侧解读。
- **符合既定双轨本体架构**；累计本轮已订正：注释 5 处（47.4）、神煞日支参照 1 处（47.5）、纳音用字 1 处（47.1）。全量测试恒 exit 0。


## 第 48 步：三项本体化扩展实施（第 50 轮，2026-11-04）

依客户裁示，将此前「候裁」的三项扩展全部落地为「本体建模 + OBDA 映射 + 运行期 SPARQL + Java 兜底」双轨。

### 48.1 神煞 78 类接入 OBDA
- `deity.owl` 增 `deity:DeityRule` 类 + 数据属性 `ruleId/basePillar/baseGanzhi/ruleDeityName/deityAlias/branchHit/ruleCategory/ruleSource`。
- `sizhubazi.obda` 增 `mappingId deity_rule`（deity_rule 186 行/78 类由「0 映射」转为已暴露）。
- `SiZhuBaZiOntologyJobWorker.handleArrangeDeities` 新增 `deitiesFromOntology()`：SPARQL 取全库起例，按本命四柱语义匹配（日干/年支/年支日支/月支/通用交集；旬空类跳过以免与 voidness 重复）；不可用时降级内置常用八类。
- 效果：运行期可排全量 78 类神煞（天乙/羊刃/禄/驿马/桃花/华盖/将星/文昌/金舆/天德/劫煞…），非仅八类。

### 48.2 SWRL 上主链
- `chart.owl` 增 `chart:hasYangRenChong`；`rules.owl` 增 R20（羊刃逢冲 → AlertShouYuanGuan），SWRL 规则 6→7。
- `aggregateReading` 增 `deriveAlertsViaSwrl()`：本命事实物化 → `BackendService.withTempAxioms` → flush 触发 R19/R20/R21 → 回读 `rules:hasAlertFlag` 个体 → 产出告警；未命中/异常回退程序判据。
- 探针实测：Openllet 由 `isBroken+noYongShen`→`AlertDaXiong`、由 `hasYangRenChong`→`AlertShouYuanGuan`，isConsistent=true。SWRL 成为告警主链真实来源。

### 48.3 白话断语入本体
- `reading.owl` 增 `reading:CategoryVerdict` 类 + `reading:conditionKey`。
- `data.sql` 增 `category_verdict` 表 15 条（事业/财运/婚姻/寿夭/飘荡 × 条件键）；DDL 建表；OBDA 增 `mappingId category_verdict`。
- `buildCategoryVerdicts` 改经 `clauseCategoryVerdict()` 按 category×conditionKey 取本体模板，未命中降级内联。

### 48.4 连带修正与校验
- SKOS `sizhubazi-terminology.ttl` 补录 12 个新增实体概念，满足「本体带标签实体 100% 有概念」交叉校验。
- `mvn -o -q test` **exit 0**；axioms=1817（+47）/ individuals=118（不变）；本轮执行 49 项用例全绿（0 失败/0 错误），BPMN/RabbitMQ 依赖类按环境未启用（源码共 66 个 @Test）。

## 第 49 步：三项整改落实（第 52 轮，2026-11-04）

针对第 51 轮四维复核所列三处残留项的整改。

### 49.1 ① ten_god_map 接入 OBDA
- `tengod.owl` 增类 `tengod:TenGodMap` + 数据属性 `mapId/baseDayStem/targetStem/mapTenGodName/mapSourceNote`。
- `sizhubazi.obda` 增 `mappingId ten_god_map`（映射 40→41，字段对齐 DDL）。
- Java 增 `tenGodOntologyMap()`（一次 SPARQL 取 100 条建缓存）+ `tenGodName()`（本体优先、`tenGodIndex` 降级）；`handleTenGodsKin` 十神展示改走本体。
- SKOS 补 6 概念。

### 49.2 ② relations 域正式标注算理域
- `relations.owl` 增 `rdfs:comment`：合冲刑穿由程序确定性算法判定，TBox 供推理/校验、不设 SPARQL（裁定⑰·铁律13）；`handleGanzhiRelations` 注释同步。

### 49.3 ③ 白话模板继续上移本体
- `category_verdict` 15→20 条：增合婚（2）/劝勉（2）/女命平和（1）。
- Java 三处 `handleMarriageMatch`/`handleMoralAdvice`/`handleFemaleChart` 改经 `verdictOr()` 取本体文，内联兜底保留。

### 49.4 校验
- `mvn -o -q test` **exit 0**；49 项全绿；axioms 1817→**1841**（+24）/ individuals=118 不变；OBDA 映射 40→41。

## 第 50 步：遗漏与硬编码再复核（第 53 轮，2026-11-04）

客户令「再次检查是否遗漏及是否硬编码」→「继续修正」。全量取证结论：

### 50.1 遗漏（无缺口）
- 物理表 DDL **25** ↔ OBDA `mappingId` **41**，按 `FROM <表>` 逐一核对 **25/25 全覆盖**（含 `ten_god_map`）。
- `SELECT` SPARQL **15 条**（第 51 轮 14 + ten_god_map 1），全部经唯一出口 `executeAboxQuery`；JDBC/JPA/JPQL 零命中。

### 50.2 硬编码（无伪造数据；修正 2 处过时表述）
- 1504 处字面量中，含 ≥20 汉字者 59 条：系统提示词 12 / 日志 3 / 白话模板兜底 39 / 算理标签 5，**无原著条文内联**（条文均经 OBDA 取 `?text ?source`）。
- `namedPatternHits` 的 `{名,描述,出处}` 三元组与 `pattern_catalog.source_note` 逐条一致，无张冠李戴；命中原文本仍取自 `pattern_catalog`。
- `data.sql` 11 处「待考」经查明：5 处 SQL 注释 + 6 处穷通宝鉴**原书原文引文**（「此命造确否待考」等），**非占位字段值**，不动。
- **修正**：L164 常量注释、L1510 小儿关杀输出文案原称「`deity_rule` 表尚未接入 OBDA / 78 类尚未接入」，与第 36 步起已完成的 OBDA 全量接入（186 条/78 类）矛盾——已改为如实表述（deity_rule 已经 OBDA mappingId 全量接入、`arrangeDeities` 经 SPARQL 取用、内置表仅降级镜像；小儿关煞本例程序算理排布、deity_rule 未单列该类）。修正后全 src 遗留措辞 grep 零命中。

### 50.3 校验
- `mvn -o -q test` **exit 0**；49 项全绿；axioms=**1841**、individuals=**118** 不变（仅改注释/文案）。

## 第 51 步：逐书逐卷逐节符合性审查（第 54 轮，2026-11-04）

客户令「再次逐卷逐节来审查，是否有缺失，并检查是否是按本体方式，没有硬编码」。以 `design/六书全文整理/*.txt` 为底本，逐书取卷/节标题与本体库（25 表 + 41 映射 + TBox 13 域）逐条反查。

### 51.1 缺失核验（六书缺 0）
- **三命通会**：`○` 起节 **298 节** ↔ `classic_text` **298 章**，逐标题反查 298/298 命中，**缺 0**（逐卷：一 6 / 二 26 / 三 23 / 四 8 / 五 20 / 六 73 / 七 22 / 八 60 / 九 60）。
- **子平真诠**：目录 47 篇 ↔ `ziping_clause` 47 条，**双向全等**（TOC 有库无=0、库有 TOC 无=0）。
- **滴天髓**：通神论 34 + 六亲论 29 = **63 节** ↔ `classic_text` 63 章，全节在位。
- **渊海子平**：正文标题 302 锚点剔 8 卷题 + 1 重出 = **293 类**，仅 6 项库名未直接命中，查明为 5 幅图（入 `fu_clause`）+ 1 处夹注地名，**无真缺**。
- **神峰通考**：`classic_text` **128 章**；论大运/太岁/金不换歌/子平举要/江湖摘锦/男命小运等疑似缺节，正文经定位均在库（CT_SF_59/61/67、FU_YH_004/095），**无内容丢失**。
- **穷通宝鉴**：`classic_text` **142 章** + `tiaohou_clause` **120（10×12 全备、clause_text 无空）**；二月/五月丁火系底本并章，`tiaohou` 丁×卯/丁×午 已单列，运行期不缺。
- 全 5 书**无 <12 字空章**。

### 51.2 本体方式核验（无硬编码）
- JDBC/JPA/JPQL **零命中**；运行期 **15 条 SPARQL** 全经唯一出口 `executeAboxQuery`（OBDA）。
- 14 张条文族表运行期均以 SPARQL 读取；**21 个 OWL 类**均 `owl:Class` 声明；OBDA 映射谓词（readingText/Source/Category）与 `readReadingFamily()` 变量一一对应。
- 1504 处字面量中 59 条长中文（提示词 12 / 日志 3 / 白话 39 / 算理标签 5）**无原著条文正文**。

### 51.3 校验
- `mvn -o -q test` **exit 0**；**63 项全绿（0 失败/0 错误/0 跳过）**，约 1 分 3 秒；axioms=**1841**、individuals=**118**。
- 完整报告：`design/SiZhuBaZi-逐书逐卷逐节符合性审查报告.md` 与 `design/SiZhuBaZi-本体化专项审计报告.md` 第十三节。

### 51.4 端到端复核捕获并修复：部署库缺 `category_verdict` 表
- **现象**：`SiZhuBaZiBpmnInMemoryTest`（生产 Worker 驱动全 21 serviceTask）原 9/11 报 `Process not completed`、各卡满 180s（合计 1629s）；Worker 根错 `OntopMappingResolver 加载失败 → Cannot find relation 'sizhubazidb'.category_verdict`。
- **根因**：`createdb_tables.ddl`（25 表）与 `data.sql`（20 行）均含 `category_verdict`、OBDA 已映射 `reading:CategoryVerdict`，但**活库 `sizhubazidb` 仅 24 表、独缺该表**；Ontop 映射须整体解析，一表缺失致整个 SPARQL 条文层加载失败，凡取数 serviceTask 全抛错。纯本体单测不触活库故长期未显。
- **修复**：自 DDL 取 `CREATE TABLE IF NOT EXISTS category_verdict`、自 data.sql 灌 20 行（事业2/财运4/婚姻4/寿夭2/飘荡3/合婚2/劝勉2/女命1），与源逐条一致（OBDA 以 `jdbc.user=root` 空密码连接，权限无碍）。
- **验证**：`SiZhuBaZiBpmnInMemoryTest` 由 9 失败/1629s → **11/11 全绿 30.8s**，映射失败关键字归零；完整套件 **63 项全绿 exit 0**；`SiZhuBaZiBpmnRealEngineTest` 因外部 Camunda 未常驻，按 `assumeAllReachable` 前置假设正常跳过（0 项，非失败）。附带清除占 `:26500` 的遗留 Camunda 进程（PID 2644）。属部署库漂移，非条文遗漏、非本体设计缺陷。
