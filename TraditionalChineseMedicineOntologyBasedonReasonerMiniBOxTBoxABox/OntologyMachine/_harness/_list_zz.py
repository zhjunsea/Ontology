# -*- coding: utf-8 -*-
import json, io
D = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness/_zz_dump.json"
d = json.load(io.open(D, encoding="utf-8"))
ind = d["ind"]; cls = d["cls"]
rows = []
for frag, v in ind.items():
    base = frag[:-8] if frag.endswith("_instance") else frag
    c = cls.get(base, {})
    rows.append((base, v["label"], c.get("comment","")))
print("total", len(rows))
for i,(b,l,c) in enumerate(rows):
    print("%3d\t%s\t%s\t%s" % (i+1, b, l, c[:40]))
