# -*- coding: utf-8 -*-
"""寻找显式否定对（X vs 不X / 无X / 未X / 反不X），供人工依医理甄选互斥对。"""
import re, os, sys, io, collections
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"

def read(p):
    with open(p, encoding='utf-8') as f: return f.read()

parents, labels = collections.defaultdict(set), {}
for cf in ["tcm-core.owl","tcm-zhengzhuang.owl","tcm-maixiang.owl","tcm-shexiang.owl","tcm-fuzheng.owl"]:
    txt = read(os.path.join(ONT, cf))
    for m in re.finditer(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">(.*?)</owl:Class>', txt, re.S):
        frag, body = m.group(1), m.group(2)
        for pm in re.finditer(r'<rdfs:subClassOf rdf:resource="#([A-Za-z0-9_]+)"', body):
            parents[frag].add(pm.group(1))
        lm = re.search(r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>', body)
        if lm: labels[frag] = lm.group(1)

def closure(frag):
    seen, stack = set(), [frag]
    while stack:
        c = stack.pop()
        for p in parents.get(c, ()):
            if p not in seen: seen.add(p); stack.append(p)
    return seen

CH = {"Zhengzhuang":"症状","Maixiang":"脉象","Shexiang":"舌象","Fuzheng":"腹证"}
def ch(frag):
    cl = closure(frag) | {frag}
    for top, name in CH.items():
        if top in cl: return name
    return "?"

# 中文标签 → fragment
by_label = collections.defaultdict(list)
for f, l in labels.items():
    by_label[l].append(f)

print("===== 显式否定对（中文名）=====")
pairs = []
for f, l in sorted(labels.items(), key=lambda kv: kv[1]):
    for pre in ["不", "无", "未", "反不", "不得"]:
        if l.startswith(pre) and len(l) > len(pre):
            pos = l[len(pre):]
            if pos in by_label:
                for pf in by_label[pos]:
                    pairs.append((pf, pos, f, l, ch(pf), ch(f)))
for pf, pl, nf, nl, cp, cn in pairs:
    print(f"  {pl}({pf}) [{cp}]  ⊥  {nl}({nf}) [{cn}]")

print("\n===== 舌象 =====")
for f in sorted(labels):
    if ch(f) == "舌象":
        print(f"  {f}\t{labels[f]}")

print("\n===== 腹证 =====")
for f in sorted(labels):
    if ch(f) == "腹证":
        print(f"  {f}\t{labels[f]}")
