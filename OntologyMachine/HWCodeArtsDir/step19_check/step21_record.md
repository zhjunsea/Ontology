# 第 21 步实施记录 · 穷通宝鉴 P9/P10 补录 · 2026-10-30

## 1. 背景
第五轮全量复检发现穷通宝鉴两条真缺口：
- **P9**：十干分季总论 13 条（正文散文未落库）。
- **P10**：五行分论 6 篇（论木/论火/论土/四季月土性质之分别/论金/论水）——系第 20 步 **P7 声明范围「五行总论附论木/火/土/金/水」的实现补全**。

另修正两处**行号错误**（★禁编造）：
- P7「五行总论」：104 → **108**。
- P9 13 条：初记行号整体偏移，按原文改为实际行号（见下）。

## 2. 生成源改动
文件：`HWCodeArtsDir/sizhubazi_catalog.py`（`TECHNIQUE_CATALOG`）
- 修正 `TEC_WuXingZongLun.source_note` 行号 104→108。
- 修正 13 个 `TEC_QT_*`（总论）`source_note` 行号：Jia_Chun 189 / Jia_Xia 222 / Jia_Qiu 248 / Jia_Dong 293 / Yi_Chun 329 / Yi_Xia 365 / Yi_Qiu 393 / Bing_Chun 469 / Bing_Xia 513 / Wu_Dong 831 / Ji_Chun 849 / Ji_Qiu 907 / Ji_Dong 930。
- 修正 `TEC_QT_Ji_Qiu`（三秋己土总论）`meaning`：按原文「万物收藏之际、内实外虚、寒气渐升；须火温之、癸水润之……丙癸不可离」重写（原摘要误掺他处内容）。
- 新增 P10 6 条：`TEC_QT_WuXing_Mu`(论木,114) / `TEC_QT_WuXing_Huo`(论火,131) / `TEC_QT_WuXing_Tu`(论土,145) / `TEC_QT_SiJiYueTu`(四季月土性质之分别,159) / `TEC_QT_WuXing_Jin`(论金,161) / `TEC_QT_WuXing_Shui`(论水,175)；category=五行分论。

## 3. 产物重生成
- `python gen_sizhubazi_full_data.py` → `ontology/database/data.sql`
  - counts：tech=**66**（其余不变：pattern=99/lady=17/fu=208/dhc=720/thc=120/nature=60/deity=186/…）。
- `createdb_tables.ddl`：仅同步第 21 步注释（结构不变，仍 21 张）。
- `sizhubazi.obda` / `sizhubazi.properties`：**无需改动**（`technique_catalog` 映射为全表 `SELECT`）。

## 4. DB 重灌与核验
```
mysql -uroot --default-character-set=utf8mb4 < createdb_tables.ddl
mysql -uroot --default-character-set=utf8mb4 sizhubazidb < data.sql
```
实测：technique_catalog=**66**（其中 category='五行分论' 6 条）、pattern=99、lady=17、fu=208、dhc=720、thc=120。

## 5. 测试
- `SiZhuBaZiOntologyComplianceTest.java`：
  - `techRows` 断言 60→**66**（注释更新）。
  - 新增 P10 6 个 `TEC_QT_*` 入库断言（P9 的 13 个断言已在前序存在）。
- 回归：`mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test -Dtest='SiZhuBaZiOntologyComplianceTest,SiZhuBaZiSkosTerminologyTest'`
  - **Tests run: 16, Failures: 0, Errors: 0**（Compliance 8/8、Skos 8/8）。
- 模块全量 `mvn test`：Tests run: 56, Failures: 12。**12 例全部为既有环境失败**——`AllSiZhuBaZiSuiteTest`(9) / `SiZhuBaZiBpmnInMemoryTest`(2) / `SiZhuBaZiBpmnRealEngineTest`(1)，根因均为 `ProcessOrchestrator.deployIfAbsent` 返回 false（Camunda/Zeebe 网关未运行，`Utilities/ProcessOrchestrator.java:116,152` 捕获异常返回 false），**与本轮 data.sql/测试改动无关**（离线合规/SKOS 校验全绿；本改动未触及 BPMN/引擎源码）。

## 6. 文档同步
- `design/SiZhuBaZi应用-本体核心设计.md`：§8 表清单 `technique_catalog` 60→66；全集规模 技法篇章 60→66；第 21 步小节（P9/P10 + 行号）。
- `design/SiZhuBaZi应用-顶层设计.md`：新增 v1.10 版本行（P9/P10，47→66）。
- `design/SiZhuBaZi应用-SKOS词汇表设计.md`：新增 v1.8 版本行（术语表不变，仍 314）。

## 7. 备份
- `HWCodeArtsDir/data.sql.bak_prestep21`（改动前 data.sql）。

## 8. 禁区
- `OpenlletResolver` / `OntologyResolver` / `OntopOBDAHandler`：**零改动**。
