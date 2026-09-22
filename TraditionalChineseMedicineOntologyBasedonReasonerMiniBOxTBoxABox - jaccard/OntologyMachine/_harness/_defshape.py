# -*- coding: utf-8 -*-
"""方证定义形状分析：合取项构成、最大合取长度、定义链、nominal(hasValue)。只读。"""
import os, glob, collections
import xml.etree.ElementTree as ET

OWL = 'http://www.w3.org/2002/07/owl#'
RDF = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'
NS = {'owl': OWL, 'rdf': RDF, 'rdfs': 'http://www.w3.org/2000/01/rdf-schema#'}

ROOT = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
FILES = [f for f in glob.glob(os.path.join(ROOT, "*.owl")) + glob.glob(os.path.join(ROOT, "fangzheng", "*.owl"))
         if ".bak" not in f]

def frag(el):
    a = el.get(RDF + 'about')
    return a.split('#')[-1] if a else '?'

def local(el):
    return el.tag.split('}')[-1]

# ---- 第一遍：方证类名 ----
fangzheng = set()
parsed = 0
for f in FILES:
    try:
        root = ET.parse(f).getroot()
        parsed += 1
    except Exception as e:
        print("PARSE FAIL", os.path.basename(f), e)
        continue
    for c in root:
        if local(c) != 'Class':
            continue
        for sc in c:
            if local(sc) == 'subClassOf':
                r = sc.get(RDF + 'resource') or ''
                if 'Fangzheng' in r:
                    fangzheng.add(frag(c))
print("解析成功文件 =", parsed, "/", len(FILES))
print("方证类总数 =", len(fangzheng))

# ---- 第二遍：定义形状 ----
kinds = collections.Counter()
maxlen, maxwho = 0, ''
chain, lens = [], []
nominal = 0
per_file = collections.Counter()

for f in FILES:
    try:
        root = ET.parse(f).getroot()
    except Exception:
        continue
    for c in root:
        if local(c) != 'Class':
            continue
        name = frag(c)
        for eq in c:
            if local(eq) != 'equivalentClass':
                continue
            inner = [x for x in eq if local(x) == 'Class']
            if not inner:
                continue
            inter = [x for x in inner[0] if local(x) == 'intersectionOf']
            if not inter:
                continue
            ops = list(inter[0])
            lens.append(len(ops))
            per_file[os.path.basename(f)] += 1
            if len(ops) > maxlen:
                maxlen, maxwho = len(ops), name
            for op in ops:
                if local(op) == 'Class':
                    r = op.get(RDF + 'about') or ''
                    nm = r.split('#')[-1]
                    if nm in fangzheng:
                        kinds['合取项=另一个方证类(定义链)'] += 1
                        chain.append((name, nm))
                    else:
                        kinds['合取项=命名类(六经/八纲/症状)'] += 1
                elif local(op) == 'Restriction':
                    onp = [x for x in op if local(x) == 'onProperty']
                    p = '?'
                    if onp:
                        rr = onp[0].get(RDF + 'resource') or '?'
                        p = rr.split('#')[-1]
                    if any(local(x) == 'hasValue' for x in op):
                        kinds['合取项=∃%s.{个体} ← nominal' % p] += 1
                        nominal += 1
                    elif any(local(x) == 'someValuesFrom' for x in op):
                        kinds['合取项=∃%s.类' % p] += 1
                    else:
                        kinds['合取项=其他限制'] += 1
                else:
                    kinds['合取项=其他(%s)' % local(op)] += 1

print("\n=== 合取项类型分布 ===")
for k, v in kinds.most_common():
    print("%7d  %s" % (v, k))
print("\nnominal(hasValue) 合取项总数 =", nominal)
print("最大合取长度 = %d （%s）" % (maxlen, maxwho))
if lens:
    s = sorted(lens)
    print("合取长度: min=%d 中位=%d p90=%d max=%d 平均=%.1f 样本=%d"
          % (s[0], s[len(s)//2], s[int(len(s)*0.9)], s[-1], sum(s)/len(s), len(s)))
print("\n=== 每个文件的 equivalentClass(交集) 数 ===")
for k, v in per_file.most_common():
    print("%7d  %s" % (v, k))
print("\n=== 定义链（合取项引用了另一个方证类）===")
for a, b in chain[:40]:
    print("  %s ≡ ... ⊓ %s" % (a, b))
print("  共", len(chain), "处")
