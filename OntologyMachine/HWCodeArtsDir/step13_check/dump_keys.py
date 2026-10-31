# -*- coding: utf-8 -*-
import os
from rdflib import Graph, RDF, RDFS, OWL, URIRef
from rdflib.namespace import split_uri

ONTO = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology"
SUBS = ["core","calendar","chart","relations","tengod","deity","pattern",
        "yongshen","fortune","reading","diagnosis","rules"]

def loc(u):
    try:
        _, n = split_uri(u)
        return n
    except Exception:
        return str(u).rstrip('/').split('/')[-1]

def ns_of(u):
    s = str(u)
    for n in SUBS:
        if f"/{n}/" in s:
            return n
    return "core"

g = Graph()
for n in SUBS:
    g.parse(os.path.join(ONTO, f"sizhubazi-{n}.owl"))

rows = []
for t, kind in ((OWL.Class, "C"), (OWL.ObjectProperty, "OP"), (OWL.DatatypeProperty, "DP")):
    for s in set(g.subjects(RDF.type, t)):
        if not isinstance(s, URIRef):
            continue
        lbs = list(g.objects(s, RDFS.label))
        if not lbs:
            continue
        rows.append((ns_of(s), loc(s), str(lbs[0]), kind))

rows.sort()
with open(os.path.join(os.path.dirname(__file__), "keys.tsv"), "w", encoding="utf-8") as fh:
    for ns, l, lb, k in rows:
        fh.write(f"{ns}:{l}\t{lb}\t{k}\n")
print("total", len(rows))
for k in ("C", "OP", "DP"):
    print(k, sum(1 for r in rows if r[3] == k))
