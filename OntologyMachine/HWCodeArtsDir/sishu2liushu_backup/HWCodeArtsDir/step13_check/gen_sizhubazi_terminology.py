# -*- coding: utf-8 -*-
import os, sys
from rdflib import Graph, RDF, RDFS, OWL, URIRef
from rdflib.namespace import split_uri
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from aliases import ALIASES
from en_labels import EN

ONTO = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology"
SUBS = ["core","calendar","chart","relations","tengod","deity","pattern",
        "yongshen","fortune","reading","diagnosis","rules"]
OUT = os.path.join(ONTO, "sizhubazi-terminology.ttl")

g = Graph()
for n in SUBS:
    g.parse(os.path.join(ONTO, f"sizhubazi-{n}.owl"))

def local(u):
    try:
        _, l = split_uri(u)
        return l
    except Exception:
        return str(u).rstrip('/').split('/')[-1]

def ns_of(u):
    s = str(u)
    for n in SUBS:
        if f"/{n}/" in s:
            return n
    return "core"

# collect label / comment by IRI, kind, ns
def collect(rdf_type, kind):
    res = {}
    for s in set(g.subjects(RDF.type, rdf_type)):
        if not isinstance(s, URIRef):
            continue
        labs = list(g.objects(s, RDFS.label))
        if not labs:
            continue
        res[s] = dict(kind=kind, ns=ns_of(s), local=local(s),
                      label=str(labs[0]),
                      comment=str(list(g.objects(s, RDFS.comment))[0]) if list(g.objects(s, RDFS.comment)) else "")
    return res

classes = collect(OWL.Class, "C")
oprops  = collect(OWL.ObjectProperty, "OP")
dprops  = collect(OWL.DatatypeProperty, "DP")

allcon = {}
allcon.update(classes); allcon.update(oprops); allcon.update(dprops)

# term name with collision handling among ALL concepts
from collections import Counter
loccount = Counter(v["local"] for v in allcon.values())
def term_name(v):
    l = v["local"]
    if loccount[l] > 1:
        return f'{v["ns"]}_{l}'
    return l

# ensure unique term names
names = [term_name(v) for v in allcon.values()]
dup = [k for k, c in Counter(names).items() if c > 1]
assert not dup, f"dup term names: {dup}"

# parent (named superclass) for classes
def parent_of(iri):
    ps = [o for o in g.objects(iri, RDFS.subClassOf) if isinstance(o, URIRef) and o in allcon]
    return ps[0] if ps else None

def iri_ref(iri, v):
    return f'{v["ns"]}:{v["local"]}'

# 同名「类」与「对象属性」共用本体标签时，对对象属性加「（关系）」限定以消歧
# （标签主体仍取自本体 rdfs:label，属本应用业务约定，非凭空造词）
DISAMBIG = {
    "relations:clashesWith": "相冲（关系）",
    "relations:combinesWith": "天干五合（关系）",
    "relations:punishesWith": "三刑（关系）",
    "relations:sixCombinesWith": "六合（关系）",
}

def render(iri, v):
    tn = term_name(v)
    key = f'{v["ns"]}:{v["local"]}'
    pref = DISAMBIG.get(key, v["label"])
    en = EN.get(key)
    assert en, f"missing @en for {key}"
    lines = [f"term:{tn}",
             "    a skos:Concept ;",
             f'    skos:prefLabel "{pref}"@zh ;',
             f'    skos:prefLabel "{en}"@en ;']
    if key in DISAMBIG:
        lines.append(f'    # 本体 rdfs:label: {v["label"]}（加「（关系）」以与同名类消歧）')
    for alt, src in ALIASES.get(key, []):
        lines.append(f'    skos:altLabel "{alt}"@zh ;   # 源: {src}')
    if v["comment"]:
        c = v["comment"].replace('"', "'").replace("\n", " ")
        lines.append(f'    skos:definition "{c}"@zh ;')
    if v["kind"] == "C":
        p = parent_of(iri)
        if p is not None:
            lines.append(f'    skos:broader term:{term_name(allcon[p])} ;')
    lines.append(f'    skos:exactMatch {iri_ref(iri, v)} .')
    return "\n".join(lines)

def sec(title, items, order_ns=True):
    order = {n: i for i, n in enumerate(SUBS)}
    items = sorted(items, key=lambda kv: (order.get(kv[1]["ns"], 99), kv[1]["local"]))
    out = [f"# ========================", f"# {title}（{len(items)}）", "# ========================"]
    cur = None
    for iri, v in items:
        if v["ns"] != cur:
            cur = v["ns"]
            out.append(f"\n# ---- {cur} ----")
        out.append(render(iri, v))
        out.append("")
    return "\n".join(out)

header = f'''@prefix skos: <http://www.w3.org/2004/02/skos/core#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix dct:  <http://purl.org/dc/terms/> .
@prefix owl:  <http://www.w3.org/2002/07/owl#> .
@prefix term: <http://example.org/sizhubazi/term/> .
@prefix core:       <http://example.org/sizhubazi/core/> .
@prefix calendar:   <http://example.org/sizhubazi/calendar/> .
@prefix chart:      <http://example.org/sizhubazi/chart/> .
@prefix relations:  <http://example.org/sizhubazi/relations/> .
@prefix tengod:     <http://example.org/sizhubazi/tengod/> .
@prefix deity:      <http://example.org/sizhubazi/deity/> .
@prefix pattern:    <http://example.org/sizhubazi/pattern/> .
@prefix yongshen:   <http://example.org/sizhubazi/yongshen/> .
@prefix fortune:    <http://example.org/sizhubazi/fortune/> .
@prefix reading:    <http://example.org/sizhubazi/reading/> .
@prefix diagnosis:  <http://example.org/sizhubazi/diagnosis/> .
@prefix rules:      <http://example.org/sizhubazi/rules/> .

<http://example.org/sizhubazi/term>
    a owl:Ontology ;
    a skos:ConceptScheme ;
    skos:prefLabel "四柱命理业务术语体系"@zh ;
    skos:prefLabel "SiZhuBaZi (Four Pillars) Business Terminology"@en ;
    dct:created "2026-10-29"^^<http://www.w3.org/2001/XMLSchema#date> ;
    dct:description "四柱命理领域核心业务术语的标准化词汇表，术语全部来源于本体 (owl:Class / owl:ObjectProperty / owl:DatatypeProperty 的 rdfs:label)，用于统一 OWL 本体及其他系统中的词义，实现同名同义、同义同名。同义词/别名逐条标注四书或设计文档出处，无出处者标【待考】。"@zh .
'''

body = "\n".join([
    header,
    sec("类（业务概念）", list(classes.items())),
    "",
    sec("对象属性（业务关系）", list(oprops.items())),
    "",
    sec("数据属性（业务字段）", list(dprops.items())),
    "",
])

with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(body)

# verify parse + uniqueness
g2 = Graph(); g2.parse(OUT)
from rdflib.namespace import SKOS
concepts = set(g2.subjects(RDF.type, SKOS.Concept))
prefs = [str(o) for s in concepts for o in g2.objects(s, SKOS.prefLabel) if getattr(o, "language", None) == "zh"]
pc = Counter(prefs)
print("classes", len(classes), "oprops", len(oprops), "dprops", len(dprops))
print("concepts", len(concepts))
print("dup prefLabels(zh):", [k for k, c in pc.items() if c > 1])
print("written", OUT)
