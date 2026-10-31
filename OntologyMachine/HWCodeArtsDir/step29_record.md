# step29 · 接通运行期条文库读取（8 张内容表【定向引据】）

日期：2026-10-30
模块：`OntologyFrameworkSiZhuBaZi`（主改 `SiZhuBaZiOntologyJobWorker.java`）

## 1. 触发
客户裁定：把此前「经 `sizhubazi.obda` 暴露、但运行期从未被排盘流程读取」的 8 张内容表，**按本命特征定位、限量引据**（单条截断、不堆砌全文），补到对应节点输出。

- 目标表：`classic_text` / `ziping_clause` / `technique_catalog` / `fu_clause` / `dts_clause` /
  `auspice_clause`（五行时地·孕生男女）/ `theory_clause`（病药·动静·盖头）/ `pattern_catalog`。
- 前提修正（历轮）：运行期条文读取主链早在第 24 步接通；本轮补齐**尚未被读取**的上述 8 表。

## 2. 改动（复用现有输出字段，未改 BPMN / Controller / 前端）
文件：`src/main/java/com/ocean/ontologyframework/sizhubazi/SiZhuBaZiOntologyJobWorker.java`

- **新增 helper 段**（`clauseSexagenaryNature` 之后，:1643–1900）：
  `CITE_MAX=220`、`clip()`、`readReadingFamily(type,cat)`（**不带 LIMIT、Java 侧按 cat 二次校验**）、
  `readPatternFamily()`、`pickReading/pickPattern/pickYongShen`、
  `citePatternCatalog / citeTechniqueBody / citeDts / citeTheory / citeAuspiceTimePlace /
   citeProgeny / citeFu / citeZiping / citeClassic`、`fuKeywords()`、`mergeCitations()`。
  库未就绪或未命中→返回空集，**降级不阻断**（铁律 13/14）。
- **节点 8 determinePattern**：`patternResult` 追加 `mergeCitations("格局典籍引据", patternCites)`，
  `patternCites = citePatternCatalog(hits) + citeDts(pattern,ganzhiRelations) + citeZiping("论用神成败救应")`（:750–756）。
- **节点 9 selectYongShen**：`yongShenMethods` 追加 `mergeCitations("用神典籍引据", citeTechniqueBody(日干,日支))`（:818–825）。
- **节点 14 generateCategoryVerdicts**：`cites` 追加
  `citeTheory() + citeAuspiceTimePlace(日主五行) + citeFu(fuKeywords(gods)) + citeClassic("滴天髓","中和") + citeClassic("三命通会","论五行生成")`（:1143–1147）。
- **子息 analyzeChildChart**：`childVerdict` 追加 `mergeCitations("子息典籍引据", citeProgeny())`（:1297）。

## 3. 验证

### 3.1 编译 + 定向回归
- `mvn -o -q -f OntologyFrameworkSiZhuBaZi/pom.xml test-compile` → **通过**。
- `-Dtest='SiZhuBaZiOntologyComplianceTest,SiZhuBaZiSkosTerminologyTest,SiZhuBaZiBirthParsingTest'` → **20/20 全绿**。

### 3.2 端到端【实际生成】验证（临时内存引擎探针，用后即删）
以 `zeebe-process-test` 内存引擎 + 生产 Worker + 真实 OBDA（Ontop 8080 / MySQL 3306 在线），
跑 1978-07-04 11:00（戊午 戊午 丁卯 丙午）男命单命，读流程变量，**引据已实际写入输出**：

- `patternResult`：`【格局典籍引据】` → `【具名格·日禄归时】`（三命通会卷六）、`【子平真诠·论用神成败救应】`。
- `yongShenMethods`：`【用神典籍引据】` → `【十干体象·丁火体象】`（渊海子平卷四）、`【十二支体象·卯木体象】`。
- `clauseCitations`：`【动静说】`/`【盖头说】`/`【病药说】`（渊海子平卷六·神峰通考）、
  `【五行时地·论火·时地吉凶】`（三命通会卷四）、`【成篇赋诀·子平泛论】`/`【成篇赋诀·克子歌】`（神峰通考）、
  `【滴天髓·通神论·十八、中和】`、`【三命通会·卷一·论五行生成】`。
- 子息命例 `childVerdict`：`【子息典籍引据】` → `【孕生男女·定妇人孕生男女·大衍法】`（三命通会卷七）。

> 结论：8 张表已按本命特征定位并**实际引据到节点输出**，端到端闭环成立。

### 3.3 `SiZhuBaZiBpmnInMemoryTest` 8 过 2 败的判定
两个失败用例：
- `t_missing_birth_raises_incident:259`：期望 `Task_ParseInput` 被过，实测 0。
- `t_marriage_without_partner_raises_incident:275`：期望 `Task_MarriageMatch` 被过，实测 0。

**判定：与本轮改动无关（既有时序/断言语义问题）**，依据：
- 两用例均在**节点 1（parseBirthInput）/ 节点 15（analyzeMarriageMatch）**失败；合婚分支经 G1 直达节点 15，
  **完全绕过**本轮改动的节点 8/9/14/子息；缺 birth 用例在节点 1 即中断。
- 本轮未改 BPMN、未改错误处理；失败断言为「抛 BPMN 错误的任务是否计入 hasPassedElement」的语义匹配问题。
- （无绿色基线可比：11:45 的 AllSuite 同类用例因**部署失败**而挂，属另一环境问题。）

## 4. 边界 / 遗留
- 禁区模块（`OpenlletResolver`/`OntologyResolver`/`OntopOBDAHandler`）**零改动**（git `--porcelain` 无输出）。
- **未改** BPMN / Controller / `index.html`；改动面仅 1 个 Java 文件。
- 待办（非本任务范围）：
  1. 在线实例（PID 27696）为**旧构建**，卡在 `Task_Interpret`（`interpretResult` 状态 CREATED）；如需端到端可见 `interpretResult`，须重建并重启应用。
  2. LLM 两路径（`interpretResult` 生效/降级）未实测（`LLM_API_KEY` 未设）。
  3. `SiZhuBaZiBpmnInMemoryTest` 两个负向用例宜独立修复（断言语义或 BPMN 错误处理）。
  4. `clauseCitations` 引据条数偏多（~12 条），如需更「精」可下调各 cite 的 limit / 引入相关度筛选。
