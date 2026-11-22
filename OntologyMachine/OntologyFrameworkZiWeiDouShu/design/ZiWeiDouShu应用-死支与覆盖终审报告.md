# 紫微斗数 · 死支与覆盖「三阶段终审」报告（证据级）

> 日期 2026-11-20　范围：判定族 9 方法 + 全项目经典覆盖 + 全类覆盖率
> 证据源：源码逐行、`target/site/jacoco/jacoco.xml`（全环境 exec 10.75MB）、《原文章节行号对照表》《经典符合性审查报告》《未覆盖分支逐条台账》、`ZiWeiDouShuJudgeResidualBranchTest` 注释。

## 阶段一　经典书籍 100% 内容覆盖（无遗漏）——PASS
- 权威源：《紫微斗數全書》EPUB 三卷（c2 卷一 649 行 / c3 卷二 1283 行 / c4 卷三 477 行），`原文章节行号对照表.md` 已把每章映射到代码方法。
- 结论（`经典符合性审查报告` §22.4 / §41 总账）：
  - 卷一 19 目：判定类格表/断语**全部收敛**，举例式以〔取文/待考〕透明登记；
  - 卷二：30 安星诀 + 十二宫各星吉凶诀 **813/813 内容行字面 100% 对齐、宫位零错位**（`palaceClassicCoverageTest`）+ 庙陷/强弱/长生/三限**全部落地**；
  - 卷三 20 目：17 目判定类落地并有测试；`論人生時要審的確`、`定小兒生時訣` 2 目为术者验时法，已由 `birthTimeVerifyText` **落地为取文输出**；**判定类缺口 = 0**。
- 口径说明：所谓 100% 指**每一章均有落点、判定逻辑无缺**；原文明示的「举例式」句按铁律以〔取文·待考〕登记（不臆造），非遗漏。

## 阶段二　被删除死支核验（无误删）——PASS
被删/化简的 4 处恒真支，逐输入等价（详见下表）；唯一保留的数据守卫 1 条。

| # | 位置 | 原形 → 现形 | 恒真依据 / 等价性 |
|---|---|---|---|
| 1 | gradeOf | `if(ji&&sha==0)ret上; if(ji&&sha>0)ret中; if(sha>0)ret下; ret常` → `if(ji){if(sha==0)ret上; ret中;} if(sha>0)ret下; ret常` | 到达第二 if 时 `ji` 真且首 if 假 ⇒ `sha!=0` ⇒ `sha>0` 必真；被删的 `sha>0==false` 侧不可达。真值表：ji真/sha0→上；ji真/sha>0→中；ji假/sha>0→下；ji假/sha0→常。逐格与原一致 |
| 2 | judgeMingGongGe | 删除 `branches == null \|\|` | `branches = clauseZhi.isEmpty()? carry : clauseZhi`，`carry=new HashSet<>()`、`zhiChars()` 非空（否则上句 `.isEmpty()` NPE）⇒ 恒非 null |
| 3 | judgeMingGongGe | `afterFull = sentStart>=0? sent.substring(...) : after` → 直接 `sent.substring(sentStart+pi+2)` | `c` 取自 `sent.split(...)`、恒为 `sent` 子串 ⇒ `sent.indexOf(c)>=0` 恒真 |
| 4 | judgePalaceZhiGrid | `fgArr.length>0? fgArr[0].trim() : ""` → 直接 `row[1].split("、")[0].trim()` | `String.split` 恒返回长度≥1 数组 ⇒ `length>0` 恒真 |
| 保留 | judgePalaceZhiGrid L6574 | `!fgFirst.isEmpty() && !matched.contains(...)` **保留** | 数据防御守卫：仅当 `row[1]` 以「、」起首才为空（语料不变量），据实计 61/62 |

- 另一处**非等价但属纠错**：`judgeLongevity`「破武**未**宫」→「破武**巳亥**」（《全書》安星武曲破军仅同宫于巳/亥），有 `poXiang/bc` 回归用例；此为**订正**非删支。
- 额外核查：`inSanFang(int,String)` 第二参须为**地支串**。历史 bug（L2307/2308 传「左辅/右弼」、L6332 传「天刑」星名 → 恒 false 死支）**已修复**：现 L2310/2311 用 `zuo/you`（=`branchOfStar` 地支串）、L6378 用 `starInSanFang`。全量 `inSanFang(` 调用点复核**均传地支串**（`ziwei/tianfu/…/zuo/you` 皆 `branchOfStar` 产物），**无残留星名死支**。

## 阶段三　业务/数据/逻辑分支测试覆盖——PASS（判定族）
- 权威 jacoco（全环境）判定族 9 方法：

| 方法 | 分支 |
|---|---|
| palaceStarQuote | 6/6 ✅ |
| judgeMingGongGe | 60/60 ✅ |
| judgeTwoLimitsTaiSui | 46/46 ✅ |
| gradeOf | 18/18 ✅ |
| judgeQiShaReFeng | 22/22 ✅ |
| judgeLiuNianTaiSui | 20/20 ✅ |
| judgeTanXingYaoLun | 52/52 ✅ |
| judgePalaceZhiGrid | 61/62 ⚠（1 支保留防御 L6574） |
| judgeTongYuan | 90/90 ✅ |

- 整类：`BRANCH 4173/4374 = 95.4%`、`LINE 3986/4066`。剩余 ~201 支按台账分类为 A 类（真实 LLM/OBDA/引擎 ioMapping）＋工具/判空防御/非法入参边界/异常兜底，**非判定族业务缺口**，且均已在台账逐条登记（无未解释项）。
- 数据分支：`ZiWeiDouShuJudgeMingGongGeInjectionTest` / `JudgeTongYuanInjectionTest` / `BClassResidualCoverageTest6~14` 覆盖空语料(rows/star null)、末星块 end=size、煞凑布尔(含陷/不含陷 × 满足/不满足)、xianOk 真/假两侧、二次待考、ONTO_* 缓存有/无、4列/首星不命中行等。

## 终审结论
三查均通过：经典判定内容 100% 覆盖（判定类缺口 0）、无死支、无误删（4 处恒真化简逐输入等价，1 处数据守卫按约定保留）、判定族业务/数据/逻辑分支全覆盖（除上述 1 条防御支）。不虚报、不臆造。
