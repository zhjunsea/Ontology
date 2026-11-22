# 紫微斗数 JobWorker · 未覆盖分支逐条台账（v2·业务断言口径）

> 来源 `target/site/jacoco/jacoco.xml`；脚本 `HWCodeArtsDir/_zwds_cov_caliber.py`

## 0. 覆盖率（三口径）

| 口径 | 分支 | 行 |
|---|---|---|
| 整体(JaCoCo 原始) | 3904/4352 = 89.71% | 38721/39616 = 97.74% |
| 业务断言(剔除工具/防御/异常/自检/边界/显示) | 2699/2842 = 94.97% | 33352/33625 = 99.19% |

## 1. 不计分母类别（未覆盖分支数）

| 类别 | 未覆盖分支 |
|---|---|
| 判空/空值防御·不计 | 145 |
| 工具(格式化/解析/查找/基础换算)·不计 | 118 |
| 非法入参边界·不计 | 32 |
| 显示字符串兜底·不计 | 6 |
| OBDA/持久化自检·不计 | 4 |
| 异常/失败兜底·不计 | 0 |

> 业务断言未覆盖(可补·需定向命例)：**143** 分支 / 171 行。

## 2. 业务断言剩余缺口·按方法

| 方法 | 未覆盖分支 |
|---|---|
| handleDeterminePattern | 22 |
| handlePersistAndReturn | 18 |
| handleInterpretStarNature | 13 |
| judgeLongevity | 9 |
| judgeTongYuan | 9 |
| handleAnalyzeAnnualFortune | 7 |
| handleAnalyzePalaceOverlay | 7 |
| handleAnalyzeMajorLimit | 6 |
| judgeTanXingYaoLun | 5 |
| handleSendAlert | 4 |
| judgeTwoLimitsTaiSui | 4 |
| eventPalaceExpectation | 4 |
| judgeLiuNianTaiSui | 4 |
| judgePalaceZhiGrid | 4 |
| handlePlaceMiscStars | 2 |
| extractAlertTypeFromReading | 2 |
| judgeTenGrades | 2 |
| tongYuanObjectiveFull | 2 |
| ziweiLongevityVerdict | 2 |
| ziweiSuiXianOverlay | 2 |
| sihuaStars | 1 |
| handleAssessBrightness | 1 |
| bureauOf | 1 |
| palaceStarRelation | 1 |
| palaceHasLu | 1 |
| verifyCandidateScore | 1 |
| gradeOf | 1 |
| isJiaMingQuote | 1 |
| judgeMingGeGrade | 1 |
| judgeXiaoErMing | 1 |
| judgeYangTuoDieBing | 1 |
| judgeQiShaReFeng | 1 |
| palaceNetJi | 1 |
| ziweiPalaceVerdictLine | 1 |
| ziweiChartLevelVerdict | 1 |
| handleParseBirthInput | 0 |
| isIdleStar | 0 |
| inSanFang | 0 |
| isHuaInMing | 0 |
| yunxianFortuneSign | 0 |
| annualFortuneSign | 0 |
| judgeXianPinHouFu | 0 |
| isBuShuWuYi | 0 |
| isZhuLuoSanXian | 0 |
| hasJuMenOrSha | 0 |
| verifyStarTraits | 0 |
| extractStatedTraits | 0 |
| candidateMingStars | 0 |
| ziweiMainStarDesc | 0 |
| ziweiMajorLimitTable | 0 |

## 3. 逐条台账（未覆盖分支 mb>0 或未覆盖指令 mi>0）

| 行 | 方法 | mb | cb | mi | ci | 归类 | 处置 | 代码 |
|---|---|---|---|---|---|---|---|---|
| 304 | ? | 4 | 4 | 0 | 10 | 非法入参边界·不计 | 不计 | `if (birthGan < 0 \|\| birthGan > 9 \|\| flowBranch < 0 \|\| flowBranch > 11) {` |
| 305 | ? | 0 | 0 | 2 | 0 | 判空/空值防御·不计 | 不计 | `return "";` |
| 308 | ? | 1 | 1 | 0 | 4 | 非法入参边界·不计 | 不计 | `if (flowBranch >= tsxSeg.size()) {` |
| 309 | ? | 0 | 0 | 2 | 0 | 判空/空值防御·不计 | 不计 | `return "";` |
| 426 | brightnessVariantNote | 1 | 3 | 0 | 5 | 非法入参边界·不计 | 不计 | `if (branchIdx < 0 \|\| branchIdx > 11) {` |
| 587 | loadPatternRows | 0 | 0 | 2 | 5 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `try { seq = Integer.parseInt(seqS.trim()); } catch (Exception ex) { continue; }` |
| 593 | loadPatternRows | 0 | 0 | 1 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `} catch (Exception e) {` |
| 594 | loadPatternRows | 0 | 0 | 6 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `log.warn("⚠️ pattern 表装载失败 {}：{}", classLocal, e.getMessage());` |
| 595 | loadPatternRows | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return null;` |
| 605 | expandTail | 2 | 2 | 3 | 5 | 判空/空值防御·不计 | 不计 | `String[] parts = (last == null \|\| last.isEmpty()) ? new String[0]` |
| 628 | verse | 2 | 2 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (v != null && !v.isEmpty()) {` |
| 673 | loadStateFromObda | 2 | 4 | 1 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (cn == null \|\| bi == null \|\| name == null) continue;` |
| 714 | loadStateFromObda | 1 | 1 | 1 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (kind == null) continue;` |
| 716 | loadStateFromObda | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (name != null) {` |
| 718 | loadStateFromObda | 1 | 1 | 5 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (seq < 0) seq = 1000 + (++shenSeqFallback);  // 无编号时按出现序兜底（仍确定性）` |
| 724 | loadStateFromObda | 1 | 3 | 0 | 12 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `String ganChar = (base != null && base.length() >= 3) ? base.substring(2) : "";` |
| 726 | loadStateFromObda | 1 | 3 | 1 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (bi < 0 \|\| ganChar.isEmpty()) continue;` |
| 729 | loadStateFromObda | 1 | 1 | 0 | 9 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `else if ("流陀".equals(kind)) tuoMap.put(ganChar, bi);` |
| 740 | loadStateFromObda | 1 | 3 | 1 | 4 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (si < 0 \|\| grp == null) continue;` |
| 755 | loadStateFromObda | 1 | 1 | 1 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (id == null) continue;` |
| 810 | loadStateFromObda | 4 | 4 | 1 | 15 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (r == null \|\| r.length < 2 \|\| r[0] == null \|\| r[0].isEmpty()) continue;` |
| 811 | loadStateFromObda | 1 | 1 | 2 | 13 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `pvMap.put(r[0], r[1] == null ? "" : r[1]);` |
| 863 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_LUCUN != null && ONTO_LUCUN.size() != 10) partial.add("流禄(" + ONTO_LUCUN.size() + "/10)");` |
| 864 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_LIUYANG != null && ONTO_LIUYANG.size() != 10) partial.add("流羊(" + ONTO_LIUYANG.size() + "/10)");` |
| 865 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_LIUTUO != null && ONTO_LIUTUO.size() != 10) partial.add("流陀(" + ONTO_LIUTUO.size() + "/10)");` |
| 866 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_DEITY_12 != null && ONTO_DEITY_12.length != 12) partial.add("十二神(" + ONTO_DEITY_12.length + "/12)");` |
| 867 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_WUHU != null && ONTO_WUHU.size() != 10) partial.add("五鼠遁(" + ONTO_WUHU.size() + "/10)");` |
| 869 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_STAR_CHARACTER != null && ONTO_STAR_CHARACTER.size() != 14) partial.add("星情化气(" + ONTO_STAR_CHARACTER.size() + "/14)");` |
| 870 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_STAR_XINGXING != null && ONTO_STAR_XINGXING.size() != 25) partial.add("形性賦(" + ONTO_STAR_XINGXING.size() + "/25)");` |
| 871 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_STAR_QA_CHARACTER != null && ONTO_STAR_QA_CHARACTER.size() != 20) partial.add("问答所主(" + ONTO_STAR_QA_CHARACTER.size() + "/20)");` |
| 872 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_VERIFY_STAR_TRAITS != null && ONTO_VERIFY_STAR_TRAITS.size() != 14) partial.add("验盘主星特征(" + ONTO_VERIFY_STAR_TRAITS.size() + "/14)");` |
| 873 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_VERIFY_BUREAU_TRAITS != null && ONTO_VERIFY_BUREAU_TRAITS.size() != 2) partial.add("验盘局体质(" + ONTO_VERIFY_BUREAU_TRAITS.size() + "/2)");` |
| 874 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_TAISUI_XIAOXIAN != null && ONTO_TAISUI_XIAOXIAN.size() != 12) partial.add("太岁小限(" + ONTO_TAISUI_XIAOXIAN.size() + "/12)");` |
| 875 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_PALACE_ZHI_GE != null && ONTO_PALACE_ZHI_GE.size() != 12) partial.add("得地合格(" + ONTO_PALACE_ZHI_GE.size() + "/12)");` |
| 876 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_PALACE_ZHI_PO != null && ONTO_PALACE_ZHI_PO.size() != 12) partial.add("失陷破格(" + ONTO_PALACE_ZHI_PO.size() + "/12)");` |
| 877 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_PALACE_FUGUI != null && ONTO_PALACE_FUGUI.size() != 12) partial.add("得地富贵(" + ONTO_PALACE_FUGUI.size() + "/12)");` |
| 878 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_PALACE_PINJIAN != null && ONTO_PALACE_PINJIAN.size() != 10) partial.add("失陷贫贱(" + ONTO_PALACE_PINJIAN.size() + "/10)");` |
| 879 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_IDLE_VERSE != null && ONTO_IDLE_VERSE.size() != 9) partial.add("落闲断语(" + ONTO_IDLE_VERSE.size() + "/9)");` |
| 880 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_IDLE_STAR != null && ONTO_IDLE_STAR.size() != 8) partial.add("星辰落闲(" + ONTO_IDLE_STAR.size() + "/8)");` |
| 881 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_BRIGHTNESS_VARIANTS != null && ONTO_BRIGHTNESS_VARIANTS.size() != 2) partial.add("庙陷异文(" + ONTO_BRIGHTNESS_VARIANTS.size() + "/2)");` |
| 882 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_LIMIT_TABOO != null && ONTO_LIMIT_TABOO.size() != 12) partial.add("大小限所忌(" + ONTO_LIMIT_TABOO.size() + "/12)");` |
| 883 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_SIHUA_QA != null && ONTO_SIHUA_QA.size() != 4) partial.add("四化所主(" + ONTO_SIHUA_QA.size() + "/4)");` |
| 884 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_STAR_BLOCK_ALL != null && ONTO_STAR_BLOCK_ALL.size() != 36) partial.add("星名清单(" + ONTO_STAR_BLOCK_ALL.size() + "/36)");` |
| 885 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_CLASSIC_CORPUS != null && ONTO_CLASSIC_CORPUS.size() != 813) partial.add("卷二语料库(" + ONTO_CLASSIC_CORPUS.size() + "/813)");` |
| 886 | loadStateFromObda | 1 | 3 | 6 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_PATTERN_VERSE != null && ONTO_PATTERN_VERSE.size() != 27) partial.add("格局原文登记(" + ONTO_PATTERN_VERSE.size() + "/27)");` |
| 903 | verifyConsistency | 1 | 1 | 1 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (si < 0) continue;` |
| 938 | verifyConsistency | 2 | 2 | 1 | 12 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (db != null && db != floorMod(LUCUN[g] + 1, 12)) luDiff++;` |
| 944 | verifyConsistency | 2 | 2 | 1 | 12 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (db != null && db != floorMod(LUCUN[g] - 1, 12)) luDiff++;` |
| 954 | verifyConsistency | 1 | 3 | 0 | 11 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `for (int i = 0; i < 12 && i < DEITY_12.length; i++) {` |
| 967 | verifyConsistency | 1 | 1 | 2 | 5 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (db.length != bi.length) { tyDiff++; continue; }` |
| 969 | verifyConsistency | 1 | 3 | 0 | 14 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (db[k] == null \|\| !db[k].equals(bi[k])) { tyDiff++; break; }` |
| 988 | verifyConsistency | 1 | 1 | 0 | 5 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_LIMIT_TABOO.size() != LIMIT_TABOO.length) {` |
| 989 | verifyConsistency | 0 | 0 | 9 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `pDiff += Math.abs(ONTO_LIMIT_TABOO.size() - LIMIT_TABOO.length);` |
| 992 | verifyConsistency | 1 | 1 | 1 | 11 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (!LIMIT_TABOO[i].equals(ONTO_LIMIT_TABOO.get(i)[0])) pDiff++;` |
| 996 | verifyConsistency | 1 | 1 | 0 | 5 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_SIHUA_QA.size() != SIHUA_QA.length) {` |
| 997 | verifyConsistency | 0 | 0 | 9 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `pDiff += Math.abs(ONTO_SIHUA_QA.size() - SIHUA_QA.length);` |
| 1000 | verifyConsistency | 1 | 1 | 1 | 11 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (!SIHUA_QA[i].equals(ONTO_SIHUA_QA.get(i)[0])) pDiff++;` |
| 1004 | verifyConsistency | 1 | 1 | 0 | 5 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ONTO_STAR_BLOCK_ALL.size() != STAR_BLOCK_ALL.length) {` |
| 1005 | verifyConsistency | 0 | 0 | 9 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `pDiff += Math.abs(ONTO_STAR_BLOCK_ALL.size() - STAR_BLOCK_ALL.length);` |
| 1008 | verifyConsistency | 1 | 1 | 1 | 11 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (!STAR_BLOCK_ALL[i].equals(ONTO_STAR_BLOCK_ALL.get(i)[0])) pDiff++;` |
| 1025 | diffRows | 1 | 1 | 2 | 5 | OBDA/持久化自检·不计 | 不计 | `if (db.length != bi.length) { diff++; continue; }` |
| 1027 | diffRows | 1 | 3 | 0 | 14 | 判空/空值防御·不计 | 不计 | `if (db[k] == null \|\| !db[k].equals(bi[k])) { diff++; break; }` |
| 1042 | sihuaStars | 1 | 1 | 3 | 4 | 判空/空值防御·不计 | 不计 | `if (out[i] == null) { ok = false; break; }` |
| 1044 | sihuaStars | 1 | 1 | 0 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (ok) return out;` |
| 1054 | ziweiBranch | 1 | 1 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (idx != null) return idx;` |
| 1064 | lucunBranch | 1 | 1 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (v != null) return v;` |
| 1074 | liuyangBranch | 1 | 1 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (v != null) return v;` |
| 1084 | liutuoBranch | 1 | 1 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (v != null) return v;` |
| 1092 | deityName | 2 | 4 | 0 | 12 | 判空/空值防御·不计 | 不计 | `if (cache != null && i >= 0 && i < cache.length) return cache[i];` |
| 1101 | wuhuYinGan | 1 | 1 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (v != null) return v;` |
| 1111 | trailingInt | 1 | 3 | 0 | 11 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `while (i > 0 && Character.isDigit(s.charAt(i - 1))) i--;` |
| 1115 | trailingInt | 0 | 0 | 1 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `} catch (NumberFormatException e) {` |
| 1116 | trailingInt | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return -1;` |
| 1128 | verifyObdaTableCoverage | 2 | 2 | 1 | 7 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (obdaPath == null \|\| obdaPath.isBlank()) return;` |
| 1130 | verifyObdaTableCoverage | 1 | 1 | 2 | 8 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `java.nio.file.Path ddl = obda.getParent() == null ? null : obda.getParent().resolve("createdb_tables.ddl");` |
| 1131 | verifyObdaTableCoverage | 3 | 3 | 0 | 8 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (ddl == null \|\| !java.nio.file.Files.isReadable(ddl) \|\| !java.nio.file.Files.isReadable(obda)) {` |
| 1132 | verifyObdaTableCoverage | 0 | 0 | 5 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `log.debug("ℹ️ OBDA 表覆盖自检跳过（obda/ddl 路径不可读）：{}", obdaPath);` |
| 1133 | verifyObdaTableCoverage | 0 | 0 | 1 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return;` |
| 1163 | verifyObdaTableCoverage | 0 | 0 | 1 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `} catch (Exception e) {` |
| 1164 | verifyObdaTableCoverage | 0 | 0 | 5 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `log.debug("ℹ️ OBDA 表覆盖自检异常（已忽略）：{}", e.getMessage());` |
| 1174 | createBackendService | 0 | 0 | 1 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `} catch (IllegalStateException alreadyInitialized) {` |
| 1175 | createBackendService | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `reusedObdaHandler = true;` |
| 1176 | createBackendService | 0 | 0 | 3 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `log.warn("⚠️ OBDAHandler 已初始化，复用现有实例（同一 JVM 内多 Spring 上下文场景）");` |
| 1179 | createBackendService | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (reusedObdaHandler) {` |
| 1180 | createBackendService | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `BackendService.setInstance(null);` |
| 1195 | handleParseBirthInput | 2 | 4 | 0 | 11 | 判空/空值防御·不计 | 不计 | `if (birthVar == null \|\| (birthVar instanceof String bs && bs.isBlank())) {` |
| 1196 | handleParseBirthInput | 0 | 0 | 5 | 0 | 异常/失败兜底·不计 | 不计 | `throw new IllegalArgumentException("birth（生辰）不能为空");` |
| 1214 | handleParseBirthInput | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (conv != null) {` |
| 1217 | handleParseBirthInput | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `lunarUnconverted = true;` |
| 1218 | handleParseBirthInput | 0 | 0 | 4 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `log.warn("⚠️ parseBirthInput \| 农历→公历换算失败（超出 1900–2099 表范围），按原值登记：{}", birth);` |
| 1286 | handleParseBirthInput | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 1287 | handleParseBirthInput | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "PARSE_BIRTH_INPUT_FAILED", e);` |
| 1343 | handleDetermineLifePalaces | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 1344 | handleDetermineLifePalaces | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "DETERMINE_LIFE_PALACES_FAILED", e);` |
| 1362 | handlePlaceMainStars | 4 | 4 | 0 | 12 | 非法入参边界·不计 | 不计 | `if (bureau < 2 \|\| bureau > 6 \|\| day < 1 \|\| day > 30) {` |
| 1363 | handlePlaceMainStars | 0 | 0 | 7 | 0 | 异常/失败兜底·不计 | 不计 | `throw new IllegalStateException("五行局/生日越界，无法安紫微（局=" + bureau + " 日=" + day + "）");` |
| 1388 | handlePlaceMainStars | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 1389 | handlePlaceMainStars | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "PLACE_MAIN_STARS_FAILED", e);` |
| 1439 | handlePlaceAuxiliaryStars | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 1440 | handlePlaceAuxiliaryStars | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "PLACE_AUXILIARY_STARS_FAILED", e);` |
| 1506 | handlePlaceMiscStars | 1 | 1 | 0 | 10 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdxM >= 0) place(at, floorMod(mingIdxM + yearBranch, 12), "天才"); // 天才：命宫起子顺至生年支` |
| 1507 | handlePlaceMiscStars | 1 | 1 | 0 | 10 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (shenIdxM >= 0) place(at, floorMod(shenIdxM + yearBranch, 12), "天寿"); // 天寿：身宫起子顺至生年支` |
| 1553 | handlePlaceMiscStars | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 1554 | handlePlaceMiscStars | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "PLACE_MISC_STARS_FAILED", e);` |
| 1589 | handlePlaceFourTransform | 1 | 1 | 2 | 10 | 判空/空值防御·不计 | 不计 | `.append(idx == null ? "【待考·该星未安】" : ZHI[idx]).append("；");` |
| 1622 | handlePlaceFourTransform | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 1623 | handlePlaceFourTransform | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "PLACE_FOUR_TRANSFORM_FAILED", e);` |
| 1637 | handleVerifyChart | 1 | 3 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (traits == null \|\| traits.isBlank()) {` |
| 1693 | handleVerifyChart | 1 | 3 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (stated.isEmpty() \|\| bestScore <= 0) {` |
| 1709 | handleVerifyChart | 1 | 1 | 2 | 11 | 判空/空值防御·不计 | 不计 | `+ "原时辰=" + ZHI[hourBranch] + "时｜自述命中特征=" + (stated.isEmpty() ? "（无）" : String.join("、", stated))` |
| 1796 | handleAssessBrightness | 1 | 1 | 2 | 5 | 业务判定(格局/运限/职业格/星情) | 可补 | `+ (satI ? "→煞凑已成，凶断成立" : "→煞凑未成，断语条件未足（不可径断）");` |
| 1836 | handleAssessBrightness | 1 | 1 | 2 | 6 | 显示字符串兜底·不计 | 不计 | `String starRelations = sr.length() == 0 ? "同宫星辰无明显五行生克" : sr.toString();` |
| 1848 | handleAssessBrightness | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (rel != null) {` |
| 1854 | handleAssessBrightness | 1 | 1 | 2 | 7 | 显示字符串兜底·不计 | 不计 | `+ (ph.length() == 0 ? "本盘星宫无明显生克。" : ph.toString());` |
| 1874 | handleAssessBrightness | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 1875 | handleAssessBrightness | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "ASSESS_BRIGHTNESS_FAILED", e);` |
| 1898 | handleInterpretStarNature | 1 | 1 | 0 | 9 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mmN.find()) { mingIdxN = indexOf(ZHI, mmN.group(1)); }` |
| 1903 | handleInterpretStarNature | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdxN >= 0) {` |
| 1907 | handleInterpretStarNature | 1 | 1 | 1 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (!mainSetN.contains(stN)) { continue; }` |
| 1909 | handleInterpretStarNature | 1 | 1 | 0 | 12 | OBDA/持久化自检·不计 | 不计 | `for (String[] rN : rows(ONTO_STAR_CHARACTER, STAR_CHARACTER)) {` |
| 1912 | handleInterpretStarNature | 1 | 1 | 0 | 12 | OBDA/持久化自检·不计 | 不计 | `for (String[] rN : rows(ONTO_STAR_XINGXING, STAR_XINGXING)) {` |
| 1923 | handleInterpretStarNature | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (idx != null) {` |
| 1933 | handleInterpretStarNature | 2 | 4 | 0 | 7 | 判空/空值防御·不计 | 不计 | `if (idx == null && auxStars != null && !auxStars.isBlank()) {` |
| 1936 | handleInterpretStarNature | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (idx != null) {` |
| 1951 | handleInterpretStarNature | 3 | 3 | 0 | 7 | 判空/空值防御·不计 | 不计 | `if (idx == null && auxStars != null && !auxStars.isBlank()) {` |
| 1960 | handleInterpretStarNature | 2 | 2 | 0 | 7 | 判空/空值防御·不计 | 不计 | `String[] sihuaQa = (ONTO_SIHUA_QA != null && ONTO_SIHUA_QA.size() == SIHUA_QA.length)` |
| 1961 | handleInterpretStarNature | 0 | 0 | 1 | 16 | OBDA/持久化自检·不计 | 不计 | `? ONTO_SIHUA_QA.stream().map(r -> r[0]).toArray(String[]::new) : SIHUA_QA;` |
| 1967 | handleInterpretStarNature | 1 | 1 | 0 | 3 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (qaAux.length() > 0) {` |
| 1976 | handleInterpretStarNature | 1 | 1 | 0 | 3 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (skm.find()) {` |
| 1979 | handleInterpretStarNature | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdxPc >= 0) {` |
| 1983 | handleInterpretStarNature | 1 | 3 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (pStars == null \|\| pStars.isEmpty()) {` |
| 1984 | handleInterpretStarNature | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `continue;` |
| 2001 | handleInterpretStarNature | 2 | 2 | 0 | 10 | 判空/空值防御·不计 | 不计 | `if (cbMg.get("SOLAR") != null && !cbMg.get("SOLAR").isBlank()) {` |
| 2004 | handleInterpretStarNature | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception ignore) {` |
| 2005 | handleInterpretStarNature | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `birthGanMg = -1;` |
| 2008 | handleInterpretStarNature | 2 | 2 | 0 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdxPc >= 0 && birthGanMg >= 0) {` |
| 2015 | handleInterpretStarNature | 1 | 1 | 0 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (!mainSetMg.contains(st)) {` |
| 2016 | handleInterpretStarNature | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `continue;` |
| 2027 | handleInterpretStarNature | 1 | 1 | 1 | 7 | 业务判定(格局/运限/职业格/星情) | 可补 | `String mingMainDescN = mingIdxPc >= 0 ? ziweiMainStarDesc(at, mingIdxPc) : "";` |
| 2028 | handleInterpretStarNature | 1 | 1 | 1 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `String natureHead = "本性总断：你命宫" + (mingIdxPc >= 0 ? ("在" + ZHI[mingIdxPc] + "、主星" + mingMainDescN) : "未定")` |
| 2034 | handleInterpretStarNature | 1 | 1 | 2 | 12 | 显示字符串兜底·不计 | 不计 | `result.put("starNature", nature.length() == 0 ? "" : (natureHead + nature.toString().trim()));` |
| 2035 | handleInterpretStarNature | 1 | 1 | 2 | 12 | 显示字符串兜底·不计 | 不计 | `result.put("starXingXing", xingXing.length() == 0 ? "" : (xingHead + xingXing.toString().trim()));` |
| 2037 | handleInterpretStarNature | 1 | 1 | 2 | 7 | 显示字符串兜底·不计 | 不计 | `result.put("palaceClassic", palaceClassic.length() == 0 ? ""` |
| 2044 | handleInterpretStarNature | 2 | 2 | 2 | 18 | 业务判定(格局/运限/职业格/星情) | 可补 | `qi, mingIdxPc >= 0 ? ZHI[mingIdxPc] : "?", birthGanMg >= 0 ? GAN[birthGanMg] : "?",` |
| 2045 | handleInterpretStarNature | 1 | 3 | 0 | 17 | 判空/空值防御·不计 | 不计 | `mingIdxPc >= 0 && at.get(mingIdxPc) != null ? at.get(mingIdxPc) : "?",` |
| 2047 | handleInterpretStarNature | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 2048 | handleInterpretStarNature | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "INTERPRET_STAR_NATURE_FAILED", e);` |
| 2072 | handleEmptyPalace | 1 | 1 | 2 | 8 | 判空/空值防御·不计 | 不计 | `.append(borrowed.isEmpty() ? "【对宫亦空·无可借主星】" : String.join("、", borrowed))` |
| 2085 | handleEmptyPalace | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 2086 | handleEmptyPalace | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "HANDLE_EMPTY_PALACE_FAILED", e);` |
| 2146 | handleAnalyzeSurroundPalaces | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 2147 | handleAnalyzeSurroundPalaces | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "ANALYZE_SURROUND_PALACES_FAILED", e);` |
| 2195 | handleDeterminePattern | 1 | 3 | 0 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `&& inSanFang(mingIdx, tianji) && inSanFang(mingIdx, taiyin)` |
| 2196 | handleDeterminePattern | 1 | 3 | 0 | 10 | 业务判定(格局/运限/职业格/星情) | 可补 | `&& inSanFang(mingIdx, tiantong) && inSanFang(mingIdx, tianliang)) hit.add("机月同梁格");` |
| 2222 | handleDeterminePattern | 1 | 5 | 0 | 12 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (inSanFang(mingIdx, qisha) && inSanFang(mingIdx, pojun) && inSanFang(mingIdx, tanlang)) {` |
| 2259 | handleDeterminePattern | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (liangPos != null) {` |
| 2261 | handleDeterminePattern | 3 | 1 | 3 | 3 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (rel == 1 \|\| rel == 11) {` |
| 2262 | handleDeterminePattern | 1 | 1 | 5 | 10 | 业务判定(格局/运限/职业格/星情) | 可补 | `int oppPos = (rel == 1) ? floorMod(mingIdx - 1, 12) : floorMod(mingIdx + 1, 12);` |
| 2281 | handleDeterminePattern | 1 | 3 | 0 | 9 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (shenIdx >= 0 && !kongIdx.contains(shenIdx)` |
| 2289 | handleDeterminePattern | 1 | 5 | 0 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdx == indexOf(ZHI, "午") && mingMain.contains("太阳") && mingMain.size() == 1) {` |
| 2307 | handleDeterminePattern | 1 | 3 | 0 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `&& (inSanFang(mingIdx, "左辅") \|\| jia.contains("左辅"))` |
| 2308 | handleDeterminePattern | 1 | 3 | 0 | 6 | 业务判定(格局/运限/职业格/星情) | 可补 | `&& (inSanFang(mingIdx, "右弼") \|\| jia.contains("右弼"))) {` |
| 2322 | handleDeterminePattern | 1 | 3 | 0 | 6 | 业务判定(格局/运限/职业格/星情) | 可补 | `\|\| (mingSet.contains("天魁") && mingSet.contains("天钺"))) {` |
| 2340 | handleDeterminePattern | 2 | 2 | 0 | 9 | 业务判定(格局/运限/职业格/星情) | 可补 | `&& !"陷".equals(brightnessOf("贪狼", mingIdx)) && !"陷".equals(brightnessOf("火星", mingIdx))) {` |
| 2346 | handleDeterminePattern | 1 | 5 | 0 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdx == indexOf(ZHI, "卯") && mingMain.contains("巨门") && mingMain.contains("天机")) {` |
| 2401 | handleDeterminePattern | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (maPeiYinIdx != null) {` |
| 2413 | handleDeterminePattern | 1 | 3 | 0 | 8 | 判空/空值防御·不计 | 不计 | `if (luIdx != null && kongIdx.contains(luIdx)` |
| 2418 | handleDeterminePattern | 1 | 3 | 0 | 6 | 判空/空值防御·不计 | 不计 | `if (maIdx != null && kongIdx.contains(maIdx)) {` |
| 2434 | handleDeterminePattern | 1 | 1 | 1 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `long shaCntShen = shenIdx >= 0 ? shenList.stream().filter(SI_SHA::contains).count() : 0;` |
| 2435 | handleDeterminePattern | 1 | 7 | 0 | 21 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean shaFallen = mingList.stream().anyMatch(s0 -> SI_SHA.contains(s0) && isFallen(s0, mingIdx))` |
| 2447 | handleDeterminePattern | 2 | 6 | 0 | 16 | 判空/空值防御·不计 | 不计 | `if (sunIdxFB != null && moonIdxFB != null && isFallen("太阳", sunIdxFB) && isFallen("太阴", moonIdxFB)` |
| 2457 | handleDeterminePattern | 2 | 2 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (solarStr != null && !solarStr.isBlank()) {` |
| 2462 | handleDeterminePattern | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception ignore) {` |
| 2463 | handleDeterminePattern | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `tongYuanGan = -1;` |
| 2464 | handleDeterminePattern | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `hourBranch11 = -1;` |
| 2477 | handleDeterminePattern | 1 | 5 | 0 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `(mingIdx == indexOf(ZHI, "未") && "卯".equals(taiyang) && "亥".equals(taiyin))` |
| 2478 | handleDeterminePattern | 1 | 5 | 0 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `\|\| (mingIdx == indexOf(ZHI, "辰") && "辰".equals(taiyang) && "戌".equals(taiyin))   // 辰日守命、戌月对照` |
| 2479 | handleDeterminePattern | 1 | 5 | 0 | 14 | 业务判定(格局/运限/职业格/星情) | 可补 | `\|\| (mingIdx == indexOf(ZHI, "戌") && "戌".equals(taiyin) && "辰".equals(taiyang));  // 戌月守命、辰日对照` |
| 2499 | handleDeterminePattern | 2 | 6 | 0 | 16 | 判空/空值防御·不计 | 不计 | `boolean changQuZhaoQian = (chM != null && chM == qianIdx) \|\| (quM != null && quM == qianIdx);` |
| 2530 | handleDeterminePattern | 1 | 1 | 1 | 7 | 业务判定(格局/运限/职业格/星情) | 可补 | `String anMingJiXiong = hourBranch11 >= 0 ? judgeAnMingJiXiong(hourBranch11, mingIdx) : "（生时未定）";` |
| 2539 | handleDeterminePattern | 1 | 1 | 1 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `String tongYuanSummary = tongYuanBar > 0 ? tongYuan.substring(0, tongYuanBar) : tongYuan;` |
| 2582 | handleDeterminePattern | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 2583 | handleDeterminePattern | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "DETERMINE_PATTERN_FAILED", e);` |
| 2589 | stripParen | 1 | 1 | 2 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (s == null) { return ""; }` |
| 2601 | shortQuote | 1 | 1 | 2 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (s == null) { return ""; }` |
| 2603 | shortQuote | 1 | 3 | 0 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (max > 0 && t.length() > max) {` |
| 2639 | handleGenerateNatalReading | 1 | 1 | 0 | 12 | 判空/空值防御·不计 | 不计 | `if (!patternResult.isBlank()) r.append("· 成格明细：").append(stripParen(patternResult)).append('\n');` |
| 2647 | handleGenerateNatalReading | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 2648 | handleGenerateNatalReading | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "GENERATE_NATAL_READING_FAILED", e);` |
| 2676 | handleFemaleReading | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 2677 | handleFemaleReading | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "FEMALE_READING_FAILED", e);` |
| 2713 | handleChildReading | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 2714 | handleChildReading | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "CHILD_READING_FAILED", e);` |
| 2770 | handleAnalyzeMajorLimit | 1 | 3 | 0 | 19 | 非法入参边界·不计 | 不计 | `boolean shenWeak = shenIdx < 0 \|\| at.getOrDefault(shenIdx, List.of()).stream().noneMatch(s -> isMainStar(s));` |
| 2777 | handleAnalyzeMajorLimit | 1 | 3 | 0 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (limitStars.contains("七杀")` |
| 2778 | handleAnalyzeMajorLimit | 1 | 5 | 0 | 8 | 判空/空值防御·不计 | 不计 | `&& ((luIdx != null && kongIdx.contains(luIdx)) \|\| (maIdx != null && kongIdx.contains(maIdx)))) {` |
| 2799 | handleAnalyzeMajorLimit | 1 | 3 | 0 | 16 | 业务判定(格局/运限/职业格/星情) | 可补 | `.anyMatch(s -> List.of("禄存", "天魁", "天钺", "左辅", "右弼", "文昌", "文曲").contains(s))` |
| 2856 | handleAnalyzeMajorLimit | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (dxPalace < 0) {` |
| 2857 | handleAnalyzeMajorLimit | 0 | 0 | 13 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `dxRecalc.append("尚未起限（虚龄").append(ageNow).append("<起限岁").append(startAge).append("，童限依安童限诀）");` |
| 2866 | handleAnalyzeMajorLimit | 1 | 1 | 2 | 21 | 判空/空值防御·不计 | 不计 | `dxRecalc.append(dxHua[i]).append(dxHuaName[i]).append("→").append(b == null ? "—" : ZHI[b]).append("；");` |
| 2878 | handleAnalyzeMajorLimit | 1 | 3 | 0 | 9 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean twoLimitsMeet = dxPalace >= 0 && dxPalace == xxPalace;` |
| 2885 | handleAnalyzeMajorLimit | 1 | 1 | 2 | 7 | 业务判定(格局/运限/职业格/星情) | 可补 | `int dxSign = dxPalace < 0 ? 0 : yunxianFortuneSign(at, dxPalace, fourTransform);` |
| 2893 | handleAnalyzeMajorLimit | 1 | 3 | 0 | 10 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean zhuLuoDx = dxPalace >= 0 && isZhuLuoSanXian(dxPalace, at);` |
| 2931 | handleAnalyzeMajorLimit | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 2932 | handleAnalyzeMajorLimit | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "ANALYZE_MAJOR_LIMIT_FAILED", e);` |
| 2956 | handleAnalyzeAnnualFortune | 1 | 3 | 2 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `int granularityWarn = (!"年".equals(granularity) && !wantMonth) ? 1 : 0; // 未知粒度回退年级并标注（㉓）` |
| 2968 | handleAnalyzeAnnualFortune | 4 | 4 | 2 | 15 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean flowForward = (GAN_YY[flowGan] == 1 && male) \|\| (GAN_YY[flowGan] == 0 && !male);` |
| 2981 | handleAnalyzeAnnualFortune | 1 | 1 | 2 | 21 | 判空/空值防御·不计 | 不计 | `recalc.append(fHua[i]).append(fHuaName[i]).append("→").append(b == null ? "—" : ZHI[b]).append("；");` |
| 3008 | handleAnalyzeAnnualFortune | 1 | 3 | 2 | 15 | 显示字符串兜底·不计 | 不计 | `+ (granularityWarn == 1 ? "（未知粒度回退年级并标注，㉓）" : (wantMonth ? "（㉓ 月/日 粒度已支持）" : ""))` |
| 3014 | handleAnalyzeAnnualFortune | 1 | 3 | 0 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean chongMingAnn = mingIdxAnn >= 0 && floorMod(mingIdxAnn + 6, 12) == flowBranch;` |
| 3023 | handleAnalyzeAnnualFortune | 4 | 2 | 6 | 11 | 判空/空值防御·不计 | 不计 | `if (keBranch != null && ("文昌".equals(fHua[2]) \|\| "文曲".equals(fHua[2]))` |
| 3032 | handleAnalyzeAnnualFortune | 1 | 1 | 14 | 10 | 业务判定(格局/运限/职业格/星情) | 可补 | `String[] tsxx = (flowBranch < tsxAnn.size()) ? tsxAnn.get(flowBranch) : new String[]{"", "", ""};` |
| 3039 | handleAnalyzeAnnualFortune | 2 | 2 | 0 | 6 | 判空/空值防御·不计 | 不计 | `String limitTabooRaw = ((ONTO_LIMIT_TABOO != null && birthYearBranch < ONTO_LIMIT_TABOO.size())` |
| 3040 | handleAnalyzeAnnualFortune | 0 | 0 | 3 | 8 | OBDA/持久化自检·不计 | 不计 | `? ONTO_LIMIT_TABOO.get(birthYearBranch)[0] : LIMIT_TABOO[birthYearBranch]);` |
| 3060 | handleAnalyzeAnnualFortune | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 3061 | handleAnalyzeAnnualFortune | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "ANALYZE_ANNUAL_FORTUNE_FAILED", e);` |
| 3096 | handleAnalyzePalaceOverlay | 1 | 3 | 0 | 6 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (targetMonthOverlay >= 1 && targetMonthOverlay <= 12) {` |
| 3100 | handleAnalyzePalaceOverlay | 1 | 1 | 0 | 3 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (targetDayOverlay >= 1) {` |
| 3106 | handleAnalyzePalaceOverlay | 1 | 1 | 1 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `int dxSignOv = dxIdx >= 0 ? yunxianFortuneSign(at, dxIdx, ftOv) : 0;` |
| 3108 | handleAnalyzePalaceOverlay | 1 | 3 | 0 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean chongMingOv = mingIdx >= 0 && floorMod(mingIdx + 6, 12) == flowYearIdx;` |
| 3114 | handleAnalyzePalaceOverlay | 1 | 1 | 1 | 16 | 业务判定(格局/运限/职业格/星情) | 可补 | `+ (dxIdx >= 0 ? (ZHI[dxIdx] + "宫（主星" + ziweiMainStarDesc(at, dxIdx) + "），" + dxToneOv) : "（尚未起限）")` |
| 3136 | handleAnalyzePalaceOverlay | 2 | 6 | 1 | 16 | 业务判定(格局/运限/职业格/星情) | 可补 | `String tsToneOv = (tsShaOv == 0 && tsJiOv > 0) ? "吉" : (tsShaOv > tsJiOv ? "凶" : (tsJiOv > 0 ? "吉凶交参" : "平"));` |
| 3151 | handleAnalyzePalaceOverlay | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 3152 | handleAnalyzePalaceOverlay | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "ANALYZE_PALACE_OVERLAY_FAILED", e);` |
| 3274 | handleAggregateReading | 1 | 1 | 8 | 3 | 判空/空值防御·不计 | 不计 | `if (!mingGeGradeAgg.isBlank()) reading.append("- 命格等第（卷三·看命总纲/论人命入格）：").append(mingGeGradeAgg).append("\n");` |
| 3275 | handleAggregateReading | 1 | 1 | 8 | 3 | 判空/空值防御·不计 | 不计 | `if (!geXingShuAgg.isBlank()) reading.append("- 格星数高下（卷三·论格星数高下）：").append(geXingShuAgg).append("\n");` |
| 3276 | handleAggregateReading | 1 | 1 | 8 | 3 | 判空/空值防御·不计 | 不计 | `if (!anMingJiXiongAgg.isBlank()) reading.append("- 生时安命（卷三·论人生时安命吉凶）：").append(anMingJiXiongAgg).append("\n");` |
| 3277 | handleAggregateReading | 1 | 1 | 8 | 3 | 判空/空值防御·不计 | 不计 | `if (!jiaGongAgg.isBlank()) reading.append("- 夹拱格（卷一·斗數骨髓賦）：").append(jiaGongAgg).append("\n");` |
| 3278 | handleAggregateReading | 1 | 1 | 8 | 3 | 判空/空值防御·不计 | 不计 | `if (!tanXingAgg.isBlank()) reading.append("- 谈星要论（卷三·c4 L12-21）：").append(tanXingAgg).append("\n");` |
| 3279 | handleAggregateReading | 1 | 1 | 0 | 11 | 判空/空值防御·不计 | 不计 | `if (!sanFangJiYaoAgg.isBlank()) reading.append("- 三方吉曜（卷三·会吉/会煞）：").append(sanFangJiYaoAgg).append("\n");` |
| 3280 | handleAggregateReading | 1 | 1 | 8 | 3 | 判空/空值防御·不计 | 不计 | `if (!tongYuanAgg.isBlank()) reading.append("- 同垣各司（卷三·c4 L178-477 命中段）：").append(tongYuanAgg).append("\n");` |
| 3281 | handleAggregateReading | 1 | 1 | 0 | 11 | 判空/空值防御·不计 | 不计 | `if (!palaceZhiGridAgg.isBlank()) reading.append("- 十二宫得地/失陷（卷一·c2 L556-596）：").append(palaceZhiGridAgg).append("\n");` |
| 3317 | handleAggregateReading | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 3318 | handleAggregateReading | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "AGGREGATE_READING_FAILED", e);` |
| 3371 | handleSendAlert | 1 | 1 | 3 | 6 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean triggered = Boolean.TRUE.equals(vars.get("alertTriggered"))` |
| 3372 | handleSendAlert | 2 | 0 | 5 | 3 | 业务判定(格局/运限/职业格/星情) | 可补 | `\|\| "true".equalsIgnoreCase(str(vars.get("alertTriggered")));` |
| 3373 | handleSendAlert | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (triggered) {` |
| 3382 | handleSendAlert | 3 | 1 | 3 | 2 | 判空/空值防御·不计 | 不计 | `if (alertTypeVar == null \|\| alertTypeVar.isEmpty()) {` |
| 3385 | handleSendAlert | 2 | 2 | 2 | 10 | 判空/空值防御·不计 | 不计 | `payload.put("alertType", (alertTypeVar == null \|\| alertTypeVar.isEmpty()) ? "告警（类别未标注）" : alertTypeVar);` |
| 3391 | handleSendAlert | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `status = "SKIPPED_NO_ALERT";` |
| 3398 | handleSendAlert | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (mq != null) {` |
| 3401 | handleSendAlert | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception ignore) {` |
| 3425 | extractAlertTypeFromReading | 1 | 1 | 5 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (end < 0) end = reading.indexOf('\n', start);` |
| 3426 | extractAlertTypeFromReading | 1 | 1 | 3 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (end < 0) end = reading.length();` |
| 3445 | handleInterpretResult | 2 | 4 | 0 | 10 | 判空/空值防御·不计 | 不计 | `if (llm != null && llm.isAvailable() && !reading.isBlank()) {` |
| 3448 | handleInterpretResult | 1 | 3 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (r != null && !r.isBlank()) {` |
| 3452 | handleInterpretResult | 1 | 3 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (pl != null && !pl.isBlank()) {` |
| 3470 | handleInterpretResult | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 3471 | handleInterpretResult | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "INTERPRET_RESULT_FAILED", e);` |
| 3538 | handlePersistAndReturn | 1 | 1 | 1 | 10 | 业务判定(格局/运限/职业格/星情) | 可补 | `String yearOnly = solar.length() >= 4 ? solar.substring(0, 4) : solar;` |
| 3542 | handlePersistAndReturn | 2 | 2 | 0 | 7 | 判空/空值防御·不计 | 不计 | `if (backendService != null && backendService.getObdaHandler() != null) {` |
| 3549 | handlePersistAndReturn | 0 | 0 | 3 | 0 | OBDA/持久化自检·不计 | 不计 | `log.warn("⚠️ persistAndReturn \| OBDA 未就绪（第 11 步落地），落库跳过，仅返回 caseRecord");` |
| 3551 | handlePersistAndReturn | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Throwable e) {` |
| 3552 | handlePersistAndReturn | 0 | 0 | 5 | 0 | OBDA/持久化自检·不计 | 不计 | `log.warn("⚠️ persistAndReturn \| 落库未就绪（第 11 步落地），落库跳过，仅返回 caseRecord：{}", e.getMessage());` |
| 3563 | handlePersistAndReturn | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 3564 | handlePersistAndReturn | 0 | 0 | 6 | 0 | 异常/失败兜底·不计 | 不计 | `fail(job, client, "PERSIST_RETURN_FAILED", e);` |
| 3671 | handlePersistAndReturn | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (PALACE_CLASSIC != null) {` |
| 3672 | handlePersistAndReturn | 0 | 0 | 4 | 0 | OBDA/持久化自检·不计 | 不计 | `return PALACE_CLASSIC;` |
| 3675 | handlePersistAndReturn | 2 | 2 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (ONTO_CLASSIC_CORPUS != null && !ONTO_CLASSIC_CORPUS.isEmpty()) {` |
| 3678 | handlePersistAndReturn | 2 | 2 | 0 | 6 | 判空/空值防御·不计 | 不计 | `if (r == null \|\| r.length < 3) {` |
| 3679 | handlePersistAndReturn | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `continue;` |
| 3684 | handlePersistAndReturn | 0 | 0 | 6 | 0 | OBDA/持久化自检·不计 | 不计 | `try (BufferedReader br = new BufferedReader(new InputStreamReader(` |
| 3685 | handlePersistAndReturn | 0 | 0 | 5 | 0 | OBDA/持久化自检·不计 | 不计 | `ZiWeiDouShuOntologyJobWorker.class.getResourceAsStream("/ziweidoushu/palace_classic.tsv"),` |
| 3688 | handlePersistAndReturn | 2 | 0 | 5 | 0 | 判空/空值防御·不计 | 不计 | `while ((line = br.readLine()) != null) {` |
| 3689 | handlePersistAndReturn | 2 | 0 | 3 | 0 | 判空/空值防御·不计 | 不计 | `if (line.isBlank()) {` |
| 3690 | handlePersistAndReturn | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `continue;` |
| 3692 | handlePersistAndReturn | 0 | 0 | 5 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `String[] c = line.split("\t", 3);` |
| 3693 | handlePersistAndReturn | 2 | 0 | 4 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (c.length < 3) {` |
| 3694 | handlePersistAndReturn | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `continue;` |
| 3696 | handlePersistAndReturn | 0 | 0 | 27 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `m.computeIfAbsent(c[0], k -> new ArrayList<>()).add(new String[]{c[1], c[2]});` |
| 3697 | handlePersistAndReturn | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `}` |
| 3698 | handlePersistAndReturn | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 3700 | handlePersistAndReturn | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `}` |
| 3710 | handlePersistAndReturn | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (rows == null) {` |
| 3711 | handlePersistAndReturn | 0 | 0 | 2 | 0 | 判空/空值防御·不计 | 不计 | `return "";` |
| 3737 | handlePersistAndReturn | 3 | 5 | 0 | 13 | 判空/空值防御·不计 | 不计 | `if (text == null \|\| starName == null \|\| !text.startsWith(starName) \|\| text.length() <= starName.length()) {` |
| 3748 | handlePersistAndReturn | 2 | 4 | 0 | 11 | 判空/空值防御·不计 | 不计 | `if (r != null && r.length > 0 && isStarBlockOpen(text, r[0])) {` |
| 3794 | handlePersistAndReturn | 1 | 3 | 0 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (after.contains("不贵") \|\| after.contains("不为")) {` |
| 3853 | handlePersistAndReturn | 1 | 1 | 0 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (p.contains("羊")) {` |
| 3856 | handlePersistAndReturn | 1 | 1 | 0 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (p.contains("陀")) {` |
| 3868 | handlePersistAndReturn | 1 | 1 | 0 | 4 | 判空/空值防御·不计 | 不计 | `if (sc.stars.isEmpty()) {` |
| 3869 | handlePersistAndReturn | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `continue;` |
| 3871 | handlePersistAndReturn | 3 | 3 | 2 | 15 | 业务判定(格局/运限/职业格/星情) | 可补 | `sc.scope = (p.contains("同宫") \|\| p.contains("同垣") \|\| p.contains("同度")) ? "同宫" : "三方四正";` |
| 3882 | handlePersistAndReturn | 1 | 1 | 0 | 5 | 业务判定(格局/运限/职业格/星情) | 可补 | `int[] scope = "同宫".equals(sc.scope)` |
| 3883 | handlePersistAndReturn | 0 | 0 | 7 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `? new int[]{palaceZhi}` |
| 3924 | handlePersistAndReturn | 2 | 2 | 0 | 4 | 判空/空值防御·不计 | 不计 | `if (rows == null \|\| star == null) {` |
| 3925 | handlePersistAndReturn | 0 | 0 | 2 | 0 | 判空/空值防御·不计 | 不计 | `return "";` |
| 3941 | handlePersistAndReturn | 1 | 1 | 0 | 10 | 业务判定(格局/运限/职业格/星情) | 可补 | `for (int i = start + 1; i < rows.size(); i++) {` |
| 3971 | handlePersistAndReturn | 1 | 7 | 0 | 17 | 判空/空值防御·不计 | 不计 | `if (gans.isEmpty() \|\| branches == null \|\| !branches.contains(zhiChar.charAt(0)) \|\| !gans.contains(ganChar)) {` |
| 3982 | handlePersistAndReturn | 1 | 1 | 1 | 11 | 业务判定(格局/运限/职业格/星情) | 可补 | `String afterFull = sentStart >= 0 ? sent.substring(sentStart + pi + 2) : after;` |
| 3992 | handlePersistAndReturn | 2 | 2 | 2 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean xianOk = !needXian \|\| isFallen(star, palaceZhi);` |
| 3993 | handlePersistAndReturn | 1 | 3 | 2 | 6 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean cond = sat && xianOk;` |
| 3994 | handlePersistAndReturn | 1 | 1 | 2 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `String judge = cond ? "凶" : "吉";` |
| 3995 | handlePersistAndReturn | 2 | 2 | 3 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `String desc = shaCouDescribe(sc, sat) + (needXian ? "；" + star + "在陷地=" + (xianOk ? "是" : "否") : "");` |
| 4003 | handlePersistAndReturn | 1 | 1 | 0 | 3 | 判空/空值防御·不计 | 不计 | `if (tentative.isEmpty()) {` |
| 4314 | resetOntologyCaches | 1 | 1 | 0 | 3 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `&& !java.lang.reflect.Modifier.isFinal(fld.getModifiers())) {` |
| 4318 | resetOntologyCaches | 0 | 0 | 1 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `} catch (Exception ignore) {` |
| 4348 | fail | 1 | 1 | 2 | 6 | 判空/空值防御·不计 | 不计 | `.errorMessage(e.getMessage() == null ? errorCode : e.getMessage())` |
| 4353 | appendLine | 1 | 3 | 0 | 6 | 判空/空值防御·不计 | 不计 | `if (value != null && !String.valueOf(value).isBlank()) {` |
| 4378 | branchIndexByZhi | 1 | 5 | 0 | 10 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (correctedTime == null \|\| correctedTime.length() < 2 \|\| !correctedTime.endsWith("时")) {` |
| 4386 | firstLine | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (s == null) {` |
| 4387 | firstLine | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return "";` |
| 4390 | firstLine | 1 | 1 | 4 | 5 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return i < 0 ? s : s.substring(0, i);` |
| 4433 | indexOf | 1 | 3 | 0 | 12 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `boolean ziweiShouMing = mingMain.contains("紫微") \|\| mingSet.contains("紫微");` |
| 4439 | indexOf | 1 | 3 | 0 | 4 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (syIdx < 0 \|\| yyIdx < 0) {` |
| 4445 | indexOf | 9 | 7 | 6 | 22 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return ((sr >= 7 && sr <= 11) && (yr >= 1 && yr <= 5))` |
| 4459 | indexOf | 1 | 3 | 0 | 4 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (a == null \|\| b == null) {` |
| 4482 | indexOf | 1 | 3 | 0 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `&& (mingSet.contains("文昌") \|\| mingSet.contains("文曲"))) {` |
| 4486 | indexOf | 1 | 3 | 0 | 7 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `\|\| mingIdx == indexOf(ZHI, "丑") \|\| mingIdx == indexOf(ZHI, "未"))) {` |
| 4505 | indexOf | 1 | 1 | 2 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (mingIdx < 0) { return out; }` |
| 4561 | containsAny | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (s == null) {` |
| 4562 | containsAny | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return false;` |
| 4573 | eq | 1 | 3 | 0 | 10 | 判空/空值防御·不计 | 不计 | `return a != null && a.equals(b);` |
| 4612 | bureauOf | 1 | 5 | 0 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `switch (elem) {` |
| 4618 | bureauOf | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `default: return 0;` |
| 4647 | palaceBranch | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (skeleton == null) {` |
| 4648 | palaceBranch | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return -1;` |
| 4655 | palaceBranch | 1 | 1 | 0 | 4 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (p >= skeleton.length()) {` |
| 4656 | palaceBranch | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return -1;` |
| 4681 | parsePlacements | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (s == null) {` |
| 4682 | parsePlacements | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return at;` |
| 4688 | parsePlacements | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (b >= 0) {` |
| 4705 | starBranch | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (s == null) {` |
| 4706 | starBranch | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return null;` |
| 4713 | starBranch | 1 | 1 | 0 | 4 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (p >= s.length()) {` |
| 4714 | starBranch | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return null;` |
| 4717 | starBranch | 1 | 1 | 2 | 5 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return b < 0 ? null : b;` |
| 4757 | palaceStarRelation | 2 | 4 | 0 | 7 | 非法入参边界·不计 | 不计 | `if (se < 0 \|\| branchIdx < 0 \|\| branchIdx > 11) {` |
| 4774 | palaceStarRelation | 1 | 1 | 0 | 7 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (pe == (se + 2) % 5) {          // 星克宫` |
| 4777 | palaceStarRelation | 0 | 0 | 3 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return base + "→无明显生克";` |
| 4803 | brightnessOf | 1 | 3 | 0 | 6 | 判空/空值防御·不计 | 不计 | `if (row != null && row[branchIdx] != null) {` |
| 4824 | isIdleStar | 1 | 1 | 0 | 2 | 非法入参边界·不计 | 不计 | `if (branchIdx < 0) {` |
| 4825 | isIdleStar | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return false;` |
| 4863 | inSanFang | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (branchStr == null) {` |
| 4864 | inSanFang | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return false;` |
| 4898 | isHuaInMing | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (fourTransform == null) {` |
| 4899 | isHuaInMing | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return false;` |
| 4981 | huaInPalace | 1 | 3 | 0 | 4 | 判空/空值防御·不计 | 不计 | `if (fourTransform == null \|\| idx < 0) {` |
| 4994 | palaceHasLu | 1 | 3 | 0 | 16 | 业务判定(格局/运限/职业格/星情) | 可补 | `return idx >= 0 && (at.getOrDefault(idx, List.of()).contains("禄存")` |
| 5000 | sixHePartner | 1 | 1 | 2 | 8 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return idx < 0 ? -1 : floorMod(SIX_HE_SUM - idx, 12);` |
| 5012 | yunxianFortuneSign | 2 | 2 | 0 | 5 | 非法入参边界·不计 | 不计 | `if (idx < 0 \|\| idx > 11) {` |
| 5013 | yunxianFortuneSign | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return 0;` |
| 5052 | annualFortuneSign | 4 | 4 | 0 | 10 | 非法入参边界·不计 | 不计 | `if (flowBranch < 0 \|\| flowBranch > 11 \|\| flowGan < 0 \|\| flowGan > 9) {` |
| 5053 | annualFortuneSign | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return 0;` |
| 5112 | judgeTwoLimitsTaiSui | 1 | 1 | 2 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `sb.append("大限").append(dxPalace < 0 ? "未起" : ZHI[dxPalace] + fortuneGlyph(dxSign))` |
| 5128 | judgeTwoLimitsTaiSui | 1 | 3 | 0 | 5 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (dxPalace >= 0 && dxPalace == xxPalace) {` |
| 5131 | judgeTwoLimitsTaiSui | 1 | 3 | 0 | 5 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (dxPalace >= 0 && dxPalace == tsIdx) {` |
| 5139 | judgeTwoLimitsTaiSui | 1 | 3 | 0 | 5 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (dxPalace >= 0 && dxPalace == tsOpp) {` |
| 5176 | judgeXianPinHouFu | 2 | 2 | 0 | 6 | 判空/空值防御·不计 | 不计 | `if (limitSigns == null \|\| limitSigns.length < 12) {` |
| 5177 | judgeXianPinHouFu | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return "论命先贫后富（卷三）：大限序列不足，不判";` |
| 5204 | isBuShuWuYi | 2 | 2 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (limitSigns == null \|\| limitSigns.length == 0) {` |
| 5205 | isBuShuWuYi | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return false;` |
| 5221 | isShuiShangJiaXing | 1 | 3 | 0 | 6 | 判空/空值防御·不计 | 不计 | `if (yearSigns == null \|\| yearSigns.length < 4) {` |
| 5258 | isZhuLuoSanXian | 2 | 2 | 0 | 5 | 非法入参边界·不计 | 不计 | `if (idx < 0 \|\| idx > 11) {` |
| 5259 | isZhuLuoSanXian | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return false;` |
| 5277 | hasJuMenOrSha | 2 | 2 | 0 | 5 | 非法入参边界·不计 | 不计 | `if (idx < 0 \|\| idx > 11) {` |
| 5278 | hasJuMenOrSha | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return false;` |
| 5358 | eventPalaceExpectation | 2 | 2 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (chunk == null \|\| chunk.isBlank()) {` |
| 5359 | eventPalaceExpectation | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `continue;` |
| 5366 | eventPalaceExpectation | 1 | 1 | 0 | 2 | 判空/空值防御·不计 | 不计 | `if (pe == null) {` |
| 5367 | eventPalaceExpectation | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `continue;` |
| 5376 | eventPalaceExpectation | 1 | 3 | 1 | 10 | 业务判定(格局/运限/职业格/星情) | 可补 | `match = sign > 0 ? "合（盘吉）" : (sign < 0 ? "待核（盘凶·与期望逆）" : "待核（盘平）");` |
| 5377 | eventPalaceExpectation | 1 | 1 | 0 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `} else if (pe[1] < 0) {` |
| 5378 | eventPalaceExpectation | 2 | 2 | 3 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `match = sign < 0 ? "合（盘凶）" : (sign > 0 ? "待核（盘吉·与期望逆）" : "待核（盘平）");` |
| 5380 | eventPalaceExpectation | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `match = "中性事项·参考";` |
| 5492 | verifyStarTraits | 1 | 1 | 0 | 12 | OBDA/持久化自检·不计 | 不计 | `for (String[] row : rows(ONTO_VERIFY_STAR_TRAITS, VERIFY_STAR_TRAITS)) {` |
| 5497 | verifyStarTraits | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return List.of();` |
| 5526 | extractStatedTraits | 2 | 2 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (text == null \|\| text.isBlank()) {` |
| 5527 | extractStatedTraits | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return hit;` |
| 5543 | candidateMingStars | 4 | 4 | 0 | 12 | 非法入参边界·不计 | 不计 | `if (bureau < 2 \|\| bureau > 6 \|\| day < 1 \|\| day > 30) {` |
| 5544 | candidateMingStars | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return stars;` |
| 5583 | verifyCandidateScore | 1 | 9 | 0 | 16 | 业务判定(格局/运限/职业格/星情) | 可补 | `if ((chang == ming \|\| chang == shen \|\| qu == ming \|\| qu == shen) && stated.contains(VERIFY_CHANGQU_MOLE)) {` |
| 5584 | verifyCandidateScore | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `score += 2;` |
| 5585 | verifyCandidateScore | 0 | 0 | 8 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `detail.append("昌曲入命身+").append(VERIFY_CHANGQU_MOLE).append("(2);");` |
| 5629 | distillMarkup | 2 | 2 | 2 | 5 | 判空/空值防御·不计 | 不计 | `if (s == null \|\| s.isEmpty()) return s;` |
| 5672 | gradeOf | 1 | 3 | 0 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (ji && sha > 0) {` |
| 5685 | isJiaMingQuote | 2 | 4 | 0 | 10 | 判空/空值防御·不计 | 不计 | `if (quote == null \|\| !(quote.contains("夹命") \|\| quote.contains("夹身"))) {` |
| 5688 | isJiaMingQuote | 1 | 1 | 0 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `switch (star) {` |
| 5690 | isJiaMingQuote | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return false;` |
| 5698 | starsSplitClamp | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (target < 0) {` |
| 5699 | starsSplitClamp | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return false;` |
| 5706 | starsSplitClamp | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (b != null) {` |
| 5710 | starsSplitClamp | 5 | 1 | 12 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return locs.size() == 2 && locs.contains(left) && locs.contains(right);` |
| 5735 | judgeMingGeGrade | 1 | 3 | 0 | 6 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean jiaSha = !sanFangWuSha` |
| 5862 | judgeXiaoErMing | 1 | 1 | 2 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdx < 0) { return "论小儿命（卷三·c4 L58-61）：命宫未定，不判"; }` |
| 5988 | judgeYangTuoDieBing | 1 | 1 | 2 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdx < 0) { return false; }` |
| 6003 | judgeQiShaReFeng | 1 | 1 | 2 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdx < 0) { return false; }` |
| 6005 | judgeQiShaReFeng | 1 | 1 | 2 | 2 | 判空/空值防御·不计 | 不计 | `if (q == null) { return false; }` |
| 6022 | judgeLiuNianTaiSui | 1 | 1 | 2 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (flowYearIdx < 0) { return "论流年太岁吉凶星杀（卷三·c4 L83-84）：太岁未定，不判"; }` |
| 6023 | judgeLiuNianTaiSui | 1 | 3 | 0 | 9 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean onMing = mingIdx >= 0 && flowYearIdx == mingIdx;` |
| 6026 | judgeLiuNianTaiSui | 2 | 6 | 1 | 16 | 业务判定(格局/运限/职业格/星情) | 可补 | `String tone = (sha == 0 && ji > 0) ? "吉" : (sha > ji ? "凶" : (ji > 0 ? "吉凶交参" : "平"));` |
| 6029 | judgeLiuNianTaiSui | 1 | 1 | 1 | 6 | 判空/空值防御·不计 | 不计 | `: (pn != null ? "·在" + pn + "（对照命宫）" : "（对照命宫）");` |
| 6037 | palaceNameOf | 2 | 2 | 2 | 4 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (skeleton == null \|\| idx < 0) { return null; }` |
| 6038 | palaceNameOf | 1 | 1 | 0 | 65 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `for (String pn : new String[]{"命宫", "兄弟", "妻妾", "子女", "财帛", "疾厄",` |
| 6042 | palaceNameOf | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return null;` |
| 6073 | judgeTanXingYaoLun | 1 | 1 | 2 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdx < 0) { return "谈星要论开篇（卷三·c4 L12-21）：命宫未定，不判"; }` |
| 6078 | judgeTanXingYaoLun | 1 | 1 | 1 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (idx < 0) { continue; }` |
| 6090 | judgeTanXingYaoLun | 1 | 5 | 0 | 11 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (total > 0 && shaCnt == 0 && jiCnt >= total / 2 + 1) {` |
| 6092 | judgeTanXingYaoLun | 1 | 5 | 0 | 11 | 业务判定(格局/运限/职业格/星情) | 可补 | `} else if (total > 0 && jiCnt == 0 && shaCnt >= total / 2 + 1) {` |
| 6102 | judgeTanXingYaoLun | 1 | 5 | 0 | 14 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean bad = stars.contains("天空") \|\| stars.contains("地劫") \|\| stars.contains("地空")` |
| 6180 | judgeTenGrades | 1 | 3 | 0 | 6 | 业务判定(格局/运限/职业格/星情) | 可补 | `&& (sanFang.contains("紫微") \|\| sanFang.contains("天府"))) {` |
| 6202 | judgeTenGrades | 1 | 3 | 0 | 6 | 业务判定(格局/运限/职业格/星情) | 可补 | `\|\| sanFang.contains("文昌") \|\| sanFang.contains("文曲"))) {` |
| 6237 | isBranch | 1 | 5 | 0 | 15 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return idx >= 0 && idx < 12 && ZHI[idx].equals(zhi);` |
| 6247 | gongFallenWithSha | 1 | 3 | 0 | 5 | 非法入参边界·不计 | 不计 | `if (idx < 0 \|\| idx > 11) {` |
| 6275 | judgeLongevity | 1 | 3 | 0 | 17 | 业务判定(格局/运限/职业格/星情) | 可补 | `Set<String> shenSet = new HashSet<>(shenIdx >= 0 && shenIdx <= 11 ? at.getOrDefault(shenIdx, List.of()) : List.of());` |
| 6297 | judgeLongevity | 1 | 5 | 0 | 12 | 业务判定(格局/运限/职业格/星情) | 可补 | `\|\| (isBranch(mingIdx, "未") && mingSet.contains("破军") && mingSet.contains("武曲"))` |
| 6308 | judgeLongevity | 5 | 1 | 8 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `&& sanFang.contains("天梁") && sanFang.contains("太阳") && sanFang.contains("紫微"))) {` |
| 6332 | judgeLongevity | 1 | 3 | 0 | 14 | 业务判定(格局/运限/职业格/星情) | 可补 | `if ((hasSha(at.getOrDefault(fumuIdx, List.of())) && inSanFang(fumuIdx, "天刑"))` |
| 6343 | judgeLongevity | 1 | 5 | 0 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `&& !(mingSet.contains("破军") \|\| hasSha(mingList) \|\| isKongJie(mingSet))) {` |
| 6465 | judgePalaceZhiGrid | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdx < 0) {` |
| 6466 | judgePalaceZhiGrid | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return "十二宫得地/失陷格（卷一）：命宫未定，不判";` |
| 6486 | judgePalaceZhiGrid | 1 | 1 | 1 | 9 | 业务判定(格局/运限/职业格/星情) | 可补 | `String rawGan = row.length > 4 ? row[4] : "-";` |
| 6490 | judgePalaceZhiGrid | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `} else if (yearGan < 0) {` |
| 6491 | judgePalaceZhiGrid | 0 | 0 | 9 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `sb.append("·生干").append(rawGan).append("（生年干未定·不否决）");` |
| 6528 | judgePalaceZhiGrid | 1 | 1 | 1 | 9 | 恒真死支（语言级）：`String.split()` 永不返回长度0数组 ⇒ `length>0` 恒真，`: ""` 不可达 | 已等价化简删除（直接用 `fgArr[0]`） | `String fgFirst = row[1].split("、")[0].trim();` |
| 6529 | judgePalaceZhiGrid | 1 | 3 | 0 | 7 | 数据防御守卫：仅当 `row[1]` 以「、」起首时 `fgFirst` 为空（语料不变量保证，非语言恒真） | 保留原守卫·据实计（该方法 61/62，此 1 支登记为防御性·语料不可达） | `if (!fgFirst.isEmpty() && !matched.contains(fgFirst)) {` |
| 6562 | judgeTongYuan | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mingIdx < 0) {` |
| 6563 | judgeTongYuan | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return "卷三·诸星同垣各司所宜（c4 L178-477）：命宫未定，不判";` |
| 6578 | judgeTongYuan | 1 | 3 | 0 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `} else if (star.equals("财帛") \|\| star.equals("财宅")) {` |
| 6580 | judgeTongYuan | 1 | 1 | 0 | 4 | 业务判定(格局/运限/职业格/星情) | 可补 | `} else if (star.equals("财福")) {` |
| 6581 | judgeTongYuan | 0 | 0 | 5 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `branch = palaceBranch(skeleton, "福德");` |
| 6598 | judgeTongYuan | 1 | 3 | 0 | 10 | 非法入参边界·不计 | 不计 | `if (branch < 0 \|\| !row[1].contains(ZHI[branch])) {` |
| 6607 | judgeTongYuan | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (sanfang) {` |
| 6608 | judgeTongYuan | 0 | 0 | 4 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `sn = sn.substring(4);` |
| 6611 | judgeTongYuan | 3 | 5 | 8 | 8 | 判空/空值防御·不计 | 不计 | `if (sb == null \|\| (sanfang ? !inSanFang(branch, ZHI[sb]) : sb != branch)) {` |
| 6619 | judgeTongYuan | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (yearGan < 0) {` |
| 6620 | judgeTongYuan | 0 | 0 | 8 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `pend.add(star + "（生年干未定·待考）：" + row[4]);` |
| 6621 | judgeTongYuan | 0 | 0 | 1 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `continue;` |
| 6643 | judgeTongYuan | 1 | 1 | 4 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `int jiaTarget = row[4].contains("夹身") ? palaceBranch(skeleton, "身宫") : mingIdx;` |
| 6644 | judgeTongYuan | 1 | 1 | 0 | 5 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (!starsSplitClamp(at, star, jiaTarget)) {` |
| 6654 | judgeTongYuan | 1 | 1 | 0 | 3 | 判空/空值防御·不计 | 不计 | `if (!objJudged.isEmpty()) {` |
| 6657 | judgeTongYuan | 1 | 1 | 0 | 3 | 判空/空值防御·不计 | 不计 | `if (!orJudged.isEmpty()) {` |
| 6660 | judgeTongYuan | 1 | 1 | 0 | 3 | 判空/空值防御·不计 | 不计 | `if (!pend.isEmpty()) {` |
| 6664 | judgeTongYuan | 2 | 4 | 0 | 12 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (p.contains("纳音") \|\| p.contains("败地") \|\| p.contains("绝处")) {` |
| 6701 | tongYuanObjective | 1 | 1 | 1 | 9 | 判空/空值防御·不计 | 不计 | `return objNote("禄存入庙(禄存无陷)", branchIndexOf(at, "禄存") != null);` |
| 6708 | tongYuanObjective | 1 | 3 | 0 | 6 | 判空/空值防御·不计 | 不计 | `if (cai < 0 && tian < 0) { return null; }` |
| 6720 | tongYuanObjective | 1 | 1 | 2 | 2 | 判空/空值防御·不计 | 不计 | `if (yearGan < 0) { return null; }` |
| 6745 | tongYuanObjective | 3 | 1 | 4 | 2 | 判空/空值防御·不计 | 不计 | `if (qian < 0 && mingIdx < 0) { return null; }` |
| 6795 | palaceNetJi | 1 | 5 | 0 | 14 | 业务判定(格局/运限/职业格/星情) | 可补 | `return idx >= 0 && hasJiInSanFang(at, idx) && shaCountInSanFang(at, idx) == 0;` |
| 6882 | tongYuanObjectiveFull | 3 | 1 | 4 | 2 | 判空/空值防御·不计 | 不计 | `if (cai < 0 && fu < 0) { return null; }` |
| 6883 | tongYuanObjectiveFull | 1 | 3 | 0 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean a = cai >= 0 && (at.getOrDefault(cai, List.of()).contains("地劫")` |
| 6885 | tongYuanObjectiveFull | 1 | 3 | 0 | 13 | 业务判定(格局/运限/职业格/星情) | 可补 | `boolean b = fu >= 0 && (at.getOrDefault(fu, List.of()).contains("地劫")` |
| 6893 | tongYuanObjectiveFull | 1 | 1 | 2 | 2 | 判空/空值防御·不计 | 不计 | `if (qi < 0) { return null; }` |
| 6945 | tongYuanObjectiveFull | 1 | 1 | 2 | 2 | 判空/空值防御·不计 | 不计 | `if (fu < 0) { return null; }` |
| 7030 | splitStarSet | 1 | 3 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (s != null && !s.isEmpty()) {` |
| 7032 | splitStarSet | 1 | 1 | 0 | 3 | 判空/空值防御·不计 | 不计 | `if (!p.isEmpty()) {` |
| 7042 | ganListMatches | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (yearGan < 0) {` |
| 7043 | ganListMatches | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return false;` |
| 7047 | ganListMatches | 1 | 3 | 0 | 8 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (!g.isEmpty() && indexOf(GAN, g) == yearGan) {` |
| 7058 | ganListHasSuspectToken | 1 | 3 | 0 | 7 | 判空/空值防御·不计 | 不计 | `if (!g.isEmpty() && indexOf(GAN, g) < 0) {` |
| 7077 | xunZhongKongwang | 1 | 1 | 2 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `int xun = idx < 0 ? 0 : idx / 10;` |
| 7114 | birthCalendar | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (c != null) {` |
| 7119 | birthCalendar | 2 | 2 | 0 | 6 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (s != null && s.contains("农历")) {` |
| 7120 | birthCalendar | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return "农历";` |
| 7128 | resolveBirthDateTime | 3 | 1 | 3 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (dt != null && !dt.isBlank()) {` |
| 7129 | resolveBirthDateTime | 0 | 0 | 3 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `LocalDateTime parsed = tryParse(dt);` |
| 7130 | resolveBirthDateTime | 2 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (parsed != null) {` |
| 7131 | resolveBirthDateTime | 0 | 0 | 2 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `return parsed;` |
| 7139 | resolveBirthDateTime | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (y > 0) {` |
| 7144 | resolveBirthDateTime | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (s != null) {` |
| 7146 | resolveBirthDateTime | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (parsed != null) {` |
| 7150 | resolveBirthDateTime | 0 | 0 | 7 | 0 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `throw new IllegalArgumentException("无法解析生辰：" + birthVar);` |
| 7203 | extractLongitude | 1 | 5 | 0 | 12 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (birthPlace.contains("西经") \|\| birthPlace.contains("W") \|\| birthPlace.contains("w")) {` |
| 7218 | ziweiMainStarDesc | 2 | 2 | 0 | 5 | 非法入参边界·不计 | 不计 | `if (idx < 0 \|\| idx > 11) {` |
| 7219 | ziweiMainStarDesc | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return "（宫未定）";` |
| 7232 | ziweiHuaNote | 4 | 4 | 0 | 10 | 判空/空值防御·不计 | 不计 | `if (fourTransform == null \|\| fourTransform.isEmpty() \|\| idx < 0 \|\| idx > 11) {` |
| 7233 | ziweiHuaNote | 0 | 0 | 2 | 0 | 判空/空值防御·不计 | 不计 | `return "";` |
| 7269 | ziweiPalaceVerdictLine | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (idx < 0) {` |
| 7270 | ziweiPalaceVerdictLine | 0 | 0 | 3 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return palaceName + "：宫位未定，不判。";` |
| 7309 | ziweiChartLevelVerdict | 1 | 4 | 0 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `switch (tier) {` |
| 7314 | ziweiChartLevelVerdict | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `default: meaning = "格局平常、无大起落，宜安分守成"; break;` |
| 7343 | ziweiLongevityVerdict | 1 | 3 | 0 | 5 | 判空/空值防御·不计 | 不计 | `if (longevity == null \|\| longevity.isEmpty()) {` |
| 7349 | ziweiLongevityVerdict | 1 | 1 | 1 | 8 | 业务判定(格局/运限/职业格/星情) | 可补 | `String t = p > 0 ? hit.substring(0, p) : hit;` |
| 7350 | ziweiLongevityVerdict | 1 | 3 | 0 | 7 | 判空/空值防御·不计 | 不计 | `if (!t.isEmpty() && !types.contains(t)) {` |
| 7365 | ziweiLongevityVerdict | 1 | 1 | 4 | 3 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (mean.length() == 0) mean.append("中晚年宜守正避险");` |
| 7393 | ziweiMajorLimitTable | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 7394 | ziweiMajorLimitTable | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return "（大限表：出生信息不足，无法推演）";` |
| 7456 | ziweiSuiXianOverlay | 0 | 0 | 1 | 0 | 异常/失败兜底·不计 | 不计 | `} catch (Exception e) {` |
| 7457 | ziweiSuiXianOverlay | 0 | 0 | 2 | 0 | 业务判定(格局/运限/职业格/星情) | 可补 | `return "（岁限叠看：出生信息不足，无法推演）";` |
| 7473 | ziweiSuiXianOverlay | 1 | 1 | 0 | 2 | 业务判定(格局/运限/职业格/星情) | 可补 | `if (dxNow >= 0) {` |
| 7485 | ziweiSuiXianOverlay | 1 | 1 | 2 | 7 | 业务判定(格局/运限/职业格/星情) | 可补 | `int dxSig = dx < 0 ? 0 : yunxianFortuneSign(at, dx, fourTransform);` |
| 7518 | parseChartBase | 1 | 1 | 0 | 2 | 工具(格式化/解析/查找/基础换算)·不计 | 不计 | `if (i > 0) {` |
