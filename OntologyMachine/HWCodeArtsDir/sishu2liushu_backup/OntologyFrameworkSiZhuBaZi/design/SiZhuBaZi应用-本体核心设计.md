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
> 原著 ＝ 本模块 `design/四书原文/` 与 `design/四书全文整理/`：**《渊海子平》**（宋·徐大升）、**《子平真诠》**（清·沈孝瞻）、**《三命通会》**（明·万民英）、**《滴天髓》**（宋·京图撰·明·刘伯温注）。参照典籍：**《穷通宝鉴》**（调候，**文本待补**，相关条文标【待考】）、**《神峰通考》**（明·张楠，合婚，文本已归档）。
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
| **S2 历法与节气** | `calendar:SolarTerm`/`SolarTermInstant`/`MonthOrder`/`TrueSolarTime`/`CalendarBoundary`；`calendar:hasSolarTerm`/`hasMonthOrder`/`hasInstant`/`instantOf`/`termYear`/`termIndex`/`termDatetime` | 节点 1（时辰校正）、节点 2（节气定月/五虎遁）、节点 11（岁运交节）之共同基础；**第 31 步**增 `calendar:SolarTermInstant`（节气精确交节时刻，年度实例） | 三命通会卷二·论遁月时/论年月日时；渊海子平卷之一·论节候歌；客户裁定（闸门⓪、①-1~③；**历法惯例，非四书**）；**第 31 步**节气精度升级（Meeus 太阳黄经法自算，客户裁定采用路径 A） |
| **S3 四书出处与流派标注** | `diagnosis:SourceNote`/`SchoolVariant`/`ToBeVerified`；`diagnosis:hasSourceNote`/`hasSchoolVariant`/`hasToBeVerified` | 全流程——★铁律 9/10「不臆造唯一断语、须指到出处」的承载机制（节点 9 多法冲突并陈、节点 12 应期） | 顶层设计 §5-4「流派差异如实呈现」；子平真诠·论时说拘泥格局/论时说以讹传讹（并陈之据） |
| **S4 术语同义与规范** | `diagnosis:Synonym`（同义词/别名）；经第 13 步 SKOS `term` 承载 | 全流程——供 `SkosSynonymReader`/`OntologyLabelMatcher` 名称归一 | 四书异名（偏官＝七杀、偏印＝倒食/枭神、羊刃＝阳刃、比肩＝禄、劫财＝刃）：渊海子平卷之二·论偏官/论倒食；三命通会卷五·论古人立印食官财名义 |
| **S5 岁运吉凶标志与告警** | `rules:AlertFlag`（大凶 / 寿元关 / 重大疾病，枚举个体）；`rules:hasAlertFlag` | 节点 A（外发告警）：SWRL 命中即外发，无数值阈值 | 顶层设计 I·46；客户裁定 ㉒（2026-10-29）；四书判定（滴天髓·知命/疾病；三命通会卷九·论疾病） |
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
| calendar:hasTrueSolarTime | Functional | calendar:isTrueSolarTimeOf | chart:BirthInput | calendar:TrueSolarTime | 历法惯例（客户裁定 ②）；非四书 |
| calendar:hasBoundary | — | calendar:isBoundaryOf | chart:BirthInput | calendar:CalendarBoundary | 历法惯例（客户裁定 ①②③）；非四书 |
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
| relations:hasHiddenRelation | — | relations:isHiddenRelationOf | core:HeavenlyStem∨core:EarthlyBranch | relations:HiddenRelation | 子平真诠·论刑冲会合解法（暗合暗拱遥克，四书有论者采） |
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
| fortune:hasSubAnnualFortune | — | fortune:isSubAnnualFortuneOf | fortune:AnnualFortune | fortune:SubAnnualFortune | 三命通会·总论岁运（流月零星；流日/流时【待考】） |
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
| calendar:longitude（出生地经度） | xsd:decimal | calendar:TrueSolarTime | 历法惯例（客户裁定 ②）；非四书 |
| calendar:boundaryType（闰月/夏令时/时区/时辰交界） | xsd:string（枚举） | calendar:CalendarBoundary | 历法惯例（客户裁定 ①②③）；非四书 |
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
| yongshen:yongShenMethod（格局法/调候法/旺衰法；标主取） | xsd:string（枚举） | yongshen:YongShen | 客户裁定 ⑫（多法冲突并陈、月令格局法优先）；【调候待考】 |
| yongshen:vitalityState（得时不旺/失时不弱/中和） | xsd:string（枚举） | yongshen:Vitality | 子平真诠·论十干得时不旺失时不弱；滴天髓·衰旺/中和 |
| yongshen:purityState（清/浊/真/假/隐/显/众/寡） | xsd:string（枚举） | yongshen:Purity | 滴天髓·清气/浊气/真神/假神/隐显/众寡 |
| yongshen:levelName（贵格/富格/平常/贫贱） | xsd:string（枚举） | yongshen:Level | 客户裁定 ⑬；子平真诠·论用神格局高低 |
| fortune:fortuneGanzhi（大运/流年/流月干支） | xsd:string | fortune:GreatFortuneCycle∨fortune:AnnualFortune∨fortune:SubAnnualFortune | 三命通会卷二·论大运/论太岁 |
| fortune:fortuneAgeRange（所辖年龄区间） | xsd:string | fortune:GreatFortuneCycle | 渊海子平卷之一·论起大运法 |
| fortune:specialPatternName（岁运并临/天克地冲/伏吟/反吟/桃花红鸾） | xsd:string（枚举） | fortune:SpecialFortunePattern | 三命通会·总论岁运；【待考】细化 |
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
| R16 太岁·天克地冲 | `fortune:AnnualFortune(?y) ∧ ... 流年干支与原局某柱天克地冲` | → `fortune:hasSpecialPattern(?y, ?sp)` | 三命通会·总论岁运。**细化【待考】** |
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
6. **三套用神法并存不矛盾**：格局法（主取）/调候法（【待考】）/旺衰法，经 `yongShenMethod` 标注，主取唯一、余者并陈；调候条文未取得前一律挂 `diagnosis:hasToBeVerified`。
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
  | `fu_clause` | 赋/歌/诗诀/独步/诀/论/篇/说/图 条文库（**208**；第 18 步 R3 205；第 20 步 P5/P8 +3，全文 LONGTEXT 照录） | `reading:`（`reading:CategoryFortune`） |
  | `ziping_clause` | 《子平真诠》47 篇正文条文库（**47**；第 23 步，全文 LONGTEXT 照录） | `reading:`（`reading:ReadingItem`） |
  | `classic_text` | 六典籍**正文全文库**（**921**；第 26 步·第九轮：滴天髓 62 / 穷通宝鉴 142 / 神峰通考 128 / 三命通会 298 / 渊海子平 291，逐篇照录、全文 LONGTEXT 不截断） | `reading:`（`reading:ReadingItem`） |
  - 映射目标 IRI 以**裸写前缀名**生成（`core:{name}` / `calendar:{name}` / ...，**不加尖括号**），与本体实体 IRI 逐字一致；仅**完整 IRI** 才用 `<...>`；`rdfs:label` 用标准前缀写法。**注**：早期写成 `<core:{name}>` 会被 Ontop 误判为「绝对 IRI（scheme=prefix）」而不展开、致映射 0 命中（第 11 步已修正）。
  - ✍️ **数据边界**：静态术语（阴阳/五行/十神名录/宫位/六亲/层次/告警标志/格局名录/神煞名录/长生十二宫）留 ABox；**查表型历法/神煞起例/十神映射/命例运行态**入 MySQL（DB 为唯一真源）。
  - ✍️ **全集规模（第 12 步 · 闸门⑪；第 18 步更新）**：天干 10 / 地支 12 / 六十甲子 60 / 纳音 30 / 藏干 28 / 节气 24 / 月建 12 / 节气精确时刻 **2424**（第 31 步：1950–2050×24）/ 神煞起例 **186**（第 16 步 H1 +49；第 17 步 I2 细煞 +33）/ 十神映射 100 / 日时断 720 / 调候 120 / 具名格局 **102**（第 17 步 I1 +2；第 20 步 P2 +2；第 22 步 P11 +3）/ 女命细分 17 / 技法篇章 **66**（第 20 步 P1/P3/P4/P6/P7 +9；第 21 步 P9 +13、P10 +6）/ 六十甲子性质 60 / 断语条文 auspice 25 + theory 12 + dts 26（第 17 步 I3）/ 赋歌诗诀等条文 **208**（第 18 步 R3 205；第 20 步 P5/P8 +3）/ 《子平真诠》正文 **47**（第 23 步）/ **六典籍正文全文 921**（第 26 步·第九轮：滴天髓 62 / 穷通宝鉴 142 / 神峰通考 128 / 三命通会 298 / 渊海子平 291）；`case_record` / `alert_flag` 为运行态（全集不含历史数据，留空）。数据来源：**AI 依四书生成 + 客户校订**（逐条附出处，无明确出处者标【待考】，如纳音吉凶三分）。
- **SKOS（第 13 步）**：十神（含别名：偏官＝七杀、偏印＝倒食）、神煞（异名异法）、格局、五行/天干/地支、层次、告警类的**中英术语** ↔ 本体 IRI；供 `OntologyLabelMatcher`/`SkosSynonymReader` 名称归一（对齐顶层设计 S4、Example §8）。经 `sizhubazi-tbox.owl` 的 `owl:imports` 并入（闸门⑥）。

---

## 9. 待业务确认项

| # | 问题 | 说明 | 建议结论（**待确认**·2026-10-29） |
|---|---|---|---|
| 1 | **OutputDomain 命名空间落位** | 顶层设计 §5 有 `OutputDomain` 分支，但 §4.1 命名空间（16 项）/§3 子本体（12 个）均无 `output` | 建议**不新增命名空间**：`ReadingConclusion`/`CaseRecord` 归 `reading:`、`AlertFlag` 归 `rules:`（照 Example `LowStock*` 由规则推导先例）|
| 2 | **十神/神煞/空亡/起运的落位** | 查表 vs SWRL vs 程序 | 建议**查表型落 OBDA（MySQL）+ 程序编排**；SWRL 只承接简单判定与最终标志（R19~R22）|
| 3 | **起运岁折算口径** | 三日=一年、一日=四月、一时=十日 | 建议**程序计算**（数值），`chart:pilotAge` 存结果（渊海子平卷之一·论起大运法）|
| 4 | **大凶/寿元关/重大疾病 判据** | 是否设数值阈值？ | 建议**不设阈值**，SWRL 命中即标志（客户裁定 ㉒）；综合判据由程序编排（R19~R21）|
| 5 | **调候用神（《穷通宝鉴》）文本未取得** | E·22/E·24 调候法依赖其文本 | 建议**标【待考】**、建 `diagnosis:ToBeVerified`；取得后并入 `yongshen`（★铁律 8/9/10）|
| 6 | **流日/流时四书无系统专论** | 粒度可选输出（客户裁定 ⑮） | 建议**年级为主、月级零星、日/时标【待考】**；`SubAnnualFortune` 预留 |
| 7 | **特殊岁运格局细化** | 岁运并临/天克地冲/伏吟/反吟/桃花红鸾 起例分散 | 建议**代表性先建、细化留第 12 步**；标【待考】处挂 `ToBeVerified` |
| 8 | **神煞异名异法并陈粒度** | 贵人吉神/禄马/凶煞 多书异名（客户裁定 ⑩） | 建议**以《三命通会》卷二为主、异名异法并列标出处**（`SchoolVariant`）|
| 9 | **命例隐私口径** | 生辰/排盘/批断结果 | 建议**完整存入（含出生地）、不脱敏**（客户裁定 ㉓）|
| 10 | **《命理探源》是否纳入** | 第 2 步流程 §7-㉔ 全局项待决 | 建议**暂不纳入**（非四书），待客户确认 |

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
| 《穷通宝鉴》调候（十天干十二月取用） | — | **文本已取得**（2026-10-30 归档 `穷通宝鉴.txt`）；第 15 步入库 `tiaohou_clause`（120 条），首句无明据者标【待考】 |

> **结论**：本设计全部出处**可回原文定位、无编造**（★铁律 8/9/10）。原著未收录/未取得者（《穷通宝鉴》调候 → **已补全**；流日/流时、特殊岁运格局细化、《命理探源》）已在 §9 显式声明并转【待考】/不纳入，未伪造出处。

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
- 待确认项 10 项（§9）与【待考】项（《穷通宝鉴》调候、流日/流时、特殊岁运格局细化、《命理探源》）须在闸门③由领域专家裁定。

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
- **OCR 还原（留痕）**：源文本《三命通会.txt》为 OCR 本，日时断 16 条按上下文就地还原（如「己亥日**乙丑**时」→「己亥日**己巳**时」、「**两**子日」→「丙子日」、「戊戌日**申寅**时」→「戊戌日**甲寅**时」）；**源本真缺 3 条**（乙巳日丁亥时、己酉日庚午时、戊子日己未时）建【待考】占位，**不编造**。
- **G8 存疑**：《穷通宝鉴》为评注散文体，逐节提炼用神，首句无明据者标【待考】（120 条中约 64 条取据、余标待考）。

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

- 行号（整理本 `design/四书全文整理/穷通宝鉴.txt`，逐条读原文核验，★禁编造）：
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

- 行号（整理本 `design/四书全文整理/渊海子平.txt`，逐条读原文核验，★禁编造）：杂气财官格 **L1038**；月上偏官格 **L1053**；杂气印绶格 **L1212**。
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

- 行号（整理本 `design/四书全文整理/子平真诠.txt`，共 436 行；逐篇以「序号、篇名」标题定位，正文为标题至下一篇之间的非「子平真诠评注」行）：首篇「论十干十二支」**L52**、末篇「论杂格」**L424**；每篇 `source_note` 记实际行号。
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
> 裁定：客户确认「**四书逐篇全文入库**」（按《子平真诠》第 23 步先例，新增专表照录），并列明**六书均不得有遗漏**。

### 1. 补齐内容（新增专表 `classic_text`）
| 编号 | 典籍·篇章 | 条数 |
|---|---|---|
| CT_DTS_* / CT_QT_* / CT_SF_* / CT_SMTH_* / CT_YH_* | 《滴天髓》/《穷通宝鉴》/《神峰通考》/《三命通会》/《渊海子平》逐篇正文（`chapter` 记篇名、`clause_text` 用 LONGTEXT **全文照录不截断**、`source_note` 记整理本实际行号） | **921**（62 / 142 / 128 / 298 / 291） |

- 说明：《子平真诠》47 篇已由第 23 步 `ziping_clause` 承载（覆盖率 99.2%），本轮不重复入 `classic_text`；六书合计正文全文 **968 篇**（921 + 47）。
- 切篇规则：滴天髓以「通神论/六亲论」+「一、篇名」定位；穷通宝鉴以短标题行定位；神峰通考以「叙/说/类/论/诀/赋/歌/篇/式/法/格/辨/图/序/括」后缀短标题定位；三命通会以「卷N + ○节名」定位；渊海子平以篇题定位。清洗页眉、页码（「三 命 通 会」「· N ·」）与「未知」分隔标记。
- 生成源：`HWCodeArtsDir/sizhubazi_classic.py`（`classic_texts()`，运行时从 `design/四书全文整理/*.txt` 照录）；`data.sql` 经 `gen_sizhubazi_full_data.py`（段「9k」）重生成。

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

