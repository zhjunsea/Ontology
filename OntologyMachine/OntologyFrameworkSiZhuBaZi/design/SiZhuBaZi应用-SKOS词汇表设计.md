# SiZhuBaZi 应用 — SKOS 词汇表设计

> **用法**：本模板产出第 13 步。**闸门⑥**：业务人员确认术语与同义词表后方可进入第 14 步。
> **基线**：`OntologyFrameworkExample/ontology/pizza-terminology.ttl` 与 `OntologyFrameworkPlumBlossom/ontology/plumblossom-terminology.ttl`（形态）；构建依据为本应用已定稿 TBox。
> **硬性**：术语**来源于本体**，每个概念必须能对应到本体 IRI（★10：不得凭空造词）。

| 项目 | 内容 |
|---|---|
| 应用 | SiZhuBaZi（四柱命理） |
| 文档日期 | 2026-10-29 |
| 关联本体 | `ontology/sizhubazi-all.owl`（已定稿 TBox，聚合 12 子本体） |
| 产出文件 | `ontology/sizhubazi-terminology.ttl` |
| 命名空间 | `http://example.org/sizhubazi/term/`（`term:` 前缀） |
| 消费方 | `OntologyLabelMatcher` / `SkosSynonymReader` |

---

## 1. 目的与范围

**目的**：把四柱命理领域核心业务术语标准化为 SKOS 概念体系，统一 OWL 本体与其他系统中的词义，实现「同名同义、同义同名」。

**范围**：覆盖本体中全部**具 `rdfs:label` 的业务元素**——即 12 个子本体的类（业务概念）、对象属性（业务关系）、数据属性（业务字段）。
共 **311** 个规范概念：类 158 + 对象属性 76 + 数据属性 77。
无 `rdfs:label` 的元素（如各 `isXxxOf` 逆属性、跨本体重复引用的占位类）**不建概念**，避免孤立词条与凭空造词。

## 2. 术语体系（概念树）

```
term:SiZhuBaZiEntity              本体事物根
├── term:GanzhiDomain             干支基础域
│   ├── term:HeavenlyStem / term:EarthlyBranch / term:YinYang
│   ├── term:FivePhase ── term:Mu/Huo/Tu/Jin/Shui
│   ├── term:SexagenaryCycle / term:HiddenStem / term:NaYin / term:LifeStage
│   └── term:GanzhiEntity
├── term:CalendarDomain           历法域（月建/节气/真太阳时/历法边界）
├── term:ChartDomain              排盘域（四柱/柱/大运/命宫/胎元/小运/生辰输入）
├── term:RelationDomain           干支作用域（冲/合/刑/害/合绊/墓库/隐蔽作用）
├── term:TenGodDomain             十神与六亲域（十神十类/六亲/宫位/十神生克制化）
├── term:DeityDomain              神煞域（神煞/贵人吉神/凶煞类/禄马/空亡）
├── term:PatternDomain            格局域（格局/八正格/外格杂格/杂气/成败）
├── term:YongshenDomain           用神旺衰域（用神/调候/旺衰/层次/清浊/源流通关）
├── term:FortuneDomain            岁运域（大运周期/流年/流月流日流时/运限/应期/化气）
├── term:ReadingDomain            断语域（命书/合婚/女命/小儿/六亲/性情/疾病/劝诫/命例）
├── term:DiagnosisDomain          出处与流派标注域 S3（出处标注/待考标记/流派分歧）
└── term:RulesDomain              告警标志域 S5（告警标志）
```

> 层级用 `skos:broader` 表达；与本体的 `rdfs:subClassOf` 类层级保持同构（域类 → `term:SiZhuBaZiEntity`）。属性类概念无 `broader`（其归属由 `term:` 命名空间与 `exactMatch` 体现）。

## 3. 概念清单（术语 ⇄ 本体 IRI 映射）

> 每行给出对应本体 IRI（类/属性），用于第 14 步交叉校验。等价于完整 ttl 的可读快照。

**分域计数**

| 子本体 | 类 | 对象属性 | 数据属性 | 小计 |
|---|---|---|---|---|
| core | 40 | 10 | 12 | 62 |
| calendar | 6 | 5 | 7 | 18 |
| chart | 12 | 11 | 11 | 34 |
| relations | 12 | 12 | 4 | 28 |
| tengod | 18 | 5 | 5 | 28 |
| deity | 6 | 3 | 4 | 13 |
| pattern | 13 | 2 | 6 | 21 |
| yongshen | 14 | 5 | 6 | 25 |
| fortune | 10 | 8 | 7 | 25 |
| reading | 21 | 11 | 9 | 41 |
| diagnosis | 4 | 3 | 4 | 11 |
| rules | 2 | 1 | 2 | 5 |

| **合计** | **158** | **76** | **77** | **311** |

**全量映射**

#### core 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:Branch_Chen` | 辰（类） | Chen Branch | `core:Branch_Chen` | `term:EarthlyBranch` |
| 2 | `term:Branch_Chou` | 丑（类） | Chou Branch | `core:Branch_Chou` | `term:EarthlyBranch` |
| 3 | `term:Branch_Hai` | 亥（类） | Hai Branch | `core:Branch_Hai` | `term:EarthlyBranch` |
| 4 | `term:Branch_Mao` | 卯（类） | Mao Branch | `core:Branch_Mao` | `term:EarthlyBranch` |
| 5 | `term:Branch_Shen` | 申（类） | Shen Branch | `core:Branch_Shen` | `term:EarthlyBranch` |
| 6 | `term:Branch_Si` | 巳（类） | Si Branch | `core:Branch_Si` | `term:EarthlyBranch` |
| 7 | `term:Branch_Wei` | 未（类） | Wei Branch | `core:Branch_Wei` | `term:EarthlyBranch` |
| 8 | `term:Branch_Wu` | 午（类） | Wu Branch | `core:Branch_Wu` | `term:EarthlyBranch` |
| 9 | `term:Branch_Xu` | 戌（类） | Xu Branch | `core:Branch_Xu` | `term:EarthlyBranch` |
| 10 | `term:Branch_Yin` | 寅（类） | Yin Branch | `core:Branch_Yin` | `term:EarthlyBranch` |
| 11 | `term:Branch_You` | 酉（类） | You Branch | `core:Branch_You` | `term:EarthlyBranch` |
| 12 | `term:Branch_Zi` | 子（类） | Zi Branch | `core:Branch_Zi` | `term:EarthlyBranch` |
| 13 | `term:EarthlyBranch` | 十二地支（类） | Earthly Branch | `core:EarthlyBranch` | `term:GanzhiDomain` |
| 14 | `term:FivePhase` | 五行（类） | Five Phase | `core:FivePhase` | `term:GanzhiDomain` |
| 15 | `term:GanzhiDomain` | 干支基础域（类） | Ganzhi Domain | `core:GanzhiDomain` | `term:SiZhuBaZiEntity` |
| 16 | `term:GanzhiEntity` | 干支实体（类） | Ganzhi Entity | `core:GanzhiEntity` | `term:SiZhuBaZiEntity` |
| 17 | `term:HeavenlyStem` | 十天干（类） | Heavenly Stem | `core:HeavenlyStem` | `term:GanzhiDomain` |
| 18 | `term:HiddenStem` | 藏干（人元司事）（类） | Hidden Stem | `core:HiddenStem` | `term:GanzhiDomain` |
| 19 | `term:Huo` | 火（类） | Fire | `core:Huo` | `term:FivePhase` |
| 20 | `term:Jin` | 金（类） | Metal | `core:Jin` | `term:FivePhase` |
| 21 | `term:LifeStage` | 长生十二宫（类） | Life Stage | `core:LifeStage` | `term:GanzhiDomain` |
| 22 | `term:Mu` | 木（类） | Wood | `core:Mu` | `term:FivePhase` |
| 23 | `term:NaYin` | 纳音（类） | Nayin | `core:NaYin` | `term:GanzhiDomain` |
| 24 | `term:SexagenaryCycle` | 六十甲子（类） | Sexagenary Cycle | `core:SexagenaryCycle` | `term:GanzhiDomain` |
| 25 | `term:Shui` | 水（类） | Water | `core:Shui` | `term:FivePhase` |
| 26 | `term:SiZhuBaZiEntity` | 本体事物根（类） | SiZhuBaZi Entity | `core:SiZhuBaZiEntity` | — |
| 27 | `term:Stem_Bing` | 丙（类） | Bing Stem | `core:Stem_Bing` | `term:HeavenlyStem` |
| 28 | `term:Stem_Ding` | 丁（类） | Ding Stem | `core:Stem_Ding` | `term:HeavenlyStem` |
| 29 | `term:Stem_Geng` | 庚（类） | Geng Stem | `core:Stem_Geng` | `term:HeavenlyStem` |
| 30 | `term:Stem_Gui` | 癸（类） | Gui Stem | `core:Stem_Gui` | `term:HeavenlyStem` |
| 31 | `term:Stem_Ji` | 己（类） | Ji Stem | `core:Stem_Ji` | `term:HeavenlyStem` |
| 32 | `term:Stem_Jia` | 甲（类） | Jia Stem | `core:Stem_Jia` | `term:HeavenlyStem` |
| 33 | `term:Stem_Ren` | 壬（类） | Ren Stem | `core:Stem_Ren` | `term:HeavenlyStem` |
| 34 | `term:Stem_Wu` | 戊（类） | Wu Stem | `core:Stem_Wu` | `term:HeavenlyStem` |
| 35 | `term:Stem_Xin` | 辛（类） | Xin Stem | `core:Stem_Xin` | `term:HeavenlyStem` |
| 36 | `term:Stem_Yi` | 乙（类） | Yi Stem | `core:Stem_Yi` | `term:HeavenlyStem` |
| 37 | `term:Tu` | 土（类） | Earth | `core:Tu` | `term:FivePhase` |
| 38 | `term:Yang` | 阳（类） | Yang | `core:Yang` | `term:YinYang` |
| 39 | `term:Yin` | 阴（类） | Yin | `core:Yin` | `term:YinYang` |
| 40 | `term:YinYang` | 阴阳（类） | Yin-Yang | `core:YinYang` | `term:GanzhiDomain` |
| 41 | `term:branchAnimal` | 地支生肖（数据属性） | branch animal | `core:branchAnimal` | — |
| 42 | `term:branchName` | 地支名（数据属性） | branch name | `core:branchName` | — |
| 43 | `term:branchOrder` | 地支序（子=1）（数据属性） | branch order | `core:branchOrder` | — |
| 44 | `term:core_hasBranch` | 有地支（对象属性） | has branch | `core:hasBranch` | — |
| 45 | `term:core_hasStem` | 有天干（对象属性） | has stem | `core:hasStem` | — |
| 46 | `term:generates` | 生（对象属性） | generates | `core:generates` | — |
| 47 | `term:hasFivePhase` | 有五行（对象属性） | has five phase | `core:hasFivePhase` | — |
| 48 | `term:hasHiddenStem` | 有藏干（对象属性） | has hidden stem | `core:hasHiddenStem` | — |
| 49 | `term:hasLifeStage` | 有长生十二宫（对象属性） | has life stage | `core:hasLifeStage` | — |
| 50 | `term:hasNaYin` | 有纳音（对象属性） | has nayin | `core:hasNaYin` | — |
| 51 | `term:hasYinYang` | 有阴阳（对象属性） | has yin-yang | `core:hasYinYang` | — |
| 52 | `term:hiddenStemRole` | 人元司事角色（数据属性） | hidden stem role | `core:hiddenStemRole` | — |
| 53 | `term:lifeStageName` | 长生十二宫名（数据属性） | life stage name | `core:lifeStageName` | — |
| 54 | `term:naYinName` | 纳音名（数据属性） | nayin name | `core:naYinName` | — |
| 55 | `term:naYinNature` | 纳音性质吉凶（数据属性） | nayin nature | `core:naYinNature` | — |
| 56 | `term:overcomes` | 克（对象属性） | overcomes | `core:overcomes` | — |
| 57 | `term:phaseName` | 五行名（数据属性） | phase name | `core:phaseName` | — |
| 58 | `term:sexagenaryNatureText` | 六十甲子性质吉凶（数据属性） | sexagenary nature text | `core:sexagenaryNatureText` | — |
| 59 | `term:stemFivePhase` | 天干五行（映射）（对象属性） | stem five-phase (mapping) | `core:stemFivePhase` | — |
| 60 | `term:stemName` | 天干名（数据属性） | stem name | `core:stemName` | — |
| 61 | `term:stemYinYang` | 天干阴阳（数据属性） | stem yin-yang | `core:stemYinYang` | — |
| 62 | `term:yinYangName` | 阴阳名（数据属性） | yin-yang name | `core:yinYangName` | — |

#### calendar 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:CalendarBoundary` | 历法边界（类） | Calendar Boundary | `calendar:CalendarBoundary` | `term:CalendarDomain` |
| 2 | `term:CalendarDomain` | 历法域（类） | Calendar Domain | `calendar:CalendarDomain` | `term:SiZhuBaZiEntity` |
| 3 | `term:MonthOrder` | 月建（月令）（类） | Month Order | `calendar:MonthOrder` | `term:CalendarDomain` |
| 4 | `term:SolarTerm` | 二十四节气（类） | Solar Term | `calendar:SolarTerm` | `term:CalendarDomain` |
| 5 | `term:SolarTermInstant` | 节气时刻（年度实例）（类） | Solar Term Instant | `calendar:SolarTermInstant` | `term:CalendarDomain` |
| 6 | `term:TrueSolarTime` | 真太阳时（类） | True Solar Time | `calendar:TrueSolarTime` | `term:CalendarDomain` |
| 7 | `term:boundaryType` | 历法边界类型（数据属性） | calendar boundary type | `calendar:boundaryType` | — |
| 8 | `term:hasBoundary` | 有历法边界（对象属性） | has calendar boundary | `calendar:hasBoundary` | — |
| 9 | `term:hasMonthOrder` | 有月建（对象属性） | has month order | `calendar:hasMonthOrder` | — |
| 10 | `term:hasSolarTerm` | 有节气（对象属性） | has solar term | `calendar:hasSolarTerm` | — |
| 11 | `term:hasTrueSolarTime` | 有真太阳时（对象属性） | has true solar time | `calendar:hasTrueSolarTime` | — |
| 12 | `term:instantOf` | 为节气之实例（对象属性） | is instant of | `calendar:instantOf` | — |
| 13 | `term:longitude` | 出生地经度（数据属性） | longitude | `calendar:longitude` | — |
| 14 | `term:monthOrderName` | 月建名（数据属性） | month order name | `calendar:monthOrderName` | — |
| 15 | `term:solarTermName` | 节气名（数据属性） | solar term name | `calendar:solarTermName` | — |
| 16 | `term:termDatetime` | 精确交节时刻（北京时间）（数据属性） | solar term datetime | `calendar:termDatetime` | — |
| 17 | `term:termIndex` | 节气序（0..23，0=小寒）（数据属性） | solar term index | `calendar:termIndex` | — |
| 18 | `term:termYear` | 节气公历年（数据属性） | solar term year | `calendar:termYear` | — |

#### chart 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:BirthInput` | 生辰输入（类） | Birth Input | `chart:BirthInput` | `term:ChartDomain` |
| 2 | `term:ChartDomain` | 排盘域（类） | Chart Domain | `chart:ChartDomain` | `term:SiZhuBaZiEntity` |
| 3 | `term:DayPillar` | 日柱（类） | Day Pillar | `chart:DayPillar` | `term:Pillar` |
| 4 | `term:FourPillars` | 四柱盘（类） | Four Pillars | `chart:FourPillars` | `term:ChartDomain` |
| 5 | `term:GreatFortune` | 大运（类） | Great Fortune | `chart:GreatFortune` | `term:ChartDomain` |
| 6 | `term:HourPillar` | 时柱（类） | Hour Pillar | `chart:HourPillar` | `term:Pillar` |
| 7 | `term:MingGong` | 命宫（类） | Life Palace (Minggong) | `chart:MingGong` | `term:ChartDomain` |
| 8 | `term:MonthPillar` | 月柱（类） | Month Pillar | `chart:MonthPillar` | `term:Pillar` |
| 9 | `term:Pillar` | 柱（类） | Pillar | `chart:Pillar` | `term:ChartDomain` |
| 10 | `term:SmallFortune` | 小运（类） | Small Fortune | `chart:SmallFortune` | `term:ChartDomain` |
| 11 | `term:TaiYuan` | 胎元（类） | Conception Palace (Taiyuan) | `chart:TaiYuan` | `term:ChartDomain` |
| 12 | `term:YearPillar` | 年柱（类） | Year Pillar | `chart:YearPillar` | `term:Pillar` |
| 13 | `term:birthDate` | 生辰（数据属性） | birth date | `chart:birthDate` | — |
| 14 | `term:birthPlace` | 出生地（数据属性） | birth place | `chart:birthPlace` | — |
| 15 | `term:calendarType` | 历法类型（数据属性） | calendar type | `chart:calendarType` | — |
| 16 | `term:chart_hasBranch` | 柱有地支（对象属性） | has branch (pillar) | `chart:hasBranch` | — |
| 17 | `term:chart_hasStem` | 柱有天干（对象属性） | has stem (pillar) | `chart:hasStem` | — |
| 18 | `term:direction` | 顺逆（顺行/逆行）（数据属性） | direction | `chart:direction` | — |
| 19 | `term:gender` | 性别（数据属性） | gender | `chart:gender` | — |
| 20 | `term:granularity` | 输出粒度（年/月/日）（数据属性） | granularity | `chart:granularity` | — |
| 21 | `term:greatFortuneSeq` | 大运序（数据属性） | great fortune sequence | `chart:greatFortuneSeq` | — |
| 22 | `term:hasBirthInput` | 有生辰输入（对象属性） | has birth input | `chart:hasBirthInput` | — |
| 23 | `term:hasDayPillar` | 有日柱（对象属性） | has day pillar | `chart:hasDayPillar` | — |
| 24 | `term:hasGreatFortune` | 有大运（对象属性） | has great fortune | `chart:hasGreatFortune` | — |
| 25 | `term:hasHourPillar` | 有时柱（对象属性） | has hour pillar | `chart:hasHourPillar` | — |
| 26 | `term:hasMingGong` | 有命宫（对象属性） | has life palace | `chart:hasMingGong` | — |
| 27 | `term:hasMonthPillar` | 有月柱（对象属性） | has month pillar | `chart:hasMonthPillar` | — |
| 28 | `term:hasSmallFortune` | 有小运（对象属性） | has small fortune | `chart:hasSmallFortune` | — |
| 29 | `term:hasTaiYuan` | 有胎元（对象属性） | has conception palace | `chart:hasTaiYuan` | — |
| 30 | `term:hasYearPillar` | 有年柱（对象属性） | has year pillar | `chart:hasYearPillar` | — |
| 31 | `term:mode` | 对象类型（男命/女命/小儿/合婚）（数据属性） | mode | `chart:mode` | — |
| 32 | `term:pilotAge` | 起运岁（数据属性） | pilot age | `chart:pilotAge` | — |
| 33 | `term:sexagenaryPair` | 该柱干支名（数据属性） | sexagenary pair | `chart:sexagenaryPair` | — |
| 34 | `term:yearStemYinYang` | 年干阴阳（阳/阴）（数据属性） | year stem yin-yang | `chart:yearStemYinYang` | — |

#### relations 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:Bondage` | 合绊（羁绊）（类） | Bondage | `relations:Bondage` | `term:RelationDomain` |
| 2 | `term:BranchRelation` | 地支关系（类） | Branch Relation | `relations:BranchRelation` | `term:RelationDomain` |
| 3 | `term:Clash` | 相冲（类） | Clash | `relations:Clash` | `term:BranchRelation` |
| 4 | `term:Harm` | 相穿（六害）（类） | Harm | `relations:Harm` | `term:BranchRelation` |
| 5 | `term:HiddenRelation` | 暗合暗拱遥冲遥克（类） | Hidden Relation | `relations:HiddenRelation` | `term:RelationDomain` |
| 6 | `term:PhaseRelation` | 干支五行生克（类） | Phase Relation | `relations:PhaseRelation` | `term:RelationDomain` |
| 7 | `term:Punishment` | 三刑（类） | Punishment | `relations:Punishment` | `term:BranchRelation` |
| 8 | `term:RelationDomain` | 干支作用域（类） | Relation Domain | `relations:RelationDomain` | `term:SiZhuBaZiEntity` |
| 9 | `term:SixCombination` | 六合（类） | Six Combination | `relations:SixCombination` | `term:BranchRelation` |
| 10 | `term:StemCombination` | 天干五合（类） | Stem Combination | `relations:StemCombination` | `term:RelationDomain` |
| 11 | `term:TombRelation` | 墓库刑冲（类） | Tomb Relation | `relations:TombRelation` | `term:BranchRelation` |
| 12 | `term:TripleCombination` | 三合（半合）（类） | Triple Combination | `relations:TripleCombination` | `term:BranchRelation` |
| 13 | `term:branchRelationType` | 地支关系类型（数据属性） | branch relation type | `relations:branchRelationType` | — |
| 14 | `term:clashesWith` | 相冲（关系）（对象属性） | clashes with (relation) | `relations:clashesWith` | — |
| 15 | `term:combinationResult` | 合化结果（数据属性） | combination result | `relations:combinationResult` | — |
| 16 | `term:combinationType` | 合化类型（数据属性） | combination type | `relations:combinationType` | — |
| 17 | `term:combinesWith` | 天干五合（关系）（对象属性） | combines with (relation) | `relations:combinesWith` | — |
| 18 | `term:harmsWith` | 相穿/六害（对象属性） | harms with | `relations:harmsWith` | — |
| 19 | `term:hasBranchRelation` | 有地支关系（对象属性） | has branch relation | `relations:hasBranchRelation` | — |
| 20 | `term:hasHiddenRelation` | 有隐蔽作用（对象属性） | has hidden relation | `relations:hasHiddenRelation` | — |
| 21 | `term:hasPhaseRelation` | 有五行生克（对象属性） | has phase relation | `relations:hasPhaseRelation` | — |
| 22 | `term:hasStemCombination` | 有天干五合（对象属性） | has stem combination | `relations:hasStemCombination` | — |
| 23 | `term:isBondedBy` | 被合绊（对象属性） | is bonded by | `relations:isBondedBy` | — |
| 24 | `term:punishesWith` | 三刑（关系）（对象属性） | punishes with (relation) | `relations:punishesWith` | — |
| 25 | `term:relationTransformed` | 是否成立/合而不合（数据属性） | relation transformed | `relations:relationTransformed` | — |
| 26 | `term:sixCombinesWith` | 六合（关系）（对象属性） | six-combines with (relation) | `relations:sixCombinesWith` | — |
| 27 | `term:transformsTo` | 化气（对象属性） | transforms to | `relations:transformsTo` | — |
| 28 | `term:tripleCombinesWith` | 三合/半合（对象属性） | triple-combines with | `relations:tripleCombinesWith` | — |

#### tengod 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:BiJian` | 比肩（类） | Bi Jian (Companion) | `tengod:BiJian` | `term:TenGod` |
| 2 | `term:FoodHurtsKill` | 食伤制杀（类） | Food Hurts Kill | `tengod:FoodHurtsKill` | `term:TenGodRelation` |
| 3 | `term:JieCai` | 劫财（类） | Jie Cai (Rob Wealth) | `tengod:JieCai` | `term:TenGod` |
| 4 | `term:Kinship` | 六亲（类） | Kinship | `tengod:Kinship` | `term:TenGodDomain` |
| 5 | `term:OfficialKillMix` | 官杀混杂（类） | Official Kill Mix | `tengod:OfficialKillMix` | `term:TenGodRelation` |
| 6 | `term:Palace` | 宫位（类） | Palace | `tengod:Palace` | `term:TenGodDomain` |
| 7 | `term:PianCai` | 偏财（类） | Pian Cai (Indirect Wealth) | `tengod:PianCai` | `term:TenGod` |
| 8 | `term:PianGuan` | 偏官（七杀）（类） | Pian Guan (Kill) | `tengod:PianGuan` | `term:TenGod` |
| 9 | `term:PianYin` | 偏印（枭神）（类） | Pian Yin (Owl God) | `tengod:PianYin` | `term:TenGod` |
| 10 | `term:ShangGuan` | 伤官（类） | Shang Guan (Hurting Officer) | `tengod:ShangGuan` | `term:TenGod` |
| 11 | `term:ShiShen` | 食神（类） | Shi Shen (Eating God) | `tengod:ShiShen` | `term:TenGod` |
| 12 | `term:TenGod` | 十神（类） | Ten God | `tengod:TenGod` | `term:TenGodDomain` |
| 13 | `term:TenGodDomain` | 十神与六亲域（类） | Ten God Domain | `tengod:TenGodDomain` | `term:SiZhuBaZiEntity` |
| 14 | `term:TenGodRelation` | 十神生克制化（类） | Ten God Relation | `tengod:TenGodRelation` | `term:TenGodDomain` |
| 15 | `term:WealthOfficialGenerate` | 财官相生（类） | Wealth Official Generate | `tengod:WealthOfficialGenerate` | `term:TenGodRelation` |
| 16 | `term:ZhengCai` | 正财（类） | Zheng Cai (Direct Wealth) | `tengod:ZhengCai` | `term:TenGod` |
| 17 | `term:ZhengGuan` | 正官（类） | Zheng Guan (Direct Officer) | `tengod:ZhengGuan` | `term:TenGod` |
| 18 | `term:ZhengYin` | 正印（类） | Zheng Yin (Direct Resource) | `tengod:ZhengYin` | `term:TenGod` |
| 19 | `term:definedByDayMaster` | 以日主定义（对象属性） | defined by day master | `tengod:definedByDayMaster` | — |
| 20 | `term:hasKinship` | 有六亲（对象属性） | has kinship | `tengod:hasKinship` | — |
| 21 | `term:hasPalace` | 有宫位（对象属性） | has palace | `tengod:hasPalace` | — |
| 22 | `term:hasTenGod` | 有十神（对象属性） | has ten god | `tengod:hasTenGod` | — |
| 23 | `term:hasTenGodRelation` | 有生克制化（对象属性） | has ten god relation | `tengod:hasTenGodRelation` | — |
| 24 | `term:kinshipName` | 六亲名（数据属性） | kinship name | `tengod:kinshipName` | — |
| 25 | `term:palaceName` | 宫位名（数据属性） | palace name | `tengod:palaceName` | — |
| 26 | `term:relationType` | 十神制化类型（数据属性） | relation type | `tengod:relationType` | — |
| 27 | `term:tenGodAlias` | 十神别名（数据属性） | ten god alias | `tengod:tenGodAlias` | — |
| 28 | `term:tenGodName` | 十神名（数据属性） | ten god name | `tengod:tenGodName` | — |

#### deity 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:AuspiciousDeity` | 贵人吉神（类） | Auspicious Deity | `deity:AuspiciousDeity` | `term:Deity` |
| 2 | `term:Deity` | 神煞（类） | Deity | `deity:Deity` | `term:DeityDomain` |
| 3 | `term:DeityDomain` | 神煞域（类） | Deity Domain | `deity:DeityDomain` | `term:SiZhuBaZiEntity` |
| 4 | `term:InauspiciousDeity` | 凶煞类（类） | Inauspicious Deity | `deity:InauspiciousDeity` | `term:Deity` |
| 5 | `term:LuMa` | 禄马类（类） | Lu-Ma (Wealth & Horse) | `deity:LuMa` | `term:Deity` |
| 6 | `term:Void` | 空亡（类） | Void | `deity:Void` | `term:DeityDomain` |
| 7 | `term:deityCategory` | 神煞类别（数据属性） | deity category | `deity:deityCategory` | — |
| 8 | `term:deityName` | 神煞名（数据属性） | deity name | `deity:deityName` | — |
| 9 | `term:hasDeity` | 有神煞（对象属性） | has deity | `deity:hasDeity` | — |
| 10 | `term:hasVoid` | 有空亡（对象属性） | has void | `deity:hasVoid` | — |
| 11 | `term:hasVoidResponse` | 空亡应期（对象属性） | has void response | `deity:hasVoidResponse` | — |
| 12 | `term:voidResponseType` | 空亡应期类型（数据属性） | void response type | `deity:voidResponseType` | — |
| 13 | `term:voidType` | 空亡类型（数据属性） | void type | `deity:voidType` | — |

#### pattern 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:AssimilationPattern` | 从象化象假从假化（类） | Assimilation Pattern (Cong Hua) | `pattern:AssimilationPattern` | `term:SpecialPattern` |
| 2 | `term:DirectionPattern` | 方局（类） | Direction Pattern (Fang Ju) | `pattern:DirectionPattern` | `term:SpecialPattern` |
| 3 | `term:ImagePattern` | 形象（类） | Image Pattern (Xing Xiang) | `pattern:ImagePattern` | `term:SpecialPattern` |
| 4 | `term:KanLiPattern` | 坎离（类） | Kan-Li Pattern | `pattern:KanLiPattern` | `term:SpecialPattern` |
| 5 | `term:MixedQiPattern` | 杂气与取用（类） | Mixed Qi Pattern | `pattern:MixedQiPattern` | `term:Pattern` |
| 6 | `term:Pattern` | 格局（类） | Pattern | `pattern:Pattern` | `term:PatternDomain` |
| 7 | `term:PatternDomain` | 格局域（类） | Pattern Domain | `pattern:PatternDomain` | `term:SiZhuBaZiEntity` |
| 8 | `term:PatternQuality` | 格局成败高低（类） | Pattern Quality | `pattern:PatternQuality` | `term:PatternDomain` |
| 9 | `term:RegularPattern` | 八正格（类） | Regular Pattern | `pattern:RegularPattern` | `term:Pattern` |
| 10 | `term:SequencePattern` | 顺局反局战局合局（类） | Sequence Pattern (Shun Ni) | `pattern:SequencePattern` | `term:SpecialPattern` |
| 11 | `term:SovereignPattern` | 君象臣象母象子象（类） | Sovereign Pattern (Jun Chen) | `pattern:SovereignPattern` | `term:SpecialPattern` |
| 12 | `term:SpecialPattern` | 外格杂格（类） | Special Pattern | `pattern:SpecialPattern` | `term:Pattern` |
| 13 | `term:ZhenDuiPattern` | 震兑（类） | Zhen-Dui Pattern | `pattern:ZhenDuiPattern` | `term:SpecialPattern` |
| 14 | `term:hasPattern` | 有格局（对象属性） | has pattern | `pattern:hasPattern` | — |
| 15 | `term:hasPatternQuality` | 有格局成败（对象属性） | has pattern quality | `pattern:hasPatternQuality` | — |
| 16 | `term:isBroken` | 格局是否破（简化判据）（数据属性） | is broken | `pattern:isBroken` | — |
| 17 | `term:patternClass` | 格局类（正格/杂气/外格杂格）（数据属性） | pattern class | `pattern:patternClass` | — |
| 18 | `term:patternMeaning` | 格局释义（数据属性） | pattern meaning | `pattern:patternMeaning` | — |
| 19 | `term:patternName` | 格局名（数据属性） | pattern name | `pattern:patternName` | — |
| 20 | `term:patternQualityName` | 成败高低下（成格/破格/纯/杂）（数据属性） | pattern quality name | `pattern:patternQualityName` | — |
| 21 | `term:patternSource` | 格局出处（数据属性） | pattern source | `pattern:patternSource` | — |

#### yongshen 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:BingYao` | 病药用神（雕枯旺弱四病 / 损益生长四药）（类） | Bing Yao (Disease & Remedy) | `yongshen:BingYao` | `term:YongshenDomain` |
| 2 | `term:FlowRelation` | 源流通关寒暖燥湿刚柔顺逆（类） | Flow Relation | `yongshen:FlowRelation` | `term:YongshenDomain` |
| 3 | `term:FuGe` | 富格（类） | Fu Ge (Wealth Pattern) | `yongshen:FuGe` | `term:Level` |
| 4 | `term:GuiGe` | 贵格（类） | Gui Ge (Noble Pattern) | `yongshen:GuiGe` | `term:Level` |
| 5 | `term:IdleGod` | 闲神（类） | Idle God (Xian Shen) | `yongshen:IdleGod` | `term:YongshenDomain` |
| 6 | `term:Level` | 命局层次（类） | Level | `yongshen:Level` | `term:YongshenDomain` |
| 7 | `term:PinJian` | 贫贱（类） | Pin Jian (Poor & Humble) | `yongshen:PinJian` | `term:Level` |
| 8 | `term:PingChang` | 平常（类） | Ping Chang (Ordinary) | `yongshen:PingChang` | `term:Level` |
| 9 | `term:Purity` | 清浊真假（类） | Purity | `yongshen:Purity` | `term:YongshenDomain` |
| 10 | `term:Tiaohou` | 调候用神（类） | Tiaohou (Seasonal Adjustment) | `yongshen:Tiaohou` | `term:YongshenDomain` |
| 11 | `term:TiaohouClause` | 调候逐月条文（十天干生十二月）（类） | Tiaohou Clause (Monthly Use) | `yongshen:TiaohouClause` | `term:Tiaohou` |
| 12 | `term:Vitality` | 旺衰（类） | Vitality | `yongshen:Vitality` | `term:YongshenDomain` |
| 13 | `term:YongShen` | 用神（相神/忌神）（类） | Yong Shen (Useful God) | `yongshen:YongShen` | `term:YongshenDomain` |
| 14 | `term:YongshenDomain` | 用神旺衰域（类） | Yongshen Domain | `yongshen:YongshenDomain` | `term:SiZhuBaZiEntity` |
| 15 | `term:hasFlowRelation` | 有源流通关（对象属性） | has flow relation | `yongshen:hasFlowRelation` | — |
| 16 | `term:hasLevel` | 有命局层次（对象属性） | has level | `yongshen:hasLevel` | — |
| 17 | `term:hasPurity` | 有清浊真假（对象属性） | has purity | `yongshen:hasPurity` | — |
| 18 | `term:hasVitality` | 有旺衰（对象属性） | has vitality | `yongshen:hasVitality` | — |
| 19 | `term:hasYongShen` | 有（主取）用神（对象属性） | has yong shen | `yongshen:hasYongShen` | — |
| 20 | `term:levelName` | 层次名（贵格/富格/平常/贫贱）（数据属性） | level name | `yongshen:levelName` | — |
| 21 | `term:noYongShen` | 是否无可用用神（简化判据）（数据属性） | no yong shen | `yongshen:noYongShen` | — |
| 22 | `term:purityState` | 清浊真假态（数据属性） | purity state | `yongshen:purityState` | — |
| 23 | `term:vitalityState` | 旺衰态（数据属性） | vitality state | `yongshen:vitalityState` | — |
| 24 | `term:yongShenMethod` | 取用法（格局法/调候法/旺衰法；标主取）（数据属性） | yong shen method | `yongshen:yongShenMethod` | — |
| 25 | `term:yongShenRole` | 用神角色（用神/相神/忌神）（数据属性） | yong shen role | `yongshen:yongShenRole` | — |

#### fortune 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:AnnualFortune` | 流年（太岁）（类） | Annual Fortune | `fortune:AnnualFortune` | `term:FortuneDomain` |
| 2 | `term:FortuneDomain` | 岁运域（类） | Fortune Domain | `fortune:FortuneDomain` | `term:SiZhuBaZiEntity` |
| 3 | `term:FortuneSynthesis` | 岁运综合辩证（类） | Fortune Synthesis | `fortune:FortuneSynthesis` | `term:FortuneDomain` |
| 4 | `term:GreatFortuneCycle` | 大运（小运/交运）（类） | Great Fortune Cycle | `fortune:GreatFortuneCycle` | `term:FortuneDomain` |
| 5 | `term:LifeStageLimit` | 运限法（类） | Life Stage Limit | `fortune:LifeStageLimit` | `term:FortuneDomain` |
| 6 | `term:ResponsePeriod` | 应期（类） | Response Period | `fortune:ResponsePeriod` | `term:FortuneDomain` |
| 7 | `term:SpecialFortunePattern` | 特殊岁运格局（类） | Special Fortune Pattern | `fortune:SpecialFortunePattern` | `term:FortuneDomain` |
| 8 | `term:SubAnnualFortune` | 流月流日流时（类） | Sub-annual Fortune | `fortune:SubAnnualFortune` | `term:FortuneDomain` |
| 9 | `term:YunHuaQi` | 运化气（化气十段锦）（类） | Fortune Transforming Qi (Yun Hua Qi) | `fortune:YunHuaQi` | `term:FortuneDomain` |
| 10 | `term:ZhenYuan` | 贞元（元运循环）（类） | Zhen Yuan (Cycle Origin) | `fortune:ZhenYuan` | `term:FortuneDomain` |
| 11 | `term:fortuneAgeRange` | 所辖年龄区间（数据属性） | fortune age range | `fortune:fortuneAgeRange` | — |
| 12 | `term:fortuneGanzhi` | 岁运干支（数据属性） | fortune ganzhi | `fortune:fortuneGanzhi` | — |
| 13 | `term:hasAnnualFortune` | 有流年（对象属性） | has annual fortune | `fortune:hasAnnualFortune` | — |
| 14 | `term:hasFortuneSynthesis` | 有岁运综合辩证（对象属性） | has fortune synthesis | `fortune:hasFortuneSynthesis` | — |
| 15 | `term:hasGreatFortuneCycle` | 有大运周期（对象属性） | has great fortune cycle | `fortune:hasGreatFortuneCycle` | — |
| 16 | `term:hasLifeStageLimit` | 有运限（对象属性） | has life stage limit | `fortune:hasLifeStageLimit` | — |
| 17 | `term:hasResponsePeriod` | 有应期（对象属性） | has response period | `fortune:hasResponsePeriod` | — |
| 18 | `term:hasSpecialPattern` | 有特殊岁运格局（对象属性） | has special fortune pattern | `fortune:hasSpecialPattern` | — |
| 19 | `term:hasSubAnnualFortune` | 有流月流日流时（对象属性） | has sub-annual fortune | `fortune:hasSubAnnualFortune` | — |
| 20 | `term:hasYunHuaQi` | 有运化气（对象属性） | has fortune transforming qi | `fortune:hasYunHuaQi` | — |
| 21 | `term:lifeStageSegment` | 运限阶段（少年/青年/中年/老年）（数据属性） | life stage segment | `fortune:lifeStageSegment` | — |
| 22 | `term:responseUnit` | 应期单位（数据属性） | response unit | `fortune:responseUnit` | — |
| 23 | `term:responseValue` | 应期值（数据属性） | response value | `fortune:responseValue` | — |
| 24 | `term:specialPatternName` | 特殊岁运格局名（数据属性） | special fortune pattern name | `fortune:specialPatternName` | — |
| 25 | `term:synthesisPriority` | 岁运优先级（数据属性） | synthesis priority | `fortune:synthesisPriority` | — |

#### reading 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:Admonition` | 劝诫修身（类） | Admonition | `reading:Admonition` | `term:ReadingItem` |
| 2 | `term:CaseRecord` | 命例记录（隐私存档 S6）（类） | Case Record | `reading:CaseRecord` | `term:ReadingDomain` |
| 3 | `term:CategoryFortune` | 分类事项（类） | Category Fortune | `reading:CategoryFortune` | `term:ReadingItem` |
| 4 | `term:ChildFortune` | 小儿关杀（类） | Child Fortune | `reading:ChildFortune` | `term:ReadingItem` |
| 5 | `term:DayHourClause` | 日时断条文（六X日X时断）（类） | Day-Hour Clause | `reading:DayHourClause` | `term:ReadingItem` |
| 6 | `term:Disease` | 疾病（类） | Disease | `reading:Disease` | `term:ReadingItem` |
| 7 | `term:DynamicStaticTheory` | 动静说（类） | Dynamic-Static Theory | `reading:DynamicStaticTheory` | `term:ReadingItem` |
| 8 | `term:HeadCoveringTheory` | 盖头说（类） | Head-Covering Theory (Gai Tou) | `reading:HeadCoveringTheory` | `term:ReadingItem` |
| 9 | `term:IlluminatingChapter` | 何知章（类） | Illuminating Chapter (He Zhi Zhang) | `reading:IlluminatingChapter` | `term:ReadingItem` |
| 10 | `term:KinshipFortune` | 六亲吉凶（类） | Kinship Fortune | `reading:KinshipFortune` | `term:ReadingItem` |
| 11 | `term:KinshipTheory` | 六亲说（类） | Kinship Theory | `reading:KinshipTheory` | `term:ReadingItem` |
| 12 | `term:LadyFortune` | 女命专论（类） | Lady Fortune | `reading:LadyFortune` | `term:ReadingItem` |
| 13 | `term:MarriageMatch` | 八字合婚（类） | Marriage Match | `reading:MarriageMatch` | `term:ReadingItem` |
| 14 | `term:MonthBranchDayStemAuspice` | 十二月支得日干吉凶（类） | Month-Branch Day-Stem Auspice | `reading:MonthBranchDayStemAuspice` | `term:ReadingItem` |
| 15 | `term:ProgenyPrediction` | 孕生男女（定妇人孕生男女）（类） | Progeny Prediction (Pregnancy) | `reading:ProgenyPrediction` | `term:ChildFortune` |
| 16 | `term:ReadingConclusion` | 综合结论（命书）（类） | Reading Conclusion | `reading:ReadingConclusion` | `term:ReadingDomain` |
| 17 | `term:ReadingDomain` | 断语域（类） | Reading Domain | `reading:ReadingDomain` | `term:SiZhuBaZiEntity` |
| 18 | `term:ReadingItem` | 断语条目（类） | Reading Item | `reading:ReadingItem` | `term:ReadingDomain` |
| 19 | `term:StemBranchAuspice` | 十干坐支得月时吉凶（类） | Stem-Branch Auspice (San Ming Vol.4) | `reading:StemBranchAuspice` | `term:ReadingItem` |
| 20 | `term:Temperament` | 性情相貌才德（类） | Temperament | `reading:Temperament` | `term:ReadingItem` |
| 21 | `term:TimePlaceFortune` | 五行时地分野吉凶（类） | Time-Place Fortune (Wu Xing Shi Di) | `reading:TimePlaceFortune` | `term:ReadingItem` |
| 22 | `term:caseBirthData` | 命例生辰（数据属性） | case birth data | `reading:caseBirthData` | — |
| 23 | `term:caseResult` | 命例批断结果（数据属性） | case result | `reading:caseResult` | — |
| 24 | `term:conclusionText` | 命书结论（数据属性） | conclusion text | `reading:conclusionText` | — |
| 25 | `term:diseaseOrgan` | 五脏六腑（数据属性） | disease organ | `reading:diseaseOrgan` | — |
| 26 | `term:diseaseSevere` | 疾病是否重大（简化判据）（数据属性） | disease severe | `reading:diseaseSevere` | — |
| 27 | `term:hasAdmonition` | 有修身劝诫（对象属性） | has admonition | `reading:hasAdmonition` | — |
| 28 | `term:hasCaseRecord` | 有命例记录（对象属性） | has case record | `reading:hasCaseRecord` | — |
| 29 | `term:hasCategoryFortune` | 有分类事项断（对象属性） | has category fortune | `reading:hasCategoryFortune` | — |
| 30 | `term:hasChildFortune` | 有小儿关杀（对象属性） | has child fortune | `reading:hasChildFortune` | — |
| 31 | `term:hasConclusion` | 有命书结论（对象属性） | has conclusion | `reading:hasConclusion` | — |
| 32 | `term:hasDisease` | 有疾病（对象属性） | has disease | `reading:hasDisease` | — |
| 33 | `term:hasKinshipFortune` | 有六亲吉凶（对象属性） | has kinship fortune | `reading:hasKinshipFortune` | — |
| 34 | `term:hasLadyFortune` | 有女命专论（对象属性） | has lady fortune | `reading:hasLadyFortune` | — |
| 35 | `term:hasMarriageMatch` | 有八字合婚（对象属性） | has marriage match | `reading:hasMarriageMatch` | — |
| 36 | `term:hasReading` | 有断语（对象属性） | has reading | `reading:hasReading` | — |
| 37 | `term:hasTemperament` | 有性情相貌才德（对象属性） | has temperament | `reading:hasTemperament` | — |
| 38 | `term:matchComplementarity` | 合婚互补性（数据属性） | match complementarity | `reading:matchComplementarity` | — |
| 39 | `term:readingCategory` | 断语类别（数据属性） | reading category | `reading:readingCategory` | — |
| 40 | `term:readingSource` | 断语出处（数据属性） | reading source | `reading:readingSource` | — |
| 41 | `term:readingText` | 断语文本（数据属性） | reading text | `reading:readingText` | — |

#### diagnosis 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:DiagnosisDomain` | 出处与流派标注域（S3）（类） | Source & School Annotation Domain | `diagnosis:DiagnosisDomain` | `term:SiZhuBaZiEntity` |
| 2 | `term:SchoolVariant` | 流派分歧（类） | School Variant | `diagnosis:SchoolVariant` | `term:DiagnosisDomain` |
| 3 | `term:SourceNote` | 出处标注（类） | Source Note | `diagnosis:SourceNote` | `term:DiagnosisDomain` |
| 4 | `term:ToBeVerified` | 待考标记（类） | To Be Verified | `diagnosis:ToBeVerified` | `term:DiagnosisDomain` |
| 5 | `term:hasSchoolVariant` | 有流派分歧（对象属性） | has school variant | `diagnosis:hasSchoolVariant` | — |
| 6 | `term:hasSourceNote` | 有出处标注（对象属性） | has source note | `diagnosis:hasSourceNote` | — |
| 7 | `term:hasToBeVerified` | 有待考标记（对象属性） | has to-be-verified | `diagnosis:hasToBeVerified` | — |
| 8 | `term:sourceBook` | 书名（数据属性） | source book | `diagnosis:sourceBook` | — |
| 9 | `term:sourceVolume` | 卷/篇（数据属性） | source volume | `diagnosis:sourceVolume` | — |
| 10 | `term:toBeVerifiedNote` | 待考说明（数据属性） | to-be-verified note | `diagnosis:toBeVerifiedNote` | — |
| 11 | `term:variantText` | 分歧判据文本（数据属性） | variant text | `diagnosis:variantText` | — |

#### rules 域

| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |
|---|---|---|---|---|---|
| 1 | `term:AlertFlag` | 告警标志（类） | Alert Flag | `rules:AlertFlag` | `term:RulesDomain` |
| 2 | `term:RulesDomain` | 告警标志域（S5）（类） | Alert Flag Domain | `rules:RulesDomain` | `term:SiZhuBaZiEntity` |
| 3 | `term:alertTriggered` | 是否命中（SWRL 推导）（数据属性） | alert triggered | `rules:alertTriggered` | — |
| 4 | `term:alertType` | 告警类型（大凶/寿元关/重大疾病）（数据属性） | alert type | `rules:alertType` | — |
| 5 | `term:hasAlertFlag` | 有告警标志（对象属性） | has alert flag | `rules:hasAlertFlag` | — |

## 4. 标签与同义词规范

| 项 | 规则 |
|---|---|
| `skos:prefLabel` | 每概念每语言恰一条；`@zh` 取本体 `rdfs:label`；`@en` 为对应英文译名（与 `@zh` 一一对应） |
| `skos:altLabel` | 同义词/别名（受 `SkosSynonymReader` 消费），**逐条标注来源** |
| `skos:definition` | 取本体 `rdfs:comment`（如有） |
| 概念方案头 | `skos:ConceptScheme` + `owl:Ontology` 双类型；`dct:created` / `dct:description` |

**同名「类/对象属性」消歧**：`relations:Clash|StemCombination|Punishment|SixCombination` 与同名对象属性共用本体标签，对**对象属性**加「（关系）」限定（如 `相冲（关系）`），标签主体仍取自本体，属业务约定。

### 4.1 同义词 / 别名表（含来源与归属概念）

> 共 **97** 条。来源为六书具体卷篇，或本应用设计文档/业务约定；查无出处者标【待考】。

| # | SKOS 概念 (`term:`) | 规范名 `prefLabel`@zh | 同义词/别名 `altLabel`@zh | 来源 |
|---|---|---|---|---|
| 1 | `term:Admonition` | 劝诫修身 | 修身 | 本应用设计文档·断语域（劝诫修身） |
| 2 | `term:AlertFlag` | 告警标志 | 告警 | 本应用设计文档·告警标志域 S5 |
| 3 | `term:AnnualFortune` | 流年（太岁） | 太岁 | 三命通会卷二·论太岁 |
| 4 | `term:AssimilationPattern` | 从象化象假从假化 | 从化 | 滴天髓·从象/化象/假从/假化 |
| 5 | `term:AuspiciousDeity` | 贵人吉神 | 吉神 | 三命通会卷二·诸神煞篇 |
| 6 | `term:BingYao` | 病药用神（雕枯旺弱四病 / 损益生长四药） | 四病四药 | 神峰通考·雕枯旺弱四病说/损益生长四药说 |
| 7 | `term:BingYao` | 病药用神（雕枯旺弱四病 / 损益生长四药） | 病药 | 神峰通考·病药说 |
| 8 | `term:Bondage` | 合绊（羁绊） | 合绊 | 本应用设计文档·干支作用域 |
| 9 | `term:Bondage` | 合绊（羁绊） | 绊 | 本应用设计文档·干支作用域 |
| 10 | `term:CaseRecord` | 命例记录（隐私存档 S6） | 命例 | 本应用设计文档·断语域（隐私存档 S6） |
| 11 | `term:CategoryFortune` | 分类事项 | 分类断 | 本应用设计文档·断语域 |
| 12 | `term:ChildFortune` | 小儿关杀 | 小儿关煞 | 三命通会卷三·论小儿关煞 |
| 13 | `term:Clash` | 相冲 | 六冲 | 三命通会卷二·论冲 |
| 14 | `term:Clash` | 相冲 | 冲 | 三命通会卷二·论冲 |
| 15 | `term:DayHourClause` | 日时断条文（六X日X时断） | 日时断条文 | 三命通会卷八·六X日Y时断 |
| 16 | `term:Deity` | 神煞 | 神杀 | 三命通会卷二·诸神煞篇 |
| 17 | `term:EarthlyBranch` | 十二地支 | 十二支 | 渊海子平卷之一·论天干地支 |
| 18 | `term:FivePhase` | 五行 | 五行之气 | 三命通会卷一·论五行生克 |
| 19 | `term:FlowRelation` | 源流通关寒暖燥湿刚柔顺逆 | 源流 | 三命通会卷二·论源流 |
| 20 | `term:FlowRelation` | 源流通关寒暖燥湿刚柔顺逆 | 通关 | 三命通会卷二·论通关 |
| 21 | `term:FoodHurtsKill` | 食伤制杀 | 食伤制煞 | 子平真诠·论食伤制煞 |
| 22 | `term:FourPillars` | 四柱盘 | 八字 | 渊海子平卷之一·论日为主 |
| 23 | `term:FourPillars` | 四柱盘 | 四柱 | 渊海子平卷之一·论日为主 |
| 24 | `term:FuGe` | 富格 | 富 | 三命通会卷二·论富贵贫贱 |
| 25 | `term:GuiGe` | 贵格 | 贵 | 三命通会卷二·论富贵贫贱 |
| 26 | `term:Harm` | 相穿（六害） | 六害 | 三命通会卷二·论六害 |
| 27 | `term:Harm` | 相穿（六害） | 相害 | 三命通会卷二·论六害 |
| 28 | `term:Harm` | 相穿（六害） | 穿 | 三命通会卷二·论六害 |
| 29 | `term:HeavenlyStem` | 十天干 | 十干 | 渊海子平卷之一·论天干地支 |
| 30 | `term:HiddenRelation` | 暗合暗拱遥冲遥克 | 暗合 | 三命通会卷二·论暗合 |
| 31 | `term:HiddenRelation` | 暗合暗拱遥冲遥克 | 遥冲 | 三命通会卷二·论遥冲 |
| 32 | `term:HiddenStem` | 藏干（人元司事） | 人元 | 三命通会卷一·论人元司事 |
| 33 | `term:HiddenStem` | 藏干（人元司事） | 支藏人元 | 渊海子平卷之一·又地支藏遁歌 |
| 34 | `term:InauspiciousDeity` | 凶煞类 | 凶煞 | 三命通会卷二·诸神煞篇 |
| 35 | `term:InauspiciousDeity` | 凶煞类 | 凶神 | 三命通会卷二·诸神煞篇 |
| 36 | `term:JieCai` | 劫财 | 败财 | 三命通会卷二·论劫财 |
| 37 | `term:KinshipFortune` | 六亲吉凶 | 六亲 | 渊海子平卷之一·论六亲 |
| 38 | `term:LadyFortune` | 女命专论 | 坤造 | 三命通会卷三·论女命 |
| 39 | `term:LadyFortune` | 女命专论 | 女命 | 三命通会卷三·论女命 |
| 40 | `term:Level` | 命局层次 | 层次 | 本应用设计文档·用神旺衰域 |
| 41 | `term:LifeStage` | 长生十二宫 | 十二长生 | 三命通会卷二·论长生 |
| 42 | `term:LifeStage` | 长生十二宫 | 长生 | 三命通会卷二·论长生 |
| 43 | `term:LifeStageLimit` | 运限法 | 运限 | 三命通会卷二·论运限 |
| 44 | `term:LuMa` | 禄马类 | 禄马 | 三命通会卷二·论禄马 |
| 45 | `term:MarriageMatch` | 八字合婚 | 合婚 | 《神峰通考》·合婚（已归档） |
| 46 | `term:MixedQiPattern` | 杂气与取用 | 杂气 | 子平真诠·论杂气 |
| 47 | `term:MonthOrder` | 月建（月令） | 月令 | 三命通会卷二·论遁月时 |
| 48 | `term:MonthOrder` | 月建（月令） | 月建 | 三命通会卷二·论遁月时 |
| 49 | `term:NaYin` | 纳音 | 纳音五行 | 三命通会卷一·论纳音取象 |
| 50 | `term:OfficialKillMix` | 官杀混杂 | 官煞混杂 | 三命通会卷二·论官煞混杂 |
| 51 | `term:Pattern` | 格局 | 格 | 子平真诠·论格局 |
| 52 | `term:PatternQuality` | 格局成败高低 | 成败 | 子平真诠·论格局成败 |
| 53 | `term:PianGuan` | 偏官（七杀） | 七杀 | 渊海子平卷之一·论七杀 |
| 54 | `term:PianGuan` | 偏官（七杀） | 七煞 | 三命通会卷二·论七煞 |
| 55 | `term:PianYin` | 偏印（枭神） | 倒食 | 渊海子平卷之一·论倒食 |
| 56 | `term:PianYin` | 偏印（枭神） | 枭神 | 三命通会卷二·论枭神 |
| 57 | `term:Punishment` | 三刑 | 刑 | 三命通会卷二·论三刑 |
| 58 | `term:Purity` | 清浊真假 | 清浊 | 子平真诠·论清浊 |
| 59 | `term:ReadingConclusion` | 综合结论（命书） | 命书 | 本应用设计文档·断语域（综合结论） |
| 60 | `term:ReadingItem` | 断语条目 | 断语 | 本应用设计文档·断语域 |
| 61 | `term:RegularPattern` | 八正格 | 八格 | 子平真诠·论正格 |
| 62 | `term:RegularPattern` | 八正格 | 正格 | 子平真诠·论正格 |
| 63 | `term:SchoolVariant` | 流派分歧 | 流派 | 本应用设计文档·出处标注域 S3 |
| 64 | `term:SequencePattern` | 顺局反局战局合局 | 顺逆 | 滴天髓·顺局/反局/战局/合局 |
| 65 | `term:SexagenaryCycle` | 六十甲子 | 六十花甲子 | 渊海子平卷之一·论六十花甲子纳音并注解 |
| 66 | `term:SexagenaryCycle` | 六十甲子 | 花甲子 | 三命通会卷一·论纳音取象 |
| 67 | `term:SixCombination` | 六合 | 地支六合 | 三命通会卷二·论六合 |
| 68 | `term:SolarTerm` | 二十四节气 | 节气 | 渊海子平卷之一·论节候歌 |
| 69 | `term:SourceNote` | 出处标注 | 出处 | 本应用设计文档·出处标注域 S3 |
| 70 | `term:SovereignPattern` | 君象臣象母象子象 | 君臣 | 滴天髓·君象/臣象/母象/子象 |
| 71 | `term:SpecialFortunePattern` | 特殊岁运格局 | 岁运格局 | 本应用设计文档·岁运域 |
| 72 | `term:SpecialPattern` | 外格杂格 | 外格 | 子平真诠·论外格 |
| 73 | `term:SpecialPattern` | 外格杂格 | 杂格 | 子平真诠·论杂格 |
| 74 | `term:StemCombination` | 天干五合 | 五合 | 三命通会卷二·论天干合 |
| 75 | `term:StemCombination` | 天干五合 | 天干相合 | 三命通会卷二·论天干合 |
| 76 | `term:SubAnnualFortune` | 流月流日流时 | 流日 | 三命通会卷二·论流日 |
| 77 | `term:SubAnnualFortune` | 流月流日流时 | 流月 | 三命通会卷二·论流月 |
| 78 | `term:Temperament` | 性情相貌才德 | 性情 | 三命通会卷二·论性情 |
| 79 | `term:Tiaohou` | 调候用神 | 调候 | 《穷通宝鉴》（《造化元钥》·徐乐吾评注）·五行总论及各干逐月取用 |
| 80 | `term:TiaohouClause` | 调候逐月条文（十天干生十二月） | 调候逐月 | 穷通宝鉴·各干逐月取用 |
| 81 | `term:ToBeVerified` | 待考标记 | 待考 | 公共铁律·无出处即标待考（★8/10） |
| 82 | `term:TombRelation` | 墓库刑冲 | 入墓 | 三命通会卷二·论墓库 |
| 83 | `term:TombRelation` | 墓库刑冲 | 墓库 | 三命通会卷二·论墓库 |
| 84 | `term:TripleCombination` | 三合（半合） | 三合 | 三命通会卷二·论三合 |
| 85 | `term:TripleCombination` | 三合（半合） | 三合局 | 三命通会卷二·论三合 |
| 86 | `term:TripleCombination` | 三合（半合） | 半合 | 三命通会卷二·论三合 |
| 87 | `term:Vitality` | 旺衰 | 强弱 | 三命通会卷二·论旺衰 |
| 88 | `term:Vitality` | 旺衰 | 旺弱 | 三命通会卷二·论旺衰 |
| 89 | `term:Void` | 空亡 | 旬空 | 三命通会卷二·论空亡 |
| 90 | `term:Void` | 空亡 | 空 | 三命通会卷二·论空亡 |
| 91 | `term:YinYang` | 阴阳 | 阴阳二气 | 三命通会卷一·论阴阳 |
| 92 | `term:YongShen` | 用神（相神/忌神） | 喜用神 | 子平真诠·论用神 |
| 93 | `term:YunHuaQi` | 运化气（化气十段锦） | 化气 | 三命通会卷二·论化气 |
| 94 | `term:ZhenYuan` | 贞元（元运循环） | 贞元 | 滴天髓·贞元 |
| 95 | `term:alertType` | 告警类型（大凶/寿元关/重大疾病） | 告警类型 | 本应用设计文档·告警标志域 S5 |
| 96 | `term:generates` | 生 | 相生 | 三命通会卷一·论五行生克 |
| 97 | `term:overcomes` | 克 | 相克 | 三命通会卷一·论五行生克 |

## 5. 交叉校验设计（第 14 步执行）

| 校验项 | 方法 | 通过判据 |
|---|---|---|
| 结构校验 | 纯 Jena 解析 TTL | 每个 `skos:Concept` 有 `prefLabel`；`broader` 指向存在概念 |
| 本体交叉 | 概念 ↔ OWL IRI 对齐 | 清单 §3 每条 IRI 在本体中存在；无孤立词条 |
| 无造词 | 词汇 ⊆ 本体术语 | 不存在无对应本体 IRI 的概念 |
| 别名溯源 | `altLabel` 均有来源注释 | 每条 `altLabel` 可回溯至六书/文档/业务约定 |

**第 13 步自检结果**（离线，`HWCodeArtsDir/step13_check/verify_terminology.py`）：概念 311、具 `exactMatch` 311、`exactMatch` 目标全部命中本体、`broader` 目标全部为概念、`@en` `prefLabel` 311 条、`altLabel` 97 条且均带「# 源:」注释。

**第 14 步校验结果**（测试类 `validation/SiZhuBaZiSkosTerminologyTest`，纯 Jena 离线）：P-1 概念方案 / P-2 `@zh` 首选标签 / P-3 `@en` 首选标签 / B-1 `broader` 健全 / X-1 `exactMatch` 命中本体 / X-2 本体带标签实体全覆盖（无遗漏、无造词）/ S-1 同义词合法（中文、不与首选标签重复）/ S-2 同义词均可溯源，**共 8 项全部通过**；`AllSiZhuBaZiSuiteTest` **全绿**（本体合规 8/8 + SKOS 8/8；BPMN/RabbitMQ 集成依环境跳过）。

## 6. 待确认项（业务已裁定）

| # | 问题 | 业务结论 |
|---|---|---|
| 1 | 部分别名出处按六书通行卷篇标注（如「七杀/七煞」「枭神/倒食」），是否接受该卷篇级溯源粒度？ | ✅ **接受**「六书卷篇级」溯源粒度（2026-10-29 业务裁定） |
| 2 | `yongshen:Tiaohou`（调候用神）别名「调候」出处《穷通宝鉴》**尚未取得文本**，暂标【待考】；取得后回填？ | ✅ **已回填**：业务提供《穷通宝鉴》文本（《造化元钥》徐乐吾评注），已归档 `design/六书原文/穷通宝鉴（清·余春台辑·徐乐吾评注）.pdf` 与 `design/六书全文整理/穷通宝鉴.txt`；别名「调候」出处回填为「《穷通宝鉴》（《造化元钥》）·五行总论及各干逐月取用」 |
| 3 | 是否需要为全部概念补充 `@en` 英文 `prefLabel`（参考 Example Pizza 有 @en；PlumBlossom 仅 @zh）？ | ✅ **补充**：已为全部 **311** 个概念添加 `@en` `prefLabel`（2026-10-29 落实） |
| 4 | 同名对象属性加「（关系）」后缀的消歧口径是否认可？ | ✅ **认可**（2026-10-29 业务裁定） |

---

## 闸门⑥留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-29 | v1.1（含 `@en`） | 业务（四柱命理领域专家） | 就 §6 四项作出裁定：①接受卷篇级溯源；③为全部概念补 `@en`（已落实）；④认可「（关系）」消歧口径。②要求引入《穷通宝鉴》，因文本缺失暂缓，维持【待考】待回填。 |
| 2026-10-30 | v1.2（回填《穷通宝鉴》） | 业务（四柱命理领域专家） | 提供《穷通宝鉴》文本，已归档；`term:Tiaohou` 别名「调候」出处由【待考】回填为「《穷通宝鉴》（《造化元钥》）·五行总论及各干逐月取用」。**闸门⑥ 最终通过，进入第 14 步。** |
