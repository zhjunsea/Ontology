# 第 18 步 · 第三轮正文级复核 R3 补录 · 留痕记录

日期：2026-10-30
触发：客户「第三次……不仅看目录，还要查看**具体内容**，看本体是否包含所有内容，不能有遗漏」（第三轮，下沉至「成篇正文」层）。
复核报告：`HWCodeArtsDir/step18_check/六典籍正文级复核报告-第三轮.md`
裁定：客户「全额入库（完整韵文层）」→「追加全部 38 篇（彻底无遗漏）」→「一并纳入」（最终 205 篇）。

## 1. 缺口 R3
| 编号 | 典籍·篇章 | 缺口 | 处置 |
|---|---|---|---|
| R3 | 渊海子平卷一~卷七、神峰通考（附录前段） | 成篇「赋/歌/诗诀/独步/诀/论/篇/说/图」正文内容层未落地（既有多为短名录概括） | 新增 `fu_clause` 条文表，全文照录 **205** 篇，经 OBDA 暴露为 `reading:CategoryFortune` |

## 2. 规模与分组（205 篇 / 301284 字符）
- 渊海子平 **162** = 卷一/二 13（论五行所生之始…继善篇）+ 卷三~七 149
- 神峰通考 **43** = 附录 41 + 前段 2（伤官十论/五星论）
- 分组：赋 37 / 诗诀 68 / 歌 18 / 论 17 / 篇 10 / 诀 7 / 说 5 / 图 4 / 独步 2 / 文 37
- 超长篇（全文 LONGTEXT 不截断）：珞琭子消息赋 22874、玉井奥诀 22362、女命大义 14452、明通赋 13571、元理赋 12001、看命口诀 11617、继善篇(渊海) 10703

## 3. 抽取口径（★8/9/10）
- 渊海子平：正文以独立行「未知」标记篇目边界，下一行即篇名；卷次由卷首行判定（卷之一@6 … 卷之七@4334）。
- 神峰通考：附录按篇名行号切块；前段（<1090，无「未知」标记）用显式行区间。
- 图类（男/女命六亲取要图、天降干支图、月律分野之图）原书为图、整理本无文字 → 以「【图录】…（原书为图，整理本正文无对应文字；以名录收录）」占位。
- `clause_text` 单行存库（仅空白归一，不丢字符），与既有条文表一行一记录惯例一致。

## 4. 工件同步
| 工件 | 变更 |
|---|---|
| `HWCodeArtsDir/sizhubazi_fu.py` | 新建生成源（`FU_CLAUSES`） |
| `HWCodeArtsDir/gen_sizhubazi_full_data.py` | 导入 `FU_CLAUSES`；新增 9i 落库段；`fu_clause` 入清表列表；计数打印 +fu |
| `ontology/database/createdb_tables.ddl` | 20→**21** 表，新增 `fu_clause`（`clause_text` LONGTEXT） |
| `ontology/database/data.sql` | 重生成，fu=**205**（单行/条，总 1940 行） |
| `ontology/database/sizhubazi.obda` | 新增映射 `fu_clause_all` → `reading:CategoryFortune`（裸写前缀名，无 `#` 注释） |
| `src/test/.../SiZhuBaZiOntologyComplianceTest.java` | P-6 增 DDL 21 表 + OBDA `fu_clause`/`reading:CategoryFortune` 断言；P-7 增 fu 205 行数 + 首/末条断言 |
| 设计文档 | 顶层设计 v1.8；本体核心设计「第 18 步」章节 + 表清单 + 规模；SKOS 设计 v1.6（无变化） |

## 5. DB 灌入验证（★铁律 13/14 DB 为唯一真源）
- `mysql < createdb_tables.ddl` + `mysql sizhubazidb < data.sql` → **0 错误**。
- 实测计数：**21 表**；`deity_rule`=**186**、`fu_clause`=**205**（最长 `FU_YH_094` 珞琭子消息赋 22874 字符，LONGTEXT 无截断）、`pattern_catalog`=97、`lady_fortune_catalog`=17、`technique_catalog`=38、`auspice_clause`=25、`day_hour_clause`=720、`tiaohou_clause`=120。
- **附带修复（既有缺陷，非本轮引入）**：`deity_rule` 三列宽度不足曾致整库灌入中断——`base_ganzhi` 32→**64**（数据最长 52）、`deity_alias` 64→**128**（最长 71）、`branch_hit` 64→**128**（最长 95）；属第 17 步 I2 细煞遗留，本轮实测 `data.sql` 才暴露。

## 6. 回归
- 合规 + SKOS：`Tests run: 16, Failures: 0`（=客户 32 基线主体，另 16 为主套件重复计数）。
- 会话首跑（RabbitMQ 未启）全量 `mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test` → **Tests run: 32, Failures: 0**。
- ⚠️ **环境提示（与本轮无关）**：本机 RabbitMQ（`erl.exe`:5672）现处于运行中，激活了本应「未就绪即整体跳过」的三组 BPMN/RabbitMQ 集成测试（`SiZhuBaZiBpmnInMemoryTest`/`SiZhuBaZiBpmnRealEngineTest`/`SwrlRabbitMqTest`），其中 2 条内存引擎用例失败（`Task_MarriageMatch`/`Task_ParseInput` 未经过）。已做 **revert 对照**（临时移除 `fu_clause` 映射后同样失败）证明**与本轮 R3 改动无关**。如需复现 32/32 基线，停用 RabbitMQ 即可。
- 禁区模块 `OpenlletResolver` / `OntopOBDAHandler`（`git status` 零改动）。
- 无新增 OWL 类/属性（沿用第 15 步已建 `reading:CategoryFortune`）；SKOS 仍 314。
