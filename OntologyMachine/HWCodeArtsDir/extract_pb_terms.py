# -*- coding: utf-8 -*-
"""从 PlumBlossom TBox 九域子本体提取术语（类 + 属性 + zh 标签 + 层级）。"""
import json
from pathlib import Path
from rdflib import Graph, RDF, RDFS, OWL, URIRef

ONT = Path(r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology")
DOMAINS = ["core", "divination", "yijing", "relations", "symbols",
           "omens", "judgement", "calendars", "rules"]

g = Graph()
for d in DOMAINS:
    g.parse(str(ONT / f"plumblossom-{d}.owl"), format="xml")

NS = "http://example.org/plumblossom/"

def label(iri):
    v = g.value(iri, RDFS.label)
    return str(v) if v else None

def local(iri):
    return str(iri).split("/")[-1].split("#")[-1]

def domain_of(iri):
    s = str(iri)
    for d in DOMAINS:
        if s.startswith(NS + d + "/") or s.startswith(NS + d + "#"):
            return d
    return None

def supers(iri):
    out = []
    for o in g.objects(iri, RDFS.subClassOf):
        if isinstance(o, URIRef):
            out.append(str(o))
    return out

out = {"classes": [], "objectProperties": [], "datatypeProperties": [],
       "individuals": []}

for s in g.subjects(RDF.type, OWL.Class):
    if isinstance(s, URIRef):
        out["classes"].append({
            "iri": str(s), "local": local(s), "label": label(s),
            "domain": domain_of(s), "super": supers(s)})

for s in g.subjects(RDF.type, OWL.ObjectProperty):
    if isinstance(s, URIRef):
        out["objectProperties"].append({
            "iri": str(s), "local": local(s), "label": label(s),
            "domain": domain_of(s)})

for s in g.subjects(RDF.type, OWL.DatatypeProperty):
    if isinstance(s, URIRef):
        out["datatypeProperties"].append({
            "iri": str(s), "local": local(s), "label": label(s),
            "domain": domain_of(s)})

# ABox individuals declared in tbox? none. Skip individuals.

for k in out:
    out[k].sort(key=lambda x: x["iri"])

res = Path(r"D:\work\Ontology\OntologyMachine\HWCodeArtsDir\pb_terms.json")
res.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")

print("classes:", len(out["classes"]))
print("objectProperties:", len(out["objectProperties"]))
print("datatypeProperties:", len(out["datatypeProperties"]))
print("--- classes with label ---")
for c in out["classes"]:
    print(f"{c['domain']:12} {c['local']:28} label={c['label']} super={[local(x) for x in c['super']]}")
print("--- object properties ---")
for p in out["objectProperties"]:
    print(f"{p['domain']:12} {p['local']:28} label={p['label']}")
print("--- datatype properties ---")
for p in out["datatypeProperties"]:
    print(f"{p['domain']:12} {p['local']:28} label={p['label']}")
