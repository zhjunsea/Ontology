# -*- coding: utf-8 -*-
import glob, os, re
from rdflib import Graph, RDF, RDFS, OWL

ONT_DIR = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkZiWeiDouShu/ontology"
files = sorted(glob.glob(os.path.join(ONT_DIR, "ziweidoushu-*.owl")))
files = [f for f in files if not f.endswith(("ziweidoushu-all.owl", "ziweidoushu-tbox.owl", "ziweidoushu-abox.owl"))]

DOMAINS = ["core","calendar","chart","star","layout","state","palace",
           "pattern","fortune","reading","diagnosis","rules"]

def label(g, s):
    for l in g.objects(s, RDFS.label):
        if l.language == "zh":
            return str(l)
    for l in g.objects(s, RDFS.label):
        return str(l)
    return None

def pref(iri):
    iri = str(iri)
    for d in DOMAINS:
        p = "http://example.org/ziweidoushu/%s#" % d
        if iri.startswith(p):
            return d, iri[len(p):]
    return None, None

summary = {}
for f in files:
    g = Graph()
    g.parse(f, format="xml")
    for kind in (OWL.Class, OWL.ObjectProperty, OWL.DatatypeProperty):
        for s in g.subjects(RDF.type, kind):
            d, name = pref(s)
            if d is None:
                continue
            lb = label(g, s)
            if lb is None:
                continue
            summary.setdefault(d, []).append((name, kind.split("#")[-1], lb))

total = 0
for d in DOMAINS:
    items = sorted(set(summary.get(d, [])))
    print("### %s (%d)" % (d, len(items)))
    for name, k, lb in items:
        print("  %-28s %-16s %s" % (name, k, lb))
        total += 1
print("TOTAL concepts:", total)
