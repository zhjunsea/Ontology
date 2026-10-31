# 第 27 步留痕：`index.html` 排盘输出体例重构（按本命推导 + 依据 + 原文印证 + 白话文）

## 一、客户诉求（本轮裁定，经三问答确认，全部采纳「推荐」方案）
1. 输出「太乱、不能理解」：**不是堆砌典籍原文**，而要**根据输入生辰八字给出各种占卜结论**。
2. 按【四柱命理命书】体例输出；结论须**按该八字推导并给出原因（依据）**，原文仅作**印证引用**（不再整段照抄无关原文，如《滴天髓》通神论/六亲论诸象被无差别拼进「格局」段）。
3. 末尾**再补一段白话文**（**调用 LLM 翻译前面的文言文**）。
4. LLM 未配置/不可用 → **降级为空串、流程不阻断**（与 PlumBlossom 一致）。
5. 修复范围＝**全面重构**（节点8 格局、节点13 岁运、节点14 分类断语）。

## 二、根因（复核确认）
- 节点8 `handleDeterminePattern`：旧逻辑把 `pattern_class` 命中的「该类别全部具名格原文」整段拼进 `patternResult`；且 `pattern_class` 取值不含 `tgName+"格"`（如「正官格」），**匹配错位 → 堆砌/错配**。
- 节点13 `handleSynthesizeFortuneChart`、节点14「分类事项断」为**硬编码通用套话**，未按本命推导。

## 三、改动清单（改前先确认方向；改动一律回写生成源／此处不涉及库与生成源）
### 1. `OntologyFrameworkSiZhuBaZi/.../SiZhuBaZiOntologyJobWorker.java`
- 新增 `@Autowired(required=false) ChatAgent llm` + 两段系统提示（`INTERPRET_SYSTEM_PROMPT` / `INTERPRET_PLAIN_SYSTEM_PROMPT`）。
- **节点8 格局重构**：①定格推导（月令本气→十神→格名，写明为何；格神透干/未透干取证）；②成败救应逐条给依据（引《子平真诠·论用神成败救应》）；③具名格改为「**命中构成条件才引用原文印证**」——**废弃旧 `pattern_class` 匹配**，新增 Worker 内置条件表 `hitNamedPatterns(int[][], int)`（魁罡/六乙鼠贵/六阴朝阳/壬骑龙背/六甲趋乾/六壬趋艮/日贵/日德/日禄归时/天元作禄/天元坐煞/时上一位贵/岁德正官/时上正官/年上七煞/官煞混杂/财官双美/专禄要制，共 18 条，各标出处）。
- **节点13 岁运综合重构**：新增 `buildFortuneSynthesis(...)`，以本命用神/忌神 + 具体大运干支**逐运**给「扶用/助忌→成格/破格」之义（引《子平真诠·论行运成格变格》）。
- **节点14 分类断语重构**：新增 `buildCategoryVerdicts(...)`，事业/财运/婚姻/寿夭/飘荡**逐项按本命十神·宫位·神煞推导并给依据**。
- **新增末尾节点 `interpretResult`（`handleInterpretResult`）**：入参 `reading`+关键变量；输出 `aiInterpretation`（命书体例详解，逐条依据+原文印证）与 `aiInterpretationPlain`（白话翻译）；`llm==null||!isAvailable()||失败` 时**双键降级空串、流程不阻断**。附提示组装 `buildInterpretUserPrompt`/`buildInterpretPlainUserPrompt`。
- 说明：旧 `clauseNamedPatterns(String)` 保留但已不再被节点8调用（避免额外改动面）。

### 2. `OntologyFrameworkSiZhuBaZi/ontology/bpmn/sizhubaziProcess.bpmn`
- 新增 `Task_Interpret`（`type=interpretResult`，ioMapping：input `reading`；output `result.aiInterpretation`/`result.aiInterpretationPlain`）。
- 接线：`Gateway_LevelMerge → Task_Interpret → Task_PersistReturn`（`Flow_Merge_To_Persist` 改指向 `Task_Interpret`，新增 `Flow_Interpret_To_Persist`）。
- BPMNDI：补 `Task_Interpret_di` 形状；`Task_PersistReturn`/`End_01` 下移一行（y +120）；补 `Flow_Interpret_To_Persist_di` 并同步 `Flow_Persist_To_End_di` 坐标。**节点数 21 → 22。**

### 3. `OntologyFrameworkSiZhuBaZi/.../web/SiZhuBaZiController.java`
- GET `/divine/{key}` 增加 `aiInterpretation`、`aiInterpretationPlain` 两键。

### 4. `OntologyFrameworkSiZhuBaZi/src/main/resources/static/index.html`
- `SECTIONS` 增加「命书详解（大模型）」「白话解读（大模型）」两节（排在末尾）。

## 四、验证
- 编译：`mvn -o -q -f OntologyFrameworkSiZhuBaZi/pom.xml test-compile` ✅。
- 定向回归（仅目标模块，**不带 `-am`**）：`mvn -o -f OntologyFrameworkSiZhuBaZi/pom.xml test -Dtest=SiZhuBaZiOntologyComplianceTest,SiZhuBaZiSkosTerminologyTest,SiZhuBaZiBirthParsingTest` → **Tests run: 20, Failures: 0, Errors: 0** ✅。
- BPMN 静态校验（flow/DI 引用、incoming/outgoing 一致性、DI 覆盖）：**22 serviceTask、33 flow，全部通过** ✅。
- 端到端内存引擎测试（`SiZhuBaZiBpmnInMemoryTest`）**本轮未跑**：本机 RabbitMQ（localhost:5672）未起 → 该类自 `assumeAllReachable` 跳过（后续有环境时补齐）。
- 禁区核验：`OpenlletResolver/`、`OntopOBDAHandler/` **零改动** ✅。

## 五、遗留 / 后续
- 有 RabbitMQ/LLM 环境时补跑端到端，实测「无 LLM → 两键空串、流程正常完成」与「有 LLM → 命书详解 + 白话解读」两条路径。
- 具名格条件表后续若需扩条目/收窄口径，按此表增删并标注出处（禁编造）。
