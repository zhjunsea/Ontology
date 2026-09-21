# -*- coding: utf-8 -*-
import json, io
zz = json.load(io.open(r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/_harness/_zz_classes.json", encoding="utf-8"))
ref = set(json.load(io.open(r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/_harness/_ref_sym_order.json", encoding="utf-8")))
out=[]
for r in zz:
    mark = "*" if r["name"] in ref else " "
    sup = ",".join(r["supers"])
    out.append(f'{mark} {r["name"]}\t{r["label"]}\t{sup}\t{r["comment"]}')
io.open(r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/_harness/_zz_list.txt","w",encoding="utf-8").write("\n".join(out))
print("written", len(out), " ref-marked:", sum(1 for r in zz if r["name"] in ref))
