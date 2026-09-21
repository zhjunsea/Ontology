# -*- coding: utf-8 -*-
import json, io
D = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/_harness/_zz_dump.json"
d = json.load(io.open(D, encoding="utf-8"))
cls = d["cls"]; ind = d["ind"]
inst_base = set(k[:-8] for k in ind if k.endswith("_instance"))
noinst = [k for k in cls if k not in inst_base]
print("classes without individual (%d):" % len(noinst))
for k in noinst:
    print("  ", k, cls[k]["label"], "sup=", cls[k]["sup"])
print()
# 根
roots = [k for k,v in cls.items() if not v["sup"]]
print("root classes:", [(k, cls[k]["label"]) for k in roots])
# 直接子类为 Zhengzhuang 的
top = [k for k,v in cls.items() if v["sup"] == ["Zhengzhuang"]]
print("direct children of Zhengzhuang:", len(top))
