# 塔架中段设计应用（TMSD）测试用例设计文档

| 项目 | 内容 |
|---|---|
| 被测系统 | OntologyFramework / TMSD（塔架中段定制化设计应用，完全正向设计） |
| 文档版本 | v2.0（按当前正向实现重写，取代 v1.0「匹配+回退」版） |
| 生成日期 | 2026-09-29 |
| 关联本体 | `TowerMidSection.owl` v16.3（16 条数值约束 + 9 条基数约束 + 3 条 hasKey） |
| 关联流程 | 《塔架中段-正向设计流程》（9 步）；`ontology/bpmn/TowerMidDesign.bpmn`（Camunda 8 / Zeebe，`Process_TowerMid`） |
| 测试代码位置 | `OntologyMachine/OntologyFramework/src/test/java/com/ocean/ontologyframework/`（`tmsd` 包 + 根包） |
| 输出物目录 | `OntologyMachine/OntologyFramework/tmsd-output/` |

> 本文档是对**现有已实现测试**的工程化描述（设计说明 + 用例清单 + 覆盖矩阵），所有数值、断言、分支均逐条取自测试源码，未作任何发明。

---

## 1. 目的与范围

**目的**：明确 TMSD 应用的质量保证方式——测什么、怎么测、用什么数据测、判定的依据是什么、如何执行。

**范围**：
- 覆盖「塔架中段定制化设计流程」的**唯一正向路径**（S0→S8 单链路、无匹配网关、无回退）、正向设计规则（附件排布 / 扶持 / 梯档 / 防雷螺柱 / 灯）、BPMN 流程编排、3 类模型参数 txt + 方案对比报告产出、本体（OWL）加载与约束一致性校验。
- 不含中医经方（TCM）与 Pizza 本体应用的测试（同工程内独立维护）。

---

## 2. 被测系统概述

### 2.1 业务链路（完全正向，全部环节必做）

```
设计输入（主体信息表 + 项目布局表；固定项：升降机=钢绳导向 / 区域=中国 / 连接=焊接）
   └─ S0 输入解析
   └─ S1 塔筒外形与分段几何（正向求解全部中段、单方案、附件间距贪心取大）
   └─ S2~S7 升降机(9)→配件类型(8)→直径(7)→高度(6)→筒节(5)→平台(4)（从会话取推荐方案回填）
   └─ S8 输出参数表（逐段 3 份 Creo txt(GKB) + 方案对比报告.md(UTF-8)）
   └─ 本体一致性校验（Openllet，真实环境）
```

**求解目标**：附件间距**贪心取大**（每步优先最大档位，避焊缝才降档），单一方案，不再保留多方案变体对比。

**关键类**：`TowerGeometry`（几何读取）、`TowerExcelReader`（Excel 读入）、`LayoutSpec`（布局表）、`TowerDesignRequest`（设计输入）、`TowerDesignEngine`（正向规则求解）、`TmsdDesignPipeline`（求解 + 本体 16 条数值约束逐条判定）、`TmsdOutputWriter`（txt/报告写出）、`TmsdOntologyModel`（OWL 运行时解析）、`TmsdVocabulary`（本体解析结果的唯一入口）、`TMSDOntologyJobWorker`（Zeebe JobWorker）。

### 2.2 流程节点（BPMN 元素 ID，全部为 `serviceTask`）

| 环节 | 元素 ID | jobType |
|---|---|---|
| S0 输入解析 | `Task_Step0` | `tmsd-step0-parse-input` |
| S1 塔筒外形/分段几何 | `Task_Step1` | `tmsd-step1-tube-shape` |
| S2 升降机/扶持 | `Task_Step9` | `tmsd-step9-elevator` |
| S3 配件类型替换 | `Task_Step8` | `tmsd-step8-accessory` |
| S4 直径设计 | `Task_Step7` | `tmsd-step7-diameter` |
| S5 高度设计 | `Task_Step6` | `tmsd-step6-height` |
| S6 筒节设计 | `Task_Step5` | `tmsd-step5-tubesection` |
| S7 平台设计 | `Task_Step4` | `tmsd-step4-platform` |
| S8 输出参数表 | `Task_Output` | `tmsd-output-parameters` |
| 开始 / 结束 | `StartEvent_Start` / `End_Design` | — |

单一顺序路径 `StartEvent_Start → Task_Step0 → Task_Step1 → Task_Step9 → … → Task_Step4 → Task_Output → End_Design`，**无排他网关、无回退**。

### 2.3 BPMN ↔ JobWorker 绑定

BPMN 与 JobWorker **无代码级引用**：BPMN 部署到流程引擎（Zeebe）后，由引擎按拓扑推进实例；到达某 `serviceTask` 时按 `zeebe:taskDefinition type` 派发作业，订阅**同名 jobType** 的 `@JobWorker`（`TMSDOntologyJobWorker`）被回调执行。**BPMN 管「怎么串」、引擎管「驱动」、Worker 管「每步怎么算」、本体管「约束对不对」**，四者靠 jobType 字面量绑定。

---

## 3. 测试分层与策略

| 层级 | 测试类 | 依赖引擎 | 是否需环境（Camunda/MySQL/Ontop） | 关注点 |
|---|---|---|---|---|
| 正向规则单元（纯 Java） | `TMSDForwardRulesTest` | 无 | 否 | 附件排布 / 边缘避焊缝 / 电缆线夹合并 / 防雷螺柱角度 / 灯间距 |
| 流水线集成（纯 Java） | `TMSDDesignPipelineTest` | 无 | 否 | 正向单方案求解、本体 16 条数值约束逐条判定、机型变化 |
| 本体加载（纯 Java） | `TmsdOntologyLoadTest` | 无 | 否 | OWL 加载、Openllet 一致性、v16.3 实体/约束/值集解析 |
| 端到端（内存引擎） | `TMSDProcessTest` | `@ZeebeProcessTest` 内存引擎 | 否 | BPMN 编排、单一路径顺序、输出物落地及参数合规 |
| 端到端（真实环境） | `TMSDDesignProcessTest` | 真实 Zeebe + TMSDApplication | **是** | 真实作业消费、本体一致性（Openllet）、输出文件存在 |
| 诊断（人工） | `TmsdDebugTest` | 无 | 否 | 打印各段几何/焊缝/附件排布搜索中间量 |

**设计原则**：
1. **分层解耦**——纯 Java 层（规则/流水线/本体/诊断）不依赖任何外部服务，可在 CI 快速回归；外部依赖集中在两个端到端测试。
2. **同一份数据贯穿**——各层共用两个历史样本 Excel（几何 + 布局）构造设计输入。
3. **本体为唯一裁判**——判定依据运行时解析自 `TowerMidSection.owl`（`TmsdVocabulary`），不臆造阈值。
4. **正例为主**——完全正向流程无「命中/否决」分支；反例以「边缘偏移 455 时 280 网格无解」作对照。

---

## 4. 测试环境与配置

### 4.1 配置加载（`TmsdTestConfig`）

读取 `application.yml`（TMSD 唯一配置源；TCM 已移除、`application-TMSDBPMN.yml` 已合并删除）。关键键：

| 键 | 值 |
|---|---|
| `ontology.main-path` | `D:/work/Ontology/GoldenWind/ontology/TowerMidSection.owl` |
| `ontology.bpmn-path` | `D:/work/Ontology/GoldenWind/ontology/bpmn/TowerMidDesign.bpmn`（唯一真值，Camunda 8 / Zeebe 可执行副本） |
| `tmsd.historical-geo-path` | `design/towerdesign-main/TowerGeoInput_10563080_HH130m_6段_…_主体434t-….xlsx` |
| `tmsd.historical-layout-path` | `design/towerdesign-main/项目布局表.xlsx` |
| `tmsd.output-dir` | `.../OntologyFramework/tmsd-output` |
| ~~`tmsd.platform-inner-diameter-tolerance-mm`~~ | v16.4 已删除该配置项并迁移至本体：`Platform ⊑ platformInnerDiameterTolerance hasValue 6`（老程序 `platSugget()` 中段分支 `choose2(6, ...)`，2.X 机型 ±6mm），配置录入网页不再提供该项 |
| `camunda.client.grpc-address` | `http://localhost:26500` |

### 4.2 真实环境端到端测试前置
1. 启动环境（Camunda 8 / MySQL / Ontop）：`EnvPrepare/scripts/start_all.py`；
2. 启动 `TMSDApplication`（`--enable-native-access=ALL-UNNAMED`），激活 `TMSDBPMN` profile；
3. Zeebe Gateway 可达 `localhost:26500`。

### 4.3 BPMN 部署与 Camunda 可见性

- **唯一真值文件**：可执行流程定义位于 `D:/work/Ontology/GoldenWind/ontology/bpmn/TowerMidDesign.bpmn`（Camunda 8 / Zeebe，`isExecutable="true"`，`Process_TowerMid`），配置键 `ontology.bpmn-path` 指向该文件；**后续所有流程更新一律改此文件**。同目录另有源设计图 `塔架中段定制化设计流程.bpmn`（`isExecutable="false"`、无 zeebe 扩展），仅供审阅、**不可部署**。
- **部署时机**：工程 `src/main` 内**没有任何部署代码**（`TMSDApplication` 是 `web-application-type: none` 的纯 JobWorker，只消费作业、不部署）。BPMN 的部署只发生在测试：`TMSDProcessTest` 用 `@ZeebeProcessTest` **内存引擎**（进程结束即销毁，Camunda UI 不可见）；`TMSDDesignProcessTest` 连 `localhost:26500` 的**真实 Zeebe** 并 `deployResource`。
- **Camunda 里看不到「任务」**：本流程 9 个节点**全部是 `serviceTask`、没有 `userTask`**，由 `@JobWorker` 后台自动认领执行；Camunda **Tasklist 只显示 `userTask`**，故 Tasklist 中不会有任何待办任务（属设计使然、非缺陷）。要在 **Operate / Modeler** 里看到该流程定义或实例，须先把 `ontology/bpmn/TowerMidDesign.bpmn` 部署到目标 Zeebe 引擎。
- **驱动机制**：BPMN 部署到 Camunda 后，由引擎按拓扑推进流程实例；到达某 `serviceTask` 时按 `zeebe:taskDefinition type` 派发作业，订阅同名 jobType 的 `@JobWorker` 被回调执行、完成后引擎续走下一步——**应用不写死流程、也不读取/解析 BPMN 自行执行**。

---

## 5. 测试数据设计

### 5.1 基准输入（两个历史样本 Excel）

| 项 | 值 |
|---|---|
| 段数 / 法兰数 | 6 段 / 7 法兰 |
| 中段范围 | 第 2 ~ 第 5 段（段总高 18480 / 19600 / 24920 / 24640 mm，均 280 整数倍） |
| 平台处内径 | `diCal(段总高 − 平台位置, n)` |
| 中段段号来源 | `TowerDesignRequest.middleSectionNumbers()` |

### 5.2 设计输入（`TowerDesignRequest`）

构造参数：`(caseName, geometry, model, ElevatorType, region, AccessoryConnectionType, layout)`。固定项：

| 参数 | 取值 |
|---|---|
| 升降机类型 `ElevatorType` | `ROPE_GUIDED`（钢绳导向） |
| 区域 | `中国`（线夹隔开） |
| 附件连接方式 `AccessoryConnectionType` | `WELDED`（焊接式） |
| 机型 `model` | 见 §5.3 |

### 5.3 用例机型（按 caseIndex 轮换）

| 层 | 机型来源 | 取值 |
|---|---|---|
| `TMSDProcessTest` | `MODELS[floorMod(caseIndex, 4)]` | `{V12, V15, V17, V19}`（用例 caseIndex=2 ⇒ V17） |
| `TMSDDesignProcessTest` | caseIndex 0..6 | 同一正向路径、不同输入样例 |
| `TMSDDesignPipelineTest` | 基例 `V12` + 变化例 `V17` | — |

---

## 6. 测试用例清单（共 25 项）

### 6.1 `TMSDForwardRulesTest`（正向规则单元，5 项）

| ID | 方法 | 输入 | 预期断言 |
|---|---|---|---|
| F-1 | `accessoryLayoutRules` | 平台高 14000、焊缝 [2800,5600,8400]、边缘偏移 0 | 排布非空；首个增量 = 980；其余增量均为 280 倍数且 ∈[1400,1960]；末组到平台 ∈[840,1960]；边缘避焊缝净距 >100 |
| F-2 | `edgeAvoidance` | 同上，边缘偏移 0 vs 455 | 偏移 0 时有解（非空）；偏移 455 时 280 网格**无解（空）**——边缘偏移与 280 网格不相容的对照 |
| F-3 | `cableClampMerge` | 爬梯增量 [980,1960,1960,1960,1960] | 电缆线夹增量 = [980, 3920, 3920]（首组 980，其后每 2 个爬梯支撑增量合并为 1 组） |
| F-4 | `lightningStudAngles` | 本体值集 | `valueSet("lightningStudInstallAngle")` = [70, 190, 310] |
| F-5 | `lightRules` | 段高 30000、焊缝 [2800,5600,8400] | 灯非空；首灯 ∈[2600,3000]；相邻灯间距 ∈[5000,10000] |

### 6.2 `TMSDDesignPipelineTest`（流水线集成，4 项）

| ID | 方法 | 输入 | 预期断言 |
|---|---|---|---|
| P-1 | `solved` | 基例 V12 | 正向方案数 = **1**（单方案）；`satisfied()` 非空；`recommended()` 非空 |
| P-2 | `accessoryRules` | 基例 V12 | 每段：`rungSpacing=280`、`firstRungToBottom=140`、`lightType=焊接灯`；附件有解时首组=980、增量 280 倍数 ∈[1400,1960]、相邻间距 ∈[1400,1960]、末组到平台 ∈[840,1960]、边缘避焊缝净距 >100；无解时允许空（`accessoryCount=0`） |
| P-3 | `modelVariation` | 机型改 V17 | 仍可正向求解（`satisfied()` 非空、`recommended()` 非空）；`L_CABLE=800`（取自本体机型个体） |
| P-4 | `printAll` | 基例 V12 | 打印满足方案与差异（人工复核用，无断言） |

### 6.3 `TmsdOntologyLoadTest`（本体加载，6 项）

| ID | 方法 | 预期断言（关键） |
|---|---|---|
| O-1 | `versionIsV163` | 本体可被 OWL API 加载；文件含 `<owl:versionInfo>v16.3</owl:versionInfo>` |
| O-2 | `consistentByOpenllet` | Openllet `isConsistent()` = true |
| O-3 | `newEntitiesPresent` | 签名含 `LightningGroundingStud`、`hasLightningGroundingStud`、`lightningGroundingStudBelongsToFlange`、`rungSpacing`、`firstRungToBottomFlange`、`lightningStudInstallAngle`、`lightningStudFlangeDistance` |
| O-4 | `newRestrictionsPresent` | `Flange ⊑ =3 hasLightningGroundingStud`；`Ladder ⊑ rungSpacing = 280`；`Ladder ⊑ firstRungToBottomFlange = 140`；`LightningGroundingStud ⊑ lightningStudFlangeDistance = 50` |
| O-5 | `vocabularyParsedFromOntology` | `ontologyVersion()=v16.3`；数值约束 **16** 条；基数约束 **9** 条 |
| O-6 | `v163MachineReadable` | `valueSet("accessorySpacingMultiple")`=[5,6,7]；`valueSet("lightningStudInstallAngle")`=[70,190,310]；`stringValue("lightType")`=焊接灯；`modelParams("V17")`=[800,650,650]；`num("lastBracketToTopFlange")`=1000 |

### 6.4 `TMSDProcessTest`（内存引擎端到端，2 项）

| ID | 方法 | 用例 | 预期断言（关键） |
|---|---|---|---|
| E-1 | `forwardSinglePath` | caseIndex=2（V17） | 流程完成、无 incident；经过 `Task_Step0/1/9/8/7/6/5/4/Output/End_Design`；节点**顺序**精确一致；写出模型参数 txt 且均为真实文件 |
| E-2 | `outputTxtSatisfiesOntologyConstraints` | caseIndex=2（V17） | 附件信息 txt(GBK) 含 `SEC_H_total=`、`H_platform=1250`、`firstAccessoryToBottom=980`、`lastBracketToPlatform=200`、`ACCESSORY_CONNECTION_TYPE=`；有解时 `accessoryCenterSpacing∈[1400,1960]`、`secondLastToPlatform∈[840,1960]`，无解时含 `H1_L_Exist = no`；`firstLightHeight∈[2600,3000]`；同时存在「筒体信息关系式」「连接法兰关系式」txt；方案对比报告(md) 含「满足本体约束的设计方案」「推荐方案」 |

> 说明：本测试在内存引擎内**自带 worker 注册**（复刻真实逻辑：S0 构造输入、S1 求解、S9~S4 回填、Output 写文件），用于在不启动 Camunda 的前提下验证 BPMN 编排与输出落地。

### 6.5 `TMSDDesignProcessTest`（真实环境端到端，7 项）

| ID | 方法 | 用例 | 预期断言（关键） |
|---|---|---|---|
| R-1 | `case0` | 样例0 | 流程完成；`satisfiedCount>0`；`recommendedVariant` 非空；`outputFiles` 非空且文件均存在、含 `.txt` 与 `方案对比报告.md`；`ontologyConsistent=true`；`ontologyReports` 非空 |
| R-2 | `case1` | 样例1 | 同上 |
| R-3 | `case2` | 样例2 | 同上 |
| R-4 | `case3` | 样例3 | 同上 |
| R-5 | `case4` | 样例4 | 同上 |
| R-6 | `case5` | 样例5 | 同上 |
| R-7 | `case6` | 样例6 | 同上 |

**R 组前置**：真实 Zeebe（`localhost:26500`）+ 运行中的 `TMSDApplication`；`@Timeout 180s`，`StopOnTimeoutExtension` 主动中断挂起；连接由 `camunda.client.grpc-address` 决定（https 走 TLS，否则 plaintext）。

### 6.6 `TmsdDebugTest`（诊断，1 项）

| ID | 方法 | 作用 |
|---|---|---|
| D-1 | `dump` | 打印各中段的 `secH/platH/courses/welds` 及附件贪心取大结果（`n/spacing/lastToPlat/minWeld/heights`），供人工核对几何与排布搜索 |

---

## 7. 本体约束校验清单（判定依据，`TowerMidSection.owl` v16.3）

### 7.1 可数值校验的 Restriction（16 条，运行时解析自本体）

| 宿主类 | 属性 | 类型 | 约束 |
|---|---|---|---|
| Platform | platformToTopDistance | HAS_VALUE | = 1250 |
| Accessory | firstAccessoryToBottom | HAS_VALUE | = 980 |
| CableBracket | lastBracketToPlatform | HAS_VALUE | = 200 |
| CableBracket | lastBracketToTopFlange | HAS_VALUE | = 1000（老应用 `Cable_top_h`） |
| WeldedLight | lightStudSpacing | HAS_VALUE | = 500 |
| Ladder | rungSpacing | HAS_VALUE | = 280 |
| Ladder | firstRungToBottomFlange | HAS_VALUE | = 140 |
| LightningGroundingStud | lightningStudFlangeDistance | HAS_VALUE | = 50 |
| Accessory | accessoryToWeldDistance | GT | > 100 |
| Accessory | accessoryCenterSpacing | RANGE | [1400, 1960] |
| Accessory | secondLastToPlatform | RANGE | [840, 1960] |
| TowerMidSection | firstLightHeight | RANGE | [2600, 3000] |
| TowerMidSection | lightToLightMinSpacing | RANGE | ≥ 5000 |
| TowerMidSection | lightToLightMaxSpacing | RANGE | ≤ 10000 |
| WeldedLight | lightStudToWeldDistance | GT | > 100 |
| Support | supportToWeldDistance | GT | > 100 |

### 7.2 基数约束（9 条，ABox 结构校验）

`TowerMidSection ⊑ =1 hasTowerTube`、`=1 hasPlatform`、`=1 hasLadder`、`≥1 hasAccessory`；`RopeGuidedElevator ⊑ ≥1 hasSupport`；`LadderGuidedElevator ⊑ =0 hasSupport`；`WeldSeam ⊑ =2 connectsTubeSection`；`WeldedLight ⊑ =2 mountedOnStud`；`Flange ⊑ =3 hasLightningGroundingStud`。

### 7.3 hasKey（3 条）

`Model:[modelName]`、`Region:[regionName]`、`TubeSection:[tubeSectionNumber]`。

---

## 8. 预期输出物

`TmsdOutputWriter.write(result, outputDir)` 对**推荐方案**逐段写出（`caseTag = 用例名`）：

| 产物 | 编码 | 命名 |
|---|---|---|
| 筒体信息关系式 | **GBK**（`TmsdOutputWriter.CREO_CHARSET`） | `<用例>-第K段-筒体信息关系式.txt` |
| 附件信息关系式 | **GBK** | `<用例>-第K段-附件信息关系式.txt` |
| 连接法兰关系式 | **GBK** | `<用例>-第K段-连接法兰关系式.txt` |
| 方案对比报告 | **UTF-8** | `<用例>-方案对比报告.md` |

**单用例数量** = 中段段数 × 3 份 txt + 1 份报告。本样例中段 4 段 ⇒ **13 份**（4×3 + 1）。附件无解段：附件 txt 中 `H{i}_L_Exist`/`H{i}_CABLE_Exist` 记为 `no`，其余照常输出。

---

## 9. 覆盖矩阵（测试层 × 关注点）

| 关注点 | F 规则 | P 流水线 | O 本体 | E 内存E2E | R 真实E2E | D 诊断 |
|---|---|---|---|---|---|---|
| 附件排布（280 倍数 / 避焊缝 / 末组落位） | ✅ | ✅ | — | — | — | ✅ |
| 梯档 280 / 首踏棍 140 / 灯型 | — | ✅ | ✅ | — | — | — |
| 防雷螺柱角度（70/190/310） | ✅ | — | ✅ | — | — | — |
| 灯间距 [5000,10000] / 首灯 [2600,3000] | ✅ | ✅ | ✅ | ✅ | — | — |
| 本体 16 数值 + 9 基数约束 | — | ✅ | ✅ | ✅ | ✅ | — |
| BPMN 单一路径编排（S0→S8） | — | — | — | ✅ | ✅ | — |
| 3 类 txt + 报告产出 | — | — | — | ✅ | ✅ | — |
| 本体一致性（Openllet） | — | — | ✅ | — | ✅ | — |
| 机型变化（V12/V15/V17/V19） | — | ✅ | ✅ | ✅ | ✅ | — |

---

## 10. 执行方式

```bash
# 纯 Java 层（无需环境）：正向规则 + 流水线 + 本体 + 诊断
mvn -pl OntologyFramework test \
  -Dtest="TMSDForwardRulesTest,TMSDDesignPipelineTest,TmsdOntologyLoadTest,TmsdDebugTest" -DfailIfNoTests=false

# 内存引擎端到端（无需 Camunda/MySQL/Ontop）
mvn -pl OntologyFramework test -Dtest="TMSDProcessTest" -DfailIfNoTests=false

# 真实环境端到端（需先启动环境 + TMSDApplication）
python EnvPrepare/scripts/start_all.py
mvn -pl OntologyFramework test -Dtest="TMSDDesignProcessTest" -DfailIfNoTests=false

# 全量 TMSD 套件
mvn -pl OntologyFramework test \
  -Dtest="TMSDForwardRulesTest,TMSDDesignPipelineTest,TmsdOntologyLoadTest,TmsdDebugTest,TMSDProcessTest,TMSDDesignProcessTest" \
  -DfailIfNoTests=false
```

> IDE 直跑：`src/test` 下各测试类均可作为 JUnit 5 直接运行；`TMSDDesignProcessTest` 需真实环境。

---

## 11. 通过准则

1. **纯 Java / 内存层全绿**（F/P/O/E/D 共 18 项），断言零失败；
2. **R 层 7 项全绿**，`ontologyConsistent=true`；
3. 输出 txt 参数全部落在 §7.1 约束内；每用例产出中段段数×3 份 txt + 1 份报告；
4. 流程实例**无 incident**，节点顺序为 S0→S1→…→S8→End；
5. 正向方案数为 **1**（单方案），附件间距贪心取大、末组到平台 ∈[840,1960]。

---

## 12. 已知边界与说明

| 项 | 说明 |
|---|---|
| 附件排布无解段 | 若某段在「边缘偏移 = 支撑半宽」判据下无解，该段附件留空输出（`H{i}_L_Exist=no`），其余正常；测试兼容空输出 |
| 边缘偏移对照 | `TMSDForwardRulesTest.edgeAvoidance` 以 `455`（爬梯支撑半宽口径的对照）演示 280 网格无解，说明边缘偏移取值对可解性的影响 |
| 内存引擎 vs 真实环境 | 内存引擎测试不加载本体服务，故一致性校验只在 R 层覆盖 |
| 平台处内径容差 | 固定 `6mm`（对应 2.X 机型），与老程序 `platSugget()` 一致；v16.4 由配置项迁移至本体表达（`Platform ⊑ platformInnerDiameterTolerance hasValue 6`），配置录入网页不再提供该项 |
| `spring-boot-maven-plugin:repackage` | 该模块含多个 `main` 类（Pizza/TCM/TMSD），`repackage` 需显式指定 `mainClass` 或跳过；**不影响测试编译与运行**（测试走 javac + surefire），仅影响 fat-jar 打包 |
| 与 TCM 测试的关系 | `JingfangDiagnosisProcessTest` 等属遗留测试、与 TMSD 无关，其失败为旧契约问题，不计入本文档范围 |

---

*本文档由 TMSD 测试源码（`TMSDForwardRulesTest` / `TMSDDesignPipelineTest` / `TmsdOntologyLoadTest` / `TmsdDebugTest` / `TMSDProcessTest` / `TMSDDesignProcessTest` / `TmsdTestConfig`）与 `ontology/bpmn/TowerMidDesign.bpmn` 自动整理生成，数值与断言均可回溯至源码。*
