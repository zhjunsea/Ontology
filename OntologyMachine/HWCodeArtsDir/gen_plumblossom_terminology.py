# -*- coding: utf-8 -*-
"""第 13 步：从 PlumBlossom TBox 九域生成 SKOS 词汇表 plumblossom-terminology.ttl。

规则：
  - 术语**只来源于本体**（带 zh rdfs:label 的 owl:Class / owl:ObjectProperty / owl:DatatypeProperty），
    不得凭空造词；无标签的结构性/逆向属性一律不收录。
  - 每个 SKOS 概念：skos:prefLabel@zh（取自 rdfs:label）、skos:definition@zh（取自 rdfs:comment，如有）、
    skos:broader（取自 rdfs:subClassOf，仅当父类也是概念）、skos:exactMatch（指向 OWL IRI，供交叉校验与
    SkosSynonymReader 反向查找）。
  - 概念本地名沿用本体本地名；跨域冲突时以域前缀消歧。
  - 生成后立即交叉校验：exactMatch 目标须在 TBox 存在；概念与本体术语一一对应。
"""
import json
from pathlib import Path
from rdflib import Graph, RDF, RDFS, OWL, URIRef

ROOT = Path(r"D:\work\Ontology\OntologyMachine")
ONT = ROOT / "OntologyFrameworkPlumBlossom" / "ontology"
DOMAINS = ["core", "divination", "yijing", "relations", "symbols",
           "omens", "judgement", "calendars", "rules"]
DKEY = {"core": "core", "divination": "div", "yijing": "yijing",
        "relations": "rel", "symbols": "sym", "omens": "ome",
        "judgement": "jud", "calendars": "cal", "rules": "rules"}
NS = "http://example.org/plumblossom/"
TERM = "http://example.org/plumblossom/term/"

g = Graph()
for d in DOMAINS:
    g.parse(str(ONT / f"plumblossom-{d}.owl"), format="xml")


def label(iri):
    v = g.value(iri, RDFS.label)
    return str(v) if v else None


def comment(iri):
    v = g.value(iri, RDFS.comment)
    return str(v) if v else None


def local(iri):
    s = str(iri)
    # 本体实体 IRI 为「前缀短形式」，如 cal:CalendarDatum、core:PlumBlossomEntity
    seg = s.split("/")[-1].split("#")[-1]
    if ":" in seg:
        seg = seg.split(":")[-1]
    return seg


PREFIX2DOMAIN = {DKEY[d]: d for d in DOMAINS}


def domain_of(iri):
    s = str(iri)
    seg = s.split("/")[-1].split("#")[-1]
    if ":" in seg:
        p = seg.split(":")[0]
        return PREFIX2DOMAIN.get(p)
    return None


# ---- 收集带标签的 TBox 术语 ----
terms = []  # (kind, iri, domain, local, zh_label, comment)
for kind, otype in [("class", OWL.Class), ("objectProperty", OWL.ObjectProperty),
                    ("datatypeProperty", OWL.DatatypeProperty)]:
    for s in g.subjects(RDF.type, otype):
        if not isinstance(s, URIRef):
            continue
        lb = label(s)
        if not lb:
            continue
        terms.append({"kind": kind, "iri": str(s), "domain": domain_of(s),
                      "local": local(s), "label": lb, "comment": comment(s)})

terms.sort(key=lambda x: x["iri"])

# ---- 本地名冲突检测 ----
from collections import Counter
cnt = Counter(t["local"] for t in terms)
collisions = {k: v for k, v in cnt.items() if v > 1}
for t in terms:
    if t["local"] in collisions:
        t["concept"] = f"{DKEY.get(t['domain'], 'x')}_{t['local']}"
    else:
        t["concept"] = t["local"]

concept_by_iri = {t["iri"]: t for t in terms}

# ---- 概念 broader（仅类；父类须也是概念）----
def broader_of(t):
    if t["kind"] != "class":
        return []
    out = []
    for o in g.objects(URIRef(t["iri"]), RDFS.subClassOf):
        if isinstance(o, URIRef) and str(o) in concept_by_iri:
            out.append(o)
    return out

# ---- 生成 ttl ----
def esc(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')

lines = []
lines.append('@prefix skos: <http://www.w3.org/2004/02/skos/core#> .')
lines.append('@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .')
lines.append('@prefix dct: <http://purl.org/dc/terms/> .')
lines.append('@prefix owl: <http://www.w3.org/2002/07/owl#> .')
lines.append('@prefix term: <http://example.org/plumblossom/term/> .')
lines.append('')
lines.append('<http://example.org/plumblossom/term>')
lines.append('    a owl:Ontology ;')
lines.append('    a skos:ConceptScheme ;')
lines.append('    skos:prefLabel "梅花易数业务术语体系"@zh ;')
lines.append('    skos:prefLabel "Plum Blossom Numerology Business Terminology"@en ;')
lines.append('    dct:created "2026-10-20"^^<http://www.w3.org/2001/XMLSchema#date> ;')
lines.append('    dct:description "梅花易数领域核心业务术语的标准化词汇表，术语全部来源于本体 '
             '(owl:Class / owl:ObjectProperty / owl:DatatypeProperty 的 rdfs:label)，'
             '用于统一 OWL 本体及其他系统中的词义，实现同名同义、同义同名。"@zh .')
lines.append('')

import sys
sys.path.insert(0, str(Path(__file__).parent))
from pb_synonyms import S as SYN

KIND_TITLE = {"class": "类（业务概念）", "objectProperty": "对象属性（业务关系）",
              "datatypeProperty": "数据属性（业务属性）"}
n_alt = 0
alt_concepts = 0
for kind in ["class", "objectProperty", "datatypeProperty"]:
    sub = [t for t in terms if t["kind"] == kind]
    lines.append("# ========================")
    lines.append(f"# {KIND_TITLE[kind]}（{len(sub)}）")
    lines.append("# ========================")
    for t in sub:
        props = ['    a skos:Concept']
        props.append(f'    skos:prefLabel "{esc(t["label"])}"@zh')
        alts = [(a, s) for (a, s) in SYN.get(t["local"], []) if a != t["label"]]
        if alts:
            alt_concepts += 1
            for al, _src in alts:
                props.append(f'    skos:altLabel "{esc(al)}"@zh')
                n_alt += 1
        if t["comment"]:
            props.append(f'    skos:definition "{esc(t["comment"])}"@zh')
        for b in broader_of(t):
            props.append(f'    skos:broader term:{concept_by_iri[str(b)]["concept"]}')
        props.append(f'    skos:exactMatch <{t["iri"]}>')
        lines.append(f"term:{t['concept']}")
        lines.append(" ;\n".join(props) + " .")
        lines.append('')

out = ONT / "plumblossom-terminology.ttl"
out.write_text("\n".join(lines), encoding="utf-8")

# ---- 交叉校验（写文件后自检）----
print(f"术语总数: {len(terms)}")
print(f"  类: {sum(1 for t in terms if t['kind']=='class')}")
print(f"  对象属性: {sum(1 for t in terms if t['kind']=='objectProperty')}")
print(f"  数据属性: {sum(1 for t in terms if t['kind']=='datatypeProperty')}")
print(f"本地名冲突消歧: {collisions if collisions else '无'}")
print(f"带同义词(altLabel)的概念: {alt_concepts} / altLabel(同义词)总数: {n_alt}")
_bad = [k for k in SYN if k not in {t['local'] for t in terms}]
print(f"同义词表中未匹配到本体术语的键: {_bad if _bad else '无'}")

g2 = Graph()
g2.parse(str(out), format="turtle")
SKOS_CONCEPT = URIRef("http://www.w3.org/2004/02/skos/core#Concept")
concepts = set(str(s) for s in g2.subjects(RDF.type, SKOS_CONCEPT))
exm = list(g2.subject_objects(URIRef("http://www.w3.org/2004/02/skos/core#exactMatch")))
missing = [str(o) for _, o in exm if str(o) not in concept_by_iri]
print(f"概念数: {len(concepts)}")
print(f"exactMatch 断言数: {len(exm)}")
print(f"exactMatch 指向不存在的本体术语: {missing if missing else '无'}")
# 覆盖：每个带标签本体术语是否都有对应概念
concept_iris = set(concept_by_iri[t['iri']]['concept'] and TERM + concept_by_iri[t['iri']]['concept'] for t in terms)
term_set = set(concept_iris)
skos_concepts = concepts
print(f"未覆盖的带标签本体术语: {term_set - skos_concepts if term_set - skos_concepts else '无'}")
print(f"无对应本体术语的孤立概念: {skos_concepts - term_set if skos_concepts - term_set else '无'}")
