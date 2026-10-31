# step31 · 节气精度升级（近似算法 → 精确交节时刻）

日期：2026-10-30
模块：`OntologyFrameworkSiZhuBaZi`
触发：客户问——「能否把节气近似精度做进一步处理？」（承 step30 只读核查：现行 `S_TERM` 为近似算法、注释标「如需高精度以 OBDA 节气表为准」）。

## 0. 客户裁定（闸门）
| 项 | 裁定 |
|---|---|
| 总路径 | **路径 A**：OBDA 节气表承载精确时刻，`solarTermDate/monthPillar/yearPillar/pilotAge` 改为按精确时刻判定 |
| 数据来源/覆盖 | 生成脚本内**自算**（无天文库），**仅覆盖 1950–2050**，超范围回退近似 |
| 本体建模 | **新增类** `calendar:SolarTermInstant`（+ `instantOf`/`hasInstance`、`termYear`/`termIndex`/`termDatetime`） |

## 1. 改动清单（回写生成源 → 再生成；禁手改产物）

| 产物 | 生成源（回写） | 再生成命令 |
|---|---|---|
| `ontology/database/data.sql` | `HWCodeArtsDir/sizhubazi_solarterm.py`（新）+ `gen_sizhubazi_full_data.py`（新增 6b 段） | `python HWCodeArtsDir/gen_sizhubazi_full_data.py` |
| 12 子本体 `.owl` + `sizhubazi-tbox.owl` | `HWCodeArtsDir/sizhubazi-tbox.owl.presplit.bak`（新增 calendar 类/属性） | `python HWCodeArtsDir/split_sizhubazi_tbox_subs.py` |
| `ontology/sizhubazi-terminology.ttl` | `HWCodeArtsDir/step13_check/en_labels.py`（补 6 个 `@en`） | `python HWCodeArtsDir/step13_check/gen_sizhubazi_terminology.py` |
| `design/SiZhuBaZi应用-SKOS词汇表设计.md` | 同 ttl（由 `gen_design_doc.py` 自动并表） | `python HWCodeArtsDir/step13_check/gen_design_doc.py` |

直接维护（无生成脚本，须与 data.sql、合规测试三处同步）：
- `ontology/database/createdb_tables.ddl`：新增表 `solar_term_time`（23→**24** 张）。
- `ontology/database/sizhubazi.obda`：新增 `mappingId solar_term_time`（→ `calendar:SolarTermInstant`）。

程序 / 测试 / 文档：
- `SiZhuBaZiOntologyJobWorker.java`：`solarTermDate(LocalDate)` → `solarTermDateTime(LocalDateTime)`（按年缓存 `solarTermCache`，经 `queryClauses`→`executeAboxQuery` 查 `solar_term_time`）；`yearPillar`/`monthPillar`/`allJieAround`/`pilotAge` 改用精确时刻比较（`yearPillar`/`monthPillar`/`pilotAge`/`allJieAround` 由 static 改实例）；OBDA_PREFIX 增 `calendar:`/`xsd:`；新增 `loadSolarTermYear`/`parseOntoDateTime`/`approxSolarTermDateTime`。
- `SiZhuBaZiOntologyComplianceTest.java`：增 DDL/OBDA/data.sql 断言 + 新用例 `step31SolarTermInstantDeclared`。
- 新增 `SiZhuBaZiSolarTermTest.java`（离线：dateTime 解析 + 近似兜底）。
- 设计文档：核心设计（S2/§5/§8）、顶层设计与应用架构设计（第 31 步留痕）。

## 2. 精度与出处
- 算法：Meeus《Astronomical Algorithms》2nd ed. **第 25 章太阳位置**（低精度，太阳视黄经 ≈0.01°）＋ **Espenak & Meeus ΔT 多项式**；牛顿迭代求目标黄经时刻，输出北京时间（UTC+8）。
- 校验：24 节气相邻间隔 14.75~15.73 天；与 Java `S_TERM` 近似最大「日」偏差 0.09 天；抽查——2024 立春 02-04 16:20、春分 03-20 11:03、夏至 06-21 04:48、冬至 12-21 17:13；2023 冬至 12-22 11:26（公开 11:27）、2025 立春 02-03 22:07（公开 22:10）。
- 结论：交节「**日**」与公开历法一致；交节「**时刻**」误差约 **±15 分钟**。★禁编造：算法与出处如实标注，不称秒级精度。

## 3. 边界与降级（不阻断）
- 年份 <1950 或 >2050：直接回退内置近似。
- DB/Ontop 不可用（`queryClauses` 捕获异常返回空集）：回退内置近似；按年缓存「空数组」，避免重复查询。
- `pilotAge` 以精确时刻择「相邻节」，天数仍按「日」计（保持原 ⑭ 年级口径）。

## 4. 回归（`mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test`，不带 `-am`）
- 离线用例集 `-Dtest=SiZhuBaZiSolarTermTest,SiZhuBaZiOntologyComplianceTest,SiZhuBaZiBirthParsingTest,SiZhuBaZiSkosTerminologyTest`：**24/24 全过**（SolarTerm 3 / Compliance 9 / BirthParsing 4 / SKOS 8）。
- 全量 `test` 因 `SiZhuBaZiBpmnInMemoryTest`（需 RabbitMQ/引擎环境）超时未跑完，属环境性，与本次改动无关。

## 5. 禁区核验
- `git status --porcelain OpenlletResolver OntologyResolver OntopOBDAHandler` → **空**（零改动）。
- 备份：`HWCodeArtsDir/step31_backup/`（calendar.owl / tbox.owl / core.owl / terminology.ttl / SKOS设计.md）与 `HWCodeArtsDir/data.sql.bak_prestep31`。
