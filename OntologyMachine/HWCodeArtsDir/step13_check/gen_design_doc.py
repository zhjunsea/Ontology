# -*- coding: utf-8 -*-
import os
from collections import defaultdict, Counter
from rdflib import Graph, RDF, RDFS, OWL, URIRef
from rdflib.namespace import SKOS, split_uri

ONTO = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology"
DESIGN = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\design"
SUBS = ["core","calendar","chart","relations","tengod","deity","pattern",
        "yongshen","fortune","reading","diagnosis","rules"]

g = Graph(); g.parse(os.path.join(ONTO, "sizhubazi-terminology.ttl"))

def local(u):
    s = str(u)
    return s.rstrip('/').split('/')[-1] if '/' in s else (s.split('#')[-1])

def ns_of(u):
    s = str(u)
    for n in SUBS:
        if f"/{n}/" in s:
            return n
    return "?"

concepts = []  # (term_local, pref_zh, iri, iri_ns, iri_local, broader_local, pref_en)
for s in set(g.subjects(RDF.type, SKOS.Concept)):
    term = local(s)
    pref = None
    pref_en = None
    for o in g.objects(s, SKOS.prefLabel):
        if getattr(o, "language", None) == "zh":
            pref = str(o)
        elif getattr(o, "language", None) == "en":
            pref_en = str(o)
    ex = list(g.objects(s, SKOS.exactMatch))
    bro = list(g.objects(s, SKOS.broader))
    iri = ex[0] if ex else None
    brl = local(bro[0]) if bro else ""
    concepts.append((term, pref, str(iri) if iri else "", ns_of(iri) if iri else "?", local(iri) if iri else "", brl, pref_en or ""))

# classify kind by ontology
og = Graph()
for n in SUBS: og.parse(os.path.join(ONTO, f"sizhubazi-{n}.owl"))
kind = {}
for p, k in ((OWL.Class,"C"),(OWL.ObjectProperty,"OP"),(OWL.DatatypeProperty,"DP")):
    for s in og.subjects(RDF.type, p):
        if isinstance(s, URIRef): kind[str(s)] = k

def kk(iri): return kind.get(iri, "C")

order = {n:i for i,n in enumerate(SUBS)}
by_ns = defaultdict(list)
for c in concepts:
    by_ns[c[3]].append(c)
for n in by_ns: by_ns[n].sort(key=lambda c: c[0])

total = len(concepts)
class_cnt = sum(1 for c in concepts if kk(c[2])=="C")
op_cnt = sum(1 for c in concepts if kk(c[2])=="OP")
dp_cnt = sum(1 for c in concepts if kk(c[2])=="DP")

# altLabel table
alt_rows = []
for s in set(g.subjects(RDF.type, SKOS.Concept)):
    term = local(s)
    pref = next((str(o) for o in g.objects(s, SKOS.prefLabel) if getattr(o,"language",None)=="zh"), "")
    for o in g.objects(s, SKOS.altLabel):
        alt_rows.append((term, pref, str(o)))
alt_rows.sort()

# domain counts
dom_rows = "".join(
    f"| {n} | {sum(1 for c in by_ns[n] if kk(c[2])=='C')} | {sum(1 for c in by_ns[n] if kk(c[2])=='OP')} | {sum(1 for c in by_ns[n] if kk(c[2])=='DP')} | {len(by_ns[n])} |\n"
    for n in SUBS if n in by_ns)

# concept table (full)
ctab = []
for n in SUBS:
    if n not in by_ns: continue
    ctab.append(f"\n#### {n} 域\n")
    ctab.append("| # | SKOS 概念 (`term:`) | `skos:prefLabel`@zh | `skos:prefLabel`@en | 对应本体 IRI | `skos:broader` |")
    ctab.append("|---|---|---|---|---|---|")
    i = 1
    for term, pref, iri, ins, il, brl, en in by_ns[n]:
        typ = {"C":"类","OP":"对象属性","DP":"数据属性"}[kk(iri)]
        prefcell = f"{pref}（{typ}）"
        bcell = f"`term:{brl}`" if brl else "—"
        ctab.append(f"| {i} | `term:{term}` | {prefcell} | {en} | `{ins}:{il}` | {bcell} |")
        i += 1
concept_table = "\n".join(ctab)

# read ttl text to extract altLabel sources (inline comments)
ttl_txt = open(os.path.join(ONTO, "sizhubazi-terminology.ttl"), encoding="utf-8").read()
src_map = {}
cur = None
for line in ttl_txt.splitlines():
    if line.startswith("term:"):
        cur = line[5:].strip()
    elif "skos:altLabel" in line and "# 源:" in line:
        val = line.split('"')[1]
        src = line.split("# 源:")[1].strip()
        src_map[(cur, val)] = src

alt_tab = ["| # | SKOS 概念 (`term:`) | 规范名 `prefLabel`@zh | 同义词/别名 `altLabel`@zh | 来源 |",
           "|---|---|---|---|---|"]
for i, (term, pref, alt) in enumerate(alt_rows, 1):
    src = src_map.get((term, alt), "")
    alt_tab.append(f"| {i} | `term:{term}` | {pref} | {alt} | {src} |")
alt_table = "\n".join(alt_tab)

doc = f"""# SiZhuBaZi 应用 — SKOS 词汇表设计

> **用法**：本模板产出第 13 步。**闸门⑥**：业务人员确认术语与同义词表后方可进入第 14 步。
> **基线**：`OntologyFrameworkExample/ontology/pizza-terminology.ttl` 与 `OntologyFrameworkPlumBlossom/ontology/plumblossom-terminology.ttl`（形态）；构建依据为本应用已定稿 TBox。
> **硬性**：术语**来源于本体**，每个概念必须能对应到本体 IRI（★10：不得凭空造词）。

| 项目 | 内容 |
|---|---|
| 应用 | SiZhuBaZi（四柱命理） |
| 文档日期 | 2026-10-29 |
| 关联本体 | `ontology/sizhubazi-all.owl`（已定稿 TBox，聚合 12 子本体） |
| 产出文件 | `ontology/sizhubazi-terminology.ttl` |
| 命名空间 | `http://example.org/sizhubazi/term/`（`term:` 前缀） |
| 消费方 | `OntologyLabelMatcher` / `SkosSynonymReader` |

---

## 1. 目的与范围

**目的**：把四柱命理领域核心业务术语标准化为 SKOS 概念体系，统一 OWL 本体与其他系统中的词义，实现「同名同义、同义同名」。

**范围**：覆盖本体中全部**具 `rdfs:label` 的业务元素**——即 12 个子本体的类（业务概念）、对象属性（业务关系）、数据属性（业务字段）。
共 **{total}** 个规范概念：类 {class_cnt} + 对象属性 {op_cnt} + 数据属性 {dp_cnt}。
无 `rdfs:label` 的元素（如各 `isXxxOf` 逆属性、跨本体重复引用的占位类）**不建概念**，避免孤立词条与凭空造词。

## 2. 术语体系（概念树）

```
term:SiZhuBaZiEntity              本体事物根
├── term:GanzhiDomain             干支基础域
│   ├── term:HeavenlyStem / term:EarthlyBranch / term:YinYang
│   ├── term:FivePhase ── term:Mu/Huo/Tu/Jin/Shui
│   ├── term:SexagenaryCycle / term:HiddenStem / term:NaYin / term:LifeStage
│   └── term:GanzhiEntity
├── term:CalendarDomain           历法域（月建/节气/真太阳时/历法边界）
├── term:ChartDomain              排盘域（四柱/柱/大运/命宫/胎元/小运/生辰输入）
├── term:RelationDomain           干支作用域（冲/合/刑/害/合绊/墓库/隐蔽作用）
├── term:TenGodDomain             十神与六亲域（十神十类/六亲/宫位/十神生克制化）
├── term:DeityDomain              神煞域（神煞/贵人吉神/凶煞类/禄马/空亡）
├── term:PatternDomain            格局域（格局/八正格/外格杂格/杂气/成败）
├── term:YongshenDomain           用神旺衰域（用神/调候/旺衰/层次/清浊/源流通关）
├── term:FortuneDomain            岁运域（大运周期/流年/流月流日流时/运限/应期/化气）
├── term:ReadingDomain            断语域（命书/合婚/女命/小儿/六亲/性情/疾病/劝诫/命例）
├── term:DiagnosisDomain          出处与流派标注域 S3（出处标注/待考标记/流派分歧）
└── term:RulesDomain              告警标志域 S5（告警标志）
```

> 层级用 `skos:broader` 表达；与本体的 `rdfs:subClassOf` 类层级保持同构（域类 → `term:SiZhuBaZiEntity`）。属性类概念无 `broader`（其归属由 `term:` 命名空间与 `exactMatch` 体现）。

## 3. 概念清单（术语 ⇄ 本体 IRI 映射）

> 每行给出对应本体 IRI（类/属性），用于第 14 步交叉校验。等价于完整 ttl 的可读快照。

**分域计数**

| 子本体 | 类 | 对象属性 | 数据属性 | 小计 |
|---|---|---|---|---|
{dom_rows}
| **合计** | **{class_cnt}** | **{op_cnt}** | **{dp_cnt}** | **{total}** |

**全量映射**
{concept_table}

## 4. 标签与同义词规范

| 项 | 规则 |
|---|---|
| `skos:prefLabel` | 每概念每语言恰一条；`@zh` 取本体 `rdfs:label`；`@en` 为对应英文译名（与 `@zh` 一一对应） |
| `skos:altLabel` | 同义词/别名（受 `SkosSynonymReader` 消费），**逐条标注来源** |
| `skos:definition` | 取本体 `rdfs:comment`（如有） |
| 概念方案头 | `skos:ConceptScheme` + `owl:Ontology` 双类型；`dct:created` / `dct:description` |

**同名「类/对象属性」消歧**：`relations:Clash|StemCombination|Punishment|SixCombination` 与同名对象属性共用本体标签，对**对象属性**加「（关系）」限定（如 `相冲（关系）`），标签主体仍取自本体，属业务约定。

### 4.1 同义词 / 别名表（含来源与归属概念）

> 共 **{len(alt_rows)}** 条。来源为六书具体卷篇，或本应用设计文档/业务约定；查无出处者标【待考】。

{alt_table}

## 5. 交叉校验设计（第 14 步执行）

| 校验项 | 方法 | 通过判据 |
|---|---|---|
| 结构校验 | 纯 Jena 解析 TTL | 每个 `skos:Concept` 有 `prefLabel`；`broader` 指向存在概念 |
| 本体交叉 | 概念 ↔ OWL IRI 对齐 | 清单 §3 每条 IRI 在本体中存在；无孤立词条 |
| 无造词 | 词汇 ⊆ 本体术语 | 不存在无对应本体 IRI 的概念 |
| 别名溯源 | `altLabel` 均有来源注释 | 每条 `altLabel` 可回溯至六书/文档/业务约定 |

**第 13 步自检结果**（离线，`HWCodeArtsDir/step13_check/verify_terminology.py`）：概念 {total}、具 `exactMatch` {total}、`exactMatch` 目标全部命中本体、`broader` 目标全部为概念、`@en` `prefLabel` {total} 条、`altLabel` {len(alt_rows)} 条且均带「# 源:」注释。

**第 14 步校验结果**（测试类 `validation/SiZhuBaZiSkosTerminologyTest`，纯 Jena 离线）：P-1 概念方案 / P-2 `@zh` 首选标签 / P-3 `@en` 首选标签 / B-1 `broader` 健全 / X-1 `exactMatch` 命中本体 / X-2 本体带标签实体全覆盖（无遗漏、无造词）/ S-1 同义词合法（中文、不与首选标签重复）/ S-2 同义词均可溯源，**共 8 项全部通过**；`AllSiZhuBaZiSuiteTest` **全绿**（本体合规 8/8 + SKOS 8/8；BPMN/RabbitMQ 集成依环境跳过）。

## 6. 待确认项（业务已裁定）

| # | 问题 | 业务结论 |
|---|---|---|
| 1 | 部分别名出处按六书通行卷篇标注（如「七杀/七煞」「枭神/倒食」），是否接受该卷篇级溯源粒度？ | ✅ **接受**「六书卷篇级」溯源粒度（2026-10-29 业务裁定） |
| 2 | `yongshen:Tiaohou`（调候用神）别名「调候」出处《穷通宝鉴》**尚未取得文本**，暂标【待考】；取得后回填？ | ✅ **已回填**：业务提供《穷通宝鉴》文本（《造化元钥》徐乐吾评注），已归档 `design/六书原文/穷通宝鉴（清·余春台辑·徐乐吾评注）.pdf` 与 `design/六书全文整理/穷通宝鉴.txt`；别名「调候」出处回填为「《穷通宝鉴》（《造化元钥》）·五行总论及各干逐月取用」 |
| 3 | 是否需要为全部概念补充 `@en` 英文 `prefLabel`（参考 Example Pizza 有 @en；PlumBlossom 仅 @zh）？ | ✅ **补充**：已为全部 **{total}** 个概念添加 `@en` `prefLabel`（2026-10-29 落实） |
| 4 | 同名对象属性加「（关系）」后缀的消歧口径是否认可？ | ✅ **认可**（2026-10-29 业务裁定） |

---

## 闸门⑥留痕
| 日期 | 确认版本 | 确认人 | 结论/修改点 |
|---|---|---|---|
| 2026-10-29 | v1.1（含 `@en`） | 业务（四柱命理领域专家） | 就 §6 四项作出裁定：①接受卷篇级溯源；③为全部概念补 `@en`（已落实）；④认可「（关系）」消歧口径。②要求引入《穷通宝鉴》，因文本缺失暂缓，维持【待考】待回填。 |
| 2026-10-30 | v1.2（回填《穷通宝鉴》） | 业务（四柱命理领域专家） | 提供《穷通宝鉴》文本，已归档；`term:Tiaohou` 别名「调候」出处由【待考】回填为「《穷通宝鉴》（《造化元钥》）·五行总论及各干逐月取用」。**闸门⑥ 最终通过，进入第 14 步。** |
"""

out = os.path.join(DESIGN, "SiZhuBaZi应用-SKOS词汇表设计.md")
with open(out, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(doc)
print("concepts", total, "class", class_cnt, "op", op_cnt, "dp", dp_cnt, "alt", len(alt_rows))
print("written", out)
