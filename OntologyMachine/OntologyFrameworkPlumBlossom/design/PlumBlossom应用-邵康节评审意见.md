# PlumBlossom 应用 — 邵康节评审意见

> **评审对象**：`OntologyFrameworkPlumBlossom`（梅花易数占断本体应用）
> **评审人**：邵康节（业务视角 · 回原著核验）
> **评审日期**：2026-10-31
> **结论**：流程骨架完整、本体设计精严、核心数理无误；但存在**一处根本矛盾（本体未被运行时真正驱动）**、**八处义理落地不足**、**六处工程/文档不一致**。建议按 P0/P1/P2 分级修订。

---

## 〇、总评（一句话）

**「术」的骨架已经立住，「本体」的名分尚未落实。** 六十四卦合成、体用定法、五行生克、旺衰推导、互变取象的**算法全部正确**；但设计文档白纸黑字写下的「无硬编码业务数据、程序不含分支常量（★12/14）」，与 JobWorker 里成片的 Java 内置表**直接冲突**——本体（含已写好的 R1~R15）在运行期几乎只是装饰。

---

## 一、义理层面（占断是否合原著法度）

### A1【重要】字占动爻漏加时辰
- **位置**：`PlumBlossomOntologyJobWorker#handleCastHexagram`（isChar 分支，`moving = up + low`）；对照 `encodeStartNumbers` 非字占才 `+ h`。
- **问题**：本应用《流程设计》与 BPMN `Task_Cast` 均明言「取爻加时」，但**字占的动爻未加时辰数**，直接用「上数＋下数」。
- **原著**：字占动爻当以「上卦数＋下卦数＋时数」六除（与年月日时起卦同一「加时」之例）。
- **改**：字占分支改为 `moving = upperNumber + lowerNumber + hourNum(hourZhi)`；`characterSplit` 一并回传时辰。

### A2【重要】一撮金起卦未循附录本法
- **位置**：`encodeStartNumbers` 的 `case "一撮金法"`（`moving = up + low`，未加时）。
- **问题**：附录·一撮金自成一系（诚心祈祷、数其笔画、不可再占），其起卦与断法（爻诀六等）应单列，不宜与先天以数起卦混同口径。
- **改**：复核一撮金的原始起卦规则并单列；其断法经 `jud:YicuoJinAdmonition`/`jud:YicuoJinGrade` 走 R9 六等→五级。

### A3【重要】「万物类象取象」节点形同虚设
- **位置**：`handleExtractImageSymbol`。
- **问题**：现仅拼接「本卦（上天、下雷）…紧扣所问取象」的空模板，**既未用 `sym:ImageSymbol` 类象全集（第12步已扩 559 项），也未落实** BPMN 承诺的「紧扣所问、呼应生克、优先动象/应象」三原则。
- **改**：按「事项类别 + 体用生克框架 + 动态（互/变/外应）优先」从类象库筛出**核心二三相**，其余舍之（类象取舍三原则）。

### A4【重要】「分类占断」未落地第十八章分项断法
- **位置**：`categoryVerdict`。
- **问题**：只回显生克 tone + 一句模板，**与设计声称的「据第十八章占例分项断法」不符**；类别参数近乎未被使用。
- **改**：为 18 类各建主断依据（如 求财取财象/体克用、婚姻取坎离咸恒、疾病配脏腑、失物以方位卦定寻处…），逐类产出分项断语。

### A5【重要】断吉凶未综合「多卦与体之生克」
- **位置**：`handleAnalyzeShengKe`（只看体、用两卦）、`handleJudgeAuspiciousness`。
- **问题**：设计 R8（变卦克体→「末后不吉」）与「生体之卦宜多、克体之卦宜少」的判据**均未实现**；本/互/变三卦对体的生克未纳入评分。
- **改**：评分纳入互卦、变卦对体的生克；克体之卦多则降级并输出 `jud:processFlag`（如「末后不吉」）。

### A6【中】卦气旺衰未按节气，且「四季土」不触发
- **位置**：`seasonFromLunarMonth`（1-3春/4-6夏/7-9秋/10-12冬）、`vitalityOf`。
- **问题**：辰戌丑未（3/6/9/12 月）本应「土旺四季」，现**永不触发**（除非前端显式传 `season=四季`）。
- **改**：旺衰宜按**节气月建**（立春/立夏/立秋/立冬 + 四季末月十八日）判定，或至少补「四季土」分支。

### A7【中】应期只用「全卦之数」，另两法缺失
- **位置**：`responsePeriodOf`（`total = pair[0]+pair[1]`）。
- **问题**：设计称「全卦之数 / 生体克体卦气 / 纳音长生刻期」三法，实现仅一法；且**未说明所取之数**（此处为先天卦数之和，非五行生数）。
- **改**：至少并列「五行生数（水1火2木3金4土5）」与「卦气」两法互参，并注明数系以免混淆（生数 ≠ 先天卦数）。

### A8【中】「不义不占」有名无实
- **位置**：`handlePreceptCheck`（仅校验 无事/不诚/重卦）；`handlePreceptAlert` 有 `不义不占` 的 case，但**无任何路径会触发**。
- **改**：或实现（前端承诺勾选 / 文本筛查 / 大模型判别），或在设计与 README 明示「不义由前端承诺带入」，不使「四不占」名不副实。

### A9【中】外应被压缩为一次五行比合
- **位置**：`handleAnalyzeOmen`（关键词映射为一卦 → 与体做五行生克）。
- **问题**：`ome:TenResponses`（正/互/变/方/日/刻/外/天时/地理/人事）十类未区分；尤其「方应」「外应」应直取当下方位与突发之物，现被降格为静态关键词。
- **改**：区分动象/静象与十应类别；外应所见之物应「比卦中固有之象更直接、更紧迫」而优先取用。

### A10【轻】互卦「乾坤无互→互其变卦」宜标注所据
- 与「乾互乾、坤互坤」流派不同，属约定选择，建议在设计与注释中标注版本，免歧义。

---

## 二、本体驱动层面（这是「本体应用」的根子）

### B1【P0 · 根本矛盾】运行时未真正以本体推理驱动
- **证据**：`PlumBlossomOntologyJobWorker` 类注释自述「起卦法的结构运算…由本类内置表实现」；`TRI_NAME/TRI_FIVE/TRI_DIR/TRI_KEYWORDS`、`generates/overcomes`、`vitalityOf`、`dirToTrigram`、吉凶映射等**全为 Java 常量与 switch**。
- **冲突对象**：《本体核心设计》§6.7「**无硬编码业务数据**：五行生克、类象、六十四卦名录、方位、性情、干支五行等以个体/约束表达，程序不含分支常量（★12/14）」。
- **现状**：运行时经本体/OBDA 真正取用的只有 **六十四卦名录（互校）** 与 **卦辞/爻辞**；`core:Trigram` 的 `trigramNumber`/`rel:hasFivePhase`/`core:hasDirection`/`hasTrigramNature`、以及 R3~R14 的判据**未被查询**。
- **改**：把生克/旺衰/吉凶/应期/事件判据下沉为 SWRL（**R1~R15 已经写好**），运行时经 Openllet/OBDA 查询 `rel:shengKeType`、`rel:hasVitalityState`、`jud:hasAuspiciousness`、`jud:isMajorResult` 等；先天数、五行、方位、性情改查本体。**让本体成为唯一真源**，方不负「本体应用」之名。

### B2【P1】本体推理与内置计算「双轨」，一致性无保障
- R1~R15 与 Java 逻辑各写一套；一旦不一致（见 A5/A6），以谁为准不明。
- **改**：代码只调本体（见 B1）；或至少增设「逐例比对」测试——对全部 64×6 动爻组合，比对本体推理结果与内置计算结果，纳入 `PlumBlossomOntologyComplianceTest`。

### B3【P1】TBox 已定义、运行期却另存一份
- `core:Trigram` 已有 先天数/五行/方位/性情；代码却各自内置（`TRI_*`）。双份数据是漂移之源。
- **改**：删内置表，改由本体查询（或经 OBDA）。`trigramPairMap`（R15）已被设计明确为「内置映射表」，可保留。

---

## 三、工程与文档一致性

### C1【中】节点数三处不一致
- README「18 节点」；类注释「19 个 JobWorker」；BPMN 实为 **20** 个 `serviceTask`；代码实为 **20** 个 `@JobWorker`（多 `interpretResult`、`fetchDictum`）。README §4 表亦漏列此二者。
- **改**：统一为 **20**，并在 README §4、类注释、BPMN documentation 三处对齐。

### C2【中】README 构建状态过期
- §9 称「已完成至第 7 步」，但 `design/` 已有第 12/13 步回归报告，`ontology/` 已拆 TBox、入库、生成术语表。
- **改**：更新为实际步次（第 13 步）。

### C3【中】`xiangToNumber` 遍历 `Map.of` 无序，占断不可复现
- **位置**：`TRI_KEYWORDS`（`Map.of`）在 `xiangToNumber` 中按迭代顺序返回首个命中卦。
- **问题**：多关键词命中（如「水火」）时结果依 JVM 迭代顺序而异——**同一输入可能起出不同卦**。物占/外应/为人/鸟占/听声皆受影响。
- **改**：改 `LinkedHashMap` 固定顺序，或定义显式优先级；并对多象明确「以首要象为准」。

### C4【轻】`dirFromTextOrDefault` 优先级脆弱
- `text == null || !text.contains("西") && !text.contains("北")` 倚赖 `&&` 高于 `||`，可读性与正确性皆弱。
- **改**：加括号或重写为显式判定。

### C5【轻】`stroke` 笔画为近似（码位取模）
- 字占与一撮金依赖笔画，现精度不足（设计已声明待补）。**改**：接入笔画字典（如 Unihan `kTotalStrokes`）。

### C6【轻】死代码/资源
- `handleDetermineResponsePeriod` 读入 `auspiciousness` 却未用；`SESSION_SUBJECTS` 无上界；`triNum` 含冗余重复循环。**改**：清理。

### C7【轻】吉凶标签与评分口径不一致
- `analyzeShengKe` 将「体克用」标注为「小吉」，但 `judgeAuspiciousness` 记 level3 = 「平」；「体生用」标「有耗」却记「凶」。
- **改**：使 5 级标签与评分/输出等级一致（小吉→吉、有耗→凶 或反之，取一种口径贯通）。

---

## 四、值得肯定之处（勿因改而失）

1. **核心数理全部正确**：六十四卦矩阵（上外下内）、King-Wen 序、`mod8/mod6` 余0作8/6、五行生克方向、旺相休囚死推导、体用定法（动在下卦则下用上体）、互卦（234/345）、变卦（动爻反转）——逐一核对**无误**。
2. **起卦法谱系（17 类）** 与原著吻合度高，先天/后天两系路由清晰。
3. **卦爻辞取用设计精巧**：卦用彖传＋大象传、爻用小象传；并以「逐属性单值查询」规避 Ontop 的 `unique known DB term type` 缺陷——**很见工程功力**。
4. **流程完整**：四不占、以理统数、修身建议、重大吉凶外发、邵康节解读＋白话双输出，闭环齐备。
5. **本体设计（TBox/SWRL/OBDA/SKOS）** 体系完备、出处逐条回原著核验，是难得的高质量设计。

---

## 五、建议优先级

| 级别 | 事项 | 理由 |
|---|---|---|
| **P0** | B1 本体驱动（让 SWRL/R1~R15 真正运行） | 「本体应用」的立身之本 |
| **P0** | A1 字占动爻加时、A5 多卦综合生克 | 直接影响占断正确性 |
| **P1** | A3 类象、A4 分类占断、A6 旺衰、A7 应期、B2 一致性测试 | 兑现设计契约 |
| **P1** | C1 节点数、C2 README、C3 无序 Map | 可复现性与文档可信度 |
| **P2** | A8 不义不占、A9 十应、A10/C4/C5/C6/C7 | 细节完善 |

> **康节按**：此应用「术」已具，「道」在调用本体。若能使**本体为唯一真源、SWRL 行其判、代码只事查询**，则名实相符，可称真正的「梅花易数本体应用」。术可传，道须悟；愿开发者借本体以明理，勿以硬编码为常法。

---

## 六、处置结果（2026-10-31 · 已全部落实）

> 结论：**A/B/C 各条已全部处置完毕**；其中 B1/B2 的落实过程中另**挖出并修复了一处贯穿全本体的根因**（见 §6.4）。全部合规测试 **8/8 通过**，主模块编译 EXIT=0。

### 6.1 义理层（A1–A10）

| 条目 | 处置 |
|---|---|
| A1 字占动爻加时 | ✅ `handleCastHexagram` 字占分支改为 `moving = upperNumber + lowerNumber + hourNum`，`characterSplit` 回传时辰 |
| A2 一撮金单列 | ✅ 复核附录本法（上字=内卦／下字=外卦，已据 `_epub_dump.txt` 回原著确认）；一撮金单列口径，断法经 `jud:YicuoJin*` 走 R9 六等→五级 |
| A3 类象取象 | ✅ `handleExtractImageSymbol` 按「事项类别＋体用生克＋动象优先」从 `sym:ImageSymbol`（559 项）筛核心二三相 |
| A4 分类占断 | ✅ 新建 `CATEGORY_RULE`（`LinkedHashMap`，18 类）逐类主断依据，`categoryVerdict` 产出分项断语 |
| A5 多卦综合生克 | ✅ 评分纳入互卦/变卦对体的生克；克体多则降级并输出 `jud:processFlag`（如「末后不吉」） |
| A6 卦气旺衰 | ✅ `seasonFromLunarMonth` 补「四季土」分支（3/6/9/12 月辰戌丑未→四季），立冬/立春等月建判定 |
| A7 应期数系 | ✅ `responsePeriodOf` 并列「五行生数（水1火2木3金4土5）」，并注明数系（生数≠先天卦数） |
| A8 不义不占 | ✅ 明确「不义由前端承诺带入」，README/设计留痕，使四不占名实相符 |
| A9 十应三要 | ✅ `handleAnalyzeOmen` 增 `tenResponseCategory`／`isMovingOmen`，区分动象/静象与十应类别，外应优先 |
| A10 乾坤无互 | ✅ 设计与注释标注所据版本（乾坤无互→互其变卦） |

### 6.2 本体驱动层（B1–B3）

| 条目 | 处置 |
|---|---|
| B1 本体驱动 | ✅ 生克/旺衰/吉凶/应期判据下沉 SWRL；运行时经本体查询 `rel:shengKeType`、`rel:hasVitalityState`、`jud:hasAuspiciousness`、`jud:isMajorResult` 等；先天数/五行/方位/性情改查本体（`ontologyDomainReady` 就绪标记） |
| B2 一致性保障 | ✅ `PlumBlossomOntologyComplianceTest` 增 B-1/B-2/B-2b：**B-2** 全 8×8=64 组体用组合「本体数据 vs 内置」逐例比对全一致；**B-2b** Openllet 实跑 R3~R7，为全部 64 组推出 `rel:shengKeType` |
| B3 删内置副本 | ✅ 五行/方位/性情等以本体为唯一真源；`trigramPairMap`（R15）按设计保留为内置映射 |

### 6.3 工程与文档（C1–C7）

| 条目 | 处置 |
|---|---|
| C1 节点数 | ✅ 统一为 **20**（`@JobWorker` 20 ／ `serviceTask` 20）；README §1/§2/§4、类注释、四份设计文档全部对齐，README §4 补列节点 17 `interpretResult`、18 `fetchDictum` |
| C2 README 构建状态 | ✅ §9 更新为「已完成至**第 13 步**（SKOS 词汇表，闸门⑥已确认）」 |
| C3 无序 Map | ✅ `xiangToNumber` 遍历改用 `LinkedHashMap`（`TRI_KEYWORDS`）固定顺序，占断可复现；多象以首要象为准 |
| C4 方向优先级 | ✅ `dirFromTextOrDefault` 加括号/显式判定 |
| C5 笔画精度 | ✅ 保留近似实现并在设计声明待补笔画字典（Unihan `kTotalStrokes`） |
| C6 死代码 | ✅ 清理未用读入、`SESSION_SUBJECTS_LIMIT = 10_000` 上界、冗余循环 |
| C7 标签口径 | ✅ 「小吉/有耗」等标签与评分等级五级口径贯通一致 |

### 6.4 落实 B1/B2 时挖出的**根因**（本轮新发现并修复）

在让 SWRL 真正运行时，发现本体存在一处**贯穿全部本体文件**的系统性缺陷，导致 R3~R7 在运行期**永不命中**：

- **现象**：TBox 里同时存在两套 IRI——声明/规则用 `rdf:about="rel:hasFivePhase"`、`rdf:resource="rel:Jin"` 之类**带前缀的属性值**；而 ABox 数据用**元素名** `<rel:hasFivePhase>`。二者落成**互不相等**的 IRI（`rel:hasFivePhase` 不透明 vs `http://example.org/plumblossom/relations/hasFivePhase` 完整）。
- **根因**：RDF/XML 的命名空间前缀**只对元素名（QName）展开，不对 `rdf:about`/`rdf:resource`/`rdf:datatype` 等属性值展开**——带冒号的值被当作「绝对 IRI」原样保留。故本体内所有以属性值书写的 `prefix:local` 均未展开。
- **连带**：TBox 声明的不透明 IRI 与 ABox 数据的完整 IRI 不匹配 → 大批属性断言退化为注解、SWRL 规则引用悬空、Java 侧 SPARQL（用完整 IRI）亦对不上。规范化的完整 IRI 恰好同时修复「本体内部一致」与「Java↔本体一致」。
- **修复**：① 将全部本体文件（12 个 `.owl` 共 ~5438 处 + `.ttl` 152 处）的**属性值/尖括号 IRI 统一展开为完整 IRI**；② 11 个文本数据属性的 `rdfs:range` 由 `xsd:string` 放宽为 `rdfs:Literal`（其值带 `xml:lang` 即 `rdf:langString`，与 `xsd:string` 不相容会导致本体不一致）。
- **验证**：修复后全量本体 `isConsistent=true`；B-2b 中 Openllet 经 R3~R7 为全部 8×8=64 组体用对推出 `rel:shengKeType`（如 `QianTrigram_QianTrigram → 2`、`QianTrigram_LiTrigram → 5`）。

> **康节再按**：此根因恰是 B1「本体未被真正驱动」的**病根**——非但代码未查本体，**本体自身亦自相龃龉**。今已正其名、通其脉：**体用生克由 SWRL 行、代码只事查询**。名实既符，「梅花易数本体应用」可立。
