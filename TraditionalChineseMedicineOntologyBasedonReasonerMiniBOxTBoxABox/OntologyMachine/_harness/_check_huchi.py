# -*- coding: utf-8 -*-
"""核验候选互斥 fragment 是否存在，并打印其父类闭包（判断复合类是否继承互斥）。"""
import re, os, sys, io, collections
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"

def read(p):
    with open(p, encoding='utf-8') as f: return f.read()

parents, labels, equiv = collections.defaultdict(set), {}, {}
for cf in ["tcm-core.owl","tcm-zhengzhuang.owl","tcm-maixiang.owl","tcm-shexiang.owl","tcm-fuzheng.owl"]:
    txt = read(os.path.join(ONT, cf))
    for m in re.finditer(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">(.*?)</owl:Class>', txt, re.S):
        frag, body = m.group(1), m.group(2)
        for pm in re.finditer(r'<rdfs:subClassOf rdf:resource="#([A-Za-z0-9_]+)"', body):
            parents[frag].add(pm.group(1))
        lm = re.search(r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>', body)
        if lm: labels[frag] = lm.group(1)
        if 'equivalentClass' in body:
            equiv[frag] = re.findall(r'rdf:resource="#([A-Za-z0-9_]+)"', body)

def closure(frag):
    seen, stack = set(), [frag]
    while stack:
        c = stack.pop()
        for p in parents.get(c, ()):
            if p not in seen: seen.add(p); stack.append(p)
    return seen

cands = """Hanchu Wuhan Buhanchu Buhan Dahan Duohanchu Daohan Touhanchu Dantouhanchu
Kouke Buke Fanbuke Dake
Xiaobianbuli Xiaobianli Xiaobiannan
Xiali Budabian Dabianying Dabiantang Dabiannan Dabianmijie
Fare Wure Bure Bure
Ehan Buehan Efeng Ere
Tu Butu Ou Buou Yuyin Buyuyin Dare Wudare
Chenchimai Fuhuanmai Fushumai Fuhuamai Chenhuamai Chenchimai
Ruomai Xumai Shimai Weimai Xiaomai Ximai Damai Hongmai Changmai Duanmai
Huamai Semai Shumai Chimai Fumai Chenmai Fumai_Yin
Antong Anzhibutong Anzhiru Anzhishiying""".split()

for f in dict.fromkeys(cands):
    if f in labels:
        cl = sorted(closure(f))
        print(f"OK   {f:16s} {labels[f]:8s} 父={cl}")
    else:
        print(f"MISS {f:16s} (本体无此类)")
