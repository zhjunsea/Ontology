# <Xxx>应用 — SKOS 词汇表设计

> **用法**：本模板产出第 13 步。**闸门⑥**：业务人员确认术语与同义词表后方可进入第 14 步。
> **基线**：`OntologyFrameworkExample/ontology/pizza-terminology.ttl`（形态）；构建依据为本应用已定稿 TBox。
> **硬性**：术语**来源于本体**，每个概念必须能对应到本体 IRI（★10：不得凭空造词）。

| 项目 | 内容 |
|---|---|
| 应用 | <Xxx> |
| 文档日期 | <YYYY-MM-DD> |
| 关联本体 | `ontology/<xxx>-all.owl`（已定稿 TBox） |
| 产出文件 | `ontology/<xxx>-terminology.ttl` |
| 命名空间 | `http://example.org/<xxx>/term/`（`term:` 前缀） |
| 消费方 | `OntologyLabelMatcher` / `SkosSynonymReader` |

---

## 1. 目的与范围

**目的**：把 <Xxx> 领域核心业务术语标准化为 SKOS 概念体系，统一 OWL 本体与其他系统中的词义，实现「同名同义、同义同名」。

**范围**：<覆盖哪些业务概念（总体概念/组件/细分/分类…）>。

## 2. 术语体系（概念树）

```
term:<根概念>
├── term:<子概念>
│   ├── term:<细分>
│   └── ...
└── ...
```

> 层级用 `skos:broader` / `skos:narrower` 表达；与本体类层级**保持同构或映射关系**。

## 3. 概念清单（术语 ⇄ 本体 IRI 映射）

> 每行必须给出对应本体 IRI（类/属性/个体），用于第 14 步交叉校验。

| SKOS 概念 | `skos:prefLabel`@zh | @en | 对应本体 IRI | `skos:broader` | `skos:definition` |
|---|---|---|---|---|---|
| `term:<Concept>` | | | `<本体 IRI>` | `term:<父>` | 有/无 |

## 4. 标签与同义词规范

| 项 | 规则 |
|---|---|
| `skos:prefLabel` | 每语言恰一条；本应用主语言 `@zh` + `@en` |
| `skos:altLabel` | 同义词/别名（受 `SkosSynonymReader` 消费） |
| `skos:definition` | 业务定义，须准确、可追溯 |
| 概念方案头 | `skos:ConceptScheme` + `owl:Ontology` 双类型；`dct:created` / `dct:description` |

## 5. 交叉校验设计（第 14 步执行）

| 校验项 | 方法 | 通过判据 |
|---|---|---|
| 结构校验 | 纯 Jena 解析 TTL | 每个 `skos:Concept` 有 `prefLabel`；`broader` 指向存在概念 |
| 本体交叉 | 概念 ↔ OWL IRI 对齐 | 清单 §3 每条 IRI 在本体中存在；无孤立词条 |
| 无造词 | 词汇 ⊆ 本体术语 | 不存在无对应本体 IRI 的概念 |

## 6. 待确认项

| # | 问题 | 业务结论 |
|---|---|---|

---

## 闸门⑥留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| | | | |
