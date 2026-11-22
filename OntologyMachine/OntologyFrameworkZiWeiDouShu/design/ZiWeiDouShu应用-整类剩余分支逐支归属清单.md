# 紫微斗数判定族 · 整类剩余分支逐支归属清单

> 生成依据：`target/site/jacoco/jacoco.xml`（全环境 jacoco exec）解析 `ZiWeiDouShuOntologyJobWorker` 类全部 `mb>0` 分支。
> 明细原始数据：`HWCodeArtsDir/_whole_class_branch_detail.out`（155 行 / 201 支）。
> 口径：**B 口径**——仅删语言级恒真死支（行为等价化简）；数据防御/非法入参守卫一律保留，据实登记未覆盖，不虚报 100%。

## 0. 总量对账

| 项 | 值 |
|---|---|
| 整类 BRANCH 覆盖 | 4173 / 4374 = 95.4% |
| 整类 LINE 覆盖 | 3986 / 4066 |
| **剩余未覆盖 BRANCH** | **201 支**（分布 38 个方法、155 个源码行） |
| 其中落在「判定族」相关 | 13 支（judgePalaceZhiGrid 1 + tongYuanObjective/Full 12），**全为防御** |
| 其余 188 支 | 全部落在 handler 编排 / 工具函数 / 数据防御 / 非法入参 / 异常兜底 / A 类环境依赖 |

分类汇总（启发式 + 人工复核）：判空/防御 102 支、业务/其他 73 支、非法入参边界 18 支、工具/解析 8 支。

---

## 1. 判定族 9 方法核账（本次终审对象）

| 判定方法 | BRANCH 覆盖 | 剩余 | 结论 |
|---|---|---|---|
| palaceStarQuote | 6/6 | 0 | 100% |
| judgeMingGongGe | 60/60 | 0 | 100% |
| judgeTwoLimitsTaiSui | 46/46 | 0 | 100% |
| gradeOf | 18/18 | 0 | 100% |
| judgeQiShaReFeng | 22/22 | 0 | 100% |
| judgeLiuNianTaiSui | 20/20 | 0 | 100% |
| judgeTanXingYaoLun | 52/52 | 0 | 100% |
| judgeTongYuan（**入口方法**） | 90/90 | 0 | 入口 100% |
| judgePalaceZhiGrid | 61/62 | 1 | 仅 L6574 数据防御守卫（口径 B 保留） |

**judgeTongYuan 族的补充说明（诚实披露）**：`judgeTongYuan`(L6605, 6 参) 在 L6675 委派 `tongYuanObjectiveFull`，后者先调 `tongYuanObjective`。这两个 helper 尚有 12 支未覆盖，但**逐条核对全部为「非法/缺失盘面」的防御返回**：

| 行 | 条件 | 性质 |
|---|---|---|
| tongYuanObjective L6746 | `branchIndexOf(at,"禄存")!=null` 的 false 侧 | 盘无禄存时防御（完整盘恒有禄存） |
| L6753 | `cai<0 && tian<0 → null` | 财帛/田宅宫位缺失防御 |
| L6765 | `yearGan<0 → null` | 年干非法防御 |
| L6790 | `qian<0 && mingIdx<0 → null` | 迁移/命宫缺失防御 |
| tongYuanObjectiveFull L6929 | `cai<0 && fu<0 → null` | 财帛/福德缺失防御 |
| L6930 / L6932 | `cai>=0` / `fu>=0` 的 false 侧 | 宫位缺失防御（非「地劫」业务判定） |
| L6992 | `fu<0 → null` | 福德宫缺失防御 |

**判定**：这 12 支在完整合法命盘中永不触发，属数据防御守卫，与 judgePalaceZhiGrid L6574 同类。**判定族无未覆盖的「业务/数据/逻辑」判定分支**。

---

## 2. 整类 201 支 · 按方法归属清单（判定族 13 支 + 其余 188 支）

> 下表为**全量 201 支**方法级归属（已含 §1 的判定族 13 支），各行剩余支合计 = 201。

图例：A=需真实环境（Ontop/MySQL/Zeebe/LLM）；D=数据防御（判空/越界/缺失盘面）；E=异常兜底；T=工具/解析；C=罕见组合（可 crafted 盘补测，属可选扩展）。

| 方法 | 剩余支 | 归属 | 说明 / 为何不计为判定缺口 |
|---|---|---|---|
| loadStateFromObda (+lambda) | 38 | A+D | 需真实 OBDA 全量表；`表size!=N` 告警、null 行跳过均属数据校验防御 |
| verifyConsistency | 14 | D | 仅在内置表与 OBDA 数据「不一致」时触发，正常数据不进入 |
| diffRows | 2 | D | 长度/null 差异分支 |
| deityName | 1 | D | 缓存 null/越界防御 |
| verifyObdaTableCoverage | 6 | A+D | 需真实 obda/ddl 文件可读 |
| handleParseBirthInput | 1 | D | 出生变量 null/空白防御 |
| handlePlaceMainStars | 1 | D | 局/日越界（非法入参） |
| handleAssessBrightness | 4 | T+D | 消息拼接三元兜底、rel 判空 |
| handleInterpretStarNature (+lambda) | 12 | D+C | 主星集判空、消息三元兜底、辅助星罕见组合 |
| handleDeterminePattern (+lambda$1/$2) | 27 | C+A | 罕见格局组合（机月同梁/杀破狼/日月反背/魁钺等）+ 端到端驱动；需 crafted 盘 |
| handleAnalyzeMajorLimit (+lambda) | 4 | D+C | 判空 + 罕见限内组合 |
| handleAnalyzeAnnualFortune | 13 | D+T | 粒度边界、判空、消息三元兜底 |
| handleAnalyzePalaceOverlay | 5 | D+T | 判空、消息三元兜底（吉凶交参/平） |
| handleSendAlert | 10 | A | 需告警触发/阈值命中的真实数据 |
| extractAlertTypeFromReading | 2 | T+A | 取文规则未命中兜底 |
| handleInterpretResult | 4 | D | 结果/文本 null 防御 |
| handlePersistAndReturn | 3 | A | 需 backendService/OBDA handler 就绪 |
| loadPalaceClassic (+lambda) | 12 | A | 需 ONTO_CLASSIC_CORPUS（813 条）真实全量语料 |
| detectShaCou | 4 | D+C | 星集空判 + 组合 |
| resetOntologyCaches | 1 | T | 反射修饰符（final 字段跳过）分支 |
| fail | 1 | E | 异常消息 null 兜底 |
| appendLine | 1 | D | value null/空白防御 |
| representativeHour | 1 | D | 时辰串 null/格式防御 |
| isJinYuFuJia | 3 | C | 生年/生时支取值范围罕见组合 |
| bureauOf | 1 | T | switch 默认分支 |
| parsePlacements | 1 | D | 位置 b<0 防御 |
| palaceStarRelation | 1 | C | 星克宫（`pe==(se+2)%5`）罕见关系 |
| majorEventBacktrace | 6 | D+C | chunk 判空 + 吉凶盘符号组合 |
| candidateMingStars | 2 | D | 局/日越界非法入参 |
| verifyCandidateScore | 1 | C | 昌曲夹命组合 |
| judgePalaceZhiGrid | 1 | D | **L6574 `!fgFirst.isEmpty()` 数据防御守卫**（口径 B 保留） |
| tongYuanObjective | 6 | D | 宫位/年干/禄存缺失防御（详见 §1） |
| tongYuanObjectiveFull | 6 | D | 宫位缺失防御（详见 §1） |
| ganListMatches | 1 | D | 干支空判 |
| ganListHasSuspectToken | 1 | D | 干支空判 |
| resolveBirthDateTime | 3 | D+T | 多日期格式判空/解析 |
| extractLongitude | 1 | C | 「西经/W」罕见输入 |

---

## 3. 结论

1. **判定族 9 方法**：8 个 BRANCH 100%，`judgePalaceZhiGrid` 61/62（仅 L6574 数据防御）；`judgeTongYuan` 入口 90/90，其 helper 的 12 支亦全为非法/缺失盘面防御。→ **无未覆盖的业务/数据/逻辑判定分支**。
2. **整类剩余 201 支**：13 支为判定族相关防御 + 188 支落在 handler 编排/工具/数据防御/非法入参/异常兜底/A 类环境依赖；**无未解释的业务分支**。
3. **可选的真正可补项**：仅 handler 侧「罕见格局组合」（C 类，约 12–15 支，集中于 handleDeterminePattern / palaceStarRelation / isJinYuFuJia）可通过 crafted 命盘扩充语料覆盖——属**判定族之外的编排层增强**，非判定族缺口。
4. **A 类**（loadStateFromObda / verifyObdaTableCoverage / handleSendAlert / loadPalaceClassic / handlePersistAndReturn 等）：需真实 Ontop/MySQL/Zeebe/LLM 环境方能触达，属环境依赖，非逻辑缺口。

> 未覆盖明细可随时由 `python HWCodeArtsDir/_whole_class_branch_detail.py` 从当前 jacoco.xml 重新导出，行级自动归属方法并启发式分类。
