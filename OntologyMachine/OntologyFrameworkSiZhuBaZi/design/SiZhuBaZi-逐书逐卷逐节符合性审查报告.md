# SiZhuBaZi 应用 · 六书逐卷逐节符合性审查报告（第 54 轮）

> 审查令：「再次逐卷逐节来审查，是否有缺失，并检查是否是按本体方式，没有硬编码」。
> 方法：以六书整理稿（`design/六书全文整理/*.txt`）为底本，逐书提取卷/节标题，与本体库（`ontology/database/data.sql` 25 张表 + `sizhubazi.obda` 41 映射 + TBox 13 域）逐条对账；同时核验运行期全部取数是否经 OBDA/SPARQL（本体方式），而非 Java 内联硬编码。**不抽样、不基于 OCR、全部脚本实算。**

---

## 一、结论速览

| 书 | 原书卷/节（标题源） | 本体存储 | 缺失 | 判定 |
|---|---|---|---|---|
| 三命通会 | 9 卷 **298 节**（`○`标题） | `classic_text` 298 章 + `deity_rule`186 + `auspice_clause`25 + `day_hour_clause`720 + `lady_fortune_catalog`17 + `pattern_catalog`102 | **0** | ✅ 逐节全等 |
| 子平真诠 | **47 篇**（目录一~四十七） | `ziping_clause` 47 条 | **0** | ✅ 双向全等 |
| 滴天髓 | 通神论 34 + 六亲论 29 = **63 节** | `classic_text` 63 章 + `dts_clause`26 + `technique_catalog`（天道/地道/人道） | **0** | ✅ 全节在位 |
| 渊海子平 | 卷之一~七，正文标题 **293 类** | `classic_text` 296 章 + `fu_clause`209 + `technique_catalog` | **0**（5 图为图录已入 `fu_clause`） | ✅ 全覆盖 |
| 神峰通考 | 128 篇/说/格 | `classic_text` 128 章 + `fu_clause`(神峰部分) + `theory_clause`12 | **0**（部分小节并入邻章，正文在库） | ✅ 正文全覆盖 |
| 穷通宝鉴 | 10 干 ×12 月 + 总论/序跋 | `classic_text` 142 章 + `tiaohou_clause` **120（10×12 全备）** + `technique_catalog`(总论) | **0** | ✅ 全备 |

**总判定：六书逐卷逐节无内容缺失；运行期取数 100% 经本体（OBDA/SPARQL），无原著条文硬编码。**

---

## 二、逐书对账明细（硬证据）

### 2.1 三命通会（298 节，0 缺失）
- 原书正文以 `○` 起节，实取 **298 个标题**；`classic_text` 存 **298 章**，逐标题反查 **298/298 命中，缺 0**。
- 逐卷章数：卷一 6 / 卷二 26 / 卷三 23 / 卷四 8 / 卷五 20 / 卷六 73 / 卷七 22 / 卷八 60 / 卷九 60。
- 卷四「论十干坐支/论十二月支/论五行时地分野/论木火土金水」8 节，另由 `auspice_clause` 拆为 25 条（十干坐支 5 + 十二月支 12 + 五行时地 6 + 孕生男女 2）供运行期取用；卷七「论女命」拆 17 条入 `lady_fortune_catalog`；卷八「日时断」全量入 `day_hour_clause` **720 条（60 日柱×12 时支，无缺组合）**；神煞入 `deity_rule` **186 条/78 类**。三命通会一卷至九卷**均有对应本体资产**。

### 2.2 子平真诠（47 篇，0 缺失）
- 目录列「一、论十干十二支 … 四十七、论杂格」共 **47 篇**；`ziping_clause` **47 条**。
- 逐篇归一化比对：**TOC 有而库无 = 0；库有而 TOC 无 = 0**（双向全等）。含论用神系列 12 篇、十神格取运系列、论行运/成格变格等，齐全。

### 2.3 滴天髓（63 节，0 缺失）
- 目录通神论 34 节 + 六亲论 29 节 = **63**；`classic_text` **63 章**（通神论 34 + 六亲论 29）。
- TOC 全部条目在库均有对应章（反查无缺失）；另 `dts_clause` 26 条抽出「形象/方局/震兑/坎离/何知章（富富贵贫贱吉凶寿夭 8 分）/闲神/从化假从假化/顺反战合/君象臣象母象子象/贞元」作运行期引据；天道/地道/人道入 `technique_catalog`。

### 2.4 渊海子平（正文全覆盖，0 缺失）
- 以整理稿「未知」标记行后的标题为锚点，实取 **302 处**；剔除 8 处卷题（「合并…卷之X」）与 1 处重复（论羊刃重出）后得 **293 类正文标题**。
- 反查：仅 6 项库章名未直接命中，逐项查明——`天降干支图 / 阴阳顺逆生旺死绝之图 / 月律分野之图 / 男命六亲取要图 / 女命六亲取要图` **5 幅图为图录，已存 `fu_clause`（如 FU_YH 系列）**；`凤凰地` 为正文夹注地名（OCR 断行误判），非独立节。**无真缺失。**
- 赋/歌/诀/诗诀（卷三~卷七）尽入 `fu_clause` **209 条**；十干/十二支体象入 `technique_catalog` 22 条。

### 2.5 神峰通考（正文全覆盖，0 缺失）
- `classic_text` 存 **128 章**（叙/五星正谬说/合婚说/病药说/四病四药说/诸格/诸赋/喜忌继善等）。
- 逐节反查补测：`论大运`、`论太岁`、`金不换看命绳尺`、`金不换骨髓歌断`、`子平举要`、`江湖摘锦`、`男命小运定局`等在本库中**正文均在**——
  - `夫大运者，就月上起`／`太岁，乃天中天子` → `classic_text` CT_SF_59；
  - `甲日子提为印绶`（金不换骨髓歌）→ CT_SF_61；
  - `男命小运定局` → CT_SF_67；
  - `用之为官不可伤`（江湖摘锦）→ `fu_clause` FU_YH_095；`造化先须看日主`（子平举要）→ FU_YH_004。
  即正文并入相邻章或 `fu_clause`，**内容零丢失**；病药/动静/盖头/六亲诸说另入 `theory_clause` 12 条。

### 2.6 穷通宝鉴（120 逐月全备，0 缺失）
- `classic_text` 存 **142 章**（韦序/造化元钥序/原序/五行总论/论木火土金水/四季月土 + 10 干逐月 + 原跋）。
- 核心矩阵 `tiaohou_clause` **120 条 = 10 干 × 12 月，组合无缺、clause_text 无空**（逐条校验）。
- 观察（非缺失）：卷目另列「二月丁火、五月丁火」二节，整理稿正文将其实并入相邻章（`二月丁火` 结论见正月丁火章、`五月丁火` 见四月丁火章），故 `classic_text` 未单列标题；但**其调候用神在 `tiaohou_clause` 丁×卯、丁×午 均已单条存在并有文**，运行期不缺。

---

## 三、运行期条文体量矩阵（全量实算）

| 表 | 行数 | 含义 / 完整性 |
|---|---|---|
| `day_hour_clause` | **720** | 60 日柱 × 12 时支，**distinct 720，无缺组合** |
| `tiaohou_clause` | **120** | 10 干 × 12 月，**全备** |
| `pattern_catalog` | 102 | 具名格局名录（三命通会卷五/卷六等） |
| `deity_rule` | 186 / 78 类 | 神煞起例全库 |
| `ten_god_map` | 100 | 十神映射（10×10） |
| `category_verdict` | 20 | 分类断语 |
| `fu_clause` | 209 | 成篇赋/歌/诀（渊海子平+神峰通考） |
| `theory_clause` | 12 | 病药/动静/盖头/六亲诸说 |
| `dts_clause` | 26 | 滴天髓象论 |
| `auspice_clause` | 25 | 三命通会卷四时地/孕生男女 |
| `technique_catalog` | 66 | 体象/神趣八法/总论等技法 |
| `lady_fortune_catalog` | 17 | 女命分类 |
| `ziping_clause` | 47 | 子平真诠全篇 |
| `nayin` / `hidden_stem` / `sexagenary_nature` | 30 / 28 / 60 | 纳音/藏干/六十甲子性质 |

---

## 四、本体方式核验（无硬编码）

### 4.1 取数全经本体
- 全工程 **JDBC / JPA / JPQL / @Query 零命中**；唯一 DB 出口 `executeAboxQuery`（OBDA 虚拟图），运行期 **15 条 SPARQL** 全部走 Ontop 本体查询。
- 条文族统一经 `readReadingFamily()/pickReading()/readPatternFamily()/pickYongShen()/queryClauses()` 取本体：`classic_text / ziping_clause / fu_clause / dts_clause / theory_clause / auspice_clause / technique_catalog / lady_fortune_catalog / pattern_catalog / category_verdict / day_hour_clause / tiaohou_clause / deity_rule / ten_god_map` **均在运行期以 SPARQL 读取**，非内联。

### 4.2 TBox 类齐备
`reading:ReadingItem / CategoryFortune / DayHourClause / StemBranchAuspice / MonthBranchDayStemAuspice / KinshipTheory / LadyFortune / TimePlaceFortune / ProgenyPrediction / DynamicStaticTheory / HeadCoveringTheory / CategoryVerdict`，`pattern:Pattern / ImagePattern / AssimilationPattern / DirectionPattern`，`tengod:TenGodMap`，`deity:DeityRule`，`yongshen:TiaohouClause / BingYao`，`calendar:SolarTermInstant` —— **均在 OWL 中 `owl:Class` 声明**。

### 4.3 OBDA 映射谓词与代码查询一致
- `fu_clause_all → reading:CategoryFortune`、`ziping_clause_all/classic_text_all/technique_catalog → reading:ReadingItem`、`auspice_* → reading:StemBranchAuspice/TimePlaceFortune/ProgenyPrediction`、`theory_* → reading:DynamicStatic…/HeadCovering…/Kinship…/yongshen:BingYao`、`pattern_catalog/dts_* → pattern:Pattern/子类`、`lady_fortune_catalog → reading:LadyFortune`、`category_verdict → reading:CategoryVerdict`、`day_hour_clause → reading:DayHourClause`、`tiaohou_clause → yongshen:TiaohouClause`、`deity_rule → deity:DeityRule`、`ten_god_map → tengod:TenGodMap`。
- 每条映射均含 `reading:readingText`（← `clause_text/meaning`）、`reading:readingSource`（← `source_note`）、`reading:readingCategory`（← 书名/分组），**与 `readReadingFamily()` 的 SPARQL 变量一一对应**，取数不落空。

### 4.4 硬编码复查
- Java 字符串字面量 1504 处，含 ≥20 汉字者 59 条：系统提示词 12 / 日志 3 / 白话模板兜底 39 / 程序算理标签 5，**无一条为原著条文正文**；条文一律经 OBDA 取 `?text ?source`。
- 程序算理（天干地支五行、五合六冲三刑六害、五虎遁/五鼠遁/JDN 起柱、旺衰多因子、起运、十神生克算法）按裁定⑰·铁律13 外置在库、算理在程序，**属既定双轨设计，非伪造数据**。

---

## 五、非缺陷性观察（如实标注）

1. **穷通宝鉴「二月丁火/五月丁火」**：整理稿未单列标题（正文并于邻章），`classic_text` 随底本 142 章；但 `tiaohou_clause` 已单条覆盖 丁×卯、丁×午，**运行期不缺**。
2. **神峰通考部分小节**（论大运/论太岁/金不换歌/子平举要/江湖摘锦/男命小运）并入邻章或落 `fu_clause`，正文在库，**无内容丢失**。
3. **渊海子平 5 幅图**（天降干支图等）以图录形式存 `fu_clause`，与文字章分列，**非缺失**。
4. 以上均属「底本标题粒度 ≠ 存储章粒度」的编排差异，不涉及条文遗漏。

---

## 六、校验

- 完整 `mvn -o -q test` **exit 0**；**63 项全绿（0 失败/0 错误/0 跳过）**，用时约 1 分 3 秒；`axioms=1841`、`individuals=118`；OBDA 映射 41、物理表 25 全覆盖。
- 六书标题反查脚本：三命通会 298=298（缺 0）、子平真诠 47=47（缺 0）、滴天髓 TOC 全在位、渊海子平 293 类（缺 0，5 图入 fu_clause）、神峰通考 128 章（疑似缺标题正文均在库）、穷通宝鉴 142 章 + 调候 120 全备。

### 6.1 端到端复核中捕获并修复的运行期缺口（`category_verdict` 表）
- 完整套件复核时，端到端 `SiZhuBaZiBpmnInMemoryTest`（生产 Worker 驱动全流程）原报 9/11 超时（`Process not completed`，1629s），Worker 根错为 `OntopMappingResolver 加载失败 → Cannot find relation 'sizhubazidb'.category_verdict`。
- **根因**：DDL（25 表）与 data.sql（20 行）均含 `category_verdict`，但**实际运行的 MySQL `sizhubazidb` 只建了 24 表、独缺该表**；Ontop 映射须整体解析，一表缺失致整个 SPARQL 条文层加载失败，取数型 serviceTask 全抛错、流程不完成。
- **修复**：按 DDL 建表 + 按 data.sql 灌 20 行（事业2/财运4/婚姻4/寿夭2/飘荡3/合婚2/劝勉2/女命1），与源一致；验证 `SiZhuBaZiBpmnInMemoryTest` 由 9 失败/1629s → **11/11 全绿 30.8s**，映射失败关键字归零；`SiZhuBaZiBpmnRealEngineTest` 因外部 Camunda 未常驻，按前置假设正常跳过（0 项，非失败）。
- 说明：此为部署库漂移（活库未随 DDL 同步），非条文遗漏、非本体设计缺陷；补建即恢复，已在审计报告 §13.6 详记。

**结论：六书逐卷逐节无缺失；全部条文经本体（OBDA→SPARQL）取用，无原著内容硬编码；部署库缺失的 `category_verdict` 表已补建，端到端全绿。**
