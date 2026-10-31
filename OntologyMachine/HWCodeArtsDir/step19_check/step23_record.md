# 第 23 步实施记录 · 《子平真诠》47 篇正文补录（新增专表 `ziping_clause`）· 2026-10-30

## 1. 背景
第六轮正文级复核（正文实体级：`r8_scan_exact.py` + `r8_patterns.py` + 逐书回读）发现——
六书中 **唯《子平真诠》47 篇正文整体未落库**（`data.sql` 中「真诠」出现 **0** 次），仅以概念层承载
（`sizhubazi-yongshen.owl` 类 / SKOS 概念 + `源: 子平真诠·论XX` 出处标注）；
其余五书均有专表承载正文：神峰通考→`theory_clause`、滴天髓→`dts_clause`、穷通宝鉴→`tiaohou_clause`、
三命通会→`auspice_clause`+`day_hour_clause`、渊海→`fu_clause`。

**客户裁定**：「新增子平真诠专表，逐篇录入 47 篇正文 + 出处行号，与其余五书对齐」。

## 2. 数据来源与抽取（★8/9/10 禁编造）
- 文本：`design/四书全文整理/子平真诠.txt`（共 436 行，沈孝瞻《子平真诠》原文）。
- 结构：L3~L50 为目录（一、…四十七、）；L51 起为正文，每篇以「序号、篇名」标题行定位，
  正文＝标题行至下一篇标题之间的非「子平真诠评注」（页眉，共 48 处）行。
- 抽取 47 篇：首篇「论十干十二支」**L52**、末篇「论杂格」**L424**；合计约 2.47 万字，最长 1514 字。
- 生成源：`HWCodeArtsDir/sizhubazi_ziping.py`（`ZIPING_CLAUSES`，47 条；`clause_text` 照录原文不截断，
  `source_note` 逐篇记实际行号）。

## 3. 本体增量
- **TBox**：**无新增类/属性**——目标类 `reading:ReadingItem` 既有（与 `technique_catalog` 同口径）。
- **DB（21→22 张表）**：
  - 新增 `ziping_clause`（`clause_id` PK / `clause_group`=「子平真诠」/ `subject`=篇名 / `clause_text` **LONGTEXT** / `source_note`）。
  - `createdb_tables.ddl`：新增第 22 表 + 头部注释补「第 23 步」行。
  - `sizhubazi.obda`：新增映射 `ziping_clause_all`（`reading:{clause_id} a reading:ReadingItem`；
    `reading:readingCategory`=clause_group / `reading:readingSource`=source_note / `reading:readingText`=clause_text）。
  - `sizhubazi.properties`：无需改动。

## 4. 产物重生成
```
python HWCodeArtsDir/gen_sizhubazi_full_data.py   # gen 导入 ZIPING_CLAUSES，新增 9j 段
```
- counts：ziping=**47**（其余不变：pattern=102/tech=66/fu=208/dts=26/theory=12/auspice=25/…）。

## 5. DB 重灌与核验
```
mysql -uroot --default-character-set=utf8mb4 < createdb_tables.ddl
mysql -uroot --default-character-set=utf8mb4 sizhubazidb < data.sql
```
实测：ziping_clause=**47**（`clause_group`='子平真诠'，ZP_01~ZP_47）；表数 **22**；pattern=102、tech=66、fu=208。

## 6. 测试
- `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test -Dtest='SiZhuBaZiOntologyComplianceTest,SiZhuBaZiSkosTerminologyTest,SiZhuBaZiBirthParsingTest,ScratchInconsistencyTest,ScratchParseTest'`
  - **Tests run: 20, Failures: 0, Errors: 0**（Compliance 8/8、Skos 8/8、BirthParsing 4/4）。
- 合规断言更新（`SiZhuBaZiOntologyComplianceTest`）：DDL 新增 `ziping_clause`（合计 22 张）、
  OBDA 新增 `ziping_clause`+`reading:ReadingItem`、data.sql 新增 **47** 行数 + 首/末条（`ZP_01`/`ZP_47`）断言。
- 重跑 `r8_scan_exact.py`：ENTRY 1968→**2015**（+47）；子平真诠、穷通宝鉴 0 未命中；残留 47→46（滴天髓「配合」因子平真诠「论十干配合性情」子串命中而减 1，属扫描器子串宽松，非本体口径变化）。

## 7. 文档同步
- `design/SiZhuBaZi应用-本体核心设计.md`：§8 表清单新增 `ziping_clause` 行；§8 全集规模补「《子平真诠》正文 47」；新增「第 23 步」小节。
- `design/SiZhuBaZi应用-顶层设计.md`：新增 v1.12 版本行。
- `design/SiZhuBaZi应用-SKOS词汇表设计.md`：新增 v1.9 版本行（术语表不变，仍 314）。

## 8. 备份
- `HWCodeArtsDir/data.sql.bak_prestep23`（改动前 data.sql）。

## 9. 禁区
- `OpenlletResolver` / `OntologyResolver` / `OntopOBDAHandler`：**零改动**。
