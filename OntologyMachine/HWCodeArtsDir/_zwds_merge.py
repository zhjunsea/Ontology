# -*- coding: utf-8 -*-
"""合并 TBox(12 子本体+tbox) + ABox 为单文件 nt，剥离全部 owl:imports，供 ontop query 取数。"""
import os, glob
from rdflib import Graph, URIRef, OWL

D = os.path.join(os.path.dirname(__file__), "..", "OntologyFrameworkZiWeiDouShu", "ontology")
files = ["ziweidoushu-tbox.owl", "ziweidoushu-abox.owl"] + [
    os.path.basename(p) for p in sorted(glob.glob(os.path.join(D, "ziweidoushu-*.owl")))
    if os.path.basename(p) not in ("ziweidoushu-tbox.owl", "ziweidoushu-abox.owl", "ziweidoushu-all.owl")]
g = Graph()
for f in files:
    g.parse(os.path.join(D, f), format="xml")
for s, p, o in list(g.triples((None, OWL.imports, None))):
    g.remove((s, p, o))
out = os.path.join(os.path.dirname(__file__), "_zwds_merged.nt")
g.serialize(destination=out, format="nt")
print("merged", len(g), "triples ->", out)
