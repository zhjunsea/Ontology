# 第 20 步 · 第五轮正文级复核 P1~P8 补录留痕 · 2026-10-30

> 承接《六典籍正文级复核报告-第四轮》（`HWCodeArtsDir/step19_check/六典籍正文级复核报告-第四轮.md`）。
> 客户裁定（本任务范围）：「发现缺口一律全部补齐、不必逐条确认；每轮补齐后再复检 6 书具体内容，循环至无遗漏」。
> 本轮=按裁定把第四轮残留的 **P1~P8（8 条）全部补录**（回写既有表，不新增表/不新增 OWL 类），并作数据/文档/测试同步。

## 1. 缺口与处置（P1~P8 → 既有表）
| 编号 | 典籍·篇章（实际文件行号） | 处置 |
|---|---|---|
| P1 | 渊海子平卷之一·论起胎法/起息法/起变法/起通法（202/206/211/216） | `technique_catalog` +4：`TEC_QiTaiFa`/`TEC_QiXiFa`/`TEC_QiBianFa`/`TEC_QiTongFa` |
| P2 | 渊海子平卷二·支后一字/凤凰地（1307/1313） | `pattern_catalog` +2：`PAT_ZhiHouYiZi`/`PAT_FengHuangDi` |
| P3 | 三命通会卷二·论进交退伏（**3493**） | `technique_catalog` +1：`TEC_JinJiaoTuiFu` |
| P4 | 三命通会卷二·十干分配天文/十二支分配地理（**2612/2716**） | `technique_catalog` +2：`TEC_ShiGanTianWen`/`TEC_ShiErZhiDiLi` |
| P5 | 三命通会**卷六**·附论墓运（**10481**） | `fu_clause` +1：`FU_SMTH_MuYun`（论，全文照录） |
| P6 | 神峰通考·月令详辨（**678**） | `technique_catalog` +1：`TEC_YueLingXiangBian` |
| P7 | 穷通宝鉴·五行总论（**104**） | `technique_catalog` +1：`TEC_WuXingZongLun` |
| P8 | 神峰通考·地支造化图/阳顺阴逆生旺死绝图（**986/714**） | `fu_clause` +2：`FU_SF_ZhiZaoTu`/`FU_SF_YangShunYinNiTu`（图，【图录】照录） |

**计数**：`technique_catalog` 38→**47**；`pattern_catalog` 97→**99**；`fu_clause` 205→**208**。

**行号勘误（★禁编造）**：第四轮报告所载「整理本行号」与实际文件行号有系统偏差，本轮以**实际文件行号**为准并重定位：
- P3 报告 3566 → 实际 **3493**；P4 报告 2667/2773 → 实际 **2612/2716**；P5 报告 10698 → 实际 **10481**；P6 报告 742 → 实际 **678**；P8 报告 1052/1080/779 → 实际 **986/714**。
- **P5 卷次勘误**：报告标「卷五」，实测三命通会 卷五起于第 7289 行、卷六起于第 8215 行、卷七起于第 11348 行，第 10481 行∈**卷六**（页眉「215」∈卷六 169–232 页）→ 正名为 **卷六**。

## 2. 回写生成源（不手改产物）
- `HWCodeArtsDir/sizhubazi_catalog.py`：`PATTERN_CATALOG` +2（P2）；`TECHNIQUE_CATALOG` +9（P1×4 / P3 / P4×2 / P6 / P7）。
- `HWCodeArtsDir/sizhubazi_fu.py`：新增 `_SMTH` 路径、`_FU_EXTRA`、`_build_extra()`，`FU_CLAUSES` 追加 P5/P8 共 3 条。
- `OntologyFrameworkSiZhuBaZi/ontology/database/data.sql`：经 `gen_sizhubazi_full_data.py` 重生成（counts: pattern=99 lady=17 tech=47 … fu=208）。
- `createdb_tables.ddl`：**结构不变**（仍 21 表），仅补表头步骤注释。

## 3. 同步物（ODA/DDL/测试/文档）
- **DDL**：无结构改动（无需改）。
- **OBDA `sizhubazi.obda`**：**无需改**——既有 `technique_catalog`/`pattern_catalog`/`fu_clause_all` 映射均为全表 `SELECT … WHERE <pk> IS NOT NULL`，新增行自动暴露为 `reading:ReadingItem`/`pattern:Pattern`/`reading:CategoryFortune`。
- **合规测试** `SiZhuBaZiOntologyComplianceTest.java`：`patternRows 97→99`、`techRows 38→47`、`fuRows 205→208`；新增 P1~P8 入库 `contains` 断言。
- **设计文档**：`SiZhuBaZi应用-本体核心设计.md`（§8 表清单三行 + §8 全集规模 + 新增「第 20 步」小节）、`SiZhuBaZi应用-顶层设计.md`（v1.9 变更行）、`SiZhuBaZi应用-SKOS词汇表设计.md`（v1.7 行，术语表仍 314 不变）。

## 4. 回归与验证
- **重灌 DB**：`mysql < createdb_tables.ddl` + `mysql < data.sql` → `pattern=99 / tech=47 / fu=208 / day_hour=720`（实测）。
- **离线用例（绿）**：`SiZhuBaZiOntologyComplianceTest` **8/8 通过**；`SiZhuBaZiSkosTerminologyTest` **8/8 通过**。
- **BPMN/引擎类（环境性失败，非本轮引入）**：`SiZhuBaZiBpmnInMemoryTest` 2 例、`SiZhuBaZiBpmnRealEngineTest` 1 例失败（"应成功部署流程"/"命书结论应非空"）。
  - **隔离实验**：将 DB 回灌**改动前** `data.sql`（pat=97/tec=38/fu=205）后复跑，两 BPMN 类**失败完全相同**（同 3 例）→ 该失败与本次 P1~P8 数据改动**无关**，属既有环境问题（RabbitMQ/Camunda/内存引擎未就绪）。
- **禁区模块** `OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`：**零改动**。

## 5. 下一步
- 第五轮全量复检（以新库为命中库重跑正文级机器扫描），确认 P1~P8 已消解、无新增缺口；循环至 0 缺口。
