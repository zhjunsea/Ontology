# step26 · 第九轮正文内容级复核 · 六典籍正文全文补齐记录（classic_text）

日期：2026-10-30
模块：`OntologyFrameworkSiZhuBaZi`

## 1. 触发

客户第 9 轮指令：「**第九轮同意**，完成后请再次仔细检查 6 本书，**不仅仅看 6 本书的目录，还要查看 6 本书的具体的内容**，看是否本体中包含了所有的内容，**不能有遗漏**」。

## 2. 本质区别（★关键）

- 既往第 3~8 轮所谓「正文级」复核，判据实为**篇章/条文级**：核对篇/章标题、具名格名、神煞名是否作为名录条目入本体（脚本 `r8_scan_exact.py`），据此历轮均判「0 真缺口」。
- 本轮首次做**整文内容级**：把每本书正文**逐句**切分，与「DB 全表文本 + OWL label + SKOS pref/alt」全量 haystack 做子串匹配，量化每书正文**实际入库比例**。
- **结论先行**：**标题在库 ≠ 正文在库**。首测合计覆盖率仅 **48.8%**，散文体正文大量未入本体。

## 3. 首测覆盖率（缺口暴露）

| 书 | 句数 | 命中 | 覆盖率 |
|---|---|---|---|
| 渊海子平 | 8266 | 7045 | 85.2% |
| 三命通会 | 6551 | 1538 | 23.5% |
| 子平真诠 | 745 | 739 | 99.2% |
| 滴天髓 | 592 | 40 | 6.8% |
| 神峰通考 | 5032 | 2351 | 46.7% |
| 穷通宝鉴 | 3042 | 120 | 3.9% |
| **合计** | **24228** | **11833** | **48.8%** |

（假阴性已排除：滴天髓「天道/人道」原注、穷通宝鉴「三秋甲木」「韦序」、神峰通考开篇经归一化子串匹配**确不在库**；已补录项命中，方法有效。）

## 4. 裁定与实施

客户裁定：**六书逐篇全文入库**（按《子平真诠》第 23 步先例，新增专表照录），并列明**六书均不得有遗漏**。

- **新增专表 `classic_text`**（第 23 表）：`text_id` PK / `book_name` / `chapter` / `clause_text` **LONGTEXT**（全文不截断）/ `source_note`（整理本实际行号）。
- **入库条数 921**：滴天髓 62 / 穷通宝鉴 142 / 神峰通考 128 / 三命通会 298 / 渊海子平 291（空篇 0）。《子平真诠》47 篇已由第 23 步 `ziping_clause` 承载，六书正文全文合计 **968** 篇。
- **生成源**：`HWCodeArtsDir/sizhubazi_classic.py`（`classic_texts()`，运行时从 `design/六书全文整理/*.txt` 照录；切篇规则见核心设计 §第 26 步）；`data.sql` 经 `gen_sizhubazi_full_data.py` 段「9k」重生成。
- **DDL**：`createdb_tables.ddl` 新增第 23 表；并补 `ziping_clause` / `classic_text` 的 `DROP TABLE IF EXISTS`。
- **OBDA**：`sizhubazi.obda` 新增映射 `classic_text_all`（→ `reading:ReadingItem`）。
- **合规测试**：`SiZhuBaZiOntologyComplianceTest` 补 `classic_text` 断言（DDL/OBDA/data.sql 三处 + `classicRows`=921）。

## 5. 回归与覆盖率复检

- 离线回归 `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test -Dtest='SiZhuBaZiOntologyComplianceTest,SiZhuBaZiSkosTerminologyTest,SiZhuBaZiBirthParsingTest'` → **20/20 全绿**。
- 重灌 DB：**23 表**；`classic_text` 各书计数 62/142/128/298/291 = **921** ✓。
- 覆盖率复检（`r4_dump_db_text.py` + `r9_content_blocks.py`）：

| 书 | 覆盖率 |
|---|---|
| 渊海子平 / 三命通会 / 滴天髓 / 神峰通考 | **100.0%** |
| 穷通宝鉴 | 98.9% |
| 子平真诠 | 99.2% |
| **合计** | **99.8%** |

- 未命中残余经逐行核验**全为目录/篇名噪声**：穷通宝鉴 33 句系卷目索引行（如「1、韦序 4、五行总论 7、论土 10、论水」），子平真诠 6 句系篇名标题行（正文在下一行且已覆盖）→ **六书 0 真内容缺口**。

## 6. 边界与合规

- **TBox/SKOS 不变**（仍 314）——仅内容层补录，沿用 `reading:ReadingItem`，无新增类/属性。
- 禁区模块（`OpenlletResolver` / `OntologyResolver` / `OntopOBDAHandler`）**零改动** ✓。
- 留痕：`HWCodeArtsDir/step25_check/六典籍正文内容级复核报告-第九轮.md`。
