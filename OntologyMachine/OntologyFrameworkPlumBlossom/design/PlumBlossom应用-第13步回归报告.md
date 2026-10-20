# PlumBlossom 应用 · 第 13 步回归报告（构建 SKOS 词汇表 · 闸门⑥）

- **步骤**：第 13 步　构建 SKOS 词汇表（闸门⑥）
- **日期**：2026-10-20
- **确认人**：邵康节（闸门⑥）
- **命令**：`./mvnw -pl OntologyFrameworkPlumBlossom test -o`（离线，工作目录 `D:\work\Ontology\OntologyMachine`）

---

## 1. SKILL 修订（客户指令，先改后跑）

依客户指令，本步内涵修订并同步 `OntologyAppBuilder/SKILL.md`：

- **第 13 步**：SKOS 词汇表目标改为**解决「不同名同义、同义不同名」**——把**输入、跨系统交互**词汇的**别名、缩写、习惯用法、口语、书面语/雅称**归并到本体**规范概念**；规范概念只来源于本体，同义表述须逐条标源。
- **闸门汇总表 ⑥ 行**：确认内容改为「SKOS 词汇表（规范术语 + 同义词/别名表）」。
- **第 14 步**：校验项增加「同义词/别名均有来源且归并到正确规范概念」。

## 2. 产出

- **新增** `ontology/plumblossom-terminology.ttl`：
  - ConceptScheme 根 `<http://example.org/plumblossom/term>`；命名空间 `.../term/`。
  - **规范概念 150**：`owl:Class` 71 / `owl:ObjectProperty` 38 / `owl:DatatypeProperty` 41（均为**带 `rdfs:label`** 的本体术语）。
  - 每概念含 `skos:prefLabel@zh`（本体标签）、`skos:definition@zh`（本体 `rdfs:comment`）、`skos:broader`（`rdfs:subClassOf`）、`skos:exactMatch`（→本体 IRI）。
  - **同义词 57 条，覆盖 41 个概念**（`skos:altLabel@zh`），逐条标源。

- **接线（客户确认）**：
  - `ontology/plumblossom-tbox.owl`：新增 `<owl:imports rdf:resource="http://example.org/plumblossom/term"/>`。
  - `ontology/catalog-v001.xml`：新增 `name="http://example.org/plumblossom/term" uri="plumblossom-terminology.ttl"`。

- **测试适配**：`PlumBlossomOntologyComplianceTest` 加载逻辑与框架 `OntologyService` 对齐——为 `.ttl` 补注册 `SimpleIRIMapper`（Turtle 显式解析）；个体统计剔除 SKOS 命名空间（`skos:Concept` 不计入 OWL 个体）。

## 3. 同义词来源（可追溯）

| 来源 | 说明 |
|---|---|
| 原著《梅花易数》（故宫珍藏善本，[宋]邵康节 著　周浩良 整理） | 提取文本 `HWCodeArtsDir/orig_0000x.txt`；如「端法后天起卦之例」「凡卦之寓物者，皆谓之风觉鸟占」「《梅花易数》一名《观梅数》」「凡物之从花甲来」等 |
| 《梅花易数全面指南》（仓储内 `design/`） | 如「先天起卦法/以数起卦」「八卦的卦德」「分类占断」等 |

> 备注：本环境网络不可达，故仅采信上述两类**仓储内可追溯来源**，未凭空造词。

**示例**（概念 → 同义词[来源]）：
- 本卦 → 正卦[原著·卷三·十应奥论]
- 先天·以数起卦 → 先天起卦法[指南] / 以数起卦[指南] / 先天之数[原著]
- 风觉占、鸟占 → 风觉鸟占[原著]
- 心法戒律·四不占 → 四不占[指南] / 不动不占[原著]
- 六十四卦名录 → 六十四卦[原著/指南]
- 六十甲子 → 花甲[原著] / 六十花甲[原著]
- 万物类象 → 类象[指南] / 取象[原著] / 万物类占[原著篇名]
- 十应 → 十应奥论[原著篇名]；三要 → 三要灵验[原著篇名] / 耳目心[原著]
- 外应域 → 外应[原著] / 外卦[原著] / 应卦[原著]

## 4. 交叉校验（生成器自检，通过）

| 项目 | 结果 |
|---|---|
| 规范概念数 | 150 |
| `skos:exactMatch` 断言数 | 150（全部命中本体术语） |
| 带同义词概念 / 同义词总数 | 41 / 57 |
| 孤立词条（无对应本体术语） | 0 |
| 未覆盖的带标签本体术语 | 0 |
| 同义词表未命中本体术语的键 | 0 |

## 5. 回归结果

| 测试类 | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| 梅花易数本体合规校验（离线） | 6 | 0 | 0 | 0 |
| 梅花易数业务术语表(SKOS)（占位→激活） | 3 | 0 | 0 | 0 |
| 告警消息外发（RabbitMQ，环境依赖） | 0 | 0 | 0 | 0 |
| 占断流程·内存引擎端到端（环境依赖） | 0 | 0 | 0 | 0 |
| 占断流程·真实引擎端到端（环境依赖） | 0 | 0 | 0 | 0 |
| **合计** | **18** | **0** | **0** | **0** |

- 结论：**`BUILD SUCCESS`**。
- 合并闭包：`closureSize=14`、`axioms=7851`、`individuals(OWL)=1133`。
  - 对比第 12 步：`closureSize` 13→14、`axioms` 7256→7851（新增 SKOS 术语表三元组）；`individuals(OWL)` 仍 **1133**（SKOS `skos:Concept` 不计入 OWL 个体）。
- 环境依赖的 3 个端到端/集成类整体跳过（端口不可达），符合既有约定。
- Openllet 对 SWRL 规则的 `Head atom contains variables not found in body` 警告为既有现象（第 12 步已在），非本次引入。

## 6. 变更清单

| 文件 | 变更 |
|---|---|
| `OntologyAppBuilder/SKILL.md` | 修订第 13 步定义、闸门⑥行、第 14 步校验项 |
| `ontology/plumblossom-terminology.ttl` | **新增**（150 概念 + 57 同义词） |
| `ontology/plumblossom-tbox.owl` | 新增对 `<.../term>` 的 `owl:imports` |
| `ontology/catalog-v001.xml` | 新增 `.../term → plumblossom-terminology.ttl` 映射 |
| `src/test/.../validation/PlumBlossomOntologyComplianceTest.java` | 加载对齐框架（`.ttl` 补 `SimpleIRIMapper`）；个体统计剔除 SKOS 命名空间 |
| `design/PlumBlossom应用-应用架构设计.md` | 闸门⑥留痕（SKILL 修订 + 重跑结果 + 接线） |
| `design/PlumBlossom应用-第13步回归报告.md` | **新增**（本报告） |

## 7. 遗留项

- 部分本体业务术语（如 `core:Trigram` 八卦、`core:Hexagram` 重卦）暂无原著可证的**独立别名**，本期未收录；待业务/权威来源补充。
- `skos:hiddenLabel`（口语/少见变体）本期未单列，同义词统一用 `skos:altLabel`。

---

**闸门⑥状态：待业务确认**（术语与同义词/别名表）。确认通过后方可进入第 14 步（词汇表测试 + 全流程测试）。
