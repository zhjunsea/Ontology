# TCMOntologyJobWorker 未覆盖分支审计台账

> 日期 2026-11-22。数据源：完整 373 项 E2E 跑在带 JaCoCo 探针的 Worker 上落盘的 `jacoco-worker.exec`（经 `jacoco:report` 生成 `jacoco.xml`）。
> 类整体：**分支 830/1260 = 65.9%**；行 1891/2270 = 83.3%；指令 10071/12041 = 83.6%；方法 157/182 = 86.3%。
> 说明：本台账用于区分「真·死方法/不可达」「可达但未被数据触发（SWRL 备选/资源定位/场景差异）」「A 类运行期守卫」，避免把可达但未覆盖的分支误当死支而乱改。

## 一、真·死方法（类内 0 调用、private，不可达）

对全类精确扫描（排除定义与 javadoc），以下 private 方法**类内 0 次调用**，且不被其它类引用（private）→ **死代码**：

| 方法 | 定义行 | 未覆盖分支 | 未覆盖指令 | 结论 |
|---|---|---|---|---|
| `topBySymptomOverlap` | 1025 | 12 | 67 | 死方法（疑被 `rankCandidatesWithScores` 取代） |
| `hasBagangOrLiujing` | 1289 | 10 | 32 | 死方法 |
| `queryParentHerbs` | 4024 | 8 | 58 | 死方法 |
| `queryHasHerbs` | 3995 | 12 | 96 | 死方法（仅被死方法 `queryParentHerbs` 调用） |

- 合计 **42 个未覆盖分支 / 253 条未覆盖指令**，全部来自死方法。
- 处置建议：与 §八 的 4 个遗留引擎同策略——**标注为非生产、不计入「业务覆盖」口径**；如需彻底清理可整段删除（这些方法私有且零调用，删除不影响任何生产链路，但需回归 373 项 E2E 确认）。
- 剔除死方法后，业务分支覆盖约 **830 / (1260-42) ≈ 68.1%**。

## 二、可达但未被数据触发（非死代码，勿误改）

| 方法/路径 | 定义行 | 未覆盖分支 | 触发条件 | 说明 |
|---|---|---|---|---|
| `deriveFormula` | 3307 | 44 | SWRL 派生失败回退（`computeHerbModification` L3945-3947 的 `else`）；及测试钩子 `deriveFormulaForTest` | 现行 E2E 均走 Openllet SWRL 派生成功路径，回退分支未触发 |
| `extractFormula` / `findHasValue` | 4057 / 4081 | 14 / 12 | `handlePrescriptionRecommendation`(L3634) | `extractFormula` 部分覆盖（走①注解 `hasPrescription` 分支 L4060，命中）；`findHasValue`/`∃you_chufang` 旧写法为**向后兼容回退**，因注解分支恒命中而未触发——**非死支、非 bug** |
| `queryRemovedHerbs` | 3966 | 10 | `handlePrescriptionRecommendation` L3648 / `handleHerbModification` L3788 | 部分路径已覆盖，剩余为未走到的注解值 |
| `findFileRecursive` | 3172 | 18 | `locateRulesOwl`(L3165) 的资源定位分支 | rules.owl 定位；E2E 走了缓存/主路径，未触发递归查找 |
| `buildCombinedDiseaseMark` | 2469 | 30 | 合病/兼夹组合场景 | 数据依赖，需更全合病组合的用例 |
| `analyzeGaps` | 2802 | 29 | 缺口分析多分支（AND/OR/或然/抽象叶子） | 数据依赖 |
| `resolveLiujingCrossProduct` | 2386 | 12 | 病位×病性交叉积消解 | 特定合病输入才触发 |
| `parseHerbRule` | 3197 | 12 | rules.owl 文本规则解析的异常/边界 | 解析容错分支 |
| `handleFangzhengClassification` | 2529 | 11 | 方证分类的多候选/无候选分支 | 场景依赖 |

## 三、结论与下一步

1. **死方法（一）**：42 分支确属不可达死代码，按既定策略标注非生产/不计入口径（可选整段删除）。与经典医理无关（是废弃实现，不是"逻辑写错"）。
2. **可达未覆盖（二）**：属 SWRL 备选/资源定位/更全场景，**不是程序 bug**，不宜"凑覆盖"或删分支；可通过补充对应场景用例提升（如：构造 SWRL 不激发的输入覆盖 `deriveFormula` 回退；构造合病组合覆盖 `buildCombinedDiseaseMark`）。
3. **已核实（BPMN 时序正常，非 bug）**：流程顺序为 `fangzheng-classification → 兼夹 → prescription-recommendation → herb-modification → explanation`；`handlePrescriptionRecommendation` 确被执行（400 指令覆盖），其 `extractFormula` 走①注解分支（`hasPrescription`）命中；`findHasValue`/`∃you_chufang` 旧写法为**向后兼容回退**，因注解恒命中而未触发——属**有意保留的回退**，非死支、非 bug。`herb-modification` 复用同一 `computeHerbModification`，两步逐字段一致。

## 四、审计结论

- **无"业务逻辑写错"型死支**：未发现经典医理层面的不可达/错误分支。
- 未覆盖分支归为两类，均**不应乱改**：
  1. **4 个废弃私有方法**（`topBySymptomOverlap`/`hasBagangOrLiujing`/`queryParentHerbs`/`queryHasHerbs`，共 42 分支）——死代码，与经典无关；
  2. **有意保留的回退/容错分支**（SWRL vs 字符串规则、注解 vs 旧 ∃you_chufang、资源递归定位、合病组合场景）——可达但当前 E2E 数据未走到。
- **已完成清理（2026-11-22）**：4 个死方法（`topBySymptomOverlap`/`hasBagangOrLiujing`/`queryHasHerbs`/`queryParentHerbs`，共 81 行）已删除，回归离线套件 116 项全绿；`TCMOntologyJobWorker` 分支 69.5%→71.9%，业务口径整体 70.4%→72.0%（详见主报告 §十三）。回退分支通过**补充对应场景用例**提升覆盖，而非删除。

## 五、为什么会有死代码（溯源）

本项目 TCM 模块历史经历多轮实现替换（git 历史多为整合提交，无逐步 blame，但代码内注释与命名可证）。这 4 个方法与 §八 的 4 个引擎类成因一致——**新实现取代旧实现，旧方法未删除**：

| 死方法 | 被谁取代（证据） |
|---|---|
| `topBySymptomOverlap` | 被 `rankCandidatesWithScores` 取代。后者注释（L1048-1052）写明「**2026-09-21 改：证据门控 + gap 主键 + 或然症第二键，零加权**」，取代了旧「按症状重叠计数 TopN」的简单排序。 |
| `queryHasHerbs` / `queryParentHerbs` | 被 OBDA 直查 `queryHerbsWithLabels` 取代。现行 `handlePrescriptionRecommendation`(L3643) 直接查 `you_yaowu` 得本方药物，不再走「母方子类继承药物」的旧路径。 |
| `hasBagangOrLiujing` | 旧的 `stage1Types` 是否含八纲/六经的判定助手，已被后续八纲/六经分类流程内联/替换。 |

- 共同模式：**功能演进时新增了更优实现，但旧的私有方法被遗留**。这类"死代码"是**维护债**（应清理），**不是"经典医理写错"的逻辑死支**。
- 与 §八 的 4 个遗留引擎类同理，本任务按用户既定策略：**保留为参考 / 标注非生产 / 不计入业务覆盖口径**（如需可整段安全删除，均为私有零调用）。

## 六、全量合并覆盖（本审计后的最新快照，见主报告 §十二）

并入全离线套件（含 `HerbRuleEngineTest` 覆盖 `deriveFormula`、`SymptomMappingServiceTest` 等）后：`TCMOntologyJobWorker` 分支 **65.9% → 69.5%**；业务口径（剔除 4 遗留死类）分支约 **70.4%**。详见主报告 §十二 总表。

> 本台账遵循任务铁律："死分支/不可达=程序问题→回查经典修正"；但**先严格区分「真死代码」与「可达未测」**，避免误删可达的回退/容错逻辑。
