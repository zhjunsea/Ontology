# 第 16 步 · 六典籍正文级复核 H1~H4 补录（2026-10-30）

## 0. 触发与裁定
- 触发：客户「再次仔细检查 6 本书，**不仅仅看目录，还要查看具体内容**，看是否本体中包含所有内容，不能有遗漏」。
- 复核报告：`HWCodeArtsDir/deep6_check/六典籍内容级深入复核报告.md`（逐条附典籍篇章与行号，★8/9/10）。
- 裁定：客户「**全部补录（H1~H4）**」。

## 1. 缺口与处置
| 编号 | 缺口 | 处置 | 规模 |
|---|---|---|---|
| H1 | 神煞（F·25/26/27/28 已列名而本体 0 命中） | `deity_rule` 补录 + ABox 补神煞名录个体 22 | 104→**153** |
| H2 | 具名格局（本体仅 3 个体） | 新增表 `pattern_catalog` + `pattern:patternSource/patternMeaning`，OBDA 暴露为 `pattern:Pattern` | **95** |
| H3 | 女命细分（H·40 16+ 分类无个体） | 新增表 `lady_fortune_catalog`，OBDA 暴露为 `reading:LadyFortune`（+`reading:readingSource`） | **17** |
| H4 | 技法/篇章 + 六十甲子性质 | 新增表 `technique_catalog`（→`reading:ReadingItem`）＋ `sexagenary_nature`（追加 `core:SexagenaryCycle`，+`core:sexagenaryNatureText`） | **38 / 60** |

## 2. 变更清单
- **本体（TBox）**：`core.owl`/`pattern.owl`/`reading.owl` 各新增数据属性，共 4：
  `core:sexagenaryNatureText`、`pattern:patternSource`、`pattern:patternMeaning`、`reading:readingSource`。
- **本体（ABox）**：`sizhubazi-abox.owl` 补神煞名录个体 22（贵人吉神 8 / 禄马 4 / 凶煞 8 / 空亡 2）。
- **DB**：`createdb_tables.ddl` 13→**17** 张；`data.sql` 重生成（`gen_sizhubazi_full_data.py`）；
  `sizhubazi.obda` 新增 4 映射（pattern_catalog/lady_fortune_catalog/technique_catalog/sexagenary_nature）。
- **SKOS**：`sizhubazi-terminology.ttl` 重生成，概念 310→**314**。
- **测试**：`SiZhuBaZiOntologyComplianceTest` 神煞断言 104→153，新增格局 95/女命 17/技法 38 行数与 DDL/OBDA 工件断言。
- **生成源**：`sizhubazi_catalog.py`（H1~H4 名录）、`sizhubazi_nature.py`（六十甲子性质，60 条齐全）。
- **设计文档**：顶层设计 v1.6 变更记录、本体核心设计「第 16 步」节 + §8 规划表、SKOS 设计 v1.4。
- **presplit 备份**：`sizhubazi-tbox.owl.presplit.bak`（根 + step15_check）同步新增 4 数据属性。

## 3. 数据来源与 OCR 留痕
- 数据来源＝AI 依六典籍源文本生成，逐条附典籍篇章（★8/9/10）；无出处者标【待考】，不编造。
- OCR 还原：《三命通会.txt》「释六十甲子性质吉凶」3 处讹字就地还原——
  辛卯**未**→辛卯**木**、**丁西**火→**丁酉**火、**已**未火→**己**未火。
- H4 出处关键词已在源文本核验：神趣八法、十干体象、十二支体象、认格局生死之歌、定格局诀、
  起八字诀、看命人式、阴阳通变、天道/地道/人道、论女命、滚浪桃花、壬骑龙背等。

## 4. 回归留痕
- `AllSiZhuBaZiSuiteTest`（`HWCodeArtsDir/step16_check/step16_mvn_regression.log`）：
  **Tests run: 32, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**。
  本体合规 8/8、SKOS 8/8；BPMN 内存/真实引擎、RabbitMQ 集成依环境跳过（Tests run: 0）。
- 禁区（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）零改动。
