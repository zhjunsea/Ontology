# -*- coding: utf-8 -*-
import os, re, sys, xml.etree.ElementTree as ET
from collections import defaultdict

ONT = r"D:\work\Ontology\GoldenWind\OntologyMachine\OntologyFrameworkExample\ontology"
RDF = "{http://www.w3.org/1999/02/22-rdf-syntax-ns#}"
OWL = "{http://www.w3.org/2002/07/owl#}"

# catalog
cat = ET.parse(os.path.join(ONT, "catalog-v001.xml"))
ns = {"c": "urn:oasis:names:tc:entity:xmlns:xml:catalog"}
catalog = {}
for uri in cat.getroot().iter("{urn:oasis:names:tc:entity:xmlns:xml:catalog}uri"):
    catalog[uri.get("name")] = uri.get("uri")

print("== catalog entries ==")
for k, v in catalog.items():
    ok = os.path.exists(os.path.join(ONT, v))
    print(f"  {k} -> {v} {'OK' if ok else 'MISSING'}")
    if not ok:
        print("  !! catalog target missing")

files = [f for f in os.listdir(ONT) if f.endswith((".owl", ".ttl"))]
ont_iri = {}
imports = defaultdict(list)

for f in sorted(files):
    p = os.path.join(ONT, f)
    with open(p, "rb") as fh:
        raw = fh.read().decode("utf-8")
    if f.endswith(".owl"):
        try:
            root = ET.parse(p).getroot()
        except Exception as e:
            print(f"!! XML parse error {f}: {e}")
            continue
        o = root.find(OWL + "Ontology")
        if o is not None:
            ont_iri[f] = o.get(RDF + "about")
        for imp in root.iter(OWL + "imports"):
            imports[f].append(imp.get(RDF + "resource"))
    else:
        m = re.search(r"<([^>]+)>\s*\n\s*a\s+owl:Ontology", raw) or re.search(r"<([^>]+)>\s+a\s+owl:Ontology", raw)
        if m:
            ont_iri[f] = m.group(1)
        for m2 in re.finditer(r"owl:imports\s+<([^>]+)>", raw):
            imports[f].append(m2.group(1))

print("\n== ontology IRIs ==")
by_iri = defaultdict(list)
for f, iri in ont_iri.items():
    by_iri[iri].append(f)
    print(f"  {iri}  <- {f}")

print("\n== duplicate ontology IRIs ==")
for iri, fs in by_iri.items():
    if len(fs) > 1:
        print(f"  DUP {iri}: {fs}")

print("\n== imports resolution ==")
bad = 0
for f, imps in imports.items():
    for i in imps:
        resolved = i in catalog
        exists = resolved and os.path.exists(os.path.join(ONT, catalog[i]))
        flag = "OK" if (resolved and exists) else "UNRESOLVED"
        if flag != "OK":
            bad += 1
        print(f"  {f}: {i} -> {catalog.get(i, '(none)')} {flag}")

# check each non-'all' ontology IRI is registered in catalog
print("\n== catalog coverage of non-all ontology IRIs ==")
for iri, fs in by_iri.items():
    if iri in ("http://example.org/pizza/all",):
        continue
    if iri not in catalog:
        print(f"  NOT-IN-CATALOG {iri} ({fs})")
        bad += 1

print("\nBAD =", bad)
