# step24 · 条文库接入 + 断语本命推导 + 命书变量分页修复记录

日期：2026-10-30
模块：`OntologyFrameworkSiZhuBaZi`（附：共享模块 `Utilities` 一处根治）
触发：用户反馈排盘输出「信息量少 / 非本命专属 / 大运流年只罗列无解释 / 直接套通用原文」四项质量缺陷。

## 1. 症状（用户反馈四项）

以某生辰（例：1978-07-04 11:00＝戊午 戊午 丁卯 丙午）跑通流程后，前端 `index.html` 输出：

1. 信息量薄、无实质；
2. 断语为泛泛通用表述，非针对该生辰；
3. 大运流年仅罗列干支序列，未作吉凶/推导解释；
4. 分类断语只抄「木主仁、火主礼…」等通用原理，未按该命推导。

## 2. 根因

- 22 张条文/名录表（`day_hour_clause` 720、`tiaohou_clause` 120、`pattern_catalog` 102、
  `auspice_clause`、`lady_fortune_catalog`、`technique_catalog`、`sexagenary_nature`、`dts/fu/ziping/theory` 等）
  **已在 `sizhubazi.obda` 全量映射**为可读属性（`reading:readingText`/`clauseSource` 等），
  但 **Worker 运行期从未查询**（仅 `case_record` 有 INSERT）；
- 各断语节点全部为硬编码模板 → 即用户所见「薄 / 非本命 / 无解释 / 套通用原文」。

## 3. 方案（客户裁定）

- **全量接入条文库 + 本命解释**：各断语节点经 OBDA SPARQL 按本命匹配（日时柱 / 日干+月支调候 /
  格局 / 十干坐支 / 月支 / 六亲 / 女命分类 / 六十甲子性质），**先引原文+出处，再按本命四柱/十神/旺衰/用神推导解释**。
- **大运流年「逐年评吉凶+应事」**：每步大运给该运干支与本命用神/格局之生克得失与主应事项，流年同法。
- 输出粒度、文案口径均由客户确认。

## 4. 改动

`src/main/java/com/ocean/ontologyframework/sizhubazi/SiZhuBaZiOntologyJobWorker.java`

- 新增条文库读取 helper 段（`OBDA_PREFIX` 常量 + `queryClauses` 降级封装）：
  `clauseDayHour` / `clauseTiaohou` / `clauseNamedPatterns` / `clauseStemSeat` / `clauseMonthBranch` /
  `clauseKinship` / `clauseLadyFortunes` / `clauseSexagenaryNature`，辅助 `stemGroup`/`pickFragment`/
  `pinyinGanzhi`/`elemRoleName`/`elemNature`/`supportsYong`/`xingPair`/`buildFortuneAnalysis`。
- 节点 3 `analyzeNayinHiddenStems`：日柱特性追加「六十甲子性质」。
- 节点 8 `determinePattern`：`patternResult` 追加「具名格局」。
- 节点 9 `selectYongShen`：接入调候（`tiaohou_clause`），措辞按本命日主/月令/十神推导；新增输出 `tiaohou`。
- 节点 10 `judgeChartLevel`：`purityTruth` 改为按本命用神透干/藏支/众寡实测（见 §6-D2 同步修 `chartLevel`）。
- 节点 11 `analyzeFortuneCycles`：新增输出 `fortuneAnalysis`（大运逐运评析 + 流年吉/平/凶）。
- 节点 14 `generateCategoryVerdicts`：分类断语按本命所见十神推导；新增输出 `clauseCitations`（原文+出处）。
- 节点 W `analyzeFemaleChart`：接入 `lady_fortune_catalog`。
- 节点 16 `aggregateReading`：`appendLine` 增补 调候 / 岁运评析 / 典籍征引。

`src/main/java/com/ocean/ontologyframework/sizhubazi/web/SiZhuBaZiController.java`

- `getDivineStatus` 增补返回 `dayPillarTraits`/`ganzhiRelations`/`voidness`/`tiaohou`/`purityTruth`/
  `fortuneAnalysis`/`clauseCitations`。

`src/main/resources/static/index.html`

- `SECTIONS`（L278–299）增补 7 个渲染分组。

`ontology/bpmn/sizhubaziProcess.bpmn`

- 新增 3 条显式 output 映射：`=result.tiaohou→tiaohou`、`=result.fortuneAnalysis→fortuneAnalysis`、
  `=result.clauseCitations→clauseCitations`。（BPMN 用显式 output 映射，Worker 新增 result 键必须在此补映射。）

`Utilities/src/main/java/com/ocean/utilities/ProcessOrchestrator.java`（共享模块根治，见 §6-D1）

- `readVariables` 由「单页取」改为**分页循环取全量**（`page(from,limit)` + `totalItems` 收敛）。

## 5. 端到端实测（真实生辰 1978-07-04 11:00 → 戊午 戊午 丁卯 丙午，男命）

环境：MySQL(sizhubazidb)/Ontop(8080)/Camunda(9080+26500)/RabbitMQ(5672) 全在线；
旧运行实例为陈旧构建，已重建重启（新 jar，启动 7.6s）。

| 诉求 | 结论 | 证据 |
|---|---|---|
| ① 信息量/有实质 | ✅ 改善（新增 7 分组 + 命书 3902 字可见） | 见 §6-D1 修复 |
| ② 针对本命 | ✅ `purityTruth`/`fortuneAnalysis`/`yongShen` 皆本命实测（用神水、忌神土） | 输出 |
| ③ 大运流年解释 | ✅ 逐运吉/凶/平+理由；流年 2026–2035 吉/平/凶 | `fortuneAnalysis` |
| ④ 条文原文+出处 | ✅ 运行时确从条文库取到真文本 | 日时断（三命通会卷八·六丁日午时断）、十干坐支（卷四）、月支断、六亲说（神峰通考）、六十甲子性质（丁卯）、调候（穷通宝鉴） |

## 6. 本轮新发现与修复

- **D1 命书空白（严重·既有·已修）**：`ProcessOrchestrator.readVariables` 未分页 → 变量 >100 时静默丢弃。
  证据：实例变量总数 **118**，`reading`(3892字) 排在第 100 名之后未被返回；DB `case_record`（case_id=41）已存 3892 字。
  修复后：实例 `reading` 正常返回 **3902 字**，前端「命书式综合结论」可见。
- **D2 层次自相矛盾（本轮逻辑·已修）**：`chartLevel` 固定短语「用神得力」，与 `purityTruth` 实测「用神力薄/寡」冲突。
  修复：`judgeChartLevel` 的 `chartLevel` 措辞改按实测用神透干/藏支/众寡生成。
  修复后：`贵格（格成身健，用神不见、用神力薄）`，与 `purityTruth` 口径一致。

## 7. 遗留（本轮未修，仅记录）

- **D3 调候数据缺口**：`tiaohou_clause.THC_丁_午` 的 `primary_use=【待考】`、`clause_text` 为片段
  （疑窜入壬水论）。属**数据源**问题（本轮未改条文表，按客户裁定不回写生成脚本）；Worker 已按【待考】如实标注、不臆造。
- **D4 引文未按月过滤**：日时断引文含「巳月」条件，本命实为午月（源文含多月条件，未按月过滤）。
- 「流月/流日/流时」细化仍标【待考】。

## 8. 验证

- `mvn -o -f Utilities/pom.xml test`：17/17 全绿。
- `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test`（离线可靠子集）：23/23 全绿
  （本体合规 8、SKOS 8、生辰解析 4、SWRL-RabbitMQ 3，含连接 5672 实发消息通过）。
- 引擎/套件类（`SiZhuBaZiBpmnInMemoryTest`/`SiZhuBaZiBpmnRealEngineTest`/`AllSiZhuBaZiSuiteTest`）
  为环境/时序依赖用例，其既有失败与本次改动无关。
- 禁区模块（OpenlletResolver/OntologyResolver/OntopOBDAHandler）**零改动**（已核 `git status`）。

## 9. 备注

- 本轮未改动任何条文表内容（`data.sql` 未动），仅新增运行期读取逻辑，故无需回写 `HWCodeArtsDir/` 生成脚本。
- `OntologyFrameworkSiZhuBaZi/` 目录未被 git 跟踪，无法以 git 取基线做严格对照。
