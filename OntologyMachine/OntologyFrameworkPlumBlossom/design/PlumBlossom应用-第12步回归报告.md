# PlumBlossom应用 — 第 12 步回归报告（ABox 扩至场景完整数据 · 闸门⑪）

| 项目 | 内容 |
|---|---|
| 应用 | PlumBlossom（梅花易数占断） |
| 步骤 | 第 12 步：ABox 扩展到场景完整数据（闸门⑪） |
| 报告日期 | 2026-10-20 |
| 确认人 | 邵康节（闸门⑪ + 命名口径 + 类象范围） |
| 结论 | **回归全绿**：`Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS` |

---

## 1. 执行环境与命令

- 工作目录：`D:\work\Ontology\OntologyMachine`
- 命令：`./mvnw -pl OntologyFrameworkPlumBlossom test -o`（离线）
- 说明：依赖外部端口的端到端用例（BPMN 真实引擎 / RabbitMQ / 内存引擎）经 `ServiceProbe.assume*Reachable` 整体跳过（本机 5672/9080/26500/8080/3306 均不可达）；离线用例可跑。

## 2. 测试结果

| 测试类 | Tests | Failures | Errors | Skipped | 备注 |
|---|---|---|---|---|---|
| `validation.PlumBlossomOntologyComplianceTest` | 6 | 0 | 0 | 0 | 离线（OWLAPI + Openllet） |
| `IriProbeTest` | 1 | 0 | 0 | 0 | 离线 |
| `PlumBlossomBpmnInMemoryTest` | 0 | 0 | 0 | 0 | 环境依赖，整体跳过 |
| `PlumBlossomBpmnRealEngineTest` | 0 | 0 | 0 | 0 | 环境依赖，整体跳过 |
| `PlumBlossomSwrlRabbitMqTest` | 0 | 0 | 0 | 0 | 环境依赖，整体跳过 |
| `AllPlumBlossomSuiteTest`（聚合） | 12 | 0 | 0 | 0 | 见下 |
| **合计** | **12** | **0** | **0** | **0** | `BUILD SUCCESS` |

> 合规校验日志：`本体加载成功 | closureSize=13 | axioms=7256 | individuals=1133`。

## 3. 数据规模（全集）

### 3.1 本体侧 `ontology/plumblossom-abox.owl`（129 → 1133 个体）

| 类型 | 数量 | 说明 |
|---|---|---|
| `sym:ImageSymbol` | 559 | 类象全集（8 卦 × 27 栏） |
| `core:YangLine` | 192 | 六十四卦之阳爻 |
| `core:YinLine` | 192 | 六十四卦之阴爻 |
| `core:SixBitHexagram` | 64 | 六十四卦名录（每卦含上下卦 + 6 爻 + 卦辞/爻辞引用） |
| `sym:SymbolDimension` | 27 | 类象维度（原 8，新增 19） |
| `jud:SubjectCategory` | 18 | 事项类别 |
| `ome:TenResponses` | 10 | 十应 |
| `core:Trigram` | 8 | 八卦 |
| `core:Direction` | 8 | 八方 |
| `rel:TrigramNature` | 8 | 八卦性情 |
| `rel:TrigramOrder` | 8 | 八卦次序 |
| `jud:YicuoJinGrade` | 6 | 一撮金六等 |
| `rel:FivePhase` / `rel:Sheng` / `rel:Ke` | 5 / 5 / 5 | 五行与生克 |
| `rel:VitalityState` | 5 | 旺相休囚死 |
| `jud:Auspiciousness` | 5 | 吉凶五级 |
| `div:Precept` | 4 | 四不占 |
| `ome:ThreeEssentials` | 3 | 三要 |
| `yijing:OppositeNature` | 1 | 诸卦反对（乾坤一例） |
| **合计** | **1133** | |

### 3.2 数据库侧 `ontology/database/data.sql`（全集）

| 表 | 行数 |
|---|---|
| `calendar_heavenly_stem` | 10 |
| `calendar_earthly_branch` | 12 |
| `calendar_nayin` | 30 |
| `calendar_sexagenary_cycle` | 60 |
| `calendar_life_stage` | 4 |
| `yijing_hexagram_sequence` | 64 |
| `yijing_hexagram_dictum` | 64 |
| `yijing_line_dictum` | 384 |
| `divination_result`（样本） | 6 |
| **合计** | **634** |

## 4. 本步变更清单

- **数据库**：`ontology/database/data.sql` 扩至全集（历法 116 + 周易文本 512 + 样本 6）；`createdb_tables.ddl` 注释同步（`Dictum_Hex_01`/`LineDictum_01_1`）。`.obda`/`.properties` 结构不变（映射按 `{name}` 拼接 IRI，覆盖全集）。
- **本体**：`plumblossom-abox.owl` 六十四卦名录扩至 64（含 384 爻）+ 类象全集（559 项 / 27 维度）；乾坤个体重命名为 `Hexagram_01`/`02`；卦辞/爻辞引用改为 `Dictum_Hex_<nn>`/`LineDictum_<nn>_<pos>`。
- **设计文档回填**：《本体顶层设计》§3/§4.1/§5/§6 个体数与命名、闸门⑪留痕与第 12 步执行结果；《本体核心设计》§7.1 第 12 步落位、§8 数据边界、§9-6；《应用架构设计》闸门⑪执行结果；《测试设计文档》§6.4 闭包规模。
- **禁区核验**：`git status --porcelain -- OpenlletResolver OntopOBDAHandler` 为空（未改动禁区模块）。

## 5. 遗留 / 待确认

- 诸卦反对性情粒度（《本体核心设计》§9-8）：第 12 步维持乾坤一例（`yijing:Opposite_QianKun`），其余 62 卦反对配对未展开，待业务确认后补充。
- 起卦法 `jud:hasMethod` 的类-个体 punning 问题（《本体核心设计》§8 告警）仍在，待后续处理。
