# -*- coding: utf-8 -*-
import re, io, os, json, glob
D = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
zz = json.load(io.open(r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/_harness/_zz_classes.json", encoding="utf-8"))
label_of = {r["name"]: r["label"] for r in zz}
# collect all #Xxx refs inside equivalentClass blocks of fangzheng files
refs = {}
for f in glob.glob(os.path.join(D,"fangzheng","*.owl")):
    if ".bak" in f: continue
    src = io.open(f, encoding="utf-8").read()
    for m in re.finditer(r'<owl:equivalentClass>(.*?)</owl:equivalentClass>', src, re.S):
        body = m.group(1)
        for r in re.findall(r'rdf:resource="#([^"]+)"', body):
            refs[r] = refs.get(r,0)+1
    # also someObjectOf / someValuesFrom style
    for r in re.findall(r'<owl:onProperty[^>]*/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"', src):
        refs[r] = refs.get(r,0)+1
# filter to symptom classes
sym = {k:v for k,v in refs.items() if k in label_of}
print("方证定义引用的类总数:", len(refs), " 其中症状类:", len(sym))
srt = sorted(sym.items(), key=lambda x:-x[1])
print("Top 60 症状(按引用次数):")
for k,v in srt[:60]:
    print(f"  {v:3d}  {k:28s} {label_of[k]}")
json.dump([k for k,_ in srt], io.open(r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/_harness/_ref_sym_order.json","w",encoding="utf-8"), ensure_ascii=False)
