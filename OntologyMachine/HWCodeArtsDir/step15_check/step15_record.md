# 第 15 步 · 六典籍内容覆盖补全 — 执行留痕

**日期**：2026-10-30  **模块**：`OntologyFrameworkSiZhuBaZi`
**触发**：客户「再次检查是否 5 本书（经澄清＝6 本）内容全部包括在本体之中，缺少则补充完整，不允许编造」。
**裁定（客户）**：①G3 日时断＝建表＋忠实抽取＋OCR 校正、源本真缺者标【待考】；②G8 调候＝建表＋逐节归纳用神、存疑标【待考】；③定稿后一次性继续 SKOS／文档／回归。

## 核查
- 逐书篇章目录与覆盖矩阵：`六典籍内容覆盖核查报告.md`。
- 共 **8 类篇章级缺口 G1~G8**（详见《本体核心设计》§第15步）。

## 落地改动
| 类别 | 文件 | 改动 |
|---|---|---|
| TBox | `sizhubazi-yongshen.owl` | +3 类（BingYao/IdleGod/TiaohouClause）+5 数据属性 |
| TBox | `sizhubazi-reading.owl` | +9 类 +3 数据属性 |
| TBox | `sizhubazi-pattern.owl` | +7 类 |
| TBox | `sizhubazi-fortune.owl` | +1 类 |
| TBox 备份 | `HWCodeArtsDir/sizhubazi-tbox.owl.presplit.bak` | 同步新增 20 类 |
| ABox | `sizhubazi-abox.owl` | +出处标注 7、+代表性个体 5、+神煞名录 7；Case_Demo01 +3 三元组 |
| DB | `ontology/database/createdb_tables.ddl` | +2 表（day_hour_clause / tiaohou_clause），共 13 张 |
| DB | `ontology/database/data.sql` | 神煞 95→104；+日时断 720、+调候 120（脚本 `gen_sizhubazi_full_data.py` 生成） |
| DB | `ontology/database/sizhubazi.obda` | +2 映射（reading:DayHourClause / yongshen:TiaohouClause）+ yongshen 前缀 |
| SKOS | `ontology/sizhubazi-terminology.ttl` | 概念 282→310（+28），altLabel 97 |
| 测试 | `SiZhuBaZiOntologyComplianceTest.java` | P-6/P-7 增补新表/映射/条文库断言；神煞 95→104 |
| 文档 | 顶层设计 / 本体核心设计 / SKOS 词汇表设计 | 变更记录 + 计数同步 |

## 抽取与【待考】留痕（★8/9/10，禁编造）
- **OCR 还原（16 条）**：源文本《三命通会.txt》为 OCR 本，如「己亥日**乙丑**时」→「己亥日**己巳**时」、「**两**子日」→「丙子日」、「戊戌日**申寅**时」→「戊戌日**甲寅**时」、「辛巳日**在**辰时」→「辛巳日**壬辰**时」等，按上下文就地还原。
- **源本真缺（3 条）**：乙巳日丁亥时、己酉日庚午时、戊子日己未时 → 建【待考】占位。
- **G8**：散文体，首句无明据者标【待考】。

## 回归
- `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test` → **Tests run: 32, Failures: 0, Errors: 0**，BUILD SUCCESS（日志 `step15_mvn_regression.log`）。
- 全部 15 个 *.owl XML 解析通过；术语校验 `verify_terminology.py` 全绿；OBDA 无 `#` 注释行。
