# 第 17 步 · 第二轮正文级复核 I1~I3 补录 · 留痕记录（2026-10-30）

> 触发：客户「再次仔细检查 6 本书……不仅看目录，还要查看**具体内容**，看本体是否包含所有内容，不能有遗漏」（第二轮，下沉至「**具名条目**」层）。
> 复核报告：`HWCodeArtsDir/deep6_check/六典籍内容级复核报告-第二轮.md`。
> 裁定：客户「**全部补录（I1~I3）**」。范围＝六书（四部原著：渊海子平/子平真诠/三命通会/滴天髓 ＋ 两部参照典籍：穷通宝鉴/神峰通考）。

## 1. 缺口与处置

| 编号 | 缺口 | 处置 | 数据增量 |
|---|---|---|---|
| I1 | 本体 BPMN 节点 8 / 顶层设计 D·17 已列「金神/时墓」，`pattern_catalog` 无对应（硬缺口） | `sizhubazi_catalog.py` 追加 `PAT_JinShen` / `PAT_ShiMu` | `pattern_catalog` 95→**97** |
| I2 | F·25/26/27 枚举外、典籍正文另载细煞（三命通会卷三·总论诸神煞：破煞/返本煞/阴阳煞/淫欲妨害煞[八专九丑]/孤鸾寡鹊煞/阴阳差错煞/桃花红艳煞；神峰通考·吉神类：官贵学馆/红鸾/天喜/解神；凶神类：隔角/伏吟/返吟/破军/悬针/平头/紫暗星/流霞杀/冲天杀/五鬼杀/红艳杀/吞焰煞/三丘五墓/马前神煞[攀鞍/年杀/月杀]/流年星耀/太白星/斧劈星/孤虚神） | `sizhubazi_catalog.py` 新增 `EXTRA_DEITY2`（33 条），`gen_sizhubazi_full_data.py` 注入 | `deity_rule` 153→**186**（+33） |
| I3 | G1/G2/G5/G6/G7 第 15 步仅建 OWL 类 + 占位 Demo，正文断语未入库 | 新建 `sizhubazi_reading.py`（AUSPICE/THEORY/DTS 三类条文），生成器接入 → 新增 3 表 + OBDA 暴露（仿 G3/G8 口径） | `auspice_clause` 25 / `theory_clause` 12 / `dts_clause` 26 |

## 2. 产出（★回写生成源，未手改产物）

- **生成源**：`HWCodeArtsDir/sizhubazi_catalog.py`（I1/I2）、`HWCodeArtsDir/sizhubazi_reading.py`（I3，新建）、`HWCodeArtsDir/gen_sizhubazi_full_data.py`（导入/清空/INSERT/计数）。
- **产物（重生成）**：
  - `ontology/database/data.sql`（重生成）：`deity=186 pattern=97 auspice=25 theory=12 dts=26`。
  - `ontology/database/createdb_tables.ddl`：新增 3 表（18/19/20），共 **20** 张；表头注释 + DROP 同步。
  - `ontology/database/sizhubazi.obda`：补 `pattern:`/`fortune:` 前缀声明 + **18** 个 I3 映射（按 `clause_group` 分派到对应 OWL 类）。
    - reading 域 → `reading:readingCategory/readingSource/readingText`；
    - pattern 域 → `pattern:patternName/patternClass/patternSource/patternMeaning`；
    - yongshen 域 → `yongShenRole/yongShenMethod` + `rdfs:comment`；
    - fortune 域 → `rdfs:label` + `rdfs:comment`。
- **合规测试**：`SiZhuBaZiOntologyComplianceTest.java` —— 神煞起例断言 153→**186**、具名格局 95→**97**，新增 3 表行数断言 + DDL/OBDA 工件断言 + `pattern:`/`fortune:` 前缀断言 + `<pattern:`/`<fortune:` 防回归守卫。
- **SKOS**：**不变（314）**——本轮未增删 OWL 类/属性；复跑 `gen_sizhubazi_terminology.py`+`verify_terminology.py` 确认。
- **设计文档**：顶层设计（变更记录 v1.7）、本体核心设计（§8 表清单 + 全集规模 + 第 17 步章节）、SKOS 词汇表设计（闸门⑥留痕 v1.5）同步。

## 3. 回归与核验

- `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test` → **`Tests run: 32, Failures: 0`**（本体合规 8/8、SKOS 8/8；BPMN/RabbitMQ 依环境跳过）。
- 禁区模块（`OpenlletResolver` / `OntologyResolver` / `OntopOBDAHandler`）**零改动**。
- `data.sql` 新表行数与无重复 ID 已核验（`deity=186/pattern=97/auspice=25/theory=12/dts=26`）。

## 4. 备注

- I3 未删除 ABox 中既有 `*_Demo` 占位个体（`reading/StemBranchAuspice_Demo` 等 5 个）——其 IRI 与真条文（`AC_*`/`TC_*`/`DC_*`）不冲突，保留无害；如需清理须走「改本体（ABox）→ 客户确认」。
- `sizhubazi.obda` 既有 `pattern_catalog` 映射此前引用未声明的 `pattern:` 前缀，本轮补齐声明（潜在修复）。
