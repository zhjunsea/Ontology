# -*- coding: utf-8 -*-
import re, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

P = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-core.owl"
text = open(P, encoding='utf-8').read()

# 找所有 Panju_ 类块
blocks = re.findall(r'<owl:Class rdf:about="#(Panju_[^"]+)">(.*?)</owl:Class>', text, re.DOTALL)
print("Panju 总数:", len(blocks))
for name, body in blocks:
    label = re.search(r'<rdfs:label xml:lang="zh">(.*?)</rdfs:label>', body)
    comment = re.search(r'<rdfs:comment xml:lang="zh">(.*?)</rdfs:comment>', body, re.DOTALL)
    # 目标八纲/六经
    tgt = re.findall(r'<panjuTarget rdf:resource="#([^"]+)"/>', body)
    if not tgt:
        tgt = re.findall(r'<hasConclusion rdf:resource="#([^"]+)"/>', body)
    print("-" * 60)
    print("%s  「%s」" % (name, label.group(1) if label else ""))
    if tgt: print("   target:", tgt)
    if comment:
        c = re.sub(r'\s+', ' ', comment.group(1)).strip()
        print("   comment:", c[:400])
