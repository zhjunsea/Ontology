# ZiWeiDouShu 应用 — 数据分支测试设计补充（覆盖矩阵 + 增量用例）

> 承接《ZiWeiDouShu应用-测试设计文档》与《-未覆盖分支逐条台账 v2》。
> 目的：按 SKILL「第 8/14 步：**数据分支＝按业务数据取值维度逐一覆盖**」口径，补齐「所有类型的星盘、命格」
> 的**逐值锁定**用例，并把「逻辑分支」缺口（handler 侧大限/流年/叠宫）通过**全星盘网格**端到端再压一遍。
> 全部固化为 JUnit，纳入 `AllZiWeiDouShuSuiteTest` 自动重放；期望值均据《紫微斗數全書》原文/既定表，不臆造。

---

## 1. 本次新增测试类

| 测试类 | 类型 | 用例 | 环境 | 定位 |
|---|---|---|---|---|
| `ZiWeiDouShuDataBranchMatrixTest` | 离线·反射 | 5 | 无 | 数据维度**逐值锁定**（命格等第/安命吉凶/告警三类/生年四化），断言精确分类值 |
| `ZiWeiDouShuChartGridTest` | 端到端·进程内引擎 | 22 图 | Ontop8080+MySQL3306 | 全星盘网格：12 命宫全链 + 粒度月/日 + 农历男女 + 经纬 + 验盘×3 + 小儿全链，驱动 handler 侧大限/流年/叠宫分支 |

> 二者已加入 `AllZiWeiDouShuSuiteTest` 的 `@SelectClasses`；环境不可达时 `ServiceProbe` 自动跳过（不阻断离线构建）。

---

## 2. 数据分支覆盖矩阵（维度 → 取值 → 覆盖用例 → 经典依据）

| # | 数据维度 | 取值（须逐一覆盖） | 覆盖用例 | 经典依据 |
|---|---|---|---|---|
| D1 | **命格等第** | 上上之命 / 上次之命 / 平等 / 平常命 / 下格之命 / 中格（6 档） | `ZiWeiDouShuDataBranchMatrixTest#mingGeGradeAllSixBranches`（逐档 crafted 星盘） | 卷三·论人命入格 / 看命总纲（c4 L22-36）：庙旺+科权禄齐会→上上；落陷加杀→下格… |
| D2 | **安命吉凶** | 12 生时支 × 12 命宫支 = 144 组（吉 / 少遂） | 同上 `#anMingJiXiongAllParity`（144 组全枚举，断言阴阳同组⇔吉） | 卷三·论人生时安命吉凶（c4 L66-67） |
| D3 | **告警类型** | 大凶(R18) / 寿元关(R19) / 重大疾病(R20) / 叠加 / 不触发 | 同上 `#alertTypesAllCombos` + `#shaPairAllThree` | R18~R21（核心设计 §5）；卷三·谈星要论凶格 |
| D4 | **生年四化** | 十天干 甲…癸 各 {禄,权,科,忌} 4 星 = 40 星 | 同上 `#sihuaAllTenStemsExact`（逐干逐字精确） | 卷二·安禄权科忌四星变化诀（壬→梁紫府武，非通行「左辅化科」） |
| D5 | **命格高低** | 上格 / 中上(富) / 中(会煞) / 下(贫) / 上(三方皆吉) / 中(吉凶交参) / 下(凶) / 常格 / 杂局 | `ZiWeiDouShuBranchGapBatchTest#gradeOfAllBranches`（既有） | 卷一·定富局/定贵局/定贫贱局 + 卷三·论格星数高下 |
| D6 | **格星数高下** | 上格(1~3) / 中格(4~6) / 下格(7~9) | `ZiWeiDouShuStarTableTest`（既有：贵局/贫贱局/数维待考） | 卷三·论格星数高下（c4 L37-54） |
| D7 | **五行局** | 水二/木三/金四/土五/火六（5 类） | `ZiWeiDouShuDataClassCoverageTest#bureauOfAllFiveViaNayin`（60 甲子）+ `matrix_cases` bureau_*（E2E） | 卷二·安紫微定位（纳音五行定局） |
| D8 | **庙陷** | 庙/旺/得地/利益/平和/不得地/陷（7 级） | `ZiWeiDouShuDataClassCoverageTest#brightnessExhaustive`（21 星×12 宫） | 卷二·星辰庙陷（第一权威） |
| D9 | **星辰生克** | 比和/宫生星/宫克星/星生宫/星克宫（5 类） | `ZiWeiDouShuDataClassCoverageTest#shengKeAllFiveClasses` | 卷二·星辰生克制化（c3 L137-140） |
| D10 | **阴阳男女** | 阳男/阴男/阳女/阴女（4 类） | `matrix_cases` yg_*（E2E）+ `ZiWeiDouShuChartGridTest`（女命全链/农历女/验盘女） | 卷二·安星男顺女逆；女命骨髓赋 |
| D11 | **命宫（12 地支）** | 子丑寅卯辰巳午未申酉戌亥（12/12） | `matrix_cases` ming_*（E2E 本命）+ **`ZiWeiDouShuChartGridTest` cg_full_*（12 命宫全链）** | 卷二·安身命例 |
| D12 | **推演深度** | 本命 / 本命+大限 / 本命+大限+流年（3 档） | `matrix_cases` depth_* + **`ZiWeiDouShuChartGridTest` 12 命宫全链** | 流程 G3 Gateway_Depth；卷三·大限/流年 |
| D13 | **命主类别** | 成人男 / 成人女 / 小儿（3 类） | `matrix_cases` female/child + **`ZiWeiDouShuChartGridTest` cg_child_full（小儿全链）** | 流程 G2 Gateway_Subject；卷三·论小儿命/男女命同异 |
| D14 | **输入模式** | 直接排盘 / 验盘（含多 subjectTraits） | `matrix_cases` verify + **`ZiWeiDouShuChartGridTest` cg_verify_a/b/c** | 流程 G1 Gateway_Mode；定盘加权（外部通行法） |
| D15 | **历法** | 公历 / 农历（双向换算） | **`ZiWeiDouShuChartGridTest` cg_lunar_m/cg_lunar_f** | `LunarCalendarConverter` 1900–2099；阶段三缺陷修复 |
| D16 | **经度/真太阳时** | 东经(北京/东京) / 西经(纽约 74W) | **`ZiWeiDouShuChartGridTest` cg_east/cg_west** | 程序实现（真太阳时，非《全書》断法） |
| D17 | **推演粒度** | 年 / 月 / 日 | `InMemoryDataMatrixTest` gap_gran_* + **`ZiWeiDouShuChartGridTest` cg_gran_month/day** | 流月/流日（外部通行法，I·62/I·51 叠合） |

> 覆盖方式说明：D1~D4、D7~D9 为**离线确定性逐值**（快、可独立回归）；D10~D17 为**端到端数据矩阵**
> （真实生产 Worker 跑进程内 Zeebe 引擎，计入 JaCoCo，驱动 handler 分支）。

---

## 3. 逻辑分支增量（handler 侧）

「全星盘网格」`ZiWeiDouShuChartGridTest` 把 `matrix_cases` 的 12 个命宫命例由「本命」提升为「本命+大限+流年」全链，
并叠加月/日粒度/农历/经纬/验盘/小儿，重点驱动未覆盖分支台账 §2 的大头：

| 目标方法 | 未覆盖分支(台账) | 驱动维度 |
|---|---|---|
| `handleAnalyzeMajorLimit` | 6 | 12 命宫×全链（各命宫大限宫位/主星庙陷/吉凶各异） |
| `handleAnalyzeAnnualFortune` | 7 | 全链（流年太岁/流年四化/太岁小限择段） |
| `handleAnalyzePalaceOverlay` | 7 | 全链 + 粒度月/日（四盘叠合层） |
| `handleDeterminePattern` | 22 | 12 命宫多命格组合 + 既有 fix_* 格局 |
| `handleInterpretStarNature` | 13 | 多命宫主星星情 |
| `judgeLongevity`/`judgeTongYuan`/`judgeTanXingYaoLun` | 9/9/5 | 多命宫交叉（纯函数经端到端盘驱动） |

> 说明：非「臆造期望」，`ZiWeiDouShuChartGridTest` 仅断言「流程 COMPLETED 且无 incident」——即以真实数据组合
> 驱动分支执行；正确性由既有 112+ 经典校验用例（StarTable 72、PatternMatrix、Ontology、SKOS 等）保证。

---

## 4. 运行方式

```bash
# 仅离线数据分支（无需环境，秒级）
mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkZiWeiDouShu -am test \
    -Dtest=ZiWeiDouShuDataBranchMatrixTest -Dsurefire.failIfNoSpecifiedTests=false

# 端到端全星盘网格（需 Ontop:8080 + MySQL:3306）
mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkZiWeiDouShu -am test \
    -Dtest=ZiWeiDouShuChartGridTest -Dsurefire.failIfNoSpecifiedTests=false

# 全套件
mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkZiWeiDouShu -am test -Dtest=AllZiWeiDouShuSuiteTest
```

> JDK：须 `JAVA_HOME=D:\work\Ontology\jdk-27`（项目 release=27，默认 java 26 无法编译）。
