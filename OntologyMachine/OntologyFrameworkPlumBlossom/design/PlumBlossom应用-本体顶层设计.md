# 梅花易数（PlumBlossom）应用 — 本体顶层设计

> **用法**：本文件产出第 3 步（本体顶层设计：设计理念、范围边界、资产组织、命名空间/import、顶层类框架）。**闸门②**：业务人员确认本体顶层设计后方可进入第 4 步（本体核心类/属性/关系设计）。
> **基线**：`OntologyFrameworkExample/design/Example应用-本体设计.md`（理念 / 资产组织 / 命名空间章节）。

| 项目 | 内容 |
|---|---|
| 应用 | PlumBlossom（梅花易数占断） |
| 文档日期 | 2026-10-20 |
| 当前版本 | **v3**（在 v2 基础上，按要求补入「流程运转支撑内容」，并修正闸门②留痕） |
| 关联顶层设计 | 《PlumBlossom应用-顶层设计》（闸门⓪已确认） |
| 关联流程 | 《PlumBlossom应用-流程设计》（闸门①已确认；**v11 定稿**） |
| 本体资产（规划） | `ontology/plumblossom-all.owl`、后续 `plumblossom-*.owl`、`catalog-v001.xml` |
| 推理器 | Openllet（经 OpenlletResolver） |
| 虚拟数据 | Ontop OBDA → MySQL（历法/干支数据） |

> 依据（**强制**）：本步骤每条设计决策须标注业务/资料出处（★铁律 10/12）；自节点 2 起的方法与数据须追溯至原著。原著 ＝ `design/故宫珍藏善本：梅花易数 ([宋] 邵康节 著 周浩良 整理).epub`（卷之一 象数易理 / 卷之二 体用生克 / 卷之三 断占总诀 / 卷之四 字画指迷 / 卷之五 拆字杂编 / 附录 邵康节易数一撮金·渔樵对问·宋史·邵雍传）；辅助依据 ＝ `design/梅花易数全面指南.md`。

> **v3 修订说明（本步关键）**：v2（及先前误置于第 4 步的内容）遗漏了**流程运转所必需的支撑内容**。以邵康节视角对第 2 步流程设计逐节点复核后，本版将下列支撑体系**上提到第 3 步顶层设计**（此前只在第 4 步核心设计里出现过，属越位）：
> 1. **周易经传**——《周易》六十四卦**卦辞**与三百八十四爻**爻辞**（节点 10「观其爻辞」、节点 12「先看《周易》爻辞」、卦断遗论「不拘体用」引《易》辞）；
> 2. **八卦全属性**——方位（八方位）、阴阳次序（乾坤六子）、性情（健顺起止陷丽说入）（节点 2/3/4/9/16）；
> 3. **历法与干支**——十天干/十二地支/六十甲子/纳音/五行长生（节点 3/8/13）；
> 4. **四不占戒律**（节点 1）；
> 5. **诸卦反对性情**（卦义，节点 10/16）。
> 同时**修正闸门②留痕**：第 3 步此前被误记为「已定稿并准予进入第 4 步」，现更正为**仍在办（待闸门②确认）**。

---

## 1. 概述与设计理念

PlumBlossom 的本体是应用的**业务真相来源**：用 OWL 表达「八卦体系 → 类象/五行 → 体用生克 → 吉凶/应期」的约束与关系，由 Openllet 在运行时推理判定，程序**只查询不判断**、不硬编码业务规则。

**核心设计理念（五条）**：

- **本体即约束**：八卦类象、五行生克、体用关系、六十四卦、旺衰等业务约束由 TBox 定义；断占逻辑尽量下沉为类约束（`owl:Restriction`）与 SWRL 规则，而非 Java 分支。
- **节点概念与运转支撑并重**：本体不仅表达「流程每个节点自身所需」的概念（起卦/卦象/体用/断验），还须表达**流程运转所依赖的支撑体系**（周易经传、八卦全属性、历法干支、戒律）——缺之则推理与断验无法完成（详见 §2.1(B)）。
- **TBox/ABox 分层**：术语定义（类、属性、约束、规则）与实例断言（个体）分层组织——TBox 稳定，ABox 可变（起卦实例/占断实例）。
- **数据虚拟化**：历法数据（农历年月日时、干支序数、季节等）留在 MySQL，经 Ontop OBDA 映射以 SPARQL 统一访问，不迁入本体文件。
- **规则驱动事件**：SWRL 规则在推理时自动推导「重大吉凶」个体，触发 RabbitMQ 告警外发（对应流程节点 15）。

> 出处：`《PlumBlossom应用-顶层设计》`§1「核心特征」（本体即约束 / BPMN 管编排 / Ontop 虚拟取数 / 前端可交互 / 消息外发）；`Example应用-本体设计.md`§1 设计理念。

---

## 2. 设计范围与边界

### 2.1(A) 本体要表达的业务概念域（流程节点对齐，逐条）

| # | 概念域 | 表达内容 | 对齐流程节点 | 出处 |
|---|---|---|---|---|
| 1 | **起卦法与起卦输入** | **17 类起卦法**（卷之一十六类：先天以数 6 类／后天以象 10 类 + 附录·一撮金法）及其输入要素 | 节点 2、2-字占 | 原著卷之一·起卦诸篇；附录·邵康节易数一撮金 |
| 2 | **卦象体系** | 八卦（8）、爻（阴/阳/动爻）、重卦（本卦/互卦/变卦、六十四卦） | 节点 3、4、5、6 | 原著卷之一·卦数起例/爻以六除/互卦止用八卦 |
| 3 | **体用关系** | 体卦/用卦/体用配置、体用宜忌 | 节点 4 | 原著卷之二·体用总诀；卷之三·体用互变之诀 |
| 4 | **五行与生克** | 五行配属、五行生克、体用生克 5 级吉凶倾向、真生真克 | 节点 7 | 原著卷之一·八宫所属五行/五行生克；卷之二·体用总诀；卷之三·体用生克之诀 |
| 5 | **卦气旺衰** | 旺/相/休/囚/死、季节与卦五行 | 节点 8 | 原著卷之一·卦气旺衰；卷之二·衰旺论；卷之三·体用衰旺之诀 |
| 6 | **万物类象** | 八卦万物属类、八卦万物类占（逐卦分野）、观物/拆字取象 | 节点 9 | 原著卷之一·八卦万物属类/八卦万物类占/八卦所属内外动静之图；卷之三·观物诸诀；卷之四/卷之五 |
| 7 | **分类占断（事项类别）** | 十八章占例事项类别及其分项断法 | 节点 10 | 原著卷之二十八章占例；卷之三·诸事响应歌/诸卦反对性情 |
| 8 | **外应** | 三要（耳/目/心）、十应（含两套枚举）及其断法 | 节点 11 | 原著卷之二·三要灵应篇/十应目论；卷之三·占卜十应诀 |
| 9 | **断验结论** | 吉凶等级（大吉/吉/平/凶/大凶）、过程描述、以理统数；含**一撮金爻诀六等**（上上/上中/中中/中平/下中/下下）及**六等→五级映射** | 节点 12 | 原著卷之二·占卜总诀/占卜论理诀；附录·一撮金（爻诀） |
| 10 | **应期** | 应期类型/范围、克应刻期、卦气定吉凶期 | 节点 13 | 原著卷之三·克应/断占总诀；卷之二·先天后天论 |
| 11 | **修身建议** | 趋避劝诫（吉则戒骄、凶则积德）——**应用层增益** | 节点 14 | 原著卷之二·观物洞玄歌/心易占卜玄机（立意）；本条为应用层增益，原著无专章 |

### 2.1(B) 流程运转支撑概念域（★v3 新增，逐条，均为流程推理/断验所必需）

> 说明：下列概念**不对应某个节点自身**，而是**多个节点运转时所共同依赖的支撑体系**；缺之则起卦、取象、断验无法完成。故一并纳入本体顶层范围。

| # | 支撑概念域 | 表达内容 | 为何流程运转所必需（对应节点） | 出处 |
|---|---|---|---|---|
| S1 | **心法戒律（四不占）** | 不诚不占 / 无事不占 / 不义不占 / 重卦不占 | 节点 1 心法戒律校验（流程入口前置校验） | 原著卷之一·观梅占「不动不占，不因事不占」推四不占；附录·一撮金「诚心祈祷、不可再占」 |
| S2 | **周易经传（《周易》卦辞/爻辞）** | 六十四卦**卦辞**、三百八十四爻**爻辞**（经文，外部经典） | 节点 10 占物类例「观其爻辞」；节点 12「成卦之后先看《周易》爻辞，以断吉凶」、卦断遗论「不拘体用」引《易》辞 | 原著卷之二·占卜总诀「成卦之后先看《周易》爻辞」；卷之二·先天后天论「后天则用爻辞，兼用卦辞」；卷之三·占物类例「凡看物数，看其成卦，观其爻辞」 |
| S3 | **八卦全属性（方位 / 阴阳次序 / 性情）** | 八卦**方位**（八方位）、**阴阳次序**（乾坤六子：父/长男/中男/少男；母/长女/中女/少女）、**性情**（健/顺/起/止/陷/丽/说/入） | 节点 2 配「八卦方位图」定端法后天方位；节点 3 端法后天「方位为下卦」；节点 4「八卦定阴阳次序」定人物长幼男女；节点 9/16 卦义性情 | 原著卷之一·八卦方位图「离南坎北，震东兑西」（余四方巽东南/乾西北/艮东北/坤西南）；卷之三·八卦定阴阳次序；卷之五·论八卦性情 |
| S4 | **历法与干支** | 十天干、十二地支、六十甲子、纳音、五行长生、季节月令 | 节点 3 成卦「取爻当以时加之」；节点 8 卦气旺衰（干支/月令定五季）；节点 13 定应期（纳音/长生/卦气之日） | 原著卷之一·十天干/十二地支；卷之五·天干地支所属五行/五行相生地支「木生在亥…」/六十甲子纳音歌 |
| S5 | **《周易》卦象符与卦序** | 八卦取象符（乾为天…兑为泽）、六十四卦**卦序次第** | 节点 3 成卦、节点 16 前端展示卦象；六十四卦名录定序 | 原著卷之五·八卦取象/六十四卦次第歌；附录·一撮金「八卦诀」 |
| S6 | **诸卦反对性情（卦义）** | 杂卦两两反对之性情（乾刚坤柔/比欣师忧/升去萃聚…） | 节点 10 分类占断卦义、节点 16 汇总「卦义性情」 | 原著卷之三·诸卦反对性情「杂卦性情反对是」 |

> **关于「周易的卦象和爻」（用户点问 · v3 结论）**：
> - **周易之「卦象」（六十四卦卦画）**：**已由既有卦象域承载**（`Hexagram`/`SixBitHexagram`），非新增。
> - **周易之「爻」（爻位结构）**：**已由既有卦象域承载**（`Line`：阴/阳/动爻），非新增。
> - **周易之「爻辞/卦辞」（经文）**：**须新增**（S2）——它是断验的**核心判据**（节点 12「先看《周易》爻辞」、节点 10「观其爻辞」），此前顶层设计遗漏，本版补入。
> - **引用条件**（对齐流程§7-8、§7-12）：先天体系「止以卦论、不甚用爻辞」**默认不引**；后天体系与「不拘体用」处**可引卦辞/爻辞辅助**；附录·一撮金法**恒引爻诀**。故本体将「《周易》经传」与「一撮金爻诀」**分属两类**（`yijing:HexagramDictum/LineDictum` vs `jud:YicoJinAdmonition`），来源与引用条件不同，**不得混为一类**。

### 2.2 不在本体范围（逐条）

| # | 不建模项 | 理由 | 出处 |
|---|---|---|---|
| 1 | **六爻纳甲 / 奇门遁甲 / 六壬课 / 风水 / 八字** | 其他术数体系，非梅花易数 | 《顶层设计》§2.2 |
| 2 | **多动爻扩展** | 梅花易数一卦一爻动，根本区别于六爻法 | 原著卷之一·爻以六除；《顶层设计》§2.2-6 |
| 3 | **错卦/综卦独立推演** | 仅作辅参，不做独立推演环节；**注**：S6「诸卦反对性情」作为**卦义属性**（卦与卦的反对关系）引入，用于断验描述，**不构成独立推演环节** | 指南§四·5 |
| 4 | **「心易」修行境界** | 非可算法化规则，仅在 UI 提示 | 指南§二·1 |
| 5 | **占断历史库** | 本期只做单次占断 | 《顶层设计》§2.2-8 |
| 6 | **分秒级应期** | 应期输出「**级别 + 范围**」（远年/近月/又近日，依动静调整），不做单点精确时刻 | 《顶层设计》§2.2-7；流程§7-9 |
| 7 | **流程编排本身** | 顺序/网关属 BPMN，本体不含流程拓扑 | `Example应用-本体设计.md`§5 同名原则 |
| 8 | **前端交互与消息通道配置** | 属应用架构（第 7 步），本体仅建模「重大吉凶」概念供规则触发 | 顶层设计§4 |
| 9 | **《周易》义理训诂/象数注疏体系** | 本体仅**引用卦辞/爻辞文本**作断验判据，不建模历代注疏、义理阐发 | 流程§7-8（仅「引用卦辞爻辞」） |

> **边界原则**：本体只表达「业务概念及其约束/关系」；**流程拓扑归 BPMN、运行态历法归 MySQL、交互归前端、《周易》义理归外部**——四者均不写死进本体。

---

## 3. 本体资产组织（规划）

```
ontology/
├── plumblossom-all.owl              ← 第 9 步：TBox 聚合入口（imports tbox；ABox imports 已注释。第 11 步起 ABox 经 OBDA 的 ontop.ontologyFile 加载）
│
├── [TBox — 术语定义；第 10 步按业务域拆为九子本体 + 聚合入口]
│   ├── plumblossom-tbox.owl         ← 第 10 步：TBox 聚合入口（显式 imports 九子本体）
│   ├── plumblossom-core.owl         ← 卦象：八卦 / 方位 / 爻 / 重卦（本卦·互卦·变卦）
│   ├── plumblossom-divination.owl   ← 起卦法类别 / 起卦输入要素 / 四不占戒律
│   ├── plumblossom-yijing.owl       ← ★v3 周易经传：卦辞 / 爻辞 / 卦序 / 八卦取象 / 诸卦反对性情
│   ├── plumblossom-relations.owl    ← 五行 / 生克 / 体用关系 / 卦气旺衰 / 八卦性情 / 阴阳次序
│   ├── plumblossom-symbols.owl      ← 万物类象（八卦属类 / 类占 / 观物 / 拆字）
│   ├── plumblossom-omens.owl        ← 外应：三要 / 十应
│   ├── plumblossom-judgement.owl    ← 事项类别 / 吉凶等级 / 应期 / 修身建议（含一撮金爻诀）
│   ├── plumblossom-calendars.owl    ← 历法：天干 / 地支 / 六十甲子 / 纳音 / 长生 / 季节
│   └── plumblossom-rules.owl        ← SWRL 规则 R1~R14
│
├── [ABox — 实例断言，第 9 步独立为单文件]
│   └── plumblossom-abox.owl         ← 1133 个体（八卦/爻/六十四卦名录（含 384 爻）/四不占/吉凶类别/
│                                       事项类别/类象全集/外应/反对等；imports tbox。历法/周易文本依第 11 步闸门⑩
│                                       移入 MySQL（第 12 步扩全集），见 ontology/database/）
│
├── [术语]
│   └── plumblossom-terminology.ttl  ← 第 13 步：SKOS 术语表（ConceptScheme 根 .../term；150 规范概念 + 57 同义词/别名；
│                                      解决「不同名同义、同义不同名」；经 tbox 的 owl:imports 并入，供 SkosSynonymReader/OntologyLabelMatcher）
│
├── [解析目录]
│   └── catalog-v001.xml             ← IRI → 本地文件映射
│
└── [数据库，第 11 步]
    ├── createdb_tables.ddl
    ├── data.sql
    ├── plumBlossom.obda
    └── plumBlossom.properties
```

> **落位说明**：`ontology/` 与模块内 `src/`、`design/` 并列（本模块自持本体文件，不跨模块复用）。
> **v3 变更**：新增 `plumblossom-yijing.owl`（周易经传）；`divination` 增「四不占戒律」；`relations` 增「八卦性情/阴阳次序」；`core` 增「方位」。
> **第 9/10 步落位实况**：第 9 步将单文件本体拆为 `plumblossom-tbox.owl` + `plumblossom-abox.owl`（含 `plumblossom-all.owl` 聚合入口）；第 10 步将 TBox 按业务域细拆为上列**九子本体**，`plumblossom-tbox.owl` 改为聚合入口。**ABox 落地为单文件**（早先规划的 4 个 `*-abox.owl` 合并为 `plumblossom-abox.owl`，与闸门⑧「分离为 tbox/abox 两文件」一致）。**第 11 步（闸门⑩）**：历法 38 + 周易文本 78 共 **116 个体入库**（MySQL `plumblossomdb`，`ontology/database/{createdb_tables.ddl,data.sql,plumBlossom.obda,plumBlossom.properties}`），ABox 由 247 减至 **129 个体**（DB 为唯一真源）；ABox 改由 Ontop 经 `ontop.ontologyFile` 加载。
> **第 12 步（闸门⑪）**：ABox 由 129 扩至 **1133 个体**（六十四卦名录 64 + 爻 384 + 类象全集 559 + 维度 27 等）；`data.sql` 扩至历法 116 + 周易文本 512。
> **第 13 步（闸门⑥）接线**：新增 `plumblossom-terminology.ttl`（SKOS，150 概念 + 57 同义词），并在 `plumblossom-tbox.owl` 增 `<owl:imports rdf:resource="http://example.org/plumblossom/term"/>`、`catalog-v001.xml` 增 `.../term → plumblossom-terminology.ttl` 映射（此为经客户确认的**接线改动**）。并因框架 `AutoIRIMapper` 不解析 `.ttl`，运行/测试加载须为 `.ttl` 补注册 `SimpleIRIMapper`（框架 `OntologyService` 已内建）。SKOS 命名空间的 `skos:Concept` 不计入 OWL 个体统计。

---

## 4. 命名空间与 import 架构

### 4.1 命名空间

| 模块 | 本体 IRI | 角色 |
|---|---|---|
| core | `http://example.org/plumblossom/core` | TBox：卦象类体系（八卦/方位/爻/重卦） |
| divination | `http://example.org/plumblossom/divination` | TBox：起卦法与起卦输入、四不占戒律 |
| **yijing** | `http://example.org/plumblossom/yijing` | **★v3 TBox：周易经传（卦辞/爻辞/卦序/八卦取象/诸卦反对性情）** |
| relations | `http://example.org/plumblossom/relations` | TBox：五行/生克/体用/旺衰/八卦性情/阴阳次序 |
| symbols | `http://example.org/plumblossom/symbols` | TBox：万物类象 |
| omens | `http://example.org/plumblossom/omens` | TBox：三要/十应 |
| judgement | `http://example.org/plumblossom/judgement` | TBox：事项类别/吉凶/应期/修身/一撮金爻诀 |
| rules | `http://example.org/plumblossom/rules` | TBox：SWRL 规则 |
| calendars | `http://example.org/plumblossom/calendars` | TBox：历法/干支/六十甲子/纳音/长生 |
| tbox | `http://example.org/plumblossom/tbox` | TBox 聚合入口（imports 九子本体） |
| term | `http://example.org/plumblossom/term` | SKOS 术语 |
| abox | `http://example.org/plumblossom/abox` | ABox：保留 **1133** 个体（八卦/爻/六十四卦名录（含 384 爻）/四不占/吉凶类别/事项类别/类象全集/外应/反对等；历法/周易文本第 11 步入库，第 12 步扩全集） |
| all | `http://example.org/plumblossom/all` | 总入口（TBox 聚合入口；ABox imports 已注释） |

### 4.2 import 拓扑（第 10 步落地）

```
plumblossom-all   (总入口, ontology.main-path)
  └── imports tbox            (plumblossom-tbox.owl)   ← TBox 聚合入口
        ├── imports core            (plumblossom-core.owl)        → imports (none)          [最底层]
        ├── imports divination      (plumblossom-divination.owl)  → imports core
        ├── imports yijing          (plumblossom-yijing.owl)      → imports core
        ├── imports symbols         (plumblossom-symbols.owl)     → imports core
        ├── imports relations       (plumblossom-relations.owl)   → imports core, symbols
        ├── imports omens           (plumblossom-omens.owl)       → imports core, symbols
        ├── imports judgement       (plumblossom-judgement.owl)   → imports relations, symbols, omens, yijing
        ├── imports calendars       (plumblossom-calendars.owl)   → imports (none)
        └── imports rules           (plumblossom-rules.owl)       → imports relations, symbols, judgement  [最上层]

(ABox, 经 OBDA 加载)  plumblossom-abox (ontop.ontologyFile) → imports tbox
```

> 运行加载入口为 `ontology/plumblossom-all.owl`（`application.yaml` 的 `ontology.main-path`）；ABox 聚合入口 `plumblossom-abox.owl`（`ontology.abox-path`）**第 11 步起**由 Ontop 经 `plumBlossom.properties` 的 `ontop.ontologyFile` 加载（虚拟知识图谱）——ABox 个体仍**不进 TBox 推理器**（铁律 11 的独立加载精神不变）。
> 依赖方向自上而下**单向、无环**：core 为最底层被依赖者，judgement/rules 为最上层整合者。
> **语义等价**：九子本体内容并集与原单文件 TBox **图同构**（图同构校验 = True，公理逐项一致）。

---

## 5. 顶层类层级框架

> 本步只勾出**主干框架**（根类与主要分支），明细类/属性/约束留待第 4 步。标注 `★` 者为 v3 增补的**流程运转支撑分支**。

```
PlumBlossomEntity（本体事物根）
│
├── DivinationInput（起卦域）
│   ├── DivinationMethod（起卦法类别）——17 子类
│   │     ├── [先天·以数] NumberBasedMethod
│   │     │     ├── YearMonthDayHourMethod（年月日时起例）
│   │     │     ├── ObjectCountMethod（物占数）
│   │     │     ├── SoundCountMethod（声音占）
│   │     │     ├── CharacterMethod（字占）
│   │     │     ├── ZhangChiMethod（丈尺占）
│   │     │     ├── ChiCunMethod（尺寸物占）
│   │     │     └── YicuoJinMethod（一撮金法·二字笔画起卦：上字内卦/下字外卦）
│   │     └── [后天·端法·以象] ImageBasedMethod
│   │           ├── PersonObserveMethod（为人占）、SelfObserveMethod（自己占）
│   │           ├── MovingThingMethod（占动物）、StillThingMethod（占静物）
│   │           ├── ThingHexagramMethod（物卦起例·端法后天）
│   │           ├── WindMethod（风觉占）、BirdMethod（鸟占）
│   │           ├── HeardSoundMethod（听声音占）
│   │           └── ShapeMethod（形物占）、ColorMethod（验色占）
│   ├── ★Precept（心法戒律·四不占）——不诚/无事/不义/重卦（4 个体）【S1】
│   └── InputDatum（起卦输入数据）——时间/数字/文字/物象/方位/外应等
│
├── DivinationSubject（占断事项域）
│   └── SubjectCategory（事项类别）——18 子类
│         （天时/人事/家宅/屋舍/婚姻/生产/饮食/求谋/求名/求财/交易/出行/
│           行人/谒见/失物/疾病/官讼/坟墓）
│
├── HexagramDomain（卦象域）
│   ├── Trigram（八卦）——乾/兑/离/震/巽/坎/艮/坤（8 子类）
│   │     └── ★属性关联：方位/阴阳·长幼次序/性情（见 YiliDomain 与 YijingDomain）【S3】
│   ├── ★Direction（八方）——东/南/西/北/东南/西南/东北/西北（8 子类）【S3】
│   ├── Line（爻）——YinLine（阴）/YangLine（阳）/MovingLine（动爻）
│   └── Hexagram（重卦，由上下卦组成）
│         ├── OriginalHexagram（本卦）、MutualHexagram（互卦）、ChangedHexagram（变卦）
│         └── SixBitHexagram（六十四卦名录，术语型）
│
├── ★YijingDomain（周易经传域）【S2/S5/S6】
│   ├── HexagramDictum（周易卦辞）——六十四卦各一（术语型）
│   ├── LineDictum（周易爻辞）——三百八十四爻各一（术语型）
│   ├── HexagramSequence（卦序/卦象符）——六十四卦次第 + 八卦取象符
│   └── OppositeNature（诸卦反对性情）——杂卦两两反对之卦义
│
├── YiliDomain（易理域）
│   ├── FivePhase（五行）——金/木/水/火/土（5 子类）
│   ├── ShengKeRelation（五行生克）——生/克（含真生真克）
│   ├── BodyUseRelation（体用关系：体卦·用卦·体用配置）
│   ├── VitalityState（卦气旺衰）——旺/相/休/囚/死（5 子类）
│   ├── ★TrigramNature（八卦性情）——健/顺/起/止/陷/丽/说/入（8 值）【S3】
│   ├── ★TrigramOrder（八卦定阴阳次序）——阴阳 + 长幼男女（8 个体）【S3】
│   └── ImageSymbol（万物类象）——按八卦、按类象维度（27 栏：天时/地理/人物/人事/身体/时序/动物/静物/屋舍/家宅/饮食/婚姻/生产/求名/谋望/交易/求利/出行/谒见/疾病/官讼/坟墓/方道/五色/姓字/数目/五味）组织
│
├── OmenDomain（外应域）
│   ├── ThreeEssentials（三要：耳/目/心）
│   └── TenResponses（十应）——正/互/变/方/日/刻/外/天时/地理/人事（10 子类）
│
├── ★CalendarDomain（历法域）【S4】
│   ├── HeavenlyStem（十天干）——甲…癸（10 子类）
│   ├── EarthlyBranch（十二地支）——子…亥（12 子类）
│   ├── SexagenaryCycle（六十甲子）
│   ├── NaYin（纳音）——60 组（含 30 名）
│   ├── LifeStage（五行长生）——木生在亥/火生于寅/金生于巳/水土长生居申
│   └── CalendarDatum（历法数据：干支/季节/月令）
│
└── JudgementDomain（断验域）
    ├── Auspiciousness（吉凶等级）——大吉/吉/平/凶/大凶（5 子类；附录一撮金六等经映射并入：上上→大吉／上中→吉／中中·中平→平／下中→凶／下下→大凶）
    ├── ★YicuoJinAdmonition（一撮金爻诀断辞）——附录逐爻断辞（恒引用；与周易爻辞分属两类）
    ├── ResponsePeriod（应期）——年/月/日/时（应期类型与范围）
    └── SelfCultivationAdvice（修身建议）
```

> 说明：`Trigram` 与 `FivePhase`/`ImageSymbol`/`Direction`/`TrigramNature`/`TrigramOrder` 的关联（八卦配五行、配类象、配方位、配性情、配阴阳长幼）、`Hexagram` 与 `BodyUseRelation` 的关联（体用判定）、`SixBitHexagram` 与 `HexagramDictum`/`LineDictum` 的关联（卦辞/爻辞绑定，先天不引/后天引）、`Auspiciousness` 的来源（体用生克/旺衰/外应综合）等**关系与约束细节留待第 4 步**。

---

## 6. TBox/ABox 分层策略

| 层 | 内容 | 落点 |
|---|---|---|
| TBox | 类/属性/约束/SWRL（八卦体系、类象、五行生克、体用、旺衰、吉凶判据、应期规则、周易经传绑定、历法干支、戒律） | 第 6 步单文件；**第 9 步拆出 `plumblossom-tbox.owl`**；**第 10 步（闸门⑨）按业务域细拆为九子本体** `core/div/yijing/rel/sym/ome/jud/cal/rules`（并集内容与原 TBox **图同构**；合计 90 类 / 64 对象属性 / 41 数据属性 / 39 约束 / SWRL R1~R14），`plumblossom-tbox.owl` 改为**TBox 聚合入口**（显式 `imports` 九子本体，单向无环） |
| ABox | 个体断言（八卦/爻定义实例、**周易卦辞/爻辞/卦序实例**、类象实例、事项类别实例、典型起卦-占断实例） | 第 6 步含典型数据；**第 9 步独立为 `plumblossom-abox.owl`**（247 个体，`imports` `plumblossom-tbox.owl`）；主入口对其 `imports` 依**铁律 11** 注释掉。**第 11 步（闸门⑩）**：历法 38 + 周易文本 78 共 **116 个体入库**（MySQL），ABox 由 247 减至 **129 个体**（DB 为唯一真源），改由 OBDA 经 `ontop.ontologyFile` 加载。**第 12 步（闸门⑪）**：ABox 由 129 扩至 **1133 个体**（六十四卦名录扩至 64、含 384 爻；类象全集 27 栏共 559 项）；DB 侧 `data.sql` 扩至历法 116 + 周易文本 512 |

> **两套实例来源**（照 Example 双轨）：
> | 来源 | 用途 | 个体命名约定 |
> |---|---|---|
> | 静态 ABox（`.owl`） | 本体推理、一致性校验、SKOS 交叉校验 | `QianTrigram`、`Hexagram_01`（重卦名录）、`Dictum_Hex_01`（乾卦辞）、`Symbol_Qian_TianShi_01`（类象）等 |
> | 运行态 MySQL（经 OBDA） | 历法/干支数据读写、旺衰与应期计算取值 | `PB_<timestamp>` 等 |
>
> **闸门⑧/⑨ 留痕**：第 9 步执行前须确认**是否做 TBox/ABox 分离**（闸门⑧）；第 10 步前须确认**是否做 TBox 拆分及粒度**（闸门⑨）。两项决策记录于文末「闸门⑧/⑨留痕」。
> **说明**：周易卦辞/爻辞规模较大（64 + 384），其 ABox 归属（随 `yijing-abox` 独立 / 并入 core-abox）与是否入库，列入第 9/11 步议题。

---

## 7. 待业务确认项

| # | 问题 | 说明 | 业务结论（**待确认**·2026-10-20） |
|---|---|---|---|
| 1 | 八卦/六十四卦是否建模为 ABox 个体？ | 卦名与卦象属**固定术语**，倾向作为术语型个体（ABox）或枚举类；决定 TBox 组织方式 | 建议**作术语型 ABox 个体**（八卦/六十四卦/爻），TBox 定义其类；照 Example 双轨，便于 SKOS 交叉校验 |
| 2 | 万物类象的建模粒度？ | 「八卦万物类占」含 27 分栏，是建模为「类象个体 + 维度属性」还是仅分层类？ | **已确认（第 12 步闸门⑪，邵康节）**：**类象个体 + 维度属性**，纳入「八卦万物类占」**全表 27 栏**（新增 19 个 `sym:SymbolDimension` 个体），共 559 项 `sym:ImageSymbol`；现代映射扩展层仍默认关闭（见第 4 步 §7） |
| 3 | 历法/干支是否需本体概念？ | 旺衰判定依赖季节，应期依赖干支日/纳音/长生 | 建议**保留 `calendars` 子本体**（天干/地支/六十甲子/纳音/长生/季节类）；数据经 Ontop OBDA 虚拟化，本体仅存概念占位 |
| 4 | 事项类别（18 章）是否全部建模？ | 与流程设计 §7-6 一致；本期是否全建 18 类还是先建高频子集？ | 建议**全建 18 类**（对齐流程§7-6：用户下拉选填为主 + 其他/自定义） |
| 5 | 「重大吉凶」判定阈值归本体还是程序？ | 影响 SWRL 规则是否建模该阈值（照 Example 低库存由 SWRL 推导） | 建议**归本体 SWRL**（照 Example 低库存推导之例），阈值即「大吉/大凶」枚举个体 |
| 6 | 应期是否可推理？ | 应期涉及「卦气数 + 干支日」计算，属规则可覆盖还是需程序计算？ | 建议**规则 + 虚拟取数混合**：应期类型/范围由 SWRL 依卦气与动静推导，具体干支日经 OBDA 取值 |
| **7** | **是否引入《周易》卦辞/爻辞？（用户点问）** | 节点 12「先看《周易》爻辞」、节点 10「观其爻辞」、卦断遗论引《易》辞；是否纳入本体？以何粒度？ | 建议**引入**：单列 `yijing` 子本体（`HexagramDictum` 64 + `LineDictum` 384，术语型 ABox）；按流程§7-8 **先天不引、后天及不拘体用处引**；与一撮金爻诀分属两类 |
| **8** | **八卦全属性是否建模？（方位/阴阳长幼/性情）** | 端法后天定下卦依赖方位；八卦定阴阳次序定人物长幼；卦义性情入断验 | 建议**建模**：`Direction`（8 子类）+ 阴阳/长幼作 `Trigram` 属性 + `TrigramNature`（8 值） |
| **9** | **四不占戒律是否建模？** | 节点 1 戒律校验；「不诚/不义」是否可程序化 | 建议**建模 `Precept`（4 个体）**；「不诚/不义」依流程§7-1 由前端**显式承诺勾选**，后端仅记录 |
| **10** | **诸卦反对性情是否建模？** | 与「错卦/综卦不建模」边界如何协调 | 建议**作为卦义属性/反对关系（对称）引入**（`OppositeNature`），仅用于断验描述，**不构成独立推演环节**（对齐 §2.2-3） |

> 第 2 步流程设计 §7 的 **12 项待裁定已于 v11 全部确认**；本表据此给出**建议结论**，待闸门②一并确认或带入第 4 步。

---

## 闸门②留痕

| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-20 | v1（初稿） | — | 6 大概念域、8 个 TBox 子本体、顶层类框架初拟 |
| 2026-10-20 | v2（回填流程 v8~v11 修订） | — | 回填流程设计定稿（v11）：起卦法域 **16→17 子类**（新增 `YicuoJinMethod` 一撮金法）、§2.1 概念域同步、§5 吉凶等级注一撮金六等映射、§2.2-6 应期改「级别+范围」、修正 v1 留痕 |
| 2026-10-20 | ~~v2（定稿）~~（**作废**） | ~~邵康节~~ | ~~闸门②通过、准予进入第 4 步~~ ——**该留痕作废**：第 3 步此时实际尚未完成（流程运转支撑内容缺失），系统不应自行推进至第 4 步 |
| 2026-10-20 | **v3（补入流程运转支撑内容）** | — | 以邵康节视角逐节点复核后，补入支撑概念域 **S1~S6**（四不占戒律 / 周易经传卦辞爻辞 / 八卦全属性 / 历法干支 / 周易卦象符与卦序 / 诸卦反对性情）；顶层类框架新增 `YijingDomain`、`CalendarDomain`、`Direction`、`Precept`、`TrigramNature`、`TrigramOrder`、`YicoJinAdmonition` 等支撑分支；新增 `yijing` 命名空间与 `plumblossom-yijing.owl` 资产；§7 待确认项由 6 项增至 **10 项** |
| 2026-10-20 | **v3（定稿）** | 邵康节 | **闸门②通过**：认可 v3 本体顶层设计定稿（含支撑概念域 **S1~S6**、顶层类框架支撑分支、§7 **十项**待确认项），准予进入第 4 步（本体核心类/属性/关系设计） |

> **当前闸门②状态**：**已确认（v3 定稿）**。可进入第 4 步。

---

## 闸门⑧留痕（是否 TBox/ABox 分离 · 第 9 步执行前）

| 日期 | 是否分离 | 依据（个体规模/是否参与推理） | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-20 | **☑分离** | 典型 ABox **247 个体**（>200 触发阈值）；ABox 为查表/术语基线，不参与全局分类推理 | 邵康节 | **闸门⑧通过**：确认 TBox/ABox 分离为两文件。**落地口径（依铁律 11）**：①拆出 `plumblossom-tbox.owl` 与 `plumblossom-abox.owl`；②`plumblossom-all.owl`（聚合 TBox 入口）对 ABox 的 `owl:imports` **注释掉**；③`application.yaml` 新增 `ontology.abox-path: ontology/plumblossom-abox.owl`；④`PlumBlossomOntologyJobWorker` 复用 `OntologyService` **独立加载 ABox**，并在 `verifyOntologyKnowledge` 中据此取 `yijing:HexagramSequence` 个体互校。**ABox 按需独立加载（推理需要即加载），不为移除而牺牲合理推理设计**。`catalog-v001.xml` 已增 tbox/abox 映射。 |

> **第 9 步执行结果**：`plumblossom-tbox.owl`（2657 行）、`plumblossom-abox.owl`（1766 行）、`plumblossom-all.owl`（32 行，聚合 TBox 入口，ABox imports 已注释）。回归 `Tests run: 12, Failures: 0, Errors: 0, BUILD SUCCESS`；合并闭包 `axioms=1920`、`individuals=245`，与拆分前**逐项一致**（语义等价）。

## 闸门⑨留痕（是否 TBox 拆分 · 第 10 步前）

| 日期 | 是否拆分 | 拆分粒度（业务域划分） | 确认人 | 结论/修改点 |
|---|---|---|---|---|
| 2026-10-20 | **☑拆分** | 按 §4.1 命名空间规划拆为**九子本体**：`core / div / yijing / rel / sym / ome / jud / cal / rules` | 邵康节 | **闸门⑨通过**：确认 TBox 按业务域拆为九子本体，用 `owl:imports` + `catalog-v001.xml` 组织；`plumblossom-tbox.owl` 改为聚合入口（同构 Example 多文件形态）。落地：九子本体各自独立可解析；聚合入口单向 `imports` 无环；子本体间按 §4.2 拓扑 `imports`（core 最底层、rules 最上层）。**语义等价**（并集内容与原 TBox 图同构=True）。 |

> **第 10 步执行结果**：九子本体（core 339 / divination 146 / yijing 98 / relations 162 / symbols 46 / omens 34 / judgement 177 / calendars 193 / rules 1438 行）+ `plumblossom-tbox.owl`（聚合入口）。合计 90 类 / 64 对象属性 / 41 数据属性 / 39 约束 / 254 SWRL 描述块，与原 TBox 逐项一致。回归 `Tests run: 12, Failures: 0, Errors: 0, BUILD SUCCESS`。

## 闸门⑩留痕（是否使用数据库 · 第 11 步执行前）

> 闸门⑩留痕见《PlumBlossom应用-应用架构设计》同名小节（数据边界/库表来源，2026-10-20 邵康节确认）。

> **第 11 步执行结果（闸门⑩）**：历法（天干 10 / 地支 12 / 六十甲子 6 / 纳音 6 / 长生 4）+ 周易文本（六十四卦序 64 / 卦辞 2 / 爻辞 12）共 **116 个体入库** MySQL `plumblossomdb`（9 表）；`plumblossom-abox.owl` 由 247 裁至 **129 个体**；新增 `ontology/database/{createdb_tables.ddl,data.sql,plumBlossom.obda,plumBlossom.properties}`（8 个 mappingId）。`PlumBlossomOntologyJobWorker` 接入 OBDA（`OBDAHandler.init` + `BackendService.getInstance(main-path, obdaHandler)`），六十四卦名录互校改经 OBDA 查询；合规测试 P-2 改为校验 `data.sql` 含 64 卦名且 ABox 不再含 `Seq_*`。回归 `Tests run: 12, Failures: 0, Errors: 0, BUILD SUCCESS`（合并闭包 `axioms=1325`、`individuals=129`）。

## 闸门⑪留痕（如何导入/生成完整数据 · 第 12 步执行前）

> 闸门⑪留痕见《PlumBlossom应用-应用架构设计》同名小节（数据来源＝AI 生成、导入方式＝延续 OBDA，2026-10-20 邵康节确认）。命名口径亦经邵康节确认：名录 `core:Hexagram_01..64`、卦辞 `yijing:Dictum_Hex_01..64`、爻辞 `yijing:LineDictum_01_1..64_6`；类象 `sym:Symbol_<卦>_<维度>_<序号>`。

> **第 12 步执行结果（闸门⑪）**：
> - **DB 侧**（`ontology/database/data.sql` 扩全集）：历法 **116**（天干 10 / 地支 12 / 纳音 30 / 六十甲子 60 / 长生 4）+ 周易文本 **512**（卦序 64 / 卦辞 64 / 爻辞 384）+ 占断样本 6；卦辞/爻辞为《周易》经传（外部引入，见《本体核心设计》§9-6）。
> - **本体侧**（`plumblossom-abox.owl` 由 129 扩至 **1133 个体**）：六十四卦名录由 2（乾坤）扩至 **64**（每卦含上下卦 + 六爻个体 + 卦辞/爻辞引用）；爻个体 **384**（192 阳 / 192 阴）；类象全集 **559**（`sym:ImageSymbol`，27 栏）+ 新增 **19** 个 `sym:SymbolDimension`（合计 27）。
> - **命名迁移**：`core:QianHexagram`→`core:Hexagram_01`、`core:KunHexagram`→`core:Hexagram_02`；`yijing:Dictum_Qian`→`yijing:Dictum_Hex_01`、`LineDictum_Qian_1`→`yijing:LineDictum_01_1` 等（仅 ABox 内自引用，无外部引用）。
> - **回归**：`Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`（合并闭包 `axioms=7256`、`individuals=1133`）。详见《PlumBlossom应用-第12步回归报告》。
