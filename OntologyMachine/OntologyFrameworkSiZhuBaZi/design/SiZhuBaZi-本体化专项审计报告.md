# 四柱八字本体应用 · 本体化专项审计报告

- **工程**：`D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi`
- **审计对象**：`SiZhuBaZiOntologyJobWorker.java`（3280 行，24 个 JobWorker 处理器）+ `SiZhuBaZiController.java`（142 行）+ 本体资产（16 个 `.owl`、`sizhubazi.obda`、`data.sql`、SKOS）
- **背景典籍**：`D:/Documents/中国传统/AI` 六部经典（三命通会 / 子平真诠 / 渊海子平 / 滴天髓 / 神峰通考 / 穷通宝鉴）
- **审计命题**：当前实现是否皆循「本体方式」，有无硬编码
- **审计轮次**：五轮递进（第 49 轮系列，2026-11-04）
- **结论级别**：定性 + 定量实证，含已订正缺陷清单

---

## 一、审计口径：何谓「本体模式」

本项目既有架构由 `rules.owl` 注释与 Worker Javadoc 明载 **★铁律 13**、客户裁定 ⑰/⑲/㉒/⑧/⑱ 共同界定：

> **「历法/起运数值计算与查表外置于程序，不写成 SWRL；程序算理 + 本体查表」的双轨架构；典籍原文入本体、运行期经 SPARQL 取。**

据此，本报告不把「程序算理」等同于「硬编码缺陷」，而按三层定性：
- **(A) 完全本体化**：运行期以 SPARQL 实查本体，无 Java 领域数据；
- **(B) 混合（合铁律13）**：程序承担算理/编排，条文与文本经本体取；
- **(C) 纯 Java 内置领域表**：以 `static final` 固化领域数据，需评估是否「知而未做」及与库一致性。

---

## 二、本体资产概览（实测）

| 维度 | 规模 |
|---|---|
| TBox `owl:Class` | 386 |
| `owl:ObjectProperty` | 290 |
| `owl:DatatypeProperty` | 154 |
| `owl:Restriction` | 71 |
| SWRL 规则 | 6 条（R1/R5/R6/R19/R21/R22，`swrl#Imp`） |
| OBDA 映射 | 38 个 mappingId / 21 张物理表 |
| ABox 个体 | 114（213 行） |
| DB 数据表 | 23 表（`data.sql` 5651 行） |
| SKOS 术语 | 1504 行 `skos:` 三元组 |

---

## 三、三分类实证

### (A) 完全本体化（运行期 SPARQL 实查，共 9 类内容 + 1 类时刻）

`executeAboxQuery` 共 13 处 SELECT，实查本体者：

| 本体类（OBDA 映射） | 承载内容 |
|---|---|
| `reading:DayHourClause` | 日时断（720 条） |
| `yongshen:TiaohouClause` | 穷通宝鉴调候（120 条） |
| `pattern:Pattern` | 具名格局名录（102 条） |
| `reading:StemBranchAuspice` | 十干坐支 |
| `reading:MonthBranchDayStemAuspice` | 月支断 |
| `reading:KinshipTheory` | 六亲说 |
| `reading:LadyFortune` | 女命名录（17 条） |
| `core:sexagenaryNatureText` | 六十甲子性质 |
| `calendar:SolarTermInstant` | 节气精确时刻（2424 条） |

另有通用 `citeClauses(typeIri)` 与 `classic_text/fu_clause/ziping_clause/technique_catalog/theory_clause/auspice_clause/dts_clause` 等定向引据。全部经 `BackendService.getObdaHandler().executeAboxQuery(...)`，异常降级返回空集。

### (B) 混合：程序算理 + 本体取文（合铁律13，非缺陷）

旺衰（`handleDayMasterStrength`）、格局（`handleDeterminePattern`，Javadoc 明标「程序（分类）+ SWRL R10 + 本体 pattern:」）、用神（`handleSelectYongShen`，调候实查 `clauseTiaohou`）、层次（`handleJudgeChartLevel`）、大运（`handleFortuneCycles`）、女命（`handleFemaleChart`，实查 `clauseLadyFortunes`）、合婚/子女/分类断等，均「程序编排 + 本体取文」。

### (C) 纯 Java 内置领域表（`static final`，110–230 行）

约 15 组、400+ 条目：天干地支/五行/生肖、藏干 `ZHI_HIDDEN`(28)、纳音 `NAYIN`(30)、十神 `TEN_GOD`+`tenGodIndex`、神煞 `TIANYI/YANGREN/LU/YIMA/TAOHUA/HUAGAI/JIANGXING`、合冲刑害 `SANHE/LIUHE/LIUCHONG/LIUHAI/SANXING/GAN_WUHE`、长生 `STAGE_NAME/CHANGSHENG_ZHI`、节气 `S_TERM`、农历 `LUNAR_INFO`(201)。

---

## 四、一致性实证：内置表 ≡ 本体库表（逐一镜像，实测 0 差异）

| 领域表 | 本体库表 | 实测 |
|---|---|---|
| 藏干 | `hidden_stem`(28) | **0 差异** |
| 纳音 | `nayin`(30) | **0 差异**（已统一「沙」） |
| 十神（10×10） | `ten_god_map`(100) | **0 差异** |
| 天乙/禄/羊刃 | `deity_rule` | 全合（羊刃异法并陈已标注） |
| 驿马/桃花/华盖/将星 | `deity_rule` | 全合（已补日支参照） |

且本体 TBox 确已建模相应领域：`core:HeavenlyStem/EarthlyBranch/SexagenaryCycle/NaYin/HiddenStem` 及 `hasFivePhase/hasYinYang/hasNaYin/hasHiddenStem/hasLifeStage` 等限制齐备——**内置表为同一真源之程序侧镜像，非本体未覆盖之私货**。

---

## 五、架构专项结论

1. **零绕体直读**：全工程无 JDBC/JdbcTemplate/EntityManager/JPQL；`SELECT` 仅 SPARQL（`executeAboxQuery`）。Controller 无领域硬编码。
2. **OBDA 覆盖**：21 张物理表有映射；**`deity_rule`(186/78类) 与 `ten_god_map`(100) 二表未映射**——`ten_god_map` 由 Java 等价替代（无损）；`deity_rule` 运行期仅排常用八类（⑧口径），余为备查名录。
3. **SWRL 实况**：Java 代码零 SWRL 推理引擎调用；框架 `BackendService`（Openllet 推理器）加载时 `flush()` 评估规则；然业务主链（`aggregateReading`→`sendAlert`）以 Java 判据触发告警，**未回读 SWRL 推导个体**——「本体声明与程序实现双写」。
4. **白话断语**：`handleCategoryVerdicts`（疾病脏腑数组 `zang`、性情判语）、`buildCategoryVerdicts`（事业/财运/婚姻/寿夭/飘荡）、`handleMoralAdvice`、`handleMarriageMatch`、`handleChildChart`、`handleTimingAndStages` 等为 Java 内嵌中文，属⑱白话侧，非本体取文。

---

## 六、缺陷与订正清单（已施作，测试恒通过）

| # | 问题 | 订正 | 轮次 |
|---|---|---|---|
| 1 | 纳音 DB「砂中金」与 Java「沙中金」不一致 | 统一为「沙」 | 47.1 |
| 2 | 4 处注释「查表(OBDA xxx)」与实现不符 | 改为「内置表（与 DB 表同源）」 | 47.1 |
| 3 | 羊刃口径 Java 阴干无刃 vs DB 十干全录 | 查明 DB 标「阴刃（异法）」并陈，非冲突，补注 | 47.1 |
| 4 | `handleTenGodsKin`/`handleArrangeDeities`/`handleChildChart` 误书「经 OBDA ten_god_map/deity_rule」（该二表无映射） | 改为「DB 表（暂未接入 OBDA）」并如实标注八类口径 | 47.4 |
| 5 | **三合类神煞（驿马/桃花/华盖/将星）仅取年支，漏排日支**（DB rule_type='年支/日支'） | 改为兼取年支、日支参照 | 47.5 |

**校验**：`mvn -o test` 恒 **exit 0**；`SiZhuBaZiOntologyComplianceTest` axioms=1770 / individuals=118（未变）。

---

## 七、遗留扩展项（原候裁项；第 50 轮已全部实施，见第九节）

1. **神煞 8→78 类**：若要本体直查全库 `deity_rule`，须扩 `deity.owl`（补 `ruleType/targetGanzhi/deityBranch` 数据属性）+ 增 OBDA 映射 + 改运行期查询。属功能扩展，须依⑧口径裁定。
2. **SWRL 上主链**：若要告警由本体 SWRL 推导个体驱动而非 Java 判据，需改 `aggregateReading` 回读 `rules:AlertFlag`。
3. **白话断语入本体**：若要求一切断言源自本体，可将上述六类模板抽入 `reading:CategoryVerdict` 等类 + 映射。

---

## 八、最终判定

- 本应用**非「伪本体」**：领域数据经「本体 TBox + OBDA 21 表 + 程序镜像（0 差异）」三处一致落地；典籍原文/条文全经 SPARQL 实查；硬编码限于「程序算理 + 白话解读」之既定双轨（铁律13/⑰/⑱），非「知而未做」之私货。
- **符合既定「本体条文 + 程序算理/编排 + 白话解读 + SWRL」双轨架构**。
- 若客户将「本体模式」严格定义为「一切领域断言（含白话）皆源自本体、SWRL 须为主链推理来源」，则第六节已订正者之外，第七节三项为需改造处——属架构取向之取舍，非数据错误。
- 五轮累计订正 5 类问题，全量测试恒通过，本体规模不变。

> 附：审计过程逐轮记录见 `design/SiZhuBaZi应用-本体核心设计.md`「第 47.1–47.5 步」及工作日志 `2026-11-03.md`「第 49 轮·一～五」。

---

## 九、第 50 轮：三项扩展实施记录（已落地，测试通过）

依客户「需要」之裁示，对第七节三项遗留扩展项全部实施，均为「本体建模 + OBDA 映射 + 运行期 SPARQL + Java 兜底」双轨改造。

### 9.1 神煞 78 类接入 OBDA（原八类 → 全量 186 条 / 78 类）
- **TBox**：`deity.owl` 新增 `deity:DeityRule` 类及 `ruleId/basePillar/baseGanzhi/ruleDeityName/deityAlias/branchHit/ruleCategory/ruleSource` 八个数据属性。
- **OBDA**：`sizhubazi.obda` 新增 `mappingId deity_rule`（原「0 映射」→ 已暴露）。
- **运行期**：`handleArrangeDeities` 新增 `deitiesFromOntology()`，经 SPARQL 取全库起例、按本命四柱匹配（`年/月/日干/日支/月支` 等基准语义，含三合、旬空跳过避免与 voidness 重复）；OBDA/DB 不可用时降级内置常用八类（原逻辑保留）。
- 覆盖率：186 条起例全部可由本体取出；`天乙/羊刃/禄/驿马/桃花/华盖/将星/文昌/金舆/天德/劫煞…` 等 78 类均在列。

### 9.2 SWRL 上主链（程序判据 → 本体推导回读）
- **TBox**：`chart.owl` 新增 `chart:hasYangRenChong`（boolean）；`rules.owl` 新增 R20「寿元关」SWRL 规则（羊刃逢冲 → `AlertShouYuanGuan`），SWRL 规则数 6 → 7。
- **运行期**：`aggregateReading` 新增 `deriveAlertsViaSwrl()`——将本命事实（`pattern:isBroken` / `yongshen:noYongShen` / `chart:hasYangRenChong`）物化为临时 ABox → `BackendService.withTempAxioms` 注注入并 `flush()` 触发 R19/R20/R21 → 回读 `rules:hasAlertFlag` 指向的 `AlertFlag` 个体 → 产出「大凶/寿元关/重大疾病」；异常或未命中回退程序判据（安全优先）。
- **实测**：探针验证 Openllet 由 `isBroken+noYongShen` 推导出 `AlertDaXiong`、由 `hasYangRenChong` 推导出 `AlertShouYuanGuan`，`isConsistent=true`——SWRL 主链真实生效（非仅声明）。

### 9.3 白话断语入本体（五段分类断语模板）
- **TBox**：`reading.owl` 新增 `reading:CategoryVerdict` 类与 `reading:conditionKey` 数据属性。
- **DB/OBDA**：`data.sql` 新增 `category_verdict` 表 15 条模板（事业/财运/婚姻/寿夭/飘荡 × 条件键）；`createdb_tables.ddl` 增建表；`sizhubazi.obda` 新增 `mappingId category_verdict`。
- **运行期**：`buildCategoryVerdicts` 改为经 `clauseCategoryVerdict()` 按 `category×conditionKey` 取本体模板，`verdictOr()` 未命中降级内联文案（离线行为不变）。

### 9.4 合规连带修正
- SKOS 术语表 `sizhubazi-terminology.ttl` 补录 12 个新增实体概念（`DeityRule/CategoryVerdict/ruleId/basePillar/.../hasYangRenChong`），满足「本体带标签实体 100% 有概念」交叉校验。
- 更新 `createdb_tables.ddl` 头部注记：`deity_rule` 已由「无 RDF 映射」改为「经 OBDA 暴露为 deity:DeityRule」。

### 9.5 校验结果
- `mvn -o -q test` **exit 0**；`SiZhuBaZiOntologyComplianceTest` axioms=1817（较前 +47）、individuals=118（不变）。
- 本轮实际执行 **49 项用例全绿**（0 失败 / 0 错误），含 SKOS 交叉校验、经典起例、合规校验、四柱解析、历法换算；另有 17 项 BPMN/RabbitMQ 依赖用例按环境未启用（源码共 66 个 @Test）。

## 十、第 51 轮：四维复核（一致性 / 完整性 / 本体模式 / 硬编码）

复核令：「检查本体应用与原著保持一致、完整性好、程序本体模式、无硬编码」。**不照搬旧结论，全部重新取证**，硬证据如下。

### 10.1 程序本体模式（成立）
- **架构**：TBox 13 业务域（core/chart/calendar/relations/tengod/deity/pattern/yongshen/fortune/reading/diagnosis/rules + tbox）合计约 195 类、对象属性约 145、数据属性约 87、**SWRL 规则 7 条**（R1/R5/R6/R19/R20/R21/R22，`swrl#Imp` 实测 7）。
- **数据访问**：全工程 **JDBC / JPA / JPQL / @Query 零命中**；所有 DB 访问唯一出口 `executeAboxQuery`（Ontop OBDA 虚拟图，L1836），共 **14 条 SPARQL**。
- **条文取数覆盖**：SPARQL 实查神煞（deity:DeityRule）、日时断（DayHourClause）、调候（TiaohouClause）、格局（Pattern）、天干坐支/月支日干（Auspice）、六亲（KinshipTheory）、女命（LadyFortune）、六十甲子性质（core:sexagenaryNatureText）、赋诀（Fu/Ziping/Theory/DTS）、分类断语（CategoryVerdict）、节气（SolarTermInstant）。
- **推理**：SWRL 上主链，`aggregateReading` 经 `withTempAxioms` 回读 `hasAlertFlag`（探针实证：isBroken+noYongShen→AlertDaXiong；+hasYangRenChong→AlertShouYuanGuan）。
- 结论：**本体模式成立**（TBox+OBDA+SWRL+SPARQL 四层齐备，非伪本体）。

### 10.2 无硬编码（无伪造数据；硬编码限于算理/镜像/术语/白话模板）
全类 `static final` 常量 38 处，逐类定性：
- **程序算理/历法**（客户裁定⑰、★铁律13 明确外置，非缺陷）：`GAN_ELEM/GAN_YY/ZHI_ELEM`（五行阴阳）、`S_TERM/JIE_IDX/JIE_BRANCH/LUNAR_INFO`（节气/农历）、`CHANGSHENG_ZHI/ZHI_START_HOUR`、`LIUHE/LIUCHONG/LIUHAI/SANXING/GAN_WUHE`（干支关系）、`tenGodIndex()` 生克算法、五虎遁/五鼠遁/JDN 起柱、旺衰多因子、大运起运。
- **查表数据镜像**（DB 为唯一真源，**实测逐一相等**）：`NAYIN` 30 名顺序全等、`ZHI_HIDDEN` 28 藏干全等（见 10.3）；八类神煞起例 `TIANYI/YANGREN/LU/SANHE/YIMA/TAOHUA/HUAGAI/JIANGXING` 与 DB `deity_rule` 同源，且已降级为本体优先。
- **术语标签**：`GAN/ZHI/ELEM_NAME/ZHI_ANIMAL/STAGE_NAME/TEN_GOD/GAN_WUHE_HUA` —— 术语字面常量（可由 SKOS 取，当前作标签常量）。
- **白话解读模板（内联）**：含 ≥15 汉字字面量 86 条 / 2274 汉字，属「解读层」拼装文案（合婚/女命/子息/劝勉/综合解读等）+ 内联兜底，非原著条文数据。
- 结论：**无「伪造数据硬编码」**；硬编码均属上述四类且带原著出处标注。严格「零硬编码、一切源自本体」未达，属既定双轨取舍。

### 10.3 完整性（好；一处表未映射、两域程序替代）
- **OBDA 覆盖**：物理表 25，mappingId **40**，24 表已映射；**唯一未映射：`ten_god_map`（100 行）**——但十神由 `tenGodIndex()` 五行生克算法等价推导，实测 **100/100 与 DB 完全一致**，属镜像冗余。
- **数据一致性硬证（本轮实测）**：NAYIN 30 名顺序全等 `True`；hidden_stem 28 藏干按支聚合全等 `True`；十神算法 vs `ten_god_map` **0/100 差异**。
- **本体规模**：axioms=1817、individuals=118（合规测试）；ABox 干支 0 声明（真源唯一归 DB）。
- **两域覆盖缺口**：`relations`（合冲刑穿）与 `tengod`（十神）二域已在 TBox 建模，但运行期 Java **仅注释引用、代码不查**（走内置算理表/算法）——注释如实标注「本体 relations:/tengod:（R7 由程序）」，非隐瞒。

### 10.4 经典一致性（与原著逐字相符）
抽样文本级比对（整理稿 ↔ DB/本体），全部相符：
| 内容 | 本应用（DB/本体） | 原著原文（六书整理稿） | 出处标注 |
|---|---|---|---|
| 纳音 30 名 | 海中金…大海水 | 三命通会卷一/渊海子平 | ✓ |
| 藏干 28 | 子癸/丑己癸辛… | 渊海子平·地支藏遁歌 | ✓ |
| 天乙贵人 | 甲戊庚→丑未；乙己→子申… | 三命通会「甲戊庚人得丑未」 | 三命通会卷二 |
| 羊刃 | 五阳有刃五阴无刃 | 「甲丙戊庚壬五阳干有刃，乙丁己辛癸五阴无刃」 | 三命通会卷五 |
| 调候·正月甲 | 「初春尚有余寒…寒木向阳」 | 穷通宝鉴「正月甲木…名寒木向阳」 | 穷通宝鉴 |
| 日时断·六甲子时 | 「子遥巳格，年月无庚辛申酉，丑绊午冲」 | 三命通会卷八同文 | 三命通会卷八 |
| 格局·岁德正官/时上正官 | 同名条目 | 三命通会卷五 | ✓ |
| 子平真诠·论十干十二支 | 「天地之间，一气而己…」 | 子平真诠同文 | ✓ |
出处分布（实测）：神煞→三命通会卷二、格局→卷五、日时断→卷八、赋诀→渊海子平卷三、调候→穷通宝鉴，标注正确无张冠李戴。

### 10.5 复核总判定
**与原著高度一致、完整性好、程序本体模式成立、无伪造硬编码。** 架构为「本体条文 + 程序算理/编排 + 白话」双轨，符合客户裁定⑧⑰⑱⑬。残留三处轻微可整改项（非数据错，属覆盖取舍）：
1. `ten_god_map` 表未接入 OBDA（建议与 `deity_rule` 并列接入，使十神亦可本体查）；
2. `relations`/`tengod` 二域运行期走程序算理、不查本体（建议接入查询或正式标注为「算理域」）；
3. 白话解读模板仍有约 86 条/2274 汉字内联（已部分上移本体 CategoryVerdict，可继续迁移）。

## 十一、第 52 轮：三项整改落实记录（已落地，测试通过）

针对第十节所列三处残留项，客户令「立即修正」，逐项落地如下，`mvn -o -q test` **exit 0 / 49 项全绿 / axioms 1817→1841（+24）/ individuals=118 不变**。

### 11.1 ① ten_god_map 接入 OBDA（完整性缺口 #1 已闭环）
- **TBox**：`sizhubazi-tengod.owl` 新增类 `tengod:TenGodMap`（subClassOf TenGodDomain）+ 5 数据属性 `mapId/baseDayStem/targetStem/mapTenGodName/mapSourceNote`，并加域注释。
- **OBDA**：`sizhubazi.obda` 新增 `mappingId ten_god_map`（映射 40→**41**），target 用 `tengod:{map_id} a tengod:TenGodMap …`，source 从 `ten_god_map` 表取 5 列（列名与 DDL 对齐）。`tengod:` 前缀已在 [PrefixDeclaration] 声明，结构校验：41 mappingId 唯一、块均有 target/source、5 属性齐、`[[ ]]` 配对。
- **Java**：新增 `tenGodOntologyMap()`（一次 SPARQL 取全库 100 条建缓存）+ `tenGodName(dayGan,target)`（本体优先、`tenGodIndex` 算法降级）；`handleTenGodsKin` 天干/藏干十神展示改走 `tenGodName`。
- **SKOS**：terminology.ttl 补录 6 概念（TenGodMap/mapId/baseDayStem/targetStem/mapTenGodName/mapSourceNote）。
- **数据**：`ten_god_map` 100 行已在库，经既有 `category_verdict` 同款 OBDA 机制自动可查；算法 vs DB 100/100 一致（第 51 轮实测）。

### 11.2 ② relations 域正式标注为「程序算理域」（缺口 #2 已消除歧义）
- `sizhubazi-relations.owl` 增 `rdfs:comment`：干支五合化气/六合三合/冲刑穿害等由程序确定性算法（内置 LIUHE/LIUCHONG/LIUHAI/SANXING/GAN_WUHE 表）判定，TBox 建模完备供推理/一致性校验、不设 SPARQL（客户裁定⑰·★铁律13）。
- `handleGanzhiRelations` 代码注释同步标注「程序算理域」。
- 说明：`tengod` 域经 11.1 已具备运行期本体查询（十神映射），不再属缺口；其生克制化关系运算仍由程序编排（属算理）。

### 11.3 ③ 白话模板继续上移本体（原 15 条 → 20 条）
- `category_verdict` 增补 5 条：合婚（`hehun_complementary`/`hehun_plain`）、劝勉（`moral_good`/`moral_bad`）、女命平和（`female_pinghe`），各带出处（神峰通考卷一/滴天髓·知命/三命通会卷七）。
- Java 三处改经 `verdictOr(类别,键,内联兜底)` 取本体文：`handleMarriageMatch`（合婚结论）、`handleMoralAdvice`（劝勉通用句，层次前缀保留程序拼装）、`handleFemaleChart`（女命平和兜底）；内联兜底原样保留，OBDA 不可用不阻断。

### 11.4 校验结果
- `mvn -o -q test` **exit 0**；49 项用例全绿（0 失败/0 错误）。
- axioms=1817→**1841**（+24：TenGodMap 类 2 + 5 数据属性声明 + 域/映射注解等）；individuals=118 不变。
- OBDA 映射 40→41；`category_verdict` 15→20；SKOS 术语 +6。
- 首跑 2 failures（`everySynonymHasTraceableSource`：新增 altLabel 缺「# 源:」注释）已修：移除 TenGodMap 冗余 altLabel 后 49 项全绿。

## 十二、第 53 轮：遗漏与硬编码再复核（不沿用旧结论，全量取证）

复核令：「再次检查是否遗漏及是否硬编码」→「继续修正」。逐项重新取证如下。

### 12.1 遗漏复核（全覆盖，无缺口）
- **表↔映射**：DDL 物理表 **25**，OBDA `mappingId` **41**；按 `FROM <表>` 逐一核对，**25/25 表均有映射**（含 `ten_god_map`，第 52 轮已接入；`auspice_clause` 4 映射、`theory_clause` 4、`dts_clause` 10、其余单映射）。
- **Java 表使用**：`classic_text`/`technique_catalog`/`case_record`/`solar_term_time` 均有运行期引用；`ganzhi_stem`/`ganzhi_branch`/`sexagenary_cycle`/`month_order`/`alert_flag` 经本体类 IRI 或 SWRL/一致性校验消费（非按表名直查，属正常）。
- **SPARQL**：`SELECT` 计 **15 条**（较第 51 轮 14 条 +1，为 ten_god_map 查询），全部经唯一出口 `executeAboxQuery`（OBDA 虚拟图）；JDBC/JPA/JPQL 仍零命中。
- 结论：**表、映射、运行期查询三层无遗漏**。

### 12.2 硬编码复核（无伪造数据；发现并修正 2 处「过时表述」）
- **字面量普查**：全类 1504 处字符串字面量，其中含 ≥20 汉字的 **59 条**，逐条定性：系统提示词 12 条、日志 3 条、白话解读模板/兜底 39 条、程序算理标签 5 条；**无一条为原著条文数据本体**（条文均经 OBDA 取 `?text ?source`）。
- **具名格局标签核对**：`namedPatternHits` 内联 `{名,描述,出处}` 三元组（魁罡/日禄归时/天元作禄/天元坐煞/六乙鼠贵/六阴朝阳/壬骑龙背/岁德正官…）与 `pattern_catalog.source_note` 逐条比对**出处一致、无张冠李戴**；命中原文本仍经 `clausePatterns()` 从 `pattern_catalog` 取，标签仅作镜像。
- **DB「待考」定性**：`data.sql` 共 11 处「待考」，**逐处查明**——5 处为 SQL 注释（说明历史落地轨迹）、6 处为穷通宝鉴原文内的「此命造确否待考」等**原书原文引文**（如 CT_QT_36/123/126/136/137/141），**均非占位式待考字段值**，无需改动、不得删。
- **★修正项（过时表述，与已完成集成矛盾）**：
  1. `SiZhuBaZiOntologyJobWorker.java` L164 常量注释原称「`deity_rule` 表暂未接入 OBDA，见第 47.4 步」——与第 36 步/现行 `deitiesFromOntology` 经 OBDA 全量取 186 条相矛盾。改为「该表已经 OBDA mappingId `deity_rule` 全量接入本体，运行期 `arrangeDeities` 经 SPARQL 取用，内置表仅作降级镜像」。
  2. 同文件 L1510 小儿关杀输出文案原称「神煞起例运行期暂排常用八类，deity_rule 表 78 类尚未接入 OBDA」——同属过时且误导。改为「小儿关煞本例按年/月/时支起例由程序算理排布，神煞总库 deity_rule 78 类/186 条已全量接入 OBDA 供 arrangeDeities 取用，惟其中未单列小儿关煞条目」（经核实 deity_rule 分类为贵人吉神/凶煞/禄马/空亡/概念，确无小儿关煞行，故措辞如实）。
- 修正后 `grep` 全 src：`尚未接入/暂未接入/暂排/未接入 OBDA/待接入` **零命中**。

### 12.3 校验结果
- `mvn -o -q test` **exit 0**；49 项用例全绿（0 失败/0 错误）。
- axioms=**1841**、individuals=**118**（与第 52 轮一致，本次仅改注释与文案，不动模型/数据）。
- OBDA 映射 41、物理表 25 全覆盖不变。

### 12.4 判定
**无遗漏、无伪造硬编码。** 本轮修正 2 处「过时表述」（非数据错，属文档/输出文案未随 deity_rule 接入 OBDA 同步更新）。至此 25 表全映射、14→15 条 SPARQL 全走 OBDA、条文真源归 DB、算理归程序的双轨架构自洽；「待考」仅为原书原文引文，非待办占位。

## 十三、第 54 轮：逐书逐卷逐节符合性审查（原书卷/节粒度对账，全量实算）

审查令：「再次逐卷逐节来审查，是否有缺失，并检查是否是按本体方式，没有硬编码」。方法：以 `design/六书全文整理/*.txt` 整理稿为底本，逐书提取卷/节标题，与本体库（25 表 + 41 映射 + TBox 13 域）逐条反查对账；同时核验运行期取数是否 100% 经 OBDA/SPARQL。**不抽样、不基于 OCR、全部脚本实算。** 完整报告另见 `design/SiZhuBaZi-逐书逐卷逐节符合性审查报告.md`。

### 13.1 六书逐卷逐节对账（缺失全 0）
| 书 | 原书卷/节（标题源实取） | 本体存储 | 缺失 |
|---|---|---|---|
| 三命通会 | 9 卷 **298 节**（`○` 起节） | `classic_text` 298 章 + deity_rule 186 + auspice_clause 25 + day_hour_clause 720 + lady_fortune 17 + pattern_catalog 102 | **0**（298/298 命中） |
| 子平真诠 | **47 篇** | `ziping_clause` 47 条 | **0**（TOC↔库双向全等） |
| 滴天髓 | 通神论 34 + 六亲论 29 = **63 节** | `classic_text` 63 章 + dts_clause 26 + technique_catalog | **0**（全节在位） |
| 渊海子平 | 正文标题 **293 类**（302 锚点剔 8 卷题 + 1 重出） | `classic_text` 296 章 + fu_clause 209 + technique_catalog | **0**（5 幅图入 fu_clause，1 处夹注地名，无真缺） |
| 神峰通考 | **128 篇**/说/格 | `classic_text` 128 章 + fu_clause（神峰部分）+ theory_clause 12 | **0**（论大运/太岁/金不换歌/子平举要/江湖摘锦/男命小运等正文并入邻章或 fu_clause，均在库） |
| 穷通宝鉴 | 10 干 × 12 月 + 总论/序跋 = **142 章** | `classic_text` 142 章 + `tiaohou_clause` **120（10×12 全备）** + technique_catalog | **0** |

### 13.2 运行期条文体量（全量实算，与 25 表一致）
day_hour_clause **720**（distinct 组合 720 = 60×12 无缺）/ tiaohou_clause **120**（10×12）/ pattern_catalog 102 / deity_rule 186·78 类 / ten_god_map 100 / category_verdict 20 / fu_clause 209 / theory_clause 12 / dts_clause 26 / auspice_clause 25 / technique_catalog 66 / lady_fortune_catalog 17 / ziping_clause 47 / nayin 30 / hidden_stem 28 / sexagenary_nature 60。全 5 书**无 <12 字空章**。

### 13.3 本体方式核验（无硬编码）
- 全工程 JDBC/JPA/JPQL/@Query **零命中**；唯一 DB 出口 `executeAboxQuery`（OBDA 虚拟图），运行期 **15 条 SPARQL** 全走 Ontop。
- 条文族（classic_text/ziping_clause/fu_clause/dts_clause/theory_clause/auspice_clause/technique_catalog/lady_fortune_catalog/pattern_catalog/category_verdict/day_hour_clause/tiaohou_clause/deity_rule/ten_god_map）运行期**均以 SPARQL 读取**，非内联。
- **21 个 OWL 类**（reading 族 12 + pattern 族 4 + tengod/deity/yongshen/calendar）均在 .owl 中 `owl:Class` 声明；OBDA 每条映射均含 `reading:readingText/readingSource/readingCategory`，与 `readReadingFamily()` 的 SPARQL 变量一一对应，取数不落空。
- 1504 处字面量中 59 条长中文逐条定性（提示词 12 / 日志 3 / 白话 39 / 算理标签 5），**无原著条文正文**。

### 13.4 非缺陷性观察（底本标题粒度 ≠ 存储章粒度）
1. 穷通宝鉴「二月丁火/五月丁火」整理稿并入邻章、未单列 `classic_text` 标题，但 `tiaohou_clause` 丁×卯/丁×午已单条覆盖，运行期不缺。
2. 神峰通考部分小节并入邻章或落 `fu_clause`，正文在库无丢失。
3. 渊海子平 5 幅图（天降干支图等）以图录形式存 `fu_clause`，非缺失。

### 13.5 判定
**六书逐卷逐节无内容缺失；全部条文经本体（OBDA→SPARQL）取用，无原著内容硬编码。** 校验 `mvn -o -q test` exit 0 / axioms=1841 / individuals=118 不变（本轮仅新增审查文档，不动模型与数据）。

### 13.6 附：运行期部署库缺失 `category_verdict` 表的发现与修复（端到端实测捕获）
在运行完整测试套件复核时，端到端 BPMN 测试（`SiZhuBaZiBpmnInMemoryTest`，由生产 `SiZhuBaZiOntologyJobWorker` 驱动全 21 个 serviceTask）暴露一处**真实运行期缺陷**（非文档/非内容缺失，属部署库漂移）：

- **现象**：11 项用例中 9 项报 `Process ... was not completed`，每项卡满 180s 超时（累计 1629s）。生产 Worker 日志显示根错为：
  `❌ OntopMappingResolver 加载失败 | path=ontology/database/sizhubazi.obda` → `InvalidMappingSourceQueriesException: Cannot find relation 'sizhubazidb'.category_verdict`。
- **根因**：`createdb_tables.ddl`（25 表）与 `data.sql`（20 行 CV_*）均**已含** `category_verdict`，OBDA 亦已映射为 `reading:CategoryVerdict`；但实际运行的 MySQL `sizhubazidb` 仅有 **24** 张表，**独缺 `category_verdict`**。因 Ontop 映射文件须整体解析，一表缺失即令**整个 SPARQL 条文层加载失败**，凡取数之 serviceTask 全部抛错，流程无法完成。此前纯本体单测（ClassicRules/Compliance/SKOS 等不触达活库）全绿，故一直未被发现。
- **修复**：自 `createdb_tables.ddl` 取 `CREATE TABLE IF NOT EXISTS category_verdict`，自 `data.sql`（第 5656–5676 行）取 20 行 `INSERT`，以 root 连 `127.0.0.1:3306/sizhubazidb` 补建并灌数；校验得 20 行、分类分布 事业2/财运4/婚姻4/寿夭2/飘荡3/合婚2/劝勉2/女命1，与 data.sql 逐条一致（OBDA 的 `jdbc.user=root` 空密码，权限无碍）。
- **验证**：修复后单独重跑 `SiZhuBaZiBpmnInMemoryTest` → **11/11 全通过，30.8s**（此前 9 失败/1629s），XML 中 `OntopMappingResolver 加载失败 / Cannot find relation` 关键字**归零**；完整 `mvn -o -q test` → **63 项全绿（0 失败/0 错误/0 跳过），exit 0，1 分 3 秒**。`SiZhuBaZiBpmnRealEngineTest` 需外部 Camunda REST(9080)/Zeebe(26500)，本环境未常驻启动，按其 `assumeAllReachable` 前置假设**正常跳过（计 0 项，非失败）**。
- **附带清理**：清除上一轮遗留、占住 `:26500` 近 1 小时的 Camunda 进程（PID 2644），避免其污染 RealEngine 连接。
- **结论**：此为「DDL/data.sql 已更新而活库未同步」的**部署库漂移**，一经补建即恢复；与六书条文完整性、本体映射设计无关，但确属「是否有缺失」审查中必须排除的运行期缺口，故据实记录并已修复。
